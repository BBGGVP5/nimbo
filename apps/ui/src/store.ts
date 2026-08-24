import { create } from "zustand";
import {
  api,
  defaultAppPreferences,
  type AppPreferences,
  type AppStatus,
  type ConflictingProcess,
  type HelperStatus,
  type Subscription,
  type SubscriptionSettingsPatch,
  type ActiveConnection,
  type TrafficStats,
} from "./lib/api";

export interface TrafficSpeed {
  upload: number;
  download: number;
}

export interface TrafficSample {
  upload: number;
  download: number;
  at: number;
}

interface AppStoreState {
  status: AppStatus | null;
  preferences: AppPreferences;
  subscriptions: Subscription[];
  activeServerId: string | null;
  activeSubscriptionUrl: string | null;
  serverPings: Record<string, number>;
  connectingServerId: string | null;
  disconnecting: boolean;
  switchingServerId: string | null;
  importDialogOpen: boolean;
  importDialogSource: string;
  conflictDialogOpen: boolean;
  conflictingProcesses: ConflictingProcess[];
  conflictStopping: boolean;
  conflictStopError: string | null;
  helperStatus: HelperStatus | null;
  helperInstalling: boolean;
  helperError: string | null;
  loading: boolean;
  error: string | null;
  trafficStats: TrafficStats | null;
  /**
   * Оценка трафика по процессам за текущую сессию. Ни ядро, ни системные
   * таблицы Windows не отдают байты на процесс, поэтому прирост трафика
   * распределяется между процессами пропорционально их активным
   * соединениям через туннель — это оценка, а не точный счётчик.
   */
  appTraffic: Record<string, { download: number; upload: number }>;
  /** Значения счётчиков на прошлом замере — от них считается прирост. */
  appTrafficMark: { download: number; upload: number };
  /** Завершённые сессии, чтобы было видно, куда уходил трафик раньше. */
  sessionHistory: SessionRecord[];
  trafficSpeed: TrafficSpeed;
  trafficHistory: TrafficSample[];
  trafficMonitoringAvailable: boolean;
  sessionStartedAt: number | null;
  setTrafficStats: (stats: TrafficStats) => void;
  recordAppTraffic: (connections: ActiveConnection[]) => void;
  clearAppTraffic: () => void;
  recordTrafficStats: (stats: TrafficStats, at?: number) => void;
  setTrafficMonitoringAvailable: (available: boolean) => void;
  setSessionStartedAt: (at: number | null) => void;
  resetTrafficSession: () => void;
  hydrate: () => Promise<void>;
  setPreferences: (preferences: AppPreferences) => Promise<AppPreferences>;
  addSubscription: (url: string, name?: string) => Promise<Subscription>;
  refreshSubscription: (url: string) => Promise<Subscription>;
  updateSubscriptionSettings: (url: string, settings: SubscriptionSettingsPatch) => Promise<Subscription>;
  removeSubscription: (url: string) => Promise<void>;
  reorderSubscriptions: (urls: string[]) => Promise<void>;
  setActiveServer: (serverId: string | null) => Promise<void>;
  setActiveSubscription: (url: string | null) => Promise<void>;
  connectServer: (serverId: string) => Promise<void>;
  disconnectServer: () => Promise<void>;
  syncStatus: () => Promise<void>;
  openConflictDialog: (conflicts: ConflictingProcess[]) => void;
  scanConflictingProcesses: () => Promise<ConflictingProcess[]>;
  closeConflictDialog: () => void;
  stopConflictingProcesses: () => Promise<void>;
  refreshHelperStatus: () => Promise<HelperStatus>;
  installHelper: () => Promise<void>;
  uninstallHelper: () => Promise<void>;
  setServerPing: (serverId: string, latency: number) => void;
  openImportDialog: (source?: string) => void;
  closeImportDialog: () => void;
  setImportDialogSource: (source: string) => void;
}

