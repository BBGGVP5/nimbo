use serde::{Deserialize, Serialize};

pub const PIPE_NAME: &str = r"\\.\pipe\nimbo-svc";
pub const PROTOCOL_VERSION: u32 = 1;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Command {
    Ping,
    GetStatus,
    Connect { server_id: String },
    Disconnect,
    ReloadConfig,
    Shutdown,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Response {
    Pong { service_version: String, protocol: u32 },
    Status(ServiceStatus),
    Ok,
    Error { code: ErrorCode, message: String },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServiceStatus {
    pub state: ConnectionState,
    pub active_server: Option<String>,
    pub uptime_seconds: u64,
    pub bytes_up: u64,
    pub bytes_down: u64,
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
    Internal,
}

#[derive(Debug, thiserror::Error)]
pub enum IpcError {
    #[error("serialization failed: {0}")]
    Serde(#[from] serde_json::Error),
    #[error("io: {0}")]
    Io(#[from] std::io::Error),
}

#[cfg(test)]
mod tests {
    use super::*;

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
        let cmd = Command::Connect { server_id: "srv-1".into() };
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
}
