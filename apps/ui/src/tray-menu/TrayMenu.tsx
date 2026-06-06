import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import {
  defaultAppPreferences,
  serverDisplayName,
  serverFlagEmoji,
  type AppPreferences,
  type SubscriptionTheme,
} from "../lib/api";
import { applyAccentGradient, refreshAppearance, subscribeAppearance } from "../lib/appearance";
import { applyVisualPreferences } from "../lib/visualTheme";

type ConnectionMode = "system_proxy" | "tun" | "both";

interface TrayServer {
  id: string;
  name: string;
  subscriptionName?: string | null;
  latencyMs?: number | null;
}

interface TrayState {
  connected: boolean;
  activeServerId: string | null;
  connectionMode: ConnectionMode;
  subscriptionCount: number;
  serverCount: number;
  language: string;
  visualPreferences: AppPreferences;
  providerTheme: SubscriptionTheme | null;
  servers: TrayServer[];
}

const LABELS = {
  ru: {
    connected: "Подключено",
    disconnected: "Отключено",
    active: "Активный сервер",
    noActive: "Сервер не выбран",
    mode: "Режим",
    subscriptionsShort: "подп.",
    serversShort: "серверов",
    show: "Открыть",
    connect: "Подключить",
    disconnect: "Отключить",
    quick: "Быстро",
    profiles: "Профили",
    statistics: "Статистика",
    logs: "Логи",
    settings: "Настройки",
    maintenance: "Обслуживание",
    refresh: "Обновить подписки",
    ping: "Проверить пинг",
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
    noActive: "No server selected",
    mode: "Mode",
    subscriptionsShort: "subs",
    serversShort: "servers",
    show: "Open",
    connect: "Connect",
    disconnect: "Disconnect",
    quick: "Quick",
    profiles: "Profiles",
    statistics: "Stats",
    logs: "Logs",
    settings: "Settings",
    maintenance: "Maintenance",
    refresh: "Refresh subscriptions",
    ping: "Check latency",
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

const SERVERS_COLLAPSED_KEY = "nimbo.tray.serversCollapsed";

export function TrayMenu() {
  const [state, setState] = useState<TrayState | null>(null);
  const [openNonce, setOpenNonce] = useState(0);
  const [serversCollapsed, setServersCollapsed] = useState(readServersCollapsed);
  const shellRef = useRef<HTMLDivElement>(null);

  const load = useCallback(async () => {
    try {
      setState(await invoke<TrayState>("tray_menu_state"));
    } catch {
      // The backend may briefly be unavailable; the next open retries.
    }
  }, []);

  const act = useCallback((action: string, serverId?: string) => {
    void invoke("tray_menu_action", { action, serverId: serverId ?? null }).catch(() => {});
  }, []);

  useEffect(() => {
    void load();
    const subscriptions: Array<Promise<UnlistenFn>> = [
      listen("tray-menu:open", () => {
        setOpenNonce((value) => value + 1);
        void load();
      }),
      listen("tray-menu:refresh", () => void load()),
    ];
    return () => {
      subscriptions.forEach((p) => void p.then((un) => un()).catch(() => {}));
    };
  }, [load]);

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

  // After every content change, report the exact surface size. A second delayed
  // measurement keeps the native window anchored while the server section folds.
  useLayoutEffect(() => {
    if (!state) return;
    const el = shellRef.current;
    if (!el) return;

    const measure = () => {
      const dpr = window.devicePixelRatio || 1;
      const rect = el.getBoundingClientRect();
      const width = Math.ceil(rect.width * dpr);
      const height = Math.ceil(rect.height * dpr);
      void invoke("tray_menu_resize", { width, height }).catch(() => {});
    };

    measure();
    const frame = window.requestAnimationFrame(measure);
    const timer = window.setTimeout(measure, 210);
    return () => {
      window.cancelAnimationFrame(frame);
      window.clearTimeout(timer);
    };
  }, [state, serversCollapsed]);

  const lang: "ru" | "en" = state?.language === "en" ? "en" : "ru";
  const t = LABELS[lang];

  const connected = state?.connected ?? false;
  const activeId = state?.activeServerId ?? null;
  const servers = state?.servers ?? [];
  const activeServer = useMemo(
    () => servers.find((server) => server.id === activeId) ?? null,
    [activeId, servers],
  );
  const activeServerName = activeServer ? displayServerName(activeServer) : t.noActive;
  const connectionMode = state?.connectionMode ?? "tun";
  const subscriptionCount = state?.subscriptionCount ?? 0;
  const serverCount = state?.serverCount ?? servers.length;
  const canConnect = !connected && activeId != null;
  const canDisconnect = connected;

  const toggleServers = useCallback(() => {
    setServersCollapsed((value) => {
      const next = !value;
      try {
        window.localStorage.setItem(SERVERS_COLLAPSED_KEY, next ? "1" : "0");
      } catch {
        // Ignore storage failures; the menu still works for the current open.
      }
      return next;
    });
  }, []);

  return (
    <div className="tray-shell" ref={shellRef}>
      <div key={openNonce} className="tray-card" data-connected={connected ? "true" : "false"}>
        <div className="tray-hero">
          <span className={`tray-status-orb ${connected ? "is-on" : "is-off"}`} aria-hidden="true">
            <span />
          </span>
          <div className="tray-hero-copy">
            <div className="tray-eyebrow">Nimbo</div>
            <div className="tray-status-title">{connected ? t.connected : t.disconnected}</div>
            <div className="tray-status-subtitle" title={activeServerName}>
              <span>{t.active}</span>
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
            {t.mode}: <strong>{t.modeNames[connectionMode]}</strong>
          </span>
          <span>
            {subscriptionCount} {t.subscriptionsShort}
          </span>
          <span>
            {serverCount} {t.serversShort}
          </span>
        </div>

        <div className="tray-action-grid">
          <button type="button" className="tray-tile tray-tile-wide" onClick={() => act("show")}>
            <ShowIcon />
            <span>{t.show}</span>
          </button>
          <button
            type="button"
            className={`tray-tile ${canConnect ? "" : "is-disabled"}`}
            disabled={!canConnect}
            onClick={() => canConnect && act("connect")}
          >
            <PowerIcon />
            <span>{t.connect}</span>
          </button>
          <button
            type="button"
            className={`tray-tile ${canDisconnect ? "" : "is-disabled"}`}
            disabled={!canDisconnect}
            onClick={() => canDisconnect && act("disconnect")}
          >
            <DisconnectIcon />
            <span>{t.disconnect}</span>
          </button>
        </div>

        <div className="tray-section-label">{t.quick}</div>
        <div className="tray-shortcuts">
          <button type="button" onClick={() => act("profiles")} title={t.profiles}>
            <ProfilesIcon />
            <span>{t.profiles}</span>
          </button>
          <button type="button" onClick={() => act("statistics")} title={t.statistics}>
            <StatsIcon />
            <span>{t.statistics}</span>
          </button>
          <button type="button" onClick={() => act("logs")} title={t.logs}>
            <LogsIcon />
            <span>{t.logs}</span>
          </button>
          <button type="button" onClick={() => act("settings")} title={t.settings}>
            <SettingsIcon />
            <span>{t.settings}</span>
          </button>
        </div>

        <div className="tray-utility-grid" aria-label={t.maintenance}>
          <button
            type="button"
            disabled={subscriptionCount === 0}
            className={subscriptionCount === 0 ? "is-disabled" : ""}
            onClick={() => subscriptionCount > 0 && act("refresh_subscriptions")}
          >
            <RefreshIcon />
            <span>{t.refresh}</span>
          </button>
          <button
            type="button"
            disabled={serverCount === 0}
            className={serverCount === 0 ? "is-disabled" : ""}
            onClick={() => serverCount > 0 && act("ping_servers")}
          >
            <RadarIcon />
            <span>{t.ping}</span>
          </button>
        </div>

        <button
          type="button"
          className="tray-section-toggle"
          aria-expanded={!serversCollapsed}
          onClick={toggleServers}
          title={serversCollapsed ? t.expandServers : t.collapseServers}
        >
          <ServersIcon />
          <span>{t.servers}</span>
          <span className="tray-section-count">{servers.length}</span>
          <ChevronIcon />
        </button>

        <div className={`tray-servers-frame ${serversCollapsed ? "is-collapsed" : ""}`}>
          <div className="tray-servers">
            {servers.length === 0 ? (
              <div className="tray-server-empty">{t.noServers}</div>
            ) : (
              servers.map((server) => {
                const flag = serverFlagEmoji(server.name);
                const active = server.id === activeId;
                const latency = formatLatency(server.latencyMs, t.noPing);
                return (
                  <button
                    key={server.id}
                    type="button"
                    className={`tray-server ${active ? "is-active" : ""}`}
                    onClick={() => act("server", server.id)}
                    title={displayServerName(server)}
                  >
                    <span className="tray-flag">{flag ?? ""}</span>
                    <span className="tray-server-copy">
                      <span className="tray-server-name">{displayServerName(server)}</span>
                      <span className="tray-server-meta">
                        {server.subscriptionName ? <span>{server.subscriptionName}</span> : null}
                        <span>{latency}</span>
                      </span>
                    </span>
                    {active ? <CheckIcon /> : null}
                  </button>
                );
              })
            )}
          </div>
        </div>

        <div className="tray-footer">
          <button type="button" className="tray-quit" onClick={() => act("quit")}>
            <QuitIcon />
            <span>{t.quit}</span>
          </button>
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

function readServersCollapsed(): boolean {
  try {
    return window.localStorage.getItem(SERVERS_COLLAPSED_KEY) === "1";
  } catch {
    return false;
  }
}

function isHexColor(value: string | null | undefined): value is string {
  return typeof value === "string" && /^#[0-9a-fA-F]{6}$/.test(value.trim());
}

function displayServerName(server: TrayServer): string {
  return serverDisplayName(server.name) || server.name || "Server";
}

function formatLatency(value: number | null | undefined, fallback: string): string {
  if (typeof value !== "number" || !Number.isFinite(value)) return fallback;
  return `${Math.round(value)} ms`;
}

function ShowIcon() {
  return (
    <svg {...svgProps}>
      <rect x="3" y="4.5" width="18" height="15" rx="2.5" />
      <path d="M3 9.5h18" />
    </svg>
  );
}

function PowerIcon() {
  return (
    <svg {...svgProps}>
      <path d="M12 3.5v8" />
      <path d="M7.3 6.8a7 7 0 1 0 9.4 0" />
    </svg>
  );
}

function DisconnectIcon() {
  return (
    <svg {...svgProps}>
      <circle cx="12" cy="12" r="8.4" />
      <path d="M6.4 6.4l11.2 11.2" />
    </svg>
  );
}

function ServersIcon() {
  return (
    <svg {...svgProps}>
      <rect x="3.5" y="4.5" width="17" height="6.4" rx="1.6" />
      <rect x="3.5" y="13.1" width="17" height="6.4" rx="1.6" />
      <path d="M7 7.7h0.01" />
      <path d="M7 16.3h0.01" />
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

function CheckIcon() {
  return (
    <svg
      className="tray-check"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M5 12.5l4.5 4.5L19 7" />
    </svg>
  );
}

function ProfilesIcon() {
  return (
    <svg {...svgProps}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M3.5 12h17" />
      <path d="M12 3.5a12.5 12.5 0 0 1 0 17" />
      <path d="M12 3.5a12.5 12.5 0 0 0 0 17" />
    </svg>
  );
}

function StatsIcon() {
  return (
    <svg {...svgProps}>
      <path d="M4.5 19.5V10" />
      <path d="M10 19.5V5" />
      <path d="M15.5 19.5v-7" />
      <path d="M3.5 20.5h17" />
    </svg>
  );
}

function LogsIcon() {
  return (
    <svg {...svgProps}>
      <path d="M7 3.5h7l4 4v12a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 19.5v-15A1.5 1.5 0 0 1 7.5 3" />
      <path d="M14 3.5v4h4" />
      <path d="M9 12h6.5" />
      <path d="M9 16h5" />
    </svg>
  );
}

function SettingsIcon() {
  return (
    <svg {...svgProps} viewBox="-1 -1 26 26">
      <path d="M12 15.4a3.4 3.4 0 1 0 0-6.8 3.4 3.4 0 0 0 0 6.8Z" />
      <path d="M19.2 14.8a1.7 1.7 0 0 0 .34 1.86l.04.04a2 2 0 1 1-2.84 2.84l-.04-.04a1.7 1.7 0 0 0-1.86-.34 1.7 1.7 0 0 0-1.04 1.57V21a2 2 0 0 1-4 0v-.06a1.7 1.7 0 0 0-1.04-1.56 1.7 1.7 0 0 0-1.86.34l-.04.04a2 2 0 1 1-2.84-2.84l.04-.04a1.7 1.7 0 0 0 .34-1.86 1.7 1.7 0 0 0-1.57-1.04H2a2 2 0 0 1 0-4h.06A1.7 1.7 0 0 0 3.62 8.8a1.7 1.7 0 0 0-.34-1.86l-.04-.04A2 2 0 1 1 6.08 4.06l.04.04a1.7 1.7 0 0 0 1.86.34A1.7 1.7 0 0 0 9.02 2.9V2.8a2 2 0 0 1 4 0v.06a1.7 1.7 0 0 0 1.04 1.56 1.7 1.7 0 0 0 1.86-.34l.04-.04a2 2 0 1 1 2.84 2.84l-.04.04a1.7 1.7 0 0 0-.34 1.86 1.7 1.7 0 0 0 1.57 1.04H20a2 2 0 0 1 0 4h-.06a1.7 1.7 0 0 0-1.56 1.02Z" />
    </svg>
  );
}

function RefreshIcon() {
  return (
    <svg {...svgProps}>
      <path d="M20 11a8 8 0 0 0-14.2-4.9L4 8" />
      <path d="M4 4v4h4" />
      <path d="M4 13a8 8 0 0 0 14.2 4.9L20 16" />
      <path d="M20 20v-4h-4" />
    </svg>
  );
}

function RadarIcon() {
  return (
    <svg {...svgProps}>
      <circle cx="12" cy="12" r="8.5" />
      <circle cx="12" cy="12" r="3" />
      <path d="M12 12 18 6" />
      <path d="M12 3.5v2" />
      <path d="M12 18.5v2" />
      <path d="M3.5 12h2" />
      <path d="M18.5 12h2" />
    </svg>
  );
}

function ChevronIcon() {
  return (
    <svg
      className="tray-chevron"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m6 9 6 6 6-6" />
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
