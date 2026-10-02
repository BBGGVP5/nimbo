mod network;
pub mod policy;

use crate::{
    commands::{self, CONNECTION_INTENT, CONNECTION_OPERATION},
    state::{AppState, PersistedState},
};
use nimbo_mihomo::selection::CorePreference;
use policy::{Action, Network, Schedule, Settings};
use serde::{Deserialize, Serialize};
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Mutex,
};
use std::time::{Duration, Instant};
use tauri::{AppHandle, Manager, State};

static PHASE: Mutex<&str> = Mutex::new("waiting");
static ALLOW_RECOVERY: AtomicBool = AtomicBool::new(false);

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum Target {
    Server { id: String, core: CorePreference },
    Profile { id: String, core: CorePreference },
}
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default)]
pub struct Config {
    pub settings: Settings,
    pub paused: bool,
    pub target: Option<Target>,
}

fn selected(snapshot: &PersistedState) -> Option<Target> {
    let core = snapshot.session_core_preference?;
    if let Some(id) = &snapshot.core_profiles.active_profile_id {
        Some(Target::Profile {
            id: id.clone(),
            core,
        })
    } else {
        snapshot.active_server_id.as_ref().map(|id| Target::Server {
            id: id.clone(),
            core,
        })
    }
}

fn preflight(app: &AppHandle, snapshot: &PersistedState, target: &Target) -> Result<(), String> {
    let mut snapshot = snapshot.clone();
    match target {
        Target::Server { id, core } => {
            snapshot.core_profiles.preferred_core = core.core();
            commands::preflight_server_connection(app, &snapshot, id)
        }
        Target::Profile { id, core } => {
            snapshot.core_profiles.preferred_core = core.core();
            crate::mihomo_runtime::preflight_profile(&snapshot, id).map(|_| ())
        }
    }
    .map_err(|_| "TARGET_UNAVAILABLE".into())
}

#[derive(Serialize)]
pub struct Status {
    settings: Settings,
    paused: bool,
    phase: String,
    target_name: Option<String>,
    supported: bool,
}
#[tauri::command]
pub fn get_on_demand(state: State<'_, AppState>) -> Status {
    state.read(|snapshot| {
        let config = &snapshot.on_demand;
        let target_name = config.target.as_ref().and_then(|target| match target {
            Target::Server { id, .. } => snapshot
                .subscriptions
                .iter()
                .flat_map(|sub| &sub.servers)
                .find(|s| &s.id == id)
                .map(|s| s.name.clone()),
            Target::Profile { id, .. } => snapshot
                .core_profiles
                .profiles
                .iter()
                .find(|p| &p.id == id)
                .map(|p| p.name.clone()),
        });
        let phase = if !config.settings.enabled {
            "disabled"
        } else if config.paused {
            "paused"
        } else {
            *PHASE.lock().unwrap_or_else(|e| e.into_inner())
        };
        Status {
            settings: config.settings.clone(),
            paused: config.paused,
            phase: phase.into(),
            target_name,
            supported: cfg!(any(windows, target_os = "linux")),
        }
    })
}

#[tauri::command]
pub async fn set_on_demand(
    app: AppHandle,
    state: State<'_, AppState>,
    settings: Settings,
) -> Result<Status, String> {
    let settings = settings.validated().map_err(String::from)?;
    if settings.enabled && !cfg!(any(windows, target_os = "linux")) {
        return Err("PLATFORM_UNAVAILABLE".into());
    }
    let before = state.snapshot();
    // A first opt-in must capture a successfully established session, not an
    // arbitrary profile ID sent by the WebView or an imported backup.
    let target = if settings.enabled {
        let target = (if before.connected {
            selected(&before)
        } else {
            None
        })
        .or_else(|| before.on_demand.target.clone())
        .ok_or("CONNECT_FIRST")?;
        preflight(&app, &before, &target)?;
        Some(target)
    } else {
        before.on_demand.target.clone()
    };
    let ticket = CONNECTION_INTENT.fetch_add(1, Ordering::SeqCst) + 1;
    let _operation = CONNECTION_OPERATION.lock().await;
    if CONNECTION_INTENT.load(Ordering::SeqCst) != ticket {
        return Err("SETTINGS_CANCELLED".into());
    }
    if let Some(target) = target.as_ref().filter(|_| settings.enabled) {
        preflight(&app, &state.snapshot(), target)?;
    }
    state.transaction(|s| {
        s.on_demand = Config {
            settings,
            paused: false,
            target,
        };
        Ok(())
    })?;
    ALLOW_RECOVERY.store(false, Ordering::SeqCst);
    set_phase("waiting");
    Ok(get_on_demand(state))
}

pub fn manual_pause(state: &AppState) -> Result<(), String> {
    ALLOW_RECOVERY.store(false, Ordering::SeqCst);
    if state.read(|s| s.on_demand.settings.enabled && !s.on_demand.paused) {
        state.transaction(|s| {
            s.on_demand.paused = true;
            Ok(())
        })?;
    }
    Ok(())
}
pub fn manual_connected(state: &AppState, ticket: u64) -> Result<(), String> {
    if state.read(|s| s.on_demand.settings.enabled) {
        state.transaction(|s| {
            if CONNECTION_INTENT.load(Ordering::SeqCst) != ticket {
                return Ok(());
            }
            s.on_demand.target = selected(s);
            s.on_demand.paused = false;
            Ok(())
        })?;
    }
    Ok(())
}
pub fn permits_recovery(snapshot: &PersistedState) -> bool {
    !snapshot.on_demand.settings.enabled
        || (!snapshot.on_demand.paused && ALLOW_RECOVERY.load(Ordering::SeqCst))
}
fn set_phase(value: &'static str) {
    *PHASE.lock().unwrap_or_else(|e| e.into_inner()) = value;
}

