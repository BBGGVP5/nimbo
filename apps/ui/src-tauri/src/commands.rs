use std::collections::{HashMap, HashSet, VecDeque};
use std::fs::{File, OpenOptions};
use std::hash::{Hash, Hasher};
use std::io::Cursor;
use std::net::{IpAddr, Ipv4Addr, TcpStream, ToSocketAddrs};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use tauri::{AppHandle, Manager, State};
use tokio::process::Command as TokioCommand;

use nimbo_device::{device_info, reset_cache, DeviceInfo};
use nimbo_ipc::PROTOCOL_VERSION;
use nimbo_subscription::{
    build_subscription, extract_xray_templates_from_value, fetch_subscription,
    happ_compatible_user_agent, parse_aggregate, parse_subscription_userinfo, FetchOptions,
    Fetched, Server, Subscription, HAPP_COMPAT_DEVICE_MODEL, HAPP_COMPAT_DEVICE_OS,
    HAPP_COMPAT_OS_VERSION, USER_AGENT,
};
use nimbo_xray_config::{
    AppRoutingMode as XrayAppRoutingMode, AppRoutingRule as XrayAppRoutingRule, ConfigBuilder,
    ProxyPorts, RoutingProfileRules as XrayRoutingProfileRules,
};

use crate::state::{
    AppPreferences, AppProxyMode, AppProxyRule, AppState, ConnectionMode, PersistedState,
    RoutingProfile, SystemProxySnapshot, TrafficRuntimeSample, TrafficTotals, TunRuntimeSnapshot,
};

const TUN_INTERFACE_NAME: &str = "wintun";
const TUN_ADDRESS: &str = "198.18.0.1";
const TUN_GATEWAY_CIDR: &str = "198.18.0.1/24";
const TUN_IPV6_GATEWAY_CIDR: &str = "fdfe:dcba:9876::1/64";
const TUN_DNS_PRIMARY: &str = "1.1.1.1";
const TUN_DNS_SECONDARY: &str = "8.8.8.8";
const DEFAULT_XRAY_TEMPLATE_KEY: &str = "default";
const XRAY_PROXY_TAG: &str = "proxy";
const REMNAWAVE_BASE_URL_ENV: &str = "REMNAWAVE_BASE_URL";
const REMNAWAVE_API_TOKEN_ENV: &str = "REMNAWAVE_API_TOKEN";
const REMNAWAVE_API_KEY_ENV: &str = "REMNAWAVE_API_KEY";
const REMNAWAVE_PROFILE_UUIDS_ENV: &str = "REMNAWAVE_CONFIG_PROFILE_UUIDS";
const NIMBO_REMNAWAVE_ENV_ENV: &str = "NIMBO_REMNAWAVE_ENV";
const CONFLICT_STOP_ATTEMPTS: usize = 6;
const CONFLICT_STOP_INITIAL_SETTLE_MS: u64 = 900;
const CONFLICT_STOP_RETRY_SETTLE_MS: u64 = 650;
const SUBSCRIPTION_LOGO_CACHE_BYTES: usize = 4 * 1024 * 1024;
const SUBSCRIPTION_LOGO_CACHE_DIR: &str = "subscription-logos";
const MAX_RUNTIME_LOG_BYTES: u64 = 5 * 1024 * 1024;
static RESUME_RECONNECT_IN_FLIGHT: AtomicBool = AtomicBool::new(false);
static XRAY_STATS_QUERY_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppStatus {
    pub state: ConnectionState,
    pub connected_at: Option<u64>,
    pub active_server_id: Option<String>,
    pub active_subscription_url: Option<String>,
    pub subscription_count: usize,
    pub server_count: usize,
    pub service_protocol: u32,
    pub connection_mode: ConnectionMode,
    pub socks_port: u16,
    pub http_port: u16,
    pub socks_username: String,
    pub socks_password: String,
    pub require_socks_auth: bool,
    pub block_socks_udp: bool,
    pub server_pings: HashMap<String, u64>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    ServiceUnavailable,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InstalledApp {
    pub name: String,
    pub executable_path: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ConflictingProcess {
    pub name: String,
    pub process_name: String,
    pub pid: u32,
    pub path: Option<String>,
    #[serde(default)]
    pub pids: Vec<u32>,
}

#[derive(Debug, Clone, Serialize)]
pub struct SubscriptionHeader {
    pub name: String,
    pub value: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct SubscriptionHeaderMetadata {
    pub url: String,
    pub status: u16,
    pub subscription_userinfo: Option<String>,
    pub upload: Option<u64>,
    pub download: Option<u64>,
    pub total: Option<u64>,
    pub expire: Option<i64>,
    pub profile_title: Option<String>,
    pub support_url: Option<String>,
    pub profile_update_interval: Option<String>,
    pub profile_update_interval_seconds: Option<u64>,
    pub announce: Option<String>,
    pub announce_headers: Vec<SubscriptionHeader>,
    pub headers: Vec<SubscriptionHeader>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct SubscriptionSettingsPatch {
    pub name: Option<String>,
    pub show_on_home: Option<bool>,
    pub update_interval_minutes: Option<u32>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProxySettingsPatch {
    pub socks_username: Option<String>,
    pub socks_password: Option<String>,
    pub require_socks_auth: Option<bool>,
    pub block_socks_udp: Option<bool>,
}

#[derive(Debug, Clone, Serialize)]
pub struct TunInstallStatus {
    pub installed: bool,
    pub can_install: bool,
    pub needs_admin_restart: bool,
    pub tun2socks_path: Option<String>,
    pub wintun_path: Option<String>,
    pub missing: Vec<String>,
    pub message: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct ServerPing {
    pub server_id: String,
    pub latency_ms: Option<u64>,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize)]
pub struct SessionTraffic {
    pub upload: u64,
    pub download: u64,
}

#[derive(Debug, Clone, Default, Serialize)]
pub struct MemoryUsage {
    /// Total resident memory of the Nimbo app + xray + tun2socks, in bytes.
    pub bytes: u64,
}

#[derive(Debug, Clone, Copy, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum ActiveConnectionRoute {
    Proxy,
    Direct,
    Block,
    Unknown,
}

#[derive(Debug, Clone, Serialize)]
pub struct ActiveConnection {
    pub id: String,
    pub protocol: String,
    pub state: String,
    pub source: String,
    pub destination: String,
    pub remote_address: String,
    pub remote_port: u16,
    pub process: String,
    pub process_path: Option<String>,
    pub pid: u32,
    pub route: ActiveConnectionRoute,
    pub rule: String,
    pub server_id: Option<String>,
    pub server_name: Option<String>,
    pub server_protocol: Option<String>,
}

pub fn builtin_routing_profiles() -> Vec<RoutingProfile> {
    vec![
        RoutingProfile {
            id: "global".into(),
            name: "Глобальный".into(),
            description: "Весь трафик через VPN".into(),
            builtin: true,
            domain_strategy: "AsIs".into(),
            rule_order: "block-proxy-direct".into(),
            global_proxy: true,
            bypass_local_ip: true,
            fake_dns: false,
            remote_dns_ip: "1.1.1.1".into(),
            local_dns_ip: "8.8.8.8".into(),
            remote_dns_url: "https://1.1.1.1/dns-query".into(),
            direct_domains: vec![],
            direct_ips: vec![],
            proxy_domains: vec![],
            proxy_ips: vec![],
            block_domains: vec!["geosite:category-ads".into()],
            block_ips: vec![],
            source_url: None,
            update_interval_hours: 24,
            last_updated_at: None,
        },
        RoutingProfile {
            id: "bypass_lan".into(),
            name: "Обход LAN".into(),
            description: "Локальные адреса напрямую".into(),
            builtin: true,
            domain_strategy: "AsIs".into(),
            rule_order: "block-proxy-direct".into(),
            global_proxy: true,
            bypass_local_ip: true,
            fake_dns: false,
            remote_dns_ip: "1.1.1.1".into(),
            local_dns_ip: "8.8.8.8".into(),
            remote_dns_url: "https://1.1.1.1/dns-query".into(),
            direct_domains: vec![],
            direct_ips: vec![
                "10.0.0.0/8".into(),
                "172.16.0.0/12".into(),
                "192.168.0.0/16".into(),
                "127.0.0.0/8".into(),
                "fc00::/7".into(),
                "fe80::/10".into(),
                "::1/128".into(),
            ],
            proxy_domains: vec![],
            proxy_ips: vec![],
            block_domains: vec!["geosite:category-ads".into()],
            block_ips: vec![],
            source_url: None,
            update_interval_hours: 24,
            last_updated_at: None,
        },
        RoutingProfile {
            id: "china_direct".into(),
            name: "Китай".into(),
            description: "Китайские сайты напрямую".into(),
            builtin: true,
            domain_strategy: "IPIfNonMatch".into(),
            rule_order: "block-proxy-direct".into(),
            global_proxy: true,
            bypass_local_ip: true,
            fake_dns: false,
            remote_dns_ip: "1.1.1.1".into(),
            local_dns_ip: "223.5.5.5".into(),
            remote_dns_url: "https://1.1.1.1/dns-query".into(),
            direct_domains: vec![
                "geosite:cn".into(),
                "geosite:apple-cn".into(),
                "geosite:google-cn".into(),
                "geosite:microsoft@cn".into(),
            ],
            direct_ips: vec![
                "geoip:cn".into(),
                "geoip:private".into(),
                "223.5.5.5/32".into(),
                "119.29.29.29/32".into(),
            ],
            proxy_domains: vec![],
            proxy_ips: vec![],
            block_domains: vec!["geosite:category-ads-all".into()],
            block_ips: vec![],
            source_url: None,
            update_interval_hours: 24,
            last_updated_at: None,
        },
        RoutingProfile {
            id: "russia_direct".into(),
            name: "Россия".into(),
            description: "Российские ресурсы напрямую".into(),
            builtin: true,
            domain_strategy: "IPIfNonMatch".into(),
            rule_order: "block-proxy-direct".into(),
            global_proxy: true,
            bypass_local_ip: true,
            fake_dns: false,
            remote_dns_ip: "1.1.1.1".into(),
            local_dns_ip: "77.88.8.8".into(),
            remote_dns_url: "https://1.1.1.1/dns-query".into(),
            direct_domains: vec![
                "domain:ru".into(),
                "domain:su".into(),
                "domain:yandex.ru".into(),
                "domain:mail.ru".into(),
                "domain:vk.com".into(),
                "domain:vk.ru".into(),
                "domain:ok.ru".into(),
                "domain:sberbank.ru".into(),
                "domain:gosuslugi.ru".into(),
                "domain:tinkoff.ru".into(),
                "domain:rt.com".into(),
                "domain:wildberries.ru".into(),
                "domain:ozon.ru".into(),
                "domain:avito.ru".into(),
                "domain:hh.ru".into(),
                "domain:2gis.ru".into(),
                "domain:rutube.ru".into(),
                "domain:dzen.ru".into(),
                "domain:kinopoisk.ru".into(),
                "domain:ivi.ru".into(),
                "domain:kion.ru".into(),
                "domain:wink.ru".into(),
                "domain:rbc.ru".into(),
                "domain:lenta.ru".into(),
                "domain:tass.ru".into(),
                "domain:ria.ru".into(),
                "domain:1tv.ru".into(),
                "domain:vesti.ru".into(),
                "domain:meduza.io".into(),
                "domain:tinkoff.com".into(),
            ],
            direct_ips: vec!["geoip:ru".into(), "geoip:private".into()],
            proxy_domains: vec![],
            proxy_ips: vec![],
            block_domains: vec!["geosite:category-ads-all".into()],
            block_ips: vec![],
            source_url: None,
            update_interval_hours: 24,
            last_updated_at: None,
        },
        RoutingProfile {
            id: "roscomvpn".into(),
            name: "RoscomVPN".into(),
            description: "Заблокированные в РФ ресурсы — через VPN, остальное напрямую".into(),
            builtin: true,
            domain_strategy: "IPIfNonMatch".into(),
            rule_order: "block-direct-proxy".into(),
            global_proxy: false,
            bypass_local_ip: true,
            fake_dns: false,
            remote_dns_ip: "1.1.1.1".into(),
            local_dns_ip: "77.88.8.8".into(),
            remote_dns_url: "https://1.1.1.1/dns-query".into(),
            direct_domains: vec!["domain:ru".into(), "geoip:ru".into()],
            direct_ips: vec!["geoip:ru".into(), "geoip:private".into()],
            proxy_domains: vec![
                "domain:openai.com".into(),
                "domain:chatgpt.com".into(),
                "domain:anthropic.com".into(),
                "domain:claude.ai".into(),
                "domain:notion.so".into(),
                "domain:linkedin.com".into(),
                "domain:tradingview.com".into(),
                "domain:patreon.com".into(),
                "domain:onlyfans.com".into(),
                "domain:medium.com".into(),
                "domain:soundcloud.com".into(),
                "domain:spotify.com".into(),
                "domain:bbc.com".into(),
                "domain:dw.com".into(),
                "domain:bandcamp.com".into(),
                "domain:itch.io".into(),
                "domain:speedtest.net".into(),
                "domain:fast.com".into(),
                "domain:figma.com".into(),
                "domain:behance.net".into(),
                "domain:dribbble.com".into(),
                "domain:proton.me".into(),
                "domain:protonmail.com".into(),
                "domain:tutanota.com".into(),
                "domain:cloudflare.com".into(),
                "domain:cloudflareclient.com".into(),
            ],
            proxy_ips: vec![],
            block_domains: vec!["geosite:category-ads-all".into()],
            block_ips: vec![],
            source_url: Some(
                "https://raw.githubusercontent.com/hydraponique/roscomvpn-routing/main/profile.json"
                    .into(),
            ),
            update_interval_hours: 24,
            last_updated_at: None,
        },
    ]
}

fn ensure_routing_profiles(snapshot: &mut PersistedState) {
    let deleted: HashSet<String> = snapshot.deleted_builtin_profiles.iter().cloned().collect();
    if snapshot.routing_profiles.is_empty() {
        snapshot.routing_profiles = builtin_routing_profiles()
            .into_iter()
            .filter(|profile| !deleted.contains(&profile.id))
            .collect();
        return;
    }
    for builtin in builtin_routing_profiles() {
        if deleted.contains(&builtin.id) {
            continue;
        }
        if let Some(existing) = snapshot
            .routing_profiles
            .iter_mut()
            .find(|profile| profile.id == builtin.id && profile.builtin)
        {
            if existing.name == "RosKomVPN" {
                existing.name = builtin.name.clone();
            }
            if existing.id == "roscomvpn" {
                existing.source_url = builtin.source_url.clone();
            }
        } else {
            snapshot.routing_profiles.push(builtin);
        }
    }
}

fn count_rules(profile: &RoutingProfile) -> u32 {
    (profile.direct_domains.len()
        + profile.direct_ips.len()
        + profile.proxy_domains.len()
        + profile.proxy_ips.len()
        + profile.block_domains.len()
        + profile.block_ips.len()) as u32
}

#[derive(Debug, Clone, Serialize)]
pub struct TrafficStats {
    pub session_upload: u64,
    pub session_download: u64,
    pub upload_speed: f64,
    pub download_speed: f64,
    pub speed_available: bool,
    pub all_time_upload: u64,
    pub all_time_download: u64,
    pub monthly_upload: u64,
    pub monthly_download: u64,
    pub monthly_period: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct TunnelLogEntry {
    pub source: String,
    pub level: String,
    pub timestamp: Option<String>,
    pub message: String,
}

#[tauri::command]
pub fn get_device_info() -> DeviceInfo {
    device_info()
}

#[tauri::command]
pub fn reset_device_id() -> Result<DeviceInfo, String> {
    reset_cache().map_err(|e| format!("Не удалось сбросить кеш HWID: {e}"))?;
    // нельзя пересоздать OnceLock, так что новый HWID применится после рестарта приложения
    Ok(device_info())
}

#[tauri::command]
pub fn read_clipboard_text() -> Result<String, String> {
    let mut clipboard =
        arboard::Clipboard::new().map_err(|e| format!("Не удалось открыть буфер обмена: {e}"))?;
    clipboard
        .get_text()
        .map_err(|e| format!("В буфере нет текста или он недоступен: {e}"))
}

#[tauri::command]
pub fn write_clipboard_text(text: String) -> Result<(), String> {
    let mut clipboard =
        arboard::Clipboard::new().map_err(|e| format!("Не удалось открыть буфер обмена: {e}"))?;
    clipboard
        .set_text(text)
        .map_err(|e| format!("Не удалось записать текст в буфер: {e}"))
}

#[tauri::command]
pub fn app_ready(app: tauri::AppHandle) {
    let state = app.state::<AppState>();
    let preferences = state.snapshot().preferences;
    if !preferences.start_minimized {
        if let Some(window) = app.get_webview_window("main") {
            let _ = window.show();
            let _ = window.unminimize();
            let _ = window.set_focus();
        }
    }
}

#[tauri::command]
pub fn get_status(state: State<'_, AppState>) -> AppStatus {
    let snapshot = state.snapshot();
    let server_count: usize = snapshot.subscriptions.iter().map(|s| s.servers.len()).sum();
    AppStatus {
        state: if snapshot.connected {
            ConnectionState::Connected
        } else {
            ConnectionState::Disconnected
        },
        connected_at: snapshot.connected_at,
        active_server_id: snapshot.active_server_id,
        active_subscription_url: snapshot.active_subscription_url,
        subscription_count: snapshot.subscriptions.len(),
        server_count,
        service_protocol: PROTOCOL_VERSION,
        connection_mode: snapshot.connection_mode,
        socks_port: ProxyPorts::default().socks,
        http_port: ProxyPorts::default().http,
        socks_username: snapshot.socks_username,
        socks_password: snapshot.socks_password,
        require_socks_auth: snapshot.require_socks_auth,
        block_socks_udp: snapshot.block_socks_udp,
        server_pings: snapshot.server_pings,
    }
}

#[tauri::command]
pub fn get_preferences(state: State<'_, AppState>) -> AppPreferences {
    let mut preferences = state.snapshot().preferences;
    if let Ok(enabled) = is_launch_at_login_enabled() {
        if preferences.launch_at_login != enabled {
            let _ = state.mutate(|s| s.preferences.launch_at_login = enabled);
            preferences.launch_at_login = enabled;
        }
    }
    preferences
}

#[tauri::command]
pub fn set_preferences(
    app: AppHandle,
    state: State<'_, AppState>,
    mut preferences: AppPreferences,
) -> Result<AppPreferences, String> {
    preferences.accent_color = normalize_accent_color(&preferences.accent_color);
    preferences.ui_style = normalize_ui_style(&preferences.ui_style);
    preferences.interface_panel_brightness = preferences.interface_panel_brightness.clamp(60, 140);
    preferences.interface_transparency = preferences.interface_transparency.clamp(0, 80);
    preferences.interface_blur = preferences.interface_blur.clamp(0, 48);
    preferences.interface_rounding = preferences.interface_rounding.clamp(50, 180);
    preferences.latency_protocol = normalize_latency_protocol(&preferences.latency_protocol);
    preferences.latency_test_url = normalize_latency_test_url(&preferences.latency_test_url);
    preferences.latency_timeout_ms = normalize_latency_timeout_ms(preferences.latency_timeout_ms);
    preferences.latency_display_format =
        normalize_latency_display_format(&preferences.latency_display_format);
    preferences.app_routing_mode = normalize_app_routing_mode(&preferences.app_routing_mode);
    preferences.tunnel_mux_concurrency = preferences.tunnel_mux_concurrency.clamp(1, 1024);
    preferences.tunnel_xudp_concurrency = preferences.tunnel_xudp_concurrency.clamp(-1, 1024);
    preferences.tunnel_xudp_udp443 = normalize_xudp_udp443_mode(&preferences.tunnel_xudp_udp443);
    preferences.lan_tcp_idle_timeout_sec = preferences.lan_tcp_idle_timeout_sec.clamp(5, 3600);
    preferences.lan_max_tcp_connections = preferences.lan_max_tcp_connections.clamp(1, 100_000);
    preferences.lan_max_udp_connections = preferences.lan_max_udp_connections.clamp(1, 100_000);
    preferences.lan_preferred_ip_family =
        normalize_preferred_ip_family(&preferences.lan_preferred_ip_family);
    preferences.subscriptions_update_interval_hours = preferences
        .subscriptions_update_interval_hours
        .clamp(1, 168);
    preferences.subscriptions_expiration_threshold_days = preferences
        .subscriptions_expiration_threshold_days
        .clamp(1, 365);
    preferences.servers_sorting = normalize_server_sorting(&preferences.servers_sorting);
    preferences.servers_connect_button =
        normalize_connect_button_style(&preferences.servers_connect_button);
    preferences.servers_ui_scale = preferences.servers_ui_scale.clamp(80, 125);
    set_launch_at_login(&app, preferences.launch_at_login)?;
    state
        .mutate(|s| s.preferences = preferences.clone())
        .map_err(|e| format!("Не удалось сохранить настройки приложения: {e}"))?;
    crate::tray::refresh_tray_menu(&app)
        .map_err(|e| format!("Не удалось обновить меню трея: {e}"))?;
    Ok(preferences)
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct AppBackup {
    schema: String,
    app: String,
    exported_at: u64,
    state: PersistedState,
}

#[tauri::command]
pub fn export_app_backup(state: State<'_, AppState>) -> Result<String, String> {
    let mut snapshot = state.snapshot();
    snapshot.connected = false;
    snapshot.connected_at = None;
    snapshot.pending_system_proxy_snapshot = None;
    snapshot.pending_tun_snapshot = None;
    let exported_at = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_secs())
        .unwrap_or_default();
    let backup = AppBackup {
        schema: "nimbo-backup-v1".into(),
        app: "Nimbo".into(),
        exported_at,
        state: snapshot,
    };
    serde_json::to_string_pretty(&backup)
        .map_err(|e| format!("Не удалось собрать резервную копию: {e}"))
}

#[tauri::command]
pub fn import_app_backup(
    state: State<'_, AppState>,
    payload: String,
) -> Result<PersistedState, String> {
    let raw: serde_json::Value = serde_json::from_str(payload.trim())
        .map_err(|e| format!("Не удалось прочитать JSON резервной копии: {e}"))?;
    let state_value = raw.get("state").cloned().unwrap_or(raw);
    let mut imported: PersistedState = serde_json::from_value(state_value)
        .map_err(|e| format!("Не удалось применить резервную копию: {e}"))?;
    imported.normalize_runtime_defaults();
    imported.connected = false;
    imported.connected_at = None;
    imported.pending_system_proxy_snapshot = None;
    imported.pending_tun_snapshot = None;

    state
        .mutate(|snapshot| {
            *snapshot = imported.clone();
        })
        .map_err(|e| format!("Не удалось сохранить резервную копию: {e}"))?;
    Ok(state.snapshot())
}

#[tauri::command]
pub fn refresh_tray_menu(app: AppHandle) -> Result<(), String> {
    crate::tray::refresh_tray_menu(&app).map_err(|e| format!("Не удалось обновить меню трея: {e}"))
}

#[tauri::command]
pub fn list_subscriptions(state: State<'_, AppState>) -> Vec<Subscription> {
    state.snapshot().subscriptions
}

#[tauri::command]
pub async fn inspect_subscription_headers(
    url: String,
) -> Result<SubscriptionHeaderMetadata, String> {
    let source = url.trim();
    if source.is_empty() {
        return Err("Вставьте URL подписки".into());
    }

    let resp = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(15))
        .build()
        .map_err(|e| format!("Не удалось создать HTTP-клиент: {e}"))?
        .get(source)
        .send()
        .await
        .map_err(|e| format!("Не удалось загрузить подписку: {e}"))?;

    let status = resp.status().as_u16();
    let mut headers = Vec::new();
    for (name, value) in resp.headers() {
        let value = value
            .to_str()
            .map(ToString::to_string)
            .unwrap_or_else(|_| format!("{:?}", value.as_bytes()));
        tracing::debug!(
            header = name.as_str(),
            value,
            "subscription response header"
        );
        headers.push(SubscriptionHeader {
            name: name.as_str().to_string(),
            value,
        });
    }

    let subscription_userinfo = header_value(&headers, "subscription-userinfo");
    let parsed_userinfo = subscription_userinfo
        .as_deref()
        .map(parse_subscription_userinfo)
        .unwrap_or_default();
    let profile_update_interval = header_value(&headers, "profile-update-interval");

    Ok(SubscriptionHeaderMetadata {
        url: source.to_string(),
        status,
        subscription_userinfo,
        upload: parsed_userinfo.upload,
        download: parsed_userinfo.download,
        total: parsed_userinfo.total,
        expire: parsed_userinfo.expire,
        profile_title: header_value(&headers, "profile-title"),
        support_url: header_value(&headers, "support-url"),
        profile_update_interval_seconds: profile_update_interval
            .as_deref()
            .and_then(|value| value.trim().parse::<u64>().ok()),
        profile_update_interval,
        announce: find_announce_header(&headers).map(|header| header.value.clone()),
        announce_headers: headers
            .iter()
            .filter(|header| is_announce_header(&header.name))
            .cloned()
            .collect(),
        headers,
    })
}

#[tauri::command]
pub async fn add_subscription(
    state: State<'_, AppState>,
    url: String,
    name: Option<String>,
) -> Result<Subscription, String> {
    let snapshot_before = state.snapshot();
    let source = url.trim();
    if snapshot_before
        .subscriptions
        .iter()
        .any(|s| s.url == source)
    {
        return Err("Подписка с таким URL уже добавлена".into());
    }

    if source.is_empty() {
        return Err("Вставьте ссылку подписки или proxy-конфиг".into());
    }

    let fetched = if is_remote_subscription(source) {
        let opts = build_fetch_options(&snapshot_before);
        fetch_subscription(source, &opts)
            .await
            .map_err(|e| format!("Не удалось загрузить: {e}"))?
    } else {
        let servers =
            parse_aggregate(source).map_err(|e| format!("Не удалось распарсить конфиг: {e}"))?;
        let xray_templates = serde_json::from_str::<serde_json::Value>(source.trim())
            .ok()
            .map(|json| extract_xray_templates_from_value(&json))
            .unwrap_or_default();
        Fetched {
            raw_body: source.to_string(),
            servers,
            info: None,
            suggested_name: None,
            description: None,
            support_url: None,
            website_url: None,
            app_proxy_rules: Vec::new(),
            logo_url: None,
            theme: None,
            xray_templates,
        }
    };

    let xray_templates = collect_subscription_xray_templates(
        &fetched,
        source,
        snapshot_before.user_agent_override.as_deref(),
    )
    .await;
    let subscription = build_subscription(source, fetched, name);

    state
        .mutate(|s| {
            remove_xray_templates_for_subscription(&mut s.xray_templates, source);
            merge_xray_template_cache(&mut s.xray_templates, source, xray_templates);
            s.subscriptions.push(subscription.clone());
        })
        .map_err(|e| format!("Не удалось сохранить: {e}"))?;

    Ok(subscription)
}

#[tauri::command]
pub async fn refresh_subscription(
    state: State<'_, AppState>,
    url: String,
) -> Result<Subscription, String> {
    let opts = build_fetch_options(&state.snapshot());
    let fetched = fetch_subscription(&url, &opts)
        .await
        .map_err(|e| format!("Не удалось обновить: {e}"))?;

    let active_was_in_servers = {
        let snap = state.snapshot();
        if let Some(active) = &snap.active_server_id {
            fetched.servers.iter().any(|srv| &srv.id == active)
        } else {
            true
        }
    };

    let xray_templates =
        collect_subscription_xray_templates(&fetched, &url, opts.user_agent.as_deref()).await;
    let updated_name = state
        .mutate(|s| {
            remove_xray_templates_for_subscription(&mut s.xray_templates, &url);
            merge_xray_template_cache(&mut s.xray_templates, &url, xray_templates);
            let existing = s.subscriptions.iter().find(|sub| sub.url == url);
            let existing_name = existing.and_then(|sub| sub.name.clone());
            let existing_show_on_home = existing.and_then(|sub| sub.meta.show_on_home);
            let existing_update_interval =
                existing.and_then(|sub| sub.meta.update_interval_minutes);
            let mut updated = build_subscription(&url, fetched.clone(), existing_name.clone());
            updated.meta.show_on_home = existing_show_on_home.or(Some(true));
            updated.meta.update_interval_minutes = existing_update_interval;
            if let Some(pos) = s.subscriptions.iter().position(|sub| sub.url == url) {
                s.subscriptions[pos] = updated.clone();
            } else {
                s.subscriptions.push(updated.clone());
            }
            if !active_was_in_servers {
                s.active_server_id = None;
            }
            existing_name
        })
        .map_err(|e| format!("Не удалось сохранить: {e}"))?;

    let _ = updated_name;
    let snap = state.snapshot();
    snap.subscriptions
        .into_iter()
        .find(|sub| sub.url == url)
        .ok_or_else(|| "Подписка не найдена после обновления".into())
}

#[tauri::command]
pub fn update_subscription_settings(
    state: State<'_, AppState>,
    url: String,
    settings: SubscriptionSettingsPatch,
) -> Result<Subscription, String> {
    let updated = state
        .mutate(|s| {
            let sub = s.subscriptions.iter_mut().find(|sub| sub.url == url)?;

            if let Some(name) = settings.name {
                let name = name.trim().to_string();
                sub.name = if name.is_empty() { None } else { Some(name) };
            }
            if let Some(show_on_home) = settings.show_on_home {
                sub.meta.show_on_home = Some(show_on_home);
            }
            if let Some(update_interval_minutes) = settings.update_interval_minutes {
                sub.meta.update_interval_minutes = Some(update_interval_minutes);
            }

            Some(sub.clone())
        })
        .map_err(|e| format!("Не удалось сохранить настройки подписки: {e}"))?;

    updated.ok_or_else(|| "Подписка не найдена".into())
}

#[tauri::command]
pub fn remove_subscription(
    state: State<'_, AppState>,
    url: String,
) -> Result<PersistedState, String> {
    state
        .mutate(|s| {
            s.subscriptions.retain(|sub| sub.url != url);
            let valid_server_ids = s
                .subscriptions
                .iter()
                .flat_map(|sub| sub.servers.iter().map(|server| server.id.clone()))
                .collect::<HashSet<_>>();
            s.server_pings
                .retain(|server_id, _| valid_server_ids.contains(server_id));
            if let Some(active) = &s.active_server_id {
                let still_present = s
                    .subscriptions
                    .iter()
                    .any(|sub| sub.servers.iter().any(|srv| &srv.id == active));
                if !still_present {
                    s.active_server_id = None;
                }
            }
            if let Some(active_url) = &s.active_subscription_url {
                let still_present = s.subscriptions.iter().any(|sub| &sub.url == active_url);
                if !still_present {
                    s.active_subscription_url = None;
                }
            }
        })
        .map_err(|e| format!("Не удалось сохранить: {e}"))?;
    Ok(state.snapshot())
}

#[tauri::command]
pub fn set_user_agent_override(
    state: State<'_, AppState>,
    user_agent: Option<String>,
) -> Result<PersistedState, String> {
    let cleaned = user_agent.and_then(|s| {
        let trimmed = s.trim().to_string();
        if trimmed.is_empty() {
            None
        } else {
            Some(trimmed)
        }
    });
    state
        .mutate(|s| s.user_agent_override = cleaned)
        .map_err(|e| format!("Не удалось сохранить: {e}"))?;
    Ok(state.snapshot())
}

#[tauri::command]
pub fn get_user_agent_override(state: State<'_, AppState>) -> Option<String> {
    state.snapshot().user_agent_override
}

#[tauri::command]
pub fn list_app_proxy_rules(state: State<'_, AppState>) -> Vec<AppProxyRule> {
    state.snapshot().app_proxy_rules
}

#[tauri::command]
pub fn list_subscription_app_proxy_rules(state: State<'_, AppState>) -> Vec<AppProxyRule> {
    let snap = state.snapshot();
    subscription_app_proxy_rules_for_display(&snap)
}

#[tauri::command]
pub fn list_installed_apps() -> Vec<InstalledApp> {
    platform_installed_apps()
}

#[tauri::command]
pub async fn list_conflicting_processes() -> Result<Vec<ConflictingProcess>, String> {
    tokio::task::spawn_blocking(detect_conflicting_processes)
        .await
        .map_err(|e| format!("Не удалось дождаться проверки конфликтующих процессов: {e}"))?
}

#[tauri::command]
pub async fn stop_conflicting_processes(
    app: AppHandle,
    pids: Option<Vec<u32>>,
) -> Result<Vec<ConflictingProcess>, String> {
    #[cfg(not(windows))]
    let _ = app;

    tokio::task::spawn_blocking(move || {
        #[cfg(windows)]
        {
            stop_conflicting_processes_blocking(app, pids)
        }
        #[cfg(not(windows))]
        {
            stop_conflicting_processes_blocking(pids)
        }
    })
    .await
    .map_err(|e| format!("Не удалось дождаться выгрузки конфликтующих процессов: {e}"))?
}

fn stop_conflicting_processes_blocking(
    #[cfg(windows)] app: AppHandle,
    pids: Option<Vec<u32>>,
) -> Result<Vec<ConflictingProcess>, String> {
    let detected = detect_conflicting_processes()?;
    let detected_pids: HashSet<u32> = detected
        .iter()
        .flat_map(|process| process.pids.iter().copied())
        .collect();
    let requested: HashSet<u32> = match pids {
        Some(list) => list.into_iter().collect(),
        None => detected_pids.iter().copied().collect(),
    };
    let mut safe_pids: Vec<u32> = requested
        .into_iter()
        .filter(|pid| detected_pids.contains(pid))
        .collect();
    safe_pids.sort_unstable();
    safe_pids.dedup();

    if safe_pids.is_empty() {
        return detect_conflicting_processes();
    }

    let target_names = target_conflict_names(&detected, &safe_pids);

    #[cfg(windows)]
    {
        let helper = crate::helper::status(&app);
        if helper.running {
            // Route through the privileged helper — no UAC prompt, can kill SYSTEM processes.
            let remaining =
                stop_conflicts_with_retries(&target_names, safe_pids.clone(), |pids| {
                    let _ = crate::helper::kill_processes(pids);
                    Ok(())
                })?;
            let remaining_pids = target_pids_from_conflicts(&remaining, &target_names);
            if remaining_pids.is_empty() {
                return Ok(remaining);
            }
            safe_pids = remaining_pids;
        }
    }

    #[cfg(windows)]
    let mut remaining = stop_conflicts_with_retries(
        &target_names,
        safe_pids.clone(),
        stop_conflicting_process_ids,
    )?;
    #[cfg(not(windows))]
    let remaining = stop_conflicts_with_retries(
        &target_names,
        safe_pids.clone(),
        stop_conflicting_process_ids,
    )?;

    #[cfg(windows)]
    {
        let remaining_pids = target_pids_from_conflicts(&remaining, &target_names);
        if !remaining_pids.is_empty() && !is_running_as_admin() {
            remaining = stop_conflicts_with_retries(
                &target_names,
                remaining_pids,
                stop_conflicting_process_ids_elevated,
            )?;
        }
    }

    Ok(remaining)
}

fn stop_conflicts_with_retries<F>(
    target_names: &HashSet<String>,
    initial_pids: Vec<u32>,
    mut stop: F,
) -> Result<Vec<ConflictingProcess>, String>
where
    F: FnMut(&[u32]) -> Result<(), String>,
{
    let mut pids = initial_pids;
    let mut remaining = detect_conflicting_processes()?;

    for attempt in 0..CONFLICT_STOP_ATTEMPTS {
        if pids.is_empty() {
            break;
        }

        let stop_error = stop(&pids).err();
        let wait_ms = if attempt == 0 {
            CONFLICT_STOP_INITIAL_SETTLE_MS
        } else {
            CONFLICT_STOP_RETRY_SETTLE_MS
        };
        std::thread::sleep(std::time::Duration::from_millis(wait_ms));

        remaining = detect_conflicting_processes()?;
        pids = target_pids_from_conflicts(&remaining, target_names);
        if let Some(error) = stop_error {
            if pids.is_empty() {
                break;
            }
            return Err(error);
        }
    }

    Ok(remaining)
}

fn target_conflict_names(
    detected: &[ConflictingProcess],
    requested_pids: &[u32],
) -> HashSet<String> {
    let requested: HashSet<u32> = requested_pids.iter().copied().collect();
    detected
        .iter()
        .filter(|process| {
            conflict_process_pids(process)
                .into_iter()
                .any(|pid| requested.contains(&pid))
        })
        .map(|process| process.name.clone())
        .collect()
}

fn target_pids_from_conflicts(
    conflicts: &[ConflictingProcess],
    target_names: &HashSet<String>,
) -> Vec<u32> {
    let mut pids = conflicts
        .iter()
        .filter(|process| target_names.contains(&process.name))
        .flat_map(conflict_process_pids)
        .collect::<HashSet<_>>()
        .into_iter()
        .collect::<Vec<_>>();
    pids.sort_unstable();
    pids
}

fn conflict_process_pids(process: &ConflictingProcess) -> Vec<u32> {
    if process.pids.is_empty() {
        vec![process.pid]
    } else {
        process.pids.clone()
    }
}

#[cfg(windows)]
#[tauri::command]
pub fn helper_status(app: AppHandle) -> crate::helper::HelperStatus {
    crate::helper::status(&app)
}

#[cfg(not(windows))]
#[tauri::command]
pub fn helper_status() -> serde_json::Value {
    serde_json::json!({
        "installed": false,
        "running": false,
        "version": null,
        "exe_present": false,
        "exe_path": null,
    })
}

#[cfg(windows)]
#[tauri::command]
pub fn install_helper(app: AppHandle) -> Result<crate::helper::HelperStatus, String> {
    crate::helper::install(&app)?;
    Ok(crate::helper::status(&app))
}

#[cfg(not(windows))]
#[tauri::command]
pub fn install_helper() -> Result<(), String> {
    Err("Хелпер доступен только на Windows.".into())
}

#[cfg(windows)]
#[tauri::command]
pub fn uninstall_helper(app: AppHandle) -> Result<crate::helper::HelperStatus, String> {
    crate::helper::uninstall(&app)?;
    Ok(crate::helper::status(&app))
}

#[cfg(not(windows))]
#[tauri::command]
pub fn uninstall_helper() -> Result<(), String> {
    Err("Хелпер доступен только на Windows.".into())
}

#[tauri::command]
pub fn get_app_icon(path: String) -> Option<String> {
    platform_get_app_icon(&path)
}

#[tauri::command]
pub async fn get_subscription_logo(
    app: AppHandle,
    subscription_url: String,
    logo_url: Option<String>,
    fetched_at: u64,
) -> Result<Option<String>, String> {
    let Some(logo_url) = logo_url
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
    else {
        return Ok(None);
    };

    let lower = logo_url.to_ascii_lowercase();
    if lower.starts_with("data:image/") {
        return Ok(Some(logo_url));
    }
    if lower.starts_with("data:") {
        return Ok(None);
    }

    let parsed = match url::Url::parse(&logo_url) {
        Ok(url) if matches!(url.scheme(), "http" | "https") => url,
        _ => return Ok(None),
    };

    let subscription_key = cache_key(&subscription_url);
    let logo_key = cache_key(&format!("{logo_url}\n{fetched_at}"));
    let cache_dir = app
        .path()
        .app_cache_dir()
        .map_err(|e| format!("Не удалось открыть папку кеша: {e}"))?
        .join(SUBSCRIPTION_LOGO_CACHE_DIR)
        .join(subscription_key);
    let cache_file = cache_dir.join(format!("{logo_key}.txt"));

    if let Ok(cached) = std::fs::read_to_string(&cache_file) {
        if cached.starts_with("data:image/") {
            return Ok(Some(cached));
        }
    }

    std::fs::create_dir_all(&cache_dir)
        .map_err(|e| format!("Не удалось создать папку кеша логотипов: {e}"))?;
    cleanup_subscription_logo_cache(&cache_dir, &cache_file);

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(12))
        .user_agent(USER_AGENT)
        .build()
        .map_err(|e| format!("Не удалось подготовить загрузку логотипа: {e}"))?;

    let response = match client
        .get(parsed)
        .header(
            reqwest::header::ACCEPT,
            "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
        )
        .send()
        .await
    {
        Ok(response) => response,
        Err(error) => {
            tracing::warn!(?error, "failed to download subscription logo");
            return Ok(Some(logo_url));
        }
    };

    if !response.status().is_success() {
        return Ok(Some(logo_url));
    }
    if response
        .content_length()
        .is_some_and(|length| length > SUBSCRIPTION_LOGO_CACHE_BYTES as u64)
    {
        return Ok(Some(logo_url));
    }

    let content_type = response
        .headers()
        .get(reqwest::header::CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        .map(str::to_string);
    let bytes = match response.bytes().await {
        Ok(bytes) => bytes,
        Err(error) => {
            tracing::warn!(?error, "failed to read subscription logo");
            return Ok(Some(logo_url));
        }
    };

    if bytes.len() > SUBSCRIPTION_LOGO_CACHE_BYTES {
        return Ok(Some(logo_url));
    }

    let Some(mime) = subscription_logo_mime(content_type.as_deref(), &bytes) else {
        return Ok(Some(logo_url));
    };
    let data_url = format!("data:{mime};base64,{}", simple_base64(&bytes));
    if let Err(error) = std::fs::write(&cache_file, data_url.as_bytes()) {
        tracing::warn!(?error, "failed to write subscription logo cache");
    }

    Ok(Some(data_url))
}

#[tauri::command]
pub fn pick_app_executable() -> Result<Option<String>, String> {
    platform_pick_app_executable()
}

#[tauri::command]
pub fn export_app_proxy_rules_file(
    contents: String,
    file_name: String,
) -> Result<Option<String>, String> {
    let file_name = sanitize_export_file_name(&file_name);
    let Some(path) = platform_pick_app_rules_export_path(&file_name)? else {
        return Ok(None);
    };

    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)
            .map_err(|e| format!("Не удалось создать папку экспорта: {e}"))?;
    }
    std::fs::write(&path, contents.as_bytes())
        .map_err(|e| format!("Не удалось сохранить файл правил приложений: {e}"))?;
    Ok(Some(path_to_string(path)))
}

const CONFLICTING_PROCESS_NAMES: &[&str] = &[
    "Cloudflare WARP",
    "CloudflareWARP",
    "warp",
    "winws",
    "zapret",
    "FlClash",
    "FlClashCore",
    "FlClashHelperService",
    "clash",
    "clash-meta",
    "clash-verge",
    "clash-verge-service",
    "Clash Verge",
    "Clash Verge Rev",
    "Clash for Windows",
    "mihomo",
    "mihomo-windows-amd64",
    "sing-box",
    "singbox",
    "Happ",
    "HappDesktop",
    "HappService",
    "Hiddify",
    "hiddify",
    "v2rayN",
    "Nekoray",
    "nekoray",
    "nekobox",
    "Outline",
    "OutlineService",
    "WireGuard",
    "wireguard",
    "wg",
    "OpenVPN",
    "openvpn",
    "OpenVPNConnect",
    "OpenVPNConnectAgent",
    "ProtonVPN",
    "ProtonVPNService",
    "NordVPN",
    "NordVPNService",
    "Incy",
    "incy",
    "IncyService",
    "IncyHelper",
];

const CONFLICTING_PROCESS_PARTIAL_KEYS: &[&str] = &[
    "cloudflarewarp",
    "zapret",
    "winws",
    "flclash",
    "clashverge",
    "clash",
    "mihomo",
    "singbox",
    "happdesktop",
    "happservice",
    "hiddify",
    "v2rayn",
    "nekoray",
    "nekobox",
    "outline",
    "wireguard",
    "openvpn",
    "protonvpn",
    "nordvpn",
    "incy",
];

#[cfg(windows)]
#[derive(Debug, Deserialize)]
struct PowershellProcess {
    #[serde(rename = "ProcessName")]
    process_name: String,
    #[serde(rename = "Id")]
    id: u32,
    #[serde(rename = "Path")]
    path: Option<String>,
    #[serde(rename = "CommandLine")]
    command_line: Option<String>,
}

#[cfg(windows)]
fn detect_conflicting_processes() -> Result<Vec<ConflictingProcess>, String> {
    let script = r#"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$items = try {
  foreach ($p in Get-Process -ErrorAction Stop) {
    if ($null -eq $p.Id -or [string]::IsNullOrWhiteSpace($p.ProcessName)) {
      continue
    }
    $path = $null
    try { $path = $p.Path } catch {}
    [PSCustomObject]@{
      ProcessName = $p.ProcessName
      Id = [uint32]$p.Id
      Path = $path
      CommandLine = $null
    }
  }
} catch {
  @()
}
[Console]::Out.Write((ConvertTo-Json -InputObject @($items) -Compress -Depth 3))
"#;

    let mut command = hidden_output_command("powershell");
    command
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(script)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    let output = command_output_with_timeout(command, std::time::Duration::from_secs(4))
        .map_err(|e| format!("Не удалось проверить конфликтующие процессы: {e}"))?;

    if !output.status.success() {
        return Err(format!(
            "Не удалось проверить конфликтующие процессы: {}",
            output.status
        ));
    }

    let text = String::from_utf8_lossy(&output.stdout).trim().to_string();
    if text.is_empty() {
        return Ok(Vec::new());
    }

    let json: serde_json::Value = serde_json::from_str(&text)
        .map_err(|e| format!("Не удалось прочитать список конфликтующих процессов: {e}"))?;
    let raw = match json {
        serde_json::Value::Array(items) => items
            .into_iter()
            .filter_map(|value| serde_json::from_value::<PowershellProcess>(value).ok())
            .collect::<Vec<_>>(),
        serde_json::Value::Object(_) => serde_json::from_value::<PowershellProcess>(json)
            .map(|item| vec![item])
            .unwrap_or_default(),
        _ => Vec::new(),
    };

    let flat = raw
        .into_iter()
        .filter(is_conflicting_process)
        .map(|process| {
            let display = conflict_display_name(
                &process.process_name,
                process.path.as_deref(),
                process.command_line.as_deref(),
            );
            let exe = process_exe_name(&process.process_name);
            let path = process.path.and_then(|path| {
                let trimmed = path.trim().to_string();
                if trimmed.is_empty() {
                    None
                } else {
                    Some(trimmed)
                }
            });
            (display, exe, process.id, path)
        })
        .collect::<Vec<_>>();

    let mut order: Vec<String> = Vec::new();
    let mut groups: HashMap<String, ConflictingProcess> = HashMap::new();
    let mut seen_pids: HashSet<u32> = HashSet::new();
    for (display, exe, pid, path) in flat {
        if !seen_pids.insert(pid) {
            continue;
        }
        let entry = groups.entry(display.clone()).or_insert_with(|| {
            order.push(display.clone());
            ConflictingProcess {
                name: display.clone(),
                process_name: exe.clone(),
                pid,
                path: path.clone(),
                pids: Vec::new(),
            }
        });
        entry.pids.push(pid);
        if process_exe_priority(&exe) < process_exe_priority(&entry.process_name)
            || (process_exe_priority(&exe) == process_exe_priority(&entry.process_name)
                && pid < entry.pid)
        {
            entry.process_name = exe;
            entry.pid = pid;
            entry.path = path;
        }
    }

    let mut conflicts: Vec<ConflictingProcess> = order
        .into_iter()
        .filter_map(|key| groups.remove(&key))
        .map(|mut group| {
            group.pids.sort_unstable();
            group.pids.dedup();
            group
        })
        .collect();
    conflicts.sort_by(|a, b| {
        a.name
            .to_ascii_lowercase()
            .cmp(&b.name.to_ascii_lowercase())
    });
    Ok(conflicts)
}

fn process_exe_priority(exe: &str) -> u8 {
    let lower = exe.to_ascii_lowercase();
    let stem = lower.strip_suffix(".exe").unwrap_or(&lower);
    if stem.ends_with("helperservice") || stem.ends_with("helper") {
        return 3;
    }
    if stem.ends_with("service") || stem.ends_with("svc") {
        return 2;
    }
    if stem.ends_with("core") {
        return 1;
    }
    0
}

#[cfg(not(windows))]
fn detect_conflicting_processes() -> Result<Vec<ConflictingProcess>, String> {
    Ok(Vec::new())
}

#[cfg(windows)]
fn stop_conflicting_process_ids(pids: &[u32]) -> Result<(), String> {
    let ids = pids
        .iter()
        .map(u32::to_string)
        .collect::<Vec<_>>()
        .join(",");
    let script = format!(
        r#"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'SilentlyContinue'
$ids = @({ids})
foreach ($id in $ids) {{
  Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
  taskkill.exe /PID $id /T /F 1>$null 2>$null
}}
exit 0
"#
    );

    let mut command = hidden_output_command("powershell");
    command
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(script)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    let output = command_output_with_timeout(command, std::time::Duration::from_secs(4))
        .map_err(|e| format!("Не удалось выгрузить конфликтующие процессы: {e}"))?;

    if !output.status.success() {
        return Err("Не удалось запустить выгрузку конфликтующих процессов.".into());
    }

    Ok(())
}

#[cfg(not(windows))]
fn stop_conflicting_process_ids(_pids: &[u32]) -> Result<(), String> {
    Ok(())
}

#[cfg(windows)]
fn stop_conflicting_process_ids_elevated(pids: &[u32]) -> Result<(), String> {
    let ids = pids
        .iter()
        .map(u32::to_string)
        .collect::<Vec<_>>()
        .join(",");
    let inner = format!(
        "$ErrorActionPreference='SilentlyContinue'; $ids=@({ids}); foreach($id in $ids) {{ try {{ Stop-Process -Id $id -Force -ErrorAction SilentlyContinue }} catch {{}}; & taskkill.exe /PID $id /T /F *> $null }}; exit 0"
    );
    let inner_escaped = inner.replace('\'', "''");
    let outer = format!(
        "try {{ $p = Start-Process -FilePath 'powershell' -ArgumentList @('-NoProfile','-WindowStyle','Hidden','-ExecutionPolicy','Bypass','-Command','{inner_escaped}') -Verb RunAs -Wait -PassThru -ErrorAction Stop; exit $p.ExitCode }} catch {{ exit 1223 }}"
    );

    let status = hidden_command("powershell")
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(&outer)
        .status()
        .map_err(|e| format!("Не удалось запустить выгрузку с правами администратора: {e}"))?;

    if !status.success() {
        if status.code() == Some(1223) {
            return Err("Выгрузка от имени администратора отменена.".into());
        }
        return Err("Не удалось завершить выгрузку с правами администратора.".into());
    }
    Ok(())
}

#[cfg(windows)]
fn is_ignored_conflicting_process(process: &PowershellProcess) -> bool {
    let name_key = compact_process_identity(&process.process_name);

    // Cloudflare keeps this background service resident and often restarts it
    // with a new PID. The service alone is not a reliable active-TUN signal.
    name_key == "warpsvc"
}

#[cfg(windows)]
fn is_conflicting_process(process: &PowershellProcess) -> bool {
    let name_key = compact_process_identity(&process.process_name);
    if name_key.is_empty() {
        return false;
    }
    if is_ignored_conflicting_process(process) {
        return false;
    }

    if CONFLICTING_PROCESS_NAMES
        .iter()
        .map(|name| compact_process_identity(name))
        .any(|known| known == name_key)
    {
        return true;
    }

    let mut search = name_key;
    if let Some(path) = process.path.as_deref() {
        search.push_str(&compact_text_identity(path));
    }
    if let Some(command_line) = process.command_line.as_deref() {
        search.push_str(&compact_text_identity(command_line));
    }

    CONFLICTING_PROCESS_PARTIAL_KEYS
        .iter()
        .any(|key| search.contains(key))
}

fn conflict_display_name(
    process_name: &str,
    path: Option<&str>,
    command_line: Option<&str>,
) -> String {
    let mut identity = compact_process_identity(process_name);
    if let Some(path) = path {
        identity.push_str(&compact_text_identity(path));
    }
    if let Some(command_line) = command_line {
        identity.push_str(&compact_text_identity(command_line));
    }

    if identity.contains("cloudflarewarp") || identity.contains("warpsvc") || identity == "warp" {
        return "Cloudflare WARP".to_string();
    }
    if identity.contains("clashverge") {
        return "Clash Verge".to_string();
    }
    if identity.contains("flclash") {
        return "FlClash".to_string();
    }
    if identity.contains("clash") || identity.contains("mihomo") {
        return "Clash/Mihomo".to_string();
    }
    if identity.contains("singbox") {
        return "sing-box".to_string();
    }
    if identity.contains("zapret") || identity.contains("winws") {
        return "zapret".to_string();
    }
    let process_key = compact_process_identity(process_name);
    if process_key == "happ" || identity.contains("happdesktop") || identity.contains("happservice")
    {
        return "Happ".to_string();
    }
    if identity.contains("hiddify") {
        return "Hiddify".to_string();
    }
    if identity.contains("v2rayn") {
        return "v2rayN".to_string();
    }
    if identity.contains("nekoray") {
        return "Nekoray".to_string();
    }
    if identity.contains("nekobox") {
        return "NekoBox".to_string();
    }
    if identity.contains("outline") {
        return "Outline".to_string();
    }
    if identity.contains("wireguard") || identity == "wg" {
        return "WireGuard".to_string();
    }
    if identity.contains("openvpn") {
        return "OpenVPN".to_string();
    }
    if identity.contains("protonvpn") {
        return "Proton VPN".to_string();
    }
    if identity.contains("nordvpn") {
        return "NordVPN".to_string();
    }
    if identity.contains("incy") {
        return "Incy".to_string();
    }

    match normalized_process_name(process_name).as_str() {
        "cloudflare warp" | "cloudflarewarp" | "warp" | "warp-svc" => "Cloudflare WARP".to_string(),
        "winws" | "zapret" => "zapret".to_string(),
        "flclash" | "flclashcore" | "flclashhelperservice" => "FlClash".to_string(),
        "clash-verge" | "clash-verge-service" | "clash verge" | "clash verge rev" => {
            "Clash Verge".to_string()
        }
        "clash" | "clash-meta" | "clash for windows" | "mihomo" | "mihomo-windows-amd64" => {
            "Clash/Mihomo".to_string()
        }
        "sing-box" | "singbox" => "sing-box".to_string(),
        "happ" | "happdesktop" | "happservice" => "Happ".to_string(),
        "hiddify" => "Hiddify".to_string(),
        "v2rayn" => "v2rayN".to_string(),
        "nekoray" => "Nekoray".to_string(),
        "nekobox" => "NekoBox".to_string(),
        "outline" | "outlineservice" => "Outline".to_string(),
        "wireguard" | "wg" => "WireGuard".to_string(),
        "openvpn" | "openvpnconnect" | "openvpnconnectagent" => "OpenVPN".to_string(),
        "protonvpn" | "protonvpnservice" => "Proton VPN".to_string(),
        "nordvpn" | "nordvpnservice" => "NordVPN".to_string(),
        "incy" | "incyservice" | "incyhelper" => "Incy".to_string(),
        _ => process_name.trim_end_matches(".exe").to_string(),
    }
}

fn process_exe_name(process_name: &str) -> String {
    let trimmed = process_name.trim();
    if trimmed.to_ascii_lowercase().ends_with(".exe") {
        trimmed.to_string()
    } else {
        format!("{trimmed}.exe")
    }
}

fn normalized_process_name(process_name: &str) -> String {
    let trimmed = process_name.trim();
    let lower = trimmed.to_ascii_lowercase();
    lower.strip_suffix(".exe").unwrap_or(&lower).to_string()
}

fn compact_process_identity(process_name: &str) -> String {
    compact_text_identity(&normalized_process_name(process_name))
}

fn compact_text_identity(value: &str) -> String {
    value
        .chars()
        .filter(|char| char.is_ascii_alphanumeric())
        .map(|char| char.to_ascii_lowercase())
        .collect()
}

#[cfg(windows)]
fn platform_get_app_icon(exe_path: &str) -> Option<String> {
    use std::ffi::OsStr;
    use std::mem;
    use std::os::windows::ffi::OsStrExt;
    use winapi::ctypes::c_void;
    use winapi::shared::guiddef::GUID;
    use winapi::shared::minwindef::UINT;
    use winapi::shared::windef::HICON;
    use winapi::um::libloaderapi::{FreeLibrary, GetProcAddress, LoadLibraryW};
    use winapi::um::shellapi::{SHGetFileInfoW, SHFILEINFOW, SHGFI_SYSICONINDEX};
    use winapi::um::wingdi::{
        CreateCompatibleDC, CreateDIBSection, DeleteDC, DeleteObject, SelectObject, BITMAPINFO,
        BITMAPINFOHEADER, BI_RGB, DIB_RGB_COLORS,
    };
    use winapi::um::winuser::{DestroyIcon, DrawIconEx};

    // Render at 64×64 — looks sharp even on HiDPI, keeps PNG small
    const SIZE: i32 = 64;
    const DI_NORMAL: UINT = 0x0003;
    const SHIL_JUMBO: i32 = 4;
    const ILD_TRANSPARENT: UINT = 0x00000001;

    // IImageList IID: {46EB5926-582E-4017-9FDF-E8998DAA0950}
    let iid_imagelist = GUID {
        Data1: 0x46EB5926,
        Data2: 0x582E,
        Data3: 0x4017,
        Data4: [0x9F, 0xDF, 0xE8, 0x99, 0x8D, 0xAA, 0x09, 0x50],
    };

    unsafe {
        let wide: Vec<u16> = OsStr::new(exe_path)
            .encode_wide()
            .chain(std::iter::once(0))
            .collect();

        // Step 1: get the system image list icon index (no HICON allocated yet)
        let mut sfinfo: SHFILEINFOW = mem::zeroed();
        let ok = SHGetFileInfoW(
            wide.as_ptr(),
            0,
            &mut sfinfo,
            mem::size_of::<SHFILEINFOW>() as UINT,
            SHGFI_SYSICONINDEX,
        );
        if ok == 0 {
            return None;
        }
        let icon_index = sfinfo.iIcon as i32;

        // Step 2: get the SHIL_JUMBO (256×256) image list via SHGetImageList
        let shell32_name: Vec<u16> = OsStr::new("shell32.dll")
            .encode_wide()
            .chain(std::iter::once(0))
            .collect();
        let shell32 = LoadLibraryW(shell32_name.as_ptr());
        if shell32.is_null() {
            return None;
        }
        let fn_ptr = GetProcAddress(shell32, c"SHGetImageList".as_ptr());
        if fn_ptr.is_null() {
            FreeLibrary(shell32);
            return None;
        }

        // HRESULT SHGetImageList(int iImageList, REFIID riid, void **ppvObj)
        type SHGetImageListFn =
            unsafe extern "system" fn(i32, *const GUID, *mut *mut c_void) -> i32;
        let sh_get_image_list: SHGetImageListFn = mem::transmute(fn_ptr);

        let mut image_list: *mut c_void = std::ptr::null_mut();
        let hr = sh_get_image_list(SHIL_JUMBO, &iid_imagelist, &mut image_list);
        FreeLibrary(shell32);
        if hr < 0 || image_list.is_null() {
            return None;
        }

        // Step 3: call IImageList::GetIcon via vtable
        // IUnknown: 0=QueryInterface, 1=AddRef, 2=Release
        // IImageList: 3=Add, 4=ReplaceIcon, 5=SetOverlayImage, 6=Replace,
        //             7=AddMasked, 8=Draw, 9=Remove, 10=GetIcon
        type GetIconFn = unsafe extern "system" fn(*mut c_void, i32, UINT, *mut HICON) -> i32;
        type ReleaseFn = unsafe extern "system" fn(*mut c_void) -> u32;

        let vtable = *(image_list as *mut *mut *const ());
        let get_icon: GetIconFn = mem::transmute(*vtable.add(10));
        let release: ReleaseFn = mem::transmute(*vtable.add(2));

        let mut hicon: HICON = std::ptr::null_mut();
        let hr2 = get_icon(image_list, icon_index, ILD_TRANSPARENT, &mut hicon);
        release(image_list);

        if hr2 < 0 || hicon.is_null() {
            return None;
        }

        // Step 4: render HICON into a SIZE×SIZE DIB
        let mem_dc = CreateCompatibleDC(std::ptr::null_mut());
        if mem_dc.is_null() {
            DestroyIcon(hicon);
            return None;
        }

        let mut bmi: BITMAPINFO = mem::zeroed();
        bmi.bmiHeader.biSize = mem::size_of::<BITMAPINFOHEADER>() as u32;
        bmi.bmiHeader.biWidth = SIZE;
        bmi.bmiHeader.biHeight = -SIZE;
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;
        bmi.bmiHeader.biCompression = BI_RGB;

        let mut bits_ptr: *mut c_void = std::ptr::null_mut();
        let hbm = CreateDIBSection(
            mem_dc,
            &bmi,
            DIB_RGB_COLORS,
            &mut bits_ptr,
            std::ptr::null_mut(),
            0,
        );
        if hbm.is_null() || bits_ptr.is_null() {
            DeleteDC(mem_dc);
            DestroyIcon(hicon);
            return None;
        }

        let pixel_count = (SIZE * SIZE) as usize;
        std::ptr::write_bytes(bits_ptr as *mut u8, 0, pixel_count * 4);

        let prev = SelectObject(mem_dc, hbm as _);
        DrawIconEx(
            mem_dc,
            0,
            0,
            hicon,
            SIZE,
            SIZE,
            0,
            std::ptr::null_mut(),
            DI_NORMAL,
        );

        let bgra = std::slice::from_raw_parts(bits_ptr as *const u8, pixel_count * 4);

        // BGRA → RGBA
        let mut rgba = Vec::with_capacity(pixel_count * 4);
        for px in bgra.chunks_exact(4) {
            rgba.push(px[2]);
            rgba.push(px[1]);
            rgba.push(px[0]);
            rgba.push(px[3]);
        }

        SelectObject(mem_dc, prev);
        DeleteObject(hbm as _);
        DeleteDC(mem_dc);
        DestroyIcon(hicon);

        let rgba = normalize_icon_rgba(&rgba, SIZE as usize, SIZE as usize);
        encode_rgba_png_base64(&rgba, SIZE as u32, SIZE as u32)
    }
}

#[cfg(not(windows))]
fn platform_get_app_icon(_exe_path: &str) -> Option<String> {
    None
}

#[cfg(windows)]
fn platform_pick_app_executable() -> Result<Option<String>, String> {
    let script = r#"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Windows.Forms
$dialog = New-Object System.Windows.Forms.OpenFileDialog
$dialog.Title = 'Выберите приложение или ярлык'
$dialog.Filter = 'Приложения и ярлыки (*.exe;*.lnk)|*.exe;*.lnk|Приложения (*.exe)|*.exe|Ярлыки (*.lnk)|*.lnk|Все файлы (*.*)|*.*'
$dialog.Multiselect = $false
if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
  $selected = $dialog.FileName
  if ([System.IO.Path]::GetExtension($selected).Equals('.lnk', [System.StringComparison]::OrdinalIgnoreCase)) {
    try {
      $shell = New-Object -ComObject WScript.Shell
      $shortcut = $shell.CreateShortcut($selected)
      if ($shortcut.TargetPath) {
        $selected = $shortcut.TargetPath
      }
    } catch {}
  }
  [Console]::Out.Write($selected)
}
"#;

    let output = hidden_output_command("powershell")
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-STA")
        .arg("-Command")
        .arg(script)
        .output()
        .map_err(|e| format!("Не удалось открыть выбор приложения: {e}"))?;

    if !output.status.success() {
        return Err("Не удалось открыть выбор приложения".into());
    }

    let selected = String::from_utf8_lossy(&output.stdout).trim().to_string();
    Ok(if selected.is_empty() {
        None
    } else {
        Some(selected)
    })
}

