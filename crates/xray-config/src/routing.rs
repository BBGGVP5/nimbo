use serde::{Deserialize, Serialize};

use crate::config::{TAG_API, TAG_DIRECT, TAG_PROXY};

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

impl Routing {
    pub fn default_with_private_direct() -> Self {
        Self::with_app_rules(&[])
    }

    pub fn with_app_rules(app_rules: &[AppRoutingRule]) -> Self {
        let mut rules = vec![
            RoutingRule {
                rule_type: "field".into(),
                inbound_tag: Some(vec!["api".into()]),
                outbound_tag: TAG_API.into(),
                ..Default::default()
            },
        ];

        rules.extend(app_rules.iter().filter(|rule| rule.enabled).filter_map(|rule| {
            let process = normalize_process_matcher(&rule.process);
            if process.is_empty() {
                return None;
            }
            Some(RoutingRule {
                rule_type: "field".into(),
                process: Some(vec![process]),
                outbound_tag: match rule.mode {
                    AppRoutingMode::Proxy => TAG_PROXY.into(),
                    AppRoutingMode::Direct => TAG_DIRECT.into(),
                },
                ..Default::default()
            })
        }));

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
            RoutingRule {
                rule_type: "field".into(),
                network: Some("tcp,udp".into()),
                outbound_tag: TAG_PROXY.into(),
                ..Default::default()
            },
        ]);

        Self {
            domain_strategy: "IPIfNonMatch".into(),
            rules,
        }
    }
}

fn normalize_process_matcher(process: &str) -> String {
    process.trim().replace('\\', "/")
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
