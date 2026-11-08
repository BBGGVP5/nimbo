//! TrustTunnel deep links (`tt://?<base64url TLV>`), deep-link format version 2,
//! and the endpoint's per-user subscription JSON.
//! Spec: https://github.com/TrustTunnel/TrustTunnel/blob/master/DEEP_LINK.md

use base64::Engine;

use crate::model::{Protocol, Server, TrustTunnelConfig, TrustTunnelUpstream};
use crate::parser::{fingerprint, url_decode, ParseError};

/// Highest deep-link format version this parser understands.
pub const MAX_DEEP_LINK_VERSION: u64 = 2;

const TAG_VERSION: u64 = 0x00;
const TAG_HOSTNAME: u64 = 0x01;
const TAG_ADDRESSES: u64 = 0x02;
const TAG_CUSTOM_SNI: u64 = 0x03;
const TAG_HAS_IPV6: u64 = 0x04;
const TAG_USERNAME: u64 = 0x05;
const TAG_PASSWORD: u64 = 0x06;
const TAG_SKIP_VERIFICATION: u64 = 0x07;
const TAG_CERTIFICATE: u64 = 0x08;
const TAG_UPSTREAM_PROTOCOL: u64 = 0x09;
const TAG_ANTI_DPI: u64 = 0x0A;
const TAG_CLIENT_RANDOM_PREFIX: u64 = 0x0B;
const TAG_NAME: u64 = 0x0C;
const TAG_DNS_UPSTREAMS: u64 = 0x0D;
const TAG_SUBSCRIPTION_URL: u64 = 0x0E;
const TAG_CLIENT_RANDOM_AUTH_KEY: u64 = 0x0F;

pub fn is_uri(line: &str) -> bool {
    line.trim_start()
        .get(..5)
        .is_some_and(|scheme| scheme.eq_ignore_ascii_case("tt://"))
}

/// Decoded deep link before it is turned into a server. A subscription-only
/// link (spec rule 6) has no static parameters and only `subscription_url`.
#[derive(Debug, Default, Clone)]
pub struct DeepLink {
    pub version: u64,
    pub hostname: Option<String>,
    pub addresses: Vec<String>,
    pub custom_sni: Option<String>,
    pub has_ipv6: Option<bool>,
    pub username: Option<String>,
    pub password: Option<String>,
    pub skip_verification: bool,
    pub certificate_der: Option<Vec<u8>>,
    pub upstream: TrustTunnelUpstream,
    pub anti_dpi: bool,
    pub client_random_prefix: Option<String>,
    pub name: Option<String>,
    pub dns_upstreams: Vec<String>,
    pub subscription_url: Option<String>,
    pub client_random_auth_key: Option<String>,
}

impl DeepLink {
    fn has_static_parameters(&self) -> bool {
        self.hostname.is_some()
            && !self.addresses.is_empty()
            && self.username.is_some()
            && self.password.is_some()
    }
}

/// Decodes `tt://?payload[#display name]`.
pub fn decode(input: &str) -> Result<(DeepLink, Option<String>), ParseError> {
    let trimmed = input.trim();
    if !is_uri(trimmed) {
        return Err(ParseError::UnsupportedScheme(
            trimmed.split("://").next().unwrap_or("").to_string(),
        ));
    }
    let rest = &trimmed[5..];
    let (payload, fragment) = match rest.split_once('#') {
        Some((payload, fragment)) => (payload, Some(fragment)),
        None => (rest, None),
    };
    // Draft 1 put the payload into the (case-insensitive) host; draft 2+ uses `?`.
    let payload = payload.strip_prefix('?').unwrap_or(payload);
    let payload: String = payload
        .chars()
        .filter(|ch| !ch.is_whitespace())
        .collect::<String>()
        .trim_end_matches('=')
        .to_string();
    if payload.is_empty() {
        return Err(ParseError::MissingField("payload"));
    }
    let bytes = base64::engine::general_purpose::URL_SAFE_NO_PAD
        .decode(payload.as_bytes())
        .or_else(|_| base64::engine::general_purpose::STANDARD_NO_PAD.decode(payload.as_bytes()))
        .map_err(|e| ParseError::InvalidBase64(e.to_string()))?;
    let link = decode_tlv(&bytes)?;
    if !link.has_static_parameters() && link.subscription_url.is_none() {
        return Err(ParseError::MissingField(
            "hostname/addresses/username/password",
        ));
    }
    Ok((link, fragment.map(fragment_name).filter(|n| !n.is_empty())))
}

