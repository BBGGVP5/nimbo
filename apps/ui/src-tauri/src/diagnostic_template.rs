//! Select a logical subscription node without rebuilding its raw transport.
use nimbo_subscription::Server;
use serde_json::{json, Value};

fn proxy(out: &Value) -> bool {
    matches!(out["protocol"].as_str(), Some("vless" | "vmess" | "trojan" | "shadowsocks" | "hysteria2"))
}

fn identity(server: &Server) -> Option<Value> {
    // Normalize model defaults through the same generator/parser. For example,
    // an absent TCP header and an explicit {type:"none"} are the same route.
    // This is only matching; the chosen raw outbound is never regenerated.
    let outbound = nimbo_xray_config::outbound::server_to_outbound(server, "identity");
    let nodes = nimbo_subscription::parser::xray_json::parse_value(&json!({"outbounds":[outbound]})).ok()?;
    if nodes.len() != 1 { return None; }
    serde_json::to_value(&nodes[0].protocol).ok()
}

pub(crate) fn derive(server: &Server, template: &Value, base: &Value) -> Result<Value, String> {
    let unavailable = || "Selected node template route is ambiguous or unsupported; no direct fallback".to_string();
    let outbounds = template["outbounds"].as_array().ok_or_else(unavailable)?;
    // Parse each candidate separately only for identity. Preserve its ORIGINAL
    // JSON below (REALITY keys, flow, XHTTP extras, mux, sockopt, chains, etc.).
    let expected = identity(server).ok_or_else(unavailable)?;
    let candidates: Vec<_> = outbounds.iter().filter(|out| {
        proxy(out) && nimbo_subscription::parser::xray_json::parse_value(&json!({"outbounds":[out]}))
            .ok().is_some_and(|nodes| nodes.len() == 1 && identity(&nodes[0]).as_ref() == Some(&expected))
    }).collect();
    if candidates.len() != 1 { return Err(unavailable()); }
    let selected = candidates[0];
    let tag = selected["tag"].as_str().filter(|s| !s.is_empty()).ok_or_else(unavailable)?;
    let mut tags = std::collections::HashSet::new();
    for out in outbounds {
        let tag = out["tag"].as_str().filter(|s| !s.is_empty()).ok_or_else(unavailable)?;
        if !tags.insert(tag) { return Err(unavailable()); }
    }
    let balancers: Vec<_> = template["routing"]["balancers"].as_array().into_iter().flatten().filter(|b| {
        b["selector"].as_array().is_some_and(|selectors| selectors.iter().any(|s| s.as_str().is_some_and(|s| tag.starts_with(s))))
    }).collect();
    if balancers.len() > 1 { return Err(unavailable()); }
    let proxy_count = outbounds.iter().filter(|out| proxy(out)).count();
    if template["routing"]["balancers"].as_array().is_none_or(|items| items.is_empty())
        && nimbo_subscription::parser::xray_json::parse_value(template).ok().is_some_and(|nodes| nodes.len() < proxy_count) {
        // The subscription parser can collapse proxy-1/proxy-2 into a virtual
        // node even without a declared balancer. There is no route proof for
        // that logical group: do not silently test its first participant.
        return Err(unavailable());
    }
    let mut rule = base["routing"]["rules"][0].clone();
    rule.as_object_mut().ok_or_else(unavailable)?.remove("outboundTag");
    let mut routing = json!({"domainStrategy":"AsIs","rules":[]});
    if let Some(balancer) = balancers.first() {
        // Health-driven strategies need autonomous observatory traffic. Until
        // that has an equally isolated proof path, fail rather than guess first.
        if !matches!(balancer["strategy"]["type"].as_str(), None | Some("random" | "roundRobin")) {
            return Err("Selected balancer requires an unsupported isolated health strategy".into());
        }
        let selectors = balancer["selector"].as_array().ok_or_else(unavailable)?;
        for out in outbounds {
            if selectors.iter().any(|s| s.as_str().is_some_and(|s| out["tag"].as_str().unwrap_or("").starts_with(s))) && !proxy(out) {
                return Err(unavailable());
            }
        }
        if let Some(fallback) = balancer["fallbackTag"].as_str() {
            if !outbounds.iter().any(|out| out["tag"] == fallback && proxy(out)) { return Err(unavailable()); }
        }
        rule["balancerTag"] = balancer["tag"].as_str().filter(|s| !s.is_empty()).ok_or_else(unavailable)?.into();
        routing["balancers"] = json!([balancer]);
    } else {
        rule["outboundTag"] = tag.into();
    }
    routing["rules"] = json!([rule]);
    let mut config = base.clone();
    let block_tag = format!("nimbo-unmatched-{}", uuid::Uuid::new_v4());
    let mut preserved = vec![json!({"tag":block_tag,"protocol":"blackhole"})];
    preserved.extend(outbounds.iter().cloned());
    config["outbounds"] = preserved.into();
    config["routing"] = routing;
    // Never copy provider inbounds, TUN, system/API listeners, observatories or
    // URL-dependent routing. Only this private authenticated inbound may enter.
    for key in ["dns", "policy", "transport"] {
        if let Some(value) = template.get(key) { config[key] = value.clone(); }
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
        let server = nimbo_subscription::parser::xray_json::parse_value(&template).unwrap().remove(0);
        let base = json!({"inbounds":[{"tag":"private"}],"routing":{"rules":[{"type":"field","inboundTag":["private"],"outboundTag":"generated"}]}});
        (server,template,base)
    }
    #[test]
    fn retains_full_reality_transport_and_forces_exact_node_before_direct_rules() {
        let (server,template,base) = fixture();
        let config = derive(&server,&template,&base).unwrap();
        assert_eq!(config["outbounds"][2],template["outbounds"][1]);
        assert_eq!(config["dns"],template["dns"]);
        assert_eq!(config["routing"]["rules"][0]["outboundTag"],"selected");
        assert_eq!(config["routing"]["rules"].as_array().unwrap().len(),1);
        assert_eq!(config["outbounds"][0]["protocol"],"blackhole");
        assert_eq!(config["inbounds"],base["inbounds"]);
    }
    #[test]
    fn never_substitutes_first_foreign_or_ambiguous_outbound() {
        let (server,mut template,base) = fixture();
        template["outbounds"][1]["settings"]["vnext"][0]["port"] = 444.into();
        assert!(derive(&server,&template,&base).is_err());
        let (server,mut template,base) = fixture();
        let mut duplicate=template["outbounds"][1].clone();duplicate["tag"]="duplicate".into();
        template["outbounds"].as_array_mut().unwrap().push(duplicate);
        assert!(derive(&server,&template,&base).is_err());
    }
    #[test]
    fn virtual_balancer_retains_members_and_strategy_not_first_outbound() {
        let (server,mut template,base) = fixture();
        let mut member=template["outbounds"][1].clone();member["tag"]="selected-2".into();member["settings"]["vnext"][0]["port"]=444.into();
        template["outbounds"].as_array_mut().unwrap().push(member);
        template["routing"]["balancers"]=json!([{"tag":"virtual","selector":["selected"],"strategy":{"type":"roundRobin"}}]);
        let config=derive(&server,&template,&base).unwrap();
        assert_eq!(config["routing"]["rules"][0]["balancerTag"],"virtual");
        assert!(config["routing"]["rules"][0].get("outboundTag").is_none());
        assert_eq!(config["routing"]["balancers"],template["routing"]["balancers"]);
        template["routing"]["balancers"][0]["selector"]=json!([""]);
        assert!(derive(&server,&template,&base).is_err());
    }
    #[test]
    fn implicit_virtual_group_without_balancer_is_not_first_participant() {
        let (server,mut template,base)=fixture();
        template["outbounds"][1]["tag"]="proxy-1".into();
        let mut second=template["outbounds"][1].clone();second["tag"]="proxy-2".into();
        second["settings"]["vnext"][0]["port"]=444.into();
        template["outbounds"].as_array_mut().unwrap().push(second);
        assert_eq!(nimbo_subscription::parser::xray_json::parse_value(&template).unwrap().len(),1);
        assert!(derive(&server,&template,&base).is_err());
    }
}
