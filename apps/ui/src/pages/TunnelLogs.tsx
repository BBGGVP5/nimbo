import { useEffect, useMemo, useRef, useState } from "react";
import { api, type TunnelLogEntry } from "../lib/api";
import { useMessages } from "../lib/i18n";
import { notifyError, notifyInfo } from "../lib/notify";
import { BackButton } from "../components/BackButton";

type LevelFilter = "all" | "info" | "warn" | "error" | "debug";
type LogLevel = Exclude<LevelFilter, "all">;

const LOG_LEVELS: LogLevel[] = ["info", "warn", "error", "debug"];

export function TunnelLogs() {
  const m = useMessages();
  const [entries, setEntries] = useState<TunnelLogEntry[]>([]);
  const [query, setQuery] = useState("");
  const [level, setLevel] = useState<LevelFilter>("all");
  const [autoScroll, setAutoScroll] = useState(true);
  const [paused, setPaused] = useState(false);
  const listRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      if (paused) return;
      try {
        const next = await api.getTunnelLogs(500);
        if (!cancelled) setEntries(next);
      } catch {
        /* ignore */
      }
    };
    void tick();
    const timer = window.setInterval(() => void tick(), 1500);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [paused]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return entries.filter((entry) => {
      if (level !== "all" && entry.level !== level) return false;
      if (!q) return true;
      return (
        entry.message.toLowerCase().includes(q) ||
        (entry.timestamp ?? "").toLowerCase().includes(q) ||
        entry.source.toLowerCase().includes(q)
      );
    });
  }, [entries, query, level]);

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
      setEntries([]);
      notifyInfo(m.tunnelLogs.cleared);
    } catch (e) {
      notifyError(String(e));
    }
  };

  return (
    <div className="tunnel-logs-page h-full flex flex-col overflow-hidden">
      <BackButton />
      <div className="tunnel-logs-header">
        <div className="tunnel-logs-title-block">
          <h1 className="page-title">{m.tunnelLogs.title}</h1>
          <div className="tunnel-logs-summary">
            <span>{m.tunnelLogs.recordCount.replace("{count}", String(entries.length))}</span>
            <span>{m.tunnelLogs.shownCount.replace("{count}", String(filtered.length))}</span>
            {LOG_LEVELS.map((item) => (
              levelCounts[item] > 0 && (
                <span
                  key={item}
                  className={["tunnel-logs-summary-chip", `tunnel-logs-summary-chip-${item}`].join(" ")}
                >
                  {item.toUpperCase()} {levelCounts[item]}
                </span>
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

      <div className="tunnel-logs-container">
        <div ref={listRef} className="tunnel-logs-list">
          {filtered.length === 0 ? (
            <div className="tunnel-logs-empty">{m.tunnelLogs.empty}</div>
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
