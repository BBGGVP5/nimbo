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
  trafficSpeed: TrafficSpeed;
  trafficSample: TrafficSample | null;
  sessionStartedAt: number | null;
  setTrafficStats: (stats: TrafficStats) => void;
  setTrafficSpeed: (speed: TrafficSpeed) => void;
  setTrafficSample: (sample: TrafficSample | null) => void;
  setSessionStartedAt: (at: number | null) => void;
  resetTrafficSession: () => void;
  hydrate: () => Promise<void>;
  setPreferences: (preferences: AppPreferences) => Promise<AppPreferences>;
  addSubscription: (url: string, name?: string) => Promise<Subscription>;
  refreshSubscription: (url: string) => Promise<Subscription>;
  updateSubscriptionSettings: (url: string, settings: SubscriptionSettingsPatch) => Promise<Subscription>;
  removeSubscription: (url: string) => Promise<void>;
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
  trafficSpeed: { upload: 0, download: 0 },
  trafficSample: null,
  sessionStartedAt: null,

  setTrafficStats: (stats) => set({ trafficStats: stats }),
  setTrafficSpeed: (speed) => set({ trafficSpeed: speed }),
  setTrafficSample: (sample) => set({ trafficSample: sample }),
  setSessionStartedAt: (at) => set({ sessionStartedAt: at }),
  resetTrafficSession: () =>
    set({
      trafficSpeed: { upload: 0, download: 0 },
      trafficSample: null,
      sessionStartedAt: null,
    }),

  hydrate: async () => {
    set({ loading: true, error: null });
    try {
      const [subs, status, preferences] = await Promise.all([
        api.listSubscriptions(),
        api.getStatus(),
        api.getPreferences(),
      ]);
      set({
        subscriptions: subs,
        status,
        activeServerId: status.active_server_id,
        activeSubscriptionUrl: status.active_subscription_url,
        serverPings: status.server_pings ?? {},
        preferences,
        disconnecting: false,
        loading: false,
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
        status: s.status
          ? {
              ...s.status,
              state: "connected",
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
        trafficSample: null,
        sessionStartedAt: null,
        status: s.status
          ? { ...s.status, state: "disconnected", active_server_id: persisted.active_server_id }
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
