use serde::{Deserialize, Serialize};

use crate::userinfo::SubscriptionInfo;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Subscription {
    pub url: String,
    pub name: Option<String>,
    #[serde(default)]
    pub meta: SubscriptionMeta,
    pub servers: Vec<Server>,
    pub info: Option<SubscriptionInfo>,
    pub fetched_at: i64,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct SubscriptionMeta {
    pub description: Option<String>,
    pub support_url: Option<String>,
    pub website_url: Option<String>,
    pub show_on_home: Option<bool>,
    pub update_interval_minutes: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Server {
    pub id: String,
    pub name: String,
    #[serde(default, alias = "serverDescription", alias = "server-description")]
    pub server_description: Option<String>,
    #[serde(default, alias = "hostUuid", alias = "host-uuid")]
    pub host_uuid: Option<String>,
    #[serde(default, rename = "xrayJsonTemplateUuid", alias = "xray_json_template_uuid")]
    pub xray_json_template_uuid: Option<String>,
    pub protocol: Protocol,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum Protocol {
    Vless(VlessConfig),
    Vmess(VmessConfig),
    Trojan(TrojanConfig),
    Shadowsocks(ShadowsocksConfig),
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VlessConfig {
    pub address: String,
    pub port: u16,
    pub uuid: String,
    pub flow: Option<String>,
    #[serde(default = "default_encryption")]
    pub encryption: String,
    pub stream: StreamSettings,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VmessConfig {
    pub address: String,
    pub port: u16,
    pub uuid: String,
    #[serde(default)]
    pub alter_id: u32,
    #[serde(default = "default_vmess_security")]
    pub security: String,
    pub stream: StreamSettings,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrojanConfig {
    pub address: String,
    pub port: u16,
    pub password: String,
    pub stream: StreamSettings,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ShadowsocksConfig {
    pub address: String,
    pub port: u16,
    pub method: String,
    pub password: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct StreamSettings {
    pub network: Network,
    pub security: Security,
    pub host: Option<String>,
    pub path: Option<String>,
    pub sni: Option<String>,
    pub fingerprint: Option<String>,
    pub alpn: Option<Vec<String>>,
    pub public_key: Option<String>,
    pub short_id: Option<String>,
    pub spider_x: Option<String>,
    pub mode: Option<String>,
    pub extra: Option<String>,
    pub header_type: Option<String>,
    pub service_name: Option<String>,
}

#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum Network {
    #[default]
    Tcp,
    Ws,
    Grpc,
    H2,
    Xhttp,
    HttpUpgrade,
    Quic,
    Kcp,
}

impl Network {
    pub fn from_xray_str(s: &str) -> Option<Self> {
        Some(match s.to_ascii_lowercase().as_str() {
            "tcp" | "raw" | "" => Network::Tcp,
            "ws" | "websocket" => Network::Ws,
            "grpc" => Network::Grpc,
            "h2" | "http" | "http/2" => Network::H2,
            "xhttp" | "splithttp" => Network::Xhttp,
            "httpupgrade" => Network::HttpUpgrade,
            "quic" => Network::Quic,
            "kcp" | "mkcp" => Network::Kcp,
            _ => return None,
        })
    }
}

#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum Security {
    #[default]
    None,
    Tls,
    Reality,
}

impl Security {
    pub fn from_xray_str(s: &str) -> Option<Self> {
        Some(match s.to_ascii_lowercase().as_str() {
            "" | "none" => Security::None,
            "tls" => Security::Tls,
            "reality" => Security::Reality,
            _ => return None,
        })
    }
}

fn default_encryption() -> String {
    "none".into()
}

fn default_vmess_security() -> String {
    "auto".into()
}