export const useAppStore = create<AppStoreState>((set, get) => ({
  status: null,
  preferences: defaultAppPreferences,
  subscriptions: [],
  activeServerId: null,
  activeSubscriptionUrl: null,
  serverPings: {},
  connectingServerId: null,
  disconnecting: false,
  switchingServerId: null,
  importDialogOpen: false,
  importDialogSource: "",
  conflictDialogOpen: false,
  conflictingProcesses: [],
  conflictStopping: false,
  conflictStopError: null,
  helperStatus: null,
  helperInstalling: false,
  helperError: null,
  loading: false,
  error: null,
  trafficStats: null,
  appTraffic: {},
  appTrafficMark: { download: 0, upload: 0 },
  sessionHistory: readSessionHistory(),
  trafficSpeed: { upload: 0, download: 0 },
  trafficHistory: [],
  trafficMonitoringAvailable: false,
  sessionStartedAt: null,

  setTrafficStats: (stats) => set({ trafficStats: stats }),

  recordAppTraffic: (connections) =>
    set((state) => {
      const stats = state.trafficStats;
      if (!stats || state.status?.state !== "connected") return {};

      const mark = state.appTrafficMark;
      const deltaDown = Math.max(0, stats.session_download - mark.download);
      const deltaUp = Math.max(0, stats.session_upload - mark.upload);
      const nextMark = { download: stats.session_download, upload: stats.session_upload };
      if (deltaDown + deltaUp === 0) return { appTrafficMark: nextMark };

      // Считаем только то, что реально идёт через туннель: прямые соединения
      // к трафику VPN отношения не имеют.
      const tunnelled = connections.filter(
        (item) => item.route === "proxy" && item.process.trim().length > 0,
      );
      if (tunnelled.length === 0) return { appTrafficMark: nextMark };

      const weights = new Map<string, number>();
      for (const item of tunnelled) {
        const name = item.process.trim();
        weights.set(name, (weights.get(name) ?? 0) + 1);
      }

      const appTraffic = { ...state.appTraffic };
      for (const [name, count] of weights) {
        const share = count / tunnelled.length;
        const current = appTraffic[name] ?? { download: 0, upload: 0 };
        appTraffic[name] = {
          download: current.download + deltaDown * share,
          upload: current.upload + deltaUp * share,
        };
      }
      return { appTraffic, appTrafficMark: nextMark };
    }),

  clearAppTraffic: () => set({ appTraffic: {}, appTrafficMark: { download: 0, upload: 0 } }),
  recordTrafficStats: (stats, at = Date.now()) =>
    set((state) => {
      const connected = state.status?.state === "connected";
      if (!connected) {
        return {
          trafficStats: stats,
          trafficSpeed: { upload: 0, download: 0 },
          trafficHistory: [],
          trafficMonitoringAvailable: false,
        };
      }

      const trafficSpeed = {
        upload: stats.upload_speed,
        download: stats.download_speed,
      };
      const trafficHistory = stats.speed_available
        ? [...state.trafficHistory, { ...trafficSpeed, at }].slice(-60)
        : state.trafficHistory;
      return {
        trafficStats: stats,
        trafficSpeed,
        trafficHistory,
        trafficMonitoringAvailable: stats.speed_available,
      };
    }),
  setTrafficMonitoringAvailable: (trafficMonitoringAvailable) =>
    set({ trafficMonitoringAvailable }),
  setSessionStartedAt: (at) => set({ sessionStartedAt: at }),
  resetTrafficSession: () =>
    set((state) => ({
      sessionHistory: closeSession(state),
      appTraffic: {},
      appTrafficMark: { download: 0, upload: 0 },
      trafficStats: null,
      trafficSpeed: { upload: 0, download: 0 },
      trafficHistory: [],
      trafficMonitoringAvailable: false,
      sessionStartedAt: null,
    })),

  hydrate: async () => {
    set({ loading: true, error: null });
    try {
      const [subs, status, preferences] = await Promise.all([
        api.listSubscriptions(),
        api.getStatus(),
        api.getPreferences(),
      ]);
      set((current) => {
        const sessionStartedAt = status.state === "connected"
          ? status.connected_at ?? current.sessionStartedAt ?? Date.now()
          : null;
        const sessionChanged = sessionStartedAt !== current.sessionStartedAt;
        return {
          subscriptions: subs,
          status,
          activeServerId: status.active_server_id,
          activeSubscriptionUrl: status.active_subscription_url,
          serverPings: status.server_pings ?? {},
          preferences,
          disconnecting: false,
          loading: false,
          sessionStartedAt,
          ...(sessionChanged
            ? {
                trafficStats: null,
                trafficSpeed: { upload: 0, download: 0 },
                trafficHistory: [],
                trafficMonitoringAvailable: false,
              }
            : {}),
        };
      });
      void api.refreshTrayMenu();
    } catch (e) {
      set({ error: String(e), loading: false });
    }
  },

  // Lightweight reconcile (no loading flash, no subs/prefs refetch) for when the
  // connection state changes outside this window — e.g. the user connected or
  // disconnected from the tray flyout. Keeps the main window's status in lock-step.
  syncStatus: async () => {
    try {
      const status = await api.getStatus();
      set((s) => ({
        status,
        activeServerId: status.active_server_id,
        activeSubscriptionUrl: status.active_subscription_url ?? s.activeSubscriptionUrl,
        serverPings: status.server_pings ?? s.serverPings,
        sessionStartedAt: status.state === "connected"
          ? status.connected_at ?? s.sessionStartedAt ?? Date.now()
          : null,
        ...(status.state !== "connected" ||
        (status.connected_at != null && status.connected_at !== s.sessionStartedAt)
          ? {
              trafficStats: null,
              trafficSpeed: { upload: 0, download: 0 },
              trafficHistory: [],
              trafficMonitoringAvailable: false,
            }
          : {}),
      }));
    } catch {
      // Transient backend hiccup; the next event or hydrate will reconcile.
    }
  },

  setPreferences: async (preferences) => {
    const saved = await api.setPreferences(preferences);
    set({ preferences: saved });
    return saved;
  },

  addSubscription: async (url, name) => {
    const sub = await api.addSubscription(url, name);
    set((s) => ({ subscriptions: [...s.subscriptions, sub] }));
    await get().hydrate();
    void api.refreshTrayMenu();
    return sub;
  },

  refreshSubscription: async (url) => {
    const sub = await api.refreshSubscription(url);
    set((s) => ({
      subscriptions: s.subscriptions.map((x) => (x.url === url ? sub : x)),
    }));
    await get().hydrate();
    void api.refreshTrayMenu();
    return sub;
  },

  updateSubscriptionSettings: async (url, settings) => {
    const sub = await api.updateSubscriptionSettings(url, settings);
    set((s) => ({
      subscriptions: s.subscriptions.map((x) => (x.url === url ? sub : x)),
    }));
    await get().hydrate();
    void api.refreshTrayMenu();
    return sub;
  },

  removeSubscription: async (url) => {
    const persisted = await api.removeSubscription(url);
    set({
      subscriptions: persisted.subscriptions,
      activeServerId: persisted.active_server_id,
      activeSubscriptionUrl: persisted.active_subscription_url ?? null,
      serverPings: persisted.server_pings ?? {},
    });
    await get().hydrate();
    void api.refreshTrayMenu();
  },

  reorderSubscriptions: async (urls) => {
    const persisted = await api.reorderSubscriptions(urls);
    set({
      subscriptions: persisted.subscriptions,
      activeServerId: persisted.active_server_id,
      activeSubscriptionUrl: persisted.active_subscription_url ?? null,
      serverPings: persisted.server_pings ?? {},
    });
    await get().hydrate();
    void api.refreshTrayMenu();
  },

  setActiveServer: async (serverId) => {
    const { status, activeServerId } = get();
    if (status?.state === "connected") {
      if (!serverId || serverId === activeServerId) return;
      set({ switchingServerId: serverId, disconnecting: true, error: null });
      try {
        await api.disconnectServer();
        get().resetTrafficSession();
        // Wait 800ms for the OS to release sockets and ports
        await new Promise((resolve) => setTimeout(resolve, 800));
        set({ switchingServerId: null });
        await get().connectServer(serverId);
      } catch (e) {
        set({ switchingServerId: null, disconnecting: false, error: String(e) });
        throw e;
      }
      return;
    }

    const persisted = await api.setActiveServer(serverId);
    set({
      subscriptions: persisted.subscriptions,
      activeServerId: persisted.active_server_id,
      serverPings: persisted.server_pings ?? {},
    });
    await get().hydrate();
    void api.refreshTrayMenu();
  },

  setActiveSubscription: async (url) => {
    const persisted = await api.setActiveSubscription(url);
    set({
      subscriptions: persisted.subscriptions,
      activeSubscriptionUrl: persisted.active_subscription_url ?? null,
    });
    await get().hydrate();
    void api.refreshTrayMenu();
  },

  connectServer: async (serverId) => {
    set({ connectingServerId: serverId, disconnecting: false, error: null });
    try {
      const persisted = await api.connectServer(serverId);
      set((s) => ({
        subscriptions: persisted.subscriptions,
        activeServerId: persisted.active_server_id,
        activeSubscriptionUrl: persisted.active_subscription_url ?? null,
        serverPings: persisted.server_pings ?? {},
        connectingServerId: null,
        sessionStartedAt: persisted.connected_at ?? Date.now(),
        status: s.status
          ? {
              ...s.status,
              state: "connected",
              connected_at: persisted.connected_at ?? Date.now(),
              active_server_id: persisted.active_server_id,
              active_subscription_url: persisted.active_subscription_url ?? null,
            }
          : s.status,
      }));
      await get().hydrate();
      void api.refreshTrayMenu();
    } catch (e) {
      set({ connectingServerId: null, error: String(e) });
      throw e;
    }
  },

  scanConflictingProcesses: async () => {
    const conflicts = await api.listConflictingProcesses().catch(() => []);
    if (conflicts.length === 0) {
      return conflicts;
    }
    get().openConflictDialog(conflicts);
    return conflicts;
  },

  openConflictDialog: (conflicts) => {
    if (conflicts.length === 0) return;
    void get().refreshHelperStatus();
    set({
      conflictDialogOpen: true,
      conflictingProcesses: conflicts,
      conflictStopping: false,
      conflictStopError: null,
    });
  },

  refreshHelperStatus: async () => {
    try {
      const status = await api.helperStatus();
      set({ helperStatus: status });
      return status;
    } catch {
      const fallback: HelperStatus = {
        installed: false,
        running: false,
        version: null,
        exe_present: false,
        exe_path: null,
      };
      set({ helperStatus: fallback });
      return fallback;
    }
  },

  installHelper: async () => {
    set({ helperInstalling: true, helperError: null });
    try {
      const status = await api.installHelper();
      set({ helperInstalling: false, helperStatus: status });
    } catch (e) {
      set({ helperInstalling: false, helperError: String(e) });
      throw e;
    }
  },

  uninstallHelper: async () => {
    set({ helperInstalling: true, helperError: null });
    try {
      const status = await api.uninstallHelper();
      set({ helperInstalling: false, helperStatus: status });
    } catch (e) {
      set({ helperInstalling: false, helperError: String(e) });
      throw e;
    }
  },

  closeConflictDialog: () => {
    set({
      conflictDialogOpen: false,
      conflictingProcesses: [],
      conflictStopping: false,
      conflictStopError: null,
      connectingServerId: null,
    });
  },

  stopConflictingProcesses: async () => {
    const { conflictingProcesses } = get();
    const pids = Array.from(
      new Set(
        conflictingProcesses.flatMap((process) =>
          process.pids && process.pids.length > 0 ? process.pids : [process.pid],
        ),
      ),
    );
    set({ conflictStopping: true, conflictStopError: null, error: null });

    try {
      const remaining = await api.stopConflictingProcesses(pids);
      const verifiedRemaining = remaining.length > 0
        ? remaining
        : await api.listConflictingProcesses().catch(() => remaining);
      if (verifiedRemaining.length > 0) {
        set({
          conflictStopping: false,
          conflictingProcesses: verifiedRemaining,
          conflictStopError: "remaining_conflicts",
        });
        return;
      }

      set({
        conflictDialogOpen: false,
        conflictingProcesses: [],
        conflictStopping: false,
        conflictStopError: null,
      });
    } catch (e) {
      const message = String(e);
      set({ conflictStopping: false, conflictStopError: message, error: message });
      throw e;
    }
  },

  disconnectServer: async () => {
    set({ disconnecting: true, connectingServerId: null, error: null });
    try {
      const persisted = await api.disconnectServer();
      set((s) => ({
        subscriptions: persisted.subscriptions,
        activeServerId: persisted.active_server_id,
        serverPings: persisted.server_pings ?? {},
        connectingServerId: null,
        disconnecting: false,
        trafficSpeed: { upload: 0, download: 0 },
        trafficHistory: [],
        trafficMonitoringAvailable: false,
        sessionStartedAt: null,
        status: s.status
          ? {
              ...s.status,
              state: "disconnected",
              connected_at: null,
              active_server_id: persisted.active_server_id,
            }
          : s.status,
      }));
      await get().hydrate();
      void api.refreshTrayMenu();
    } catch (e) {
      set({ connectingServerId: null, disconnecting: false, error: String(e) });
      throw e;
    }
  },

  setServerPing: (serverId, latency) => {
    set((s) => ({
      serverPings: {
        ...s.serverPings,
        [serverId]: latency,
      },
    }));
  },

  openImportDialog: (source = "") => {
    set({
      importDialogOpen: true,
      importDialogSource: source,
    });
  },

  closeImportDialog: () => {
    set({
      importDialogOpen: false,
      importDialogSource: "",
    });
  },

  setImportDialogSource: (source) => {
    set({ importDialogSource: source });
  },
}));

