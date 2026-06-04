import { useEffect, useRef } from "react";
import { fillTemplate, useMessages } from "../lib/i18n";
import {
  type AppNotification,
  type NotificationTone,
  clearNotificationHistory,
  getLastSeen,
  markNotificationsSeen,
  notifyInfo,
  removeNotification,
  useNotificationHistory,
} from "../lib/notify";

export function Notifications() {
  const m = useMessages();
  const { items } = useNotificationHistory();
  // Snapshot the "last seen" timestamp once, before marking everything read,
  // so notifications that arrived since the previous visit stay highlighted.
  const seenThreshold = useRef(getLastSeen());

  useEffect(() => {
    markNotificationsSeen();
  }, []);

  const onClearAll = () => {
    clearNotificationHistory();
    notifyInfo(m.notifications.cleared);
  };

  return (
    <div className="page-view">
      <div className="mb-7 flex items-start justify-between gap-4 mobile-column">
        <div className="flex items-center gap-3">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[var(--color-accent-active-bg)] text-[var(--color-accent-bright)]">
            <BellIcon />
          </span>
          <div>
            <h1 className="page-title">{m.notifications.title}</h1>
            <p className="page-subtitle">
              {items.length > 0
                ? `${items.length} ${m.notifications.count}`
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
      </div>

      {items.length === 0 ? (
        <div className="notification-history-empty">
          <span className="notification-history-empty-icon">
            <BellOffIcon />
          </span>
          <div className="text-lg font-semibold text-[var(--color-text-dim)]">
            {m.notifications.empty}
          </div>
          <div className="max-w-md text-sm text-[var(--color-text-faint)]">
            {m.notifications.emptyHint}
          </div>
        </div>
      ) : (
        <div className="notification-history-list">
          {items.map((item) => (
            <NotificationRow
              key={item.id}
              item={item}
              unread={item.createdAt > seenThreshold.current}
              removeLabel={m.notifications.remove}
              timeLabel={relativeTime(item.createdAt, m)}
              onRemove={() => removeNotification(item.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function NotificationRow({
  item,
  unread,
  timeLabel,
  removeLabel,
  onRemove,
}: {
  item: AppNotification;
  unread: boolean;
  timeLabel: string;
  removeLabel: string;
  onRemove: () => void;
}) {
  return (
    <div
      className={["notification-history-item", unread ? "notification-history-unread" : ""].join(" ")}
      data-tone={item.tone}
    >
      {unread && <span className="notification-history-unread-dot" aria-hidden="true" />}
      <span className="notification-history-icon">
        <ToneIcon tone={item.tone} />
      </span>
      <div className="notification-history-body">
        <div className="notification-history-message">{item.message}</div>
        <div className="notification-history-time">{timeLabel}</div>
      </div>
      <button
        type="button"
        onClick={onRemove}
        className="notification-history-delete"
        title={removeLabel}
        aria-label={removeLabel}
      >
        <CloseIcon />
      </button>
    </div>
  );
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
          <circle cx="12" cy="12" r="9" />
          <path d="M12 11v6" />
          <path d="M12 7.5v.01" />
        </svg>
      );
  }
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M6 6l12 12M18 6 6 18" />
    </svg>
  );
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
    <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  );
}

function BellOffIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M8.7 3A6 6 0 0 1 18 8c0 3.5.8 5.7 1.6 7" />
      <path d="M6 8a6 6 0 0 0 .6 2.6" />
      <path d="M5 19h14" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      <path d="m2 2 20 20" />
    </svg>
  );
}
