import { useEffect, useId, useRef, useState } from 'react';
import type { CoreGroup, CoreProfile, CoreSnapshot } from '../lib/coreApi';
import { groupCanSelect } from '../lib/coreProfiles';
import { NimboSelect } from './NimboSelect';
import { CountryFlag } from './CountryFlag';
import { subscriptionMemberDetails } from './core-subscription-groups';
import type { useMihomoPing } from './useMihomoPing';

function PingIcon({pending=false}:{pending?:boolean}) {
  return <svg className={pending ? 'core-proxy-ping-icon is-pending' : 'core-proxy-ping-icon'} viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12h4l3-8 4 16 3-8h4"/></svg>;
}

function proxyType(type: string | undefined, ru: boolean): string {
  const labels: Record<string, string> = {
    direct:'Direct', reject:'Reject', vless:'VLESS', vmess:'VMess', trojan:'Trojan', ss:'Shadowsocks', shadowsocks:'Shadowsocks',
    hysteria2:'Hysteria2', hysteria:'Hysteria', tuic:'TUIC', wireguard:'WireGuard', socks5:'SOCKS5', http:'HTTP',
    'url-test':ru ? 'Авто' : 'Auto', urltest:ru ? 'Авто' : 'Auto', fallback:'Fallback',
    select:ru ? 'Группа' : 'Group', selector:ru ? 'Группа' : 'Group', 'load-balance':ru ? 'Балансировка' : 'Load balance',
  };
  return type ? labels[type.toLowerCase()] ?? type : ru ? 'Прокси' : 'Proxy';
}

function ProxyName({ name, ru }: { name: string; ru: boolean }) {
  if (name === 'DIRECT') return <>{ru ? 'Напрямую' : 'Direct'}</>;
  if (name === 'REJECT') return <>{ru ? 'Блокировать' : 'Reject'}</>;
  const flag = /^(\p{Regional_Indicator}{2})\s*/u.exec(name);
  return flag ? <><CountryFlag serverName={name} fallback={flag[1]} className="country-flag-xs"/>{' '}{name.slice(flag[0].length)}</> : <>{name}</>;
}

