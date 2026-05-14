use std::collections::HashMap;
use url::Url;

use crate::model::{Network, Protocol, Security, Server, StreamSettings, VlessConfig};
use crate::parser::{ParseError, fingerprint, url_decode};

pub fn parse(input: &str) -> Result<Server, ParseError> {
    let url = Url::parse(input).map_err(|e| ParseError::InvalidUrl(e.to_string()))?;
    if url.scheme() != "vless" {
        return Err(ParseError::UnsupportedScheme(url.scheme().to_string()));
    }

    let uuid = url.username().to_string();
    if uuid.is_empty() {
        return Err(ParseError::MissingField("uuid"));
    }
    let host = url.host_str().ok_or(ParseError::MissingField("host"))?;
    let port = url
        .port_or_known_default()
        .ok_or(ParseError::MissingField("port"))?;

    let q: HashMap<String, String> = url.query_pairs().into_owned().collect();

    let stream = StreamSettings {
        network: q
            .get("type")
            .map(|s| s.as_str())
            .and_then(Network::from_xray_str)
            .unwrap_or_default(),
        security: q
            .get("security")
            .map(|s| s.as_str())
            .and_then(Security::from_xray_str)
            .unwrap_or_default(),
        host: q.get("host").cloned(),
        path: q.get("path").map(|s| url_decode(s)),
        sni: q.get("sni").cloned(),
        fingerprint: q.get("fp").cloned(),
        alpn: q
            .get("alpn")
            .map(|s| s.split(',').map(|x| x.to_string()).collect()),
        public_key: q.get("pbk").cloned(),
        short_id: q.get("sid").cloned(),
        spider_x: q.get("spx").cloned(),
        mode: q.get("mode").cloned(),
        extra: q.get("extra").map(|s| url_decode(s)),
        header_type: q.get("headerType").cloned(),
        service_name: q.get("serviceName").map(|s| url_decode(s)),
    };

    let cfg = VlessConfig {
        address: host.to_string(),
        port,
        uuid: uuid.clone(),
        flow: q.get("flow").cloned().filter(|s| !s.is_empty()),
        encryption: q
            .get("encryption")
            .cloned()
            .unwrap_or_else(|| "none".into()),
        stream,
    };

    let name = url
        .fragment()
        .map(|s| url_decode(s))
        .unwrap_or_else(|| format!("vless-{host}:{port}"));

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
        protocol: Protocol::Vless(cfg),
    })
}

fn query_param(q: &HashMap<String, String>, keys: &[&str]) -> Option<String> {
    q.iter().find_map(|(key, value)| {
        let normalized = key
            .chars()
            .filter(|ch| *ch != '-' && *ch != '_')
            .flat_map(char::to_lowercase)
            .collect::<String>();
        let matches = keys.iter().any(|wanted| {
            wanted
                .chars()
                .filter(|ch| *ch != '-' && *ch != '_')
                .flat_map(char::to_lowercase)
                .collect::<String>()
                == normalized
        });
        if matches {
            let trimmed = value.trim();
            (!trimmed.is_empty()).then(|| trimmed.to_string())
        } else {
            None
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_basic_vless_tcp_tls() {
        let url = "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@example.com:443?type=tcp&security=tls&sni=cdn.example.com#My-Server";
        let s = parse(url).unwrap();
        assert_eq!(s.name, "My-Server");
        match s.protocol {
            Protocol::Vless(v) => {
                assert_eq!(v.address, "example.com");
                assert_eq!(v.port, 443);
                assert_eq!(v.uuid, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
                assert_eq!(v.stream.network, Network::Tcp);
                assert_eq!(v.stream.security, Security::Tls);
                assert_eq!(v.stream.sni.as_deref(), Some("cdn.example.com"));
            }
            _ => panic!("wrong protocol"),
        }
    }

    #[test]
    fn parses_vless_reality_xhttp() {
        let url = "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@1.2.3.4:443?type=xhttp&security=reality&pbk=KEYKEYKEY&fp=chrome&sni=microsoft.com&sid=1234&flow=xtls-rprx-vision&mode=auto#srv1";
        let s = parse(url).unwrap();
        match s.protocol {
            Protocol::Vless(v) => {
                assert_eq!(v.flow.as_deref(), Some("xtls-rprx-vision"));
                assert_eq!(v.stream.network, Network::Xhttp);
                assert_eq!(v.stream.security, Security::Reality);
                assert_eq!(v.stream.public_key.as_deref(), Some("KEYKEYKEY"));
                assert_eq!(v.stream.short_id.as_deref(), Some("1234"));
                assert_eq!(v.stream.fingerprint.as_deref(), Some("chrome"));
                assert_eq!(v.stream.mode.as_deref(), Some("auto"));
            }
            _ => panic!("wrong protocol"),
        }
    }

    #[test]
    fn parses_vless_ws() {
        let url = "vless://uuid-here@host.tld:8443?type=ws&security=tls&path=%2Fws-path&host=cdn.host.tld#ws-srv";
        let s = parse(url).unwrap();
        match s.protocol {
            Protocol::Vless(v) => {
                assert_eq!(v.stream.network, Network::Ws);
                assert_eq!(v.stream.path.as_deref(), Some("/ws-path"));
                assert_eq!(v.stream.host.as_deref(), Some("cdn.host.tld"));
            }
            _ => panic!("wrong protocol"),
        }
    }

    #[test]
    fn parses_custom_server_metadata() {
        let url = "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@example.com:443?type=tcp&serverDescription=Custom%20desc&hostUuid=host-1&xrayJsonTemplateUuid=tpl-1#srv";
        let s = parse(url).unwrap();
        assert_eq!(s.server_description.as_deref(), Some("Custom desc"));
        assert_eq!(s.host_uuid.as_deref(), Some("host-1"));
        assert_eq!(s.xray_json_template_uuid.as_deref(), Some("tpl-1"));
    }

    #[test]
    fn rejects_non_vless() {
        let err = parse("vmess://foo").unwrap_err();
        assert!(matches!(err, ParseError::InvalidUrl(_) | ParseError::UnsupportedScheme(_)));
    }
}
