import type { ReactNode } from "react";
import { HomeMetaIcon } from "../../components/HomeMetaIcon";
import { Link } from "react-router-dom";
import type { Messages } from "../../lib/i18n";
import { fillTemplate } from "../../lib/i18n";
import { ChevronIcon } from "./SignalServerRail";
import { Surface } from "../../components/Universal";

export type SignalConnectionState = "connected" | "connecting" | "disconnecting" | "switching" | "idle";

export interface SignalTile {
  key: string;
  label: string;
  value: string;
  tone: "ok" | "warn" | "off";
}

export interface SignalHomeProps {
  labels: Messages;
  state: SignalConnectionState;
  stateWord: string;
  modeLabel: string;
  sessionLabel: string;
  sessionProgress: number;
  metaLine: string;
  profileTitle: string;
  profileSubtitle: string;
  serverFlag: ReactNode;
  serverName: string;
  autoSelected: boolean;
  serverProtocol: string;
  serverPing: ReactNode;
  serverDescription: string | null;
  telemetryAvailable: boolean;
  downloadRate: string;
  downloadUnit: string;
  downloadTotal: string;
  uploadRate: string;
  uploadUnit: string;
  uploadTotal: string;
  tiles: SignalTile[];
  chart: ReactNode;
  extras: ReactNode;
  actions: ReactNode;
  serverRail: ReactNode;
  onOpenServers: () => void;
  onCheckPings: () => void;
  onRefreshSubscription?: () => void;
  refreshing?: boolean;
  pinging: boolean;
  railWidth: number;
  railCollapsed: boolean;
  onResizeStart: (event: React.MouseEvent) => void;
  onResizeReset: () => void;
  onExpandRail: () => void;
  expandLabel: string;
}

export function SignalHome(props: SignalHomeProps) {
  const { labels: m, state, stateWord, modeLabel, sessionLabel, metaLine, profileTitle, profileSubtitle,
    serverFlag, serverName, autoSelected, serverProtocol, serverPing, telemetryAvailable, downloadRate, downloadUnit, downloadTotal,
    uploadRate, uploadUnit, uploadTotal, tiles, chart, extras, actions, serverRail, onOpenServers, onCheckPings,
    pinging } = props;
  const ru = m.common.locale.startsWith("ru");
  return <div className="universal-home">
    <header className="universal-page-heading"><div><h1>{m.app.home}</h1><p>{profileSubtitle}</p></div>
      </header>
    <div className="universal-home-grid">
      <section className="universal-connection-section">
        <header className="universal-section-heading"><h2>{m.settings.connection}</h2><span>{profileTitle}</span></header>
        <Surface className={`universal-connection universal-connection--${state}`}>
          <div className="universal-connection-top"><div><span className="universal-eyebrow"><i aria-hidden="true" />{state === "connected" ? (ru ? "VPN АКТИВЕН" : "VPN ACTIVE") : modeLabel}{state === "connected" ? ` · ${modeLabel}` : ""}</span>
            <h2 role="status">{state === "connected" ? (ru ? "Вы подключены" : "You are connected") : stateWord}</h2><p>{state === "connected" ? (ru ? "Через выбранный сервер" : "Using the selected server") : metaLine}</p></div><div className="universal-power">{actions}</div></div>
          <button type="button" className="universal-selected-server" title={m.signal.serversTitle} onClick={onOpenServers}>
            <span className="signal-server-flag">{serverFlag}</span><span className="signal-server-copy">
              <span className="signal-tile-key">{autoSelected ? (ru ? "Авто · текущий сервер" : "Auto · current server") : m.home.selectedServer}</span><strong>{serverName}</strong><small>{profileTitle} · {serverProtocol}</small></span><ChevronIcon direction="right"/>
          </button>
          <div className="universal-session"><div><span><HomeMetaIcon kind="clock" />{m.home.sessionDuration}</span><strong>{sessionLabel}</strong></div>
            <div><span><HomeMetaIcon kind="ping" />{ru ? "Пинг сервера" : "Server latency"}</span><strong>{serverPing}</strong></div></div>
          <Link to="/routing" className="universal-route-summary"><HomeMetaIcon kind="route" /><span><b>{m.app.routing}</b><small>{tiles.map(tile => tile.value).join(" · ")}</small></span><ChevronIcon direction="right"/></Link>
        </Surface>
      </section>
      <section className="universal-subscription-section"><header className="universal-section-heading"><h2>{ru ? "Мои подписки" : "My subscriptions"}</h2><Link to="/subscriptions">{m.app.profiles} ↗</Link></header>
        {serverRail}
        {pinging && <div className="universal-home-tools"><button type="button" className="signal-btn signal-btn--ghost" onClick={onCheckPings}>{m.common.cancel}</button></div>}
      </section>
    </div>
    {state === "connected" && <div className="universal-metrics-grid">
      <Surface className={`universal-speed${telemetryAvailable ? "" : " is-waiting"}`}><h2 className="universal-metric-title">{ru ? "Скорость соединения" : "Connection speed"}</h2>{!telemetryAvailable ? <p className="universal-telemetry-wait" role="status">{ru ? "Ожидаем данные от ядра" : "Waiting for core measurements"}</p> : <><div className="signal-flow">
        <div className="signal-flow-cell"><span className="signal-flow-label">↓ {m.signal.download}</span><span className="signal-flow-value">{downloadRate}<small>{downloadUnit}</small></span><span className="signal-flow-total">{fillTemplate(m.signal.perSession, { value: downloadTotal })}</span></div>
        <div className="signal-flow-cell"><span className="signal-flow-label">↑ {m.signal.upload}</span><span className="signal-flow-value">{uploadRate}<small>{uploadUnit}</small></span><span className="signal-flow-total">{fillTemplate(m.signal.perSession, { value: uploadTotal })}</span></div>
      </div>{chart}</>}</Surface>{extras}
    </div>}
  </div>;
}
