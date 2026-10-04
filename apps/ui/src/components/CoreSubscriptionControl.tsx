import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useCoreStore } from '../coreStore';
import { isTauriRuntime, type Subscription } from '../lib/api';
import { groupCanSelect, mihomoErrorMessage } from '../lib/coreProfiles';
import { useMessages } from '../lib/i18n';
import { NimboSelect } from './NimboSelect';

/** The subscribed YAML owns routing; never present its legacy Xray nodes as Mihomo selections. */
export function CoreSubscriptionControl({ sub }: { sub: Subscription }) {
  const core = useCoreStore();
  const ru = useMessages().common.locale.startsWith('ru');
  const id = sub.meta?.mihomo_profile_id;
  const profile = core.data?.profiles.find(p => p.id === id);
  const running = !!id && core.runtime?.running === true && core.runtime.profile_id === id;
  useEffect(() => {
    if (!isTauriRuntime()) return;
    if (!core.loaded) void core.refresh();
    else if (running && !core.snapshot && !core.busy && !core.error) void core.live('snapshot');
  }, [core.loaded, running, core.snapshot, core.busy, core.error, core.refresh, core.live]);
  return <div className="core-subscription-control" data-no-toggle>
    <div className="core-subscription-head"><strong>Mihomo</strong><span>{running ? (ru ? 'Подключён' : 'Connected') : profile ? (ru ? 'Группы и правила подписки' : 'Subscription groups and rules') : (ru ? 'Обновите подписку для Mihomo' : 'Refresh the subscription for Mihomo')}</span></div>
    {profile && <div className="core-subscription-actions"><button type="button" className="signal-btn signal-btn--sm" disabled={!!core.busy || !isTauriRuntime()} onClick={() => void (running ? core.stop() : core.connect(profile.id))}>{running ? (ru ? 'Отключить' : 'Disconnect') : (ru ? 'Подключить' : 'Connect')}</button><Link to="/mihomo">{ru ? 'Группы' : 'Groups'} ↗</Link></div>}
    {running && Object.entries(core.snapshot?.groups ?? {}).filter(([, group]) => !group.hidden).map(([name, group]) => <div className="core-subscription-group" key={name}><span>{name}</span>{groupCanSelect(group) ? <NimboSelect aria-label={name} value={group.now ?? ''} disabled={!!core.busy} onChange={e => void core.live('select', name, e.target.value)}>{!group.now && <option value="" disabled>—</option>}{(group.all ?? []).map(member => <option key={member} value={member}>{member}</option>)}</NimboSelect> : <small>{group.now ?? '—'}</small>}</div>)}
    {core.error && <small role="alert">{mihomoErrorMessage(core.error, ru)}</small>}
  </div>;
}
