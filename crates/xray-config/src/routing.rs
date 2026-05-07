use serde::{Deserialize, Serialize};

use crate::config::{TAG_API, TAG_DIRECT, TAG_PROXY};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Routing {
    #[serde(rename = "domainStrategy")]
    pub domain_strategy: String,
    pub rules: Vec<RoutingRule>,
}

impl Routing {
    pub fn default_with_private_direct() -> Self {
        Self {
            domain_strategy: "IPIfNonMatch".into(),
            rules: vec![
                RoutingRule {
                    rule_type: "field".into(),
                    inbound_tag: Some(vec!["api".into()]),
                    outbound_tag: TAG_API.into(),
                    ..Default::default()
                },
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
            ],
        }
    }
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
    #[serde(rename = "outboundTag")]
    pub outbound_tag: String,
}
