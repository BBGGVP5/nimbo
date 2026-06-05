use std::sync::{Mutex, OnceLock};

use serde::Serialize;
use tauri::image::Image;
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{
    AppHandle, Emitter, Manager, PhysicalPosition, PhysicalSize, WebviewUrl, WebviewWindowBuilder,
};

use crate::state::{AppPreferences, AppState, Language, PersistedState};
use nimbo_subscription::SubscriptionTheme;

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
const MENU_WINDOW: &str = "tray-menu";

/// Cursor anchor (physical px) captured on right-click, plus whether a reveal
/// is pending. The popup measures its rendered content and calls
/// `tray_menu_resize`, which reads this to size, position, and show the window
/// at the right spot.
struct MenuCtl {
    anchor: Option<(f64, f64)>,
    pending: bool,
}

static MENU_CTL: OnceLock<Mutex<MenuCtl>> = OnceLock::new();

fn menu_ctl() -> &'static Mutex<MenuCtl> {
    MENU_CTL.get_or_init(|| Mutex::new(MenuCtl { anchor: None, pending: false }))
}

pub fn setup_tray(app: &AppHandle) -> tauri::Result<()> {
    let snapshot = app.state::<AppState>().snapshot();
    let icon = get_tray_icon(snapshot.connected)?;

    TrayIconBuilder::with_id(TRAY_ID)
        .icon(icon)
        .tooltip(tray_tooltip(snapshot.connected))
        .show_menu_on_left_click(false)
        .on_tray_icon_event(|tray, event| match event {
            TrayIconEvent::DoubleClick { .. }
            | TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } => {
                show_main_window(tray.app_handle());
            }
            // Right-click opens our custom webview flyout instead of a native menu.
            TrayIconEvent::Click {
                button: MouseButton::Right,
                button_state: MouseButtonState::Up,
                position,
                ..
            } => {
                open_tray_menu(tray.app_handle(), position.x, position.y);
            }
            _ => {}
        })
        .build(app)?;

    Ok(())
}

pub fn refresh_tray_menu(app: &AppHandle) -> tauri::Result<()> {
    let connected = app.state::<AppState>().snapshot().connected;
    if let Some(tray) = app.tray_by_id(TRAY_ID) {
        tray.set_icon(Some(get_tray_icon(connected)?))?;
        tray.set_tooltip(Some(tray_tooltip(connected)))?;
    }
    // Also update the window taskbar icon so the indicator shows there too
    if let Some(window) = app.get_webview_window("main") {
        if let Ok(icon) = get_tray_icon(connected) {
            let _ = window.set_icon(icon);
        }
    }
    // If the popup is open, let it refresh its contents to reflect new state.
    let _ = app.emit_to(MENU_WINDOW, "tray-menu:refresh", ());
    Ok(())
}

fn tray_tooltip(connected: bool) -> &'static str {
    if connected { "Nimbo — Подключено" } else { "Nimbo" }
}

/// Lazily create the (hidden) frameless webview that renders the tray menu.
fn create_menu_window(app: &AppHandle) -> tauri::Result<()> {
    WebviewWindowBuilder::new(app, MENU_WINDOW, WebviewUrl::App("tray-menu.html".into()))
        .title("Nimbo")
        .inner_size(268.0, 360.0)
        .decorations(false)
        .transparent(true)
        .shadow(false)
        .always_on_top(true)
        .skip_taskbar(true)
        .resizable(false)
        .visible(false)
        .focused(false)
        .build()?;
    Ok(())
}

/// Capture the cursor anchor and ask the popup to (re)load and reveal itself.
fn open_tray_menu(app: &AppHandle, x: f64, y: f64) {
    {
        let mut ctl = menu_ctl().lock().unwrap();
        ctl.anchor = Some((x, y));
        ctl.pending = true;
    }
    if app.get_webview_window(MENU_WINDOW).is_none() {
        if let Err(error) = create_menu_window(app) {
            tracing::warn!(?error, "failed to create tray menu window");
            return;
        }
        // First creation: the window's own mount flow performs the initial
        // load + resize (which reveals it because `pending` is set), so a
        // missed event here is harmless.
        return;
    }
    let _ = app.emit_to(MENU_WINDOW, "tray-menu:open", ());
}

