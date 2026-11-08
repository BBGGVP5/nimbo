import { createRoot } from "react-dom/client";
import { MemoryRouter } from "react-router-dom";
import { Statistics } from "../../src/pages/Statistics";
import { Routing } from "../../src/pages/Routing";
import { api, type AppStatus, type TrafficStats } from "../../src/lib/api";
import { useAppStore } from "../../src/store";
import "../../src/styles.css";
import "../../src/universal.css";
import "../../src/secondary.css";
import "../../src/preview-parity.css";
import "../../src/preview-fonts.css";

// No application hydration, process launching, or live network data. All IPC is fail-closed in the runner.
const params = new URLSearchParams(location.search);
const theme = params.get("theme") === "light" ? "light" : "dark";
const style = params.get("style") === "material_you" ? "material_you" : params.get("style") === "dotted" ? "dotted" : "signal";
const scenario = params.get("scenario") ?? "live";
document.body.dataset.uiStyle = style; document.body.dataset.theme = theme;
document.body.style.overflow = "auto";
const MiB = 1024 * 1024;
const now = Date.now();
let stats: TrafficStats = {
  session_available: true, session_upload: 48 * MiB, session_download: 256 * MiB,
  upload_speed: 0.25 * MiB, download_speed: 1.5 * MiB, speed_available: true,
  all_time_upload: 2048 * MiB, all_time_download: 12288 * MiB,
  monthly_upload: 512 * MiB, monthly_download: 4096 * MiB, monthly_period: "2026-10",
  route_traffic: { proxy_upload: 40 * MiB, proxy_download: 220 * MiB, direct_upload: 8 * MiB, direct_download: 36 * MiB },
  tcp_connections: 24, udp_connections: 7,
};
if (scenario === "unavailable") stats = { ...stats, session_available: false, session_upload: 0, session_download: 0, upload_speed: 0, download_speed: 0 };
if (scenario === "legacy") stats = { ...stats, session_available: undefined, route_traffic: undefined, tcp_connections: undefined, udp_connections: undefined };
if (scenario === "xray") stats = { ...stats, tcp_connections: null, udp_connections: null };
if (scenario === "idle") stats = { ...stats, session_upload: 0, session_download: 0, upload_speed: 0, download_speed: 0, tcp_connections: 0, udp_connections: 0,
  route_traffic: { proxy_upload: 0, proxy_download: 0, direct_upload: 0, direct_download: 0 } };
const saved = await api.getPreferences();
useAppStore.setState({ preferences: { ...saved, ui_style: style, theme_mode: theme, language: params.get("language") === "ru" ? "ru" : "en", tunnel_dns: "fixture-preserved" },
  status: { state: scenario === "offline" ? "disconnected" : "connected" } as AppStatus,
  subscriptions: [], activeServerId: null, sessionStartedAt: scenario === "offline" ? null : now - 300000,
  trafficStats: stats, trafficMonitoringAvailable: stats.speed_available,
  trafficHistory: scenario === "unavailable" || scenario === "idle" ? [] : Array.from({ length: 24 }, (_, i) => ({ at: now - (23 - i) * 1000, upload: (0.1 + i % 4 * 0.05) * MiB, download: (0.4 + i % 7 * 0.12) * MiB })),
  appTraffic: {}, sessionHistory: [{ id: "fixture-history", startedAt: now - 3600000, endedAt: now - 1800000,
    serverId: null, serverName: "Previous connection", download: 128 * MiB, upload: 16 * MiB, ping: null, apps: [] }],
});
api.getTrafficStats = async () => stats;
api.listRoutingProfiles = async () => ({ profiles: [], active: "global" });
const page = params.get("page") === "routing" ? <Routing /> : <Statistics />;
createRoot(document.getElementById("root")!).render(<MemoryRouter initialEntries={[params.get("page") === "routing" ? "/routing" : "/statistics"]}><main style={{ padding: 24, maxWidth: 1100, margin: "0 auto" }}>{page}</main></MemoryRouter>);
