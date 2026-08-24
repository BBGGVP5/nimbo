import type { ReactNode } from "react";
import { useState } from "react";
import { serverDisplayLabel } from "../lib/serverUiOverrides";
import { SignalStatistics, type SignalStatsRange } from "./stats/SignalStatistics";
import { api, formatBytes } from "../lib/api";
import { useMessages } from "../lib/i18n";
import { useAppStore } from "../store";
import { notifyError, notifyInfo } from "../lib/notify";
import { BackButton } from "../components/BackButton";

export function Statistics() {
  const m = useMessages();
  const status = useAppStore((s) => s.status);
  const connected = status?.state === "connected";

  const stats = useAppStore((s) => s.trafficStats);
  const speed = useAppStore((s) => s.trafficSpeed);
  const speedAvailable = useAppStore((s) => s.trafficMonitoringAvailable);
  const sessionStartedAt = useAppStore((s) => s.sessionStartedAt);
  const setTrafficStats = useAppStore((s) => s.setTrafficStats);
  const preferences = useAppStore((s) => s.preferences);
  const speedHistory = useAppStore((s) => s.trafficHistory);
  const activeServerId = useAppStore((s) => s.activeServerId);
  const subscriptions = useAppStore((s) => s.subscriptions);
  const serverPings = useAppStore((s) => s.serverPings);
  const [range, setRange] = useState<SignalStatsRange>("day");
  const appTraffic = useAppStore((state) => state.appTraffic);
  const sessionHistory = useAppStore((state) => state.sessionHistory);
  const activeServer = subscriptions
    .flatMap((sub) => sub.servers)
    .find((server) => server.id === activeServerId) ?? null;
  const activeServerName = activeServer ? serverDisplayLabel(activeServer) : "";
  const activePing = activeServerId ? serverPings[activeServerId] : undefined;

  const handleReset = async () => {
    try {
      await api.resetTrafficTotals();
      const next = await api.getTrafficStats().catch(() => null);
      if (next) setTrafficStats(next);
      notifyInfo(m.statistics.totalsReset);
    } catch (e) {
      notifyError(String(e));
    }
  };

  const statusLabel = connected
    ? m.statistics.statusConnected
    : status?.state === "connecting"
      ? m.statistics.statusConnecting
      : m.statistics.statusDisconnected;

  // ── Signal ────────────────────────────────────────────────────
  // Раскладка превью: карточка суммарного трафика с графиком, разбивка
  // по приложениям и таблица сессий. Данные те же, что на обычном экране.
  if (preferences.ui_style === "signal") {
    const sessionUp = stats?.session_upload ?? 0;
    const sessionDown = stats?.session_download ?? 0;
    const points = speedHistory.length > 0
      ? speedHistory.map((sample) => ({ download: sample.download, upload: sample.upload }))
      : [{ download: 0, upload: 0 }, { download: 0, upload: 0 }];
    const axis = signalAxis(range, m);
    const totals = range === "hour"
      ? { down: sessionDown, up: sessionUp }
      : range === "day"
        ? { down: stats?.monthly_download ?? 0, up: stats?.monthly_upload ?? 0 }
        : { down: stats?.all_time_download ?? 0, up: stats?.all_time_upload ?? 0 };
    // Приложения: текущая сессия — живая оценка, завершённые — то, что
    // записалось в историю.
    const signalApps = (sessionStartedAt
      ? Object.entries(appTraffic).map(([name, value]) => ({
          name,
          bytes: Math.round(value.download + value.upload),
        }))
      : (sessionHistory[0]?.apps ?? []))
      .filter((item) => item.bytes > 0)
      .sort((a, b) => b.bytes - a.bytes)
      .slice(0, 6);

    const current = sessionStartedAt
      ? [{
          id: "current",
          startedLabel: formatTime(sessionStartedAt),
          server: activeServerName || m.signal.noServer,
          flag: m.signal.sessionNow,
          duration: formatDurationShort(Date.now() - sessionStartedAt),
          download: sessionDown,
          upload: sessionUp,
          ping: activePing ?? null,
        }]
      : [];
    const sessions = [
      ...current,
      ...sessionHistory.slice(0, 12).map((item) => ({
        id: item.id,
        startedLabel: formatSessionStart(item.startedAt),
        server: item.serverName || m.signal.noServer,
        flag: "",
        duration: formatDurationShort(item.endedAt - item.startedAt),
        download: item.download,
        upload: item.upload,
        ping: item.ping,
      })),
    ];

    return (
      <SignalStatistics
        labels={m}
        subtitle={statusLabel}
        range={range}
        onRange={setRange}
        totalBytes={totals.down + totals.up}
        downloadBytes={totals.down}
        uploadBytes={totals.up}
        points={points}
        axis={axis}
        apps={signalApps}
        sessions={sessions}
        onReset={() => void handleReset()}
      />
    );
  }

  return (
    <div className="statistics-page h-full overflow-auto">
      <BackButton />
      <div className="statistics-header">
        <h1 className="page-title">{m.statistics.title}</h1>
        <button
          type="button"
          className="settings-action"
          onClick={() => void handleReset()}
        >
          {m.statistics.reset}
        </button>
      </div>

      <div className="statistics-speed-grid">
        <SpeedCard
          label={m.statistics.uploaded}
          value={speedAvailable ? formatSpeed(speed.upload) : "—"}
          tint="up"
          icon={<ArrowUpIcon />}
          total={stats ? formatBytes(stats.session_upload) : "0 B"}
        />
        <SpeedCard
          label={m.statistics.received}
          value={speedAvailable ? formatSpeed(speed.download) : "—"}
          tint="down"
          icon={<ArrowDownIcon />}
          total={stats ? formatBytes(stats.session_download) : "0 B"}
        />
      </div>

      <div className="statistics-totals-grid">
        <TotalsCard
          title={m.statistics.allTime}
          icon={<HistoryIcon />}
          upload={stats?.all_time_upload ?? 0}
          download={stats?.all_time_download ?? 0}
          labels={m}
        />
        <TotalsCard
          title={m.statistics.thisMonth.replace("{month}", formatMonthPeriod(stats?.monthly_period))}
          icon={<CalendarIcon />}
          upload={stats?.monthly_upload ?? 0}
          download={stats?.monthly_download ?? 0}
          labels={m}
        />
      </div>

      <div className="statistics-info-card">
        <InfoRow label={m.statistics.statusLabel} value={statusLabel} highlighted={connected} />
        <InfoRow
          label={m.statistics.connectedAt}
          value={sessionStartedAt ? formatTime(sessionStartedAt) : "—"}
        />
        <InfoRow
          label={m.statistics.sessionStart}
          value={sessionStartedAt ? formatDate(sessionStartedAt) : "—"}
        />
        <InfoRow
          label={m.statistics.txBytes}
          value={stats ? formatBytes(stats.session_upload) : "0 B"}
          numeric
        />
        <InfoRow
          label={m.statistics.rxBytes}
          value={stats ? formatBytes(stats.session_download) : "0 B"}
          numeric
        />
      </div>
    </div>
  );
}

