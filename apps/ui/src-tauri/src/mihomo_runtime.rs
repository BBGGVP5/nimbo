//! Desktop ownership adapter for the shared native wire API.
//! Linux TUN is delegated to a pinned root broker; no GUI elevation or Xray conversion.
use crate::commands::{CONNECTION_INTENT, CONNECTION_OPERATION};
use crate::state::{AppState, ConnectionMode, PersistedState};
use nimbo_mihomo::{
    process::Session,
    wire::{Snapshot, VerifiedBinary},
    CoreKind, FullProfile, ProfileKind, ProfileSummary,
};
use serde::Serialize;
use serde_json::Value;
use std::{path::Path, sync::atomic::Ordering};
use tauri::{AppHandle, Manager, State};

fn binary(app: &AppHandle) -> Result<VerifiedBinary, String> {
    let expected = option_env!("NIMBO_MIHOMO_SHA256").unwrap_or("");
    if expected.is_empty() {
        return Err("CORE_UNAVAILABLE".into());
    }
    let platform = option_env!("NIMBO_MIHOMO_PLATFORM").unwrap_or("");
    let name = if cfg!(windows) {
        "nimbo-mihomo.exe"
    } else {
        "nimbo-mihomo"
    };
    let relative = Path::new("mihomo").join(platform).join(name);
    let mut paths = Vec::new();
    if let Ok(root) = app.path().resource_dir() {
        paths.push(root.join("resources").join(&relative));
        paths.push(root.join(&relative));
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(root) = exe.parent() {
            paths.push(root.join("resources").join(&relative));
        }
    }
    #[cfg(debug_assertions)]
    paths.push(
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("resources")
            .join(relative),
    );
    if let Some(binary) = paths
        .into_iter()
        .find_map(|p| VerifiedBinary::verify(&p, expected).ok())
    {
        return Ok(binary);
    }
    #[cfg(target_os = "linux")]
    {
        let resource = app.path().resource_dir().map_err(|_| "CORE_UNAVAILABLE")?;
        let cache = app
            .path()
            .app_cache_dir()
            .map_err(|_| "CORE_UNAVAILABLE")?
            .join("verified-mihomo")
            .join(expected);
        for root in [resource.join("resources/mihomo"), resource.join("mihomo")] {
            let archive = root.join(platform).join("nimbo-mihomo.zip");
            if let Ok(path) = crate::helper_linux::materialize_mihomo(&archive, &cache, expected) {
                if let Ok(binary) = VerifiedBinary::verify(&path, expected) {
                    return Ok(binary);
                }
            }
        }
    }
    Err("CORE_UNAVAILABLE".into())
}
#[derive(Serialize)]
pub struct CoreAvailability {
    core: CoreKind,
    selector_available: bool,
    binary_verified: bool,
    inspect_available: bool,
    system_proxy_available: bool,
    tun_available: bool,
    reason: Option<String>,
}
#[tauri::command]
pub fn get_core_availability(app: AppHandle) -> Vec<CoreAvailability> {
    let xray_supported = crate::xray_release::current().is_ok();
    let xray = crate::xray_release::current()
        .ok()
        .and_then(|asset| {
            crate::commands::xray_candidate_paths(&app)
                .ok()
                .and_then(|paths| {
                    let custom = std::env::var_os("NIMBO_XRAY_PATH").map(std::path::PathBuf::from);
                    asset
                        .select_runtime(custom.as_deref(), &paths)
                        .ok()
                        .flatten()
                })
        })
        .is_some();
    let awg = crate::awg_runtime::binary(&app).is_ok();
    let tun = crate::commands::get_tun_status(app.clone())
        .is_ok_and(|status| status.installed && !status.needs_admin_restart);
    let mihomo = binary(&app).is_ok();
    #[cfg(target_os = "linux")]
    let mihomo_tun =
        nimbo_mihomo::helper::available(option_env!("NIMBO_MIHOMO_SHA256").unwrap_or(""));
    #[cfg(not(target_os = "linux"))]
    let mihomo_tun = false;
    vec![
        CoreAvailability {
            core: CoreKind::Xray,
            // The existing Xray path can install its pinned runtime on connect.
            selector_available: xray_supported,
            binary_verified: xray,
            inspect_available: false,
            system_proxy_available: xray && cfg!(windows),
            tun_available: xray && tun,
            reason: (!xray).then(|| "PINNED_CORE_NOT_INSTALLED".into()),
        },
        CoreAvailability {
            core: CoreKind::Awg,
            selector_available: awg && xray_supported,
            binary_verified: awg,
            inspect_available: false,
            system_proxy_available: awg && xray && cfg!(windows),
            tun_available: awg && xray && tun,
            reason: (!(awg && xray)).then(|| "AWG_REQUIRES_VERIFIED_ADAPTER_AND_XRAY".into()),
        },
        CoreAvailability {
            core: CoreKind::Mihomo,
            selector_available: mihomo && cfg!(any(windows, target_os = "linux")),
            binary_verified: mihomo,
            inspect_available: mihomo,
            system_proxy_available: mihomo && cfg!(windows),
            tun_available: mihomo && mihomo_tun,
            reason: if !mihomo {
                Some("CORE_UNAVAILABLE".into())
            } else if cfg!(target_os = "linux") {
                (!mihomo_tun).then(|| "MIHOMO_HELPER_REQUIRED".into())
            } else {
                Some("MANAGED_PROXY_ONLY_TUN_DNS_KS_UNAVAILABLE".into())
            },
        },
    ]
}
#[tauri::command]
pub fn get_core_profiles(state: State<'_, AppState>) -> Value {
    let s = state.snapshot();
    serde_json::json!({"preferred_core":nimbo_mihomo::selection::CorePreference::from(s.core_profiles.preferred_core),"active_profile_id":s.core_profiles.active_profile_id,
        "profiles":s.core_profiles.profiles.iter().map(FullProfile::summary).collect::<Vec<_>>()})
}
#[derive(Serialize)]
pub struct ImportResult {
    profile: ProfileSummary,
    inspection_error: Option<String>,
}
#[tauri::command]
pub async fn import_mihomo_profile(
    app: AppHandle,
    state: State<'_, AppState>,
    name: String,
    source: String,
) -> Result<ImportResult, String> {
    let mut profile =
        FullProfile::new(name, ProfileKind::MihomoYaml, source).map_err(String::from)?;
    let inspection_error = match binary(&app) {
        Ok(bin) => match nimbo_mihomo::process::inspect(&bin, &profile).await {
            Ok(i) => {
                profile.attach_inspection(i).map_err(String::from)?;
                None
            }
            Err(e) => Some(e),
        },
        Err(e) => Some(e),
    };
    let summary = profile.summary();
    state.transaction(|s| {
        if s.core_profiles.profiles.len() >= 1024 {
            return Err("TOO_MANY_PROFILES".into());
        }
        s.core_profiles.profiles.push(profile);
        Ok(())
    })?;
    Ok(ImportResult {
        profile: summary,
        inspection_error,
    })
}
#[tauri::command]
pub async fn import_mihomo_profile_url(
    app: AppHandle,
    state: State<'_, AppState>,
    name: String,
    url: String,
) -> Result<ImportResult, String> {
    // Neither URL nor headers are persisted; import never changes the active core.
    let source = nimbo_mihomo::download::fetch_source(&url)
        .await
        .map_err(String::from)?;
    import_mihomo_profile(app, state, name, source).await
}
#[tauri::command]
pub fn export_core_profile(
    state: State<'_, AppState>,
    profile_id: String,
) -> Result<String, String> {
    let s = state.snapshot();
    let p = s.core_profiles.profile(&profile_id).map_err(String::from)?;
    p.verify().map_err(String::from)?;
    Ok(p.original_text.clone())
}
#[tauri::command]
pub async fn replace_core_profile(
    state: State<'_, AppState>,
    profile_id: String,
    revision: u64,
    source: String,
) -> Result<ProfileSummary, String> {
    let _lock = CONNECTION_OPERATION.lock().await;
    state.transaction(|s| {
        if s.connected && s.core_profiles.active_profile_id.as_deref() == Some(&profile_id) {
            return Err("PROFILE_ACTIVE".into());
        }
        let p = s
            .core_profiles
            .profile_mut(&profile_id)
            .map_err(String::from)?;
        p.replace_source(revision, source).map_err(String::from)?;
        Ok(p.summary())
    })
}
#[tauri::command]
pub async fn inspect_core_profile(
    app: AppHandle,
    state: State<'_, AppState>,
    profile_id: String,
) -> Result<ProfileSummary, String> {
    let p = state
        .snapshot()
        .core_profiles
        .profile(&profile_id)
        .map_err(String::from)?
        .clone();
    let i = nimbo_mihomo::process::inspect(&binary(&app)?, &p).await?;
    state.transaction(|s| {
        let current = s
            .core_profiles
            .profile_mut(&profile_id)
            .map_err(String::from)?;
        if current.revision != p.revision {
            return Err("STALE_REVISION".into());
        }
        current.attach_inspection(i).map_err(String::from)?;
        Ok(current.summary())
    })
}
#[tauri::command]
pub async fn remove_core_profile(
    state: State<'_, AppState>,
    profile_id: String,
) -> Result<(), String> {
    let _lock = CONNECTION_OPERATION.lock().await;
    state.transaction(|s| {
        if s.connected && s.core_profiles.active_profile_id.as_deref() == Some(&profile_id) {
            return Err("PROFILE_ACTIVE".into());
        }
        s.core_profiles.profile(&profile_id).map_err(String::from)?;
        s.core_profiles.profiles.retain(|p| p.id != profile_id);
        if s.core_profiles.active_profile_id.as_deref() == Some(&profile_id) {
            s.core_profiles.active_profile_id = None;
        }
        Ok(())
    })
}
#[tauri::command]
pub async fn set_core_preference(
    state: State<'_, AppState>,
    core: Option<nimbo_mihomo::selection::CorePreference>,
) -> Result<(), String> {
    let _lock = CONNECTION_OPERATION.lock().await;
    state.transaction(|s| {
        // Preference applies to the next connection. Do not touch the active
        // profile, processes, network ownership or connection intent here.
        if s.connected || s.auto_subscription_url.is_some() {
            s.session_core_preference
                .get_or_insert(nimbo_mihomo::selection::CorePreference::from(
                    s.core_profiles.preferred_core,
                ));
        }
        s.core_profiles.preferred_core = core.unwrap_or_default().core();
        Ok(())
    })
}
#[derive(Serialize)]
pub struct RuntimeStatus {
    pub running: bool,
    pub profile_id: Option<String>,
    pub session_id: Option<String>,
    pub native_generation: Option<u64>,
    pub mixed_address: Option<String>,
    pub network_owner: String,
}
#[tauri::command]
pub fn get_mihomo_status(state: State<'_, AppState>) -> RuntimeStatus {
    state.runtime(|r| match r.mihomo.as_mut() {
        Some(m) => RuntimeStatus {
            running: m.is_running(),
            profile_id: Some(m.profile_id.clone()),
            session_id: Some(m.session_id.clone()),
            native_generation: Some(m.controller().generation()),
            mixed_address: Some(m.info.mixed_address.clone()),
            network_owner: m.info.network_owner.clone(),
        },
        None => RuntimeStatus {
            running: false,
            profile_id: None,
            session_id: None,
            native_generation: None,
            mixed_address: None,
            network_owner: "none".into(),
        },
    })
}
fn session_controller(
    state: &AppState,
    profile_id: &str,
    session_id: &str,
) -> Result<nimbo_mihomo::controller::Controller, String> {
    state.runtime(|r| {
        let m = r.mihomo.as_mut().ok_or("NOT_RUNNING")?;
        if !m.owns(profile_id, session_id) {
            return Err("STALE_GENERATION".into());
        }
        if !m.is_running() {
            return Err("CORE_EXITED".into());
        }
        Ok(m.controller())
    })
}
pub fn check_network_mode(mode: ConnectionMode, kill_switch: bool) -> Result<(), String> {
    if cfg!(target_os = "linux") && mode == ConnectionMode::Tun {
        return if kill_switch {
            Err("MIHOMO_KILL_SWITCH_UNAVAILABLE".into())
        } else {
            Ok(())
        };
    }
    nimbo_mihomo::selection::ensure_mihomo_network(
        mode == ConnectionMode::SystemProxy,
        kill_switch,
        cfg!(windows),
    )
}