const SESSION_HISTORY_KEY = "nimbo.sessionHistory.v1";
const SESSION_HISTORY_LIMIT = 60;

export interface SessionRecord {
  id: string;
  startedAt: number;
  endedAt: number;
  serverId: string | null;
  serverName: string;
  download: number;
  upload: number;
  ping: number | null;
  apps: Array<{ name: string; bytes: number }>;
}

function readSessionHistory(): SessionRecord[] {
  try {
    const raw = localStorage.getItem(SESSION_HISTORY_KEY);
    const parsed = raw ? (JSON.parse(raw) as SessionRecord[]) : [];
    return Array.isArray(parsed) ? parsed.slice(0, SESSION_HISTORY_LIMIT) : [];
  } catch {
    return [];
  }
}

function writeSessionHistory(records: SessionRecord[]) {
  try {
    localStorage.setItem(SESSION_HISTORY_KEY, JSON.stringify(records.slice(0, SESSION_HISTORY_LIMIT)));
  } catch {
    /* хранилище может быть недоступно — история не критична */
  }
}

/**
 * Складывает завершённую сессию в историю. Пустые сессии (без трафика и
 * длительности) пропускаем, чтобы список не засорялся переподключениями.
 */
function closeSession(state: {
  sessionStartedAt: number | null;
  trafficStats: TrafficStats | null;
  activeServerId: string | null;
  subscriptions: Subscription[];
  serverPings: Record<string, number>;
  appTraffic: Record<string, { download: number; upload: number }>;
  sessionHistory: SessionRecord[];
}): SessionRecord[] {
  const startedAt = state.sessionStartedAt;
  const stats = state.trafficStats;
  if (!startedAt || !stats) return state.sessionHistory;

  const download = stats.session_download;
  const upload = stats.session_upload;
  const endedAt = Date.now();
  if (download + upload === 0 && endedAt - startedAt < 5000) return state.sessionHistory;

  const server = state.subscriptions
    .flatMap((sub) => sub.servers)
    .find((item) => item.id === state.activeServerId);
  const apps = Object.entries(state.appTraffic)
    .map(([name, value]) => ({ name, bytes: Math.round(value.download + value.upload) }))
    .filter((item) => item.bytes > 0)
    .sort((a, b) => b.bytes - a.bytes)
    .slice(0, 8);

  const record: SessionRecord = {
    id: `${startedAt}`,
    startedAt,
    endedAt,
    serverId: state.activeServerId,
    serverName: server?.name?.trim() || "",
    download,
    upload,
    ping: state.activeServerId ? state.serverPings[state.activeServerId] ?? null : null,
    apps,
  };

  const next = [record, ...state.sessionHistory.filter((item) => item.id !== record.id)];
  writeSessionHistory(next);
  return next.slice(0, SESSION_HISTORY_LIMIT);
}
