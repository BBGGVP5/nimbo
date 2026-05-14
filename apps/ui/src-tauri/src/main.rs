#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    if nimbo_ui_lib::handle_cli_args() {
        return;
    }
    nimbo_ui_lib::run()
}
