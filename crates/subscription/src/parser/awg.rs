//! Single-peer AWG/WireGuard imports. Errors must never contain source values.
use std::collections::BTreeMap;
use std::net::IpAddr;

use super::{b64_decode, b64_decode_str, fingerprint, url_decode, ParseError};
use crate::model::{AwgConfig, Protocol, Server};

const MAX_CONFIG: usize = 128 * 1024;

fn invalid() -> ParseError {
    ParseError::InvalidUrl("invalid single-peer AWG/WireGuard configuration".into())
}

pub fn is_uri(input: &str) -> bool {
    input
        .split_once("://")
        .map(|(scheme, _)| {
            matches!(
                scheme.to_ascii_lowercase().as_str(),
                "awg" | "amneziawg" | "wireguard" | "wg"
            )
        })
        .unwrap_or(false)
}

pub fn looks_like_ini(input: &str) -> bool {
    input
        .trim_start_matches('\u{feff}')
        .lines()
        .any(|line| line.trim().eq_ignore_ascii_case("[Interface]"))
}

const ADVANCED: &[&str] = &[
    "header_protection_key",
    "content_padding_addition",
    "rekey_after_time",
    "rekey_timeout",
    "reject_after_time",
    "keepalive_timeout",
    "max_handshake_attempts",
    "random_trailers",
    "disable_cookies",
];
fn canonical_key(key: &str) -> String {
    ADVANCED
        .iter()
        .find(|name| normalized(name) == normalized(key))
        .map(|v| v.to_string())
        .unwrap_or_else(|| key.to_ascii_lowercase())
}
fn normalized(key: &str) -> String {
    key.chars()
        .filter(|c| *c != '_' && *c != '-')
        .flat_map(char::to_lowercase)
        .collect()
}

pub fn parse(input: &str) -> Result<Server, ParseError> {
    if input.len() > MAX_CONFIG * 2 {
        return Err(invalid());
    }
    let input = input.trim().trim_start_matches('\u{feff}');
    let mut name = None;
    let ini = if is_uri(input) {
        let rest = input.split_once("://").ok_or_else(invalid)?.1;
        let (rest, fragment) = rest
            .split_once('#')
            .map(|(a, b)| (a, Some(b)))
            .unwrap_or((rest, None));
        name = fragment.map(url_decode).filter(|v| !v.trim().is_empty());
        let (body, query) = rest.split_once('?').unwrap_or((rest, ""));
        // Do not form-decode '+' in base64 key values.
        let mut params = BTreeMap::new();
        for part in query.split('&').filter(|v| !v.is_empty()) {
            let (k, v) = part.split_once('=').ok_or_else(invalid)?;
            let value = url_decode(v);
            if value.contains(['\r', '\n', '\0']) {
                return Err(invalid());
            }
            if params.insert(normalized(&url_decode(k)), value).is_some() {
                return Err(invalid());
            }
        }
        if name.is_none() {
            name = params.get("name").cloned();
        }
        let decoded = url_decode(body);
        if looks_like_ini(&decoded) {
            decoded
        } else if let Ok(text) = b64_decode_str(&decoded) {
            if !looks_like_ini(&text) {
                return Err(invalid());
            }
            text
        } else {
            query_ini(body, &params)?
        }
    } else {
        input.to_string()
    };
    let config = parse_ini(&ini)?;
    Ok(Server {
        id: fingerprint(&config.config),
        name: name.unwrap_or_else(|| format!("AmneziaWG {}:{}", config.address, config.port)),
        server_description: None,
        host_uuid: None,
        xray_json_template_uuid: None,
        protocol: Protocol::Awg(config),
    })
}

