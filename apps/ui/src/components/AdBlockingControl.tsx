import { useId, useRef, useState } from "react";
import { useMessages } from "../lib/i18n";
import { useAppStore } from "../store";
import "./ad-blocking-control.css";

/** Saved preference only: changing this control never reconfigures a running session. */
export function AdBlockingControl() {
  const ru = useMessages().common.locale.startsWith("ru");
  const t = (r: string, e: string) => ru ? r : e;
  const id = useId();
  const enabled = useAppStore(s => s.preferences.ad_blocking_enabled === true);
  const writing = useRef(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function toggle() {
    if (writing.current) return;
    writing.current = true;
    setSaving(true); setError(null);
    const state = useAppStore.getState();
    const requested = !state.preferences.ad_blocking_enabled;
    try {
      const saved = await state.setPreferences({ ...state.preferences, ad_blocking_enabled: requested });
      if (saved.ad_blocking_enabled !== requested) throw new Error(t("Приложение не сохранило настройку. Обновите Nimbo и повторите.", "The app did not save this setting. Update Nimbo and try again."));
    } catch (e) {
      setError(t("Не удалось сохранить настройку. ", "Could not save this setting. ") + String(e));
    } finally {
      writing.current = false; setSaving(false);
    }
  }

  return <section className="ad-blocking-control" aria-labelledby={`${id}-title`} aria-busy={saving}>
    <div className="ad-blocking-heading">
      <span className="ad-blocking-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M12 3 4 6v6c0 4 4 7 8 9 4-2 8-5 8-9V6Z"/><path d="m8 12 3 3 5-6"/></svg></span>
      <div><h2 id={`${id}-title`}>{t("Блокировка рекламы", "Ad blocking")}</h2><p className="ad-blocking-state" role="status">{saving ? t("Сохранение…", "Saving…") : enabled ? t("Включена для следующего подключения", "On for the next connection") : t("Выключена для следующего подключения", "Off for the next connection")}</p></div>
      <button type="button" className="ad-blocking-switch" role="switch" aria-checked={enabled} aria-label={t("Блокировка рекламы", "Ad blocking")} aria-describedby={`${id}-description`} disabled={saving} onClick={() => void toggle()}><span /></button>
    </div>
    <p id={`${id}-description`}>{t("Фильтрует рекламные домены. Не убирает всю рекламу.", "Filters ad domains. Does not remove all ads.")}</p>
    <details><summary>{t("Подробнее", "Details")}</summary>
      <p>{t("Mihomo требует режим rule. При включённой опции global и direct недоступны.", "Mihomo requires rule mode while this option is on; global and direct are unavailable.")}</p>
      <p>{t("Встроенная реклама, обход Nimbo и собственный зашифрованный DNS приложений могут остаться. Правила подписки и ваши правила сохраняются и при выключенной опции.", "In-app/video ads, bypass traffic and encrypted DNS may remain. Provider and custom rules are preserved, even when this option is off.")}</p>
    </details>
    {error && <p className="ad-blocking-error" role="alert">{error}</p>}
  </section>;
}
