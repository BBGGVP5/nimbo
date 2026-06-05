//! Procedurally-rendered monochrome line icons for the tray menu.
//!
//! Windows menu icons are rasterized by `muda` into a fixed 16x16 `HBITMAP`
//! (see `muda::platform_impl::windows::icon::to_hbitmap`), so there is no point
//! shipping larger bitmaps — they would only be downscaled by `DrawIconEx`.
//! Instead we render each glyph at 4x (64px) with analytic, signed-distance
//! anti-aliasing and box-downsample to a crisp 16x16 RGBA `Image`. The result
//! is a clean, Fluent-style set of light line icons that sit well on the dark
//! Windows context menu.

use tauri::image::Image;

/// Output (final) icon size, matching muda's 16x16 menu bitmap.
const OUT: usize = 16;
/// Supersampling factor for analytic anti-aliasing.
const SS: usize = 4;
/// Hi-res working resolution.
const HI: usize = OUT * SS;
/// Edge feather, in normalized units (~one hi-res pixel).
const FEATHER: f32 = 1.15 / HI as f32;
/// Default stroke half-width, in normalized [0,1] units.
const HW: f32 = 0.052;

type Rgb = (u8, u8, u8);

/// Soft white for the line icons — readable on the dark menu without glare.
const LINE: Rgb = (226, 227, 230);
/// Dimmed variant for disabled actions (Connect/Disconnect when unavailable).
const LINE_DIM: Rgb = (118, 121, 128);
/// Connected status dot.
const ACCENT_ON: Rgb = (63, 185, 80);
/// Disconnected status dot.
const ACCENT_OFF: Rgb = (122, 126, 134);

/// A hi-res coverage buffer. Primitives composite via `max` (shape union),
/// which avoids over-darkening where strokes overlap.
struct Canvas {
    cov: Vec<f32>,
}

impl Canvas {
    fn new() -> Self {
        Self { cov: vec![0.0; HI * HI] }
    }

    /// Apply a per-pixel coverage function over the whole canvas, unioning the
    /// result into the buffer. Coordinates passed to `f` are normalized [0,1].
    fn each<F: Fn(f32, f32) -> f32>(&mut self, f: F) {
        for y in 0..HI {
            let ny = (y as f32 + 0.5) / HI as f32;
            for x in 0..HI {
                let nx = (x as f32 + 0.5) / HI as f32;
                let c = f(nx, ny);
                if c > 0.0 {
                    let i = y * HI + x;
                    if c > self.cov[i] {
                        self.cov[i] = c;
                    }
                }
            }
        }
    }

    /// Stroke a line segment with rounded caps.
    fn seg(&mut self, ax: f32, ay: f32, bx: f32, by: f32, hw: f32) {
        self.each(|x, y| cov_stroke(dist_seg(x, y, ax, ay, bx, by), hw));
    }

    /// Stroke a full circle outline.
    fn ring(&mut self, cx: f32, cy: f32, r: f32, hw: f32) {
        self.each(|x, y| {
            let d = ((x - cx).powi(2) + (y - cy).powi(2)).sqrt();
            cov_stroke((d - r).abs(), hw)
        });
    }

    /// Stroke a circular arc, leaving a gap of half-angle `gap_half` (radians)
    /// centered at the top of the circle.
    fn arc_top_gap(&mut self, cx: f32, cy: f32, r: f32, hw: f32, gap_half: f32) {
        let top = -std::f32::consts::FRAC_PI_2;
        self.each(|x, y| {
            let dx = x - cx;
            let dy = y - cy;
            let ang = dy.atan2(dx);
            if ang_diff(ang, top) < gap_half {
                0.0
            } else {
                let d = (dx * dx + dy * dy).sqrt();
                cov_stroke((d - r).abs(), hw)
            }
        });
    }

    /// Fill a disc.
    fn disc(&mut self, cx: f32, cy: f32, r: f32) {
        self.each(|x, y| {
            let d = ((x - cx).powi(2) + (y - cy).powi(2)).sqrt();
            cov_fill(d - r)
        });
    }

    /// Stroke a rounded-rectangle outline given two opposite corners.
    fn rrect(&mut self, x0: f32, y0: f32, x1: f32, y1: f32, r: f32, hw: f32) {
        let cx = (x0 + x1) / 2.0;
        let cy = (y0 + y1) / 2.0;
        let hx = (x1 - x0) / 2.0;
        let hy = (y1 - y0) / 2.0;
        self.each(|x, y| cov_stroke(sd_roundbox(x - cx, y - cy, hx, hy, r).abs(), hw));
    }

    /// Downsample to a 16x16 straight-alpha RGBA image tinted with `color`.
    fn finish(&self, color: Rgb) -> Image<'static> {
        let mut out = vec![0u8; OUT * OUT * 4];
        let area = (SS * SS) as f32;
        for oy in 0..OUT {
            for ox in 0..OUT {
                let mut sum = 0.0;
                for sy in 0..SS {
                    let row = (oy * SS + sy) * HI + ox * SS;
                    for sx in 0..SS {
                        sum += self.cov[row + sx];
                    }
                }
                let a = (sum / area).clamp(0.0, 1.0);
                let i = (oy * OUT + ox) * 4;
                out[i] = color.0;
                out[i + 1] = color.1;
                out[i + 2] = color.2;
                out[i + 3] = (a * 255.0 + 0.5) as u8;
            }
        }
        Image::new_owned(out, OUT as u32, OUT as u32)
    }
}

