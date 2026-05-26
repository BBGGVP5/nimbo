pub mod fetcher;
pub mod model;
pub mod parser;
pub mod userinfo;

pub use fetcher::{
    FetchOptions, Fetched, FetchError, HAPP_COMPAT_DEVICE_MODEL, HAPP_COMPAT_DEVICE_OS,
    HAPP_COMPAT_OS_VERSION, build_subscription, dedupe_subscription_servers,
    extract_xray_templates_from_value, fetch_subscription, happ_compatible_user_agent,
};
pub use model::{
    Hysteria2Config, Network, Protocol, Security, Server, ShadowsocksConfig, StreamSettings,
    Subscription, SubscriptionAppProxyMode, SubscriptionAppProxyRule, SubscriptionMeta,
    TrojanConfig, VlessConfig, VmessConfig,
};
pub use parser::{ParseError, parse_aggregate};
pub use userinfo::{SubscriptionInfo, parse_subscription_userinfo};

pub const USER_AGENT: &str = concat!("Nimbo/", env!("CARGO_PKG_VERSION"));
