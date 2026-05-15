import { useCallback, useEffect, useRef, useState } from "react";
import { NavLink, Route, Routes, Navigate, useLocation, useNavigate } from "react-router-dom";
import { Home } from "./pages/Home";
import { Subscriptions } from "./pages/Subscriptions";
import { Servers } from "./pages/Servers";
import { Applications } from "./pages/Applications";
import { Settings } from "./pages/Settings";
import { NotificationCenter } from "./components/NotificationCenter";
import { useAppStore } from "./store";
import { api, type AppPreferences, type AppUpdateInfo } from "./lib/api";
import { initNimboDeepLinks } from "./lib/deepLinks";
import { fillTemplate, useMessages, type Messages } from "./lib/i18n";
import nimboLogo from "./assets/nimbo.png";

const APP_UPDATE_DIALOG_EVENT = "nimbo:show-update-dialog";

type AppUpdateDialogEvent = CustomEvent<AppUpdateInfo>;

const navItems = [
  { to: "/", key: "home", icon: "bolt", end: true },
  { to: "/subscriptions", key: "profiles", icon: "globe", end: false },
  { to: "/apps", key: "apps", icon: "phone", end: false },
  { to: "/settings", key: "settings", icon: "settings", end: false },
];

export default function App() {
  const preferences = useAppStore((s) => s.preferences);
  const status = useAppStore((s) => s.status);
  const subscriptions = useAppStore((s) => s.subscriptions);
  const activeServerId = useAppStore((s) => s.activeServerId);
  const connectServer = useAppStore((s) => s.connectServer);
  const hydrate = useAppStore((s) => s.hydrate);
  const launchedActions = useRef(false);
  const onboardingChecked = useRef(false);
  const sidebarWidth = useResizableSidebar();
  const updateChecked = useRef(false);
  const updateCheckInFlight = useRef(false);
  const updateStartupScheduled = useRef(false);
  const updateRetryTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const checkUpdatesOnLaunch = useRef(preferences.check_updates_on_launch);
  const [startupUpdate, setStartupUpdate] = useState<AppUpdateInfo | null>(null);
  const m = useMessages();

  useEffect(() => applyVisualPreferences(preferences), [preferences]);

  useEffect(() => {
    checkUpdatesOnLaunch.current = preferences.check_updates_on_launch;
  }, [preferences.check_updates_on_launch]);

  useEffect(() => {
    if (launchedActions.current || !status) return;
    launchedActions.current = true;

    const serverIds = subscriptions.flatMap((sub) => sub.servers.map((server) => server.id));
    if (preferences.ping_on_launch && serverIds.length) {
      void api.pingServers(serverIds).then(() => hydrate()).catch(() => undefined);
    }

    if (
      preferences.auto_connect_on_launch &&
      status.state === "disconnected" &&
      activeServerId
    ) {
      void connectServer(activeServerId).catch(() => undefined);
    }
  }, [activeServerId, connectServer, hydrate, preferences.auto_connect_on_launch, preferences.ping_on_launch, status, subscriptions]);

  useEffect(() => {
    if (
      updateStartupScheduled.current ||
      updateChecked.current ||
      !preferences.check_updates_on_launch
    ) {
      return;
    }

    updateStartupScheduled.current = true;

    const retryDelaysMs = [180, 1200, 3000, 7000, 15000, 30000];
    let attempt = 0;
    let cancelled = false;

    const scheduleAttempt = (delay: number) => {
      updateRetryTimer.current = setTimeout(runAttempt, delay);
    };

    const runAttempt = () => {
      if (cancelled) return;

      if (!checkUpdatesOnLaunch.current) {
        updateChecked.current = true;
        return;
      }

      if (updateChecked.current || updateCheckInFlight.current) return;

      updateCheckInFlight.current = true;
      void api.checkAppUpdate()
        .then((update) => {
          if (cancelled) return;

          updateChecked.current = true;
          if (update.available) setStartupUpdate(update);
        })
        .catch(() => {
          if (cancelled) return;

          attempt += 1;
          if (!updateChecked.current && attempt < retryDelaysMs.length) {
            scheduleAttempt(retryDelaysMs[attempt]);
          }
        })
        .finally(() => {
          updateCheckInFlight.current = false;
        });
    };

    scheduleAttempt(retryDelaysMs[attempt]);

    return () => {
      cancelled = true;
      if (updateRetryTimer.current) clearTimeout(updateRetryTimer.current);
      if (!updateChecked.current) updateStartupScheduled.current = false;
    };
  }, [preferences.check_updates_on_launch]);

  useEffect(() => {
    const onShowUpdateDialog = (event: Event) => {
      const update = (event as AppUpdateDialogEvent).detail;
      if (update?.available) setStartupUpdate(update);
    };
    window.addEventListener(APP_UPDATE_DIALOG_EVENT, onShowUpdateDialog);
    return () => window.removeEventListener(APP_UPDATE_DIALOG_EVENT, onShowUpdateDialog);
  }, []);

  return (
    <div className="app-shell flex h-full">
      <DeepLinkBridge />
      <OnboardingRedirect
        ready={Boolean(status)}
        hasSubscriptions={subscriptions.length > 0}
        checkedRef={onboardingChecked}
      />
      <NotificationCenter />
      {startupUpdate && (
        <UpdateDialog
          update={startupUpdate}
          onDownload={() => {
            const url = startupUpdate.download_url ?? startupUpdate.release_url;
            void api.openUpdateDownload(url);
            setStartupUpdate(null);
          }}
          onClose={() => setStartupUpdate(null)}
        />
      )}
      <aside
        className="app-sidebar shrink-0 flex flex-col p-3"
        style={{ "--sidebar-width": `${sidebarWidth.width}px` } as React.CSSProperties}
      >
        <div className="glass rounded-2xl flex-1 flex flex-col overflow-hidden">
          <div className="app-brand px-5 pt-5 pb-4">
            <div className="app-brand-lockup">
              <img src={nimboLogo} alt="" className="app-brand-logo" aria-hidden="true" />
              <div className="text-lg font-semibold tracking-tight bg-gradient-to-r from-[var(--color-accent-bright)] to-[var(--color-status-connected)] bg-clip-text text-transparent">
                Nimbo
              </div>
            </div>
          </div>
          <nav className="flex-1 px-2 space-y-1">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  [
                    "app-nav-link block px-4 py-3 my-0.5 rounded-xl text-sm transition-all",
                    isActive
                      ? "border-[var(--color-border-strong)] bg-[rgba(255,255,255,0.055)] text-[var(--color-text)] shadow-[inset_0_1px_0_rgba(255,255,255,0.06),0_0_18px_var(--color-glow-accent)]"
                      : "text-[var(--color-text-dim)] hover:border-[var(--color-border)] hover:text-[var(--color-text)] hover:bg-[rgba(255,255,255,0.035)]",
                  ].join(" ")
                }
              >
                <NavIcon name={item.icon} />
                <span className="app-nav-text">{navLabel(m.app, item.key, false)}</span>
                <span className="app-nav-short">{navLabel(m.app, item.key, true)}</span>
              </NavLink>
            ))}
          </nav>
          <div className="app-build px-5 py-4 text-[10px] text-[var(--color-text-faint)] font-mono uppercase tracking-wider">
            v0.1.0
          </div>
        </div>
      </aside>

      <div
        role="separator"
        aria-orientation="vertical"
        aria-label="Изменить ширину панели навигации"
        className="app-sidebar-resizer"
        onMouseDown={sidebarWidth.onResizeStart}
        onDoubleClick={sidebarWidth.reset}
      />

      <main className="app-main flex-1 overflow-auto p-3 pl-0">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/subscriptions" element={<Subscriptions />} />
          <Route path="/subscriptions/:url" element={<Servers />} />
          <Route path="/apps" element={<Applications />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}

