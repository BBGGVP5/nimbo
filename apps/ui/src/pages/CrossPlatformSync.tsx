import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import {
  api,
  isTauriRuntime,
  type CrossSyncCategories,
  type CrossSyncDirection,
  type CrossSyncPairedDevice,
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

// States where the phone is mid-flow and the pairing panel must stay on screen
// even if the user tried to close it. "showing_qr" is deliberately excluded: it
// is the state we can cancel freely, and keeping it here would let a stale poll
// re-open the panel right after the user closed it.
const LIVE_PAIRING_STATES = [
  "awaiting_approval",
  "paired",
  "awaiting_import_confirmation",
  "export_authorized",
];

export function CrossPlatformSync() {
  const subscriptions = useAppStore((state) => state.subscriptions);
  const hydrate = useAppStore((state) => state.hydrate);
  const refreshSubscription = useAppStore((state) => state.refreshSubscription);
  const [session, setSession] = useState<CrossSyncSession | null>(null);
  const [devices, setDevices] = useState<CrossSyncPairedDevice[]>([]);
  const [categories, setCategories] = useState<CrossSyncCategories>(loadCategories);
  const [lastSync, setLastSync] = useState<LastSync | null>(() => readJson(LAST_SYNC_KEY, null));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());
  const [direction, setDirection] = useState<CrossSyncDirection>("desktop_to_android");
  const [expandedDevice, setExpandedDevice] = useState<string | null>(null);
  const [syncHint, setSyncHint] = useState<string | null>(null);
  const [pairingOpen, setPairingOpen] = useState(false);
  const [devicesLoaded, setDevicesLoaded] = useState(false);
  const autoOpenedPairing = useRef(false);

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

  const openPairing = useCallback(() => {
    setPairingOpen(true);
    setSyncHint(null);
    void startSession();
  }, [startSession]);

  const closePairing = useCallback(async () => {
    setPairingOpen(false);
    setError(null);
    if (!isTauriRuntime()) return;
    try {
      setSession(await api.crossSyncCancel());
    } catch {
      // The session was already gone; the panel is closed either way.
    }
  }, []);

  useEffect(() => {
    return () => {
      if (isTauriRuntime()) void api.crossSyncCancel().catch(() => undefined);
    };
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!isTauriRuntime()) return;
    const timer = window.setInterval(() => {
      void api.crossSyncStatus().then((status) => {
        setSession(status);
        setDevices(status.devices ?? []);
      }).catch(() => undefined);
    }, 800);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!isTauriRuntime()) {
      setDevicesLoaded(true);
      return;
    }
    void api.crossSyncListDevices()
      .then(setDevices)
      .catch(() => undefined)
      .finally(() => setDevicesLoaded(true));
  }, []);

  // Nothing to show on a fresh install, so open the pairing panel right away.
  // With devices already paired the list is the useful view: no QR until asked.
  useEffect(() => {
    if (!devicesLoaded || autoOpenedPairing.current) return;
    autoOpenedPairing.current = true;
    if (devices.length === 0) openPairing();
  }, [devicesLoaded, devices.length, openPairing]);

  useEffect(() => {
    if (!pairingOpen || !session?.expires_at_ms || session.state !== "showing_qr") return;
    const delay = Math.max(250, session.expires_at_ms - Date.now() + 250);
    const timer = window.setTimeout(() => void startSession(), delay);
    return () => window.clearTimeout(timer);
  }, [pairingOpen, session?.expires_at_ms, session?.state, startSession]);

  useEffect(() => {
    if (session?.state !== "completed") return;
    const value = { at: Date.now(), device: session.remote_device || "Android" };
    setLastSync(value);
    writeJson(LAST_SYNC_KEY, value);
    if (isTauriRuntime()) void api.crossSyncListDevices().then(setDevices).catch(() => undefined);
  }, [session?.state, session?.remote_device]);

  // A phone that is mid-handshake keeps the panel open regardless of the toggle,
  // otherwise the approval prompt would be unreachable.
  const pairingBusyWithPhone = Boolean(session && LIVE_PAIRING_STATES.includes(session.state));
  const showPairing = pairingOpen || pairingBusyWithPhone;

  const secondsLeft = session?.expires_at_ms
    ? Math.max(0, Math.ceil((session.expires_at_ms - now) / 1000))
    : 0;
  const timerProgress = Math.min(1, Math.max(0, secondsLeft / 60));
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
    setSession(await api.crossSyncApprove(categories, direction));
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

  const removeDevice = async (deviceId: string) => runAction(async () => {
    setDevices(await api.crossSyncRemoveDevice(deviceId));
  });

  const setAutoSync = async (deviceId: string, enabled: boolean) => runAction(async () => {
    setDevices(await api.crossSyncSetAutoSync(deviceId, enabled));
  });

  const setDeviceCategories = async (deviceId: string, categories: CrossSyncCategories) => runAction(async () => {
    setDevices(await api.crossSyncSetDeviceCategories(deviceId, categories));
  });

  const syncNow = async (deviceId: string) => runAction(async () => {
    setDevices(await api.crossSyncListDevices());
    setSyncHint(deviceId);
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
        {showPairing && (
          <button
            className="cross-sync-secondary"
            onClick={() => void closePairing()}
            disabled={busy || pairingBusyWithPhone}
            title={pairingBusyWithPhone ? "Дождитесь завершения сеанса с телефоном" : "Скрыть QR и вернуться к списку устройств"}
          >
            {devices.length > 0 ? "К списку устройств" : "Скрыть QR"}
          </button>
        )}
      </header>

      {!showPairing && (
        <section className="cross-sync-card cross-sync-overview">
          <div className="cross-sync-overview-main">
            <div className="cross-sync-overview-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 3 4 6.2v5.3c0 4.6 3.4 8.4 8 9.5 4.6-1.1 8-4.9 8-9.5V6.2L12 3Z" />
                <path d="m9 12 2.2 2.2L15.5 10" />
              </svg>
            </div>
            <div>
              <h2>
                {devices.length > 0
                  ? `${devices.length} ${deviceWord(devices.length)} на связи`
                  : "Устройства не подключены"}
              </h2>
              <p>
                {devices.length > 0
                  ? "Данные расходятся по локальной сети сами. QR нужен только для нового устройства."
                  : "Подключите телефон, чтобы переносить подписки и настройки между Nimbo Desktop и Android."}
              </p>
            </div>
          </div>
          <button className="cross-sync-primary" onClick={openPairing} disabled={busy}>
            {devices.length > 0 ? "Добавить ещё" : "Подключить телефон"}
          </button>
        </section>
      )}

      {showPairing && (
      <div className="cross-sync-layout">
        <section className="cross-sync-card cross-sync-pair-card">
          <div className="cross-sync-card-heading">
            <span className="cross-sync-step">1</span>
            <div>
              <h2>Подключите телефон</h2>
              <p>В Android Nimbo откройте «Настройки → Синхронизация» и отсканируйте код.</p>
            </div>
          </div>

          <div className={`cross-sync-link-visual${session?.remote_device ? " is-linked" : " is-waiting"}`} aria-hidden="true">
            <span className="cross-sync-link-device"><PlatformIcon platform="desktop" /></span>
            <span className="cross-sync-link-rail">
              <i /><i /><i />
            </span>
            <span className="cross-sync-link-device"><PlatformIcon platform="android" /></span>
          </div>

          {session?.qr_payload && !session.remote_device && (
            <div className="cross-sync-qr-wrap">
              <div className="cross-sync-qr-stage">
                <div className="cross-sync-qr">
                  <QRCodeSVG value={session.qr_payload} size={256} level="M" marginSize={2} />
                </div>
              </div>
              <div className={`cross-sync-expiry${secondsLeft <= 15 ? " is-urgent" : ""}`}>
                <div
                  className="cross-sync-timer"
                  role="timer"
                  aria-label={`QR изменится через ${secondsLeft} секунд`}
                >
                  <span className="cross-sync-timer-num">
                    <strong>{secondsLeft}</strong>
                    <span>сек</span>
                  </span>
                  <span className="cross-sync-timer-track">
                    <i style={{ width: `${timerProgress * 100}%` }} />
                  </span>
                </div>
                <span>QR обновится автоматически</span>
              </div>
            </div>
          )}

          {(!session || session?.state === "idle" || session?.state === "expired") && !busy && (
            <button className="cross-sync-primary" onClick={() => void startSession()}>
              Создать одноразовый QR
            </button>
          )}
          {busy && <div className="cross-sync-loading"><i />Подготавливаем защищённый сеанс…</div>}

          {session?.remote_device && (
            <div className="cross-sync-device-passport">
              <div className="cross-sync-device-topline">
                <div className="cross-sync-device-icon"><PlatformIcon platform="android" /></div>
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
      )}

      {devices.length > 0 && (
        <section className="cross-sync-card cross-sync-devices-card">
          <div className="cross-sync-card-heading">
            <span className="cross-sync-step">{showPairing ? "3" : devices.length}</span>
            <div>
              <h2>Устройства</h2>
              <p>Синхронизированные устройства и автосинхронизация по локальной сети.</p>
            </div>
          </div>
          <div className="cross-sync-devices">
            {devices.map((device) => {
              const open = expandedDevice === device.device_id;
              return (
                <div className={`cross-sync-device-row${open ? " is-open" : ""}`} key={device.device_id}>
                  <button
                    className="cross-sync-device-head"
                    onClick={() => setExpandedDevice(open ? null : device.device_id)}
                    aria-expanded={open}
                  >
                    <div className="cross-sync-device-icon">
                      <PlatformIcon platform={device.platform} />
                    </div>
                    <div className="cross-sync-device-meta">
                      <strong>{device.name}</strong>
                      <span>{device.os_name || "Устройство"} · {formatLastSeen(device.last_seen_ms)}</span>
                    </div>
                    <svg className={`cross-sync-chevron${open ? " is-open" : ""}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="m6 9 6 6 6-6" />
                    </svg>
                  </button>
                  {open && (
                    <div className="cross-sync-device-details">
                      <div className="cross-sync-device-specs">
                        <span>Nimbo {device.app_version || "—"}</span>
                        <span>{device.platform === "android" ? "Android" : "Desktop"}</span>
                        <span>Добавлено {new Date(device.created_at_ms).toLocaleDateString()}</span>
                        <span>Синхронизировано {new Date(device.last_seen_ms).toLocaleString()}</span>
                      </div>
                      {device.last_subscription_count > 0 && (
                        <div className="cross-sync-device-subs">
                          <span className="cross-sync-subs-label">
                            На устройстве {device.last_subscription_count} {subscriptionWord(device.last_subscription_count)}
                          </span>
                          {device.last_subscription_names.length > 0 && (
                            <div className="cross-sync-subscription-chips">
                              {device.last_subscription_names.slice(0, 6).map((name, index) => <span key={`${name}-${index}`}>{name}</span>)}
                              {device.last_subscription_count > 6 && <span className="is-more">+{device.last_subscription_count - 6} ещё</span>}
                            </div>
                          )}
                        </div>
                      )}
                      <div className="cross-sync-device-categories">
                        <span className="cross-sync-subs-label">Синхронизировать с этим устройством</span>
                        <div className="cross-sync-options">
                          <SyncOption title="Подписки" detail="Ссылки и названия" checked={device.categories.subscriptions} disabled={busy} onChange={(v) => void setDeviceCategories(device.device_id, { ...device.categories, subscriptions: v })} />
                          <SyncOption title="Оформление" detail="Тема, акцент и стекло" checked={device.categories.appearance} disabled={busy} onChange={(v) => void setDeviceCategories(device.device_id, { ...device.categories, appearance: v })} />
                          <SyncOption title="Подключение" detail="Kill Switch и сеть" checked={device.categories.connection} disabled={busy} onChange={(v) => void setDeviceCategories(device.device_id, { ...device.categories, connection: v })} />
                          <SyncOption title="Автоматизация" detail="Обновления и интервалы" checked={device.categories.automation} disabled={busy} onChange={(v) => void setDeviceCategories(device.device_id, { ...device.categories, automation: v })} />
                        </div>
                        <SyncOption
                          className="cross-sync-option-autosync"
                          title="Автосинхронизация"
                          detail="Обновлять данные автоматически, без кнопки"
                          checked={device.auto_sync}
                          disabled={busy}
                          onChange={(v) => void setAutoSync(device.device_id, v)}
                        />
                      </div>
                      <div className="cross-sync-device-actions">
                        <button
                          className="cross-sync-sync-now"
                          onClick={() => void syncNow(device.device_id)}
                          disabled={busy}
                          title="Запустить синхронизацию с этим устройством"
                        >
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                            <path d="M21 12a9 9 0 1 1-2.64-6.36M21 3v6h-6" />
                          </svg>
                          <span>Синхронизировать</span>
                        </button>
                        <button
                          className="cross-sync-remove"
                          onClick={() => void removeDevice(device.device_id)}
                          disabled={busy}
                          title="Удалить устройство и отключить синхронизацию"
                        >
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                            <path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M10 11v6M14 11v6" />
                          </svg>
                          <span>Удалить</span>
                        </button>
                      </div>
                      {syncHint === device.device_id && (
                        <div className="cross-sync-sync-hint">
                          Nimbo на Android сам подключается к этому компьютеру. Откройте приложение на телефоне (Настройки → Синхронизация) — данные обновятся автоматически.
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </section>
      )}

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
            <div className="cross-sync-direction">
              <span className="cross-sync-direction-label">Куда передать данные:</span>
              <label className="cross-sync-direction-option">
                <input
                  type="radio"
                  name="cross-sync-direction"
                  checked={direction === "desktop_to_android"}
                  onChange={() => setDirection("desktop_to_android")}
                />
                <span><strong>С ПК на телефон</strong><small>Настройки и подписки этого компьютера будут перенесены на Android</small></span>
              </label>
              <label className="cross-sync-direction-option">
                <input
                  type="radio"
                  name="cross-sync-direction"
                  checked={direction === "android_to_desktop"}
                  onChange={() => setDirection("android_to_desktop")}
                />
                <span><strong>С телефона на ПК</strong><small>Подписки и настройки Android будут перенесены на этот компьютер</small></span>
              </label>
            </div>
          </div>
          <div className="cross-sync-actions">
            <button className="cross-sync-secondary" onClick={() => void reject()} disabled={busy}>Отклонить</button>
            <button className="cross-sync-primary" onClick={() => void approve()} disabled={busy || selectedCount === 0}>Разрешить</button>
          </div>
        </section>
      )}

      {session?.state === "device_offline" && (
        <section className="cross-sync-card cross-sync-status-card cross-sync-offline-card">
          <div className="cross-sync-offline" />
          <div>
            <h2>Устройство не в сети</h2>
            <p>Связь с телефоном потеряна во время сеанса — вероятно, приложение на нём было закрыто. Откройте Nimbo на телефоне и запустите синхронизацию заново.</p>
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
          <div>
            <strong>Устройство подключено</strong>
            <span>Добавлено подписок: {session.result?.added_subscriptions.length ?? 0}. Дальше устройство синхронизируется само.</span>
          </div>
          <div className="cross-sync-actions">
            <button className="cross-sync-secondary" onClick={() => void startSession()} disabled={busy}>Добавить ещё</button>
            <button className="cross-sync-primary" onClick={() => void closePairing()} disabled={busy}>Готово</button>
          </div>
        </section>
      )}

      {(error || session?.error) && <div className="cross-sync-error">{error || session?.error}</div>}

      <footer className="cross-sync-footer">
        <span>Шифрование AES‑256‑GCM · локальная сеть</span>
        <span>{lastSync ? `Последняя синхронизация: ${new Date(lastSync.at).toLocaleString()} · ${lastSync.device}` : "Синхронизаций ещё не было"}</span>
      </footer>
    </div>
  );
}

function SyncOption({ title, detail, checked, disabled, className, onChange }: { title: string; detail: string; checked: boolean; disabled?: boolean; className?: string; onChange: (value: boolean) => void }) {
  return (
    <label className={`cross-sync-option${disabled ? " is-disabled" : ""}${className ? ` ${className}` : ""}`}>
      <span><strong>{title}</strong><small>{detail}</small></span>
      <input type="checkbox" checked={checked} disabled={disabled} onChange={(event) => onChange(event.target.checked)} />
      <i aria-hidden="true" />
    </label>
  );
}

function PlatformIcon({ platform, className }: { platform: string; className?: string }) {
  if (platform === "android") {
    return (
      <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="m17.6 9.48 1.84-3.18c.16-.31.04-.69-.26-.85a.637.637 0 0 0-.83.22l-1.88 3.24a11.463 11.463 0 0 0-8.94 0L5.65 5.67a.643.643 0 0 0-.87-.2c-.28.18-.37.54-.22.83L6.4 9.48A10.78 10.78 0 0 0 1 18h22a10.78 10.78 0 0 0-5.4-8.52zM7 15.25a1.25 1.25 0 1 1 0-2.5 1.25 1.25 0 0 1 0 2.5zm10 0a1.25 1.25 0 1 1 0-2.5 1.25 1.25 0 0 1 0 2.5z" />
      </svg>
    );
  }
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3.5" y="5" width="17" height="12" rx="2.2" />
      <path d="M9.5 20.5h5M12 17v3.5" />
    </svg>
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

function subscriptionWord(count: number): string {
  const n = Math.abs(count) % 100;
  const n1 = n % 10;
  if (n > 10 && n < 20) return "подписок";
  if (n1 > 1 && n1 < 5) return "подписки";
  if (n1 === 1) return "подписка";
  return "подписок";
}

function deviceWord(count: number): string {
  const n = Math.abs(count) % 100;
  const n1 = n % 10;
  if (n > 10 && n < 20) return "устройств";
  if (n1 > 1 && n1 < 5) return "устройства";
  if (n1 === 1) return "устройство";
  return "устройств";
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

function formatLastSeen(lastSeenMs: number): string {
  if (!lastSeenMs) return "ещё не синхронизировалось";
  const seconds = Math.max(0, Math.floor((Date.now() - lastSeenMs) / 1000));
  if (seconds < 60) return "в сети · только что";
  if (seconds < 3600) return `синхронизировано ${Math.floor(seconds / 60)} мин назад`;
  const hours = Math.floor(seconds / 3600);
  if (hours < 24) return `синхронизировано ${hours} ч назад`;
  return `синхронизировано ${new Date(lastSeenMs).toLocaleString()}`;
}
