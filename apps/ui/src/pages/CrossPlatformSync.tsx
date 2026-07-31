import { useCallback, useEffect, useMemo, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import {
  api,
  isTauriRuntime,
  type CrossSyncCategories,
  type CrossSyncSession,
} from "../lib/api";
import { useAppStore } from "../store";

const CATEGORY_KEY = "nimbo.crossSync.categories.v1";
const LAST_SYNC_KEY = "nimbo.crossSync.last.v1";
const DEFAULT_CATEGORIES: CrossSyncCategories = {
  subscriptions: true,
  appearance: true,
  connection: true,
  automation: true,
};

type LastSync = { at: number; device: string };

export function CrossPlatformSync() {
  const subscriptions = useAppStore((state) => state.subscriptions);
  const hydrate = useAppStore((state) => state.hydrate);
  const refreshSubscription = useAppStore((state) => state.refreshSubscription);
  const [session, setSession] = useState<CrossSyncSession | null>(null);
  const [categories, setCategories] = useState<CrossSyncCategories>(loadCategories);
  const [lastSync, setLastSync] = useState<LastSync | null>(() => readJson(LAST_SYNC_KEY, null));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());

  const startSession = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      setSession(await api.crossSyncStart());
    } catch (cause) {
      setError(errorText(cause));
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    void startSession();
    return () => {
      if (isTauriRuntime()) void api.crossSyncCancel().catch(() => undefined);
    };
  }, [startSession]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!isTauriRuntime()) return;
    const terminal = new Set(["idle", "expired", "completed", "cancelled", "rejected"]);
    if (session && terminal.has(session.state)) return;
    const timer = window.setInterval(() => {
      void api.crossSyncStatus().then(setSession).catch(() => undefined);
    }, 800);
    return () => window.clearInterval(timer);
  }, [session?.state]);

  useEffect(() => {
    if (!session?.expires_at_ms || session.state !== "showing_qr") return;
    const delay = Math.max(250, session.expires_at_ms - Date.now() + 250);
    const timer = window.setTimeout(() => void startSession(), delay);
    return () => window.clearTimeout(timer);
  }, [session?.expires_at_ms, session?.state, startSession]);

  useEffect(() => {
    if (session?.state !== "completed") return;
    const value = { at: Date.now(), device: session.remote_device || "Android" };
    setLastSync(value);
    writeJson(LAST_SYNC_KEY, value);
  }, [session?.state, session?.remote_device]);

  const secondsLeft = session?.expires_at_ms
    ? Math.max(0, Math.ceil((session.expires_at_ms - now) / 1000))
    : 0;
  const selectedCount = Object.values(categories).filter(Boolean).length;
  const categoriesLocked = Boolean(session && [
    "paired",
    "awaiting_import_confirmation",
    "export_authorized",
    "completed",
  ].includes(session.state));
  const remote = session?.remote_inventory;
  const remoteInfo = session?.remote_device_info;
  const remoteSubscriptions = session?.remote_subscriptions ?? [];
  const recommended = useMemo(() => {
    if (!remote) return null;
    if (subscriptions.length === 0 && remote.subscriptions > 0) return "android_to_desktop";
    if (remote.subscriptions === 0 && subscriptions.length > 0) return "desktop_to_android";
    return null;
  }, [remote, subscriptions.length]);

  const updateCategory = (key: keyof CrossSyncCategories, value: boolean) => {
    const next = { ...categories, [key]: value };
    setCategories(next);
    writeJson(CATEGORY_KEY, next);
  };

  const approve = async () => runAction(async () => {
    setSession(await api.crossSyncApprove(categories));
  });
  const reject = async () => runAction(async () => {
    setSession(await api.crossSyncReject());
  });
  const acceptImport = async () => runAction(async () => {
    const completed = await api.crossSyncAcceptImport();
    setSession(completed);
    await hydrate();
    for (const url of completed.result?.added_subscriptions ?? []) {
      await refreshSubscription(url).catch(() => undefined);
    }
    await hydrate();
  });

  async function runAction(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (cause) {
      setError(errorText(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page-view cross-sync-page">
      <header className="cross-sync-header">
        <div>
          <h1 className="page-title">Синхронизация</h1>
          <p className="page-subtitle">Подписки и настройки между Nimbo Desktop и Android</p>
        </div>
        <button className="cross-sync-secondary" onClick={() => void startSession()} disabled={busy}>
          Новый QR
        </button>
      </header>

      <div className="cross-sync-layout">
        <section className="cross-sync-card cross-sync-pair-card">
          <div className="cross-sync-card-heading">
            <span className="cross-sync-step">1</span>
            <div>
              <h2>Подключите телефон</h2>
              <p>В Android Nimbo откройте «Настройки → Синхронизация» и отсканируйте код.</p>
            </div>
          </div>

          {session?.qr_payload && !session.remote_device && (
            <div className="cross-sync-qr-wrap">
              <div className="cross-sync-qr">
                <QRCodeSVG value={session.qr_payload} size={256} level="M" marginSize={2} />
              </div>
              <div className="cross-sync-expiry">
                QR изменится через <strong>{secondsLeft} сек.</strong>
              </div>
            </div>
          )}

          {!session?.qr_payload && !session?.remote_device && !busy && (
            <button className="cross-sync-primary" onClick={() => void startSession()}>
              Создать одноразовый QR
            </button>
          )}
          {busy && <div className="cross-sync-loading">Подготавливаем защищённый сеанс…</div>}

          {session?.remote_device && (
            <div className="cross-sync-device-passport">
              <div className="cross-sync-device-topline">
                <div className="cross-sync-device-icon">A</div>
                <div className="cross-sync-device-identity">
                  <span className="cross-sync-device-eyebrow">Обнаружено устройство</span>
                  <strong>{remoteInfo?.name || session.remote_device}</strong>
                  <span>{[remoteInfo?.os_name || "Android", remoteInfo?.os_version].filter(Boolean).join(" ")}</span>
                </div>
                <span className="cross-sync-online">В сети</span>
              </div>
              <div className="cross-sync-device-specs">
                <span>Nimbo {remoteInfo?.app_version || "—"}</span>
                <span>{remoteInfo?.architecture || "архитектура не указана"}</span>
                <span>{remote?.subscriptions ?? 0} подписок</span>
              </div>
              <SubscriptionPreview names={remoteSubscriptions} total={remote?.subscriptions ?? 0} />
            </div>
          )}

          {session?.comparison_code && (
            <div className="cross-sync-code">
              <span>Код проверки</span>
              <strong>{session.comparison_code}</strong>
              <small>На телефоне должен быть такой же код</small>
            </div>
          )}
        </section>

        <section className="cross-sync-card">
          <div className="cross-sync-card-heading">
            <span className="cross-sync-step">2</span>
            <div>
              <h2>Что синхронизировать</h2>
              <p>Настройка сохраняется для следующих ручных синхронизаций.</p>
            </div>
          </div>
          <div className="cross-sync-options">
            <SyncOption title="Подписки" detail="Ссылки и названия; серверы заново проверяются" checked={categories.subscriptions} disabled={categoriesLocked} onChange={(v) => updateCategory("subscriptions", v)} />
            <SyncOption title="Оформление" detail="Тема, акцент, стекло, прозрачность и скругление" checked={categories.appearance} disabled={categoriesLocked} onChange={(v) => updateCategory("appearance", v)} />
            <SyncOption title="Подключение" detail="Kill Switch, TLS-фрагментация и график скорости" checked={categories.connection} disabled={categoriesLocked} onChange={(v) => updateCategory("connection", v)} />
            <SyncOption title="Автоматизация" detail="Обновления, ping при запуске, язык и интервалы" checked={categories.automation} disabled={categoriesLocked} onChange={(v) => updateCategory("automation", v)} />
          </div>
          <div className="cross-sync-excluded">
            Не переносятся разрешения VPN, списки приложений и пути к .exe, пароли локального SOCKS,
            логи, статистика и текущее подключение.
          </div>
        </section>
      </div>

      {session?.state === "awaiting_approval" && (
        <section className="cross-sync-card cross-sync-confirm">
          <div>
            <h2>Разрешить сопряжение с {session.remote_device}?</h2>
            <p>Телефон увидит только выбранные выше {selectedCount} категории. Сверьте шестизначный код.</p>
            {recommended && (
              <div className="cross-sync-recommendation">
                Рекомендуем: {recommended === "android_to_desktop" ? "перенести данные с телефона на пустой ПК" : "перенести данные с ПК на телефон"}.
              </div>
            )}
          </div>
          <div className="cross-sync-actions">
            <button className="cross-sync-secondary" onClick={() => void reject()} disabled={busy}>Отклонить</button>
            <button className="cross-sync-primary" onClick={() => void approve()} disabled={busy || selectedCount === 0}>Разрешить</button>
          </div>
        </section>
      )}

      {session?.state === "paired" && (
        <section className="cross-sync-card cross-sync-status-card">
          <div className="cross-sync-pulse" />
          <div><h2>Сопряжение подтверждено</h2><p>Выберите направление и категории на телефоне.</p></div>
        </section>
      )}

      {session?.state === "awaiting_import_confirmation" && (
        <section className="cross-sync-card cross-sync-confirm">
          <div>
            <h2>Импортировать данные с {session.remote_device}?</h2>
            <p>{remote?.subscriptions ?? 0} подписок. Существующие подписки не удаляются, совпадения объединяются.</p>
          </div>
          <div className="cross-sync-actions">
            <button className="cross-sync-secondary" onClick={() => void reject()} disabled={busy}>Отклонить</button>
            <button className="cross-sync-primary" onClick={() => void acceptImport()} disabled={busy}>Импортировать</button>
          </div>
        </section>
      )}

      {session?.state === "export_authorized" && (
        <section className="cross-sync-card cross-sync-status-card">
          <div className="cross-sync-pulse" />
          <div><h2>Передача на Android разрешена</h2><p>Подтвердите применение данных на телефоне.</p></div>
        </section>
      )}

      {session?.state === "completed" && (
        <section className="cross-sync-card cross-sync-success">
          <strong>Синхронизация завершена</strong>
          <span>Добавлено подписок: {session.result?.added_subscriptions.length ?? 0}</span>
          <button className="cross-sync-secondary" onClick={() => void startSession()}>Синхронизировать ещё раз</button>
        </section>
      )}

      {(error || session?.error) && <div className="cross-sync-error">{error || session?.error}</div>}

      <footer className="cross-sync-footer">
        <span>Шифрование AES‑256‑GCM · локальная сеть · одноразовый сеанс</span>
        <span>{lastSync ? `Последняя синхронизация: ${new Date(lastSync.at).toLocaleString()} · ${lastSync.device}` : "Синхронизаций ещё не было"}</span>
      </footer>
    </div>
  );
}

function SyncOption({ title, detail, checked, disabled, onChange }: { title: string; detail: string; checked: boolean; disabled?: boolean; onChange: (value: boolean) => void }) {
  return (
    <label className={`cross-sync-option${disabled ? " is-disabled" : ""}`}>
      <span><strong>{title}</strong><small>{detail}</small></span>
      <input type="checkbox" checked={checked} disabled={disabled} onChange={(event) => onChange(event.target.checked)} />
      <i aria-hidden="true" />
    </label>
  );
}

function SubscriptionPreview({ names, total }: { names: string[]; total: number }) {
  if (total === 0) {
    return <div className="cross-sync-subscriptions-empty">На устройстве пока нет подписок</div>;
  }
  const shown = names.slice(0, 6);
  const hidden = Math.max(0, total - shown.length);
  return (
    <div className="cross-sync-subscriptions-preview">
      <span className="cross-sync-subscriptions-label">Будут синхронизированы</span>
      <div className="cross-sync-subscription-chips">
        {shown.map((name, index) => <span key={`${name}-${index}`}>{name}</span>)}
        {hidden > 0 && <span className="is-more">+{hidden} ещё</span>}
      </div>
    </div>
  );
}

function loadCategories(): CrossSyncCategories {
  return readJson<CrossSyncCategories>(CATEGORY_KEY, DEFAULT_CATEGORIES);
}

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function writeJson(key: string, value: unknown) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage can be disabled */ }
}

function errorText(value: unknown): string {
  return value instanceof Error ? value.message : String(value || "Неизвестная ошибка синхронизации");
}
