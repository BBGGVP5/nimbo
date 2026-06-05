import type { AppPreferences, SubscriptionTheme } from "./api";
import { resolveLanguage } from "./i18n";

interface VisualPreferenceOptions {
  includeUiScale?: boolean;
}

export function applyVisualPreferences(
  preferences: AppPreferences,
  providerTheme?: SubscriptionTheme | null,
  options: VisualPreferenceOptions = {},
) {
  if (typeof window === "undefined") return () => {};

  const includeUiScale = options.includeUiScale ?? true;
  const isHexColor = (value: string | null | undefined): value is string =>
    typeof value === "string" && /^#[0-9a-fA-F]{6}$/.test(value.trim());
  const providerAccent = providerTheme?.accent;
  const providerOrb1 = providerTheme?.orb1;
  const providerOrb2 = providerTheme?.orb2;
  const themeAccent = isHexColor(providerAccent) ? providerAccent.trim() : null;
  const themeOrb1 = isHexColor(providerOrb1) ? providerOrb1.trim() : null;
  const themeOrb2 = isHexColor(providerOrb2) ? providerOrb2.trim() : null;
  const themeBlur =
    providerTheme && typeof providerTheme.blur === "number" && Number.isFinite(providerTheme.blur)
      ? providerTheme.blur
      : null;
  const themeUiStyle =
    providerTheme?.ui_style === "material_you" || providerTheme?.ui_style === "nimbo"
      ? providerTheme.ui_style
      : null;

  const media = window.matchMedia("(prefers-color-scheme: light)");
  const apply = () => {
    const resolvedTheme = preferences.theme_mode === "system"
      ? (media.matches ? "light" : "dark")
      : preferences.theme_mode;
    const isLightTheme = resolvedTheme === "light";
    const isBlackTheme = resolvedTheme === "black";
    document.documentElement.lang = resolveLanguage(preferences.language);
    document.body.dataset.theme = resolvedTheme;
    document.body.dataset.uiStyle = themeUiStyle ?? preferences.ui_style;

    const accent = themeAccent
      ?? (preferences.accent_mode === "system"
        ? readSystemAccentColor()
        : preferences.accent_color);
    const bright = mixHex(accent, isLightTheme ? "#ffffff" : isBlackTheme ? "#f2f1ff" : "#d9d2ff", isBlackTheme ? 0.26 : 0.32);
    const soft = mixHex(accent, "#ffffff", 0.58);
    const glow = hexToRgba(accent, isLightTheme ? 0.22 : isBlackTheme ? 0.22 : 0.28);
    const activeBg = hexToRgba(accent, isLightTheme ? 0.12 : isBlackTheme ? 0.14 : 0.16);
    const panel = hexToRgba(accent, isLightTheme ? 0.08 : isBlackTheme ? 0.07 : 0.10);
    const bgAura1 = hexToRgba(themeOrb1 ?? accent, isLightTheme ? 0.15 : isBlackTheme ? 0.09 : 0.20);
    const bgAura2 = hexToRgba(themeOrb2 ?? bright, isLightTheme ? 0.10 : isBlackTheme ? 0.06 : 0.12);
    const bgAura3 = hexToRgba(soft, isLightTheme ? 0.09 : isBlackTheme ? 0.07 : 0.13);
    const root = document.documentElement;
    const panelBrightness = clampVisual(preferences.interface_panel_brightness, 100, 60, 140);
    const transparency = clampVisual(preferences.interface_transparency, 0, 0, 80);
    const blur = clampVisual(themeBlur ?? preferences.interface_blur, 25, 0, 48);
    const rounding = clampVisual(preferences.interface_rounding, 100, 50, 180) / 100;
    const brightnessDelta = panelBrightness - 100;
    const overlayStrength = Math.min(1, Math.abs(brightnessDelta) / 40) * (brightnessDelta > 0 ? 0.18 : 0.22);
    const panelOverlay = brightnessDelta === 0
      ? "rgba(255, 255, 255, 0)"
      : brightnessDelta > 0
        ? `rgba(255, 255, 255, ${overlayStrength.toFixed(3)})`
        : `rgba(0, 0, 0, ${overlayStrength.toFixed(3)})`;
    const scaledRadius = (base: number) => `${Math.max(2, Math.round(base * rounding))}px`;

    root.style.setProperty("--ui-panel-alpha-percent", `${100 - transparency}%`);
    root.style.setProperty("--ui-control-alpha-percent", `${Math.max(20, 100 - Math.round(transparency * 0.84))}%`);
    root.style.setProperty("--ui-panel-overlay", panelOverlay);
    root.style.setProperty("--ui-blur", `${blur}px`);
    root.style.setProperty("--ui-backdrop-filter", `blur(${blur}px) saturate(145%)`);
    root.style.setProperty("--ui-backdrop-filter-soft", `blur(${Math.round(blur * 0.65)}px) saturate(130%)`);
    root.style.setProperty("--ui-radius-xs", scaledRadius(4));
    root.style.setProperty("--ui-radius-sm", scaledRadius(8));
    root.style.setProperty("--ui-radius-md", scaledRadius(10));
    root.style.setProperty("--ui-radius-lg", scaledRadius(14));
    root.style.setProperty("--ui-radius-xl", scaledRadius(16));
    root.style.setProperty("--ui-radius-2xl", scaledRadius(22));
    root.style.setProperty("--ui-radius-3xl", scaledRadius(30));
    root.style.setProperty("--radius-sm", scaledRadius(4));
    root.style.setProperty("--radius-md", scaledRadius(6));
    root.style.setProperty("--radius-lg", scaledRadius(8));
    root.style.setProperty("--radius-xl", scaledRadius(12));
    root.style.setProperty("--radius-2xl", scaledRadius(16));
    root.style.setProperty("--radius-3xl", scaledRadius(24));
    root.style.setProperty("--color-accent", accent);
    root.style.setProperty("--color-accent-bright", bright);
    root.style.setProperty("--color-accent-soft", soft);
    root.style.setProperty("--color-status-connected", bright);
    root.style.setProperty("--color-status-connecting", accent);
    root.style.setProperty("--color-glow-accent", glow);
    root.style.setProperty("--color-accent-active-bg", activeBg);
    root.style.setProperty("--color-accent-panel", panel);
    root.style.setProperty("--color-bg-aura-1", bgAura1);
    root.style.setProperty("--color-bg-aura-2", bgAura2);
    root.style.setProperty("--color-bg-aura-3", bgAura3);
    root.style.setProperty("--app-ui-scale", String(preferences.servers_ui_scale / 100));

    if (includeUiScale) {
      document.body.style.setProperty("zoom", String(preferences.servers_ui_scale / 100));
    } else {
      document.body.style.removeProperty("zoom");
    }
  };

  apply();
  if (preferences.theme_mode === "system") {
    media.addEventListener("change", apply);
    return () => media.removeEventListener("change", apply);
  }
  return () => {};
}

