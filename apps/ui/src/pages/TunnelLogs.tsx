import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api, type TunnelLogEntry } from "../lib/api";
import { useMessages } from "../lib/i18n";
import { notifyError, notifyInfo } from "../lib/notify";
import { BackButton } from "../components/BackButton";

type LevelFilter = "all" | "info" | "warn" | "error" | "debug";
type LogLevel = Exclude<LevelFilter, "all">;
type SourceFilter = "all" | string;

const LOG_LEVELS: LogLevel[] = ["info", "warn", "error", "debug"];

export function TunnelLogs() {
  const m = useMessages();
  const [entries, setEntries] = useState<TunnelLogEntry[]>([]);
  const [query, setQuery] = useState("");
  const [level, setLevel] = useState<LevelFilter>("all");
  const [source, setSource] = useState<SourceFilter>("all");
  const [autoScroll, setAutoScroll] = useState(true);
  const [paused, setPaused] = useState(false);
  const [refreshing, setRefreshing] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const listRef = useRef<HTMLDivElement | null>(null);

  const loadLogs = useCallback(async () => {
    setRefreshing(true);
    try {
      const next = await api.getTunnelLogs(1000);
      setEntries(next);
      setLoadError(null);
      setLastUpdated(new Date());
    } catch (error) {
      setLoadError(String(error));
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    if (paused) return;
    void loadLogs();
    const timer = window.setInterval(() => void loadLogs(), 2000);
    return () => window.clearInterval(timer);
  }, [loadLogs, paused]);

  const sources = useMemo(
    () => Array.from(new Set(entries.map((entry) => entry.source || "core"))).sort(),
    [entries],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return entries.filter((entry) => {
      if (level !== "all" && entry.level !== level) return false;
      if (source !== "all" && entry.source !== source) return false;
      if (!q) return true;
      return (
        entry.message.toLowerCase().includes(q) ||
        (entry.timestamp ?? "").toLowerCase().includes(q) ||
        entry.source.toLowerCase().includes(q)
      );
    });
  }, [entries, query, level, source]);

  const levelCounts = useMemo(() => {
    const counts: Record<LogLevel, number> = { info: 0, warn: 0, error: 0, debug: 0 };
    for (const entry of entries) {
      if (Object.prototype.hasOwnProperty.call(counts, entry.level)) {
        counts[entry.level as LogLevel] += 1;
      }
    }
    return counts;
  }, [entries]);

  useEffect(() => {
    if (!autoScroll || !listRef.current) return;
    listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [filtered, autoScroll]);

  const onClear = async () => {
    try {
      await api.clearTunnelLogs();
      await loadLogs();
      notifyInfo(m.tunnelLogs.cleared);
    } catch (e) {
      notifyError(String(e));
    }
  };

  const onOpenFolder = async () => {
    try {
      await api.openLogsFolder();
    } catch (error) {
      notifyError(String(error));
    }
  };

  const updateLabel = lastUpdated
    ? m.tunnelLogs.lastUpdated.replace("{time}", lastUpdated.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" }))
    : m.tunnelLogs.neverUpdated;

  return (
    <div className="tunnel-logs-page h-full flex flex-col overflow-hidden">
      <BackButton />
      <div className="tunnel-logs-header">
        <div className="tunnel-logs-title-block">
          <div className="tunnel-logs-eyebrow">
            <span className={["tunnel-logs-live-dot", loadError ? "is-error" : paused ? "is-paused" : ""].join(" ")} />
            {loadError ? m.tunnelLogs.loadError : paused ? m.tunnelLogs.paused : m.tunnelLogs.live}
          </div>
          <h1 className="page-title">{m.tunnelLogs.title}</h1>
          <p className="tunnel-logs-subtitle">{m.tunnelLogs.subtitle}</p>
          <div className="tunnel-logs-summary">
            <span>{m.tunnelLogs.recordCount.replace("{count}", String(entries.length))}</span>
            <span>{m.tunnelLogs.shownCount.replace("{count}", String(filtered.length))}</span>
            {LOG_LEVELS.map((item) => (
              levelCounts[item] > 0 && (
                <button
                  type="button"
                  key={item}
                  className={["tunnel-logs-summary-chip", `tunnel-logs-summary-chip-${item}`, level === item ? "is-active" : ""].join(" ")}
                  onClick={() => setLevel((current) => current === item ? "all" : item)}
                >
                  {item.toUpperCase()} {levelCounts[item]}
                </button>
              )
            ))}
          </div>
        </div>
        <div className="tunnel-logs-actions">
          <div className="tunnel-logs-search">
            <SearchIcon />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={m.tunnelLogs.searchPlaceholder}
              className="tunnel-logs-search-input"
            />
          </div>
          <select
            value={level}
            onChange={(e) => setLevel(e.target.value as LevelFilter)}
            className="tunnel-logs-select"
          >
            <option value="all">{m.tunnelLogs.levelAll}</option>
            <option value="info">INFO</option>
            <option value="warn">WARN</option>
            <option value="error">ERROR</option>
            <option value="debug">DEBUG</option>
          </select>
          <select
            value={source}
            onChange={(e) => setSource(e.target.value)}
            className="tunnel-logs-select tunnel-logs-source-select"
            aria-label={m.tunnelLogs.sourceAll}
          >
            <option value="all">{m.tunnelLogs.sourceAll}</option>
            {sources.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
          <button
            type="button"
            className="tunnel-logs-icon-btn"
            title={m.tunnelLogs.refresh}
            aria-label={m.tunnelLogs.refresh}
            onClick={() => void loadLogs()}
            disabled={refreshing}
          >
            <RefreshIcon spinning={refreshing} />
          </button>
          <button
            type="button"
            className="tunnel-logs-icon-btn"
            title={m.tunnelLogs.openFolder}
            aria-label={m.tunnelLogs.openFolder}
            onClick={() => void onOpenFolder()}
          >
            <FolderIcon />
          </button>
          <button
            type="button"
            className="tunnel-logs-icon-btn"
            title={m.tunnelLogs.clear}
            aria-label={m.tunnelLogs.clear}
            onClick={() => void onClear()}
          >
            <TrashIcon />
          </button>
        </div>
      </div>

      {loadError && (
        <div className="tunnel-logs-error" role="status">
          <span>{m.tunnelLogs.loadError}</span>
          <code>{loadError}</code>
          <button type="button" onClick={() => void loadLogs()}>{m.tunnelLogs.refresh}</button>
        </div>
      )}

      <div className="tunnel-logs-container">
        <div ref={listRef} className="tunnel-logs-list">
          {filtered.length === 0 ? (
            <div className="tunnel-logs-empty">
              <EmptyLogsIcon />
              <strong>{m.tunnelLogs.empty}</strong>
              <span>{m.tunnelLogs.emptyHint}</span>
            </div>
          ) : (
            filtered.map((entry, idx) => (
              <div
                key={`${entry.source}-${entry.timestamp ?? "no-time"}-${idx}-${entry.message.slice(0, 32)}`}
                className="tunnel-logs-row"
              >
                <span
                  className={["tunnel-logs-level", `tunnel-logs-level-${entry.level}`].join(" ")}
                >
                  {entry.level.toUpperCase()}
                </span>
                <span className="tunnel-logs-source" title={entry.source}>
                  {entry.source || "core"}
                </span>
                <span className="tunnel-logs-ts">{entry.timestamp || "--"}</span>
                <span className="tunnel-logs-text">{entry.message}</span>
              </div>
            ))
          )}
        </div>

        <div className="tunnel-logs-footer">
          <span className="tunnel-logs-updated">{updateLabel}</span>
          <label className="tunnel-logs-checkbox">
            <input
              type="checkbox"
              checked={autoScroll}
              onChange={(e) => setAutoScroll(e.target.checked)}
            />
            <span>{m.tunnelLogs.autoScroll}</span>
          </label>
          <label className="tunnel-logs-checkbox">
            <input
              type="checkbox"
              checked={paused}
              onChange={(e) => setPaused(e.target.checked)}
            />
            <span>{m.tunnelLogs.pause}</span>
          </label>
          <span className="tunnel-logs-count">
            {m.tunnelLogs.recordCount.replace("{count}", String(filtered.length))}
          </span>
        </div>
      </div>
    </div>
  );
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 6h18" />
      <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
      <path d="M19 6 18 20a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
      <path d="M10 11v6M14 11v6" />
    </svg>
  );
}

function RefreshIcon({ spinning }: { spinning: boolean }) {
  return (
    <svg viewBox="0 0 24 24" className={["h-4 w-4", spinning ? "tunnel-logs-spin" : ""].join(" ")} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 6v5h-5" />
      <path d="M4 18v-5h5" />
      <path d="M6.1 9A7 7 0 0 1 18 6l2 5M4 13l2 5a7 7 0 0 0 11.9-3" />
    </svg>
  );
}

function FolderIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 6.5A1.5 1.5 0 0 1 4.5 5H9l2 2h8.5A1.5 1.5 0 0 1 21 8.5v9A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5Z" />
    </svg>
  );
}

function EmptyLogsIcon() {
  return (
    <svg viewBox="0 0 48 48" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M14 7h14l7 7v27H14Z" />
      <path d="M28 7v8h7M19 23h11M19 29h11M19 35h7" />
    </svg>
  );
}
