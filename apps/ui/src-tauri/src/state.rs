use std::collections::HashMap;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Child;
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use nimbo_subscription::{dedupe_subscription_servers, Subscription};

const STORAGE_FILE: &str = "subscriptions.json";

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct PersistedState {
    #[serde(default)]
    pub subscriptions: Vec<Subscription>,
    #[serde(default)]
    pub active_server_id: Option<String>,
    #[serde(default)]
    pub active_subscription_url: Option<String>,
    #[serde(default)]
    pub user_agent_override: Option<String>,
    #[serde(default)]
    pub app_proxy_rules: Vec<AppProxyRule>,
    #[serde(default)]
    pub connected: bool,
    #[serde(default)]
    pub connection_mode: ConnectionMode,
    #[serde(default = "default_socks_username")]
    pub socks_username: String,
    #[serde(default = "default_socks_password")]
    pub socks_password: String,
    #[serde(default)]
    pub require_socks_auth: bool,
    #[serde(default)]
    pub block_socks_udp: bool,
    #[serde(default)]
    pub server_pings: HashMap<String, u64>,
    #[serde(default)]
    pub xray_templates: HashMap<String, serde_json::Value>,
    #[serde(default)]
    pub preferences: AppPreferences,
    #[serde(default = "default_routing_profile_id")]
    pub active_routing_profile: String,
    #[serde(default)]
    pub routing_profiles: Vec<RoutingProfile>,
    #[serde(default)]
    pub deleted_builtin_profiles: Vec<String>,
    #[serde(default)]
    pub traffic_totals: TrafficTotals,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub pending_system_proxy_snapshot: Option<SystemProxySnapshot>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub pending_tun_snapshot: Option<TunRuntimeSnapshot>,
}

impl PersistedState {
    pub fn normalize_runtime_defaults(&mut self) {
        if self.socks_username.trim().is_empty() {
            self.socks_username = default_socks_username();
        }
        if self.socks_password.trim().is_empty() {
            self.socks_password = default_socks_password();
        }
        self.normalize_subscription_servers();
    }

    fn normalize_subscription_servers(&mut self) {
        let mut aliases = HashMap::new();
        for subscription in &mut self.subscriptions {
            aliases.extend(dedupe_subscription_servers(&mut subscription.servers));
        }

        if aliases.is_empty() {
            return;
        }

        if let Some(active_server_id) = self.active_server_id.clone() {
            if let Some(replacement) = aliases.get(&active_server_id) {
                self.active_server_id = Some(replacement.clone());
            }
        }

        for (removed_id, kept_id) in aliases {
            let Some(removed_ping) = self.server_pings.remove(&removed_id) else {
                continue;
            };
            self.server_pings
                .entry(kept_id)
                .and_modify(|current| *current = (*current).min(removed_ping))
                .or_insert(removed_ping);
        }
    }
}

