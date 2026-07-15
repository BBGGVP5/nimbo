use serde::{Deserialize, Serialize};

pub const PIPE_NAME: &str = r"\\.\pipe\nimbo-svc";
pub const PROTOCOL_VERSION: u32 = 1;

pub const FRAME_LENGTH_BYTES: usize = 4;
pub const FRAME_MAX_BYTES: u32 = 2 * 1024 * 1024;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Command {
    Ping,
    GetStatus,
    Connect { server_id: String },
    Disconnect,
    SetAppProxyRules { rules: Vec<AppProxyRule> },
    ReloadConfig,
    KillProcesses { pids: Vec<u32> },
    Shutdown,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Response {
    Pong {
        service_version: String,
        protocol: u32,
    },
    Status(ServiceStatus),
    KillReport(KillReport),
    Ok,
    Error {
        code: ErrorCode,
        message: String,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServiceStatus {
    pub state: ConnectionState,
    pub active_server: Option<String>,
    pub uptime_seconds: u64,
    pub bytes_up: u64,
    pub bytes_down: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct KillReport {
    pub killed: Vec<u32>,
    pub failed: Vec<KillFailure>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct KillFailure {
    pub pid: u32,
    pub message: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct AppProxyRule {
    pub id: String,
    pub name: String,
    pub executable_path: String,
    pub mode: AppProxyMode,
    pub enabled: bool,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum AppProxyMode {
    Proxy,
    Direct,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
    Error,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ErrorCode {
    UnknownCommand,
    InvalidPayload,
    ServerNotFound,
    CoreFailed,
    TunFailed,
    PermissionDenied,
    FrameTooLarge,
    Internal,
}

#[derive(Debug, thiserror::Error)]
pub enum IpcError {
    #[error("serialization failed: {0}")]
    Serde(#[from] serde_json::Error),
    #[error("io: {0}")]
    Io(#[from] std::io::Error),
    #[error("frame too large: {0} bytes")]
    FrameTooLarge(u32),
    #[error("connection closed before frame completed")]
    UnexpectedEof,
}

pub mod framing {
    use std::io::{Read, Write};

    use super::{IpcError, FRAME_LENGTH_BYTES, FRAME_MAX_BYTES};

    pub fn write_frame<W: Write>(writer: &mut W, payload: &[u8]) -> Result<(), IpcError> {
        let length = u32::try_from(payload.len()).map_err(|_| IpcError::FrameTooLarge(u32::MAX))?;
        if length > FRAME_MAX_BYTES {
            return Err(IpcError::FrameTooLarge(length));
        }
        writer.write_all(&length.to_be_bytes())?;
        writer.write_all(payload)?;
        writer.flush()?;
        Ok(())
    }

    pub fn read_frame<R: Read>(reader: &mut R) -> Result<Vec<u8>, IpcError> {
        let mut header = [0u8; FRAME_LENGTH_BYTES];
        read_exact_or_eof(reader, &mut header)?;
        let length = u32::from_be_bytes(header);
        if length > FRAME_MAX_BYTES {
            return Err(IpcError::FrameTooLarge(length));
        }
        let mut payload = vec![0u8; length as usize];
        if length > 0 {
            read_exact_or_eof(reader, &mut payload)?;
        }
        Ok(payload)
    }

    fn read_exact_or_eof<R: Read>(reader: &mut R, buf: &mut [u8]) -> Result<(), IpcError> {
        let mut filled = 0;
        while filled < buf.len() {
            match reader.read(&mut buf[filled..])? {
                0 => return Err(IpcError::UnexpectedEof),
                n => filled += n,
            }
        }
        Ok(())
    }
}

pub fn encode_command(command: &Command) -> Result<Vec<u8>, IpcError> {
    Ok(serde_json::to_vec(command)?)
}

pub fn decode_command(bytes: &[u8]) -> Result<Command, IpcError> {
    Ok(serde_json::from_slice(bytes)?)
}

pub fn encode_response(response: &Response) -> Result<Vec<u8>, IpcError> {
    Ok(serde_json::to_vec(response)?)
}

pub fn decode_response(bytes: &[u8]) -> Result<Response, IpcError> {
    Ok(serde_json::from_slice(bytes)?)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    #[test]
    fn ping_roundtrip() {
        let cmd = Command::Ping;
        let s = serde_json::to_string(&cmd).unwrap();
        assert_eq!(s, r#"{"type":"ping"}"#);
        let back: Command = serde_json::from_str(&s).unwrap();
        assert!(matches!(back, Command::Ping));
    }

    #[test]
    fn connect_roundtrip() {
        let cmd = Command::Connect {
            server_id: "srv-1".into(),
        };
        let s = serde_json::to_string(&cmd).unwrap();
        let back: Command = serde_json::from_str(&s).unwrap();
        match back {
            Command::Connect { server_id } => assert_eq!(server_id, "srv-1"),
            _ => panic!("wrong variant"),
        }
    }

    #[test]
    fn status_response_roundtrip() {
        let resp = Response::Status(ServiceStatus {
            state: ConnectionState::Connected,
            active_server: Some("srv-1".into()),
            uptime_seconds: 42,
            bytes_up: 1024,
            bytes_down: 2048,
        });
        let s = serde_json::to_string(&resp).unwrap();
        let back: Response = serde_json::from_str(&s).unwrap();
        match back {
            Response::Status(st) => {
                assert_eq!(st.state, ConnectionState::Connected);
                assert_eq!(st.bytes_down, 2048);
            }
            _ => panic!("wrong variant"),
        }
    }

    #[test]
    fn kill_processes_roundtrip() {
        let cmd = Command::KillProcesses {
            pids: vec![1, 2, 3],
        };
        let bytes = encode_command(&cmd).unwrap();
        let back = decode_command(&bytes).unwrap();
        match back {
            Command::KillProcesses { pids } => assert_eq!(pids, vec![1, 2, 3]),
            _ => panic!("wrong variant"),
        }
    }

    #[test]
    fn kill_report_roundtrip() {
        let resp = Response::KillReport(KillReport {
            killed: vec![10],
            failed: vec![KillFailure {
                pid: 20,
                message: "denied".into(),
            }],
        });
        let bytes = encode_response(&resp).unwrap();
        let back = decode_response(&bytes).unwrap();
        match back {
            Response::KillReport(report) => {
                assert_eq!(report.killed, vec![10]);
                assert_eq!(report.failed.len(), 1);
                assert_eq!(report.failed[0].pid, 20);
            }
            _ => panic!("wrong variant"),
        }
    }

    #[test]
    fn framing_roundtrip() {
        let payload = b"hello world";
        let mut buffer: Vec<u8> = Vec::new();
        framing::write_frame(&mut buffer, payload).unwrap();
        assert_eq!(buffer.len(), payload.len() + FRAME_LENGTH_BYTES);

        let mut cursor = Cursor::new(buffer);
        let decoded = framing::read_frame(&mut cursor).unwrap();
        assert_eq!(decoded, payload);
    }
}
