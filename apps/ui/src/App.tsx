import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { NavLink, Route, Routes, Navigate, useLocation, useNavigate } from "react-router-dom";
import { Home } from "./pages/Home";
import { Subscriptions } from "./pages/Subscriptions";
import { Servers } from "./pages/Servers";
import { Applications } from "./pages/Applications";
import { Connections } from "./pages/Connections";
import { Routing } from "./pages/Routing";
import { Statistics } from "./pages/Statistics";
import { TunnelLogs } from "./pages/TunnelLogs";
import { Settings } from "./pages/Settings";
import { Notifications } from "./pages/Notifications";
import { NotificationCenter } from "./components/NotificationCenter";
import { useNotificationHistory } from "./lib/notify";
import {
  applyAccentGradient,
  loadBackgroundBlob,
  useAppearance,
  type AppearanceState,
} from "./lib/appearance";
import { useAppStore } from "./store";
import { api, type AppPreferences, type AppUpdateInfo, type ConflictingProcess, type HelperStatus, type SubscriptionTheme } from "./lib/api";
import { cachedSubscriptionTheme } from "./lib/subscriptionTheme";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { initNimboDeepLinks } from "./lib/deepLinks";
import { fillTemplate, resolveLanguage, useMessages, type Messages } from "./lib/i18n";
import nimboLogo from "./assets/nimbo.png";

const APP_UPDATE_DIALOG_EVENT = "nimbo:show-update-dialog";

type AppUpdateDialogEvent = CustomEvent<AppUpdateInfo>;

const navItems = [
  { to: "/", key: "home", icon: "bolt", end: true, compactHide: false },
  { to: "/subscriptions", key: "profiles", icon: "globe", end: false, compactHide: false },
  { to: "/routing", key: "routing", icon: "route", end: false, compactHide: true },
  { to: "/apps", key: "apps", icon: "phone", end: false, compactHide: false },
  { to: "/connections", key: "connections", icon: "connections", end: false, compactHide: true },
  { to: "/statistics", key: "statistics", icon: "stats", end: false, compactHide: true },
  { to: "/tunnel-logs", key: "tunnelLogs", icon: "logs", end: false, compactHide: true },
  { to: "/notifications", key: "notifications", icon: "bell", end: false, compactHide: true },
  { to: "/settings", key: "settings", icon: "settings", end: false, compactHide: false },
];

