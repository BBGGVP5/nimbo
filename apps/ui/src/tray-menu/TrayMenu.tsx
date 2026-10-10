import { ConnectionStateIcon } from "../components/ConnectionStateIcon";
import { OperationPhrase } from "../components/OperationPhrase";
import nimboLogo from "../assets/nimbo.png";
import { fillTemplate, getMessages } from "../lib/i18n";
import { latencyPresentation, type LatencyProtocol } from "../lib/latency";
import { LatencyDisplay } from "../components/LatencyDisplay";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import {
  defaultAppPreferences,
  serverDisplayName,
  type AppPreferences,
  type SubscriptionTheme,
} from "../lib/api";
import { applyAccentGradient, refreshAppearance, subscribeAppearance } from "../lib/appearance";
import { applyVisualPreferences } from "../lib/visualTheme";
import { favoriteServers } from "./quickServers";

type ConnectionMode = "system_proxy" | "tun" | "both";

interface TrayServer {
  id: string;
  name: string;
  subscriptionName?: string | null;
  latencyMs?: number | null;
}

export interface TrayState {
  connected: boolean;
  activeServerId: string | null;
  activeProfileName?: string | null;
  autoSelected: boolean;
  connectionMode: ConnectionMode;
  subscriptionCount: number;
  serverCount: number;
  language: string;
  visualPreferences: AppPreferences;
  providerTheme: SubscriptionTheme | null;
  servers: TrayServer[];
  needsAdmin: boolean;
}

interface TrayConnectResult {
  action: "connect" | "disconnect";
  ok: boolean;
  error?: string | null;
}

const LABELS = {
  ru: {
    connected: "Подключено",
    disconnected: "Отключено",
    active: "Активный сервер",
    autoActive: "Авто · текущий сервер",
    noActive: "Сервер не выбран",
    mode: "Режим",
    subscriptionsShort: "подп.",
    serversShort: "серверов",
    show: "Открыть",
    connect: "Подключить",
    disconnect: "Отключить",
    connecting: "Подключение…",
    disconnecting: "Отключение…",
    adminNeeded: "Для TUN нужны права администратора",
    restartAdmin: "Перезапустить",
    quick: "Разделы",
    routing: "Маршрутизация",
    sync: "Синхронизация",
    favorites: "Избранное",
    favoriteHint: "Отметьте серверы звёздочкой в Nimbo",
    connectionFailed: "Действие подключения не выполнено. Подробности — в логах.",
    profiles: "Профили",
    connections: "Соединения",
    apps: "Приложения",
    statistics: "Статистика",
    logs: "Логи",
    settings: "Настройки",
    maintenance: "Обслуживание",
    refresh: "Обновить подписки",
    ping: "Проверить пинг",
    refreshRunning: "Обновление подписок…",
    refreshDone: "Подписки обновлены",
    pingRunning: "Проверка пинга…",
    pingDone: "Пинг обновлён",
    pingBest: "лучший",
    taskFailed: "Не удалось выполнить",
    servers: "Серверы",
    collapseServers: "Свернуть серверы",
    expandServers: "Развернуть серверы",
    noServers: "Нет серверов",
    noPing: "пинг не измерен",
    close: "Закрыть",
    quit: "Выйти",
    modeNames: {
      system_proxy: "Proxy",
      tun: "TUN",
      both: "TUN + Proxy",
    } satisfies Record<ConnectionMode, string>,
  },
  en: {
    connected: "Connected",
    disconnected: "Disconnected",
    active: "Active server",
    autoActive: "Auto · current server",
    noActive: "No server selected",
    mode: "Mode",
    subscriptionsShort: "subs",
    serversShort: "servers",
    show: "Open",
    connect: "Connect",
    disconnect: "Disconnect",
    connecting: "Connecting…",
    disconnecting: "Disconnecting…",
    adminNeeded: "TUN needs administrator rights",
    restartAdmin: "Restart",
    quick: "Sections",
    routing: "Routing",
    sync: "Sync",
    favorites: "Favorites",
    favoriteHint: "Star servers in Nimbo to see them here",
    connectionFailed: "Connection action failed. Check the logs for details.",
    profiles: "Profiles",
    connections: "Connections",
    apps: "Applications",
    statistics: "Stats",
    logs: "Logs",
    settings: "Settings",
    maintenance: "Maintenance",
    refresh: "Refresh subscriptions",
    ping: "Check latency",
    refreshRunning: "Refreshing subscriptions…",
    refreshDone: "Subscriptions updated",
    pingRunning: "Checking latency…",
    pingDone: "Latency updated",
    pingBest: "best",
    taskFailed: "Action failed",
    servers: "Servers",
    collapseServers: "Collapse servers",
    expandServers: "Expand servers",
    noServers: "No servers",
    noPing: "not measured",
    close: "Close",
    quit: "Quit",
    modeNames: {
      system_proxy: "Proxy",
      tun: "TUN",
      both: "TUN + Proxy",
    } satisfies Record<ConnectionMode, string>,
  },
} as const;

