use serde::Deserialize;

use crate::model::{Network, Protocol, Security, Server, StreamSettings, VmessConfig};
use crate::parser::{ParseError, b64_decode_str, fingerprint, parse_port};

#[derive(Debug, Deserialize)]
struct VmessRaw {
    #[serde(default)]
    v: serde_json::Value,
    #[serde(default)]
    ps: String,
    add: String,
    #[serde(default)]
    port: serde_json::Value,
    id: String,
    #[serde(default)]
    aid: serde_json::Value,
    #[serde(default)]
    scy: String,
    #[serde(default)]
    net: String,
    #[serde(default, rename = "type")]
    header_type: String,
    #[serde(default)]
    host: String,
    #[serde(default)]
    path: String,
    #[serde(default)]
    tls: String,
    #[serde(default)]
    sni: String,
    #[serde(default)]
    alpn: String,
    #[serde(default)]
    fp: String,
}

pub fn parse(input: &str) -> Result<Server, ParseError> {
    let payload = input
        .strip_prefix("vmess://")
        .ok_or_else(|| ParseError::UnsupportedScheme("expected vmess://".into()))?;

    let json = b64_decode_str(payload)?;
    let raw: VmessRaw =
        serde_json::from_str(&json).map_err(|e| ParseError::InvalidJson(e.to_string()))?;

    let _ = raw.v;
    let port = match &raw.port {
        serde_json::Value::Number(n) => n
            .as_u64()
            .and_then(|p| u16::try_from(p).ok())
            .ok_or_else(|| ParseError::InvalidPort(raw.port.to_string()))?,
        serde_json::Value::String(s) => parse_port(s)?,
        _ => return Err(ParseError::MissingField("port")),
    };
    let alter_id = match &raw.aid {
        serde_json::Value::Number(n) => n.as_u64().unwrap_or(0) as u32,
        serde_json::Value::String(s) => s.parse::<u32>().unwrap_or(0),
        _ => 0,
    };

    let stream = StreamSettings {
        network: Network::from_xray_str(&raw.net).unwrap_or_default(),
        security: Security::from_xray_str(&raw.tls).unwrap_or_default(),
        host: opt(raw.host),
        path: opt(raw.path),
        sni: opt(raw.sni),
        fingerprint: opt(raw.fp),
        alpn: if raw.alpn.is_empty() {
            None
        } else {
            Some(raw.alpn.split(',').map(|s| s.to_string()).collect())
        },
        header_type: opt(raw.header_type),
        ..Default::default()
    };

    let security_field = if raw.scy.is_empty() {
        "auto".into()
    } else {
        raw.scy
    };

    let name = if raw.ps.is_empty() {
        format!("vmess-{}:{}", raw.add, port)
    } else {
        raw.ps
    };

    let cfg = VmessConfig {
        address: raw.add,
        port,
        uuid: raw.id,
        alter_id,
        security: security_field,
        stream,
    };

    Ok(Server {
        id: fingerprint(input),
        name,
        protocol: Protocol::Vmess(cfg),
    })
}

fn opt(s: String) -> Option<String> {
    if s.is_empty() {
        None
    } else {
        Some(s)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use base64::Engine;

    fn make_vmess(payload: &str) -> String {
        format!(
            "vmess://{}",
            base64::engine::general_purpose::STANDARD.encode(payload)
        )
    }

    #[test]
    fn parses_vmess_ws_tls() {
        let json = r#"{"v":"2","ps":"My Server","add":"1.2.3.4","port":"443","id":"uuid-1234","aid":"0","scy":"auto","net":"ws","type":"none","host":"cdn.tld","path":"/api","tls":"tls","sni":"cdn.tld"}"#;
        let url = make_vmess(json);
        let s = parse(&url).unwrap();
        assert_eq!(s.name, "My Server");
        match s.protocol {
            Protocol::Vmess(v) => {
                assert_eq!(v.address, "1.2.3.4");
                assert_eq!(v.port, 443);
                assert_eq!(v.uuid, "uuid-1234");
                assert_eq!(v.alter_id, 0);
                assert_eq!(v.security, "auto");
                assert_eq!(v.stream.network, Network::Ws);
                assert_eq!(v.stream.security, Security::Tls);
                assert_eq!(v.stream.host.as_deref(), Some("cdn.tld"));
                assert_eq!(v.stream.path.as_deref(), Some("/api"));
            }
            _ => panic!("wrong protocol"),
        }
    }

    #[test]
    fn handles_numeric_port() {
        let json = r#"{"ps":"X","add":"h","port":8443,"id":"u","aid":0,"net":"tcp","tls":""}"#;
        let url = make_vmess(json);
        let s = parse(&url).unwrap();
        match s.protocol {
            Protocol::Vmess(v) => assert_eq!(v.port, 8443),
            _ => panic!("wrong protocol"),
        }
    }

    #[test]
    fn rejects_non_vmess() {
        let err = parse("vless://foo").unwrap_err();
        assert!(matches!(err, ParseError::UnsupportedScheme(_)));
    }
}
