import { useEffect, useMemo, useState, type ReactNode } from "react";
import { protocolLabel, transportLabel, type Server, type Subscription } from "../../lib/api";
import { fillTemplate, type Messages } from "../../lib/i18n";
import { serverDisplayLabel } from "../../lib/serverUiOverrides";
import { CountryFlag } from "../../components/CountryFlag";

/**
 * Рельс серверов в стиле Signal: поиск, фильтры-чипы и плотный список,
 * где качество связи видно полосками, а не только цифрой.
 *
 * Компонент работает с теми же данными и обработчиками, что и обычная
 * боковая панель, поэтому сортировка, избранное, фильтр по протоколу и
 * выбор сервера ведут себя одинаково в обоих стилях.
 */

type SortMode = "default" | "name" | "ping" | "protocol";

export interface SignalServerRailProps {
  labels: Messages;
  subs: Subscription[];
  currentSub: Subscription | null;
  entries: Array<{ server: Server; sub: Subscription }>;
  activeId: string | null;
  pingByServer: Record<string, number | undefined>;
  pingingServerIds: Set<string>;
  favorites: Set<string>;
  onToggleFavorite: (id: string) => void;
  sortMode: SortMode;
  onSortMode: (mode: SortMode) => void;
  pingOrder: "asc" | "desc";
  onPingOrder: (order: "asc" | "desc") => void;
  protocolFilter: string | null;
  onProtocolFilter: (value: string | null) => void;
  availableProtocols: string[];
  showFavOnly: boolean;
  onShowFavOnly: (value: boolean) => void;
  onPickServer: (server: Server, sub: Subscription) => void;
  pinging: boolean;
  onPing: () => void;
  onSwitchSubscription: (url: string) => void;
  onCollapse?: () => void;
  emptyAction?: ReactNode;
  hiddenCount?: number;
  onShowHidden?: () => void;
}

/** Полоски качества: 4 — отличный пинг, 1 — плохой, 0 — не измерен. */
function pingBars(ping: number | undefined): number {
  if (ping == null) return 0;
  if (ping < 60) return 4;
  if (ping < 120) return 3;
  if (ping < 220) return 2;
  return 1;
}

