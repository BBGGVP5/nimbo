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
        protocol: Protocol::Trojan(cfg),
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
}