#[derive(Serialize)]
pub struct TrayMenuServer {
    id: String,
    name: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TrayMenuState {
    connected: bool,
    active_server_id: Option<String>,
    language: String,
    visual_preferences: AppPreferences,
    provider_theme: Option<SubscriptionTheme>,
    servers: Vec<TrayMenuServer>,
}

/// Snapshot the data the popup needs to render itself.
#[tauri::command]
pub fn tray_menu_state(app: AppHandle) -> TrayMenuState {
    let snapshot = app.state::<AppState>().snapshot();
    let language = match snapshot.preferences.language.resolved() {
        Language::En => "en",
        _ => "ru",
    }
    .to_string();
    let active_server_id = snapshot.active_server_id.clone();
    let visual_preferences = snapshot.preferences.clone();
    let provider_theme = active_provider_theme(&snapshot);

    let mut servers = Vec::new();
    for sub in &snapshot.subscriptions {
        for server in &sub.servers {
            servers.push(TrayMenuServer {
                id: server.id.clone(),
                name: server.name.clone(),
            });
        }
    }

    TrayMenuState {
        connected: snapshot.connected,
        active_server_id,
        language,
        visual_preferences,
        provider_theme,
        servers,
    }
}

fn active_provider_theme(snapshot: &PersistedState) -> Option<SubscriptionTheme> {
    if !snapshot.preferences.provider_theme {
        return None;
    }

    let by_url = snapshot
        .active_subscription_url
        .as_deref()
        .and_then(|url| snapshot.subscriptions.iter().find(|sub| sub.url == url));
    let by_server = || {
        snapshot.active_server_id.as_deref().and_then(|server_id| {
            snapshot
                .subscriptions
                .iter()
                .find(|sub| sub.servers.iter().any(|server| server.id == server_id))
        })
    };

    by_url.or_else(by_server).and_then(|sub| sub.meta.theme.clone())
}

/// Size the popup to its measured content, then (if a reveal is pending)
/// position it relative to the cursor and show it.
#[tauri::command]
pub fn tray_menu_resize(app: AppHandle, width: f64, height: f64) {
    let Some(window) = app.get_webview_window(MENU_WINDOW) else {
        return;
    };
    let w = width.max(1.0).round() as u32;
    let h = height.max(1.0).round() as u32;
    let _ = window.set_size(PhysicalSize::new(w, h));

    let (anchor, pending) = {
        let mut ctl = menu_ctl().lock().unwrap();
        let result = (ctl.anchor, ctl.pending);
        ctl.pending = false;
        result
    };
    if !pending {
        return;
    }
    let Some((ax, ay)) = anchor else {
        return;
    };

    // Grow up-and-left from the cursor (bottom-right tray convention).
    let mut left = ax - w as f64;
    let mut top = ay - h as f64;

    // Keep the flyout fully inside the work area of the cursor's monitor.
    if let Ok(Some(monitor)) = window.current_monitor() {
        let area = monitor.work_area();
        let min_x = area.position.x as f64;
        let min_y = area.position.y as f64;
        let max_x = min_x + area.size.width as f64;
        let max_y = min_y + area.size.height as f64;
        left = left.min(max_x - w as f64).max(min_x);
        top = top.min(max_y - h as f64).max(min_y);
    }

    let _ = window.set_position(PhysicalPosition::new(left.round() as i32, top.round() as i32));
    let _ = window.show();
    let _ = window.set_focus();
}

/// Perform a menu action, then dismiss the popup.
#[tauri::command]
pub fn tray_menu_action(app: AppHandle, action: String, server_id: Option<String>) {
    if let Some(window) = app.get_webview_window(MENU_WINDOW) {
        let _ = window.hide();
    }
    match action.as_str() {
        "show" => show_main_window(&app),
        "connect" => connect_active_server(&app),
        "disconnect" => disconnect(&app),
        "server" => {
            if let Some(id) = server_id {
                connect_specific_server(&app, id);
            }
        }
        "quit" => app.exit(0),
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
