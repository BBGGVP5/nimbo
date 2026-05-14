use serde_json::Value;

use crate::model::{
    Network, Protocol, Security, Server, ShadowsocksConfig, StreamSettings, TrojanConfig,
    VlessConfig, VmessConfig,
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
    let name = item
        .get("remarks")
        .and_then(|v| v.as_str())
        .map(str::to_string);
    let server_description = item
        .get("meta")
        .and_then(|m| m.get("serverDescription"))
        .and_then(|v| v.as_str())
        .map(str::to_string)
        .or_else(|| {
            item.get("serverDescription")
                .and_then(|v| v.as_str())
                .map(str::to_string)
        });
    let host_uuid = item
        .get("meta")
        .and_then(|m| m.get("hostUuid"))
        .and_then(|v| v.as_str())
        .map(str::to_string);
    let xray_json_template_uuid = item
        .get("meta")
        .and_then(|m| m.get("xrayJsonTemplateUuid"))
        .and_then(|v| v.as_str())
        .map(str::to_string);

    let item_index = out.len();
    for outbound in outbounds {
        if !is_proxy_outbound(outbound) {
            continue;
        }
        if let Some(mut server) = parse_outbound(outbound, item_index) {
            if let Some(name) = &name {
                if !name.trim().is_empty() {
                    server.name = name.clone();
                }
            }
            if server.server_description.is_none() {
                server.server_description = server_description.clone();
            }
            if server.host_uuid.is_none() {
                server.host_uuid = host_uuid.clone();
            }
            if server.xray_json_template_uuid.is_none() {
                server.xray_json_template_uuid = xray_json_template_uuid.clone();
            }
            out.push(server);
            return;
        }
    }
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
        "vless" | "vmess" | "trojan" | "shadowsocks"
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