type MaintenanceAction = "refresh_subscriptions" | "ping_servers";

interface TrayTask {
  kind: MaintenanceAction;
  status: "running" | "done" | "error";
  count?: number;
  best?: number | null;
  servers?: number;
  // Live ping progress: how many servers measured so far out of the total.
  done?: number;
  total?: number;
}

interface TrayActionDone {
  action: string;
  ok: boolean;
  count?: number;
  best?: number | null;
  servers?: number;
}

export function TrayMenu({ previewState }: { previewState?: TrayState } = {}) {
  const [state, setState] = useState<TrayState | null>(previewState ?? null);
  const [favoriteIds, setFavoriteIds] = useState<string | null>(null);
  const [connectionFailed, setConnectionFailed] = useState(false);
  const [openNonce, setOpenNonce] = useState(0);
  const [task, setTask] = useState<TrayTask | null>(null);
  // The state the user just asked the toggle to reach, held from the click until
  // the backend confirms (or a timeout fires). Non-null means a switch is in
  // flight; we show this target optimistically so the toggle never flickers when
  // `connected` flips a render before the in-flight flag clears.
  const [pendingTarget, setPendingTarget] = useState<boolean | null>(null);
  // Set when a connect attempt is blocked by missing administrator rights;
  // surfaces a banner with a one-click relaunch-as-admin action.
  const [adminBlocked, setAdminBlocked] = useState(false);
  const cardRef = useRef<HTMLDivElement>(null);
  const taskTimers = useRef<number[]>([]);
  const switchTimer = useRef<number | null>(null);

  const clearTaskTimers = useCallback(() => {
    taskTimers.current.forEach((id) => window.clearTimeout(id));
    taskTimers.current = [];
  }, []);

  const clearSwitchTimer = useCallback(() => {
    if (switchTimer.current != null) {
      window.clearTimeout(switchTimer.current);
      switchTimer.current = null;
    }
  }, []);

  const load = useCallback(async () => {
    if (previewState) return;
    try {
      setState(await invoke<TrayState>("tray_menu_state"));
    } catch {
      // The backend may briefly be unavailable; the next open retries.
    }
  }, [previewState]);

  const act = useCallback((action: string, serverId?: string) => {
    if (previewState) {
      setState(current => current ? { ...current,
        connected: action === "disconnect" ? false : action === "connect" || action === "server" ? true : current.connected,
        activeServerId: serverId ?? current.activeServerId,
      } : current);
      setPendingTarget(null);
      return;
    }
    void invoke("tray_menu_action", { action, serverId: serverId ?? null }).catch(() => {
      setConnectionFailed(true);
      setPendingTarget(null);
      clearSwitchTimer();
    });
  }, [previewState, clearSwitchTimer]);

  useEffect(() => {
    const read = () => {
      try { setFavoriteIds(previewState ? JSON.stringify(previewState.servers.map(s => s.id)) : localStorage.getItem("nimbo.favorites")); }
      catch { setFavoriteIds(null); }
    };
    read();
    window.addEventListener("storage", read);
    return () => window.removeEventListener("storage", read);
  }, [openNonce, previewState]);

  useEffect(() => {
    if (previewState) return;
    void load();
    const subscriptions: Array<Promise<UnlistenFn>> = [
      listen("tray-menu:open", () => {
        setOpenNonce((value) => value + 1);
        // Drop a lingering result banner from a previous session, but keep a
        // spinner that is still in flight.
        setTask((current) => (current?.status === "running" ? current : null));
        setAdminBlocked(false);
        void load();
      }),
      listen("tray-menu:refresh", () => void load()),
      // Connect/disconnect now report back here (the flyout stays open). On
      // success we let the `connected` change settle the optimistic toggle so it
      // never flickers to the wrong side for a frame; on failure we drop the
      // switching state right away and raise the admin prompt if that was why.
      listen<TrayConnectResult>("tray-menu:connect-result", (event) => {
        const { ok, error } = event.payload;
        setConnectionFailed(!ok);
        clearSwitchTimer();
        setPendingTarget(null);
        if (!ok) {
          clearSwitchTimer();
          setPendingTarget(null);
          if (isAdminError(error)) setAdminBlocked(true);
        }
        void load();
      }),
      listen<TrayActionDone>("tray-menu:action-done", (event) => {
        const payload = event.payload;
        setTask((current) => {
          if (!current || current.kind !== payload.action) return current;
          return {
            kind: current.kind,
            status: payload.ok ? "done" : "error",
            count: payload.count,
            best: payload.best,
            servers: payload.servers,
          };
        });
        void load();
        clearTaskTimers();
        taskTimers.current.push(window.setTimeout(() => setTask(null), 2600));
      }),
    ];
    return () => {
      clearTaskTimers();
      subscriptions.forEach((p) => void p.then((un) => un()).catch(() => {}));
    };
  }, [load, clearTaskTimers, clearSwitchTimer, previewState]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") act("hide");
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [act]);

  useEffect(() => {
    if (!state) return;
    return applyVisualPreferences(
      state.visualPreferences ?? defaultAppPreferences,
      state.providerTheme ?? null,
      { includeUiScale: false },
    );
  }, [state]);

  useEffect(() => {
    if (!state) return;
    const apply = () => {
      const preferences = state.visualPreferences ?? defaultAppPreferences;
      const providerTheme = state.providerTheme ?? null;
      const providerAccent = providerTheme?.accent;
      if (isHexColor(providerAccent)) {
        const colors = [providerAccent, providerTheme?.orb1, providerTheme?.orb2]
          .filter(isHexColor)
          .map((color) => color.trim());
        applyAccentGradient("custom", colors[0], colors);
        return;
      }

      applyAccentGradient(
        preferences.accent_mode,
        preferences.accent_color,
        refreshAppearance().palette,
      );
    };

    apply();
    const unsubscribeAppearance = subscribeAppearance(apply);
    const onStorage = () => apply();
    window.addEventListener("storage", onStorage);
    return () => {
      unsubscribeAppearance();
      window.removeEventListener("storage", onStorage);
    };
  }, [state]);

  // Size the native window to the rendered card and keep it glued as the height
  // changes (server fold/unfold, status banner, live ping rows). The card no
  // longer animates its own geometry — the entrance lives on an inner layer — so
  // its layout box is stable from the first frame: we size + reveal once, then a
  // ResizeObserver tracks every later height change. Driving the resize from real
  // layout changes (instead of a fixed-length per-frame loop racing a transform)
  // keeps the window bounds, DWM rounding and the fallback rounded region in
  // lock-step, so Windows never exposes the native rectangle behind the flyout.
  useLayoutEffect(() => {
    if (!state || previewState) return;
    const card = cardRef.current;
    if (!card) return;

    const measure = () => {
      const dpr = window.devicePixelRatio || 1;
      const rect = card.getBoundingClientRect();
      const style = window.getComputedStyle(card);
      const radius = parseCssPixels(style.getPropertyValue("--tray-radius"), 8);
      // Floor (not ceil) so the window is never a fraction of a pixel wider than
      // the painted card — that gap renders as a black seam on WebView2. Pass the
      // real dpr and CSS radius too: the rounded clip region is derived from them, and if it
      // disagrees with this size the corners round short and go black.
      const width = Math.floor(rect.width * dpr);
      const height = Math.floor(rect.height * dpr);
      if (width > 0 && height > 0) {
        void invoke("tray_menu_resize", { width, height, dpr, radius }).catch(() => {});
      }
    };

    // Coalesce bursts of layout changes (e.g. a height transition) to one resize
    // per frame so the window follows the card without redundant native calls.
    let raf = 0;
    const schedule = () => {
      if (raf) return;
      raf = window.requestAnimationFrame(() => {
        raf = 0;
        measure();
      });
    };

    measure();
    const observer = new ResizeObserver(schedule);
    observer.observe(card);
    return () => {
      if (raf) window.cancelAnimationFrame(raf);
      observer.disconnect();
    };
  }, [state, openNonce, previewState]);

  const lang: "ru" | "en" = state?.language === "en" ? "en" : "ru";
  const t = LABELS[lang];

  const connected = state?.connected ?? false;
  const activeId = state?.activeServerId ?? null;
  const servers = state?.servers ?? [];
  const favorites = useMemo(() => favoriteServers(servers, favoriteIds), [servers, favoriteIds]);
  const activeServer = useMemo(
    () => servers.find((server) => server.id === activeId) ?? null,
    [activeId, servers],
  );
  const activeServerName = state?.activeProfileName || (activeServer ? displayServerName(activeServer) : t.noActive);
  const connectionMode = state?.connectionMode ?? "tun";
  const canConnect = !connected && (activeId != null || !!state?.activeProfileName);
  const canDisconnect = connected;
  const canToggle = canConnect || canDisconnect;
  // Show the admin banner proactively (mode needs TUN but the app is not
  // elevated) or reactively (a connect attempt failed with an admin error).
  const showAdmin = (state?.needsAdmin ?? false) || adminBlocked;
  const switching = pendingTarget !== null;
  // While a switch is in flight, show its target optimistically so the knob
  // slides and recolors the instant the user clicks; otherwise mirror the real
  // connection state.
  const targetOn = pendingTarget ?? connected;
  const toggleLabel = switching
    ? targetOn
      ? t.connecting
      : t.disconnecting
    : targetOn
      ? t.disconnect
      : t.connect;

  const prevConnected = useRef(connected);
  useEffect(() => {
    if (prevConnected.current === connected) return;
    prevConnected.current = connected;
    // The backend reported the new connection state — land the animation and
    // drop any reactive admin banner (a successful connect clears the block).
    setPendingTarget(null);
    clearSwitchTimer();
    if (connected) setAdminBlocked(false);
  }, [connected, clearSwitchTimer]);

  // Drop a stuck switching state if this view unmounts mid-action.
  useEffect(() => clearSwitchTimer, [clearSwitchTimer]);

  const toggleConnection = useCallback(() => {
    if (switching || !canToggle) return;
    clearSwitchTimer();
    setAdminBlocked(false);
    setPendingTarget(!connected);
    act(connected ? "disconnect" : "connect");
    // Safety net only: the backend always emits a result (success lands via the
    // connection-state change, failure via connect-result), so this just rescues
    // a lost event. Generous, since a TUN connection can take a while.
    switchTimer.current = window.setTimeout(() => setPendingTarget(null), 30000);
  }, [switching, canToggle, connected, act, clearSwitchTimer]);

  const restartAsAdmin = useCallback(() => {
    void invoke("restart_as_admin").catch(() => {});
  }, []);

  const connectFavorite = (id: string) => {
    if (switching) return;
    clearSwitchTimer();
    setPendingTarget(true);
    act("server", id);
    if (!previewState) switchTimer.current = window.setTimeout(() => {
      setPendingTarget(null);
      setConnectionFailed(true);
      void load();
    }, 30000);
  };

  const taskLabel = task ? describeTask(task, t, state?.visualPreferences?.latency_protocol) : null;
  const taskEstimate = latencyPresentation(task?.best, state?.visualPreferences?.latency_protocol);
  const taskExplanation = task?.kind === "ping_servers" && taskEstimate.approximate
    ? fillTemplate(getMessages(lang).settings.latencyEstimateLabel, { estimate: taskEstimate.label, raw: task!.best! })
    : undefined;

  return (
    <div className="tray-shell">
      <div
        key={openNonce}
        ref={cardRef}
        className="tray-card"
        style={{ maxHeight: Math.max(240, window.screen.availHeight - 64), overflowY: "auto" }}
        data-connected={connected ? "true" : "false"}
      >
        <div className="tray-card-inner">
        <div className="tray-hero">
          <span className={`tray-status-orb ${connected ? "is-on" : "is-off"}`} aria-hidden="true">
            <img src={nimboLogo} alt="" className="tray-brand-cloud"/>
          </span>
          <div className="tray-hero-copy">
            <div className="tray-eyebrow">Nimbo</div>
            <div className="tray-status-title">{connected ? t.connected : t.disconnected}</div>
            <div className="tray-status-subtitle" title={activeServerName}>
              <span>{state?.autoSelected && connected ? t.autoActive : t.active}</span>
              <strong>{activeServerName}</strong>
            </div>
          </div>
          <button
            type="button"
            className="tray-close-button"
            aria-label={t.close}
            title={t.close}
            onClick={() => act("hide")}
          >
            <CloseIcon />
          </button>
        </div>

        <div className="tray-meta-row" aria-label={t.mode}>
          <span>
            {t.mode}:&nbsp;<strong>{t.modeNames[connectionMode]}</strong>
          </span>
        </div>

        <button
          type="button"
          className={`tray-connect-toggle ${canToggle ? "" : "is-disabled"}`}
          data-on={targetOn ? "true" : "false"}
          data-busy={switching ? "true" : "false"}
          disabled={!canToggle || switching}
          role="switch"
          aria-checked={connected}
          aria-busy={switching}
          aria-label={connected ? t.disconnect : t.connect}
          onClick={toggleConnection}
        >
          <span className="tray-power" aria-hidden="true">
            <ConnectionStateIcon connected={connected} busy={switching} />
            <span className="tray-power-ring" />
          </span>
          <span className="tray-connect-text">
            <span key={toggleLabel} className="tray-connect-text-value">
              {toggleLabel}
            </span>
          </span>
        </button>

        <OperationPhrase active={switching && !connectionFailed} locale={lang} />

        {showAdmin ? (
          <div className="tray-admin-notice" role="alert">
            <span className="tray-admin-icon" aria-hidden="true">
              <ShieldIcon />
            </span>
            <span className="tray-admin-text">{t.adminNeeded}</span>
            <button type="button" className="tray-admin-action" onClick={restartAsAdmin}>
              {t.restartAdmin}
            </button>
          </div>
        ) : null}

        {connectionFailed ? (
          <div className="tray-task is-error" role="alert">
            <span className="tray-task-text">{t.connectionFailed}</span>
            <button className="tray-admin-action" type="button" onClick={() => act("logs")}>{t.logs}</button>
            <button className="tray-close-button" type="button" aria-label={t.close} onClick={() => setConnectionFailed(false)}><CloseIcon /></button>
          </div>
        ) : null}

        <section className="tray-favorites" aria-label={t.favorites}>
          <div className="tray-favorites-heading">
            <span>{t.favorites}</span>
            <button type="button" className="tray-admin-action" onClick={() => act("profiles")}>{t.profiles} →</button>
          </div>
          <div className="tray-servers">
            {favorites.length === 0 ? <p className="tray-server-empty">{t.favoriteHint}</p> : favorites.map(server => (
              <button key={server.id} type="button" className={`tray-server ${server.id === activeId ? "is-active" : ""}`}
                aria-current={server.id === activeId ? "true" : undefined}
                aria-label={`${t.connect}: ${displayServerName(server)}`}
                disabled={switching || (connected && server.id === activeId)}
                onClick={() => connectFavorite(server.id)}>
                <span className="tray-flag" aria-hidden="true">☆</span>
                <span className="tray-server-copy">
                  <span className="tray-server-name" title={displayServerName(server)}>{displayServerName(server)}</span>
                  <span className="tray-server-meta"><span>{server.subscriptionName}</span><span>{server.latencyMs != null ? <LatencyDisplay value={server.latencyMs} format={state?.visualPreferences?.latency_display_format} protocol={state?.visualPreferences?.latency_protocol} language={lang} /> : t.noPing}</span></span>
                </span>
                <span className="tray-check" aria-hidden="true">{server.id === activeId ? "✓" : "→"}</span>
              </button>
            ))}
          </div>
        </section>

        {task && taskLabel ? (
          <div className={`tray-task is-${task.status}`} role="status" aria-live="polite">
            {task.status === "running" ? (
              <span className="tray-task-spinner" aria-hidden="true" />
            ) : (
              <span className="tray-task-icon" aria-hidden="true">
                {task.status === "done" ? <TaskDoneIcon /> : <TaskErrorIcon />}
              </span>
            )}
            <span className="tray-task-text" title={taskExplanation} aria-label={taskExplanation ? `${taskLabel}. ${taskExplanation}` : undefined}>{taskLabel}</span>
          </div>
        ) : null}

        <div className="tray-footer">
          <button type="button" className="tray-quit" onClick={() => act("show")}>{t.show} Nimbo</button>
          <button type="button" className="tray-quit" onClick={() => act("quit")}>
            <QuitIcon />
            <span>{t.quit}</span>
          </button>
        </div>
        </div>
      </div>
    </div>
  );
}

