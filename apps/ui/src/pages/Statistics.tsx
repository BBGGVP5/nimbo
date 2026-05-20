import { useEffect } from "react";
import type { ReactNode } from "react";
import { api, formatBytes } from "../lib/api";
import { useMessages } from "../lib/i18n";
import { useAppStore } from "../store";
import { notifyError, notifyInfo } from "../lib/notify";

export function Statistics() {
  const m = useMessages();
  const status = useAppStore((s) => s.status);
  const connected = status?.state === "connected";

  const stats = useAppStore((s) => s.trafficStats);
  const speed = useAppStore((s) => s.trafficSpeed);
  const sessionStartedAt = useAppStore((s) => s.sessionStartedAt);
  const setTrafficStats = useAppStore((s) => s.setTrafficStats);
  const setTrafficSpeed = useAppStore((s) => s.setTrafficSpeed);
  const setTrafficSample = useAppStore((s) => s.setTrafficSample);
  const setSessionStartedAt = useAppStore((s) => s.setSessionStartedAt);

  useEffect(() => {
    if (!connected) return;
    if (useAppStore.getState().sessionStartedAt == null) {
      setSessionStartedAt(Date.now());
    }
  }, [connected, setSessionStartedAt]);

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      try {
        const next = await api.getTrafficStats();
        if (cancelled) return;
        setTrafficStats(next);
        const now = Date.now();
        const prev = useAppStore.getState().trafficSample;
        setTrafficSample({ upload: next.session_upload, download: next.session_download, at: now });
        if (prev && connected) {
          const dt = Math.max(0.001, (now - prev.at) / 1000);
          setTrafficSpeed({
            upload: Math.max(0, (next.session_upload - prev.upload) / dt),
            download: Math.max(0, (next.session_download - prev.download) / dt),
          });
        } else if (!connected) {
          setTrafficSpeed({ upload: 0, download: 0 });
        }
      } catch {
        /* ignore */
      }
    };
    void tick();
    const timer = window.setInterval(() => void tick(), 1000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [connected, setTrafficStats, setTrafficSample, setTrafficSpeed]);

  const handleReset = async () => {
    try {
      await api.resetTrafficTotals();
      setTrafficSample(null);
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

  return (
    <div className="statistics-page h-full overflow-auto">
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
          value={formatSpeed(speed.upload)}
          tint="up"
          icon={<ArrowUpIcon />}
          total={stats ? formatBytes(stats.session_upload) : "0 B"}
        />
        <SpeedCard
          label={m.statistics.received}
          value={formatSpeed(speed.download)}
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