#[cfg(not(windows))]
fn platform_pick_app_executable() -> Result<Option<String>, String> {
    Ok(None)
}

fn sanitize_export_file_name(file_name: &str) -> String {
    let mut cleaned = file_name
        .trim()
        .chars()
        .map(|ch| {
            if ch.is_control() || matches!(ch, '<' | '>' | ':' | '"' | '/' | '\\' | '|' | '?' | '*')
            {
                '_'
            } else {
                ch
            }
        })
        .collect::<String>();

    cleaned = cleaned.trim_matches(&[' ', '.'][..]).to_string();
    if cleaned.is_empty() {
        cleaned = "nimbo-app-rules.json".into();
    }
    if !cleaned.to_ascii_lowercase().ends_with(".json") {
        cleaned.push_str(".json");
    }
    cleaned
}

#[cfg(windows)]
fn platform_pick_app_rules_export_path(default_file_name: &str) -> Result<Option<PathBuf>, String> {
    let default_file_name = default_file_name.replace('\'', "''");
    let script = r#"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Windows.Forms
$dialog = New-Object System.Windows.Forms.SaveFileDialog
$dialog.Title = 'Экспорт правил приложений'
$dialog.Filter = 'JSON (*.json)|*.json|Все файлы (*.*)|*.*'
$dialog.DefaultExt = 'json'
$dialog.AddExtension = $true
$dialog.OverwritePrompt = $true
$dialog.FileName = '__NIMBO_DEFAULT_FILE_NAME__'
if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
  [Console]::Out.Write($dialog.FileName)
}
"#
    .replace("__NIMBO_DEFAULT_FILE_NAME__", &default_file_name);

    let output = hidden_output_command("powershell")
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-STA")
        .arg("-Command")
        .arg(script)
        .output()
        .map_err(|e| format!("Не удалось открыть экспорт правил приложений: {e}"))?;

    if !output.status.success() {
        return Err("Не удалось открыть экспорт правил приложений".into());
    }

    let selected = String::from_utf8_lossy(&output.stdout).trim().to_string();
    Ok(if selected.is_empty() {
        None
    } else {
        Some(PathBuf::from(selected))
    })
}

#[cfg(not(windows))]
fn platform_pick_app_rules_export_path(default_file_name: &str) -> Result<Option<PathBuf>, String> {
    let dir = nimbo_data_dir()?.join("exports");
    std::fs::create_dir_all(&dir).map_err(|e| format!("Не удалось создать папку экспорта: {e}"))?;
    Ok(Some(dir.join(default_file_name)))
}

fn normalize_icon_rgba(rgba: &[u8], width: usize, height: usize) -> Vec<u8> {
    const ALPHA_THRESHOLD: u8 = 8;
    const PADDING: usize = 4;

    if width == 0 || height == 0 || rgba.len() < width * height * 4 {
        return rgba.to_vec();
    }

    let mut min_x = width;
    let mut min_y = height;
    let mut max_x = 0usize;
    let mut max_y = 0usize;
    let mut found = false;

    for y in 0..height {
        for x in 0..width {
            let alpha = rgba[(y * width + x) * 4 + 3];
            if alpha > ALPHA_THRESHOLD {
                min_x = min_x.min(x);
                min_y = min_y.min(y);
                max_x = max_x.max(x);
                max_y = max_y.max(y);
                found = true;
            }
        }
    }

    if !found {
        return rgba.to_vec();
    }

    let crop_w = max_x - min_x + 1;
    let crop_h = max_y - min_y + 1;
    let target = width.min(height).saturating_sub(PADDING * 2).max(1);
    let scale = target as f32 / crop_w.max(crop_h) as f32;
    let dst_w = ((crop_w as f32 * scale).round() as usize).clamp(1, width);
    let dst_h = ((crop_h as f32 * scale).round() as usize).clamp(1, height);
    let dst_x = (width - dst_w) / 2;
    let dst_y = (height - dst_h) / 2;
    let mut out = vec![0u8; width * height * 4];

    for y in 0..dst_h {
        for x in 0..dst_w {
            let src_x = min_x as f32 + ((x as f32 + 0.5) * crop_w as f32 / dst_w as f32) - 0.5;
            let src_y = min_y as f32 + ((y as f32 + 0.5) * crop_h as f32 / dst_h as f32) - 0.5;
            let px = sample_rgba_bilinear(rgba, width, height, src_x, src_y);
            let out_idx = ((dst_y + y) * width + dst_x + x) * 4;
            out[out_idx..out_idx + 4].copy_from_slice(&px);
        }
    }

    out
}