function SpeedCard({
  label,
  value,
  total,
  icon,
  tint,
}: {
  label: string;
  value: string;
  total: string;
  icon: ReactNode;
  tint: "up" | "down";
}) {
  const m = useMessages();
  return (
    <div className={["statistics-speed-card", tint === "up" ? "tint-up" : "tint-down"].join(" ")}>
      <div className="statistics-speed-head">
        <span className="statistics-speed-icon">{icon}</span>
        <span className="statistics-speed-label">{label}</span>
      </div>
      <div className="statistics-speed-value">{value}</div>
      <div className="statistics-speed-total">
        {m.statistics.sessionTotal}: <span>{total}</span>
      </div>
    </div>
  );
}

function TotalsCard({
  title,
  icon,
  upload,
  download,
  labels,
}: {
  title: string;
  icon: ReactNode;
  upload: number;
  download: number;
  labels: ReturnType<typeof useMessages>;
}) {
  return (
    <div className="statistics-totals-card">
      <div className="statistics-totals-head">
        <span className="statistics-totals-icon">{icon}</span>
        <span className="statistics-totals-title">{title}</span>
      </div>
      <div className="statistics-totals-rows">
        <div className="statistics-totals-row">
          <span className="statistics-totals-row-label">
            <ArrowUpIcon />
            {labels.statistics.uploaded}
          </span>
          <span className="statistics-totals-row-value tint-up">{formatBytes(upload)}</span>
        </div>
        <div className="statistics-totals-row">
          <span className="statistics-totals-row-label">
            <ArrowDownIcon />
            {labels.statistics.received}
          </span>
          <span className="statistics-totals-row-value tint-down">{formatBytes(download)}</span>
        </div>
        <div className="statistics-totals-row">
          <span className="statistics-totals-row-label">{labels.statistics.combined}</span>
          <span className="statistics-totals-row-value">{formatBytes(upload + download)}</span>
        </div>
      </div>
    </div>
  );
}

