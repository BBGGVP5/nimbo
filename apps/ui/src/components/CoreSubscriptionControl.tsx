import { useEffect, useRef } from 'react';
import { useCoreStore } from '../coreStore';
import { isTauriRuntime, type Subscription } from '../lib/api';
import { coreApi } from '../lib/coreApi';
import { groupCanSelect, mihomoErrorMessage } from '../lib/coreProfiles';
import { useMessages } from '../lib/i18n';
import { currentSubscriptionInspection, subscriptionGroups } from './core-subscription-groups';
import './core-subscription-groups.css';

/** The subscribed YAML owns routing; never present its legacy Xray nodes as Mihomo selections. */
export function CoreSubscriptionControl({ sub }: { sub: Subscription }) {
  const core = useCoreStore();
  const ru = useMessages().common.locale.startsWith('ru');
  const id = sub.meta?.mihomo_profile_id;
  const profile = core.data?.profiles.find(p => p.id === id);
  const running = !!id && core.runtime?.running === true && core.runtime.profile_id === id;
  const native = isTauriRuntime();
  const inspection = currentSubscriptionInspection(profile);
  const groups = subscriptionGroups(profile, running ? core.snapshot : null, running);
  const subscriptionVersion = `${id ?? ''}:${sub.fetched_at ?? ''}`;
  const requestedVersion = useRef<string | null>(null);
  useEffect(() => {
    if (!native || !id || core.busy || requestedVersion.current === subscriptionVersion) return;
    requestedVersion.current = subscriptionVersion;
    void core.refresh();
  }, [native, id, subscriptionVersion, core.busy, core.refresh]);
  useEffect(() => {
    if (!native || core.busy || core.error) return;
    if (!core.loaded) void core.refresh();
    else if (running && !core.snapshot) void core.live('snapshot');
    else if (!running && profile && !inspection) void core.mutate('inspect', () => coreApi.inspect(profile.id));
  }, [native, core.loaded, running, profile, inspection, core.snapshot, core.busy, core.error, core.refresh, core.live, core.mutate]);
  return <div className="core-subscription-control" data-no-toggle>
    <div className="core-subscription-head"><strong>Mihomo</strong><span>{running ? (ru ? 'Подключён' : 'Connected') : profile ? (ru ? 'Группы подписки' : 'Subscription groups') : (ru ? 'Обновите подписку для Mihomo' : 'Refresh the subscription for Mihomo')}</span>
      {profile && <button type="button" className="signal-btn signal-btn--sm" disabled={!!core.busy || !native} onClick={() => void (running ? core.stop() : core.connect(profile.id))}>{running ? (ru ? 'Отключить' : 'Disconnect') : (ru ? 'Подключить' : 'Connect')}</button>}
    </div>
    {!running && groups.length > 0 && <small>{ru ? 'Выбор сервера доступен после подключения.' : 'Server selection is available after connecting.'}</small>}
    {!running && inspection && inspection.issues.length > 0 && <details className="core-subscription-inspection">
      <summary>{ru ? 'Замечания инспектора' : 'Inspection issues'} · {inspection.issues.length}</summary>
      <small>{ru ? 'Группы показаны из YAML. Возможность подключения проверит ядро.' : 'Groups are shown from YAML. The core checks whether it can connect.'}</small>
      <ul>{inspection.issues.map((issue, index) => <li key={index}>{issue.code}{issue.path ? ` · ${issue.path}` : ''}</li>)}</ul>
    </details>}
    {!running && profile && (!inspection || !groups.length || inspection.issues.length > 0) && <div>
      <button type="button" className="signal-btn signal-btn--sm signal-btn--ghost" disabled={!!core.busy || !native} onClick={() => void core.mutate('inspect', () => coreApi.inspect(profile.id))}>{ru ? 'Проверить профиль' : 'Inspect profile'}</button>
    </div>}
    {groups.map(([name, group]) => <details className="core-subscription-group" key={`${id}:${name}`} open>
      <summary><span><strong>{name}</strong><small>{groupCanSelect(group) ? (ru ? 'Выбор сервера' : 'Server selection') : group.type}</small></span><span>{group.now ?? '—'}</span></summary>
      <div className="core-subscription-members" role="group" aria-label={name}>
        {(group.all ?? []).map(member => <button key={member} type="button" className="core-subscription-member" aria-label={`${name}: ${member}`} aria-pressed={running && group.now === member} disabled={!running || !groupCanSelect(group) || !!core.busy || !native} onClick={() => void core.live('select', name, member)}><span className="core-member-indicator" aria-hidden="true"/><span>{member}</span></button>)}
        {!group.all?.length && <small>{running ? (ru ? 'Нет доступных серверов' : 'No available servers') : (ru ? 'Состав группы — после подключения' : 'Group members are available after connecting')}</small>}
      </div>
    </details>)}
    {running && !core.snapshot && <small role="status">{ru ? 'Загружаем группы…' : 'Loading groups…'}</small>}
    {!running && profile && !inspection && !core.error && <small role="status">{native ? (ru ? 'Загружаем категории…' : 'Loading categories…') : (ru ? 'Проверьте профиль в приложении, чтобы загрузить категории.' : 'Inspect the profile in the app to load categories.')}</small>}
    {!running && inspection && !groups.length && <small role="status">{ru ? 'В этом YAML нет видимых групп.' : 'This YAML has no visible groups.'}</small>}
    {running && core.snapshot && !groups.length && <small role="status">{ru ? 'Ядро не вернуло видимые группы.' : 'The core returned no visible groups.'}</small>}
    {core.error && <small role="alert">{mihomoErrorMessage(core.error, ru)}</small>}
  </div>;
}