fn query_ini(body: &str, params: &BTreeMap<String, String>) -> Result<String, ParseError> {
    let get = |keys: &[&str]| keys.iter().find_map(|key| params.get(*key)).cloned();
    let mut interface = vec![];
    let mut peer = vec![];
    for (key, aliases) in [
        ("PrivateKey", vec!["privatekey", "privkey", "secretkey"]),
        ("Address", vec!["address", "ip", "localaddress", "localip"]),
        ("DNS", vec!["dns"]),
        ("MTU", vec!["mtu"]),
    ] {
        if let Some(v) = get(&aliases) {
            interface.push(format!("{key} = {v}"));
        }
    }
    for key in [
        "Jc", "Jmin", "Jmax", "S1", "S2", "S3", "S4", "H1", "H2", "H3", "H4", "I1", "I2", "I3",
        "I4", "I5",
    ] {
        if let Some(v) = params.get(&key.to_ascii_lowercase()) {
            interface.push(format!("{key} = {v}"));
        }
    }
    for key in ADVANCED {
        if let Some(v) = params.get(&normalized(key)) {
            interface.push(format!("{key} = {v}"));
        }
    }
    for (key, aliases) in [
        (
            "PublicKey",
            vec!["publickey", "pubkey", "peerpublickey", "serverpublickey"],
        ),
        (
            "PresharedKey",
            vec!["presharedkey", "psk", "peerpresharedkey"],
        ),
        ("AllowedIPs", vec!["allowedips", "iprange", "routes"]),
        (
            "PersistentKeepalive",
            vec![
                "persistentkeepalive",
                "keepalive",
                "persistentkeepaliveinterval",
            ],
        ),
    ] {
        if let Some(v) = get(&aliases) {
            peer.push(format!("{key} = {v}"));
        }
    }
    if get(&["allowedips", "iprange", "routes"]).is_none() {
        peer.push("AllowedIPs = 0.0.0.0/0, ::/0".into());
    }
    let endpoint = get(&["endpoint", "server"]).unwrap_or_else(|| url_decode(body));
    parse_endpoint(&endpoint)?;
    peer.push(format!("Endpoint = {endpoint}"));
    Ok(format!(
        "[Interface]\n{}\n[Peer]\n{}\n",
        interface.join("\n"),
        peer.join("\n")
    ))
}

pub fn parse_endpoint(input: &str) -> Result<(String, u16), ParseError> {
    let (host, port) = input.rsplit_once(':').ok_or_else(invalid)?;
    let port: u16 = port.parse().map_err(|_| invalid())?;
    let host = if host.starts_with('[') && host.ends_with(']') {
        let host = &host[1..host.len() - 1];
        if !matches!(host.parse::<IpAddr>(), Ok(IpAddr::V6(_))) {
            return Err(invalid());
        }
        host
    } else {
        if !host
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, b'.' | b'-'))
        {
            return Err(invalid());
        }
        host
    };
    if host.is_empty() || port == 0 {
        return Err(invalid());
    }
    Ok((host.into(), port))
}

