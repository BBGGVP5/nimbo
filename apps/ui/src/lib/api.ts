import { invoke } from "@tauri-apps/api/core";
import { getVersion } from "@tauri-apps/api/app";
import uiPackage from "../../package.json";

export type ConnectionState =
  | "disconnected"
  | "connecting"
  | "connected"
  | "service_unavailable";

export interface AppStatus {
  state: ConnectionState;
  active_server_id: string | null;
  active_subscription_url: string | null;
  subscription_count: number;
  server_count: number;
  service_protocol: number;
  connection_mode: ConnectionMode;
  socks_port: number;
  http_port: number;
  socks_username: string;
  socks_password: string;
  require_socks_auth: boolean;
  block_socks_udp: boolean;
  server_pings: Record<string, number>;
}

export interface DeviceInfo {
  hwid: string;
  os: string;
  os_version: string;
  hostname: string;
  user_agent: string;
}

export type Network =
  | "tcp"
  | "ws"
  | "grpc"
  | "h2"
  | "xhttp"
  | "http_upgrade"
  | "quic"
  | "kcp";

export type Security = "none" | "tls" | "reality";

export interface StreamSettings {
  network: Network;
  security: Security;
  host?: string | null;
  path?: string | null;
  sni?: string | null;
  fingerprint?: string | null;
  alpn?: string[] | null;
  public_key?: string | null;
  short_id?: string | null;
  spider_x?: string | null;
  mode?: string | null;
  extra?: string | null;
  header_type?: string | null;
  service_name?: string | null;
}

export type Protocol =
  | { kind: "vless"; address: string; port: number; uuid: string; flow?: string | null; encryption: string; stream: StreamSettings }
  | { kind: "vmess"; address: string; port: number; uuid: string; alter_id: number; security: string; stream: StreamSettings }
  | { kind: "trojan"; address: string; port: number; password: string; stream: StreamSettings }
  | { kind: "shadowsocks"; address: string; port: number; method: string; password: string }
  | { kind: "hysteria2"; address: string; port: number; password: string; sni?: string | null; alpn?: string[] | null; insecure: boolean; obfs?: string | null; obfs_password?: string | null };

export interface Server {
  id: string;
  name: string;
  server_description?: string | null;
  serverDescription?: string | null;
  host_uuid?: string | null;
  xrayJsonTemplateUuid?: string | null;
  xray_json_template_uuid?: string | null;
  protocol: Protocol;
}

export interface SubscriptionInfo {
  upload?: number | null;
  download?: number | null;
  total?: number | null;
  expire?: number | null;
}

export interface SubscriptionMeta {
  description?: string | null;
  support_url?: string | null;
  website_url?: string | null;
  show_on_home?: boolean | null;
  update_interval_minutes?: number | null;
  app_proxy_rules?: AppProxyRule[];
}

export interface Subscription {
  url: string;
  name: string | null;
  meta?: SubscriptionMeta | null;
  servers: Server[];
  info: SubscriptionInfo | null;
  fetched_at: number;
}

export interface SubscriptionSettingsPatch {
  name?: string | null;
  show_on_home?: boolean | null;
  update_interval_minutes?: number | null;
}

export interface SubscriptionHeader {
  name: string;
  value: string;
}

export interface SubscriptionHeaderMetadata {
  url: string;
  status: number;
  subscription_userinfo?: string | null;
  upload?: number | null;
  download?: number | null;
  total?: number | null;
  expire?: number | null;
  profile_title?: string | null;
  support_url?: string | null;
  profile_update_interval?: string | null;
  profile_update_interval_seconds?: number | null;
  announce?: string | null;
  announce_headers: SubscriptionHeader[];
  headers: SubscriptionHeader[];
}

export interface PersistedState {
  subscriptions: Subscription[];
  active_server_id: string | null;
  active_subscription_url?: string | null;
  user_agent_override?: string | null;
  app_proxy_rules?: AppProxyRule[];
  connected?: boolean;
  connection_mode?: ConnectionMode;
  socks_username?: string;
  socks_password?: string;
  require_socks_auth?: boolean;
  block_socks_udp?: boolean;
  server_pings?: Record<string, number>;
  preferences?: AppPreferences;
}

export type ThemeMode = "system" | "dark" | "black" | "light";
export type AccentMode = "system" | "preset" | "custom";
export type UiStyle = "nebula" | "material_you";
export type AppLanguage = "ru" | "en";
export type LatencyProtocol = "tcp_connect" | "icmp" | "http_head";
export type LatencyDisplayFormat = "ms" | "badge";
export type XudpUdp443Mode = "reject" | "allow" | "skip";
export type PreferredIpFamily = "auto" | "ipv4" | "ipv6";
export type ServerSorting = "provider" | "name" | "ping" | "protocol";
export type ConnectButtonStyle = "classic" | "compact";
export type AppRoutingMode = "direct" | "proxy";

export interface AppPreferences {
  launch_at_login: boolean;
  auto_connect_on_launch: boolean;
  start_minimized: boolean;
  minimize_to_tray: boolean;
  ping_on_launch: boolean;
  check_updates_on_launch: boolean;
  provider_theme: boolean;
  ui_style: UiStyle;
  interface_panel_brightness: number;
  interface_transparency: number;
  interface_blur: number;
  interface_rounding: number;
  theme_mode: ThemeMode;
  accent_mode: AccentMode;
  accent_color: string;
  language: AppLanguage;
  latency_protocol: LatencyProtocol;
  latency_test_url: string;
  latency_timeout_ms: number;
  latency_display_format: LatencyDisplayFormat;
  app_routing_mode: AppRoutingMode;
  show_speed_chart: boolean;
  show_memory_usage: boolean;
  connection_kill_switch: boolean;
  tunnel_sniffing: boolean;
  tunnel_mux_enabled: boolean;
  tunnel_mux_concurrency: number;
  tunnel_xudp_concurrency: number;
  tunnel_xudp_udp443: XudpUdp443Mode;
  tunnel_tls_fragmentation: boolean;
  lan_allow_connections: boolean;
  lan_allow_tethering: boolean;
  lan_proxy_enabled: boolean;
  lan_tcp_idle_timeout_sec: number;
  lan_max_tcp_connections: number;
  lan_max_udp_connections: number;
  lan_preferred_ip_family: PreferredIpFamily;
  subscriptions_auto_update: boolean;
  subscriptions_update_interval_hours: number;
  subscriptions_notify_expiration: boolean;
  subscriptions_expiration_threshold_days: number;
  subscriptions_notify_updates: boolean;
  subscriptions_update_on_launch: boolean;
  subscriptions_ping_after_update: boolean;
  servers_sorting: ServerSorting;
  servers_connect_button: ConnectButtonStyle;
  servers_ui_scale: number;
  servers_proxy_only_button: boolean;
}

export type AppProxyMode = "proxy" | "direct";
export type ConnectionMode = "system_proxy" | "tun" | "both";

export interface AppProxyRule {
  id: string;
  name: string;
  executable_path: string;
  mode: AppProxyMode;
  enabled: boolean;
}

export interface InstalledApp {
  name: string;
  executable_path: string;
}

export interface ConflictingProcess {
  name: string;
  process_name: string;
  pid: number;
  path?: string | null;
  pids: number[];
}

export interface HelperStatus {
  installed: boolean;
  running: boolean;
  version: string | null;
  exe_present: boolean;
  exe_path: string | null;
}

export interface ProxySettingsPatch {
  socks_username?: string | null;
  socks_password?: string | null;
  require_socks_auth?: boolean | null;
  block_socks_udp?: boolean | null;
}

export interface ServerPing {
  server_id: string;
  latency_ms?: number | null;
  error?: string | null;
}

export interface SessionTraffic {
  upload: number;
  download: number;
}

export type ActiveConnectionRoute = "proxy" | "direct" | "block" | "unknown";

export interface ActiveConnection {
  id: string;
  protocol: string;
  state: string;
  source: string;
  destination: string;
  remote_address: string;
  remote_port: number;
  process: string;
  process_path?: string | null;
  pid: number;
  route: ActiveConnectionRoute;
  rule: string;
  server_id?: string | null;
  server_name?: string | null;
  server_protocol?: string | null;
}

export interface MemoryUsage {
  bytes: number;
}

export interface RoutingProfileSummary {
  id: string;
  name: string;
  description: string;
  builtin: boolean;
  rules_count: number;
  action: string;
  strategy: string;
}

export interface RoutingProfile {
  id: string;
  name: string;
  description: string;
  builtin: boolean;
  domain_strategy: string;
  rule_order: string;
  global_proxy: boolean;
  bypass_local_ip: boolean;
  fake_dns: boolean;
  remote_dns_ip: string;
  local_dns_ip: string;
  remote_dns_url: string;
  direct_domains: string[];
  direct_ips: string[];
  proxy_domains: string[];
  proxy_ips: string[];
  block_domains: string[];
  block_ips: string[];
  source_url: string | null;
  update_interval_hours: number;
  last_updated_at: number | null;
}

