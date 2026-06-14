use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant};

use serde::Serialize;
use tauri::image::Image;
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::utils::config::Color;
use tauri::{
    AppHandle, Emitter, Manager, PhysicalPosition, PhysicalSize, WebviewUrl, WebviewWindowBuilder,
};

use crate::state::{AppPreferences, AppState, ConnectionMode, Language, PersistedState};
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
        // Accent-colored ring (#75a7ff) around the perimeter, with a soft outer halo.
        let cx = (dst_w as f32 - 1.0) / 2.0;
        let cy = (dst_h as f32 - 1.0) / 2.0;
        let max_r = (dst_w.min(dst_h) as f32) / 2.0;
        let outer_r = max_r - 1.5;
        let ring_thickness = (max_r * 0.08).max(3.0);
        let inner_r = outer_r - ring_thickness;
        let halo_outer = outer_r + (max_r * 0.05).max(2.0);

        let (ar, ag, ab) = (117u8, 167u8, 255u8);

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
const MENU_WINDOW_BG: Color = Color(32, 34, 49, 255);

/// Tray icon rectangle (physical px: x, y, width, height) captured on
/// right-click, plus whether a reveal is pending. The popup measures its
/// rendered content and calls `tray_menu_resize`, which reads this to size,
/// position, and show the window anchored to the icon itself.
struct MenuCtl {
    anchor: Option<(f64, f64, f64, f64)>,
    pending: bool,
    /// When the popup was last dismissed. Lets a tray click that arrives right
    /// after a focus-loss dismissal toggle the menu closed instead of reopening.
    last_hidden: Option<Instant>,
}

static MENU_CTL: OnceLock<Mutex<MenuCtl>> = OnceLock::new();

fn menu_ctl() -> &'static Mutex<MenuCtl> {
    MENU_CTL.get_or_init(|| {
        Mutex::new(MenuCtl {
            anchor: None,
            pending: false,
            last_hidden: None,
        })
    })
}

/// Record that the popup was just dismissed (e.g. it lost focus because the user
/// pressed the tray icon). `open_tray_menu` reads this so the same click that
/// dismissed it does not immediately reopen it.
pub fn note_tray_menu_hidden() {
    if let Ok(mut ctl) = menu_ctl().lock() {
        ctl.last_hidden = Some(Instant::now());
    }
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
                rect,
                ..
            } => {
                // Anchor to the icon's rectangle (not the cursor) so the flyout
                // always pops out from the tray icon. The OS reports the rect in
                // physical px on Windows; convert defensively via the main
                // window's scale factor in case it arrives logical.
                let app = tray.app_handle();
                let scale = app
                    .get_webview_window("main")
                    .and_then(|window| window.scale_factor().ok())
                    .unwrap_or(1.0);
                let pos = rect.position.to_physical::<f64>(scale);
                let size = rect.size.to_physical::<f64>(scale);
                open_tray_menu(app, pos.x, pos.y, size.width, size.height);
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
    // Keep the main window in sync too: a connect/disconnect from the tray (or a
    // background monitor) changes the state without the main window's store
    // knowing, so nudge it to re-read the status.
    let _ = app.emit_to("main", "nimbo:state-changed", ());
    Ok(())
}

fn tray_tooltip(connected: bool) -> &'static str {
    if connected { "Nimbo — Подключено" } else { "Nimbo" }
}

/// Lazily create the (hidden) frameless webview that renders the tray menu.
fn create_menu_window(app: &AppHandle) -> tauri::Result<()> {
    let window =
        WebviewWindowBuilder::new(app, MENU_WINDOW, WebviewUrl::App("tray-menu.html".into()))
            .title("Nimbo")
            .inner_size(326.0, 420.0)
            .decorations(false)
            .transparent(false)
            .background_color(MENU_WINDOW_BG)
            .shadow(false)
            .always_on_top(true)
            .skip_taskbar(true)
            .resizable(false)
            .visible(false)
            .focused(false)
            .build()?;
    let _ = window.set_background_color(Some(MENU_WINDOW_BG));
    let native_rounding = configure_native_tray_window(&window);
    if !native_rounding {
        if let Ok(size) = window.inner_size() {
            let scale = window.scale_factor().unwrap_or(1.0);
            apply_rounded_region(&window, size.width, size.height, scale, 8.0);
        }
    }
    Ok(())
}

