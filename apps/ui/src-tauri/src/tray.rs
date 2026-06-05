use std::sync::OnceLock;

use tauri::image::Image;
use tauri::menu::{CheckMenuItem, IsMenuItem, Menu, MenuItem, PredefinedMenuItem, Submenu};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager};

use crate::state::{AppState, Language};

// Pre-computed PNG bytes for the connected-state tray icon (green dot overlay).
static CONNECTED_ICON_PNG: OnceLock<Vec<u8>> = OnceLock::new();

static DISCONNECTED_ICON_PNG: OnceLock<Vec<u8>> = OnceLock::new();

fn get_tray_icon(connected: bool) -> tauri::Result<Image<'static>> {
    if connected {
        let png = CONNECTED_ICON_PNG.get_or_init(|| {
            make_tray_icon_png(true)
                .unwrap_or_else(|_| include_bytes!("../icons/tray.png").to_vec())
        });
        Image::from_bytes(png)
    } else {
        let png = DISCONNECTED_ICON_PNG.get_or_init(|| {
            make_tray_icon_png(false)
                .unwrap_or_else(|_| include_bytes!("../icons/tray.png").to_vec())
        });
        Image::from_bytes(png)
    }
}

fn make_tray_icon_png(connected: bool) -> Result<Vec<u8>, Box<dyn std::error::Error + Send + Sync>> {
    use png::{BitDepth, ColorType};

    let png_bytes = include_bytes!("../icons/tray.png");

    let decoder = png::Decoder::new(png_bytes.as_ref());
    let mut reader = decoder.read_info()?;
    let mut raw = vec![0u8; reader.output_buffer_size()];
    let info = reader.next_frame(&mut raw)?;

    let src_w = info.width as usize;
    let src_h = info.height as usize;

    let mut src_rgba = vec![0u8; src_w * src_h * 4];
    match (info.color_type, info.bit_depth) {
        (ColorType::Rgba, BitDepth::Eight) => {
            src_rgba.copy_from_slice(&raw[..src_w * src_h * 4]);
        }
        (ColorType::Rgb, BitDepth::Eight) => {
            for i in 0..src_w * src_h {
                src_rgba[i * 4] = raw[i * 3];
                src_rgba[i * 4 + 1] = raw[i * 3 + 1];
                src_rgba[i * 4 + 2] = raw[i * 3 + 2];
                src_rgba[i * 4 + 3] = 255;
            }
        }
        (ColorType::GrayscaleAlpha, BitDepth::Eight) => {
            for i in 0..src_w * src_h {
                let v = raw[i * 2];
                src_rgba[i * 4] = v;
                src_rgba[i * 4 + 1] = v;
                src_rgba[i * 4 + 2] = v;
                src_rgba[i * 4 + 3] = raw[i * 2 + 1];
            }
        }
        (ColorType::Grayscale, BitDepth::Eight) => {
            for i in 0..src_w * src_h {
                let v = raw[i];
                src_rgba[i * 4] = v;
                src_rgba[i * 4 + 1] = v;
                src_rgba[i * 4 + 2] = v;
                src_rgba[i * 4 + 3] = 255;
            }
        }
        _ => {
            return Err(format!(
                "unsupported PNG format: {:?} {:?}",
                info.color_type, info.bit_depth
            )
            .into());
        }
    }

    // Output at 4x the source for crisp rendering on HiDPI tray.
    let dst_w: usize = 128;
    let dst_h: usize = 128;
    let mut dst = vec![0u8; dst_w * dst_h * 4];

    // For the connected state, shrink the icon content so the accent ring sits
    // OUTSIDE the artwork and never overlaps it.
    let content_scale: f32 = if connected { 0.80 } else { 1.0 };
    let content_w = (dst_w as f32 * content_scale) as usize;
    let content_h = (dst_h as f32 * content_scale) as usize;
    let offset_x = (dst_w - content_w) / 2;
    let offset_y = (dst_h - content_h) / 2;

    // Bilinear upscale of source onto the centered content area.
    for dy in 0..content_h {
        let sy = (dy as f32 + 0.5) * (src_h as f32 / content_h as f32) - 0.5;
        let sy0 = sy.floor().max(0.0) as usize;
        let sy1 = (sy0 + 1).min(src_h - 1);
        let fy = (sy - sy0 as f32).clamp(0.0, 1.0);
        for dx in 0..content_w {
            let sx = (dx as f32 + 0.5) * (src_w as f32 / content_w as f32) - 0.5;
            let sx0 = sx.floor().max(0.0) as usize;
            let sx1 = (sx0 + 1).min(src_w - 1);
            let fx = (sx - sx0 as f32).clamp(0.0, 1.0);

            let p00 = &src_rgba[(sy0 * src_w + sx0) * 4..(sy0 * src_w + sx0) * 4 + 4];
            let p01 = &src_rgba[(sy0 * src_w + sx1) * 4..(sy0 * src_w + sx1) * 4 + 4];
            let p10 = &src_rgba[(sy1 * src_w + sx0) * 4..(sy1 * src_w + sx0) * 4 + 4];
            let p11 = &src_rgba[(sy1 * src_w + sx1) * 4..(sy1 * src_w + sx1) * 4 + 4];

            let mut out_px = [0u8; 4];
            for c in 0..4 {
                let top = p00[c] as f32 * (1.0 - fx) + p01[c] as f32 * fx;
                let bot = p10[c] as f32 * (1.0 - fx) + p11[c] as f32 * fx;
                out_px[c] = (top * (1.0 - fy) + bot * fy).round().clamp(0.0, 255.0) as u8;
            }

            let didx = ((offset_y + dy) * dst_w + (offset_x + dx)) * 4;
            dst[didx..didx + 4].copy_from_slice(&out_px);
        }
    }

    if connected {
        // Accent-colored ring (#7c5dfa) around the perimeter, with a soft outer halo.
        let cx = (dst_w as f32 - 1.0) / 2.0;
        let cy = (dst_h as f32 - 1.0) / 2.0;
        let max_r = (dst_w.min(dst_h) as f32) / 2.0;
        let outer_r = max_r - 1.5;
        let ring_thickness = (max_r * 0.08).max(3.0);
        let inner_r = outer_r - ring_thickness;
        let halo_outer = outer_r + (max_r * 0.05).max(2.0);

        let (ar, ag, ab) = (124u8, 93u8, 250u8);

        for py in 0..dst_h {
            for px in 0..dst_w {
                let dx = px as f32 - cx;
                let dy = py as f32 - cy;
                let d = (dx * dx + dy * dy).sqrt();

                // Anti-aliased solid ring
                let alpha_ring: u8 = if d <= outer_r + 1.0 && d >= inner_r - 1.0 {
                    let outer_a = ((outer_r - d) + 0.5).clamp(0.0, 1.0);
                    let inner_a = ((d - inner_r) + 0.5).clamp(0.0, 1.0);
                    (outer_a.min(inner_a) * 255.0) as u8
                } else {
                    0
                };

                // Outer halo
                let alpha_halo: u8 = if d > outer_r && d <= halo_outer {
                    let t = 1.0 - (d - outer_r) / (halo_outer - outer_r).max(0.001);
                    (t.clamp(0.0, 1.0) * 90.0) as u8
                } else {
                    0
                };

                let alpha = alpha_ring.max(alpha_halo);
                if alpha > 0 {
                    let idx = (py * dst_w + px) * 4;
                    let a = alpha as f32 / 255.0;
                    let inv = 1.0 - a;
                    dst[idx] = ((ar as f32) * a + (dst[idx] as f32) * inv) as u8;
                    dst[idx + 1] = ((ag as f32) * a + (dst[idx + 1] as f32) * inv) as u8;
                    dst[idx + 2] = ((ab as f32) * a + (dst[idx + 2] as f32) * inv) as u8;
                    dst[idx + 3] = dst[idx + 3].max(alpha);
                }
            }
        }
    }

    let mut out = Vec::new();
    {
        let mut encoder = png::Encoder::new(&mut out, dst_w as u32, dst_h as u32);
        encoder.set_color(ColorType::Rgba);
        encoder.set_depth(BitDepth::Eight);
        let mut writer = encoder.write_header()?;
        writer.write_image_data(&dst)?;
    }

    Ok(out)
}