/// Subscription URL carried by a deep link, if any (already validated as https).
pub fn subscription_url(input: &str) -> Option<String> {
    decode(input)
        .ok()
        .and_then(|(link, _)| link.subscription_url)
}

pub fn parse(input: &str) -> Result<Server, ParseError> {
    let (link, fragment_name) = decode(input)?;
    if !link.has_static_parameters() {
        // Subscription-only links need a successful fetch before they can connect.
        return Err(ParseError::MissingField(
            "hostname/addresses/username/password",
        ));
    }
    let certificate = link.certificate_der.as_deref().map(der_chain_to_pem);
    let config = build_config(ConfigParts {
        hostname: link.hostname.unwrap_or_default(),
        addresses: link.addresses,
        custom_sni: link.custom_sni,
        has_ipv6: link.has_ipv6.unwrap_or(true),
        username: link.username.unwrap_or_default(),
        password: link.password.unwrap_or_default(),
        skip_verification: link.skip_verification,
        certificate,
        upstream: link.upstream,
        anti_dpi: link.anti_dpi,
        client_random_prefix: link.client_random_prefix,
        client_random_auth_key: link.client_random_auth_key,
        dns_upstreams: link.dns_upstreams,
        subscription_url: link.subscription_url,
    })?;
    let name = fragment_name
        .or(link.name)
        .unwrap_or_else(|| default_name(&config));
    Ok(Server {
        id: fingerprint(input.trim()),
        name,
        server_description: None,
        host_uuid: None,
        xray_json_template_uuid: None,
        protocol: Protocol::TrustTunnel(config),
    })
}

/// Detects the endpoint's `[subscription]` JSON response.
pub fn looks_like_subscription_json(value: &serde_json::Value) -> bool {
    value.get("hostname").is_some_and(|v| v.is_string())
        && value.get("address").is_some_and(|v| v.is_string())
        && value.get("username").is_some_and(|v| v.is_string())
        && value.get("password").is_some_and(|v| v.is_string())
        && value.get("upstream_protocol").is_some()
}