/// Capture the tray icon rectangle and ask the popup to (re)load and reveal itself.
fn open_tray_menu(app: &AppHandle, ix: f64, iy: f64, iw: f64, ih: f64) {
    // Toggle: if the popup is already up — or was dismissed a moment ago by this
    // very click (Windows fires the focus-loss the instant the icon is pressed,
    // hiding the window before this handler runs) — leave it closed instead of
    // popping it straight back open.
    let dismissed_recently = menu_ctl()
        .lock()
        .ok()
        .and_then(|ctl| ctl.last_hidden)
        .map(|at| at.elapsed() < Duration::from_millis(350))
        .unwrap_or(false);
    let visible = app
        .get_webview_window(MENU_WINDOW)
        .and_then(|window| window.is_visible().ok())
        .unwrap_or(false);
    if visible || dismissed_recently {
        if let Some(window) = app.get_webview_window(MENU_WINDOW) {
            let _ = window.hide();
        }
        if let Ok(mut ctl) = menu_ctl().lock() {
            ctl.pending = false;
            ctl.last_hidden = None;
        }
        return;
    }

    {
        let mut ctl = menu_ctl().lock().unwrap();
        ctl.anchor = Some((ix, iy, iw, ih));
        ctl.pending = true;
        ctl.last_hidden = None;
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
    subscription_name: Option<String>,
    latency_ms: Option<u64>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TrayMenuState {
    connected: bool,
    active_server_id: Option<String>,
    connection_mode: ConnectionMode,
    subscription_count: usize,
    server_count: usize,
    language: String,
    visual_preferences: AppPreferences,
    provider_theme: Option<SubscriptionTheme>,
    servers: Vec<TrayMenuServer>,
    /// The active mode needs the TUN driver but the app is not elevated, so a
    /// connect attempt will fail until Nimbo is restarted as administrator. The
    /// flyout shows this up front instead of letting the toggle silently fail.
    needs_admin: bool,
}

/// Elevation can only change by relaunching the process, so probe it once
/// (the check spawns `net session`) and reuse the answer for every tray render.
fn is_elevated_cached() -> bool {
    static ELEVATED: OnceLock<bool> = OnceLock::new();
    *ELEVATED.get_or_init(crate::commands::is_running_as_admin)
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
    let connection_mode = snapshot.connection_mode;
    let subscription_count = snapshot.subscriptions.len();
    let server_count = snapshot
        .subscriptions
        .iter()
        .map(|sub| sub.servers.len())
        .sum();
    let visual_preferences = snapshot.preferences.clone();
    let provider_theme = active_provider_theme(&snapshot);

    let mut servers = Vec::new();
    for sub in &snapshot.subscriptions {
        for server in &sub.servers {
            servers.push(TrayMenuServer {
                id: server.id.clone(),
                name: server.name.clone(),
                subscription_name: sub.name.clone(),
                latency_ms: snapshot.server_pings.get(&server.id).copied(),
            });
        }
    }

    let needs_admin =
        matches!(connection_mode, ConnectionMode::Tun | ConnectionMode::Both) && !is_elevated_cached();

    TrayMenuState {
        connected: snapshot.connected,
        active_server_id,
        connection_mode,
        subscription_count,
        server_count,
        language,
        visual_preferences,
        provider_theme,
        servers,
        needs_admin,
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
/// position it relative to the tray icon and show it.
#[tauri::command]
pub fn tray_menu_resize(
    app: AppHandle,
    width: f64,
    height: f64,
    dpr: Option<f64>,
    radius: Option<f64>,
) {
    let Some(window) = app.get_webview_window(MENU_WINDOW) else {
        return;
    };
    let w = width.max(1.0).round() as u32;
    let h = height.max(1.0).round() as u32;
    // Prefer the webview's own device-pixel-ratio (the same value used to compute
    // `width`/`height`) so the rounded clip region matches the rendered card. The
    // window's `scale_factor()` can lag or disagree on HiDPI, which rounds the
    // region short and leaves WebView2's black corners poking out.
    let scale = dpr
        .filter(|value| value.is_finite() && *value > 0.0)
        .or_else(|| window.scale_factor().ok())
        .unwrap_or(1.0);

    let (anchor, pending) = {
        let mut ctl = menu_ctl().lock().unwrap();
        let result = (ctl.anchor, ctl.pending);
        ctl.pending = false;
        result
    };

    // Resize, apply native rounding, re-clip the fallback region, position and
    // reveal as one unit on the UI thread. Keeping them together means the
    // window bounds and the rounded shape never disagree for a frame.
    let win = window.clone();
    let _ = app.run_on_main_thread(move || {
        let _ = win.set_background_color(Some(MENU_WINDOW_BG));
        let _ = win.set_size(PhysicalSize::new(w, h));
        let native_rounding = configure_native_tray_window(&win);
        if !native_rounding {
            apply_rounded_region(&win, w, h, scale, radius.unwrap_or(8.0));
        }
        if let Some((ix, iy, iw, ih)) = anchor {
            position_menu_window(&win, w, h, ix, iy, iw, ih);
        }
        if pending {
            let _ = win.show();
            let _ = win.set_focus();
        }
    });
}

fn position_menu_window(
    window: &tauri::WebviewWindow,
    w: u32,
    h: u32,
    ix: f64,
    iy: f64,
    iw: f64,
    ih: f64,
) {
    const GAP: f64 = 8.0;

    let icon_center_x = ix + iw / 2.0;
    let icon_center_y = iy + ih / 2.0;

    // Center the flyout horizontally on the icon so it reads as dropping out of
    // it. Default to growing upward (a bottom taskbar is the common case);
    // refined against the work area below.
    let mut left = icon_center_x - w as f64 / 2.0;
    let mut top = iy - h as f64 - GAP;

    // Keep the flyout inside the work area of the icon's monitor, and flip it to
    // the opposite side of the icon when the preferred side has no room (e.g. a
    // top-docked taskbar, where the menu must drop below the icon).
    let monitor = window
        .monitor_from_point(icon_center_x, icon_center_y)
        .ok()
        .flatten()
        .or_else(|| window.current_monitor().ok().flatten());
    if let Some(monitor) = monitor {
        let area = monitor.work_area();
        let min_x = area.position.x as f64;
        let min_y = area.position.y as f64;
        let max_x = min_x + area.size.width as f64;
        let max_y = min_y + area.size.height as f64;

        let above_top = iy - h as f64 - GAP;
        let below_top = iy + ih + GAP;
        let space_above = iy - min_y;
        let space_below = max_y - (iy + ih);
        top = if space_above >= h as f64 + GAP || space_above >= space_below {
            above_top
        } else {
            below_top
        };

        left = left.min(max_x - w as f64).max(min_x);
        top = top.min(max_y - h as f64).max(min_y);
    }

    let _ = window.set_position(PhysicalPosition::new(left.round() as i32, top.round() as i32));
}

/// Perform a menu action. Most actions dismiss the popup; maintenance actions
/// (refresh / ping) run in the background and report progress into the still-open
/// flyout instead.
#[tauri::command]
pub fn tray_menu_action(app: AppHandle, action: String, server_id: Option<String>) {
    // Connect/disconnect now keep the flyout open so the toggle can play its
    // switching animation and surface a result (e.g. the admin-rights prompt)
    // instead of the window vanishing the instant it is clicked.
    let keep_open = matches!(
        action.as_str(),
        "refresh_subscriptions" | "ping_servers" | "connect" | "disconnect"
    );
    if !keep_open {
        if let Some(window) = app.get_webview_window(MENU_WINDOW) {
            let _ = window.hide();
        }
    }
    match action.as_str() {
        "hide" => {}
        "show" => show_main_window(&app),
        "home" => open_main_route(&app, "/"),
        "profiles" => open_main_route(&app, "/subscriptions"),
        "statistics" => open_main_route(&app, "/statistics"),
        "logs" => open_main_route(&app, "/tunnel-logs"),
        "settings" => open_main_route(&app, "/settings"),
        "refresh_subscriptions" => refresh_all_subscriptions(&app),
        "ping_servers" => ping_all_servers(&app),
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

fn open_main_route(app: &AppHandle, route: &'static str) {
    show_main_window(app);
    let _ = app.emit_to("main", "nimbo:navigate", route);
}

fn show_main_window(app: &AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

fn refresh_all_subscriptions(app: &AppHandle) {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let urls = app
            .state::<AppState>()
            .snapshot()
            .subscriptions
            .iter()
            .filter(|sub| sub.url.starts_with("http://") || sub.url.starts_with("https://"))
            .map(|sub| sub.url.clone())
            .collect::<Vec<_>>();

        let mut ok = 0usize;
        for url in urls {
            let state = app.state::<AppState>();
            if crate::commands::refresh_subscription(state, url).await.is_ok() {
                ok += 1;
            }
        }

        let _ = refresh_tray_menu(&app);

        let servers = app
            .state::<AppState>()
            .snapshot()
            .subscriptions
            .iter()
            .map(|sub| sub.servers.len())
            .sum::<usize>();
        let _ = app.emit_to(
            MENU_WINDOW,
            "tray-menu:action-done",
            serde_json::json!({
                "action": "refresh_subscriptions",
                "ok": true,
                "count": ok,
                "servers": servers,
            }),
        );
    });
}

fn ping_all_servers(app: &AppHandle) {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let server_ids = app
            .state::<AppState>()
            .snapshot()
            .subscriptions
            .iter()
            .flat_map(|sub| sub.servers.iter().map(|server| server.id.clone()))
            .collect::<Vec<_>>();

        let mut count = 0usize;
        let mut best: Option<u64> = None;
        if !server_ids.is_empty() {
            let state = app.state::<AppState>();
            if let Ok(results) = crate::commands::ping_servers(state, server_ids).await {
                for result in &results {
                    if let Some(latency) = result.latency_ms {
                        count += 1;
                        best = Some(best.map_or(latency, |current| current.min(latency)));
                    }
                }
            }
        }

        let _ = refresh_tray_menu(&app);
        let _ = app.emit_to(
            MENU_WINDOW,
            "tray-menu:action-done",
            serde_json::json!({
                "action": "ping_servers",
                "ok": true,
                "count": count,
                "best": best,
            }),
        );
    });
}

fn connect_active_server(app: &AppHandle) {
    let Some(server_id) = app.state::<AppState>().snapshot().active_server_id else {
        // No server selected — nothing connected, so report failure to let the
        // flyout drop the switching state instead of waiting for the timeout.
        emit_connect_result(app, "connect", false, None);
        return;
    };
    connect_specific_server(app, server_id);
}

fn connect_specific_server(app: &AppHandle, server_id: String) {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let state = app.state::<AppState>();
        let result = crate::commands::connect_server(app.clone(), state, server_id).await;
        let _ = refresh_tray_menu(&app);
        emit_connect_result(&app, "connect", result.is_ok(), result.err());
    });
}

fn disconnect(app: &AppHandle) {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let state = app.state::<AppState>();
        let result = crate::commands::disconnect_server(app.clone(), state).await;
        let _ = refresh_tray_menu(&app);
        emit_connect_result(&app, "disconnect", result.is_ok(), result.err());
    });
}

