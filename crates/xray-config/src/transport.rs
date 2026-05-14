use serde_json::{Value, json};

use nimbo_subscription::{Network, Security, StreamSettings};

pub fn build_stream_settings(stream: &StreamSettings) -> Value {
    let network_key = network_str(stream.network);
    let security_key = security_str(stream.security);

    let mut obj = serde_json::Map::new();
    obj.insert("network".into(), Value::String(network_key.into()));
    obj.insert("security".into(), Value::String(security_key.into()));

    match stream.security {
        Security::Tls => {
            obj.insert("tlsSettings".into(), build_tls(stream));
        }
        Security::Reality => {
            obj.insert("realitySettings".into(), build_reality(stream));
        }
        Security::None => {}
    }

    let net_settings_key = format!("{}Settings", network_key);
    if let Some(transport_settings) = build_transport_settings(stream) {
        obj.insert(net_settings_key, transport_settings);
    }

    Value::Object(obj)
}

fn network_str(net: Network) -> &'static str {
    match net {
        Network::Tcp => "tcp",
        Network::Ws => "ws",
        Network::Grpc => "grpc",
        Network::H2 => "http",
        Network::Xhttp => "xhttp",
        Network::HttpUpgrade => "httpupgrade",
        Network::Quic => "quic",
        Network::Kcp => "mkcp",
    }
}

fn security_str(sec: Security) -> &'static str {
    match sec {
        Security::None => "none",
        Security::Tls => "tls",
        Security::Reality => "reality",
    }
}

fn build_tls(stream: &StreamSettings) -> Value {
    let mut tls = serde_json::Map::new();
    if let Some(sni) = &stream.sni {
        tls.insert("serverName".into(), Value::String(sni.clone()));
    }
    if let Some(fp) = &stream.fingerprint {
        tls.insert("fingerprint".into(), Value::String(fp.clone()));
    }
    if let Some(alpn) = &stream.alpn {
        if !alpn.is_empty() {
            tls.insert(
                "alpn".into(),
                Value::Array(alpn.iter().cloned().map(Value::String).collect()),
            );
        }
    }
    Value::Object(tls)
}

fn build_reality(stream: &StreamSettings) -> Value {
    let mut r = serde_json::Map::new();
    if let Some(sni) = &stream.sni {
        r.insert("serverName".into(), Value::String(sni.clone()));
    }
    if let Some(fp) = &stream.fingerprint {
        r.insert("fingerprint".into(), Value::String(fp.clone()));
    }
    if let Some(pbk) = &stream.public_key {
        r.insert("publicKey".into(), Value::String(pbk.clone()));
    }
    if let Some(sid) = &stream.short_id {
        r.insert("shortId".into(), Value::String(sid.clone()));
    }
    if let Some(spx) = &stream.spider_x {
        r.insert("spiderX".into(), Value::String(spx.clone()));
    }
    if let Some(alpn) = &stream.alpn {
        if !alpn.is_empty() {
            r.insert(
                "alpn".into(),
                Value::Array(alpn.iter().cloned().map(Value::String).collect()),
            );
        }
    }
    Value::Object(r)
}

fn build_transport_settings(stream: &StreamSettings) -> Option<Value> {
    match stream.network {
        Network::Tcp => Some(build_tcp(stream)),
        Network::Ws => Some(build_ws(stream)),
        Network::Grpc => Some(build_grpc(stream)),
        Network::H2 => Some(build_http(stream)),
        Network::Xhttp => Some(build_xhttp(stream)),
        Network::HttpUpgrade => Some(build_httpupgrade(stream)),
        Network::Quic => Some(build_quic(stream)),
        Network::Kcp => Some(build_kcp(stream)),
    }
}

fn build_tcp(stream: &StreamSettings) -> Value {
    if stream.header_type.as_deref() == Some("http") {
        let mut header = serde_json::Map::new();
        header.insert("type".into(), Value::String("http".into()));
        if let Some(host) = &stream.host {
            header.insert(
                "request".into(),
                json!({
                    "headers": { "Host": [host] }
                }),
            );
        }
        json!({ "header": Value::Object(header) })
    } else {
        json!({ "header": { "type": "none" } })
    }
}

fn build_ws(stream: &StreamSettings) -> Value {
    let mut ws = serde_json::Map::new();
    if let Some(path) = &stream.path {
        ws.insert("path".into(), Value::String(path.clone()));
    }
    if let Some(host) = &stream.host {
        ws.insert("headers".into(), json!({ "Host": host }));
    }
    Value::Object(ws)
}

fn build_grpc(stream: &StreamSettings) -> Value {
    let mut g = serde_json::Map::new();
    if let Some(svc) = &stream.service_name {
        g.insert("serviceName".into(), Value::String(svc.clone()));
    } else if let Some(path) = &stream.path {
        g.insert("serviceName".into(), Value::String(path.clone()));
    }
    let multi = stream.mode.as_deref() == Some("multi");
    g.insert("multiMode".into(), Value::Bool(multi));
    Value::Object(g)
}

