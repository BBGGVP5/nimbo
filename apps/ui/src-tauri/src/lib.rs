pub mod commands;
pub mod cross_sync;
#[cfg(windows)]
pub mod helper;
#[cfg(target_os = "linux")]
pub mod helper_linux;
pub mod logging;
pub mod state;
pub mod tray;
pub mod updater;

use crate::commands::{
    add_subscription, app_ready, clear_tunnel_logs, connect_server, delete_routing_profile,
    disconnect_server, export_app_backup, export_app_proxy_rules_file, export_routing_profile,
    get_app_icon, get_device_info, get_memory_usage, get_preferences, get_routing_profile,
    get_run_through_nimbo_context_menu_enabled, get_session_traffic, get_status,
    get_subscription_logo, get_traffic_stats, get_tun_status, get_tunnel_logs,
    get_user_agent_override, helper_status, import_app_backup, import_routing_profile,
    inspect_subscription_headers, install_helper, install_tun, list_active_connections,
    list_app_proxy_rules, list_conflicting_processes, list_installed_apps, list_routing_profiles,
    list_subscription_app_proxy_rules, list_subscriptions, migrate_subscriptions, open_logs_folder,
    open_routing_folder, pick_app_executable, ping_server, ping_servers, read_clipboard_text,
    reapply_runtime_config, refresh_subscription, refresh_tray_menu, remove_subscription,
    reorder_subscriptions, reset_builtin_routing_profiles, reset_device_id, reset_traffic_totals,
    restart_as_admin, run_through_nimbo, set_active_routing_profile, set_active_server,
    set_active_subscription, set_app_proxy_rules, set_connection_mode, set_preferences,
    set_proxy_settings, set_run_through_nimbo_context_menu, set_user_agent_override,
    stop_conflicting_processes, uninstall_helper, update_routing_profile,
    update_subscription_settings, write_clipboard_text,
};
use crate::cross_sync::{
    cross_sync_accept_import, cross_sync_approve, cross_sync_cancel, cross_sync_list_devices,
    cross_sync_reject, cross_sync_remove_device, cross_sync_set_auto_sync, cross_sync_start,
    cross_sync_status, CrossSyncManager,
};
use crate::state::AppState;
use crate::tray::{tray_menu_action, tray_menu_resize, tray_menu_state};
use crate::updater::{
    check_app_update, dismiss_post_update_info, get_post_update_info, install_app_update,
    open_update_download,
};
use tauri::{AppHandle, Emitter, Manager, RunEvent, WindowEvent};
use tauri_plugin_deep_link::DeepLinkExt;

/// Shutting down stops Xray, restores the system proxy and rolls back routes —
/// all of it synchronous on the event-loop thread. Any window still on screen
/// while that runs is a frozen frame the compositor cannot repaint (a white
/// rectangle where the WebView used to be, plus the always-on-top tray flyout
/// hanging over the desktop). Take every window down first, then block.
fn hide_all_windows(app: &AppHandle) {
    for (_, window) in app.webview_windows() {
        let _ = window.hide();
    }
}

/// Native background for the main window, matched to the theme the WebView is
/// about to paint. Without it the window is white underneath, which shows up as
/// a flash while the frontend boots and as a white rectangle after the WebView
/// is released on shutdown.
pub fn apply_main_window_background(app: &AppHandle) {
    use tauri::utils::config::Color;

    let Some(window) = app.get_webview_window("main") else {
        return;
    };
    let theme = app.state::<AppState>().snapshot().preferences.theme_mode;
    let color = match theme {
        crate::state::ThemeMode::Light => Color(246, 245, 250, 255),
        crate::state::ThemeMode::Black => Color(4, 5, 10, 255),
        crate::state::ThemeMode::Dark => Color(11, 12, 18, 255),
        crate::state::ThemeMode::System => match window.theme() {
            Ok(tauri::Theme::Light) => Color(246, 245, 250, 255),
            _ => Color(11, 12, 18, 255),
        },
    };
    let _ = window.set_background_color(Some(color));
}

static EXIT_CLEANUP_DONE: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

