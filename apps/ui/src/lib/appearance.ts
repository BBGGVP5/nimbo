import { useEffect, useState } from "react";
import { DEFAULT_ACCENT_COLOR, DEFAULT_ACCENT_PALETTE } from "./api";

// ── Types ─────────────────────────────────────────────────────

export type BackgroundMediaType = "image" | "video";

export interface PalettePreset {
  id: string;
  colors: string[];
}

export interface AppearanceState {
  /** 1–3 hex colours used when the accent mode is "custom". */
  palette: string[];
  /** Saved custom palettes. */
  presets: PalettePreset[];
  /** "none" | built-in preset id | "custom". */
  background: string;
  /** Type of the uploaded custom background, if any. */
  customType: BackgroundMediaType | null;
  /** Display name of the uploaded custom background. */
  customName: string | null;
  /** Darkening overlay over the background, 0–90 (%). */
  backgroundDim: number;
  /** Blur applied to the background, 0–40 (px). */
  backgroundBlur: number;
}

export interface BackgroundPreset {
  id: string;
  label: string;
  animated: boolean;
}

export const BACKGROUND_PRESETS: BackgroundPreset[] = [
  { id: "none", label: "Стандарт", animated: false },
  { id: "aurora", label: "Aurora", animated: true },
  { id: "nebula", label: "Nebula", animated: true },
  { id: "sunset", label: "Sunset", animated: true },
  { id: "ocean", label: "Ocean", animated: true },
  { id: "emerald", label: "Emerald", animated: true },
  { id: "mesh", label: "Mesh", animated: true },
  { id: "cyberpunk", label: "Cyberpunk", animated: true },
  { id: "deepspace", label: "Deep Space", animated: true },
  { id: "fire", label: "Fire", animated: true },
  { id: "lava", label: "Lava", animated: true },
  { id: "neon", label: "Neon", animated: true },
  { id: "nordic", label: "Nordic", animated: true },
  { id: "blossom", label: "Blossom", animated: true },
  { id: "grid", label: "Grid", animated: true },
  { id: "mono", label: "Mono", animated: false },
];

// ── Persistence ───────────────────────────────────────────────

const STORAGE_KEY = "nimbo.appearance.v1";
const CHANGE_EVENT = "nimbo:appearance-change";

const DEFAULT_STATE: AppearanceState = {
  palette: [...DEFAULT_ACCENT_PALETTE],
  presets: [],
  background: "none",
  customType: null,
  customName: null,
  backgroundDim: 38,
  backgroundBlur: 0,
};

function clampHexList(value: unknown): string[] {
  if (!Array.isArray(value)) return [...DEFAULT_STATE.palette];
  const colors = value
    .filter((c): c is string => typeof c === "string" && /^#[0-9a-f]{6}$/i.test(c))
    .slice(0, 3)
    .map((c) => c.toLowerCase());
  return colors.length ? colors : [...DEFAULT_STATE.palette];
}

function loadState(): AppearanceState {
  if (typeof window === "undefined") return { ...DEFAULT_STATE };
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_STATE };
    const parsed = JSON.parse(raw) as Partial<AppearanceState>;
    return {
      palette: clampHexList(parsed.palette),
      presets: Array.isArray(parsed.presets)
        ? parsed.presets
            .filter((p): p is PalettePreset => Boolean(p) && Array.isArray((p as PalettePreset).colors))
            .map((p) => ({ id: String(p.id), colors: clampHexList(p.colors) }))
        : [],
      background: typeof parsed.background === "string" ? parsed.background : DEFAULT_STATE.background,
      customType: parsed.customType === "image" || parsed.customType === "video" ? parsed.customType : null,
      customName: typeof parsed.customName === "string" ? parsed.customName : null,
      backgroundDim: clampNumber(parsed.backgroundDim, DEFAULT_STATE.backgroundDim, 0, 90),
      backgroundBlur: clampNumber(parsed.backgroundBlur, DEFAULT_STATE.backgroundBlur, 0, 40),
    };
  } catch {
    return { ...DEFAULT_STATE };
  }
}

