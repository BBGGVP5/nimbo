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
