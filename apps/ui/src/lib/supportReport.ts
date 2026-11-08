/** Privacy boundary: project a small schema, never serialize API objects or log text. */
export interface SupportSources {
  version?: unknown; status?: unknown; device?: unknown; profiles?: unknown;
  logs?: unknown; issue?: unknown;
}
const record = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
const oneOf = (value: unknown, allowed: readonly string[]): string | null =>
  typeof value === "string" && allowed.includes(value) ? value : null;
const count = (value: unknown): number | null =>
  typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : null;
const version = (value: unknown): string | null =>
  typeof value === "string" && /^\d{1,5}(?:\.\d{1,5}){1,3}(?:-(?:alpha|beta|rc)(?:\.\d{1,5})?)?$/.test(value) ? value : null;

export function buildSupportReport(sources: SupportSources, capturedAt = new Date().toISOString()): string {
  const status = record(sources.status), device = record(sources.device), profiles = record(sources.profiles);
  const levels = {debug: 0, info: 0, warn: 0, error: 0};
  const logs = Array.isArray(sources.logs) ? sources.logs.slice(-1000) : null;
  for (const entry of logs ?? []) {
    const level = oneOf(record(entry).level, Object.keys(levels));
    if (level) levels[level as keyof typeof levels]++;
  }
  const os = typeof device.os === "string" ? device.os.toLowerCase() : "";
  return JSON.stringify({
    schema: "nimbo-support-v1", capturedAt,
    appVersion: version(sources.version), buildRevision: null,
    platform: oneOf(os, ["windows", "linux", "macos", "android", "ios"]), osVersion: version(device.os_version),
    issue: oneOf(sources.issue, ["connection", "subscription", "ping", "startup", "other"]) ?? "other",
    issueSource: "user-selected category, not an automatically detected failure",
    connection: {state: oneOf(status.state, ["disconnected", "connecting", "connected", "service_unavailable"]),
      mode: oneOf(status.connection_mode, ["system_proxy", "tun", "both"])},
    corePreference: oneOf(profiles.preferred_core, ["auto", "xray", "mihomo", "awg"]),
    coreScope: "Preference only; does not identify the running core or its version.",
    subscriptionCount: count(status.subscription_count), serverCount: count(status.server_count),
    logEventCounts: logs ? levels : null,
    sources: {version: sources.version !== undefined, status: sources.status !== undefined,
      device: sources.device !== undefined, profiles: sources.profiles !== undefined, logs: logs !== null},
    scope: "Local snapshot. At most 1000 recent log events counted; free text, config, addresses, IDs and credentials excluded. Build revision is unavailable in this desktop API.",
  }, null, 2);
}

/** A missing/failed native source remains unknown; never copy exception text into a report. */
export async function readSupportSource<T>(request: Promise<T>, timeoutMs = 5000): Promise<T | undefined> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([request.catch(() => undefined),
      new Promise<undefined>(resolve => { timer = setTimeout(() => resolve(undefined), timeoutMs); })]);
  } finally { if (timer !== undefined) clearTimeout(timer); }
}