fn build_http(stream: &StreamSettings) -> Value {
    let mut h = serde_json::Map::new();
    if let Some(path) = &stream.path {
        h.insert("path".into(), Value::String(path.clone()));
    }
    if let Some(host) = &stream.host {
        h.insert(
            "host".into(),
            Value::Array(host.split(',').map(|s| Value::String(s.trim().into())).collect()),
        );
    }
    Value::Object(h)
}

fn build_xhttp(stream: &StreamSettings) -> Value {
    let mut x = serde_json::Map::new();
    if let Some(path) = &stream.path {
        x.insert("path".into(), Value::String(path.clone()));
    }
    if let Some(host) = &stream.host {
        x.insert("host".into(), Value::String(host.clone()));
    }
    if let Some(mode) = &stream.mode {
        x.insert("mode".into(), Value::String(mode.clone()));
    }
    if let Some(extra) = &stream.extra {
        if let Ok(extra_value) = serde_json::from_str::<Value>(extra) {
            x.insert("extra".into(), extra_value);
        }
    }
    Value::Object(x)
}

fn build_httpupgrade(stream: &StreamSettings) -> Value {
    let mut h = serde_json::Map::new();
    if let Some(path) = &stream.path {
        h.insert("path".into(), Value::String(path.clone()));
    }
    if let Some(host) = &stream.host {
        h.insert("host".into(), Value::String(host.clone()));
    }
    Value::Object(h)
}

fn build_quic(_stream: &StreamSettings) -> Value {
    json!({
        "security": "none",
        "key": "",
        "header": { "type": "none" }
    })
}

fn build_kcp(stream: &StreamSettings) -> Value {
    json!({
        "header": {
            "type": stream.header_type.clone().unwrap_or_else(|| "none".into())
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use nimbo_subscription::{Network, Security, StreamSettings};

    #[test]
    fn ws_with_path_and_host() {
        let s = StreamSettings {
            network: Network::Ws,
            security: Security::Tls,
            path: Some("/ws".into()),
            host: Some("cdn.tld".into()),
            sni: Some("cdn.tld".into()),
            ..Default::default()
        };
        let v = build_stream_settings(&s);
        assert_eq!(v["network"], "ws");
        assert_eq!(v["security"], "tls");
        assert_eq!(v["wsSettings"]["path"], "/ws");
        assert_eq!(v["wsSettings"]["headers"]["Host"], "cdn.tld");
        assert_eq!(v["tlsSettings"]["serverName"], "cdn.tld");
    }

    #[test]
    fn reality_xhttp() {
        let s = StreamSettings {
            network: Network::Xhttp,
            security: Security::Reality,
            sni: Some("microsoft.com".into()),
            fingerprint: Some("chrome".into()),
            public_key: Some("KEY".into()),
            short_id: Some("01ab".into()),
            mode: Some("auto".into()),
            ..Default::default()
        };
        let v = build_stream_settings(&s);
        assert_eq!(v["network"], "xhttp");
        assert_eq!(v["security"], "reality");
        assert_eq!(v["realitySettings"]["serverName"], "microsoft.com");
        assert_eq!(v["realitySettings"]["publicKey"], "KEY");
        assert_eq!(v["realitySettings"]["shortId"], "01ab");
        assert_eq!(v["realitySettings"]["fingerprint"], "chrome");
        assert_eq!(v["xhttpSettings"]["mode"], "auto");
    }

    #[test]
    fn grpc_service_name() {
        let s = StreamSettings {
            network: Network::Grpc,
            security: Security::Tls,
            service_name: Some("grpc-service".into()),
            mode: Some("multi".into()),
            sni: Some("h.tld".into()),
            ..Default::default()
        };
        let v = build_stream_settings(&s);
        assert_eq!(v["network"], "grpc");
        assert_eq!(v["grpcSettings"]["serviceName"], "grpc-service");
        assert_eq!(v["grpcSettings"]["multiMode"], true);
    }

    #[test]
    fn h2_renders_as_http() {
        let s = StreamSettings {
            network: Network::H2,
            security: Security::Tls,
            path: Some("/h2".into()),
            host: Some("a.tld,b.tld".into()),
            sni: Some("a.tld".into()),
            ..Default::default()
        };
        let v = build_stream_settings(&s);
        assert_eq!(v["network"], "http");
        assert_eq!(v["httpSettings"]["path"], "/h2");
        assert_eq!(v["httpSettings"]["host"][0], "a.tld");
        assert_eq!(v["httpSettings"]["host"][1], "b.tld");
    }
}
