use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

use nimbo_subscription::{
    Hysteria2Config, Protocol, Server, ShadowsocksConfig, TrojanConfig, VlessConfig, VmessConfig,
};

use crate::transport::build_stream_settings;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Outbound {
    pub tag: String,
    pub protocol: String,
    pub settings: Value,
    #[serde(skip_serializing_if = "Option::is_none", rename = "streamSettings")]
    pub stream_settings: Option<Value>,
}

pub fn server_to_outbound(server: &Server, tag: &str) -> Outbound {
    match &server.protocol {
        Protocol::Vless(cfg) => vless_outbound(tag, cfg),
        Protocol::Vmess(cfg) => vmess_outbound(tag, cfg),
        Protocol::Trojan(cfg) => trojan_outbound(tag, cfg),
        Protocol::Shadowsocks(cfg) => shadowsocks_outbound(tag, cfg),
        Protocol::Hysteria2(cfg) => hysteria2_outbound(tag, cfg),
    }
}

pub fn outbound_direct(tag: &str) -> Outbound {
    Outbound {
        tag: tag.into(),
        protocol: "freedom".into(),
        settings: json!({ "domainStrategy": "UseIP" }),
        stream_settings: None,
    }
}

pub fn outbound_block(tag: &str) -> Outbound {
    Outbound {
        tag: tag.into(),
        protocol: "blackhole".into(),
        settings: json!({ "response": { "type": "none" } }),
        stream_settings: None,
    }
}

fn vless_outbound(tag: &str, cfg: &VlessConfig) -> Outbound {
    let mut user = serde_json::Map::new();
    user.insert("id".into(), Value::String(cfg.uuid.clone()));
    user.insert("encryption".into(), Value::String(cfg.encryption.clone()));
    if let Some(flow) = &cfg.flow {
        if !flow.is_empty() {
            user.insert("flow".into(), Value::String(flow.clone()));
        }
    }

    let settings = json!({
        "vnext": [{
            "address": cfg.address,
            "port": cfg.port,
            "users": [Value::Object(user)]
        }]
    });

    Outbound {
        tag: tag.into(),
        protocol: "vless".into(),
        settings,
        stream_settings: Some(build_stream_settings(&cfg.stream)),
    }
}

fn vmess_outbound(tag: &str, cfg: &VmessConfig) -> Outbound {
    let user = json!({
        "id": cfg.uuid,
        "alterId": cfg.alter_id,
        "security": cfg.security,
    });
    let settings = json!({
        "vnext": [{
            "address": cfg.address,
            "port": cfg.port,
            "users": [user]
        }]
    });

    Outbound {
        tag: tag.into(),
        protocol: "vmess".into(),
        settings,
        stream_settings: Some(build_stream_settings(&cfg.stream)),
    }
}

fn trojan_outbound(tag: &str, cfg: &TrojanConfig) -> Outbound {
    let settings = json!({
        "servers": [{
            "address": cfg.address,
            "port": cfg.port,
            "password": cfg.password,
        }]
    });

    Outbound {
        tag: tag.into(),
        protocol: "trojan".into(),
        settings,
        stream_settings: Some(build_stream_settings(&cfg.stream)),
    }
}

fn shadowsocks_outbound(tag: &str, cfg: &ShadowsocksConfig) -> Outbound {
    let settings = json!({
        "servers": [{
            "address": cfg.address,
            "port": cfg.port,
            "method": cfg.method,
            "password": cfg.password,
        }]
    });

    Outbound {
        tag: tag.into(),
        protocol: "shadowsocks".into(),
        settings,
        stream_settings: None,
    }
}

fn hysteria2_outbound(tag: &str, cfg: &Hysteria2Config) -> Outbound {
    let settings = json!({
        "version": 2,
        "address": cfg.address,
        "port": cfg.port
    });

    let mut tls = serde_json::Map::new();
    if let Some(sni) = &cfg.sni {
        tls.insert("serverName".into(), Value::String(sni.clone()));
    }
    if let Some(alpn) = &cfg.alpn {
        if !alpn.is_empty() {
            tls.insert(
                "alpn".into(),
                Value::Array(alpn.iter().cloned().map(Value::String).collect()),
            );
        }
    }
    if cfg.insecure {
        tls.insert("allowInsecure".into(), Value::Bool(true));
    }

    let mut hysteria = serde_json::Map::new();
    hysteria.insert("version".into(), Value::Number(2.into()));
    hysteria.insert("auth".into(), Value::String(cfg.password.clone()));

    let mut stream = serde_json::Map::new();
    stream.insert("network".into(), Value::String("hysteria".into()));
    stream.insert("security".into(), Value::String("tls".into()));
    stream.insert("hysteriaSettings".into(), Value::Object(hysteria));
    stream.insert("tlsSettings".into(), Value::Object(tls));

    Outbound {
        tag: tag.into(),
        protocol: "hysteria".into(),
        settings,
        stream_settings: Some(Value::Object(stream)),
    }
}
