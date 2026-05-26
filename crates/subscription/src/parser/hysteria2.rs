use std::collections::HashMap;

use url::Url;

use crate::model::{Hysteria2Config, Protocol, Server};
use crate::parser::{ParseError, fingerprint, url_decode};

pub fn parse(input: &str) -> Result<Server, ParseError> {
    let url = Url::parse(input).map_err(|e| ParseError::InvalidUrl(e.to_string()))?;
    if url.scheme() != "hysteria2" && url.scheme() != "hy2" {
        return Err(ParseError::UnsupportedScheme(url.scheme().to_string()));
    }

    let host = url.host_str().ok_or(ParseError::MissingField("host"))?;
    let port = url.port_or_known_default().unwrap_or(443);
    let q: HashMap<String, String> = url.query_pairs().into_owned().collect();

    let password = q
        .get("auth")
        .or_else(|| q.get("password"))
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
        .or_else(|| {
            let mut auth = url_decode(url.username()).trim().to_string();
            if let Some(password) = url.password() {
                let password = url_decode(password);
                auth = if auth.is_empty() {
                    password
                } else {
                    format!("{auth}:{password}")
                };
            }
            (!auth.is_empty()).then_some(auth)
        })
        .ok_or(ParseError::MissingField("auth"))?;

    let cfg = Hysteria2Config {
        address: host.to_string(),
        port,
        password,
        sni: query_param(&q, &["sni", "peer"]),
        alpn: q
            .get("alpn")
            .map(|value| value.split(',').map(|item| item.trim().to_string()).filter(|item| !item.is_empty()).collect())
            .filter(|items: &Vec<String>| !items.is_empty()),
        insecure: q
            .get("insecure")
            .or_else(|| q.get("allowInsecure"))
            .map(|value| matches!(value.as_str(), "1" | "true" | "TRUE" | "True"))
            .unwrap_or(false),
        obfs: query_param(&q, &["obfs"]),
        obfs_password: query_param(&q, &["obfs-password", "obfs_password", "obfsPassword"]),
    };

    let name = url
        .fragment()
        .map(url_decode)
        .unwrap_or_else(|| format!("hysteria2-{host}:{port}"));

    Ok(Server {
        id: fingerprint(input),
        name,
        server_description: query_param(
            &q,
            &["serverDescription", "server_description", "server-description"],
        ),
        host_uuid: query_param(&q, &["hostUuid", "host_uuid", "host-uuid"]),
        xray_json_template_uuid: query_param(
            &q,
            &[
                "xrayJsonTemplateUuid",
                "xray_json_template_uuid",
                "xray-json-template-uuid",
            ],
        ),
        protocol: Protocol::Hysteria2(cfg),
    })
}

fn query_param(q: &HashMap<String, String>, keys: &[&str]) -> Option<String> {
    q.iter().find_map(|(key, value)| {
        let normalized = normalize_key(key);
        let matches = keys.iter().any(|wanted| normalize_key(wanted) == normalized);
        if matches {
            let trimmed = value.trim();
            (!trimmed.is_empty()).then(|| trimmed.to_string())
        } else {
            None
        }
    })
}

fn normalize_key(value: &str) -> String {
    value
        .chars()
        .filter(|ch| *ch != '-' && *ch != '_')
        .flat_map(char::to_lowercase)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_hysteria2_uri() {
        let s = parse("hysteria2://secret@example.com:443?sni=cdn.example.com&alpn=h3&insecure=1#hy2").unwrap();
        assert_eq!(s.name, "hy2");
        match s.protocol {
            Protocol::Hysteria2(h) => {
                assert_eq!(h.address, "example.com");
                assert_eq!(h.port, 443);
                assert_eq!(h.password, "secret");
                assert_eq!(h.sni.as_deref(), Some("cdn.example.com"));
                assert_eq!(h.alpn.as_deref(), Some(&["h3".to_string()][..]));
                assert!(h.insecure);
            }
            _ => panic!("wrong protocol"),
        }
    }
}
