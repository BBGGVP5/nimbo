use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Inbound {
    pub tag: String,
    pub listen: String,
    pub port: u16,
    pub protocol: String,
    pub settings: Value,
    #[serde(skip_serializing_if = "Option::is_none", rename = "streamSettings")]
    pub stream_settings: Option<Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sniffing: Option<Value>,
}

impl Inbound {
    pub fn socks(port: u16) -> Self {
        Self::socks_with_options(port, None, false)
    }

    pub fn socks_with_options(
        port: u16,
        account: Option<(&str, &str)>,
        block_udp: bool,
    ) -> Self {
        let settings = if let Some((user, pass)) = account {
            json!({
                "auth": "password",
                "accounts": [{ "user": user, "pass": pass }],
                "udp": !block_udp,
                "ip": "127.0.0.1"
            })
        } else {
            json!({
                "auth": "noauth",
                "udp": !block_udp,
                "ip": "127.0.0.1"
            })
        };

        Self {
            tag: "socks-in".into(),
            listen: "127.0.0.1".into(),
            port,
            protocol: "socks".into(),
            settings,
            stream_settings: None,
            sniffing: Some(json!({
                "enabled": true,
                "destOverride": ["http", "tls", "quic"],
                "routeOnly": false
            })),
        }
    }

    pub fn http(port: u16) -> Self {
        Self {
            tag: "http-in".into(),
            listen: "127.0.0.1".into(),
            port,
            protocol: "http".into(),
            settings: json!({}),
            stream_settings: None,
            sniffing: Some(json!({
                "enabled": true,
                "destOverride": ["http", "tls", "quic"],
                "routeOnly": false
            })),
        }
    }

    pub fn api_dokodemo(port: u16) -> Self {
        Self {
            tag: "api".into(),
            listen: "127.0.0.1".into(),
            port,
            protocol: "dokodemo-door".into(),
            settings: json!({
                "address": "127.0.0.1"
            }),
            stream_settings: None,
            sniffing: None,
        }
    }
}
