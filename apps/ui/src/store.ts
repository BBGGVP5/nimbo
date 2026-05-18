import { create } from "zustand";
import {
  api,
  defaultAppPreferences,
  type AppPreferences,
  type AppStatus,
  type ConflictingProcess,
  type Subscription,
  type SubscriptionSettingsPatch,
} from "./lib/api";

interface AppStoreState {
  status: AppStatus | null;
  preferences: AppPreferences;
  subscriptions: Subscription[];
  activeServerId: string | null;
  activeSubscriptionUrl: string | null;
  serverPings: Record<string, number>;
  connectingServerId: string | null;
  disconnecting: boolean;
  importDialogOpen: boolean;
  importDialogSource: string;
  conflictDialogOpen: boolean;
  conflictingProcesses: ConflictingProcess[];
  conflictStopping: boolean;
  conflictStopError: string | null;
  loading: boolean;
  error: string | null;
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
  scanConflictingProcesses: () => Promise<ConflictingProcess[]>;
  closeConflictDialog: () => void;
  stopConflictingProcesses: () => Promise<void>;
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
  importDialogOpen: false,
  importDialogSource: "",
  conflictDialogOpen: false,
  conflictingProcesses: [],
  conflictStopping: false,
  conflictStopError: null,
  loading: false,
  error: null,

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
    if (conflicts.length > 0) {
      set({
        conflictDialogOpen: true,
        conflictingProcesses: conflicts,
        conflictStopping: false,
        conflictStopError: null,
      });
    }
    return conflicts;
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
    const pids = conflictingProcesses.map((process) => process.pid);
    set({ conflictStopping: true, conflictStopError: null, error: null });

    try {
      const remaining = await api.stopConflictingProcesses(pids);
      if (remaining.length > 0) {
        set({
          conflictStopping: false,
          conflictingProcesses: remaining,
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
