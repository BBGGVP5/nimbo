import type { RouteTraffic, TrafficStats } from "./api";

export type StatsRange = "session" | "month" | "all";

function counter(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? value : null;
}

export function measuredRoute(value: RouteTraffic | null | undefined) {
  if (!value || [value.proxy_upload, value.proxy_download, value.direct_upload, value.direct_download].some(v => counter(v) === null)) return null;
  const proxy = value.proxy_upload + value.proxy_download;
  const direct = value.direct_upload + value.direct_download;
  const total = proxy + direct;
  return Number.isFinite(total) ? { proxy, direct, total } : null;
}

/** No inference from selected server, connection counts, or total session bytes. */
export function routeShare(value: RouteTraffic | null | undefined): number | null {
  const measured = measuredRoute(value);
  return measured && measured.total > 0 ? measured.proxy / measured.total : null;
}

export function sessionMeasured(stats: TrafficStats | null, connected: boolean): boolean {
  if (!connected || !stats || stats.session_available === false) return false;
  // Legacy binaries have no availability flag. Require evidence before showing zero.
  return stats.session_available === true || stats.speed_available || stats.session_upload > 0 || stats.session_download > 0;
}

export function trafficDashboardValues(stats: TrafficStats | null, range: StatsRange, connected: boolean, speedAvailable: boolean) {
  const session = sessionMeasured(stats, connected);
  const prefix = range === "session" ? "session" : range === "month" ? "monthly" : "all_time";
  const totalsAvailable = stats != null && (range !== "session" || session);
  const ratesAvailable = session && speedAvailable && stats?.speed_available;
  const activeCount = (value: unknown) => session && counter(value) !== null && Number.isInteger(value) ? value as number : null;
  return {
    upload: totalsAvailable ? counter(stats[`${prefix}_upload`]) : null,
    download: totalsAvailable ? counter(stats[`${prefix}_download`]) : null,
    uploadSpeed: ratesAvailable ? counter(stats.upload_speed) : null,
    downloadSpeed: ratesAvailable ? counter(stats.download_speed) : null,
    route: range === "session" && session ? measuredRoute(stats?.route_traffic) : null,
    tcp: activeCount(stats?.tcp_connections),
    udp: activeCount(stats?.udp_connections),
  };
}

/** Both series share one scale. Empty/single-point samples do not invent a history. */
export function trafficPaths(points: { download: number; upload: number }[], width = 640, height = 180) {
  const clean = points.filter(p => Number.isFinite(p.download) && Number.isFinite(p.upload) && p.download >= 0 && p.upload >= 0);
  const max = Math.max(1, ...clean.flatMap(p => [p.download, p.upload]));
  const path = (key: "download" | "upload") => clean.length < 2 ? "" : clean.map((p, i) => `${i ? "L" : "M"}${i * width / (clean.length - 1)},${height - 8 - p[key] / max * (height - 16)}`).join(" ");
  return { download: path("download"), upload: path("upload"), max, count: clean.length };
}
