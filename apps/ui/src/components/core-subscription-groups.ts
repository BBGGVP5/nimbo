import type { CoreGroup, CoreProfile, CoreSnapshot } from '../lib/coreApi';

/** Parsed display metadata remains useful even when mode-specific admission reports issues. */
export function currentSubscriptionInspection(profile: CoreProfile | undefined): CoreProfile['inspection'] {
  const inspection = profile?.inspection;
  return profile && inspection && inspection.api === 1 && inspection.sourceDigest === profile.source_digest
    && Array.isArray(inspection.graph?.groups) && Array.isArray(inspection.issues) ? inspection : null;
}

/** Disconnected metadata is a preview, never evidence of an active selection/session. */
export function subscriptionGroups(profile: CoreProfile | undefined, snapshot: CoreSnapshot | null, running: boolean): Array<[string, CoreGroup]> {
  if (running) return Object.entries(snapshot?.groups ?? {}).filter(([, group]) => !group.hidden);
  const inspection = currentSubscriptionInspection(profile);
  if (!inspection) return [];
  const declared = inspection.graph?.groups;
  if (!Array.isArray(declared)) return [];
  const groups = new Map<string, CoreGroup>();
  for (const raw of declared) {
    if (!raw || typeof raw !== 'object' || typeof raw.name !== 'string' || !raw.name || raw.hidden || typeof raw.type !== 'string') continue;
    const members = Array.isArray(raw.proxies) ? [...new Set(raw.proxies.filter((member: unknown): member is string => typeof member === 'string' && !!member))] : [];
    const selection = profile?.selections?.[raw.name];
    groups.set(raw.name, { type: raw.type === 'select' ? 'Selector' : raw.type, all: members as string[], now: members.includes(selection) ? selection : undefined });
  }
  return [...groups];
}

/** Read name/type only; the graph must never become a source/credential renderer. */
export function subscriptionMemberDetails(profile: CoreProfile | undefined, snapshot: CoreSnapshot | null, running: boolean, name: string): {type?: string; now?: string} {
  if (running) {
    const group = snapshot?.groups[name];
    if (group) return { ...(typeof group.type === 'string' ? {type: group.type} : {}), ...(typeof group.now === 'string' ? {now: group.now} : {}) };
    for (const provider of Object.values(snapshot?.providers ?? {})) {
      const proxy = provider.proxies?.find(entry => entry.name === name);
      if (typeof proxy?.type === 'string') return {type: proxy.type};
    }
  }
  const graph = currentSubscriptionInspection(profile)?.graph;
  for (const entries of [graph?.proxies, graph?.groups]) {
    if (!Array.isArray(entries)) continue;
    const entry = entries.find(value => value && typeof value === 'object' && value.name === name);
    if (typeof entry?.type === 'string') return {type: entry.type};
  }
  return name === 'DIRECT' ? {type:'Direct'} : name === 'REJECT' ? {type:'Reject'} : {};
}
