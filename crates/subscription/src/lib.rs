pub mod fetcher;
pub mod mirrors;
pub mod model;
pub mod parser;
pub mod userinfo;

pub use fetcher::{
    build_subscription, dedupe_subscription_servers, extract_xray_templates_from_value,
    fetch_subscription, fetch_subscription_with_mirrors, happ_compatible_user_agent, FetchError,
    FetchOptions, Fetched,
    HAPP_COMPAT_DEVICE_MODEL, HAPP_COMPAT_DEVICE_OS, HAPP_COMPAT_OS_VERSION,
};
pub use model::{
    Hysteria2Config, NaiveConfig, NaiveTransport, Network, Protocol, Security, Server,
    ShadowsocksConfig, StreamSettings, Subscription, SubscriptionAppProxyMode,
    SubscriptionAppProxyRule, SubscriptionMeta, SubscriptionTheme, TlsFragmentConfig, TrojanConfig,
    VlessConfig, VmessConfig,
};
pub use mirrors::{
    candidates as mirror_candidates, extract_from_url as extract_mirrors_from_url,
    host_of as mirror_host_of, merge as merge_mirrors, parse as parse_mirrors,
    rewrite as rewrite_mirror, LinkWithMirrors, MAX_MIRRORS as MAX_SUBSCRIPTION_MIRRORS,
};
pub use parser::{parse_aggregate, ParseError};
pub use userinfo::{parse_subscription_userinfo, SubscriptionInfo};

pub const USER_AGENT: &str = concat!("Nimbo/", env!("CARGO_PKG_VERSION"));

/// Increment when a parser fix requires already saved subscriptions to be rebuilt.
pub const CURRENT_SUBSCRIPTION_PARSER_REVISION: u32 = 1;