pub(crate) fn preflight_profile(
    snapshot: &PersistedState,
    profile_id: &str,
) -> Result<FullProfile, String> {
    let profile = snapshot
        .core_profiles
        .profile(profile_id)
        .map_err(String::from)?;
    nimbo_mihomo::selection::ensure_compatible(
        snapshot.core_profiles.preferred_core,
        profile.kind.required_core(),
    )?;
    check_network_mode(
        snapshot.connection_mode,
        snapshot.preferences.connection_kill_switch,
    )?;
    if profile.kind != ProfileKind::MihomoYaml {
        return Err("UNSUPPORTED_CORE".into());
    }
    profile.verify().map_err(String::from)?;
    Ok(profile.clone())
}
/// Release the connection owner promptly when disconnect/switch cancels a slow
/// provider/delay call. Dropping a start future reaps its owned subprocess.
async fn await_current<T>(
    ticket: u64,
    intent: &std::sync::atomic::AtomicU64,
    operation: impl std::future::Future<Output = Result<T, String>>,
) -> Result<T, String> {
    if intent.load(Ordering::SeqCst) != ticket {
        return Err("CONNECTION_CANCELLED".into());
    }
    tokio::select! {
        result=operation=>{
            if intent.load(Ordering::SeqCst)!=ticket{Err("CONNECTION_CANCELLED".into())}else{result}
        },
        _=async{while intent.load(Ordering::SeqCst)==ticket{tokio::time::sleep(std::time::Duration::from_millis(25)).await;}}=>Err("CONNECTION_CANCELLED".into()),
    }
}