fn sample_rgba_bilinear(rgba: &[u8], width: usize, height: usize, x: f32, y: f32) -> [u8; 4] {
    let x = x.clamp(0.0, (width - 1) as f32);
    let y = y.clamp(0.0, (height - 1) as f32);
    let x0 = x.floor() as usize;
    let y0 = y.floor() as usize;
    let x1 = (x0 + 1).min(width - 1);
    let y1 = (y0 + 1).min(height - 1);
    let tx = x - x0 as f32;
    let ty = y - y0 as f32;
    let mut out = [0u8; 4];

    for channel in 0..4 {
        let c00 = rgba[(y0 * width + x0) * 4 + channel] as f32;
        let c10 = rgba[(y0 * width + x1) * 4 + channel] as f32;
        let c01 = rgba[(y1 * width + x0) * 4 + channel] as f32;
        let c11 = rgba[(y1 * width + x1) * 4 + channel] as f32;
        let top = c00 + (c10 - c00) * tx;
        let bottom = c01 + (c11 - c01) * tx;
        out[channel] = (top + (bottom - top) * ty).round().clamp(0.0, 255.0) as u8;
    }

    out
}

fn encode_rgba_png_base64(rgba: &[u8], width: u32, height: u32) -> Option<String> {
    use png::{BitDepth, ColorType, Encoder};
    let mut buf = Vec::new();
    {
        let mut enc = Encoder::new(&mut buf, width, height);
        enc.set_color(ColorType::Rgba);
        enc.set_depth(BitDepth::Eight);
        let mut writer = enc.write_header().ok()?;
        writer.write_image_data(rgba).ok()?;
    }
    Some(format!("data:image/png;base64,{}", simple_base64(&buf)))
}

fn cache_key(value: &str) -> String {
    let mut hasher = std::collections::hash_map::DefaultHasher::new();
    value.hash(&mut hasher);
    format!("{:016x}", hasher.finish())
}

fn cleanup_subscription_logo_cache(cache_dir: &Path, keep_file: &Path) {
    let Ok(entries) = std::fs::read_dir(cache_dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path == keep_file {
            continue;
        }
        if path.extension().and_then(|value| value.to_str()) == Some("txt") {
            let _ = std::fs::remove_file(path);
        }
    }
}

fn subscription_logo_mime(content_type: Option<&str>, bytes: &[u8]) -> Option<&'static str> {
    let declared = content_type
        .and_then(|value| value.split(';').next())
        .map(str::trim)
        .unwrap_or("")
        .to_ascii_lowercase();
    match declared.as_str() {
        "image/png" => return Some("image/png"),
        "image/jpeg" | "image/jpg" => return Some("image/jpeg"),
        "image/webp" => return Some("image/webp"),
        "image/gif" => return Some("image/gif"),
        "image/svg+xml" => return Some("image/svg+xml"),
        "image/avif" => return Some("image/avif"),
        "image/bmp" => return Some("image/bmp"),
        "image/x-icon" | "image/vnd.microsoft.icon" => return Some("image/x-icon"),
        _ => {}
    }

    if bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        return Some("image/png");
    }
    if bytes.starts_with(b"\xff\xd8\xff") {
        return Some("image/jpeg");
    }
    if bytes.starts_with(b"GIF87a") || bytes.starts_with(b"GIF89a") {
        return Some("image/gif");
    }
    if bytes.len() >= 12 && &bytes[0..4] == b"RIFF" && &bytes[8..12] == b"WEBP" {
        return Some("image/webp");
    }
    if bytes.len() >= 12 && &bytes[4..12] == b"ftypavif" {
        return Some("image/avif");
    }
    if bytes.starts_with(b"BM") {
        return Some("image/bmp");
    }
    if bytes.starts_with(b"\0\0\x01\0") {
        return Some("image/x-icon");
    }
    let prefix = std::str::from_utf8(&bytes[..bytes.len().min(256)])
        .ok()?
        .trim_start()
        .to_ascii_lowercase();
    if prefix.starts_with("<svg") || (prefix.starts_with("<?xml") && prefix.contains("<svg")) {
        return Some("image/svg+xml");
    }

    None
}

fn simple_base64(data: &[u8]) -> String {
    const T: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(data.len().div_ceil(3) * 4);
    for c in data.chunks(3) {
        let b0 = c[0] as usize;
        let b1 = if c.len() > 1 { c[1] as usize } else { 0 };
        let b2 = if c.len() > 2 { c[2] as usize } else { 0 };
        out.push(T[b0 >> 2] as char);
        out.push(T[((b0 & 3) << 4) | (b1 >> 4)] as char);
        out.push(if c.len() > 1 {
            T[((b1 & 15) << 2) | (b2 >> 6)] as char
        } else {
            '='
        });
        out.push(if c.len() > 2 { T[b2 & 63] as char } else { '=' });
    }
    out
}

#[tauri::command]
pub async fn reapply_runtime_config(
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<bool, String> {
    let snapshot = state.snapshot();
    if !snapshot.connected {
        return Ok(false);
    }
    let Some(server_id) = snapshot.active_server_id.clone() else {
        return Ok(false);
    };
    connect_server(app, state, server_id).await?;
    Ok(true)
}

#[tauri::command]
pub fn set_app_proxy_rules(
    state: State<'_, AppState>,
    rules: Vec<AppProxyRule>,
) -> Result<PersistedState, String> {
    let cleaned = rules
        .into_iter()
        .filter_map(|mut rule| {
            rule.id = rule.id.trim().to_string();
            rule.name = rule.name.trim().to_string();
            rule.executable_path = rule.executable_path.trim().to_string();
            if rule.id.is_empty() || rule.executable_path.is_empty() {
                return None;
            }
            if rule.name.is_empty() {
                rule.name = executable_name(&rule.executable_path);
            }
            Some(rule)
        })
        .collect::<Vec<_>>();

    let mut seen = HashMap::<String, usize>::new();
    let mut deduped: Vec<AppProxyRule> = Vec::with_capacity(cleaned.len());
    for rule in cleaned {
        let key = canonical_app_rule_key(&rule.executable_path);
        if key.is_empty() {
            continue;
        }
        if let Some(&index) = seen.get(&key) {
            deduped[index] = rule;
        } else {
            seen.insert(key, deduped.len());
            deduped.push(rule);
        }
    }

    state
        .mutate(|s| s.app_proxy_rules = deduped)
        .map_err(|e| format!("Не удалось сохранить правила приложений: {e}"))?;
    Ok(state.snapshot())
}

#[tauri::command]
pub async fn set_connection_mode(
    app: AppHandle,
    state: State<'_, AppState>,
    mode: ConnectionMode,
) -> Result<PersistedState, String> {
    let snapshot = state.snapshot();
    if snapshot.connection_mode == mode {
        return Ok(snapshot);
    }

    if !snapshot.connected {
        state
            .mutate(|s| s.connection_mode = mode)
            .map_err(|e| format!("Не удалось сохранить режим подключения: {e}"))?;
        return Ok(state.snapshot());
    }

    let server_id = snapshot
        .active_server_id
        .clone()
        .ok_or_else(|| "Активный сервер для переподключения не найден".to_string())?;

    if mode.uses_tun() {
        let status = ensure_tun_dependencies(&app)
            .await
            .map_err(|e| format!("Новый режим не применён: не удалось подготовить TUN: {e}"))?;
        if !status.installed {
            return Err(format!("Новый режим не применён: {}", status.message));
        }
        if !is_running_as_admin() {
            return Err(
                "Новый режим не применён: для TUN перезапусти Nimbo от имени администратора."
                    .into(),
            );
        }
    }

    disconnect_server(app.clone(), app.state::<AppState>()).await?;
    state
        .mutate(|s| s.connection_mode = mode)
        .map_err(|e| format!("Не удалось сохранить режим подключения: {e}"))?;

    let result = connect_server(app.clone(), app.state::<AppState>(), server_id).await;
    let _ = crate::tray::refresh_tray_menu(&app);
    result.map_err(|e| format!("Режим подключения сохранён, но переподключиться не удалось: {e}"))
}

#[tauri::command]
pub fn get_tun_status(app: AppHandle) -> Result<TunInstallStatus, String> {
    tun_status(&app)
}

#[tauri::command]
pub async fn install_tun(app: AppHandle) -> Result<TunInstallStatus, String> {
    ensure_tun_dependencies(&app).await?;
    tun_status(&app)
}

#[tauri::command]
pub fn restart_as_admin(app: AppHandle) -> Result<(), String> {
    relaunch_as_admin()?;
    app.exit(0);
    Ok(())
}

#[tauri::command]
pub fn set_proxy_settings(
    state: State<'_, AppState>,
    settings: ProxySettingsPatch,
) -> Result<PersistedState, String> {
    state
        .mutate(|s| {
            if let Some(username) = settings.socks_username {
                let username = username.trim();
                if !username.is_empty() {
                    s.socks_username = username.to_string();
                }
            }
            if let Some(password) = settings.socks_password {
                let password = password.trim();
                if !password.is_empty() {
                    s.socks_password = password.to_string();
                }
            }
            if let Some(require) = settings.require_socks_auth {
                s.require_socks_auth = require;
            }
            if let Some(block) = settings.block_socks_udp {
                s.block_socks_udp = block;
            }
            s.normalize_runtime_defaults();
        })
        .map_err(|e| format!("Не удалось сохранить настройки прокси: {e}"))?;
    Ok(state.snapshot())
}

#[tauri::command]
pub async fn ping_server(
    state: State<'_, AppState>,
    server_id: String,
) -> Result<ServerPing, String> {
    let snap = state.snapshot();
    let Some(server) = find_server(&snap, &server_id) else {
        return Err("Сервер не найден в подписках".into());
    };
    let protocol = normalize_latency_protocol(&snap.preferences.latency_protocol);
    let test_url = normalize_latency_test_url(&snap.preferences.latency_test_url);
    let timeout_ms = normalize_latency_timeout_ms(snap.preferences.latency_timeout_ms);
    let result = latency_ping_server(&server, timeout_ms, &protocol, &test_url).await;
    persist_ping_results(&state, std::slice::from_ref(&result))?;
    Ok(result)
}

#[tauri::command]
pub async fn ping_servers(
    state: State<'_, AppState>,
    server_ids: Vec<String>,
) -> Result<Vec<ServerPing>, String> {
    const PING_CONCURRENCY: usize = 6;

    let snap = state.snapshot();
    let protocol = normalize_latency_protocol(&snap.preferences.latency_protocol);
    let test_url = normalize_latency_test_url(&snap.preferences.latency_test_url);
    let timeout_ms = normalize_latency_timeout_ms(snap.preferences.latency_timeout_ms);
    let servers = server_ids
        .into_iter()
        .filter_map(|server_id| find_server(&snap, &server_id))
        .collect::<Vec<_>>();

    let mut out = Vec::with_capacity(servers.len());
    for chunk in servers.chunks(PING_CONCURRENCY) {
        let mut handles = Vec::with_capacity(chunk.len());
        for server in chunk {
            let server = server.clone();
            let protocol = protocol.clone();
            let test_url = test_url.clone();
            handles.push(tokio::spawn(async move {
                latency_ping_server(&server, timeout_ms, &protocol, &test_url).await
            }));
        }

        for handle in handles {
            if let Ok(result) = handle.await {
                out.push(result);
            }
        }
    }
    persist_ping_results(&state, &out)?;
    Ok(out)
}

fn persist_ping_results(state: &State<'_, AppState>, results: &[ServerPing]) -> Result<(), String> {
    state
        .mutate(|s| {
            for result in results {
                if let Some(latency) = result.latency_ms {
                    s.server_pings.insert(result.server_id.clone(), latency);
                }
            }
        })
        .map(|_| ())
        .map_err(|e| format!("Не удалось сохранить пинг серверов: {e}"))
}

#[tauri::command]
pub fn get_memory_usage(state: State<'_, AppState>) -> MemoryUsage {
    let mut pids: Vec<u32> = Vec::new();
    state.runtime(|runtime| {
        if let Some(child) = runtime.xray.as_ref() {
            pids.push(child.id());
        }
        if let Some(child) = runtime.tun2socks.as_ref() {
            pids.push(child.id());
        }
    });
    let mut total: u64 = current_process_memory();
    for pid in pids {
        total = total.saturating_add(process_memory_by_pid(pid));
    }
    MemoryUsage { bytes: total }
}

#[cfg(windows)]
fn current_process_memory() -> u64 {
    use windows_sys::Win32::System::ProcessStatus::{
        GetProcessMemoryInfo, PROCESS_MEMORY_COUNTERS,
    };
    use windows_sys::Win32::System::Threading::GetCurrentProcess;

    unsafe {
        let handle = GetCurrentProcess();
        let mut counters: PROCESS_MEMORY_COUNTERS = std::mem::zeroed();
        let size = std::mem::size_of::<PROCESS_MEMORY_COUNTERS>() as u32;
        if GetProcessMemoryInfo(handle, &mut counters, size) != 0 {
            counters.WorkingSetSize as u64
        } else {
            0
        }
    }
}

#[cfg(windows)]
fn process_memory_by_pid(pid: u32) -> u64 {
    use windows_sys::Win32::Foundation::CloseHandle;
    use windows_sys::Win32::System::ProcessStatus::{
        GetProcessMemoryInfo, PROCESS_MEMORY_COUNTERS,
    };
    use windows_sys::Win32::System::Threading::{
        OpenProcess, PROCESS_QUERY_LIMITED_INFORMATION, PROCESS_VM_READ,
    };

    unsafe {
        let handle = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, 0, pid);
        if handle.is_null() {
            return 0;
        }
        let mut counters: PROCESS_MEMORY_COUNTERS = std::mem::zeroed();
        let size = std::mem::size_of::<PROCESS_MEMORY_COUNTERS>() as u32;
        let ok = GetProcessMemoryInfo(handle, &mut counters, size);
        CloseHandle(handle);
        if ok != 0 {
            counters.WorkingSetSize as u64
        } else {
            0
        }
    }
}

#[cfg(not(windows))]
fn current_process_memory() -> u64 {
    0
}

#[cfg(not(windows))]
fn process_memory_by_pid(_pid: u32) -> u64 {
    0
}

#[cfg(windows)]
#[derive(Debug, Deserialize)]
struct PowershellNetworkConnection {
    #[serde(rename = "Protocol")]
    protocol: String,
    #[serde(rename = "State")]
    state: String,
    #[serde(rename = "LocalAddress")]
    local_address: String,
    #[serde(rename = "LocalPort")]
    local_port: u16,
    #[serde(rename = "RemoteAddress")]
    remote_address: String,
    #[serde(rename = "RemotePort")]
    remote_port: u16,
    #[serde(rename = "Pid")]
    pid: u32,
    #[serde(rename = "ProcessName")]
    process_name: Option<String>,
    #[serde(rename = "Path")]
    path: Option<String>,
}

#[derive(Debug, Clone)]
struct ActiveConnectionDecision {
    route: ActiveConnectionRoute,
    rule: String,
    server_id: Option<String>,
    server_name: Option<String>,
    server_protocol: Option<String>,
}

#[tauri::command]
pub async fn list_active_connections(
    state: State<'_, AppState>,
) -> Result<Vec<ActiveConnection>, String> {
    let snapshot = state.snapshot();
    if !snapshot.connected {
        return Ok(Vec::new());
    }
    let active_server = snapshot
        .active_server_id
        .as_deref()
        .and_then(|server_id| find_server(&snapshot, server_id));
    let tunnel_server_ips = state.runtime(|runtime| {
        runtime
            .tun_snapshot
            .as_ref()
            .map(|snapshot| snapshot.bypass_ips.clone())
            .unwrap_or_default()
    });

    tokio::task::spawn_blocking(move || {
        platform_active_connections(&snapshot, active_server.as_ref(), &tunnel_server_ips)
    })
    .await
    .map_err(|e| format!("Не удалось дождаться списка соединений: {e}"))?
}

#[cfg(windows)]
fn platform_active_connections(
    snapshot: &PersistedState,
    active_server: Option<&Server>,
    tunnel_server_ips: &[String],
) -> Result<Vec<ActiveConnection>, String> {
    let script = r#"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$processCache = @{}
function Get-ProcessMeta([uint32]$processId) {
  if (-not $processCache.ContainsKey($processId)) {
    $name = ""
    $path = $null
    try {
      $p = Get-Process -Id $processId -ErrorAction Stop
      $name = $p.ProcessName
      if (-not [string]::IsNullOrWhiteSpace($name) -and -not $name.ToLowerInvariant().EndsWith(".exe")) {
        $name = "$name.exe"
      }
      try { $path = $p.Path } catch {}
    } catch {}
    $processCache[$processId] = [PSCustomObject]@{ Name = $name; Path = $path }
  }
  return $processCache[$processId]
}

$tcpConnections = @(
  Get-NetTCPConnection -State Established -ErrorAction SilentlyContinue |
    Where-Object {
      $null -ne $_.RemotePort -and
      [uint32]$_.RemotePort -ne 0 -and
      -not [string]::IsNullOrWhiteSpace($_.RemoteAddress) -and
      @("0.0.0.0", "::", "*") -notcontains [string]$_.RemoteAddress
    }
)
$udpEndpoints = @(
  Get-NetUDPEndpoint -ErrorAction SilentlyContinue |
    Where-Object {
      [uint32]$_.OwningProcess -ne 0 -and
      [uint32]$_.LocalPort -ne 0
    }
)
$pids = @(
  $tcpConnections | ForEach-Object { [uint32]$_.OwningProcess }
  $udpEndpoints | ForEach-Object { [uint32]$_.OwningProcess }
) | Sort-Object -Unique
foreach ($processIdValue in $pids) {
  [void](Get-ProcessMeta $processIdValue)
}

$items = @(
  foreach ($c in $tcpConnections) {
    if ($null -eq $c.RemotePort -or [uint32]$c.RemotePort -eq 0) { continue }
    if ([string]::IsNullOrWhiteSpace($c.RemoteAddress)) { continue }
    if (@("0.0.0.0", "::", "*") -contains [string]$c.RemoteAddress) { continue }
    $state = $c.State.ToString()
    $meta = Get-ProcessMeta ([uint32]$c.OwningProcess)
    [PSCustomObject]@{
      Protocol = "tcp"
      State = $state
      LocalAddress = [string]$c.LocalAddress
      LocalPort = [uint32]$c.LocalPort
      RemoteAddress = [string]$c.RemoteAddress
      RemotePort = [uint32]$c.RemotePort
      Pid = [uint32]$c.OwningProcess
      ProcessName = $meta.Name
      Path = $meta.Path
    }
  }
  foreach ($u in $udpEndpoints) {
    $meta = Get-ProcessMeta ([uint32]$u.OwningProcess)
    [PSCustomObject]@{
      Protocol = "udp"
      State = "Bound"
      LocalAddress = [string]$u.LocalAddress
      LocalPort = [uint32]$u.LocalPort
      RemoteAddress = "*"
      RemotePort = 0
      Pid = [uint32]$u.OwningProcess
      ProcessName = $meta.Name
      Path = $meta.Path
    }
  }
)
[Console]::Out.Write((ConvertTo-Json -InputObject @($items) -Compress -Depth 4))
"#;

    let mut command = hidden_output_command("powershell");
    command
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(script)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    let output = command_output_with_timeout(command, std::time::Duration::from_secs(6))
        .map_err(|e| format!("Не удалось получить активные соединения: {e}"))?;
    if !output.status.success() {
        return Err(format!(
            "Не удалось получить активные соединения: {}",
            output.status
        ));
    }

    let text = String::from_utf8_lossy(&output.stdout).trim().to_string();
    if text.is_empty() {
        return Ok(Vec::new());
    }
    let json: serde_json::Value = serde_json::from_str(&text)
        .map_err(|e| format!("Не удалось прочитать список соединений: {e}"))?;
    let raw = match json {
        serde_json::Value::Array(items) => items
            .into_iter()
            .filter_map(|value| serde_json::from_value::<PowershellNetworkConnection>(value).ok())
            .collect::<Vec<_>>(),
        serde_json::Value::Object(_) => serde_json::from_value::<PowershellNetworkConnection>(json)
            .map(|item| vec![item])
            .unwrap_or_default(),
        _ => Vec::new(),
    };

    let ports = ProxyPorts::default();
    let profile_rules = xray_routing_profile_rules(snapshot, &[]);
    let app_rules = active_server
        .map(|server| combined_app_proxy_rules(snapshot, server))
        .unwrap_or_default();

    let mut out = Vec::new();
    for item in raw {
        let process = item
            .process_name
            .as_deref()
            .map(str::trim)
            .filter(|name| !name.is_empty())
            .map(process_exe_name)
            .unwrap_or_else(|| {
                if item.pid == 4 {
                    "System".into()
                } else {
                    "Unknown process".into()
                }
            });
        let process_path = item.path.as_ref().and_then(|path| {
            let trimmed = path.trim();
            (!trimmed.is_empty()).then(|| trimmed.to_string())
        });
        if should_hide_internal_connection(
            &process,
            &item.local_address,
            &item.remote_address,
            item.remote_port,
            ports,
        ) {
            continue;
        }

        let decision = classify_active_connection(
            snapshot,
            active_server,
            tunnel_server_ips,
            &app_rules,
            &profile_rules,
            &process,
            process_path.as_deref(),
            &item.remote_address,
            item.remote_port,
            ports,
        );

        let udp_endpoint = item.protocol.eq_ignore_ascii_case("udp") && item.remote_port == 0;
        out.push(ActiveConnection {
            id: format!(
                "{}:{}-{}:{}-{}",
                item.local_address,
                item.local_port,
                item.remote_address,
                item.remote_port,
                item.pid
            ),
            protocol: item.protocol.to_ascii_uppercase(),
            state: item.state,
            source: format!("{}:{}", item.local_address, item.local_port),
            destination: if udp_endpoint {
                "*".into()
            } else {
                format!("{}:{}", item.remote_address, item.remote_port)
            },
            remote_address: item.remote_address,
            remote_port: item.remote_port,
            process,
            process_path,
            pid: item.pid,
            route: decision.route,
            rule: decision.rule,
            server_id: decision.server_id,
            server_name: decision.server_name,
            server_protocol: decision.server_protocol,
        });
    }

    out.sort_by(|a, b| {
        route_sort_weight(a.route)
            .cmp(&route_sort_weight(b.route))
            .then_with(|| a.process.cmp(&b.process))
            .then_with(|| a.destination.cmp(&b.destination))
    });
    Ok(out)
}

#[cfg(not(windows))]
fn platform_active_connections(
    _snapshot: &PersistedState,
    _active_server: Option<&Server>,
    _tunnel_server_ips: &[String],
) -> Result<Vec<ActiveConnection>, String> {
    Ok(Vec::new())
}

// OS adapters provide this data flat; grouping it would make the call sites less clear.
#[allow(clippy::too_many_arguments)]
fn classify_active_connection(
    snapshot: &PersistedState,
    active_server: Option<&Server>,
    tunnel_server_ips: &[String],
    app_rules: &[AppProxyRule],
    profile_rules: &XrayRoutingProfileRules,
    process: &str,
    process_path: Option<&str>,
    remote_address: &str,
    remote_port: u16,
    ports: ProxyPorts,
) -> ActiveConnectionDecision {
    if let Some(rule) = matching_app_proxy_rule(app_rules, process, process_path) {
        return match rule.mode {
            AppProxyMode::Proxy => proxy_connection_decision(active_server, "process rule"),
            AppProxyMode::Direct => ActiveConnectionDecision {
                route: ActiveConnectionRoute::Direct,
                rule: "process rule".into(),
                server_id: None,
                server_name: None,
                server_protocol: None,
            },
        };
    }

    if snapshot.connected {
        if is_local_proxy_endpoint(remote_address, remote_port, ports) {
            return proxy_connection_decision(active_server, "system proxy");
        }
        if matches_active_server_endpoint(
            active_server,
            tunnel_server_ips,
            remote_address,
            remote_port,
        ) {
            return proxy_connection_decision(active_server, "xray outbound");
        }
    }

    if let Ok(ip) = remote_address.parse::<IpAddr>() {
        if let Some((route, rule)) = matching_profile_ip_rule(profile_rules, &ip) {
            return match route {
                ActiveConnectionRoute::Proxy => proxy_connection_decision(active_server, rule),
                ActiveConnectionRoute::Direct => ActiveConnectionDecision {
                    route,
                    rule: rule.into(),
                    server_id: None,
                    server_name: None,
                    server_protocol: None,
                },
                ActiveConnectionRoute::Block => ActiveConnectionDecision {
                    route,
                    rule: rule.into(),
                    server_id: None,
                    server_name: None,
                    server_protocol: None,
                },
                ActiveConnectionRoute::Unknown => unknown_connection_decision("ip rule"),
            };
        }
    }

    if profile_rules.global_proxy && snapshot.connected {
        proxy_connection_decision(active_server, "fallback")
    } else if !profile_rules.global_proxy {
        ActiveConnectionDecision {
            route: ActiveConnectionRoute::Direct,
            rule: "fallback".into(),
            server_id: None,
            server_name: None,
            server_protocol: None,
        }
    } else {
        unknown_connection_decision("not connected")
    }
}

fn proxy_connection_decision(
    active_server: Option<&Server>,
    rule: impl Into<String>,
) -> ActiveConnectionDecision {
    ActiveConnectionDecision {
        route: ActiveConnectionRoute::Proxy,
        rule: rule.into(),
        server_id: active_server.map(|server| server.id.clone()),
        server_name: active_server.map(|server| server.name.clone()),
        server_protocol: active_server.map(server_protocol_label),
    }
}

fn unknown_connection_decision(rule: impl Into<String>) -> ActiveConnectionDecision {
    ActiveConnectionDecision {
        route: ActiveConnectionRoute::Unknown,
        rule: rule.into(),
        server_id: None,
        server_name: None,
        server_protocol: None,
    }
}

fn route_sort_weight(route: ActiveConnectionRoute) -> u8 {
    match route {
        ActiveConnectionRoute::Proxy => 0,
        ActiveConnectionRoute::Direct => 1,
        ActiveConnectionRoute::Block => 2,
        ActiveConnectionRoute::Unknown => 3,
    }
}

fn should_hide_internal_connection(
    process: &str,
    local_address: &str,
    remote_address: &str,
    remote_port: u16,
    ports: ProxyPorts,
) -> bool {
    if matches!(
        normalized_process_name(process).as_str(),
        "nimbo" | "nimbo-ui" | "xray" | "tun2socks"
    ) {
        return true;
    }

    let remote_is_loopback = remote_address
        .parse::<IpAddr>()
        .is_ok_and(|ip| ip.is_loopback());
    if !remote_is_loopback || is_local_proxy_endpoint(remote_address, remote_port, ports) {
        return false;
    }

    local_address
        .parse::<IpAddr>()
        .map(|ip| ip.is_loopback())
        .unwrap_or(true)
}

fn is_local_proxy_endpoint(remote_address: &str, remote_port: u16, ports: ProxyPorts) -> bool {
    matches!(remote_port, port if port == ports.socks || port == ports.http)
        && remote_address
            .parse::<IpAddr>()
            .is_ok_and(|ip| ip.is_loopback())
}

fn matches_active_server_endpoint(
    active_server: Option<&Server>,
    tunnel_server_ips: &[String],
    remote_address: &str,
    remote_port: u16,
) -> bool {
    let Some(server) = active_server else {
        return false;
    };
    let (host, port) = server_endpoint(server);
    if remote_port != port {
        return false;
    }
    if remote_address.eq_ignore_ascii_case(&host) {
        return true;
    }
    tunnel_server_ips
        .iter()
        .any(|ip| ip.eq_ignore_ascii_case(remote_address))
}

fn matching_app_proxy_rule<'a>(
    rules: &'a [AppProxyRule],
    process: &str,
    process_path: Option<&str>,
) -> Option<&'a AppProxyRule> {
    let process_file = normalize_process_file(process);
    let process_stem = process_file.trim_end_matches(".exe").to_string();
    let normalized_path = process_path.map(normalize_process_key);
    let process_path_file = process_path.map(normalize_process_file);
    let process_path_stem = process_path_file
        .as_deref()
        .map(|file| file.trim_end_matches(".exe").to_string());

    rules.iter().find(|rule| {
        if !rule.enabled {
            return false;
        }
        let rule_key = normalize_process_key(&rule.executable_path);
        if normalized_path.as_deref() == Some(rule_key.as_str()) {
            return true;
        }
        let rule_file = normalize_process_file(&rule.executable_path);
        let rule_stem = rule_file.trim_end_matches(".exe");
        rule_key == process_file
            || rule_file == process_file
            || (!rule_stem.is_empty() && rule_stem == process_stem)
            || process_path_file.as_deref() == Some(rule_file.as_str())
            || (!rule_stem.is_empty() && process_path_stem.as_deref() == Some(rule_stem))
    })
}

fn normalize_process_key(value: &str) -> String {
    value.trim().replace('\\', "/").to_ascii_lowercase()
}

fn normalize_process_file(value: &str) -> String {
    normalize_process_key(value)
        .split('/')
        .next_back()
        .unwrap_or_default()
        .trim()
        .to_string()
}

fn matching_profile_ip_rule(
    profile: &XrayRoutingProfileRules,
    ip: &IpAddr,
) -> Option<(ActiveConnectionRoute, &'static str)> {
    for action in routing_action_order(&profile.rule_order) {
        let (items, route) = match action {
            "block" => (&profile.block_ips, ActiveConnectionRoute::Block),
            "proxy" => (&profile.proxy_ips, ActiveConnectionRoute::Proxy),
            "direct" => (&profile.direct_ips, ActiveConnectionRoute::Direct),
            _ => continue,
        };
        if items.iter().any(|rule| ip_rule_matches(ip, rule)) {
            return Some((route, "ip rule"));
        }
    }

    if profile.bypass_local_ip && is_private_or_local_ip(ip) {
        return Some((ActiveConnectionRoute::Direct, "private ip"));
    }

    None
}

fn routing_action_order(rule_order: &str) -> Vec<&'static str> {
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

fn ip_rule_matches(ip: &IpAddr, rule: &str) -> bool {
    let rule = rule.trim();
    if rule.is_empty() {
        return false;
    }
    if rule.eq_ignore_ascii_case("geoip:private") {
        return is_private_or_local_ip(ip);
    }
    if let Ok(candidate) = rule.parse::<IpAddr>() {
        return &candidate == ip;
    }
    if rule.contains('/') {
        return ip_matches_cidr(ip, rule);
    }
    false
}

fn ip_matches_cidr(ip: &IpAddr, cidr: &str) -> bool {
    let Some((base, prefix)) = cidr.split_once('/') else {
        return false;
    };
    let Ok(prefix) = prefix.trim().parse::<u8>() else {
        return false;
    };
    let Ok(base) = base.trim().parse::<IpAddr>() else {
        return false;
    };
    match (ip, base) {
        (IpAddr::V4(ip), IpAddr::V4(base)) if prefix <= 32 => {
            let mask = if prefix == 0 {
                0
            } else {
                u32::MAX << (32 - prefix)
            };
            u32::from(*ip) & mask == u32::from(base) & mask
        }
        (IpAddr::V6(ip), IpAddr::V6(base)) if prefix <= 128 => {
            let mask = if prefix == 0 {
                0
            } else {
                u128::MAX << (128 - prefix)
            };
            u128::from(*ip) & mask == u128::from(base) & mask
        }
        _ => false,
    }
}

fn is_private_or_local_ip(ip: &IpAddr) -> bool {
    match ip {
        IpAddr::V4(ip) => {
            ip.is_private()
                || ip.is_loopback()
                || ip.is_link_local()
                || ip.is_broadcast()
                || ip.octets()[0] == 0
        }
        IpAddr::V6(ip) => {
            let first = ip.segments()[0];
            ip.is_loopback()
                || ip.is_unspecified()
                || (first & 0xfe00) == 0xfc00
                || (first & 0xffc0) == 0xfe80
        }
    }
}

fn server_protocol_label(server: &Server) -> String {
    match &server.protocol {
        nimbo_subscription::Protocol::Vless(_) => "VLESS",
        nimbo_subscription::Protocol::Vmess(_) => "VMess",
        nimbo_subscription::Protocol::Trojan(_) => "Trojan",
        nimbo_subscription::Protocol::Shadowsocks(_) => "Shadowsocks",
        nimbo_subscription::Protocol::Hysteria2(_) => "Hysteria2",
    }
    .into()
}

