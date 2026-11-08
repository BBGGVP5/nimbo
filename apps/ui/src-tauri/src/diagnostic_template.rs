//! Select a logical subscription node without rebuilding its raw transport.
use nimbo_subscription::Server;
use serde_json::{json, Value};

fn proxy(out: &Value) -> bool {
    matches!(
        out["protocol"].as_str(),
        Some("vless" | "vmess" | "trojan" | "shadowsocks" | "hysteria" | "hysteria2")
    )
}

fn identity(server: &Server) -> Option<Value> {
    // Normalize model defaults through the same generator/parser. For example,
    // an absent TCP header and an explicit {type:"none"} are the same route.
    // This is only matching; the chosen raw outbound is never regenerated.
    let outbound = nimbo_xray_config::outbound::server_to_outbound(server, "identity");
    let nodes =
        nimbo_subscription::parser::xray_json::parse_value(&json!({"outbounds":[outbound]}))
            .ok()?;
    if nodes.len() != 1 {
        return None;
    }
    serde_json::to_value(&nodes[0].protocol).ok()
}

pub(crate) fn derive(
    server: &Server,
    template: &Value,
    base: &Value,
    probe_url: &str,
) -> Result<Value, String> {
    let unavailable = || {
        "Selected node template route is ambiguous or unsupported; no direct fallback".to_string()
    };
    let outbounds = template["outbounds"].as_array().ok_or_else(unavailable)?;
    // Parse each candidate separately only for identity. Preserve its ORIGINAL
    // JSON below (REALITY keys, flow, XHTTP extras, mux, sockopt, chains, etc.).
    let expected = identity(server).ok_or_else(unavailable)?;
    let candidates: Vec<_> = outbounds
        .iter()
        .filter(|out| {
            proxy(out)
                && nimbo_subscription::parser::xray_json::parse_value(&json!({"outbounds":[out]}))
                    .ok()
                    .is_some_and(|nodes| {
                        nodes.len() == 1 && identity(&nodes[0]).as_ref() == Some(&expected)
                    })
        })
        .collect();
    if candidates.len() != 1 {
        return Err(unavailable());
    }
    let selected = candidates[0];
    let tag = selected["tag"]
        .as_str()
        .filter(|s| !s.is_empty())
        .ok_or_else(unavailable)?;
    let mut tags = std::collections::HashSet::new();
    for out in outbounds {
        let tag = out["tag"]
            .as_str()
            .filter(|s| !s.is_empty())
            .ok_or_else(unavailable)?;
        if !tags.insert(tag) {
            return Err(unavailable());
        }
    }
    let balancers: Vec<_> = template["routing"]["balancers"]
        .as_array()
        .into_iter()
        .flatten()
        .filter(|b| {
            b["selector"].as_array().is_some_and(|selectors| {
                selectors
                    .iter()
                    .any(|s| s.as_str().is_some_and(|s| tag.starts_with(s)))
            })
        })
        .collect();
    if balancers.len() > 1 {
        return Err(unavailable());
    }
    let proxy_count = outbounds.iter().filter(|out| proxy(out)).count();
    if template["routing"]["balancers"]
        .as_array()
        .map_or(true, |items| items.is_empty())
        && nimbo_subscription::parser::xray_json::parse_value(template)
            .ok()
            .is_some_and(|nodes| nodes.len() < proxy_count)
    {
        // The subscription parser can collapse proxy-1/proxy-2 into a virtual
        // node even without a declared balancer. There is no route proof for
        // that logical group: do not silently test its first participant.
        return Err(unavailable());
    }
    let mut rule = base["routing"]["rules"][0].clone();
    rule.as_object_mut()
        .ok_or_else(unavailable)?
        .remove("outboundTag");
    let mut routing = json!({"domainStrategy":"AsIs","rules":[]});
    let block_tag = format!("nimbo-unmatched-{}", uuid::Uuid::new_v4());
    let mut observer = None;
    if let Some(balancer) = balancers.first() {
        // Preserve the native health strategy; never replace it with one member.
        // Only the bounded observer below may generate diagnostic health traffic.
        if !matches!(
            balancer["strategy"]["type"].as_str(),
            None | Some("random" | "roundRobin" | "leastPing")
        ) {
            return Err(
                "Selected balancer requires an unsupported isolated health strategy".into(),
            );
        }
        let selectors = balancer["selector"].as_array().ok_or_else(unavailable)?;
        for out in outbounds {
            if selectors.iter().any(|s| {
                s.as_str()
                    .is_some_and(|s| out["tag"].as_str().unwrap_or("").starts_with(s))
            }) && !proxy(out)
            {
                return Err(unavailable());
            }
        }
        if let Some(raw_fallback) = balancer.get("fallbackTag") {
            let fallback = raw_fallback
                .as_str()
                .filter(|s| !s.is_empty())
                .ok_or_else(unavailable)?;
            if !outbounds
                .iter()
                .any(|out| out["tag"] == fallback && (proxy(out) || out["protocol"] == "blackhole"))
            {
                return Err(unavailable());
            }
        }
        rule["balancerTag"] = balancer["tag"]
            .as_str()
            .filter(|s| !s.is_empty())
            .ok_or_else(unavailable)?
            .into();
        let mut isolated_balancer = (*balancer).clone();
        if balancer["strategy"]["type"] == "leastPing" {
            let url = url::Url::parse(probe_url).map_err(|_| unavailable())?;
            if !matches!(url.scheme(), "http" | "https")
                || url.host_str().is_none()
                || !url.username().is_empty()
                || url.password().is_some()
                || url.fragment().is_some()
            {
                return Err(unavailable());
            }
            let members: Vec<_> = outbounds
                .iter()
                .filter(|out| {
                    selectors.iter().any(|s| {
                        s.as_str()
                            .is_some_and(|s| out["tag"].as_str().unwrap_or("").starts_with(s))
                    })
                })
                .map(|out| out["tag"].clone())
                .collect();
            if members.is_empty() || members.len() > 16 {
                return Err(unavailable());
            }
            observer = Some(json!({"subjectSelector": members, "probeUrl":probe_url,
                "probeInterval":"1h", "enableConcurrency":true}));
            if balancer.get("fallbackTag").is_none() {
                isolated_balancer["fallbackTag"] = block_tag.clone().into();
            }
        }
        routing["balancers"] = json!([isolated_balancer]);
    } else {
        rule["outboundTag"] = tag.into();
    }
    routing["rules"] = json!([rule]);
    let mut config = base.clone();
    let mut preserved = vec![json!({"tag":block_tag,"protocol":"blackhole"})];
    preserved.extend(outbounds.iter().cloned());
    config["outbounds"] = preserved.into();
    config["routing"] = routing;
    if let Some(observer) = observer {
        config["observatory"] = observer;
    }
    // Never copy provider inbounds, TUN, system/API listeners, provider observatories or
    // URL-dependent routing. Only this private authenticated inbound may enter.
    for key in ["dns", "policy", "transport"] {
        if let Some(value) = template.get(key) {
            config[key] = value.clone();
        }
    }
    Ok(config)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn fixture() -> (Server, Value, Value) {
        let template = json!({"inbounds":[{"port":1080}],"dns":{"hosts":{"vpn.example":"127.0.0.1"}},
          "outbounds":[{"tag":"direct","protocol":"freedom"},
            {"tag":"selected","protocol":"vless","settings":{"vnext":[{"address":"vpn.example","port":443,"users":[{"id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","encryption":"none","flow":"xtls-rprx-vision"}]}]},
             "streamSettings":{"network":"tcp","security":"reality","realitySettings":{"serverName":"sni.example","publicKey":"public-key","shortId":"abcd","fingerprint":"chrome"},"sockopt":{"tcpKeepAliveIdle":37}},"mux":{"enabled":false}}],
          "routing":{"rules":[{"domain":["test.example"],"outboundTag":"direct"}]}});
        let server = nimbo_subscription::parser::xray_json::parse_value(&template)
            .unwrap()
            .remove(0);
        let base = json!({"inbounds":[{"tag":"private"}],"routing":{"rules":[{"type":"field","inboundTag":["private"],"outboundTag":"generated"}]}});
        (server, template, base)
    }
    #[test]
    fn probe_regression_hysteria_template_preserves_raw_transport() {
        let (_, mut template, base) = fixture();
        template["outbounds"][1] = json!({
            "tag":"selected", "protocol":"hysteria",
            "settings":{"version":2,"address":"vpn.example","port":443},
            "streamSettings":{"network":"hysteria","security":"tls",
                "hysteriaSettings":{"version":2,"auth":"fixture-password"},
                "tlsSettings":{"serverName":"vpn.example","alpn":["h3"]}}
        });
        let server = nimbo_subscription::parser::xray_json::parse_value(&template)
            .unwrap()
            .remove(0);
        let config = derive(&server, &template, &base, "https://probe.example/check").unwrap();
        assert_eq!(config["outbounds"][2], template["outbounds"][1]);
        assert_eq!(config["routing"]["rules"][0]["outboundTag"], "selected");
        assert_eq!(config["outbounds"][0]["protocol"], "blackhole");
    }

    #[test]
    fn retains_full_reality_transport_and_forces_exact_node_before_direct_rules() {
        let (server, template, base) = fixture();
        let config = derive(&server, &template, &base, "https://probe.example/check").unwrap();
        assert_eq!(config["outbounds"][2], template["outbounds"][1]);
        assert_eq!(config["dns"], template["dns"]);
        assert_eq!(config["routing"]["rules"][0]["outboundTag"], "selected");
        assert_eq!(config["routing"]["rules"].as_array().unwrap().len(), 1);
        assert_eq!(config["outbounds"][0]["protocol"], "blackhole");
        assert_eq!(config["inbounds"], base["inbounds"]);
    }
    #[test]
    fn never_substitutes_first_foreign_or_ambiguous_outbound() {
        let (server, mut template, base) = fixture();
        template["outbounds"][1]["settings"]["vnext"][0]["port"] = 444.into();
        assert!(derive(&server, &template, &base, "https://probe.example/check").is_err());
        let (server, mut template, base) = fixture();
        let mut duplicate = template["outbounds"][1].clone();
        duplicate["tag"] = "duplicate".into();
        template["outbounds"]
            .as_array_mut()
            .unwrap()
            .push(duplicate);
        assert!(derive(&server, &template, &base, "https://probe.example/check").is_err());
    }
    #[test]
    fn virtual_balancer_retains_members_and_strategy_not_first_outbound() {
        let (server, mut template, base) = fixture();
        let mut member = template["outbounds"][1].clone();
        member["tag"] = "selected-2".into();
        member["settings"]["vnext"][0]["port"] = 444.into();
        template["outbounds"].as_array_mut().unwrap().push(member);
        template["routing"]["balancers"] =
            json!([{"tag":"virtual","selector":["selected"],"strategy":{"type":"roundRobin"}}]);
        let config = derive(&server, &template, &base, "https://probe.example/check").unwrap();
        assert_eq!(config["routing"]["rules"][0]["balancerTag"], "virtual");
        assert!(config["routing"]["rules"][0].get("outboundTag").is_none());
        assert_eq!(
            config["routing"]["balancers"],
            template["routing"]["balancers"]
        );
        template["routing"]["balancers"][0]["selector"] = json!([""]);
        assert!(derive(&server, &template, &base, "https://probe.example/check").is_err());
    }
    #[test]
    fn least_ping_uses_private_observer_and_deny_fallback() {
        let (server, mut template, base) = fixture();
        let mut member = template["outbounds"][1].clone();
        member["tag"] = "selected-2".into();
        member["settings"]["vnext"][0]["port"] = 444.into();
        template["outbounds"].as_array_mut().unwrap().push(member);
        template["routing"]["balancers"] =
            json!([{"tag":"virtual","selector":["selected"],"strategy":{"type":"leastPing"}}]);
        template["observatory"] =
            json!({"subjectSelector":[""],"probeUrl":"https://foreign.example"});
        let config = derive(&server, &template, &base, "https://probe.example/check").unwrap();
        assert_eq!(config["routing"]["rules"][0]["balancerTag"], "virtual");
        assert_eq!(
            config["observatory"]["subjectSelector"],
            json!(["selected", "selected-2"])
        );
        assert_eq!(
            config["observatory"]["probeUrl"],
            "https://probe.example/check"
        );
        assert_eq!(config["observatory"]["enableConcurrency"], true);
        assert_eq!(
            config["routing"]["balancers"][0]["fallbackTag"],
            config["outbounds"][0]["tag"]
        );
        assert_eq!(config["outbounds"][0]["protocol"], "blackhole");
        assert!(config.get("burstObservatory").is_none());
        for bad_url in [
            "file:///tmp/secret",
            "https://user:pass@probe.example",
            "http://probe.example/#fragment",
        ] {
            assert!(derive(&server, &template, &base, bad_url).is_err());
        }
        template["routing"]["balancers"][0]["strategy"]["type"] = "leastLoad".into();
        assert!(derive(&server, &template, &base, "https://probe.example/check").is_err());
        template["routing"]["balancers"][0]["strategy"]["type"] = "leastPing".into();
        template["routing"]["balancers"][0]["fallbackTag"] = "direct".into();
        assert!(derive(&server, &template, &base, "https://probe.example/check").is_err());
    }

    #[test]
    fn implicit_virtual_group_without_balancer_is_not_first_participant() {
        let (server, mut template, base) = fixture();
        template["outbounds"][1]["tag"] = "proxy-1".into();
        let mut second = template["outbounds"][1].clone();
        second["tag"] = "proxy-2".into();
        second["settings"]["vnext"][0]["port"] = 444.into();
        template["outbounds"].as_array_mut().unwrap().push(second);
        assert_eq!(
            nimbo_subscription::parser::xray_json::parse_value(&template)
                .unwrap()
                .len(),
            1
        );
        assert!(derive(&server, &template, &base, "https://probe.example/check").is_err());
    }
}
