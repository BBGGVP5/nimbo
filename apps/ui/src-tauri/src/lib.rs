pub mod commands;
pub mod state;

use crate::commands::{
    add_subscription, get_status, list_subscriptions, refresh_subscription, remove_subscription,
    set_active_server,
};
use crate::state::AppState;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let app_state = AppState::load().expect("failed to load app state");

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(app_state)
        .invoke_handler(tauri::generate_handler![
            get_status,
            list_subscriptions,
            add_subscription,
            refresh_subscription,
            remove_subscription,
            set_active_server,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
