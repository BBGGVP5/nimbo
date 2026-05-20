import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { CountryFlag } from "../components/CountryFlag";
import { notifyError } from "../lib/notify";
import { useMessages } from "../lib/i18n";
import type { Messages } from "../lib/i18n";
import { pingServersProgressively } from "../lib/ping";
import { useAppStore } from "../store";
import {
  api,
  formatBytes,
  formatExpire,
  serverDisplayName,
  serverListDescription,
  subscriptionVisibleOnHome,
  type Server,
  type Subscription,
} from "../lib/api";

type ServerEntry = { server: Server; sub: Subscription };
type SortMode = "default" | "ping" | "protocol";
type SpeedSample = { upload: number; download: number; at: number };

const SPEED_HISTORY_LIMIT = 60;
const MEMORY_HISTORY_LIMIT = 60;

// ── Favorites persistence ────────────────────────────────────

function useFavorites() {
  const [favorites, setFavorites] = useState<Set<string>>(() => {
    try {
      const raw = localStorage.getItem("nimbo.favorites");
      return new Set(raw ? (JSON.parse(raw) as string[]) : []);
    } catch {
      return new Set();
    }
  });

  const toggle = useCallback((id: string) => {
    setFavorites((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      try {
        localStorage.setItem("nimbo.favorites", JSON.stringify([...next]));
      } catch {}
      return next;
    });
  }, []);

  return { favorites, toggle };
}

function makeOrderKey(subUrl: string): string {
  return `nimbo.order.${subUrl.replace(/[^a-z0-9]/gi, "_").slice(0, 80)}`;
}

function readServerOrder(subUrl: string): string[] {
  try {
    const raw = localStorage.getItem(makeOrderKey(subUrl));
    return raw ? (JSON.parse(raw) as string[]) : [];
  } catch {
    return [];
  }
}

function writeServerOrder(subUrl: string, order: string[]) {
  try {
    localStorage.setItem(makeOrderKey(subUrl), JSON.stringify(order));
  } catch {}
}

function validateServerOrder(stored: string[], currentIds: string[], subUrl: string): string[] {
  if (!stored.length) return [];
  const currentSet = new Set(currentIds);
  // Discard stored order if it references IDs not present in the current subscription
  const hasUnknown = stored.some((id) => !currentSet.has(id));
  if (hasUnknown) {
    try { localStorage.removeItem(makeOrderKey(subUrl)); } catch {}
    return [];
  }
  return stored;
}

function sortEntries(
  entries: ServerEntry[],
  mode: SortMode,
  pings: Record<string, number>,
  order: string[],
  pingOrder: "asc" | "desc" = "asc",
): ServerEntry[] {
  if (mode === "ping") {
    return [...entries].sort((a, b) => {
      const pa = pings[a.server.id] ?? Infinity;
      const pb = pings[b.server.id] ?? Infinity;
      return pingOrder === "asc" ? pa - pb : pb - pa;
    });
  }
  if (mode === "protocol") {
    return [...entries].sort((a, b) =>
      a.server.protocol.kind.localeCompare(b.server.protocol.kind),
    );
  }
  if (order.length) {
    const idx = new Map(order.map((id, i) => [id, i]));
    return [...entries].sort(
      (a, b) =>
        (idx.get(a.server.id) ?? Number.MAX_SAFE_INTEGER) -
        (idx.get(b.server.id) ?? Number.MAX_SAFE_INTEGER),
    );
  }
  return entries;
}

// ── Dismissable hook ─────────────────────────────────────────

function useDismissable(open: boolean, onClose: () => void) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) onClose();
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("mousedown", onPointerDown);
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("mousedown", onPointerDown);
      window.removeEventListener("keydown", onKey);
    };
  }, [open, onClose]);
  return ref;
}

// ── Home ─────────────────────────────────────────────────────

