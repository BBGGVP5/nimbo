import type { ReactNode } from "react";
import { useMessages } from "../lib/i18n";

export function useSecondaryCopy() {
  const ru = useMessages().common.locale.startsWith("ru");
  return ru ? {
    workspace: "Инструменты Nimbo", loading: "Загружаем данные…", retry: "Повторить", error: "Не удалось получить данные",
    noMatches: "Ничего не найдено", resetFilters: "Сбросить фильтры", unavailable: "Измерений пока нет",
    appHint: "Выберите приложения и сайты, для которых нужен отдельный маршрут.", catalog: "Приложения и сайты", tools: "Запуск и интеграция",
    selected: "Выбрано", available: "В каталоге", inherited: "Из подписок", local: "Локальные правила",
    liveHint: "Текущие соединения и правила активного профиля.", offline: "Нет активного подключения", offlineHint: "Подключитесь к VPN, чтобы увидеть реальные соединения.",
    measurementHint: "График появится после получения измерений от ядра.", samples: "Последние измерения · B/s", session: "Текущая сессия", month: "Текущий месяц", allTime: "За всё время",
    history: "История сессий", resetHint: "Сброс затронет накопленные счётчики трафика.", clear: "Очистить", confirmClear: "Очистить историю?",
    clearHint: "Это действие удалит сохранённые записи. Настройки подключения останутся без изменений.",
  } : {
    workspace: "Nimbo tools", loading: "Loading data…", retry: "Retry", error: "Unable to load data",
    noMatches: "No matches", resetFilters: "Reset filters", unavailable: "No measurements yet",
    appHint: "Choose applications and sites that need a separate route.", catalog: "Applications and sites", tools: "Launch and integration",
    selected: "Selected", available: "In catalog", inherited: "From subscriptions", local: "Local rules",
    liveHint: "Current connections and rules for the active profile.", offline: "Not connected", offlineHint: "Connect to the VPN to see actual connections.",
    measurementHint: "The chart appears once the core reports measurements.", samples: "Latest measurements · B/s", session: "Current session", month: "This month", allTime: "All time",
    history: "Session history", resetHint: "This resets accumulated traffic counters.", clear: "Clear", confirmClear: "Clear history?",
    clearHint: "Saved entries will be removed. Connection settings will not change.",
  };
}

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  const copy = useSecondaryCopy();
  return <header className="secondary-header"><div><span className="secondary-eyebrow">{copy.workspace}</span><h1 className="page-title">{title}</h1>{description && <p>{description}</p>}</div>{actions && <div className="secondary-actions">{actions}</div>}</header>;
}

export function StatePanel({ title, detail, busy = false, error = false, action }: { title: string; detail?: string; busy?: boolean; error?: boolean; action?: ReactNode }) {
  return <div className="secondary-state" data-error={error || undefined} role={error ? "alert" : "status"} aria-busy={busy}>
    <span className="secondary-state-mark" aria-hidden="true">{error ? "!" : busy ? "…" : "—"}</span><h3>{title}</h3>{detail && <p>{detail}</p>}{action}
  </div>;
}

export function Metric({ label, value, note }: { label: string; value: ReactNode; note?: string }) {
  return <div className="secondary-metric"><span>{label}</span><strong>{value}</strong>{note && <small>{note}</small>}</div>;
}
