import { NimboSelect } from '../components/NimboSelect';
import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { useCoreStore } from '../coreStore';
import { useAppStore } from '../store';
import { api, isTauriRuntime } from '../lib/api';
import { coreApi } from '../lib/coreApi';
import { decodeCoreSource, groupCanSelect, MAX_CORE_SOURCE_BYTES, MAX_CORE_URL_BYTES, mihomoBlockReason, mihomoErrorMessage, validDelayUrl, validateCoreSource, validateCoreSourceUrl } from '../lib/coreProfiles';
import { startVisiblePolling } from '../lib/visiblePolling';
import { useMessages } from '../lib/i18n';
import './mihomo-profiles.css';

export function MihomoProfiles() {
  const ru = useMessages().common.locale.startsWith('ru');
  const core = useCoreStore();
  const mode = useAppStore(s => s.status?.connection_mode ?? 'tun');
  const killSwitch = useAppStore(s => s.preferences.connection_kill_switch);
  const native = isTauriRuntime();
  const [name, setName] = useState('');
  const [source, setSource] = useState('');
  const [sourceUrl, setSourceUrl] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [localError, setLocalError] = useState<unknown>(null);
  const [fileBusy, setFileBusy] = useState(false);
  const [removeId, setRemoveId] = useState<string | null>(null);
  const [delayName, setDelayName] = useState('');
  const [delayUrl, setDelayUrl] = useState('');
  const text = (r: string, en: string) => ru ? r : en;
  const busy = !!core.busy || fileBusy;
  const profiles = core.data?.profiles.filter(p => p.kind === 'mihomo_yaml') ?? [];
  const capability = core.availability.find(item => item.core === 'mihomo');
  const blocked = mihomoBlockReason(capability, core.data?.preferred_core, mode, killSwitch);
  const running = core.runtime?.running === true;
  const active = profiles.find(p => p.id === core.runtime?.profile_id);

  useEffect(() => {
    if (!native) return;
    let disposed = false;
    const refresh = async () => {
      await useCoreStore.getState().refresh();
      if (!disposed && document.visibilityState === 'visible' && useCoreStore.getState().runtime?.running) {
        await useCoreStore.getState().live('snapshot');
      }
    };
    const stop = startVisiblePolling(refresh, 10000);
    return () => { disposed = true; stop(); };
  }, [native]);

  async function loadFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    setFileBusy(true); setLocalError(null); setMessage(null);
    try {
      if (file.size > MAX_CORE_SOURCE_BYTES) throw Error('SOURCE_TOO_LARGE');
      setSource(decodeCoreSource(await file.arrayBuffer()));
      if (!name) setName(file.name.replace(/\.ya?ml$/i, ''));
    } catch (error) { setLocalError(error); }
    finally { setFileBusy(false); event.target.value = ''; }
  }

  async function importProfile(event: FormEvent) {
    event.preventDefault(); setLocalError(null); setMessage(null);
    try {
      const original = validateCoreSource(source);
      let inspectionError: string | null = null;
      const ok = await core.mutate('import', async () => {
        const result = await coreApi.import(name.trim() || 'Mihomo', original);
        inspectionError = result.inspection_error;
      });
      if (ok) {
        setSource(''); setName('');
        setMessage(text('Полный YAML сохранён.', 'Complete YAML saved.'));
        if (inspectionError) setLocalError(inspectionError);
      } else setLocalError(useCoreStore.getState().error);
    } catch (error) { setLocalError(error); }
  }

  async function importUrl(event: FormEvent) {
    event.preventDefault();
    if (busy || !native) return;
    setLocalError(null); setMessage(null);
    try {
      const url = validateCoreSourceUrl(sourceUrl);
      let inspectionError: string | null = null;
      const ok = await core.mutate('import-url', async () => {
        const result = await coreApi.importUrl(name.trim() || 'Mihomo', url);
        inspectionError = result.inspection_error;
      });
      if (ok) {
        setSourceUrl(''); setName('');
        setMessage(text('Полный YAML загружен и сохранён. Ссылка не хранится.', 'Complete YAML downloaded and saved. The URL is not stored.'));
        if (inspectionError) setLocalError(inspectionError);
      } else setLocalError(useCoreStore.getState().error);
    } catch (error) { setLocalError(error); }
  }

  async function exportProfile(id: string) {
    setLocalError(null); setMessage(null);
    const ok = await core.mutate('export', async () => api.writeClipboardText(await coreApi.export(id)));
    if (ok) setMessage(text('Исходный YAML скопирован в буфер обмена.', 'Original YAML copied to clipboard.'));
  }

  return <div className="mihomo-page">
    <header className="mihomo-page__header">
      <a className="settings-action" href="#/settings?section=connection">← {text('Настройки подключения', 'Connection settings')}</a>
      <h1>Mihomo</h1>
      <p>{text('Полные YAML-профили', 'Complete YAML profiles')}</p>
    </header>
    <div className="mihomo-notice">
      <details><summary>{text('Режимы подключения', 'Connection modes')}</summary>
      <p>{text('Windows и Linux: TUN через проверенный системный помощник. Windows поддерживает System Proxy и Both, а с новым помощником — внешний Kill Switch для TUN/Both. Защита сохраняется при сбое и закрытии приложения. Правила записываются для загрузки Windows; после перезапуска нужен явный сброс перед новым подключением. Настройки подключения не меняются автоматически.', 'Windows and Linux: TUN through the verified system helper. Windows supports System Proxy and Both; the updated helper provides external Kill Switch for TUN/Both. Protection survives failure and app exit. Windows boot protection is registered; after a restart, explicitly reset before reconnecting. Connection settings are never changed automatically.')}</p>
      </details>
      {!native && <p>{text('Для импорта и подключения откройте desktop-приложение.', 'Open the desktop app to import and connect.')}</p>}
      {native && blocked && <p role="status">{mihomoErrorMessage(blocked, ru)}</p>}
    </div>
    {(localError || core.error) && <p className="mihomo-error" role="alert">{mihomoErrorMessage(localError || core.error, ru)}</p>}
    {message && <p role="status">{message}</p>}
    {native && capability?.binary_verified && capability.reason === 'MIHOMO_HELPER_REQUIRED' && <div className="mihomo-actions">
      <button disabled={busy || running} onClick={() => void core.mutate('prepare-tun', () => coreApi.prepareTun())}>
        {core.busy === 'prepare-tun' ? text('Подготовка…', 'Preparing…') : text('Подготовить Mihomo TUN', 'Prepare Mihomo TUN')}
      </button>
      <small>{text('Потребуется системное подтверждение', 'System authorization is required')}</small>
    </div>}

    <section className="mihomo-panel" aria-labelledby="mihomo-import-heading" aria-busy={busy}>
      <h2 id="mihomo-import-heading">{text('Импорт полного YAML', 'Import complete YAML')}</h2>
      <p>{text('Файл, ссылка или текст, до 4 МиБ. Группы и правила сохраняются.', 'File, URL or text, up to 4 MiB. Groups and rules are preserved.')}</p>
      <label className="mihomo-import__name">{text('Название профиля', 'Profile name')}<input value={name} maxLength={512} disabled={busy || !native} onChange={e => setName(e.target.value)} autoComplete="off" /></label>
      <form onSubmit={event => void importUrl(event)} className="mihomo-import-url">
        <label>{text('Ссылка на полный YAML', 'Complete YAML URL')}<input type="url" value={sourceUrl} maxLength={MAX_CORE_URL_BYTES} disabled={busy || !native} onChange={e => setSourceUrl(e.target.value)} autoComplete="off" spellCheck={false} placeholder="https://…" aria-describedby="mihomo-url-policy" /></label>
        <button type="submit" disabled={busy || !native || !sourceUrl.trim()}>{core.busy === 'import-url' ? text('Загружаем…', 'Downloading…') : text('Загрузить профиль', 'Download profile')}</button>
      </form>
      <p id="mihomo-url-policy">{text('Загрузка один раз, до 20 секунд. Ссылка не сохраняется; это не автообновляемая подписка.', 'One-time download, up to 20 seconds. The URL is not stored; this is not an automatically refreshed subscription.')}</p>
      <details className="mihomo-import-details">
      <summary>{text('Импорт из файла или текста', 'Import from file or text')}</summary>
      <form onSubmit={event => void importProfile(event)} className="mihomo-import">
        <label>{text('YAML-файл', 'YAML file')}<input type="file" accept=".yaml,.yml,text/yaml,application/yaml" disabled={busy || !native} onChange={event => void loadFile(event)} /></label>
        <label className="mihomo-import__source">{text('Полный исходный YAML', 'Complete original YAML')}<textarea rows={7} spellCheck={false} autoComplete="off" value={source} disabled={busy || !native} onChange={event => setSource(event.target.value)} placeholder="proxy-groups: …" /></label>
        <button type="submit" disabled={busy || !native || !source.trim()}>{text('Импортировать', 'Import profile')}</button>
      </form>
      </details>
    </section>

    <section className="mihomo-panel" aria-labelledby="mihomo-profiles-heading">
      <div className="mihomo-section-head"><h2 id="mihomo-profiles-heading">{text('Профили', 'Profiles')} <small>{profiles.length}</small></h2><button disabled={busy || !native} onClick={() => void core.refresh()}>{text('Обновить', 'Refresh')}</button></div>
      {!profiles.length && <p>{text('Импортируйте полный YAML, чтобы подключиться через Mihomo.', 'Import a complete YAML profile to connect through Mihomo.')}</p>}
      {profiles.map(profile => <article className="mihomo-profile" key={profile.id} data-active={running && core.runtime?.profile_id === profile.id}>
        <div><h3>{profile.name}</h3><p>{profile.inspection ? text('Проверен инспектором · ', 'Inspected · ') + profile.inspection.issues.length + text(' замечаний', ' issues') : text('Ожидает native-инспекции', 'Awaiting native inspection')}{running && core.runtime?.profile_id === profile.id && <> · <strong>{text('Подключён', 'Connected')}</strong></>}</p></div>
        <div className="mihomo-actions">
          {running && core.runtime?.profile_id === profile.id
            ? <button disabled={busy} onClick={() => void core.stop()}>{text('Отключить', 'Disconnect')}</button>
            : <button disabled={busy || !native || !!blocked} title={blocked ? mihomoErrorMessage(blocked, ru) : undefined} onClick={() => void core.connect(profile.id)}>{text('Подключить', 'Connect')}</button>}
          <button disabled={busy || !native || !capability?.inspect_available} onClick={() => void core.mutate('inspect', () => coreApi.inspect(profile.id))}>{text('Проверить', 'Inspect')}</button>
          <button disabled={busy || !native} onClick={() => void exportProfile(profile.id)}>{text('Копировать YAML', 'Copy YAML')}</button>
          {removeId !== profile.id
            ? <button disabled={busy || !native || (running && core.runtime?.profile_id === profile.id)} onClick={() => setRemoveId(profile.id)}>{text('Удалить', 'Remove')}</button>
            : <><button disabled={busy} onClick={() => void core.mutate('remove', () => coreApi.remove(profile.id)).then(ok => { if (ok) setRemoveId(null); })}>{text('Подтвердить удаление', 'Confirm removal')}</button><button disabled={busy} onClick={() => setRemoveId(null)}>{text('Отмена', 'Cancel')}</button></>}
        </div>
      </article>)}
    </section>

    {running && <section className="mihomo-panel" aria-labelledby="mihomo-live-heading">
      <div className="mihomo-section-head"><h2 id="mihomo-live-heading">{text('Активная сессия', 'Active session')} · {active?.name ?? 'Mihomo'}</h2><button disabled={busy} onClick={() => void core.live('snapshot')}>{text('Обновить состояние', 'Refresh state')}</button></div>
      <p>{text('Группы и категории получены от native-ядра. Выбор подтверждается ядром перед сохранением; автоматические группы управляются самим Mihomo.', 'Groups and categories come from the native core. Selections are acknowledged before saving; automatic groups are managed by Mihomo.')}</p>
      <h3>{text('Группы', 'Groups')}</h3>
      {Object.entries(core.snapshot?.groups ?? {}).filter(([, group]) => !group.hidden).map(([name, group]) => <div className="mihomo-live-row" key={name}>
        <div><strong>{name}</strong><small>{group.type ?? '—'}</small></div>
        {groupCanSelect(group) ? <NimboSelect aria-label={name} disabled={busy} value={group.now ?? ''} onChange={event => void core.live('select', name, event.target.value)}>
          {!group.now && <option value="" disabled>—</option>}{(group.all ?? []).map(member => <option key={member} value={member}>{member}</option>)}
        </NimboSelect> : <span>{group.now ?? '—'}</span>}
      </div>)}
      <h3>Proxy providers</h3>
      {Object.entries(core.snapshot?.providers ?? {}).map(([name, provider]) => <div className="mihomo-live-row" key={name}><div><strong>{name}</strong><small>{provider.vehicleType ?? '—'} · {provider.proxies?.length ?? 0} {text('узлов', 'nodes')}</small></div><button disabled={busy} onClick={() => void core.live('provider', name)}>{text('Обновить provider', 'Refresh provider')}</button></div>)}
      <h3>Rule providers</h3>
      {Object.entries(core.snapshot?.ruleProviders ?? {}).map(([name, provider]) => <div className="mihomo-live-row" key={name}><div><strong>{name}</strong><small>{provider.behavior ?? '—'} · {provider.ruleCount ?? 0} {text('правил', 'rules')}</small></div><button disabled={busy} onClick={() => void core.live('rules', name)}>{text('Обновить правила', 'Refresh rules')}</button></div>)}
      {!core.snapshot && <p>{text('Загрузите состояние сессии для отображения групп и providers.', 'Refresh session state to load groups and providers.')}</p>}
      <details className="mihomo-delay"><summary>{text('Проверка задержки через native-ядро', 'Native delay check')}</summary><div className="mihomo-actions">
        <label>{text('Имя узла или группы', 'Node or group name')}<input value={delayName} disabled={busy} onChange={e => setDelayName(e.target.value)} /></label>
        <label>HTTP(S) URL<input type="url" value={delayUrl} disabled={busy} onChange={e => setDelayUrl(e.target.value)} /></label>
        <button disabled={busy || !delayName || !validDelayUrl(delayUrl)} onClick={() => void core.live('delay', delayName, delayUrl)}>{text('Проверить задержку', 'Check delay')}</button>
      </div>{core.delay && <p role="status">{core.delay.name}: {core.delay.ms} ms</p>}</details>
    </section>}
    {core.busy && <p role="status" aria-live="polite">{text('Операция выполняется…', 'Working…')}</p>}
  </div>;
}