#[derive(Debug, Clone, Serialize)]
pub struct RoutingProfileSummary {
    pub id: String,
    pub name: String,
    pub description: String,
    pub builtin: bool,
    pub rules_count: u32,
    pub action: String,
    pub strategy: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct RoutingProfileList {
    pub profiles: Vec<RoutingProfileSummary>,
    pub active: String,
}

fn summarize_profile(profile: &RoutingProfile) -> RoutingProfileSummary {
    RoutingProfileSummary {
        id: profile.id.clone(),
        name: profile.name.clone(),
        description: profile.description.clone(),
        builtin: profile.builtin,
        rules_count: count_rules(profile),
        action: profile.rule_order.clone(),
        strategy: profile.domain_strategy.clone(),
    }
}

fn snapshot_with_profiles(state: &State<'_, AppState>) -> PersistedState {
    let mut snapshot = state.snapshot();
    let needs_seed = snapshot.routing_profiles.is_empty();
    if needs_seed {
        ensure_routing_profiles(&mut snapshot);
        let seeded = snapshot.routing_profiles.clone();
        let _ = state.mutate(|s| {
            if s.routing_profiles.is_empty() {
                s.routing_profiles = seeded;
            }
        });
    }
    snapshot
}

fn build_routing_list(snapshot: &PersistedState) -> RoutingProfileList {
    let profiles: Vec<RoutingProfileSummary> = snapshot
        .routing_profiles
        .iter()
        .map(summarize_profile)
        .collect();
    let mut active = snapshot.active_routing_profile.clone();
    if !profiles.iter().any(|p| p.id == active) {
        active = profiles
            .first()
            .map(|p| p.id.clone())
            .unwrap_or_else(|| "global".into());
    }
    RoutingProfileList { profiles, active }
}

#[tauri::command]
pub fn list_routing_profiles(state: State<'_, AppState>) -> RoutingProfileList {
    let snapshot = snapshot_with_profiles(&state);
    build_routing_list(&snapshot)
}

#[tauri::command]
pub fn set_active_routing_profile(
    state: State<'_, AppState>,
    profile_id: String,
) -> Result<RoutingProfileList, String> {
    let snapshot = snapshot_with_profiles(&state);
    let trimmed = profile_id.trim().to_string();
    if !snapshot.routing_profiles.iter().any(|p| p.id == trimmed) {
        return Err(format!("Профиль маршрутизации не найден: {trimmed}"));
    }
    state
        .mutate(|s| s.active_routing_profile = trimmed.clone())
        .map_err(|e| format!("Не удалось сохранить активный профиль: {e}"))?;
    Ok(build_routing_list(&snapshot_with_profiles(&state)))
}

#[tauri::command]
pub fn get_routing_profile(
    state: State<'_, AppState>,
    profile_id: String,
) -> Result<RoutingProfile, String> {
    let snapshot = snapshot_with_profiles(&state);
    snapshot
        .routing_profiles
        .into_iter()
        .find(|p| p.id == profile_id)
        .ok_or_else(|| format!("Профиль не найден: {profile_id}"))
}

#[tauri::command]
pub fn update_routing_profile(
    state: State<'_, AppState>,
    profile: RoutingProfile,
) -> Result<RoutingProfile, String> {
    let trimmed_id = profile.id.trim().to_string();
    if trimmed_id.is_empty() {
        return Err("Профиль должен иметь идентификатор".into());
    }
    state
        .mutate(|s| {
            ensure_routing_profiles(s);
            let mut next = profile.clone();
            next.id = trimmed_id.clone();
            next.name = next.name.trim().to_string();
            if next.name.is_empty() {
                next.name = next.id.clone();
            }
            if let Some(idx) = s.routing_profiles.iter().position(|p| p.id == trimmed_id) {
                let existing_builtin = s.routing_profiles[idx].builtin;
                next.builtin = existing_builtin || next.builtin;
                s.routing_profiles[idx] = next;
            } else {
                s.routing_profiles.push(next);
            }
        })
        .map_err(|e| format!("Не удалось сохранить профиль: {e}"))?;
    let snapshot = state.snapshot();
    snapshot
        .routing_profiles
        .into_iter()
        .find(|p| p.id == trimmed_id)
        .ok_or_else(|| "Профиль не найден после сохранения".into())
}

#[tauri::command]
pub fn delete_routing_profile(
    state: State<'_, AppState>,
    profile_id: String,
) -> Result<RoutingProfileList, String> {
    let snapshot = snapshot_with_profiles(&state);
    let Some(target) = snapshot
        .routing_profiles
        .iter()
        .find(|p| p.id == profile_id)
    else {
        return Err(format!("Профиль не найден: {profile_id}"));
    };
    let was_builtin = target.builtin;
    state
        .mutate(|s| {
            s.routing_profiles.retain(|p| p.id != profile_id);
            if was_builtin && !s.deleted_builtin_profiles.contains(&profile_id) {
                s.deleted_builtin_profiles.push(profile_id.clone());
            }
            if s.active_routing_profile == profile_id {
                s.active_routing_profile = s
                    .routing_profiles
                    .first()
                    .map(|p| p.id.clone())
                    .unwrap_or_else(|| "global".into());
            }
        })
        .map_err(|e| format!("Не удалось удалить профиль: {e}"))?;
    Ok(build_routing_list(&state.snapshot()))
}

#[tauri::command]
pub fn export_routing_profile(
    state: State<'_, AppState>,
    profile_id: String,
) -> Result<String, String> {
    let profile = get_routing_profile(state, profile_id)?;
    serde_json::to_string_pretty(&profile)
        .map_err(|e| format!("Не удалось сериализовать профиль: {e}"))
}

#[tauri::command]
pub fn import_routing_profile(
    state: State<'_, AppState>,
    payload: String,
) -> Result<RoutingProfile, String> {
    let trimmed = payload.trim();
    if trimmed.is_empty() {
        return Err("Пустые данные импорта".into());
    }
    let json = if trimmed.starts_with('{') {
        trimmed.to_string()
    } else {
        let cleaned: String = trimmed.chars().filter(|c| !c.is_whitespace()).collect();
        let bytes = decode_base64(&cleaned).ok_or_else(|| {
            "Не удалось декодировать base64. Ожидается JSON или base64.".to_string()
        })?;
        String::from_utf8(bytes)
            .map_err(|_| "base64 декодирован, но это не текст UTF-8".to_string())?
    };
    let parsed: RoutingProfile = serde_json::from_str(&json)
        .map_err(|e| format!("Не удалось разобрать JSON профиля: {e}"))?;

    let mut next = parsed;
    next.id = next.id.trim().to_string();
    if next.id.is_empty() {
        next.id = format!("custom-{}", uuid::Uuid::new_v4().simple());
    }
    next.builtin = false;

    let saved_id = next.id.clone();
    state
        .mutate(|s| {
            ensure_routing_profiles(s);
            if let Some(idx) = s.routing_profiles.iter().position(|p| p.id == next.id) {
                if s.routing_profiles[idx].builtin {
                    next.id = format!("custom-{}", uuid::Uuid::new_v4().simple());
                }
            }
            if let Some(idx) = s.routing_profiles.iter().position(|p| p.id == next.id) {
                s.routing_profiles[idx] = next.clone();
            } else {
                s.routing_profiles.push(next.clone());
            }
        })
        .map_err(|e| format!("Не удалось сохранить импортированный профиль: {e}"))?;

    state
        .snapshot()
        .routing_profiles
        .into_iter()
        .find(|p| p.id == saved_id || p.id.starts_with("custom-"))
        .ok_or_else(|| "Профиль импортирован, но не найден".into())
}

#[tauri::command]
pub fn reset_builtin_routing_profiles(
    state: State<'_, AppState>,
) -> Result<RoutingProfileList, String> {
    state
        .mutate(|s| {
            s.deleted_builtin_profiles.clear();
            let defaults = builtin_routing_profiles();
            let builtin_ids: HashSet<String> = defaults.iter().map(|p| p.id.clone()).collect();
            s.routing_profiles.retain(|p| !builtin_ids.contains(&p.id));
            for profile in defaults {
                s.routing_profiles.insert(0, profile);
            }
        })
        .map_err(|e| format!("Не удалось сбросить профили: {e}"))?;
    Ok(build_routing_list(&state.snapshot()))
}

#[tauri::command]
pub fn open_routing_folder() -> Result<(), String> {
    let dir = nimbo_data_dir().map_err(|e| format!("Не удалось получить путь к данным: {e}"))?;
    std::fs::create_dir_all(&dir).map_err(|e| format!("Не удалось создать папку: {e}"))?;

    #[cfg(windows)]
    {
        let mut command = Command::new("explorer.exe");
        command.arg(&dir);
        command
            .spawn()
            .map_err(|e| format!("Не удалось открыть проводник: {e}"))?;
    }
    #[cfg(target_os = "macos")]
    {
        let _ = Command::new("open").arg(&dir).spawn();
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        let _ = Command::new("xdg-open").arg(&dir).spawn();
    }
    Ok(())
}

#[tauri::command]
pub fn open_logs_folder() -> Result<(), String> {
    let dir = nimbo_data_dir()
        .map_err(|e| format!("Не удалось получить путь к данным: {e}"))?
        .join("logs");
    std::fs::create_dir_all(&dir).map_err(|e| format!("Не удалось создать папку логов: {e}"))?;

    #[cfg(windows)]
    Command::new("explorer.exe")
        .arg(&dir)
        .spawn()
        .map_err(|e| format!("Не удалось открыть папку логов: {e}"))?;
    #[cfg(target_os = "macos")]
    Command::new("open")
        .arg(&dir)
        .spawn()
        .map_err(|e| format!("Не удалось открыть папку логов: {e}"))?;
    #[cfg(all(unix, not(target_os = "macos")))]
    Command::new("xdg-open")
        .arg(&dir)
        .spawn()
        .map_err(|e| format!("Не удалось открыть папку логов: {e}"))?;
    Ok(())
}

fn decode_base64(input: &str) -> Option<Vec<u8>> {
    const TABLE: &[u8; 256] = &{
        let mut t = [255u8; 256];
        let chars = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        let mut i = 0;
        while i < chars.len() {
            t[chars[i] as usize] = i as u8;
            i += 1;
        }
        t[b'-' as usize] = 62;
        t[b'_' as usize] = 63;
        t
    };
    let bytes: Vec<u8> = input.bytes().filter(|b| *b != b'=').collect();
    let mut out = Vec::with_capacity(bytes.len() * 3 / 4);
    let mut buf: u32 = 0;
    let mut bits: u32 = 0;
    for b in bytes {
        let v = TABLE[b as usize];
        if v == 255 {
            return None;
        }
        buf = (buf << 6) | v as u32;
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            out.push((buf >> bits) as u8);
            buf &= (1 << bits) - 1;
        }
    }
    Some(out)
}

fn current_month_period() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0);
    let days = now / 86_400;
    let (year, month) = days_to_year_month(days);
    format!("{:04}-{:02}", year, month)
}

fn unix_timestamp_millis() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
        .min(u64::MAX as u128) as u64
}