/// Parses the per-user JSON served by a TrustTunnel endpoint subscription.
pub fn parse_subscription_json(body: &str) -> Result<Server, ParseError> {
    let value: serde_json::Value =
        serde_json::from_str(body.trim()).map_err(|e| ParseError::InvalidJson(e.to_string()))?;
    if !looks_like_subscription_json(&value) {
        return Err(ParseError::InvalidJson(
            "not a TrustTunnel subscription".into(),
        ));
    }
    let text = |key: &str| {
        value
            .get(key)
            .and_then(|v| v.as_str())
            .map(str::trim)
            .filter(|v| !v.is_empty())
            .map(str::to_string)
    };
    let flag =
        |key: &str, default: bool| value.get(key).and_then(|v| v.as_bool()).unwrap_or(default);
    let upstream = match text("upstream_protocol").as_deref() {
        Some(p) if p.eq_ignore_ascii_case("http3") || p.eq_ignore_ascii_case("quic") => {
            TrustTunnelUpstream::Http3
        }
        _ => TrustTunnelUpstream::Http2,
    };
    let certificate = text("certificate").map(|cert| {
        if cert.contains("-----BEGIN") {
            cert
        } else {
            base64::engine::general_purpose::STANDARD
                .decode(cert.as_bytes())
                .map(|der| der_chain_to_pem(&der))
                .unwrap_or(cert)
        }
    });
    let dns_upstreams = value
        .get("dns_upstreams")
        .and_then(|v| v.as_array())
        .map(|items| {
            items
                .iter()
                .filter_map(|v| v.as_str().map(str::trim).filter(|v| !v.is_empty()))
                .map(str::to_string)
                .collect()
        })
        .unwrap_or_default();
    let config = build_config(ConfigParts {
        hostname: text("hostname").ok_or(ParseError::MissingField("hostname"))?,
        addresses: vec![text("address").ok_or(ParseError::MissingField("address"))?],
        custom_sni: text("custom_sni"),
        has_ipv6: flag("has_ipv6", true),
        username: text("username").ok_or(ParseError::MissingField("username"))?,
        password: value
            .get("password")
            .and_then(|v| v.as_str())
            .filter(|v| !v.is_empty())
            .map(str::to_string)
            .ok_or(ParseError::MissingField("password"))?,
        skip_verification: flag("skip_verification", false),
        certificate,
        upstream,
        anti_dpi: flag("anti_dpi", false),
        client_random_prefix: text("client_random_prefix"),
        client_random_auth_key: text("client_random_auth_key"),
        dns_upstreams,
        subscription_url: None,
    })?;
    let name = text("name").unwrap_or_else(|| default_name(&config));
    Ok(Server {
        id: fingerprint(&format!(
            "tt-sub:{}:{}:{}",
            config.hostname, config.username, config.port
        )),
        name,
        server_description: None,
        host_uuid: None,
        xray_json_template_uuid: None,
        protocol: Protocol::TrustTunnel(config),
    })
}

struct ConfigParts {
    hostname: String,
    addresses: Vec<String>,
    custom_sni: Option<String>,
    has_ipv6: bool,
    username: String,
    password: String,
    skip_verification: bool,
    certificate: Option<String>,
    upstream: TrustTunnelUpstream,
    anti_dpi: bool,
    client_random_prefix: Option<String>,
    client_random_auth_key: Option<String>,
    dns_upstreams: Vec<String>,
    subscription_url: Option<String>,
}

fn build_config(parts: ConfigParts) -> Result<TrustTunnelConfig, ParseError> {
    let hostname = parts.hostname.trim().to_string();
    if hostname.is_empty() {
        return Err(ParseError::MissingField("hostname"));
    }
    if parts.username.trim().is_empty() {
        return Err(ParseError::MissingField("username"));
    }
    if parts.password.is_empty() {
        return Err(ParseError::MissingField("password"));
    }
    let mut addresses = Vec::new();
    for raw in &parts.addresses {
        let normalized = normalize_address(raw, 443)?;
        if !addresses.contains(&normalized) {
            addresses.push(normalized);
        }
    }
    let first = addresses
        .first()
        .ok_or(ParseError::MissingField("addresses"))?;
    let (address, port) = split_host_port(first, 443)?;
    Ok(TrustTunnelConfig {
        address,
        port,
        addresses,
        hostname,
        custom_sni: parts.custom_sni.filter(|v| !v.trim().is_empty()),
        has_ipv6: parts.has_ipv6,
        username: parts.username.trim().to_string(),
        password: parts.password,
        skip_verification: parts.skip_verification,
        certificate: parts.certificate.filter(|v| !v.trim().is_empty()),
        upstream: parts.upstream,
        anti_dpi: parts.anti_dpi,
        client_random_prefix: parts.client_random_prefix.filter(|v| !v.trim().is_empty()),
        client_random_auth_key: parts
            .client_random_auth_key
            .filter(|v| !v.trim().is_empty()),
        dns_upstreams: parts.dns_upstreams,
        subscription_url: parts.subscription_url,
        local_socks: None,
    })
}

fn default_name(config: &TrustTunnelConfig) -> String {
    format!("TrustTunnel {}", config.hostname)
}

