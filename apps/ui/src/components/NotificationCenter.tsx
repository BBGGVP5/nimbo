import { useEffect, useState } from "react";
import { type AppNotification, subscribeNotifications } from "../lib/notify";

export function NotificationCenter() {
  const [items, setItems] = useState<AppNotification[]>([]);

  useEffect(() => {
    return subscribeNotifications((notification) => {
      setItems((current) => [...current.slice(-3), notification]);
      window.setTimeout(() => {
        setItems((current) => current.filter((item) => item.id !== notification.id));
      }, 3600);
    });
  }, []);

  if (!items.length) return null;

  return (
    <div className="pointer-events-none fixed right-5 top-5 z-[80] flex w-[min(360px,calc(100vw-40px))] flex-col gap-2">
      {items.map((item) => (
        <div
          key={item.id}
          className={[
            "pointer-events-auto rounded-xl border px-4 py-3 text-sm font-semibold shadow-[0_18px_42px_rgba(0,0,0,0.32)] backdrop-blur-xl",
            item.tone === "error"
              ? "border-[rgba(244,67,54,0.34)] bg-[rgba(45,24,31,0.92)] text-[#ff8a8a]"
              : "border-[var(--color-accent)] bg-[var(--color-accent-panel)] text-[var(--color-accent-bright)]",
          ].join(" ")}
        >
          {item.message}
        </div>
      ))}
    </div>
  );
}