export function SignalServerRail({
  labels: m,
  subs,
  currentSub,
  entries,
  activeId,
  pingByServer,
  pingingServerIds,
  favorites,
  onToggleFavorite,
  sortMode,
  onSortMode,
  pingOrder,
  onPingOrder,
  protocolFilter,
  onProtocolFilter,
  availableProtocols,
  showFavOnly,
  onShowFavOnly,
  onPickServer,
  pinging,
  onPing,
  onSwitchSubscription,
  onCollapse,
  emptyAction,
  hiddenCount = 0,
  onShowHidden,
}: SignalServerRailProps) {
  const [query, setQuery] = useState("");
  const sortValue = sortMode === "name"
    ? "name"
    : sortMode === "ping"
      ? (pingOrder === "asc" ? "ping-asc" : "ping-desc")
      : "default";

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return entries;
    return entries.filter(({ server }) =>
      `${serverDisplayLabel(server)} ${protocolLabel(server.protocol)}`.toLowerCase().includes(needle),
    );
  }, [entries, query]);

  return (
    <div className="signal-srv">
      <div className="signal-srv-head">
        <span className="signal-srv-title">{m.signal.serversTitle}</span>
        <span className="signal-srv-count">
          {fillTemplate(m.signal.railCount, { servers: entries.length, profiles: subs.length })}
        </span>
        {onCollapse && (
          <button
            type="button"
            className="signal-icon-btn signal-srv-collapse"
            onClick={onCollapse}
            title={m.signal.collapseRail}
            aria-label={m.signal.collapseRail}
          >
            <ChevronIcon direction="right" />
          </button>
        )}
      </div>

      {subs.length > 1 && (
        <div className="signal-srv-subs">
          {subs.map((sub) => {
            const name = sub.name?.trim() || m.common.subscription;
            return (
              <button
                type="button"
                key={sub.url}
                className={`signal-chip${currentSub?.url === sub.url ? " is-on" : ""}`}
                onClick={() => onSwitchSubscription(sub.url)}
                title={name}
              >
                {name}
              </button>
            );
          })}
        </div>
      )}

      <label className="signal-srv-search">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" aria-hidden="true">
          <circle cx="11" cy="11" r="6.5" />
          <path d="m20 20-3.6-3.6" />
        </svg>
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={m.profiles.searchServers}
          spellCheck={false}
        />
      </label>

      <div className="signal-srv-tools">
        <SignalSelect
          className="signal-select--grow"
          icon={<SortIcon />}
          title={m.signal.sortBy}
          value={sortValue}
          options={[
            { value: "default", label: m.signal.sortDefault },
            { value: "ping-asc", label: `${m.signal.sortPing} ↑` },
            { value: "ping-desc", label: `${m.signal.sortPing} ↓` },
            { value: "name", label: m.signal.sortName },
          ]}
          onChange={(value) => {
            if (value === "name") {
              onSortMode("name");
              return;
            }
            if (value === "ping-asc" || value === "ping-desc") {
              onSortMode("ping");
              onPingOrder(value === "ping-asc" ? "asc" : "desc");
              return;
            }
            onSortMode("default");
          }}
        />

        <button
          type="button"
          className={`signal-icon-btn${showFavOnly ? " is-on" : ""}`}
          onClick={() => onShowFavOnly(!showFavOnly)}
          title={m.signal.favOnly}
          aria-pressed={showFavOnly}
        >
          <StarIcon filled={showFavOnly} />
        </button>
        <button
          type="button"
          className={`signal-icon-btn${pinging ? " is-pinging" : ""}`}
          onClick={onPing}
          disabled={pinging}
          title={m.home.pingServers}
          aria-label={m.home.pingServers}
        >
          <PingIcon />
        </button>

        {availableProtocols.length > 0 && (
          <SignalSelect
            className="signal-select--wide"
            icon={<FilterIcon />}
            title={m.signal.columnProtocol}
            value={protocolFilter ?? ""}
            options={[
              { value: "", label: m.signal.allProtocols },
              ...availableProtocols.map((proto) => ({ value: proto, label: proto })),
            ]}
            onChange={(value) => {
              onProtocolFilter(value ? value : null);
              if (value) onSortMode("protocol");
              else if (sortMode === "protocol") onSortMode("default");
            }}
          />
        )}
      </div>

      <div className="signal-srv-list">
        {visible.length === 0 && (
          <div className="signal-srv-empty">
            {entries.length === 0 ? m.common.serverNotSelected : m.home.noFavorites}
            {emptyAction}
          </div>
        )}
        {visible.map(({ server, sub }) => {
          const isActive = server.id === activeId;
          const ping = pingByServer[server.id];
          const bars = pingBars(ping);
          const loading = pingingServerIds.has(server.id);
          const favorite = favorites.has(server.id);
          return (
            <div
              key={`${sub.url}:${server.id}`}
              className={`signal-srv-row${isActive ? " is-active" : ""}`}
            >
              <button
                type="button"
                className="signal-srv-pick"
                onClick={() => onPickServer(server, sub)}
                title={serverDisplayLabel(server)}
              >
                <span className="signal-srv-flag" aria-hidden="true">
                  <CountryFlag serverName={server.name} fallback={<span className="signal-srv-globe">◍</span>} className="country-flag-sm" />
                </span>
                <span className="signal-srv-copy">
                  <span className="signal-srv-name">{serverDisplayLabel(server)}</span>
                  <span className="signal-srv-sub">
                    {isActive
                      ? fillTemplate(m.signal.activeNow, { protocol: protocolLabel(server.protocol) })
                      : `${protocolLabel(server.protocol)} · ${transportLabel(server.protocol) || "JSON"}`}
                  </span>
                </span>
                <span className="signal-srv-ping">
                  <span className={`signal-bars signal-bars--${bars}`} aria-hidden="true">
                    <i />
                    <i />
                    <i />
                    <i />
                  </span>
                  <span className="signal-srv-ms">{loading ? "…" : ping != null ? ping : "—"}</span>
                </span>
              </button>
              <button
                type="button"
                className={`signal-star${favorite ? " is-on" : ""}`}
                onClick={() => onToggleFavorite(server.id)}
                title={m.home.favoritesOnly}
                aria-pressed={favorite}
              >
                <StarIcon filled={favorite} />
              </button>
            </div>
          );
        })}
      </div>

      {hiddenCount > 0 && (
        <div className="signal-srv-hidden">
          <span>{fillTemplate(m.common.hiddenServers, { count: hiddenCount })}</span>
          {onShowHidden && (
            <button type="button" onClick={onShowHidden}>
              {m.common.showHiddenServers}
            </button>
          )}
        </div>
      )}
    </div>
  );
}

