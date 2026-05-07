import { invoke } from "@tauri-apps/api/core";

export type ConnectionState =
  | "disconnected"
  | "connecting"
  | "connected"
  | "service_unavailable";

export interface AppStatus {
  state: ConnectionState;
  active_server_id: string | null;
  subscription_count: number;
  server_count: number;
  service_protocol: number;
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
  protocol: Protocol;
}

export interface SubscriptionInfo {
  upload?: number | null;
  download?: number | null;
  total?: number | null;
  expire?: number | null;
}

export interface Subscription {
  url: string;
  name: string | null;
  servers: Server[];
  info: SubscriptionInfo | null;
  fetched_at: number;
}

export interface PersistedState {
  subscriptions: Subscription[];
  active_server_id: string | null;
}

export const api = {
  getStatus: () => invoke<AppStatus>("get_status"),
  getDeviceInfo: () => invoke<DeviceInfo>("get_device_info"),
  resetDeviceId: () => invoke<DeviceInfo>("reset_device_id"),
  listSubscriptions: () => invoke<Subscription[]>("list_subscriptions"),
  addSubscription: (url: string, name?: string) =>
    invoke<Subscription>("add_subscription", { url, name: name ?? null }),
  refreshSubscription: (url: string) =>
    invoke<Subscription>("refresh_subscription", { url }),
  removeSubscription: (url: string) =>
    invoke<PersistedState>("remove_subscription", { url }),
  setActiveServer: (serverId: string | null) =>
    invoke<PersistedState>("set_active_server", { serverId }),
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

export function transportLabel(p: Protocol): string {
  if (p.kind === "shadowsocks") return p.method;
  const stream = p.stream;
  const sec = stream.security === "none" ? "" : stream.security.toUpperCase();
  const net = stream.network.replace("_", "-").toUpperCase();
  return [net, sec].filter(Boolean).join(" · ");
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
