use serde_json::Value;

use crate::model::{
    Hysteria2Config, Network, Protocol, Security, Server, ShadowsocksConfig, StreamSettings,
    TrojanConfig, VlessConfig, VmessConfig,
};
use crate::parser::{ParseError, fingerprint};

pub fn parse(body: &str) -> Result<Vec<Server>, ParseError> {
    let root: Value =
        serde_json::from_str(body).map_err(|e| ParseError::InvalidJson(e.to_string()))?;
    parse_value(&root)
}

pub fn parse_value(root: &Value) -> Result<Vec<Server>, ParseError> {
    let mut out = Vec::new();
    match root {
        Value::Array(items) => {
            for item in items {
                collect_servers_from_item(item, &mut out);
            }
        }
        Value::Object(_) => {
            collect_servers_from_item(root, &mut out);
        }
        _ => return Err(ParseError::Empty),
    }
    if out.is_empty() {
        return Err(ParseError::Empty);
    }
    Ok(out)
}

fn collect_servers_from_item(item: &Value, out: &mut Vec<Server>) {
    let outbounds = match item.get("outbounds").and_then(|v| v.as_array()) {
        Some(o) => o,
        None => return,
    };
    let name = display_name(item);
    let server_description = metadata_string(
        item,
        &[
            "serverDescription",
            "server_description",
            "server-description",
            "serverDesc",
            "server_desc",
        ],
    );
    let host_uuid = metadata_string(item, &["hostUuid", "host_uuid", "host-uuid"]);
    let xray_json_template_uuid = metadata_string(
        item,
        &[
            "xrayJsonTemplateUuid",
            "xray_json_template_uuid",
            "xray-json-template-uuid",
        ],
    );

    let explicit_balancer_prefixes = explicit_balancer_prefixes(item);
    let implicit_balancer_base = if explicit_balancer_prefixes.is_empty() {
        implicit_balancer_base(outbounds)
    } else {
        None
    };

    let proxy_count = outbounds
        .iter()
        .filter(|outbound| is_proxy_outbound(outbound))
        .count();
    let mut proxy_index = 0usize;
    let mut seen_ids = std::collections::HashSet::new();
    let mut emitted_balancer_groups = std::collections::HashSet::new();
    for outbound in outbounds {
        if !is_proxy_outbound(outbound) {
            continue;
        }
        proxy_index += 1;
        let tag = outbound_tag(outbound);
        let balancer_group = tag.as_deref().and_then(|t| {
            match_balancer_group(t, &explicit_balancer_prefixes, implicit_balancer_base.as_deref())
        });
        // Balancer participants share one logical destination — keep only the
        // first one we see and skip the rest, so the autobalancer appears as a
        // single server entry instead of one per "proxy-N" outbound.
        if let Some(group) = &balancer_group {
            if !emitted_balancer_groups.insert(group.clone()) {
                continue;
            }
        }
        // Secondary outbounds without their own display name are internal proxy
        // chains. Skip them to avoid exposing internal routing entries as
        // user-visible servers.
        if balancer_group.is_none()
            && proxy_count > 1
            && proxy_index > 1
            && display_name(outbound).is_none()
            && tag.is_none()
        {
            continue;
        }
        let item_index = out.len();
        if let Some(mut server) = parse_outbound(outbound, item_index) {
            let outbound_server_description = metadata_string(
                outbound,
                &[
                    "serverDescription",
                    "server_description",
                    "server-description",
                    "serverDesc",
                    "server_desc",
                ],
            )
            .or_else(|| server_description.clone());
            let outbound_host_uuid = metadata_string(outbound, &["hostUuid", "host_uuid", "host-uuid"])
                .or_else(|| host_uuid.clone());
            let outbound_template_uuid = metadata_string(
                outbound,
                &[
                    "xrayJsonTemplateUuid",
                    "xray_json_template_uuid",
                    "xray-json-template-uuid",
                ],
            )
            .or_else(|| xray_json_template_uuid.clone());

            let resolved_name = display_name(outbound)
                .or_else(|| {
                    if balancer_group.is_some() || proxy_count <= 1 || proxy_index == 1 {
                        name.clone()
                    } else {
                        tag.clone().map(|tag| {
                            name.as_ref()
                                .map(|name| format!("{name} · {tag}"))
                                .unwrap_or(tag)
                        })
                    }
                });

            if let Some(name) = &resolved_name {
                if !name.trim().is_empty() {
                    server.name = name.clone();
                }
            }
            if server.server_description.is_none() {
                server.server_description = outbound_server_description;
            }
            if server.host_uuid.is_none() {
                server.host_uuid = outbound_host_uuid;
            }
            if server.xray_json_template_uuid.is_none() {
                server.xray_json_template_uuid = outbound_template_uuid;
            }
            server.id = stable_server_id(&server);
            if seen_ids.insert(server.id.clone()) {
                out.push(server);
            }
        }
    }
}

