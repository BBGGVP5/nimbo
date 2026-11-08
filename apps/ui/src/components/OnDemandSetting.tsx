import { useEffect, useRef, useState } from 'react';
import { isTauriRuntime } from '../lib/api';
import { useMessages } from '../lib/i18n';
import { defaultOnDemand, onDemandApi, onDemandError, validatedOnDemand, type OnDemandStatus } from '../lib/onDemand';
import { startVisiblePolling } from '../lib/visiblePolling';
import './on-demand-setting.css';

export function OnDemandSetting() {
  const ru = useMessages().common.locale.startsWith('ru');
  const native = isTauriRuntime();
  const t = (ruText: string, enText: string) => ru ? ruText : enText;
  const [remote, setRemote] = useState<OnDemandStatus | null>(null);
  const [settings, setSettings] = useState(defaultOnDemand);
  const [names, setNames] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);
  const writing = useRef(false);
  const generation = useRef(0);
  useEffect(() => {
    if (!native) return;
    let disposed = false;
    let initialized = false;
    const stop = startVisiblePolling(async () => {
      const version = generation.current;
      if (writing.current || disposed) return;
      try {
        const result = await onDemandApi.get();
        if (disposed || writing.current || version !== generation.current) return;
        setRemote(result);
        if (!initialized) { setError(null); setSettings(result.settings); setNames(result.settings.trusted_ssids.join('\n')); initialized = true; }
      } catch (e) { if (!disposed && !initialized) setError(e); }
    }, 10000);
    return () => { disposed = true; stop(); };
  }, [native]);

  const phase: Record<string, string> = {
    disabled: t('Выключено', 'Off'), paused: t('Пауза после отключения', 'Paused after disconnect'),
    waiting: t('Ожидаем подходящую сеть', 'Waiting for a matching network'),
    network_unavailable: t('Не удалось определить сеть', 'Network information unavailable'),
    trusted: t('Доверенный Wi-Fi · отключено', 'Trusted Wi-Fi · disconnected'),
    connected: t('Подключено', 'Connected'), connecting: t('Подключаемся…', 'Connecting…'),
    retry: t('Повторим подключение позже', 'Will retry later'), no_target: t('Выберите доступный сервер', 'Choose an available server'),
  };
  async function save() {
    if (writing.current || !native) return;
    writing.current = true; generation.current++; setSaving(true); setError(null); setSaved(false);
    try {
      const result = await onDemandApi.save(validatedOnDemand(settings, names));
      setRemote(result); setSettings(result.settings); setNames(result.settings.trusted_ssids.join('\n')); setSaved(true);
    } catch (e) { setError(e); }
    finally { writing.current = false; setSaving(false); }
  }
  return <section className="on-demand-setting" aria-labelledby="on-demand-title" aria-busy={saving}>
    <div className="on-demand-heading"><div><h3 id="on-demand-title">{t('Автоподключение по сети', 'Network auto-connect')}</h3><p>{t('Nimbo подключится при появлении выбранной сети.', 'Nimbo connects when a selected network becomes available.')}</p></div>
      <span className="on-demand-status" role="status">{remote ? phase[remote.phase] ?? phase.waiting : native ? t('Загрузка…', 'Loading…') : t('Предпросмотр', 'Preview')}</span></div>
    <fieldset disabled={!native || !remote?.supported || saving}>
      <label className="on-demand-enable"><input type="checkbox" checked={settings.enabled} onChange={e => { setSettings({ ...settings, enabled: e.target.checked }); setSaved(false); }} />{t('Включить правила', 'Enable rules')}</label>
      <div className="on-demand-networks" role="group" aria-label={t('Типы сети', 'Network types')}>
        {(['wifi', 'ethernet', 'cellular'] as const).map((key, i) => <button type="button" key={key} aria-pressed={settings[key]} onClick={() => { setSettings({ ...settings, [key]: !settings[key] }); setSaved(false); }}>
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true">{i === 0 ? <><path d="M3 8a14 14 0 0 1 18 0M6 12a9 9 0 0 1 12 0M9 16a4.5 4.5 0 0 1 6 0"/><circle cx="12" cy="20" r=".8" fill="currentColor"/></> : i === 1 ? <><rect x="6" y="3" width="12" height="12" rx="2"/><path d="M9 3v5m3-5v5m3-5v5m-3 7v6m-5 0h10"/></> : <><path d="M4 20v-4m5 4v-8m5 8V8m5 12V4"/></>}</svg>
          <span>{i === 0 ? 'Wi-Fi' : i === 1 ? 'Ethernet' : t('Мобильная', 'Cellular')}</span></button>)}
      </div>
      <label className="on-demand-ssids" htmlFor="on-demand-ssids">{t('Доверенные Wi-Fi', 'Trusted Wi-Fi')}<textarea id="on-demand-ssids" rows={3} value={names} maxLength={4096} autoComplete="off" spellCheck={false} placeholder={t('Имя сети — по одному на строку', 'One network name per line')} onChange={e => { setNames(e.target.value); setSaved(false); }} aria-describedby="on-demand-trusted-help" /></label>
      <p id="on-demand-trusted-help">{t('В этих сетях Nimbo отключится. Регистр имени учитывается. Если имя недоступно или активна другая сеть, автоматического отключения не будет.', 'Nimbo disconnects on these networks. Names are case-sensitive. Unavailable names or another active uplink prevent automatic disconnection.')}</p>
      <div className="on-demand-footer"><p>{t('Сервер: ', 'Server: ')}<strong>{remote?.target_name ?? t('подключитесь перед первым включением', 'connect before enabling')}</strong></p><button type="button" className="settings-action" onClick={() => void save()}>{saving ? t('Сохранение…', 'Saving…') : remote?.paused && settings.enabled ? t('Сохранить и возобновить', 'Save and resume') : t('Сохранить', 'Save')}</button></div>
    </fieldset>
    {saved && <p role="status">{t('Правила сохранены.', 'Rules saved.')}</p>}
    {error != null && <p role="alert">{onDemandError(error, ru)}</p>}
    <p>{native ? t('Работает, пока Nimbo открыт, в том числе в трее. Ручное отключение ставит правила на паузу; новое подключение обновляет выбранный сервер и возобновляет их. Linux: требуется NetworkManager. Windows: для имён Wi-Fi может понадобиться доступ к местоположению.', 'Works while Nimbo is running, including in the tray. Manual disconnect pauses the rules; connecting again updates the server and resumes them. Linux requires NetworkManager. Windows may require location access for Wi-Fi names.') : t('Правила доступны в desktop-приложении.', 'Rules are available in the desktop app.')}</p>
  </section>;
}