/// `ExitRequested` and `Exit` both fire on a normal quit; the teardown is slow
/// enough that running it twice is visible, so let the first one win.
fn cleanup_once(app: &AppHandle) {
    if EXIT_CLEANUP_DONE.swap(true, std::sync::atomic::Ordering::SeqCst) {
        return;
    }
    hide_all_windows(app);
    crate::commands::cleanup_runtime_for_exit(app);
}

#[cfg(windows)]
struct SingleInstanceGuard(windows_sys::Win32::Foundation::HANDLE);

#[cfg(windows)]
unsafe impl Send for SingleInstanceGuard {}
#[cfg(windows)]
unsafe impl Sync for SingleInstanceGuard {}

#[cfg(windows)]
impl Drop for SingleInstanceGuard {
    fn drop(&mut self) {
        unsafe {
            let _ = windows_sys::Win32::Foundation::CloseHandle(self.0);
        }
    }
}

#[cfg(not(windows))]
struct SingleInstanceGuard;

#[cfg(windows)]
fn acquire_single_instance() -> Option<SingleInstanceGuard> {
    use std::ffi::OsStr;
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Foundation::{
        CloseHandle, GetLastError, ERROR_ACCESS_DENIED, ERROR_ALREADY_EXISTS,
    };
    use windows_sys::Win32::System::Threading::CreateMutexW;

    let name: Vec<u16> = OsStr::new("Local\\Nimbo.Ui.Singleton")
        .encode_wide()
        .chain(std::iter::once(0))
        .collect();
    let handle = unsafe { CreateMutexW(std::ptr::null(), 1, name.as_ptr()) };
    if handle.is_null() {
        if unsafe { GetLastError() } == ERROR_ACCESS_DENIED {
            eprintln!("Nimbo is already running; exiting duplicate instance");
            return None;
        }
        eprintln!(
            "failed to create Nimbo single-instance mutex: {}",
            std::io::Error::last_os_error()
        );
        return None;
    }
    if unsafe { GetLastError() } == ERROR_ALREADY_EXISTS {
        unsafe {
            let _ = CloseHandle(handle);
        }
        eprintln!("Nimbo is already running; exiting duplicate instance");
        return None;
    }
    Some(SingleInstanceGuard(handle))
}

#[cfg(not(windows))]
fn acquire_single_instance() -> Option<SingleInstanceGuard> {
    Some(SingleInstanceGuard)
}

pub fn handle_cli_args() -> bool {
    if std::env::args().any(|arg| arg == "--update-health-check") {
        if let Err(error) = crate::updater::run_update_health_check() {
            eprintln!("update health check failed: {error}");
            std::process::exit(1);
        }
        return true;
    }

    if std::env::args().any(|arg| arg == "--install-tun") {
        if let Err(error) = crate::commands::install_tun_dependencies_for_installer() {
            eprintln!("failed to install TUN dependencies: {error}");
            std::process::exit(1);
        }
        return true;
    }

    false
}

fn run_through_cli_path() -> Option<String> {
    let mut args = std::env::args().skip(1);
    while let Some(arg) = args.next() {
        if arg == "--run-through" {
            return args
                .next()
                .map(|value| value.trim().to_string())
                .filter(|value| !value.is_empty());
        }
    }
    None
}

fn run_through_queue_dir() -> std::path::PathBuf {
    dirs::data_local_dir()
        .unwrap_or_else(std::env::temp_dir)
        .join("Nimbo")
        .join("run-through-requests")
}

fn queue_run_through_request(path: &str) -> Result<(), String> {
    let dir = run_through_queue_dir();
    std::fs::create_dir_all(&dir)
        .map_err(|e| format!("failed to create protected-launch queue: {e}"))?;
    let stamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    let target = dir.join(format!("{}-{stamp}.json", std::process::id()));
    let temp = dir.join(format!(".{}-{stamp}.tmp", std::process::id()));
    let payload = serde_json::to_vec(path)
        .map_err(|e| format!("failed to encode protected-launch request: {e}"))?;
    std::fs::write(&temp, payload)
        .map_err(|e| format!("failed to write protected-launch request: {e}"))?;
    std::fs::rename(&temp, &target)
        .map_err(|e| format!("failed to publish protected-launch request: {e}"))
}

