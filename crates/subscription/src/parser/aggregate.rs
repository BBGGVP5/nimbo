use crate::model::Server;
use crate::parser::{
    ParseError, b64_decode_str, hysteria2, shadowsocks, trojan, vless, vmess, xray_json,
};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Format {
    Base64Aggregate,
    PlainList,
    XrayJson,
    SingboxJson,
    ClashYaml,
    Unknown,
}

pub fn detect_format(body: &str) -> Format {
    let trimmed = body.trim();
    if trimmed.is_empty() {
        return Format::Unknown;
    }

    if trimmed.starts_with('{') || trimmed.starts_with('[') {
        if let Ok(value) = serde_json::from_str::<serde_json::Value>(trimmed) {
            let probe = match &value {
                serde_json::Value::Array(items) => items.first(),
                serde_json::Value::Object(_) => Some(&value),
                _ => None,
            };
            if let Some(node) = probe {
                if node.get("outbounds").is_some() {
                    if has_singbox_marker(node) {
                        return Format::SingboxJson;
                    }
                    return Format::XrayJson;
                }
            }
        }
    }

    let stripped: String = trimmed.lines().map(|l| l.trim()).collect::<Vec<_>>().join("\n");
    if stripped.lines().any(|l| !l.is_empty() && looks_like_proxy_url(l)) {
        return Format::PlainList;
    }

    if trimmed.starts_with("proxies:") || trimmed.contains("\nproxies:") {
        return Format::ClashYaml;
    }

    if let Ok(decoded) = b64_decode_str(trimmed) {
        if decoded
            .lines()
            .any(|l| !l.is_empty() && looks_like_proxy_url(l.trim()))
        {
            return Format::Base64Aggregate;
        }
    }

    Format::Unknown
}

pub fn parse_aggregate(body: &str) -> Result<Vec<Server>, ParseError> {
    match detect_format(body) {
        Format::Base64Aggregate => {
            let decoded = b64_decode_str(body.trim())?;
            parse_plain_list(&decoded)
        }
        Format::PlainList => parse_plain_list(body),
        Format::XrayJson => xray_json::parse(body),
        Format::SingboxJson | Format::ClashYaml => Err(ParseError::InvalidUrl(
            "sing-box/clash subscription formats not yet supported".into(),
        )),
        Format::Unknown => Err(ParseError::Empty),
    }
}

fn parse_plain_list(text: &str) -> Result<Vec<Server>, ParseError> {
    let mut out = Vec::new();
    let mut last_err: Option<ParseError> = None;

    for raw_line in text.lines() {
        let line = raw_line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        match parse_single(line) {
            Ok(server) => out.push(server),
            Err(e) => {
                tracing::debug!(line = %line, err = ?e, "skipping unparseable line");
                last_err = Some(e);
            }
        }
    }

    if out.is_empty() {
        return Err(last_err.unwrap_or(ParseError::Empty));
    }
    Ok(out)
}

pub fn parse_single(line: &str) -> Result<Server, ParseError> {
    if line.starts_with("vless://") {
        vless::parse(line)
    } else if line.starts_with("vmess://") {
        vmess::parse(line)
    } else if line.starts_with("trojan://") {
        trojan::parse(line)
    } else if line.starts_with("ss://") {
        shadowsocks::parse(line)
    } else if line.starts_with("hysteria2://") || line.starts_with("hy2://") {
        hysteria2::parse(line)
    } else {
        Err(ParseError::UnsupportedScheme(
            line.split("://").next().unwrap_or("").to_string(),
        ))
    }
}

fn looks_like_proxy_url(line: &str) -> bool {
    line.starts_with("vless://")
        || line.starts_with("vmess://")
        || line.starts_with("trojan://")
        || line.starts_with("ss://")
        || line.starts_with("hysteria2://")
        || line.starts_with("hy2://")
        || line.starts_with("tuic://")
}

fn has_singbox_marker(v: &serde_json::Value) -> bool {
    let Some(outs) = v.get("outbounds").and_then(|x| x.as_array()) else {
        return false;
    };
    for o in outs {
        if let Some(ty) = o.get("type").and_then(|x| x.as_str()) {
            match ty {
                "vless" | "vmess" | "trojan" | "shadowsocks" | "hysteria2" | "tuic" | "selector"
                | "urltest" | "direct" | "block" | "dns" => return true,
                _ => {}
            }
        }
    }
    false
}

#[cfg(test)]
mod tests {
    use super::*;
    use base64::Engine;

    const VLESS_LINE: &str =
        "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@ex.com:443?type=tcp&security=tls#srv";
    const TROJAN_LINE: &str = "trojan://pwd@h.tld:443?security=tls&sni=h.tld#trojan-srv";
    const SS_LINE: &str = "ss://aes-256-gcm:p@h.tld:8388#ss-srv";
    const HY2_LINE: &str = "hysteria2://secret@hy.tld:443?sni=cdn.hy.tld#hy2-srv";

    #[test]
    fn detects_plain_list() {
        let body = format!("{VLESS_LINE}\n{TROJAN_LINE}\n");
        assert_eq!(detect_format(&body), Format::PlainList);
    }

    #[test]
    fn detects_base64_aggregate() {
        let body = format!("{VLESS_LINE}\n{TROJAN_LINE}\n{SS_LINE}\n");
        let encoded = base64::engine::general_purpose::STANDARD.encode(&body);
        assert_eq!(detect_format(&encoded), Format::Base64Aggregate);
    }

    #[test]
    fn detects_xray_json() {
        let body = r#"{"outbounds":[{"protocol":"vless","tag":"a"}]}"#;
        assert_eq!(detect_format(body), Format::XrayJson);
    }

    #[test]
    fn detects_singbox_json() {
        let body = r#"{"outbounds":[{"type":"vless","tag":"a"}]}"#;
        assert_eq!(detect_format(body), Format::SingboxJson);
    }

    #[test]
    fn parses_plain_list_aggregate() {
        let body = format!("{VLESS_LINE}\n{TROJAN_LINE}\n{SS_LINE}\n{HY2_LINE}\n");
        let servers = parse_aggregate(&body).unwrap();
        assert_eq!(servers.len(), 4);
    }

    #[test]
    fn parses_base64_aggregate() {
        let body = format!("{VLESS_LINE}\n{TROJAN_LINE}\n{SS_LINE}");
        let encoded = base64::engine::general_purpose::STANDARD.encode(&body);
        let servers = parse_aggregate(&encoded).unwrap();
        assert_eq!(servers.len(), 3);
    }

    #[test]
    fn skips_unparseable_lines_but_keeps_good_ones() {
        let body = format!("# comment\n\n{VLESS_LINE}\nnot-a-url\n{TROJAN_LINE}\n");
        let servers = parse_aggregate(&body).unwrap();
        assert_eq!(servers.len(), 2);
    }

    #[test]
    fn empty_returns_error() {
        let err = parse_aggregate("").unwrap_err();
        assert!(matches!(err, ParseError::Empty));
    }
}
