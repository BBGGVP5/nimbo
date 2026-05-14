use std::collections::{HashMap, HashSet};
use std::fs::File;
use std::io::Cursor;
use std::net::{IpAddr, ToSocketAddrs};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, State};

use nimbo_device::{DeviceInfo, device_info, reset_cache};
use nimbo_ipc::PROTOCOL_VERSION;
use nimbo_subscription::{
    Server,
    FetchOptions, Fetched, Subscription, build_subscription, fetch_subscription,
    parse_aggregate, parse_subscription_userinfo,
};
use nimbo_xray_config::{
    AppRoutingMode as XrayAppRoutingMode, AppRoutingRule as XrayAppRoutingRule, ConfigBuilder,
    ProxyPorts, server_to_outbound,
};

use crate::state::{
    AppPreferences, AppProxyMode, AppProxyRule, AppState, ConnectionMode, PersistedState,
    SystemProxySnapshot, TunRuntimeSnapshot,
};

const TUN_INTERFACE_NAME: &str = "wintun";
const TUN_ADDRESS: &str = "198.18.0.1";
const TUN_NETMASK: &str = "255.255.255.0";
const TUN_DNS_PRIMARY: &str = "1.1.1.1";
const TUN_DNS_SECONDARY: &str = "8.8.8.8";
const DEFAULT_XRAY_TEMPLATE_KEY: &str = "default";
const XRAY_PROXY_TAG: &str = "proxy";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppStatus {
    pub state: ConnectionState,
    pub active_server_id: Option<String>,
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
    let mut clipboard = arboard::Clipboard::new()
        .map_err(|e| format!("Не удалось открыть буфер обмена: {e}"))?;
    clipboard
        .get_text()
        .map_err(|e| format!("В буфере нет текста или он недоступен: {e}"))
}