/** Шеврон для кнопок сворачивания рельса. */
export function ChevronIcon({ direction }: { direction: "left" | "right" }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {direction === "right" ? <path d="m9 5 7 7-7 7" /> : <path d="m15 5-7 7 7 7" />}
    </svg>
  );
}

/** Значок сортировки — две строки разной длины со стрелкой. */
function SortIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" aria-hidden="true">
      <path d="M4 7h11M4 12h7M4 17h4" />
      <path d="M17 10v8m0 0 3-3m-3 3-3-3" />
    </svg>
  );
}

/** Значок фильтра — воронка. */
function FilterIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 5h16l-6.2 7.4V19l-3.6-2v-4.6z" />
    </svg>
  );
}

/** Значок избранного. */
export function StarIcon({ filled = false }: { filled?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill={filled ? "currentColor" : "none"}
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m12 3.6 2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8L3.5 9.8l5.9-.9z" />
    </svg>
  );
}

/** Значок проверки задержки — две встречные стрелки. */
export function PingIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M7 20V7m0 0L3.5 10.5M7 7l3.5 3.5" />
      <path d="M17 4v13m0 0 3.5-3.5M17 17l-3.5-3.5" />
    </svg>
  );
}

/** Значок обновления. */
export function RefreshIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 12a8 8 0 1 1-2.6-5.9" />
      <path d="M20 4v4.5h-4.5" />
    </svg>
  );
}

/** Стрелка порядка. */
export function ArrowIcon({ direction }: { direction: "up" | "down" }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {direction === "up" ? <path d="M12 19V5m0 0-5 5m5-5 5 5" /> : <path d="M12 5v14m0 0 5-5m-5 5-5-5" />}
    </svg>
  );
}

/** Троеточие для меню. */
export function DotsIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <circle cx="5.5" cy="12" r="1.7" />
      <circle cx="12" cy="12" r="1.7" />
      <circle cx="18.5" cy="12" r="1.7" />
    </svg>
  );
}

export interface SignalSelectOption {
  value: string;
  label: string;
}

/**
 * Выпадающий список в оформлении приложения.
 *
 * Системный `<select>` рисует своё окно средствами ОС — оно не поддаётся
 * стилям и выбивалось из интерфейса, поэтому список собран вручную и
 * позиционируется фиксированно, чтобы его не обрезал скролл рельса.
 */
export function SignalSelect({
  icon,
  title,
  value,
  options,
  onChange,
  className = "",
}: {
  icon: ReactNode;
  title: string;
  value: string;
  options: SignalSelectOption[];
  onChange: (value: string) => void;
  className?: string;
}) {
  const [anchor, setAnchor] = useState<{ top: number; left: number; width: number } | null>(null);
  const current = options.find((option) => option.value === value) ?? options[0];

  useEffect(() => {
    if (!anchor) return;
    const close = () => setAnchor(null);
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);
    return () => {
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [anchor]);

  return (
    <>
      <button
        type="button"
        className={`signal-select ${className}`.trim()}
        title={title}
        aria-label={title}
        aria-haspopup="listbox"
        aria-expanded={anchor != null}
        onClick={(event) => {
          if (anchor) {
            setAnchor(null);
            return;
          }
          const rect = event.currentTarget.getBoundingClientRect();
          setAnchor({ top: rect.bottom + 6, left: rect.left, width: rect.width });
        }}
      >
        <span className="signal-select-icon" aria-hidden="true">
          {icon}
        </span>
        <span className="signal-select-value">{current?.label ?? ""}</span>
      </button>
      {anchor && (
        <>
          <span className="signal-menu-scrim" onClick={() => setAnchor(null)} />
          <span
            className="signal-menu signal-menu--floating signal-select-list"
            role="listbox"
            style={{ top: anchor.top, left: anchor.left, minWidth: Math.max(anchor.width, 168) }}
          >
            {options.map((option) => (
              <button
                type="button"
                key={option.value}
                role="option"
                aria-selected={option.value === value}
                className={option.value === value ? "is-selected" : ""}
                onClick={() => {
                  setAnchor(null);
                  onChange(option.value);
                }}
              >
                {option.label}
              </button>
            ))}
          </span>
        </>
      )}
    </>
  );
}
