#![cfg_attr(windows, windows_subsystem = "windows")]

mod payload;

fn main() {
    if std::env::args().any(|arg| arg == "--uninstall") {
        let _ = payload::uninstall_from_cli();
        return;
    }

    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            payload::probe_installation,
            payload::choose_install_dir,
            payload::install_nimbo,
            payload::open_nimbo,
        ])
        .run(tauri::generate_context!())
        .expect("failed to run Nimbo Setup");
}
