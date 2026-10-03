import { useEffect } from 'react';
import { useCoreStore } from '../coreStore';
import { isTauriRuntime } from '../lib/api';
import { type CorePreference } from '../lib/coreApi';
import { useMessages } from '../lib/i18n';
import './core-preference-setting.css';

export function CorePreferenceSetting() {
  const ru = useMessages().common.locale.startsWith('ru');
  const { data, availability, busy, error, refresh, preference } = useCoreStore();
  const native = isTauriRuntime();
  useEffect(() => { if (native) void refresh(); }, [native, refresh]);
  const awg = availability.find(item => item.core === 'awg');
  const awgAvailable = awg?.selector_available === true;
  const xrayAvailable = availability.find(item => item.core === 'xray')?.selector_available === true;
  const mihomo=availability.find(item => item.core === 'mihomo');
  const mihomoAvailable=mihomo?.selector_available===true;
  const mihomoLabel=mihomo?.tun_available ? 'Mihomo · TUN' : mihomo?.system_proxy_available ? 'Mihomo · System Proxy' : 'Mihomo';
  const label = ru ? 'Ядро' : 'Core';
  return <div className="settings-row settings-row-block core-preference-setting">
    <div className="core-preference-setting__header">
      <label className="settings-row-title" htmlFor="desktop-core-preference">{label}</label>
    <select id="desktop-core-preference" className="settings-input" aria-describedby="desktop-core-help desktop-core-availability"
      value={data?.preferred_core ?? 'auto'} disabled={!native || !data || !!busy}
      onChange={event => void preference(event.target.value as CorePreference)}>
      <option value="auto">{ru ? 'Авто (по умолчанию)' : 'Auto (default)'}</option>
      <option value="xray" disabled={!xrayAvailable}>{xrayAvailable ? 'Xray' : ru ? 'Xray — недоступно' : 'Xray — unavailable'}</option>
      <option value="awg" disabled={!awgAvailable}>{awgAvailable ? 'AWG' : ru ? 'AWG — недоступно' : 'AWG — unavailable'}</option>
      <option value="mihomo" disabled={!mihomoAvailable}>{mihomoAvailable ? mihomoLabel : ru ? 'Mihomo — недоступно' : 'Mihomo — unavailable'}</option>
    </select>
    </div>
    <div className="settings-row-description" id="desktop-core-help">
      {ru
        ? 'Применится при следующем ручном подключении. Авто выбирает ядро по формату профиля; явный выбор требует совместимого профиля. Текущее соединение и его восстановление сохраняют прежний выбор.'
        : 'Applies on the next manual connection. Auto uses the profile format; an explicit choice requires a compatible profile. The current connection and its recovery keep the previous choice.'}
    </div>
    <div className="settings-row-description" id="desktop-core-availability">
      {ru ? 'Mihomo: полные YAML-профили. Linux TUN требует подготовленного помощника; Windows использует System Proxy.' : 'Mihomo: full YAML profiles. Linux TUN requires the prepared helper; Windows uses System Proxy.'}{' '}
      {native && data && (awgAvailable
        ? ru ? 'AWG: доступен проверенный адаптер; подключение также использует Xray.' : 'AWG: verified adapter available; connections also use Xray.'
        : ru ? 'AWG: проверенный адаптер отсутствует или платформа не поддерживается.' : 'AWG: verified adapter missing or platform unsupported.')}
      {!native && (ru ? 'Выбор доступен в desktop-приложении.' : 'Selection is available in the desktop app.')}
    </div>
    <a className="settings-action" href="#/mihomo">{ru ? 'Профили Mihomo: импорт и группы →' : 'Mihomo profiles: import and groups →'}</a>
    {busy === 'preference' && <div role="status">{ru ? 'Сохранение…' : 'Saving…'}</div>}
    {error && <div role="alert" className="settings-row-description">
      {ru ? 'Не удалось прочитать или сохранить выбор ядра. Попробуйте ещё раз.' : 'Could not read or save the core preference. Try again.'}
      <button type="button" className="settings-action" disabled={!!busy} onClick={() => void refresh()}>{ru ? 'Обновить' : 'Refresh'}</button>
    </div>}
  </div>;
}