fn drain_run_through_requests() -> Vec<String> {
    let dir = run_through_queue_dir();
    let Ok(entries) = std::fs::read_dir(&dir) else {
        return Vec::new();
    };
    let mut files = entries
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| path.extension().and_then(|ext| ext.to_str()) == Some("json"))
        .collect::<Vec<_>>();
    files.sort();
    files
        .into_iter()
        .filter_map(|path| {
            let value = std::fs::read(&path)
                .ok()
                .and_then(|bytes| serde_json::from_slice::<String>(&bytes).ok());
            let _ = std::fs::remove_file(path);
            value
        })
        .collect()
}

#[cfg(windows)]
fn wait_for_parent_relaunch() {
    let mut args = std::env::args().skip(1);
    while let Some(arg) = args.next() {
        if arg != "--wait-for-parent" {
            continue;
        }

        let Some(pid) = args.next().and_then(|value| value.parse::<u32>().ok()) else {
            return;
        };

        wait_for_process_exit(pid);
        return;
    }
}

#[cfg(windows)]
fn wait_for_process_exit(pid: u32) {
    use windows_sys::Win32::Foundation::CloseHandle;
    use windows_sys::Win32::System::Threading::{OpenProcess, WaitForSingleObject};

    const SYNCHRONIZE: u32 = 0x0010_0000;
    const WAIT_TIMEOUT_MS: u32 = 15_000;

    let handle = unsafe { OpenProcess(SYNCHRONIZE, 0, pid) };
    if handle.is_null() {
        std::thread::sleep(std::time::Duration::from_millis(700));
        return;
    }

    unsafe {
        WaitForSingleObject(handle, WAIT_TIMEOUT_MS);
        let _ = CloseHandle(handle);
    }
}