const TRAY_ID: &str = "nimbo-tray";
const MENU_STATUS: &str = "tray-status";
const MENU_SHOW: &str = "tray-show";
const MENU_CONNECT: &str = "tray-connect";
const MENU_DISCONNECT: &str = "tray-disconnect";
const MENU_QUIT: &str = "tray-quit";
const MENU_SERVER_PREFIX: &str = "tray-server:";

pub fn setup_tray(app: &AppHandle) -> tauri::Result<()> {
    let snapshot = app.state::<AppState>().snapshot();
    let menu = build_tray_menu(app)?;
    let icon = get_tray_icon(snapshot.connected)?;

    TrayIconBuilder::with_id(TRAY_ID)
        .icon(icon)
        .tooltip(tray_tooltip(snapshot.connected))
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
    let connected = app.state::<AppState>().snapshot().connected;
    if let Some(tray) = app.tray_by_id(TRAY_ID) {
        tray.set_menu(Some(build_tray_menu(app)?))?;
        tray.set_icon(Some(get_tray_icon(connected)?))?;
        tray.set_tooltip(Some(tray_tooltip(connected)))?;
    }
    // Also update the window taskbar icon so the indicator shows there too
    if let Some(window) = app.get_webview_window("main") {
        if let Ok(icon) = get_tray_icon(connected) {
            let _ = window.set_icon(icon);
        }
    }
    Ok(())
}

fn tray_tooltip(connected: bool) -> &'static str {
    if connected { "Nimbo — Подключено" } else { "Nimbo" }
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
    // `resolved()` collapses `System` to a concrete `Ru`/`En`; the `System`
    // arm below only exists to keep the match exhaustive.
    match language.resolved() {
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
        Language::Ru | Language::System => TrayLabels {
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
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let state = app.state::<AppState>();
        let _ = crate::commands::disconnect_server(app.clone(), state).await;
        let _ = refresh_tray_menu(&app);
    });
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