pub fn start_monitor(app: AppHandle) {
    tauri::async_runtime::spawn(async move {
        let mut schedule = Schedule::default();
        let mut previous: Option<(Config, u64)> = None;
        loop {
            tokio::time::sleep(Duration::from_secs(5)).await;
            let state = app.state::<AppState>();
            let config = state.read(|s| s.on_demand.clone());
            let ticket = CONNECTION_INTENT.load(Ordering::SeqCst);
            if previous.as_ref() != Some(&(config.clone(), ticket)) {
                schedule = Schedule::default();
                previous = Some((config.clone(), ticket));
                ALLOW_RECOVERY.store(false, Ordering::SeqCst);
            }
            if !config.settings.enabled || config.paused {
                continue;
            }
            let observation = network::observe(!config.settings.trusted_ssids.is_empty()).await;
            if ticket != CONNECTION_INTENT.load(Ordering::SeqCst)
                || state.read(|s| s.on_demand != config)
            {
                continue;
            }
            let action = policy::decide(&config.settings, config.paused, &observation);
            if action != Action::Connect {
                ALLOW_RECOVERY.store(false, Ordering::SeqCst);
            }
            if !schedule.observe(observation.clone(), Instant::now()) {
                continue;
            }
            if action == Action::Hold {
                set_phase(if observation == Network::Unknown {
                    "network_unavailable"
                } else {
                    "waiting"
                });
                continue;
            }
            let _operation = CONNECTION_OPERATION.lock().await;
            // A manual operation may have held the lock while the network changed.
            if network::observe(!config.settings.trusted_ssids.is_empty()).await != observation {
                continue;
            }
            let (unchanged, connected) = state.read(|s| (s.on_demand == config, s.connected));
            if ticket != CONNECTION_INTENT.load(Ordering::SeqCst) || !unchanged {
                continue;
            }
            ALLOW_RECOVERY.store(action == Action::Connect, Ordering::SeqCst);
            if action == Action::Disconnect {
                if connected {
                    if commands::stop_runtime(&state).is_err()
                        || state
                            .transaction(|s| {
                                s.connected = false;
                                s.connected_at = None;
                                s.auto_subscription_url = None;
                                Ok(())
                            })
                            .is_err()
                    {
                        set_phase("retry");
                        schedule.failed(Instant::now());
                        continue;
                    }
                    let _ = crate::tray::refresh_tray_menu(&app);
                }
                set_phase("trusted");
                continue;
            }
            if connected {
                let running = state.runtime(|r| {
                    if let Some(core) = r.mihomo.as_mut() {
                        core.is_running()
                    } else {
                        r.xray
                            .as_mut()
                            .is_some_and(|core| matches!(core.try_wait(), Ok(None)))
                            && !r.awg.as_mut().is_some_and(|core| core.has_exited())
                    }
                });
                if running {
                    schedule.succeeded();
                    set_phase("connected");
                    continue;
                }
                if commands::stop_runtime(&state).is_err()
                    || state
                        .transaction(|s| {
                            s.connected = false;
                            s.connected_at = None;
                            Ok(())
                        })
                        .is_err()
                {
                    set_phase("retry");
                    schedule.failed(Instant::now());
                    continue;
                }
            }
            let Some(target) = &config.target else {
                set_phase("no_target");
                continue;
            };
            let snapshot = state.snapshot();
            if preflight(&app, &snapshot, target).is_err() {
                set_phase("no_target");
                schedule.failed(Instant::now());
                continue;
            }
            set_phase("connecting");
            let result = match target {
                Target::Server { id, core } => commands::connect_server_inner(
                    app.clone(),
                    state.clone(),
                    id.clone(),
                    core.core(),
                )
                .await
                .map(|_| ()),
                Target::Profile { id, core } => {
                    let mut saved = snapshot;
                    saved.core_profiles.preferred_core = core.core();
                    crate::mihomo_runtime::connect_profile_inner(
                        app.clone(),
                        state.clone(),
                        id.clone(),
                        saved,
                        ticket,
                    )
                    .await
                    .map(|_| ())
                }
            };
            if result.is_err() {
                // We still own the operation lock. Clean only this failed start;
                // a newer manual command has not started its runtime yet.
                let _ = commands::stop_runtime(&state);
                let _ = state.transaction(|s| {
                    s.connected = false;
                    s.connected_at = None;
                    Ok(())
                });
                schedule.failed(Instant::now());
                set_phase("retry");
            } else {
                schedule.succeeded();
                set_phase("connected");
            }
            let _ = crate::tray::refresh_tray_menu(&app);
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn old_state_is_disabled_and_pause_survives_roundtrip() {
        let mut state: PersistedState = serde_json::from_str("{}").unwrap();
        assert!(!state.on_demand.settings.enabled);
        assert!(selected(&state).is_none());
        state.active_server_id = Some("node".into());
        assert!(selected(&state).is_none());
        state.session_core_preference = Some(CorePreference::Xray);
        state.on_demand.settings.enabled = true;
        state.on_demand.paused = true;
        state.on_demand.target = selected(&state);
        let restored: PersistedState =
            serde_json::from_slice(&serde_json::to_vec(&state).unwrap()).unwrap();
        assert_eq!(state.on_demand, restored.on_demand);
        assert!(!permits_recovery(&restored));
        assert_eq!(
            restored.on_demand.target,
            Some(Target::Server {
                id: "node".into(),
                core: CorePreference::Xray
            })
        );
    }
}
