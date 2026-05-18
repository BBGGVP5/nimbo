use std::collections::{HashMap, HashSet};
use std::fs::File;
#[cfg(all(windows, target_arch = "x86_64"))]
use std::io::Cursor;
use std::net::{IpAddr, Ipv4Addr, TcpStream, ToSocketAddrs};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, State};

use nimbo_device::{DeviceInfo, device_info, reset_cache};
use nimbo_ipc::PROTOCOL_VERSION;
use nimbo_subscription::{
    FetchOptions, Fetched, HAPP_COMPAT_DEVICE_MODEL, HAPP_COMPAT_DEVICE_OS,
    HAPP_COMPAT_OS_VERSION, Server, Subscription, build_subscription, fetch_subscription,
    extract_xray_templates_from_value, happ_compatible_user_agent,
    parse_aggregate, parse_subscription_userinfo,
};
use nimbo_xray_config::{
    AppRoutingMode as XrayAppRoutingMode, AppRoutingRule as XrayAppRoutingRule, ConfigBuilder,
    ProxyPorts,
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
const REMNAWAVE_BASE_URL_ENV: &str = "REMNAWAVE_BASE_URL";
const REMNAWAVE_API_TOKEN_ENV: &str = "REMNAWAVE_API_TOKEN";
const REMNAWAVE_API_KEY_ENV: &str = "REMNAWAVE_API_KEY";
const REMNAWAVE_PROFILE_UUIDS_ENV: &str = "REMNAWAVE_CONFIG_PROFILE_UUIDS";
const NIMBO_REMNAWAVE_ENV_ENV: &str = "NIMBO_REMNAWAVE_ENV";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppStatus {
    pub state: ConnectionState,
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
    preferences.latency_protocol = normalize_latency_protocol(&preferences.latency_protocol);
    preferences.latency_test_url = normalize_latency_test_url(&preferences.latency_test_url);
    preferences.latency_timeout_ms = normalize_latency_timeout_ms(preferences.latency_timeout_ms);
    preferences.latency_display_format =
        normalize_latency_display_format(&preferences.latency_display_format);
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

    let fetched = if is_remote_subscription(source) {
        let opts = build_fetch_options(&snapshot_before);
        fetch_subscription(source, &opts)
            .await
            .map_err(|e| format!("Не удалось загрузить: {e}"))?
    } else {
        let servers = parse_aggregate(source)
            .map_err(|e| format!("Не удалось распарсить конфиг: {e}"))?;
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
pub fn list_conflicting_processes() -> Result<Vec<ConflictingProcess>, String> {
    detect_conflicting_processes()
}

#[tauri::command]
pub fn stop_conflicting_processes(pids: Option<Vec<u32>>) -> Result<Vec<ConflictingProcess>, String> {
    let detected = detect_conflicting_processes()?;
    let requested = pids
        .unwrap_or_else(|| detected.iter().map(|process| process.pid).collect::<Vec<_>>())
        .into_iter()
        .collect::<HashSet<_>>();
    let safe_pids = detected
        .into_iter()
        .filter(|process| requested.contains(&process.pid))
        .map(|process| process.pid)
        .collect::<Vec<_>>();

    if safe_pids.is_empty() {
        return Ok(detect_conflicting_processes()?);
    }

    stop_conflicting_process_ids(&safe_pids)?;
    std::thread::sleep(std::time::Duration::from_millis(450));
    detect_conflicting_processes()
}

#[tauri::command]
pub fn get_app_icon(path: String) -> Option<String> {
    platform_get_app_icon(&path)
}

#[tauri::command]
pub fn pick_app_executable() -> Result<Option<String>, String> {
    platform_pick_app_executable()
}

const CONFLICTING_PROCESS_NAMES: &[&str] = &[
    "Cloudflare WARP",
    "CloudflareWARP",
    "warp",
    "warp-svc",
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
}

#[cfg(windows)]
fn detect_conflicting_processes() -> Result<Vec<ConflictingProcess>, String> {
    let names = CONFLICTING_PROCESS_NAMES
        .iter()
        .map(|name| format!("'{}'", name.replace('\'', "''")))
        .collect::<Vec<_>>()
        .join(",");
    let script = format!(
        r#"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$names = @({names})
$items = @()
foreach ($p in Get-Process -ErrorAction SilentlyContinue) {{
  if ($names -contains $p.ProcessName) {{
    $path = $null
    try {{ $path = $p.Path }} catch {{}}
    $items += [PSCustomObject]@{{
      ProcessName = $p.ProcessName
      Id = $p.Id
      Path = $path
    }}
  }}
}}
[Console]::Out.Write((ConvertTo-Json -InputObject $items -Compress -Depth 3))
"#
    );

    let output = hidden_output_command("powershell")
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(script)
        .output()
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

    let mut conflicts = raw
        .into_iter()
        .map(|process| ConflictingProcess {
            name: conflict_display_name(&process.process_name),
            process_name: process_exe_name(&process.process_name),
            pid: process.id,
            path: process
                .path
                .and_then(|path| {
                    let trimmed = path.trim().to_string();
                    if trimmed.is_empty() { None } else { Some(trimmed) }
                }),
        })
        .collect::<Vec<_>>();
    conflicts.sort_by(|a, b| {
        a.name
            .to_ascii_lowercase()
            .cmp(&b.name.to_ascii_lowercase())
            .then(a.process_name.cmp(&b.process_name))
            .then(a.pid.cmp(&b.pid))
    });
    conflicts.dedup_by_key(|process| process.pid);
    Ok(conflicts)
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
$ids = @({ids})
$errors = @()
foreach ($id in $ids) {{
  try {{
    Stop-Process -Id $id -Force -ErrorAction Stop
  }} catch {{
    $errors += ('{{0}}: {{1}}' -f $id, $_.Exception.Message)
  }}
}}
if ($errors.Count -gt 0) {{
  [Console]::Out.Write(($errors -join "`n"))
  exit 1
}}
"#
    );

    let output = hidden_output_command("powershell")
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(script)
        .output()
        .map_err(|e| format!("Не удалось выгрузить конфликтующие процессы: {e}"))?;

    if output.status.success() {
        return Ok(());
    }

    let details = String::from_utf8_lossy(&output.stdout).trim().to_string();
    if details.is_empty() {
        Err("Не удалось выгрузить конфликтующие процессы.".into())
    } else {
        Err(format!("Не удалось выгрузить конфликтующие процессы: {details}"))
    }
}

#[cfg(not(windows))]
fn stop_conflicting_process_ids(_pids: &[u32]) -> Result<(), String> {
    Ok(())
}

fn conflict_display_name(process_name: &str) -> String {
    match normalized_process_name(process_name).as_str() {
        "cloudflare warp" | "cloudflarewarp" | "warp" | "warp-svc" => {
            "Cloudflare WARP".to_string()
        }
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
    process_name
        .trim()
        .trim_end_matches(".exe")
        .to_ascii_lowercase()
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
        CreateCompatibleDC, CreateDIBSection, DeleteDC, DeleteObject, SelectObject,
        BI_RGB, BITMAPINFO, BITMAPINFOHEADER, DIB_RGB_COLORS,
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
        let fn_ptr = GetProcAddress(shell32, b"SHGetImageList\0".as_ptr() as *const i8);
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
        type GetIconFn =
            unsafe extern "system" fn(*mut c_void, i32, UINT, *mut HICON) -> i32;
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
        DrawIconEx(mem_dc, 0, 0, hicon, SIZE, SIZE, 0, std::ptr::null_mut(), DI_NORMAL);

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
    Ok(if selected.is_empty() { None } else { Some(selected) })
}

#[cfg(not(windows))]
fn platform_pick_app_executable() -> Result<Option<String>, String> {
    Ok(None)
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

fn simple_base64(data: &[u8]) -> String {
    const T: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity((data.len() + 2) / 3 * 4);
    for c in data.chunks(3) {
        let b0 = c[0] as usize;
        let b1 = if c.len() > 1 { c[1] as usize } else { 0 };
        let b2 = if c.len() > 2 { c[2] as usize } else { 0 };
        out.push(T[b0 >> 2] as char);
        out.push(T[((b0 & 3) << 4) | (b1 >> 4)] as char);
        out.push(if c.len() > 1 { T[((b1 & 15) << 2) | (b2 >> 6)] as char } else { '=' });
        out.push(if c.len() > 2 { T[b2 & 63] as char } else { '=' });
    }
    out
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
    let timeout_ms = normalize_latency_timeout_ms(snap.preferences.latency_timeout_ms);
    let result = tcp_ping_server(&server, timeout_ms).await;
    persist_ping_results(&state, std::slice::from_ref(&result))?;
    Ok(result)
}

#[tauri::command]
pub async fn ping_servers(
    state: State<'_, AppState>,
    server_ids: Vec<String>,
) -> Result<Vec<ServerPing>, String> {
    let snap = state.snapshot();
    let timeout_ms = normalize_latency_timeout_ms(snap.preferences.latency_timeout_ms);
    let mut handles = Vec::with_capacity(server_ids.len());
    for server_id in server_ids {
        if let Some(server) = find_server(&snap, &server_id) {
            handles.push(tokio::spawn(async move {
                tcp_ping_server(&server, timeout_ms).await
            }));
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
    }

    state
        .mutate(|s| {
            s.active_server_id = Some(server_id);
            s.active_subscription_url = Some(subscription_url);
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
    find_server_with_subscription(snapshot, server_id).map(|(_, server)| server)
}

fn find_server_with_subscription(
    snapshot: &PersistedState,
    server_id: &str,
) -> Option<(String, Server)> {
    snapshot
        .subscriptions
        .iter()
        .find_map(|sub| {
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
        cache.insert(namespaced_xray_template_key(subscription_url, &uuid), template);
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
            for (key, template) in fetch_public_xray_templates(&client, subscription_url, servers).await {
                out.entry(key).or_insert(template);
            }
        }
        Err(error) => {
            println!("xray template cache: client init failed: {error}");
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
    let remote =
        fetch_xray_templates_for_subscription(subscription_url, user_agent_override, &fetched.servers).await;
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
    insert_request_header(&mut headers, "X-Happ-Device-Os-Version", HAPP_COMPAT_OS_VERSION);
    insert_request_header(&mut headers, "X-Happ-Device-Model", HAPP_COMPAT_DEVICE_MODEL);
    insert_request_header(&mut headers, "X-Nimbo-User-Agent", app_user_agent);
    insert_request_header(&mut headers, "X-Client-User-Agent", app_user_agent);
    insert_request_header(&mut headers, "X-Client-Name", "Nimbo");
    insert_request_header(&mut headers, "X-Client-Version", env!("CARGO_PKG_VERSION"));
    insert_request_header(&mut headers, "X-Nimbo-Device-Os", &device.os);
    insert_request_header(&mut headers, "X-Nimbo-Device-Os-Version", &device.os_version);
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
            println!("xray template cache: Remnawave API client init failed: {error}");
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
        paths.push(home.join(".codex").join("mcp").join("mcp-remnawave").join(".env"));
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
    insert_request_header(&mut headers, "X-Happ-Device-Os-Version", HAPP_COMPAT_OS_VERSION);
    insert_request_header(&mut headers, "X-Happ-Device-Model", HAPP_COMPAT_DEVICE_MODEL);
    if let Ok(value) = reqwest::header::HeaderValue::from_str(&format!("Bearer {}", config.api_token))
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
        left.host_uuid.as_deref().map(str::trim).filter(|value| !value.is_empty()),
        right.host_uuid.as_deref().map(str::trim).filter(|value| !value.is_empty()),
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
    for base_path in [
        format!("/api/sub/{short_uuid}"),
        public_base,
    ] {
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
    let default_route = current_default_ipv4_route();
    let physical_ipv4 = default_route
        .as_ref()
        .and_then(|route| current_physical_ipv4(route.interface_index));
    let mut config = build_runtime_xray_config(&server, snapshot, ports)?;
    if let Some(ip) = physical_ipv4.as_deref() {
        patch_freedom_outbounds_send_through(&mut config, ip);
    }
    let config_path = write_xray_config(&config)?;
    let xray_path = ensure_xray_binary(app).await?;
    let tun_files = prepare_tun_runtime_files(app)?;
    let bypass_ips = resolve_server_ipv4s(&server).await;
    if bypass_ips.is_empty() {
        let (host, _) = server_endpoint(&server);
        return Err(format!(
            "Не удалось получить реальный IPv4 для сервера {host}. Если включен другой TUN/FakeDNS клиент, выключи его перед подключением Nimbo."
        ));
    }

    let mut xray = spawn_xray(&xray_path, &config_path)?;
    if let Err(error) = wait_for_xray_port(&mut xray, ports.socks) {
        let _ = xray.kill();
        let _ = xray.wait();
        return Err(error);
    }
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
    // MTU 1500 — standard Ethernet MTU. Jumbo frames (9000) cause fragmentation
    // and PMTU discovery failures on real network paths, especially over
    // Reality+Vision which doesn't reliably propagate ICMP Fragmentation Needed.
    // Happ and similar clients use 1500 (or 1420 for extra headroom).
    command
        .arg("-device")
        .arg(TUN_INTERFACE_NAME)
        .arg("-proxy")
        .arg(proxy)
        .arg("-mtu")
        .arg("1500")
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

// Local IPv4 of the physical interface that owns the default route. Used as
// `sendThrough` on the freedom outbound so direct traffic bypasses the TUN
// adapter — without this, xray's "direct" sockets get re-captured by the
// 0.0.0.0/1 + 128.0.0.0/1 routes pointing at the TUN, creating an infinite loop.
#[cfg(windows)]
fn current_physical_ipv4(interface_index: u32) -> Option<String> {
    let cmd = format!(
        "$ip = Get-NetIPAddress -InterfaceIndex {interface_index} -AddressFamily IPv4 -ErrorAction SilentlyContinue | \
         Where-Object {{ $_.IPAddress -and $_.IPAddress -notlike '169.254.*' }} | \
         Select-Object -First 1; \
         if ($ip) {{ Write-Output $ip.IPAddress }}"
    );
    let output = hidden_output_command("powershell")
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(cmd)
        .output()
        .ok()?;
    if !output.status.success() {
        return None;
    }
    let ip = String::from_utf8_lossy(&output.stdout).trim().to_string();
    if ip.is_empty() || ip.starts_with("198.18.") {
        None
    } else {
        Some(ip)
    }
}

#[cfg(not(windows))]
fn current_physical_ipv4(_interface_index: u32) -> Option<String> {
    None
}

// Set `sendThrough` on every outbound that uses the `freedom` protocol (i.e. the
// "direct" path). Bypasses TUN by binding the socket to the physical adapter IP.
fn patch_freedom_outbounds_send_through(config: &mut serde_json::Value, send_through: &str) {
    let Some(outbounds) = config
        .get_mut("outbounds")
        .and_then(serde_json::Value::as_array_mut)
    else {
        return;
    };
    for outbound in outbounds {
        let Some(obj) = outbound.as_object_mut() else {
            continue;
        };
        if obj.get("protocol").and_then(serde_json::Value::as_str) != Some("freedom") {
            continue;
        }
        obj.insert(
            "sendThrough".into(),
            serde_json::Value::String(send_through.to_string()),
        );
    }
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
            let is_a_record =
                answer.get("type").and_then(serde_json::Value::as_u64) == Some(1);
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

    let Some(template) = template else {
        return Ok(base_value);
    };

    apply_xray_template(template.clone(), base_value, server)
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
        if let Some(template) = snapshot
            .xray_templates
            .get(&namespaced_xray_template_key(subscription_url, DEFAULT_XRAY_TEMPLATE_KEY))
        {
            return Some(template);
        }
    }
    for key in &lookup_keys {
        if let Some(template) = snapshot.xray_templates.get(key) {
            return Some(template);
        }
    }
    snapshot.xray_templates.get(DEFAULT_XRAY_TEMPLATE_KEY).or_else(|| {
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
        if rules_arr.iter().any(|existing| routing_rules_overlap(existing, &rule)) {
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
        if rule.get("balancerTag").and_then(serde_json::Value::as_str).is_some() {
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
        .is_some_and(|items| items.iter().any(|item| item.as_str() == Some("geoip:private")));
    let is_private_domain = rule
        .get("domain")
        .and_then(serde_json::Value::as_array)
        .is_some_and(|items| items.iter().any(|item| item.as_str() == Some("geosite:private")));
    is_private_ip || is_private_domain
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
    let log_path = nimbo_data_dir()?.join("runtime").join("xray.log");
    if let Some(parent) = log_path.parent() {
        std::fs::create_dir_all(parent)
            .map_err(|e| format!("Не удалось создать папку логов Xray: {e}"))?;
    }
    let stdout = File::create(&log_path)
        .map_err(|e| format!("Не удалось создать лог Xray: {e}"))?;
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
    let _ = flush_dns_cache();

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

#[allow(dead_code)]
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

fn normalize_latency_protocol(value: &str) -> String {
    match value.trim() {
        "tcp_connect" => "tcp_connect".into(),
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
    fn runtime_config_preserves_remnawave_proxy_pool_and_balancer_rules() {
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
        assert!(outbounds.iter().any(|outbound| outbound["tag"] == "proxy-2"));
        assert!(config["routing"].get("balancers").is_some());
        assert!(rules.iter().any(|rule| {
            rule.get("domain")
                .and_then(serde_json::Value::as_array)
                .map(|domains| domains.iter().any(|domain| domain == "domain:example.org"))
                .unwrap_or(false)
        }));
        assert!(rules.iter().any(|rule| rule.get("balancerTag") == Some(&json!("BALANCER"))));
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

        let templates = extract_xray_templates_with_server_keys(&body, &[server.clone()]);
        let mut snapshot = PersistedState::default();
        snapshot
            .xray_templates
            .insert(server_xray_template_key(&server.id), templates[&server_xray_template_key(&server.id)].clone());

        let config = build_runtime_xray_config(&server, &snapshot, ProxyPorts::default()).unwrap();
        let rules = config["routing"]["rules"].as_array().unwrap();

        assert!(rules.iter().any(|rule| {
            rule.get("domain")
                .and_then(serde_json::Value::as_array)
                .map(|domains| domains.iter().any(|domain| domain == "domain:from-remnawave.example"))
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

        let config = build_runtime_xray_config(&server_b, &snapshot, ProxyPorts::default()).unwrap();
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
}