fn display_name(value: &Value) -> Option<String> {
    metadata_string(value, &["remarks", "remark", "ps", "name", "title"])
}

fn explicit_balancer_prefixes(item: &Value) -> Vec<String> {
    let Some(balancers) = item
        .get("routing")
        .and_then(|routing| routing.get("balancers"))
        .and_then(Value::as_array)
    else {
        return Vec::new();
    };
    let mut prefixes: Vec<String> = Vec::new();
    for balancer in balancers {
        let Some(selectors) = balancer.get("selector").and_then(Value::as_array) else {
            continue;
        };
        for selector in selectors {
            if let Some(raw) = selector.as_str() {
                let trimmed = raw.trim();
                if !trimmed.is_empty() && !prefixes.iter().any(|existing| existing == trimmed) {
                    prefixes.push(trimmed.to_string());
                }
            }
        }
    }
    prefixes
}

fn implicit_balancer_base(outbounds: &[Value]) -> Option<String> {
    let proxy_outbounds: Vec<&Value> = outbounds.iter().filter(|o| is_proxy_outbound(o)).collect();
    if proxy_outbounds.len() < 2 {
        return None;
    }
    let tags: Vec<String> = proxy_outbounds.iter().filter_map(|o| outbound_tag(o)).collect();
    if tags.len() != proxy_outbounds.len() {
        return None;
    }

    let base = tags.first()?.split('-').next()?.to_string();
    if base.is_empty() {
        return None;
    }

    let suffix_prefix = format!("{base}-");
    let mut has_numeric_suffix = false;
    for tag in &tags {
        if tag == &base {
            continue;
        }
        match tag.strip_prefix(&suffix_prefix) {
            Some(suffix) if !suffix.is_empty() && suffix.chars().all(|c| c.is_ascii_digit()) => {
                has_numeric_suffix = true;
            }
            _ => return None,
        }
    }

    has_numeric_suffix.then_some(base)
}

fn match_balancer_group(
    tag: &str,
    explicit_prefixes: &[String],
    implicit_base: Option<&str>,
) -> Option<String> {
    for prefix in explicit_prefixes {
        if tag.starts_with(prefix.as_str()) {
            return Some(prefix.clone());
        }
    }
    if let Some(base) = implicit_base {
        if tag == base {
            return Some(base.to_string());
        }
        if let Some(suffix) = tag.strip_prefix(&format!("{base}-")) {
            if !suffix.is_empty() && suffix.chars().all(|c| c.is_ascii_digit()) {
                return Some(base.to_string());
            }
        }
    }
    None
}

fn outbound_tag(value: &Value) -> Option<String> {
    value
        .get("tag")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|tag| !tag.is_empty())
        .map(ToString::to_string)
}

fn metadata_string(value: &Value, keys: &[&str]) -> Option<String> {
    field_string(value, keys)
        .or_else(|| nested_field_string(value, "meta", keys))
        .or_else(|| nested_field_string(value, "clientOverrides", keys))
        .or_else(|| nested_field_string(value, "client_overrides", keys))
}

fn nested_field_string(value: &Value, object_key: &str, keys: &[&str]) -> Option<String> {
    get_case_insensitive(value, object_key).and_then(|nested| field_string(nested, keys))
}

fn field_string(value: &Value, keys: &[&str]) -> Option<String> {
    for key in keys {
        if let Some(text) = get_case_insensitive(value, key).and_then(Value::as_str) {
            let trimmed = text.trim();
            if !trimmed.is_empty() {
                return Some(trimmed.to_string());
            }
        }
    }
    None
}

fn get_case_insensitive<'a>(value: &'a Value, key: &str) -> Option<&'a Value> {
    let Value::Object(map) = value else {
        return None;
    };
    map.get(key).or_else(|| {
        let normalized = normalize_json_key(key);
        map.iter()
            .find(|(candidate, _)| normalize_json_key(candidate) == normalized)
            .map(|(_, value)| value)
    })
}