/// The fragment is a display name; Remnawave-style panels may append
/// `?serverDescription=<base64>` to it, which never belongs in the name.
fn fragment_name(fragment: &str) -> String {
    let decoded = url_decode(fragment);
    let name = match decoded.find("?serverDescription=") {
        Some(index) => &decoded[..index],
        None => decoded.as_str(),
    };
    name.trim().to_string()
}

/// `host:port`, `[v6]:port`, bare host or bare IPv6 → canonical `host:port`.
fn normalize_address(raw: &str, default_port: u16) -> Result<String, ParseError> {
    let (host, port) = split_host_port(raw, default_port)?;
    Ok(if host.contains(':') {
        format!("[{host}]:{port}")
    } else {
        format!("{host}:{port}")
    })
}

pub fn split_host_port(raw: &str, default_port: u16) -> Result<(String, u16), ParseError> {
    let raw = raw.trim();
    if raw.is_empty() {
        return Err(ParseError::MissingField("address"));
    }
    if let Some(rest) = raw.strip_prefix('[') {
        let (host, tail) = rest
            .split_once(']')
            .ok_or_else(|| ParseError::InvalidUrl(raw.to_string()))?;
        let port = match tail.strip_prefix(':') {
            Some(port) => crate::parser::parse_port(port)?,
            None if tail.is_empty() => default_port,
            None => return Err(ParseError::InvalidUrl(raw.to_string())),
        };
        return Ok((host.to_string(), port));
    }
    match raw.rsplit_once(':') {
        // More than one colon without brackets is a bare IPv6 address.
        Some((host, _)) if host.contains(':') => Ok((raw.to_string(), default_port)),
        Some((host, port)) if !host.is_empty() => {
            Ok((host.to_string(), crate::parser::parse_port(port)?))
        }
        Some(_) => Err(ParseError::MissingField("host")),
        None => Ok((raw.to_string(), default_port)),
    }
}

fn decode_tlv(bytes: &[u8]) -> Result<DeepLink, ParseError> {
    let mut link = DeepLink::default();
    let mut cursor = 0usize;
    while cursor < bytes.len() {
        let tag = read_varint(bytes, &mut cursor)?;
        let length = usize::try_from(read_varint(bytes, &mut cursor)?)
            .map_err(|_| invalid("length overflow"))?;
        let end = cursor
            .checked_add(length)
            .filter(|end| *end <= bytes.len())
            .ok_or_else(|| invalid("truncated value"))?;
        let value = &bytes[cursor..end];
        cursor = end;
        match tag {
            TAG_VERSION => {
                let mut inner = 0usize;
                link.version = read_varint(value, &mut inner)?;
            }
            TAG_HOSTNAME => link.hostname = Some(utf8(value)?),
            TAG_ADDRESSES => {
                let address = utf8(value)?;
                if !address.trim().is_empty() {
                    link.addresses.push(address.trim().to_string());
                }
            }
            TAG_CUSTOM_SNI => link.custom_sni = Some(utf8(value)?),
            TAG_HAS_IPV6 => link.has_ipv6 = Some(boolean(value)?),
            TAG_USERNAME => link.username = Some(utf8(value)?),
            TAG_PASSWORD => link.password = Some(utf8(value)?),
            TAG_SKIP_VERIFICATION => link.skip_verification = boolean(value)?,
            TAG_CERTIFICATE => {
                link.certificate_der = (!value.is_empty()).then(|| value.to_vec());
            }
            TAG_UPSTREAM_PROTOCOL => {
                let mut inner = 0usize;
                link.upstream = match read_varint(value, &mut inner)? {
                    2 => TrustTunnelUpstream::Http3,
                    _ => TrustTunnelUpstream::Http2,
                };
            }
            TAG_ANTI_DPI => link.anti_dpi = boolean(value)?,
            TAG_CLIENT_RANDOM_PREFIX => link.client_random_prefix = Some(utf8(value)?),
            TAG_NAME => link.name = Some(utf8(value)?).filter(|n| !n.trim().is_empty()),
            TAG_DNS_UPSTREAMS => link.dns_upstreams = string_array(value)?,
            TAG_SUBSCRIPTION_URL => {
                let url = utf8(value)?;
                if !url.trim().to_ascii_lowercase().starts_with("https://") {
                    return Err(ParseError::InvalidUrl(
                        "subscription_url must use https".into(),
                    ));
                }
                link.subscription_url = Some(url.trim().to_string());
            }
            TAG_CLIENT_RANDOM_AUTH_KEY => link.client_random_auth_key = Some(utf8(value)?),
            // Unknown tags are forward-compatible extensions.
            _ => {}
        }
    }
    if link.version > MAX_DEEP_LINK_VERSION {
        return Err(ParseError::InvalidUrl(format!(
            "unsupported TrustTunnel deep link version {}",
            link.version
        )));
    }
    Ok(link)
}

