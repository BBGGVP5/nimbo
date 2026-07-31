use std::collections::HashSet;
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
use tauri::State;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};
use tokio::time::{timeout, Duration};
use uuid::Uuid;

use crate::state::{AccentMode, AppState, Language, PersistedState, ThemeMode, UpdateChannel};

const SYNC_SCHEMA: &str = "nimbo-cross-sync-v1";
const AAD_PREFIX: &str = "nimbo-sync-v1:";
const SESSION_LIFETIME_MS: u64 = 75_000;
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
                .filter(|item| !item.url.trim().is_empty())
                .map(|item| SyncSubscription {
                    url: item.url.trim().to_string(),
                    name: item.name.clone(),
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
                accent_color: prefs.accent_color.clone(),
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
    state: String,
    pending_import: Option<PendingImport>,
    pending_direction: Option<SyncDirection>,
    pending_categories: Option<SyncCategories>,
    result: Option<SyncApplyResult>,
    error: Option<String>,
    consumed: bool,
}

impl ActiveSession {
    fn view(&self) -> SyncSessionView {
        let qr_payload = if self.consumed || now_ms() >= self.expires_at_ms {
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
            state: if now_ms() >= self.expires_at_ms && !self.consumed {
                "expired".into()
            } else {
                self.state.clone()
            },
            qr_payload,
            comparison_code: Some(self.comparison_code.clone()),
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
        }
    }
}

#[derive(Default)]
pub struct CrossSyncManager {
    inner: Arc<Mutex<Option<ActiveSession>>>,
}

impl CrossSyncManager {
    async fn start(&self, desktop_bundle: SyncBundle) -> Result<SyncSessionView, String> {
        let host = local_private_ipv4().ok_or_else(|| {
            "Не удалось определить локальный адрес. Подключите ПК и телефон к одной Wi-Fi сети."
                .to_string()
        })?;
        let listener = TcpListener::bind((Ipv4Addr::UNSPECIFIED, 0))
            .await
            .map_err(|error| format!("Не удалось открыть локальный порт: {error}"))?;
        let port = listener
            .local_addr()
            .map_err(|error| error.to_string())?
            .port();
        let random = SystemRandom::new();
        let mut key = [0u8; 32];
        random
            .fill(&mut key)
            .map_err(|_| "Не удалось создать ключ сеанса".to_string())?;
        let generation = Uuid::new_v4();
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
            state: "showing_qr".into(),
            pending_import: None,
            pending_direction: None,
            pending_categories: None,
            result: None,
            error: None,
            consumed: false,
        };
        let view = session.view();
        *lock_session(&self.inner) = Some(session);

        let sessions = Arc::clone(&self.inner);
        tokio::spawn(async move {
            serve_session(listener, sessions, generation, expires_at_ms).await;
        });
        Ok(view)
    }

    fn status(&self) -> SyncSessionView {
        lock_session(&self.inner)
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
            })
    }
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
pub fn cross_sync_status(manager: State<'_, CrossSyncManager>) -> SyncSessionView {
    manager.status()
}

#[tauri::command]
pub fn cross_sync_approve(
    manager: State<'_, CrossSyncManager>,
    categories: SyncCategories,
) -> Result<SyncSessionView, String> {
    let mut guard = lock_session(&manager.inner);
    let session = active_session_mut(&mut guard)?;
    if session.state != "awaiting_approval" || session.remote_bundle.is_none() {
        return Err("Телефон ещё не подключился".into());
    }
    session.approved = true;
    session.approved_categories = categories;
    session.state = "paired".into();
    Ok(session.view())
}

#[tauri::command]
pub fn cross_sync_reject(manager: State<'_, CrossSyncManager>) -> Result<SyncSessionView, String> {
    let mut guard = lock_session(&manager.inner);
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
        let mut guard = lock_session(&manager.inner);
        let session = active_session_mut(&mut guard)?;
        session
            .pending_import
            .take()
            .ok_or_else(|| "Нет ожидающего импорта".to_string())?
    };
    let result = state
        .mutate(|snapshot| apply_bundle(snapshot, &pending.bundle, pending.categories))
        .map_err(|error| error.to_string())?;
    let mut guard = lock_session(&manager.inner);
    let session = active_session_mut(&mut guard)?;
    session.result = Some(result);
    session.state = "completed".into();
    // Keep the listener alive until the phone observes "completed" and sends a
    // receipt. Otherwise the final status poll races with this command.
    Ok(session.view())
}

