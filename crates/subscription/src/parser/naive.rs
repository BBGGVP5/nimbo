use std::collections::HashMap;

use url::Url;

use crate::model::{NaiveConfig, NaiveTransport, Protocol, Server};
use crate::parser::{fingerprint, url_decode, ParseError};

pub fn parse(input: &str) -> Result<Server, ParseError> {
    let url = Url::parse(input).map_err(|e| ParseError::InvalidUrl(e.to_string()))?;
    let transport = match url.scheme() {
        "naive" | "naive+https" => NaiveTransport::Https,
        "naive+quic" => NaiveTransport::Quic,
        other => return Err(ParseError::UnsupportedScheme(other.to_string())),
    };
    let host = url.host_str().ok_or(ParseError::MissingField("host"))?;
    let port = url.port().unwrap_or(443);
    let username = url_decode(url.username()).trim().to_string();
    if username.is_empty() {
        return Err(ParseError::MissingField("username"));
    }
    let password = url
        .password()
        .map(url_decode)
        .filter(|value| !value.is_empty())
        .ok_or(ParseError::MissingField("password"))?;
    let query: HashMap<String, String> = url.query_pairs().into_owned().collect();
    let name = url
        .fragment()
        .map(url_decode)
        .filter(|value| !value.trim().is_empty())
        .unwrap_or_else(|| format!("NaiveProxy {host}:{port}"));

    Ok(Server {
        id: fingerprint(input),
        name,
        server_description: query_value(&query, "serverDescription"),
        host_uuid: query_value(&query, "hostUuid"),
        xray_json_template_uuid: query_value(&query, "xrayJsonTemplateUuid"),
        protocol: Protocol::Naive(NaiveConfig {
            address: host.to_string(),
            port,
            username,
            password,
            transport,
            local_port: None,
        }),
    })
}

fn query_value(query: &HashMap<String, String>, wanted: &str) -> Option<String> {
    let wanted = normalize_key(wanted);
    query.iter().find_map(|(key, value)| {
        (normalize_key(key) == wanted)
            .then(|| value.trim().to_string())
            .filter(|value| !value.is_empty())
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
    fn parses_https_and_encoded_credentials() {
        let server =
            parse("naive+https://user%40mail:p%40ss@example.com:8443?hostUuid=h-1#Naive%20EU")
                .unwrap();
        assert_eq!(server.name, "Naive EU");
        assert_eq!(server.host_uuid.as_deref(), Some("h-1"));
        match server.protocol {
            Protocol::Naive(config) => {
                assert_eq!(config.address, "example.com");
                assert_eq!(config.port, 8443);
                assert_eq!(config.username, "user@mail");
                assert_eq!(config.password, "p@ss");
                assert_eq!(config.transport, NaiveTransport::Https);
            }
            _ => panic!("wrong protocol"),
        }
    }

    #[test]
    fn parses_quic() {
        let server = parse("naive+quic://u:p@example.com#QUIC").unwrap();
        match server.protocol {
            Protocol::Naive(config) => assert_eq!(config.transport, NaiveTransport::Quic),
            _ => panic!("wrong protocol"),
        }
    }
}
