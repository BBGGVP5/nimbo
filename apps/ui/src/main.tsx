import React from "react";
import ReactDOM from "react-dom/client";
import { HashRouter } from "react-router-dom";
import { getCurrentWindow, LogicalSize, type Window as TauriWindow } from "@tauri-apps/api/window";
import App from "./App";
import { isTauriRuntime } from "./lib/api";
import { useAppStore } from "./store";
import "flag-icons/css/flag-icons.min.css";
import "./styles.css";

useAppStore.getState().hydrate();

document.addEventListener("contextmenu", (event) => {
  event.preventDefault();
});

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <HashRouter>
      <App />
    </HashRouter>
  </React.StrictMode>,
);

const WINDOW_SIZE_STORAGE_KEY = "nimbo.windowSize";
const WINDOW_SIZE_MIN_WIDTH = 320;
const WINDOW_SIZE_MIN_HEIGHT = 520;
const WINDOW_SIZE_SAVE_DELAY_MS = 250;

interface StoredWindowSize {
  width: number;
  height: number;
}

function setupWindowSizeMemory() {
  if (!isTauriRuntime()) return;

  void setupTauriWindowSizeMemory().catch(() => undefined);
}

async function setupTauriWindowSizeMemory() {
  const appWindow = getCurrentWindow();
  const storedSize = readStoredWindowSize();

  if (storedSize) {
    await appWindow.setSize(new LogicalSize(storedSize.width, storedSize.height)).catch(() => undefined);
  }

  let saveTimer: number | undefined;
  await appWindow.onResized(() => {
    if (saveTimer !== undefined) {
      window.clearTimeout(saveTimer);
    }

    saveTimer = window.setTimeout(() => {
      void saveCurrentWindowSize(appWindow).catch(() => undefined);
    }, WINDOW_SIZE_SAVE_DELAY_MS);
  });
}

async function saveCurrentWindowSize(appWindow: TauriWindow) {
  const [isMinimized, isMaximized, isFullscreen] = await Promise.all([
    appWindow.isMinimized(),
    appWindow.isMaximized(),
    appWindow.isFullscreen(),
  ]);

  if (isMinimized || isMaximized || isFullscreen) return;

  const [physicalSize, scaleFactor] = await Promise.all([
    appWindow.innerSize(),
    appWindow.scaleFactor(),
  ]);
  const logicalSize = physicalSize.toLogical(scaleFactor);
  const normalizedSize = normalizeWindowSize(logicalSize.width, logicalSize.height);

  if (!normalizedSize) return;

  try {
    window.localStorage.setItem(WINDOW_SIZE_STORAGE_KEY, JSON.stringify(normalizedSize));
  } catch {
    // Ignore storage failures; window sizing should never block app startup.
  }
}

function readStoredWindowSize(): StoredWindowSize | null {
  try {
    const raw = window.localStorage.getItem(WINDOW_SIZE_STORAGE_KEY);
    if (!raw) return null;

    const parsed = JSON.parse(raw) as Partial<StoredWindowSize>;
    return normalizeWindowSize(parsed.width, parsed.height);
  } catch {
    return null;
  }
}

function normalizeWindowSize(width: unknown, height: unknown): StoredWindowSize | null {
  if (typeof width !== "number" || typeof height !== "number") return null;
  if (!Number.isFinite(width) || !Number.isFinite(height)) return null;

  return {
    width: Math.max(WINDOW_SIZE_MIN_WIDTH, Math.round(width)),
    height: Math.max(WINDOW_SIZE_MIN_HEIGHT, Math.round(height)),
  };
}

setupWindowSizeMemory();