fn default_routing_profile_id() -> String {
    "global".into()
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RoutingProfile {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub builtin: bool,
    #[serde(default = "default_domain_strategy")]
    pub domain_strategy: String,
    #[serde(default = "default_rule_order")]
    pub rule_order: String,
    #[serde(default = "default_true")]
    pub global_proxy: bool,
    #[serde(default = "default_true")]
    pub bypass_local_ip: bool,
    #[serde(default)]
    pub fake_dns: bool,
    #[serde(default = "default_remote_dns_ip")]
    pub remote_dns_ip: String,
    #[serde(default = "default_local_dns_ip")]
    pub local_dns_ip: String,
    #[serde(default = "default_remote_dns_url")]
    pub remote_dns_url: String,
    #[serde(default)]
    pub direct_domains: Vec<String>,
    #[serde(default)]
    pub direct_ips: Vec<String>,
    #[serde(default)]
    pub proxy_domains: Vec<String>,
    #[serde(default)]
    pub proxy_ips: Vec<String>,
    #[serde(default)]
    pub block_domains: Vec<String>,
    #[serde(default)]
    pub block_ips: Vec<String>,
    #[serde(default)]
    pub source_url: Option<String>,
    #[serde(default = "default_update_interval_hours")]
    pub update_interval_hours: u32,
    #[serde(default)]
    pub last_updated_at: Option<i64>,
}

fn default_domain_strategy() -> String {
    "AsIs".into()
}

fn default_rule_order() -> String {
    "block-proxy-direct".into()
}

fn default_remote_dns_ip() -> String {
    "1.1.1.1".into()
}

fn default_local_dns_ip() -> String {
    "8.8.8.8".into()
}

fn default_remote_dns_url() -> String {
    "https://1.1.1.1/dns-query".into()
}

fn default_update_interval_hours() -> u32 {
    24
}

fn default_true() -> bool {
    true
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct TrafficTotals {
    #[serde(default)]
    pub all_time_upload: u64,
    #[serde(default)]
    pub all_time_download: u64,
    #[serde(default)]
    pub monthly_upload: u64,
    #[serde(default)]
    pub monthly_download: u64,
    /// "YYYY-MM" — month for which monthly_* counters are valid.
    #[serde(default)]
    pub monthly_period: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct AppPreferences {
    pub launch_at_login: bool,
    pub auto_connect_on_launch: bool,
    pub start_minimized: bool,
    pub minimize_to_tray: bool,
    pub ping_on_launch: bool,
    pub check_updates_on_launch: bool,
    pub provider_theme: bool,
    pub show_subscription_logo: bool,
    #[serde(default = "default_ui_style")]
    pub ui_style: String,
    #[serde(default = "default_interface_panel_brightness")]
    pub interface_panel_brightness: u32,
    #[serde(default)]
    pub interface_transparency: u32,
    #[serde(default = "default_interface_blur")]
    pub interface_blur: u32,
    #[serde(default = "default_interface_rounding")]
    pub interface_rounding: u32,
    pub theme_mode: ThemeMode,
    pub accent_mode: AccentMode,
    pub accent_color: String,
    pub language: Language,
    pub latency_protocol: String,
    pub latency_test_url: String,
    pub latency_timeout_ms: u32,
    pub latency_display_format: String,
    #[serde(default = "default_app_routing_mode")]
    pub app_routing_mode: String,
    #[serde(default = "default_show_speed_chart")]
    pub show_speed_chart: bool,
    #[serde(default)]
    pub show_memory_usage: bool,
    #[serde(default)]
    pub connection_kill_switch: bool,
    #[serde(default = "default_true")]
    pub tunnel_sniffing: bool,
    #[serde(default)]
    pub tunnel_mux_enabled: bool,
    #[serde(default = "default_tunnel_mux_concurrency")]
    pub tunnel_mux_concurrency: u32,
    #[serde(default = "default_tunnel_xudp_concurrency")]
    pub tunnel_xudp_concurrency: i32,
    #[serde(default = "default_tunnel_xudp_udp443")]
    pub tunnel_xudp_udp443: String,
    #[serde(default)]
    pub tunnel_tls_fragmentation: bool,
    #[serde(default = "default_true")]
    pub lan_allow_connections: bool,
    #[serde(default)]
    pub lan_allow_tethering: bool,
    #[serde(default)]
    pub lan_proxy_enabled: bool,
    #[serde(default = "default_lan_tcp_idle_timeout_sec")]
    pub lan_tcp_idle_timeout_sec: u32,
    #[serde(default = "default_lan_max_tcp_connections")]
    pub lan_max_tcp_connections: u32,
    #[serde(default = "default_lan_max_udp_connections")]
    pub lan_max_udp_connections: u32,
    #[serde(default = "default_lan_preferred_ip_family")]
    pub lan_preferred_ip_family: String,
    #[serde(default = "default_true")]
    pub subscriptions_auto_update: bool,
    #[serde(default = "default_subscriptions_update_interval_hours")]
    pub subscriptions_update_interval_hours: u32,
    #[serde(default = "default_true")]
    pub subscriptions_notify_expiration: bool,
    #[serde(default = "default_subscriptions_expiration_threshold_days")]
    pub subscriptions_expiration_threshold_days: u32,
    #[serde(default = "default_true")]
    pub subscriptions_notify_updates: bool,
    #[serde(default)]
    pub subscriptions_update_on_launch: bool,
    #[serde(default)]
    pub subscriptions_ping_after_update: bool,
    #[serde(default = "default_servers_sorting")]
    pub servers_sorting: String,
    #[serde(default = "default_servers_connect_button")]
    pub servers_connect_button: String,
    #[serde(default = "default_servers_ui_scale")]
    pub servers_ui_scale: u32,
    #[serde(default)]
    pub servers_proxy_only_button: bool,
}

fn default_show_speed_chart() -> bool {
    true
}

fn default_ui_style() -> String {
    "nimbo".into()
}

fn default_interface_panel_brightness() -> u32 {
    100
}

fn default_interface_blur() -> u32 {
    25
}

fn default_interface_rounding() -> u32 {
    100
}

fn default_tunnel_mux_concurrency() -> u32 {
    8
}

fn default_tunnel_xudp_concurrency() -> i32 {
    -1
}

fn default_tunnel_xudp_udp443() -> String {
    "reject".into()
}

fn default_app_routing_mode() -> String {
    "direct".into()
}

fn default_lan_tcp_idle_timeout_sec() -> u32 {
    60
}

fn default_lan_max_tcp_connections() -> u32 {
    256
}

fn default_lan_max_udp_connections() -> u32 {
    128
}

fn default_lan_preferred_ip_family() -> String {
    "auto".into()
}

fn default_subscriptions_update_interval_hours() -> u32 {
    6
}

fn default_subscriptions_expiration_threshold_days() -> u32 {
    3
}

fn default_servers_sorting() -> String {
    "provider".into()
}

fn default_servers_connect_button() -> String {
    "classic".into()
}

fn default_servers_ui_scale() -> u32 {
    100
}

impl Default for AppPreferences {
    fn default() -> Self {
        Self {
            launch_at_login: false,
            auto_connect_on_launch: false,
            start_minimized: false,
            minimize_to_tray: true,
            ping_on_launch: true,
            check_updates_on_launch: true,
            provider_theme: true,
            show_subscription_logo: true,
            ui_style: default_ui_style(),
            interface_panel_brightness: default_interface_panel_brightness(),
            interface_transparency: 0,
            interface_blur: default_interface_blur(),
            interface_rounding: default_interface_rounding(),
            theme_mode: ThemeMode::System,
            accent_mode: AccentMode::Preset,
            accent_color: "#75a7ff".into(),
            language: Language::Ru,
            latency_protocol: "tcp_connect".into(),
            latency_test_url: "https://www.gstatic.com/generate_204".into(),
            latency_timeout_ms: 5000,
            latency_display_format: "ms".into(),
            app_routing_mode: default_app_routing_mode(),
            show_speed_chart: true,
            show_memory_usage: false,
            connection_kill_switch: false,
            tunnel_sniffing: true,
            tunnel_mux_enabled: false,
            tunnel_mux_concurrency: default_tunnel_mux_concurrency(),
            tunnel_xudp_concurrency: default_tunnel_xudp_concurrency(),
            tunnel_xudp_udp443: default_tunnel_xudp_udp443(),
            tunnel_tls_fragmentation: false,
            lan_allow_connections: true,
            lan_allow_tethering: false,
            lan_proxy_enabled: false,
            lan_tcp_idle_timeout_sec: default_lan_tcp_idle_timeout_sec(),
            lan_max_tcp_connections: default_lan_max_tcp_connections(),
            lan_max_udp_connections: default_lan_max_udp_connections(),
            lan_preferred_ip_family: default_lan_preferred_ip_family(),
            subscriptions_auto_update: true,
            subscriptions_update_interval_hours: default_subscriptions_update_interval_hours(),
            subscriptions_notify_expiration: true,
            subscriptions_expiration_threshold_days:
                default_subscriptions_expiration_threshold_days(),
            subscriptions_notify_updates: true,
            subscriptions_update_on_launch: false,
            subscriptions_ping_after_update: false,
            servers_sorting: default_servers_sorting(),
            servers_connect_button: default_servers_connect_button(),
            servers_ui_scale: default_servers_ui_scale(),
            servers_proxy_only_button: false,
        }
    }
}

#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ThemeMode {
    #[default]
    System,
    Dark,
    Black,
    Light,
}

#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum AccentMode {
    System,
    #[default]
    Preset,
    Custom,
}

#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum Language {
    #[default]
    Ru,
    En,
    System,
}