export function showAppUpdateDialog(update: AppUpdateInfo) {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(APP_UPDATE_DIALOG_EVENT, { detail: update }));
}

function UpdateDialog({
  update,
  onDownload,
  onClose,
}: {
  update: AppUpdateInfo;
  onDownload: () => void;
  onClose: () => void;
}) {
  const m = useMessages();
  const canDownload = Boolean(update.download_url || update.release_url);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="update-dialog-backdrop" role="presentation" onClick={onClose}>
      <div
        className="update-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="update-dialog-title"
        aria-describedby="update-dialog-text"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="update-dialog-art">
          <div className="update-dialog-orbit">
            <img src={nimboLogo} alt="" className="update-dialog-logo" aria-hidden="true" />
          </div>
        </div>
        <h2 id="update-dialog-title" className="update-dialog-title">{m.settings.updateAvailable}</h2>
        <div className="update-dialog-version">v{update.latest_version}</div>
        <p id="update-dialog-text" className="update-dialog-text">
          {fillTemplate(m.settings.updateReady, { version: update.latest_version })}
          <br />
          {update.asset ? m.settings.updateRecommended : m.settings.updateNoAsset}
        </p>
        <button
          type="button"
          className="update-dialog-download"
          disabled={!canDownload}
          onClick={onDownload}
        >
          {m.settings.downloadUpdate}
        </button>
        <button type="button" className="update-dialog-later" onClick={onClose}>
          {m.common.later}
        </button>
      </div>
    </div>
  );
}