const svgProps = {
  className: "tray-icon",
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
};

function isHexColor(value: string | null | undefined): value is string {
  return typeof value === "string" && /^#[0-9a-fA-F]{6}$/.test(value.trim());
}

// A connect failure that boils down to "relaunch as administrator" (TUN/both
// mode without elevation). Mirrors the main window's `isAdminRestartError`.
function isAdminError(message: string | null | undefined): boolean {
  if (!message) return false;
  const normalized = message.toLowerCase();
  return normalized.includes("администратор") || normalized.includes("administrator");
}

function parseCssPixels(value: string, fallback: number): number {
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function displayServerName(server: TrayServer): string {
  return serverDisplayName(server.name) || server.name || "Server";
}

type Labels = (typeof LABELS)[keyof typeof LABELS];

function describeTask(task: TrayTask, t: Labels, protocol?: LatencyProtocol): string {
  if (task.status === "running") {
    if (task.kind === "ping_servers") {
      return typeof task.done === "number" && typeof task.total === "number"
        ? `${t.pingRunning} ${task.done}/${task.total}`
        : t.pingRunning;
    }
    return t.refreshRunning;
  }
  if (task.status === "error") {
    return t.taskFailed;
  }
  if (task.kind === "ping_servers") {
    const parts: string[] = [t.pingDone];
    if (typeof task.count === "number") parts.push(`${task.count} ${t.serversShort}`);
    if (typeof task.best === "number" && Number.isFinite(task.best)) {
      parts.push(`${t.pingBest} ${latencyPresentation(task.best, protocol).label}`);
    }
    return parts.join(" · ");
  }
  return typeof task.servers === "number"
    ? `${t.refreshDone} · ${task.servers} ${t.serversShort}`
    : t.refreshDone;
}


function TaskDoneIcon() {
  return (
    <svg
      className="tray-task-glyph"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.4}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M5 12.5l4.5 4.5L19 7" />
    </svg>
  );
}

function TaskErrorIcon() {
  return (
    <svg
      className="tray-task-glyph"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 7v6" />
      <path d="M12 16.5h0.01" />
    </svg>
  );
}

function ShieldIcon() {
  return (
    <svg {...svgProps}>
      <path d="M12 3.2l6.5 2.4v5.1c0 4-2.7 7.1-6.5 8.1-3.8-1-6.5-4.1-6.5-8.1V5.6L12 3.2Z" />
      <path d="M9.4 11.8l1.9 1.9 3.4-3.6" />
    </svg>
  );
}

function QuitIcon() {
  return (
    <svg {...svgProps}>
      <path d="M14 4.5H6.5A2 2 0 0 0 4.5 6.5v11a2 2 0 0 0 2 2H14" />
      <path d="M16.5 8.5L20 12l-3.5 3.5" />
      <path d="M20 12H9.5" />
    </svg>
  );
}



function CloseIcon() {
  return (
    <svg
      className="tray-close-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M6 6l12 12" />
      <path d="M18 6 6 18" />
    </svg>
  );
}
