use serde::{Deserialize, Serialize};

use crate::config::{TAG_API, TAG_BLOCK, TAG_DIRECT, TAG_PROXY};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Routing {
    #[serde(rename = "domainStrategy")]
    pub domain_strategy: String,
    pub rules: Vec<RoutingRule>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AppRoutingRule {
    pub process: String,
    pub mode: AppRoutingMode,
    pub enabled: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AppRoutingMode {
    Proxy,
    Direct,
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct RoutingProfileRules {
    pub domain_strategy: String,
    pub global_proxy: bool,
    pub bypass_local_ip: bool,
    pub rule_order: String,
    pub direct_domains: Vec<String>,
    pub direct_ips: Vec<String>,
    pub proxy_domains: Vec<String>,
    pub proxy_ips: Vec<String>,
    pub block_domains: Vec<String>,
    pub block_ips: Vec<String>,
}

const DOMAIN_ENTRY_PREFIX: &str = "__domain__:";

impl Routing {
    pub fn default_with_private_direct() -> Self {
        Self::with_rules(&[], None)
    }

    pub fn with_app_rules(app_rules: &[AppRoutingRule]) -> Self {
        Self::with_rules(app_rules, None)
    }

    pub fn with_rules(
        app_rules: &[AppRoutingRule],
        profile_rules: Option<&RoutingProfileRules>,
    ) -> Self {
        let mut rules = vec![RoutingRule {
            rule_type: "field".into(),
            inbound_tag: Some(vec!["api".into()]),
            outbound_tag: TAG_API.into(),
            ..Default::default()
        }];

        rules.extend(
            app_rules
                .iter()
                .filter(|rule| rule.enabled)
                .filter_map(|rule| {
                    let outbound_tag = match rule.mode {
                        AppRoutingMode::Proxy => TAG_PROXY.into(),
                        AppRoutingMode::Direct => TAG_DIRECT.into(),
                    };

                    if let Some(domain) = normalize_domain_matcher(&rule.process) {
                        return Some(RoutingRule {
                            rule_type: "field".into(),
                            domain: Some(vec![domain]),
                            outbound_tag,
                            ..Default::default()
                        });
                    }

                    let process = normalize_process_matchers(&rule.process);
                    if process.is_empty() {
                        return None;
                    }
                    Some(RoutingRule {
                        rule_type: "field".into(),
                        process: Some(process),
                        outbound_tag,
                        ..Default::default()
                    })
                }),
        );

        if let Some(profile) = profile_rules {
            append_profile_rules(&mut rules, profile);
        } else {
            append_private_direct_rules(&mut rules);
        }

        let fallback_tag = if profile_rules.is_some_and(|profile| !profile.global_proxy) {
            TAG_DIRECT
        } else {
            TAG_PROXY
        };
        rules.push(RoutingRule {
            rule_type: "field".into(),
            network: Some("tcp,udp".into()),
            outbound_tag: fallback_tag.into(),
            ..Default::default()
        });

        Self {
            domain_strategy: profile_rules
                .and_then(|profile| {
                    let strategy = profile.domain_strategy.trim();
                    (!strategy.is_empty()).then(|| strategy.to_string())
                })
                .unwrap_or_else(|| "IPIfNonMatch".into()),
            rules,
        }
    }
}

fn append_profile_rules(rules: &mut Vec<RoutingRule>, profile: &RoutingProfileRules) {
    for action in action_order(&profile.rule_order) {
        match action {
            "block" => {
                append_target_rules(rules, &profile.block_domains, &profile.block_ips, TAG_BLOCK)
            }
            "proxy" => {
                append_target_rules(rules, &profile.proxy_domains, &profile.proxy_ips, TAG_PROXY)
            }
            "direct" => append_target_rules(
                rules,
                &profile.direct_domains,
                &profile.direct_ips,
                TAG_DIRECT,
            ),
            _ => {}
        }
    }

    if profile.bypass_local_ip {
        append_private_direct_rules(rules);
    }
}

fn action_order(rule_order: &str) -> Vec<&'static str> {
    let mut out = Vec::new();
    for token in rule_order.split('-') {
        match token.trim().to_ascii_lowercase().as_str() {
            "block" if !out.contains(&"block") => out.push("block"),
            "proxy" if !out.contains(&"proxy") => out.push("proxy"),
            "direct" if !out.contains(&"direct") => out.push("direct"),
            _ => {}
        }
    }
    for fallback in ["block", "proxy", "direct"] {
        if !out.contains(&fallback) {
            out.push(fallback);
        }
    }
    out
}

fn append_target_rules(
    rules: &mut Vec<RoutingRule>,
    domain_candidates: &[String],
    ip_candidates: &[String],
    outbound_tag: &str,
) {
    let mut domains = Vec::new();
    let mut ips = Vec::new();

    for item in domain_candidates.iter().chain(ip_candidates) {
        if let Some(ip) = normalize_ip_matcher(item) {
            push_unique(&mut ips, ip);
        } else if let Some(domain) = normalize_profile_domain_matcher(item) {
            push_unique(&mut domains, domain);
        }
    }

    if !domains.is_empty() {
        rules.push(RoutingRule {
            rule_type: "field".into(),
            domain: Some(domains),
            outbound_tag: outbound_tag.into(),
            ..Default::default()
        });
    }

    if !ips.is_empty() {
        rules.push(RoutingRule {
            rule_type: "field".into(),
            ip: Some(ips),
            outbound_tag: outbound_tag.into(),
            ..Default::default()
        });
    }
}

fn append_private_direct_rules(rules: &mut Vec<RoutingRule>) {
    rules.extend([
        RoutingRule {
            rule_type: "field".into(),
            ip: Some(vec!["geoip:private".into()]),
            outbound_tag: TAG_DIRECT.into(),
            ..Default::default()
        },
        RoutingRule {
            rule_type: "field".into(),
            domain: Some(vec!["geosite:private".into()]),
            outbound_tag: TAG_DIRECT.into(),
            ..Default::default()
        },
    ]);
}

fn push_unique(items: &mut Vec<String>, value: String) {
    if !items.iter().any(|item| item == &value) {
        items.push(value);
    }
}

fn normalize_process_matchers(process: &str) -> Vec<String> {
    let normalized = process.trim().replace('\\', "/");
    if normalized.is_empty() {
        return Vec::new();
    }

    let mut out = vec![normalized.clone()];
    if let Some(file_name) = normalized
        .split('/')
        .next_back()
        .map(str::trim)
        .filter(|name| !name.is_empty())
    {
        push_unique(&mut out, file_name.to_string());
        if let Some(stem) = file_name.strip_suffix(".exe") {
            if !stem.is_empty() {
                push_unique(&mut out, stem.to_string());
            }
        }
    }
    out
}

fn normalize_domain_matcher(target: &str) -> Option<String> {
    let domain = target.trim().strip_prefix(DOMAIN_ENTRY_PREFIX)?.trim();
    let domain = domain
        .trim_start_matches("http://")
        .trim_start_matches("https://");
    let domain = domain.split('/').next().unwrap_or(domain);
    let domain = domain.trim_matches('.').to_ascii_lowercase();
    if domain.is_empty() {
        None
    } else {
        Some(format!("domain:{domain}"))
    }
}

fn normalize_profile_domain_matcher(target: &str) -> Option<String> {
    let value = target.trim();
    if value.is_empty() || normalize_ip_matcher(value).is_some() {
        return None;
    }
    if value.starts_with("domain:")
        || value.starts_with("geosite:")
        || value.starts_with("regexp:")
        || value.starts_with("keyword:")
        || value.starts_with("full:")
    {
        return Some(value.to_string());
    }

    let domain = value
        .trim_start_matches("http://")
        .trim_start_matches("https://")
        .split('/')
        .next()
        .unwrap_or(value)
        .trim_matches('.')
        .to_ascii_lowercase();
    (!domain.is_empty()).then(|| format!("domain:{domain}"))
}

fn normalize_ip_matcher(target: &str) -> Option<String> {
    let value = target.trim();
    if value.is_empty() {
        return None;
    }
    if value.starts_with("geoip:")
        || value.contains('/')
        || value.parse::<std::net::IpAddr>().is_ok()
    {
        return Some(value.to_string());
    }
    None
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct RoutingRule {
    #[serde(rename = "type")]
    pub rule_type: String,
    #[serde(skip_serializing_if = "Option::is_none", rename = "inboundTag")]
    pub inbound_tag: Option<Vec<String>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub ip: Option<Vec<String>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub domain: Option<Vec<String>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub port: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub network: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub process: Option<Vec<String>>,
    #[serde(rename = "outboundTag")]
    pub outbound_tag: String,
}