fn days_to_year_month(mut days: i64) -> (i32, u32) {
    let mut year: i32 = 1970;
    loop {
        let in_year = if is_leap_year(year) { 366 } else { 365 };
        if days >= in_year {
            days -= in_year;
            year += 1;
        } else {
            break;
        }
    }
    let months_in_year = if is_leap_year(year) {
        [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    } else {
        [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    };
    let mut month: u32 = 1;
    for &m in &months_in_year {
        if days < m as i64 {
            break;
        }
        days -= m as i64;
        month += 1;
    }
    (year, month)
}

fn is_leap_year(year: i32) -> bool {
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}

#[tauri::command]
pub async fn get_traffic_stats(
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<TrafficStats, String> {
    let snapshot = state.snapshot();
    let mut totals = snapshot.traffic_totals.clone();
    let current = current_month_period();
    if totals.monthly_period != current {
        totals.monthly_period = current.clone();
        totals.monthly_upload = 0;
        totals.monthly_download = 0;
    }

    let (session, upload_speed, download_speed, speed_available) = if snapshot.connected {
        let xray_running = state.runtime(|runtime| runtime.xray.is_some());
        if xray_running {
            let xray_path = ensure_xray_binary(&app).await?;
            let session = query_xray_session_traffic(xray_path, ProxyPorts::default()).await?;
            let rate = state.runtime(|runtime| {
                record_traffic_sample(
                    &mut runtime.traffic_samples,
                    std::time::Instant::now(),
                    &session,
                )
            });
            let (upload_speed, download_speed, speed_available) = rate
                .map(|(upload, download)| (upload, download, true))
                .unwrap_or((0.0, 0.0, false));
            (session, upload_speed, download_speed, speed_available)
        } else {
            state.runtime(|runtime| runtime.traffic_samples.clear());
            (SessionTraffic::default(), 0.0, 0.0, false)
        }
    } else {
        state.runtime(|runtime| runtime.traffic_samples.clear());
        (SessionTraffic::default(), 0.0, 0.0, false)
    };

    Ok(TrafficStats {
        session_upload: session.upload,
        session_download: session.download,
        upload_speed,
        download_speed,
        speed_available,
        all_time_upload: totals.all_time_upload.saturating_add(session.upload),
        all_time_download: totals.all_time_download.saturating_add(session.download),
        monthly_upload: totals.monthly_upload.saturating_add(session.upload),
        monthly_download: totals.monthly_download.saturating_add(session.download),
        monthly_period: current,
    })
}

const TRAFFIC_RATE_WINDOW: Duration = Duration::from_secs(4);

fn record_traffic_sample(
    samples: &mut VecDeque<TrafficRuntimeSample>,
    at: std::time::Instant,
    traffic: &SessionTraffic,
) -> Option<(f64, f64)> {
    if samples.back().is_some_and(|previous| {
        traffic.upload < previous.upload || traffic.download < previous.download
    }) {
        samples.clear();
    }

    samples.push_back(TrafficRuntimeSample {
        at,
        upload: traffic.upload,
        download: traffic.download,
    });
    while samples.len() > 2
        && samples
            .front()
            .is_some_and(|sample| at.duration_since(sample.at) > TRAFFIC_RATE_WINDOW)
    {
        samples.pop_front();
    }
    traffic_rate(samples)
}

fn traffic_rate(samples: &VecDeque<TrafficRuntimeSample>) -> Option<(f64, f64)> {
    let first = samples.front()?;
    let last = samples.back()?;
    let elapsed = last.at.duration_since(first.at).as_secs_f64();
    if elapsed < 0.25 || last.upload < first.upload || last.download < first.download {
        return None;
    }
    Some((
        (last.upload - first.upload) as f64 / elapsed,
        (last.download - first.download) as f64 / elapsed,
    ))
}

#[tauri::command]
pub fn reset_traffic_totals(state: State<'_, AppState>) -> Result<TrafficTotals, String> {
    state
        .mutate(|s| {
            s.traffic_totals = TrafficTotals {
                all_time_upload: 0,
                all_time_download: 0,
                monthly_upload: 0,
                monthly_download: 0,
                monthly_period: current_month_period(),
            };
        })
        .map_err(|e| format!("Не удалось сбросить счетчики: {e}"))?;
    Ok(state.snapshot().traffic_totals)
}

fn read_log_tail(path: &Path, max_lines: usize) -> Vec<String> {
    let Ok(contents) = std::fs::read_to_string(path) else {
        return Vec::new();
    };
    let lines: Vec<&str> = contents.lines().collect();
    let start = lines.len().saturating_sub(max_lines);
    lines[start..].iter().map(|s| s.to_string()).collect()
}

fn log_sort_key(timestamp: Option<&str>) -> String {
    timestamp
        .unwrap_or_default()
        .replace('/', "-")
        .replace('T', " ")
        .trim_end_matches('Z')
        .to_string()
}

fn strip_ansi_codes(input: &str) -> String {
    let mut result = String::with_capacity(input.len());
    let mut chars = input.chars().peekable();
    while let Some(ch) = chars.next() {
        if ch == '\u{1b}' && chars.peek() == Some(&'[') {
            chars.next();
            for code in chars.by_ref() {
                if code.is_ascii_alphabetic() {
                    break;
                }
            }
            continue;
        }
        result.push(ch);
    }
    result
}

fn parse_log_line(source: &str, raw: &str) -> Option<TunnelLogEntry> {
    let clean = strip_ansi_codes(raw);
    let trimmed = clean.trim();
    if trimmed.is_empty() {
        return None;
    }

    let mut timestamp: Option<String> = None;
    let mut rest = trimmed.to_string();
    let bytes = trimmed.as_bytes();
    if bytes.len() >= 19
        && bytes[4] == b'/'
        && bytes[7] == b'/'
        && bytes[10] == b' '
        && bytes[13] == b':'
        && bytes[16] == b':'
    {
        timestamp = Some(trimmed[..19].to_string());
        rest = trimmed[19..]
            .trim_start_matches(|c: char| c == '.' || c.is_ascii_digit())
            .trim_start()
            .to_string();
    } else if bytes.len() >= 19
        && bytes[4] == b'-'
        && bytes[7] == b'-'
        && (bytes[10] == b'T' || bytes[10] == b' ')
        && bytes[13] == b':'
        && bytes[16] == b':'
    {
        timestamp = Some(trimmed[..19].replace('T', " "));
        rest = trimmed[19..]
            .trim_start_matches(|c: char| c == '.' || c == 'Z' || c.is_ascii_digit())
            .trim_start()
            .to_string();
    }

    let lower = rest.to_lowercase();
    let level = if lower.starts_with("error ") || lower.contains("[error]") {
        "error"
    } else if lower.starts_with("warn ")
        || lower.starts_with("warning ")
        || lower.contains("[warning]")
        || lower.contains("[warn]")
    {
        "warn"
    } else if lower.starts_with("debug ") || lower.contains("[debug]") {
        "debug"
    } else {
        "info"
    };

    for prefix in ["ERROR ", "WARN ", "WARNING ", "INFO ", "DEBUG ", "TRACE "] {
        if rest.starts_with(prefix) {
            rest = rest[prefix.len()..].trim_start().to_string();
            break;
        }
    }

    Some(TunnelLogEntry {
        source: source.into(),
        level: level.into(),
        timestamp,
        message: rest,
    })
}

#[tauri::command]
pub fn get_tunnel_logs(limit: Option<usize>) -> Result<Vec<TunnelLogEntry>, String> {
    let max_lines = limit.unwrap_or(500).min(5000);
    let mut entries: Vec<(String, usize, TunnelLogEntry)> = Vec::new();
    let mut sequence = 0usize;
    if let Ok(dir) = nimbo_data_dir() {
        let runtime = dir.join("runtime");
        let mut sources = vec![
            ("xray", runtime.join("xray.log.1")),
            ("xray", runtime.join("xray.log")),
            ("tun2socks", runtime.join("tun2socks.log.1")),
            ("tun2socks", runtime.join("tun2socks.log")),
        ];
        if let Some(app_log) = crate::logging::app_log_path() {
            sources.push(("nimbo", app_log.with_extension("log.1")));
            sources.push(("nimbo", app_log));
        }
        if let Some(helper_log) = helper_log_path() {
            sources.push(("helper", helper_log.with_extension("log.1")));
            sources.push(("helper", helper_log));
        }

        for (source, path) in sources {
            for raw in read_log_tail(&path, max_lines) {
                if let Some(entry) = parse_log_line(source, &raw) {
                    let sort_key = log_sort_key(entry.timestamp.as_deref());
                    entries.push((sort_key, sequence, entry));
                    sequence += 1;
                }
            }
        }
    }
    entries.sort_by(|a, b| a.0.cmp(&b.0).then_with(|| a.1.cmp(&b.1)));
    if entries.len() > max_lines {
        let start = entries.len() - max_lines;
        entries = entries.split_off(start);
    }
    Ok(entries.into_iter().map(|(_, _, entry)| entry).collect())
}

#[tauri::command]
pub fn clear_tunnel_logs() -> Result<(), String> {
    let data_dir = nimbo_data_dir().map_err(|e| format!("Не удалось получить папку логов: {e}"))?;
    let runtime = data_dir.join("runtime");
    let mut paths = vec![
        runtime.join("xray.log"),
        runtime.join("xray.log.1"),
        runtime.join("tun2socks.log"),
        runtime.join("tun2socks.log.1"),
        data_dir.join("logs").join("nimbo.log"),
        data_dir.join("logs").join("nimbo.log.1"),
    ];
    paths.retain(|path| path.exists());
    for path in paths {
        if path.exists() {
            if let Err(e) = std::fs::write(&path, b"") {
                return Err(format!("Не удалось очистить {}: {e}", path.display()));
            }
        }
    }
    tracing::info!("application logs cleared by user");
    Ok(())
}

fn helper_log_path() -> Option<PathBuf> {
    std::env::var_os("ProgramData")
        .map(PathBuf::from)
        .or_else(dirs::data_local_dir)
        .map(|base| base.join("Nimbo").join("helper.log"))
}

fn rotate_runtime_log(path: &Path) -> Result<(), String> {
    let Ok(metadata) = std::fs::metadata(path) else {
        return Ok(());
    };
    if metadata.len() < MAX_RUNTIME_LOG_BYTES {
        return Ok(());
    }

    let rotated = path.with_extension("log.1");
    if rotated.exists() {
        std::fs::remove_file(&rotated)
            .map_err(|e| format!("Не удалось удалить старый лог {}: {e}", rotated.display()))?;
    }
    std::fs::rename(path, &rotated)
        .map_err(|e| format!("Не удалось ротировать лог {}: {e}", path.display()))
}

#[tauri::command]
pub async fn get_session_traffic(
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<SessionTraffic, String> {
    let snapshot = state.snapshot();
    if !snapshot.connected {
        return Ok(SessionTraffic::default());
    }

    let xray_running = state.runtime(|runtime| runtime.xray.is_some());
    if !xray_running {
        return Ok(SessionTraffic::default());
    }

    let xray_path = ensure_xray_binary(&app).await?;
    query_xray_session_traffic(xray_path, ProxyPorts::default()).await
}

async fn query_xray_session_traffic(
    xray_path: PathBuf,
    ports: ProxyPorts,
) -> Result<SessionTraffic, String> {
    tokio::task::spawn_blocking(move || {
        let _query_guard = XRAY_STATS_QUERY_LOCK
            .lock()
            .map_err(|_| "Блокировка статистики Xray повреждена".to_string())?;
        let mut command = hidden_output_path_command(&xray_path);
        command
            .arg("api")
            .arg("statsquery")
            .arg(format!("--server=127.0.0.1:{}", ports.api))
            .arg("-pattern")
            .arg("")
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        let output = command_output_with_timeout(command, std::time::Duration::from_secs(3))
            .map_err(|e| format!("Не удалось запросить статистику Xray: {e}"))?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr).trim().to_string();
            return Err(if stderr.is_empty() {
                format!("Xray statsquery завершился с кодом {}", output.status)
            } else {
                format!(
                    "Xray statsquery завершился с кодом {}: {stderr}",
                    output.status
                )
            });
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        Ok(parse_xray_stats_output(&stdout))
    })
    .await
    .map_err(|e| format!("Не удалось дождаться статистики Xray: {e}"))?
}

fn command_output_with_timeout(
    mut command: Command,
    timeout: std::time::Duration,
) -> Result<std::process::Output, String> {
    use std::io::Read;

    let mut child = command
        .spawn()
        .map_err(|e| format!("процесс не запустился: {e}"))?;
    let stdout_reader = child.stdout.take().map(|mut stdout| {
        std::thread::spawn(move || {
            let mut buffer = Vec::new();
            stdout.read_to_end(&mut buffer).map(|_| buffer)
        })
    });
    let stderr_reader = child.stderr.take().map(|mut stderr| {
        std::thread::spawn(move || {
            let mut buffer = Vec::new();
            stderr.read_to_end(&mut buffer).map(|_| buffer)
        })
    });
    let start = std::time::Instant::now();
    loop {
        match child.try_wait() {
            Ok(Some(status)) => {
                let stdout = join_command_output_reader(stdout_reader, "stdout")?;
                let stderr = join_command_output_reader(stderr_reader, "stderr")?;
                return Ok(std::process::Output {
                    status,
                    stdout,
                    stderr,
                });
            }
            Ok(None) if start.elapsed() >= timeout => {
                let _ = child.kill();
                let _ = child.wait();
                let _ = join_command_output_reader(stdout_reader, "stdout");
                let _ = join_command_output_reader(stderr_reader, "stderr");
                return Err("таймаут".into());
            }
            Ok(None) => std::thread::sleep(std::time::Duration::from_millis(80)),
            Err(e) => {
                let _ = child.kill();
                let _ = child.wait();
                let _ = join_command_output_reader(stdout_reader, "stdout");
                let _ = join_command_output_reader(stderr_reader, "stderr");
                return Err(format!("не удалось дождаться процесса: {e}"));
            }
        }
    }
}

fn join_command_output_reader(
    reader: Option<std::thread::JoinHandle<std::io::Result<Vec<u8>>>>,
    stream_name: &str,
) -> Result<Vec<u8>, String> {
    let Some(reader) = reader else {
        return Ok(Vec::new());
    };
    reader
        .join()
        .map_err(|_| format!("поток чтения {stream_name} аварийно завершился"))?
        .map_err(|e| format!("не удалось прочитать {stream_name}: {e}"))
}

fn parse_xray_stats_output(output: &str) -> SessionTraffic {
    let Ok(json) = serde_json::from_str::<serde_json::Value>(output) else {
        return SessionTraffic::default();
    };
    let Some(stats) = json.get("stat").and_then(serde_json::Value::as_array) else {
        return SessionTraffic::default();
    };

    let mut inbound = SessionTraffic::default();
    let mut outbound_proxy = SessionTraffic::default();

    for stat in stats {
        let name = stat
            .get("name")
            .and_then(serde_json::Value::as_str)
            .unwrap_or_default();
        let value = stat.get("value").and_then(parse_stat_value).unwrap_or(0);
        if name.starts_with("inbound>>>api>>>") {
            continue;
        }

        if name.starts_with("inbound>>>") {
            if name.ends_with(">>>traffic>>>uplink") {
                inbound.upload = inbound.upload.saturating_add(value);
            } else if name.ends_with(">>>traffic>>>downlink") {
                inbound.download = inbound.download.saturating_add(value);
            }
        } else if name.starts_with("outbound>>>proxy>>>") {
            if name.ends_with(">>>traffic>>>uplink") {
                outbound_proxy.upload = outbound_proxy.upload.saturating_add(value);
            } else if name.ends_with(">>>traffic>>>downlink") {
                outbound_proxy.download = outbound_proxy.download.saturating_add(value);
            }
        }
    }

    if inbound.upload > 0 || inbound.download > 0 {
        inbound
    } else {
        outbound_proxy
    }
}

fn parse_stat_value(value: &serde_json::Value) -> Option<u64> {
    value
        .as_u64()
        .or_else(|| value.as_i64().and_then(|v| u64::try_from(v).ok()))
        .or_else(|| {
            value
                .as_str()
                .and_then(|text| text.trim().parse::<u64>().ok())
        })
}

fn build_fetch_options(state: &PersistedState) -> FetchOptions {
    let mut opts = FetchOptions::default();
    if let Some(ua) = &state.user_agent_override {
        opts.user_agent = Some(ua.clone());
    }
    opts
}

fn is_remote_subscription(source: &str) -> bool {
    source.starts_with("http://") || source.starts_with("https://")
}

fn header_value(headers: &[SubscriptionHeader], name: &str) -> Option<String> {
    headers
        .iter()
        .find(|header| header.name.eq_ignore_ascii_case(name))
        .map(|header| header.value.clone())
}

fn find_announce_header(headers: &[SubscriptionHeader]) -> Option<&SubscriptionHeader> {
    headers
        .iter()
        .find(|header| is_announce_header(&header.name))
}

fn is_announce_header(name: &str) -> bool {
    let lower = name.to_ascii_lowercase();
    lower == "announce"
        || lower == "profile-announce"
        || lower == "subscription-announce"
        || lower == "happ-announce"
        || lower == "happannounce"
        || lower.contains("announce")
}

fn executable_name(path: &str) -> String {
    std::path::Path::new(path)
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("Приложение")
        .to_string()
}

#[cfg(windows)]
fn platform_installed_apps() -> Vec<InstalledApp> {
    use std::collections::BTreeMap;
    use winreg::enums::{HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE, KEY_READ};
    use winreg::RegKey;

    const UNINSTALL: &str = r"Software\Microsoft\Windows\CurrentVersion\Uninstall";
    const WOW_UNINSTALL: &str = r"Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall";

    let roots = [
        (HKEY_CURRENT_USER, UNINSTALL),
        (HKEY_LOCAL_MACHINE, UNINSTALL),
        (HKEY_LOCAL_MACHINE, WOW_UNINSTALL),
    ];

    let mut apps = BTreeMap::<String, InstalledApp>::new();
    for (root, path) in roots {
        let hive = RegKey::predef(root);
        let Ok(key) = hive.open_subkey_with_flags(path, KEY_READ) else {
            continue;
        };

        for name in key.enum_keys().flatten() {
            let Ok(app_key) = key.open_subkey_with_flags(name, KEY_READ) else {
                continue;
            };
            let Ok(display_name) = app_key.get_value::<String, _>("DisplayName") else {
                continue;
            };
            let executable_path = app_key
                .get_value::<String, _>("DisplayIcon")
                .ok()
                .or_else(|| app_key.get_value::<String, _>("InstallLocation").ok())
                .and_then(|value| normalize_installed_app_path(&value));

            if let Some(executable_path) = executable_path {
                let key = format!(
                    "{}|{}",
                    display_name.to_lowercase(),
                    executable_path.to_lowercase()
                );
                apps.entry(key).or_insert(InstalledApp {
                    name: display_name,
                    executable_path,
                });
            }
        }
    }

    apps.into_values().collect()
}

#[cfg(windows)]
fn normalize_installed_app_path(value: &str) -> Option<String> {
    let trimmed = value.trim().trim_matches('"');
    if trimmed.is_empty() {
        return None;
    }

    let lower = trimmed.to_lowercase();
    let without_args = lower
        .find(".exe")
        .map(|index| trimmed[..index + 4].to_string())
        .unwrap_or_else(|| trimmed.to_string());

    if without_args.to_lowercase().ends_with(".exe") {
        return Some(without_args);
    }

    None
}

#[cfg(not(windows))]
fn platform_installed_apps() -> Vec<InstalledApp> {
    Vec::new()
}

#[tauri::command]
pub fn set_active_server(
    state: State<'_, AppState>,
    server_id: Option<String>,
) -> Result<PersistedState, String> {
    let subscription_url = if let Some(id) = &server_id {
        let snap = state.snapshot();
        let subscription_url = snap
            .subscriptions
            .iter()
            .find(|sub| sub.servers.iter().any(|srv| &srv.id == id))
            .map(|sub| sub.url.clone())
            .ok_or_else(|| "Сервер не найден в подписках".to_string())?;
        Some(subscription_url)
    } else {
        None
    };
    state
        .mutate(|s| {
            s.active_server_id = server_id;
            if let Some(subscription_url) = subscription_url {
                s.active_subscription_url = Some(subscription_url);
            }
        })
        .map_err(|e| format!("Не удалось сохранить: {e}"))?;
    Ok(state.snapshot())
}

#[tauri::command]
pub fn set_active_subscription(
    state: State<'_, AppState>,
    url: Option<String>,
) -> Result<PersistedState, String> {
    if let Some(url) = &url {
        let snap = state.snapshot();
        let exists = snap.subscriptions.iter().any(|sub| sub.url == *url);
        if !exists {
            return Err("Подписка не найдена".into());
        }
    }
    state
        .mutate(|s| s.active_subscription_url = url)
        .map_err(|e| format!("Не удалось сохранить активную подписку: {e}"))?;
    Ok(state.snapshot())
}

#[tauri::command]
pub async fn connect_server(
    app: AppHandle,
    state: State<'_, AppState>,
    server_id: String,
) -> Result<PersistedState, String> {
    let snap = state.snapshot();
    let Some((subscription_url, server)) = find_server_with_subscription(&snap, &server_id) else {
        return Err("Сервер не найден в подписках".into());
    };

    match snap.connection_mode {
        ConnectionMode::SystemProxy => connect_system_proxy(&app, &state, server, &snap).await?,
        ConnectionMode::Tun => {
            let status = ensure_tun_dependencies(&app).await.map_err(|e| {
                format!(
                    "TUN не установлен: {e}. Установи TUN в настройках и перезапусти Nimbo от имени администратора."
                )
            })?;
            if !status.installed {
                return Err(format!(
                    "{} Установи TUN в настройках и перезапусти Nimbo от имени администратора.",
                    status.message
                ));
            }
            if !is_running_as_admin() {
                return Err(
                    "TUN установлен, но для подключения нужен запуск от имени администратора. Перезапусти Nimbo от имени администратора и подключись снова."
                        .into(),
                );
            }
            connect_tun(&app, &state, server, &snap, status).await?;
        }
        ConnectionMode::Both => {
            let status = ensure_tun_dependencies(&app).await.map_err(|e| {
                format!(
                    "TUN не установлен: {e}. Установи TUN в настройках и перезапусти Nimbo от имени администратора."
                )
            })?;
            if !status.installed {
                return Err(format!(
                    "{} Установи TUN в настройках и перезапусти Nimbo от имени администратора.",
                    status.message
                ));
            }
            if !is_running_as_admin() {
                return Err(
                    "TUN установлен, но для подключения нужен запуск от имени администратора. Перезапусти Nimbo от имени администратора и подключись снова."
                        .into(),
                );
            }
            connect_both(&app, &state, server, &snap, status).await?;
        }
    }

    let connected_at = snap.connected_at.unwrap_or_else(unix_timestamp_millis);
    state
        .mutate(|s| {
            s.active_server_id = Some(server_id);
            s.active_subscription_url = Some(subscription_url);
            s.connected = true;
            s.connected_at = Some(connected_at);
        })
        .map_err(|e| format!("Не удалось сохранить статус подключения: {e}"))?;
    Ok(state.snapshot())
}

#[tauri::command]
pub async fn disconnect_server(
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<PersistedState, String> {
    let snapshot = state.snapshot();
    let final_session = if snapshot.connected {
        let xray_running = state.runtime(|runtime| runtime.xray.is_some());
        if xray_running {
            match ensure_xray_binary(&app).await {
                Ok(xray_path) => query_xray_session_traffic(xray_path, ProxyPorts::default())
                    .await
                    .unwrap_or_default(),
                Err(_) => SessionTraffic::default(),
            }
        } else {
            SessionTraffic::default()
        }
    } else {
        SessionTraffic::default()
    };

    stop_runtime(&state)?;
    let current_period = current_month_period();
    state
        .mutate(|s| {
            s.connected = false;
            s.connected_at = None;
            if s.traffic_totals.monthly_period != current_period {
                s.traffic_totals.monthly_period = current_period.clone();
                s.traffic_totals.monthly_upload = 0;
                s.traffic_totals.monthly_download = 0;
            }
            s.traffic_totals.all_time_upload = s
                .traffic_totals
                .all_time_upload
                .saturating_add(final_session.upload);
            s.traffic_totals.all_time_download = s
                .traffic_totals
                .all_time_download
                .saturating_add(final_session.download);
            s.traffic_totals.monthly_upload = s
                .traffic_totals
                .monthly_upload
                .saturating_add(final_session.upload);
            s.traffic_totals.monthly_download = s
                .traffic_totals
                .monthly_download
                .saturating_add(final_session.download);
        })
        .map_err(|e| format!("Не удалось отключиться: {e}"))?;
    Ok(state.snapshot())
}

fn find_server(snapshot: &PersistedState, server_id: &str) -> Option<Server> {
    find_server_with_subscription(snapshot, server_id).map(|(_, server)| server)
}

fn find_server_with_subscription(
    snapshot: &PersistedState,
    server_id: &str,
) -> Option<(String, Server)> {
    snapshot.subscriptions.iter().find_map(|sub| {
        sub.servers
            .iter()
            .find(|server| server.id == server_id)
            .cloned()
            .map(|server| (sub.url.clone(), server))
    })
}

fn merge_xray_template_cache(
    cache: &mut HashMap<String, serde_json::Value>,
    subscription_url: &str,
    templates: HashMap<String, serde_json::Value>,
) {
    for (uuid, template) in templates {
        cache.insert(uuid.clone(), template.clone());
        cache.insert(
            namespaced_xray_template_key(subscription_url, &uuid),
            template,
        );
    }
}

fn remove_xray_templates_for_subscription(
    cache: &mut HashMap<String, serde_json::Value>,
    subscription_url: &str,
) {
    let prefix = format!("{subscription_url}::");
    cache.retain(|key, _| !key.starts_with(&prefix));
}

fn namespaced_xray_template_key(subscription_url: &str, key: &str) -> String {
    format!("{subscription_url}::{key}")
}

async fn fetch_xray_templates_for_subscription(
    subscription_url: &str,
    user_agent_override: Option<&str>,
    servers: &[Server],
) -> HashMap<String, serde_json::Value> {
    let mut out = HashMap::new();

    match build_remnawave_client(user_agent_override) {
        Ok(client) => {
            for (key, template) in
                fetch_public_xray_templates(&client, subscription_url, servers).await
            {
                out.entry(key).or_insert(template);
            }
        }
        Err(error) => {
            tracing::warn!(?error, "xray template cache client init failed");
        }
    }

    for (key, template) in fetch_api_xray_templates(user_agent_override).await {
        out.entry(key).or_insert(template);
    }
    out
}

/// Сначала берём templates прямо из тела подписки, затем дополняем публичными
/// Remnawave endpoints. Так каждый сервер может выбрать свой xrayJsonTemplateUuid.
async fn collect_subscription_xray_templates(
    fetched: &Fetched,
    subscription_url: &str,
    user_agent_override: Option<&str>,
) -> HashMap<String, serde_json::Value> {
    let mut out = extract_xray_templates_with_server_keys(&fetched.raw_body, &fetched.servers);
    for (key, template) in fetched.xray_templates.clone() {
        out.entry(key).or_insert(template);
    }
    let remote = fetch_xray_templates_for_subscription(
        subscription_url,
        user_agent_override,
        &fetched.servers,
    )
    .await;
    for (key, template) in remote {
        out.entry(key).or_insert(template);
    }
    out
}

fn build_remnawave_client(
    user_agent_override: Option<&str>,
) -> Result<reqwest::Client, reqwest::Error> {
    let device = device_info();
    let app_user_agent = user_agent_override.unwrap_or(&device.user_agent);
    let mut headers = reqwest::header::HeaderMap::new();
    insert_request_header(&mut headers, "X-Hwid", &device.hwid);
    insert_request_header(&mut headers, "X-Device-Os", &device.os);
    insert_request_header(&mut headers, "X-Device-Os-Version", &device.os_version);
    insert_request_header(&mut headers, "X-Ver-Os", &device.os_version);
    insert_request_header(&mut headers, "X-Device-Model", &device.hostname);
    insert_request_header(&mut headers, "X-Happ-Device-Os", HAPP_COMPAT_DEVICE_OS);
    insert_request_header(
        &mut headers,
        "X-Happ-Device-Os-Version",
        HAPP_COMPAT_OS_VERSION,
    );
    insert_request_header(
        &mut headers,
        "X-Happ-Device-Model",
        HAPP_COMPAT_DEVICE_MODEL,
    );
    insert_request_header(&mut headers, "X-Nimbo-User-Agent", app_user_agent);
    insert_request_header(&mut headers, "X-Client-User-Agent", app_user_agent);
    insert_request_header(&mut headers, "X-Client-Name", "Nimbo");
    insert_request_header(&mut headers, "X-Client-Version", env!("CARGO_PKG_VERSION"));
    insert_request_header(&mut headers, "X-Nimbo-Device-Os", &device.os);
    insert_request_header(
        &mut headers,
        "X-Nimbo-Device-Os-Version",
        &device.os_version,
    );
    insert_request_header(&mut headers, "X-Nimbo-Device-Model", &device.hostname);

    reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(15))
        .user_agent(happ_compatible_user_agent(app_user_agent))
        .default_headers(headers)
        .build()
}

fn insert_request_header(
    headers: &mut reqwest::header::HeaderMap,
    name: &'static str,
    value: &str,
) {
    if value.is_empty() {
        return;
    }
    if let Ok(value) = reqwest::header::HeaderValue::from_str(value) {
        headers.insert(name, value);
    }
}

#[derive(Debug, Clone)]
struct RemnawaveApiConfig {
    base_url: String,
    api_token: String,
    api_key: Option<String>,
    profile_uuids: Vec<String>,
}

async fn fetch_api_xray_templates(
    user_agent_override: Option<&str>,
) -> HashMap<String, serde_json::Value> {
    let Some(config) = remnawave_api_config() else {
        return HashMap::new();
    };

    let client = match build_remnawave_api_client(&config, user_agent_override) {
        Ok(client) => client,
        Err(error) => {
            tracing::warn!(
                ?error,
                "Remnawave API client init failed for xray template cache"
            );
            return HashMap::new();
        }
    };

    let mut out = HashMap::new();
    let mut profile_uuids = config.profile_uuids.clone();
    if profile_uuids.is_empty() {
        if let Some(json) = get_remnawave_json(
            &client,
            &remnawave_api_url(&config.base_url, "/api/config-profiles"),
        )
        .await
        {
            profile_uuids = collect_config_profile_uuids(&json);
            for (key, template) in extract_xray_templates_from_value(&json) {
                if let Some(template) = runtime_template_sections(&template) {
                    out.entry(key).or_insert(template);
                }
            }
        }
    }

    for uuid in profile_uuids {
        let url = remnawave_api_url(
            &config.base_url,
            &format!("/api/config-profiles/{uuid}/computed-config"),
        );
        let Some(json) = get_remnawave_json(&client, &url).await else {
            continue;
        };
        if let Some(template) = runtime_template_sections(&json) {
            out.entry(uuid.clone()).or_insert(template);
        }
        for (key, template) in extract_xray_templates_from_value(&json) {
            if let Some(template) = runtime_template_sections(&template) {
                out.entry(key).or_insert(template);
            }
        }
    }

    out
}

fn remnawave_api_config() -> Option<RemnawaveApiConfig> {
    let file_values = remnawave_env_file_values();
    let value = |key: &str| -> Option<String> {
        std::env::var(key)
            .ok()
            .and_then(non_empty_string)
            .or_else(|| file_values.get(key).cloned().and_then(non_empty_string))
    };

    let base_url = value(REMNAWAVE_BASE_URL_ENV)?;
    let api_token = value(REMNAWAVE_API_TOKEN_ENV)?;
    let api_key = value(REMNAWAVE_API_KEY_ENV);
    let profile_uuids = value(REMNAWAVE_PROFILE_UUIDS_ENV)
        .map(|value| {
            value
                .split([',', ';', '\n'])
                .filter_map(non_empty_string)
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();

    Some(RemnawaveApiConfig {
        base_url,
        api_token,
        api_key,
        profile_uuids,
    })
}

fn non_empty_string(value: impl AsRef<str>) -> Option<String> {
    let value = value.as_ref().trim();
    if value.is_empty() || value.eq_ignore_ascii_case("null") {
        None
    } else {
        Some(value.to_string())
    }
}

fn remnawave_env_file_values() -> HashMap<String, String> {
    let mut out = HashMap::new();
    for path in remnawave_env_file_candidates() {
        let Ok(contents) = std::fs::read_to_string(&path) else {
            continue;
        };
        for (key, value) in parse_env_file(&contents) {
            out.entry(key).or_insert(value);
        }
    }
    out
}

fn remnawave_env_file_candidates() -> Vec<PathBuf> {
    let mut paths = Vec::new();
    if let Some(path) = std::env::var_os(NIMBO_REMNAWAVE_ENV_ENV).map(PathBuf::from) {
        paths.push(path);
    }
    if let Ok(dir) = nimbo_data_dir() {
        paths.push(dir.join("remnawave.env"));
        paths.push(dir.join(".env"));
    }
    if let Ok(cwd) = std::env::current_dir() {
        for dir in cwd.ancestors() {
            paths.push(dir.join(".env"));
            paths.push(dir.join(".env.local"));
        }
    }
    if let Some(home) = dirs::home_dir() {
        paths.push(
            home.join(".codex")
                .join("mcp")
                .join("mcp-remnawave")
                .join(".env"),
        );
    }

    let mut seen = HashSet::new();
    paths.retain(|path| seen.insert(path.clone()));
    paths
}

fn parse_env_file(contents: &str) -> HashMap<String, String> {
    let mut out = HashMap::new();
    for line in contents.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        let key = key.trim();
        if key.is_empty() {
            continue;
        }
        let value = trim_env_value(value.trim());
        out.insert(key.to_string(), value);
    }
    out
}

fn trim_env_value(value: &str) -> String {
    if value.len() >= 2 {
        let bytes = value.as_bytes();
        let quoted = (bytes[0] == b'"' && bytes[value.len() - 1] == b'"')
            || (bytes[0] == b'\'' && bytes[value.len() - 1] == b'\'');
        if quoted {
            return value[1..value.len() - 1].to_string();
        }
    }
    value.to_string()
}

fn build_remnawave_api_client(
    config: &RemnawaveApiConfig,
    user_agent_override: Option<&str>,
) -> Result<reqwest::Client, reqwest::Error> {
    let device = device_info();
    let app_user_agent = user_agent_override.unwrap_or(&device.user_agent);
    let mut headers = reqwest::header::HeaderMap::new();
    insert_request_header(&mut headers, "X-Hwid", &device.hwid);
    insert_request_header(&mut headers, "X-Device-Os", &device.os);
    insert_request_header(&mut headers, "X-Device-Os-Version", &device.os_version);
    insert_request_header(&mut headers, "X-Happ-Device-Os", HAPP_COMPAT_DEVICE_OS);
    insert_request_header(
        &mut headers,
        "X-Happ-Device-Os-Version",
        HAPP_COMPAT_OS_VERSION,
    );
    insert_request_header(
        &mut headers,
        "X-Happ-Device-Model",
        HAPP_COMPAT_DEVICE_MODEL,
    );
    if let Ok(value) =
        reqwest::header::HeaderValue::from_str(&format!("Bearer {}", config.api_token))
    {
        headers.insert(reqwest::header::AUTHORIZATION, value);
    }
    if let Some(api_key) = &config.api_key {
        insert_request_header(&mut headers, "X-Api-Key", api_key);
    }

    reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(15))
        .user_agent(happ_compatible_user_agent(app_user_agent))
        .default_headers(headers)
        .build()
}

fn remnawave_api_url(base_url: &str, api_path: &str) -> String {
    let Ok(mut url) = url::Url::parse(base_url.trim()) else {
        return format!(
            "{}/{}",
            base_url.trim().trim_end_matches('/'),
            api_path.trim_start_matches('/')
        );
    };
    let base_path = url.path().trim_end_matches('/');
    let path = if base_path.ends_with("/api") || base_path == "/api" {
        format!(
            "{}/{}",
            base_path,
            api_path.trim_start_matches("/api/").trim_start_matches('/')
        )
    } else {
        format!("{}/{}", base_path, api_path.trim_start_matches('/'))
    };
    url.set_path(&path);
    url.set_query(None);
    url.set_fragment(None);
    url.to_string()
}

fn collect_config_profile_uuids(value: &serde_json::Value) -> Vec<String> {
    let mut out = Vec::new();
    collect_config_profile_uuids_inner(value, &mut out);
    let mut seen = HashSet::new();
    out.retain(|uuid| seen.insert(uuid.clone()));
    out
}

fn collect_config_profile_uuids_inner(value: &serde_json::Value, out: &mut Vec<String>) {
    match value {
        serde_json::Value::Object(map) => {
            let likely_profile = map.contains_key("uuid")
                && (map.contains_key("name")
                    || map.contains_key("config")
                    || map.contains_key("inbounds")
                    || map.contains_key("createdAt")
                    || map.contains_key("updatedAt"));
            if likely_profile {
                if let Some(uuid) = map.get("uuid").and_then(serde_json::Value::as_str) {
                    if let Some(uuid) = non_empty_string(uuid) {
                        out.push(uuid);
                    }
                }
            }
            for nested in map.values() {
                collect_config_profile_uuids_inner(nested, out);
            }
        }
        serde_json::Value::Array(items) => {
            for nested in items {
                collect_config_profile_uuids_inner(nested, out);
            }
        }
        _ => {}
    }
}

fn runtime_template_sections(value: &serde_json::Value) -> Option<serde_json::Value> {
    let template = find_xray_template_like(value)?;
    let object = template.as_object()?;
    let mut out = serde_json::Map::new();
    for key in ["routing", "dns", "log"] {
        if let Some(section) = object.get(key).cloned() {
            out.insert(key.into(), section);
        }
    }
    if out.is_empty() {
        None
    } else {
        Some(serde_json::Value::Object(out))
    }
}

fn find_xray_template_like(value: &serde_json::Value) -> Option<&serde_json::Value> {
    match value {
        serde_json::Value::Object(map) => {
            if map.contains_key("routing") || map.contains_key("dns") || map.contains_key("log") {
                return Some(value);
            }
            for nested in map.values() {
                if let Some(found) = find_xray_template_like(nested) {
                    return Some(found);
                }
            }
            None
        }
        serde_json::Value::Array(items) => {
            for nested in items {
                if let Some(found) = find_xray_template_like(nested) {
                    return Some(found);
                }
            }
            None
        }
        _ => None,
    }
}

fn extract_xray_templates_with_server_keys(
    body: &str,
    known_servers: &[Server],
) -> HashMap<String, serde_json::Value> {
    let trimmed = body.trim();
    if !trimmed.starts_with('{') && !trimmed.starts_with('[') {
        return HashMap::new();
    }
    let Ok(value) = serde_json::from_str::<serde_json::Value>(trimmed) else {
        return HashMap::new();
    };
    extract_xray_templates_from_json_value(&value, known_servers)
}

fn extract_xray_templates_from_json_value(
    value: &serde_json::Value,
    known_servers: &[Server],
) -> HashMap<String, serde_json::Value> {
    let mut templates = Vec::new();
    collect_full_xray_template_values(value, &mut templates);

    let allow_default_key = templates.len() <= 1;
    let mut out = HashMap::new();
    for template in templates {
        insert_full_xray_template(&mut out, template, known_servers, allow_default_key);
    }
    out
}

fn collect_full_xray_template_values<'a>(
    value: &'a serde_json::Value,
    out: &mut Vec<&'a serde_json::Value>,
) {
    match value {
        serde_json::Value::Object(map) => {
            if is_full_xray_config_template(value) {
                out.push(value);
                return;
            }
            for nested in map.values() {
                collect_full_xray_template_values(nested, out);
            }
        }
        serde_json::Value::Array(items) => {
            for nested in items {
                collect_full_xray_template_values(nested, out);
            }
        }
        _ => {}
    }
}

fn insert_full_xray_template(
    out: &mut HashMap<String, serde_json::Value>,
    template: &serde_json::Value,
    known_servers: &[Server],
    allow_default_key: bool,
) {
    for (key, value) in extract_xray_templates_from_value(template) {
        if allow_default_key || key != DEFAULT_XRAY_TEMPLATE_KEY {
            out.entry(key).or_insert(value);
        }
    }

    let parsed_servers = serde_json::to_string(template)
        .ok()
        .and_then(|json| parse_aggregate(&json).ok())
        .unwrap_or_default();
    for parsed_server in parsed_servers {
        for key in server_xray_template_lookup_keys(&parsed_server) {
            out.entry(key).or_insert_with(|| template.clone());
        }
        if let Some(known_server) = known_servers
            .iter()
            .find(|known_server| same_subscription_server(known_server, &parsed_server))
        {
            for key in server_xray_template_lookup_keys(known_server) {
                out.entry(key).or_insert_with(|| template.clone());
            }
        }
    }
}

fn is_full_xray_config_template(value: &serde_json::Value) -> bool {
    value
        .get("outbounds")
        .and_then(serde_json::Value::as_array)
        .is_some_and(|outbounds| !outbounds.is_empty())
}

fn server_xray_template_lookup_keys(server: &Server) -> Vec<String> {
    let mut keys = Vec::new();
    if !server.id.trim().is_empty() {
        keys.push(server_xray_template_key(&server.id));
    }
    if let Some(host_uuid) = server
        .host_uuid
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
    {
        keys.push(host_xray_template_key(host_uuid));
    }
    if let Some(template_uuid) = server
        .xray_json_template_uuid
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty() && !value.eq_ignore_ascii_case("null"))
    {
        keys.push(template_uuid.to_string());
    }

    let mut seen = HashSet::new();
    keys.retain(|key| seen.insert(key.clone()));
    keys
}

fn server_xray_template_key(server_id: &str) -> String {
    format!("server:{server_id}")
}

fn host_xray_template_key(host_uuid: &str) -> String {
    format!("host:{host_uuid}")
}

fn same_subscription_server(left: &Server, right: &Server) -> bool {
    if let (Some(left_host), Some(right_host)) = (
        left.host_uuid
            .as_deref()
            .map(str::trim)
            .filter(|value| !value.is_empty()),
        right
            .host_uuid
            .as_deref()
            .map(str::trim)
            .filter(|value| !value.is_empty()),
    ) {
        if left_host.eq_ignore_ascii_case(right_host) {
            return true;
        }
    }
    server_connection_identity(left) == server_connection_identity(right)
}

fn server_connection_identity(server: &Server) -> String {
    match &server.protocol {
        nimbo_subscription::Protocol::Vless(config) => format!(
            "vless:{}:{}:{}:{:?}",
            config.address.trim().to_ascii_lowercase(),
            config.port,
            config.uuid.trim().to_ascii_lowercase(),
            config.stream
        ),
        nimbo_subscription::Protocol::Vmess(config) => format!(
            "vmess:{}:{}:{}:{}:{:?}",
            config.address.trim().to_ascii_lowercase(),
            config.port,
            config.uuid.trim().to_ascii_lowercase(),
            config.security.trim().to_ascii_lowercase(),
            config.stream
        ),
        nimbo_subscription::Protocol::Trojan(config) => format!(
            "trojan:{}:{}:{}:{:?}",
            config.address.trim().to_ascii_lowercase(),
            config.port,
            config.password,
            config.stream
        ),
        nimbo_subscription::Protocol::Shadowsocks(config) => format!(
            "shadowsocks:{}:{}:{}:{}",
            config.address.trim().to_ascii_lowercase(),
            config.port,
            config.method.trim().to_ascii_lowercase(),
            config.password
        ),
        nimbo_subscription::Protocol::Hysteria2(config) => format!(
            "hysteria2:{}:{}:{}:{}:{:?}:{}",
            config.address.trim().to_ascii_lowercase(),
            config.port,
            config.password,
            config.sni.as_deref().unwrap_or_default(),
            config.alpn,
            config.insecure
        ),
    }
}

async fn fetch_public_xray_templates(
    client: &reqwest::Client,
    subscription_url: &str,
    known_servers: &[Server],
) -> HashMap<String, serde_json::Value> {
    let mut out = HashMap::new();
    for url in subscription_xray_template_urls(subscription_url) {
        let Some(json) = get_remnawave_json(client, &url).await else {
            continue;
        };
        for (key, template) in extract_xray_templates_from_json_value(&json, known_servers) {
            out.entry(key).or_insert(template);
        }
    }
    out
}

fn subscription_xray_template_urls(subscription_url: &str) -> Vec<String> {
    let Ok(parsed) = url::Url::parse(subscription_url) else {
        return Vec::new();
    };
    let Some(short_uuid) = subscription_short_uuid(&parsed) else {
        return Vec::new();
    };
    let public_base = subscription_public_base_path(&parsed, &short_uuid);

    let mut urls = Vec::new();
    for base_path in [format!("/api/sub/{short_uuid}"), public_base] {
        for suffix in ["happ", "xray-json", "xray", "json", "nimbo"] {
            let mut url = parsed.clone();
            url.set_path(&format!("{base_path}/{suffix}"));
            url.set_fragment(None);
            urls.push(url.to_string());
        }
    }

    for suffix in ["happ", "xray-json", "xray", "nimbo"] {
        let mut url = parsed.clone();
        url.set_path(&format!("/api/sub/{short_uuid}/{suffix}"));
        url.set_fragment(None);
        urls.push(url.to_string());
    }

    for (key, value) in [
        ("format", "xray-json"),
        ("subscriptionType", "XRAY_JSON"),
        ("type", "xray-json"),
    ] {
        let mut url = parsed.clone();
        url.set_fragment(None);
        url.query_pairs_mut().append_pair(key, value);
        urls.push(url.to_string());
    }

    let mut seen = HashSet::new();
    urls.retain(|url| seen.insert(url.clone()));
    urls
}

fn subscription_public_base_path(parsed: &url::Url, short_uuid: &str) -> String {
    let mut segments = parsed
        .path_segments()
        .map(|items| {
            items
                .filter(|segment| !segment.trim().is_empty())
                .map(ToString::to_string)
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();
    if let Some(pos) = segments.iter().rposition(|segment| segment == short_uuid) {
        segments.truncate(pos + 1);
    } else {
        segments.push(short_uuid.to_string());
    }
    format!("/{}", segments.join("/"))
}

fn subscription_short_uuid(parsed: &url::Url) -> Option<String> {
    let segments = parsed
        .path_segments()?
        .filter(|segment| !segment.trim().is_empty())
        .collect::<Vec<_>>();
    let candidate = segments
        .windows(2)
        .find_map(|window| {
            if window[0].eq_ignore_ascii_case("sub") {
                Some(window[1])
            } else {
                None
            }
        })
        .or_else(|| segments.last().copied())?;
    let candidate = candidate
        .trim()
        .trim_end_matches(".txt")
        .trim_end_matches(".json");
    if candidate.is_empty() {
        None
    } else {
        Some(candidate.to_string())
    }
}

async fn get_remnawave_json(client: &reqwest::Client, url: &str) -> Option<serde_json::Value> {
    let request = client
        .get(url)
        .header(reqwest::header::ACCEPT, "application/json");
    let response = request.send().await.ok()?;
    if !response.status().is_success() {
        if response.status().as_u16() != 404 {
            tracing::debug!(%url, status = %response.status(), "xray template cache response");
        }
        return None;
    }
    let text = response.text().await.ok()?;
    serde_json::from_str::<serde_json::Value>(&text).ok()
}

async fn tcp_ping_server(server: &Server, timeout_ms: u32) -> ServerPing {
    let (host, port) = server_endpoint(server);
    let start = std::time::Instant::now();
    let result = tokio::time::timeout(
        std::time::Duration::from_millis(timeout_ms as u64),
        tokio::net::TcpStream::connect((host.as_str(), port)),
    )
    .await;

    match result {
        Ok(Ok(_stream)) => ServerPing {
            server_id: server.id.clone(),
            latency_ms: Some(start.elapsed().as_millis().min(u64::MAX as u128) as u64),
            error: None,
        },
        Ok(Err(error)) => ServerPing {
            server_id: server.id.clone(),
            latency_ms: None,
            error: Some(error.to_string()),
        },
        Err(_) => ServerPing {
            server_id: server.id.clone(),
            latency_ms: None,
            error: Some("timeout".into()),
        },
    }
}

async fn latency_ping_server(
    server: &Server,
    timeout_ms: u32,
    protocol: &str,
    test_url: &str,
) -> ServerPing {
    match protocol {
        "icmp" => icmp_ping_server(server, timeout_ms).await,
        "http_head" => http_ping_url(server, timeout_ms, test_url).await,
        _ => tcp_ping_server(server, timeout_ms).await,
    }
}

async fn icmp_ping_server(server: &Server, timeout_ms: u32) -> ServerPing {
    let (host, _) = server_endpoint(server);
    let start = std::time::Instant::now();
    let mut command = TokioCommand::new("ping");
    if cfg!(windows) {
        command.args(["-n", "1", "-w", &timeout_ms.to_string(), &host]);
    } else {
        let timeout_seconds = ((timeout_ms as f64) / 1000.0).ceil().max(1.0) as u64;
        command.args(["-c", "1", "-W", &timeout_seconds.to_string(), &host]);
    }
    command.stdout(Stdio::piped()).stderr(Stdio::piped());

    let result = tokio::time::timeout(
        std::time::Duration::from_millis(timeout_ms as u64 + 1000),
        command.output(),
    )
    .await;

    match result {
        Ok(Ok(output)) if output.status.success() => ServerPing {
            server_id: server.id.clone(),
            latency_ms: Some(start.elapsed().as_millis().min(u64::MAX as u128) as u64),
            error: None,
        },
        Ok(Ok(output)) => {
            let stderr = String::from_utf8_lossy(&output.stderr).trim().to_string();
            let stdout = String::from_utf8_lossy(&output.stdout).trim().to_string();
            ServerPing {
                server_id: server.id.clone(),
                latency_ms: None,
                error: Some(if stderr.is_empty() { stdout } else { stderr }),
            }
        }
        Ok(Err(error)) => ServerPing {
            server_id: server.id.clone(),
            latency_ms: None,
            error: Some(error.to_string()),
        },
        Err(_) => ServerPing {
            server_id: server.id.clone(),
            latency_ms: None,
            error: Some("timeout".into()),
        },
    }
}

async fn http_ping_url(server: &Server, timeout_ms: u32, test_url: &str) -> ServerPing {
    let start = std::time::Instant::now();
    let client = match reqwest::Client::builder()
        .timeout(std::time::Duration::from_millis(timeout_ms as u64))
        .build()
    {
        Ok(client) => client,
        Err(error) => {
            return ServerPing {
                server_id: server.id.clone(),
                latency_ms: None,
                error: Some(error.to_string()),
            };
        }
    };

    match client.head(test_url).send().await {
        Ok(_) => ServerPing {
            server_id: server.id.clone(),
            latency_ms: Some(start.elapsed().as_millis().min(u64::MAX as u128) as u64),
            error: None,
        },
        Err(error) => ServerPing {
            server_id: server.id.clone(),
            latency_ms: None,
            error: Some(error.to_string()),
        },
    }
}

fn server_endpoint(server: &Server) -> (String, u16) {
    match &server.protocol {
        nimbo_subscription::Protocol::Vless(config) => (config.address.clone(), config.port),
        nimbo_subscription::Protocol::Vmess(config) => (config.address.clone(), config.port),
        nimbo_subscription::Protocol::Trojan(config) => (config.address.clone(), config.port),
        nimbo_subscription::Protocol::Shadowsocks(config) => (config.address.clone(), config.port),
        nimbo_subscription::Protocol::Hysteria2(config) => (config.address.clone(), config.port),
    }
}

async fn connect_system_proxy(
    app: &AppHandle,
    state: &State<'_, AppState>,
    server: Server,
    snapshot: &PersistedState,
) -> Result<(), String> {
    stop_runtime(state)?;

    let ports = ProxyPorts::default();
    let config = build_runtime_xray_config(&server, snapshot, ports)?;
    let config_path = write_xray_config(&config)?;
    let xray_path = ensure_xray_binary(app).await?;

    let mut child = spawn_xray(&xray_path, &config_path)?;
    if let Err(error) = wait_for_xray_port(&mut child, ports.socks) {
        let _ = child.kill();
        let _ = child.wait();
        return Err(error);
    }
    let proxy_snapshot = match apply_system_proxy(ports) {
        Ok(snapshot) => snapshot,
        Err(error) => {
            let _ = child.kill();
            let _ = child.wait();
            return Err(error);
        }
    };
    if let Err(error) = state.mutate(|s| {
        s.pending_system_proxy_snapshot = proxy_snapshot.clone();
    }) {
        let _ = child.kill();
        let _ = child.wait();
        let _ = restore_system_proxy(proxy_snapshot);
        return Err(format!(
            "Не удалось сохранить снимок системного proxy: {error}"
        ));
    }

    state.runtime(|runtime| {
        runtime.xray = Some(child);
        runtime.system_proxy_snapshot = proxy_snapshot;
    });

    Ok(())
}

async fn connect_both(
    app: &AppHandle,
    state: &State<'_, AppState>,
    server: Server,
    snapshot: &PersistedState,
    status: TunInstallStatus,
) -> Result<(), String> {
    connect_tun(app, state, server, snapshot, status).await?;

    let ports = ProxyPorts::default();
    match apply_system_proxy(ports) {
        Ok(proxy_snapshot) => {
            if let Err(error) = state.mutate(|s| {
                s.pending_system_proxy_snapshot = proxy_snapshot.clone();
            }) {
                let _ = restore_system_proxy(proxy_snapshot);
                stop_runtime(state)?;
                return Err(format!(
                    "Не удалось сохранить снимок системного proxy: {error}"
                ));
            }
            state.runtime(|runtime| {
                runtime.system_proxy_snapshot = proxy_snapshot;
            });
        }
        Err(error) => {
            stop_runtime(state)?;
            return Err(error);
        }
    }
    Ok(())
}

async fn connect_tun(
    app: &AppHandle,
    state: &State<'_, AppState>,
    server: Server,
    snapshot: &PersistedState,
    _status: TunInstallStatus,
) -> Result<(), String> {
    stop_runtime(state)?;

    let ports = ProxyPorts::default();
    let default_route = current_default_ipv4_route();
    let mut config = build_runtime_xray_config(&server, snapshot, ports)?;
    add_native_tun_inbound(&mut config);
    let config_path = write_xray_config(&config)?;
    let xray_path = ensure_xray_binary(app).await?;
    prepare_tun_runtime_files(app)?;
    let bypass_ips = resolve_server_ipv4s(&server).await;
    let mut xray = spawn_xray(&xray_path, &config_path)?;
    if let Err(error) = wait_for_xray_port(&mut xray, ports.socks) {
        let _ = xray.kill();
        let _ = xray.wait();
        return Err(error);
    }

    let tun_snapshot = TunRuntimeSnapshot {
        bypass_ips,
        gateway: default_route.as_ref().map(|route| route.gateway.clone()),
        interface_index: default_route.as_ref().map(|route| route.interface_index),
    };

    if let Err(error) = wait_for_native_tun_interface() {
        let _ = xray.kill();
        let _ = xray.wait();
        let _ = cleanup_tun(Some(tun_snapshot));
        return Err(error);
    }
    if let Err(error) = state.mutate(|s| s.pending_tun_snapshot = Some(tun_snapshot.clone())) {
        let _ = xray.kill();
        let _ = xray.wait();
        let _ = cleanup_tun(Some(tun_snapshot));
        return Err(format!("Не удалось сохранить снимок TUN: {error}"));
    }

    state.runtime(|runtime| {
        runtime.xray = Some(xray);
        runtime.tun_snapshot = Some(tun_snapshot);
    });

    Ok(())
}

fn prepare_tun_runtime_files(app: &AppHandle) -> Result<(), String> {
    let bin_dir = nimbo_data_dir()?.join("bin");
    std::fs::create_dir_all(&bin_dir).map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;

    let tun2socks_source = find_existing_path(tun2socks_candidate_paths(app)?)
        .ok_or_else(|| "tun2socks.exe не найден после установки TUN.".to_string())?;
    let wintun_source = find_existing_path(wintun_candidate_paths(app)?)
        .ok_or_else(|| "wintun.dll не найден после установки TUN.".to_string())?;

    let tun2socks_path = bin_dir.join(tun2socks_name());
    let wintun_path = bin_dir.join(wintun_name());
    copy_tun_file(&tun2socks_source, &tun2socks_path)?;
    copy_tun_file(&wintun_source, &wintun_path)?;

    Ok(())
}

#[derive(Debug, Clone)]
struct DefaultIpv4Route {
    gateway: String,
    interface_index: u32,
}

#[cfg(windows)]
fn current_default_ipv4_route() -> Option<DefaultIpv4Route> {
    let output = hidden_output_command("powershell")
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(
            "$r = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -AddressFamily IPv4 -ErrorAction SilentlyContinue | \
             Where-Object { $_.NextHop -and $_.NextHop -ne '0.0.0.0' } | \
             Sort-Object RouteMetric,InterfaceMetric | Select-Object -First 1; \
             if ($r) { Write-Output (\"{0}|{1}\" -f $r.NextHop, $r.InterfaceIndex) }",
        )
        .output()
        .ok()?;
    if !output.status.success() {
        return None;
    }
    let text = String::from_utf8_lossy(&output.stdout);
    let mut parts = text.trim().split('|');
    let gateway = parts.next()?.trim().to_string();
    let interface_index = parts.next()?.trim().parse().ok()?;
    if gateway.is_empty() {
        return None;
    }
    Some(DefaultIpv4Route {
        gateway,
        interface_index,
    })
}

fn add_native_tun_inbound(config: &mut serde_json::Value) {
    let Some(inbounds) = config
        .get_mut("inbounds")
        .and_then(serde_json::Value::as_array_mut)
    else {
        return;
    };
    if inbounds
        .iter()
        .any(|inbound| inbound.get("tag").and_then(serde_json::Value::as_str) == Some("tun-in"))
    {
        return;
    }

    inbounds.insert(
        0,
        serde_json::json!({
            "tag": "tun-in",
            "protocol": "tun",
            "settings": {
                "name": TUN_INTERFACE_NAME,
                "mtu": 1500,
                "gateway": [TUN_GATEWAY_CIDR, TUN_IPV6_GATEWAY_CIDR],
                "dns": [TUN_DNS_PRIMARY, TUN_DNS_SECONDARY],
                "userLevel": 0,
                "autoSystemRoutingTable": ["0.0.0.0/0", "::/0"],
                "autoOutboundsInterface": "auto"
            },
            "sniffing": {
                "enabled": true,
                "destOverride": ["http", "tls", "quic", "fakedns"],
                "routeOnly": false
            }
        }),
    );
}

#[cfg(windows)]
fn wait_for_native_tun_interface() -> Result<(), String> {
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(5);
    loop {
        if native_tun_interface_exists() {
            return Ok(());
        }
        if std::time::Instant::now() >= deadline {
            return Err(format!(
                "Xray не поднял TUN-интерфейс {TUN_INTERFACE_NAME}.{}",
                recent_xray_log_suffix()
            ));
        }
        std::thread::sleep(std::time::Duration::from_millis(160));
    }
}

#[cfg(windows)]
fn native_tun_interface_exists() -> bool {
    let escaped = TUN_INTERFACE_NAME.replace('\'', "''");
    let script = format!(
        "if (Get-NetAdapter -Name '{escaped}' -ErrorAction SilentlyContinue) {{ exit 0 }} else {{ exit 1 }}"
    );
    hidden_command("powershell")
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(script)
        .status()
        .map(|status| status.success())
        .unwrap_or(false)
}

#[cfg(not(windows))]
fn wait_for_native_tun_interface() -> Result<(), String> {
    Ok(())
}

#[cfg(not(windows))]
fn current_default_ipv4_route() -> Option<DefaultIpv4Route> {
    None
}

async fn resolve_server_ipv4s(server: &Server) -> Vec<String> {
    let (host, port) = server_endpoint(server);
    if let Ok(IpAddr::V4(ip)) = host.parse::<IpAddr>() {
        return vec![ip.to_string()];
    }

    let mut ips = Vec::new();
    if let Ok(addrs) = (host.as_str(), port).to_socket_addrs() {
        for addr in addrs {
            if let IpAddr::V4(ip) = addr.ip() {
                push_real_ipv4(&mut ips, ip);
            }
        }
    }

    if ips.is_empty() {
        for ip in resolve_ipv4s_via_doh(&host).await {
            push_real_ipv4(&mut ips, ip);
        }
    }

    ips
}

fn push_real_ipv4(ips: &mut Vec<String>, ip: Ipv4Addr) {
    if is_fake_dns_ipv4(ip) {
        return;
    }
    let ip = ip.to_string();
    if !ips.contains(&ip) {
        ips.push(ip);
    }
}

fn is_fake_dns_ipv4(ip: Ipv4Addr) -> bool {
    let octets = ip.octets();
    octets[0] == 198 && matches!(octets[1], 18 | 19)
}

async fn resolve_ipv4s_via_doh(host: &str) -> Vec<Ipv4Addr> {
    let Ok(client) = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(5))
        .build()
    else {
        return Vec::new();
    };

    let mut ips = Vec::new();
    for endpoint in [
        "https://cloudflare-dns.com/dns-query",
        "https://dns.google/resolve",
    ] {
        let Ok(mut url) = url::Url::parse(endpoint) else {
            continue;
        };
        url.query_pairs_mut()
            .append_pair("name", host)
            .append_pair("type", "A");

        let Ok(response) = client
            .get(url)
            .header(reqwest::header::ACCEPT, "application/dns-json")
            .send()
            .await
        else {
            continue;
        };
        if !response.status().is_success() {
            continue;
        }
        let Ok(text) = response.text().await else {
            continue;
        };
        let Ok(body) = serde_json::from_str::<serde_json::Value>(&text) else {
            continue;
        };
        let Some(answers) = body.get("Answer").and_then(serde_json::Value::as_array) else {
            continue;
        };
        for answer in answers {
            let is_a_record = answer.get("type").and_then(serde_json::Value::as_u64) == Some(1);
            if !is_a_record {
                continue;
            }
            let Some(data) = answer.get("data").and_then(serde_json::Value::as_str) else {
                continue;
            };
            let Ok(ip) = data.parse::<Ipv4Addr>() else {
                continue;
            };
            if !ips.contains(&ip) {
                ips.push(ip);
            }
        }
        if !ips.is_empty() {
            break;
        }
    }
    ips
}

fn build_runtime_xray_config(
    server: &Server,
    snapshot: &PersistedState,
    ports: ProxyPorts,
) -> Result<serde_json::Value, String> {
    let app_rules = combined_app_proxy_rules(snapshot, server);
    let mut builder = ConfigBuilder::new(ports)
        .server(server)
        .app_routing_rules(xray_app_rules(&app_rules))
        .profile_routing_rules(xray_routing_profile_rules(snapshot, &app_rules))
        .block_socks_udp(snapshot.block_socks_udp);
    if snapshot.require_socks_auth {
        builder = builder.socks_auth(&snapshot.socks_username, &snapshot.socks_password);
    }

    let base_config = builder.build();
    let base_value = serde_json::to_value(&base_config)
        .map_err(|e| format!("Не удалось собрать базовый Xray config: {e}"))?;

    let subscription_url = snapshot
        .subscriptions
        .iter()
        .find(|sub| sub.servers.iter().any(|srv| srv.id == server.id))
        .map(|sub| sub.url.as_str());
    let template_key = server
        .xray_json_template_uuid
        .as_deref()
        .filter(|uuid| !uuid.trim().is_empty())
        .unwrap_or(DEFAULT_XRAY_TEMPLATE_KEY);
    let template = select_xray_template(snapshot, subscription_url, server, template_key);

    let mut config = if let Some(template) = template {
        apply_xray_template(template.clone(), base_value, server)?
    } else {
        base_value
    };

    apply_runtime_preferences(&mut config, &snapshot.preferences);
    Ok(config)
}

fn apply_runtime_preferences(config: &mut serde_json::Value, preferences: &AppPreferences) {
    apply_inbound_preferences(config, preferences);
    apply_mux_preferences(config, preferences);
    apply_tls_fragmentation_preferences(config, preferences);
}

/// Tag of the freedom outbound that performs TLS ClientHello fragmentation.
const XRAY_FRAGMENT_TAG: &str = "fragment";

/// Wires up Xray-style TLS fragmentation: a dedicated `freedom` outbound with a
/// `fragment` (packets=tlshello) setting, dialed through by the proxy outbound
/// via `streamSettings.sockopt.dialerProxy`. This splits the TLS ClientHello —
/// including the SNI — across several TCP segments so SNI-based DPI can't read
/// the hostname in one piece. Idempotent: removes its own outbound first so the
/// toggle can flip on/off without leaving stale config behind.
fn apply_tls_fragmentation_preferences(
    config: &mut serde_json::Value,
    preferences: &AppPreferences,
) {
    let Some(outbounds) = config
        .get_mut("outbounds")
        .and_then(serde_json::Value::as_array_mut)
    else {
        return;
    };

    // Drop any fragment outbound we injected previously so re-applying is clean.
    outbounds.retain(|outbound| {
        outbound.get("tag").and_then(serde_json::Value::as_str) != Some(XRAY_FRAGMENT_TAG)
    });

    // Locate the proxy outbound (same heuristic as mux).
    let target_index = outbounds
        .iter()
        .position(|outbound| {
            outbound.get("tag").and_then(serde_json::Value::as_str) == Some(XRAY_PROXY_TAG)
        })
        .or_else(|| {
            outbounds.iter().position(|outbound| {
                let protocol = outbound
                    .get("protocol")
                    .and_then(serde_json::Value::as_str)
                    .unwrap_or_default();
                protocol != "freedom" && protocol != "blackhole"
            })
        });

    let Some(index) = target_index else {
        return;
    };

    if preferences.tunnel_tls_fragmentation {
        if let Some(outbound) = outbounds[index].as_object_mut() {
            let stream = outbound
                .entry("streamSettings")
                .or_insert_with(|| serde_json::json!({}));
            if let Some(stream_obj) = stream.as_object_mut() {
                let sockopt = stream_obj
                    .entry("sockopt")
                    .or_insert_with(|| serde_json::json!({}));
                if let Some(sockopt_obj) = sockopt.as_object_mut() {
                    sockopt_obj.insert(
                        "dialerProxy".into(),
                        serde_json::Value::String(XRAY_FRAGMENT_TAG.into()),
                    );
                }
            }
        }

        outbounds.push(serde_json::json!({
            "tag": XRAY_FRAGMENT_TAG,
            "protocol": "freedom",
            "settings": {
                "domainStrategy": "AsIs",
                "fragment": {
                    "packets": "tlshello",
                    "length": "10-20",
                    "interval": "10-20"
                }
            }
        }));
    } else if let Some(sockopt) = outbounds[index]
        .get_mut("streamSettings")
        .and_then(|stream| stream.get_mut("sockopt"))
        .and_then(serde_json::Value::as_object_mut)
    {
        if sockopt
            .get("dialerProxy")
            .and_then(serde_json::Value::as_str)
            == Some(XRAY_FRAGMENT_TAG)
        {
            sockopt.remove("dialerProxy");
        }
    }
}

fn apply_inbound_preferences(config: &mut serde_json::Value, preferences: &AppPreferences) {
    let Some(inbounds) = config
        .get_mut("inbounds")
        .and_then(serde_json::Value::as_array_mut)
    else {
        return;
    };

    for inbound in inbounds {
        let protocol = inbound
            .get("protocol")
            .and_then(serde_json::Value::as_str)
            .unwrap_or_default();
        if protocol != "socks" && protocol != "http" {
            continue;
        }

        inbound["listen"] = serde_json::Value::String(
            if preferences.lan_allow_connections {
                "0.0.0.0"
            } else {
                "127.0.0.1"
            }
            .into(),
        );

        if preferences.tunnel_sniffing {
            inbound["sniffing"] = serde_json::json!({
                "enabled": true,
                "destOverride": ["http", "tls", "quic", "fakedns"],
                "routeOnly": false
            });
        } else if let Some(object) = inbound.as_object_mut() {
            object.remove("sniffing");
        }
    }
}

fn apply_mux_preferences(config: &mut serde_json::Value, preferences: &AppPreferences) {
    let Some(outbounds) = config
        .get_mut("outbounds")
        .and_then(serde_json::Value::as_array_mut)
    else {
        return;
    };

    let target_index = outbounds
        .iter()
        .position(|outbound| {
            outbound.get("tag").and_then(serde_json::Value::as_str) == Some(XRAY_PROXY_TAG)
        })
        .or_else(|| {
            outbounds.iter().position(|outbound| {
                let protocol = outbound
                    .get("protocol")
                    .and_then(serde_json::Value::as_str)
                    .unwrap_or_default();
                protocol != "freedom" && protocol != "blackhole"
            })
        });

    let Some(index) = target_index else {
        return;
    };

    outbounds[index]["mux"] = serde_json::json!({
        "enabled": preferences.tunnel_mux_enabled,
        "concurrency": preferences.tunnel_mux_concurrency,
        "xudpConcurrency": preferences.tunnel_xudp_concurrency,
        "xudpProxyUDP443": preferences.tunnel_xudp_udp443.as_str()
    });
}

fn select_xray_template<'a>(
    snapshot: &'a PersistedState,
    subscription_url: Option<&str>,
    server: &Server,
    template_key: &str,
) -> Option<&'a serde_json::Value> {
    let mut lookup_keys = server_xray_template_lookup_keys(server);
    if !template_key.trim().is_empty() {
        lookup_keys.push(template_key.to_string());
    }
    let mut seen = HashSet::new();
    lookup_keys.retain(|key| seen.insert(key.clone()));

    if let Some(subscription_url) = subscription_url {
        for key in &lookup_keys {
            if let Some(template) = snapshot
                .xray_templates
                .get(&namespaced_xray_template_key(subscription_url, key))
            {
                return Some(template);
            }
        }
        if let Some(template) = snapshot.xray_templates.get(&namespaced_xray_template_key(
            subscription_url,
            DEFAULT_XRAY_TEMPLATE_KEY,
        )) {
            return Some(template);
        }
    }
    for key in &lookup_keys {
        if let Some(template) = snapshot.xray_templates.get(key) {
            return Some(template);
        }
    }
    snapshot
        .xray_templates
        .get(DEFAULT_XRAY_TEMPLATE_KEY)
        .or_else(|| {
            if snapshot.xray_templates.len() == 1 {
                snapshot.xray_templates.values().next()
            } else {
                None
            }
        })
}

