import type { ReactNode } from "react";
import type { Messages } from "../../lib/i18n";
import { fillTemplate } from "../../lib/i18n";
import { ChevronIcon } from "./SignalServerRail";

/**
 * Приборная панель стиля Signal.
 *
 * Экран отвечает на три вопроса сразу: подключён ли я, куда и насколько
 * быстро. Кольцо состояния со временем сессии, скорости отдельными ячейками,
 * ряд плиток с режимом сети — и список серверов в правом рельсе.
 *
 * Компонент только раскладывает данные: подключением, пингом и списком
 * серверов по-прежнему занимается страница «Главная», поэтому остальные
 * стили интерфейса продолжают работать со своей прежней разметкой.
 */

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
  serverProtocol: string;
  serverPing: string | null;
  serverDescription: string | null;
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

const RING_CIRCUMFERENCE = 2 * Math.PI * 43;

export function SignalHome({
  labels: m,
  state,
  stateWord,
  modeLabel,
  sessionLabel,
  sessionProgress,
  metaLine,
  profileTitle,
  profileSubtitle,
  serverFlag,
  serverName,
  serverProtocol,
  serverPing,
  serverDescription,
  downloadRate,
  downloadUnit,
  downloadTotal,
  uploadRate,
  uploadUnit,
  uploadTotal,
  tiles,
  chart,
  extras,
  actions,
  serverRail,
  onOpenServers,
  onCheckPings,
  onRefreshSubscription,
  refreshing = false,
  pinging,
  railWidth,
  railCollapsed,
  onResizeStart,
  onResizeReset,
  onExpandRail,
  expandLabel,
}: SignalHomeProps) {
  const dash = Math.max(0, Math.min(1, sessionProgress)) * RING_CIRCUMFERENCE;

  return (
    <div
      className={`signal-home${railCollapsed ? " signal-home-rail-collapsed" : ""}`}
      style={{ "--servers-panel-width": `${railWidth}px` } as React.CSSProperties}
    >
      <section className="signal-work">
        <header className="signal-work-head">
          <div className="signal-work-head-copy">
            <div className="signal-work-title">{profileTitle}</div>
            <div className="signal-work-sub">{profileSubtitle}</div>
          </div>
          <div className="signal-work-actions">
            {onRefreshSubscription && (
              <button
                type="button"
                className="signal-btn signal-btn--ghost signal-btn--sm"
                onClick={onRefreshSubscription}
                disabled={refreshing}
              >
                {m.home.refreshSubscription}
              </button>
            )}
            <button
              type="button"
              className="signal-btn signal-btn--ghost signal-btn--sm"
              onClick={onCheckPings}
              disabled={pinging}
            >
              {m.home.pingServers}
            </button>
          </div>
        </header>

        <section className={`signal-hero signal-hero--${state}`}>
          <div className="signal-hero-top">
            <div className="signal-ring" aria-hidden="true">
              <svg viewBox="0 0 100 100">
                <circle cx="50" cy="50" r="43" className="signal-ring-track" />
                <circle
                  cx="50"
                  cy="50"
                  r="43"
                  className="signal-ring-value"
                  strokeDasharray={`${dash} ${RING_CIRCUMFERENCE}`}
                />
              </svg>
              {state === "connected" && <span className="signal-ring-pulse" />}
              <span className="signal-ring-core">{sessionLabel}</span>
            </div>

            <div className="signal-hero-state">
              <div className="signal-state-line">
                <span className="signal-state-word">{stateWord}</span>
                <span className="signal-state-pill">{modeLabel}</span>
              </div>
              <div className="signal-state-meta">{metaLine}</div>

              {/* Сама плашка сервера и открывает список — отдельная кнопка
                  «Серверы» рядом была лишней. */}
              <button
                type="button"
                className="signal-server-chip"
                title={m.signal.serversTitle}
                onClick={onOpenServers}
              >
                <span className="signal-server-flag">{serverFlag}</span>
                <span className="signal-server-copy">
                  <span className="signal-server-name">{serverName}</span>
                  <span className="signal-server-proto">{serverProtocol}</span>
                  {serverDescription && (
                    <span className="signal-server-description">{serverDescription}</span>
                  )}
                </span>
                {serverPing && <span className="signal-server-ping">{serverPing}</span>}
                <span className="signal-server-open" aria-hidden="true">
                  <ChevronIcon direction="right" />
                </span>
              </button>
            </div>

            <div className="signal-power">{actions}</div>
          </div>

          <div className="signal-flow">
            <div className="signal-flow-cell">
              <span className="signal-flow-label">
                <i className="signal-flow-dot signal-flow-dot--down" />
                {m.signal.download}
              </span>
              <span className="signal-flow-value">
                {downloadRate}
                <small>{downloadUnit}</small>
              </span>
              <span className="signal-flow-total">
                {fillTemplate(m.signal.perSession, { value: downloadTotal })}
              </span>
            </div>
            <div className="signal-flow-cell">
              <span className="signal-flow-label">
                <i className="signal-flow-dot signal-flow-dot--up" />
                {m.signal.upload}
              </span>
              <span className="signal-flow-value">
                {uploadRate}
                <small>{uploadUnit}</small>
              </span>
              <span className="signal-flow-total">
                {fillTemplate(m.signal.perSession, { value: uploadTotal })}
              </span>
            </div>
          </div>

          {chart && <div className="signal-chart">{chart}</div>}

          <div className="signal-tiles">
            {tiles.map((tile) => (
              <div className="signal-tile" key={tile.key}>
                <span className="signal-tile-key">{tile.label}</span>
                <span className="signal-tile-value">
                  <i className={`signal-led signal-led--${tile.tone}`} />
                  {tile.value}
                </span>
              </div>
            ))}
          </div>
        </section>

        {extras && <div className="signal-extras">{extras}</div>}
      </section>

      {!railCollapsed && (
        <div className="signal-rail-resizer" onMouseDown={onResizeStart} onDoubleClick={onResizeReset} />
      )}
      {railCollapsed ? (
        <button
          type="button"
          className="signal-rail-expand"
          onClick={onExpandRail}
          title={expandLabel}
          aria-label={expandLabel}
        >
          <ChevronIcon direction="left" />
          <span className="signal-rail-expand-text">{expandLabel}</span>
        </button>
      ) : (
        <aside className="signal-rail-right">{serverRail}</aside>
      )}
    </div>
  );
}
