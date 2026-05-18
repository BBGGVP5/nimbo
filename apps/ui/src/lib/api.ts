import { invoke } from "@tauri-apps/api/core";

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
  | { kind: "shadowsocks"; address: string; port: number; method: string; password: string };

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

export type ThemeMode = "system" | "dark" | "light";
export type AccentMode = "system" | "preset" | "custom";
export type AppLanguage = "ru" | "en";
export type LatencyProtocol = "tcp_connect";
export type LatencyDisplayFormat = "ms" | "badge";

export interface AppPreferences {
  launch_at_login: boolean;
  auto_connect_on_launch: boolean;
  start_minimized: boolean;
  minimize_to_tray: boolean;
  ping_on_launch: boolean;
  check_updates_on_launch: boolean;
  provider_theme: boolean;
  theme_mode: ThemeMode;
  accent_mode: AccentMode;
  accent_color: string;
  language: AppLanguage;
  latency_protocol: LatencyProtocol;
  latency_test_url: string;
  latency_timeout_ms: number;
  latency_display_format: LatencyDisplayFormat;
}

export type AppProxyMode = "proxy" | "direct";
export type ConnectionMode = "system_proxy" | "tun";

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

export const defaultAppPreferences: AppPreferences = {
  launch_at_login: false,
  auto_connect_on_launch: false,
  start_minimized: false,
  minimize_to_tray: true,
  ping_on_launch: true,
  check_updates_on_launch: true,
  provider_theme: true,
  theme_mode: "system",
  accent_mode: "preset",
  accent_color: "#7c5dfa",
  language: "ru",
  latency_protocol: "tcp_connect",
  latency_test_url: "https://www.gstatic.com/generate_204",
  latency_timeout_ms: 5000,
  latency_display_format: "ms",
};

function normalizePreferences(value: Partial<AppPreferences> | null | undefined): AppPreferences {
  const theme = value?.theme_mode === "dark" || value?.theme_mode === "light" || value?.theme_mode === "system"
    ? value.theme_mode
    : defaultAppPreferences.theme_mode;
  const accentMode = value?.accent_mode === "system" || value?.accent_mode === "preset" || value?.accent_mode === "custom"
    ? value.accent_mode
    : defaultAppPreferences.accent_mode;
  const language = value?.language === "en" || value?.language === "ru"
    ? value.language
    : defaultAppPreferences.language;
  const accent = typeof value?.accent_color === "string" && /^#[0-9a-f]{6}$/i.test(value.accent_color)
    ? value.accent_color.toLowerCase()
    : defaultAppPreferences.accent_color;
  const latencyProtocol = value?.latency_protocol === "tcp_connect"
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
    latency_protocol: latencyProtocol,
    latency_test_url: latencyTestUrl,
    latency_timeout_ms: latencyTimeoutMs,
    latency_display_format: latencyDisplayFormat,
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
    socks_username: stored?.socks_username ?? "nimbo",
    socks_password: stored?.socks_password ?? "nmb-preview-password",
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
  return /^(vless|vmess|trojan|ss):\/\//i.test(value.trim());
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
    const uuid = q.get("id") || crypto.randomUUID();
    const network = (q.get("type") as Network | null) || "tcp";
    const security = (q.get("security") as Security | null) || "tls";

    return {
      id: crypto.randomUUID(),
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
      id: crypto.randomUUID(),
      name: fallbackName,
      server_description: null,
      host_uuid: null,
      xray_json_template_uuid: null,
      protocol: {
        kind: "vless",
        address: fallbackAddress,
        port: fallbackPort,
        uuid: crypto.randomUUID(),
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
    hwid: crypto.randomUUID(),
    os: "Windows",
    os_version: navigator.platform || "unknown",
    hostname: "browser-preview",
    user_agent: "Nimbo/0.1.0",
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
      current_version: "0.1.0",
      latest_version: "3.0.33",
      release_name: "3.0.33",
      release_notes: "Демо обновления для браузерного предпросмотра.",
      release_url: "https://github.com/BBGGVP5/nimbo/releases",
      published_at: new Date().toISOString(),
      target: "Windows x64",
      asset: {
        name: "Nimbo_3.0.33_x64-setup.exe",
        download_url: "https://github.com/BBGGVP5/nimbo/releases",
        size: 0,
        content_type: "application/octet-stream",
      },
      download_url: "https://github.com/BBGGVP5/nimbo/releases",
    };
  }

  return {
    available: false,
    current_version: "0.1.0",
    latest_version: "0.1.0",
    release_name: "0.1.0",
    release_notes: null,
    release_url: "https://github.com/BBGGVP5/nimbo/releases",
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

const browserInstalledApps: InstalledApp[] = [
  { name: "Google Chrome", executable_path: "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" },
  { name: "Telegram", executable_path: "C:\\Users\\User\\AppData\\Roaming\\Telegram Desktop\\Telegram.exe" },
  { name: "Steam", executable_path: "C:\\Program Files (x86)\\Steam\\steam.exe" },
  { name: "Discord", executable_path: "C:\\Users\\User\\AppData\\Local\\Discord\\app.exe" },
  { name: "Android Studio", executable_path: "C:\\Program Files\\Android\\Android Studio\\bin\\studio64.exe" },
];

export const api = {
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
            socks_username: state.socks_username ?? "nimbo",
            socks_password: state.socks_password ?? "nmb-preview-password",
            require_socks_auth: Boolean(state.require_socks_auth),
            block_socks_udp: Boolean(state.block_socks_udp),
            server_pings: state.server_pings ?? {},
          } satisfies AppStatus;
        })()),
  getSessionTraffic: () =>
    isTauriRuntime()
      ? invoke<SessionTraffic>("get_session_traffic")
      : Promise.resolve(readBrowserJson<SessionTraffic>("nimbo.sessionTraffic", { upload: 0, download: 0 })),
  getDeviceInfo: () =>
    isTauriRuntime()
      ? invoke<DeviceInfo>("get_device_info")
      : Promise.resolve(browserDeviceInfo()),
  resetDeviceId: () => {
    if (isTauriRuntime()) return invoke<DeviceInfo>("reset_device_id");
    const device = { ...browserDeviceInfo(), hwid: crypto.randomUUID() };
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
            socks_username: settings.socks_username?.trim() || current.socks_username || "nimbo",
            socks_password: settings.socks_password?.trim() || current.socks_password || "nmb-preview-password",
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
          const ping = 42;
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
          const nextPings = { ...current.server_pings };
          const result = serverIds.map((serverId, index) => {
            const latency = 38 + index * 7;
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
  }
}

export function serverEndpoint(p: Protocol): string {
  switch (p.kind) {
    case "vless":
    case "vmess":
    case "trojan":
    case "shadowsocks":
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
  return serverEndpoint(server.protocol);
}

export function transportLabel(p: Protocol): string {
  if (p.kind === "shadowsocks") return p.method;
  const stream = p.stream;
  const sec = stream.security === "none" ? "" : stream.security.toUpperCase();
  const net = stream.network.replace("_", "-").toUpperCase();
  return [net, sec].filter(Boolean).join(" · ");
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

  const isoMatch = name.match(/(?:^|[\s[\](|·\-_])([A-Z]{2})(?=$|[\s[\](|·\-_\d:])/);
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
