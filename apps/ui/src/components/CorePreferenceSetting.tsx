import { useEffect } from 'react';
import { useCoreStore } from '../coreStore';
import { isTauriRuntime } from '../lib/api';
import { type CorePreference } from '../lib/coreApi';
import { useMessages } from '../lib/i18n';
import { NimboSelect } from './NimboSelect';
import { mihomoErrorMessage } from '../lib/coreProfiles';
import './core-preference-setting.css';

export function CorePreferenceSetting({ context = 'connection' }: { context?: 'connection' | 'latency' } = {}) {
  const ru = useMessages().common.locale.startsWith('ru');
  const { data, availability, busy, error, refresh, preference } = useCoreStore();
  const native = isTauriRuntime();
  useEffect(() => { if (native) void refresh(); }, [native, refresh]);
  const awg = availability.find(item => item.core === 'awg');
  const awgAvailable = awg?.selector_available === true;
  const xrayAvailable = availability.find(item => item.core === 'xray')?.selector_available === true;
  const mihomo=availability.find(item => item.core === 'mihomo');
  const mihomoAvailable=mihomo?.selector_available===true;
  const mihomoLabel='Mihomo';
  const label = context === 'latency' ? ru ? 'Ядро подключения' : 'Connection core' : ru ? 'Ядро' : 'Core';
  return <div className="settings-row settings-row-block core-preference-setting">
    <div className="core-preference-setting__header">
      <label className="settings-row-title" htmlFor="desktop-core-preference">{label}</label>
    <NimboSelect id="desktop-core-preference" className="settings-input" aria-describedby="desktop-core-help"
      value={data?.preferred_core ?? 'auto'} disabled={!native || !data || !!busy}
      onChange={event => void preference(event.target.value as CorePreference)}>
      <option value="auto">{ru ? 'Авто (по умолчанию)' : 'Auto (default)'}</option>
      <option value="xray" disabled={!xrayAvailable}>{xrayAvailable ? 'Xray' : ru ? 'Xray — недоступно' : 'Xray — unavailable'}</option>
      <option value="awg" disabled={!awgAvailable}>{awgAvailable ? 'AWG' : ru ? 'AWG — недоступно' : 'AWG — unavailable'}</option>
      <option value="mihomo" disabled={!mihomoAvailable}>{mihomoAvailable ? mihomoLabel : ru ? 'Mihomo — недоступно' : 'Mihomo — unavailable'}</option>
    </NimboSelect>
    </div>
    <div className="settings-row-description" id="desktop-core-help">
      {ru
        ? 'Со следующего подключения. Авто — по формату подписки.'
        : 'From the next connection. Auto follows the subscription format.'}
    </div>
    <details className="core-preference-setting__details"><summary>{ru ? 'Совместимость ядер' : 'Core compatibility'}</summary><div className="settings-row-description" id="desktop-core-availability">
      {ru ? 'Текущее соединение не меняется. Для Mihomo подписка должна отдавать совместимый YAML; группы и правила сохраняются. Для TUN нужен системный помощник. После сбоя Kill Switch снимайте явным сбросом.' : 'The active connection stays unchanged. Mihomo needs compatible subscription YAML; groups and rules are preserved. TUN requires the system helper. Reset Kill Switch explicitly after a failure.'}{' '}
      {native && data && (awgAvailable
        ? ru ? 'AWG — через проверенный адаптер и Xray.' : 'AWG uses the verified adapter and Xray.'
        : ru ? 'AWG недоступен на этой установке.' : 'AWG is unavailable in this installation.')}
      {!native && (ru ? 'Выбор доступен в desktop-приложении.' : 'Selection is available in the desktop app.')}
    </div></details>
    <a className="settings-action" href="#/mihomo">{ru ? 'Дополнительно: YAML и группы' : 'Advanced: YAML and groups'}</a>
    {busy === 'preference' && <div role="status">{ru ? 'Сохранение…' : 'Saving…'}</div>}
    {error && <div role="alert" className="settings-row-description">
      {mihomoErrorMessage(error, ru)}
      <button type="button" className="settings-action" disabled={!!busy} onClick={() => void refresh()}>{ru ? 'Обновить' : 'Refresh'}</button>
    </div>}
  </div>;
}
