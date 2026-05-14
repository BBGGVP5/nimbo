use thiserror::Error;

pub mod config;
pub mod inbound;
pub mod outbound;
pub mod routing;
pub mod transport;

pub use config::{ConfigBuilder, ProxyPorts, XrayConfig, build_config, build_config_with_ports};
pub use inbound::Inbound;
pub use outbound::{Outbound, server_to_outbound};
pub use routing::{AppRoutingMode, AppRoutingRule};

#[derive(Debug, Error)]
pub enum BuildError {
    #[error("unsupported transport: {0}")]
    UnsupportedTransport(String),
}
