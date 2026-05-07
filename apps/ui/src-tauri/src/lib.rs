use nimbo_ipc::PROTOCOL_VERSION;

#[tauri::command]
fn ipc_protocol_version() -> u32 {
    PROTOCOL_VERSION
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![ipc_protocol_version])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
