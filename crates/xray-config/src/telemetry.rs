//! Read-only cumulative traffic counters. Inbound and outbound values describe
//! the same bytes; they must never be added together.
use serde::Serialize;
use serde_json::Value;

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize)]
pub struct RouteTraffic {
    pub proxy_upload: u64,
    pub proxy_download: u64,
    pub direct_upload: u64,
    pub direct_download: u64,
}
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct TrafficCounters {
    pub upload: u64,
    pub download: u64,
    pub routes: Option<RouteTraffic>,
}

fn value(v: &Value) -> Option<u64> {
    v.as_u64().or_else(|| v.as_str()?.trim().parse().ok())
}

pub fn parse_traffic_counters(output: &str) -> Result<TrafficCounters, &'static str> {
    let json: Value = serde_json::from_str(output).map_err(|_| "INVALID_TRAFFIC_RESPONSE")?;
    let stats = json
        .get("stat")
        .and_then(Value::as_array)
        .ok_or("INVALID_TRAFFIC_RESPONSE")?;
    let (mut up, mut down, mut inbound_seen, mut route_seen, mut unknown_route) =
        (0u64, 0u64, false, false, false);
    let mut routes = RouteTraffic::default();
    for stat in stats {
        let Some(name) = stat.get("name").and_then(Value::as_str) else {
            continue;
        };
        let parts: Vec<_> = name.split(">>>").collect();
        if parts.len() != 4 || parts[2] != "traffic" || !matches!(parts[3], "uplink" | "downlink") {
            continue;
        }
        if matches!(
            parts[1],
            "api" | "dns" | "fragment" | "nimbo-ping" | "probe"
        ) {
            continue;
        }
        // Xray's CLI omits protobuf zero values (`omitempty`). A recognized
        // named counter with no value is measured zero, not a failed response.
        // Explicit null/negative/malformed values remain errors.
        let n = match stat.get("value") {
            None => 0,
            Some(raw) => value(raw).ok_or("INVALID_TRAFFIC_COUNTER")?,
        };
        let is_up = parts[3] == "uplink";
        match parts[0] {
            "inbound" => {
                inbound_seen = true;
                if is_up {
                    up = up.saturating_add(n);
                } else {
                    down = down.saturating_add(n);
                }
            }
            "outbound" => {
                let target = match (parts[1], is_up) {
                    ("proxy", true) => Some(&mut routes.proxy_upload),
                    ("proxy", false) => Some(&mut routes.proxy_download),
                    ("direct", true) => Some(&mut routes.direct_upload),
                    ("direct", false) => Some(&mut routes.direct_download),
                    ("block", _) => None,
                    _ => {
                        unknown_route = true;
                        None
                    }
                };
                if let Some(target) = target {
                    *target = target.saturating_add(n);
                    route_seen = true;
                }
            }
            _ => {}
        }
    }
    if !inbound_seen {
        up = routes.proxy_upload.saturating_add(routes.direct_upload);
        down = routes.proxy_download.saturating_add(routes.direct_download);
    }
    // Arbitrary full templates may use custom/nested outbound tags. Never label
    // a partial known-tag sample as a complete session route distribution.
    Ok(TrafficCounters {
        upload: up,
        download: down,
        routes: (route_seen && !unknown_route).then_some(routes),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn cli_omitted_zero_counters_preserve_nonzero_traffic() {
        let output = r#"{"stat":[
            {"name":"inbound>>>socks>>>traffic>>>uplink","value":128},
            {"name":"inbound>>>socks>>>traffic>>>downlink","value":512},
            {"name":"outbound>>>proxy>>>traffic>>>uplink","value":128},
            {"name":"outbound>>>proxy>>>traffic>>>downlink","value":512},
            {"name":"outbound>>>direct>>>traffic>>>uplink"},
            {"name":"outbound>>>direct>>>traffic>>>downlink"}
        ]}"#;
        let t = parse_traffic_counters(output).unwrap();
        assert_eq!((t.upload, t.download), (128, 512));
        assert_eq!(t.routes.unwrap(), RouteTraffic {
            proxy_upload: 128, proxy_download: 512,
            direct_upload: 0, direct_download: 0,
        });
        for raw in [serde_json::Value::Null, json!(false), json!("broken"), json!(-1)] {
            let output = json!({"stat":[{"name":"outbound>>>proxy>>>traffic>>>uplink","value":raw}]}).to_string();
            assert!(parse_traffic_counters(&output).is_err());
        }
        assert!(parse_traffic_counters("{}").is_err());
    }
    #[test]
    fn excludes_api_and_never_double_counts_route_bytes() {
        let data = json!({"stat":[
            {"name":"inbound>>>api>>>traffic>>>uplink","value":"9000"},
            {"name":"inbound>>>socks>>>traffic>>>uplink","value":"100"},
            {"name":"outbound>>>proxy>>>traffic>>>uplink","value":"70"},
            {"name":"outbound>>>direct>>>traffic>>>uplink","value":30}
        ]});
        let t = parse_traffic_counters(&data.to_string()).unwrap();
        assert_eq!(t.upload, 100);
        assert_eq!(
            t.routes.unwrap(),
            RouteTraffic {
                proxy_upload: 70,
                direct_upload: 30,
                ..Default::default()
            }
        );
    }
    #[test]
    fn custom_templates_do_not_report_a_partial_route_chart() {
        let data = json!({"stat":[{"name":"outbound>>>proxy>>>traffic>>>uplink","value":20},
            {"name":"outbound>>>foreign-node>>>traffic>>>uplink","value":60},
            {"name":"inbound>>>tun-in>>>traffic>>>uplink","value":80}]});
        let t = parse_traffic_counters(&data.to_string()).unwrap();
        assert_eq!(t.upload, 80);
        assert!(t.routes.is_none());
    }
    #[test]
    fn distinguishes_unavailable_from_measured_zero_and_rejects_negative() {
        assert!(parse_traffic_counters("{}").is_err());
        assert!(parse_traffic_counters(
            r#"{"stat":[{"name":"outbound>>>proxy>>>traffic>>>uplink","value":-1}]}"#
        )
        .is_err());
        let empty = parse_traffic_counters(r#"{"stat":[]}"#).unwrap();
        assert!(empty.routes.is_none());
        let zero = parse_traffic_counters(
            r#"{"stat":[{"name":"outbound>>>proxy>>>traffic>>>uplink","value":"0"}]}"#,
        )
        .unwrap();
        assert_eq!(zero.routes.unwrap().proxy_upload, 0);
    }
}