fn normalize_json_key(value: &str) -> String {
    value
        .chars()
        .filter(|ch| *ch != '-' && *ch != '_')
        .flat_map(char::to_lowercase)
        .collect()
}

fn is_proxy_outbound(outbound: &Value) -> bool {
    let tag = outbound.get("tag").and_then(|v| v.as_str()).unwrap_or("");
    if tag.eq_ignore_ascii_case("direct")
        || tag.eq_ignore_ascii_case("block")
        || tag.eq_ignore_ascii_case("dns")
        || tag.eq_ignore_ascii_case("blackhole")
    {
        return false;
    }
    matches!(
        outbound
            .get("protocol")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_ascii_lowercase()
            .as_str(),
        "vless" | "vmess" | "trojan" | "shadowsocks" | "hysteria" | "hysteria2"
    )
}

fn parse_outbound(outbound: &Value, item_index: usize) -> Option<Server> {
    let protocol_name = outbound.get("protocol").and_then(|v| v.as_str())?;
    let settings = outbound.get("settings")?;
    let stream_settings = outbound.get("streamSettings");

    let (protocol, address, port) = match protocol_name.to_ascii_lowercase().as_str() {
        "vless" => parse_vless(settings, stream_settings)?,
        "vmess" => parse_vmess(settings, stream_settings)?,
        "trojan" => parse_trojan(settings, stream_settings)?,
        "shadowsocks" => parse_shadowsocks(settings)?,
        "hysteria" | "hysteria2" => parse_hysteria2(settings, stream_settings)?,
        _ => return None,
    };

    let fingerprint_input = format!(
        "{}::{}::{}::{}",
        item_index,
        protocol_name,
        address,
        port
    );
    Some(Server {
        id: fingerprint(&fingerprint_input),
        name: format!("{protocol_name}-{address}:{port}"),
        server_description: None,
        host_uuid: None,
        xray_json_template_uuid: None,
        protocol,
    })
}

fn stable_server_id(server: &Server) -> String {
    if let Some(host_uuid) = server.host_uuid.as_deref().filter(|value| !value.trim().is_empty()) {
        return fingerprint(&format!("xray-json:host:{host_uuid}"));
    }

    let identity = protocol_identity(&server.protocol);
    if let Some(template_uuid) = server
        .xray_json_template_uuid
        .as_deref()
        .filter(|value| !value.trim().is_empty())
    {
        return fingerprint(&format!(
            "xray-json:template:{template_uuid}:{identity}:{}",
            server.name
        ));
    }

    fingerprint(&format!("xray-json:item:{identity}:{}", server.name))
}

fn protocol_identity(protocol: &Protocol) -> String {
    match protocol {
        Protocol::Vless(config) => format!(
            "vless:{}:{}:{}:{:?}",
            config.address, config.port, config.uuid, config.stream
        ),
        Protocol::Vmess(config) => format!(
            "vmess:{}:{}:{}:{:?}",
            config.address, config.port, config.uuid, config.stream
        ),
        Protocol::Trojan(config) => format!(
            "trojan:{}:{}:{}:{:?}",
            config.address, config.port, config.password, config.stream
        ),
        Protocol::Shadowsocks(config) => format!(
            "shadowsocks:{}:{}:{}:{}",
            config.address, config.port, config.method, config.password
        ),
        Protocol::Hysteria2(config) => format!(
            "hysteria2:{}:{}:{}:{}:{:?}:{}",
            config.address, config.port, config.password, config.sni.as_deref().unwrap_or_default(), config.alpn, config.insecure
        ),
    }
}

fn parse_vless(
    settings: &Value,
    stream_settings: Option<&Value>,
) -> Option<(Protocol, String, u16)> {
    let vnext = settings.get("vnext")?.as_array()?.first()?;
    let address = vnext.get("address")?.as_str()?.to_string();
    let port = port_from_value(vnext.get("port")?)?;
    let user = vnext.get("users")?.as_array()?.first()?;
    let uuid = user.get("id")?.as_str()?.to_string();
    let flow = user
        .get("flow")
        .and_then(|v| v.as_str())
        .map(str::to_string)
        .filter(|s| !s.is_empty());
    let encryption = user
        .get("encryption")
        .and_then(|v| v.as_str())
        .map(str::to_string)
        .unwrap_or_else(|| "none".into());

    let stream = build_stream(stream_settings);

    Some((
        Protocol::Vless(VlessConfig {
            address: address.clone(),
            port,
            uuid,
            flow,
            encryption,
            stream,
        }),
        address,
        port,
    ))
}

