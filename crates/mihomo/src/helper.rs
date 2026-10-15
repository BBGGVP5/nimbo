//! Authenticated root broker client. An owned connection is the TUN lease.
use nimbo_ipc::{
    decode_response, encode_command, framing, Command, MihomoTunRequest, Response, UNIX_SOCKET_PATH,
};
use std::{
    io::{BufReader, BufWriter},
    net::Shutdown,
    os::{fd::AsRawFd, unix::net::UnixStream},
    time::Duration,
};
fn connect() -> Result<UnixStream, String> {
    let stream = UnixStream::connect(UNIX_SOCKET_PATH).map_err(|_| "MIHOMO_HELPER_REQUIRED")?;
    let mut creds = libc::ucred {
        pid: 0,
        uid: u32::MAX,
        gid: u32::MAX,
    };
    let mut length = std::mem::size_of::<libc::ucred>() as libc::socklen_t;
    if unsafe {
        libc::getsockopt(
            stream.as_raw_fd(),
            libc::SOL_SOCKET,
            libc::SO_PEERCRED,
            (&mut creds as *mut libc::ucred).cast(),
            &mut length,
        )
    } != 0
        || creds.uid != 0
    {
        return Err("HELPER_AUTH_FAILED".into());
    }
    stream
        .set_read_timeout(Some(Duration::from_secs(35)))
        .and_then(|_| stream.set_write_timeout(Some(Duration::from_secs(5))))
        .map_err(|_| "HELPER_IO_FAILED")?;
    Ok(stream)
}
fn call(stream: &UnixStream, command: &Command) -> Result<Response, String> {
    framing::write_frame(
        &mut BufWriter::new(stream),
        &encode_command(command).map_err(|_| "INVALID_REQUEST")?,
    )
    .map_err(|_| "HELPER_IO_FAILED")?;
    let bytes = framing::read_frame(&mut BufReader::new(stream)).map_err(|_| "HELPER_IO_FAILED")?;
    match decode_response(&bytes).map_err(|_| "INVALID_HELPER_RESPONSE")? {
        Response::Error { message, .. } => Err(
            if !message.is_empty()
                && message.len() < 80
                && message.bytes().all(|b| b.is_ascii_uppercase() || b == b'_')
            {
                message
            } else {
                "HELPER_REJECTED".into()
            },
        ),
        other => Ok(other),
    }
}
struct Cancel(Option<UnixStream>);
impl Drop for Cancel {
    fn drop(&mut self) {
        if let Some(stream) = self.0.take() {
            let _ = stream.shutdown(Shutdown::Both);
        }
    }
}
async fn exchange_on(
    stream: UnixStream,
    command: Command,
) -> Result<(UnixStream, Response), String> {
    let mut cancelled = Cancel(Some(stream.try_clone().map_err(|_| "HELPER_IO_FAILED")?));
    let (tx, rx) = tokio::sync::oneshot::channel();
    std::thread::spawn(move || {
        let response = call(&stream, &command);
        let _ = tx.send(response.map(|r| (stream, r)));
    });
    let result = tokio::time::timeout(Duration::from_secs(36), rx)
        .await
        .map_err(|_| "HELPER_TIMEOUT")?
        .map_err(|_| "HELPER_IO_FAILED")?;
    if result.is_ok() {
        drop(cancelled.0.take());
    }
    result
}
pub fn available(expected: &str) -> bool {
    if expected.len() != 64 {
        return false;
    }
    connect().and_then(|stream|{stream.set_read_timeout(Some(Duration::from_secs(3))).map_err(|_|"HELPER_IO_FAILED")?;call(&stream,&Command::MihomoStatus)})
      .is_ok_and(|r|matches!(r,Response::MihomoAvailability{binary_sha256,available:true,..} if binary_sha256==expected))
}
pub async fn preflight(request: MihomoTunRequest) -> Result<(), String> {
    let (_, response) = exchange_on(connect()?, Command::MihomoPreflight(request)).await?;
    if matches!(response, Response::Ok) {
        Ok(())
    } else {
        Err("INVALID_HELPER_RESPONSE".into())
    }
}
pub struct Lease {
    stream: Option<UnixStream>,
}
impl Lease {
    pub async fn start(
        request: MihomoTunRequest,
    ) -> Result<(Self, nimbo_ipc::MihomoReady), String> {
        let (stream, response) = exchange_on(connect()?, Command::MihomoUp(request)).await?;
        match response {
            Response::MihomoReady(ready) => Ok((
                Self {
                    stream: Some(stream),
                },
                ready,
            )),
            _ => Err("INVALID_HELPER_RESPONSE".into()),
        }
    }
    pub fn running(&mut self) -> bool {
        self.stream.as_ref().is_some_and(|stream| {
            let _ = stream.set_read_timeout(Some(Duration::from_secs(1)));
            matches!(
                call(stream, &Command::MihomoStatus),
                Ok(Response::MihomoAvailability { running: true, .. })
            )
        })
    }
    pub fn stop(&mut self) -> Result<(), String> {
        let Some(stream) = self.stream.take() else {
            return Ok(());
        };
        let _ = stream.set_read_timeout(Some(Duration::from_secs(5)));
        let response = call(&stream, &Command::MihomoDown);
        let _ = stream.shutdown(Shutdown::Both);
        match response {
            Ok(Response::Ok) => Ok(()),
            Err(e) => Err(e),
            _ => Err("INVALID_HELPER_RESPONSE".into()),
        }
    }
}
impl Drop for Lease {
    fn drop(&mut self) {
        let _ = self.stop();
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    async fn cancelled_future_closes_the_actual_connection() {
        let (client, mut server) = UnixStream::pair().unwrap();
        let closed = std::thread::spawn(move || {
            use std::io::Read;
            let _ = framing::read_frame(&mut server).unwrap();
            server
                .set_read_timeout(Some(Duration::from_secs(2)))
                .unwrap();
            let mut byte = [0];
            assert_eq!(server.read(&mut byte).unwrap(), 0);
        });
        assert!(tokio::time::timeout(
            Duration::from_millis(25),
            exchange_on(client, Command::MihomoStatus)
        )
        .await
        .is_err());
        closed.join().unwrap();
    }
    #[tokio::test]
    async fn success_keeps_the_returned_lease_open() {
        let (client, mut server) = UnixStream::pair().unwrap();
        let peer = std::thread::spawn(move || {
            framing::read_frame(&mut server).unwrap();
            framing::write_frame(
                &mut server,
                &nimbo_ipc::encode_response(&Response::Ok).unwrap(),
            )
            .unwrap();
            use std::io::Read;
            let mut b = [0];
            assert_eq!(server.read(&mut b).unwrap(), 0);
        });
        let (stream, reply) = exchange_on(client, Command::MihomoDown).await.unwrap();
        assert!(matches!(reply, Response::Ok));
        drop(stream);
        peer.join().unwrap();
    }
}