function InfoRow({
  label,
  value,
  numeric = false,
  highlighted = false,
}: {
  label: string;
  value: string;
  numeric?: boolean;
  highlighted?: boolean;
}) {
  return (
    <div className="statistics-info-row">
      <span className="statistics-info-label">{label}</span>
      <span
        className={[
          "statistics-info-value",
          numeric ? "font-variant-numeric tabular-nums" : "",
          highlighted ? "statistics-info-value-active" : "",
        ].join(" ")}
      >
        {value}
      </span>
    </div>
  );
}

function formatSpeed(bps: number): string {
  if (!Number.isFinite(bps) || bps <= 0) return "0 B/s";
  const units = ["B/s", "KB/s", "MB/s", "GB/s"];
  let v = bps;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  const precision = v >= 100 ? 0 : v >= 10 ? 1 : 2;
  return `${v.toFixed(precision)} ${units[i]}`;
}

function formatTime(ms: number): string {
  const elapsed = Math.max(0, Math.floor((Date.now() - ms) / 1000));
  const hours = Math.floor(elapsed / 3600);
  const minutes = Math.floor((elapsed % 3600) / 60);
  const seconds = elapsed % 60;
  const pad = (v: number) => v.toString().padStart(2, "0");
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

function formatDate(ms: number): string {
  const d = new Date(ms);
  return d.toLocaleString();
}

function formatMonthPeriod(period?: string): string {
  if (!period) return "—";
  const match = period.match(/^(\d{4})-(\d{2})$/);
  if (!match) return period;
  return `${match[2]}-${match[1]}`;
}

function ArrowUpIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 19V5" />
      <path d="m5 12 7-7 7 7" />
    </svg>
  );
}

function ArrowDownIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 5v14" />
      <path d="m19 12-7 7-7-7" />
    </svg>
  );
}

function HistoryIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
      <path d="M3 4v4h4" />
      <path d="M12 7v5l3 2" />
    </svg>
  );
}

function CalendarIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M16 3v4M8 3v4M3 11h18" />
    </svg>
  );
}

/** Подписи оси под выбранный период — как на превью. */
function signalAxis(range: SignalStatsRange, m: ReturnType<typeof useMessages>): string[] {
  if (range === "hour") return ["-60", "-45", "-30", "-15", m.signal.axisNow];
  if (range === "week") return ["-7", "-5", "-3", "-1", m.signal.axisNow];
  return ["00:00", "06:00", "12:00", "18:00", "23:59"];
}

/** Длительность сессии в формате чч:мм:сс. */
function formatDurationShort(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const hours = String(Math.floor(total / 3600)).padStart(2, "0");
  const minutes = String(Math.floor((total % 3600) / 60)).padStart(2, "0");
  const seconds = String(total % 60).padStart(2, "0");
  return `${hours}:${minutes}:${seconds}`;
}

/** «Сегодня 16:19» / «Вчера 21:40» / дата — как в превью. */
function formatSessionStart(at: number): string {
  const date = new Date(at);
  const today = new Date();
  const yesterday = new Date(today.getTime() - 86400000);
  const sameDay = (a: Date, b: Date) =>
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
  const time = date.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  if (sameDay(date, today)) return time;
  if (sameDay(date, yesterday)) return `-1 ${time}`;
  return `${date.toLocaleDateString(undefined, { day: "2-digit", month: "2-digit" })} ${time}`;
}
