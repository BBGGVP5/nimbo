export type LatencyProtocol = "tcp_connect" | "icmp" | "http_head" | "http_get" | "nimbo";
export type LatencyDisplayFormat = "numeric" | "bars" | "both" | "dots" | "ms" | "badge";
export const DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204";
export const LATENCY_URL_PRESETS = [
  { value: DEFAULT_TEST_URL, label: "Google (204)" },
  { value: "https://cp.cloudflare.com/generate_204", label: "Cloudflare (204)" },
  { value: "https://www.msftconnecttest.com/connecttest.txt", label: "Microsoft" },
] as const;

export function normalizeLatencyProtocol(value: unknown): LatencyProtocol {
  return value === "nimbo" || value === "http_get" || value === "http_head" || value === "icmp"
    ? value : "tcp_connect";
}
export function normalizeLatencyDisplay(value: unknown): LatencyDisplayFormat {
  return value === "numeric" || value === "bars" || value === "both" || value === "dots" || value === "badge"
    ? value : "ms";
}
export function normalizeLatencyUrl(value: unknown): string {
  if (typeof value !== "string") return DEFAULT_TEST_URL;
  try {
    const url = new URL(value.trim());
    return ["http:", "https:"].includes(url.protocol) && url.hostname && !url.username && !url.password
      ? value.trim() : DEFAULT_TEST_URL;
  } catch { return DEFAULT_TEST_URL; }
}
export function normalizeLatencyTimeout(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? Math.min(60000, Math.max(500, Math.round(value))) : 5000;
}
export function latencyBars(value: unknown, inProgress = false): number {
  if (inProgress || typeof value !== "number" || !Number.isFinite(value) || value < 0) return 0;
  return value < 100 ? 4 : value < 200 ? 3 : value < 400 ? 2 : 1;
}
export function latencySettingsKey(preferences: { latency_protocol?: string; latency_test_url?: string; latency_timeout_ms?: number }): string {
  return JSON.stringify([preferences.latency_protocol, preferences.latency_test_url, preferences.latency_timeout_ms]);
}