function navLabel(labels: Messages["app"], key: string, short: boolean): string {
  if (key === "home") return labels.home;
  if (key === "profiles") return labels.profiles;
  if (key === "apps") return short ? labels.appsShort : labels.apps;
  return labels.settings;
}

function applyVisualPreferences(preferences: AppPreferences) {
  if (typeof window === "undefined") return () => {};

  const media = window.matchMedia("(prefers-color-scheme: light)");
  const apply = () => {
    const resolvedTheme = preferences.theme_mode === "system"
      ? (media.matches ? "light" : "dark")
      : preferences.theme_mode;
    document.documentElement.lang = preferences.language;
    document.body.dataset.theme = resolvedTheme;

    const accent = preferences.accent_mode === "system"
      ? readSystemAccentColor()
      : preferences.accent_color;
    const bright = mixHex(accent, resolvedTheme === "light" ? "#ffffff" : "#d9d2ff", 0.32);
    const soft = mixHex(accent, "#ffffff", 0.58);
    const glow = hexToRgba(accent, resolvedTheme === "light" ? 0.22 : 0.28);
    const activeBg = hexToRgba(accent, resolvedTheme === "light" ? 0.12 : 0.16);
    const panel = hexToRgba(accent, resolvedTheme === "light" ? 0.08 : 0.10);
    const bgAura1 = hexToRgba(accent, resolvedTheme === "light" ? 0.15 : 0.20);
    const bgAura2 = hexToRgba(bright, resolvedTheme === "light" ? 0.10 : 0.12);
    const bgAura3 = hexToRgba(soft, resolvedTheme === "light" ? 0.09 : 0.13);
    const root = document.documentElement;
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
  };

  apply();
  if (preferences.theme_mode === "system") {
    media.addEventListener("change", apply);
    return () => media.removeEventListener("change", apply);
  }
  return () => {};
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

const SIDEBAR_WIDTH_KEY = "nimbo.sidebarWidth";
const SIDEBAR_WIDTH_DEFAULT = 200;
const SIDEBAR_WIDTH_MIN = 160;
const SIDEBAR_WIDTH_MAX = 340;

function useResizableSidebar() {
  const [width, setWidth] = useState<number>(() => {
    if (typeof window === "undefined") return SIDEBAR_WIDTH_DEFAULT;
    const stored = Number.parseInt(window.localStorage.getItem(SIDEBAR_WIDTH_KEY) ?? "", 10);
    if (Number.isFinite(stored) && stored >= SIDEBAR_WIDTH_MIN && stored <= SIDEBAR_WIDTH_MAX) {
      return stored;
    }
    return SIDEBAR_WIDTH_DEFAULT;
  });

  useEffect(() => {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(SIDEBAR_WIDTH_KEY, String(width));
  }, [width]);

  const onResizeStart = useCallback((event: React.MouseEvent) => {
    event.preventDefault();
    const startX = event.clientX;
    const startWidth = (event.currentTarget.previousElementSibling as HTMLElement | null)?.getBoundingClientRect().width ?? width;
    const onMove = (e: MouseEvent) => {
      const delta = e.clientX - startX;
      const next = Math.min(SIDEBAR_WIDTH_MAX, Math.max(SIDEBAR_WIDTH_MIN, startWidth + delta));
      setWidth(next);
    };
    const onUp = () => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
  }, [width]);

  const reset = useCallback(() => setWidth(SIDEBAR_WIDTH_DEFAULT), []);

  return { width, onResizeStart, reset };
}

function OnboardingRedirect({
  ready,
  hasSubscriptions,
  checkedRef,
}: {
  ready: boolean;
  hasSubscriptions: boolean;
  checkedRef: React.MutableRefObject<boolean>;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  useEffect(() => {
    if (checkedRef.current || !ready) return;
    checkedRef.current = true;
    if (!hasSubscriptions && location.pathname === "/") {
      navigate("/subscriptions", { replace: true });
    }
  }, [ready, hasSubscriptions, location.pathname, navigate, checkedRef]);
  return null;
}

function DeepLinkBridge() {
  const navigate = useNavigate();
  const openImportDialog = useAppStore((s) => s.openImportDialog);
  const connectServer = useAppStore((s) => s.connectServer);
  const disconnectServer = useAppStore((s) => s.disconnectServer);
  const refreshSubscription = useAppStore((s) => s.refreshSubscription);

  useEffect(() => {
    let dispose = () => {};

    void initNimboDeepLinks({
      navigate,
      openImportDialog,
      connectServer,
      disconnectServer,
      refreshSubscription,
    }).then((unlisten) => {
      dispose = unlisten;
    });

    return () => {
      dispose();
    };
  }, [navigate, openImportDialog, connectServer, disconnectServer, refreshSubscription]);

  return null;
}

function NavIcon({ name }: { name: string }) {
  const common = {
    className: "app-nav-icon",
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: "1.8",
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
  };

  if (name === "bolt") {
    return (
      <svg {...common}>
        <path d="m13 2-9 12h7l-1 8 9-12h-7l1-8Z" />
      </svg>
    );
  }
  if (name === "globe") {
    return (
      <svg {...common}>
        <circle cx="12" cy="12" r="9" />
        <path d="M3 12h18" />
        <path d="M12 3a13.5 13.5 0 0 1 0 18" />
        <path d="M12 3a13.5 13.5 0 0 0 0 18" />
      </svg>
    );
  }
  if (name === "phone") {
    return (
      <svg {...common}>
        <rect x="7" y="3" width="10" height="18" rx="2.2" />
        <path d="M11 17h2" />
      </svg>
    );
  }
  return (
    <svg {...common}>
      <path d="M12 15.5A3.5 3.5 0 1 0 12 8a3.5 3.5 0 0 0 0 7.5Z" />
      <path d="M19.4 15a1.8 1.8 0 0 0 .36 1.98l.04.04a2.1 2.1 0 0 1-2.98 2.98l-.04-.04a1.8 1.8 0 0 0-1.98-.36 1.8 1.8 0 0 0-1.1 1.66V21a2.1 2.1 0 0 1-4.2 0v-.06A1.8 1.8 0 0 0 8.4 19.3a1.8 1.8 0 0 0-1.98.36l-.04.04a2.1 2.1 0 1 1-2.98-2.98l.04-.04A1.8 1.8 0 0 0 3.8 14.7 1.8 1.8 0 0 0 2.14 13H2a2.1 2.1 0 0 1 0-4.2h.06A1.8 1.8 0 0 0 3.7 7.7a1.8 1.8 0 0 0-.36-1.98L3.3 5.68A2.1 2.1 0 1 1 6.28 2.7l.04.04A1.8 1.8 0 0 0 8.3 3.1 1.8 1.8 0 0 0 10 1.44V1.4a2.1 2.1 0 0 1 4.2 0v.06a1.8 1.8 0 0 0 1.1 1.64 1.8 1.8 0 0 0 1.98-.36l.04-.04a2.1 2.1 0 1 1 2.98 2.98l-.04.04a1.8 1.8 0 0 0-.36 1.98 1.8 1.8 0 0 0 1.66 1.1H22a2.1 2.1 0 0 1 0 4.2h-.06A1.8 1.8 0 0 0 19.4 15Z" />
    </svg>
  );
}
