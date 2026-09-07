import { formatBytes } from "../../lib/api";
import { fillTemplate, type Messages } from "../../lib/i18n";

/**
 * Статистика в стиле Signal, как на превью: слева карточка суммарного
 * трафика с областями приёма и отдачи, справа разбивка по приложениям,
 * снизу таблица сессий.
 *
 * Компонент только рисует; данные и период выбирает страница.
 */

export type SignalStatsRange = "hour" | "day" | "week";

export interface SignalStatsPoint {
  download: number;
  upload: number;
}

export interface SignalStatsApp {
  name: string;
  bytes: number;
}

export interface SignalStatsSession {
  id: string;
  startedLabel: string;
  server: string;
  flag: string;
  duration: string;
  download: number;
  upload: number;
  ping: number | null;
}

export interface SignalStatisticsProps {
  labels: Messages;
  subtitle: string;
  range: SignalStatsRange;
  onRange: (range: SignalStatsRange) => void;
  totalBytes: number;
  downloadBytes: number;
  uploadBytes: number;
  points: SignalStatsPoint[];
  axis: string[];
  apps: SignalStatsApp[];
  sessions: SignalStatsSession[];
  onReset: () => void;
}

const CHART_WIDTH = 640;
const CHART_HEIGHT = 190;

/** Строит путь области и линии по точкам, нормируя к максимуму. */
function buildPaths(values: number[]): { line: string; area: string } {
  if (values.length === 0) return { line: "", area: "" };
  const max = Math.max(...values, 1);
  const step = values.length > 1 ? CHART_WIDTH / (values.length - 1) : CHART_WIDTH;
  const points = values.map((value, index) => {
    const x = Math.round(index * step);
    const y = Math.round(CHART_HEIGHT - (value / max) * (CHART_HEIGHT - 16) - 8);
    return `${x},${y}`;
  });
  return {
    line: `M${points.join(" L")}`,
    area: `M${points.join(" L")} L${CHART_WIDTH},${CHART_HEIGHT} L0,${CHART_HEIGHT} Z`,
  };
}

