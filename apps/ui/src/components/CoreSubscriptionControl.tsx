import { useEffect, useRef } from 'react';
import { useCoreStore } from '../coreStore';
import { isTauriRuntime, type Subscription } from '../lib/api';
import { coreApi } from '../lib/coreApi';
import { mihomoErrorMessage } from '../lib/coreProfiles';
import { useMessages } from '../lib/i18n';
import { currentSubscriptionInspection, subscriptionGroups } from './core-subscription-groups';
import { MihomoProxyGroups } from './MihomoProxyGroups';
import { useMihomoPing } from './useMihomoPing';
import './core-subscription-groups.css';

/** The subscribed YAML owns routing; never present its legacy Xray nodes as Mihomo selections. */
export function CoreSubscriptionControl({ sub }: { sub: Subscription }) {
  const core = useCoreStore();
  const ru = useMessages().common.locale.startsWith('ru');
  const id = sub.meta?.mihomo_profile_id ?? undefined;
  const profile = core.data?.profiles.find(p => p.id === id);
  const running = !!id && core.runtime?.running === true && core.runtime.profile_id === id;
  const native = isTauriRuntime();
  const inspection = currentSubscriptionInspection(profile);
  const groups = subscriptionGroups(profile, running ? core.snapshot : null, running);
  const subscriptionVersion = `${id ?? ''}:${sub.fetched_at ?? ''}`;
  const requestedVersion = useRef<string | null>(null);
  const ping = useMihomoPing(id, sub.url);
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
    <MihomoProxyGroups profileId={id} profile={profile} snapshot={running ? core.snapshot : null} groups={groups} running={running} disabled={!!core.busy || !native} ru={ru} ping={ping} onSelect={(name, member) => void core.live('select', name, member)}/>
    {((running && !core.snapshot) || (!running && profile && !inspection && !core.error && native)) && <small role="status">{ru ? 'Загрузка…' : 'Loading…'}</small>}
    {!profile && core.loaded && !core.error && <small role="status">{ru ? 'Обновите подписку' : 'Refresh the subscription'}</small>}
    {((!running && inspection) || (running && core.snapshot)) && !groups.length && <small role="status">{ru ? 'Нет категорий' : 'No categories'}</small>}
    {core.error && <small role="alert">{mihomoErrorMessage(core.error, ru)}</small>}
  </div>;
}