fn parse_vmess(
    settings: &Value,
    stream_settings: Option<&Value>,
) -> Option<(Protocol, String, u16)> {
    let vnext = settings.get("vnext")?.as_array()?.first()?;
    let address = vnext.get("address")?.as_str()?.to_string();
    let port = port_from_value(vnext.get("port")?)?;
    let user = vnext.get("users")?.as_array()?.first()?;
    let uuid = user.get("id")?.as_str()?.to_string();
    let alter_id = user
        .get("alterId")
        .and_then(|v| v.as_u64())
        .unwrap_or(0) as u32;
    let security = user
        .get("security")
        .and_then(|v| v.as_str())
        .map(str::to_string)
        .unwrap_or_else(|| "auto".into());

    let stream = build_stream(stream_settings);

    Some((
        Protocol::Vmess(VmessConfig {
            address: address.clone(),
            port,
            uuid,
            alter_id,
            security,
            stream,
        }),
        address,
        port,
    ))
}

fn parse_trojan(
    settings: &Value,
    stream_settings: Option<&Value>,
) -> Option<(Protocol, String, u16)> {
    let server = settings.get("servers")?.as_array()?.first()?;
    let address = server.get("address")?.as_str()?.to_string();
    let port = port_from_value(server.get("port")?)?;
    let password = server.get("password")?.as_str()?.to_string();

    let stream = build_stream(stream_settings);

    Some((
        Protocol::Trojan(TrojanConfig {
            address: address.clone(),
            port,
            password,
            stream,
        }),
        address,
        port,
    ))
}

fn parse_shadowsocks(settings: &Value) -> Option<(Protocol, String, u16)> {
    let server = settings.get("servers")?.as_array()?.first()?;
    let address = server.get("address")?.as_str()?.to_string();
    let port = port_from_value(server.get("port")?)?;
    let method = server.get("method")?.as_str()?.to_string();
    let password = server.get("password")?.as_str()?.to_string();

    Some((
        Protocol::Shadowsocks(ShadowsocksConfig {
            address: address.clone(),
            port,
            method,
            password,
        }),
        address,
        port,
    ))
}

fn parse_hysteria2(
    settings: &Value,
    stream_settings: Option<&Value>,
) -> Option<(Protocol, String, u16)> {
    let address = settings.get("address")?.as_str()?.to_string();
    let port = port_from_value(settings.get("port")?)?;
    let hysteria = stream_settings.and_then(|stream| stream.get("hysteriaSettings"));
    let tls = stream_settings.and_then(|stream| stream.get("tlsSettings"));
    let password = hysteria
        .and_then(|value| value.get("auth"))
        .and_then(Value::as_str)
        .or_else(|| settings.get("password").and_then(Value::as_str))
        .unwrap_or_default()
        .to_string();
    let sni = tls
        .and_then(|value| value.get("serverName"))
        .and_then(Value::as_str)
        .map(str::to_string);
    let alpn = tls
        .and_then(|value| value.get("alpn"))
        .and_then(Value::as_array)
        .map(|items| {
            items
                .iter()
                .filter_map(|item| item.as_str().map(str::to_string))
                .collect::<Vec<_>>()
        })
        .filter(|items| !items.is_empty());
    let insecure = tls
        .and_then(|value| value.get("allowInsecure"))
        .and_then(Value::as_bool)
        .unwrap_or(false);

    Some((
        Protocol::Hysteria2(Hysteria2Config {
            address: address.clone(),
            port,
            password,
            sni,
            alpn,
            insecure,
            obfs: None,
            obfs_password: None,
        }),
        address,
        port,
    ))
}

