pub mod commands;
#[cfg(windows)]
pub mod helper;
pub mod state;
pub mod tray;
pub mod updater;

use crate::commands::{
    add_subscription, connect_server, disconnect_server, get_device_info, get_preferences, get_session_traffic,
    get_status, get_tun_status, get_user_agent_override, helper_status, inspect_subscription_headers, install_helper, install_tun,
    get_app_icon, list_app_proxy_rules, list_conflicting_processes, list_installed_apps, list_subscriptions, pick_app_executable, ping_server, ping_servers, read_clipboard_text,
    refresh_subscription, refresh_tray_menu, remove_subscription, reset_device_id, restart_as_admin,
    set_active_server, set_active_subscription, set_app_proxy_rules, set_connection_mode, set_preferences,
    set_proxy_settings, set_user_agent_override, stop_conflicting_processes, uninstall_helper, update_subscription_settings, write_clipboard_text,
};
use crate::state::AppState;
use crate::updater::{check_app_update, open_update_download};
use tauri::{Manager, WindowEvent};
use tauri_plugin_deep_link::DeepLinkExt;

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

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let app_state = AppState::load().expect("failed to load app state");

    tauri::Builder::default()
        .plugin(tauri_plugin_deep_link::init())
        .manage(app_state)
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                let preferences = window.app_handle().state::<AppState>().snapshot().preferences;
                if preferences.minimize_to_tray {
                    api.prevent_close();
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
            get_status,
            get_preferences,
            get_session_traffic,
            get_device_info,
            reset_device_id,
            read_clipboard_text,
            write_clipboard_text,
            get_user_agent_override,
            set_user_agent_override,
            list_app_proxy_rules,
            list_conflicting_processes,
            list_installed_apps,
            get_app_icon,
            pick_app_executable,
            stop_conflicting_processes,
            set_app_proxy_rules,
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
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