function clampNumber(value: unknown, fallback: number, min: number, max: number): number {
  const numeric = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numeric)) return fallback;
  return Math.min(max, Math.max(min, Math.round(numeric)));
}

let state: AppearanceState = loadState();

function persist() {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    /* ignore quota errors */
  }
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

export function getAppearance(): AppearanceState {
  return state;
}

export function refreshAppearance(): AppearanceState {
  state = loadState();
  return state;
}

export function setAppearance(patch: Partial<AppearanceState>): void {
  state = { ...state, ...patch };
  persist();
}

export function subscribeAppearance(listener: () => void): () => void {
  window.addEventListener(CHANGE_EVENT, listener);
  return () => window.removeEventListener(CHANGE_EVENT, listener);
}

export function useAppearance(): AppearanceState {
  const [value, setValue] = useState<AppearanceState>(() => getAppearance());
  useEffect(() => {
    const update = () => setValue(getAppearance());
    update();
    return subscribeAppearance(update);
  }, []);
  return value;
}

// ── Palette presets ───────────────────────────────────────────

export function savePalettePreset(colors: string[]): void {
  const normalized = clampHexList(colors);
  const id = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
  const exists = state.presets.some(
    (p) => p.colors.join(",").toLowerCase() === normalized.join(",").toLowerCase(),
  );
  if (exists) return;
  setAppearance({ presets: [...state.presets, { id, colors: normalized }].slice(-12) });
}

export function removePalettePreset(id: string): void {
  setAppearance({ presets: state.presets.filter((p) => p.id !== id) });
}

export function accentGradientCss(colors: string[]): string {
  if (colors.length >= 3) return `linear-gradient(135deg, ${colors[0]}, ${colors[1]}, ${colors[2]})`;
  if (colors.length === 2) return `linear-gradient(135deg, ${colors[0]}, ${colors[1]})`;
  return colors[0] ?? DEFAULT_ACCENT_COLOR;
}

/** Applies the custom-accent gradient CSS variables to the document root. */
export function applyAccentGradient(accentMode: string, accentColor: string, palette: string[]): void {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  const colors = accentMode === "custom" && palette.length >= 1
    ? palette
    : accentColor.toLowerCase() === DEFAULT_ACCENT_COLOR
      ? [...DEFAULT_ACCENT_PALETTE]
      : [accentColor];
  root.style.setProperty("--accent-gradient", accentGradientCss(colors));
  root.style.setProperty("--color-accent-2", colors[1] ?? colors[0] ?? accentColor);
  root.style.setProperty("--color-accent-3", colors[2] ?? colors[1] ?? colors[0] ?? accentColor);
}

// ── Custom background blob (IndexedDB) ────────────────────────

const DB_NAME = "nimbo-appearance";
const STORE = "media";
const BG_KEY = "background";

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE)) {
        request.result.createObjectStore(STORE);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function saveBackgroundBlob(blob: Blob): Promise<void> {
  const db = await openDb();
  try {
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE, "readwrite");
      tx.objectStore(STORE).put(blob, BG_KEY);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}

export async function loadBackgroundBlob(): Promise<Blob | null> {
  const db = await openDb();
  try {
    return await new Promise<Blob | null>((resolve, reject) => {
      const tx = db.transaction(STORE, "readonly");
      const request = tx.objectStore(STORE).get(BG_KEY);
      request.onsuccess = () => resolve((request.result as Blob | undefined) ?? null);
      request.onerror = () => reject(request.error);
    });
  } finally {
    db.close();
  }
}

export async function clearBackgroundBlob(): Promise<void> {
  const db = await openDb();
  try {
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE, "readwrite");
      tx.objectStore(STORE).delete(BG_KEY);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}