export interface RoutingProfileList {
  profiles: RoutingProfileSummary[];
  active: string;
}

export interface TrafficStats {
  session_upload: number;
  session_download: number;
  all_time_upload: number;
  all_time_download: number;
  monthly_upload: number;
  monthly_download: number;
  monthly_period: string;
}

export interface TrafficTotals {
  all_time_upload: number;
  all_time_download: number;
  monthly_upload: number;
  monthly_download: number;
  monthly_period: string;
}

export interface TunnelLogEntry {
  source: "xray" | "tun2socks" | string;
  level: "info" | "warn" | "error" | "debug" | string;
  timestamp: string | null;
  message: string;
}

export interface TunInstallStatus {
  installed: boolean;
  can_install: boolean;
  needs_admin_restart: boolean;
  tun2socks_path?: string | null;
  wintun_path?: string | null;
  missing: string[];
  message: string;
}

export interface AppUpdateAsset {
  name: string;
  download_url: string;
  size: number;
  content_type?: string | null;
}

export interface AppUpdateInfo {
  available: boolean;
  current_version: string;
  latest_version: string;
  release_name: string;
  release_notes?: string | null;
  release_url: string;
  published_at?: string | null;
  target: string;
  asset?: AppUpdateAsset | null;
  download_url?: string | null;
}

const BROWSER_PERSISTED_STATE_KEY = "nimbo.persistedState";
const DEFAULT_SOCKS_USERNAME = "nimbo";
const DEFAULT_SOCKS_PASSWORD = "nmb-preview-password";
export const APP_VERSION = typeof uiPackage.version === "string" ? uiPackage.version : "1.0.0";

function nonEmptyString(value: string | null | undefined, fallback: string): string {
  const trimmed = value?.trim();
  return trimmed ? trimmed : fallback;
}

function randomUuid(): string {
  const cryptoApi = globalThis.crypto;
  if (typeof cryptoApi?.randomUUID === "function") {
    return cryptoApi.randomUUID();
  }

  const bytes = new Uint8Array(16);
  if (typeof cryptoApi?.getRandomValues === "function") {
    cryptoApi.getRandomValues(bytes);
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256);
    }
  }

  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((value) => value.toString(16).padStart(2, "0"));
  return [
    hex.slice(0, 4).join(""),
    hex.slice(4, 6).join(""),
    hex.slice(6, 8).join(""),
    hex.slice(8, 10).join(""),
    hex.slice(10, 16).join(""),
  ].join("-");
}

export const defaultAppPreferences: AppPreferences = {
  launch_at_login: false,
  auto_connect_on_launch: false,
  start_minimized: false,
  minimize_to_tray: true,
  ping_on_launch: true,
  check_updates_on_launch: true,
  provider_theme: true,
  ui_style: "nebula",
  interface_panel_brightness: 100,
  interface_transparency: 0,
  interface_blur: 25,
  interface_rounding: 100,
  theme_mode: "system",
  accent_mode: "preset",
  accent_color: "#7c5dfa",
  language: "ru",
  latency_protocol: "tcp_connect",
  latency_test_url: "https://www.gstatic.com/generate_204",
  latency_timeout_ms: 5000,
  latency_display_format: "ms",
  app_routing_mode: "direct",
  show_speed_chart: true,
  show_memory_usage: false,
  connection_kill_switch: false,
  tunnel_sniffing: true,
  tunnel_mux_enabled: false,
  tunnel_mux_concurrency: 8,
  tunnel_xudp_concurrency: -1,
  tunnel_xudp_udp443: "reject",
  tunnel_tls_fragmentation: false,
  lan_allow_connections: true,
  lan_allow_tethering: false,
  lan_proxy_enabled: false,
  lan_tcp_idle_timeout_sec: 60,
  lan_max_tcp_connections: 256,
  lan_max_udp_connections: 128,
  lan_preferred_ip_family: "auto",
  subscriptions_auto_update: true,
  subscriptions_update_interval_hours: 6,
  subscriptions_notify_expiration: true,
  subscriptions_expiration_threshold_days: 3,
  subscriptions_notify_updates: true,
  subscriptions_update_on_launch: false,
  subscriptions_ping_after_update: false,
  servers_sorting: "provider",
  servers_connect_button: "classic",
  servers_ui_scale: 100,
  servers_proxy_only_button: false,
};

function normalizePreferences(value: Partial<AppPreferences> | null | undefined): AppPreferences {
  const theme =
    value?.theme_mode === "dark" ||
    value?.theme_mode === "black" ||
    value?.theme_mode === "light" ||
    value?.theme_mode === "system"
    ? value.theme_mode
    : defaultAppPreferences.theme_mode;
  const accentMode = value?.accent_mode === "system" || value?.accent_mode === "preset" || value?.accent_mode === "custom"
    ? value.accent_mode
    : defaultAppPreferences.accent_mode;
  const uiStyle = value?.ui_style === "material_you" || value?.ui_style === "nebula"
    ? value.ui_style
    : defaultAppPreferences.ui_style;
  const language = value?.language === "en" || value?.language === "ru"
    ? value.language
    : defaultAppPreferences.language;
  const accent = typeof value?.accent_color === "string" && /^#[0-9a-f]{6}$/i.test(value.accent_color)
    ? value.accent_color.toLowerCase()
    : defaultAppPreferences.accent_color;
  const latencyProtocol =
    value?.latency_protocol === "tcp_connect" ||
    value?.latency_protocol === "icmp" ||
    value?.latency_protocol === "http_head"
      ? value.latency_protocol
      : defaultAppPreferences.latency_protocol;
  const latencyTestUrl =
    typeof value?.latency_test_url === "string" && /^https?:\/\//i.test(value.latency_test_url.trim())
      ? value.latency_test_url.trim()
      : defaultAppPreferences.latency_test_url;
  const latencyTimeoutMs =
    typeof value?.latency_timeout_ms === "number" && Number.isFinite(value.latency_timeout_ms)
      ? Math.min(60000, Math.max(500, Math.round(value.latency_timeout_ms)))
      : defaultAppPreferences.latency_timeout_ms;
  const latencyDisplayFormat = value?.latency_display_format === "badge" || value?.latency_display_format === "ms"
    ? value.latency_display_format
    : defaultAppPreferences.latency_display_format;
  const appRoutingMode = value?.app_routing_mode === "proxy" ? "proxy" : defaultAppPreferences.app_routing_mode;
  const xudpUdp443 =
    value?.tunnel_xudp_udp443 === "allow" ||
    value?.tunnel_xudp_udp443 === "skip" ||
    value?.tunnel_xudp_udp443 === "reject"
      ? value.tunnel_xudp_udp443
      : defaultAppPreferences.tunnel_xudp_udp443;
  const preferredIpFamily =
    value?.lan_preferred_ip_family === "ipv4" ||
    value?.lan_preferred_ip_family === "ipv6" ||
    value?.lan_preferred_ip_family === "auto"
      ? value.lan_preferred_ip_family
      : defaultAppPreferences.lan_preferred_ip_family;
  const serversSorting =
    value?.servers_sorting === "name" ||
    value?.servers_sorting === "ping" ||
    value?.servers_sorting === "protocol" ||
    value?.servers_sorting === "provider"
      ? value.servers_sorting
      : defaultAppPreferences.servers_sorting;
  const connectButton =
    value?.servers_connect_button === "compact" || value?.servers_connect_button === "classic"
      ? value.servers_connect_button
      : defaultAppPreferences.servers_connect_button;

  const clampNumber = (
    raw: unknown,
    fallback: number,
    min: number,
    max: number,
    round = true,
  ) => {
    const numeric = typeof raw === "number" ? raw : Number(raw);
    if (!Number.isFinite(numeric)) return fallback;
    const normalized = round ? Math.round(numeric) : numeric;
    return Math.min(max, Math.max(min, normalized));
  };

  return {
    ...defaultAppPreferences,
    ...(value ?? {}),
    theme_mode: theme,
    accent_mode: accentMode,
    language,
    accent_color: accent,
    launch_at_login: Boolean(value?.launch_at_login),
    auto_connect_on_launch: Boolean(value?.auto_connect_on_launch),
    start_minimized: Boolean(value?.start_minimized),
    minimize_to_tray: value?.minimize_to_tray !== false,
    ping_on_launch: value?.ping_on_launch !== false,
    check_updates_on_launch: value?.check_updates_on_launch !== false,
    provider_theme: value?.provider_theme !== false,
    ui_style: uiStyle,
    interface_panel_brightness: clampNumber(value?.interface_panel_brightness, defaultAppPreferences.interface_panel_brightness, 60, 140),
    interface_transparency: clampNumber(value?.interface_transparency, defaultAppPreferences.interface_transparency, 0, 80),
    interface_blur: clampNumber(value?.interface_blur, defaultAppPreferences.interface_blur, 0, 48),
    interface_rounding: clampNumber(value?.interface_rounding, defaultAppPreferences.interface_rounding, 50, 180),
    latency_protocol: latencyProtocol,
    latency_test_url: latencyTestUrl,
    latency_timeout_ms: latencyTimeoutMs,
    latency_display_format: latencyDisplayFormat,
    app_routing_mode: appRoutingMode,
    show_speed_chart: value?.show_speed_chart !== false,
    show_memory_usage: Boolean(value?.show_memory_usage),
    connection_kill_switch: Boolean(value?.connection_kill_switch),
    tunnel_sniffing: value?.tunnel_sniffing !== false,
    tunnel_mux_enabled: Boolean(value?.tunnel_mux_enabled),
    tunnel_mux_concurrency: clampNumber(value?.tunnel_mux_concurrency, defaultAppPreferences.tunnel_mux_concurrency, 1, 1024),
    tunnel_xudp_concurrency: clampNumber(value?.tunnel_xudp_concurrency, defaultAppPreferences.tunnel_xudp_concurrency, -1, 1024),
    tunnel_xudp_udp443: xudpUdp443,
    tunnel_tls_fragmentation: Boolean(value?.tunnel_tls_fragmentation),
    lan_allow_connections: value?.lan_allow_connections !== false,
    lan_allow_tethering: Boolean(value?.lan_allow_tethering),
    lan_proxy_enabled: Boolean(value?.lan_proxy_enabled),
    lan_tcp_idle_timeout_sec: clampNumber(value?.lan_tcp_idle_timeout_sec, defaultAppPreferences.lan_tcp_idle_timeout_sec, 5, 3600),
    lan_max_tcp_connections: clampNumber(value?.lan_max_tcp_connections, defaultAppPreferences.lan_max_tcp_connections, 1, 100000),
    lan_max_udp_connections: clampNumber(value?.lan_max_udp_connections, defaultAppPreferences.lan_max_udp_connections, 1, 100000),
    lan_preferred_ip_family: preferredIpFamily,
    subscriptions_auto_update: value?.subscriptions_auto_update !== false,
    subscriptions_update_interval_hours: clampNumber(value?.subscriptions_update_interval_hours, defaultAppPreferences.subscriptions_update_interval_hours, 1, 168),
    subscriptions_notify_expiration: value?.subscriptions_notify_expiration !== false,
    subscriptions_expiration_threshold_days: clampNumber(value?.subscriptions_expiration_threshold_days, defaultAppPreferences.subscriptions_expiration_threshold_days, 1, 365),
    subscriptions_notify_updates: value?.subscriptions_notify_updates !== false,
    subscriptions_update_on_launch: Boolean(value?.subscriptions_update_on_launch),
    subscriptions_ping_after_update: Boolean(value?.subscriptions_ping_after_update),
    servers_sorting: serversSorting,
    servers_connect_button: connectButton,
    servers_ui_scale: clampNumber(value?.servers_ui_scale, defaultAppPreferences.servers_ui_scale, 80, 125),
    servers_proxy_only_button: Boolean(value?.servers_proxy_only_button),
  };
}