#[tauri::command]
pub fn write_clipboard_text(text: String) -> Result<(), String> {
    let mut clipboard = arboard::Clipboard::new()
        .map_err(|e| format!("Не удалось открыть буфер обмена: {e}"))?;
    clipboard
        .set_text(text)
        .map_err(|e| format!("Не удалось записать текст в буфер: {e}"))
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
        active_server_id: snapshot.active_server_id,
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
    set_launch_at_login(&app, preferences.launch_at_login)?;
    state
        .mutate(|s| s.preferences = preferences.clone())
        .map_err(|e| format!("Не удалось сохранить настройки приложения: {e}"))?;
    crate::tray::refresh_tray_menu(&app).map_err(|e| format!("Не удалось обновить меню трея: {e}"))?;
    Ok(preferences)
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
pub async fn inspect_subscription_headers(url: String) -> Result<SubscriptionHeaderMetadata, String> {
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
        println!("subscription header: {}: {}", name.as_str(), value);
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
    if snapshot_before.subscriptions.iter().any(|s| s.url == source) {
        return Err("Подписка с таким URL уже добавлена".into());
    }

    if source.is_empty() {
        return Err("Вставьте ссылку подписки или proxy-конфиг".into());
    }

    let mut fetched = if is_remote_subscription(source) {
        let opts = build_fetch_options(&snapshot_before);
        fetch_subscription(source, &opts)
            .await
            .map_err(|e| format!("Не удалось загрузить: {e}"))?
    } else {
        let servers = parse_aggregate(source)
            .map_err(|e| format!("Не удалось распарсить конфиг: {e}"))?;
        Fetched {
            raw_body: source.to_string(),
            servers,
            info: None,
            suggested_name: None,
            description: None,
            support_url: None,
            website_url: None,
        }
    };

    if is_remote_subscription(source) {
        enrich_servers_from_remnawave_api(
            source,
            &mut fetched.servers,
            snapshot_before.user_agent_override.as_deref(),
        )
        .await;
    }
    let xray_templates = fetch_xray_templates_for_subscription(
        source,
        &fetched.servers,
        snapshot_before.user_agent_override.as_deref(),
    )
    .await;
    let subscription = build_subscription(source, fetched, name);

    state
        .mutate(|s| {
            merge_xray_template_cache(&mut s.xray_templates, xray_templates);
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
    let mut fetched = fetch_subscription(&url, &opts)
        .await
        .map_err(|e| format!("Не удалось обновить: {e}"))?;
    enrich_servers_from_remnawave_api(&url, &mut fetched.servers, opts.user_agent.as_deref()).await;

    let active_was_in_servers = {
        let snap = state.snapshot();
        if let Some(active) = &snap.active_server_id {
            fetched.servers.iter().any(|srv| &srv.id == active)
        } else {
            true
        }
    };

    let xray_templates =
        fetch_xray_templates_for_subscription(&url, &fetched.servers, opts.user_agent.as_deref())
            .await;
    let updated_name = state
        .mutate(|s| {
            merge_xray_template_cache(&mut s.xray_templates, xray_templates);
            let existing = s
                .subscriptions
                .iter()
                .find(|sub| sub.url == url);
            let existing_name = existing.and_then(|sub| sub.name.clone());
            let existing_show_on_home = existing.and_then(|sub| sub.meta.show_on_home);
            let existing_update_interval = existing.and_then(|sub| sub.meta.update_interval_minutes);
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
            let Some(sub) = s.subscriptions.iter_mut().find(|sub| sub.url == url) else {
                return None;
            };

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
        if trimmed.is_empty() { None } else { Some(trimmed) }
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
pub fn list_installed_apps() -> Vec<InstalledApp> {
    platform_installed_apps()
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

    state
        .mutate(|s| s.app_proxy_rules = cleaned)
        .map_err(|e| format!("Не удалось сохранить правила приложений: {e}"))?;
    Ok(state.snapshot())
}

#[tauri::command]
pub fn set_connection_mode(
    state: State<'_, AppState>,
    mode: ConnectionMode,
) -> Result<PersistedState, String> {
    state
        .mutate(|s| s.connection_mode = mode)
        .map_err(|e| format!("Не удалось сохранить режим подключения: {e}"))?;
    Ok(state.snapshot())
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
    let result = tcp_ping_server(&server).await;
    persist_ping_results(&state, std::slice::from_ref(&result))?;
    Ok(result)
}

#[tauri::command]
pub async fn ping_servers(
    state: State<'_, AppState>,
    server_ids: Vec<String>,
) -> Result<Vec<ServerPing>, String> {
    let snap = state.snapshot();
    let mut handles = Vec::with_capacity(server_ids.len());
    for server_id in server_ids {
        if let Some(server) = find_server(&snap, &server_id) {
            handles.push(tokio::spawn(async move { tcp_ping_server(&server).await }));
        }
    }

    let mut out = Vec::with_capacity(handles.len());
    for handle in handles {
        if let Ok(result) = handle.await {
            out.push(result);
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
                format!("Xray statsquery завершился с кодом {}: {stderr}", output.status)
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
    let mut child = command
        .spawn()
        .map_err(|e| format!("процесс не запустился: {e}"))?;
    let start = std::time::Instant::now();
    loop {
        match child.try_wait() {
            Ok(Some(_)) => {
                return child
                    .wait_with_output()
                    .map_err(|e| format!("не удалось прочитать вывод: {e}"));
            }
            Ok(None) if start.elapsed() >= timeout => {
                let _ = child.kill();
                let _ = child.wait();
                return Err("таймаут".into());
            }
            Ok(None) => std::thread::sleep(std::time::Duration::from_millis(80)),
            Err(e) => return Err(format!("не удалось дождаться процесса: {e}")),
        }
    }
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
        .or_else(|| value.as_str().and_then(|text| text.trim().parse::<u64>().ok()))
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
    headers.iter().find(|header| is_announce_header(&header.name))
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
                let key = format!("{}|{}", display_name.to_lowercase(), executable_path.to_lowercase());
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
    if let Some(id) = &server_id {
        let snap = state.snapshot();
        let exists = snap
            .subscriptions
            .iter()
            .any(|sub| sub.servers.iter().any(|srv| &srv.id == id));
        if !exists {
            return Err("Сервер не найден в подписках".into());
        }
    }
    state
        .mutate(|s| s.active_server_id = server_id)
        .map_err(|e| format!("Не удалось сохранить: {e}"))?;
    Ok(state.snapshot())
}

#[tauri::command]
pub async fn connect_server(
    app: AppHandle,
    state: State<'_, AppState>,
    server_id: String,
) -> Result<PersistedState, String> {
    let snap = state.snapshot();
    let Some(server) = find_server(&snap, &server_id) else {
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
    }

    state
        .mutate(|s| {
            s.active_server_id = Some(server_id);
            s.connected = true;
        })
        .map_err(|e| format!("Не удалось сохранить статус подключения: {e}"))?;
    Ok(state.snapshot())
}

#[tauri::command]
pub fn disconnect_server(state: State<'_, AppState>) -> Result<PersistedState, String> {
    stop_runtime(&state)?;
    state
        .mutate(|s| s.connected = false)
        .map_err(|e| format!("Не удалось отключиться: {e}"))?;
    Ok(state.snapshot())
}

fn find_server(snapshot: &PersistedState, server_id: &str) -> Option<Server> {
    snapshot
        .subscriptions
        .iter()
        .flat_map(|sub| sub.servers.iter())
        .find(|server| server.id == server_id)
        .cloned()
}

fn merge_xray_template_cache(
    cache: &mut HashMap<String, serde_json::Value>,
    templates: HashMap<String, serde_json::Value>,
) {
    for (uuid, template) in templates {
        cache.insert(uuid, template);
    }
}

#[derive(Debug, Clone)]
struct RemnawaveApiContext {
    base: String,
}

#[derive(Debug, Clone, Default)]
struct HostApiInfo {
    server_description: Option<String>,
    xray_json_template_uuid: Option<String>,
}

async fn enrich_servers_from_remnawave_api(
    subscription_url: &str,
    servers: &mut [Server],
    user_agent_override: Option<&str>,
) {
    let Some(context) = remnawave_api_context(subscription_url) else {
        return;
    };
    let client = match build_remnawave_client(user_agent_override) {
        Ok(client) => client,
        Err(error) => {
            println!("remnawave host cache: client init failed: {error}");
            return;
        }
    };

    let mut host_cache = HashMap::<String, Option<HostApiInfo>>::new();
    for server in servers {
        let Some(host_uuid) = clean_optional_uuid(server.host_uuid.as_deref()) else {
            continue;
        };
        if !host_cache.contains_key(&host_uuid) {
            let info = fetch_host_api_info(&client, &context, &host_uuid).await;
            host_cache.insert(host_uuid.clone(), info);
        }
        let Some(Some(info)) = host_cache.get(&host_uuid) else {
            continue;
        };
        if server
            .server_description
            .as_deref()
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .is_none()
        {
            if let Some(description) = &info.server_description {
                server.server_description = Some(description.clone());
            }
        }
        if server.xray_json_template_uuid.is_none() {
            server.xray_json_template_uuid = info.xray_json_template_uuid.clone();
        }
    }
}

async fn fetch_host_api_info(
    client: &reqwest::Client,
    context: &RemnawaveApiContext,
    host_uuid: &str,
) -> Option<HostApiInfo> {
    let url = format!("{}/api/hosts/{host_uuid}", context.base);
    let json = get_remnawave_json(client, &url).await?;
    let root = json
        .get("response")
        .or_else(|| json.get("data"))
        .unwrap_or(&json);
    Some(HostApiInfo {
        server_description: first_json_string_deep(
            root,
            &[
                "serverDescription",
                "server_description",
                "server-description",
                "serverDesc",
                "server_desc",
                "hostDescription",
                "host_description",
            ],
        ),
        xray_json_template_uuid: first_json_string_deep(
            root,
            &[
                "xrayJsonTemplateUuid",
                "xray_json_template_uuid",
                "xray-json-template-uuid",
            ],
        )
        .and_then(|value| clean_optional_uuid(Some(&value))),
    })
}

async fn fetch_xray_templates_for_subscription(
    subscription_url: &str,
    servers: &[Server],
    user_agent_override: Option<&str>,
) -> HashMap<String, serde_json::Value> {
    let mut out = HashMap::new();
    let Some(context) = remnawave_api_context(subscription_url) else {
        return out;
    };
    let client = match build_remnawave_client(user_agent_override) {
        Ok(client) => client,
        Err(error) => {
            println!("xray template cache: client init failed: {error}");
            return out;
        }
    };

    let mut template_uuids = servers
        .iter()
        .filter_map(|server| clean_optional_uuid(server.xray_json_template_uuid.as_deref()))
        .collect::<Vec<_>>();

    for host_uuid in servers
        .iter()
        .filter(|server| server.xray_json_template_uuid.is_none())
        .filter_map(|server| clean_optional_uuid(server.host_uuid.as_deref()))
    {
        if let Some(uuid) = fetch_host_xray_template_uuid(&client, &context, &host_uuid).await {
            template_uuids.push(uuid);
        }
    }

    template_uuids.sort();
    template_uuids.dedup();

    for uuid in template_uuids {
        if let Some(template) = fetch_xray_template_by_uuid(&client, &context, &uuid).await {
            out.insert(uuid, template);
        }
    }

    if let Some(template) = fetch_default_xray_template(&client, &context).await {
        out.insert(DEFAULT_XRAY_TEMPLATE_KEY.into(), template);
    } else if let Some(template) = fetch_public_xray_template(&client, subscription_url).await {
        out.insert(DEFAULT_XRAY_TEMPLATE_KEY.into(), template);
    }

    if !out.contains_key(DEFAULT_XRAY_TEMPLATE_KEY) && out.len() == 1 {
        if let Some(template) = out.values().next().cloned() {
            out.insert(DEFAULT_XRAY_TEMPLATE_KEY.into(), template);
        }
    }

    out
}

fn build_remnawave_client(
    user_agent_override: Option<&str>,
) -> Result<reqwest::Client, reqwest::Error> {
    let device = device_info();
    let mut headers = reqwest::header::HeaderMap::new();
    insert_request_header(&mut headers, "X-Hwid", &device.hwid);
    insert_request_header(&mut headers, "X-Device-Os", &device.os);
    insert_request_header(&mut headers, "X-Device-Os-Version", &device.os_version);
    insert_request_header(&mut headers, "X-Device-Model", &device.hostname);

    reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(15))
        .user_agent(user_agent_override.unwrap_or(&device.user_agent))
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

fn remnawave_api_context(subscription_url: &str) -> Option<RemnawaveApiContext> {
    let parsed = url::Url::parse(subscription_url).ok()?;
    Some(RemnawaveApiContext {
        base: format!("{}://{}", parsed.scheme(), parsed.host_str()?),
    })
}

fn clean_optional_uuid(value: Option<&str>) -> Option<String> {
    let value = value?.trim();
    if value.is_empty() || value.eq_ignore_ascii_case("null") {
        None
    } else {
        Some(value.to_string())
    }
}

async fn fetch_host_xray_template_uuid(
    client: &reqwest::Client,
    context: &RemnawaveApiContext,
    host_uuid: &str,
) -> Option<String> {
    let url = format!("{}/api/hosts/{host_uuid}", context.base);
    let json = get_remnawave_json(client, &url).await?;
    let root = json
        .get("response")
        .or_else(|| json.get("data"))
        .unwrap_or(&json);
    first_json_string_deep(root, &["xrayJsonTemplateUuid", "xray_json_template_uuid"])
        .and_then(|value| clean_optional_uuid(Some(&value)))
}

async fn fetch_xray_template_by_uuid(
    client: &reqwest::Client,
    context: &RemnawaveApiContext,
    uuid: &str,
) -> Option<serde_json::Value> {
    let urls = [
        format!("{}/api/subscription-templates/{uuid}", context.base),
        format!("{}/api/subscription-template/{uuid}", context.base),
        format!("{}/api/xray-json-templates/{uuid}", context.base),
        format!("{}/api/xray-json-template/{uuid}", context.base),
    ];
    for url in urls {
        let Some(json) = get_remnawave_json(client, &url).await else {
            continue;
        };
        if let Some(template) = extract_xray_template_json(&json) {
            return Some(template);
        }
    }
    None
}

async fn fetch_default_xray_template(
    client: &reqwest::Client,
    context: &RemnawaveApiContext,
) -> Option<serde_json::Value> {
    let urls = [
        format!("{}/api/subscription-templates", context.base),
        format!("{}/api/subscription-templates?templateType=XRAY_JSON", context.base),
        format!("{}/api/xray-json-templates", context.base),
    ];
    for url in urls {
        let Some(json) = get_remnawave_json(client, &url).await else {
            continue;
        };
        if let Some(template) = extract_default_xray_template_json(&json) {
            return Some(template);
        }
    }
    None
}

async fn fetch_public_xray_template(
    client: &reqwest::Client,
    subscription_url: &str,
) -> Option<serde_json::Value> {
    for url in subscription_xray_template_urls(subscription_url) {
        let Some(json) = get_remnawave_json(client, &url).await else {
            continue;
        };
        if let Some(template) = extract_xray_template_json(&json) {
            return Some(template);
        }
    }
    None
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
    for base_path in [
        format!("/api/sub/{short_uuid}"),
        public_base,
    ] {
        for suffix in ["xray-json", "xray", "json"] {
            let mut url = parsed.clone();
            url.set_path(&format!("{base_path}/{suffix}"));
            url.set_fragment(None);
            urls.push(url.to_string());
        }
    }

    for suffix in ["xray-json", "xray"] {
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

    urls.sort();
    urls.dedup();
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

async fn get_remnawave_json(
    client: &reqwest::Client,
    url: &str,
) -> Option<serde_json::Value> {
    let request = client
        .get(url)
        .header(reqwest::header::ACCEPT, "application/json");
    let response = request.send().await.ok()?;
    if !response.status().is_success() {
        if response.status().as_u16() != 404 {
            println!("xray template cache: {url} -> {}", response.status());
        }
        return None;
    }
    let text = response.text().await.ok()?;
    serde_json::from_str::<serde_json::Value>(&text).ok()
}

fn extract_default_xray_template_json(json: &serde_json::Value) -> Option<serde_json::Value> {
    let mut templates = Vec::new();
    collect_json_objects(json, &mut templates);

    templates
        .iter()
        .copied()
        .filter(|value| looks_like_xray_template_record(value))
        .find(|value| {
            first_json_bool(value, &["isDefault", "is_default", "default"]).unwrap_or(false)
                || first_json_string(value, &["name", "templateName", "template_name"])
                    .map(|name| name.to_ascii_lowercase().contains("default"))
                    .unwrap_or(false)
        })
        .and_then(extract_xray_template_json)
        .or_else(|| {
            templates
                .iter()
                .copied()
                .find(|value| looks_like_xray_template_record(value))
                .and_then(extract_xray_template_json)
        })
        .or_else(|| extract_xray_template_json(json))
}

fn extract_xray_template_json(value: &serde_json::Value) -> Option<serde_json::Value> {
    if value.get("outbounds").is_some() {
        return Some(value.clone());
    }
    let root = value
        .get("response")
        .or_else(|| value.get("data"))
        .unwrap_or(value);
    if root.get("outbounds").is_some() {
        return Some(root.clone());
    }
    for key in [
        "templateJson",
        "template_json",
        "xrayJsonTemplate",
        "xray_json_template",
        "config",
        "json",
        "template",
    ] {
        if let Some(template) = get_case_insensitive_json(root, key).and_then(parse_template_value) {
            return Some(template);
        }
    }
    None
}

fn parse_template_value(value: &serde_json::Value) -> Option<serde_json::Value> {
    if value.get("outbounds").is_some() {
        return Some(value.clone());
    }
    if let Some(text) = value.as_str() {
        return serde_json::from_str::<serde_json::Value>(text)
            .ok()
            .filter(|json| json.get("outbounds").is_some());
    }
    None
}

fn looks_like_xray_template_record(value: &serde_json::Value) -> bool {
    let template_type = first_json_string(
        value,
        &["templateType", "template_type", "type", "kind"],
    )
    .unwrap_or_default()
    .to_ascii_lowercase();
    template_type.contains("xray") || extract_xray_template_json(value).is_some()
}

fn collect_json_objects<'a>(value: &'a serde_json::Value, out: &mut Vec<&'a serde_json::Value>) {
    match value {
        serde_json::Value::Object(map) => {
            out.push(value);
            for nested in map.values() {
                collect_json_objects(nested, out);
            }
        }
        serde_json::Value::Array(items) => {
            for nested in items {
                collect_json_objects(nested, out);
            }
        }
        _ => {}
    }
}

fn first_json_string(value: &serde_json::Value, keys: &[&str]) -> Option<String> {
    for key in keys {
        if let Some(text) = get_case_insensitive_json(value, key).and_then(serde_json::Value::as_str) {
            let text = text.trim();
            if !text.is_empty() {
                return Some(text.to_string());
            }
        }
    }
    None
}

fn first_json_string_deep(value: &serde_json::Value, keys: &[&str]) -> Option<String> {
    if let Some(found) = first_json_string(value, keys) {
        return Some(found);
    }
    match value {
        serde_json::Value::Object(map) => {
            for nested in map.values() {
                if let Some(found) = first_json_string_deep(nested, keys) {
                    return Some(found);
                }
            }
            None
        }
        serde_json::Value::Array(items) => {
            for nested in items {
                if let Some(found) = first_json_string_deep(nested, keys) {
                    return Some(found);
                }
            }
            None
        }
        _ => None,
    }
}

fn first_json_bool(value: &serde_json::Value, keys: &[&str]) -> Option<bool> {
    for key in keys {
        if let Some(value) = get_case_insensitive_json(value, key).and_then(serde_json::Value::as_bool) {
            return Some(value);
        }
    }
    None
}

fn get_case_insensitive_json<'a>(
    value: &'a serde_json::Value,
    key: &str,
) -> Option<&'a serde_json::Value> {
    let serde_json::Value::Object(map) = value else {
        return None;
    };
    map.iter()
        .find(|(candidate, _)| normalize_json_key(candidate) == normalize_json_key(key))
        .map(|(_, value)| value)
}

fn normalize_json_key(key: &str) -> String {
    key.chars()
        .filter(|ch| *ch != '_' && *ch != '-')
        .flat_map(char::to_lowercase)
        .collect()
}

async fn tcp_ping_server(server: &Server) -> ServerPing {
    let (host, port) = server_endpoint(server);
    let start = std::time::Instant::now();
    let result = tokio::time::timeout(
        std::time::Duration::from_secs(5),
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

fn server_endpoint(server: &Server) -> (String, u16) {
    match &server.protocol {
        nimbo_subscription::Protocol::Vless(config) => (config.address.clone(), config.port),
        nimbo_subscription::Protocol::Vmess(config) => (config.address.clone(), config.port),
        nimbo_subscription::Protocol::Trojan(config) => (config.address.clone(), config.port),
        nimbo_subscription::Protocol::Shadowsocks(config) => (config.address.clone(), config.port),
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
    let proxy_snapshot = match apply_system_proxy(ports) {
        Ok(snapshot) => snapshot,
        Err(error) => {
            let _ = child.kill();
            let _ = child.wait();
            return Err(error);
        }
    };

    state.runtime(|runtime| {
        runtime.xray = Some(child);
        runtime.system_proxy_snapshot = proxy_snapshot;
    });

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
    let config = build_runtime_xray_config(&server, snapshot, ports)?;
    let config_path = write_xray_config(&config)?;
    let xray_path = ensure_xray_binary(app).await?;
    let tun_files = prepare_tun_runtime_files(app)?;
    let default_route = current_default_ipv4_route();
    let bypass_ips = resolve_server_ipv4s(&server);

    let mut xray = spawn_xray(&xray_path, &config_path)?;
    let mut tun2socks = match spawn_tun2socks(&tun_files.tun2socks_path, ports, snapshot) {
        Ok(child) => child,
        Err(error) => {
            let _ = xray.kill();
            let _ = xray.wait();
            return Err(error);
        }
    };

    let tun_snapshot = TunRuntimeSnapshot {
        bypass_ips,
        gateway: default_route.as_ref().map(|route| route.gateway.clone()),
        interface_index: default_route.as_ref().map(|route| route.interface_index),
    };

    if let Err(error) = configure_tun_interface(&tun_snapshot) {
        let _ = tun2socks.kill();
        let _ = tun2socks.wait();
        let _ = xray.kill();
        let _ = xray.wait();
        let _ = cleanup_tun(Some(tun_snapshot));
        return Err(error);
    }

    state.runtime(|runtime| {
        runtime.xray = Some(xray);
        runtime.tun2socks = Some(tun2socks);
        runtime.tun_snapshot = Some(tun_snapshot);
    });

    Ok(())
}

struct TunRuntimeFiles {
    tun2socks_path: PathBuf,
}

fn prepare_tun_runtime_files(app: &AppHandle) -> Result<TunRuntimeFiles, String> {
    let bin_dir = nimbo_data_dir()?.join("bin");
    std::fs::create_dir_all(&bin_dir)
        .map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;

    let tun2socks_source = find_existing_path(tun2socks_candidate_paths(app)?)
        .ok_or_else(|| "tun2socks.exe не найден после установки TUN.".to_string())?;
    let wintun_source = find_existing_path(wintun_candidate_paths(app)?)
        .ok_or_else(|| "wintun.dll не найден после установки TUN.".to_string())?;

    let tun2socks_path = bin_dir.join(tun2socks_name());
    let wintun_path = bin_dir.join(wintun_name());
    copy_tun_file(&tun2socks_source, &tun2socks_path)?;
    copy_tun_file(&wintun_source, &wintun_path)?;

    Ok(TunRuntimeFiles { tun2socks_path })
}

fn spawn_tun2socks(
    tun2socks_path: &Path,
    ports: ProxyPorts,
    snapshot: &PersistedState,
) -> Result<std::process::Child, String> {
    let proxy = socks_proxy_url(ports, snapshot);
    let log_path = nimbo_data_dir()?.join("runtime").join("tun2socks.log");
    if let Some(parent) = log_path.parent() {
        std::fs::create_dir_all(parent)
            .map_err(|e| format!("Не удалось создать папку логов TUN: {e}"))?;
    }
    let stdout = File::create(&log_path)
        .map_err(|e| format!("Не удалось создать лог tun2socks: {e}"))?;
    let stderr = stdout
        .try_clone()
        .map_err(|e| format!("Не удалось открыть лог tun2socks: {e}"))?;
    let mut command = Command::new(tun2socks_path);
    command
        .arg("-device")
        .arg(TUN_INTERFACE_NAME)
        .arg("-proxy")
        .arg(proxy)
        .arg("-mtu")
        .arg("9000")
        .arg("-loglevel")
        .arg("warn")
        .current_dir(tun2socks_path.parent().unwrap_or_else(|| Path::new(".")))
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
        .map_err(|e| format!("Не удалось запустить tun2socks: {e}"))?;
    std::thread::sleep(std::time::Duration::from_millis(900));
    if let Ok(Some(status)) = child.try_wait() {
        let log = std::fs::read_to_string(&log_path)
            .unwrap_or_default()
            .lines()
            .rev()
            .take(8)
            .collect::<Vec<_>>()
            .into_iter()
            .rev()
            .collect::<Vec<_>>()
            .join("\n");
        let detail = if log.trim().is_empty() {
            status.to_string()
        } else {
            format!("{status}\n{log}")
        };
        return Err(format!("tun2socks завершился сразу после запуска: {detail}"));
    }
    Ok(child)
}

fn socks_proxy_url(ports: ProxyPorts, snapshot: &PersistedState) -> String {
    if snapshot.require_socks_auth {
        format!(
            "socks5://{}:{}@127.0.0.1:{}",
            percent_encode_url_part(&snapshot.socks_username),
            percent_encode_url_part(&snapshot.socks_password),
            ports.socks
        )
    } else {
        format!("socks5://127.0.0.1:{}", ports.socks)
    }
}

fn percent_encode_url_part(value: &str) -> String {
    let mut encoded = String::new();
    for byte in value.bytes() {
        let keep = byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'.' | b'_' | b'~');
        if keep {
            encoded.push(byte as char);
        } else {
            encoded.push_str(&format!("%{byte:02X}"));
        }
    }
    encoded
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

#[cfg(not(windows))]
fn current_default_ipv4_route() -> Option<DefaultIpv4Route> {
    None
}

fn resolve_server_ipv4s(server: &Server) -> Vec<String> {
    let (host, port) = server_endpoint(server);
    if let Ok(IpAddr::V4(ip)) = host.parse::<IpAddr>() {
        return vec![ip.to_string()];
    }
    let mut ips = Vec::new();
    if let Ok(addrs) = (host.as_str(), port).to_socket_addrs() {
        for addr in addrs {
            if let IpAddr::V4(ip) = addr.ip() {
                let ip = ip.to_string();
                if !ips.contains(&ip) {
                    ips.push(ip);
                }
            }
        }
    }
    ips
}

fn build_runtime_xray_config(
    server: &Server,
    snapshot: &PersistedState,
    ports: ProxyPorts,
) -> Result<serde_json::Value, String> {
    let mut builder = ConfigBuilder::new(ports)
        .server(server)
        .app_routing_rules(xray_app_rules(&snapshot.app_proxy_rules))
        .block_socks_udp(snapshot.block_socks_udp);
    if snapshot.require_socks_auth {
        builder = builder.socks_auth(&snapshot.socks_username, &snapshot.socks_password);
    }

    let base_config = builder.build();
    let base_value = serde_json::to_value(&base_config)
        .map_err(|e| format!("Не удалось собрать базовый Xray config: {e}"))?;

    let template_key = server
        .xray_json_template_uuid
        .as_deref()
        .filter(|uuid| !uuid.trim().is_empty())
        .unwrap_or(DEFAULT_XRAY_TEMPLATE_KEY);
    let template = snapshot
        .xray_templates
        .get(template_key)
        .or_else(|| snapshot.xray_templates.get(DEFAULT_XRAY_TEMPLATE_KEY))
        .or_else(|| {
            if snapshot.xray_templates.len() == 1 {
                snapshot.xray_templates.values().next()
            } else {
                None
            }
        });

    let Some(template) = template else {
        return Ok(base_value);
    };

    apply_xray_template(template.clone(), base_value, server)
}

fn apply_xray_template(
    mut template: serde_json::Value,
    base_config: serde_json::Value,
    server: &Server,
) -> Result<serde_json::Value, String> {
    if !template.is_object() {
        return Ok(base_config);
    }

    let proxy_outbound = serde_json::to_value(server_to_outbound(server, XRAY_PROXY_TAG))
        .map_err(|e| format!("Не удалось собрать outbound Xray: {e}"))?;

    if let Some(inbounds) = base_config.get("inbounds").cloned() {
        template["inbounds"] = inbounds;
    }
    if let Some(api) = base_config.get("api").cloned() {
        template["api"] = api;
    }
    if let Some(policy) = base_config.get("policy").cloned() {
        template["policy"] = policy;
    }
    if let Some(stats) = base_config.get("stats").cloned() {
        template["stats"] = stats;
    }
    if let Some(routing) = template.get_mut("routing").and_then(|r| r.as_object_mut()) {
        merge_template_routing(routing, &base_config);
    } else if let Some(routing) = base_config.get("routing").cloned() {
        template["routing"] = routing;
    }

    if template.get("dns").is_none() {
        if let Some(dns) = base_config.get("dns").cloned() {
            template["dns"] = dns;
        }
    }
    if template.get("log").is_none() {
        if let Some(log) = base_config.get("log").cloned() {
            template["log"] = log;
        }
    }

    let Some(outbounds) = template.get_mut("outbounds").and_then(serde_json::Value::as_array_mut)
    else {
        template["outbounds"] = base_config
            .get("outbounds")
            .cloned()
            .unwrap_or_else(|| serde_json::json!([proxy_outbound]));
        return Ok(template);
    };

    if let Some(existing) = outbounds
        .iter_mut()
        .find(|outbound| outbound.get("tag").and_then(serde_json::Value::as_str) == Some(XRAY_PROXY_TAG))
    {
        *existing = proxy_outbound;
        return Ok(template);
    }

    if let Some(existing) = outbounds.iter_mut().find(|outbound| {
        let protocol = outbound
            .get("protocol")
            .and_then(serde_json::Value::as_str)
            .unwrap_or_default();
        !matches!(protocol, "freedom" | "blackhole" | "dns")
    }) {
        *existing = proxy_outbound;
    } else {
        outbounds.insert(0, proxy_outbound);
    }

    Ok(template)
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

    normalize_xray_routing_rules(rules_arr);

    let mut priority_rules = Vec::new();
    let mut fallback_rules = Vec::new();
    for rule in base_rules {
        if is_xray_proxy_fallback_rule(rule) {
            fallback_rules.push(rule.clone());
        } else {
            priority_rules.push(rule.clone());
        }
    }

    for rule in priority_rules.into_iter().rev() {
        rules_arr.insert(0, rule);
    }
    for rule in fallback_rules {
        if !rules_arr.iter().any(|existing| existing == &rule) {
            rules_arr.push(rule);
        }
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
            rule.insert(
                "type".into(),
                serde_json::Value::String("field".into()),
            );
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

fn xray_app_rules(rules: &[AppProxyRule]) -> Vec<XrayAppRoutingRule> {
    rules
        .iter()
        .map(|rule| XrayAppRoutingRule {
            process: rule.executable_path.clone(),
            enabled: rule.enabled,
            mode: match rule.mode {
                AppProxyMode::Proxy => XrayAppRoutingMode::Proxy,
                AppProxyMode::Direct => XrayAppRoutingMode::Direct,
            },
        })
        .collect()
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

    #[cfg(all(windows, target_arch = "x86_64"))]
    {
        download_xray_windows_x64().await
    }

    #[cfg(not(all(windows, target_arch = "x86_64")))]
    {
        Err("xray-core не найден. Положи xray.exe рядом с приложением или укажи NIMBO_XRAY_PATH.".into())
    }
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
    if cfg!(windows) { "xray.exe" } else { "xray" }
}

#[cfg(all(windows, target_arch = "x86_64"))]
async fn download_xray_windows_x64() -> Result<PathBuf, String> {
    let bin_dir = nimbo_data_dir()?.join("bin");
    std::fs::create_dir_all(&bin_dir)
        .map_err(|e| format!("Не удалось создать папку Xray: {e}"))?;
    let target = bin_dir.join("xray.exe");
    let archive_url =
        "https://github.com/XTLS/Xray-core/releases/latest/download/Xray-windows-64.zip";

    println!("xray: downloading {archive_url}");
    let bytes = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(90))
        .build()
        .map_err(|e| format!("Не удалось создать HTTP-клиент для Xray: {e}"))?
        .get(archive_url)
        .send()
        .await
        .map_err(|e| format!("Не удалось скачать Xray: {e}"))?
        .error_for_status()
        .map_err(|e| format!("Не удалось скачать Xray: {e}"))?
        .bytes()
        .await
        .map_err(|e| format!("Не удалось прочитать архив Xray: {e}"))?;

    let reader = Cursor::new(bytes);
    let mut archive = zip::ZipArchive::new(reader)
        .map_err(|e| format!("Не удалось открыть архив Xray: {e}"))?;
    for index in 0..archive.len() {
        let mut file = archive
            .by_index(index)
            .map_err(|e| format!("Не удалось прочитать файл архива Xray: {e}"))?;
        if !file.is_file() {
            continue;
        }
        let Some(name) = file.enclosed_name().and_then(|path| path.file_name().map(|name| name.to_owned())) else {
            continue;
        };
        let output = bin_dir.join(name);
        let mut out = File::create(&output)
            .map_err(|e| format!("Не удалось распаковать Xray: {e}"))?;
        std::io::copy(&mut file, &mut out)
            .map_err(|e| format!("Не удалось записать файл Xray: {e}"))?;
    }

    if target.exists() {
        Ok(target)
    } else {
        Err("Архив Xray скачан, но xray.exe внутри не найден.".into())
    }
}

fn spawn_xray(xray_path: &Path, config_path: &Path) -> Result<std::process::Child, String> {
    let mut command = Command::new(xray_path);
    command
        .arg("run")
        .arg("-config")
        .arg(config_path)
        .current_dir(xray_path.parent().unwrap_or_else(|| Path::new(".")))
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());

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
        return Err(format!("Xray завершился сразу после запуска: {status}"));
    }
    Ok(child)
}

fn stop_runtime(state: &State<'_, AppState>) -> Result<(), String> {
    let (tun_snapshot, proxy_snapshot) = state.runtime(|runtime| {
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
    let tun_result = cleanup_tun(tun_snapshot);
    let proxy_result = restore_system_proxy(proxy_snapshot);
    tun_result?;
    proxy_result?;
    Ok(())
}

#[cfg(windows)]
fn configure_tun_interface(snapshot: &TunRuntimeSnapshot) -> Result<(), String> {
    retry_tun_command(|| {
        run_hidden(
            "netsh",
            &[
                "interface".into(),
                "ip".into(),
                "set".into(),
                "address".into(),
                format!("name={TUN_INTERFACE_NAME}"),
                "static".into(),
                TUN_ADDRESS.into(),
                TUN_NETMASK.into(),
            ],
        )
    })
    .map_err(|e| format!("Не удалось настроить адрес TUN-адаптера: {e}"))?;

    let _ = run_hidden(
        "netsh",
        &[
            "interface".into(),
            "ipv4".into(),
            "set".into(),
            "interface".into(),
            TUN_INTERFACE_NAME.into(),
            "metric=1".into(),
        ],
    );
    let _ = run_hidden(
        "netsh",
        &[
            "interface".into(),
            "ip".into(),
            "set".into(),
            "dns".into(),
            format!("name={TUN_INTERFACE_NAME}"),
            "static".into(),
            TUN_DNS_PRIMARY.into(),
            "primary".into(),
        ],
    );
    let _ = run_hidden(
        "netsh",
        &[
            "interface".into(),
            "ip".into(),
            "add".into(),
            "dns".into(),
            format!("name={TUN_INTERFACE_NAME}"),
            TUN_DNS_SECONDARY.into(),
            "index=2".into(),
        ],
    );

    add_server_bypass_routes(snapshot)?;
    add_tun_route("0.0.0.0/1")?;
    add_tun_route("128.0.0.0/1")?;
    Ok(())
}

#[cfg(not(windows))]
fn configure_tun_interface(_snapshot: &TunRuntimeSnapshot) -> Result<(), String> {
    Err("TUN сейчас реализован только для Windows.".into())
}

#[cfg(windows)]
fn retry_tun_command<F>(mut command: F) -> Result<(), String>
where
    F: FnMut() -> Result<(), String>,
{
    let mut last_error = None;
    for _ in 0..12 {
        match command() {
            Ok(()) => return Ok(()),
            Err(error) => {
                last_error = Some(error);
                std::thread::sleep(std::time::Duration::from_millis(350));
            }
        }
    }
    Err(last_error.unwrap_or_else(|| "команда не выполнилась".into()))
}

#[cfg(windows)]
fn add_server_bypass_routes(snapshot: &TunRuntimeSnapshot) -> Result<(), String> {
    let (Some(gateway), Some(interface_index)) = (&snapshot.gateway, snapshot.interface_index)
    else {
        return Ok(());
    };
    for ip in &snapshot.bypass_ips {
        run_hidden(
            "route",
            &[
                "ADD".into(),
                ip.clone(),
                "MASK".into(),
                "255.255.255.255".into(),
                gateway.clone(),
                "METRIC".into(),
                "1".into(),
                "IF".into(),
                interface_index.to_string(),
            ],
        )?;
    }
    Ok(())
}

#[cfg(windows)]
fn add_tun_route(prefix: &str) -> Result<(), String> {
    run_hidden(
        "netsh",
        &[
            "interface".into(),
            "ipv4".into(),
            "add".into(),
            "route".into(),
            format!("prefix={prefix}"),
            format!("interface={TUN_INTERFACE_NAME}"),
            format!("nexthop={TUN_ADDRESS}"),
            "metric=1".into(),
            "store=active".into(),
        ],
    )
    .map_err(|e| format!("Не удалось добавить маршрут {prefix}: {e}"))
}

#[cfg(windows)]
fn cleanup_tun(snapshot: Option<TunRuntimeSnapshot>) -> Result<(), String> {
    let _ = delete_tun_route("0.0.0.0/1");
    let _ = delete_tun_route("128.0.0.0/1");
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

fn nimbo_data_dir() -> Result<PathBuf, String> {
    dirs::data_dir()
        .map(|dir| dir.join("Nimbo"))
        .ok_or_else(|| "APPDATA недоступен".to_string())
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

fn install_bundled_tun_files_without_app() -> Result<(), String> {
    let bin_dir = nimbo_data_dir()?.join("bin");
    std::fs::create_dir_all(&bin_dir)
        .map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;

    if let Some(path) = find_existing_path(local_bundled_tun_paths(tun2socks_name())) {
        copy_tun_file(&path, &bin_dir.join(tun2socks_name()))?;
    }
    if let Some(path) = find_existing_path(local_bundled_tun_paths(wintun_name())) {
        copy_tun_file(&path, &bin_dir.join(wintun_name()))?;
    }
    Ok(())
}

fn install_bundled_tun_files(app: &AppHandle) -> Result<(), String> {
    let bin_dir = nimbo_data_dir()?.join("bin");
    std::fs::create_dir_all(&bin_dir)
        .map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;

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
        paths.push(cwd.join("src-tauri").join("resources").join("tun").join(tun2socks_name()));
        paths.push(cwd.join("src-tauri").join("binaries").join(tun2socks_name()));
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
        paths.push(cwd.join("src-tauri").join("resources").join("tun").join(wintun_name()));
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
        paths.push(resource.join("resources").join("resources").join("tun").join(file_name));
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
            paths.push(dir.join("resources").join("resources").join("tun").join(file_name));
            paths.push(dir.join("resources").join("tun").join(file_name));
            paths.push(dir.join("tun").join(file_name));
            paths.push(dir.join("binaries").join(file_name));
        }
    }
    if let Ok(cwd) = std::env::current_dir() {
        paths.push(cwd.join("resources").join("resources").join("tun").join(file_name));
        paths.push(cwd.join("resources").join("tun").join(file_name));
        paths.push(cwd.join("tun").join(file_name));
        paths.push(cwd.join("binaries").join(file_name));
    }
    paths
}

fn tun2socks_name() -> &'static str {
    if cfg!(windows) { "tun2socks.exe" } else { "tun2socks" }
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
    std::fs::create_dir_all(&bin_dir)
        .map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;
    let target = bin_dir.join(tun2socks_name());
    if target.exists() {
        return Ok(target);
    }

    let archive_url =
        "https://github.com/xjasonlyu/tun2socks/releases/latest/download/tun2socks-windows-amd64.zip";
    println!("tun2socks: downloading {archive_url}");
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
    std::fs::create_dir_all(&bin_dir)
        .map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;
    let target = bin_dir.join(wintun_name());
    if target.exists() {
        return Ok(target);
    }

    let archive_url = "https://www.wintun.net/builds/wintun-0.14.1.zip";
    println!("wintun: downloading {archive_url}");
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
fn extract_named_file_from_zip(bytes: Vec<u8>, file_name: &str, target: &Path) -> Result<(), String> {
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
        let Some(name) = file.enclosed_name().and_then(|path| path.file_name().map(|name| name.to_owned())) else {
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
        let normalized = path.to_string_lossy().replace('\\', "/").to_ascii_lowercase();
        if normalized.ends_with("/bin/amd64/wintun.dll") || normalized.ends_with("bin/amd64/wintun.dll") {
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
fn is_running_as_admin() -> bool {
    hidden_command("net")
        .arg("session")
        .status()
        .map(|status| status.success())
        .unwrap_or(false)
}

#[cfg(not(windows))]
fn is_running_as_admin() -> bool {
    false
}

#[cfg(windows)]
fn relaunch_as_admin() -> Result<(), String> {
    let exe = std::env::current_exe()
        .map_err(|e| format!("Не удалось найти путь Nimbo: {e}"))?;
    let escaped = exe.to_string_lossy().replace('\'', "''");
    hidden_command("powershell")
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(format!("Start-Process -FilePath '{escaped}' -Verb RunAs"))
        .status()
        .map_err(|e| format!("Не удалось перезапустить от имени администратора: {e}"))?;
    Ok(())
}

#[cfg(not(windows))]
fn relaunch_as_admin() -> Result<(), String> {
    Err("Перезапуск от имени администратора доступен только на Windows.".into())
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
        "#7c5dfa".into()
    }
}

#[cfg(windows)]
fn is_launch_at_login_enabled() -> Result<bool, String> {
    use winreg::enums::{HKEY_CURRENT_USER, KEY_READ};
    use winreg::RegKey;

    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let key = match hkcu.open_subkey_with_flags(
        r"Software\Microsoft\Windows\CurrentVersion\Run",
        KEY_READ,
    ) {
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
    fn subscription_xray_template_urls_keep_public_subscription_query() {
        let urls = subscription_xray_template_urls(
            "https://example.com/api/sub/e37b73d0-ec4d-4b49-b4cd-86685d6703ee?token=abc",
        );

        assert!(urls.iter().any(|url| url
            == "https://example.com/api/sub/e37b73d0-ec4d-4b49-b4cd-86685d6703ee/xray-json?token=abc"));
    }

    #[test]
    fn subscription_xray_template_urls_support_short_public_paths() {
        let urls = subscription_xray_template_urls("https://example.com/short-id?token=abc");

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
}