fn apply_xray_template(
    template: serde_json::Value,
    mut base_config: serde_json::Value,
    _server: &Server,
) -> Result<serde_json::Value, String> {
    if !template.is_object() {
        return Ok(base_config);
    }

    if is_full_xray_config_template(&template) {
        return apply_full_xray_template(template, base_config);
    }

    if let Some(routing) = template.get("routing").cloned() {
        if let Some(mut routing) = routing.as_object().cloned() {
            merge_template_routing(&mut routing, &base_config);
            base_config["routing"] = serde_json::Value::Object(routing);
        }
    }

    if let Some(mut dns) = template.get("dns").cloned() {
        normalize_xray_dns_config(&mut dns);
        base_config["dns"] = dns;
    }
    if let Some(log) = template.get("log").cloned() {
        base_config["log"] = log;
    }

    Ok(base_config)
}

fn apply_full_xray_template(
    mut template: serde_json::Value,
    base_config: serde_json::Value,
) -> Result<serde_json::Value, String> {
    let Some(config) = template.as_object_mut() else {
        return Ok(base_config);
    };

    sanitize_full_xray_config(config);
    normalize_xray_outbounds(config);
    for key in ["inbounds", "api", "stats"] {
        if let Some(value) = base_config.get(key).cloned() {
            config.insert(key.into(), value);
        }
    }
    // Preserve DNS and log from base if the template didn't supply them.
    // Without DNS Xray can't resolve domains for IPIfNonMatch routing or proxy targets.
    for key in ["dns", "log"] {
        if !config.contains_key(key) {
            if let Some(value) = base_config.get(key).cloned() {
                config.insert(key.into(), value);
            }
        }
    }
    if let Some(dns) = config.get_mut("dns") {
        normalize_xray_dns_config(dns);
    }
    merge_xray_policy(config, &base_config);
    merge_full_template_routing(config, &base_config);

    Ok(template)
}

fn normalize_xray_dns_config(dns: &mut serde_json::Value) {
    let Some(dns) = dns.as_object_mut() else {
        return;
    };
    let Some(strategy) = dns
        .get("queryStrategy")
        .and_then(serde_json::Value::as_str)
        .map(ToString::to_string)
    else {
        return;
    };
    if matches!(
        strategy.as_str(),
        "UseIP" | "UseIPv4" | "UseIPv6" | "UseSystem"
    ) {
        return;
    }
    dns.insert(
        "queryStrategy".into(),
        serde_json::Value::String("UseIP".into()),
    );
}

fn normalize_xray_outbounds(config: &mut serde_json::Map<String, serde_json::Value>) {
    let Some(outbounds) = config
        .get_mut("outbounds")
        .and_then(serde_json::Value::as_array_mut)
    else {
        return;
    };

    for outbound in outbounds {
        let Some(outbound) = outbound.as_object_mut() else {
            continue;
        };
        if outbound.get("protocol").and_then(serde_json::Value::as_str) != Some("freedom") {
            continue;
        }

        let settings = outbound
            .entry("settings")
            .or_insert_with(|| serde_json::json!({}));
        if !settings.is_object() {
            *settings = serde_json::json!({});
        }
        let Some(settings) = settings.as_object_mut() else {
            continue;
        };
        settings
            .entry("domainStrategy")
            .or_insert_with(|| serde_json::Value::String("UseIP".into()));
    }
}

fn sanitize_full_xray_config(config: &mut serde_json::Map<String, serde_json::Value>) {
    for key in [
        "remarks",
        "remark",
        "ps",
        "name",
        "title",
        "serverDescription",
        "server_description",
        "server-description",
        "hostUuid",
        "host_uuid",
        "host-uuid",
        "xrayJsonTemplateUuid",
        "xray_json_template_uuid",
        "xray-json-template-uuid",
        "clientOverrides",
        "client_overrides",
        "meta",
        "remnawave",
    ] {
        config.remove(key);
    }
}

fn merge_xray_policy(
    config: &mut serde_json::Map<String, serde_json::Value>,
    base_config: &serde_json::Value,
) {
    let Some(base_policy) = base_config.get("policy").cloned() else {
        return;
    };
    let Some(base_system) = base_policy.get("system").cloned() else {
        config.entry("policy").or_insert(base_policy);
        return;
    };

    let policy = config
        .entry("policy")
        .or_insert_with(|| serde_json::Value::Object(serde_json::Map::new()));
    let Some(policy) = policy.as_object_mut() else {
        config.insert("policy".into(), base_policy);
        return;
    };
    let system = policy
        .entry("system")
        .or_insert_with(|| serde_json::Value::Object(serde_json::Map::new()));
    let Some(system) = system.as_object_mut() else {
        policy.insert("system".into(), base_system);
        return;
    };
    if let Some(base_system) = base_system.as_object() {
        for (key, value) in base_system {
            system.entry(key.clone()).or_insert_with(|| value.clone());
        }
    }
}

fn merge_full_template_routing(
    config: &mut serde_json::Map<String, serde_json::Value>,
    base_config: &serde_json::Value,
) {
    let routing = config
        .entry("routing")
        .or_insert_with(|| serde_json::json!({ "domainStrategy": "IPIfNonMatch", "rules": [] }));
    if !routing.is_object() {
        *routing = serde_json::json!({ "domainStrategy": "IPIfNonMatch", "rules": [] });
    }
    let Some(routing) = routing.as_object_mut() else {
        return;
    };
    merge_template_routing(routing, base_config);
}

fn merge_template_routing(
    routing: &mut serde_json::Map<String, serde_json::Value>,
    base_config: &serde_json::Value,
) {
    let Some(base_rules) = base_config
        .get("routing")
        .and_then(|routing| routing.get("rules"))
        .and_then(serde_json::Value::as_array)
    else {
        return;
    };

    let rules = routing
        .entry("rules")
        .or_insert_with(|| serde_json::Value::Array(Vec::new()));
    let Some(rules_arr) = rules.as_array_mut() else {
        return;
    };

    sanitize_template_routing_rules(rules_arr);
    normalize_xray_routing_rules(rules_arr);

    let mut priority_rules = Vec::new();
    let mut fallback_rules = Vec::new();
    for rule in base_rules {
        if is_xray_proxy_fallback_rule(rule) || is_xray_private_direct_rule(rule) {
            fallback_rules.push(rule.clone());
        } else {
            priority_rules.push(rule.clone());
        }
    }

    for rule in priority_rules.into_iter().rev() {
        if rules_arr
            .iter()
            .any(|existing| routing_rules_overlap(existing, &rule))
        {
            continue;
        }
        rules_arr.insert(0, rule);
    }
    for rule in fallback_rules {
        if !rules_arr.iter().any(|existing| existing == &rule) {
            rules_arr.push(rule);
        }
    }
}

fn sanitize_template_routing_rules(rules: &mut Vec<serde_json::Value>) {
    rules.retain_mut(|rule| {
        let Some(rule) = rule.as_object_mut() else {
            return false;
        };
        if is_template_catch_all_rule(rule) {
            return false;
        }
        if rule
            .get("balancerTag")
            .and_then(serde_json::Value::as_str)
            .is_some()
        {
            return true;
        }
        let Some(outbound_tag) = rule
            .get("outboundTag")
            .and_then(serde_json::Value::as_str)
            .map(ToString::to_string)
        else {
            return false;
        };
        !outbound_tag.trim().is_empty()
    });
}

fn is_template_catch_all_rule(rule: &serde_json::Map<String, serde_json::Value>) -> bool {
    let has_target = rule
        .get("outboundTag")
        .and_then(serde_json::Value::as_str)
        .is_some()
        || rule
            .get("balancerTag")
            .and_then(serde_json::Value::as_str)
            .is_some();
    if !has_target {
        return false;
    }

    ![
        "attrs",
        "domain",
        "inboundTag",
        "ip",
        "port",
        "process",
        "protocol",
        "source",
        "sourcePort",
        "user",
    ]
    .iter()
    .any(|key| {
        rule.get(*key)
            .is_some_and(|value| !value_is_empty_matcher(value))
    })
}

fn value_is_empty_matcher(value: &serde_json::Value) -> bool {
    match value {
        serde_json::Value::Null => true,
        serde_json::Value::String(value) => value.trim().is_empty(),
        serde_json::Value::Array(values) => values.is_empty(),
        serde_json::Value::Object(values) => values.is_empty(),
        _ => false,
    }
}

fn normalize_xray_routing_rules(rules: &mut [serde_json::Value]) {
    for rule in rules {
        let Some(rule) = rule.as_object_mut() else {
            continue;
        };
        if rule.contains_key("type") {
            continue;
        }
        let has_matcher = [
            "attrs",
            "balancerTag",
            "domain",
            "inboundTag",
            "ip",
            "network",
            "port",
            "process",
            "protocol",
            "source",
            "sourcePort",
            "user",
        ]
        .iter()
        .any(|key| rule.contains_key(*key));
        if has_matcher || rule.contains_key("outboundTag") {
            rule.insert("type".into(), serde_json::Value::String("field".into()));
        }
    }
}

fn is_xray_proxy_fallback_rule(rule: &serde_json::Value) -> bool {
    if rule.get("outboundTag").and_then(serde_json::Value::as_str) != Some(XRAY_PROXY_TAG) {
        return false;
    }
    if rule.get("network").and_then(serde_json::Value::as_str) != Some("tcp,udp") {
        return false;
    }
    [
        "attrs",
        "domain",
        "inboundTag",
        "ip",
        "port",
        "process",
        "protocol",
        "source",
        "sourcePort",
        "user",
    ]
    .iter()
    .all(|key| rule.get(*key).is_none())
}

// Returns true if two routing rules target the same outbound and share at
// least one matcher entry (domain / ip / protocol / process / inboundTag).
// Used to suppress duplicate priority rules when the subscription template
// already covers them (e.g. both base and template have `geoip:ru → direct`).
fn routing_rules_overlap(a: &serde_json::Value, b: &serde_json::Value) -> bool {
    let outbound_a = a.get("outboundTag").and_then(serde_json::Value::as_str);
    let outbound_b = b.get("outboundTag").and_then(serde_json::Value::as_str);
    if outbound_a.is_none() || outbound_a != outbound_b {
        return false;
    }
    for key in ["ip", "domain", "protocol", "process", "inboundTag"] {
        let arr_a = a.get(key).and_then(serde_json::Value::as_array);
        let arr_b = b.get(key).and_then(serde_json::Value::as_array);
        if let (Some(arr_a), Some(arr_b)) = (arr_a, arr_b) {
            if arr_a.iter().any(|x| arr_b.contains(x)) {
                return true;
            }
        }
    }
    false
}

fn is_xray_private_direct_rule(rule: &serde_json::Value) -> bool {
    if rule.get("outboundTag").and_then(serde_json::Value::as_str) != Some("direct") {
        return false;
    }
    let is_private_ip = rule
        .get("ip")
        .and_then(serde_json::Value::as_array)
        .is_some_and(|items| {
            items
                .iter()
                .any(|item| item.as_str() == Some("geoip:private"))
        });
    let is_private_domain = rule
        .get("domain")
        .and_then(serde_json::Value::as_array)
        .is_some_and(|items| {
            items
                .iter()
                .any(|item| item.as_str() == Some("geosite:private"))
        });
    is_private_ip || is_private_domain
}

fn xray_app_rules(rules: &[AppProxyRule]) -> Vec<XrayAppRoutingRule> {
    rules
        .iter()
        .map(|rule| XrayAppRoutingRule {
            process: xray_app_rule_target(&rule.executable_path),
            enabled: rule.enabled,
            mode: match rule.mode {
                AppProxyMode::Proxy => XrayAppRoutingMode::Proxy,
                AppProxyMode::Direct => XrayAppRoutingMode::Direct,
            },
        })
        .collect()
}

fn xray_app_rule_target(path: &str) -> String {
    let trimmed = path.trim();
    if trimmed.starts_with(APP_DOMAIN_ENTRY_PREFIX) {
        return trimmed.to_string();
    }
    let normalized = trimmed.replace('\\', "/");
    normalized
        .rsplit('/')
        .next()
        .map(str::trim)
        .filter(|name| !name.is_empty())
        .unwrap_or(trimmed)
        .to_string()
}

fn combined_app_proxy_rules(snapshot: &PersistedState, server: &Server) -> Vec<AppProxyRule> {
    let provider = provider_app_proxy_rules(snapshot, server);
    let local = snapshot.app_proxy_rules.clone();

    // Local rules can refer to the same process via different paths
    // (e.g. "opera.exe" vs full install path). Group them by canonical key
    // so any disabled override wins — runtime process matching is basename-based.
    let mut local_by_key: HashMap<String, AppProxyRule> = HashMap::new();
    for rule in &local {
        let key = canonical_app_rule_key(&rule.executable_path);
        if key.is_empty() {
            continue;
        }
        local_by_key
            .entry(key)
            .and_modify(|existing| {
                if existing.enabled && !rule.enabled {
                    *existing = rule.clone();
                }
            })
            .or_insert_with(|| rule.clone());
    }

    let mut out = Vec::new();
    let mut seen = HashSet::new();

    // Subscription provides the default rule. A local disabled rule turns that
    // header rule off; a local enabled rule is an explicit user override.
    for mut rule in provider {
        let key = canonical_app_rule_key(&rule.executable_path);
        if key.is_empty() || !seen.insert(key.clone()) {
            continue;
        }
        if let Some(local_rule) = local_by_key.get(&key) {
            rule.enabled = local_rule.enabled;
            if local_rule.enabled {
                rule.name = local_rule.name.clone();
                rule.executable_path = local_rule.executable_path.clone();
                rule.mode = local_rule.mode;
            }
        }
        out.push(rule);
    }

    // Local-only rules (not mentioned in subscription) keep both their mode and enabled.
    for rule in local {
        let key = canonical_app_rule_key(&rule.executable_path);
        if key.is_empty() || !seen.insert(key) {
            continue;
        }
        out.push(rule);
    }

    out
}

fn canonical_app_rule_key(path: &str) -> String {
    let trimmed = path.trim();
    if trimmed.is_empty() {
        return String::new();
    }
    if let Some(rest) = trimmed.strip_prefix(APP_DOMAIN_ENTRY_PREFIX) {
        return format!(
            "{APP_DOMAIN_ENTRY_PREFIX}{}",
            rest.trim().to_ascii_lowercase()
        );
    }
    let normalized = trimmed.replace('\\', "/").to_ascii_lowercase();
    normalized
        .rsplit('/')
        .next()
        .unwrap_or(&normalized)
        .trim()
        .to_string()
}

fn provider_app_proxy_rules(snapshot: &PersistedState, server: &Server) -> Vec<AppProxyRule> {
    let subscription = snapshot
        .active_subscription_url
        .as_deref()
        .and_then(|url| snapshot.subscriptions.iter().find(|sub| sub.url == url))
        .or_else(|| {
            snapshot.subscriptions.iter().find(|sub| {
                sub.servers
                    .iter()
                    .any(|candidate| candidate.id == server.id)
            })
        });

    subscription_app_proxy_rules_from(subscription)
}

fn subscription_app_proxy_rules_for_display(snapshot: &PersistedState) -> Vec<AppProxyRule> {
    let subscription = snapshot
        .active_subscription_url
        .as_deref()
        .and_then(|url| snapshot.subscriptions.iter().find(|sub| sub.url == url))
        .or_else(|| {
            snapshot.active_server_id.as_deref().and_then(|server_id| {
                snapshot.subscriptions.iter().find(|sub| {
                    sub.servers
                        .iter()
                        .any(|candidate| candidate.id == server_id)
                })
            })
        })
        .or_else(|| snapshot.subscriptions.first());

    subscription_app_proxy_rules_from(subscription)
}

fn subscription_app_proxy_rules_from(
    subscription: Option<&nimbo_subscription::Subscription>,
) -> Vec<AppProxyRule> {
    let Some(subscription) = subscription else {
        return Vec::new();
    };

    subscription
        .meta
        .app_proxy_rules
        .iter()
        .map(|rule| AppProxyRule {
            id: rule.id.clone(),
            name: rule.name.clone(),
            executable_path: normalize_app_executable_path(&rule.executable_path),
            mode: match rule.mode {
                nimbo_subscription::SubscriptionAppProxyMode::Direct => AppProxyMode::Direct,
                nimbo_subscription::SubscriptionAppProxyMode::Proxy => AppProxyMode::Proxy,
            },
            enabled: rule.enabled,
        })
        .collect()
}

const APP_DOMAIN_ENTRY_PREFIX: &str = "__domain__:";

fn normalize_app_executable_path(path: &str) -> String {
    let trimmed = path.trim();
    if trimmed.is_empty() || trimmed.starts_with(APP_DOMAIN_ENTRY_PREFIX) {
        return trimmed.to_string();
    }
    match detect_domain_target(trimmed) {
        Some(domain) => format!("{APP_DOMAIN_ENTRY_PREFIX}{domain}"),
        None => trimmed.to_string(),
    }
}

fn detect_domain_target(target: &str) -> Option<String> {
    let value = target.trim();
    if value.is_empty() || value.contains('\\') {
        return None;
    }
    let bytes = value.as_bytes();
    if bytes.len() >= 3
        && bytes[1] == b':'
        && (bytes[2] == b'\\' || bytes[2] == b'/')
        && bytes[0].is_ascii_alphabetic()
    {
        return None;
    }
    let lower = value.to_ascii_lowercase();
    let head = lower.split(['?', '#']).next().unwrap_or(&lower);
    for ext in [".exe", ".bat", ".msi", ".cmd", ".lnk"] {
        if head.ends_with(ext) {
            return None;
        }
    }

    let candidate = if value.contains("://") {
        value.to_string()
    } else {
        format!("https://{value}")
    };
    let url = url::Url::parse(&candidate).ok()?;
    if url.scheme() != "http" && url.scheme() != "https" {
        return None;
    }
    let host = url.host_str()?.to_ascii_lowercase();
    let host = host.trim_end_matches('.');
    if host.contains('.') {
        Some(host.to_string())
    } else {
        None
    }
}

fn xray_routing_profile_rules(
    snapshot: &PersistedState,
    _app_proxy_rules: &[AppProxyRule],
) -> XrayRoutingProfileRules {
    let profile = snapshot
        .routing_profiles
        .iter()
        .find(|profile| profile.id == snapshot.active_routing_profile)
        .cloned()
        .or_else(|| {
            builtin_routing_profiles()
                .into_iter()
                .find(|profile| profile.id == snapshot.active_routing_profile)
        })
        .or_else(|| {
            builtin_routing_profiles()
                .into_iter()
                .find(|profile| profile.id == "global")
        });

    let Some(profile) = profile else {
        return XrayRoutingProfileRules {
            domain_strategy: "IPIfNonMatch".into(),
            global_proxy: true,
            bypass_local_ip: true,
            rule_order: "block-proxy-direct".into(),
            ..Default::default()
        };
    };

    // Fallback (the route used when no app/domain/IP rule matches) is whatever
    // the active routing profile says — we no longer auto-flip it based on
    // whether the enabled app rules happen to be all-direct or all-proxy.
    // That heuristic was confusing: setting one app to "via VPN" silently
    // turned the rest of the traffic to direct, which surprised users who
    // expected the profile (e.g. "Глобальный" with global_proxy=true) to keep
    // routing everything else through the tunnel.
    XrayRoutingProfileRules {
        domain_strategy: profile.domain_strategy,
        global_proxy: profile.global_proxy,
        bypass_local_ip: profile.bypass_local_ip,
        rule_order: profile.rule_order,
        direct_domains: profile.direct_domains,
        direct_ips: profile.direct_ips,
        proxy_domains: profile.proxy_domains,
        proxy_ips: profile.proxy_ips,
        block_domains: profile.block_domains,
        block_ips: profile.block_ips,
    }
}

fn write_xray_config(config: &serde_json::Value) -> Result<PathBuf, String> {
    let runtime_dir = nimbo_data_dir()?.join("runtime");
    std::fs::create_dir_all(&runtime_dir)
        .map_err(|e| format!("Не удалось создать папку runtime: {e}"))?;
    let config_path = runtime_dir.join("xray-config.json");
    let json = serde_json::to_vec_pretty(config)
        .map_err(|e| format!("Не удалось собрать Xray config: {e}"))?;
    std::fs::write(&config_path, json)
        .map_err(|e| format!("Не удалось записать Xray config: {e}"))?;
    Ok(config_path)
}

async fn ensure_xray_binary(app: &AppHandle) -> Result<PathBuf, String> {
    if let Some(path) = std::env::var_os("NIMBO_XRAY_PATH").map(PathBuf::from) {
        if path.exists() {
            return Ok(path);
        }
    }

    for path in xray_candidate_paths(app)? {
        if path.exists() {
            return Ok(path);
        }
    }

    download_xray_runtime().await
}

fn xray_candidate_paths(app: &AppHandle) -> Result<Vec<PathBuf>, String> {
    let mut paths = Vec::new();
    let data_bin = nimbo_data_dir()?.join("bin").join(xray_exe_name());
    paths.push(data_bin);

    if let Ok(resource) = app.path().resource_dir() {
        paths.push(resource.join("xray").join(xray_exe_name()));
        paths.push(resource.join("bin").join(xray_exe_name()));
        paths.push(resource.join(xray_exe_name()));
    }

    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            paths.push(dir.join(xray_exe_name()));
            paths.push(dir.join("bin").join(xray_exe_name()));
        }
    }

    if let Ok(cwd) = std::env::current_dir() {
        paths.push(cwd.join("binaries").join(xray_exe_name()));
        paths.push(cwd.join("src-tauri").join("binaries").join(xray_exe_name()));
    }

    if let Some(path_env) = std::env::var_os("PATH") {
        paths.extend(std::env::split_paths(&path_env).map(|dir| dir.join(xray_exe_name())));
    }

    Ok(paths)
}

fn xray_exe_name() -> &'static str {
    if cfg!(windows) {
        "xray.exe"
    } else {
        "xray"
    }
}

fn xray_release_archive_name(os: &str, arch: &str) -> Option<&'static str> {
    match (os, arch) {
        ("windows", "x86_64") => Some("Xray-windows-64.zip"),
        ("windows", "x86") => Some("Xray-windows-32.zip"),
        ("windows", "aarch64") => Some("Xray-windows-arm64-v8a.zip"),
        ("linux", "x86_64") => Some("Xray-linux-64.zip"),
        ("linux", "x86") => Some("Xray-linux-32.zip"),
        ("linux", "aarch64") => Some("Xray-linux-arm64-v8a.zip"),
        _ => None,
    }
}

fn xray_release_archive_url() -> Option<String> {
    xray_release_archive_name(std::env::consts::OS, std::env::consts::ARCH).map(|archive| {
        format!("https://github.com/XTLS/Xray-core/releases/latest/download/{archive}")
    })
}

