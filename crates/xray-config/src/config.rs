use serde::{Deserialize, Serialize};

use nimbo_subscription::Server;

use crate::inbound::Inbound;
use crate::outbound::{outbound_block, outbound_direct, server_to_outbound, Outbound};
use crate::routing::{AppRoutingRule, Routing, RoutingProfileRules};

pub const TAG_PROXY: &str = "proxy";
pub const TAG_DIRECT: &str = "direct";
pub const TAG_BLOCK: &str = "block";
pub const TAG_API: &str = "api";

#[derive(Debug, Clone, Copy)]
pub struct ProxyPorts {
    pub socks: u16,
    pub http: u16,
    pub api: u16,
}

impl Default for ProxyPorts {
    fn default() -> Self {
        Self {
            socks: 10808,
            http: 10809,
            api: 10810,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct XrayConfig {
    pub log: LogConfig,
    pub dns: DnsConfig,
    pub inbounds: Vec<Inbound>,
    pub outbounds: Vec<Outbound>,
    pub routing: Routing,
    pub api: ApiConfig,
    pub policy: Policy,
    pub stats: Stats,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LogConfig {
    pub loglevel: String,
}

impl Default for LogConfig {
    fn default() -> Self {
        Self {
            loglevel: "warning".into(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DnsConfig {
    pub servers: Vec<String>,
    #[serde(rename = "queryStrategy", skip_serializing_if = "Option::is_none")]
    pub query_strategy: Option<String>,
}

impl Default for DnsConfig {
    fn default() -> Self {
        Self {
            servers: vec!["1.1.1.1".into(), "8.8.8.8".into()],
            query_strategy: Some("UseIP".into()),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiConfig {
    pub tag: String,
    pub services: Vec<String>,
}

impl Default for ApiConfig {
    fn default() -> Self {
        Self {
            tag: TAG_API.into(),
            services: vec!["StatsService".into()],
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct Policy {
    pub system: PolicySystem,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicySystem {
    #[serde(rename = "statsInboundUplink")]
    pub stats_inbound_uplink: bool,
    #[serde(rename = "statsInboundDownlink")]
    pub stats_inbound_downlink: bool,
    #[serde(rename = "statsOutboundUplink")]
    pub stats_outbound_uplink: bool,
    #[serde(rename = "statsOutboundDownlink")]
    pub stats_outbound_downlink: bool,
}

impl Default for PolicySystem {
    fn default() -> Self {
        Self {
            stats_inbound_uplink: true,
            stats_inbound_downlink: true,
            stats_outbound_uplink: true,
            stats_outbound_downlink: true,
        }
    }
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Stats {}

pub fn build_config(server: &Server) -> XrayConfig {
    build_config_with_ports(server, ProxyPorts::default())
}

pub fn build_config_with_ports(server: &Server, ports: ProxyPorts) -> XrayConfig {
    ConfigBuilder::new(ports).server(server).build()
}

pub struct ConfigBuilder {
    ports: ProxyPorts,
    proxy_outbound: Option<Outbound>,
    app_routing_rules: Vec<AppRoutingRule>,
    profile_routing_rules: Option<RoutingProfileRules>,
    log_level: String,
    socks_account: Option<(String, String)>,
    block_socks_udp: bool,
}

impl ConfigBuilder {
    pub fn new(ports: ProxyPorts) -> Self {
        Self {
            ports,
            proxy_outbound: None,
            app_routing_rules: Vec::new(),
            profile_routing_rules: None,
            log_level: "warning".into(),
            socks_account: None,
            block_socks_udp: false,
        }
    }

    pub fn server(mut self, server: &Server) -> Self {
        self.proxy_outbound = Some(server_to_outbound(server, TAG_PROXY));
        self
    }

    pub fn log_level(mut self, level: impl Into<String>) -> Self {
        self.log_level = level.into();
        self
    }

    pub fn app_routing_rules(mut self, rules: impl Into<Vec<AppRoutingRule>>) -> Self {
        self.app_routing_rules = rules.into();
        self
    }

    pub fn profile_routing_rules(mut self, rules: RoutingProfileRules) -> Self {
        self.profile_routing_rules = Some(rules);
        self
    }

    pub fn socks_auth(mut self, username: impl Into<String>, password: impl Into<String>) -> Self {
        self.socks_account = Some((username.into(), password.into()));
        self
    }

    pub fn block_socks_udp(mut self, block: bool) -> Self {
        self.block_socks_udp = block;
        self
    }

    pub fn build(self) -> XrayConfig {
        let Self {
            ports,
            proxy_outbound,
            app_routing_rules,
            profile_routing_rules,
            log_level,
            socks_account,
            block_socks_udp,
        } = self;

        let proxy = proxy_outbound.unwrap_or_else(|| outbound_direct(TAG_PROXY));

        let inbounds = vec![
            Inbound::socks_with_options(
                ports.socks,
                socks_account
                    .as_ref()
                    .map(|(username, password)| (username.as_str(), password.as_str())),
                block_socks_udp,
            ),
            Inbound::http(ports.http),
            Inbound::api_dokodemo(ports.api),
        ];

        let outbounds = vec![
            proxy,
            outbound_direct(TAG_DIRECT),
            outbound_block(TAG_BLOCK),
        ];

        XrayConfig {
            log: LogConfig {
                loglevel: log_level,
            },
            dns: DnsConfig::default(),
            inbounds,
            outbounds,
            routing: Routing::with_rules(&app_routing_rules, profile_routing_rules.as_ref()),
            api: ApiConfig::default(),
            policy: Policy::default(),
            stats: Stats::default(),
        }
    }
}
