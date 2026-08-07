use std::collections::{HashMap, HashSet};
use std::net::{Ipv4Addr, SocketAddr, UdpSocket};
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use nimbo_subscription::{Subscription, SubscriptionMeta};
use ring::aead::{self, Aad, LessSafeKey, Nonce, UnboundKey};
use ring::digest::{digest, SHA256};
use ring::rand::{SecureRandom, SystemRandom};
use serde::{Deserialize, Serialize};
use tauri::{Manager, State};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};
use tokio::time::{timeout, Duration};
use uuid::Uuid;

use crate::state::{AccentMode, AppState, Language, PersistedState, ThemeMode, UpdateChannel};

const SYNC_SCHEMA: &str = "nimbo-cross-sync-v1";
const AAD_PREFIX: &str = "nimbo-sync-v1:";
const SESSION_LIFETIME_MS: u64 = 60_000;
const MAX_FRAME_BYTES: usize = 2 * 1024 * 1024;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum SyncDirection {
    DesktopToAndroid,
    AndroidToDesktop,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub struct SyncCategories {
    #[serde(default = "default_true")]
    pub subscriptions: bool,
    #[serde(default = "default_true")]
    pub appearance: bool,
    #[serde(default = "default_true")]
    pub connection: bool,
    #[serde(default = "default_true")]
    pub automation: bool,
}

impl SyncCategories {
    fn intersect(self, other: Self) -> Self {
        Self {
            subscriptions: self.subscriptions && other.subscriptions,
            appearance: self.appearance && other.appearance,
            connection: self.connection && other.connection,
            automation: self.automation && other.automation,
        }
    }
}

impl Default for SyncCategories {
    fn default() -> Self {
        Self {
            subscriptions: true,
            appearance: true,
            connection: true,
            automation: true,
        }
    }
}

fn default_true() -> bool {
    true
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncSubscription {
    pub url: String,
    pub name: Option<String>,
    #[serde(default)]
    pub order: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct SyncDeviceInfo {
    pub name: String,
    pub platform: String,
    pub os_name: String,
    pub os_version: Option<String>,
    pub app_version: Option<String>,
    pub architecture: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncAppearance {
    pub theme_mode: String,
    pub ui_style: String,
    pub accent_color: String,
    pub panel_brightness: u32,
    pub transparency: u32,
    pub blur: u32,
    pub rounding: u32,
    pub provider_theme: bool,
    pub show_subscription_logo: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncConnection {
    pub kill_switch: bool,
    pub tls_fragmentation: bool,
    pub show_speed_chart: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncAutomation {
    pub language: String,
    pub ping_on_launch: bool,
    pub update_channel: String,
    pub update_wifi_only: bool,
    pub subscriptions_auto_update: bool,
    pub subscriptions_update_interval_hours: u32,
    pub subscriptions_update_on_launch: bool,
    pub subscriptions_ping_after_update: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncBundle {
    pub schema: String,
    pub platform: String,
    pub device_name: String,
    pub created_at_ms: u64,
    #[serde(default)]
    pub device_info: Option<SyncDeviceInfo>,
    #[serde(default)]
    pub subscriptions: Vec<SyncSubscription>,
    pub appearance: Option<SyncAppearance>,
    pub connection: Option<SyncConnection>,
    pub automation: Option<SyncAutomation>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct SyncInventory {
    pub subscriptions: usize,
    pub has_appearance: bool,
    pub has_connection: bool,
    pub has_automation: bool,
}

impl SyncBundle {
    fn from_state(state: &PersistedState) -> Self {
        let prefs = &state.preferences;
        let device_name = std::env::var("COMPUTERNAME")
            .or_else(|_| std::env::var("HOSTNAME"))
            .unwrap_or_else(|_| "Nimbo Desktop".into());
        let (os_name, os_version) = desktop_os_details();
        Self {
            schema: SYNC_SCHEMA.into(),
            platform: "desktop".into(),
            device_name,
            created_at_ms: now_ms(),
            device_info: Some(SyncDeviceInfo {
                name: std::env::var("COMPUTERNAME")
                    .or_else(|_| std::env::var("HOSTNAME"))
                    .unwrap_or_else(|_| "Nimbo Desktop".into()),
                platform: "desktop".into(),
                os_name,
                os_version,
                app_version: Some(env!("CARGO_PKG_VERSION").into()),
                architecture: Some(std::env::consts::ARCH.into()),
            }),
            subscriptions: state
                .subscriptions
                .iter()
                .enumerate()
                .filter(|(_, item)| !item.url.trim().is_empty())
                .map(|(index, item)| SyncSubscription {
                    url: item.url.trim().to_string(),
                    name: item.name.clone(),
                    order: index as u32,
                })
                .collect(),
            appearance: Some(SyncAppearance {
                theme_mode: match prefs.theme_mode {
                    ThemeMode::System => "system",
                    ThemeMode::Dark => "dark",
                    ThemeMode::Black => "black",
                    ThemeMode::Light => "light",
                }
                .into(),
                ui_style: prefs.ui_style.clone(),
                accent_color: if prefs.accent_mode == AccentMode::Custom {
                    prefs.accent_color.clone()
                } else {
                    String::new()
                },
                panel_brightness: prefs.interface_panel_brightness,
                transparency: prefs.interface_transparency,
                blur: prefs.interface_blur,
                rounding: prefs.interface_rounding,
                provider_theme: prefs.provider_theme,
                show_subscription_logo: prefs.show_subscription_logo,
            }),
            connection: Some(SyncConnection {
                kill_switch: prefs.connection_kill_switch,
                tls_fragmentation: prefs.tunnel_tls_fragmentation,
                show_speed_chart: prefs.show_speed_chart,
            }),
            automation: Some(SyncAutomation {
                language: match prefs.language {
                    Language::Ru => "ru",
                    Language::En => "en",
                    Language::System => "system",
                }
                .into(),
                ping_on_launch: prefs.ping_on_launch,
                update_channel: match prefs.update_channel {
                    UpdateChannel::Stable => "stable",
                    UpdateChannel::Beta => "beta",
                }
                .into(),
                update_wifi_only: prefs.update_wifi_only,
                subscriptions_auto_update: prefs.subscriptions_auto_update,
                subscriptions_update_interval_hours: prefs.subscriptions_update_interval_hours,
                subscriptions_update_on_launch: prefs.subscriptions_update_on_launch,
                subscriptions_ping_after_update: prefs.subscriptions_ping_after_update,
            }),
        }
    }

    fn inventory(&self) -> SyncInventory {
        SyncInventory {
            subscriptions: self.subscriptions.len(),
            has_appearance: self.appearance.is_some(),
            has_connection: self.connection.is_some(),
            has_automation: self.automation.is_some(),
        }
    }

    fn filtered(&self, categories: SyncCategories) -> Self {
        Self {
            schema: self.schema.clone(),
            platform: self.platform.clone(),
            device_name: self.device_name.clone(),
            created_at_ms: self.created_at_ms,
            device_info: self.device_info.clone(),
            subscriptions: if categories.subscriptions {
                self.subscriptions.clone()
            } else {
                Vec::new()
            },
            appearance: if categories.appearance {
                self.appearance.clone()
            } else {
                None
            },
            connection: if categories.connection {
                self.connection.clone()
            } else {
                None
            },
            automation: if categories.automation {
                self.automation.clone()
            } else {
                None
            },
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct EncryptedEnvelope {
    v: u8,
    sid: String,
    nonce: String,
    ciphertext: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct WireRequest {
    action: String,
    #[serde(default)]
    device_id: Option<String>,
    device_name: Option<String>,
    bundle: Option<SyncBundle>,
    direction: Option<SyncDirection>,
    categories: Option<SyncCategories>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct WireResponse {
    state: String,
    comparison_code: Option<String>,
    desktop_bundle: Option<SyncBundle>,
    desktop_inventory: Option<SyncInventory>,
    desktop_device_info: Option<SyncDeviceInfo>,
    desktop_subscriptions: Vec<String>,
    expires_at_ms: Option<u64>,
    message: Option<String>,
    #[serde(default)]
    paired: bool,
    #[serde(default)]
    device_id: Option<String>,
    #[serde(default)]
    paired_key: Option<String>,
    #[serde(default)]
    server_port: Option<u16>,
    #[serde(default)]
    applied: bool,
    #[serde(default)]
    applied_categories: Vec<String>,
    #[serde(default)]
    added_subscriptions: Vec<String>,
    #[serde(default)]
    direction: Option<SyncDirection>,
    #[serde(default = "default_true")]
    auto_sync: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PairedDevice {
    pub device_id: String,
    pub name: String,
    #[serde(default)]
    pub platform: String,
    #[serde(default)]
    pub os_name: String,
    #[serde(default)]
    pub app_version: Option<String>,
    pub key: String,
    #[serde(default)]
    pub created_at_ms: u64,
    #[serde(default)]
    pub last_seen_ms: u64,
    #[serde(default)]
    pub categories: SyncCategories,
    #[serde(default)]
    pub last_seen_remote_sig: Option<String>,
    #[serde(default = "default_true")]
    pub auto_sync: bool,
    #[serde(default)]
    pub last_subscription_count: u32,
    #[serde(default)]
    pub last_subscription_names: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct PairedDeviceView {
    pub device_id: String,
    pub name: String,
    pub platform: String,
    pub os_name: String,
    pub app_version: Option<String>,
    pub created_at_ms: u64,
    pub last_seen_ms: u64,
    pub auto_sync: bool,
    pub categories: SyncCategories,
    pub last_subscription_count: u32,
    pub last_subscription_names: Vec<String>,
}

impl From<&PairedDevice> for PairedDeviceView {
    fn from(device: &PairedDevice) -> Self {
        Self {
            device_id: device.device_id.clone(),
            name: device.name.clone(),
            platform: device.platform.clone(),
            os_name: device.os_name.clone(),
            app_version: device.app_version.clone(),
            created_at_ms: device.created_at_ms,
            last_seen_ms: device.last_seen_ms,
            auto_sync: device.auto_sync,
            categories: device.categories,
            last_subscription_count: device.last_subscription_count,
            last_subscription_names: device.last_subscription_names.clone(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct SyncApplyResult {
    pub added_subscriptions: Vec<String>,
    pub applied_categories: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct SyncSessionView {
    pub state: String,
    pub qr_payload: Option<String>,
    pub comparison_code: Option<String>,
    pub expires_at_ms: Option<u64>,
    pub remote_device: Option<String>,
    pub remote_inventory: Option<SyncInventory>,
    pub remote_device_info: Option<SyncDeviceInfo>,
    pub remote_subscriptions: Vec<String>,
    pub pending_direction: Option<SyncDirection>,
    pub pending_categories: Option<SyncCategories>,
    pub result: Option<SyncApplyResult>,
    pub error: Option<String>,
    pub server_port: Option<u16>,
    pub devices: Vec<PairedDeviceView>,
}

#[derive(Debug, Clone)]
struct PendingImport {
    bundle: SyncBundle,
    categories: SyncCategories,
}

#[derive(Debug)]
struct ActiveSession {
    generation: Uuid,
    id: String,
    key: [u8; 32],
    host: Ipv4Addr,
    port: u16,
    expires_at_ms: u64,
    comparison_code: String,
    desktop_bundle: SyncBundle,
    remote_bundle: Option<SyncBundle>,
    remote_device: Option<String>,
    approved: bool,
    approved_categories: SyncCategories,
    approved_direction: Option<SyncDirection>,
    state: String,
    pending_import: Option<PendingImport>,
    pending_direction: Option<SyncDirection>,
    pending_categories: Option<SyncCategories>,
    result: Option<SyncApplyResult>,
    error: Option<String>,
    consumed: bool,
    device_id: Option<String>,
    paired_device_id: Option<String>,
    paired_key: Option<String>,
    last_contact_ms: u64,
}

impl ActiveSession {
    fn view(&self) -> SyncSessionView {
        let now = now_ms();
        let expired = now >= self.expires_at_ms && !self.consumed;
        let phone_was_involved = self.last_contact_ms > 0
            || self.remote_bundle.is_some()
            || self.remote_device.is_some();
        // Телефон закрыл приложение во время сеанса: он перестал опрашивать
        // сервер, а после истечения срока QR уже не восстановить. Показываем
        // состояние "устройство не в сети" вместо призыва создать новый QR.
        let effective_state = if self.consumed {
            self.state.clone()
        } else if expired {
            if phone_was_involved {
                "device_offline".into()
            } else {
                "expired".into()
            }
        } else if self.state == "awaiting_approval"
            && self.last_contact_ms > 0
            && now.saturating_sub(self.last_contact_ms) > 15_000
        {
            // В ожидании подтверждения телефон опрашивает каждые ~0.8 с —
            // долгая тишина значит, что приложение на нём закрыто.
            "device_offline".into()
        } else {
            self.state.clone()
        };
        let qr_payload = if self.consumed || now >= self.expires_at_ms {
            None
        } else {
            Some(format!(
                "nimbo-sync://pair?v=1&host={}&port={}&sid={}&key={}&exp={}&code={}",
                self.host,
                self.port,
                self.id,
                URL_SAFE_NO_PAD.encode(self.key),
                self.expires_at_ms,
                self.comparison_code
            ))
        };
        SyncSessionView {
            state: effective_state.clone(),
            qr_payload,
            comparison_code: if self.consumed || effective_state == "device_offline" {
                None
            } else {
                Some(self.comparison_code.clone())
            },
            expires_at_ms: Some(self.expires_at_ms),
            remote_device: self.remote_device.clone(),
            remote_inventory: self.remote_bundle.as_ref().map(SyncBundle::inventory),
            remote_device_info: self
                .remote_bundle
                .as_ref()
                .and_then(|bundle| bundle.device_info.clone()),
            remote_subscriptions: self
                .remote_bundle
                .as_ref()
                .map(subscription_preview_names)
                .unwrap_or_default(),
            pending_direction: self.pending_direction.clone(),
            pending_categories: self.pending_categories,
            result: self.result.clone(),
            error: self.error.clone(),
            server_port: Some(self.port),
            devices: Vec::new(),
        }
    }
}

#[derive(Clone, Default)]
pub struct CrossSyncManager {
    inner: Arc<Mutex<ManagerInner>>,
}

#[derive(Default)]
struct ManagerInner {
    pairing: Option<ActiveSession>,
    server: Option<ServerRuntime>,
    app_handle: Option<tauri::AppHandle>,
}

struct ServerRuntime {
    port: u16,
    generation: Uuid,
}

const SYNC_PORT_BASE: u16 = 47_920;

impl CrossSyncManager {
    pub fn attach(&self, app_handle: tauri::AppHandle) {
        lock_inner(&self.inner).app_handle = Some(app_handle);
    }

    pub async fn ensure_server(&self) -> Result<u16, String> {
        {
            let guard = lock_inner(&self.inner);
            if let Some(server) = &guard.server {
                return Ok(server.port);
            }
            if guard.app_handle.is_none() {
                return Err("Синхронизация недоступна".into());
            }
        }
        let (listener, port) = bind_sync_listener().await?;
        let generation = Uuid::new_v4();
        {
            let mut guard = lock_inner(&self.inner);
            if let Some(server) = &guard.server {
                return Ok(server.port);
            }
            guard.server = Some(ServerRuntime { port, generation });
        }
        let inner = Arc::clone(&self.inner);
        tokio::spawn(async move {
            serve_forever(listener, inner, generation).await;
        });
        Ok(port)
    }

    async fn start(&self, desktop_bundle: SyncBundle) -> Result<SyncSessionView, String> {
        {
            let guard = lock_inner(&self.inner);
            if let Some(existing) = guard.pairing.as_ref() {
                if !existing.consumed
                    && now_ms() < existing.expires_at_ms
                    && matches!(
                        existing.state.as_str(),
                        "paired" | "export_authorized" | "awaiting_import_confirmation"
                    )
                {
                    return Ok(existing.view());
                }
            }
        }
        let host = local_private_ipv4().ok_or_else(|| {
            "Не удалось определить локальный адрес. Подключите ПК и телефон к одной Wi-Fi сети."
                .to_string()
        })?;
        let port = self.ensure_server().await?;
        let generation = {
            let guard = lock_inner(&self.inner);
            guard
                .server
                .as_ref()
                .map(|server| server.generation)
                .ok_or_else(|| "Сервер синхронизации не запущен".to_string())?
        };
        let random = SystemRandom::new();
        let mut key = [0u8; 32];
        random
            .fill(&mut key)
            .map_err(|_| "Не удалось создать ключ сеанса".to_string())?;
        let id = Uuid::new_v4().to_string();
        let expires_at_ms = now_ms() + SESSION_LIFETIME_MS;
        let comparison_code = comparison_code(&key, &id);
        let session = ActiveSession {
            generation,
            id,
            key,
            host,
            port,
            expires_at_ms,
            comparison_code,
            desktop_bundle,
            remote_bundle: None,
            remote_device: None,
            approved: false,
            approved_categories: SyncCategories::default(),
            approved_direction: None,
            state: "showing_qr".into(),
            pending_import: None,
            pending_direction: None,
            pending_categories: None,
            result: None,
            error: None,
            consumed: false,
            device_id: None,
            paired_device_id: None,
            paired_key: None,
            last_contact_ms: 0,
        };
        let view = session.view();
        lock_inner(&self.inner).pairing = Some(session);
        Ok(view)
    }

    fn status(&self, state: &AppState) -> SyncSessionView {
        let guard = lock_inner(&self.inner);
        let mut view = guard
            .pairing
            .as_ref()
            .map(ActiveSession::view)
            .unwrap_or(SyncSessionView {
                state: "idle".into(),
                qr_payload: None,
                comparison_code: None,
                expires_at_ms: None,
                remote_device: None,
                remote_inventory: None,
                remote_device_info: None,
                remote_subscriptions: Vec::new(),
                pending_direction: None,
                pending_categories: None,
                result: None,
                error: None,
                server_port: guard.server.as_ref().map(|server| server.port),
                devices: Vec::new(),
            });
        view.devices = state
            .snapshot()
            .paired_devices
            .iter()
            .map(PairedDeviceView::from)
            .collect();
        view
    }
}

async fn bind_sync_listener() -> Result<(TcpListener, u16), String> {
    for port in SYNC_PORT_BASE..SYNC_PORT_BASE + 5 {
        if let Ok(listener) = TcpListener::bind((Ipv4Addr::UNSPECIFIED, port)).await {
            return Ok((listener, port));
        }
    }
    let listener = TcpListener::bind((Ipv4Addr::UNSPECIFIED, 0))
        .await
        .map_err(|error| format!("Не удалось открыть локальный порт: {error}"))?;
    let port = listener
        .local_addr()
        .map_err(|error| error.to_string())?
        .port();
    Ok((listener, port))
}

#[tauri::command]
pub async fn cross_sync_start(
    state: State<'_, AppState>,
    manager: State<'_, CrossSyncManager>,
) -> Result<SyncSessionView, String> {
    manager
        .start(SyncBundle::from_state(&state.snapshot()))
        .await
}

#[tauri::command]
pub fn cross_sync_status(
    state: State<'_, AppState>,
    manager: State<'_, CrossSyncManager>,
) -> SyncSessionView {
    manager.status(&state)
}

#[tauri::command]
pub fn cross_sync_list_devices(state: State<'_, AppState>) -> Vec<PairedDeviceView> {
    state
        .snapshot()
        .paired_devices
        .iter()
        .map(PairedDeviceView::from)
        .collect()
}

#[tauri::command]
pub fn cross_sync_set_auto_sync(
    state: State<'_, AppState>,
    device_id: String,
    enabled: bool,
) -> Result<Vec<PairedDeviceView>, String> {
    state
        .mutate(|snapshot| {
            let device = snapshot
                .paired_devices
                .iter_mut()
                .find(|device| device.device_id == device_id)
                .ok_or_else(|| "Устройство не найдено".to_string())?;
            device.auto_sync = enabled;
            Ok::<(), String>(())
        })
        .map_err(|error| error.to_string())??;
    Ok(cross_sync_list_devices(state))
}

#[tauri::command]
pub fn cross_sync_set_device_categories(
    state: State<'_, AppState>,
    device_id: String,
    categories: SyncCategories,
) -> Result<Vec<PairedDeviceView>, String> {
    state
        .mutate(|snapshot| {
            let device = snapshot
                .paired_devices
                .iter_mut()
                .find(|device| device.device_id == device_id)
                .ok_or_else(|| "Устройство не найдено".to_string())?;
            device.categories = categories;
            Ok::<(), String>(())
        })
        .map_err(|error| error.to_string())??;
    Ok(cross_sync_list_devices(state))
}

#[tauri::command]
pub fn cross_sync_remove_device(
    state: State<'_, AppState>,
    device_id: String,
) -> Result<Vec<PairedDeviceView>, String> {
    state
        .mutate(|snapshot| {
            snapshot
                .paired_devices
                .retain(|device| device.device_id != device_id);
        })
        .map_err(|error| error.to_string())?;
    Ok(cross_sync_list_devices(state))
}

#[tauri::command]
pub fn cross_sync_approve(
    manager: State<'_, CrossSyncManager>,
    categories: SyncCategories,
    direction: SyncDirection,
) -> Result<SyncSessionView, String> {
    let mut guard = lock_inner(&manager.inner);
    let session = active_session_mut(&mut guard)?;
    if session.state != "awaiting_approval" || session.remote_bundle.is_none() {
        return Err("Телефон ещё не подключился".into());
    }
    session.approved = true;
    session.approved_categories = categories;
    session.approved_direction = Some(direction.clone());
    session.pending_direction = Some(direction);
    session.state = "paired".into();
    Ok(session.view())
}

#[tauri::command]
pub fn cross_sync_reject(manager: State<'_, CrossSyncManager>) -> Result<SyncSessionView, String> {
    let mut guard = lock_inner(&manager.inner);
    let session = active_session_mut(&mut guard)?;
    session.state = "rejected".into();
    session.error = Some("Сопряжение отклонено на ПК".into());
    // Leave the encrypted status endpoint alive until expiry so the phone can
    // observe a rejection made from the desktop UI.
    Ok(session.view())
}

#[tauri::command]
pub fn cross_sync_accept_import(
    state: State<'_, AppState>,
    manager: State<'_, CrossSyncManager>,
) -> Result<SyncSessionView, String> {
    let pending = {
        let mut guard = lock_inner(&manager.inner);
        let session = active_session_mut(&mut guard)?;
        session
            .pending_import
            .take()
            .ok_or_else(|| "Нет ожидающего импорта".to_string())?
    };
    let result = state
        .mutate(|snapshot| apply_bundle(snapshot, &pending.bundle, pending.categories))
        .map_err(|error| error.to_string())?;
    let mut guard = lock_inner(&manager.inner);
    let session = active_session_mut(&mut guard)?;
    session.result = Some(result);
    session.state = "completed".into();
    // Keep the listener alive until the phone observes "completed" and sends a
    // receipt. Otherwise the final status poll races with this command.
    Ok(session.view())
}

#[tauri::command]
pub fn cross_sync_cancel(
    state: State<'_, AppState>,
    manager: State<'_, CrossSyncManager>,
) -> SyncSessionView {
    let mut guard = lock_inner(&manager.inner);
    if let Some(session) = guard.pairing.as_mut() {
        // The page unmounts when the user leaves the screen, but an approved
        // session may still be waiting for the phone to finish the transfer
        // (apply bundle -> receipt) and persist the paired device. Tearing the
        // session down here would silently break that pairing. Only cancel
        // sessions that are still waiting for a desktop decision.
        if !session.consumed
            && matches!(
                session.state.as_str(),
                "showing_qr" | "awaiting_approval"
            )
        {
            session.state = "cancelled".into();
            session.consumed = true;
        }
    }
    drop(guard);
    manager.status(&state)
}

fn active_session_mut(guard: &mut ManagerInner) -> Result<&mut ActiveSession, String> {
    let session = guard
        .pairing
        .as_mut()
        .ok_or_else(|| "Сеанс не создан".to_string())?;
    if session.consumed || now_ms() >= session.expires_at_ms {
        return Err("Сеанс уже завершён или истёк".into());
    }
    Ok(session)
}

async fn serve_forever(
    listener: TcpListener,
    inner: Arc<Mutex<ManagerInner>>,
    generation: Uuid,
) {
    loop {
        match listener.accept().await {
            Ok((stream, peer)) => {
                let inner = Arc::clone(&inner);
                tokio::spawn(async move {
                    if let Err(error) = handle_stream(stream, peer, inner, generation).await {
                        tracing::warn!(?error, "cross-sync request rejected");
                    }
                });
            }
            Err(error) => {
                tracing::warn!(?error, "cross-sync listener failed");
                break;
            }
        }
    }
}

async fn handle_stream(
    mut stream: TcpStream,
    peer: SocketAddr,
    inner: Arc<Mutex<ManagerInner>>,
    generation: Uuid,
) -> Result<(), String> {
    if !is_private_peer(peer.ip()) {
        return Err("non-private peer rejected".into());
    }
    let length = stream.read_u32().await.map_err(|error| error.to_string())? as usize;
    if length == 0 || length > MAX_FRAME_BYTES {
        return Err("invalid frame length".into());
    }
    let mut frame = vec![0u8; length];
    timeout(Duration::from_secs(8), stream.read_exact(&mut frame))
        .await
        .map_err(|_| "read timeout".to_string())?
        .map_err(|error| error.to_string())?;
    let envelope: EncryptedEnvelope =
        serde_json::from_slice(&frame).map_err(|error| error.to_string())?;
    if envelope.v != 1 {
        return Err("unsupported envelope version".into());
    }

    let app_handle = {
        let guard = lock_inner(&inner);
        guard
            .app_handle
            .clone()
            .ok_or_else(|| "sync unavailable".to_string())?
    };
    let app_state = app_handle.state::<AppState>();

    let (key, session_id, response) = if let Some(device_id) = envelope.sid.strip_prefix("resume:") {
        let device = app_state
            .snapshot()
            .paired_devices
            .into_iter()
            .find(|device| device.device_id == device_id)
            .ok_or_else(|| "device_not_paired".to_string())?;
        let key = decode_key(&device.key)?;
        let plaintext = decrypt(&key, &envelope)?;
        let request: WireRequest =
            serde_json::from_slice(&plaintext).map_err(|error| error.to_string())?;
        let response = process_resume_request(&app_state, &device, request)?;
        (key, envelope.sid.clone(), response)
    } else {
        let (key, session_id) = {
            let guard = lock_inner(&inner);
            let session = guard
                .pairing
                .as_ref()
                .ok_or_else(|| "session missing".to_string())?;
            if session.generation != generation
                || session.consumed
                || now_ms() >= session.expires_at_ms
                || envelope.sid != session.id
            {
                return Err("session mismatch or expired".into());
            }
            (session.key, session.id.clone())
        };
        let plaintext = decrypt(&key, &envelope)?;
        let request: WireRequest =
            serde_json::from_slice(&plaintext).map_err(|error| error.to_string())?;
        let response = process_pairing_request(&inner, generation, request, &app_state)?;
        (key, session_id, response)
    };

    let response_json = serde_json::to_vec(&response).map_err(|error| error.to_string())?;
    let encrypted = encrypt(&key, &session_id, &response_json)?;
    let response_frame = serde_json::to_vec(&encrypted).map_err(|error| error.to_string())?;
    if response_frame.len() > MAX_FRAME_BYTES {
        return Err("response too large".into());
    }
    stream
        .write_u32(response_frame.len() as u32)
        .await
        .map_err(|error| error.to_string())?;
    stream
        .write_all(&response_frame)
        .await
        .map_err(|error| error.to_string())?;
    stream.flush().await.map_err(|error| error.to_string())?;
    Ok(())
}

fn process_pairing_request(
    inner: &Arc<Mutex<ManagerInner>>,
    generation: Uuid,
    request: WireRequest,
    app_state: &AppState,
) -> Result<WireResponse, String> {
    let mut guard = lock_inner(inner);
    let server_port = guard.server.as_ref().map(|server| server.port);
    let session = guard
        .pairing
        .as_mut()
        .ok_or_else(|| "session missing".to_string())?;
    if session.generation != generation || session.consumed || now_ms() >= session.expires_at_ms {
        return Err("session expired".into());
    }
    if session.state == "rejected" && request.action != "status" {
        return Err("session rejected".into());
    }
    session.last_contact_ms = now_ms();
    match request.action.as_str() {
        "hello" => {
            if session.state != "showing_qr" {
                return Err("pairing hello is no longer accepted".into());
            }
            let bundle = request
                .bundle
                .ok_or_else(|| "hello bundle missing".to_string())?;
            validate_bundle(&bundle)?;
            session.device_id = request.device_id;
            session.remote_device = request
                .device_name
                .or_else(|| Some(bundle.device_name.clone()));
            session.remote_bundle = Some(bundle);
            session.state = "awaiting_approval".into();
        }
        "status" => {}
        "commit" => {
            if !session.approved || session.state != "paired" {
                return Err("desktop approval required".into());
            }
            let direction = request
                .direction
                .ok_or_else(|| "direction missing".to_string())?;
            if let Some(approved_direction) = session.approved_direction.as_ref() {
                if approved_direction != &direction {
                    return Err(
                        "Направление передачи выбрано на компьютере — синхронизация отменена"
                            .into(),
                    );
                }
            }
            let categories = request
                .categories
                .unwrap_or_default()
                .intersect(session.approved_categories);
            session.pending_direction = Some(direction.clone());
            session.pending_categories = Some(categories);
            if session.paired_device_id.is_none() {
                if let Some(device_id) = request.device_id.clone() {
                    let name = session
                        .remote_device
                        .clone()
                        .unwrap_or_else(|| "Android".into());
                    let info = session
                        .remote_bundle
                        .as_ref()
                        .and_then(|bundle| bundle.device_info.clone());
                    let key = register_paired_device(
                        app_state,
                        &device_id,
                        name,
                        info.as_ref().map(|info| info.platform.clone()).unwrap_or_default(),
                        info.as_ref().map(|info| info.os_name.clone()).unwrap_or_default(),
                        info.and_then(|info| info.app_version),
                        categories,
                    )?;
                    session.paired_device_id = Some(device_id);
                    session.paired_key = Some(key);
                }
            }
            match direction {
                SyncDirection::AndroidToDesktop => {
                    let bundle = request
                        .bundle
                        .or_else(|| session.remote_bundle.clone())
                        .ok_or_else(|| "mobile bundle missing".to_string())?;
                    validate_bundle(&bundle)?;
                    session.pending_import = Some(PendingImport { bundle, categories });
                    session.state = "awaiting_import_confirmation".into();
                }
                SyncDirection::DesktopToAndroid => {
                    session.state = "export_authorized".into();
                }
            }
        }
        "receipt" => {
            if !session.approved
                || !matches!(session.state.as_str(), "export_authorized" | "completed")
            {
                return Err("receipt is not expected in the current state".into());
            }
            session.state = "completed".into();
            session.consumed = true;
            if session.paired_device_id.is_none() {
                if let Some(device_id) = session
                    .device_id
                    .clone()
                    .or_else(|| request.device_id.clone())
                {
                let name = session
                    .remote_device
                    .clone()
                    .unwrap_or_else(|| "Android".into());
                let info = session
                    .remote_bundle
                    .as_ref()
                    .and_then(|bundle| bundle.device_info.clone());
                let key = register_paired_device(
                    app_state,
                    &device_id,
                    name,
                    info.as_ref().map(|info| info.platform.clone()).unwrap_or_default(),
                    info.as_ref().map(|info| info.os_name.clone()).unwrap_or_default(),
                    info.and_then(|info| info.app_version),
                    session.approved_categories,
                )?;
                session.paired_device_id = Some(device_id);
                session.paired_key = Some(key);
                }
            }
        }
        _ => return Err("unknown action".into()),
    }
    Ok(WireResponse {
        state: session.state.clone(),
        comparison_code: Some(session.comparison_code.clone()),
        desktop_bundle: if session.approved {
            Some(session.desktop_bundle.filtered(session.approved_categories))
        } else {
            None
        },
        desktop_inventory: Some(session.desktop_bundle.inventory()),
        desktop_device_info: session.desktop_bundle.device_info.clone(),
        desktop_subscriptions: subscription_preview_names(&session.desktop_bundle),
        expires_at_ms: Some(session.expires_at_ms),
        message: session.error.clone(),
        paired: false,
        device_id: session.paired_device_id.clone(),
        paired_key: session.paired_key.clone(),
        server_port,
        applied: false,
        applied_categories: Vec::new(),
        added_subscriptions: Vec::new(),
        direction: session.approved_direction.clone().or_else(|| session.pending_direction.clone()),
        auto_sync: true,
    })
}

#[derive(Default)]
struct SyncApplyOutcome {
    applied: bool,
    applied_categories: Vec<String>,
    added_subscriptions: Vec<String>,
}

fn process_resume_request(
    app_state: &AppState,
    device: &PairedDevice,
    request: WireRequest,
) -> Result<WireResponse, String> {
    let now = now_ms();
    let device_id = device.device_id.clone();
    let mut outcome = SyncApplyOutcome::default();
    match request.action.as_str() {
        "unpair" => {
            app_state
                .mutate(|state| {
                    state
                        .paired_devices
                        .retain(|paired| paired.device_id != device_id);
                })
                .map_err(|error| error.to_string())?;
            return Ok(WireResponse {
                state: "unpaired".into(),
                comparison_code: None,
                desktop_bundle: None,
                desktop_inventory: None,
                desktop_device_info: None,
                desktop_subscriptions: Vec::new(),
                expires_at_ms: None,
                message: Some("Устройство удалено".into()),
                paired: false,
                device_id: Some(device_id),
                paired_key: None,
                server_port: None,
                applied: false,
                applied_categories: Vec::new(),
                added_subscriptions: Vec::new(),
                direction: None,
                auto_sync: false,
            });
        }
        "hello" => {
            if !device.auto_sync {
                return resume_response(app_state, &device_id, &SyncApplyOutcome::default());
            }
            let bundle = request
                .bundle
                .as_ref()
                .ok_or_else(|| "hello bundle missing".to_string())?;
            validate_bundle(bundle)?;
            let categories = request
                .categories
                .unwrap_or_default()
                .intersect(device.categories);
            outcome = app_state
                .mutate(|state| apply_incoming_bundle(state, &device_id, bundle, categories, now))
                .map_err(|error| error.to_string())??;
        }
        "status" => {
            app_state
                .mutate(|state| {
                    if let Some(paired) = state
                        .paired_devices
                        .iter_mut()
                        .find(|paired| paired.device_id == device_id)
                    {
                        paired.last_seen_ms = now;
                    }
                })
                .map_err(|error| error.to_string())?;
        }
        _ => return Err("unknown action".into()),
    }
    resume_response(app_state, &device_id, &outcome)
}

fn resume_response(
    app_state: &AppState,
    device_id: &str,
    outcome: &SyncApplyOutcome,
) -> Result<WireResponse, String> {
    let snapshot = app_state.snapshot();
    let paired = snapshot
        .paired_devices
        .iter()
        .find(|paired| paired.device_id == device_id)
        .ok_or_else(|| "device_not_paired".to_string())?;
    let desktop_bundle = SyncBundle::from_state(&snapshot).filtered(paired.categories);
    let auto_sync = paired.auto_sync;
    Ok(WireResponse {
        state: "active".into(),
        comparison_code: None,
        desktop_bundle: auto_sync.then(|| desktop_bundle.clone()),
        desktop_inventory: auto_sync.then(|| desktop_bundle.inventory()),
        desktop_device_info: auto_sync.then(|| desktop_bundle.device_info.clone()).flatten(),
        desktop_subscriptions: auto_sync.then(|| subscription_preview_names(&desktop_bundle)).unwrap_or_default(),
        expires_at_ms: None,
        message: None,
        paired: true,
        device_id: Some(paired.device_id.clone()),
        paired_key: None,
        server_port: None,
        applied: outcome.applied,
        applied_categories: outcome.applied_categories.clone(),
        added_subscriptions: outcome.added_subscriptions.clone(),
        direction: None,
        auto_sync,
    })
}

fn apply_incoming_bundle(
    state: &mut PersistedState,
    device_id: &str,
    bundle: &SyncBundle,
    categories: SyncCategories,
    now: u64,
) -> Result<SyncApplyOutcome, String> {
    let Some(paired) = state
        .paired_devices
        .iter_mut()
        .find(|paired| paired.device_id == device_id)
    else {
        return Err("device_not_paired".into());
    };
    paired.last_seen_ms = now;
    paired.last_subscription_count = bundle.subscriptions.len() as u32;
    paired.last_subscription_names = subscription_preview_names(bundle);
    let signature = bundle_signature(&bundle.filtered(categories));
    if paired.last_seen_remote_sig.as_deref() == Some(&signature) {
        return Ok(SyncApplyOutcome::default());
    }
    let result = apply_bundle(state, bundle, categories);
    if let Some(paired) = state
        .paired_devices
        .iter_mut()
        .find(|paired| paired.device_id == device_id)
    {
        paired.last_seen_remote_sig = Some(signature);
    }
    Ok(SyncApplyOutcome {
        applied: true,
        applied_categories: result.applied_categories,
        added_subscriptions: result.added_subscriptions,
    })
}

fn register_paired_device(
    app_state: &AppState,
    device_id: &str,
    name: String,
    platform: String,
    os_name: String,
    app_version: Option<String>,
    categories: SyncCategories,
) -> Result<String, String> {
    let random = SystemRandom::new();
    let mut key = [0u8; 32];
    random
        .fill(&mut key)
        .map_err(|_| "Не удалось создать ключ устройства".to_string())?;
    let key_b64 = URL_SAFE_NO_PAD.encode(key);
    let now = now_ms();
    app_state
        .mutate(|state| {
            if let Some(existing) = state
                .paired_devices
                .iter_mut()
                .find(|paired| paired.device_id == device_id)
            {
                existing.name = name.clone();
                existing.platform = platform.clone();
                existing.os_name = os_name.clone();
                existing.app_version = app_version.clone();
                existing.categories = categories;
                existing.key = key_b64.clone();
                existing.last_seen_ms = now;
                existing.last_subscription_count = 0;
                existing.last_subscription_names.clear();
            } else {
                state.paired_devices.push(PairedDevice {
                    device_id: device_id.to_string(),
                    name,
                    platform,
                    os_name,
                    app_version,
                    key: key_b64.clone(),
                    created_at_ms: now,
                    last_seen_ms: now,
                    categories,
                    last_seen_remote_sig: None,
                    auto_sync: true,
                    last_subscription_count: 0,
                    last_subscription_names: Vec::new(),
                });
            }
        })
        .map_err(|error| error.to_string())?;
    Ok(key_b64)
}

fn decode_key(key_b64: &str) -> Result<[u8; 32], String> {
    let decoded = URL_SAFE_NO_PAD
        .decode(key_b64)
        .map_err(|error| format!("invalid device key: {error}"))?;
    decoded
        .try_into()
        .map_err(|_| "invalid device key length".to_string())
}

fn bundle_signature(bundle: &SyncBundle) -> String {
    let bytes = serde_json::to_vec(bundle).unwrap_or_default();
    URL_SAFE_NO_PAD.encode(digest(&SHA256, &bytes))
}

fn validate_bundle(bundle: &SyncBundle) -> Result<(), String> {
    if bundle.schema != SYNC_SCHEMA {
        return Err("unsupported sync schema".into());
    }
    if bundle.subscriptions.len() > 500 {
        return Err("too many subscriptions".into());
    }
    if bundle
        .subscriptions
        .iter()
        .any(|item| item.url.len() > 8192 || item.name.as_deref().unwrap_or_default().len() > 256)
    {
        return Err("subscription entry too large".into());
    }
    if let Some(info) = &bundle.device_info {
        let values = [
            Some(info.name.as_str()),
            Some(info.platform.as_str()),
            Some(info.os_name.as_str()),
            info.os_version.as_deref(),
            info.app_version.as_deref(),
            info.architecture.as_deref(),
        ];
        if values.into_iter().flatten().any(|value| value.len() > 160) {
            return Err("device metadata is too large".into());
        }
    }
    Ok(())
}

fn apply_bundle(
    state: &mut PersistedState,
    bundle: &SyncBundle,
    categories: SyncCategories,
) -> SyncApplyResult {
    let mut result = SyncApplyResult::default();
    if categories.subscriptions {
        let mut known: HashSet<String> = state
            .subscriptions
            .iter()
            .map(|item| canonical_url(&item.url))
            .collect();
        for incoming in &bundle.subscriptions {
            let url = incoming.url.trim();
            let key = canonical_url(url);
            if url.is_empty() || known.contains(&key) {
                continue;
            }
            state.subscriptions.push(Subscription {
                url: url.to_string(),
                name: incoming.name.clone().filter(|name| !name.trim().is_empty()),
                meta: SubscriptionMeta::default(),
                servers: Vec::new(),
                info: None,
                fetched_at: 0,
            });
            known.insert(key);
            result.added_subscriptions.push(url.to_string());
        }
        if bundle.subscriptions.len() >= 2 {
            let order: HashMap<String, usize> = bundle
                .subscriptions
                .iter()
                .enumerate()
                .map(|(index, item)| (canonical_url(&item.url), index))
                .collect();
            state.subscriptions.sort_by_key(|item| {
                order
                    .get(&canonical_url(&item.url))
                    .copied()
                    .unwrap_or(usize::MAX)
            });
        }
        result.applied_categories.push("subscriptions".into());
    }
    if categories.appearance {
        if let Some(value) = &bundle.appearance {
            state.preferences.theme_mode = match value.theme_mode.as_str() {
                "light" => ThemeMode::Light,
                "dark" => ThemeMode::Dark,
                "black" => ThemeMode::Black,
                _ => ThemeMode::System,
            };
            state.preferences.ui_style = if value.ui_style == "material_you" {
                "material_you".into()
            } else {
                "nimbo".into()
            };
            if !value.accent_color.is_empty() && is_hex_color(&value.accent_color) {
                state.preferences.accent_mode = AccentMode::Custom;
                state.preferences.accent_color = value.accent_color.to_ascii_lowercase();
            }
            state.preferences.interface_panel_brightness = value.panel_brightness.clamp(50, 200);
            state.preferences.interface_transparency = value.transparency.min(100);
            state.preferences.interface_blur = value.blur.min(80);
            state.preferences.interface_rounding = value.rounding.clamp(25, 200);
            state.preferences.provider_theme = value.provider_theme;
            state.preferences.show_subscription_logo = value.show_subscription_logo;
        }
        result.applied_categories.push("appearance".into());
    }
    if categories.connection {
        if let Some(value) = &bundle.connection {
            state.preferences.connection_kill_switch = value.kill_switch;
            state.preferences.tunnel_tls_fragmentation = value.tls_fragmentation;
            state.preferences.show_speed_chart = value.show_speed_chart;
        }
        result.applied_categories.push("connection".into());
    }
    if categories.automation {
        if let Some(value) = &bundle.automation {
            state.preferences.language = match value.language.as_str() {
                "en" => Language::En,
                "system" => Language::System,
                _ => Language::Ru,
            };
            state.preferences.ping_on_launch = value.ping_on_launch;
            state.preferences.update_channel = if value.update_channel == "beta" {
                UpdateChannel::Beta
            } else {
                UpdateChannel::Stable
            };
            state.preferences.update_wifi_only = value.update_wifi_only;
            state.preferences.subscriptions_auto_update = value.subscriptions_auto_update;
            state.preferences.subscriptions_update_interval_hours =
                value.subscriptions_update_interval_hours.clamp(1, 168);
            state.preferences.subscriptions_update_on_launch = value.subscriptions_update_on_launch;
            state.preferences.subscriptions_ping_after_update =
                value.subscriptions_ping_after_update;
        }
        result.applied_categories.push("automation".into());
    }
    result
}

fn encrypt(
    key: &[u8; 32],
    session_id: &str,
    plaintext: &[u8],
) -> Result<EncryptedEnvelope, String> {
    let random = SystemRandom::new();
    let mut nonce_bytes = [0u8; 12];
    random
        .fill(&mut nonce_bytes)
        .map_err(|_| "nonce generation failed".to_string())?;
    let unbound = UnboundKey::new(&aead::AES_256_GCM, key)
        .map_err(|_| "invalid encryption key".to_string())?;
    let key = LessSafeKey::new(unbound);
    let mut in_out = plaintext.to_vec();
    key.seal_in_place_append_tag(
        Nonce::assume_unique_for_key(nonce_bytes),
        Aad::from(format!("{AAD_PREFIX}{session_id}").as_bytes()),
        &mut in_out,
    )
    .map_err(|_| "encryption failed".to_string())?;
    Ok(EncryptedEnvelope {
        v: 1,
        sid: session_id.to_string(),
        nonce: URL_SAFE_NO_PAD.encode(nonce_bytes),
        ciphertext: URL_SAFE_NO_PAD.encode(in_out),
    })
}

fn decrypt(key: &[u8; 32], envelope: &EncryptedEnvelope) -> Result<Vec<u8>, String> {
    let nonce_vec = URL_SAFE_NO_PAD
        .decode(&envelope.nonce)
        .map_err(|_| "invalid nonce encoding".to_string())?;
    let nonce_bytes: [u8; 12] = nonce_vec
        .try_into()
        .map_err(|_| "invalid nonce length".to_string())?;
    let mut in_out = URL_SAFE_NO_PAD
        .decode(&envelope.ciphertext)
        .map_err(|_| "invalid ciphertext encoding".to_string())?;
    let unbound = UnboundKey::new(&aead::AES_256_GCM, key)
        .map_err(|_| "invalid encryption key".to_string())?;
    let key = LessSafeKey::new(unbound);
    let plaintext = key
        .open_in_place(
            Nonce::assume_unique_for_key(nonce_bytes),
            Aad::from(format!("{AAD_PREFIX}{}", envelope.sid).as_bytes()),
            &mut in_out,
        )
        .map_err(|_| "authentication failed".to_string())?;
    Ok(plaintext.to_vec())
}

fn comparison_code(key: &[u8; 32], session_id: &str) -> String {
    let mut material = Vec::with_capacity(key.len() + session_id.len());
    material.extend_from_slice(key);
    material.extend_from_slice(session_id.as_bytes());
    let value = digest(&SHA256, &material);
    let bytes = value.as_ref();
    let number = u32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]) % 1_000_000;
    format!("{number:06}")
}

fn local_private_ipv4() -> Option<Ipv4Addr> {
    #[cfg(windows)]
    if let Some(ip) = physical_lan_ipv4_windows() {
        return Some(ip);
    }

    let socket = UdpSocket::bind((Ipv4Addr::UNSPECIFIED, 0)).ok()?;
    socket.connect(("1.1.1.1", 80)).ok()?;
    let ip = match socket.local_addr().ok()?.ip() {
        std::net::IpAddr::V4(ip) => ip,
        _ => return None,
    };
    is_private_ipv4(ip).then_some(ip)
}

// Selecting the route to an Internet address can return Nimbo's own TUN/VPN
// adapter while the tunnel is active. Prefer an actual Wi-Fi/Ethernet adapter
// so the address embedded in the QR remains reachable from the phone.
#[cfg(windows)]
fn physical_lan_ipv4_windows() -> Option<Ipv4Addr> {
    use windows_sys::Win32::Foundation::{ERROR_BUFFER_OVERFLOW, ERROR_SUCCESS};
    use windows_sys::Win32::NetworkManagement::IpHelper::{
        GetAdaptersAddresses, GAA_FLAG_SKIP_ANYCAST, GAA_FLAG_SKIP_DNS_SERVER,
        GAA_FLAG_SKIP_MULTICAST, IF_TYPE_ETHERNET_CSMACD, IF_TYPE_IEEE80211,
        IP_ADAPTER_ADDRESSES_LH,
    };
    use windows_sys::Win32::NetworkManagement::Ndis::IfOperStatusUp;
    use windows_sys::Win32::Networking::WinSock::{AF_INET, SOCKADDR_IN};

    let flags = GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_DNS_SERVER;
    let mut size = 0u32;
    let initial = unsafe {
        GetAdaptersAddresses(0, flags, std::ptr::null(), std::ptr::null_mut(), &mut size)
    };
    if initial != ERROR_BUFFER_OVERFLOW || size == 0 {
        return None;
    }
    let mut storage = vec![0u64; (size as usize).div_ceil(std::mem::size_of::<u64>())];
    let adapters = storage.as_mut_ptr().cast::<IP_ADAPTER_ADDRESSES_LH>();
    if unsafe { GetAdaptersAddresses(0, flags, std::ptr::null(), adapters, &mut size) }
        != ERROR_SUCCESS
    {
        return None;
    }

    let mut ethernet_fallback = None;
    let mut adapter = adapters;
    while !adapter.is_null() {
        let current = unsafe { &*adapter };
        let is_wifi = current.IfType == IF_TYPE_IEEE80211;
        let is_ethernet = current.IfType == IF_TYPE_ETHERNET_CSMACD;
        if current.OperStatus == IfOperStatusUp && (is_wifi || is_ethernet) {
            let mut unicast = current.FirstUnicastAddress;
            while !unicast.is_null() {
                let socket = unsafe { &(*unicast).Address };
                if !socket.lpSockaddr.is_null()
                    && unsafe { (*socket.lpSockaddr).sa_family } == AF_INET
                {
                    let address = unsafe { &*(socket.lpSockaddr.cast::<SOCKADDR_IN>()) };
                    let ip = Ipv4Addr::from(u32::from_be(unsafe { address.sin_addr.S_un.S_addr }));
                    if is_private_ipv4(ip) {
                        if is_wifi {
                            return Some(ip);
                        }
                        ethernet_fallback = Some(ip);
                    }
                }
                unicast = unsafe { (*unicast).Next };
            }
        }
        adapter = current.Next;
    }
    ethernet_fallback
}

fn is_private_peer(ip: std::net::IpAddr) -> bool {
    match ip {
        std::net::IpAddr::V4(ip) => is_private_ipv4(ip),
        std::net::IpAddr::V6(ip) => {
            let first = ip.segments()[0];
            (first & 0xfe00) == 0xfc00 || (first & 0xffc0) == 0xfe80
        }
    }
}

fn is_private_ipv4(ip: Ipv4Addr) -> bool {
    ip.is_private() || ip.is_link_local()
}

fn canonical_url(raw: &str) -> String {
    let trimmed = raw.trim();
    match url::Url::parse(trimmed) {
        Ok(mut url) => {
            let path = url.path().trim_end_matches('/').to_string();
            url.set_path(&path);
            url.set_fragment(None);
            // Url already normalizes scheme and host. Paths and queries may
            // contain case-sensitive subscription credentials.
            url.to_string().trim_end_matches('/').to_string()
        }
        Err(_) => trimmed.to_ascii_lowercase(),
    }
}

fn is_hex_color(value: &str) -> bool {
    value.len() == 7
        && value.starts_with('#')
        && value.as_bytes()[1..]
            .iter()
            .all(|byte| byte.is_ascii_hexdigit())
}

fn subscription_preview_names(bundle: &SyncBundle) -> Vec<String> {
    bundle
        .subscriptions
        .iter()
        .enumerate()
        .map(|(index, item)| {
            item.name
                .as_deref()
                .map(str::trim)
                .filter(|name| {
                    !name.is_empty() && !name.contains("://") && *name != item.url.trim()
                })
                .map(|name| name.chars().take(80).collect())
                .unwrap_or_else(|| format!("Подписка {}", index + 1))
        })
        .collect()
}

#[cfg(windows)]
fn desktop_os_details() -> (String, Option<String>) {
    use winreg::enums::HKEY_LOCAL_MACHINE;
    use winreg::RegKey;

    let current_version = RegKey::predef(HKEY_LOCAL_MACHINE)
        .open_subkey("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion");
    let Ok(key) = current_version else {
        return ("Windows".into(), None);
    };
    let mut product: String = key
        .get_value("ProductName")
        .unwrap_or_else(|_| "Windows".into());
    let display: String = key.get_value("DisplayVersion").unwrap_or_default();
    let build: String = key.get_value("CurrentBuildNumber").unwrap_or_default();
    if build.parse::<u32>().unwrap_or_default() >= 22_000 {
        product = product.replace("Windows 10", "Windows 11");
    }
    let version = match (display.is_empty(), build.is_empty()) {
        (false, false) => Some(format!("{display} · build {build}")),
        (false, true) => Some(display),
        (true, false) => Some(format!("build {build}")),
        (true, true) => None,
    };
    (product, version)
}

#[cfg(not(windows))]
fn desktop_os_details() -> (String, Option<String>) {
    let name = match std::env::consts::OS {
        "macos" => "macOS",
        "linux" => "Linux",
        other => other,
    };
    (name.into(), None)
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

fn lock_inner(
    inner: &Arc<Mutex<ManagerInner>>,
) -> std::sync::MutexGuard<'_, ManagerInner> {
    inner.lock().unwrap_or_else(|error| error.into_inner())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encrypted_frame_round_trips_and_detects_tampering() {
        let key = [7u8; 32];
        let envelope = encrypt(&key, "session", b"secret subscription").unwrap();
        assert_eq!(decrypt(&key, &envelope).unwrap(), b"secret subscription");

        let mut tampered = envelope.clone();
        let mut ciphertext = URL_SAFE_NO_PAD.decode(&tampered.ciphertext).unwrap();
        ciphertext[0] ^= 1;
        tampered.ciphertext = URL_SAFE_NO_PAD.encode(ciphertext);
        assert!(decrypt(&key, &tampered).is_err());
    }

    #[test]
    fn only_private_addresses_are_accepted() {
        assert!(is_private_ipv4(Ipv4Addr::new(192, 168, 1, 4)));
        assert!(is_private_ipv4(Ipv4Addr::new(10, 0, 0, 8)));
        assert!(!is_private_ipv4(Ipv4Addr::new(8, 8, 8, 8)));
    }

    #[test]
    fn imports_merge_subscription_urls() {
        let mut state = PersistedState::default();
        state.subscriptions.push(Subscription {
            url: "https://example.com/sub/".into(),
            name: Some("Local".into()),
            meta: SubscriptionMeta::default(),
            servers: vec![],
            info: None,
            fetched_at: 0,
        });
        let bundle = SyncBundle {
            schema: SYNC_SCHEMA.into(),
            platform: "android".into(),
            device_name: "Phone".into(),
            created_at_ms: 0,
            device_info: None,
            subscriptions: vec![
                SyncSubscription { order: 0,
                    url: "HTTPS://EXAMPLE.COM/sub".into(),
                    name: Some("Remote".into()),
                },
                SyncSubscription { order: 0,
                    url: "https://second.example/key".into(),
                    name: Some("Second".into()),
                },
            ],
            appearance: None,
            connection: None,
            automation: None,
        };
        let result = apply_bundle(
            &mut state,
            &bundle,
            SyncCategories {
                subscriptions: true,
                appearance: false,
                connection: false,
                automation: false,
            },
        );
        assert_eq!(state.subscriptions.len(), 2);
        assert_eq!(
            result.added_subscriptions,
            vec!["https://second.example/key"]
        );
        assert_eq!(state.subscriptions[0].name.as_deref(), Some("Local"));
    }

    #[test]
    fn resume_hello_applies_bundle_once_then_skips_unchanged() {
        let mut state = PersistedState::default();
        state.paired_devices.push(PairedDevice {
            device_id: "phone-1".into(),
            name: "Phone".into(),
            platform: "android".into(),
            os_name: String::new(),
            app_version: None,
            key: URL_SAFE_NO_PAD.encode([9u8; 32]),
            created_at_ms: 0,
            last_seen_ms: 0,
            categories: SyncCategories::default(),
            last_seen_remote_sig: None,
            auto_sync: true,
            last_subscription_count: 0,
            last_subscription_names: Vec::new(),
        });
        let bundle = SyncBundle {
            schema: SYNC_SCHEMA.into(),
            platform: "android".into(),
            device_name: "Phone".into(),
            created_at_ms: 0,
            device_info: None,
            subscriptions: vec![SyncSubscription { order: 0,
                url: "https://example.com/sub".into(),
                name: Some("Main".into()),
            }],
            appearance: None,
            connection: None,
            automation: None,
        };

        let first = apply_incoming_bundle(&mut state, "phone-1", &bundle, SyncCategories::default(), 100).unwrap();
        assert!(first.applied);
        assert_eq!(first.added_subscriptions, vec!["https://example.com/sub"]);
        assert_eq!(state.subscriptions.len(), 1);
        assert_eq!(state.paired_devices[0].last_seen_ms, 100);

        let second = apply_incoming_bundle(&mut state, "phone-1", &bundle, SyncCategories::default(), 200).unwrap();
        assert!(!second.applied);
        assert!(second.added_subscriptions.is_empty());
        assert_eq!(state.subscriptions.len(), 1);
    }

    #[test]
    fn resume_hello_ignores_disabled_categories() {
        let mut state = PersistedState::default();
        state.paired_devices.push(PairedDevice {
            device_id: "phone-1".into(),
            name: "Phone".into(),
            platform: "android".into(),
            os_name: String::new(),
            app_version: None,
            key: URL_SAFE_NO_PAD.encode([9u8; 32]),
            created_at_ms: 0,
            last_seen_ms: 0,
            categories: SyncCategories {
                subscriptions: false,
                appearance: true,
                connection: true,
                automation: true,
            },
            last_seen_remote_sig: None,
            auto_sync: true,
            last_subscription_count: 0,
            last_subscription_names: Vec::new(),
        });
        let bundle = SyncBundle {
            schema: SYNC_SCHEMA.into(),
            platform: "android".into(),
            device_name: "Phone".into(),
            created_at_ms: 0,
            device_info: None,
            subscriptions: vec![SyncSubscription { order: 0,
                url: "https://example.com/sub".into(),
                name: None,
            }],
            appearance: None,
            connection: None,
            automation: None,
        };

        let outcome = apply_incoming_bundle(
            &mut state,
            "phone-1",
            &bundle,
            SyncCategories {
                subscriptions: false,
                appearance: true,
                connection: true,
                automation: true,
            },
            100,
        )
        .unwrap();
        assert!(outcome.applied);
        assert!(outcome.added_subscriptions.is_empty());
        assert!(state.subscriptions.is_empty());
    }

    #[test]
    fn bundle_signature_tracks_content_changes() {
        let base = SyncBundle {
            schema: SYNC_SCHEMA.into(),
            platform: "android".into(),
            device_name: "Phone".into(),
            created_at_ms: 0,
            device_info: None,
            subscriptions: vec![SyncSubscription { order: 0,
                url: "https://example.com/sub".into(),
                name: None,
            }],
            appearance: None,
            connection: None,
            automation: None,
        };
        let mut changed = base.clone();
        changed.subscriptions[0].name = Some("Renamed".into());
        let categories = SyncCategories::default();
        assert_ne!(
            bundle_signature(&base.filtered(categories)),
            bundle_signature(&changed.filtered(categories))
        );
        assert_eq!(
            bundle_signature(&base.filtered(categories)),
            bundle_signature(&base.filtered(categories))
        );
    }

    #[test]
    fn canonical_url_preserves_case_sensitive_credentials() {
        assert_eq!(
            canonical_url("HTTPS://EXAMPLE.COM/Token/AbC?key=XyZ#fragment"),
            "https://example.com/Token/AbC?key=XyZ"
        );
        assert_ne!(
            canonical_url("https://example.com/Token/AbC?key=XyZ"),
            canonical_url("https://example.com/token/abc?key=xyz")
        );
    }

    #[test]
    fn subscription_preview_never_exposes_secret_urls() {
        let bundle = SyncBundle {
            schema: SYNC_SCHEMA.into(),
            platform: "android".into(),
            device_name: "Phone".into(),
            created_at_ms: 0,
            device_info: None,
            subscriptions: vec![
                SyncSubscription { order: 0,
                    url: "https://provider.example/sub/SecretKey".into(),
                    name: Some("Основная".into()),
                },
                SyncSubscription { order: 0,
                    url: "https://provider.example/sub/SecondSecret".into(),
                    name: None,
                },
            ],
            appearance: None,
            connection: None,
            automation: None,
        };

        let previews = subscription_preview_names(&bundle);
        assert_eq!(previews, vec!["Основная", "Подписка 2"]);
        assert!(previews.iter().all(|value| !value.contains("://")));
    }

    fn test_session() -> ActiveSession {
        ActiveSession {
            generation: Uuid::new_v4(),
            id: "session-1".into(),
            key: [1u8; 32],
            host: Ipv4Addr::LOCALHOST,
            port: 47_920,
            expires_at_ms: now_ms() + 60_000,
            comparison_code: "123456".into(),
            desktop_bundle: SyncBundle {
                schema: SYNC_SCHEMA.into(),
                platform: "desktop".into(),
                device_name: "PC".into(),
                created_at_ms: 0,
                device_info: None,
                subscriptions: vec![],
                appearance: None,
                connection: None,
                automation: None,
            },
            remote_bundle: None,
            remote_device: None,
            approved: false,
            approved_categories: SyncCategories::default(),
            approved_direction: None,
            state: "showing_qr".into(),
            pending_import: None,
            pending_direction: None,
            pending_categories: None,
            result: None,
            error: None,
            consumed: false,
            device_id: None,
            paired_device_id: None,
            paired_key: None,
            last_contact_ms: 0,
        }
    }

    #[test]
    fn expired_session_without_phone_contact_reports_expired() {
        let mut session = test_session();
        session.expires_at_ms = 1;
        let view = session.view();
        assert_eq!(view.state, "expired");
        assert!(view.qr_payload.is_none());
    }

    #[test]
    fn expired_session_with_phone_contact_reports_device_offline() {
        let mut session = test_session();
        session.last_contact_ms = 1_000;
        session.remote_device = Some("Phone".into());
        session.expires_at_ms = 1;
        let view = session.view();
        assert_eq!(view.state, "device_offline");
        assert!(view.qr_payload.is_none());
        assert!(view.comparison_code.is_none());
    }

    #[test]
    fn silent_phone_in_awaiting_approval_reports_device_offline_early() {
        let mut session = test_session();
        session.last_contact_ms = now_ms() - 20_000;
        session.remote_device = Some("Phone".into());
        session.state = "awaiting_approval".into();
        let view = session.view();
        assert_eq!(view.state, "device_offline");
        assert!(view.comparison_code.is_none());
    }

    #[test]
    fn completed_session_stays_completed_after_expiry() {
        let mut session = test_session();
        session.consumed = true;
        session.state = "completed".into();
        session.expires_at_ms = 1;
        let view = session.view();
        assert_eq!(view.state, "completed");
    }
}