export default function App() {
  const preferences = useAppStore((s) => s.preferences);
  const status = useAppStore((s) => s.status);
  const error = useAppStore((s) => s.error);
  const subscriptions = useAppStore((s) => s.subscriptions);
  const activeServerId = useAppStore((s) => s.activeServerId);
  const activeSubscriptionUrl = useAppStore((s) => s.activeSubscriptionUrl);
  const connectServer = useAppStore((s) => s.connectServer);
  const hydrate = useAppStore((s) => s.hydrate);
  const refreshSubscription = useAppStore((s) => s.refreshSubscription);
  const conflictDialogOpen = useAppStore((s) => s.conflictDialogOpen);
  const conflictingProcesses = useAppStore((s) => s.conflictingProcesses);
  const conflictStopping = useAppStore((s) => s.conflictStopping);
  const conflictStopError = useAppStore((s) => s.conflictStopError);
  const scanConflictingProcesses = useAppStore((s) => s.scanConflictingProcesses);
  const closeConflictDialog = useAppStore((s) => s.closeConflictDialog);
  const stopConflictingProcesses = useAppStore((s) => s.stopConflictingProcesses);
  const helperStatus = useAppStore((s) => s.helperStatus);
  const helperInstalling = useAppStore((s) => s.helperInstalling);
  const helperError = useAppStore((s) => s.helperError);
  const installHelper = useAppStore((s) => s.installHelper);
  const launchedActions = useRef(false);
  const onboardingChecked = useRef(false);
  const sidebarWidth = useResizableSidebar();
  const updateChecked = useRef(false);
  const updateCheckInFlight = useRef(false);
  const updateStartupScheduled = useRef(false);
  const updateRetryTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const checkUpdatesOnLaunch = useRef(preferences.check_updates_on_launch);
  const lastAwakeTick = useRef(Date.now());
  const resumeReconnectInFlight = useRef(false);
  const [startupUpdate, setStartupUpdate] = useState<AppUpdateInfo | null>(null);
  const m = useMessages();
  const { unread: unreadNotifications } = useNotificationHistory();
  const appearance = useAppearance();

  const providerTheme = useMemo<SubscriptionTheme | null>(() => {
    if (!preferences.provider_theme) return null;
    const active =
      subscriptions.find((s) => s.url === activeSubscriptionUrl) ??
      subscriptions.find((s) => s.servers.some((srv) => srv.id === activeServerId));
    return cachedSubscriptionTheme(active);
  }, [preferences.provider_theme, subscriptions, activeSubscriptionUrl, activeServerId]);

  useEffect(
    () => applyVisualPreferences(preferences, providerTheme),
    [preferences, providerTheme],
  );

  useEffect(() => {
    const isHex = (c: string | null | undefined): c is string =>
      typeof c === "string" && /^#[0-9a-fA-F]{6}$/.test(c.trim());
    if (isHex(providerTheme?.accent)) {
      const orbs = [providerTheme!.accent, providerTheme!.orb1, providerTheme!.orb2]
        .filter(isHex)
        .map((c) => c.trim());
      applyAccentGradient("custom", orbs[0], orbs);
    } else {
      applyAccentGradient(preferences.accent_mode, preferences.accent_color, appearance.palette);
    }
  }, [providerTheme, preferences.accent_mode, preferences.accent_color, appearance.palette]);

  useEffect(() => {
    // Notify the backend that the React frontend has mounted and is ready
    void api.appReady().catch(() => undefined);
  }, []);

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

    if (preferences.subscriptions_update_on_launch) {
      const remoteSubscriptions = subscriptions.filter((sub) => /^https?:\/\//i.test(sub.url));
      void Promise.allSettled(remoteSubscriptions.map((sub) => refreshSubscription(sub.url)))
        .then(async () => {
          if (!preferences.subscriptions_ping_after_update) return;
          const refreshedIds = useAppStore
            .getState()
            .subscriptions
            .flatMap((sub) => sub.servers.map((server) => server.id));
          if (refreshedIds.length) {
            await api.pingServers(refreshedIds).catch(() => undefined);
            await hydrate();
          }
        })
        .catch(() => undefined);
    }

    void (async () => {
      const conflicts = await scanConflictingProcesses().catch(() => []);
      if (conflicts.length === 0) {
        window.setTimeout(() => {
          void scanConflictingProcesses().catch(() => []);
        }, 1500);
      }

      if (
        conflicts.length === 0 &&
        preferences.auto_connect_on_launch &&
        status.state === "disconnected" &&
        activeServerId
      ) {
        await connectServer(activeServerId).catch(() => undefined);
      }
    })();
  }, [activeServerId, connectServer, hydrate, preferences.auto_connect_on_launch, preferences.ping_on_launch, preferences.subscriptions_ping_after_update, preferences.subscriptions_update_on_launch, refreshSubscription, scanConflictingProcesses, status, subscriptions]);

  useEffect(() => {
    if (!preferences.subscriptions_auto_update) return;
    const intervalMs = preferences.subscriptions_update_interval_hours * 60 * 60 * 1000;
    const timer = window.setInterval(() => {
      const remoteSubscriptions = useAppStore
        .getState()
        .subscriptions
        .filter((sub) => /^https?:\/\//i.test(sub.url));
      void Promise.allSettled(remoteSubscriptions.map((sub) => refreshSubscription(sub.url)))
        .then(async () => {
          if (!preferences.subscriptions_ping_after_update) return;
          const serverIds = useAppStore
            .getState()
            .subscriptions
            .flatMap((sub) => sub.servers.map((server) => server.id));
          if (serverIds.length) {
            await api.pingServers(serverIds).catch(() => undefined);
            await hydrate();
          }
        })
        .catch(() => undefined);
    }, intervalMs);
    return () => window.clearInterval(timer);
  }, [hydrate, preferences.subscriptions_auto_update, preferences.subscriptions_ping_after_update, preferences.subscriptions_update_interval_hours, refreshSubscription]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      const now = Date.now();
      const gap = now - lastAwakeTick.current;
      lastAwakeTick.current = now;
      if (gap < 30000 || resumeReconnectInFlight.current) return;

      const state = useAppStore.getState();
      if (state.status?.state !== "connected" || !state.activeServerId) return;
      resumeReconnectInFlight.current = true;
      void state.connectServer(state.activeServerId)
        .catch(() => state.hydrate())
        .finally(() => {
          resumeReconnectInFlight.current = false;
        });
    }, 5000);
    return () => window.clearInterval(timer);
  }, []);

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

  // Show window when store hydration finishes (either successfully or with an error)
  useEffect(() => {
    const hydrationCompleted = status !== null || error !== null;
    if (hydrationCompleted) {
      if (!preferences.start_minimized) {
        requestAnimationFrame(() => {
          setTimeout(() => {
            try {
              const win = getCurrentWindow();
              void win.show()
                .then(() => {
                  void win.setFocus().catch(() => undefined);
                })
                .catch(() => undefined);
            } catch (e) {
              console.warn("Tauri getCurrentWindow().show() failed (probably not in Tauri):", e);
            }
          }, 80); // 80ms is extremely smooth and safe
        });
      }
    }
  }, [status, error, preferences.start_minimized]);

  return (
    <div className="app-shell flex h-full">
      <AppBackground appearance={appearance} />
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
      {conflictDialogOpen && (
        <ConflictingSoftwareDialog
          conflicts={conflictingProcesses}
          busy={conflictStopping}
          error={conflictStopError}
          helper={helperStatus}
          helperInstalling={helperInstalling}
          helperError={helperError}
          onInstallHelper={() => void installHelper().catch(() => undefined)}
          onClose={closeConflictDialog}
          onStop={() => void stopConflictingProcesses().catch(() => undefined)}
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
              <div className="text-[15px] font-semibold tracking-tight text-[var(--color-text)]">
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
                data-compact-hide={item.compactHide ? "true" : undefined}
                className={({ isActive }) =>
                  [
                    "app-nav-link block px-3 py-2.5 my-0.5 rounded-lg text-[13px] transition-colors",
                    item.compactHide ? "app-nav-link-secondary" : "",
                    isActive
                      ? "app-nav-link-active text-[var(--color-text)]"
                      : "text-[var(--color-text-dim)] hover:text-[var(--color-text)] hover:bg-[color-mix(in_srgb,var(--color-text)_4%,transparent)]",
                  ].join(" ")
                }
              >
                <NavIcon name={item.icon} />
                <span className="app-nav-text">{navLabel(m.app, item.key, false)}</span>
                <span className="app-nav-short">{navLabel(m.app, item.key, true)}</span>
                {item.key === "notifications" && unreadNotifications > 0 && (
                  <span className="app-nav-badge" aria-label={`${unreadNotifications} ${m.notifications.unread}`}>
                    {unreadNotifications > 99 ? "99+" : unreadNotifications}
                  </span>
                )}
              </NavLink>
            ))}
          </nav>
          <div className="app-build px-5 py-4 text-[10px] text-[var(--color-text-faint)] font-mono uppercase tracking-wider">
            v1.0.0
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
          <Route path="/routing" element={<Routing />} />
          <Route path="/apps" element={<Applications />} />
          <Route path="/connections" element={<Connections />} />
          <Route path="/statistics" element={<Statistics />} />
          <Route path="/tunnel-logs" element={<TunnelLogs />} />
          <Route path="/notifications" element={<Notifications />} />
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