#[cfg(not(windows))]
fn wait_for_parent_relaunch() {}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    wait_for_parent_relaunch();
    logging::init();
    tracing::info!(version = env!("CARGO_PKG_VERSION"), "nimbo-ui starting");

    let cli_run_through = run_through_cli_path();

    let Some(single_instance_guard) = acquire_single_instance() else {
        if let Some(path) = cli_run_through.as_deref() {
            if let Err(error) = queue_run_through_request(path) {
                tracing::error!(?error, "failed to forward protected-launch request");
            }
        }
        tracing::info!("duplicate nimbo-ui instance rejected");
        return;
    };
    let app_state = match AppState::load() {
        Ok(state) => state,
        Err(error) => {
            tracing::error!(?error, "failed to load app state");
            return;
        }
    };

    let app = tauri::Builder::default()
        .plugin(tauri_plugin_deep_link::init())
        .manage(app_state)
        .manage(CrossSyncManager::default())
        .manage(single_instance_guard)
        .on_window_event(|window, event| {
            // The custom tray popup is a transient flyout: hide it as soon as it
            // loses focus (click elsewhere), and never run the main-window
            // close/minimize logic for it.
            if window.label() == "tray-menu" {
                if let WindowEvent::Focused(false) = event {
                    crate::tray::note_tray_menu_hidden();
                    let _ = window.hide();
                }
                return;
            }
            if let WindowEvent::CloseRequested { api, .. } = event {
                let preferences = window
                    .app_handle()
                    .state::<AppState>()
                    .snapshot()
                    .preferences;
                if preferences.minimize_to_tray {
                    api.prevent_close();
                    let _ = window.hide();
                } else {
                    // Hide every window immediately for a perceptually instant close,
                    // letting the background event loop clean up proxies and routes
                    // gracefully. The tray flyout is always-on-top, so leaving it up
                    // would park an empty dark panel over the desktop until we exit.
                    hide_all_windows(window.app_handle());
                }
            }
        })
        .setup(move |app| {
            #[cfg(any(windows, target_os = "linux"))]
            {
                app.deep_link().register_all()?;
            }

            tray::setup_tray(app.handle())?;
            apply_main_window_background(app.handle());
            crate::commands::cleanup_disconnected_runtime_on_startup(app.handle());

            // Long-lived sync server: runs for the whole app lifetime so paired
            // phones can keep syncing after the sync tab is closed.
            let app_handle = app.handle().clone();
            app_handle
                .state::<CrossSyncManager>()
                .attach(app_handle.clone());
            let startup_manager = app_handle.state::<CrossSyncManager>().inner().clone();
            tauri::async_runtime::spawn(async move {
                if let Err(error) = startup_manager.ensure_server().await {
                    tracing::warn!(?error, "cross-sync server failed to start");
                }
            });

            if let Some(path) = cli_run_through.as_deref() {
                let _ = queue_run_through_request(path);
            }
            let protected_launch_handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                // Let the WebView subscribe before delivering the first request.
                tokio::time::sleep(std::time::Duration::from_millis(1_200)).await;
                loop {
                    for executable_path in drain_run_through_requests() {
                        if let Some(window) = protected_launch_handle.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                        let _ = protected_launch_handle
                            .emit("nimbo:run-through-request", executable_path);
                    }
                    tokio::time::sleep(std::time::Duration::from_millis(600)).await;
                }
            });

            if app
                .state::<AppState>()
                .snapshot()
                .preferences
                .start_minimized
            {
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.hide();
                }
            }

            #[cfg(all(windows, target_arch = "x86_64"))]
            {
                let handle = app.handle().clone();
                tauri::async_runtime::spawn(async move {
                    if let Err(error) = crate::commands::ensure_tun_dependencies(&handle).await {
                        tracing::warn!(?error, "failed to auto-install TUN dependencies");
                    }
                });
            }

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            app_ready,
            get_status,
            get_preferences,
            export_app_backup,
            import_app_backup,
            get_session_traffic,
            get_memory_usage,
            get_device_info,
            reset_device_id,
            read_clipboard_text,
            write_clipboard_text,
            get_user_agent_override,
            set_user_agent_override,
            list_app_proxy_rules,
            list_subscription_app_proxy_rules,
            list_active_connections,
            list_conflicting_processes,
            list_installed_apps,
            get_app_icon,
            get_subscription_logo,
            pick_app_executable,
            run_through_nimbo,
            get_run_through_nimbo_context_menu_enabled,
            set_run_through_nimbo_context_menu,
            export_app_proxy_rules_file,
            stop_conflicting_processes,
            set_app_proxy_rules,
            reapply_runtime_config,
            set_connection_mode,
            set_preferences,
            get_tun_status,
            install_tun,
            restart_as_admin,
            set_proxy_settings,
            list_subscriptions,
            inspect_subscription_headers,
            add_subscription,
            refresh_subscription,
            migrate_subscriptions,
            update_subscription_settings,
            remove_subscription,
            reorder_subscriptions,
            set_active_server,
            set_active_subscription,
            ping_server,
            ping_servers,
            refresh_tray_menu,
            connect_server,
            disconnect_server,
            check_app_update,
            install_app_update,
            open_update_download,
            get_post_update_info,
            dismiss_post_update_info,
            helper_status,
            install_helper,
            uninstall_helper,
            list_routing_profiles,
            set_active_routing_profile,
            get_routing_profile,
            update_routing_profile,
            delete_routing_profile,
            export_routing_profile,
            import_routing_profile,
            reset_builtin_routing_profiles,
            open_routing_folder,
            get_traffic_stats,
            reset_traffic_totals,
            get_tunnel_logs,
            clear_tunnel_logs,
            open_logs_folder,
            cross_sync_start,
            cross_sync_status,
            cross_sync_approve,
            cross_sync_reject,
            cross_sync_accept_import,
            cross_sync_cancel,
            cross_sync_list_devices,
            cross_sync_remove_device,
            cross_sync_set_auto_sync,
            tray_menu_state,
            tray_menu_resize,
            tray_menu_action,
        ])
        .build(tauri::generate_context!());

    let app = match app {
        Ok(app) => app,
        Err(error) => {
            tracing::error!(?error, "failed to build tauri application");
            return;
        }
    };

    app.run(|app_handle, event| match event {
        RunEvent::ExitRequested { .. } | RunEvent::Exit => {
            cleanup_once(app_handle);
        }
        RunEvent::Resumed => {
            crate::commands::reconnect_runtime_after_resume(app_handle);
        }
        _ => {}
    });
}
