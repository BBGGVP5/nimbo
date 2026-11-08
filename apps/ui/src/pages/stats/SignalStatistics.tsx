import { LatencyDisplay } from "../../components/LatencyDisplay";
import { Surface } from "../../components/Universal";
import { PageHeader, StatePanel, useSecondaryCopy } from "../../components/Secondary";
import type { ReactNode } from "react";
import { formatBytes } from "../../lib/api";
import { type Messages } from "../../lib/i18n";
import { trafficPaths, type StatsRange } from "../../lib/statisticsPresentation";
export type SignalStatsRange = StatsRange;
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
  download: number | null;
  upload: number | null;
  ping: number | null;
}

export interface SignalStatisticsProps {
  labels: Messages;
  subtitle: string;
  range: SignalStatsRange;
  onRange: (range: SignalStatsRange) => void;
  dashboard: ReactNode;
  points: SignalStatsPoint[];
  axis: string[];
  apps: SignalStatsApp[];
  sessions: SignalStatsSession[];
  onReset: () => void;
}


export function SignalStatistics({ labels: m, subtitle, range, onRange, dashboard, points, axis, apps, sessions, onReset }: SignalStatisticsProps) {
  const copy = useSecondaryCopy();
  const paths = trafficPaths(points);
  const appsMax = Math.max(1, ...apps.map(app => app.bytes));
  const bytes = (value: number | null) => value === null ? "—" : formatBytes(value);
  return <div className="page-view secondary-page statistics-workspace">
    <PageHeader title={m.statistics.title} description={subtitle} actions={<button className="btn" onClick={onReset}>{m.statistics.reset}</button>} />
    <div className="secondary-segments" role="group" aria-label={m.statistics.title}>
      {(["session", "month", "all"] as SignalStatsRange[]).map(value => <button key={value} className="btn" aria-pressed={value === range} onClick={() => onRange(value)}>{value === "session" ? copy.session : value === "month" ? copy.month : copy.allTime}</button>)}
    </div>
    {dashboard}
    <div className="statistics-analysis-grid">
      <Surface className="statistics-chart"><div className="secondary-section-head"><h2>{copy.samples}</h2><span>{paths.count > 1 ? `${formatBytes(paths.max)}/s` : "—"}</span></div>
        {paths.count < 2 ? <StatePanel title={copy.unavailable} detail={copy.measurementHint} /> : <>
          <svg viewBox="0 0 640 180" preserveAspectRatio="none" role="img" aria-label={copy.samples}><path className="statistics-grid-line" d="M0,60 H640 M0,120 H640" /><path data-series="download" d={paths.download} /><path data-series="upload" d={paths.upload} /></svg>
          <div className="statistics-axis">{axis.map((label, i) => <span key={i}>{label}</span>)}</div>
          <div className="statistics-legend"><span>— {m.signal.download}</span><span>┄ {m.signal.upload}</span></div>
        </>}
      </Surface>
      <Surface className="statistics-apps"><div className="secondary-section-head"><h2>{m.signal.byApps}</h2><span>{apps.length}</span></div>
        {apps.length === 0 ? <StatePanel title={m.signal.noAppData} /> : apps.map(app => <div className="statistics-app-row" key={app.name}><div><strong>{app.name}</strong><span>{formatBytes(app.bytes)}</span></div><div className="statistics-app-track"><i style={{width: `${app.bytes / appsMax * 100}%`}} /></div></div>)}
        <p className="secondary-note">{m.signal.tunnelTrafficOnly} {m.common.locale.startsWith("ru") ? "Оценка по активным соединениям, не измеренные байты приложений." : "Estimated from active connections, not measured per-app bytes."}</p>
      </Surface>
    </div>
    <Surface className="statistics-sessions"><div className="secondary-section-head"><h2>{copy.history}</h2><span>{sessions.length}</span></div>
      {sessions.length === 0 ? <StatePanel title={m.signal.noSessions} /> : <table><thead><tr><th>{m.signal.columnSession}</th><th>{m.signal.columnServer}</th><th>{m.signal.columnDuration}</th><th>{m.signal.download}</th><th>{m.signal.upload}</th><th>{m.signal.columnAvgPing}</th></tr></thead><tbody>
        {sessions.map(session => <tr key={session.id}><td data-label={m.signal.columnSession}>{session.startedLabel} {session.flag}</td><td data-label={m.signal.columnServer}>{session.server}</td><td data-label={m.signal.columnDuration}>{session.duration}</td><td data-label={m.signal.download}>{bytes(session.download)}</td><td data-label={m.signal.upload}>{bytes(session.upload)}</td><td data-label={m.signal.columnAvgPing}>{session.ping != null ? <LatencyDisplay value={session.ping} /> : "—"}</td></tr>)}
      </tbody></table>}
    </Surface>
  </div>;
}