/** Navigation is local; proxy selection is authoritative only after native readback. */
export function MihomoProxyGroups({ profileId, profile, snapshot, groups, running, disabled, ru, ping, onSelect }: {
  profileId: string | undefined; profile: CoreProfile | undefined; snapshot: CoreSnapshot | null;
  groups: Array<[string, CoreGroup]>; running: boolean; disabled: boolean; ru: boolean;
  onSelect: (group: string, member: string) => void;
  ping: ReturnType<typeof useMihomoPing>;
}) {
  const [choice, setChoice] = useState<{profileId: string | undefined; name: string} | null>(null);
  const id = useId(), tabs = useRef<Array<HTMLButtonElement | null>>([]);
  const active = groups.find(([name]) => choice?.profileId === profileId && choice?.name === name) ?? groups[0];
  const index = active ? groups.findIndex(([name]) => name === active[0]) : -1;
  useEffect(() => {
    const tab = tabs.current[index], list = tab?.parentElement;
    if (!tab || !list) return;
    const left = tab.offsetLeft, right = left + tab.offsetWidth;
    if (left < list.scrollLeft) list.scrollLeft = left;
    else if (right > list.scrollLeft + list.clientWidth) list.scrollLeft = right - list.clientWidth;
  }, [index, profileId]);
  if (!active) return null;
  const [name, group] = active;
  const choose = (name: string) => setChoice({profileId, name});
  return <div className="core-proxy-browser">
    <div className="core-proxy-navigation">
      <div className="core-proxy-tabs" role="tablist" aria-label={ru ? 'Категории' : 'Categories'}>
        {groups.map(([category], tabIndex) => <button type="button" role="tab" key={category} ref={node => { tabs.current[tabIndex] = node; }}
          id={`${id}-tab-${tabIndex}`} aria-selected={category === name} aria-controls={`${id}-panel`} tabIndex={category === name ? 0 : -1}
          className="core-proxy-tab" title={category} onClick={() => choose(category)}
          onKeyDown={event => {
            let next = tabIndex;
            if (event.key === 'ArrowRight') next = (tabIndex + 1) % groups.length;
            else if (event.key === 'ArrowLeft') next = (tabIndex + groups.length - 1) % groups.length;
            else if (event.key === 'Home') next = 0;
            else if (event.key === 'End') next = groups.length - 1;
            else return;
            event.preventDefault(); choose(groups[next][0]); tabs.current[next]?.focus({preventScroll:true});
          }}>{category}</button>)}
      </div>
      <NimboSelect className="core-proxy-category-menu" aria-label={ru ? 'Категория' : 'Category'} title={ru ? 'Категории' : 'Categories'} value={name} onChange={event => choose(event.target.value)}>
        {groups.map(([category]) => <option key={category} value={category}>{category}</option>)}
      </NimboSelect>
      <button type="button" className="core-proxy-ping-all" disabled={!ping.running && (!ping.available || !group.all?.length)}
        aria-label={ping.running ? (ru ? 'Остановить пинг' : 'Stop ping') : `${ru ? 'Пинг категории' : 'Ping category'}: ${name}`}
        title={ping.running ? (ru ? 'Остановить пинг' : 'Stop ping') : !running ? (ru ? 'Пинг доступен при подключении' : 'Connect to check latency') : (ru ? 'Пинг серверов категории' : 'Check category servers')}
        onClick={() => ping.running ? ping.cancel() : ping.run(group.all ?? [])}>
        <PingIcon pending={ping.running}/><span>{ping.running ? (ru ? 'Стоп' : 'Stop') : (ru ? 'Пинг' : 'Ping')}</span>
      </button>
    </div>
    <div role="tabpanel" id={`${id}-panel`} aria-labelledby={`${id}-tab-${index}`} tabIndex={0}>
      <div className="core-subscription-members" role="group" aria-label={name}>
        {(group.all ?? []).map(member => {
          const info = subscriptionMemberDetails(profile, running ? snapshot : null, running, member);
          const selected = running && group.now === member;
          const result = ping.results.get(member);
          const latency = result?.state === 'success' ? `${result.ms} ms` : result?.state === 'pending' ? '…' : result?.state === 'error' ? (result.reason === 'timeout' ? (ru ? 'Тайм-аут' : 'Timeout') : (ru ? 'Недоступен' : 'Unavailable')) : '—';
          return <div key={member} className="core-proxy-card">
            <button type="button" className="core-subscription-member" aria-label={`${name}: ${member}`} aria-pressed={selected}
            disabled={!running || !groupCanSelect(group) || disabled} title={member} onClick={() => onSelect(name, member)}>
            <span className="core-proxy-card-name"><ProxyName name={member} ru={ru}/></span>
            {selected && <svg className="core-proxy-card-selected" viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg>}
            <span className="core-proxy-card-meta"><span>{proxyType(info.type, ru)}</span>{info.now && <span className="core-proxy-card-route" title={info.now}><ProxyName name={info.now} ru={ru}/></span>}
              <span className="core-proxy-card-latency" data-state={result?.state ?? 'idle'} role="status" aria-label={`${member}: ${latency}`}>{latency}</span>
            </span>
            </button>
            <button type="button" className="core-proxy-ping-one" aria-label={`${ru ? 'Пинг' : 'Ping'}: ${member}`}
              title={!running ? (ru ? 'Пинг доступен при подключении' : 'Connect to check latency') : (ru ? 'Проверить пинг' : 'Check latency')}
              disabled={!ping.available || ping.running} aria-busy={result?.state === 'pending'} onClick={() => ping.run([member])}>
              <PingIcon pending={result?.state === 'pending'}/>
            </button>
          </div>;
        })}
      </div>
      {!group.all?.length && <small role="status">{ru ? 'Нет серверов' : 'No servers'}</small>}
    </div>
  </div>;
}