pub fn parse_ini(input: &str) -> Result<AwgConfig, ParseError> {
    if input.len() > MAX_CONFIG || input.contains('\0') {
        return Err(invalid());
    }
    let mut section = "";
    let mut sections = (false, false);
    let mut interface = BTreeMap::new();
    let mut peer = BTreeMap::new();
    for raw in input.trim_start_matches('\u{feff}').lines() {
        let line = raw.split('#').next().unwrap_or_default().trim();
        if line.is_empty() || line.starts_with(';') {
            continue;
        }
        if line.eq_ignore_ascii_case("[Interface]") {
            if sections.0 {
                return Err(invalid());
            }
            sections.0 = true;
            section = "interface";
            continue;
        }
        if line.eq_ignore_ascii_case("[Peer]") {
            if sections.1 {
                return Err(invalid());
            }
            sections.1 = true;
            section = "peer";
            continue;
        }
        let (key, value) = line.split_once('=').ok_or_else(invalid)?;
        let key = canonical_key(key.trim());
        let value = value.trim();
        let target = match section {
            "interface" => &mut interface,
            "peer" => &mut peer,
            _ => return Err(invalid()),
        };
        let allowed = if section == "interface" {
            matches!(
                key.as_str(),
                "header_protection_key"
                    | "content_padding_addition"
                    | "rekey_after_time"
                    | "rekey_timeout"
                    | "reject_after_time"
                    | "keepalive_timeout"
                    | "max_handshake_attempts"
                    | "random_trailers"
                    | "disable_cookies"
                    | "privatekey"
                    | "address"
                    | "dns"
                    | "mtu"
                    | "listenport"
                    | "jc"
                    | "jmin"
                    | "jmax"
                    | "s1"
                    | "s2"
                    | "s3"
                    | "s4"
                    | "h1"
                    | "h2"
                    | "h3"
                    | "h4"
                    | "i1"
                    | "i2"
                    | "i3"
                    | "i4"
                    | "i5"
            )
        } else {
            matches!(
                key.as_str(),
                "publickey" | "presharedkey" | "endpoint" | "allowedips" | "persistentkeepalive"
            )
        };
        if !allowed || value.is_empty() || target.insert(key, value.to_string()).is_some() {
            return Err(invalid());
        }
    }
    for (map, key) in [(&interface, "privatekey"), (&peer, "publickey")] {
        validate_key(map.get(key).ok_or_else(invalid)?)?;
    }
    if let Some(key) = peer.get("presharedkey") {
        validate_key(key)?;
    }
    validate_cidrs(interface.get("address").ok_or_else(invalid)?)?;
    validate_cidrs(peer.get("allowedips").ok_or_else(invalid)?)?;
    let (address, port) = parse_endpoint(peer.get("endpoint").ok_or_else(invalid)?)?;
    for (map, key) in [(&mut interface, "privatekey"), (&mut peer, "publickey")] {
        if let Some(value) = map.get_mut(key) {
            *value = canonical_key_value(value)?;
        }
    }
    for (map, key) in [
        (&mut peer, "presharedkey"),
        (&mut interface, "header_protection_key"),
    ] {
        if let Some(value) = map.get_mut(key) {
            *value = canonical_key_value(value)?;
        }
    }
    // Preserve every recognized AWG 3.1 value, including H ranges and I packet expressions.
    let config = format!(
        "[Interface]\n{}[Peer]\n{}",
        render(&interface),
        render(&peer)
    );
    Ok(AwgConfig {
        address,
        port,
        config,
        local_socks: None,
    })
}

fn render(map: &BTreeMap<String, String>) -> String {
    map.iter().map(|(k, v)| format!("{k} = {v}\n")).collect()
}
fn canonical_key_value(value: &str) -> Result<String, ParseError> {
    use base64::Engine;
    let bytes = if value.len() == 64 && value.bytes().all(|b| b.is_ascii_hexdigit()) {
        (0..64)
            .step_by(2)
            .map(|i| u8::from_str_radix(&value[i..i + 2], 16).map_err(|_| invalid()))
            .collect::<Result<Vec<_>, _>>()?
    } else {
        b64_decode(value).map_err(|_| invalid())?
    };
    if bytes.len() != 32 {
        return Err(invalid());
    }
    Ok(base64::engine::general_purpose::STANDARD.encode(bytes))
}
fn validate_key(value: &str) -> Result<(), ParseError> {
    if value.len() == 64 && value.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Ok(());
    }
    if b64_decode(value).map_err(|_| invalid())?.len() != 32 {
        return Err(invalid());
    }
    Ok(())
}
fn validate_cidrs(value: &str) -> Result<(), ParseError> {
    for cidr in value.split(',') {
        let (ip, prefix) = cidr.trim().split_once('/').ok_or_else(invalid)?;
        let ip: IpAddr = ip.parse().map_err(|_| invalid())?;
        let prefix: u8 = prefix.parse().map_err(|_| invalid())?;
        if prefix > if ip.is_ipv4() { 32 } else { 128 } {
            return Err(invalid());
        }
    }
    Ok(())
}

/// Revalidate persisted/imported state and pin exactly the IP whose route is bypassed.
pub fn pin_endpoint(config: &str, ip: IpAddr) -> Result<AwgConfig, ParseError> {
    let parsed = parse_ini(config)?;
    let endpoint = std::net::SocketAddr::new(ip, parsed.port).to_string();
    let text = parsed
        .config
        .lines()
        .map(|line| {
            if line.starts_with("endpoint = ") {
                format!("endpoint = {endpoint}")
            } else {
                line.to_string()
            }
        })
        .collect::<Vec<_>>()
        .join("\n");
    parse_ini(&text)
}

