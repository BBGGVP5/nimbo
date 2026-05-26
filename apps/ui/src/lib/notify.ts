export type NotificationTone = "info" | "success" | "warning" | "error";

export type AppNotification = {
  id: string;
  tone: NotificationTone;
  message: string;
};

const EVENT_NAME = "nimbo:notify";

export function notify(message: string, tone: NotificationTone = "info"): void {
  window.dispatchEvent(
    new CustomEvent<AppNotification>(EVENT_NAME, {
      detail: {
        id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        tone,
        message,
      },
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