export function isTauriRuntime(): boolean {
  if (typeof window === "undefined") return false;
  const tauriInternals = (window as typeof window & { __TAURI_INTERNALS__?: { invoke?: unknown } }).__TAURI_INTERNALS__;
  return typeof tauriInternals?.invoke === "function";
}

function readBrowserJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function writeBrowserJson<T>(key: string, value: T): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* browser storage may be unavailable */
  }
}

function browserPersistedState(): PersistedState {
  const stored = readBrowserJson<Partial<PersistedState> | null>(BROWSER_PERSISTED_STATE_KEY, null);
  return {
    subscriptions: Array.isArray(stored?.subscriptions) ? stored.subscriptions : [],
    active_server_id: stored?.active_server_id ?? null,
    active_subscription_url: stored?.active_subscription_url ?? null,
    user_agent_override: stored?.user_agent_override ?? null,
    app_proxy_rules: Array.isArray(stored?.app_proxy_rules) ? stored.app_proxy_rules : [],
    connected: Boolean(stored?.connected),
    connection_mode: stored?.connection_mode ?? "tun",
    socks_username: nonEmptyString(stored?.socks_username, DEFAULT_SOCKS_USERNAME),
    socks_password: nonEmptyString(stored?.socks_password, DEFAULT_SOCKS_PASSWORD),
    require_socks_auth: Boolean(stored?.require_socks_auth),
    block_socks_udp: Boolean(stored?.block_socks_udp),
    server_pings: stored?.server_pings && typeof stored.server_pings === "object" ? stored.server_pings : {},
    preferences: normalizePreferences(stored?.preferences),
  };
}

function writeBrowserPersistedState(state: PersistedState): PersistedState {
  writeBrowserJson(BROWSER_PERSISTED_STATE_KEY, state);
  return state;
}

function isSingleProxyLink(value: string): boolean {
  return /^(vless|vmess|trojan|ss|hysteria2|hy2):\/\//i.test(value.trim());
}

