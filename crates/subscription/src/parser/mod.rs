pub mod aggregate;
pub mod shadowsocks;
pub mod trojan;
pub mod vless;
pub mod vmess;
pub mod xray_json;

pub use aggregate::parse_aggregate;

use thiserror::Error;

#[derive(Debug, Error)]
pub enum ParseError {
    #[error("unsupported scheme: {0}")]
    UnsupportedScheme(String),
    #[error("invalid url: {0}")]
    InvalidUrl(String),
    #[error("missing required field: {0}")]
    MissingField(&'static str),
    #[error("invalid base64: {0}")]
    InvalidBase64(String),
    #[error("invalid utf8: {0}")]
    InvalidUtf8(String),
    #[error("invalid json: {0}")]
    InvalidJson(String),
    #[error("invalid port: {0}")]
    InvalidPort(String),
    #[error("empty subscription")]
    Empty,
}

pub(crate) fn b64_decode(s: &str) -> Result<Vec<u8>, ParseError> {
    use base64::Engine;
    let cleaned: String = s.chars().filter(|c| !c.is_whitespace()).collect();

    if let Ok(v) = base64::engine::general_purpose::STANDARD.decode(&cleaned) {
        return Ok(v);
    }
    if let Ok(v) = base64::engine::general_purpose::STANDARD_NO_PAD.decode(&cleaned) {
        return Ok(v);
    }
    if let Ok(v) = base64::engine::general_purpose::URL_SAFE.decode(&cleaned) {
        return Ok(v);
    }
    if let Ok(v) = base64::engine::general_purpose::URL_SAFE_NO_PAD.decode(&cleaned) {
        return Ok(v);
    }
    Err(ParseError::InvalidBase64("no engine matched".into()))
}

pub(crate) fn b64_decode_str(s: &str) -> Result<String, ParseError> {
    let bytes = b64_decode(s)?;
    String::from_utf8(bytes).map_err(|e| ParseError::InvalidUtf8(e.to_string()))
}

pub(crate) fn url_decode(s: &str) -> String {
    percent_encoding::percent_decode_str(s)
        .decode_utf8_lossy()
        .into_owned()
}

pub(crate) fn parse_port(s: &str) -> Result<u16, ParseError> {
    s.parse::<u16>()
        .map_err(|_| ParseError::InvalidPort(s.to_string()))
}

pub(crate) fn fingerprint(input: &str) -> String {
    use std::hash::{Hash, Hasher};
    let mut hasher = std::collections::hash_map::DefaultHasher::new();
    input.hash(&mut hasher);
    format!("{:x}", hasher.finish())
}