/// RFC 9000 §16 variable-length integer.
fn read_varint(bytes: &[u8], cursor: &mut usize) -> Result<u64, ParseError> {
    let first = *bytes
        .get(*cursor)
        .ok_or_else(|| invalid("truncated varint"))?;
    let size = 1usize << (first >> 6);
    let end = cursor
        .checked_add(size)
        .filter(|end| *end <= bytes.len())
        .ok_or_else(|| invalid("truncated varint"))?;
    let mut value = u64::from(first & 0x3f);
    for byte in &bytes[*cursor + 1..end] {
        value = (value << 8) | u64::from(*byte);
    }
    *cursor = end;
    Ok(value)
}

fn utf8(value: &[u8]) -> Result<String, ParseError> {
    String::from_utf8(value.to_vec()).map_err(|e| ParseError::InvalidUtf8(e.to_string()))
}

fn boolean(value: &[u8]) -> Result<bool, ParseError> {
    match value {
        [byte] => Ok(*byte != 0),
        _ => Err(invalid("bad boolean")),
    }
}

fn string_array(value: &[u8]) -> Result<Vec<String>, ParseError> {
    let mut out = Vec::new();
    let mut cursor = 0usize;
    while cursor < value.len() {
        let length = usize::try_from(read_varint(value, &mut cursor)?)
            .map_err(|_| invalid("length overflow"))?;
        let end = cursor
            .checked_add(length)
            .filter(|end| *end <= value.len())
            .ok_or_else(|| invalid("truncated string array"))?;
        let item = utf8(&value[cursor..end])?;
        if !item.trim().is_empty() {
            out.push(item.trim().to_string());
        }
        cursor = end;
    }
    Ok(out)
}

fn invalid(reason: &str) -> ParseError {
    ParseError::InvalidUrl(format!("TrustTunnel deep link: {reason}"))
}

/// Splits concatenated DER certificates and re-encodes them as a PEM chain.
pub fn der_chain_to_pem(der: &[u8]) -> String {
    let mut pem = String::new();
    let mut cursor = 0usize;
    while cursor < der.len() {
        let length = der_element_length(&der[cursor..]).unwrap_or(der.len() - cursor);
        let end = (cursor + length).min(der.len());
        let body = base64::engine::general_purpose::STANDARD.encode(&der[cursor..end]);
        pem.push_str("-----BEGIN CERTIFICATE-----\n");
        for line in body.as_bytes().chunks(64) {
            pem.push_str(std::str::from_utf8(line).unwrap_or_default());
            pem.push('\n');
        }
        pem.push_str("-----END CERTIFICATE-----\n");
        cursor = end;
    }
    pem
}

/// Total length (header + content) of one DER SEQUENCE.
fn der_element_length(der: &[u8]) -> Option<usize> {
    if der.first() != Some(&0x30) {
        return None;
    }
    let first = *der.get(1)?;
    if first < 0x80 {
        return Some(2 + usize::from(first));
    }
    let count = usize::from(first & 0x7f);
    if count == 0 || count > 4 || der.len() < 2 + count {
        return None;
    }
    let mut length = 0usize;
    for byte in &der[2..2 + count] {
        length = (length << 8) | usize::from(*byte);
    }
    Some(2 + count + length)
}

