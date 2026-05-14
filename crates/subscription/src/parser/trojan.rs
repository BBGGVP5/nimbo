use std::collections::HashMap;
use url::Url;

use crate::model::{Network, Protocol, Security, Server, StreamSettings, TrojanConfig};
use crate::parser::{ParseError, fingerprint, url_decode};

pub fn parse(input: &str) -> Result<Server, ParseError> {
    let url = Url::parse(input).map_err(|e| ParseError::InvalidUrl(e.to_string()))?;
    if url.scheme() != "trojan" {
        return Err(ParseError::UnsupportedScheme(url.scheme().to_string()));
    }

    let password = url_decode(url.username());
    if password.is_empty() {
        return Err(ParseError::MissingField("password"));
    }
    let host = url.host_str().ok_or(ParseError::MissingField("host"))?;
    let port = url.port().ok_or(ParseError::MissingField("port"))?;

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
            .unwrap_or(Security::Tls),
        host: q.get("host").cloned(),
        path: q.get("path").map(|s| url_decode(s)),
        sni: q.get("sni").cloned().or_else(|| q.get("peer").cloned()),
        fingerprint: q.get("fp").cloned(),
        alpn: q
            .get("alpn")
            .map(|s| s.split(',').map(|x| x.to_string()).collect()),
        header_type: q.get("headerType").cloned(),
        service_name: q.get("serviceName").map(|s| url_decode(s)),
        ..Default::default()
    };

    let cfg = TrojanConfig {
        address: host.to_string(),
        port,
        password,
        stream,
    };

    let name = url
        .fragment()
        .map(|s| url_decode(s))
        .unwrap_or_else(|| format!("trojan-{host}:{port}"));

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
        protocol: Protocol::Trojan(cfg),
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
    fn parses_basic_trojan() {
        let url = "trojan://my-password@srv.tld:443?security=tls&sni=srv.tld&type=tcp#prod";
        let s = parse(url).unwrap();
        assert_eq!(s.name, "prod");
        match s.protocol {
            Protocol::Trojan(t) => {
                assert_eq!(t.address, "srv.tld");
                assert_eq!(t.port, 443);
                assert_eq!(t.password, "my-password");
                assert_eq!(t.stream.network, Network::Tcp);
                assert_eq!(t.stream.security, Security::Tls);
            }
            _ => panic!("wrong protocol"),
        }
    }

    #[test]
    fn url_decodes_password_and_name() {
        let url = "trojan://p%40ss%21@h.tld:443#%D1%84%D1%80%D0%B0%D0%BD%D0%BA%D1%83%D1%80%D1%82";
        let s = parse(url).unwrap();
        assert_eq!(s.name, "франкурт");
        match s.protocol {
            Protocol::Trojan(t) => assert_eq!(t.password, "p@ss!"),
            _ => panic!("wrong"),
        }
    }

    #[test]
    fn defaults_to_tls() {
        let url = "trojan://pwd@h.tld:443#x";
        let s = parse(url).unwrap();
        match s.protocol {
            Protocol::Trojan(t) => assert_eq!(t.stream.security, Security::Tls),
            _ => panic!(),
        }
    }

    #[test]
    fn parses_custom_server_metadata() {
        let url = "trojan://pwd@h.tld:443?serverDescription=Custom%20desc&hostUuid=host-1&xrayJsonTemplateUuid=tpl-1#x";
        let s = parse(url).unwrap();
        assert_eq!(s.server_description.as_deref(), Some("Custom desc"));
        assert_eq!(s.host_uuid.as_deref(), Some("host-1"));
        assert_eq!(s.xray_json_template_uuid.as_deref(), Some("tpl-1"));
    }
}