fn build_stream(stream_settings: Option<&Value>) -> StreamSettings {
    let Some(stream) = stream_settings else {
        return StreamSettings::default();
    };

    let network = stream
        .get("network")
        .and_then(|v| v.as_str())
        .and_then(Network::from_xray_str)
        .unwrap_or_default();
    let security = stream
        .get("security")
        .and_then(|v| v.as_str())
        .and_then(Security::from_xray_str)
        .unwrap_or_default();

    let reality = stream.get("realitySettings");
    let tls = stream.get("tlsSettings");
    let ws = stream.get("wsSettings");
    let h2 = stream.get("httpSettings");
    let xhttp = stream
        .get("xhttpSettings")
        .or_else(|| stream.get("splithttpSettings"));
    let grpc = stream.get("grpcSettings");
    let httpupgrade = stream.get("httpupgradeSettings");
    let tcp = stream.get("tcpSettings");

    let sni = reality
        .and_then(|v| v.get("serverName"))
        .and_then(|v| v.as_str())
        .or_else(|| tls.and_then(|v| v.get("serverName")).and_then(|v| v.as_str()))
        .map(str::to_string);
    let fingerprint = reality
        .and_then(|v| v.get("fingerprint"))
        .and_then(|v| v.as_str())
        .or_else(|| {
            tls.and_then(|v| v.get("fingerprint"))
                .and_then(|v| v.as_str())
        })
        .map(str::to_string);
    let alpn = tls
        .and_then(|v| v.get("alpn"))
        .and_then(|v| v.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|x| x.as_str().map(str::to_string))
                .collect()
        });
    let public_key = reality
        .and_then(|v| v.get("publicKey"))
        .and_then(|v| v.as_str())
        .map(str::to_string);
    let short_id = reality
        .and_then(|v| v.get("shortId"))
        .and_then(|v| v.as_str())
        .map(str::to_string);
    let spider_x = reality
        .and_then(|v| v.get("spiderX"))
        .and_then(|v| v.as_str())
        .map(str::to_string);

    let host = ws
        .and_then(|v| v.get("headers"))
        .and_then(|v| v.get("Host"))
        .and_then(|v| v.as_str())
        .or_else(|| xhttp.and_then(|v| v.get("host")).and_then(|v| v.as_str()))
        .or_else(|| httpupgrade.and_then(|v| v.get("host")).and_then(|v| v.as_str()))
        .or_else(|| {
            h2.and_then(|v| v.get("host"))
                .and_then(|v| v.as_array())
                .and_then(|arr| arr.first())
                .and_then(|v| v.as_str())
        })
        .map(str::to_string);
    let path = ws
        .and_then(|v| v.get("path"))
        .and_then(|v| v.as_str())
        .or_else(|| xhttp.and_then(|v| v.get("path")).and_then(|v| v.as_str()))
        .or_else(|| {
            httpupgrade
                .and_then(|v| v.get("path"))
                .and_then(|v| v.as_str())
        })
        .or_else(|| h2.and_then(|v| v.get("path")).and_then(|v| v.as_str()))
        .map(str::to_string);
    let mode = xhttp
        .and_then(|v| v.get("mode"))
        .and_then(|v| v.as_str())
        .map(str::to_string);
    let extra = xhttp
        .and_then(|v| v.get("extra"))
        .map(|v| v.to_string());
    let service_name = grpc
        .and_then(|v| v.get("serviceName"))
        .and_then(|v| v.as_str())
        .map(str::to_string);
    let header_type = tcp
        .and_then(|v| v.get("header"))
        .and_then(|v| v.get("type"))
        .and_then(|v| v.as_str())
        .map(str::to_string);

    StreamSettings {
        network,
        security,
        host,
        path,
        sni,
        fingerprint,
        alpn,
        public_key,
        short_id,
        spider_x,
        mode,
        extra,
        header_type,
        service_name,
    }
}

