import { useEffect, useMemo, useRef, useState } from "react";
import { fillTemplate, useMessages } from "../lib/i18n";
import {
  type AppNotification,
  type NotificationTone,
  clearNotificationHistory,
  getLastSeen,
  markNotificationsSeen,
  notify,
  removeNotification,
  useNotificationHistory,
} from "../lib/notify";

type NotificationFilter = "all" | NotificationTone;
type NotificationGroup = {
  key: "today" | "yesterday" | "earlier";
  items: AppNotification[];
};

export function Notifications() {
  const m = useMessages();
  const { items } = useNotificationHistory();
  const [filter, setFilter] = useState<NotificationFilter>("all");
  // Snapshot the timestamp before marking the page as seen so new items stay highlighted.
  const seenThreshold = useRef(getLastSeen());

  useEffect(() => {
    markNotificationsSeen();
  }, []);

  const toneCounts = useMemo(
    () =>
      items.reduce<Record<NotificationTone, number>>(
        (counts, item) => {
          counts[item.tone] += 1;
          return counts;
        },
        { info: 0, success: 0, warning: 0, error: 0 },
      ),
    [items],
  );

  const filteredItems = useMemo(
    () => (filter === "all" ? items : items.filter((item) => item.tone === filter)),
    [filter, items],
  );
  const groups = useMemo(() => groupNotifications(filteredItems), [filteredItems]);
  const activityCount = toneCounts.info + toneCounts.warning;
  const latestItem = items.reduce<AppNotification | undefined>(
    (latest, item) => (!latest || item.createdAt > latest.createdAt ? item : latest),
    undefined,
  );

  const filters: Array<{ key: NotificationFilter; label: string; count: number }> = [
    { key: "all", label: m.notifications.filterAll, count: items.length },
    { key: "error", label: m.notifications.filterErrors, count: toneCounts.error },
    { key: "info", label: m.notifications.filterUpdates, count: toneCounts.info },
    { key: "success", label: m.notifications.filterCompleted, count: toneCounts.success },
    { key: "warning", label: m.notifications.filterActivity, count: toneCounts.warning },
  ];

  const onClearAll = () => {
    clearNotificationHistory();
    setFilter("all");
    notify(m.notifications.cleared, "info", false);
  };

  return (
    <div className="page-view notification-page">
      <header className="notification-page-header">
        <div className="notification-page-heading">
          <span className="notification-page-heading-icon" aria-hidden="true">
            <BellIcon />
          </span>
          <div>
            <h1 className="page-title">{m.notifications.title}</h1>
            <p className="page-subtitle">
              {items.length > 0
                ? formatHistoryCount(items.length, m)
                : m.notifications.subtitle}
            </p>
          </div>
        </div>
        {items.length > 0 && (
          <button
            type="button"
            onClick={onClearAll}
            className="notification-history-clear"
            title={m.notifications.clearAll}
            aria-label={m.notifications.clearAll}
          >
            <TrashIcon />
          </button>
        )}
      </header>

      <section className="notification-overview" aria-labelledby="notification-overview-title">
        <div className="notification-overview-main">
          <div className="notification-overview-count" aria-label={formatHistoryCount(items.length, m)}>
            {items.length}
          </div>
          <div className="notification-overview-copy">
            <div className="notification-overview-eyebrow">{m.notifications.centerLabel}</div>
            <h2 id="notification-overview-title">
              {items.length > 0 ? m.notifications.centerTitle : m.notifications.centerEmptyTitle}
            </h2>
            <p>
              {latestItem
                ? fillTemplate(m.notifications.latest, {
                    time: relativeTime(latestItem.createdAt, m),
                  })
                : m.notifications.centerEmptyHint}
            </p>
          </div>
        </div>

        <div className="notification-overview-progress" aria-hidden="true">
          {items.length === 0 ? (
            <span className="notification-progress-empty" />
          ) : (
            <>
              {toneCounts.success > 0 && (
                <span data-tone="success" style={{ flexGrow: toneCounts.success }} />
              )}
              {toneCounts.error > 0 && (
                <span data-tone="error" style={{ flexGrow: toneCounts.error }} />
              )}
              {activityCount > 0 && (
                <span data-tone="activity" style={{ flexGrow: activityCount }} />
              )}
            </>
          )}
        </div>

        <div className="notification-overview-stats">
          <OverviewStat tone="success" value={toneCounts.success} label={m.notifications.statReady} />
          <OverviewStat tone="error" value={toneCounts.error} label={m.notifications.statErrors} />
          <OverviewStat tone="activity" value={activityCount} label={m.notifications.statActivity} />
        </div>
      </section>

      <section className="notification-filters" aria-labelledby="notification-filters-title">
        <div className="notification-section-label" id="notification-filters-title">
          <FilterIcon />
          <span>{m.notifications.filters}</span>
        </div>
        <div className="notification-filter-scroll">
          {filters.map((option) => (
            <button
              key={option.key}
              type="button"
              className="notification-filter-chip"
              data-tone={option.key}
              data-active={filter === option.key ? "true" : "false"}
              aria-pressed={filter === option.key}
              onClick={() => setFilter(option.key)}
            >
              <span className="notification-filter-dot" aria-hidden="true" />
              <span>{option.label}</span>
              <span className="notification-filter-count">{option.count}</span>
            </button>
          ))}
        </div>
      </section>

      {items.length === 0 ? (
        <EmptyState title={m.notifications.empty} hint={m.notifications.emptyHint} />
      ) : filteredItems.length === 0 ? (
        <EmptyState title={m.notifications.noMatches} hint={m.notifications.noMatchesHint} />
      ) : (
        <div className="notification-history-list">
          {groups.map((group) => (
            <section className="notification-history-group" key={group.key}>
              <div className="notification-history-group-title">
                <span>{groupLabel(group.key, m)}</span>
                <span className="notification-history-group-line" />
                <span>{group.items.length}</span>
              </div>
              <div className="notification-history-group-items">
                {group.items.map((item) => (
                  <NotificationRow
                    key={item.id}
                    item={item}
                    unread={item.createdAt > seenThreshold.current}
                    removeLabel={m.notifications.remove}
                    toneLabel={toneLabel(item.tone, m)}
                    timeLabel={relativeTime(item.createdAt, m)}
                    onRemove={() => removeNotification(item.id)}
                  />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  );
}

function OverviewStat({
  tone,
  value,
  label,
}: {
  tone: "success" | "error" | "activity";
  value: number;
  label: string;
}) {
  return (
    <div className="notification-overview-stat" data-tone={tone}>
      <span className="notification-overview-stat-dot" aria-hidden="true" />
      <div>
        <strong>{value}</strong>
        <span>{label}</span>
      </div>
    </div>
  );
}

function EmptyState({ title, hint }: { title: string; hint: string }) {
  return (
    <div className="notification-history-empty">
      <span className="notification-history-empty-icon">
        <BellIcon />
      </span>
      <div className="notification-history-empty-title">{title}</div>
      <div className="notification-history-empty-hint">{hint}</div>
    </div>
  );
}

function NotificationRow({
  item,
  unread,
  timeLabel,
  toneLabel,
  removeLabel,
  onRemove,
}: {
  item: AppNotification;
  unread: boolean;
  timeLabel: string;
  toneLabel: string;
  removeLabel: string;
  onRemove: () => void;
}) {
  return (
    <article
      className={["notification-history-item", unread ? "notification-history-unread" : ""].join(" ")}
      data-tone={item.tone}
    >
      <span className="notification-history-icon">
        <ToneIcon tone={item.tone} />
      </span>
      <div className="notification-history-body">
        <div className="notification-history-meta">
          <span className="notification-history-tone">{toneLabel}</span>
          <time className="notification-history-time" dateTime={new Date(item.createdAt).toISOString()}>
            {timeLabel}
          </time>
        </div>
        <div className="notification-history-message">{item.message}</div>
      </div>
      <button
        type="button"
        onClick={onRemove}
        className="notification-history-delete"
        title={removeLabel}
        aria-label={removeLabel}
      >
        <TrashIcon />
      </button>
    </article>
  );
}

function groupNotifications(items: AppNotification[]): NotificationGroup[] {
  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const yesterdayStart = todayStart - 24 * 60 * 60 * 1000;
  const groups = new Map<NotificationGroup["key"], AppNotification[]>([
    ["today", []],
    ["yesterday", []],
    ["earlier", []],
  ]);

  for (const item of items) {
    const key = item.createdAt >= todayStart
      ? "today"
      : item.createdAt >= yesterdayStart
        ? "yesterday"
        : "earlier";
    groups.get(key)?.push(item);
  }

  return Array.from(groups, ([key, groupedItems]) => ({ key, items: groupedItems })).filter(
    (group) => group.items.length > 0,
  );
}

function groupLabel(key: NotificationGroup["key"], m: ReturnType<typeof useMessages>): string {
  if (key === "today") return m.notifications.today;
  if (key === "yesterday") return m.notifications.yesterday;
  return m.notifications.earlier;
}

function toneLabel(tone: NotificationTone, m: ReturnType<typeof useMessages>): string {
  if (tone === "success") return m.notifications.toneSuccess;
  if (tone === "error") return m.notifications.toneError;
  if (tone === "warning") return m.notifications.toneWarning;
  return m.notifications.toneInfo;
}

function formatHistoryCount(count: number, m: ReturnType<typeof useMessages>): string {
  const category = new Intl.PluralRules(m.common.locale).select(count);
  const template = category === "one"
    ? m.notifications.historyCountOne
    : category === "few"
      ? m.notifications.historyCountFew
      : m.notifications.historyCountMany;
  return fillTemplate(template, { count });
}

function relativeTime(createdAt: number, m: ReturnType<typeof useMessages>): string {
  const diffMs = Math.max(0, Date.now() - createdAt);
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return m.notifications.justNow;
  if (minutes < 60) return fillTemplate(m.notifications.minutesAgo, { count: minutes });
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return fillTemplate(m.notifications.hoursAgo, { count: hours });
  const days = Math.floor(hours / 24);
  if (days <= 7) return fillTemplate(m.notifications.daysAgo, { count: days });
  return new Intl.DateTimeFormat(m.common.locale, {
    day: "2-digit",
    month: "2-digit",
    year: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(createdAt));
}

function ToneIcon({ tone }: { tone: NotificationTone }) {
  const common = {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 2,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
  };
  switch (tone) {
    case "error":
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="9" />
          <path d="M9 9l6 6M15 9l-6 6" />
        </svg>
      );
    case "warning":
      return (
        <svg {...common}>
          <path d="M12 3 2.5 20h19L12 3Z" />
          <path d="M12 10v5" />
          <path d="M12 18.5v.01" />
        </svg>
      );
    case "success":
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="9" />
          <path d="m8 12 3 3 5-6" />
        </svg>
      );
    case "info":
    default:
      return (
        <svg {...common}>
          <path d="M20 11a8 8 0 1 1-2.34-5.66" />
          <path d="M20 4v7h-7" />
        </svg>
      );
  }
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 6h18" />
      <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
      <path d="M19 6 18 20a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
      <path d="M10 11v6M14 11v6" />
    </svg>
  );
}

function BellIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  );
}

function FilterIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M3.4 5.1A1.5 1.5 0 0 1 4.7 4h14.6a1.5 1.5 0 0 1 1.16 2.45L15 13.1V19a1 1 0 0 1-.55.9l-4 2A1 1 0 0 1 9 21v-7.9L3.54 6.45a1.5 1.5 0 0 1-.14-1.35Z" />
    </svg>
  );
}