function ConflictingSoftwareDialog({
  conflicts,
  busy,
  error,
  helper,
  helperInstalling,
  helperError,
  onInstallHelper,
  onClose,
  onStop,
}: {
  conflicts: ConflictingProcess[];
  busy: boolean;
  error: string | null;
  helper: HelperStatus | null;
  helperInstalling: boolean;
  helperError: string | null;
  onInstallHelper: () => void;
  onClose: () => void;
  onStop: () => void;
}) {
  const m = useMessages();
  const close = useCallback(() => {
    if (!busy) onClose();
  }, [busy, onClose]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") close();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close]);

  return (
    <div className="app-dialog-backdrop conflict-dialog-backdrop" role="presentation" onClick={close}>
      <div
        className="conflict-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="conflict-dialog-title"
        aria-describedby="conflict-dialog-text"
        onClick={(event) => event.stopPropagation()}
      >
        <button
          type="button"
          className="conflict-dialog-close"
          aria-label={m.common.close}
          disabled={busy}
          onClick={close}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>

        <div className="conflict-dialog-badge">TUN</div>
        <h2 id="conflict-dialog-title" className="conflict-dialog-title">
          {m.home.conflictTitle}
        </h2>
        <p id="conflict-dialog-text" className="conflict-dialog-text">
          {m.home.conflictText}
        </p>

        <ul className="conflict-dialog-list">
          {conflicts.map((process) => (
            <li key={`${process.pid}-${process.process_name}`} className="conflict-dialog-process">
              <span className="conflict-dialog-process-dot" aria-hidden="true" />
              <span className="conflict-dialog-process-main">
                <span className="conflict-dialog-process-name">
                  {process.name} <span>{process.process_name}</span>
                </span>
                {process.path && <span className="conflict-dialog-process-path">{process.path}</span>}
              </span>
              <span className="conflict-dialog-process-pid">PID {process.pid}</span>
            </li>
          ))}
        </ul>

        <p className="conflict-dialog-note">{m.home.conflictAdvice}</p>
        {helper && !helper.running && helper.exe_present && (
          <div className="conflict-helper-offer">
            <div className="conflict-helper-offer-text">
              <div className="conflict-helper-offer-title">{m.home.helperOfferTitle}</div>
              <div className="conflict-helper-offer-body">{m.home.helperOfferText}</div>
            </div>
            <button
              type="button"
              className="settings-action settings-action-primary"
              disabled={helperInstalling || busy}
              onClick={onInstallHelper}
            >
              {helperInstalling ? m.home.helperInstalling : m.home.helperInstall}
            </button>
          </div>
        )}
        {helper && helper.running && (
          <div className="conflict-helper-ready">{m.home.helperReady}</div>
        )}
        {helperError && <div className="conflict-dialog-error">{helperError}</div>}
        {error && (
          <div className="conflict-dialog-error">
            {error === "remaining_conflicts" ? m.home.conflictRemaining : error}
          </div>
        )}

        <div className="conflict-dialog-actions">
          <button type="button" className="settings-action" disabled={busy} onClick={close}>
            {m.common.close}
          </button>
          <button
            type="button"
            className="settings-action settings-action-primary conflict-dialog-stop"
            disabled={busy}
            onClick={onStop}
          >
            {busy ? m.home.conflictStopping : m.home.conflictStop}
          </button>
        </div>
      </div>
    </div>
  );
}

function navLabel(labels: Messages["app"], key: string, short: boolean): string {
  if (key === "home") return labels.home;
  if (key === "profiles") return labels.profiles;
  if (key === "routing") return short ? labels.routingShort : labels.routing;
  if (key === "apps") return short ? labels.appsShort : labels.apps;
  if (key === "connections") return short ? labels.connectionsShort : labels.connections;
  if (key === "statistics") return short ? labels.statisticsShort : labels.statistics;
  if (key === "tunnelLogs") return short ? labels.tunnelLogsShort : labels.tunnelLogs;
  if (key === "notifications") return short ? labels.notificationsShort : labels.notifications;
  return labels.settings;
}

function applyVisualPreferences(
  preferences: AppPreferences,
  providerTheme?: SubscriptionTheme | null,
) {
  if (typeof window === "undefined") return () => {};

  const isHexColor = (value: string | null | undefined): value is string =>
    typeof value === "string" && /^#[0-9a-fA-F]{6}$/.test(value.trim());
  const themeAccent = isHexColor(providerTheme?.accent) ? providerTheme!.accent!.trim() : null;
  const themeOrb1 = isHexColor(providerTheme?.orb1) ? providerTheme!.orb1!.trim() : null;
  const themeOrb2 = isHexColor(providerTheme?.orb2) ? providerTheme!.orb2!.trim() : null;
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
    const clampVisual = (value: unknown, fallback: number, min: number, max: number) => {
      const numeric = typeof value === "number" ? value : Number(value);
      if (!Number.isFinite(numeric)) return fallback;
      return Math.min(max, Math.max(min, Math.round(numeric)));
    };
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
    document.body.style.setProperty("zoom", String(preferences.servers_ui_scale / 100));
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
const SIDEBAR_WIDTH_MIGRATION_KEY = "nimbo.sidebarWidth.v2";
const SIDEBAR_WIDTH_DEFAULT = 232;
const SIDEBAR_WIDTH_MIN = 188;
const SIDEBAR_WIDTH_MAX = 340;

function useResizableSidebar() {
  const [width, setWidth] = useState<number>(() => {
    if (typeof window === "undefined") return SIDEBAR_WIDTH_DEFAULT;
    try {
      const stored = Number.parseInt(window.localStorage.getItem(SIDEBAR_WIDTH_KEY) ?? "", 10);
      const migrated = window.localStorage.getItem(SIDEBAR_WIDTH_MIGRATION_KEY) === "1";
      if (!migrated) {
        window.localStorage.setItem(SIDEBAR_WIDTH_MIGRATION_KEY, "1");
        if (!Number.isFinite(stored) || stored < SIDEBAR_WIDTH_DEFAULT) {
          return SIDEBAR_WIDTH_DEFAULT;
        }
      }
      if (Number.isFinite(stored) && stored >= SIDEBAR_WIDTH_MIN && stored <= SIDEBAR_WIDTH_MAX) {
        return stored;
      }
    } catch {
      return SIDEBAR_WIDTH_DEFAULT;
    }
    return SIDEBAR_WIDTH_DEFAULT;
  });

  useEffect(() => {
    if (typeof window === "undefined") return;
    try {
      window.localStorage.setItem(SIDEBAR_WIDTH_KEY, String(width));
    } catch {
      /* ignore */
    }
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

function AppBackground({ appearance }: { appearance: AppearanceState }) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const active = appearance.background !== "none";

  useEffect(() => {
    document.body.classList.toggle("has-app-bg", active);
    return () => {
      document.body.classList.remove("has-app-bg");
    };
  }, [active]);

  useEffect(() => {
    if (appearance.background !== "custom") {
      setObjectUrl(null);
      return;
    }
    let revoke: string | null = null;
    let cancelled = false;
    void loadBackgroundBlob()
      .then((blob) => {
        if (cancelled || !blob) return;
        const url = URL.createObjectURL(blob);
        revoke = url;
        setObjectUrl(url);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
      if (revoke) URL.revokeObjectURL(revoke);
    };
  }, [appearance.background, appearance.customType, appearance.customName]);

  if (!active) return null;

  const isCustom = appearance.background === "custom";
  const isPreset = !isCustom;
  const blobPresets = [
    "aurora",
    "nebula",
    "sunset",
    "ocean",
    "emerald",
    "mesh",
    "cyberpunk",
    "deepspace",
    "fire",
    "lava",
    "neon",
    "nordic",
    "blossom",
  ];
  const hasBlobs = isPreset && blobPresets.includes(appearance.background);

  const layer = (
    <div
      className="app-background"
      data-preset={isPreset ? appearance.background : undefined}
      style={
        {
          "--app-bg-dim": String(appearance.backgroundDim / 100),
          "--app-bg-blur": `${appearance.backgroundBlur}px`,
        } as React.CSSProperties
      }
    >
      {hasBlobs && (
        <div className="app-background-blobs">
          <div className="app-bg-blob app-bg-blob-1" />
          <div className="app-bg-blob app-bg-blob-2" />
          <div className="app-bg-blob app-bg-blob-3" />
          <div className="app-bg-blob app-bg-blob-4" />
        </div>
      )}
      {isCustom && objectUrl && appearance.customType === "video" && (
        <video
          className="app-background-media"
          src={objectUrl}
          autoPlay
          loop
          muted
          playsInline
        />
      )}
      {isCustom && objectUrl && appearance.customType === "image" && (
        <div
          className="app-background-media"
          style={{ backgroundImage: `url("${objectUrl}")` }}
        />
      )}
      <div className="app-background-overlay" />
    </div>
  );

  return createPortal(layer, document.body);
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
    strokeWidth: "1.6",
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
  if (name === "route") {
    return (
      <svg {...common}>
        <circle cx="6" cy="19" r="2.4" />
        <circle cx="18" cy="5" r="2.4" />
        <path d="M16.6 6.4 7.4 17.6" />
        <path d="M8 7h5a3 3 0 0 1 0 6h-2a3 3 0 0 0 0 6h5" />
      </svg>
    );
  }
  if (name === "connections") {
    return (
      <svg {...common}>
        <path d="M4 7h16M4 12h16M4 17h16" />
        <circle cx="8" cy="7" r="1.5" />
        <circle cx="14" cy="12" r="1.5" />
        <circle cx="10" cy="17" r="1.5" />
      </svg>
    );
  }
  if (name === "stats") {
    return (
      <svg {...common}>
        <path d="M4 19V9" />
        <path d="M10 19V5" />
        <path d="M16 19v-7" />
        <path d="M3 21h18" />
      </svg>
    );
  }
  if (name === "logs") {
    return (
      <svg {...common}>
        <path d="M7 3h7l4 4v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z" />
        <path d="M14 3v4h4" />
        <path d="M9 12h7M9 16h5" />
      </svg>
    );
  }
  if (name === "bell") {
    return (
      <svg {...common}>
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
    );
  }
  return (
    <svg {...common} viewBox="-1.2 -1.2 26.4 26.4">
      <path d="M12 15.5A3.5 3.5 0 1 0 12 8a3.5 3.5 0 0 0 0 7.5Z" />
      <path d="M19.4 15a1.8 1.8 0 0 0 .36 1.98l.04.04a2.1 2.1 0 0 1-2.98 2.98l-.04-.04a1.8 1.8 0 0 0-1.98-.36 1.8 1.8 0 0 0-1.1 1.66V21a2.1 2.1 0 0 1-4.2 0v-.06A1.8 1.8 0 0 0 8.4 19.3a1.8 1.8 0 0 0-1.98.36l-.04.04a2.1 2.1 0 1 1-2.98-2.98l.04-.04A1.8 1.8 0 0 0 3.8 14.7 1.8 1.8 0 0 0 2.14 13H2a2.1 2.1 0 0 1 0-4.2h.06A1.8 1.8 0 0 0 3.7 7.7a1.8 1.8 0 0 0-.36-1.98L3.3 5.68A2.1 2.1 0 1 1 6.28 2.7l.04.04A1.8 1.8 0 0 0 8.3 3.1 1.8 1.8 0 0 0 10 1.44V1.4a2.1 2.1 0 0 1 4.2 0v.06a1.8 1.8 0 0 0 1.1 1.64 1.8 1.8 0 0 0 1.98-.36l.04-.04a2.1 2.1 0 1 1 2.98 2.98l-.04.04a1.8 1.8 0 0 0-.36 1.98 1.8 1.8 0 0 0 1.66 1.1H22a2.1 2.1 0 0 1 0 4.2h-.06A1.8 1.8 0 0 0 19.4 15Z" />
    </svg>
  );
}
