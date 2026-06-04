import { useCallback, useEffect, useRef, useState } from "react";
import { type AppNotification, type NotificationTone, subscribeNotifications } from "../lib/notify";
import { useMessages } from "../lib/i18n";

type DisplayState = "shown" | "leaving";

type DisplayedNotification = AppNotification & {
  state: DisplayState;
};

const SHOW_DURATION_MS = 3600;
const EXIT_DURATION_MS = 260;

export function NotificationCenter() {
  const m = useMessages();
  const [items, setItems] = useState<DisplayedNotification[]>([]);
  const exitTimers = useRef(new Map<string, ReturnType<typeof setTimeout>>());
  const hideTimers = useRef(new Map<string, ReturnType<typeof setTimeout>>());

  const dismiss = useCallback((id: string) => {
    // Cancel the auto-hide timer if it is still pending, then play the exit
    // animation and remove the toast once it finishes.
    const hideTimer = hideTimers.current.get(id);
    if (hideTimer) {
      clearTimeout(hideTimer);
      hideTimers.current.delete(id);
    }
    if (exitTimers.current.has(id)) return;
    setItems((current) =>
      current.map((item) => (item.id === id ? { ...item, state: "leaving" } : item)),
    );
    const exitTimer = setTimeout(() => {
      exitTimers.current.delete(id);
      setItems((current) => current.filter((item) => item.id !== id));
    }, EXIT_DURATION_MS);
    exitTimers.current.set(id, exitTimer);
  }, []);

  useEffect(() => {
    const exitTimersSnapshot = exitTimers.current;
    const hideTimersSnapshot = hideTimers.current;
    const unsubscribe = subscribeNotifications((notification) => {
      setItems((current) => [...current.slice(-3), { ...notification, state: "shown" }]);

      // After the visible period: switch to "leaving" so the exit animation plays,
      // then remove from the DOM once the animation has finished.
      const hideTimer = setTimeout(() => {
        hideTimersSnapshot.delete(notification.id);
        setItems((current) =>
          current.map((item) =>
            item.id === notification.id ? { ...item, state: "leaving" } : item,
          ),
        );
        const exitTimer = setTimeout(() => {
          exitTimersSnapshot.delete(notification.id);
          setItems((current) => current.filter((item) => item.id !== notification.id));
        }, EXIT_DURATION_MS);
        exitTimersSnapshot.set(notification.id, exitTimer);
      }, SHOW_DURATION_MS);
      hideTimersSnapshot.set(notification.id, hideTimer);
    });

    return () => {
      unsubscribe();
      for (const timer of hideTimersSnapshot.values()) clearTimeout(timer);
      for (const timer of exitTimersSnapshot.values()) clearTimeout(timer);
      hideTimersSnapshot.clear();
      exitTimersSnapshot.clear();
    };
  }, []);

  if (!items.length) return null;

  return (
    <div className="pointer-events-none fixed right-4 top-4 z-[80] flex w-[min(340px,calc(100vw-32px))] flex-col gap-2">
      {items.map((item) => (
        <div
          key={item.id}
          data-state={item.state}
          data-tone={item.tone}
          className="notification-toast pointer-events-auto flex items-start gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-3 text-[13px] font-medium tracking-[-0.01em] text-[var(--color-text)] shadow-[0_12px_30px_rgba(0,0,0,0.35)]"
        >
          <span className={["grid h-5 w-5 shrink-0 place-items-center", toneIconColor(item.tone)].join(" ")}>
            <NotificationIcon tone={item.tone} />
          </span>
          <span className="flex-1 leading-5">{item.message}</span>
          <button
            type="button"
            onClick={() => dismiss(item.id)}
            className="notification-toast-close"
            title={m.common.close}
            aria-label={m.common.close}
          >
            <CloseIcon />
          </button>
        </div>
      ))}
    </div>
  );
}

function toneIconColor(tone: NotificationTone): string {
  switch (tone) {
    case "error":
      return "text-[#ff7a7a]";
    case "warning":
      return "text-[#f5c040]";
    case "success":
      return "text-[#5dd9a1]";
    case "info":
    default:
      return "text-[var(--color-accent-bright)]";
  }
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M6 6l12 12M18 6 6 18" />
    </svg>
  );
}

function NotificationIcon({ tone }: { tone: NotificationTone }) {
  const stroke = "currentColor";
  const common = {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke,
    strokeWidth: 2,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
    className: "h-5 w-5",
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