export function Home() {
  const m = useMessages();
  const navigate = useNavigate();
  const subs = useAppStore((s) => s.subscriptions);
  const activeId = useAppStore((s) => s.activeServerId);
  const activeSubscriptionUrl = useAppStore((s) => s.activeSubscriptionUrl);
  const status = useAppStore((s) => s.status);
  const serverPings = useAppStore((s) => s.serverPings);
  const connectingServerId = useAppStore((s) => s.connectingServerId);
  const disconnecting = useAppStore((s) => s.disconnecting);
  const setActive = useAppStore((s) => s.setActiveServer);
  const setActiveSubscription = useAppStore((s) => s.setActiveSubscription);
  const connectServer = useAppStore((s) => s.connectServer);
  const disconnectServer = useAppStore((s) => s.disconnectServer);
  const refreshSubscription = useAppStore((s) => s.refreshSubscription);
  const setServerPing = useAppStore((s) => s.setServerPing);

  const preferences = useAppStore((s) => s.preferences);

  const [refreshingUrl, setRefreshingUrl] = useState<string | null>(null);
  const [pinging, setPinging] = useState(false);
  const [pingingServerIds, setPingingServerIds] = useState<Set<string>>(() => new Set());
  const [adminDialogOpen, setAdminDialogOpen] = useState(false);
  const [connectedAt, setConnectedAt] = useState<number | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [sessionTraffic, setSessionTraffic] = useState({ upload: 0, download: 0 });
  const [speedSamples, setSpeedSamples] = useState<SpeedSample[]>([]);
  const [memorySamples, setMemorySamples] = useState<number[]>([]);
  const [currentMemoryBytes, setCurrentMemoryBytes] = useState(0);
  const previousTrafficRef = useRef<{ upload: number; download: number; at: number } | null>(null);

  // Server list features
  const [sortMode, setSortMode] = useState<SortMode>("default");
  const [pingOrder, setPingOrder] = useState<"asc" | "desc">("asc");
  const [protocolFilter, setProtocolFilter] = useState<string | null>(null);
  const [showFavOnly, setShowFavOnly] = useState(false);
  const [compactSheetOpen, setCompactSheetOpen] = useState(false);
  const { favorites, toggle: toggleFavorite } = useFavorites();

  const [widgetsCollapsed, setWidgetsCollapsed] = useState(() => {
    try {
      return localStorage.getItem("nimbo.homeWidgetsCollapsed") === "1";
    } catch {
      return false;
    }
  });
  const toggleWidgetsCollapsed = useCallback(() => {
    setWidgetsCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem("nimbo.homeWidgetsCollapsed", next ? "1" : "0");
      } catch {}
      return next;
    });
  }, []);

  const [sidePanelCollapsed, setSidePanelCollapsed] = useState(() => {
    try {
      return localStorage.getItem("nimbo.sidePanelCollapsed") === "1";
    } catch {
      return false;
    }
  });
  const toggleSidePanelCollapsed = useCallback(() => {
    setSidePanelCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem("nimbo.sidePanelCollapsed", next ? "1" : "0");
      } catch {}
      return next;
    });
  }, []);

  const [serverListCollapsed, setServerListCollapsed] = useState(() => {
    try {
      return localStorage.getItem("nimbo.serverListCollapsed") === "1";
    } catch {
      return false;
    }
  });
  const toggleServerListCollapsed = useCallback(() => {
    setServerListCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem("nimbo.serverListCollapsed", next ? "1" : "0");
      } catch {}
      return next;
    });
  }, []);

  const visibleSubs = useMemo(() => subs.filter(subscriptionVisibleOnHome), [subs]);

  const currentSub = useMemo(() => {
    if (activeSubscriptionUrl) {
      const match = visibleSubs.find((sub) => sub.url === activeSubscriptionUrl);
      if (match) return match;
    }
    return visibleSubs[0] ?? null;
  }, [visibleSubs, activeSubscriptionUrl]);

  const baseEntries = useMemo<ServerEntry[]>(
    () =>
      currentSub
        ? currentSub.servers.map((server) => ({ server, sub: currentSub }))
        : [],
    [currentSub],
  );

  const [customOrder, setCustomOrder] = useState<string[]>(() => {
    if (!currentSub) return [];
    return validateServerOrder(readServerOrder(currentSub.url), currentSub.servers.map((s) => s.id), currentSub.url);
  });

  const currentSubUrl = currentSub?.url;
  useEffect(() => {
    if (!currentSubUrl || !currentSub) {
      setCustomOrder([]);
      return;
    }
    const stored = readServerOrder(currentSubUrl);
    setCustomOrder(validateServerOrder(stored, currentSub.servers.map((s) => s.id), currentSubUrl));
  }, [currentSubUrl, currentSub]);

  const availableProtocols = useMemo(() => {
    const kinds = new Set(baseEntries.map((e) => e.server.protocol.kind));
    return [...kinds].sort();
  }, [baseEntries]);

  const sortedEntries = useMemo(() => {
    let result = sortEntries(baseEntries, sortMode, serverPings, customOrder, pingOrder);
    if (showFavOnly) result = result.filter((e) => favorites.has(e.server.id));
    if (protocolFilter) result = result.filter((e) => e.server.protocol.kind === protocolFilter);
    return result;
  }, [baseEntries, sortMode, serverPings, customOrder, showFavOnly, favorites, pingOrder, protocolFilter]);

  const onReorder = useCallback(
    (reordered: ServerEntry[]) => {
      const order = reordered.map((e) => e.server.id);
      setCustomOrder(order);
      if (currentSubUrl) writeServerOrder(currentSubUrl, order);
    },
    [currentSubUrl],
  );

  const activeEntry = useMemo(() => {
    if (!activeId) return null;
    const inCurrent = baseEntries.find((item) => item.server.id === activeId);
    if (inCurrent) return inCurrent;
    for (const sub of visibleSubs) {
      const server = sub.servers.find((s) => s.id === activeId);
      if (server) return { server, sub };
    }
    return null;
  }, [activeId, baseEntries, visibleSubs]);
  const fallbackEntry = activeEntry ?? baseEntries[0] ?? null;
  const connected = status?.state === "connected";
  const connecting = Boolean(connectingServerId);

  useEffect(() => {
    if (!connected) {
      setConnectedAt(null);
      setElapsedSeconds(0);
      setSessionTraffic({ upload: 0, download: 0 });
      setSpeedSamples([]);
      setMemorySamples([]);
      setCurrentMemoryBytes(0);
      previousTrafficRef.current = null;
      return;
    }
    setConnectedAt((current) => current ?? Date.now());
  }, [connected]);

  useEffect(() => {
    if (!connected || connectedAt == null) return;
    const tick = () =>
      setElapsedSeconds(Math.max(0, Math.floor((Date.now() - connectedAt) / 1000)));
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [connected, connectedAt]);

  useEffect(() => {
    if (!connected) return;
    let cancelled = false;
    const loadTraffic = async () => {
      try {
        const traffic = await api.getSessionTraffic();
        if (cancelled) return;
        setSessionTraffic(traffic);
        const now = Date.now();
        const prev = previousTrafficRef.current;
        previousTrafficRef.current = { upload: traffic.upload, download: traffic.download, at: now };
        if (prev) {
          const elapsed = Math.max(0.001, (now - prev.at) / 1000);
          const uploadDelta = Math.max(0, traffic.upload - prev.upload);
          const downloadDelta = Math.max(0, traffic.download - prev.download);
          const uploadBps = uploadDelta / elapsed;
          const downloadBps = downloadDelta / elapsed;
          setSpeedSamples((current) => {
            const next = [...current, { upload: uploadBps, download: downloadBps, at: now }];
            return next.length > SPEED_HISTORY_LIMIT ? next.slice(-SPEED_HISTORY_LIMIT) : next;
          });
        }
      } catch {
        if (!cancelled) setSessionTraffic((current) => current);
      }
    };
    void loadTraffic();
    const timer = window.setInterval(() => void loadTraffic(), 1000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [connected]);

  useEffect(() => {
    if (!connected || !preferences.show_memory_usage) return;
    let cancelled = false;
    const loadMemory = async () => {
      try {
        const usage = await api.getMemoryUsage();
        if (cancelled) return;
        setCurrentMemoryBytes(usage.bytes);
        setMemorySamples((current) => {
          const next = [...current, usage.bytes];
          return next.length > MEMORY_HISTORY_LIMIT ? next.slice(-MEMORY_HISTORY_LIMIT) : next;
        });
      } catch {
        /* ignore */
      }
    };
    void loadMemory();
    const timer = window.setInterval(() => void loadMemory(), 2000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [connected, preferences.show_memory_usage]);

  const onToggleServer = async (serverId: string) => {
    try {
      await setActive(activeId === serverId ? null : serverId);
    } catch (e) {
      const message = String(e);
      if (isAdminRestartError(message)) setAdminDialogOpen(true);
      else notifyError(message);
    }
  };

  const onRefreshSelected = async () => {
    if (!currentSub) return;
    setRefreshingUrl(currentSub.url);
    try {
      await refreshSubscription(currentSub.url);
    } catch (e) {
      notifyError(String(e));
    } finally {
      setRefreshingUrl(null);
    }
  };

  const onPingServers = async () => {
    if (!baseEntries.length) return;
    const serverIds = baseEntries.map(({ server }) => server.id);
    setPinging(true);
    setPingingServerIds(new Set(serverIds));
    try {
      await pingServersProgressively(serverIds, (result) => {
        setPingingServerIds((current) => {
          const next = new Set(current);
          next.delete(result.server_id);
          return next;
        });
        if (result.latency_ms != null) setServerPing(result.server_id, result.latency_ms);
      });
    } finally {
      setPinging(false);
      setPingingServerIds(new Set());
    }
  };

  const onPingServer = async (serverId: string) => {
    setPingingServerIds((current) => {
      const next = new Set(current);
      next.add(serverId);
      return next;
    });
    try {
      const result = await api.pingServer(serverId);
      if (result.latency_ms != null) setServerPing(result.server_id, result.latency_ms);
    } catch (e) {
      notifyError(String(e));
    } finally {
      setPingingServerIds((current) => {
        const next = new Set(current);
        next.delete(serverId);
        return next;
      });
    }
  };

  const onToggleConnection = async () => {
    try {
      if (connected) {
        await disconnectServer();
        return;
      }
      if (!fallbackEntry) {
        notifyError(m.home.addProfileFirst);
        return;
      }
      await connectServer(fallbackEntry.server.id);
    } catch (e) {
      const message = String(e);
      if (isAdminRestartError(message)) setAdminDialogOpen(true);
      else notifyError(message);
    }
  };

  const onSwitchSubscription = async (url: string) => {
    if (currentSub?.url === url) return;
    try {
      await setActiveSubscription(url);
    } catch (e) {
      notifyError(String(e));
    }
  };

  const onNavigateToProfile = () => {
    const sub = activeEntry?.sub ?? currentSub;
    if (sub) navigate(`/subscriptions/${encodeURIComponent(sub.url)}`);
  };

  const sharedPanelProps = {
    subs: visibleSubs,
    currentSub,
    entries: sortedEntries,
    activeId,
    pingByServer: serverPings,
    pingingServerIds,
    favorites,
    onToggleFavorite: toggleFavorite,
    sortMode,
    onSortMode: setSortMode,
    pingOrder,
    onPingOrder: setPingOrder,
    protocolFilter,
    onProtocolFilter: setProtocolFilter,
    availableProtocols,
    showFavOnly,
    onShowFavOnly: setShowFavOnly,
    onPickServer: onToggleServer,
    pinging,
    onPing: onPingServers,
    onPingServer,
    onSwitchSubscription,
    onReorder,
    listCollapsed: serverListCollapsed,
    onToggleListCollapsed: toggleServerListCollapsed,
    labels: m,
  };

  return (
    <div
      className={[
        "home-grid h-full",
        sidePanelCollapsed ? "home-grid-collapsed" : "",
        !sidePanelCollapsed && serverListCollapsed && sortedEntries.length ? "home-grid-server-list-collapsed" : "",
      ].join(" ")}
    >
      <section className="home-center">
        <div className="home-top">
          <ProfileSummary
            sub={currentSub}
            refreshing={refreshingUrl === currentSub?.url}
            onRefresh={onRefreshSelected}
            labels={m}
          />
        </div>

        <div className="home-middle">
          <ConnectionButton
            connected={connected}
            connecting={connecting}
            disconnecting={disconnecting}
            onClick={onToggleConnection}
            labels={m}
          />

          <div
            className={[
              "connection-status mt-5",
              connected ? "connection-status-connected" : "",
              connecting ? "connection-status-connecting" : "",
              disconnecting ? "connection-status-disconnecting" : "",
            ].join(" ")}
          >
            {disconnecting
              ? m.home.disconnecting
              : connecting
                ? m.home.connecting
                : connected
                  ? `${m.home.connected} ${formatDuration(elapsedSeconds)}`
                  : m.home.pressToConnect}
          </div>

          <CompactServerBar
            entry={fallbackEntry}
            ping={fallbackEntry ? serverPings[fallbackEntry.server.id] : undefined}
            onNavigate={onNavigateToProfile}
            onOpenList={() => setCompactSheetOpen(true)}
            labels={m}
          />
        </div>

        <div className="home-bottom">
          {connected && (
            <div className="home-bottom-stack">
              <button
                type="button"
                onClick={toggleWidgetsCollapsed}
                aria-expanded={!widgetsCollapsed}
                className="home-widgets-toggle"
              >
                <span>{m.home.networkSpeed}</span>
                <WidgetsChevron open={!widgetsCollapsed} />
              </button>
              {!widgetsCollapsed && (
                <>
                  {preferences.show_speed_chart && (
                    <NetworkSpeedChart samples={speedSamples} labels={m} />
                  )}
                  <SessionTrafficBlocks
                    upload={sessionTraffic.upload}
                    download={sessionTraffic.download}
                  />
                  {preferences.show_memory_usage && (
                    <MemoryUsageCard
                      bytes={currentMemoryBytes}
                      samples={memorySamples}
                      labels={m}
                    />
                  )}
                </>
              )}
            </div>
          )}
        </div>
      </section>

      {!sidePanelCollapsed && (
        <aside className="home-right">
          <ServerSidePanel
            {...sharedPanelProps}
            onCollapseSidePanel={toggleSidePanelCollapsed}
          />
        </aside>
      )}
      {sidePanelCollapsed && (
        <button
          type="button"
          onClick={toggleSidePanelCollapsed}
          className="home-side-expand"
          title={m.home.changeServer}
          aria-label={m.home.changeServer}
        >
          <ChevronLeftIcon />
        </button>
      )}

      {compactSheetOpen && (
        <CompactServerSheet
          {...sharedPanelProps}
          onClose={() => setCompactSheetOpen(false)}
        />
      )}

      {adminDialogOpen && <AdminRestartDialog onClose={() => setAdminDialogOpen(false)} />}
    </div>
  );
}

// ── CompactServerBar ──────────────────────────────────────────

function CompactServerBar({
  entry,
  ping,
  onNavigate,
  onOpenList,
  labels,
}: {
  entry: ServerEntry | null;
  ping?: number;
  onNavigate: () => void;
  onOpenList: () => void;
  labels: Messages;
}) {
  if (!entry) return null;
  const label = serverDisplayName(entry.server.name);

  return (
    <div className="compact-server-bar mt-4 flex w-full max-w-[400px] items-center gap-1.5">
      <button
        onClick={onNavigate}
        className="flex h-14 min-w-0 flex-1 items-center gap-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-glass-bg)] px-3 text-left transition-all hover:border-[var(--color-border-strong)] hover:bg-[var(--color-glass-bg-strong)]"
      >
        <div className="grid h-8 w-8 shrink-0 place-items-center rounded-md bg-[var(--color-glass-bg)]">
          <CountryFlag
            serverName={entry.server.name}
            fallback={<GlobeIcon />}
            className="country-flag-sm"
          />
        </div>
        <span className="flex-1 truncate text-[15px] font-semibold text-white">{label}</span>
        {ping != null && (
          <span
            className="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold tabular-nums"
            style={(() => {
              const tier = pingTier(ping);
              return { background: tier.bg, color: tier.fg };
            })()}
          >
            {ping} ms
          </span>
        )}
      </button>
      <button
        onClick={onOpenList}
        title={labels.home.changeServer}
        className="grid h-14 w-14 shrink-0 place-items-center rounded-xl border border-[var(--color-border)] bg-[var(--color-glass-bg)] text-[var(--color-text-faint)] transition-all hover:border-[var(--color-border-strong)] hover:bg-[var(--color-glass-bg-strong)] hover:text-white"
      >
        <ListIcon />
      </button>
    </div>
  );
}

// ── CompactServerSheet ────────────────────────────────────────

type PanelProps = {
  subs: Subscription[];
  currentSub: Subscription | null;
  entries: ServerEntry[];
  activeId: string | null;
  pingByServer: Record<string, number>;
  pingingServerIds: ReadonlySet<string>;
  favorites: ReadonlySet<string>;
  onToggleFavorite: (id: string) => void;
  sortMode: SortMode;
  onSortMode: (mode: SortMode) => void;
  pingOrder: "asc" | "desc";
  onPingOrder: (order: "asc" | "desc") => void;
  protocolFilter: string | null;
  onProtocolFilter: (proto: string | null) => void;
  availableProtocols: string[];
  showFavOnly: boolean;
  onShowFavOnly: (v: boolean) => void;
  pinging: boolean;
  onPing: () => void;
  onPingServer: (serverId: string) => Promise<void>;
  onPickServer: (serverId: string) => Promise<void>;
  onSwitchSubscription: (url: string) => Promise<void>;
  onReorder: (entries: ServerEntry[]) => void;
  listCollapsed: boolean;
  onToggleListCollapsed: () => void;
  onCollapseSidePanel?: () => void;
  labels: Messages;
};

function CompactServerSheet({
  onClose,
  ...panelProps
}: PanelProps & { onClose: () => void }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-40 flex flex-col"
      style={{ background: "rgba(15,15,26,0.97)", backdropFilter: "blur(20px)" }}
    >
      <div className="flex items-center gap-3 border-b border-[var(--color-border)] px-4 py-3">
        <h2 className="flex-1 text-base font-bold text-white">
          {panelProps.labels.home.changeServer}
        </h2>
        <button
          onClick={onClose}
          className="grid h-8 w-8 place-items-center rounded-lg bg-[var(--color-glass-bg)] text-[var(--color-text-faint)] hover:text-white"
        >
          <CloseIcon />
        </button>
      </div>
      <div className="flex-1 overflow-hidden">
        <ServerSidePanel {...panelProps} />
      </div>
    </div>
  );
}

// ── ServerSidePanel ───────────────────────────────────────────

function ServerSidePanel({
  subs,
  currentSub,
  entries,
  activeId,
  pingByServer,
  pingingServerIds,
  favorites,
  onToggleFavorite,
  sortMode,
  onSortMode,
  pingOrder,
  onPingOrder,
  protocolFilter,
  onProtocolFilter,
  availableProtocols,
  showFavOnly,
  onShowFavOnly,
  pinging,
  onPing,
  onPingServer,
  onPickServer,
  onSwitchSubscription,
  onReorder,
  listCollapsed,
  onToggleListCollapsed,
  onCollapseSidePanel,
  labels,
}: PanelProps) {
  const [switcherOpen, setSwitcherOpen] = useState(false);
  const switcherRef = useDismissable(switcherOpen, () => setSwitcherOpen(false));
  const showSwitcher = subs.length > 1;
  const currentName = currentSub?.name?.trim() || labels.common.subscription;

  const isReorderable = sortMode === "default";

  const handleMoveUp = (idx: number) => {
    if (idx === 0) return;
    const next = [...entries];
    [next[idx - 1], next[idx]] = [next[idx], next[idx - 1]];
    onReorder(next);
  };

  const handleMoveDown = (idx: number) => {
    if (idx === entries.length - 1) return;
    const next = [...entries];
    [next[idx], next[idx + 1]] = [next[idx + 1], next[idx]];
    onReorder(next);
  };

  if (!entries.length && !showFavOnly) {
    return (
      <div className="server-side-panel flex flex-col">
        <Link
          to="/subscriptions"
          className="interactive panel mx-3 mt-3 grid grid-cols-[28px_1fr_18px] items-center gap-2.5 px-3 py-2.5 text-left"
        >
          <div className="grid h-7 w-7 place-items-center rounded-lg bg-[var(--color-glass-bg)] text-[var(--color-text-faint)]">
            <GlobeIcon />
          </div>
          <div className="min-w-0 text-[13px] font-medium text-[var(--color-text-dim)]">
            {labels.common.serverNotSelected}
          </div>
          <ChevronDownIcon open={false} />
        </Link>
      </div>
    );
  }

  return (
    <div
      className={["server-side-panel flex flex-col", listCollapsed ? "server-side-panel-collapsed" : "h-full"].join(" ")}
    >
      {/* Servers plaque + side-collapse button on the same row */}
      <div className="server-plaque-row">
        <button
          type="button"
          onClick={onToggleListCollapsed}
          aria-expanded={!listCollapsed}
          className="server-plaque"
        >
          <span className="server-plaque-label">{labels.common.servers}</span>
          <span className="server-plaque-count">{entries.length}</span>
          <ChevronDownIcon open={!listCollapsed} />
        </button>
        {onCollapseSidePanel && (
          <button
            type="button"
            onClick={onCollapseSidePanel}
            className="server-side-collapse"
            title={labels.common.close}
            aria-label={labels.common.close}
          >
            <ChevronRightIcon />
          </button>
        )}
      </div>
      {/* Subscription switcher */}
      {showSwitcher && !listCollapsed && (
        <div ref={switcherRef} className="px-3 pt-3">
          <button
            onClick={() => setSwitcherOpen((v) => !v)}
            className="interactive panel grid w-full grid-cols-[28px_1fr_18px] items-center gap-2.5 px-3 py-2.5 text-left"
          >
            <div className="grid h-7 w-7 place-items-center rounded-lg bg-[var(--color-glass-bg)] text-[var(--color-text-faint)]">
              <GlobeIcon />
            </div>
            <div className="min-w-0">
              <div className="text-[10px] uppercase tracking-wider text-[var(--color-text-faint)]">
                {labels.common.subscription}
              </div>
              <div className="truncate text-[13px] font-semibold text-white">{currentName}</div>
            </div>
            <ChevronDownIcon open={switcherOpen} />
          </button>
          {switcherOpen && (
            <div className="panel mt-2 max-h-[260px] overflow-auto py-1" onWheel={(e) => e.stopPropagation()}>
              {subs.map((sub) => {
                const name = sub.name?.trim() || sub.url;
                const active = currentSub?.url === sub.url;
                return (
                  <button
                    key={sub.url}
                    onClick={() => {
                      setSwitcherOpen(false);
                      void onSwitchSubscription(sub.url);
                    }}
                    className={[
                      "grid w-full grid-cols-[24px_1fr_auto] items-center gap-2 px-3 py-2 text-left transition-all hover:bg-[var(--color-glass-bg)]",
                      active ? "bg-[var(--color-glass-bg-strong)]" : "",
                    ].join(" ")}
                  >
                    <div className="grid h-6 w-6 place-items-center rounded-lg bg-[var(--color-glass-bg)] text-[var(--color-text-faint)]">
                      <GlobeIcon />
                    </div>
                    <div className="min-w-0">
                      <div className="truncate text-[13px] font-semibold text-white">{name}</div>
                      <div className="truncate text-[11px] text-[var(--color-text-faint)]">
                        {sub.servers.length} {labels.common.servers}
                      </div>
                    </div>
                    {active && (
                      <span className="rounded-full bg-[var(--color-accent-active-bg)] px-2 py-0.5 text-[10px] font-bold text-[var(--color-accent-bright)]">
                        ●
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Sort & favorites controls */}
      {!listCollapsed && (
      <div className="px-3 pt-2 pb-1 space-y-1.5">
        <div className="flex items-center gap-1.5">
          <button
            onClick={() => onShowFavOnly(!showFavOnly)}
            title={showFavOnly ? labels.home.showAll : labels.home.favoritesOnly}
            className={[
              "shrink-0 flex h-8 w-8 items-center justify-center rounded-xl transition-all",
              showFavOnly
                ? "bg-[var(--color-accent-active-bg)] text-[var(--color-accent-bright)] ring-1 ring-[color-mix(in_srgb,var(--color-accent)_40%,transparent)]"
                : "bg-[var(--color-glass-bg)] text-[var(--color-text-faint)] hover:bg-[var(--color-glass-bg-strong)] hover:text-white",
            ].join(" ")}
          >
            <StarIcon filled={showFavOnly} />
          </button>
          <div className="flex flex-1 items-center gap-0.5 overflow-hidden rounded-xl border border-[var(--color-border)] bg-[rgba(255,255,255,0.04)] p-0.5">
            {(["default", "ping", "protocol"] as SortMode[]).map((mode) => {
              const active = sortMode === mode;
              const isPing = mode === "ping";
              return (
                <button
                  key={mode}
                  onClick={() => {
                    if (isPing && active) {
                      onPingOrder(pingOrder === "asc" ? "desc" : "asc");
                    } else {
                      onSortMode(mode);
                      if (mode !== "protocol") onProtocolFilter(null);
                    }
                  }}
                  className={[
                    "flex min-w-0 flex-1 items-center justify-center gap-1 truncate rounded-lg py-1.5 px-1.5 text-[11px] font-semibold transition-all",
                    active
                      ? isPing
                        ? "bg-[var(--color-accent-active-bg)] text-[var(--color-accent-bright)] shadow-sm"
                        : "bg-[rgba(255,255,255,0.1)] text-white shadow-sm"
                      : "text-[var(--color-text-faint)] hover:text-[var(--color-text)]",
                  ].join(" ")}
                >
                  <span className="truncate">
                    {mode === "default"
                      ? labels.home.sortDefault
                      : mode === "ping"
                        ? labels.home.sortPing
                        : labels.home.sortProtocol}
                  </span>
                  {isPing && <PingSortArrow active={active} order={pingOrder} />}
                </button>
              );
            })}
          </div>
          <button
            onClick={onPing}
            title={labels.home.pingServers}
            className={[
              "shrink-0 flex h-8 w-8 items-center justify-center rounded-xl transition-all",
              pinging
                ? "bg-[var(--color-accent-active-bg)] text-[var(--color-accent-bright)]"
                : "bg-[var(--color-glass-bg)] text-[var(--color-text-faint)] hover:bg-[var(--color-glass-bg-strong)] hover:text-white",
            ].join(" ")}
          >
            <PingIcon spinning={pinging} />
          </button>
        </div>
        {sortMode === "protocol" && availableProtocols.length > 1 && (
          <div className="flex flex-wrap gap-1 px-0.5">
            {availableProtocols.map((proto) => {
              const isActive = protocolFilter === proto;
              return (
                <button
                  key={proto}
                  onClick={() => onProtocolFilter(isActive ? null : proto)}
                  className={[
                    "flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[10px] font-bold transition-all",
                    isActive
                      ? "bg-[var(--color-accent-active-bg)] text-[var(--color-accent-bright)] ring-1 ring-[color-mix(in_srgb,var(--color-accent)_40%,transparent)]"
                      : "bg-[var(--color-glass-bg)] text-[var(--color-text-faint)] hover:bg-[var(--color-glass-bg-strong)] hover:text-white",
                  ].join(" ")}
                >
                  {protoName(proto)}
                  {isActive && <span className="opacity-70">×</span>}
                </button>
              );
            })}
          </div>
        )}
      </div>
      )}

      {/* Server list */}
      {!listCollapsed && (
      <div className="server-side-list mt-1 flex-1 overflow-y-auto px-2 pb-3">
        {entries.length === 0 ? (
          <div className="px-3 py-8 text-center text-[12px] text-[var(--color-text-faint)]">
            {labels.home.noFavorites}
          </div>
        ) : (
          entries.map(({ server }, idx) => {
            const label = serverDisplayName(server.name);
            const description = serverListDescription(
              server,
              entries.map((e) => e.server),
            );
            const isActive = activeId === server.id;
            const isFav = favorites.has(server.id);

            return (
              <div
                key={server.id}
                className={[
                  "group relative flex items-center rounded-lg transition-all",
                  isActive ? "bg-[var(--color-glass-bg-strong)]" : "hover:bg-[var(--color-glass-bg)]",
                ].join(" ")}
              >
                {isReorderable && (
                  <div className="flex shrink-0 flex-col items-center gap-0 pl-1">
                    <button
                      type="button"
                      onClick={(e) => { e.stopPropagation(); handleMoveUp(idx); }}
                      disabled={idx === 0}
                      className="flex h-4 w-5 items-center justify-center rounded text-[var(--color-text-faint)] opacity-0 transition-all group-hover:opacity-40 hover:!opacity-90 hover:text-[var(--color-accent-bright)] disabled:pointer-events-none disabled:!opacity-0"
                      aria-label="Move up"
                    >
                      <ChevronUpSmIcon />
                    </button>
                    <button
                      type="button"
                      onClick={(e) => { e.stopPropagation(); handleMoveDown(idx); }}
                      disabled={idx === entries.length - 1}
                      className="flex h-4 w-5 items-center justify-center rounded text-[var(--color-text-faint)] opacity-0 transition-all group-hover:opacity-40 hover:!opacity-90 hover:text-[var(--color-accent-bright)] disabled:pointer-events-none disabled:!opacity-0"
                      aria-label="Move down"
                    >
                      <ChevronDownSmIcon />
                    </button>
                  </div>
                )}
                <button
                  onClick={() => void onPickServer(server.id)}
                  className="flex min-w-0 flex-1 items-center gap-2.5 py-3 pl-2.5 pr-1 text-left"
                >
                  <div className="grid h-8 w-8 shrink-0 place-items-center rounded-md bg-[var(--color-glass-bg)] text-[var(--color-text-faint)]">
                    <CountryFlag
                      serverName={server.name}
                      fallback={<GlobeIcon />}
                      className="country-flag-sm"
                    />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[14px] font-semibold text-white">{label}</div>
                    {description && (
                      <div className="truncate text-[12px] text-[var(--color-text-faint)]">
                        {description}
                      </div>
                    )}
                  </div>
                </button>
                <div className="flex shrink-0 items-center gap-1 pr-2">
                  <PingBadge
                    ping={pingByServer[server.id]}
                    loading={pingingServerIds.has(server.id)}
                  />
                  <button
                    onClick={() => onToggleFavorite(server.id)}
                    title={isFav ? "Убрать из избранных" : "В избранное"}
                    className={[
                      "grid h-7 w-7 shrink-0 place-items-center rounded-md transition-all",
                      isFav
                        ? "text-[var(--color-accent-bright)] opacity-100"
                        : "text-[var(--color-text-faint)] opacity-0 group-hover:opacity-60",
                    ].join(" ")}
                  >
                    <StarIcon filled={isFav} />
                  </button>
                  <button
                    onClick={() => void onPingServer(server.id)}
                    title={labels.home.pingServers}
                    aria-label={labels.home.pingServers}
                    disabled={pingingServerIds.has(server.id)}
                    className={[
                      "grid h-7 w-7 shrink-0 place-items-center rounded-md transition-all",
                      pingingServerIds.has(server.id)
                        ? "text-[var(--color-accent-bright)] opacity-100"
                        : "text-[var(--color-text-faint)] opacity-0 group-hover:opacity-70 hover:bg-[var(--color-glass-bg-strong)] hover:text-white",
                    ].join(" ")}
                  >
                    <PingIcon spinning={pingingServerIds.has(server.id)} />
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
      )}
    </div>
  );
}

// ── ConnectionButton ──────────────────────────────────────────

function ConnectionButton({
  connected,
  connecting,
  disconnecting,
  onClick,
  labels,
}: {
  connected: boolean;
  connecting: boolean;
  disconnecting: boolean;
  onClick: () => void;
  labels: Messages;
}) {
  const busy = connecting || disconnecting;
  const title = disconnecting
    ? labels.home.disconnecting
    : connected
      ? labels.home.disconnect
      : labels.home.connect;

  return (
    <button
      onClick={onClick}
      disabled={busy}
      title={title}
      aria-label={title}
      className={[
        "connection-button",
        connected ? "connection-button-connected" : "",
        connecting ? "connection-button-connecting" : "",
        disconnecting ? "connection-button-disconnecting" : "",
      ].join(" ")}
    >
      <span className="connection-button-halo" />
      <span className="connection-button-ring" />
      <span className="connection-button-core">
        {busy ? (
          <span className="connection-button-loader" />
        ) : connected ? (
          <ShieldButtonIcon />
        ) : (
          <PowerButtonIcon />
        )}
      </span>
    </button>
  );
}

// ── AdminRestartDialog ────────────────────────────────────────

function AdminRestartDialog({ onClose }: { onClose: () => void }) {
  const m = useMessages();
  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center p-5"
      style={{ background: "rgba(0,0,0,0.75)", backdropFilter: "blur(12px)" }}
      role="presentation"
      onClick={onClose}
    >
      <div
        className="panel w-full max-w-sm bg-[rgb(26,26,46)] p-6 text-center shadow-[0_24px_80px_rgba(0,0,0,0.9)]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-dialog-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mx-auto mb-4 grid h-16 w-16 place-items-center rounded-full bg-[var(--color-accent-active-bg)] text-[var(--color-accent-bright)]">
          <ShieldAlertIcon />
        </div>
        <h2 id="admin-dialog-title" className="mb-3 text-xl font-black text-white">
          {m.home.adminTitle}
        </h2>
        <p className="mb-6 text-sm font-medium leading-relaxed text-[var(--color-text-dim)]">
          {m.home.adminText}
        </p>
        <div className="grid grid-cols-1 gap-2">
          <button
            onClick={onClose}
            className="primary-button interactive rounded-xl py-3 text-base font-bold"
          >
            {m.common.ok}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── ProfileSummary ────────────────────────────────────────────

function ProfileSummary({
  sub,
  refreshing,
  onRefresh,
  labels,
}: {
  sub: Subscription | null;
  refreshing: boolean;
  onRefresh: () => void;
  labels: Messages;
}) {
  if (!sub) return null;

  const used = (sub.info?.upload ?? 0) + (sub.info?.download ?? 0);
  const total = sub.info?.total ?? null;
  const expires = formatExpire(sub.info?.expire);
  const description = sub.meta?.description?.trim() || "";

  return (
    <div className="home-stack w-full max-w-[740px] space-y-2">
      <div className="panel px-4 py-2.5">
        <div className="grid grid-cols-[1fr_auto] items-start gap-2">
          <div className="min-w-0">
            <div className="truncate text-[15px] font-semibold text-white">
              {sub.name ?? labels.common.subscription}
            </div>
            <div className="mt-0.5 text-[12px] text-[var(--color-text-dim)]">{expires}</div>
          </div>
          <div className="flex gap-2">
            <SmallActionButton title={labels.home.refreshSubscription} onClick={onRefresh}>
              <RefreshIcon spinning={refreshing} />
            </SmallActionButton>
          </div>
        </div>
        {description && (
          <div
            className="profile-summary-description mt-1.5 text-[12px] leading-relaxed text-[var(--color-text-faint)]"
            title={description}
          >
            {description}
          </div>
        )}
        <div className="mt-2 h-1 rounded-full bg-[var(--color-glass-bg)]" />
        <div className="mt-2 text-[12px] font-semibold tabular-nums text-[var(--color-text-dim)]">
          {formatBytes(used)}
          {total ? ` / ${formatBytes(total)}` : " / ∞"}
        </div>
      </div>
    </div>
  );
}

// ── PingBadge ─────────────────────────────────────────────────

function PingBadge({ ping, loading = false }: { ping?: number; loading?: boolean }) {
  if (loading) {
    return (
      <span className="shrink-0 rounded-full bg-[var(--color-accent-active-bg)] px-2 py-1 text-[11px] font-semibold tabular-nums text-[var(--color-accent-bright)]">
        ...
      </span>
    );
  }
  if (ping == null) return null;
  const tier = pingTier(ping);
  return (
    <span
      className="shrink-0 rounded-full px-2 py-1 text-[11px] font-semibold tabular-nums"
      style={{ background: tier.bg, color: tier.fg }}
      title={tier.label}
    >
      {ping} ms
    </span>
  );
}

function pingTier(ping: number): { bg: string; fg: string; label: string } {
  if (ping < 100) {
    return { bg: "rgba(76, 217, 100, 0.16)", fg: "#7be084", label: "Хороший пинг" };
  }
  if (ping < 300) {
    return { bg: "rgba(245, 192, 64, 0.16)", fg: "#f5c040", label: "Средний пинг" };
  }
  return { bg: "rgba(239, 83, 80, 0.18)", fg: "#ff8080", label: "Высокий пинг" };
}

// ── SessionTrafficBlocks ──────────────────────────────────────

// ── NetworkSpeedChart ─────────────────────────────────────────

function formatSpeed(bytesPerSecond: number): string {
  if (!Number.isFinite(bytesPerSecond) || bytesPerSecond <= 0) return "0 B/s";
  const units = ["B/s", "KB/s", "MB/s", "GB/s"];
  let v = bytesPerSecond;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  const precision = v >= 100 ? 0 : v >= 10 ? 1 : v >= 1 ? 2 : 2;
  return `${v.toFixed(precision)} ${units[i]}`;
}

function NetworkSpeedChart({
  samples,
  labels,
}: {
  samples: SpeedSample[];
  labels: Messages;
}) {
  const latest = samples[samples.length - 1];
  const uploadBps = latest?.upload ?? 0;
  const downloadBps = latest?.download ?? 0;

  const width = 320;
  const height = 110;
  const padding = 4;
  const allValues = samples.flatMap((s) => [s.upload, s.download]);
  const peak = Math.max(1, ...allValues);
  const points = Math.max(samples.length, 2);

  const buildPath = (key: "upload" | "download") => {
    if (samples.length < 2) {
      const y = height - padding;
      return `M ${padding} ${y} L ${width - padding} ${y}`;
    }
    const stepX = (width - padding * 2) / (points - 1);
    return samples
      .map((s, idx) => {
        const x = padding + idx * stepX;
        const normalized = s[key] / peak;
        const y = height - padding - normalized * (height - padding * 2);
        return `${idx === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
      })
      .join(" ");
  };

  const buildArea = (key: "upload" | "download") => {
    const linePath = buildPath(key);
    return `${linePath} L ${width - padding} ${height - padding} L ${padding} ${height - padding} Z`;
  };

  return (
    <div className="speed-chart-card">
      <div className="speed-chart-header">
        <div className="speed-chart-title">
          <SpeedIcon />
          <span>{labels.home.networkSpeed}</span>
        </div>
        <div className="speed-chart-values">
          <span className="speed-chart-value-up">
            <UploadIcon /> {formatSpeed(uploadBps)}
          </span>
          <span className="speed-chart-value-down">
            <DownloadIcon /> {formatSpeed(downloadBps)}
          </span>
        </div>
      </div>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        preserveAspectRatio="none"
        className="speed-chart-svg"
        aria-hidden="true"
      >
        <defs>
          <linearGradient id="speed-chart-down-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--color-accent-bright)" stopOpacity="0.32" />
            <stop offset="100%" stopColor="var(--color-accent-bright)" stopOpacity="0" />
          </linearGradient>
          <linearGradient id="speed-chart-up-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#7be084" stopOpacity="0.22" />
            <stop offset="100%" stopColor="#7be084" stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={buildArea("download")} fill="url(#speed-chart-down-fill)" />
        <path
          d={buildPath("download")}
          fill="none"
          stroke="var(--color-accent-bright)"
          strokeWidth="1.6"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        <path d={buildArea("upload")} fill="url(#speed-chart-up-fill)" />
        <path
          d={buildPath("upload")}
          fill="none"
          stroke="#7be084"
          strokeWidth="1.4"
          strokeLinejoin="round"
          strokeLinecap="round"
          strokeDasharray="3 2"
        />
      </svg>
    </div>
  );
}

// ── MemoryUsageCard ───────────────────────────────────────────

function MemoryUsageCard({
  bytes,
  samples,
  labels,
}: {
  bytes: number;
  samples: number[];
  labels: Messages;
}) {
  const width = 320;
  const height = 72;
  const padding = 5;
  const values = samples.length ? samples : [bytes];
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const range = Math.max(1, maxValue - minValue, maxValue * 0.08);

  const buildPath = () => {
    if (values.length < 2) {
      const y = height - padding;
      return `M ${padding} ${y} L ${width - padding} ${y}`;
    }
    const stepX = (width - padding * 2) / (values.length - 1);
    return values
      .map((value, idx) => {
        const x = padding + idx * stepX;
        const normalized = (value - minValue) / range;
        const y = height - padding - normalized * (height - padding * 2);
        return `${idx === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
      })
      .join(" ");
  };

  const buildArea = () =>
    `${buildPath()} L ${width - padding} ${height - padding} L ${padding} ${height - padding} Z`;

  return (
    <div className="memory-card">
      <div className="memory-card-header">
        <div className="memory-card-title">
          <ChipIcon />
          <span>{labels.home.memoryUsage}</span>
        </div>
        <span className="memory-card-value">{formatBytes(bytes)}</span>
      </div>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        preserveAspectRatio="none"
        className="memory-card-svg"
        aria-hidden="true"
      >
        <defs>
          <linearGradient id="memory-card-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--color-accent-bright)" stopOpacity="0.24" />
            <stop offset="100%" stopColor="var(--color-accent-bright)" stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={buildArea()} fill="url(#memory-card-fill)" />
        <path
          d={buildPath()}
          fill="none"
          stroke="var(--color-accent-bright)"
          strokeWidth="1.6"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
      </svg>
    </div>
  );
}

function SpeedIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 14a4 4 0 1 0-4-4" />
      <path d="m12 14 4-4" />
      <path d="M20 12a8 8 0 1 0-13.66 5.66" />
    </svg>
  );
}

function ChipIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="6" y="6" width="12" height="12" rx="2" />
      <path d="M9 3v3M15 3v3M9 18v3M15 18v3M3 9h3M3 15h3M18 9h3M18 15h3" />
    </svg>
  );
}

function SessionTrafficBlocks({
  upload,
  download,
}: {
  upload: number;
  download: number;
}) {
  const m = useMessages();
  return (
    <div className="session-traffic mb-5 grid w-full max-w-[420px] grid-cols-2 gap-3">
      <div className="session-traffic-card">
        <div className="mb-1 flex items-center gap-2">
          <UploadIcon />
          <div className="session-traffic-label">{m.home.upload}</div>
        </div>
        <div className="session-traffic-value">{formatBytes(upload)}</div>
      </div>
      <div className="session-traffic-card">
        <div className="mb-1 flex items-center gap-2">
          <DownloadIcon />
          <div className="session-traffic-label">{m.home.downloaded}</div>
        </div>
        <div className="session-traffic-value">{formatBytes(download)}</div>
      </div>
    </div>
  );
}

// ── Utilities ─────────────────────────────────────────────────

function formatDuration(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (value: number) => value.toString().padStart(2, "0");
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

function isAdminRestartError(message: string): boolean {
  const normalized = message.toLowerCase();
  return normalized.includes("tun") && normalized.includes("администратор");
}

function SmallActionButton({
  children,
  title,
  onClick,
}: {
  children: ReactNode;
  title: string;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      title={title}
      className="interactive grid h-10 w-10 place-items-center rounded-lg border border-[var(--color-border)] bg-[var(--color-glass-bg)] text-[var(--color-text-dim)]"
    >
      {children}
    </button>
  );
}

// ── StarIcon ──────────────────────────────────────────────────

function StarIcon({ filled }: { filled: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4.5 w-4.5"
      style={{ width: "18px", height: "18px" }}
      fill={filled ? "currentColor" : "none"}
      stroke="currentColor"
      strokeWidth={filled ? "0" : "1.8"}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
    </svg>
  );
}

// ── Protocol helpers ──────────────────────────────────────────

function protoName(kind: string): string {
  switch (kind) {
    case "vless": return "VLESS";
    case "vmess": return "VMess";
    case "trojan": return "Trojan";
    case "shadowsocks": return "SS";
    default: return kind.toUpperCase();
  }
}

// ── PingSortArrow ─────────────────────────────────────────────

function PingSortArrow({ active, order }: { active: boolean; order: "asc" | "desc" }) {
  return (
    <svg
      viewBox="0 0 10 14"
      className={[
        "h-3 w-2.5 shrink-0 transition-all duration-200",
        active ? "opacity-100" : "opacity-40",
        order === "desc" ? "rotate-180" : "",
      ].join(" ")}
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M5 12V2" />
      <path d="M1 6l4-4 4 4" />
    </svg>
  );
}

// ── Icons ─────────────────────────────────────────────────────

function ShieldButtonIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="connection-button-icon"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.4"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path
        fill="currentColor"
        stroke="none"
        d="M12 2.7 19 5.55v5.6c0 4.36-2.78 8.28-7 10.02-4.22-1.74-7-5.66-7-10.02v-5.6L12 2.7Z"
      />
    </svg>
  );
}

function PowerButtonIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="connection-button-icon"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M18.36 6.64a9 9 0 1 1-12.73 0" />
      <line x1="12" y1="2" x2="12" y2="12" />
    </svg>
  );
}

function ShieldAlertIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-10 w-10"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <line x1="12" y1="8" x2="12" y2="12" />
      <line x1="12" y1="16" x2="12.01" y2="16" />
    </svg>
  );
}

function GlobeIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18" />
      <path d="M12 3a13.5 13.5 0 0 1 0 18" />
      <path d="M12 3a13.5 13.5 0 0 0 0 18" />
    </svg>
  );
}

function ChevronDownIcon({ open }: { open: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={[
        "h-4 w-4 text-[var(--color-text-faint)] transition-transform",
        open ? "rotate-180" : "",
      ].join(" ")}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}

function ChevronLeftIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m15 6-6 6 6 6" />
    </svg>
  );
}

function ChevronRightIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m9 6 6 6-6 6" />
    </svg>
  );
}

function WidgetsChevron({ open }: { open: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={[
        "h-4 w-4 transition-transform",
        open ? "rotate-180" : "",
      ].join(" ")}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}

function RefreshIcon({ spinning }: { spinning: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={["h-4 w-4", spinning ? "animate-spin" : ""].join(" ")}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M20 6v5h-5" />
      <path d="M4 18v-5h5" />
      <path d="M19 11a7 7 0 0 0-12-4l-3 3" />
      <path d="M5 13a7 7 0 0 0 12 4l3-3" />
    </svg>
  );
}

function PingIcon({ spinning }: { spinning: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={["h-4 w-4", spinning ? "animate-pulse" : ""].join(" ")}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M4 20v-2" />
      <path d="M8 20v-5" />
      <path d="M12 20v-8" />
      <path d="M16 20v-11" />
      <path d="M20 20V5" />
    </svg>
  );
}

function UploadIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4 text-[var(--color-accent-bright)]"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 19V5" />
      <path d="m5 12 7-7 7 7" />
    </svg>
  );
}

function DownloadIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4 text-[var(--color-accent-bright)]"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 5v14" />
      <path d="m19 12-7 7-7-7" />
    </svg>
  );
}

function ChevronUpSmIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-3 w-3"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m18 15-6-6-6 6" />
    </svg>
  );
}

function ChevronDownSmIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-3 w-3"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}

function ListIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <line x1="8" y1="6" x2="21" y2="6" />
      <line x1="8" y1="12" x2="21" y2="12" />
      <line x1="8" y1="18" x2="21" y2="18" />
      <line x1="3" y1="6" x2="3.01" y2="6" />
      <line x1="3" y1="12" x2="3.01" y2="12" />
      <line x1="3" y1="18" x2="3.01" y2="18" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M18 6 6 18M6 6l12 12" />
    </svg>
  );
}