/// Tell the still-open flyout how a connect/disconnect attempt ended so it can
/// stop the switching animation and, on an admin-rights failure, prompt to
/// relaunch elevated.
fn emit_connect_result(app: &AppHandle, action: &str, ok: bool, error: Option<String>) {
    let _ = app.emit_to(
        MENU_WINDOW,
        "tray-menu:connect-result",
        serde_json::json!({ "action": action, "ok": ok, "error": error }),
    );
}

/// Ask Windows 11 DWM to round the top-level popup like a native Fluent flyout.
/// This is more reliable than relying on transparent WebView2 corners, which can
/// show a black system-painted rectangle behind the CSS card.
#[cfg(windows)]
fn configure_native_tray_window(window: &tauri::WebviewWindow) -> bool {
    use std::ffi::c_void;
    use windows_sys::Win32::Graphics::Dwm::{
        DwmSetWindowAttribute, DWMWA_BORDER_COLOR, DWMWA_WINDOW_CORNER_PREFERENCE, DWMWCP_ROUND,
    };

    let Ok(handle) = window.hwnd() else {
        return false;
    };
    unsafe {
        let corner = DWMWCP_ROUND;
        let rounded = DwmSetWindowAttribute(
            handle.0 as _,
            DWMWA_WINDOW_CORNER_PREFERENCE as u32,
            &corner as *const _ as *const c_void,
            std::mem::size_of_val(&corner) as u32,
        ) >= 0;

        // Hide the thin square DWM border; the CSS card draws its own rounded
        // border inside the clipped native window.
        let no_border: u32 = 0xFFFFFFFE;
        let _ = DwmSetWindowAttribute(
            handle.0 as _,
            DWMWA_BORDER_COLOR as u32,
            &no_border as *const _ as *const c_void,
            std::mem::size_of_val(&no_border) as u32,
        );
        rounded
    }
}

