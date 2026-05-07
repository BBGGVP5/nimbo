import { create } from "zustand";
import { api, type AppStatus, type Subscription } from "./lib/api";

interface AppStoreState {
  status: AppStatus | null;
  subscriptions: Subscription[];
  activeServerId: string | null;
  loading: boolean;
  error: string | null;
  hydrate: () => Promise<void>;
  addSubscription: (url: string, name?: string) => Promise<Subscription>;
  refreshSubscription: (url: string) => Promise<Subscription>;
  removeSubscription: (url: string) => Promise<void>;
  setActiveServer: (serverId: string | null) => Promise<void>;
}

export const useAppStore = create<AppStoreState>((set, get) => ({
  status: null,
  subscriptions: [],
  activeServerId: null,
  loading: false,
  error: null,

  hydrate: async () => {
    set({ loading: true, error: null });
    try {
      const [subs, status] = await Promise.all([
        api.listSubscriptions(),
        api.getStatus(),
      ]);
      set({
        subscriptions: subs,
        status,
        activeServerId: status.active_server_id,
        loading: false,
      });
    } catch (e) {
      set({ error: String(e), loading: false });
    }
  },

  addSubscription: async (url, name) => {
    const sub = await api.addSubscription(url, name);
    set((s) => ({ subscriptions: [...s.subscriptions, sub] }));
    await get().hydrate();
    return sub;
  },

  refreshSubscription: async (url) => {
    const sub = await api.refreshSubscription(url);
    set((s) => ({
      subscriptions: s.subscriptions.map((x) => (x.url === url ? sub : x)),
    }));
    await get().hydrate();
    return sub;
  },

  removeSubscription: async (url) => {
    const persisted = await api.removeSubscription(url);
    set({
      subscriptions: persisted.subscriptions,
      activeServerId: persisted.active_server_id,
    });
    await get().hydrate();
  },

  setActiveServer: async (serverId) => {
    const persisted = await api.setActiveServer(serverId);
    set({
      subscriptions: persisted.subscriptions,
      activeServerId: persisted.active_server_id,
    });
    await get().hydrate();
  },
}));
