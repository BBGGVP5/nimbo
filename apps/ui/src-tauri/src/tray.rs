use tauri::image::Image;
use tauri::menu::{CheckMenuItem, IsMenuItem, Menu, MenuItem, PredefinedMenuItem, Submenu};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager};

use crate::state::{AppState, Language};

const TRAY_ID: &str = "nimbo-tray";
const MENU_STATUS: &str = "tray-status";
const MENU_SHOW: &str = "tray-show";
const MENU_CONNECT: &str = "tray-connect";
const MENU_DISCONNECT: &str = "tray-disconnect";
const MENU_QUIT: &str = "tray-quit";
const MENU_SERVER_PREFIX: &str = "tray-server:";

pub fn setup_tray(app: &AppHandle) -> tauri::Result<()> {
    let menu = build_tray_menu(app)?;
    let icon = Image::from_bytes(include_bytes!("../icons/tray.png"))?;

    TrayIconBuilder::with_id(TRAY_ID)
        .icon(icon)
        .tooltip("Nimbo")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| handle_menu_event(app, event.id().as_ref()))
        .on_tray_icon_event(|tray, event| {
            if matches!(
                event,
                TrayIconEvent::DoubleClick { .. }
                    | TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    }
            ) {
                show_main_window(tray.app_handle());
            }
        })
        .build(app)?;

    Ok(())
}

pub fn refresh_tray_menu(app: &AppHandle) -> tauri::Result<()> {
    if let Some(tray) = app.tray_by_id(TRAY_ID) {
        tray.set_menu(Some(build_tray_menu(app)?))?;
        tray.set_tooltip(Some("Nimbo"))?;
    }
    Ok(())
}

fn build_tray_menu(app: &AppHandle) -> tauri::Result<Menu<tauri::Wry>> {
    let snapshot = app.state::<AppState>().snapshot();
    let labels = tray_labels(snapshot.preferences.language);
    let status_text = if snapshot.connected { labels.connected } else { labels.disconnected };

    let menu = Menu::new(app)?;
    let status = MenuItem::with_id(app, MENU_STATUS, status_text, false, None::<&str>)?;
    let show = MenuItem::with_id(app, MENU_SHOW, labels.show, true, None::<&str>)?;
    let connect = MenuItem::with_id(
        app,
        MENU_CONNECT,
        labels.connect,
        snapshot.active_server_id.is_some() && !snapshot.connected,
        None::<&str>,
    )?;
    let disconnect = MenuItem::with_id(
        app,
        MENU_DISCONNECT,
        labels.disconnect,
        snapshot.connected,
        None::<&str>,
    )?;
    let servers = Submenu::new(app, labels.servers, true)?;
    let quit = MenuItem::with_id(app, MENU_QUIT, labels.quit, true, None::<&str>)?;

    let mut server_count = 0usize;
    for sub in &snapshot.subscriptions {
        for server in &sub.servers {
            server_count += 1;
            let selected = snapshot.active_server_id.as_deref() == Some(server.id.as_str());
            let label = if selected {
                format!("✓ {}", compact_menu_label(&server.name))
            } else {
                compact_menu_label(&server.name)
            };
            let item = CheckMenuItem::with_id(
                app,
                format!("{MENU_SERVER_PREFIX}{}", server.id),
                label,
                true,
                selected,
                None::<&str>,
            )?;
            servers.append(&item)?;
        }
    }

    if server_count == 0 {
        let empty = MenuItem::with_id(app, "tray-server-empty", labels.no_servers, false, None::<&str>)?;
        servers.append(&empty)?;
    }

    menu.append_items(&[
        &status as &dyn IsMenuItem<_>,
        &PredefinedMenuItem::separator(app)?,
        &show,
        &PredefinedMenuItem::separator(app)?,
        &connect,
        &disconnect,
        &PredefinedMenuItem::separator(app)?,
        &servers,
        &PredefinedMenuItem::separator(app)?,
        &quit,
    ])?;

    Ok(menu)
}

struct TrayLabels {
    connected: &'static str,
    disconnected: &'static str,
    show: &'static str,
    connect: &'static str,
    disconnect: &'static str,
    servers: &'static str,
    no_servers: &'static str,
    quit: &'static str,
}

fn tray_labels(language: Language) -> TrayLabels {
    match language {
        Language::En => TrayLabels {
            connected: "Connected",
            disconnected: "Disconnected",
            show: "Show Nimbo",
            connect: "Connect",
            disconnect: "Disconnect",
            servers: "Servers",
            no_servers: "No servers",
            quit: "Quit",
        },
        Language::Ru => TrayLabels {
            connected: "Подключено",
            disconnected: "Отключено",
            show: "Показать Nimbo",
            connect: "Подключить",
            disconnect: "Отключить",
            servers: "Серверы",
            no_servers: "Нет серверов",
            quit: "Выйти",
        },
    }
}

fn handle_menu_event(app: &AppHandle, id: &str) {
    match id {
        MENU_SHOW => show_main_window(app),
        MENU_CONNECT => connect_active_server(app),
        MENU_DISCONNECT => disconnect(app),
        MENU_QUIT => app.exit(0),
        _ if id.starts_with(MENU_SERVER_PREFIX) => {
            let server_id = id.trim_start_matches(MENU_SERVER_PREFIX).to_string();
            connect_specific_server(app, server_id);
        }
        _ => {}
    }
}

fn show_main_window(app: &AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

fn connect_active_server(app: &AppHandle) {
    let Some(server_id) = app.state::<AppState>().snapshot().active_server_id else {
        return;
    };
    connect_specific_server(app, server_id);
}

fn connect_specific_server(app: &AppHandle, server_id: String) {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let state = app.state::<AppState>();
        let _ = crate::commands::connect_server(app.clone(), state, server_id).await;
        let _ = refresh_tray_menu(&app);
    });
}

fn disconnect(app: &AppHandle) {
    let state = app.state::<AppState>();
    let _ = crate::commands::disconnect_server(state);
    let _ = refresh_tray_menu(app);
}

fn compact_menu_label(name: &str) -> String {
    let label = name.split_whitespace().collect::<Vec<_>>().join(" ");
    const MAX: usize = 56;
    if label.chars().count() <= MAX {
        return label;
    }
    let mut out = label.chars().take(MAX - 1).collect::<String>();
    out.push('…');
    out
}