impl Language {
    /// Resolves `System` to a concrete language based on the OS UI locale.
    /// Concrete `Ru`/`En` values are returned unchanged.
    pub fn resolved(self) -> Language {
        match self {
            Language::System => detect_system_language(),
            other => other,
        }
    }
}

#[cfg(windows)]
fn detect_system_language() -> Language {
    // LANG_RUSSIAN primary language identifier.
    const LANG_RUSSIAN: u16 = 0x19;
    let langid = unsafe { winapi::um::winnls::GetUserDefaultUILanguage() };
    if (langid & 0x3ff) == LANG_RUSSIAN {
        Language::Ru
    } else {
        Language::En
    }
}

#[cfg(not(windows))]
fn detect_system_language() -> Language {
    Language::En
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct AppProxyRule {
    pub id: String,
    pub name: String,
    pub executable_path: String,
    pub mode: AppProxyMode,
    pub enabled: bool,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum AppProxyMode {
    Proxy,
    Direct,
}

#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ConnectionMode {
    SystemProxy,
    #[default]
    Tun,
    Both,
}

impl ConnectionMode {
    pub fn uses_system_proxy(self) -> bool {
        matches!(self, Self::SystemProxy | Self::Both)
    }

    pub fn uses_tun(self) -> bool {
        matches!(self, Self::Tun | Self::Both)
    }
}

fn default_socks_username() -> String {
    "nimbo".into()
}

fn default_socks_password() -> String {
    let id = uuid::Uuid::new_v4().simple().to_string();
    format!("nmb-{}", &id[..16])
}

#[derive(Default)]
pub struct RuntimeState {
    pub xray: Option<Child>,
    pub tun2socks: Option<Child>,
    pub system_proxy_snapshot: Option<SystemProxySnapshot>,
    pub tun_snapshot: Option<TunRuntimeSnapshot>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct SystemProxySnapshot {
    pub proxy_enable: Option<u32>,
    pub proxy_server: Option<String>,
    pub proxy_override: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct TunRuntimeSnapshot {
    pub bypass_ips: Vec<String>,
    pub gateway: Option<String>,
    pub interface_index: Option<u32>,
}

pub struct AppState {
    inner: Mutex<PersistedState>,
    runtime: Mutex<RuntimeState>,
    persist_lock: Mutex<()>,
    storage_path: PathBuf,
}

impl AppState {
    pub fn load() -> anyhow::Result<Self> {
        Self::load_from_path(storage_path()?)
    }

    fn load_from_path(storage_path: PathBuf) -> anyhow::Result<Self> {
        let mut inner = if storage_path.exists() {
            let bytes = std::fs::read(&storage_path)?;
            let had_update_launch_preference = serde_json::from_slice::<serde_json::Value>(&bytes)
                .ok()
                .and_then(|value| value.get("preferences").cloned())
                .and_then(|preferences| preferences.get("check_updates_on_launch").cloned())
                .is_some();
            match serde_json::from_slice::<PersistedState>(&bytes) {
                Ok(mut s) => {
                    if !had_update_launch_preference {
                        s.preferences.check_updates_on_launch = true;
                    }
                    s
                }
                Err(e) => {
                    match backup_corrupted_state(&storage_path) {
                        Ok(backup) => tracing::warn!(
                            ?e,
                            backup = %backup.display(),
                            "subscriptions.json corrupted, backup created and defaults restored"
                        ),
                        Err(backup_error) => tracing::error!(
                            ?e,
                            ?backup_error,
                            "subscriptions.json corrupted and backup failed"
                        ),
                    }
                    PersistedState::default()
                }
            }
        } else {
            PersistedState::default()
        };
        inner.normalize_runtime_defaults();
        inner.connected = false;
        let state = Self {
            inner: Mutex::new(inner),
            runtime: Mutex::new(RuntimeState::default()),
            persist_lock: Mutex::new(()),
            storage_path,
        };
        state.persist()?;
        Ok(state)
    }

    pub fn snapshot(&self) -> PersistedState {
        self.inner
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .clone()
    }

    pub fn mutate<F, R>(&self, f: F) -> anyhow::Result<R>
    where
        F: FnOnce(&mut PersistedState) -> R,
    {
        let result = {
            let mut guard = self.inner.lock().unwrap_or_else(|error| error.into_inner());
            f(&mut guard)
        };
        self.persist()?;
        Ok(result)
    }

    pub fn runtime<F, R>(&self, f: F) -> R
    where
        F: FnOnce(&mut RuntimeState) -> R,
    {
        let mut guard = self
            .runtime
            .lock()
            .unwrap_or_else(|error| error.into_inner());
        f(&mut guard)
    }

    fn persist(&self) -> anyhow::Result<()> {
        let _persist_guard = self.persist_lock.lock().unwrap_or_else(|e| e.into_inner());
        if let Some(parent) = self.storage_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let snapshot = self.inner.lock().unwrap_or_else(|e| e.into_inner()).clone();
        let json = serde_json::to_vec_pretty(&snapshot)?;
        let temp_path = self.storage_path.with_extension("json.tmp");
        let mut temp = std::fs::OpenOptions::new()
            .create(true)
            .truncate(true)
            .write(true)
            .open(&temp_path)?;
        temp.write_all(&json)?;
        temp.sync_all()?;
        drop(temp);

        if let Err(error) = replace_file(&temp_path, &self.storage_path) {
            let _ = std::fs::remove_file(&temp_path);
            return Err(error.into());
        }
        Ok(())
    }
}

fn backup_corrupted_state(path: &Path) -> anyhow::Result<PathBuf> {
    let stamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis();
    let backup = path.with_file_name(format!("subscriptions.corrupt-{stamp}.json"));
    std::fs::copy(path, &backup)?;
    Ok(backup)
}

#[cfg(windows)]
fn replace_file(source: &Path, target: &Path) -> std::io::Result<()> {
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Storage::FileSystem::{
        MoveFileExW, MOVEFILE_REPLACE_EXISTING, MOVEFILE_WRITE_THROUGH,
    };

    let source: Vec<u16> = source.as_os_str().encode_wide().chain(Some(0)).collect();
    let target: Vec<u16> = target.as_os_str().encode_wide().chain(Some(0)).collect();
    let result = unsafe {
        MoveFileExW(
            source.as_ptr(),
            target.as_ptr(),
            MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH,
        )
    };
    if result == 0 {
        Err(std::io::Error::last_os_error())
    } else {
        Ok(())
    }
}

#[cfg(not(windows))]
fn replace_file(source: &Path, target: &Path) -> std::io::Result<()> {
    std::fs::rename(source, target)
}

fn storage_path() -> anyhow::Result<PathBuf> {
    let base = dirs::data_dir().ok_or_else(|| anyhow::anyhow!("APPDATA not available"))?;
    Ok(base.join("Nimbo").join(STORAGE_FILE))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn corrupted_state_is_backed_up_and_replaced_with_valid_json() {
        let dir = std::env::temp_dir().join(format!("nimbo-state-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join(STORAGE_FILE);
        std::fs::write(&path, b"{ definitely not json").unwrap();

        let state = AppState::load_from_path(path.clone()).unwrap();
        assert!(state.snapshot().subscriptions.is_empty());
        let saved: PersistedState = serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
        assert!(saved.subscriptions.is_empty());
        assert!(std::fs::read_dir(&dir)
            .unwrap()
            .filter_map(Result::ok)
            .any(|entry| entry
                .file_name()
                .to_string_lossy()
                .starts_with("subscriptions.corrupt-")));
        assert!(!path.with_extension("json.tmp").exists());

        std::fs::remove_dir_all(dir).unwrap();
    }
}