/// Encoder used by tests and by the UI's "copy link" path.
pub fn encode(config: &TrustTunnelConfig, name: Option<&str>) -> String {
    fn varint(out: &mut Vec<u8>, value: u64) {
        if value < 1 << 6 {
            out.push(value as u8);
        } else if value < 1 << 14 {
            out.extend_from_slice(&((value as u16) | 0x4000).to_be_bytes());
        } else if value < 1 << 30 {
            out.extend_from_slice(&((value as u32) | 0x8000_0000).to_be_bytes());
        } else {
            out.extend_from_slice(&(value | 0xC000_0000_0000_0000).to_be_bytes());
        }
    }
    fn field(out: &mut Vec<u8>, tag: u64, value: &[u8]) {
        varint(out, tag);
        varint(out, value.len() as u64);
        out.extend_from_slice(value);
    }
    let mut out = Vec::new();
    field(&mut out, TAG_VERSION, &[MAX_DEEP_LINK_VERSION as u8]);
    field(&mut out, TAG_HOSTNAME, config.hostname.as_bytes());
    for address in &config.addresses {
        field(&mut out, TAG_ADDRESSES, address.as_bytes());
    }
    if let Some(sni) = &config.custom_sni {
        field(&mut out, TAG_CUSTOM_SNI, sni.as_bytes());
    }
    if !config.has_ipv6 {
        field(&mut out, TAG_HAS_IPV6, &[0]);
    }
    field(&mut out, TAG_USERNAME, config.username.as_bytes());
    field(&mut out, TAG_PASSWORD, config.password.as_bytes());
    if config.skip_verification {
        field(&mut out, TAG_SKIP_VERIFICATION, &[1]);
    }
    if let Some(der) = config.certificate.as_deref().and_then(pem_chain_to_der) {
        field(&mut out, TAG_CERTIFICATE, &der);
    }
    let upstream = match config.upstream {
        TrustTunnelUpstream::Http2 => 1,
        TrustTunnelUpstream::Http3 => 2,
    };
    field(&mut out, TAG_UPSTREAM_PROTOCOL, &[upstream]);
    if config.anti_dpi {
        field(&mut out, TAG_ANTI_DPI, &[1]);
    }
    if let Some(prefix) = &config.client_random_prefix {
        field(&mut out, TAG_CLIENT_RANDOM_PREFIX, prefix.as_bytes());
    }
    if let Some(name) = name.filter(|n| !n.trim().is_empty()) {
        field(&mut out, TAG_NAME, name.as_bytes());
    }
    if !config.dns_upstreams.is_empty() {
        let mut list = Vec::new();
        for dns in &config.dns_upstreams {
            varint(&mut list, dns.len() as u64);
            list.extend_from_slice(dns.as_bytes());
        }
        field(&mut out, TAG_DNS_UPSTREAMS, &list);
    }
    if let Some(url) = &config.subscription_url {
        field(&mut out, TAG_SUBSCRIPTION_URL, url.as_bytes());
    }
    if let Some(key) = &config.client_random_auth_key {
        field(&mut out, TAG_CLIENT_RANDOM_AUTH_KEY, key.as_bytes());
    }
    format!(
        "tt://?{}",
        base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(out)
    )
}