function clampVisual(value: unknown, fallback: number, min: number, max: number) {
  const numeric = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numeric)) return fallback;
  return Math.min(max, Math.max(min, Math.round(numeric)));
}

function readSystemAccentColor(): string {
  const sample = document.createElement("span");
  sample.style.color = "Highlight";
  sample.style.position = "fixed";
  sample.style.visibility = "hidden";
  document.body.appendChild(sample);
  const value = getComputedStyle(sample).color;
  sample.remove();
  return cssColorToHex(value) ?? "#4f8cff";
}

function cssColorToHex(value: string): string | null {
  const match = value.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
  if (!match) return null;
  return rgbToHex(Number(match[1]), Number(match[2]), Number(match[3]));
}

function mixHex(a: string, b: string, amount: number): string {
  const ca = hexToRgb(a);
  const cb = hexToRgb(b);
  if (!ca || !cb) return a;
  return rgbToHex(
    Math.round(ca.r + (cb.r - ca.r) * amount),
    Math.round(ca.g + (cb.g - ca.g) * amount),
    Math.round(ca.b + (cb.b - ca.b) * amount),
  );
}

function hexToRgba(hex: string, alpha: number): string {
  const rgb = hexToRgb(hex);
  if (!rgb) return `rgba(124, 93, 250, ${alpha})`;
  return `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, ${alpha})`;
}

function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const match = hex.match(/^#?([0-9a-f]{6})$/i);
  if (!match) return null;
  const value = Number.parseInt(match[1], 16);
  return { r: (value >> 16) & 255, g: (value >> 8) & 255, b: value & 255 };
}

function rgbToHex(r: number, g: number, b: number): string {
  return `#${[r, g, b].map((x) => Math.max(0, Math.min(255, x)).toString(16).padStart(2, "0")).join("")}`;
}