#[cfg(test)]
mod tests {
    use super::*;
    use base64::Engine;
    const KEY: &str = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
    fn ini() -> String {
        format!("[Interface]\nPrivateKey={KEY}\nAddress=10.0.0.2/32, fd00::2/128\nJc=4\nS3=10\nS4=20\nH1=1-10\nI5=<b 0x1234>\n[Peer]\nPublicKey={KEY}\nEndpoint=vpn.example:51820\nAllowedIPs=0.0.0.0/0, ::/0\nPersistentKeepalive=25\n")
    }
    #[test]
    fn hex_keys_and_advanced_snake_case_and_camel_aliases() {
        let text = ini().replace(KEY,&"01".repeat(32)).replace("Jc=4",&format!("Jc=4\nHeaderProtectionKey={}\ncontent_padding_addition=8\nRekeyAfterTime=120\nrandom_trailers=true\ndisable_cookies=false", "02".repeat(32)));
        let config = parse_ini(&text).unwrap();
        assert!(config.config.contains(&format!("privatekey = {KEY}")));
        assert!(config.config.contains("rekey_after_time = 120"));
        assert!(config.config.contains("content_padding_addition = 8"));
        assert!(config
            .config
            .contains("header_protection_key = AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="));
        assert_eq!(
            parse_ini(&format!("\u{feff}{text}")).unwrap().config,
            config.config
        );
    }
    #[test]
    fn imports_raw_and_base64_uris_without_losing_awg31_fields() {
        for source in [
            ini(),
            format!(
                "awg://{}#Office%20VPN",
                base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(ini())
            ),
            format!(
                "wireguard://{}",
                base64::engine::general_purpose::STANDARD.encode(ini())
            ),
        ] {
            let servers = crate::parse_aggregate(&source).unwrap();
            let Protocol::Awg(config) = &servers[0].protocol else {
                panic!()
            };
            assert_eq!(config.address, "vpn.example");
            assert!(config.config.contains("s3 = 10\ns4 = 20"));
            assert!(config.config.contains("h1 = 1-10"));
            assert!(config.config.contains("i5 = <b 0x1234>"));
            assert!(config.local_socks.is_none());
            assert!(!format!("{config:?}").contains(KEY));
        }
    }
    #[test]
    fn query_aliases_and_ipv6_endpoint() {
        let server = parse(&format!("amneziawg://[2001:db8::1]:1234?private_key={KEY}&public_key={KEY}&address=10.0.0.2/32&S4=12#IPv6")).unwrap();
        let Protocol::Awg(config) = server.protocol else {
            panic!()
        };
        assert_eq!(config.address, "2001:db8::1");
        assert_eq!(config.port, 1234);
        assert!(config.config.contains("s4 = 12"));
    }
    #[test]
    fn invalid_inputs_are_redacted_and_single_peer_only() {
        for source in [
            format!("{}[Peer]\nPublicKey={KEY}", ini()),
            ini().replace(KEY, "SECRET_NOT_A_KEY"),
            ini().replace("Jc=4", "PostUp=SECRET_COMMAND"),
            ini().replace("51820", "0"),
            ini().replace("/32", "/99"),
        ] {
            let err = parse(&source).unwrap_err().to_string();
            assert!(!err.contains("SECRET"));
            assert!(!err.contains(KEY));
        }
        assert!(parse(&format!(
            "awg://vpn.example:1?private_key={KEY}%0AAddress=127.0.0.1/32"
        ))
        .is_err());
    }
    #[test]
    fn endpoint_pin_and_runtime_credentials_are_not_persisted() {
        let mut config = pin_endpoint(&ini(), "192.0.2.1".parse().unwrap()).unwrap();
        assert_eq!(config.address, "192.0.2.1");
        config.local_socks = Some(crate::AwgLocalSocks {
            port: 1234,
            username: "runtime-user".into(),
            password: "runtime-secret".into(),
        });
        let json = serde_json::to_string(&config).unwrap();
        assert!(!json.contains("runtime-secret"));
        assert!(serde_json::from_str::<AwgConfig>(&json)
            .unwrap()
            .local_socks
            .is_none());
    }
}
