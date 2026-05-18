use std::collections::HashMap;
use std::path::PathBuf;
use std::process::Child;
use std::sync::Mutex;

use serde::{Deserialize, Serialize};

use nimbo_subscription::Subscription;

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
    pub theme_mode: ThemeMode,
    pub accent_mode: AccentMode,
    pub accent_color: String,
    pub language: Language,
    pub latency_protocol: String,
    pub latency_test_url: String,
    pub latency_timeout_ms: u32,
    pub latency_display_format: String,
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
            theme_mode: ThemeMode::System,
            accent_mode: AccentMode::Preset,
            accent_color: "#7c5dfa".into(),
            language: Language::Ru,
            latency_protocol: "tcp_connect".into(),
            latency_test_url: "https://www.gstatic.com/generate_204".into(),
            latency_timeout_ms: 5000,
            latency_display_format: "ms".into(),
        }
    }
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ThemeMode {
    System,
    Dark,
    Light,
}

impl Default for ThemeMode {
    fn default() -> Self {
        Self::System
    }
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum AccentMode {
    System,
    Preset,
    Custom,
}

impl Default for AccentMode {
    fn default() -> Self {
        Self::Preset
    }
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum Language {
    Ru,
    En,
}

impl Default for Language {
    fn default() -> Self {
        Self::Ru
    }
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

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ConnectionMode {
    SystemProxy,
    Tun,
}

impl Default for ConnectionMode {
    fn default() -> Self {
        Self::Tun
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

#[derive(Debug, Clone)]
pub struct SystemProxySnapshot {
    pub proxy_enable: Option<u32>,
    pub proxy_server: Option<String>,
    pub proxy_override: Option<String>,
}

#[derive(Debug, Clone)]
pub struct TunRuntimeSnapshot {
    pub bypass_ips: Vec<String>,
    pub gateway: Option<String>,
    pub interface_index: Option<u32>,
}

pub struct AppState {
    inner: Mutex<PersistedState>,
    runtime: Mutex<RuntimeState>,
    storage_path: PathBuf,
}

impl AppState {
    pub fn load() -> anyhow::Result<Self> {
        let storage_path = storage_path()?;
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
                    tracing::warn!(?e, "subscriptions.json corrupted, starting fresh");
                    PersistedState::default()
                }
            }
        } else {
            PersistedState::default()
        };
        inner.connected = false;
        let state = Self {
            inner: Mutex::new(inner),
            runtime: Mutex::new(RuntimeState::default()),
            storage_path,
        };
        state.persist()?;
        Ok(state)
    }

    pub fn snapshot(&self) -> PersistedState {
        self.inner.lock().expect("state poisoned").clone()
    }

    pub fn mutate<F, R>(&self, f: F) -> anyhow::Result<R>
    where
        F: FnOnce(&mut PersistedState) -> R,
    {
        let result = {
            let mut guard = self.inner.lock().expect("state poisoned");
            f(&mut guard)
        };
        self.persist()?;
        Ok(result)
    }

    pub fn runtime<F, R>(&self, f: F) -> R
    where
        F: FnOnce(&mut RuntimeState) -> R,
    {
        let mut guard = self.runtime.lock().expect("runtime state poisoned");
        f(&mut guard)
    }

    fn persist(&self) -> anyhow::Result<()> {
        if let Some(parent) = self.storage_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let snapshot = self.inner.lock().expect("state poisoned").clone();
        let json = serde_json::to_vec_pretty(&snapshot)?;
        std::fs::write(&self.storage_path, json)?;
        Ok(())
    }
}

fn storage_path() -> anyhow::Result<PathBuf> {
    let base = dirs::data_dir().ok_or_else(|| anyhow::anyhow!("APPDATA not available"))?;
    Ok(base.join("Nimbo").join(STORAGE_FILE))
}
