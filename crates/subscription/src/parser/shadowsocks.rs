use crate::model::{Protocol, Server, ShadowsocksConfig};
use crate::parser::{ParseError, b64_decode_str, fingerprint, parse_port, url_decode};

pub fn parse(input: &str) -> Result<Server, ParseError> {
    let payload = input
        .strip_prefix("ss://")
        .ok_or_else(|| ParseError::UnsupportedScheme("expected ss://".into()))?;

    let (head, name) = match payload.split_once('#') {
        Some((h, n)) => (h, Some(url_decode(n))),
        None => (payload, None),
    };

    let (cred_part, host_part) = if let Some((c, h)) = head.split_once('@') {
        (c.to_string(), h.to_string())
    } else {
        let decoded = b64_decode_str(head)?;
        match decoded.split_once('@') {
            Some((c, h)) => (c.to_string(), h.to_string()),
            None => return Err(ParseError::InvalidUrl("ss: missing @".into())),
        }
    };

    let cred_decoded = if cred_part.contains(':') {
        cred_part
    } else {
        b64_decode_str(&cred_part)?
    };
    let (method, password) = cred_decoded
        .split_once(':')
        .ok_or_else(|| ParseError::InvalidUrl("ss: missing method:password".into()))?;
    let method = method.to_string();
    let password = password.to_string();

    let host_part = host_part
        .split('?')
        .next()
        .unwrap_or(&host_part)
        .to_string();
    let (host, port) = host_part
        .rsplit_once(':')
        .ok_or_else(|| ParseError::InvalidUrl("ss: missing host:port".into()))?;
    let port = parse_port(port)?;
    let host = host.trim_start_matches('[').trim_end_matches(']');

    let cfg = ShadowsocksConfig {
        address: host.to_string(),
        port,
        method,
        password,
    };

    let name = name.unwrap_or_else(|| format!("ss-{host}:{port}"));

    Ok(Server {
        id: fingerprint(input),
        name,
        protocol: Protocol::Shadowsocks(cfg),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use base64::Engine;

    #[test]
    fn parses_plain_userinfo() {
        let url = "ss://aes-256-gcm:secret-pass@1.2.3.4:8388#node-a";
        let s = parse(url).unwrap();
        assert_eq!(s.name, "node-a");
        match s.protocol {
            Protocol::Shadowsocks(c) => {
                assert_eq!(c.address, "1.2.3.4");
                assert_eq!(c.port, 8388);
                assert_eq!(c.method, "aes-256-gcm");
                assert_eq!(c.password, "secret-pass");
            }
            _ => panic!(),
        }
    }

    #[test]
    fn parses_legacy_full_base64() {
        let inner = "aes-256-gcm:secret-pass@1.2.3.4:8388";
        let url = format!(
            "ss://{}#legacy",
            base64::engine::general_purpose::STANDARD.encode(inner)
        );
        let s = parse(&url).unwrap();
        match s.protocol {
            Protocol::Shadowsocks(c) => {
                assert_eq!(c.method, "aes-256-gcm");
                assert_eq!(c.password, "secret-pass");
                assert_eq!(c.port, 8388);
            }
            _ => panic!(),
        }
    }

    #[test]
    fn parses_userinfo_base64_at_host_plain() {
        let userinfo = "aes-256-gcm:secret-pass";
        let url = format!(
            "ss://{}@1.2.3.4:8388#mixed",
            base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(userinfo)
        );
        let s = parse(&url).unwrap();
        match s.protocol {
            Protocol::Shadowsocks(c) => {
                assert_eq!(c.method, "aes-256-gcm");
                assert_eq!(c.password, "secret-pass");
            }
            _ => panic!(),
        }
    }
}
