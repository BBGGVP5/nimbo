pub mod fetcher;
pub mod model;
pub mod parser;
pub mod userinfo;

pub use fetcher::{FetchOptions, Fetched, FetchError, build_subscription, fetch_subscription};
pub use model::{
    Network, Protocol, Security, Server, ShadowsocksConfig, StreamSettings, Subscription,
    TrojanConfig, VlessConfig, VmessConfig,
};
pub use parser::{ParseError, parse_aggregate};
pub use userinfo::{SubscriptionInfo, parse_subscription_userinfo};

pub const USER_AGENT: &str = concat!("Nimbo/", env!("CARGO_PKG_VERSION"));
