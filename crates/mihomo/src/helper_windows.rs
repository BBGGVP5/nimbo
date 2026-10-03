//! Authenticated Windows broker client. Dropping startup closes the actual pipe.
use nimbo_ipc::{windows as trust, Command, MihomoTunRequest, Response};
use std::{
    os::windows::io::RawHandle,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
    time::Duration,
};
use tokio::net::windows::named_pipe::NamedPipeClient;
async fn connect() -> Result<NamedPipeClient, String> {
    use windows_sys::Win32::{Foundation::*, Storage::FileSystem::*};
    let deadline = tokio::time::Instant::now() + Duration::from_secs(2);
    loop {
        let handle = unsafe {
            CreateFileW(
                trust::wide(std::ffi::OsStr::new(trust::TUN_PIPE)).as_ptr(),
                trust::CLIENT_ACCESS,
                0,
                std::ptr::null(),
                OPEN_EXISTING,
                FILE_FLAG_OVERLAPPED | SECURITY_SQOS_PRESENT | SECURITY_IDENTIFICATION,
                std::ptr::null_mut(),
            )
        };
        if handle != INVALID_HANDLE_VALUE {
            let pipe = unsafe { NamedPipeClient::from_raw_handle(handle as RawHandle) }
                .map_err(|_| "HELPER_IO_FAILED")?;
            trust::authenticate_server(&pipe)?;
            return Ok(pipe);
        }
        if unsafe { GetLastError() } != ERROR_PIPE_BUSY || tokio::time::Instant::now() >= deadline {
            return Err("MIHOMO_HELPER_REQUIRED".into());
        }
        tokio::time::sleep(Duration::from_millis(25)).await;
    }
}
async fn call(
    pipe: &mut NamedPipeClient,
    command: Command,
    timeout: Duration,
) -> Result<Response, String> {
    tokio::time::timeout(timeout, async {
        trust::write_frame(
            pipe,
            &nimbo_ipc::encode_command(&command).map_err(|_| "INVALID_REQUEST")?,
        )
        .await?;
        let bytes = trust::read_frame(pipe).await?;
        match nimbo_ipc::decode_response(&bytes).map_err(|_| "INVALID_HELPER_RESPONSE")? {
            Response::Error { message, .. } => Err(
                if message.len() < 80
                    && !message.is_empty()
                    && message.bytes().all(|b| b.is_ascii_uppercase() || b == b'_')
                {
                    message
                } else {
                    "HELPER_REJECTED".into()
                },
            ),
            response => Ok(response),
        }
    })
    .await
    .map_err(|_| "HELPER_TIMEOUT")?
}
pub fn capabilities(expected: &str) -> (bool, bool, bool) {
    if expected.len() != 64 {
        return (false, false, false);
    }
    let expected = expected.to_owned();
    std::thread::spawn(move || {
        let Ok(runtime) = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
        else {
            return (false, false, false);
        };
        runtime.block_on(async {
            let Ok(mut pipe) = connect().await else {
                return (false, false, false);
            };
            match call(&mut pipe, Command::MihomoStatus, Duration::from_secs(3)).await {
                Ok(response) => verified_capabilities(&response, &expected),
                _ => (false, false, false),
            }
        })
    })
    .join()
    .unwrap_or((false, false, false))
}
fn verified_capabilities(response: &Response, expected: &str) -> (bool, bool, bool) {
    match response {
        Response::MihomoAvailability {
            binary_sha256,
            available: true,
            both_available,
            kill_switch_available,
            reboot_kill_switch_available,
            ..
        } if binary_sha256 == expected => (
            true,
            *both_available,
            *kill_switch_available && *reboot_kill_switch_available,
        ),
        _ => (false, false, false),
    }
}
async fn verify_policy(
    pipe: &mut NamedPipeClient,
    request: &MihomoTunRequest,
) -> Result<(), String> {
    if !request.kill_switch {
        return Ok(());
    }
    let response = call(pipe, Command::MihomoStatus, Duration::from_secs(3)).await?;
    if verified_capabilities(&response, &request.binary_sha256).2 {
        Ok(())
    } else {
        Err("MIHOMO_KILL_SWITCH_UNAVAILABLE".into())
    }
}
pub fn available(expected: &str) -> bool {
    capabilities(expected).0
}
pub async fn reset_kill_switch() -> Result<(), String> {
    let mut pipe = connect().await?;
    match call(
        &mut pipe,
        Command::MihomoResetKillSwitch,
        Duration::from_secs(5),
    )
    .await?
    {
        Response::Ok => Ok(()),
        _ => Err("INVALID_HELPER_RESPONSE".into()),
    }
}
pub async fn preflight(request: MihomoTunRequest) -> Result<(), String> {
    let mut pipe = connect().await?;
    verify_policy(&mut pipe, &request).await?;
    if matches!(
        call(
            &mut pipe,
            Command::MihomoPreflight(request),
            Duration::from_secs(35)
        )
        .await?,
        Response::Ok
    ) {
        Ok(())
    } else {
        Err("INVALID_HELPER_RESPONSE".into())
    }
}
pub struct Lease {
    stop: Option<tokio::sync::oneshot::Sender<bool>>,
    result: std::sync::mpsc::Receiver<Result<(), String>>,
    running: Arc<AtomicBool>,
    abandoned: bool,
    kill_switch: bool,
}
impl Lease {
    pub async fn start(
        request: MihomoTunRequest,
    ) -> Result<(Self, nimbo_ipc::MihomoReady), String> {
        let (stop, mut cancel) = tokio::sync::oneshot::channel();
        let (startup, ready) = tokio::sync::oneshot::channel();
        let (tx, result) = std::sync::mpsc::sync_channel(1);
        let running = Arc::new(AtomicBool::new(false));
        let live = running.clone();
        let kill_switch = request.kill_switch;
        // The owning pipe is created inside this dedicated reactor, not moved
        // from another runtime. Cancellation drops its last handle immediately.
        std::thread::spawn(move || {
            let result = (|| {
                let runtime = tokio::runtime::Builder::new_current_thread()
                    .enable_all()
                    .build()
                    .map_err(|_| "HELPER_IO_FAILED")?;
                runtime.block_on(async{
                    let mut pipe=tokio::select!{r=connect()=>r?,_=&mut cancel=>return Err("CONNECTION_CANCELLED".into())};
                    let response=tokio::select!{
                        r=async{verify_policy(&mut pipe,&request).await?;call(&mut pipe,Command::MihomoUp(request),Duration::from_secs(35)).await}=>r,
                        _=&mut cancel=>return Err("CONNECTION_CANCELLED".into()),
                    };
                    let ready=match response{Ok(Response::MihomoReady(r))=>r,Ok(_)=>{let e="INVALID_HELPER_RESPONSE".to_string();let _=startup.send(Err(e.clone()));return Err(e);},Err(e)=>{let _=startup.send(Err(e.clone()));return Err(e);}};
                    live.store(true,Ordering::SeqCst);
                    if startup.send(Ok(ready)).is_err(){return Err("CONNECTION_CANCELLED".into());}
                    loop{
                        tokio::select!{
                            r=&mut cancel=>{if r != Ok(true) {return Ok(());} break;},
                            _=tokio::time::sleep(Duration::from_secs(1))=>{}
                        }
                        match call(&mut pipe, Command::MihomoStatus, Duration::from_secs(2)).await {
                            Ok(Response::MihomoAvailability {running:true,..})=>{},
                            Ok(Response::MihomoAvailability {running:false,..})=>{live.store(false,Ordering::SeqCst);return Err(if kill_switch {"KILL_SWITCH_RESET_REQUIRED"} else {"CORE_EXITED"}.into());},
                            Err(e)=>return Err(e),
                            _=>return Err("INVALID_HELPER_RESPONSE".into()),
                        }
                    }
                    if matches!(call(&mut pipe,Command::MihomoDown,Duration::from_secs(35)).await?,Response::Ok){Ok(())}else{Err("INVALID_HELPER_RESPONSE".into())}
                })
            })();
            live.store(false, Ordering::SeqCst);
            let _ = tx.send(result);
        });
        // An uncompleted future owns this sender. Dropping it cancels the actual
        // worker/connection; there is no detached task keeping a native lease.
        let ready = ready.await.map_err(|_| "HELPER_IO_FAILED")??;
        Ok((
            Self {
                stop: Some(stop),
                result,
                running,
                abandoned: false,
                kill_switch,
            },
            ready,
        ))
    }
    pub fn running(&mut self) -> bool {
        self.running.load(Ordering::SeqCst)
    }
    pub fn abandon(&mut self) {
        // Dropping the sender closes the owning pipe, but never sends Down.
        let Some(stop) = self.stop.take() else {
            return;
        };
        self.abandoned = true;
        drop(stop);
        self.running.store(false, Ordering::SeqCst);
        let _ = self.result.recv_timeout(Duration::from_secs(3));
    }
    pub fn stop(&mut self) -> Result<(), String> {
        let Some(stop) = self.stop.take() else {
            return if self.abandoned {
                Err(if self.kill_switch {
                    "KILL_SWITCH_RESET_REQUIRED"
                } else {
                    "CORE_EXITED"
                }
                .into())
            } else {
                Ok(())
            };
        };
        let _ = stop.send(true);
        self.result
            .recv_timeout(Duration::from_secs(38))
            .map_err(|_| "TUN_CLEANUP_FAILED")?
    }
}
impl Drop for Lease {
    fn drop(&mut self) {
        self.abandon();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn older_helper_is_not_reboot_safe_even_when_static_kill_switch_is_available() {
        let legacy = r#"{"type":"mihomo_availability","binary_sha256":"expected","available":true,"running":false,"both_available":true,"kill_switch_available":true}"#;
        let mut response: Response = serde_json::from_str(legacy).unwrap();
        assert_eq!(
            verified_capabilities(&response, "expected"),
            (true, true, false)
        );
        if let Response::MihomoAvailability {
            reboot_kill_switch_available,
            ..
        } = &mut response
        {
            *reboot_kill_switch_available = true;
        }
        assert_eq!(
            verified_capabilities(&response, "expected"),
            (true, true, true)
        );
        assert_eq!(
            verified_capabilities(&response, "different"),
            (false, false, false)
        );
    }
    #[test]
    fn repeated_explicit_disconnect_is_idempotent() {
        let (tx, mut rx) = tokio::sync::oneshot::channel();
        let (result_tx, result) = std::sync::mpsc::channel();
        result_tx.send(Ok(())).unwrap();
        let mut lease = Lease {
            stop: Some(tx),
            result,
            running: Arc::new(AtomicBool::new(true)),
            abandoned: false,
            kill_switch: true,
        };
        lease.stop().unwrap();
        assert_eq!(rx.try_recv(), Ok(true));
        lease.stop().unwrap();
    }
    #[test]
    fn abandoned_lease_cannot_later_claim_a_clean_disconnect() {
        let (tx, _rx) = tokio::sync::oneshot::channel();
        let (result_tx, result) = std::sync::mpsc::channel();
        result_tx.send(Ok(())).unwrap();
        let mut lease = Lease {
            stop: Some(tx),
            result,
            running: Arc::new(AtomicBool::new(true)),
            abandoned: false,
            kill_switch: true,
        };
        lease.abandon();
        assert_eq!(lease.stop().unwrap_err(), "KILL_SWITCH_RESET_REQUIRED");
    }
    #[test]
    fn dropping_a_lease_is_not_an_explicit_disconnect() {
        let (tx, mut rx) = tokio::sync::oneshot::channel();
        let (result_tx, result) = std::sync::mpsc::channel();
        result_tx.send(Ok(())).unwrap();
        drop(Lease {
            stop: Some(tx),
            result,
            running: Arc::new(AtomicBool::new(true)),
            abandoned: false,
            kill_switch: true,
        });
        assert_eq!(
            rx.try_recv(),
            Err(tokio::sync::oneshot::error::TryRecvError::Closed)
        );
    }
    #[tokio::test]
    async fn kernel_pid_rejects_a_substitute_pipe_server() {
        use tokio::net::windows::named_pipe::{ClientOptions, ServerOptions};
        let name = format!(r"\\.\pipe\nimbo-auth-test-{}", uuid::Uuid::new_v4());
        let server = ServerOptions::new()
            .first_pipe_instance(true)
            .create(&name)
            .unwrap();
        let client = ClientOptions::new().open(name).unwrap();
        server.connect().await.unwrap();
        assert_eq!(
            trust::authenticate_server(&client).unwrap_err(),
            "HELPER_AUTH_FAILED"
        );
    }
    #[tokio::test]
    async fn cancelled_owned_start_closes_actual_pipe() {
        use tokio::io::AsyncReadExt;
        use tokio::net::windows::named_pipe::{ClientOptions, ServerOptions};
        let name = format!(r"\\.\pipe\nimbo-cancel-test-{}", uuid::Uuid::new_v4());
        let mut server = ServerOptions::new()
            .first_pipe_instance(true)
            .create(&name)
            .unwrap();
        let peer = tokio::spawn(async move {
            server.connect().await.unwrap();
            trust::read_frame(&mut server).await.unwrap();
            let mut b = [0u8; 1];
            let result = tokio::time::timeout(Duration::from_secs(2), server.read(&mut b))
                .await
                .unwrap();
            assert!(
                matches!(result, Ok(0)) || result.is_err(),
                "owning pipe survived cancelled future"
            );
        });
        let operation = async move {
            let mut pipe = ClientOptions::new().open(name).unwrap();
            call(&mut pipe, Command::MihomoStatus, Duration::from_secs(30)).await
        };
        assert!(tokio::time::timeout(Duration::from_millis(25), operation)
            .await
            .is_err());
        peer.await.unwrap();
    }
}