#[tauri::command]
pub async fn connect_mihomo_profile(
    app: AppHandle,
    state: State<'_, AppState>,
    profile_id: String,
) -> Result<RuntimeStatus, String> {
    let snapshot = state.snapshot();
    preflight_profile(&snapshot, &profile_id)?;
    binary(&app)?;
    let ticket = CONNECTION_INTENT.fetch_add(1, Ordering::SeqCst) + 1;
    let _lock = CONNECTION_OPERATION.lock().await;
    let snapshot = state.snapshot();
    let result = connect_profile_inner(app, state.clone(), profile_id, snapshot, ticket).await?;
    crate::on_demand::manual_connected(&state, ticket)?;
    Ok(result)
}

#[tauri::command]
pub async fn prepare_mihomo_tun(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    let _lock = CONNECTION_OPERATION.lock().await;
    if state.snapshot().connected {
        return Err("DISCONNECT_BEFORE_CORE_CHANGE".into());
    }
    #[cfg(target_os = "linux")]
    {
        let binary = binary(&app)?;
        tokio::task::spawn_blocking(move || {
            crate::helper_linux::install_mihomo(&app, binary.path())
        })
        .await
        .map_err(|_| "HELPER_INSTALL_FAILED")??;
        if !nimbo_mihomo::helper::available(option_env!("NIMBO_MIHOMO_SHA256").unwrap_or("")) {
            return Err("MIHOMO_HELPER_REQUIRED".into());
        }
        Ok(())
    }
    #[cfg(not(target_os = "linux"))]
    {
        let _ = app;
        Err("MIHOMO_TUN_UNAVAILABLE".into())
    }
}
#[cfg(target_os = "linux")]
async fn connect_tun_inner(
    app: AppHandle,
    state: State<'_, AppState>,
    profile: FullProfile,
    bin: VerifiedBinary,
    snapshot: PersistedState,
    ticket: u64,
) -> Result<RuntimeStatus, String> {
    // Root/source admission completes before touching a still-working session.
    await_current(
        ticket,
        &CONNECTION_INTENT,
        nimbo_mihomo::helper::preflight(nimbo_mihomo::process::tun_request(&bin, &profile, false)?),
    )
    .await?;
    crate::commands::stop_runtime(&state)?;
    state.transaction(|s| {
        s.connected = false;
        s.connected_at = None;
        Ok(())
    })?;
    let session = await_current(
        ticket,
        &CONNECTION_INTENT,
        Session::start_tun(&bin, &profile, false),
    )
    .await?;
    state.transaction(|s| {
        if CONNECTION_INTENT.load(Ordering::SeqCst) != ticket {
            return Err("CONNECTION_CANCELLED".into());
        }
        if s.connection_mode != ConnectionMode::Tun || s.preferences.connection_kill_switch {
            return Err("CONNECTION_CANCELLED".into());
        }
        if s.core_profiles
            .profile(&profile.id)
            .map_err(String::from)?
            .source_digest
            != profile.source_digest
        {
            return Err("STALE_REVISION".into());
        }
        s.connected = true;
        s.connected_at = Some(
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_millis() as u64,
        );
        s.active_server_id = None;
        s.auto_subscription_url = None;
        s.core_profiles.active_profile_id = Some(profile.id.clone());
        s.session_core_preference = Some(nimbo_mihomo::selection::CorePreference::from(
            snapshot.core_profiles.preferred_core,
        ));
        Ok(())
    })?;
    state.runtime(|r| {
        r.mihomo = Some(session);
    });
    let _ = crate::tray::refresh_tray_menu(&app);
    Ok(get_mihomo_status(state))
}