fn pem_chain_to_der(pem: &str) -> Option<Vec<u8>> {
    let mut der = Vec::new();
    let mut body = String::new();
    let mut inside = false;
    for line in pem.lines().map(str::trim) {
        if line.starts_with("-----BEGIN") {
            inside = true;
            body.clear();
        } else if line.starts_with("-----END") {
            inside = false;
            der.extend(
                base64::engine::general_purpose::STANDARD
                    .decode(body.as_bytes())
                    .ok()?,
            );
        } else if inside {
            body.push_str(line);
        }
    }
    (!der.is_empty()).then_some(der)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tlv(tag: u8, value: &[u8]) -> Vec<u8> {
        let mut out = vec![tag];
        if value.len() < 64 {
            out.push(value.len() as u8);
        } else {
            out.extend_from_slice(&((value.len() as u16) | 0x4000).to_be_bytes());
        }
        out.extend_from_slice(value);
        out
    }

    fn link(parts: &[Vec<u8>]) -> String {
        let bytes: Vec<u8> = parts.concat();
        format!(
            "tt://?{}",
            base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(bytes)
        )
    }

    fn config(server: &Server) -> &TrustTunnelConfig {
        match &server.protocol {
            Protocol::TrustTunnel(config) => config,
            other => panic!("wrong protocol: {other:?}"),
        }
    }

    #[test]
    fn parses_spec_example() {
        // DEEP_LINK.md example without the certificate.
        let input = link(&[
            tlv(0x01, b"vpn.example.com"),
            tlv(0x02, b"1.2.3.4:443"),
            tlv(0x03, b"example.org"),
            tlv(0x04, &[1]),
            tlv(0x05, b"premium"),
            tlv(0x06, b"s3cretPass"),
            tlv(0x09, &[1]),
        ]);
        let server = parse(&input).unwrap();
        assert_eq!(server.name, "TrustTunnel vpn.example.com");
        let c = config(&server);
        assert_eq!(c.hostname, "vpn.example.com");
        assert_eq!(c.address, "1.2.3.4");
        assert_eq!(c.port, 443);
        assert_eq!(c.custom_sni.as_deref(), Some("example.org"));
        assert_eq!(c.tls_server_name(), "example.org");
        assert!(c.has_ipv6);
        assert_eq!(c.username, "premium");
        assert_eq!(c.password, "s3cretPass");
        assert_eq!(c.upstream, TrustTunnelUpstream::Http2);
        assert!(!c.skip_verification && !c.anti_dpi);
    }

    #[test]
    fn parses_v2_fields_multiple_addresses_and_dns() {
        let mut dns = Vec::new();
        for item in ["tls://1.1.1.1", "https://dns.example.com/dns-query"] {
            dns.push(item.len() as u8);
            dns.extend_from_slice(item.as_bytes());
        }
        let input = link(&[
            tlv(0x00, &[2]),
            tlv(0x01, b"vpn.example.com"),
            tlv(0x02, b"203.0.113.1:8443"),
            tlv(0x02, b"[2001:db8::1]:443"),
            tlv(0x05, b"u"),
            tlv(0x06, b"p"),
            tlv(0x09, &[2]),
            tlv(0x0A, &[1]),
            tlv(0x0B, b"a0b0/f0f0"),
            tlv(0x0C, "Германия".as_bytes()),
            tlv(0x0D, &dns),
            tlv(0x0F, b"00112233"),
            tlv(0x3E, b"future field"),
        ]);
        let server = parse(&input).unwrap();
        assert_eq!(server.name, "Германия");
        let c = config(&server);
        assert_eq!(c.addresses, vec!["203.0.113.1:8443", "[2001:db8::1]:443"]);
        assert_eq!((c.address.as_str(), c.port), ("203.0.113.1", 8443));
        assert_eq!(c.upstream, TrustTunnelUpstream::Http3);
        assert!(c.anti_dpi);
        assert_eq!(c.client_random_prefix.as_deref(), Some("a0b0/f0f0"));
        assert_eq!(c.client_random_auth_key.as_deref(), Some("00112233"));
        assert_eq!(c.dns_upstreams.len(), 2);
    }

    #[test]
    fn fragment_overrides_name_and_drops_panel_description() {
        let base = link(&[
            tlv(0x01, b"h.example"),
            tlv(0x02, b"h.example:443"),
            tlv(0x05, b"u"),
            tlv(0x06, b"p"),
            tlv(0x0C, b"Embedded"),
        ]);
        let server = parse(&format!("{base}#NL%20Amsterdam?serverDescription=ZGVzYw")).unwrap();
        assert_eq!(server.name, "NL Amsterdam");
    }

    #[test]
    fn rejects_newer_version_and_missing_fields() {
        let newer = link(&[
            tlv(0x00, &[3]),
            tlv(0x01, b"h"),
            tlv(0x02, b"h:443"),
            tlv(0x05, b"u"),
            tlv(0x06, b"p"),
        ]);
        assert!(parse(&newer).is_err());
        let missing = link(&[tlv(0x01, b"h"), tlv(0x05, b"u"), tlv(0x06, b"p")]);
        assert!(parse(&missing).is_err());
        assert!(parse("tt://?").is_err());
        assert!(parse("tt://?AQ").is_err());
    }

    #[test]
    fn subscription_only_link_exposes_url_but_no_server() {
        let input = link(&[
            tlv(0x00, &[2]),
            tlv(0x0E, b"https://user:pa%40ss@vpn.example.com/subscription"),
        ]);
        assert_eq!(
            subscription_url(&input).as_deref(),
            Some("https://user:pa%40ss@vpn.example.com/subscription")
        );
        assert!(parse(&input).is_err());
        let plain_http = link(&[tlv(0x0E, b"http://vpn.example.com/subscription")]);
        assert!(decode(&plain_http).is_err());
    }

    #[test]
    fn converts_der_chain_to_pem_and_back() {
        // Two fake DER SEQUENCEs: short and long-form lengths.
        let mut chain = vec![0x30, 0x03, 1, 2, 3];
        chain.extend_from_slice(&[0x30, 0x81, 0x80]);
        chain.extend(std::iter::repeat(7u8).take(0x80));
        let pem = der_chain_to_pem(&chain);
        assert_eq!(pem.matches("BEGIN CERTIFICATE").count(), 2);
        assert_eq!(pem_chain_to_der(&pem).unwrap(), chain);

        let input = link(&[
            tlv(0x01, b"h"),
            tlv(0x02, b"1.1.1.1:443"),
            tlv(0x05, b"u"),
            tlv(0x06, b"p"),
            tlv(0x08, &chain),
        ]);
        let server = parse(&input).unwrap();
        assert_eq!(config(&server).certificate.as_deref(), Some(pem.as_str()));
    }

    #[test]
    fn encode_round_trips() {
        let original = link(&[
            tlv(0x01, b"vpn.example.com"),
            tlv(0x02, b"1.2.3.4:443"),
            tlv(0x05, b"user"),
            tlv(0x06, b"pass"),
            tlv(0x09, &[2]),
        ]);
        let server = parse(&original).unwrap();
        let again = parse(&encode(config(&server), Some("Round"))).unwrap();
        assert_eq!(again.name, "Round");
        assert_eq!(config(&again).upstream, TrustTunnelUpstream::Http3);
        assert_eq!(config(&again).addresses, config(&server).addresses);
    }

    #[test]
    fn parses_endpoint_subscription_json() {
        let body = r#"{"version":2,"hostname":"vpn.example.com","address":"203.0.113.1:443",
            "username":"alice","password":"secret","has_ipv6":false,"upstream_protocol":"http3",
            "anti_dpi":false,"skip_verification":false,"custom_sni":"example.org",
            "name":"Acme","dns_upstreams":["tls://1.1.1.1"]}"#;
        let server = parse_subscription_json(body).unwrap();
        assert_eq!(server.name, "Acme");
        let c = config(&server);
        assert_eq!(c.upstream, TrustTunnelUpstream::Http3);
        assert!(!c.has_ipv6);
        assert_eq!(c.address, "203.0.113.1");
        assert_eq!(c.tls_server_name(), "example.org");
    }

    #[test]
    fn splits_host_ports() {
        assert_eq!(split_host_port("h:1", 443).unwrap(), ("h".into(), 1));
        assert_eq!(split_host_port("h", 443).unwrap(), ("h".into(), 443));
        assert_eq!(split_host_port("[::1]:5", 443).unwrap(), ("::1".into(), 5));
        assert_eq!(
            split_host_port("2001:db8::1", 443).unwrap(),
            ("2001:db8::1".into(), 443)
        );
    }
}