async fn download_xray_runtime() -> Result<PathBuf, String> {
    let archive_url = xray_release_archive_url().ok_or_else(|| {
        format!(
            "Автоматическая загрузка xray-core не поддерживается для {} {}. Укажите NIMBO_XRAY_PATH.",
            std::env::consts::OS,
            std::env::consts::ARCH
        )
    })?;
    let bin_dir = nimbo_data_dir()?.join("bin");
    std::fs::create_dir_all(&bin_dir).map_err(|e| format!("Не удалось создать папку Xray: {e}"))?;

    tracing::info!(%archive_url, "downloading xray");
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(90))
        .user_agent("Nimbo-Xray-Updater")
        .build()
        .map_err(|e| format!("Не удалось создать HTTP-клиент для Xray: {e}"))?;
    let digest_url = format!("{archive_url}.dgst");
    let (archive_bytes, digest_file) = tokio::try_join!(
        download_xray_asset(&client, &archive_url),
        download_xray_digest(&client, &digest_url),
    )?;
    verify_xray_archive_digest(&archive_bytes, &digest_file)?;
    install_xray_runtime_archive(&archive_bytes, &bin_dir)
}

async fn download_xray_asset(client: &reqwest::Client, url: &str) -> Result<Vec<u8>, String> {
    let bytes = client
        .get(url)
        .send()
        .await
        .map_err(|e| format!("Не удалось скачать Xray: {e}"))?
        .error_for_status()
        .map_err(|e| format!("Не удалось скачать Xray: {e}"))?
        .bytes()
        .await
        .map_err(|e| format!("Не удалось прочитать архив Xray: {e}"))?;
    Ok(bytes.to_vec())
}

async fn download_xray_digest(client: &reqwest::Client, url: &str) -> Result<String, String> {
    client
        .get(url)
        .send()
        .await
        .map_err(|e| format!("Не удалось скачать контрольную сумму Xray: {e}"))?
        .error_for_status()
        .map_err(|e| format!("Не удалось скачать контрольную сумму Xray: {e}"))?
        .text()
        .await
        .map_err(|e| format!("Не удалось прочитать контрольную сумму Xray: {e}"))
}

fn verify_xray_archive_digest(bytes: &[u8], digest_file: &str) -> Result<(), String> {
    let expected = digest_file
        .lines()
        .filter_map(|line| line.trim().split_once('='))
        .find_map(|(algorithm, value)| {
            let algorithm = algorithm.trim();
            (algorithm.eq_ignore_ascii_case("SHA256") || algorithm.eq_ignore_ascii_case("SHA2-256"))
                .then(|| value.trim())
        })
        .filter(|value| value.len() == 64 && value.chars().all(|ch| ch.is_ascii_hexdigit()))
        .ok_or_else(|| "В .dgst Xray нет корректной SHA-256 суммы.".to_string())?;
    let actual = format!("{:x}", Sha256::digest(bytes));
    if actual.eq_ignore_ascii_case(expected) {
        Ok(())
    } else {
        Err("Контрольная сумма архива Xray не совпала.".into())
    }
}

fn install_xray_runtime_archive(archive_bytes: &[u8], bin_dir: &Path) -> Result<PathBuf, String> {
    const XRAY_RUNTIME_DATA_FILES: &[&str] = &["geoip.dat", "geosite.dat"];
    let staging_dir = bin_dir.join(format!(".nimbo-xray-{}.partial", std::process::id()));
    if staging_dir.exists() {
        std::fs::remove_dir_all(&staging_dir)
            .map_err(|e| format!("Не удалось очистить временную папку Xray: {e}"))?;
    }
    std::fs::create_dir_all(&staging_dir)
        .map_err(|e| format!("Не удалось создать временную папку Xray: {e}"))?;

    let install_result = (|| {
        let reader = Cursor::new(archive_bytes);
        let mut archive = zip::ZipArchive::new(reader)
            .map_err(|e| format!("Не удалось открыть архив Xray: {e}"))?;
        let mut found_binary = false;
        for index in 0..archive.len() {
            let mut file = archive
                .by_index(index)
                .map_err(|e| format!("Не удалось прочитать файл архива Xray: {e}"))?;
            if !file.is_file() {
                continue;
            }
            let Some(enclosed_name) = file.enclosed_name() else {
                continue;
            };
            let Some(name) = enclosed_name.file_name().and_then(|name| name.to_str()) else {
                continue;
            };
            if name != xray_exe_name() && !XRAY_RUNTIME_DATA_FILES.contains(&name) {
                continue;
            }
            let output = staging_dir.join(name);
            let mut out =
                File::create(&output).map_err(|e| format!("Не удалось распаковать Xray: {e}"))?;
            std::io::copy(&mut file, &mut out)
                .map_err(|e| format!("Не удалось записать файл Xray: {e}"))?;
            out.sync_all()
                .map_err(|e| format!("Не удалось сохранить файл Xray: {e}"))?;
            found_binary |= name == xray_exe_name();
        }

        if !found_binary {
            return Err(format!(
                "Архив Xray скачан, но {} внутри не найден.",
                xray_exe_name()
            ));
        }

        let staged_binary = staging_dir.join(xray_exe_name());
        mark_xray_executable(&staged_binary)?;
        for name in XRAY_RUNTIME_DATA_FILES
            .iter()
            .copied()
            .chain(std::iter::once(xray_exe_name()))
        {
            let staged = staging_dir.join(name);
            if !staged.exists() {
                continue;
            }
            let destination = bin_dir.join(name);
            if destination.exists() {
                std::fs::remove_file(&destination)
                    .map_err(|e| format!("Не удалось обновить файл Xray {name}: {e}"))?;
            }
            std::fs::rename(&staged, &destination)
                .map_err(|e| format!("Не удалось установить файл Xray {name}: {e}"))?;
        }

        Ok(bin_dir.join(xray_exe_name()))
    })();

    let _ = std::fs::remove_dir_all(&staging_dir);
    install_result
}

#[cfg(unix)]
fn mark_xray_executable(path: &Path) -> Result<(), String> {
    use std::os::unix::fs::PermissionsExt;

    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o755))
        .map_err(|e| format!("Не удалось выдать права на запуск Xray: {e}"))
}

#[cfg(not(unix))]
fn mark_xray_executable(_path: &Path) -> Result<(), String> {
    Ok(())
}

fn spawn_xray(xray_path: &Path, config_path: &Path) -> Result<std::process::Child, String> {
    let log_path = nimbo_data_dir()?.join("runtime").join("xray.log");
    if let Some(parent) = log_path.parent() {
        std::fs::create_dir_all(parent)
            .map_err(|e| format!("Не удалось создать папку логов Xray: {e}"))?;
    }
    rotate_runtime_log(&log_path)?;
    let stdout = OpenOptions::new()
        .create(true)
        .append(true)
        .open(&log_path)
        .map_err(|e| format!("Не удалось открыть лог Xray: {e}"))?;
    let stderr = stdout
        .try_clone()
        .map_err(|e| format!("Не удалось открыть лог Xray: {e}"))?;

    let mut command = Command::new(xray_path);
    command
        .arg("run")
        .arg("-config")
        .arg(config_path)
        .current_dir(xray_path.parent().unwrap_or_else(|| Path::new(".")))
        .stdin(Stdio::null())
        .stdout(Stdio::from(stdout))
        .stderr(Stdio::from(stderr));

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }

    let mut child = command
        .spawn()
        .map_err(|e| format!("Не удалось запустить Xray: {e}"))?;
    std::thread::sleep(std::time::Duration::from_millis(450));
    if let Ok(Some(status)) = child.try_wait() {
        return Err(format!(
            "Xray завершился сразу после запуска: {status}{}",
            recent_xray_log_suffix()
        ));
    }
    Ok(child)
}

fn wait_for_xray_port(child: &mut std::process::Child, port: u16) -> Result<(), String> {
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(4);
    let addr = ("127.0.0.1", port);
    let mut last_error = None;

    while std::time::Instant::now() < deadline {
        if let Ok(Some(status)) = child.try_wait() {
            return Err(format!(
                "Xray завершился до открытия SOCKS-порта {port}: {status}{}",
                recent_xray_log_suffix()
            ));
        }
        match TcpStream::connect(addr) {
            Ok(_) => return Ok(()),
            Err(error) => last_error = Some(error.to_string()),
        }
        std::thread::sleep(std::time::Duration::from_millis(120));
    }

    Err(format!(
        "Xray запустился, но SOCKS-порт {port} не открылся{}{}",
        last_error
            .map(|error| format!(": {error}"))
            .unwrap_or_default(),
        recent_xray_log_suffix()
    ))
}

fn recent_xray_log_suffix() -> String {
    let Ok(path) = nimbo_data_dir().map(|dir| dir.join("runtime").join("xray.log")) else {
        return String::new();
    };
    let log = std::fs::read_to_string(path)
        .unwrap_or_default()
        .lines()
        .rev()
        .take(10)
        .collect::<Vec<_>>()
        .into_iter()
        .rev()
        .collect::<Vec<_>>()
        .join("\n");
    if log.trim().is_empty() {
        String::new()
    } else {
        format!("\n{log}")
    }
}

fn stop_runtime(state: &State<'_, AppState>) -> Result<(), String> {
    let pending = state.snapshot();
    let (tun_snapshot, proxy_snapshot) = state.runtime(|runtime| {
        runtime.traffic_samples.clear();
        if let Some(mut child) = runtime.tun2socks.take() {
            let _ = child.kill();
            let _ = child.wait();
        }
        if let Some(mut child) = runtime.xray.take() {
            let _ = child.kill();
            let _ = child.wait();
        }
        (
            runtime.tun_snapshot.take(),
            runtime.system_proxy_snapshot.take(),
        )
    });
    let proxy_snapshot = proxy_snapshot.or_else(|| {
        pending
            .pending_system_proxy_snapshot
            .filter(|_| current_system_proxy_is_exact_nimbo().unwrap_or(false))
    });
    let tun_result = cleanup_tun(tun_snapshot.or(pending.pending_tun_snapshot));
    let proxy_result = restore_system_proxy(proxy_snapshot);
    tun_result?;
    proxy_result?;
    state
        .mutate(|s| {
            s.pending_tun_snapshot = None;
            s.pending_system_proxy_snapshot = None;
        })
        .map_err(|e| format!("Не удалось сбросить runtime-снимок: {e}"))?;
    Ok(())
}

pub fn cleanup_disconnected_runtime_on_startup(app: &AppHandle) {
    let state = app.state::<AppState>();
    if state.snapshot().connected {
        return;
    }
    if let Err(error) = stop_runtime(&state) {
        tracing::warn!(?error, "failed to clean stale runtime on startup");
    }
    if let Err(error) = cleanup_stale_nimbo_system_proxy() {
        tracing::warn!(
            ?error,
            "failed to clean stale Nimbo system proxy on startup"
        );
    }
    if let Err(error) = kill_orphan_nimbo_core_processes() {
        tracing::warn!(
            ?error,
            "failed to stop orphaned Nimbo core processes on startup"
        );
    }
}

pub fn cleanup_runtime_for_exit(app: &AppHandle) {
    let state = app.state::<AppState>();
    if let Err(error) = stop_runtime(&state) {
        tracing::warn!(?error, "failed to clean runtime during app exit");
    }
    if let Err(error) = state.mutate(|s| {
        s.connected = false;
        s.connected_at = None;
    }) {
        tracing::warn!(
            ?error,
            "failed to persist disconnected state during app exit"
        );
    }
}

pub fn reconnect_runtime_after_resume(app: &AppHandle) {
    let snapshot = app.state::<AppState>().snapshot();
    if !snapshot.connected {
        return;
    }
    let Some(server_id) = snapshot.active_server_id.clone() else {
        return;
    };
    if RESUME_RECONNECT_IN_FLIGHT.swap(true, Ordering::SeqCst) {
        return;
    }

    let app_handle = app.clone();
    tauri::async_runtime::spawn(async move {
        tokio::time::sleep(std::time::Duration::from_millis(1200)).await;
        let state = app_handle.state::<AppState>();
        let result = connect_server(app_handle.clone(), state, server_id).await;
        if let Err(error) = result {
            tracing::warn!(?error, "failed to reconnect runtime after resume");
            let state = app_handle.state::<AppState>();
            let _ = state.mutate(|s| {
                s.connected = false;
                s.connected_at = None;
            });
        }
        let _ = crate::tray::refresh_tray_menu(&app_handle);
        RESUME_RECONNECT_IN_FLIGHT.store(false, Ordering::SeqCst);
    });
}

#[cfg(windows)]
fn cleanup_tun(snapshot: Option<TunRuntimeSnapshot>) -> Result<(), String> {
    let _ = delete_tun_route("0.0.0.0/0");
    let _ = delete_tun_route("0.0.0.0/1");
    let _ = delete_tun_route("128.0.0.0/1");
    let _ = delete_tun_ipv6_route("::/0");
    if let Some(snapshot) = snapshot {
        if let Some(gateway) = snapshot.gateway {
            for ip in snapshot.bypass_ips {
                let _ = run_hidden(
                    "route",
                    &[
                        "DELETE".into(),
                        ip,
                        "MASK".into(),
                        "255.255.255.255".into(),
                        gateway.clone(),
                    ],
                );
            }
        }
    }
    let _ = run_hidden(
        "netsh",
        &[
            "interface".into(),
            "ip".into(),
            "set".into(),
            "dns".into(),
            format!("name={TUN_INTERFACE_NAME}"),
            "dhcp".into(),
        ],
    );
    let _ = flush_dns_cache();
    Ok(())
}

#[cfg(not(windows))]
fn cleanup_tun(_snapshot: Option<TunRuntimeSnapshot>) -> Result<(), String> {
    Ok(())
}

#[cfg(windows)]
fn delete_tun_route(prefix: &str) -> Result<(), String> {
    run_hidden(
        "netsh",
        &[
            "interface".into(),
            "ipv4".into(),
            "delete".into(),
            "route".into(),
            format!("prefix={prefix}"),
            format!("interface={TUN_INTERFACE_NAME}"),
            format!("nexthop={TUN_ADDRESS}"),
        ],
    )
}

#[cfg(windows)]
fn delete_tun_ipv6_route(prefix: &str) -> Result<(), String> {
    run_hidden(
        "netsh",
        &[
            "interface".into(),
            "ipv6".into(),
            "delete".into(),
            "route".into(),
            format!("prefix={prefix}"),
            format!("interface={TUN_INTERFACE_NAME}"),
            "store=active".into(),
        ],
    )
}

fn nimbo_data_dir() -> Result<PathBuf, String> {
    dirs::data_dir()
        .map(|dir| dir.join("Nimbo"))
        .ok_or_else(|| "APPDATA недоступен".to_string())
}

#[cfg(windows)]
fn flush_dns_cache() -> Result<(), String> {
    run_hidden("ipconfig", &["/flushdns".into()])
}

#[cfg(not(windows))]
fn flush_dns_cache() -> Result<(), String> {
    Ok(())
}

pub(crate) async fn ensure_tun_dependencies(app: &AppHandle) -> Result<TunInstallStatus, String> {
    let before = tun_status(app)?;
    if before.installed {
        return Ok(before);
    }

    #[cfg(all(windows, target_arch = "x86_64"))]
    {
        install_bundled_tun_files(app)?;
        let after_bundle = tun_status(app)?;
        if after_bundle.installed {
            return Ok(after_bundle);
        }

        download_tun2socks_windows_x64().await?;
        download_wintun_windows_x64().await?;
        tun_status(app)
    }

    #[cfg(not(all(windows, target_arch = "x86_64")))]
    {
        Err("Автоустановка TUN сейчас доступна только на Windows x64.".into())
    }
}

pub(crate) fn install_tun_dependencies_for_installer() -> Result<(), String> {
    #[cfg(all(windows, target_arch = "x86_64"))]
    {
        install_bundled_tun_files_without_app()?;
        let bin_dir = nimbo_data_dir()?.join("bin");
        let tun2socks_path = bin_dir.join(tun2socks_name());
        let wintun_path = bin_dir.join(wintun_name());
        if tun2socks_path.exists() && wintun_path.exists() {
            return Ok(());
        }

        let runtime = tokio::runtime::Runtime::new()
            .map_err(|e| format!("Не удалось создать runtime для установки TUN: {e}"))?;
        runtime.block_on(async {
            download_tun2socks_windows_x64().await?;
            download_wintun_windows_x64().await?;
            Ok::<(), String>(())
        })?;
        Ok(())
    }

    #[cfg(not(all(windows, target_arch = "x86_64")))]
    {
        Err("Автоустановка TUN сейчас доступна только на Windows x64.".into())
    }
}

fn tun_status(app: &AppHandle) -> Result<TunInstallStatus, String> {
    let tun2socks_path = find_existing_path(tun2socks_candidate_paths(app)?);
    let wintun_path = find_existing_path(wintun_candidate_paths(app)?);
    let mut missing = Vec::new();
    if tun2socks_path.is_none() {
        missing.push(tun2socks_name().to_string());
    }
    if wintun_path.is_none() {
        missing.push(wintun_name().to_string());
    }

    let installed = missing.is_empty();
    let needs_admin_restart = installed && !is_running_as_admin();
    let message = if installed && needs_admin_restart {
        "TUN установлен. Для подключения перезапусти Nimbo от имени администратора.".to_string()
    } else if installed {
        "TUN установлен и готов.".to_string()
    } else {
        format!("TUN не установлен: отсутствует {}.", missing.join(", "))
    };

    Ok(TunInstallStatus {
        installed,
        can_install: cfg!(all(windows, target_arch = "x86_64")),
        needs_admin_restart,
        tun2socks_path: tun2socks_path.map(path_to_string),
        wintun_path: wintun_path.map(path_to_string),
        missing,
        message,
    })
}

#[allow(dead_code)]
fn install_bundled_tun_files_without_app() -> Result<(), String> {
    let bin_dir = nimbo_data_dir()?.join("bin");
    std::fs::create_dir_all(&bin_dir).map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;

    if let Some(path) = find_existing_path(local_bundled_tun_paths(tun2socks_name())) {
        copy_tun_file(&path, &bin_dir.join(tun2socks_name()))?;
    }
    if let Some(path) = find_existing_path(local_bundled_tun_paths(wintun_name())) {
        copy_tun_file(&path, &bin_dir.join(wintun_name()))?;
    }
    Ok(())
}

#[allow(dead_code)]
fn install_bundled_tun_files(app: &AppHandle) -> Result<(), String> {
    let bin_dir = nimbo_data_dir()?.join("bin");
    std::fs::create_dir_all(&bin_dir).map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;

    if let Some(path) = find_existing_path(bundled_tun2socks_paths(app)?) {
        copy_tun_file(&path, &bin_dir.join(tun2socks_name()))?;
    }
    if let Some(path) = find_existing_path(bundled_wintun_paths(app)?) {
        copy_tun_file(&path, &bin_dir.join(wintun_name()))?;
    }
    Ok(())
}

fn copy_tun_file(source: &Path, target: &Path) -> Result<(), String> {
    if source == target {
        return Ok(());
    }
    if let Some(parent) = target.parent() {
        std::fs::create_dir_all(parent)
            .map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;
    }
    std::fs::copy(source, target)
        .map_err(|e| format!("Не удалось установить {}: {e}", target.display()))?;
    Ok(())
}

fn find_existing_path(paths: Vec<PathBuf>) -> Option<PathBuf> {
    paths.into_iter().find(|path| path.exists())
}

fn tun2socks_candidate_paths(app: &AppHandle) -> Result<Vec<PathBuf>, String> {
    let mut paths = Vec::new();
    if let Some(path) = std::env::var_os("NIMBO_TUN2SOCKS_PATH").map(PathBuf::from) {
        paths.push(path);
    }
    paths.push(nimbo_data_dir()?.join("bin").join(tun2socks_name()));
    paths.extend(bundled_tun2socks_paths(app)?);
    if let Ok(cwd) = std::env::current_dir() {
        paths.push(cwd.join("resources").join("tun").join(tun2socks_name()));
        paths.push(cwd.join("binaries").join(tun2socks_name()));
        paths.push(
            cwd.join("src-tauri")
                .join("resources")
                .join("tun")
                .join(tun2socks_name()),
        );
        paths.push(
            cwd.join("src-tauri")
                .join("binaries")
                .join(tun2socks_name()),
        );
    }
    Ok(paths)
}

fn wintun_candidate_paths(app: &AppHandle) -> Result<Vec<PathBuf>, String> {
    let mut paths = Vec::new();
    if let Some(path) = std::env::var_os("NIMBO_WINTUN_PATH").map(PathBuf::from) {
        paths.push(path);
    }
    paths.push(nimbo_data_dir()?.join("bin").join(wintun_name()));
    paths.extend(bundled_wintun_paths(app)?);
    if let Ok(cwd) = std::env::current_dir() {
        paths.push(cwd.join("resources").join("tun").join(wintun_name()));
        paths.push(cwd.join("binaries").join(wintun_name()));
        paths.push(
            cwd.join("src-tauri")
                .join("resources")
                .join("tun")
                .join(wintun_name()),
        );
        paths.push(cwd.join("src-tauri").join("binaries").join(wintun_name()));
    }
    Ok(paths)
}

fn bundled_tun2socks_paths(app: &AppHandle) -> Result<Vec<PathBuf>, String> {
    bundled_tun_paths(app, tun2socks_name())
}

fn bundled_wintun_paths(app: &AppHandle) -> Result<Vec<PathBuf>, String> {
    bundled_tun_paths(app, wintun_name())
}

fn bundled_tun_paths(app: &AppHandle, file_name: &str) -> Result<Vec<PathBuf>, String> {
    let mut paths = Vec::new();
    if let Ok(resource) = app.path().resource_dir() {
        paths.push(resource.join("resources").join("tun").join(file_name));
        paths.push(
            resource
                .join("resources")
                .join("resources")
                .join("tun")
                .join(file_name),
        );
        paths.push(resource.join("tun").join(file_name));
        paths.push(resource.join("binaries").join(file_name));
        paths.push(resource.join(file_name));
    }
    paths.extend(local_bundled_tun_paths(file_name));
    Ok(paths)
}

fn local_bundled_tun_paths(file_name: &str) -> Vec<PathBuf> {
    let mut paths = Vec::new();
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            paths.push(
                dir.join("resources")
                    .join("resources")
                    .join("tun")
                    .join(file_name),
            );
            paths.push(dir.join("resources").join("tun").join(file_name));
            paths.push(dir.join("tun").join(file_name));
            paths.push(dir.join("binaries").join(file_name));
        }
    }
    if let Ok(cwd) = std::env::current_dir() {
        paths.push(
            cwd.join("resources")
                .join("resources")
                .join("tun")
                .join(file_name),
        );
        paths.push(cwd.join("resources").join("tun").join(file_name));
        paths.push(cwd.join("tun").join(file_name));
        paths.push(cwd.join("binaries").join(file_name));
    }
    paths
}

fn tun2socks_name() -> &'static str {
    if cfg!(windows) {
        "tun2socks.exe"
    } else {
        "tun2socks"
    }
}

fn wintun_name() -> &'static str {
    "wintun.dll"
}

fn path_to_string(path: PathBuf) -> String {
    path.to_string_lossy().to_string()
}

#[cfg(all(windows, target_arch = "x86_64"))]
async fn download_tun2socks_windows_x64() -> Result<PathBuf, String> {
    let bin_dir = nimbo_data_dir()?.join("bin");
    std::fs::create_dir_all(&bin_dir).map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;
    let target = bin_dir.join(tun2socks_name());
    if target.exists() {
        return Ok(target);
    }

    let archive_url =
        "https://github.com/xjasonlyu/tun2socks/releases/latest/download/tun2socks-windows-amd64.zip";
    tracing::info!(%archive_url, "downloading tun2socks");
    let bytes = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(90))
        .build()
        .map_err(|e| format!("Не удалось создать HTTP-клиент для tun2socks: {e}"))?
        .get(archive_url)
        .send()
        .await
        .map_err(|e| format!("Не удалось скачать tun2socks: {e}"))?
        .error_for_status()
        .map_err(|e| format!("Не удалось скачать tun2socks: {e}"))?
        .bytes()
        .await
        .map_err(|e| format!("Не удалось прочитать архив tun2socks: {e}"))?;

    extract_named_file_from_zip(bytes.to_vec(), tun2socks_name(), &target)?;
    Ok(target)
}

#[cfg(all(windows, target_arch = "x86_64"))]
async fn download_wintun_windows_x64() -> Result<PathBuf, String> {
    let bin_dir = nimbo_data_dir()?.join("bin");
    std::fs::create_dir_all(&bin_dir).map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;
    let target = bin_dir.join(wintun_name());
    if target.exists() {
        return Ok(target);
    }

    let archive_url = "https://www.wintun.net/builds/wintun-0.14.1.zip";
    tracing::info!(%archive_url, "downloading wintun");
    let bytes = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(90))
        .build()
        .map_err(|e| format!("Не удалось создать HTTP-клиент для Wintun: {e}"))?
        .get(archive_url)
        .send()
        .await
        .map_err(|e| format!("Не удалось скачать Wintun: {e}"))?
        .error_for_status()
        .map_err(|e| format!("Не удалось скачать Wintun: {e}"))?
        .bytes()
        .await
        .map_err(|e| format!("Не удалось прочитать архив Wintun: {e}"))?;

    extract_amd64_wintun_from_zip(bytes.to_vec(), &target)?;
    Ok(target)
}

#[cfg(all(windows, target_arch = "x86_64"))]
fn extract_named_file_from_zip(
    bytes: Vec<u8>,
    file_name: &str,
    target: &Path,
) -> Result<(), String> {
    let reader = Cursor::new(bytes);
    let mut archive = zip::ZipArchive::new(reader)
        .map_err(|e| format!("Не удалось открыть архив {file_name}: {e}"))?;
    for index in 0..archive.len() {
        let mut file = archive
            .by_index(index)
            .map_err(|e| format!("Не удалось прочитать архив {file_name}: {e}"))?;
        if !file.is_file() {
            continue;
        }
        let Some(name) = file
            .enclosed_name()
            .and_then(|path| path.file_name().map(|name| name.to_owned()))
        else {
            continue;
        };
        let name = name.to_string_lossy();
        let is_target = name.eq_ignore_ascii_case(file_name)
            || (file_name.eq_ignore_ascii_case(tun2socks_name())
                && name.to_ascii_lowercase().starts_with("tun2socks")
                && name.to_ascii_lowercase().ends_with(".exe"));
        if is_target {
            let mut out = File::create(target)
                .map_err(|e| format!("Не удалось создать {}: {e}", target.display()))?;
            std::io::copy(&mut file, &mut out)
                .map_err(|e| format!("Не удалось записать {}: {e}", target.display()))?;
            return Ok(());
        }
    }
    Err(format!("В архиве не найден {file_name}."))
}

#[cfg(all(windows, target_arch = "x86_64"))]
fn extract_amd64_wintun_from_zip(bytes: Vec<u8>, target: &Path) -> Result<(), String> {
    let reader = Cursor::new(bytes);
    let mut archive = zip::ZipArchive::new(reader)
        .map_err(|e| format!("Не удалось открыть архив Wintun: {e}"))?;
    for index in 0..archive.len() {
        let mut file = archive
            .by_index(index)
            .map_err(|e| format!("Не удалось прочитать архив Wintun: {e}"))?;
        if !file.is_file() {
            continue;
        }
        let Some(path) = file.enclosed_name() else {
            continue;
        };
        let normalized = path
            .to_string_lossy()
            .replace('\\', "/")
            .to_ascii_lowercase();
        if normalized.ends_with("/bin/amd64/wintun.dll")
            || normalized.ends_with("bin/amd64/wintun.dll")
        {
            let mut out = File::create(target)
                .map_err(|e| format!("Не удалось создать {}: {e}", target.display()))?;
            std::io::copy(&mut file, &mut out)
                .map_err(|e| format!("Не удалось записать {}: {e}", target.display()))?;
            return Ok(());
        }
    }
    Err("В архиве Wintun не найден bin/amd64/wintun.dll.".into())
}

#[cfg(windows)]
pub(crate) fn is_running_as_admin() -> bool {
    hidden_command("net")
        .arg("session")
        .status()
        .map(|status| status.success())
        .unwrap_or(false)
}

#[cfg(not(windows))]
pub(crate) fn is_running_as_admin() -> bool {
    false
}

#[cfg(windows)]
fn relaunch_as_admin() -> Result<(), String> {
    let exe = std::env::current_exe().map_err(|e| format!("Не удалось найти путь Nimbo: {e}"))?;
    let escaped = exe.to_string_lossy().replace('\'', "''");
    let parent_pid = std::process::id();
    let status = hidden_command("powershell")
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(format!(
            "$ErrorActionPreference='Stop'; try {{ Start-Process -FilePath '{escaped}' -ArgumentList @('--wait-for-parent','{parent_pid}') -Verb RunAs; exit 0 }} catch {{ exit 1223 }}"
        ))
        .status()
        .map_err(|e| format!("Не удалось перезапустить от имени администратора: {e}"))?;
    if !status.success() {
        return Err("Перезапуск от имени администратора отменён или не удался.".into());
    }
    Ok(())
}

#[cfg(not(windows))]
fn relaunch_as_admin() -> Result<(), String> {
    Err("Перезапуск от имени администратора доступен только на Windows.".into())
}

#[cfg(windows)]
fn current_system_proxy_is_exact_nimbo() -> Result<bool, String> {
    use winreg::enums::{HKEY_CURRENT_USER, KEY_READ};
    use winreg::RegKey;

    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let path = r"Software\Microsoft\Windows\CurrentVersion\Internet Settings";
    let key = hkcu
        .open_subkey_with_flags(path, KEY_READ)
        .map_err(|e| format!("Не удалось открыть настройки proxy Windows: {e}"))?;
    let proxy_enable: Option<u32> = key.get_value("ProxyEnable").ok();
    if proxy_enable != Some(1) {
        return Ok(false);
    }
    Ok(key
        .get_value::<String, _>("ProxyServer")
        .ok()
        .as_deref()
        .is_some_and(|value| is_exact_nimbo_system_proxy(value, ProxyPorts::default())))
}

#[cfg(not(windows))]
fn current_system_proxy_is_exact_nimbo() -> Result<bool, String> {
    Ok(false)
}

#[cfg(windows)]
fn cleanup_stale_nimbo_system_proxy() -> Result<(), String> {
    use winreg::enums::{HKEY_CURRENT_USER, KEY_READ, KEY_WRITE};
    use winreg::RegKey;

    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let path = r"Software\Microsoft\Windows\CurrentVersion\Internet Settings";
    let key = hkcu
        .open_subkey_with_flags(path, KEY_READ | KEY_WRITE)
        .map_err(|e| format!("Не удалось открыть настройки proxy Windows: {e}"))?;

    let proxy_enable: Option<u32> = key.get_value("ProxyEnable").ok();
    if proxy_enable != Some(1) {
        return Ok(());
    }

    let proxy_server = key.get_value::<String, _>("ProxyServer").ok();
    if !proxy_server
        .as_deref()
        .is_some_and(|value| is_exact_nimbo_system_proxy(value, ProxyPorts::default()))
    {
        return Ok(());
    }

    key.set_value("ProxyEnable", &0u32)
        .map_err(|e| format!("Не удалось выключить залипший системный proxy Nimbo: {e}"))?;
    let _ = hidden_command("netsh")
        .arg("winhttp")
        .arg("reset")
        .arg("proxy")
        .status();
    Ok(())
}

#[cfg(not(windows))]
fn cleanup_stale_nimbo_system_proxy() -> Result<(), String> {
    Ok(())
}

fn is_exact_nimbo_system_proxy(proxy_server: &str, ports: ProxyPorts) -> bool {
    let compact = proxy_server
        .split_whitespace()
        .collect::<String>()
        .to_ascii_lowercase();
    let expected = [
        format!("http=127.0.0.1:{}", ports.http),
        format!("https=127.0.0.1:{}", ports.http),
        format!("socks=127.0.0.1:{}", ports.socks),
    ];
    compact.split(';').collect::<HashSet<_>>()
        == expected.iter().map(String::as_str).collect::<HashSet<_>>()
}