export function SignalStatistics({
  labels: m,
  subtitle,
  range,
  onRange,
  totalBytes,
  downloadBytes,
  uploadBytes,
  points,
  axis,
  apps,
  sessions,
  onReset,
}: SignalStatisticsProps) {
  const download = buildPaths(points.map((point) => point.download));
  const upload = buildPaths(points.map((point) => point.upload));
  const totalText = formatBytes(totalBytes).split(" ");
  const appsMax = Math.max(1, ...apps.map((app) => app.bytes));

  return (
    <div className="page-view signal-page">
      <header className="signal-work-head">
        <div className="signal-work-head-copy">
          <div className="signal-work-title">{m.statistics.title}</div>
          <div className="signal-work-sub">{subtitle}</div>
        </div>
        <div className="signal-seg" role="group" aria-label={m.statistics.title}>
          {(["hour", "day", "week"] as SignalStatsRange[]).map((value) => (
            <button
              type="button"
              key={value}
              className={`signal-seg-item${range === value ? " is-on" : ""}`}
              onClick={() => onRange(value)}
            >
              {value === "hour" ? m.signal.rangeHour : value === "day" ? m.signal.rangeDay : m.signal.rangeWeek}
            </button>
          ))}
        </div>
      </header>

      <div className="signal-stats-grid">
        <section className="signal-card signal-chart-card">
          <div className="signal-chart-head">
            <div>
              <div className="signal-section-kicker">{m.signal.totalTraffic}</div>
              <div className="signal-total">
                {totalText[0]}
                <small>{totalText[1] ?? ""}</small>
              </div>
            </div>
            <div className="signal-legend">
              <span>
                <i className="signal-flow-dot signal-flow-dot--down" />
                {m.signal.download} {formatBytes(downloadBytes)}
              </span>
              <span>
                <i className="signal-flow-dot signal-flow-dot--up" />
                {m.signal.upload} {formatBytes(uploadBytes)}
              </span>
            </div>
          </div>

          <svg
            className="signal-chart-svg"
            viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
            preserveAspectRatio="none"
            role="img"
            aria-label={m.signal.totalTraffic}
          >
            <defs>
              <linearGradient id="signal-dl" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--signal-sky, #5bc0ff)" stopOpacity="0.35" />
                <stop offset="100%" stopColor="var(--signal-sky, #5bc0ff)" stopOpacity="0" />
              </linearGradient>
              <linearGradient id="signal-ul" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--signal-lilac, #b08cff)" stopOpacity="0.3" />
                <stop offset="100%" stopColor="var(--signal-lilac, #b08cff)" stopOpacity="0" />
              </linearGradient>
            </defs>
            <line className="signal-chart-grid" x1="0" y1={CHART_HEIGHT * 0.33} x2={CHART_WIDTH} y2={CHART_HEIGHT * 0.33} />
            <line className="signal-chart-grid" x1="0" y1={CHART_HEIGHT * 0.66} x2={CHART_WIDTH} y2={CHART_HEIGHT * 0.66} />
            <path d={download.area} fill="url(#signal-dl)" />
            <path d={download.line} className="signal-chart-line signal-chart-line--down" />
            <path d={upload.area} fill="url(#signal-ul)" />
            <path d={upload.line} className="signal-chart-line signal-chart-line--up" />
          </svg>

          <div className="signal-chart-axis">
            {axis.map((label) => (
              <span key={label}>{label}</span>
            ))}
          </div>
        </section>

        <section className="signal-card signal-apps-card">
          <div className="signal-card-head">
            <span className="signal-apps-title">{m.signal.byApps}</span>
          </div>
          <div className="signal-apps-list">
            {apps.length === 0 && <div className="signal-srv-empty">{m.signal.noAppData}</div>}
            {apps.map((app) => (
              <div className="signal-app-row" key={app.name}>
                <span className="signal-app-icon" aria-hidden="true">
                  {app.name.slice(0, 2)}
                </span>
                <span className="signal-app-copy">
                  <span className="signal-app-name">{app.name}</span>
                  <span className="signal-app-bar">
                    <i style={{ width: `${Math.round((app.bytes / appsMax) * 100)}%` }} />
                  </span>
                </span>
                <span className="signal-app-value">{formatBytes(app.bytes)}</span>
              </div>
            ))}
          </div>
          <p className="signal-apps-note">{m.signal.tunnelTrafficOnly}</p>
        </section>
      </div>

      <section className="signal-table-wrap">
        <table className="signal-table">
          <thead>
            <tr>
              <th>{m.signal.columnSession}</th>
              <th>{m.signal.columnServer}</th>
              <th>{m.signal.columnDuration}</th>
              <th>{m.signal.download}</th>
              <th>{m.signal.upload}</th>
              <th className="signal-table-action">{m.signal.columnAvgPing}</th>
            </tr>
          </thead>
          <tbody>
            {sessions.length === 0 && (
              <tr>
                <td colSpan={6} className="signal-table-empty">
                  {m.signal.noSessions}
                </td>
              </tr>
            )}
            {sessions.map((session) => (
              <tr key={session.id}>
                <td className="signal-num">{session.startedLabel}</td>
                <td>
                  <span className="signal-session-server">
                    <span className="signal-session-flag">{session.flag}</span>
                    {session.server}
                  </span>
                </td>
                <td className="signal-num">{session.duration}</td>
                <td className="signal-num">{formatBytes(session.download)}</td>
                <td className="signal-num">{formatBytes(session.upload)}</td>
                <td className="signal-num signal-table-action">
                  {session.ping != null ? (
                    <span className={session.ping < 60 ? "is-good" : session.ping < 150 ? "is-fair" : "is-bad"}>
                      {fillTemplate(m.signal.msValue, { value: session.ping })}
                    </span>
                  ) : (
                    "—"
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <div className="signal-card-head">
        <span className="signal-section-kicker">{m.statistics.reset}</span>
        <button type="button" className="signal-btn signal-btn--ghost signal-btn--sm" onClick={onReset}>
          {m.statistics.reset}
        </button>
      </div>
    </div>
  );
}