/// Coverage of a stroke of half-width `hw` at boundary distance `d` (>= 0).
#[inline]
fn cov_stroke(d: f32, hw: f32) -> f32 {
    (0.5 - (d - hw) / FEATHER).clamp(0.0, 1.0)
}

/// Coverage of a filled region given its signed distance (negative = inside).
#[inline]
fn cov_fill(sd: f32) -> f32 {
    (0.5 - sd / FEATHER).clamp(0.0, 1.0)
}

/// Distance from point `p` to segment `a`-`b`.
#[inline]
fn dist_seg(px: f32, py: f32, ax: f32, ay: f32, bx: f32, by: f32) -> f32 {
    let vx = bx - ax;
    let vy = by - ay;
    let wx = px - ax;
    let wy = py - ay;
    let c1 = vx * wx + vy * wy;
    if c1 <= 0.0 {
        return (wx * wx + wy * wy).sqrt();
    }
    let c2 = vx * vx + vy * vy;
    if c2 <= c1 {
        return ((px - bx).powi(2) + (py - by).powi(2)).sqrt();
    }
    let t = c1 / c2;
    let projx = ax + t * vx;
    let projy = ay + t * vy;
    ((px - projx).powi(2) + (py - projy).powi(2)).sqrt()
}

/// Signed distance to a rounded box centered at the origin.
#[inline]
fn sd_roundbox(px: f32, py: f32, hx: f32, hy: f32, r: f32) -> f32 {
    let qx = px.abs() - (hx - r);
    let qy = py.abs() - (hy - r);
    let ox = qx.max(0.0);
    let oy = qy.max(0.0);
    let outside = (ox * ox + oy * oy).sqrt();
    let inside = qx.max(qy).min(0.0);
    outside + inside - r
}

/// Smallest absolute angular difference between two angles (radians).
#[inline]
fn ang_diff(a: f32, b: f32) -> f32 {
    let mut d = (a - b).abs() % (2.0 * std::f32::consts::PI);
    if d > std::f32::consts::PI {
        d = 2.0 * std::f32::consts::PI - d;
    }
    d
}

/// Status indicator: a filled dot, green when connected, muted gray otherwise.
pub fn status_icon(connected: bool) -> Image<'static> {
    let mut c = Canvas::new();
    c.disc(0.5, 0.5, 0.27);
    c.finish(if connected { ACCENT_ON } else { ACCENT_OFF })
}

/// "Show Nimbo": an app window glyph (rounded frame with a title bar).
pub fn show_icon() -> Image<'static> {
    let mut c = Canvas::new();
    c.rrect(0.18, 0.21, 0.82, 0.79, 0.12, 0.05);
    c.seg(0.27, 0.41, 0.73, 0.41, 0.033);
    c.finish(LINE)
}

/// "Connect": a power glyph (open ring with a vertical stem).
pub fn connect_icon(enabled: bool) -> Image<'static> {
    let mut c = Canvas::new();
    c.arc_top_gap(0.5, 0.52, 0.30, HW, 0.60);
    c.seg(0.5, 0.16, 0.5, 0.50, HW);
    c.finish(if enabled { LINE } else { LINE_DIM })
}

/// "Disconnect": a ring with a diagonal slash ("turned off / no connection").
pub fn disconnect_icon(enabled: bool) -> Image<'static> {
    let mut c = Canvas::new();
    c.ring(0.5, 0.5, 0.30, HW);
    c.seg(0.29, 0.29, 0.71, 0.71, HW);
    c.finish(if enabled { LINE } else { LINE_DIM })
}

/// "Servers": a two-unit server rack with status LEDs.
pub fn servers_icon() -> Image<'static> {
    let mut c = Canvas::new();
    c.rrect(0.17, 0.20, 0.83, 0.43, 0.05, 0.05);
    c.rrect(0.17, 0.57, 0.83, 0.80, 0.05, 0.05);
    c.disc(0.34, 0.315, 0.046);
    c.disc(0.34, 0.685, 0.046);
    c.finish(LINE)
}

/// "Quit": a logout glyph (door frame open on the right with an exiting arrow).
pub fn quit_icon() -> Image<'static> {
    let mut c = Canvas::new();
    // Door frame, open on the right side.
    c.seg(0.26, 0.20, 0.26, 0.80, HW);
    c.seg(0.26, 0.20, 0.50, 0.20, HW);
    c.seg(0.26, 0.80, 0.50, 0.80, HW);
    // Arrow exiting to the right.
    c.seg(0.40, 0.50, 0.82, 0.50, HW);
    c.seg(0.66, 0.34, 0.83, 0.50, HW);
    c.seg(0.66, 0.66, 0.83, 0.50, HW);
    c.finish(LINE)
}
