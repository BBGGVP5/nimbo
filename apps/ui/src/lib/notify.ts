import { useEffect, useState } from "react";

export type NotificationTone = "info" | "success" | "warning" | "error";

export type AppNotification = {
  id: string;
  tone: NotificationTone;
  message: string;
  createdAt: number;
};

const EVENT_NAME = "nimbo:notify";
const HISTORY_EVENT = "nimbo:notify-history";
const HISTORY_KEY = "nimbo.notifications.history";
const SEEN_KEY = "nimbo.notifications.lastSeen";
const HISTORY_LIMIT = 100;

function loadHistory(): AppNotification[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(HISTORY_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as AppNotification[];
    return Array.isArray(parsed) ? parsed.filter((item) => item && typeof item.message === "string") : [];
  } catch {
    return [];
  }
}

let history: AppNotification[] = loadHistory();

function persistHistory() {
  if (typeof window !== "undefined") {
    try {
      window.localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
    } catch {
      /* ignore quota/serialization errors */
    }
    window.dispatchEvent(new Event(HISTORY_EVENT));
  }
}

export function notify(message: string, tone: NotificationTone = "info", addToHistory = true): void {
  const notification: AppNotification = {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    tone,
    message,
    createdAt: Date.now(),
  };
  if (addToHistory) {
    history = [notification, ...history].slice(0, HISTORY_LIMIT);
    persistHistory();
  }
  window.dispatchEvent(
    new CustomEvent<AppNotification>(EVENT_NAME, {
      detail: notification,
    }),
  );
}

export function notifyInfo(message: string): void {
  notify(message, "info");
}

export function notifySuccess(message: string): void {
  notify(message, "success");
}

export function notifyWarning(message: string): void {
  notify(message, "warning");
}

export function notifyError(message: string): void {
  notify(message, "error");
}

export function subscribeNotifications(listener: (notification: AppNotification) => void): () => void {
  const handler = (event: Event) => {
    listener((event as CustomEvent<AppNotification>).detail);
  };
  window.addEventListener(EVENT_NAME, handler);
  return () => window.removeEventListener(EVENT_NAME, handler);
}

// ── Notification history ──────────────────────────────────────

export function getNotificationHistory(): AppNotification[] {
  return history;
}

export function subscribeNotificationHistory(listener: () => void): () => void {
  window.addEventListener(HISTORY_EVENT, listener);
  return () => window.removeEventListener(HISTORY_EVENT, listener);
}

export function clearNotificationHistory(): void {
  history = [];
  persistHistory();
}

export function removeNotification(id: string): void {
  history = history.filter((item) => item.id !== id);
  persistHistory();
}

function readLastSeen(): number {
  if (typeof window === "undefined") return 0;
  try {
    const raw = window.localStorage.getItem(SEEN_KEY);
    const parsed = raw ? Number.parseInt(raw, 10) : 0;
    return Number.isFinite(parsed) ? parsed : 0;
  } catch {
    return 0;
  }
}

export function getLastSeen(): number {
  return readLastSeen();
}

export function getUnreadCount(): number {
  const lastSeen = readLastSeen();
  return history.reduce((count, item) => (item.createdAt > lastSeen ? count + 1 : count), 0);
}

export function markNotificationsSeen(): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(SEEN_KEY, String(Date.now()));
  } catch {
    /* ignore */
  }
  window.dispatchEvent(new Event(HISTORY_EVENT));
}

export function useNotificationHistory(): { items: AppNotification[]; unread: number } {
  const [items, setItems] = useState<AppNotification[]>(() => getNotificationHistory());
  const [unread, setUnread] = useState<number>(() => getUnreadCount());

  useEffect(() => {
    const update = () => {
      setItems(getNotificationHistory());
      setUnread(getUnreadCount());
    };
    update();
    return subscribeNotificationHistory(update);
  }, []);

  return { items, unread };
}