fn port_from_value(value: &Value) -> Option<u16> {
    if let Some(n) = value.as_u64() {
        u16::try_from(n).ok()
    } else if let Some(s) = value.as_str() {
        s.parse::<u16>().ok()
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_remnawave_array_with_meta() {
        let body = r#"[
            {
                "outbounds": [
                    {
                        "tag": "proxy",
                        "protocol": "vless",
                        "settings": {
                            "vnext": [{
                                "address": "ams.example.com",
                                "port": 443,
                                "users": [{
                                    "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                                    "encryption": "none",
                                    "flow": "xtls-rprx-vision"
                                }]
                            }]
                        },
                        "streamSettings": {
                            "network": "tcp",
                            "security": "reality",
                            "realitySettings": {
                                "serverName": "google.com",
                                "publicKey": "KEY",
                                "shortId": "ABCD",
                                "fingerprint": "chrome"
                            },
                            "tcpSettings": {}
                        }
                    },
                    { "tag": "direct", "protocol": "freedom" },
                    { "tag": "block", "protocol": "blackhole" }
                ],
                "remarks": "🇳🇱 Нидерланды",
                "meta": { "serverDescription": "Автобалансер ⚡· VLESS 🌏" }
            }
        ]"#;
        let servers = parse(body).unwrap();
        assert_eq!(servers.len(), 1);
        let s = &servers[0];
        assert_eq!(s.name, "🇳🇱 Нидерланды");
        assert_eq!(
            s.server_description.as_deref(),
            Some("Автобалансер ⚡· VLESS 🌏")
        );
        match &s.protocol {
            Protocol::Vless(v) => {
                assert_eq!(v.address, "ams.example.com");
                assert_eq!(v.port, 443);
                assert_eq!(v.uuid, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
                assert_eq!(v.flow.as_deref(), Some("xtls-rprx-vision"));
                assert_eq!(v.stream.network, Network::Tcp);
                assert_eq!(v.stream.security, Security::Reality);
                assert_eq!(v.stream.sni.as_deref(), Some("google.com"));
                assert_eq!(v.stream.public_key.as_deref(), Some("KEY"));
                assert_eq!(v.stream.short_id.as_deref(), Some("ABCD"));
                assert_eq!(v.stream.fingerprint.as_deref(), Some("chrome"));
            }
            _ => panic!("expected vless"),
        }
    }

    #[test]
    fn parses_single_object_xray_config() {
        let body = r#"{
            "outbounds": [
                {
                    "tag": "proxy",
                    "protocol": "trojan",
                    "settings": {
                        "servers": [{
                            "address": "trojan.example.com",
                            "port": 443,
                            "password": "secret"
                        }]
                    },
                    "streamSettings": {
                        "network": "ws",
                        "security": "tls",
                        "wsSettings": { "path": "/api", "headers": { "Host": "cdn.example.com" } },
                        "tlsSettings": { "serverName": "cdn.example.com" }
                    }
                }
            ]
        }"#;
        let servers = parse(body).unwrap();
        assert_eq!(servers.len(), 1);
        match &servers[0].protocol {
            Protocol::Trojan(t) => {
                assert_eq!(t.address, "trojan.example.com");
                assert_eq!(t.port, 443);
                assert_eq!(t.password, "secret");
                assert_eq!(t.stream.network, Network::Ws);
                assert_eq!(t.stream.security, Security::Tls);
                assert_eq!(t.stream.path.as_deref(), Some("/api"));
                assert_eq!(t.stream.host.as_deref(), Some("cdn.example.com"));
                assert_eq!(t.stream.sni.as_deref(), Some("cdn.example.com"));
            }
            _ => panic!("expected trojan"),
        }
    }

    #[test]
    fn parses_outbound_level_metadata() {
        let body = r#"{
            "outbounds": [
                {
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "meta.example.com",
                            "port": 443,
                            "users": [{
                                "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                                "encryption": "none"
                            }]
                        }]
                    },
                    "meta": {
                        "serverDescription": "Outbound custom description",
                        "hostUuid": "host-1",
                        "xrayJsonTemplateUuid": "tpl-1"
                    }
                }
            ]
        }"#;
        let servers = parse(body).unwrap();
        assert_eq!(servers.len(), 1);
        let server = &servers[0];
        assert_eq!(
            server.server_description.as_deref(),
            Some("Outbound custom description")
        );
        assert_eq!(server.host_uuid.as_deref(), Some("host-1"));
        assert_eq!(server.xray_json_template_uuid.as_deref(), Some("tpl-1"));
    }

    #[test]
    fn collapses_autobalancer_proxy_outbounds_into_one_server() {
        let body = r#"{
            "remarks": "Bundle",
            "outbounds": [
                {
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "a.example.com",
                            "port": 443,
                            "users": [{ "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" }]
                        }]
                    }
                },
                {
                    "tag": "proxy-2",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "b.example.com",
                            "port": 443,
                            "users": [{ "id": "bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee" }]
                        }]
                    }
                }
            ]
        }"#;

        let servers = parse(body).unwrap();

        assert_eq!(servers.len(), 1);
        assert_eq!(servers[0].name, "Bundle");
    }

    #[test]
    fn collapses_explicit_balancer_participants_into_one_server() {
        let body = r#"{
            "remarks": "Автобалансер EU",
            "outbounds": [
                {
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "a.example.com",
                            "port": 443,
                            "users": [{ "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" }]
                        }]
                    }
                },
                {
                    "tag": "proxy-2",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "b.example.com",
                            "port": 443,
                            "users": [{ "id": "bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee" }]
                        }]
                    }
                },
                {
                    "tag": "proxy-3",
                    "protocol": "shadowsocks",
                    "settings": {
                        "servers": [{
                            "address": "ss.example.com",
                            "port": 8388,
                            "method": "chacha20-ietf-poly1305",
                            "password": "p"
                        }]
                    }
                }
            ],
            "routing": {
                "balancers": [
                    { "tag": "balancer", "selector": ["proxy"] }
                ]
            }
        }"#;

        let servers = parse(body).unwrap();

        assert_eq!(servers.len(), 1);
        assert_eq!(servers[0].name, "Автобалансер EU");
    }

    #[test]
    fn keeps_distinct_outbounds_when_tags_do_not_match_balancer_pattern() {
        let body = r#"{
            "remarks": "Mixed",
            "outbounds": [
                {
                    "tag": "proxy-eu",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "a.example.com",
                            "port": 443,
                            "users": [{ "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" }]
                        }]
                    }
                },
                {
                    "tag": "proxy-us",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "b.example.com",
                            "port": 443,
                            "users": [{ "id": "bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee" }]
                        }]
                    }
                }
            ]
        }"#;

        let servers = parse(body).unwrap();

        assert_eq!(servers.len(), 2);
    }

    #[test]
    fn server_id_is_stable_when_xray_json_order_changes() {
        let first = r#"[
            {
                "remarks": "A",
                "meta": { "hostUuid": "host-a" },
                "outbounds": [{
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "a.example.com",
                            "port": 443,
                            "users": [{ "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" }]
                        }]
                    }
                }]
            },
            {
                "remarks": "B",
                "meta": { "hostUuid": "host-b" },
                "outbounds": [{
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "b.example.com",
                            "port": 443,
                            "users": [{ "id": "bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee" }]
                        }]
                    }
                }]
            }
        ]"#;
        let second = r#"[
            {
                "remarks": "B",
                "meta": { "hostUuid": "host-b" },
                "outbounds": [{
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "b.example.com",
                            "port": 443,
                            "users": [{ "id": "bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee" }]
                        }]
                    }
                }]
            },
            {
                "remarks": "A",
                "meta": { "hostUuid": "host-a" },
                "outbounds": [{
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "a.example.com",
                            "port": 443,
                            "users": [{ "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" }]
                        }]
                    }
                }]
            }
        ]"#;

        let servers_a = parse(first).unwrap();
        let servers_b = parse(second).unwrap();
        let a_id = servers_a.iter().find(|server| server.name == "A").unwrap().id.clone();
        let a_id_after_reorder = servers_b.iter().find(|server| server.name == "A").unwrap().id.clone();

        assert_eq!(a_id, a_id_after_reorder);
    }

    #[test]
    fn skips_items_without_proxy_outbound() {
        let body = r#"[
            { "outbounds": [{ "tag": "direct", "protocol": "freedom" }] },
            {
                "outbounds": [{
                    "tag": "proxy",
                    "protocol": "shadowsocks",
                    "settings": {
                        "servers": [{
                            "address": "ss.example.com",
                            "port": 8388,
                            "method": "chacha20-ietf-poly1305",
                            "password": "p"
                        }]
                    }
                }],
                "remarks": "SS Node"
            }
        ]"#;
        let servers = parse(body).unwrap();
        assert_eq!(servers.len(), 1);
        assert_eq!(servers[0].name, "SS Node");
        match &servers[0].protocol {
            Protocol::Shadowsocks(ss) => {
                assert_eq!(ss.address, "ss.example.com");
                assert_eq!(ss.port, 8388);
                assert_eq!(ss.method, "chacha20-ietf-poly1305");
                assert_eq!(ss.password, "p");
            }
            _ => panic!("expected shadowsocks"),
        }
    }

    #[test]
    fn empty_array_errors() {
        assert!(matches!(parse("[]").unwrap_err(), ParseError::Empty));
    }
}