#[tauri::command]
pub fn cross_sync_cancel(manager: State<'_, CrossSyncManager>) -> SyncSessionView {
    if let Some(session) = lock_session(&manager.inner).as_mut() {
        if session.state != "completed" && !session.consumed {
            session.state = "cancelled".into();
            session.consumed = true;
        }
    }
    manager.status()
}

fn active_session_mut(guard: &mut Option<ActiveSession>) -> Result<&mut ActiveSession, String> {
    let session = guard
        .as_mut()
        .ok_or_else(|| "Сеанс не создан".to_string())?;
    if session.consumed || now_ms() >= session.expires_at_ms {
        return Err("Сеанс уже завершён или истёк".into());
    }
    Ok(session)
}

async fn serve_session(
    listener: TcpListener,
    sessions: Arc<Mutex<Option<ActiveSession>>>,
    generation: Uuid,
    expires_at_ms: u64,
) {
    loop {
        let remaining = expires_at_ms.saturating_sub(now_ms());
        if remaining == 0 || !is_generation_active(&sessions, generation) {
            break;
        }
        match timeout(
            Duration::from_millis(remaining.min(1000)),
            listener.accept(),
        )
        .await
        {
            Ok(Ok((stream, peer))) => {
                let sessions = Arc::clone(&sessions);
                tokio::spawn(async move {
                    if let Err(error) = handle_stream(stream, peer, sessions, generation).await {
                        tracing::warn!(?error, "cross-sync request rejected");
                    }
                });
            }
            Ok(Err(error)) => {
                tracing::warn!(?error, "cross-sync listener failed");
                break;
            }
            Err(_) => {}
        }
    }
}

async fn handle_stream(
    mut stream: TcpStream,
    peer: SocketAddr,
    sessions: Arc<Mutex<Option<ActiveSession>>>,
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
    let (key, session_id) = {
        let guard = lock_session(&sessions);
        let session = guard
            .as_ref()
            .ok_or_else(|| "session missing".to_string())?;
        if session.generation != generation
            || session.consumed
            || now_ms() >= session.expires_at_ms
            || envelope.v != 1
            || envelope.sid != session.id
        {
            return Err("session mismatch or expired".into());
        }
        (session.key, session.id.clone())
    };
    let plaintext = decrypt(&key, &envelope)?;
    let request: WireRequest =
        serde_json::from_slice(&plaintext).map_err(|error| error.to_string())?;
    let response = process_request(&sessions, generation, request)?;
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

fn process_request(
    sessions: &Arc<Mutex<Option<ActiveSession>>>,
    generation: Uuid,
    request: WireRequest,
) -> Result<WireResponse, String> {
    let mut guard = lock_session(sessions);
    let session = guard
        .as_mut()
        .ok_or_else(|| "session missing".to_string())?;
    if session.generation != generation || session.consumed || now_ms() >= session.expires_at_ms {
        return Err("session expired".into());
    }
    if session.state == "rejected" && request.action != "status" {
        return Err("session rejected".into());
    }
    match request.action.as_str() {
        "hello" => {
            if session.state != "showing_qr" {
                return Err("pairing hello is no longer accepted".into());
            }
            let bundle = request
                .bundle
                .ok_or_else(|| "hello bundle missing".to_string())?;
            validate_bundle(&bundle)?;
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
            let categories = request
                .categories
                .unwrap_or_default()
                .intersect(session.approved_categories);
            session.pending_direction = Some(direction.clone());
            session.pending_categories = Some(categories);
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
    })
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
            if is_hex_color(&value.accent_color) {
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

fn lock_session(
    sessions: &Arc<Mutex<Option<ActiveSession>>>,
) -> std::sync::MutexGuard<'_, Option<ActiveSession>> {
    sessions.lock().unwrap_or_else(|error| error.into_inner())
}

fn is_generation_active(sessions: &Arc<Mutex<Option<ActiveSession>>>, generation: Uuid) -> bool {
    lock_session(sessions)
        .as_ref()
        .map(|session| session.generation == generation && !session.consumed)
        .unwrap_or(false)
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
                SyncSubscription {
                    url: "HTTPS://EXAMPLE.COM/sub".into(),
                    name: Some("Remote".into()),
                },
                SyncSubscription {
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
                SyncSubscription {
                    url: "https://provider.example/sub/SecretKey".into(),
                    name: Some("Основная".into()),
                },
                SyncSubscription {
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
}
