use serde::{Deserialize, Serialize};
use tauri::State;

use nimbo_device::{DeviceInfo, device_info, reset_cache};
use nimbo_ipc::PROTOCOL_VERSION;
use nimbo_subscription::{
    FetchOptions, Subscription, build_subscription, fetch_subscription,
};

use crate::state::{AppState, PersistedState};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppStatus {
    pub state: ConnectionState,
    pub active_server_id: Option<String>,
    pub subscription_count: usize,
    pub server_count: usize,
    pub service_protocol: u32,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    ServiceUnavailable,
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
pub fn get_status(state: State<'_, AppState>) -> AppStatus {
    let snapshot = state.snapshot();
    let server_count: usize = snapshot.subscriptions.iter().map(|s| s.servers.len()).sum();
    AppStatus {
        state: ConnectionState::ServiceUnavailable,
        active_server_id: snapshot.active_server_id,
        subscription_count: snapshot.subscriptions.len(),
        server_count,
        service_protocol: PROTOCOL_VERSION,
    }
}

#[tauri::command]
pub fn list_subscriptions(state: State<'_, AppState>) -> Vec<Subscription> {
    state.snapshot().subscriptions
}

#[tauri::command]
pub async fn add_subscription(
    state: State<'_, AppState>,
    url: String,
    name: Option<String>,
) -> Result<Subscription, String> {
    let snapshot_before = state.snapshot();
    if snapshot_before.subscriptions.iter().any(|s| s.url == url) {
        return Err("Подписка с таким URL уже добавлена".into());
    }

    let opts = build_fetch_options(&snapshot_before);
    let fetched = fetch_subscription(&url, &opts)
        .await
        .map_err(|e| format!("Не удалось загрузить: {e}"))?;

    let subscription = build_subscription(&url, fetched, name);

    state
        .mutate(|s| s.subscriptions.push(subscription.clone()))
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

    let updated_name = state
        .mutate(|s| {
            let existing_name = s
                .subscriptions
                .iter()
                .find(|sub| sub.url == url)
                .and_then(|sub| sub.name.clone());
            let updated = build_subscription(&url, fetched.clone(), existing_name.clone());
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
pub fn remove_subscription(
    state: State<'_, AppState>,
    url: String,
) -> Result<PersistedState, String> {
    state
        .mutate(|s| {
            s.subscriptions.retain(|sub| sub.url != url);
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

fn build_fetch_options(state: &PersistedState) -> FetchOptions {
    let mut opts = FetchOptions::default();
    if let Some(ua) = &state.user_agent_override {
        opts.user_agent = Some(ua.clone());
    }
    opts
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
