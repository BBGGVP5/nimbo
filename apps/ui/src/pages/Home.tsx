import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
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

function useDismissable(open: boolean, onClose: () => void) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: MouseEvent) => {
      const node = ref.current;
      if (node && !node.contains(event.target as Node)) {
        onClose();
      }
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

export function Home() {
  const m = useMessages();
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

  const [refreshingUrl, setRefreshingUrl] = useState<string | null>(null);
  const [pinging, setPinging] = useState(false);
  const [pingingServerIds, setPingingServerIds] = useState<Set<string>>(() => new Set());
  const [adminDialogOpen, setAdminDialogOpen] = useState(false);
  const [connectedAt, setConnectedAt] = useState<number | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [sessionTraffic, setSessionTraffic] = useState({ upload: 0, download: 0 });

  const visibleSubs = useMemo(
    () => subs.filter(subscriptionVisibleOnHome),
    [subs],
  );

  const currentSub = useMemo(() => {
    if (activeSubscriptionUrl) {
      const match = visibleSubs.find((sub) => sub.url === activeSubscriptionUrl);
      if (match) return match;
    }
    return visibleSubs[0] ?? null;
  }, [visibleSubs, activeSubscriptionUrl]);

  const entries = useMemo<ServerEntry[]>(
    () => (currentSub ? currentSub.servers.map((server) => ({ server, sub: currentSub })) : []),
    [currentSub],
  );

  const activeEntry = activeId ? entries.find((item) => item.server.id === activeId) ?? null : null;
  const fallbackEntry = activeEntry ?? entries[0] ?? null;
  const selectedSubscription = currentSub;
  const connected = status?.state === "connected";
  const connecting = Boolean(connectingServerId);

  useEffect(() => {
    if (!connected) {
      setConnectedAt(null);
      setElapsedSeconds(0);
      setSessionTraffic({ upload: 0, download: 0 });
      return;
    }
    setConnectedAt((current) => current ?? Date.now());
  }, [connected]);

  useEffect(() => {
    if (!connected || connectedAt == null) return;
    const tick = () => {
      setElapsedSeconds(Math.max(0, Math.floor((Date.now() - connectedAt) / 1000)));
    };
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
        if (!cancelled) setSessionTraffic(traffic);
      } catch {
        if (!cancelled) setSessionTraffic((current) => current);
      }
    };
    void loadTraffic();
    const timer = window.setInterval(() => void loadTraffic(), 2000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [connected]);

  const onToggleServer = async (serverId: string) => {
    try {
      await setActive(activeId === serverId ? null : serverId);
    } catch (e) {
      const message = String(e);
      if (isAdminRestartError(message)) {
        setAdminDialogOpen(true);
      } else {
        notifyError(message);
      }
    }
  };

  const onRefreshSelected = async () => {
    if (!selectedSubscription) return;
    setRefreshingUrl(selectedSubscription.url);
    try {
      await refreshSubscription(selectedSubscription.url);
    } catch (e) {
      notifyError(String(e));
    } finally {
      setRefreshingUrl(null);
    }
  };

  const onPingServers = async () => {
    if (!entries.length) return;
    const serverIds = entries.map(({ server }) => server.id);
    setPinging(true);
    setPingingServerIds(new Set(serverIds));
    try {
      await pingServersProgressively(serverIds, (result) => {
        setPingingServerIds((current) => {
          const next = new Set(current);
          next.delete(result.server_id);
          return next;
        });
        if (result.latency_ms != null) {
          setServerPing(result.server_id, result.latency_ms);
        }
      });
    } finally {
      setPinging(false);
      setPingingServerIds(new Set());
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
      if (isAdminRestartError(message)) {
        setAdminDialogOpen(true);
      } else {
        notifyError(message);
      }
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

  return (
    <div className="home-grid h-full">
      <section className="home-center">
        <div className="home-top">
          <ProfileSummary
            sub={selectedSubscription}
            refreshing={refreshingUrl === selectedSubscription?.url}
            pinging={pinging}
            onRefresh={onRefreshSelected}
            onPing={onPingServers}
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
        </div>

        <div className="home-bottom">
          {connected && (
            <SessionTrafficBlocks
              upload={sessionTraffic.upload}
              download={sessionTraffic.download}
            />
          )}
        </div>
      </section>

      <aside className="home-right">
        <ServerSidePanel
          subs={visibleSubs}
          currentSub={currentSub}
          entries={entries}
          activeId={activeId}
          pingByServer={serverPings}
          pingingServerIds={pingingServerIds}
          onPickServer={onToggleServer}
          onSwitchSubscription={onSwitchSubscription}
          labels={m}
        />
      </aside>
      {adminDialogOpen && <AdminRestartDialog onClose={() => setAdminDialogOpen(false)} />}
    </div>
  );
}

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
        {busy ? <span className="connection-button-loader" /> : connected ? <ShieldButtonIcon /> : <PowerButtonIcon />}
      </span>
    </button>
  );
}

function AdminRestartDialog({ onClose }: { onClose: () => void }) {
  const m = useMessages();
  return (
    <div className="app-dialog-backdrop" role="presentation" onClick={onClose}>
      <div
        className="panel w-full max-w-md bg-[rgba(26,26,46,0.98)] p-8 text-center shadow-[0_32px_100px_rgba(0,0,0,0.6)]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-dialog-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mx-auto mb-6 grid h-20 w-20 place-items-center rounded-full bg-[var(--color-accent-active-bg)] text-[var(--color-accent-bright)]">
          <ShieldAlertIcon />
        </div>
        <h2 id="admin-dialog-title" className="mb-4 text-2xl font-black text-white">
          {m.home.adminTitle}
        </h2>
        <p className="mb-8 text-lg font-medium leading-relaxed text-[var(--color-text-dim)]">
          {m.home.adminText}
        </p>
        <div className="grid grid-cols-1 gap-3">
          <button
            onClick={onClose}
            className="primary-button interactive rounded-2xl py-4 text-lg font-bold"
          >
            {m.common.ok}
          </button>
        </div>
      </div>
    </div>
  );
}

function ProfileSummary({
  sub,
  refreshing,
  pinging,
  onRefresh,
  onPing,
  labels,
}: {
  sub: Subscription | null;
  refreshing: boolean;
  pinging: boolean;
  onRefresh: () => void;
  onPing: () => void;
  labels: Messages;
}) {
  if (!sub) return null;

  const used = (sub.info?.upload ?? 0) + (sub.info?.download ?? 0);
  const total = sub.info?.total ?? null;
  const expires = formatExpire(sub.info?.expire);
  const description = sub.meta?.description?.trim() || "";
  const visibleDescription = /^описание подписки$/i.test(description) ? "" : description;

  return (
    <div className="home-stack w-full max-w-[740px] space-y-2">
      <div className="panel px-4 py-2.5">
        <div className="grid grid-cols-[1fr_auto] items-start gap-2">
          <div className="min-w-0">
            <div className="truncate text-[15px] font-semibold text-white">{sub.name ?? labels.common.subscription}</div>
            <div className="mt-0.5 text-[12px] text-[var(--color-text-dim)]">{expires}</div>
          </div>
          <div className="flex gap-2">
            <SmallActionButton title={labels.home.pingServers} onClick={onPing}>
              <PingIcon spinning={pinging} />
            </SmallActionButton>
            <SmallActionButton title={labels.home.refreshSubscription} onClick={onRefresh}>
              <RefreshIcon spinning={refreshing} />
            </SmallActionButton>
          </div>
        </div>
        {visibleDescription && (
          <div
            className="profile-summary-description mt-1.5 text-[12px] leading-relaxed text-[var(--color-text-faint)]"
            title={visibleDescription}
          >
            {visibleDescription}
          </div>
        )}
        <div className="mt-2 h-1 rounded-full bg-[var(--color-glass-bg)]" />
        <div className="mt-2 text-[12px] font-semibold tabular-nums text-[var(--color-text-dim)]">
          {formatBytes(used)}{total ? ` / ${formatBytes(total)}` : " / ∞"}
        </div>
      </div>
    </div>
  );
}

function ServerSidePanel({
  subs,
  currentSub,
  entries,
  activeId,
  pingByServer,
  pingingServerIds,
  onPickServer,
  onSwitchSubscription,
  labels,
}: {
  subs: Subscription[];
  currentSub: Subscription | null;
  entries: ServerEntry[];
  activeId: string | null;
  pingByServer: Record<string, number>;
  pingingServerIds: ReadonlySet<string>;
  onPickServer: (serverId: string) => void;
  onSwitchSubscription: (url: string) => void;
  labels: Messages;
}) {
  const [switcherOpen, setSwitcherOpen] = useState(false);
  const switcherRef = useDismissable(switcherOpen, () => setSwitcherOpen(false));
  const showSwitcher = subs.length > 1;
  const currentName = currentSub?.name?.trim() || labels.common.subscription;

  if (!entries.length) {
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
    <div className="server-side-panel flex h-full flex-col">
      {showSwitcher && (
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
            <div className="panel mt-2 max-h-[260px] overflow-auto py-1">
              {subs.map((sub) => {
                const name = sub.name?.trim() || sub.url;
                const active = currentSub?.url === sub.url;
                return (
                  <button
                    key={sub.url}
                    onClick={() => {
                      setSwitcherOpen(false);
                      onSwitchSubscription(sub.url);
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

      <div className="server-side-list mt-2 flex-1 overflow-y-auto px-2 pb-3">
        {entries.map(({ server }) => {
          const label = serverDisplayName(server.name);
          const description = serverListDescription(
            server,
            entries.map(({ server }) => server),
          );
          const isActive = activeId === server.id;
          return (
            <button
              key={server.id}
              onClick={() => onPickServer(server.id)}
              className={[
                "server-side-item grid w-full grid-cols-[28px_1fr_auto] items-center gap-2.5 rounded-lg px-2.5 py-2 text-left transition-all hover:bg-[var(--color-glass-bg)]",
                isActive ? "bg-[var(--color-glass-bg-strong)]" : "",
              ].join(" ")}
            >
              <div className="grid h-7 w-7 place-items-center rounded-md bg-[var(--color-glass-bg)] text-[var(--color-text-faint)]">
                <CountryFlag serverName={server.name} fallback={<GlobeIcon />} className="country-flag-sm" />
              </div>
              <div className="min-w-0">
                <div className="truncate text-[13px] font-semibold text-white">{label}</div>
                {description && (
                  <div className="truncate text-[11px] text-[var(--color-text-faint)]">
                    {description}
                  </div>
                )}
              </div>
              <PingBadge ping={pingByServer[server.id]} loading={pingingServerIds.has(server.id)} />
            </button>
          );
        })}
      </div>
    </div>
  );
}

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
    return {
      bg: "rgba(76, 217, 100, 0.16)",
      fg: "#7be084",
      label: "Хороший пинг",
    };
  }
  if (ping < 300) {
    return {
      bg: "rgba(245, 192, 64, 0.16)",
      fg: "#f5c040",
      label: "Средний пинг",
    };
  }
  return {
    bg: "rgba(239, 83, 80, 0.18)",
    fg: "#ff8080",
    label: "Высокий пинг",
  };
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
    <div className="session-traffic mb-5 grid w-full max-w-[420px] grid-cols-2 gap-2">
      <div className="session-traffic-card">
        <div className="session-traffic-label">{m.home.upload}</div>
        <div className="session-traffic-value">{formatBytes(upload)}</div>
      </div>
      <div className="session-traffic-card">
        <div className="session-traffic-label">{m.home.downloaded}</div>
        <div className="session-traffic-value">{formatBytes(download)}</div>
      </div>
    </div>
  );
}

function formatDuration(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (value: number) => value.toString().padStart(2, "0");
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
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
    <svg viewBox="0 0 24 24" className="h-10 w-10" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <line x1="12" y1="8" x2="12" y2="12" />
      <line x1="12" y1="16" x2="12.01" y2="16" />
    </svg>
  );
}

function isAdminRestartError(message: string): boolean {
  const normalized = message.toLowerCase();
  return normalized.includes("tun") && normalized.includes("администратор");
}

function GlobeIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
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
      className={["h-4 w-4 text-[var(--color-text-faint)] transition-transform", open ? "rotate-180" : ""].join(" ")}
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
    <svg viewBox="0 0 24 24" className={["h-4 w-4", spinning ? "animate-spin" : ""].join(" ")} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 6v5h-5" />
      <path d="M4 18v-5h5" />
      <path d="M19 11a7 7 0 0 0-12-4l-3 3" />
      <path d="M5 13a7 7 0 0 0 12 4l3-3" />
    </svg>
  );
}

function PingIcon({ spinning }: { spinning: boolean }) {
  return (
    <svg viewBox="0 0 24 24" className={["h-4 w-4", spinning ? "animate-pulse" : ""].join(" ")} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 20v-2" />
      <path d="M8 20v-5" />
      <path d="M12 20v-8" />
      <path d="M16 20v-11" />
      <path d="M20 20V5" />
    </svg>
  );
}

