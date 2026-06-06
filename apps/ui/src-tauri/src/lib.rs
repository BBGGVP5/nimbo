pub mod commands;
#[cfg(windows)]
pub mod helper;
pub mod state;
pub mod tray;
pub mod updater;

use crate::commands::{
    add_subscription, app_ready, clear_tunnel_logs, connect_server, delete_routing_profile,
    disconnect_server, export_app_backup, export_app_proxy_rules_file, export_routing_profile,
    get_app_icon, get_device_info, get_memory_usage, get_preferences, get_routing_profile,
    get_session_traffic, get_status, get_subscription_logo, get_traffic_stats, get_tun_status,
    get_tunnel_logs, get_user_agent_override, helper_status, import_app_backup, import_routing_profile,
    inspect_subscription_headers, install_helper, install_tun, list_active_connections,
    list_app_proxy_rules, list_conflicting_processes, list_installed_apps, list_routing_profiles,
    list_subscription_app_proxy_rules, list_subscriptions, open_routing_folder,
    pick_app_executable, ping_server, ping_servers, read_clipboard_text, refresh_subscription,
    refresh_tray_menu, remove_subscription, reapply_runtime_config, reset_builtin_routing_profiles,
    reset_device_id, reset_traffic_totals, restart_as_admin, set_active_routing_profile,
    set_active_server, set_active_subscription, set_app_proxy_rules, set_connection_mode,
    set_preferences, set_proxy_settings, set_user_agent_override, stop_conflicting_processes,
    uninstall_helper, update_routing_profile, update_subscription_settings, write_clipboard_text,
};
use crate::state::AppState;
use crate::tray::{tray_menu_action, tray_menu_resize, tray_menu_state};
use crate::updater::{check_app_update, open_update_download};
use tauri::{Manager, RunEvent, WindowEvent};
use tauri_plugin_deep_link::DeepLinkExt;

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
        CloseHandle, ERROR_ACCESS_DENIED, ERROR_ALREADY_EXISTS, GetLastError,
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
    if !std::env::args().any(|arg| arg == "--install-tun") {
        return false;
    }

    if let Err(error) = crate::commands::install_tun_dependencies_for_installer() {
        eprintln!("failed to install TUN dependencies: {error}");
        std::process::exit(1);
    }
    true
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

    let Some(single_instance_guard) = acquire_single_instance() else {
        return;
    };
    let app_state = AppState::load().expect("failed to load app state");

    tauri::Builder::default()
        .plugin(tauri_plugin_deep_link::init())
        .manage(app_state)
        .manage(single_instance_guard)
        .on_window_event(|window, event| {
            // The custom tray popup is a transient flyout: hide it as soon as it
            // loses focus (click elsewhere), and never run the main-window
            // close/minimize logic for it.
            if window.label() == "tray-menu" {
                if let WindowEvent::Focused(false) = event {
                    let _ = window.hide();
                }
                return;
            }
            if let WindowEvent::CloseRequested { api, .. } = event {
                let preferences = window.app_handle().state::<AppState>().snapshot().preferences;
                if preferences.minimize_to_tray {
                    api.prevent_close();
                    let _ = window.hide();
                } else {
                    // Hide the window immediately for a perceptually instant close,
                    // letting background event loop clean up proxies and routes gracefully
                    let _ = window.hide();
                }
            }
        })
        .setup(|app| {
            #[cfg(any(windows, target_os = "linux"))]
            {
                app.deep_link().register_all()?;
            }

            tray::setup_tray(app.handle())?;
            crate::commands::cleanup_disconnected_runtime_on_startup(app.handle());

            if app.state::<AppState>().snapshot().preferences.start_minimized {
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
            update_subscription_settings,
            remove_subscription,
            set_active_server,
            set_active_subscription,
            ping_server,
            ping_servers,
            refresh_tray_menu,
            connect_server,
            disconnect_server,
            check_app_update,
            open_update_download,
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
            tray_menu_state,
            tray_menu_resize,
            tray_menu_action,
        ])
        .build(tauri::generate_context!())
        .expect("error while building tauri application")
        .run(|app_handle, event| match event {
            RunEvent::ExitRequested { .. } | RunEvent::Exit => {
                crate::commands::cleanup_runtime_for_exit(app_handle);
            }
            RunEvent::Resumed => {
                crate::commands::reconnect_runtime_after_resume(app_handle);
            }
            _ => {}
        });
}