#[cfg(not(windows))]
fn configure_native_tray_window(_window: &tauri::WebviewWindow) -> bool {
    false
}

/// Clip the popup window to a rounded rectangle that matches the CSS card radius.
/// DWM rounding is preferred; this is a Win32 fallback for systems where DWM
/// does not round an undecorated utility window. Must be called on the UI thread.
#[cfg(windows)]
fn apply_rounded_region(
    window: &tauri::WebviewWindow,
    width: u32,
    height: u32,
    scale: f64,
    css_radius: f64,
) {
    use winapi::shared::windef::HWND;
    use winapi::um::wingdi::CreateRoundRectRgn;
    use winapi::um::winuser::SetWindowRgn;

    // Mirror the actual `.tray-card` border radius, scaled to physical pixels.
    // Bias up by a pixel so the fallback region rounds a hair more than the card
    // instead of stopping short of it.
    let radius = ((css_radius.max(1.0) * scale).round() as i32 + 1).max(1);
    let Ok(handle) = window.hwnd() else {
        return;
    };
    unsafe {
        let region =
            CreateRoundRectRgn(0, 0, width as i32 + 1, height as i32 + 1, radius * 2, radius * 2);
        if !region.is_null() {
            // On success the window takes ownership of the region handle.
            SetWindowRgn(handle.0 as HWND, region, 1);
        }
    }
}

#[cfg(not(windows))]
fn apply_rounded_region(
    _window: &tauri::WebviewWindow,
    _width: u32,
    _height: u32,
    _scale: f64,
    _css_radius: f64,
) {
}
