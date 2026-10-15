//! Lightweight opt-in runtime overlay, not an edit to subscriptions or profiles.
use serde_json::{json, Value};
pub const AD_DOMAINS: &[&str] = &[
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "googleads.g.doubleclick.net",
    "adservice.google.com",
    "ads.yahoo.com",
    "advertising.com",
    "adsrvr.org",
    "adnxs.com",
    "adform.net",
    "adroll.com",
    "taboola.com",
    "outbrain.com",
    "criteo.com",
    "criteo.net",
    "scorecardresearch.com",
    "quantserve.com",
    "ads.facebook.com",
    "app-measurement.com",
    "amazon-adsystem.com",
];
pub fn apply(config: &mut Value, enabled: bool) -> Result<(), &'static str> {
    if !enabled {
        return Ok(());
    }
    let object = config.as_object_mut().ok_or("INVALID_XRAY_CONFIG")?;
    if let Some(inbounds) = object.get_mut("inbounds").and_then(Value::as_array_mut) {
        for inbound in inbounds {
            if !matches!(inbound["protocol"].as_str(), Some("tun" | "socks" | "http")) {
                continue;
            }
            if inbound["tag"]
                .as_str()
                .is_some_and(|tag| tag.starts_with("nimbo-"))
            {
                continue; // Private diagnostics keep their authenticated route.
            }
            if let Some(inbound) = inbound.as_object_mut() {
                let sniffing = inbound.entry("sniffing").or_insert_with(|| json!({}));
                if !sniffing.is_object() {
                    *sniffing = json!({});
                }
                let sniffing = sniffing.as_object_mut().ok_or("INVALID_XRAY_SNIFFING")?;
                sniffing.insert("enabled".into(), json!(true));
                // Identify domains for routing only, keeping the original destination.
                sniffing.insert("routeOnly".into(), json!(true));
                sniffing.insert("destOverride".into(), json!(["http", "tls", "quic"]));
            }
        }
    }
    let outbounds = object
        .get_mut("outbounds")
        .and_then(Value::as_array_mut)
        .ok_or("INVALID_XRAY_OUTBOUNDS")?;
    // Reuse any actual blackhole, never assume a provider's 'block' tag has that protocol.
    let block = outbounds
        .iter()
        .find(|o| o["protocol"] == "blackhole")
        .and_then(|o| o["tag"].as_str())
        .map(str::to_owned)
        .unwrap_or_else(|| {
            let mut tag = "nimbo-ad-block".to_string();
            while outbounds.iter().any(|o| o["tag"] == tag) {
                tag.push('_');
            }
            outbounds.push(json!({"tag":tag,"protocol":"blackhole","settings":{}}));
            tag
        });
    let routing = object
        .entry("routing")
        .or_insert_with(|| json!({}))
        .as_object_mut()
        .ok_or("INVALID_XRAY_ROUTING")?;
    let rules = routing
        .entry("rules")
        .or_insert_with(|| json!([]))
        .as_array_mut()
        .ok_or("INVALID_XRAY_RULES")?;
    rules.insert(0, json!({"type":"field","domain":AD_DOMAINS.iter().map(|d|format!("domain:{d}")).collect::<Vec<_>>(),"outboundTag":block}));
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn off_preserves_provider_rules_and_on_wins_before_catchalls() {
        let original = json!({"outbounds":[{"tag":"block","protocol":"freedom"}],"routing":{"rules":[
            {"type":"field","domain":["domain:provider.invalid"],"outboundTag":"custom"},
            {"type":"field","network":"tcp,udp","outboundTag":"proxy"}]}});
        let mut cfg = original.clone();
        apply(&mut cfg, false).unwrap();
        assert_eq!(cfg, original);
        apply(&mut cfg, true).unwrap();
        assert_eq!(cfg["routing"]["rules"][0]["outboundTag"], "nimbo-ad-block");
        assert_eq!(cfg["routing"]["rules"][1], original["routing"]["rules"][0]);
        assert_eq!(
            cfg["routing"]["rules"][0]["domain"][0],
            "domain:doubleclick.net"
        );
        assert_eq!(cfg["outbounds"][0], original["outbounds"][0]);
    }
    #[test]
    fn never_reuses_a_non_blackhole_or_collides_with_user_tags() {
        let mut cfg = json!({"outbounds":[{"tag":"nimbo-ad-block","protocol":"freedom"}]});
        apply(&mut cfg, true).unwrap();
        assert_eq!(cfg["routing"]["rules"][0]["outboundTag"], "nimbo-ad-block_");
    }
    #[test]
    fn enables_runtime_domain_detection_without_changing_private_inbounds() {
        let mut cfg = json!({"inbounds":[
            {"tag":"tun-in","protocol":"tun","sniffing":{"enabled":false}},
            {"tag":"nimbo-health","protocol":"http","sniffing":{"enabled":false}}
        ],"outbounds":[]});
        let original = cfg.clone();
        apply(&mut cfg, true).unwrap();
        assert_eq!(cfg["inbounds"][0]["sniffing"]["enabled"], true);
        assert_eq!(cfg["inbounds"][0]["sniffing"]["routeOnly"], true);
        assert_eq!(cfg["inbounds"][1], original["inbounds"][1]);
    }
}
