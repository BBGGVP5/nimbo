import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
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

interface TrayServer {
  id: string;
  name: string;
}

interface TrayState {
  connected: boolean;
  activeServerId: string | null;
  language: string;
  visualPreferences: AppPreferences;
  providerTheme: SubscriptionTheme | null;
  servers: TrayServer[];
}

const LABELS = {
  ru: {
    connected: "Подключено",
    disconnected: "Отключено",
    show: "Показать Nimbo",
    connect: "Подключить",
    disconnect: "Отключить",
    servers: "Серверы",
    noServers: "Нет серверов",
    quit: "Выйти",
  },
  en: {
    connected: "Connected",
    disconnected: "Disconnected",
    show: "Show Nimbo",
    connect: "Connect",
    disconnect: "Disconnect",
    servers: "Servers",
    noServers: "No servers",
    quit: "Quit",
  },
} as const;

export function TrayMenu() {
  const [state, setState] = useState<TrayState | null>(null);
  const shellRef = useRef<HTMLDivElement>(null);

  const load = useCallback(async () => {
    try {
      setState(await invoke<TrayState>("tray_menu_state"));
    } catch {
      // The backend may briefly be unavailable; the next open retries.
    }
  }, []);

  useEffect(() => {
    void load();
    const subscriptions: Array<Promise<UnlistenFn>> = [
      listen("tray-menu:open", () => void load()),
      listen("tray-menu:refresh", () => void load()),
    ];
    return () => {
      subscriptions.forEach((p) => void p.then((un) => un()).catch(() => {}));
    };
  }, [load]);

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

  // After each render with data, report the exact content size so the backend
  // can size the flyout and (on open) position + reveal it at the cursor.
  useLayoutEffect(() => {
    if (!state) return;
    const el = shellRef.current;
    if (!el) return;
    const dpr = window.devicePixelRatio || 1;
    const width = Math.ceil(el.offsetWidth * dpr);
    const height = Math.ceil(el.offsetHeight * dpr);
    void invoke("tray_menu_resize", { width, height }).catch(() => {});
  }, [state]);

  const lang: "ru" | "en" = state?.language === "en" ? "en" : "ru";
  const t = LABELS[lang];

  const connected = state?.connected ?? false;
  const activeId = state?.activeServerId ?? null;
  const servers = state?.servers ?? [];
  const canConnect = !connected && activeId != null;
  const canDisconnect = connected;

  const act = (action: string, serverId?: string) =>
    void invoke("tray_menu_action", { action, serverId: serverId ?? null }).catch(() => {});

  return (
    <div className="tray-shell" ref={shellRef}>
      <div className="tray-card">
        <div className="tray-item is-static">
          <span className={`status-dot ${connected ? "is-on" : "is-off"}`} />
          <span className="tray-label">{connected ? t.connected : t.disconnected}</span>
        </div>

        <div className="tray-sep" />

        <button type="button" className="tray-item" onClick={() => act("show")}>
          <ShowIcon />
          <span className="tray-label">{t.show}</span>
        </button>

        <button
          type="button"
          className={`tray-item ${canConnect ? "" : "is-disabled"}`}
          disabled={!canConnect}
          onClick={() => canConnect && act("connect")}
        >
          <PowerIcon />
          <span className="tray-label">{t.connect}</span>
        </button>

        <button
          type="button"
          className={`tray-item ${canDisconnect ? "" : "is-disabled"}`}
          disabled={!canDisconnect}
          onClick={() => canDisconnect && act("disconnect")}
        >
          <DisconnectIcon />
          <span className="tray-label">{t.disconnect}</span>
        </button>

        <div className="tray-sep" />

        <div className="tray-section-title">
          <ServersIcon />
          <span>{t.servers}</span>
        </div>
        <div className="tray-servers">
          {servers.length === 0 ? (
            <div className="tray-item is-static is-muted">
              <span className="tray-label">{t.noServers}</span>
            </div>
          ) : (
            servers.map((server) => {
              const flag = serverFlagEmoji(server.name);
              const active = server.id === activeId;
              return (
                <button
                  key={server.id}
                  type="button"
                  className={`tray-item tray-server ${active ? "is-active" : ""}`}
                  onClick={() => act("server", server.id)}
                >
                  <span className="tray-flag">{flag ?? ""}</span>
                  <span className="tray-label">{serverDisplayName(server.name)}</span>
                  {active ? <CheckIcon /> : null}
                </button>
              );
            })
          )}
        </div>

        <div className="tray-sep" />

        <button type="button" className="tray-item" onClick={() => act("quit")}>
          <QuitIcon />
          <span className="tray-label">{t.quit}</span>
        </button>
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
    <svg {...svgProps} className="tray-section-icon">
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
