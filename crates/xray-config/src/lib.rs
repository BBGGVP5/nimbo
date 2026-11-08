use thiserror::Error;

pub mod ad_blocking;
pub mod config;
pub mod inbound;
pub mod modules;
pub mod outbound;
pub mod routing;
pub mod telemetry;
pub mod transport;

pub use config::{build_config, build_config_with_ports, ConfigBuilder, ProxyPorts, XrayConfig};
pub use inbound::Inbound;
pub use modules::{module_routing_rules, parse_module, ParsedModule, RoutingModule};
pub use outbound::{server_to_outbound, Outbound};
pub use routing::{AppRoutingMode, AppRoutingRule, RoutingProfileRules};

#[derive(Debug, Error)]
pub enum BuildError {
    #[error("unsupported transport: {0}")]
    UnsupportedTransport(String),
}
