use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

use nimbo_subscription::{
    Protocol, Server, ShadowsocksConfig, TrojanConfig, VlessConfig, VmessConfig,
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
