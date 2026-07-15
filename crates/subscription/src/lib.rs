pub mod fetcher;
pub mod model;
pub mod parser;
pub mod userinfo;

pub use fetcher::{
    build_subscription, dedupe_subscription_servers, extract_xray_templates_from_value,
    fetch_subscription, happ_compatible_user_agent, FetchError, FetchOptions, Fetched,
    HAPP_COMPAT_DEVICE_MODEL, HAPP_COMPAT_DEVICE_OS, HAPP_COMPAT_OS_VERSION,
};
pub use model::{
    Hysteria2Config, Network, Protocol, Security, Server, ShadowsocksConfig, StreamSettings,
    Subscription, SubscriptionAppProxyMode, SubscriptionAppProxyRule, SubscriptionMeta,
    SubscriptionTheme, TrojanConfig, VlessConfig, VmessConfig,
};
pub use parser::{parse_aggregate, ParseError};
pub use userinfo::{parse_subscription_userinfo, SubscriptionInfo};

pub const USER_AGENT: &str = concat!("Nimbo/", env!("CARGO_PKG_VERSION"));