#[cfg(windows)]
fn kill_orphan_nimbo_core_processes() -> Result<(), String> {
    let data_dir = path_to_string(nimbo_data_dir()?);
    let config_path = path_to_string(nimbo_data_dir()?.join("runtime").join("xray-config.json"));
    let data_dir = data_dir.replace('\'', "''");
    let config_path = config_path.replace('\'', "''");
    let script = format!(
        r#"
$ErrorActionPreference = 'SilentlyContinue'
$dataDir = '{data_dir}'
$configPath = '{config_path}'
$processes = Get-CimInstance Win32_Process -Filter "Name = 'xray.exe' OR Name = 'tun2socks.exe'"
foreach ($p in @($processes)) {{
  $name = [string]$p.Name
  $path = [string]$p.ExecutablePath
  $cmd = [string]$p.CommandLine
  $owned = $false
  if ($name -ieq 'xray.exe' -and $cmd -and $cmd.IndexOf($configPath, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {{
    $owned = $true
  }}
  if ($name -ieq 'tun2socks.exe' -and $path -and $path.StartsWith($dataDir, [System.StringComparison]::OrdinalIgnoreCase)) {{
    $owned = $true
  }}
  if ($owned) {{
    Stop-Process -Id ([int]$p.ProcessId) -Force -ErrorAction SilentlyContinue
  }}
}}
exit 0
"#
    );

    let mut command = hidden_output_command("powershell");
    command
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(script)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    let output = command_output_with_timeout(command, std::time::Duration::from_secs(4))
        .map_err(|e| format!("Не удалось проверить залипшие процессы Nimbo: {e}"))?;
    if output.status.success() {
        Ok(())
    } else {
        Err(format!(
            "Проверка залипших процессов Nimbo завершилась с кодом {}",
            output.status
        ))
    }
}

#[cfg(not(windows))]
fn kill_orphan_nimbo_core_processes() -> Result<(), String> {
    Ok(())
}

#[cfg(windows)]
fn apply_system_proxy(ports: ProxyPorts) -> Result<Option<SystemProxySnapshot>, String> {
    use winreg::enums::{HKEY_CURRENT_USER, KEY_READ, KEY_WRITE};
    use winreg::RegKey;

    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let path = r"Software\Microsoft\Windows\CurrentVersion\Internet Settings";
    let key = hkcu
        .open_subkey_with_flags(path, KEY_READ | KEY_WRITE)
        .map_err(|e| format!("Не удалось открыть настройки proxy Windows: {e}"))?;

    let snapshot = SystemProxySnapshot {
        proxy_enable: key.get_value("ProxyEnable").ok(),
        proxy_server: key.get_value("ProxyServer").ok(),
        proxy_override: key.get_value("ProxyOverride").ok(),
    };

    key.set_value("ProxyEnable", &1u32)
        .map_err(|e| format!("Не удалось включить системный proxy: {e}"))?;
    key.set_value(
        "ProxyServer",
        &format!(
            "http=127.0.0.1:{};https=127.0.0.1:{};socks=127.0.0.1:{}",
            ports.http, ports.http, ports.socks
        ),
    )
    .map_err(|e| format!("Не удалось записать адрес системного proxy: {e}"))?;
    key.set_value("ProxyOverride", &"<local>")
        .map_err(|e| format!("Не удалось записать исключения системного proxy: {e}"))?;

    let _ = hidden_command("netsh")
        .arg("winhttp")
        .arg("import")
        .arg("proxy")
        .arg("source=ie")
        .status();

    Ok(Some(snapshot))
}

#[cfg(not(windows))]
fn apply_system_proxy(_ports: ProxyPorts) -> Result<Option<SystemProxySnapshot>, String> {
    Err("Системный proxy сейчас реализован только для Windows.".into())
}

#[cfg(windows)]
fn restore_system_proxy(snapshot: Option<SystemProxySnapshot>) -> Result<(), String> {
    use winreg::enums::{HKEY_CURRENT_USER, KEY_WRITE};
    use winreg::RegKey;

    let Some(snapshot) = snapshot else {
        return Ok(());
    };
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let path = r"Software\Microsoft\Windows\CurrentVersion\Internet Settings";
    let key = hkcu
        .open_subkey_with_flags(path, KEY_WRITE)
        .map_err(|e| format!("Не удалось открыть настройки proxy Windows: {e}"))?;

    if let Some(value) = snapshot.proxy_enable {
        key.set_value("ProxyEnable", &value)
            .map_err(|e| format!("Не удалось восстановить ProxyEnable: {e}"))?;
    } else {
        let _ = key.delete_value("ProxyEnable");
    }
    if let Some(value) = snapshot.proxy_server {
        key.set_value("ProxyServer", &value)
            .map_err(|e| format!("Не удалось восстановить ProxyServer: {e}"))?;
    } else {
        let _ = key.delete_value("ProxyServer");
    }
    if let Some(value) = snapshot.proxy_override {
        key.set_value("ProxyOverride", &value)
            .map_err(|e| format!("Не удалось восстановить ProxyOverride: {e}"))?;
    } else {
        let _ = key.delete_value("ProxyOverride");
    }

    let _ = hidden_command("netsh")
        .arg("winhttp")
        .arg("reset")
        .arg("proxy")
        .status();

    Ok(())
}

fn normalize_accent_color(value: &str) -> String {
    let value = value.trim();
    if value.len() == 7
        && value.starts_with('#')
        && value.chars().skip(1).all(|ch| ch.is_ascii_hexdigit())
    {
        value.to_ascii_lowercase()
    } else {
        "#75a7ff".into()
    }
}

fn normalize_ui_style(value: &str) -> String {
    match value.trim() {
        "nimbo" | "material_you" => value.trim().into(),
        _ => "nimbo".into(),
    }
}

fn normalize_latency_protocol(value: &str) -> String {
    match value.trim() {
        "tcp_connect" | "icmp" | "http_head" => value.trim().into(),
        _ => "tcp_connect".into(),
    }
}

fn normalize_latency_test_url(value: &str) -> String {
    let value = value.trim();
    if value.starts_with("http://") || value.starts_with("https://") {
        value.into()
    } else {
        "https://www.gstatic.com/generate_204".into()
    }
}

fn normalize_latency_timeout_ms(value: u32) -> u32 {
    value.clamp(500, 60_000)
}

fn normalize_latency_display_format(value: &str) -> String {
    match value.trim() {
        "ms" | "badge" => value.trim().into(),
        _ => "ms".into(),
    }
}

fn normalize_app_routing_mode(value: &str) -> String {
    match value.trim() {
        "proxy" => "proxy".into(),
        _ => "direct".into(),
    }
}

fn normalize_xudp_udp443_mode(value: &str) -> String {
    match value.trim() {
        "allow" | "skip" | "reject" => value.trim().into(),
        _ => "reject".into(),
    }
}

fn normalize_preferred_ip_family(value: &str) -> String {
    match value.trim() {
        "auto" | "ipv4" | "ipv6" => value.trim().into(),
        _ => "auto".into(),
    }
}

fn normalize_server_sorting(value: &str) -> String {
    match value.trim() {
        "provider" | "name" | "ping" | "protocol" => value.trim().into(),
        _ => "provider".into(),
    }
}

fn normalize_connect_button_style(value: &str) -> String {
    match value.trim() {
        "classic" | "compact" => value.trim().into(),
        _ => "classic".into(),
    }
}

#[cfg(windows)]
fn is_launch_at_login_enabled() -> Result<bool, String> {
    use winreg::enums::{HKEY_CURRENT_USER, KEY_READ};
    use winreg::RegKey;

    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let key = match hkcu
        .open_subkey_with_flags(r"Software\Microsoft\Windows\CurrentVersion\Run", KEY_READ)
    {
        Ok(key) => key,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(false),
        Err(e) => return Err(format!("Не удалось открыть автозапуск Windows: {e}")),
    };
    Ok(key.get_value::<String, _>("Nimbo").is_ok())
}

#[cfg(not(windows))]
fn is_launch_at_login_enabled() -> Result<bool, String> {
    Ok(false)
}

#[cfg(windows)]
fn set_launch_at_login(_app: &AppHandle, enabled: bool) -> Result<(), String> {
    use winreg::enums::HKEY_CURRENT_USER;
    use winreg::RegKey;

    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let (key, _) = hkcu
        .create_subkey(r"Software\Microsoft\Windows\CurrentVersion\Run")
        .map_err(|e| format!("Не удалось открыть автозапуск Windows: {e}"))?;

    if enabled {
        let exe = std::env::current_exe()
            .map_err(|e| format!("Не удалось определить путь Nimbo.exe: {e}"))?;
        let value = format!("\"{}\"", exe.display());
        key.set_value("Nimbo", &value)
            .map_err(|e| format!("Не удалось включить автозапуск: {e}"))?;
    } else {
        match key.delete_value("Nimbo") {
            Ok(()) => {}
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
            Err(e) => return Err(format!("Не удалось выключить автозапуск: {e}")),
        }
    }

    Ok(())
}

#[cfg(not(windows))]
fn set_launch_at_login(_app: &AppHandle, enabled: bool) -> Result<(), String> {
    if enabled {
        return Err("Автозапуск пока поддерживается только на Windows.".into());
    }
    Ok(())
}

#[cfg(not(windows))]
fn restore_system_proxy(_snapshot: Option<SystemProxySnapshot>) -> Result<(), String> {
    Ok(())
}

fn hidden_command(program: &str) -> Command {
    let mut command = Command::new(program);
    command
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    command
}

fn hidden_output_command(program: &str) -> Command {
    let mut command = Command::new(program);
    command.stdin(Stdio::null()).stderr(Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    command
}

fn hidden_output_path_command(program: &Path) -> Command {
    let mut command = Command::new(program);
    command.stdin(Stdio::null()).stderr(Stdio::piped());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    command
}

fn run_hidden(program: &str, args: &[String]) -> Result<(), String> {
    let status = hidden_command(program)
        .args(args)
        .status()
        .map_err(|e| format!("{program} не запустился: {e}"))?;
    if status.success() {
        Ok(())
    } else {
        Err(format!("{program} завершился с кодом {status}"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn validates_xray_archive_sha256() {
        verify_xray_archive_digest(
            b"nimbo",
            "SHA256= fae4ccc83b91d0f3d002cc9799e33d28a11fd847ab1aa9adf60061ddcde09105",
        )
        .unwrap();
        verify_xray_archive_digest(
            b"nimbo",
            "SHA2-256= fae4ccc83b91d0f3d002cc9799e33d28a11fd847ab1aa9adf60061ddcde09105",
        )
        .unwrap();
        assert!(verify_xray_archive_digest(
            b"nimbo",
            "SHA256= 0000000000000000000000000000000000000000000000000000000000000000"
        )
        .is_err());
    }

    #[test]
    fn resolves_popular_xray_runtime_archives() {
        assert_eq!(
            xray_release_archive_name("windows", "x86_64"),
            Some("Xray-windows-64.zip")
        );
        assert_eq!(
            xray_release_archive_name("linux", "x86_64"),
            Some("Xray-linux-64.zip")
        );
        assert_eq!(
            xray_release_archive_name("linux", "aarch64"),
            Some("Xray-linux-arm64-v8a.zip")
        );
    }

    #[test]
    fn native_tun_captures_ipv4_and_ipv6_default_routes() {
        let mut config = serde_json::json!({ "inbounds": [] });
        add_native_tun_inbound(&mut config);
        let settings = &config["inbounds"][0]["settings"];

        assert_eq!(
            settings["gateway"],
            serde_json::json!([TUN_GATEWAY_CIDR, TUN_IPV6_GATEWAY_CIDR])
        );
        assert_eq!(
            settings["autoSystemRoutingTable"],
            serde_json::json!(["0.0.0.0/0", "::/0"])
        );
    }

    #[test]
    fn traffic_rate_uses_the_oldest_sample_in_the_window() {
        let start = std::time::Instant::now();
        let samples = std::collections::VecDeque::from([
            TrafficRuntimeSample {
                at: start,
                upload: 100,
                download: 200,
            },
            TrafficRuntimeSample {
                at: start + std::time::Duration::from_secs(2),
                upload: 2_100,
                download: 4_200,
            },
        ]);

        assert_eq!(traffic_rate(&samples), Some((1_000.0, 2_000.0)));
    }

    #[cfg(windows)]
    #[test]
    fn command_timeout_reader_drains_large_powershell_output() {
        let mut command = hidden_output_command("powershell");
        command
            .arg("-NoProfile")
            .arg("-Command")
            .arg("[Console]::Out.Write(('x' * 262144))")
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());

        let output = command_output_with_timeout(command, std::time::Duration::from_secs(5))
            .expect("large piped output must not deadlock");

        assert!(output.status.success());
        assert_eq!(output.stdout.len(), 262_144);
        assert!(output.stderr.is_empty());
    }

    fn test_server() -> Server {
        Server {
            id: "server-1".into(),
            name: "Server 1".into(),
            server_description: None,
            host_uuid: None,
            xray_json_template_uuid: None,
            protocol: nimbo_subscription::Protocol::Vless(nimbo_subscription::VlessConfig {
                address: "example.com".into(),
                port: 443,
                uuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee".into(),
                flow: None,
                encryption: "none".into(),
                stream: nimbo_subscription::StreamSettings::default(),
            }),
        }
    }

    fn snapshot_with_provider_app_rule(
        server: Server,
        rule: nimbo_subscription::SubscriptionAppProxyRule,
    ) -> PersistedState {
        let mut snapshot = PersistedState {
            active_subscription_url: Some("https://example.com/sub".into()),
            ..Default::default()
        };
        snapshot
            .subscriptions
            .push(nimbo_subscription::Subscription {
                url: "https://example.com/sub".into(),
                name: Some("Test".into()),
                meta: nimbo_subscription::SubscriptionMeta {
                    app_proxy_rules: vec![rule],
                    ..Default::default()
                },
                servers: vec![server],
                info: None,
                fetched_at: 0,
            });
        snapshot
    }

    #[test]
    fn active_connection_process_rule_wins_over_local_proxy_endpoint() {
        let snapshot = PersistedState {
            connected: true,
            ..Default::default()
        };
        let app_rules = vec![AppProxyRule {
            id: "opera-direct".into(),
            name: "Opera".into(),
            executable_path: "opera.exe".into(),
            mode: AppProxyMode::Direct,
            enabled: true,
        }];

        let decision = classify_active_connection(
            &snapshot,
            None,
            &[],
            &app_rules,
            &XrayRoutingProfileRules::default(),
            "opera.exe",
            None,
            "127.0.0.1",
            ProxyPorts::default().socks,
            ProxyPorts::default(),
        );

        assert!(matches!(decision.route, ActiveConnectionRoute::Direct));
        assert_eq!(decision.rule, "process rule");

        let decision_from_path = classify_active_connection(
            &snapshot,
            None,
            &[],
            &app_rules,
            &XrayRoutingProfileRules::default(),
            "Unknown process",
            Some(r"C:\Users\User\AppData\Local\Programs\Opera\opera.exe"),
            "127.0.0.1",
            ProxyPorts::default().socks,
            ProxyPorts::default(),
        );

        assert!(matches!(
            decision_from_path.route,
            ActiveConnectionRoute::Direct
        ));
        assert_eq!(decision_from_path.rule, "process rule");
    }

    #[test]
    fn active_connections_hide_local_loopback_noise_but_keep_proxy_clients() {
        let ports = ProxyPorts::default();

        assert!(should_hide_internal_connection(
            "opera.exe",
            "127.0.0.1",
            "127.0.0.1",
            13000,
            ports,
        ));
        assert!(!should_hide_internal_connection(
            "opera.exe",
            "127.0.0.1",
            "127.0.0.1",
            ports.socks,
            ports,
        ));
        assert!(should_hide_internal_connection(
            "xray.exe",
            "192.168.1.20",
            "1.1.1.1",
            443,
            ports,
        ));
    }

    #[test]
    fn exact_nimbo_system_proxy_matches_only_own_full_proxy_string() {
        let ports = ProxyPorts::default();

        assert!(is_exact_nimbo_system_proxy(
            "http=127.0.0.1:10809;https=127.0.0.1:10809;socks=127.0.0.1:10808",
            ports,
        ));
        assert!(is_exact_nimbo_system_proxy(
            " socks=127.0.0.1:10808 ; http=127.0.0.1:10809 ; https=127.0.0.1:10809 ",
            ports,
        ));
        assert!(!is_exact_nimbo_system_proxy("127.0.0.1:10809", ports));
        assert!(!is_exact_nimbo_system_proxy(
            "http=127.0.0.1:7890;https=127.0.0.1:7890;socks=127.0.0.1:7890",
            ports,
        ));
    }

    #[test]
    fn xray_app_rules_emit_process_name_for_paths() {
        let rules = xray_app_rules(&[AppProxyRule {
            id: "opera-proxy".into(),
            name: "Opera".into(),
            executable_path: r"C:\Users\User\AppData\Local\Programs\Opera\opera.exe".into(),
            mode: AppProxyMode::Proxy,
            enabled: true,
        }]);

        assert_eq!(rules[0].process, "opera.exe");
        assert_eq!(rules[0].mode, XrayAppRoutingMode::Proxy);
        assert!(rules[0].enabled);
    }

    #[test]
    fn external_drive_telegram_rule_matches_the_process_name() {
        let rules = xray_app_rules(&[AppProxyRule {
            id: "telegram-proxy".into(),
            name: "Telegram".into(),
            executable_path: r"E:\Portable\Telegram Desktop\Telegram.exe".into(),
            mode: AppProxyMode::Proxy,
            enabled: true,
        }]);

        assert_eq!(rules[0].process, "Telegram.exe");
        assert_eq!(rules[0].mode, XrayAppRoutingMode::Proxy);
        assert!(rules[0].enabled);
    }

    #[test]
    fn provider_app_rule_can_be_disabled_locally() {
        let server = test_server();
        let mut snapshot = snapshot_with_provider_app_rule(
            server.clone(),
            nimbo_subscription::SubscriptionAppProxyRule {
                id: "provider-opera".into(),
                name: "Opera".into(),
                executable_path: "opera.exe".into(),
                mode: nimbo_subscription::SubscriptionAppProxyMode::Direct,
                enabled: true,
                source: Some("process-direct".into()),
            },
        );
        snapshot.app_proxy_rules.push(AppProxyRule {
            id: "local-opera".into(),
            name: "Opera".into(),
            executable_path: r"C:\Program Files\Opera\opera.exe".into(),
            mode: AppProxyMode::Proxy,
            enabled: false,
        });

        let rules = combined_app_proxy_rules(&snapshot, &server);

        assert_eq!(rules.len(), 1);
        assert!(!rules[0].enabled);
        assert_eq!(rules[0].mode, AppProxyMode::Direct);
    }

    #[test]
    fn local_enabled_app_rule_overrides_provider_mode() {
        let server = test_server();
        let mut snapshot = snapshot_with_provider_app_rule(
            server.clone(),
            nimbo_subscription::SubscriptionAppProxyRule {
                id: "provider-opera".into(),
                name: "Opera".into(),
                executable_path: "opera.exe".into(),
                mode: nimbo_subscription::SubscriptionAppProxyMode::Direct,
                enabled: true,
                source: Some("process-direct".into()),
            },
        );
        snapshot.app_proxy_rules.push(AppProxyRule {
            id: "local-opera".into(),
            name: "Opera override".into(),
            executable_path: r"C:\Program Files\Opera\opera.exe".into(),
            mode: AppProxyMode::Proxy,
            enabled: true,
        });

        let rules = combined_app_proxy_rules(&snapshot, &server);

        assert_eq!(rules.len(), 1);
        assert!(rules[0].enabled);
        assert_eq!(rules[0].mode, AppProxyMode::Proxy);
        assert_eq!(rules[0].name, "Opera override");
    }

    #[test]
    fn runtime_config_local_proxy_override_emits_proxy_process_rule() {
        let server = test_server();
        let mut snapshot = snapshot_with_provider_app_rule(
            server.clone(),
            nimbo_subscription::SubscriptionAppProxyRule {
                id: "provider-opera".into(),
                name: "Opera".into(),
                executable_path: "opera.exe".into(),
                mode: nimbo_subscription::SubscriptionAppProxyMode::Direct,
                enabled: true,
                source: Some("process-direct".into()),
            },
        );
        snapshot.app_proxy_rules.push(AppProxyRule {
            id: "local-opera".into(),
            name: "Opera override".into(),
            executable_path: r"C:\Program Files\Opera\opera.exe".into(),
            mode: AppProxyMode::Proxy,
            enabled: true,
        });

        let config = build_runtime_xray_config(&server, &snapshot, ProxyPorts::default()).unwrap();
        let rules = config["routing"]["rules"].as_array().unwrap();
        let process_rule = rules
            .iter()
            .find(|rule| {
                rule.get("process")
                    .and_then(serde_json::Value::as_array)
                    .is_some_and(|items| items.iter().any(|item| item == "opera.exe"))
            })
            .expect("missing opera process rule");

        assert_eq!(process_rule["outboundTag"], "proxy");
        assert_eq!(rules.last().unwrap()["outboundTag"], "proxy");
    }

    #[test]
    fn merge_template_routing_keeps_template_rules_before_proxy_fallback() {
        let mut routing_value = json!({
            "rules": [
                {
                    "domain": ["regexp:.*\\.ru$"],
                    "outboundTag": "direct"
                }
            ]
        });
        let base_config = json!({
            "routing": {
                "rules": [
                    {
                        "type": "field",
                        "inboundTag": ["api"],
                        "outboundTag": "api"
                    },
                    {
                        "type": "field",
                        "network": "tcp,udp",
                        "outboundTag": "proxy"
                    }
                ]
            }
        });

        merge_template_routing(routing_value.as_object_mut().unwrap(), &base_config);

        let rules = routing_value["rules"].as_array().unwrap();
        assert_eq!(rules[0]["outboundTag"], "api");
        assert_eq!(rules[1]["outboundTag"], "direct");
        assert_eq!(rules[1]["type"], "field");
        assert_eq!(rules[2]["outboundTag"], "proxy");
    }

    #[test]
    fn merge_template_routing_drops_template_direct_fallback() {
        let mut routing_value = json!({
            "rules": [
                {
                    "network": "tcp,udp",
                    "outboundTag": "direct"
                },
                {
                    "domain": ["domain:direct.example"],
                    "outboundTag": "direct"
                }
            ]
        });
        let base_config = json!({
            "routing": {
                "rules": [
                    {
                        "type": "field",
                        "network": "tcp,udp",
                        "outboundTag": "proxy"
                    }
                ]
            }
        });

        merge_template_routing(routing_value.as_object_mut().unwrap(), &base_config);

        let rules = routing_value["rules"].as_array().unwrap();
        assert!(rules.iter().any(|rule| {
            rule.get("domain")
                .and_then(serde_json::Value::as_array)
                .map(|domains| {
                    domains
                        .iter()
                        .any(|domain| domain == "domain:direct.example")
                })
                .unwrap_or(false)
        }));
        assert!(!rules.iter().any(|rule| {
            rule.get("outboundTag") == Some(&json!("direct"))
                && rule.get("network").and_then(serde_json::Value::as_str) == Some("tcp,udp")
        }));
        assert_eq!(rules.last().unwrap()["outboundTag"], "proxy");
    }

    #[test]
    fn merge_template_routing_drops_template_balancer_fallback() {
        let mut routing_value = json!({
            "balancers": [
                {
                    "tag": "BALANCER-BACKUP",
                    "selector": ["backup"],
                    "fallbackTag": "direct"
                }
            ],
            "rules": [
                {
                    "network": "tcp,udp",
                    "balancerTag": "BALANCER-BACKUP"
                },
                {
                    "domain": ["domain:direct.example"],
                    "balancerTag": "BALANCER-BACKUP"
                }
            ]
        });
        let base_config = json!({
            "routing": {
                "rules": [
                    {
                        "type": "field",
                        "process": ["opera.exe"],
                        "outboundTag": "proxy"
                    },
                    {
                        "type": "field",
                        "network": "tcp,udp",
                        "outboundTag": "proxy"
                    }
                ]
            }
        });

        merge_template_routing(routing_value.as_object_mut().unwrap(), &base_config);

        let rules = routing_value["rules"].as_array().unwrap();
        assert_eq!(rules[0]["process"][0], "opera.exe");
        assert!(!rules.iter().any(|rule| {
            rule.get("balancerTag") == Some(&json!("BALANCER-BACKUP"))
                && rule.get("network").and_then(serde_json::Value::as_str) == Some("tcp,udp")
        }));
        assert!(rules.iter().any(|rule| {
            rule.get("balancerTag") == Some(&json!("BALANCER-BACKUP"))
                && rule
                    .get("domain")
                    .and_then(serde_json::Value::as_array)
                    .is_some()
        }));
        assert_eq!(rules.last().unwrap()["outboundTag"], "proxy");
    }

    #[test]
    fn subscription_xray_template_urls_keep_public_subscription_query() {
        let urls = subscription_xray_template_urls(
            "https://example.com/api/sub/e37b73d0-ec4d-4b49-b4cd-86685d6703ee?token=abc",
        );

        assert_eq!(
            urls.first().map(String::as_str),
            Some("https://example.com/api/sub/e37b73d0-ec4d-4b49-b4cd-86685d6703ee/happ?token=abc")
        );
        assert!(urls.iter().any(|url| url
            == "https://example.com/api/sub/e37b73d0-ec4d-4b49-b4cd-86685d6703ee/xray-json?token=abc"));
    }

    #[test]
    fn subscription_xray_template_urls_support_short_public_paths() {
        let urls = subscription_xray_template_urls("https://example.com/short-id?token=abc");

        assert!(urls
            .iter()
            .position(|url| url == "https://example.com/short-id/happ?token=abc")
            .is_some());
        assert!(urls
            .iter()
            .any(|url| url == "https://example.com/short-id/xray-json?token=abc"));
        assert!(urls
            .iter()
            .any(|url| url == "https://example.com/short-id/json?token=abc"));
    }

    #[test]
    fn runtime_config_uses_single_cached_template_as_default() {
        let server = Server {
            id: "server-1".into(),
            name: "Server 1".into(),
            server_description: None,
            host_uuid: None,
            xray_json_template_uuid: None,
            protocol: nimbo_subscription::Protocol::Vless(nimbo_subscription::VlessConfig {
                address: "example.com".into(),
                port: 443,
                uuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee".into(),
                flow: None,
                encryption: "none".into(),
                stream: nimbo_subscription::StreamSettings::default(),
            }),
        };
        let mut snapshot = PersistedState::default();
        snapshot.xray_templates.insert(
            "tpl-1".into(),
            json!({
                "routing": {
                    "rules": [
                        {
                            "domain": ["domain:example.org"],
                            "outboundTag": "direct"
                        }
                    ]
                },
                "outbounds": [
                    {
                        "tag": "direct",
                        "protocol": "freedom"
                    },
                    {
                        "tag": "block",
                        "protocol": "blackhole"
                    }
                ]
            }),
        );

        let config = build_runtime_xray_config(&server, &snapshot, ProxyPorts::default()).unwrap();
        let rules = config["routing"]["rules"].as_array().unwrap();

        assert!(rules.iter().any(|rule| {
            rule.get("domain")
                .and_then(serde_json::Value::as_array)
                .map(|domains| domains.iter().any(|domain| domain == "domain:example.org"))
                .unwrap_or(false)
        }));
    }

    #[test]
    fn runtime_config_preserves_remnawave_proxy_pool_and_removes_balancer_fallback() {
        let server = Server {
            id: "server-1".into(),
            name: "Server 1".into(),
            server_description: None,
            host_uuid: None,
            xray_json_template_uuid: None,
            protocol: nimbo_subscription::Protocol::Vless(nimbo_subscription::VlessConfig {
                address: "example.com".into(),
                port: 443,
                uuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee".into(),
                flow: None,
                encryption: "none".into(),
                stream: nimbo_subscription::StreamSettings::default(),
            }),
        };
        let mut snapshot = PersistedState::default();
        snapshot.xray_templates.insert(
            "default".into(),
            json!({
                "routing": {
                    "balancers": [
                        {
                            "tag": "BALANCER",
                            "selector": ["proxy", "proxy-2"]
                        }
                    ],
                    "rules": [
                        {
                            "domain": ["domain:example.org"],
                            "outboundTag": "direct"
                        },
                        {
                            "network": "tcp,udp",
                            "balancerTag": "BALANCER"
                        }
                    ]
                },
                "outbounds": [
                    {
                        "tag": "proxy",
                        "protocol": "vless"
                    },
                    {
                        "tag": "proxy-2",
                        "protocol": "vless"
                    }
                ]
            }),
        );

        let config = build_runtime_xray_config(&server, &snapshot, ProxyPorts::default()).unwrap();
        let outbounds = config["outbounds"].as_array().unwrap();
        let rules = config["routing"]["rules"].as_array().unwrap();

        assert!(outbounds.iter().any(|outbound| outbound["tag"] == "proxy"));
        assert!(outbounds
            .iter()
            .any(|outbound| outbound["tag"] == "proxy-2"));
        assert!(config["routing"].get("balancers").is_some());
        assert!(rules.iter().any(|rule| {
            rule.get("domain")
                .and_then(serde_json::Value::as_array)
                .map(|domains| domains.iter().any(|domain| domain == "domain:example.org"))
                .unwrap_or(false)
        }));
        assert!(!rules.iter().any(|rule| {
            rule.get("balancerTag") == Some(&json!("BALANCER"))
                && rule.get("network").and_then(serde_json::Value::as_str) == Some("tcp,udp")
        }));
        assert_eq!(rules.last().unwrap()["outboundTag"], "proxy");
    }

    #[test]
    fn dns_template_query_strategy_is_normalized_for_dns_section() {
        let mut dns = json!({
            "tag": "dns_out",
            "queryStrategy": "IPIfNonMatch",
            "servers": [{ "address": "https://dns.google/dns-query" }]
        });

        normalize_xray_dns_config(&mut dns);

        assert_eq!(dns["queryStrategy"], "UseIP");
    }

    #[test]
    fn fake_dns_ipv4s_are_not_used_as_real_bypass_targets() {
        let mut ips = Vec::new();

        push_real_ipv4(&mut ips, Ipv4Addr::new(198, 18, 0, 20));
        push_real_ipv4(&mut ips, Ipv4Addr::new(198, 19, 255, 1));
        push_real_ipv4(&mut ips, Ipv4Addr::new(203, 0, 113, 10));

        assert_eq!(ips, vec!["203.0.113.10"]);
    }

    #[test]
    fn freedom_outbounds_from_templates_use_xray_dns() {
        let mut config = json!({
            "outbounds": [
                {
                    "tag": "direct",
                    "protocol": "freedom"
                },
                {
                    "tag": "proxy",
                    "protocol": "vless"
                }
            ]
        });

        normalize_xray_outbounds(config.as_object_mut().unwrap());

        assert_eq!(
            config["outbounds"][0]["settings"]["domainStrategy"],
            "UseIP"
        );
        assert!(config["outbounds"][1].get("settings").is_none());
    }

    #[test]
    fn xray_template_cache_keys_full_config_by_matching_server() {
        let server = Server {
            id: "known-server".into(),
            name: "Server 1".into(),
            server_description: None,
            host_uuid: None,
            xray_json_template_uuid: None,
            protocol: nimbo_subscription::Protocol::Vless(nimbo_subscription::VlessConfig {
                address: "edge.example.com".into(),
                port: 443,
                uuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee".into(),
                flow: None,
                encryption: "none".into(),
                stream: nimbo_subscription::StreamSettings::default(),
            }),
        };
        let body = json!({
            "remarks": "Server 1",
            "routing": {
                "rules": [
                    {
                        "domain": ["domain:from-remnawave.example"],
                        "outboundTag": "direct"
                    }
                ]
            },
            "outbounds": [
                {
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [
                            {
                                "address": "edge.example.com",
                                "port": 443,
                                "users": [
                                    {
                                        "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                                        "encryption": "none"
                                    }
                                ]
                            }
                        ]
                    },
                    "streamSettings": {
                        "network": "tcp"
                    }
                },
                {
                    "tag": "direct",
                    "protocol": "freedom"
                }
            ]
        })
        .to_string();

        let templates =
            extract_xray_templates_with_server_keys(&body, std::slice::from_ref(&server));
        let mut snapshot = PersistedState::default();
        snapshot.xray_templates.insert(
            server_xray_template_key(&server.id),
            templates[&server_xray_template_key(&server.id)].clone(),
        );

        let config = build_runtime_xray_config(&server, &snapshot, ProxyPorts::default()).unwrap();
        let rules = config["routing"]["rules"].as_array().unwrap();

        assert!(rules.iter().any(|rule| {
            rule.get("domain")
                .and_then(serde_json::Value::as_array)
                .map(|domains| {
                    domains
                        .iter()
                        .any(|domain| domain == "domain:from-remnawave.example")
                })
                .unwrap_or(false)
        }));
    }

    #[test]
    fn runtime_config_uses_server_template_uuid() {
        let server_a = Server {
            id: "server-a".into(),
            name: "Server A".into(),
            server_description: None,
            host_uuid: None,
            xray_json_template_uuid: Some("tpl-a".into()),
            protocol: nimbo_subscription::Protocol::Vless(nimbo_subscription::VlessConfig {
                address: "a.example.com".into(),
                port: 443,
                uuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee".into(),
                flow: None,
                encryption: "none".into(),
                stream: nimbo_subscription::StreamSettings::default(),
            }),
        };
        let server_b = Server {
            id: "server-b".into(),
            name: "Server B".into(),
            server_description: None,
            host_uuid: None,
            xray_json_template_uuid: Some("tpl-b".into()),
            protocol: nimbo_subscription::Protocol::Vless(nimbo_subscription::VlessConfig {
                address: "b.example.com".into(),
                port: 443,
                uuid: "bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee".into(),
                flow: None,
                encryption: "none".into(),
                stream: nimbo_subscription::StreamSettings::default(),
            }),
        };
        let subscription_url = "https://example.com/sub";
        let mut snapshot = PersistedState::default();
        snapshot.subscriptions.push(Subscription {
            url: subscription_url.into(),
            name: None,
            meta: Default::default(),
            servers: vec![server_a, server_b.clone()],
            info: None,
            fetched_at: 0,
        });
        snapshot.xray_templates.insert(
            namespaced_xray_template_key(subscription_url, "tpl-a"),
            json!({
                "routing": {
                    "rules": [
                        {
                            "domain": ["domain:a.example"],
                            "outboundTag": "direct"
                        }
                    ]
                },
                "outbounds": [
                    {
                        "tag": "proxy",
                        "protocol": "freedom"
                    }
                ]
            }),
        );
        snapshot.xray_templates.insert(
            namespaced_xray_template_key(subscription_url, "tpl-b"),
            json!({
                "routing": {
                    "rules": [
                        {
                            "domain": ["domain:b.example"],
                            "outboundTag": "direct"
                        }
                    ]
                },
                "outbounds": [
                    {
                        "tag": "proxy",
                        "protocol": "freedom"
                    }
                ]
            }),
        );

        let config =
            build_runtime_xray_config(&server_b, &snapshot, ProxyPorts::default()).unwrap();
        let rules = config["routing"]["rules"].as_array().unwrap();

        assert!(rules.iter().any(|rule| {
            rule.get("domain")
                .and_then(serde_json::Value::as_array)
                .map(|domains| domains.iter().any(|domain| domain == "domain:b.example"))
                .unwrap_or(false)
        }));
        assert!(!rules.iter().any(|rule| {
            rule.get("domain")
                .and_then(serde_json::Value::as_array)
                .map(|domains| domains.iter().any(|domain| domain == "domain:a.example"))
                .unwrap_or(false)
        }));
    }

    #[test]
    fn log_parser_handles_tracing_format() {
        let entry = parse_log_line(
            "nimbo",
            "2026-06-12T14:15:16.123456Z  WARN nimbo_ui::commands: reconnect failed",
        )
        .unwrap();

        assert_eq!(entry.timestamp.as_deref(), Some("2026-06-12 14:15:16"));
        assert_eq!(entry.level, "warn");
        assert_eq!(entry.message, "nimbo_ui::commands: reconnect failed");
    }

    #[test]
    fn warning_with_error_word_stays_warning() {
        let entry = parse_log_line(
            "xray",
            "2026/06/12 14:15:16.123 [Warning] observatory: error ping endpoint",
        )
        .unwrap();

        assert_eq!(entry.level, "warn");
        assert_eq!(entry.timestamp.as_deref(), Some("2026/06/12 14:15:16"));
    }
}