function fallbackServerFromProxyLink(value: string): Server {
  const fallbackName = "Импортированный сервер";
  const fallbackAddress = "proxy.local";
  const fallbackPort = 443;

  try {
    const u = new URL(value);
    const q = new URLSearchParams(u.search);
    const hashName = decodeURIComponent((u.hash || "").replace(/^#/, "")).trim();
    const address = u.hostname || fallbackAddress;
    const port = Number(u.port) || fallbackPort;
    const uuid = q.get("id") || randomUuid();
    const network = (q.get("type") as Network | null) || "tcp";
    const security = (q.get("security") as Security | null) || "tls";

    if (u.protocol === "hysteria2:" || u.protocol === "hy2:") {
      return {
        id: randomUuid(),
        name: hashName || "Hysteria2",
        server_description: queryValue(q, ["serverDescription", "server_description", "server-description"]),
        host_uuid: queryValue(q, ["hostUuid", "host_uuid", "host-uuid"]),
        xray_json_template_uuid: queryValue(q, [
          "xrayJsonTemplateUuid",
          "xray_json_template_uuid",
          "xray-json-template-uuid",
        ]),
        protocol: {
          kind: "hysteria2",
          address,
          port,
          password: q.get("auth") || decodeURIComponent(u.username || ""),
          sni: q.get("sni") || q.get("peer"),
          alpn: q.get("alpn")?.split(",").map((item) => item.trim()).filter(Boolean) || null,
          insecure: q.get("insecure") === "1" || q.get("insecure") === "true",
          obfs: q.get("obfs"),
          obfs_password: q.get("obfs-password") || q.get("obfs_password"),
        },
      };
    }

    return {
      id: randomUuid(),
      name: hashName || fallbackName,
      server_description: queryValue(q, ["serverDescription", "server_description", "server-description"]),
      host_uuid: queryValue(q, ["hostUuid", "host_uuid", "host-uuid"]),
      xray_json_template_uuid: queryValue(q, [
        "xrayJsonTemplateUuid",
        "xray_json_template_uuid",
        "xray-json-template-uuid",
      ]),
      protocol: {
        kind: "vless",
        address,
        port,
        uuid,
        encryption: "none",
        stream: {
          network,
          security,
          host: q.get("host"),
          path: q.get("path"),
          sni: q.get("sni"),
          fingerprint: q.get("fp"),
          alpn: null,
          public_key: q.get("pbk"),
          short_id: q.get("sid"),
          spider_x: q.get("spx"),
          mode: q.get("mode"),
          extra: null,
          header_type: q.get("headerType"),
          service_name: q.get("serviceName"),
        },
      },
    };
  } catch {
    return {
      id: randomUuid(),
      name: fallbackName,
      server_description: null,
      host_uuid: null,
      xray_json_template_uuid: null,
      protocol: {
        kind: "vless",
        address: fallbackAddress,
        port: fallbackPort,
          uuid: randomUuid(),
        encryption: "none",
        stream: { network: "tcp", security: "tls" },
      },
    };
  }
}

function queryValue(params: URLSearchParams, keys: string[]): string | null {
  for (const key of keys) {
    const direct = params.get(key)?.trim();
    if (direct) return direct;
    const normalizedKey = normalizeParamKey(key);
    for (const [candidate, value] of params) {
      if (normalizeParamKey(candidate) === normalizedKey) {
        const trimmed = value.trim();
        if (trimmed) return trimmed;
      }
    }
  }
  return null;
}

function normalizeParamKey(value: string): string {
  return value.replace(/[-_]/g, "").toLowerCase();
}

function fallbackSubscriptionName(source: string, customName?: string | null): string | null {
  const named = customName?.trim();
  if (named) return named;
  if (isSingleProxyLink(source)) return "Proxy link";

  try {
    const u = new URL(source);
    const fromHash = decodeURIComponent((u.hash || "").replace(/^#/, "")).trim();
    if (fromHash) return fromHash;

    const pathParts = u.pathname.split("/").filter(Boolean);
    const lastPath = (pathParts.length ? pathParts[pathParts.length - 1] : "").trim();
    if (lastPath) {
      return lastPath.replace(/\.(txt|json|ya?ml)$/i, "");
    }
  } catch {
    /* ignore */
  }

  try {
    const host = new URL(source).hostname;
    return host || "Подписка";
  } catch {
    return "Подписка";
  }
}

function browserDeviceInfo(): DeviceInfo {
  const stored = readBrowserJson<DeviceInfo | null>("nimbo.deviceInfo", null);
  if (stored) return stored;

  const device: DeviceInfo = {
    hwid: randomUuid(),
    os: "Windows",
    os_version: navigator.platform || "unknown",
    hostname: "browser-preview",
    user_agent: `Nimbo/${APP_VERSION}`,
  };
  writeBrowserJson("nimbo.deviceInfo", device);
  return device;
}

function browserUpdateInfo(): AppUpdateInfo {
  const params = new URLSearchParams(window.location.search);
  const mockUpdate = params.has("mockUpdate") || readBrowserJson<boolean>("nimbo.mockUpdate", false);
  if (mockUpdate) {
    return {
      available: true,
      current_version: APP_VERSION,
      latest_version: "3.0.33",
      release_name: "3.0.33",
      release_notes: "Демо обновления для браузерного предпросмотра.",
      release_url: "https://github.com/Case211/nimbo-app/releases",
      published_at: new Date().toISOString(),
      target: "Windows x64",
      asset: {
        name: "Nimbo_3.0.33_x64-setup.exe",
        download_url: "https://github.com/Case211/nimbo-app/releases",
        size: 0,
        content_type: "application/octet-stream",
      },
      download_url: "https://github.com/Case211/nimbo-app/releases",
    };
  }

  return {
    available: false,
    current_version: APP_VERSION,
    latest_version: APP_VERSION,
    release_name: APP_VERSION,
    release_notes: null,
    release_url: "https://github.com/Case211/nimbo-app/releases",
    published_at: null,
    target: "Browser preview",
    asset: null,
    download_url: null,
  };
}

function browserConflictingProcesses(): ConflictingProcess[] {
  const params = new URLSearchParams(window.location.search);
  const dismissed = readBrowserJson<boolean>("nimbo.mockConflictsDismissed", false);
  const mockConflict = params.has("mockConflict") || (readBrowserJson<boolean>("nimbo.mockConflict", false) && !dismissed);
  if (!mockConflict) return [];

  return [
    {
      name: "Cloudflare WARP",
      process_name: "warp-svc.exe",
      pid: 4820,
      path: "C:\\Program Files\\Cloudflare\\Cloudflare WARP\\warp-svc.exe",
      pids: [4820],
    },
    {
      name: "FlClash",
      process_name: "FlClashCore.exe",
      pid: 6144,
      path: "C:\\Users\\User\\AppData\\Local\\FlClash\\FlClashCore.exe",
      pids: [6144],
    },
  ];
}

function currentBrowserMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function browserRoutingProfiles(): RoutingProfileList {
  const stored = readBrowserJson<string | null>("nimbo.activeRoutingProfile", null);
  const overrides = readBrowserJson<Record<string, RoutingProfile>>("nimbo.routingProfileDetails", {});
  const seeds: Array<Omit<RoutingProfileSummary, "rules_count"> & { rules_count?: number }> = [
    { id: "global", name: "Глобальный", description: "Весь трафик через VPN", builtin: true, rules_count: 1, action: "block-proxy-direct", strategy: "AsIs" },
    { id: "bypass_lan", name: "Обход LAN", description: "Локальные адреса напрямую", builtin: true, rules_count: 7, action: "block-proxy-direct", strategy: "AsIs" },
    { id: "china_direct", name: "Китай", description: "Китайские сайты напрямую", builtin: true, rules_count: 8, action: "block-proxy-direct", strategy: "IPIfNonMatch" },
    { id: "russia_direct", name: "Россия", description: "Российские ресурсы напрямую", builtin: true, rules_count: 30, action: "block-proxy-direct", strategy: "IPIfNonMatch" },
    { id: "roscomvpn", name: "RoscomVPN", description: "Заблокированные в РФ ресурсы — через VPN, остальное напрямую", builtin: true, rules_count: 200, action: "block-direct-proxy", strategy: "IPIfNonMatch" },
  ];
  const builtinIds = new Set(seeds.map((s) => s.id));
  const customSummaries: RoutingProfileSummary[] = Object.values(overrides)
    .filter((p) => !builtinIds.has(p.id))
    .map((p) => ({
      id: p.id,
      name: p.name,
      description: p.description,
      builtin: false,
      rules_count: p.direct_domains.length + p.direct_ips.length + p.proxy_domains.length + p.proxy_ips.length + p.block_domains.length + p.block_ips.length,
      action: p.rule_order,
      strategy: p.domain_strategy,
    }));
  const profiles = [
    ...seeds.map((seed) => {
        const override = overrides[seed.id];
        if (override) {
          return {
            id: override.id,
            name: override.name,
            description: override.description,
            builtin: true,
            rules_count: override.direct_domains.length + override.direct_ips.length + override.proxy_domains.length + override.proxy_ips.length + override.block_domains.length + override.block_ips.length,
            action: override.rule_order,
            strategy: override.domain_strategy,
          } satisfies RoutingProfileSummary;
        }
        return seed as RoutingProfileSummary;
      }),
    ...customSummaries,
  ];
  const active = stored && profiles.some((profile) => profile.id === stored) ? stored : "global";
  return { profiles, active };
}

function browserRoutingProfileDetail(id: string): RoutingProfile {
  const overrides = readBrowserJson<Record<string, RoutingProfile>>("nimbo.routingProfileDetails", {});
  if (overrides[id]) return overrides[id];
  const defaults: Record<string, Partial<RoutingProfile>> = {
    global: { name: "Глобальный", description: "Весь трафик через VPN", domain_strategy: "AsIs", rule_order: "block-proxy-direct", global_proxy: true, bypass_local_ip: true, fake_dns: false, block_domains: ["geosite:category-ads"] },
    bypass_lan: { name: "Обход LAN", description: "Локальные адреса напрямую", domain_strategy: "AsIs", rule_order: "block-proxy-direct", global_proxy: true, bypass_local_ip: true, direct_ips: ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"], block_domains: ["geosite:category-ads"] },
    china_direct: { name: "Китай", description: "Китайские сайты напрямую", domain_strategy: "IPIfNonMatch", rule_order: "block-proxy-direct", direct_domains: ["geosite:cn"], direct_ips: ["geoip:cn"], block_domains: ["geosite:category-ads-all"] },
    russia_direct: { name: "Россия", description: "Российские ресурсы напрямую", domain_strategy: "IPIfNonMatch", rule_order: "block-proxy-direct", direct_domains: ["domain:ru", "domain:yandex.ru"], direct_ips: ["geoip:ru"], block_domains: ["geosite:category-ads-all"] },
    roscomvpn: { name: "RoscomVPN", description: "Заблокированные в РФ ресурсы — через VPN, остальное напрямую", domain_strategy: "IPIfNonMatch", rule_order: "block-direct-proxy", proxy_domains: ["domain:openai.com", "domain:chatgpt.com", "domain:claude.ai"], direct_ips: ["geoip:ru"], block_domains: ["geosite:category-ads-all"], source_url: "https://raw.githubusercontent.com/hydraponique/roscomvpn-routing/main/profile.json" },
  };
  const seed = defaults[id] ?? {};
  return {
    id,
    name: seed.name ?? id,
    description: seed.description ?? "",
    builtin: id in defaults,
    domain_strategy: seed.domain_strategy ?? "AsIs",
    rule_order: seed.rule_order ?? "block-proxy-direct",
    global_proxy: seed.global_proxy ?? true,
    bypass_local_ip: seed.bypass_local_ip ?? true,
    fake_dns: seed.fake_dns ?? false,
    remote_dns_ip: seed.remote_dns_ip ?? "1.1.1.1",
    local_dns_ip: seed.local_dns_ip ?? "8.8.8.8",
    remote_dns_url: seed.remote_dns_url ?? "https://1.1.1.1/dns-query",
    direct_domains: seed.direct_domains ?? [],
    direct_ips: seed.direct_ips ?? [],
    proxy_domains: seed.proxy_domains ?? [],
    proxy_ips: seed.proxy_ips ?? [],
    block_domains: seed.block_domains ?? [],
    block_ips: seed.block_ips ?? [],
    source_url: seed.source_url ?? null,
    update_interval_hours: seed.update_interval_hours ?? 24,
    last_updated_at: seed.last_updated_at ?? null,
  };
}

function browserTrafficStats(): TrafficStats {
  const totals = readBrowserJson<TrafficTotals>("nimbo.trafficTotals", {
    all_time_upload: 0,
    all_time_download: 0,
    monthly_upload: 0,
    monthly_download: 0,
    monthly_period: currentBrowserMonth(),
  });
  const session = readBrowserJson<SessionTraffic>("nimbo.sessionTraffic", { upload: 0, download: 0 });
  const month = currentBrowserMonth();
  const valid = totals.monthly_period === month;
  return {
    session_upload: session.upload,
    session_download: session.download,
    all_time_upload: totals.all_time_upload + session.upload,
    all_time_download: totals.all_time_download + session.download,
    monthly_upload: (valid ? totals.monthly_upload : 0) + session.upload,
    monthly_download: (valid ? totals.monthly_download : 0) + session.download,
    monthly_period: month,
  };
}

function browserTunnelLogs(limit: number): TunnelLogEntry[] {
  const stored = readBrowserJson<TunnelLogEntry[]>("nimbo.tunnelLogs", []);
  if (stored.length) return stored.slice(-limit);
  const sample: TunnelLogEntry[] = [];
  const now = new Date();
  for (let i = 0; i < Math.min(limit, 12); i++) {
    const ts = new Date(now.getTime() - (12 - i) * 4000);
    sample.push({
      source: i % 2 === 0 ? "xray" : "tun2socks",
      level: i === 4 || i === 9 ? "warn" : "info",
      timestamp: ts.toISOString().replace("T", " ").slice(0, 19).replace(/-/g, "/"),
      message:
        i === 4
          ? "[Warning] app/proxyman/inbound: connection ends > ..."
          : i === 9
            ? "[Warning] app/observatory/burst: error ping https://www.gstatic.com/generate_204"
            : "[Info] core: tunnel established",
    });
  }
  return sample;
}

function browserActiveConnections(): ActiveConnection[] {
  const current = browserPersistedState();
  const activeServer = current.subscriptions
    .flatMap((sub) => sub.servers)
    .find((server) => server.id === current.active_server_id);
  const serverName = activeServer?.name ?? "Demo Server";
  const serverId = activeServer?.id ?? "demo-server";
  const protocol = activeServer ? protocolLabel(activeServer.protocol) : "VLESS";
  return [
    {
      id: "demo-telegram",
      protocol: "TCP",
      state: "Established",
      source: "127.0.0.1:12709",
      destination: "149.154.167.41:443",
      remote_address: "149.154.167.41",
      remote_port: 443,
      process: "Telegram.exe",
      process_path: "C:\\Users\\User\\AppData\\Roaming\\Telegram Desktop\\Telegram.exe",
      pid: 4920,
      route: "proxy",
      rule: "fallback",
      server_id: serverId,
      server_name: serverName,
      server_protocol: protocol,
    },
    {
      id: "demo-browser",
      protocol: "TCP",
      state: "Established",
      source: "127.0.0.1:15343",
      destination: "142.251.36.67:443",
      remote_address: "142.251.36.67",
      remote_port: 443,
      process: "firefox.exe",
      process_path: "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
      pid: 7612,
      route: "direct",
      rule: "process rule",
      server_id: null,
      server_name: null,
      server_protocol: null,
    },
    {
      id: "demo-xray",
      protocol: "TCP",
      state: "Established",
      source: "192.168.1.40:55210",
      destination: "203.0.113.10:443",
      remote_address: "203.0.113.10",
      remote_port: 443,
      process: "xray.exe",
      process_path: "C:\\Users\\User\\AppData\\Roaming\\Nimbo\\bin\\xray.exe",
      pid: 8844,
      route: "proxy",
      rule: "xray outbound",
      server_id: serverId,
      server_name: serverName,
      server_protocol: protocol,
    },
  ];
}

const browserInstalledApps: InstalledApp[] = [
  { name: "Google Chrome", executable_path: "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" },
  { name: "Telegram", executable_path: "C:\\Users\\User\\AppData\\Roaming\\Telegram Desktop\\Telegram.exe" },
  { name: "Steam", executable_path: "C:\\Program Files (x86)\\Steam\\steam.exe" },
  { name: "Discord", executable_path: "C:\\Users\\User\\AppData\\Local\\Discord\\app.exe" },
  { name: "Android Studio", executable_path: "C:\\Program Files\\Android\\Android Studio\\bin\\studio64.exe" },
];

function downloadBrowserTextFile(fileName: string, contents: string): string | null {
  if (typeof document === "undefined") return null;

  const blob = new Blob([contents], { type: "application/json;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.rel = "noopener";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  return fileName;
}

export const api = {
  appReady: () =>
    isTauriRuntime()
      ? invoke<void>("app_ready")
      : Promise.resolve(),
  getAppVersion: () =>
    isTauriRuntime()
      ? getVersion().catch(() => APP_VERSION)
      : Promise.resolve(APP_VERSION),
  getPreferences: () =>
    isTauriRuntime()
      ? invoke<AppPreferences>("get_preferences")
      : Promise.resolve(browserPersistedState().preferences ?? defaultAppPreferences),
  setPreferences: (preferences: AppPreferences) =>
    isTauriRuntime()
      ? invoke<AppPreferences>("set_preferences", { preferences })
      : Promise.resolve((() => {
          const current = browserPersistedState();
          const normalized = normalizePreferences(preferences);
          writeBrowserPersistedState({ ...current, preferences: normalized });
          return normalized;
        })()),
  exportAppBackup: () =>
    isTauriRuntime()
      ? invoke<string>("export_app_backup")
      : Promise.resolve(JSON.stringify({
          schema: "nimbo-backup-v1",
          app: "Nimbo",
          exported_at: new Date().toISOString(),
          state: { ...browserPersistedState(), connected: false },
        }, null, 2)),
  importAppBackup: (payload: string) =>
    isTauriRuntime()
      ? invoke<PersistedState>("import_app_backup", { payload })
      : Promise.resolve((() => {
          const parsed = JSON.parse(payload) as { state?: PersistedState } | PersistedState;
          const next = ("state" in parsed && parsed.state ? parsed.state : parsed) as PersistedState;
          const normalized: PersistedState = {
            ...browserPersistedState(),
            ...next,
            connected: false,
            preferences: normalizePreferences(next.preferences),
          };
          return writeBrowserPersistedState(normalized);
        })()),
  refreshTrayMenu: () =>
    isTauriRuntime()
      ? invoke<void>("refresh_tray_menu")
      : Promise.resolve(),
  getStatus: () =>
    isTauriRuntime()
      ? invoke<AppStatus>("get_status")
      : Promise.resolve((() => {
          const state = browserPersistedState();
          const serverCount = state.subscriptions.reduce((sum, sub) => sum + sub.servers.length, 0);
          return {
            state: state.connected ? "connected" : "disconnected",
            active_server_id: state.active_server_id,
            active_subscription_url: state.active_subscription_url ?? null,
            subscription_count: state.subscriptions.length,
            server_count: serverCount,
            service_protocol: 1,
            connection_mode: state.connection_mode ?? "tun",
            socks_port: 10808,
            http_port: 10809,
            socks_username: nonEmptyString(state.socks_username, DEFAULT_SOCKS_USERNAME),
            socks_password: nonEmptyString(state.socks_password, DEFAULT_SOCKS_PASSWORD),
            require_socks_auth: Boolean(state.require_socks_auth),
            block_socks_udp: Boolean(state.block_socks_udp),
            server_pings: state.server_pings ?? {},
          } satisfies AppStatus;
        })()),
  getSessionTraffic: () =>
    isTauriRuntime()
      ? invoke<SessionTraffic>("get_session_traffic")
      : Promise.resolve(readBrowserJson<SessionTraffic>("nimbo.sessionTraffic", { upload: 0, download: 0 })),
  listActiveConnections: () =>
    isTauriRuntime()
      ? invoke<ActiveConnection[]>("list_active_connections")
      : Promise.resolve(browserActiveConnections()),
  getMemoryUsage: () =>
    isTauriRuntime()
      ? invoke<MemoryUsage>("get_memory_usage")
      : Promise.resolve((() => {
          const base = 220 * 1024 * 1024;
          const jitter = Math.floor(Math.random() * 30 * 1024 * 1024);
          return { bytes: base + jitter } satisfies MemoryUsage;
        })()),
  listRoutingProfiles: () =>
    isTauriRuntime()
      ? invoke<RoutingProfileList>("list_routing_profiles")
      : Promise.resolve(browserRoutingProfiles()),
  setActiveRoutingProfile: (profileId: string) =>
    isTauriRuntime()
      ? invoke<RoutingProfileList>("set_active_routing_profile", { profileId })
      : Promise.resolve((() => {
          writeBrowserJson("nimbo.activeRoutingProfile", profileId);
          return { ...browserRoutingProfiles(), active: profileId };
        })()),
  getRoutingProfile: (profileId: string) =>
    isTauriRuntime()
      ? invoke<RoutingProfile>("get_routing_profile", { profileId })
      : Promise.resolve(browserRoutingProfileDetail(profileId)),
  updateRoutingProfile: (profile: RoutingProfile) =>
    isTauriRuntime()
      ? invoke<RoutingProfile>("update_routing_profile", { profile })
      : Promise.resolve((() => {
          const map = readBrowserJson<Record<string, RoutingProfile>>("nimbo.routingProfileDetails", {});
          map[profile.id] = profile;
          writeBrowserJson("nimbo.routingProfileDetails", map);
          return profile;
        })()),
  deleteRoutingProfile: (profileId: string) =>
    isTauriRuntime()
      ? invoke<RoutingProfileList>("delete_routing_profile", { profileId })
      : Promise.resolve((() => {
          const map = readBrowserJson<Record<string, RoutingProfile>>("nimbo.routingProfileDetails", {});
          delete map[profileId];
          writeBrowserJson("nimbo.routingProfileDetails", map);
          if (readBrowserJson<string | null>("nimbo.activeRoutingProfile", null) === profileId) {
            writeBrowserJson("nimbo.activeRoutingProfile", "global");
          }
          return browserRoutingProfiles();
        })()),
  exportRoutingProfile: (profileId: string) =>
    isTauriRuntime()
      ? invoke<string>("export_routing_profile", { profileId })
      : Promise.resolve(JSON.stringify(browserRoutingProfileDetail(profileId), null, 2)),
  importRoutingProfile: (payload: string) =>
    isTauriRuntime()
      ? invoke<RoutingProfile>("import_routing_profile", { payload })
      : Promise.resolve((() => {
          const trimmed = payload.trim();
          let json = trimmed;
          if (!trimmed.startsWith("{")) {
            json = atob(trimmed.replace(/\s+/g, "").replace(/-/g, "+").replace(/_/g, "/"));
          }
          const parsed = JSON.parse(json) as RoutingProfile;
          if (!parsed.id) parsed.id = `custom-${Date.now().toString(36)}`;
          parsed.builtin = false;
          const map = readBrowserJson<Record<string, RoutingProfile>>("nimbo.routingProfileDetails", {});
          map[parsed.id] = parsed;
          writeBrowserJson("nimbo.routingProfileDetails", map);
          return parsed;
        })()),
  resetBuiltinRoutingProfiles: () =>
    isTauriRuntime()
      ? invoke<RoutingProfileList>("reset_builtin_routing_profiles")
      : Promise.resolve((() => {
          writeBrowserJson("nimbo.routingProfileDetails", {});
          return browserRoutingProfiles();
        })()),
  openRoutingFolder: () =>
    isTauriRuntime()
      ? invoke<void>("open_routing_folder")
      : Promise.resolve(),
  getTrafficStats: () =>
    isTauriRuntime()
      ? invoke<TrafficStats>("get_traffic_stats")
      : Promise.resolve(browserTrafficStats()),
  resetTrafficTotals: () =>
    isTauriRuntime()
      ? invoke<TrafficTotals>("reset_traffic_totals")
      : Promise.resolve((() => {
          const totals: TrafficTotals = {
            all_time_upload: 0,
            all_time_download: 0,
            monthly_upload: 0,
            monthly_download: 0,
            monthly_period: currentBrowserMonth(),
          };
          writeBrowserJson("nimbo.trafficTotals", totals);
          return totals;
        })()),
  getTunnelLogs: (limit?: number) =>
    isTauriRuntime()
      ? invoke<TunnelLogEntry[]>("get_tunnel_logs", { limit: limit ?? null })
      : Promise.resolve(browserTunnelLogs(limit ?? 50)),
  clearTunnelLogs: () =>
    isTauriRuntime()
      ? invoke<void>("clear_tunnel_logs")
      : Promise.resolve(writeBrowserJson<TunnelLogEntry[]>("nimbo.tunnelLogs", [])),
  getDeviceInfo: () =>
    isTauriRuntime()
      ? invoke<DeviceInfo>("get_device_info")
      : Promise.resolve(browserDeviceInfo()),
  resetDeviceId: () => {
    if (isTauriRuntime()) return invoke<DeviceInfo>("reset_device_id");
    const device = { ...browserDeviceInfo(), hwid: randomUuid() };
    writeBrowserJson("nimbo.deviceInfo", device);
    return Promise.resolve(device);
  },
  getUserAgentOverride: () =>
    isTauriRuntime()
      ? invoke<string | null>("get_user_agent_override")
      : Promise.resolve(readBrowserJson<string | null>("nimbo.userAgentOverride", null)),
  setUserAgentOverride: (userAgent: string | null) =>
    isTauriRuntime()
      ? invoke<PersistedState>("set_user_agent_override", { userAgent })
      : (writeBrowserJson("nimbo.userAgentOverride", userAgent),
        Promise.resolve({
          subscriptions: [],
          active_server_id: null,
          user_agent_override: userAgent,
        } satisfies PersistedState)),
  listAppProxyRules: () =>
    isTauriRuntime()
      ? invoke<AppProxyRule[]>("list_app_proxy_rules")
      : Promise.resolve(readBrowserJson<AppProxyRule[]>("nimbo.appProxyRules", [])),
  listSubscriptionAppProxyRules: () =>
    isTauriRuntime()
      ? invoke<AppProxyRule[]>("list_subscription_app_proxy_rules")
      : Promise.resolve([] as AppProxyRule[]),
  reapplyRuntimeConfig: () =>
    isTauriRuntime()
      ? invoke<boolean>("reapply_runtime_config")
      : Promise.resolve(false),
  listInstalledApps: () =>
    isTauriRuntime()
      ? invoke<InstalledApp[]>("list_installed_apps")
      : Promise.resolve(browserInstalledApps),
  listConflictingProcesses: () =>
    isTauriRuntime()
      ? invoke<ConflictingProcess[]>("list_conflicting_processes")
      : Promise.resolve(browserConflictingProcesses()),
  stopConflictingProcesses: (pids?: number[]) =>
    isTauriRuntime()
      ? invoke<ConflictingProcess[]>("stop_conflicting_processes", { pids: pids ?? null })
      : (writeBrowserJson("nimbo.mockConflictsDismissed", true), Promise.resolve([])),
  helperStatus: (): Promise<HelperStatus> =>
    isTauriRuntime()
      ? invoke<HelperStatus>("helper_status")
      : Promise.resolve({
          installed: false,
          running: false,
          version: null,
          exe_present: false,
          exe_path: null,
        }),
  installHelper: (): Promise<HelperStatus> =>
    isTauriRuntime()
      ? invoke<HelperStatus>("install_helper")
      : Promise.reject(new Error("Helper доступен только в десктоп-приложении")),
  uninstallHelper: (): Promise<HelperStatus> =>
    isTauriRuntime()
      ? invoke<HelperStatus>("uninstall_helper")
      : Promise.reject(new Error("Helper доступен только в десктоп-приложении")),
  getAppIcon: (path: string): Promise<string | null> =>
    isTauriRuntime()
      ? invoke<string | null>("get_app_icon", { path })
      : Promise.resolve(null),
  pickAppExecutable: (): Promise<string | null> =>
    isTauriRuntime()
      ? invoke<string | null>("pick_app_executable")
      : Promise.resolve(null),
  exportAppProxyRulesFile: (contents: string, fileName: string): Promise<string | null> =>
    isTauriRuntime()
      ? invoke<string | null>("export_app_proxy_rules_file", { contents, fileName })
      : Promise.resolve(downloadBrowserTextFile(fileName, contents)),
  setAppProxyRules: (rules: AppProxyRule[]) =>
    isTauriRuntime()
      ? invoke<PersistedState>("set_app_proxy_rules", { rules })
      : (writeBrowserJson("nimbo.appProxyRules", rules),
        Promise.resolve({
          subscriptions: [],
          active_server_id: null,
          app_proxy_rules: rules,
        } satisfies PersistedState)),
  setConnectionMode: (mode: ConnectionMode) =>
    isTauriRuntime()
      ? invoke<PersistedState>("set_connection_mode", { mode })
      : Promise.resolve((() => {
          const current = browserPersistedState();
          return writeBrowserPersistedState({ ...current, connection_mode: mode });
        })()),
  getTunStatus: () =>
    isTauriRuntime()
      ? invoke<TunInstallStatus>("get_tun_status")
      : Promise.resolve({
          installed: true,
          can_install: true,
          needs_admin_restart: false,
          tun2socks_path: "browser-preview",
          wintun_path: "browser-preview",
          missing: [],
          message: "TUN установлен и готов.",
        } satisfies TunInstallStatus),
  installTun: () =>
    isTauriRuntime()
      ? invoke<TunInstallStatus>("install_tun")
      : Promise.resolve({
          installed: true,
          can_install: true,
          needs_admin_restart: false,
          tun2socks_path: "browser-preview",
          wintun_path: "browser-preview",
          missing: [],
          message: "TUN установлен и готов.",
        } satisfies TunInstallStatus),
  restartAsAdmin: () =>
    isTauriRuntime()
      ? invoke<void>("restart_as_admin")
      : Promise.resolve(),
  checkAppUpdate: () =>
    isTauriRuntime()
      ? invoke<AppUpdateInfo>("check_app_update")
      : Promise.resolve(browserUpdateInfo()),
  openUpdateDownload: (downloadUrl: string) =>
    isTauriRuntime()
      ? invoke<void>("open_update_download", { downloadUrl })
      : Promise.resolve(window.open(downloadUrl, "_blank", "noopener")),
  setProxySettings: (settings: ProxySettingsPatch) =>
    isTauriRuntime()
      ? invoke<PersistedState>("set_proxy_settings", { settings })
      : Promise.resolve((() => {
          const current = browserPersistedState();
          return writeBrowserPersistedState({
            ...current,
            socks_username: nonEmptyString(settings.socks_username, current.socks_username || DEFAULT_SOCKS_USERNAME),
            socks_password: nonEmptyString(settings.socks_password, current.socks_password || DEFAULT_SOCKS_PASSWORD),
            require_socks_auth: settings.require_socks_auth ?? current.require_socks_auth ?? false,
            block_socks_udp: settings.block_socks_udp ?? current.block_socks_udp ?? false,
          });
        })()),
  listSubscriptions: () =>
    isTauriRuntime()
      ? invoke<Subscription[]>("list_subscriptions")
      : Promise.resolve(browserPersistedState().subscriptions),
  inspectSubscriptionHeaders: (url: string) =>
    isTauriRuntime()
      ? invoke<SubscriptionHeaderMetadata>("inspect_subscription_headers", { url })
      : Promise.reject(new Error("inspect_subscription_headers доступен только в Tauri")),
  readClipboardText: () =>
    isTauriRuntime()
      ? invoke<string>("read_clipboard_text")
      : Promise.reject(new Error("Буфер обмена доступен только в приложении.")),
  writeClipboardText: (text: string) =>
    isTauriRuntime()
      ? invoke<void>("write_clipboard_text", { text })
      : Promise.reject(new Error("Буфер обмена доступен только в приложении.")),
  addSubscription: (url: string, name?: string) =>
    isTauriRuntime()
      ? invoke<Subscription>("add_subscription", { url, name: name ?? null })
      : Promise.resolve((() => {
          const cleaned = url.trim();
          const current = browserPersistedState();
          const servers = isSingleProxyLink(cleaned) ? [fallbackServerFromProxyLink(cleaned)] : [];
          const created: Subscription = {
            url: cleaned,
            name: fallbackSubscriptionName(cleaned, name ?? null),
            meta: {
              description: null,
              support_url: "https://t.me/nebulaguard_channel",
              website_url: (() => {
                try {
                  const u = new URL(cleaned);
                  return `${u.protocol}//${u.host}`;
                } catch {
                  return null;
                }
              })(),
            },
            servers,
            info: null,
            fetched_at: Math.floor(Date.now() / 1000),
          };

          const subscriptions = [
            ...current.subscriptions.filter((s) => s.url !== cleaned),
            created,
          ];
          const activeServerId = current.active_server_id ?? servers[0]?.id ?? null;
          writeBrowserPersistedState({
            ...current,
            subscriptions,
            active_server_id: activeServerId,
          });
          return created;
        })()),
  refreshSubscription: (url: string) =>
    isTauriRuntime()
      ? invoke<Subscription>("refresh_subscription", { url })
      : Promise.resolve((() => {
          const current = browserPersistedState();
          const index = current.subscriptions.findIndex((s) => s.url === url);
          if (index < 0) {
            throw new Error("Подписка не найдена.");
          }
          const updated: Subscription = {
            ...current.subscriptions[index],
            fetched_at: Math.floor(Date.now() / 1000),
          };
          const subscriptions = [...current.subscriptions];
          subscriptions[index] = updated;
          writeBrowserPersistedState({ ...current, subscriptions });
          return updated;
        })()),
  updateSubscriptionSettings: (url: string, settings: SubscriptionSettingsPatch) =>
    isTauriRuntime()
      ? invoke<Subscription>("update_subscription_settings", { url, settings })
      : Promise.resolve((() => {
          const current = browserPersistedState();
          const index = current.subscriptions.findIndex((s) => s.url === url);
          if (index < 0) {
            throw new Error("Подписка не найдена.");
          }
          const updated: Subscription = {
            ...current.subscriptions[index],
            name: settings.name === undefined ? current.subscriptions[index].name : (settings.name?.trim() || null),
            meta: {
              ...(current.subscriptions[index].meta ?? {}),
              show_on_home: settings.show_on_home ?? current.subscriptions[index].meta?.show_on_home ?? true,
              update_interval_minutes:
                settings.update_interval_minutes ?? current.subscriptions[index].meta?.update_interval_minutes ?? null,
            },
          };
          const subscriptions = [...current.subscriptions];
          subscriptions[index] = updated;
          writeBrowserPersistedState({ ...current, subscriptions });
          return updated;
        })()),
  removeSubscription: (url: string) =>
    isTauriRuntime()
      ? invoke<PersistedState>("remove_subscription", { url })
      : Promise.resolve((() => {
          const current = browserPersistedState();
          const subscriptions = current.subscriptions.filter((s) => s.url !== url);
          const validServerIds = new Set(subscriptions.flatMap((s) => s.servers.map((x) => x.id)));
          const active_server_id = current.active_server_id && validServerIds.has(current.active_server_id)
            ? current.active_server_id
            : null;
          return writeBrowserPersistedState({ ...current, subscriptions, active_server_id });
        })()),
  setActiveServer: (serverId: string | null) =>
    isTauriRuntime()
      ? invoke<PersistedState>("set_active_server", { serverId })
      : Promise.resolve((() => {
          const current = browserPersistedState();
          return writeBrowserPersistedState({ ...current, active_server_id: serverId });
        })()),
  setActiveSubscription: (url: string | null) =>
    isTauriRuntime()
      ? invoke<PersistedState>("set_active_subscription", { url })
      : Promise.resolve((() => {
          const current = browserPersistedState();
          return writeBrowserPersistedState({ ...current, active_subscription_url: url });
        })()),
  pingServer: (serverId: string) =>
    isTauriRuntime()
      ? invoke<ServerPing>("ping_server", { serverId })
      : Promise.resolve((() => {
          const current = browserPersistedState();
          const protocol = current.preferences?.latency_protocol ?? defaultAppPreferences.latency_protocol;
          const ping = protocol === "icmp" ? 28 : protocol === "http_head" ? 54 : 42;
          writeBrowserPersistedState({
            ...current,
            server_pings: { ...current.server_pings, [serverId]: ping },
          });
          return { server_id: serverId, latency_ms: ping, error: null } satisfies ServerPing;
        })()),
  pingServers: (serverIds: string[]) =>
    isTauriRuntime()
      ? invoke<ServerPing[]>("ping_servers", { serverIds })
      : Promise.resolve((() => {
          const current = browserPersistedState();
          const protocol = current.preferences?.latency_protocol ?? defaultAppPreferences.latency_protocol;
          const base = protocol === "icmp" ? 28 : protocol === "http_head" ? 54 : 38;
          const nextPings = { ...current.server_pings };
          const result = serverIds.map((serverId, index) => {
            const latency = base + index * 7;
            nextPings[serverId] = latency;
            return {
              server_id: serverId,
              latency_ms: latency,
              error: null,
            } satisfies ServerPing;
          });
          writeBrowserPersistedState({ ...current, server_pings: nextPings });
          return result;
        })()),
  connectServer: (serverId: string) =>
    isTauriRuntime()
      ? invoke<PersistedState>("connect_server", { serverId })
      : Promise.resolve((() => {
          const current = browserPersistedState();
          return writeBrowserPersistedState({
            ...current,
            active_server_id: serverId,
            connected: true,
          });
        })()),
  disconnectServer: () =>
    isTauriRuntime()
      ? invoke<PersistedState>("disconnect_server")
      : Promise.resolve((() => {
          const current = browserPersistedState();
          return writeBrowserPersistedState({
            ...current,
            connected: false,
          });
        })()),
};

export function protocolLabel(p: Protocol): string {
  switch (p.kind) {
    case "vless":
      return "VLESS";
    case "vmess":
      return "VMess";
    case "trojan":
      return "Trojan";
    case "shadowsocks":
      return "Shadowsocks";
    case "hysteria2":
      return "Hysteria2";
  }
}

export function serverEndpoint(p: Protocol): string {
  switch (p.kind) {
    case "vless":
    case "vmess":
    case "trojan":
    case "shadowsocks":
    case "hysteria2":
      return `${p.address}:${p.port}`;
  }
}

export function serverCustomDescription(server: Server): string | null {
  const description = (server.server_description ?? server.serverDescription ?? "").trim();
  if (!description) return null;
  return description;
}

export function serverListDescription(server: Server, servers: Server[]): string | null {
  const description = serverCustomDescription(server);
  if (description) return description;
  void servers;
  return serverProtocolPlaceholder(server.protocol);
}

export function transportLabel(p: Protocol): string {
  if (p.kind === "shadowsocks") return p.method;
  if (p.kind === "hysteria2") return [p.sni ? "TLS" : "QUIC", p.alpn?.join(", ")].filter(Boolean).join(" · ");
  const stream = p.stream;
  const sec = stream.security === "none" ? "" : stream.security.toUpperCase();
  const net = stream.network.replace("_", "-").toUpperCase();
  return [net, sec].filter(Boolean).join(" · ");
}

export function serverProtocolPlaceholder(p: Protocol): string {
  const details = transportLabel(p)
    .split(" · ")
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => part.replace(/_/g, "-").toUpperCase());
  return `${[protocolLabel(p).toUpperCase(), ...details].join(" / ")} | JSON`;
}

/** Converts a two-letter ISO 3166-1 alpha-2 country code to its flag emoji */
function isoToFlagEmoji(code: string): string {
  const upper = code.toUpperCase();
  if (!/^[A-Z]{2}$/.test(upper)) return "";
  const A = 0x1f1e6 - 65;
  return String.fromCodePoint(A + upper.charCodeAt(0), A + upper.charCodeAt(1));
}

/** Known country name patterns → ISO code */
const COUNTRY_KEYWORDS: Array<[RegExp, string]> = [
  [/russia|россия|рос\b|russkiy|ru\b/i, "RU"],
  [/germany|deutsch|german|de\b|германия|герм\b/i, "DE"],
  [/united.?states|usa\b|us\b|america|америк/i, "US"],
  [/netherlands|dutch|nether|nl\b|голланд|нидерл/i, "NL"],
  [/finland|finnish|finl|fi\b|финлянд/i, "FI"],
  [/france|french|fr\b|франц/i, "FR"],
  [/united.?kingdom|uk\b|britain|british|gb\b|англ|великобрит/i, "GB"],
  [/canada|canadian|ca\b|канад/i, "CA"],
  [/japan|japanese|jp\b|япони/i, "JP"],
  [/singapore|sg\b|сингапур/i, "SG"],
  [/hong.?kong|hk\b|гонконг/i, "HK"],
  [/south.?korea|korea|kr\b|корея/i, "KR"],
  [/turkey|turkish|tr\b|турци/i, "TR"],
  [/poland|polish|pl\b|польш/i, "PL"],
  [/sweden|swedish|se\b|швеци/i, "SE"],
  [/norway|norwegian|no\b|норвег/i, "NO"],
  [/denmark|danish|dk\b|данни/i, "DK"],
  [/switzerland|swiss|ch\b|швейцар/i, "CH"],
  [/austria|austrian|at\b|австри/i, "AT"],
  [/czech|cz\b|чехи/i, "CZ"],
  [/ukraine|ukrainian|ua\b|украин/i, "UA"],
  [/moldova|md\b|молдов/i, "MD"],
  [/latvia|lv\b|латви/i, "LV"],
  [/lithuania|lt\b|литв/i, "LT"],
  [/estonia|ee\b|эстони/i, "EE"],
  [/belarus|by\b|белорус/i, "BY"],
  [/kazakhstan|kz\b|казахст/i, "KZ"],
  [/spain|spanish|es\b|испани/i, "ES"],
  [/italy|italian|it\b|итали/i, "IT"],
  [/portugal|pt\b|португал/i, "PT"],
  [/romania|ro\b|румыни/i, "RO"],
  [/hungary|hu\b|венгри/i, "HU"],
  [/serbia|rs\b|серби/i, "RS"],
  [/bulgaria|bg\b|болгари/i, "BG"],
  [/croatia|hr\b|хорват/i, "HR"],
  [/slovakia|sk\b|словаки/i, "SK"],
  [/brazil|br\b|брази/i, "BR"],
  [/argentina|ar\b|аргентин/i, "AR"],
  [/india|in\b|инди/i, "IN"],
  [/china|cn\b|китай|китайс/i, "CN"],
  [/australia|au\b|австрали/i, "AU"],
  [/new.?zealand|nz\b|новая зеландия/i, "NZ"],
  [/south.?africa|za\b|юж.*афри/i, "ZA"],
  [/egypt|eg\b|египт/i, "EG"],
  [/israel|il\b|израил/i, "IL"],
  [/iran|ir\b|иран/i, "IR"],
  [/iraq|iq\b|ирак/i, "IQ"],
  [/mexico|mx\b|мексик/i, "MX"],
  [/colombia|co\b|колумби/i, "CO"],
  [/chile|cl\b|чили/i, "CL"],
  [/latvia|lv\b/i, "LV"],
  [/iceland|is\b|исланд/i, "IS"],
  [/georgia|ge\b|грузи/i, "GE"],
  [/armenia|am\b|армени/i, "AM"],
  [/azerbaijan|az\b|азербайдж/i, "AZ"],
  [/thailand|thai|th\b|таиланд/i, "TH"],
  [/vietnam|viet|vn\b|вьетнам/i, "VN"],
  [/indonesia|id\b|индонези/i, "ID"],
  [/malaysia|my\b|малайзи/i, "MY"],
  [/philippines|ph\b|филиппин/i, "PH"],
  [/taiwan|tw\b|тайван/i, "TW"],
  [/pakistan|pk\b|пакистан/i, "PK"],
  [/bangladesh|bd\b|бангладеш/i, "BD"],
  [/nigeria|ng\b|нигери/i, "NG"],
  [/kenya|ke\b|кени/i, "KE"],
];

export function serverFlagEmoji(name: string): string | null {
  const iso = serverCountryCode(name);
  return iso ? isoToFlagEmoji(iso) : null;
}

export function subscriptionVisibleOnHome(sub: Subscription): boolean {
  return sub.meta?.show_on_home !== false;
}

export function serverCountryCode(name: string): string | null {
  const emojiMatch = name.match(/[\u{1F1E6}-\u{1F1FF}]{2}/u);
  if (emojiMatch) {
    const codePoints = Array.from(emojiMatch[0]).map((char) => char.codePointAt(0));
    if (codePoints.length === 2 && codePoints[0] && codePoints[1]) {
      const A = 0x1f1e6;
      return String.fromCharCode(
        65 + codePoints[0] - A,
        65 + codePoints[1] - A,
      );
    }
  }

  const isoMatch = name.match(/^[^\p{L}]*([A-Z]{2})(?=$|[\s[\](|·\-_\d:])/u);
  if (isoMatch) return isoMatch[1];

  for (const [pattern, iso] of COUNTRY_KEYWORDS) {
    if (pattern.test(name)) return iso;
  }

  return null;
}

export function serverDisplayName(name: string): string {
  return name

    .replace(/[\u{1F1E6}-\u{1F1FF}]{2}/gu, "")
    .replace(/[\u{1F3F3}\u{1F3F4}]\uFE0F?/gu, "")
    .replace(/\s{2,}/g, " ")
    .trim()
    .replace(/^[A-Z]{2}(?=[\s·\-|]|$)/u, "")
    .replace(/^\s*[·\-|]\s*/u, "")
    .trim();
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let v = bytes / 1024;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(v >= 100 ? 0 : v >= 10 ? 1 : 2)} ${units[i]}`;
}

export function formatExpire(unixSeconds: number | null | undefined): string {
  if (!unixSeconds) return "—";
  const ms = unixSeconds * 1000;
  const date = new Date(ms);
  const diff = ms - Date.now();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  const fmt = date.toLocaleDateString("ru-RU", { day: "2-digit", month: "short", year: "numeric" });
  if (days > 0) return `${fmt} (через ${days} дн.)`;
  if (days === 0) return `${fmt} (сегодня)`;
  return `${fmt} (истекла)`;
}
