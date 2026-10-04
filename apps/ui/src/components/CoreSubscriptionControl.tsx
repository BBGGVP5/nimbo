import { useEffect } from 'react';
import { useCoreStore } from '../coreStore';
import { isTauriRuntime, type Subscription } from '../lib/api';
import { coreApi } from '../lib/coreApi';
import { groupCanSelect, mihomoErrorMessage } from '../lib/coreProfiles';
import { useMessages } from '../lib/i18n';
import { subscriptionGroups } from './core-subscription-groups';
import './core-subscription-groups.css';

/** The subscribed YAML owns routing; never present its legacy Xray nodes as Mihomo selections. */
export function CoreSubscriptionControl({ sub }: { sub: Subscription }) {
  const core = useCoreStore();
  const ru = useMessages().common.locale.startsWith('ru');
  const id = sub.meta?.mihomo_profile_id;
  const profile = core.data?.profiles.find(p => p.id === id);
  const running = !!id && core.runtime?.running === true && core.runtime.profile_id === id;
  const native = isTauriRuntime();
  const groups = subscriptionGroups(profile, running ? core.snapshot : null, running);
  useEffect(() => {
    if (!native || core.busy || core.error) return;
    if (!core.loaded) void core.refresh();
    else if (running && !core.snapshot) void core.live('snapshot');
    else if (!running && profile && !profile.inspection) void core.mutate('inspect', () => coreApi.inspect(profile.id));
  }, [native, core.loaded, running, profile, core.snapshot, core.busy, core.error, core.refresh, core.live, core.mutate]);
  return <div className="core-subscription-control" data-no-toggle>
    <div className="core-subscription-head"><strong>Mihomo</strong><span>{running ? (ru ? 'Подключён' : 'Connected') : profile ? (ru ? 'Группы подписки' : 'Subscription groups') : (ru ? 'Обновите подписку для Mihomo' : 'Refresh the subscription for Mihomo')}</span>
      {profile && <button type="button" className="signal-btn signal-btn--sm" disabled={!!core.busy || !native} onClick={() => void (running ? core.stop() : core.connect(profile.id))}>{running ? (ru ? 'Отключить' : 'Disconnect') : (ru ? 'Подключить' : 'Connect')}</button>}
    </div>
    {!running && groups.length > 0 && <small>{ru ? 'Подключитесь, чтобы менять серверы. Состав провайдеров загрузится при подключении.' : 'Connect to change servers. Provider members load on connection.'}</small>}
    {groups.map(([name, group]) => <details className="core-subscription-group" key={`${id}:${name}`} open>
      <summary><span><strong>{name}</strong><small>{groupCanSelect(group) ? (ru ? 'Выбор сервера' : 'Server selection') : group.type}</small></span><span>{group.now ?? '—'}</span></summary>
      <div className="core-subscription-members" role="group" aria-label={name}>
        {(group.all ?? []).map(member => <button key={member} type="button" className="core-subscription-member" aria-label={`${name}: ${member}`} aria-pressed={running && group.now === member} disabled={!running || !groupCanSelect(group) || !!core.busy || !native} onClick={() => void core.live('select', name, member)}><span className="core-member-indicator" aria-hidden="true"/><span>{member}</span></button>)}
        {!group.all?.length && <small>{running ? (ru ? 'Нет доступных серверов' : 'No available servers') : (ru ? 'Серверы провайдера — после подключения' : 'Provider members are available after connecting')}</small>}
      </div>
    </details>)}
    {running && !core.snapshot && <small role="status">{ru ? 'Загружаем группы…' : 'Loading groups…'}</small>}
    {core.error && <small role="alert">{mihomoErrorMessage(core.error, ru)}</small>}
  </div>;
}