/// Caller owns CONNECTION_OPERATION. Lifecycle restoration supplies its durable
/// session snapshot; manual connections supply the pending preference snapshot.
pub(crate) async fn connect_profile_inner(
    app: AppHandle,
    state: State<'_, AppState>,
    profile_id: String,
    snapshot: PersistedState,
    ticket: u64,
) -> Result<RuntimeStatus, String> {
    let profile = preflight_profile(&snapshot, &profile_id)?;
    let bin = binary(&app)?;
    #[cfg(target_os = "linux")]
    if snapshot.connection_mode == ConnectionMode::Tun {
        return connect_tun_inner(app, state, profile, bin, snapshot, ticket).await;
    }
    // Reject unsupported YAML features before disrupting the current runtime.
    let inspection = await_current(
        ticket,
        &CONNECTION_INTENT,
        nimbo_mihomo::process::inspect(&bin, &profile),
    )
    .await?;
    if !inspection.issues.is_empty() {
        return Err("UNSUPPORTED_FIELD".into());
    }
    if CONNECTION_INTENT.load(Ordering::SeqCst) != ticket {
        return Err("CONNECTION_CANCELLED".into());
    }
    crate::commands::stop_runtime(&state)?;
    state.transaction(|s| {
        s.connected = false;
        s.connected_at = None;
        Ok(())
    })?;
    let data_dir = crate::commands::nimbo_data_dir()?
        .join("mihomo")
        .join(&profile.id)
        .join(&profile.source_digest);
    let session = await_current(
        ticket,
        &CONNECTION_INTENT,
        Session::start(&bin, &profile, &data_dir),
    )
    .await?;
    if CONNECTION_INTENT.load(Ordering::SeqCst) != ticket {
        return Err("CONNECTION_CANCELLED".into());
    }
    let port = nimbo_mihomo::wire::loopback_address(&session.info.mixed_address)?.port();
    // Native owns proxy/provider sockets only. Nimbo owns exactly this OS proxy snapshot.
    let proxy = Some(crate::mihomo_proxy::snapshot()?);
    // Durable recovery journal precedes the first host mutation.
    state.transaction(|s| {
        check_network_mode(s.connection_mode, s.preferences.connection_kill_switch)?;
        if CONNECTION_INTENT.load(Ordering::SeqCst) != ticket {
            return Err("CONNECTION_CANCELLED".into());
        }
        s.pending_system_proxy_snapshot = proxy.clone();
        s.pending_mihomo_proxy_port = Some(port);
        Ok(())
    })?;
    if let Err(error) = crate::mihomo_proxy::apply(port) {
        if crate::mihomo_proxy::restore(proxy.clone()).is_ok() {
            let _ = state.transaction(|s| {
                s.pending_system_proxy_snapshot = None;
                s.pending_mihomo_proxy_port = None;
                Ok(())
            });
        }
        return Err(error);
    }
    if let Err(error) = state.transaction(|s| {
        if CONNECTION_INTENT.load(Ordering::SeqCst) != ticket {
            return Err("CONNECTION_CANCELLED".into());
        }
        if s.core_profiles
            .profile(&profile_id)
            .map_err(String::from)?
            .source_digest
            != profile.source_digest
        {
            return Err("STALE_REVISION".into());
        }
        s.pending_system_proxy_snapshot = proxy.clone();
        s.pending_mihomo_proxy_port = Some(port);
        s.connected = true;
        s.connected_at = Some(
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_millis() as u64,
        );
        s.active_server_id = None;
        s.auto_subscription_url = None;
        s.core_profiles.active_profile_id = Some(profile_id);
        s.session_core_preference = Some(nimbo_mihomo::selection::CorePreference::from(
            snapshot.core_profiles.preferred_core,
        ));
        Ok(())
    }) {
        if crate::mihomo_proxy::restore(proxy).is_ok() {
            let _ = state.transaction(|s| {
                s.pending_system_proxy_snapshot = None;
                s.pending_mihomo_proxy_port = None;
                Ok(())
            });
        }
        return Err(error);
    }
    state.runtime(|r| {
        r.system_proxy_snapshot = proxy;
        r.mihomo = Some(session);
    });
    let _ = crate::tray::refresh_tray_menu(&app);
    Ok(get_mihomo_status(state))
}
#[tauri::command]
pub async fn mihomo_snapshot(
    state: State<'_, AppState>,
    profile_id: String,
    session_id: String,
) -> Result<Snapshot, String> {
    let ticket = CONNECTION_INTENT.load(Ordering::SeqCst);
    let _lock = CONNECTION_OPERATION.lock().await;
    let c = session_controller(&state, &profile_id, &session_id)?;
    await_current(ticket, &CONNECTION_INTENT, c.snapshot()).await
}
#[tauri::command]
pub async fn mihomo_select(
    state: State<'_, AppState>,
    profile_id: String,
    session_id: String,
    group: String,
    name: String,
) -> Result<Snapshot, String> {
    let ticket = CONNECTION_INTENT.load(Ordering::SeqCst);
    let _lock = CONNECTION_OPERATION.lock().await;
    let c = session_controller(&state, &profile_id, &session_id)?;
    let snapshot = await_current(ticket, &CONNECTION_INTENT, c.select(&group, &name)).await?;
    // Selection is persisted only after the native response confirms actual 'now'.
    state.transaction(|s| {
        if CONNECTION_INTENT.load(Ordering::SeqCst) != ticket {
            return Err("CONNECTION_CANCELLED".into());
        }
        s.core_profiles
            .profile_mut(&profile_id)
            .map_err(String::from)?
            .selections
            .insert(group, name);
        Ok(())
    })?;
    Ok(snapshot)
}
#[tauri::command]
pub async fn mihomo_refresh_provider(
    state: State<'_, AppState>,
    profile_id: String,
    session_id: String,
    name: String,
) -> Result<Snapshot, String> {
    let ticket = CONNECTION_INTENT.load(Ordering::SeqCst);
    let _lock = CONNECTION_OPERATION.lock().await;
    // Native bridge performs strict atomic replacement. Never bypass it via REST.
    let c = session_controller(&state, &profile_id, &session_id)?;
    await_current(ticket, &CONNECTION_INTENT, c.refresh_provider(&name)).await
}
#[tauri::command]
pub async fn mihomo_refresh_rule_provider(
    state: State<'_, AppState>,
    profile_id: String,
    session_id: String,
    name: String,
) -> Result<Snapshot, String> {
    let ticket = CONNECTION_INTENT.load(Ordering::SeqCst);
    let _lock = CONNECTION_OPERATION.lock().await;
    let c = session_controller(&state, &profile_id, &session_id)?;
    await_current(ticket, &CONNECTION_INTENT, c.refresh_rule_provider(&name)).await
}
#[tauri::command]
pub async fn mihomo_delay(
    state: State<'_, AppState>,
    profile_id: String,
    session_id: String,
    name: String,
    url: String,
    timeout_ms: u64,
    expected_status: Option<String>,
) -> Result<Value, String> {
    let ticket = CONNECTION_INTENT.load(Ordering::SeqCst);
    let _lock = CONNECTION_OPERATION.lock().await;
    let c = session_controller(&state, &profile_id, &session_id)?;
    await_current(
        ticket,
        &CONNECTION_INTENT,
        c.delay(&name, &url, timeout_ms, expected_status.as_deref()),
    )
    .await
}
/// Called by existing periodic monitor, serialized with user connection operations.
/// Do not reconnect automatically: a failure must not resurrect cancelled intent.
pub async fn reconcile_process_exit(app: &AppHandle) {
    let Ok(_lock) = CONNECTION_OPERATION.try_lock() else {
        return;
    };
    let state = app.state::<AppState>();
    let failed = state.runtime(|r| r.mihomo.as_mut().is_some_and(|m| !m.is_running()));
    if failed {
        let _ = crate::commands::stop_runtime(&state);
        let _ = state.transaction(|s| {
            s.connected = false;
            s.connected_at = None;
            Ok(())
        });
        let _ = crate::tray::refresh_tray_menu(app);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn mihomo_preflight_preserves_connected_state_and_source_on_incompatible_mode() {
        let mut snapshot = PersistedState::default();
        let source = "# exact\r\nproxy-groups: [{name: Pick, type: select, proxies: [DIRECT]}]\r\nrules: [MATCH,Pick]\r\n";
        let profile =
            FullProfile::new("Fixture".into(), ProfileKind::MihomoYaml, source.into()).unwrap();
        let id = profile.id.clone();
        snapshot.core_profiles.profiles.push(profile);
        snapshot.connected = true;
        snapshot.active_server_id = Some("existing-xray".into());
        for mode in [ConnectionMode::Tun, ConnectionMode::Both] {
            snapshot.connection_mode = mode;
            assert!(preflight_profile(&snapshot, &id)
                .err()
                .unwrap()
                .contains("MIHOMO_TUN_UNAVAILABLE"));
        }
        snapshot.connection_mode = ConnectionMode::SystemProxy;
        snapshot.preferences.connection_kill_switch = true;
        assert!(preflight_profile(&snapshot, &id)
            .err()
            .unwrap()
            .contains("MIHOMO_KILL_SWITCH_UNAVAILABLE"));
        snapshot.preferences.connection_kill_switch = false;
        assert_eq!(preflight_profile(&snapshot, &id).is_ok(), cfg!(windows));
        snapshot.core_profiles.preferred_core = Some(CoreKind::Xray);
        assert!(preflight_profile(&snapshot, &id)
            .err()
            .unwrap()
            .starts_with("CORE_MISMATCH:"));
        assert!(snapshot.connected);
        assert_eq!(snapshot.active_server_id.as_deref(), Some("existing-xray"));
        assert_eq!(
            snapshot.core_profiles.profiles[0].original_text.as_bytes(),
            source.as_bytes()
        );
    }
    #[tokio::test]
    async fn cancelled_slow_operation_releases_owner_and_drops_future() {
        use std::sync::{
            atomic::{AtomicBool, AtomicU64},
            Arc,
        };
        struct Dropped(Arc<AtomicBool>);
        impl Drop for Dropped {
            fn drop(&mut self) {
                self.0.store(true, Ordering::SeqCst);
            }
        }
        let intent = Arc::new(AtomicU64::new(1));
        let next = intent.clone();
        let dropped = Arc::new(AtomicBool::new(false));
        let flag = dropped.clone();
        tokio::spawn(async move {
            tokio::time::sleep(std::time::Duration::from_millis(10)).await;
            next.store(2, Ordering::SeqCst);
        });
        let operation = async move {
            let _drop = Dropped(flag);
            std::future::pending::<Result<(), String>>().await
        };
        let result = tokio::time::timeout(
            std::time::Duration::from_secs(1),
            await_current(1, &intent, operation),
        )
        .await
        .unwrap();
        assert_eq!(result.unwrap_err(), "CONNECTION_CANCELLED");
        assert!(dropped.load(Ordering::SeqCst));
    }
    #[tokio::test]
    async fn stale_queued_operation_is_never_dispatched() {
        let intent = std::sync::atomic::AtomicU64::new(2);
        let future = async {
            panic!("stale operation dispatched");
            #[allow(unreachable_code)]
            Ok::<(), String>(())
        };
        assert_eq!(
            await_current(1, &intent, future).await.unwrap_err(),
            "CONNECTION_CANCELLED"
        );
    }
    #[test]
    fn host_network_modes_fail_closed_before_runtime_start() {
        if cfg!(target_os = "linux") {
            assert!(check_network_mode(ConnectionMode::Tun, false).is_ok());
        } else {
            assert_eq!(
                check_network_mode(ConnectionMode::Tun, false).unwrap_err(),
                "MIHOMO_TUN_UNAVAILABLE"
            );
        }
        assert_eq!(
            check_network_mode(ConnectionMode::Both, false).unwrap_err(),
            "MIHOMO_TUN_UNAVAILABLE"
        );
        assert_eq!(
            check_network_mode(ConnectionMode::SystemProxy, true).unwrap_err(),
            "MIHOMO_KILL_SWITCH_UNAVAILABLE"
        );
    }
}
