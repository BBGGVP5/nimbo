import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import { CountryFlag } from "../components/CountryFlag";
import { notifyError, notifyInfo } from "../lib/notify";
import { fillTemplate, useMessages, type Messages } from "../lib/i18n";
import { pingServersProgressively } from "../lib/ping";
import { useAppStore } from "../store";
import {
  api,
  formatBytes,
  formatExpire,
  protocolLabel,
  serverDisplayName,
  serverListDescription,
  transportLabel,
  type Server,
  type Subscription,
} from "../lib/api";

type ServerUiOverride = {
  name?: string;
  hidden?: boolean;
};

type ServerUiOverrides = Record<string, ServerUiOverride>;

const FAVORITES_KEY = "nimbo.favorites";
const SERVER_OVERRIDES_KEY = "nimbo.serverUiOverrides";

function readFavoriteServers(): Set<string> {
  try {
    const raw = localStorage.getItem(FAVORITES_KEY);
    return new Set(raw ? (JSON.parse(raw) as string[]) : []);
  } catch {
    return new Set();
  }
}

function writeFavoriteServers(value: Set<string>) {
  try {
    localStorage.setItem(FAVORITES_KEY, JSON.stringify([...value]));
  } catch {}
}

function useFavoriteServers() {
  const [favorites, setFavorites] = useState<Set<string>>(readFavoriteServers);

  const toggle = useCallback((id: string) => {
    setFavorites((previous) => {
      const next = new Set(previous);
      next.has(id) ? next.delete(id) : next.add(id);
      writeFavoriteServers(next);
      return next;
    });
  }, []);

  return { favorites, toggle };
}

function readServerUiOverrides(): ServerUiOverrides {
  try {
    const raw = localStorage.getItem(SERVER_OVERRIDES_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as ServerUiOverrides;
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function writeServerUiOverrides(value: ServerUiOverrides) {
  try {
    localStorage.setItem(SERVER_OVERRIDES_KEY, JSON.stringify(value));
  } catch {}
}

function useServerUiOverrides() {
  const [overrides, setOverrides] = useState<ServerUiOverrides>(readServerUiOverrides);

  const commit = useCallback((producer: (current: ServerUiOverrides) => ServerUiOverrides) => {
    setOverrides((current) => {
      const next = producer(current);
      writeServerUiOverrides(next);
      return next;
    });
  }, []);

  const renameServer = useCallback((serverId: string, name: string) => {
    const trimmed = name.trim();
    if (!trimmed) return;
    commit((current) => ({
      ...current,
      [serverId]: {
        ...(current[serverId] ?? {}),
        name: trimmed,
      },
    }));
  }, [commit]);

  const hideServer = useCallback((serverId: string) => {
    commit((current) => ({
      ...current,
      [serverId]: {
        ...(current[serverId] ?? {}),
        hidden: true,
      },
    }));
  }, [commit]);

  return { overrides, renameServer, hideServer };
}

function serverDisplayLabel(server: Server, overrides: ServerUiOverrides): string {
  const customName = overrides[server.id]?.name?.trim();
  return customName || serverDisplayName(server.name);
}

export function Subscriptions() {
  const m = useMessages();
  const subs = useAppStore((s) => s.subscriptions);
  const activeId = useAppStore((s) => s.activeServerId);
  const serverPings = useAppStore((s) => s.serverPings);
  const connectingServerId = useAppStore((s) => s.connectingServerId);
  const switchingServerId = useAppStore((s) => s.switchingServerId);
  const setActive = useAppStore((s) => s.setActiveServer);
  const refreshSubscription = useAppStore((s) => s.refreshSubscription);
  const updateSubscriptionSettings = useAppStore((s) => s.updateSubscriptionSettings);
  const removeSubscription = useAppStore((s) => s.removeSubscription);
  const importOpen = useAppStore((s) => s.importDialogOpen);
  const openImportDialog = useAppStore((s) => s.openImportDialog);
  const closeImportDialog = useAppStore((s) => s.closeImportDialog);
  const [query, setQuery] = useState("");
  const [showFavOnly, setShowFavOnly] = useState(false);
  const { favorites, toggle: toggleFavorite } = useFavoriteServers();
  const {
    overrides: serverOverrides,
    renameServer,
    hideServer,
  } = useServerUiOverrides();
  const [adminDialogOpen, setAdminDialogOpen] = useState(false);
  const serverCount = subs.reduce((sum, sub) => sum + sub.servers.length, 0);

  const filteredSubs = useMemo(() => {
    const q = query.trim().toLowerCase();
    let base = subs
      .map((sub) => ({
        ...sub,
        servers: sub.servers.filter((server) => !serverOverrides[server.id]?.hidden),
      }))
      .filter((sub) => sub.servers.length > 0 || !q);
    if (showFavOnly) {
      base = base
        .map((sub) => ({ ...sub, servers: sub.servers.filter((s) => favorites.has(s.id)) }))
        .filter((sub) => sub.servers.length > 0);
    }
    if (!q) return base;
    return base
      .map((sub) => ({
        ...sub,
        servers: sub.servers.filter(
          (server) => {
            const description = serverListDescription(server, sub.servers);
            const displayName = serverDisplayLabel(server, serverOverrides);
            return (
              displayName.toLowerCase().includes(q) ||
              server.name.toLowerCase().includes(q) ||
              (description?.toLowerCase().includes(q) ?? false)
            );
          },
        ),
      }))
      .filter(
        (sub) =>
          sub.servers.length > 0 ||
          (sub.name ?? "").toLowerCase().includes(q) ||
          sub.url.toLowerCase().includes(q),
      );
  }, [favorites, query, serverOverrides, showFavOnly, subs]);

  const onSelect = async (server: Server) => {
    try {
      await setActive(activeId === server.id ? null : server.id);
    } catch (e) {
      const message = String(e);
      if (isAdminRestartError(message)) {
        setAdminDialogOpen(true);
      } else {
        notifyError(message);
      }
    }
  };

  return (
    <div className="page-view page-view-wide">
      <div className="mb-7 flex items-start justify-between gap-4 mobile-column">
        <div>
          <h1 className="page-title">{m.profiles.title}</h1>
          <p className="page-subtitle">
            {serverCount} {m.common.servers} · {subs.length} {m.common.subscriptions}
          </p>
        </div>
        <div className="flex gap-3">
          <IconButton
            title={showFavOnly ? "Все профили" : m.profiles.favorite}
            icon={<StarIcon filled={showFavOnly} />}
            onClick={() => setShowFavOnly((v) => !v)}
            active={showFavOnly}
          />
          <IconButton
            title={m.profiles.add}
            icon={<PlusIcon />}
            accent
            onClick={() => openImportDialog()}
          />
        </div>
      </div>

      <div className="relative mb-8">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={m.profiles.searchServers}
          className="dark-input px-5 py-4 pr-12 text-lg"
        />
        {query && (
          <button
            type="button"
            aria-label={m.tunnelLogs.clear}
            title={m.tunnelLogs.clear}
            onClick={() => setQuery("")}
            className="absolute right-3 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-lg text-[var(--color-text-faint)] transition-colors hover:bg-[var(--color-glass-bg)] hover:text-white"
          >
            <XIcon />
          </button>
        )}
      </div>

      {subs.length === 0 ? (
        <EmptyProfiles onAdd={() => openImportDialog()} />
      ) : (
        <div className="space-y-4">
          {filteredSubs.map((sub) => (
            <ProfileCard
              key={sub.url}
              sub={sub}
              activeId={activeId}
              serverPings={serverPings}
              connectingId={connectingServerId || switchingServerId}
              favorites={favorites}
              serverOverrides={serverOverrides}
              onSelect={onSelect}
              onToggleFavorite={toggleFavorite}
              onRenameServer={renameServer}
              onHideServer={hideServer}
              onRefresh={() => refreshSubscription(sub.url)}
              onUpdate={(settings) => updateSubscriptionSettings(sub.url, settings)}
              onRemove={() => removeSubscription(sub.url)}
            />
          ))}
        </div>
      )}

      {importOpen && <ImportDialog onClose={closeImportDialog} />}
      {adminDialogOpen && <AdminRestartDialog onClose={() => setAdminDialogOpen(false)} />}
    </div>
  );
}

function EmptyProfiles({ onAdd }: { onAdd: () => void }) {
  const m = useMessages();
  return (
    <div className="flex min-h-[52vh] items-center justify-center">
      <div className="text-center">
        <div className="mx-auto mb-6 grid h-20 w-20 place-items-center rounded-full bg-[var(--color-glass-bg)] text-5xl text-[var(--color-text-faint)]">
          <GlobeIcon className="h-12 w-12" />
        </div>
        <div className="mb-3 text-2xl font-semibold text-[var(--color-text-dim)]">
          {m.profiles.emptyTitle}
        </div>
        <div className="mb-7 text-lg text-[var(--color-text-faint)]">
          {m.profiles.emptyDescription}
        </div>
        <button
          onClick={onAdd}
          className="interactive rounded-2xl border border-[var(--color-accent)] bg-[var(--color-glass-bg)] px-8 py-4 text-lg font-semibold text-[var(--color-accent-bright)]"
        >
          + {m.profiles.add}
        </button>
      </div>
    </div>
  );
}

function ProfileCard({
  sub,
  activeId,
  serverPings,
  connectingId,
  favorites,
  serverOverrides,
  onSelect,
  onToggleFavorite,
  onRenameServer,
  onHideServer,
  onRefresh,
  onUpdate,
  onRemove,
}: {
  sub: Subscription;
  activeId: string | null;
  serverPings: Record<string, number>;
  connectingId: string | null;
  favorites: ReadonlySet<string>;
  serverOverrides: ServerUiOverrides;
  onSelect: (server: Server) => void;
  onToggleFavorite: (serverId: string) => void;
  onRenameServer: (serverId: string, name: string) => void;
  onHideServer: (serverId: string) => void;
  onRefresh: () => Promise<unknown>;
  onUpdate: (settings: {
    name?: string | null;
    show_on_home?: boolean | null;
    update_interval_minutes?: number | null;
  }) => Promise<unknown>;
  onRemove: () => Promise<unknown>;
}) {
  const m = useMessages();
  const used = (sub.info?.upload ?? 0) + (sub.info?.download ?? 0);
  const total = sub.info?.total ?? null;
  const expires = formatExpire(sub.info?.expire);
  const supportUrl = sub.meta?.support_url?.trim() || "https://t.me/nebulaguard_channel";
  const siteUrl = sub.meta?.website_url?.trim() || subscriptionSiteUrl(sub.url);
  const description = sub.meta?.description?.trim() || "";
  const visibleDescription = /^описание подписки$/i.test(description) ? "" : description;
  const showOnHome = sub.meta?.show_on_home !== false;
  const updateInterval = sub.meta?.update_interval_minutes ?? 720;
  const updatedAt = formatFetchedAt(sub.fetched_at, m);
  const [refreshing, setRefreshing] = useState(false);
  const [removing, setRemoving] = useState(false);
  const [pinging, setPinging] = useState(false);
  const [pingingServerIds, setPingingServerIds] = useState<Set<string>>(() => new Set());
  const [expanded, setExpanded] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [confirmRemoveOpen, setConfirmRemoveOpen] = useState(false);
  const setServerPing = useAppStore((s) => s.setServerPing);

  const trafficValue = total ? `${formatBytes(used)} / ${formatBytes(total)}` : `${formatBytes(used)} / ∞`;
  const toggleExpanded = () => setExpanded((value) => !value);
  const onSummaryClick = (event: React.MouseEvent<HTMLDivElement>) => {
    const target = event.target as HTMLElement;
    if (target.closest("a,button,input,textarea,select,[data-no-toggle]")) return;
    toggleExpanded();
  };

  const onRefreshClick = async () => {
    setRefreshing(true);
    try {
      await onRefresh();
    } catch (e) {
      notifyError(String(e));
    } finally {
      setRefreshing(false);
    }
  };

  const onRemoveClick = async () => {
    setConfirmRemoveOpen(true);
  };

  const confirmRemove = async () => {
    setRemoving(true);
    try {
      await onRemove();
      notifyInfo(m.profiles.deleted);
      setConfirmRemoveOpen(false);
    } catch (e) {
      notifyError(String(e));
    } finally {
      setRemoving(false);
    }
  };

  const onPingClick = async () => {
    const serverIds = sub.servers.map((server) => server.id);
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

  const onPingServerClick = async (serverId: string) => {
    setPingingServerIds((current) => {
      const next = new Set(current);
      next.add(serverId);
      return next;
    });
    try {
      const result = await api.pingServer(serverId);
      if (result.latency_ms != null) {
        setServerPing(result.server_id, result.latency_ms);
      }
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

  return (
    <section className="panel relative">
      <div
        className="cursor-pointer p-4"
        onClick={onSummaryClick}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          const target = e.target as HTMLElement;
          if (target.closest("a,button,input,textarea,select,[data-no-toggle]")) return;
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            toggleExpanded();
          }
        }}
      >
        <div
          className="mb-4 grid grid-cols-[24px_minmax(0,1fr)_auto] items-start gap-3 rounded-xl text-left"
        >
          <div className="pt-1 text-[var(--color-text-faint)]">
            <ChevronIcon open={expanded} />
          </div>
          <div className="min-w-0">
            <div className="flex min-w-0 items-center gap-3">
              <div className="truncate text-lg font-semibold text-white">{sub.name ?? m.common.subscription}</div>
              <span className="shrink-0 rounded-full bg-[rgba(255,255,255,0.08)] px-2.5 py-1 text-xs font-bold text-[var(--color-text-dim)]">
                {sub.servers.length}
              </span>
            </div>
            <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[var(--color-text-faint)]">
              <span>{intervalLabel(updateInterval, m)}</span>
              <span>{m.profiles.updated}: {updatedAt}</span>
              {!showOnHome && <span>{m.profiles.hiddenFromHome}</span>}
            </div>
          </div>
          <div className="flex items-center gap-1.5" data-no-toggle>
            <IconButton compact title={m.home.pingServers} icon={<SignalIcon pulse={pinging} />} onClick={onPingClick} />
            <IconButton compact title={m.home.refreshSubscription} icon={<RefreshIcon spin={refreshing} />} onClick={onRefreshClick} />
            <div className="relative">
              <IconButton
                compact
                title={m.profiles.subscriptionMenu}
                icon={<DotsIcon />}
                onClick={() => setMenuOpen((value) => !value)}
                disabled={removing}
              />
              {menuOpen && (
                <div className="absolute right-0 top-11 z-20 w-52 overflow-hidden rounded-xl border border-[var(--color-border)] bg-[#221f2b] shadow-[0_18px_42px_rgba(0,0,0,0.36)]">
                  <button
                    onClick={() => {
                      setMenuOpen(false);
                      setSettingsOpen(true);
                    }}
                    className="flex w-full items-center gap-3 px-4 py-3 text-left text-sm font-semibold text-white hover:bg-[var(--color-glass-bg)]"
                  >
                    <SettingsIcon />
                    {m.profiles.subscriptionSettings}
                  </button>
                  <button
                    onClick={() => {
                      setMenuOpen(false);
                      void onRemoveClick();
                    }}
                    className="flex w-full items-center gap-3 border-t border-[var(--color-border)] px-4 py-3 text-left text-sm font-semibold text-[var(--color-status-error)] hover:bg-[rgba(244,67,54,0.10)]"
                  >
                    <TrashIcon pulse={removing} />
                    {m.profiles.delete}
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="mobile-stack mb-3 grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,0.72fr)] gap-2 text-xs">
          <MiniStat label={m.profiles.traffic} value={trafficValue} />
          <MiniStat label={m.profiles.expires} value={expires} />
          <MiniStat label={m.profiles.updated} value={updatedAt} />
        </div>

        <div className="mb-3 rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-accent-panel)] px-3 py-3">
          <div className="mb-1 text-[9px] uppercase tracking-wider text-[var(--color-text-faint)]">
            {m.common.description}
          </div>
          <div className="whitespace-pre-wrap text-sm leading-relaxed text-[var(--color-text-dim)]">
            {visibleDescription || m.common.noDescription}
          </div>
        </div>

        <div className="mobile-wrap flex gap-2">
          <a
            href={supportUrl}
            target="_blank"
            rel="noreferrer"
            data-no-toggle
            className="interactive inline-flex items-center gap-2 rounded-xl border border-[var(--color-accent)] bg-[var(--color-accent-panel)] px-3 py-2 text-sm font-semibold text-[var(--color-accent-bright)]"
          >
            <SupportIcon />
            {m.common.support}
          </a>
          {siteUrl && (
            <a
              href={siteUrl}
              target="_blank"
              rel="noreferrer"
              data-no-toggle
              className="interactive inline-flex items-center gap-2 rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-accent-panel)] px-3 py-2 text-sm font-semibold text-[var(--color-accent-bright)]"
            >
              <GlobeIcon className="h-4 w-4" />
              {m.common.site}
            </a>
          )}
        </div>
      </div>

      {expanded && (
        <div className="divide-y divide-[var(--color-border)] border-t border-[var(--color-border)] bg-[rgba(255,255,255,0.018)]">
          {deduplicateById(sub.servers).map((server) => (
            <ServerLine
              key={server.id}
              server={server}
              servers={sub.servers}
              active={activeId === server.id}
              connecting={connectingId === server.id}
              ping={serverPings[server.id]}
              pinging={pingingServerIds.has(server.id)}
              displayName={serverDisplayLabel(server, serverOverrides)}
              favorite={favorites.has(server.id)}
              onSelect={() => onSelect(server)}
              onPing={() => onPingServerClick(server.id)}
              onToggleFavorite={() => onToggleFavorite(server.id)}
              onRename={(name) => onRenameServer(server.id, name)}
              onHide={() => onHideServer(server.id)}
            />
          ))}
        </div>
      )}

      {settingsOpen && (
        <SubscriptionSettingsDialog
          sub={sub}
          showOnHome={showOnHome}
          updateInterval={updateInterval}
          supportUrl={supportUrl}
          siteUrl={siteUrl}
          description={visibleDescription}
          sourceUrl={sub.url}
          onDelete={() => {
            setSettingsOpen(false);
            setConfirmRemoveOpen(true);
          }}
          onSave={onUpdate}
          onClose={() => setSettingsOpen(false)}
        />
      )}

      {confirmRemoveOpen && (
        <ConfirmDialog
          title={m.profiles.deleteSubscriptionTitle}
          description={fillTemplate(m.profiles.deleteSubscriptionDescription, { name: sub.name ?? m.profiles.thisSubscription })}
          confirmLabel={m.profiles.delete}
          danger
          busy={removing}
          onConfirm={confirmRemove}
          onClose={() => setConfirmRemoveOpen(false)}
        />
      )}
    </section>
  );
}

function ServerLine({
  server,
  servers,
  active,
  connecting,
  ping,
  pinging,
  displayName,
  favorite,
  onSelect,
  onPing,
  onToggleFavorite,
  onRename,
  onHide,
}: {
  server: Server;
  servers: Server[];
  active: boolean;
  connecting: boolean;
  ping?: number;
  pinging: boolean;
  displayName: string;
  favorite: boolean;
  onSelect: () => void;
  onPing: () => void;
  onToggleFavorite: () => void;
  onRename: (name: string) => void;
  onHide: () => void;
}) {
  const m = useMessages();
  const label = displayName;
  const [menuOpen, setMenuOpen] = useState(false);
  const [renameOpen, setRenameOpen] = useState(false);
  const [confirmHideOpen, setConfirmHideOpen] = useState(false);
  void servers;

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={(event) => {
        if (event.currentTarget !== event.target) return;
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onSelect();
        }
      }}
      className={[
        "server-profile-row group",
        active ? "server-profile-row-active" : "",
        connecting ? "server-profile-row-connecting" : "",
      ].join(" ")}
    >
      <div className="server-profile-logo">
        <CountryFlag serverName={server.name} fallback={<GlobeIcon className="h-5 w-5" />} className="country-flag-server" />
        {favorite && (
          <span className="server-favorite-mark" aria-hidden="true">
            <HeartIcon filled />
          </span>
        )}
      </div>
      <div className="server-profile-main">
        <div className="server-profile-title-line">
          <div className="server-profile-title">{label}</div>
          {connecting && (
            <span className="server-row-pill server-row-pill-selected">
              {m.common.connecting}
            </span>
          )}
        </div>
        <div className="server-profile-meta">
          <span className="server-row-pill server-row-pill-proto">{protocolLabel(server.protocol)}</span>
          <span className="server-row-pill server-row-pill-transport">{networkBadge(server.protocol)}</span>
        </div>
      </div>
      <div className="server-profile-actions" data-no-toggle>
        <PingBadge ping={ping} loading={pinging} />
        <button
          type="button"
          title={favorite ? m.profiles.unfavoriteServer : m.profiles.favoriteServer}
          aria-label={favorite ? m.profiles.unfavoriteServer : m.profiles.favoriteServer}
          aria-pressed={favorite}
          onClick={(event) => {
            event.stopPropagation();
            onToggleFavorite();
          }}
          className={[
            "server-row-icon-button",
            favorite ? "server-row-icon-button-active" : "",
          ].join(" ")}
        >
          <HeartIcon filled={favorite} />
        </button>
        <div className="server-row-menu-wrap">
          <button
            type="button"
            title={m.profiles.serverMenu}
            aria-label={m.profiles.serverMenu}
            aria-expanded={menuOpen}
            onClick={(event) => {
              event.stopPropagation();
              setMenuOpen((value) => !value);
            }}
            className="server-row-icon-button server-row-dots-button"
          >
            <DotsIcon />
          </button>
          {menuOpen && (
            <div
              className="server-row-menu"
              onClick={(event) => event.stopPropagation()}
            >
              <button
                type="button"
                onClick={() => {
                  setMenuOpen(false);
                  void onPing();
                }}
              >
                <SignalIcon pulse={pinging} small />
                {m.profiles.testLatency}
              </button>
              <button
                type="button"
                onClick={() => {
                  setMenuOpen(false);
                  setRenameOpen(true);
                }}
              >
                <EditIcon />
                {m.profiles.renameServer}
              </button>
              <button
                type="button"
                className="server-row-menu-danger"
                onClick={() => {
                  setMenuOpen(false);
                  setConfirmHideOpen(true);
                }}
              >
                <TrashIcon />
                {m.profiles.deleteServer}
              </button>
            </div>
          )}
        </div>
      </div>

      {renameOpen && (
        <RenameServerDialog
          initialName={label}
          onSave={(name) => {
            onRename(name);
            notifyInfo(m.profiles.serverRenamed);
            setRenameOpen(false);
          }}
          onClose={() => setRenameOpen(false)}
        />
      )}

      {confirmHideOpen && (
        <ConfirmDialog
          title={m.profiles.deleteServerTitle}
          description={fillTemplate(m.profiles.deleteServerDescription, { name: label })}
          confirmLabel={m.profiles.delete}
          danger
          onConfirm={() => {
            onHide();
            notifyInfo(m.profiles.serverHidden);
            setConfirmHideOpen(false);
          }}
          onClose={() => setConfirmHideOpen(false)}
        />
      )}
    </div>
  );
}

function PingBadge({ ping, loading = false }: { ping?: number; loading?: boolean }) {
  if (loading) {
    return (
      <span className="server-ping-badge server-ping-badge-loading">
        ...
      </span>
    );
  }
  if (ping == null) return null;
  return (
    <span className="server-ping-badge">
      {ping} ms
    </span>
  );
}

function RenameServerDialog({
  initialName,
  onSave,
  onClose,
}: {
  initialName: string;
  onSave: (name: string) => void;
  onClose: () => void;
}) {
  const m = useMessages();
  const [name, setName] = useState(initialName);
  const canSave = name.trim().length > 0;

  return (
    <ModalPortal>
      <div className="app-dialog-backdrop" role="presentation" onClick={onClose}>
        <div
          className="panel server-rename-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="rename-server-title"
          onClick={(event) => event.stopPropagation()}
        >
          <div id="rename-server-title" className="mb-2 text-xl font-bold text-white">
            {m.profiles.renameServer}
          </div>
          <div className="mb-4 text-sm text-[var(--color-text-faint)]">
            {m.profiles.renameServerDescription}
          </div>
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            className="dark-input px-4 py-3 text-base"
            autoFocus
          />
          <div className="mt-5 grid grid-cols-2 gap-3">
            <button
              type="button"
              onClick={onClose}
              className="interactive rounded-xl border border-[var(--color-border)] px-4 py-3 text-sm font-semibold text-[var(--color-text-dim)]"
            >
              {m.common.cancel}
            </button>
            <button
              type="button"
              disabled={!canSave}
              onClick={() => canSave && onSave(name)}
              className="primary-button interactive rounded-xl px-4 py-3 text-sm font-bold disabled:opacity-50"
            >
              {m.common.save}
            </button>
          </div>
        </div>
      </div>
    </ModalPortal>
  );
}

function SubscriptionSettingsDialog({
  sub,
  showOnHome,
  updateInterval,
  supportUrl,
  siteUrl,
  description,
  sourceUrl,
  onDelete,
  onSave,
  onClose,
}: {
  sub: Subscription;
  showOnHome: boolean;
  updateInterval: number;
  supportUrl: string;
  siteUrl: string | null;
  description: string;
  sourceUrl: string;
  onDelete: () => void;
  onSave: (settings: {
    name?: string | null;
    show_on_home?: boolean | null;
    update_interval_minutes?: number | null;
  }) => Promise<unknown>;
  onClose: () => void;
}) {
  const m = useMessages();
  const [name, setName] = useState(sub.name ?? "");
  const [visible, setVisible] = useState(showOnHome);
  const [interval, setInterval] = useState(updateInterval);
  const [saving, setSaving] = useState(false);
  const [copiedUrl, setCopiedUrl] = useState(false);

  const save = async () => {
    setSaving(true);
    try {
      await onSave({
        name: name.trim(),
        show_on_home: visible,
        update_interval_minutes: interval,
      });
      notifyInfo(m.profiles.settingsSaved);
      onClose();
    } catch (e) {
      notifyError(String(e));
    } finally {
      setSaving(false);
    }
  };

  const copyUrl = async () => {
    try {
      await api.writeClipboardText(sourceUrl);
      setCopiedUrl(true);
      notifyInfo(m.common.copied);
      window.setTimeout(() => setCopiedUrl(false), 1200);
    } catch {
      try {
        if (!navigator.clipboard?.writeText) throw new Error(m.common.copy);
        await navigator.clipboard.writeText(sourceUrl);
        setCopiedUrl(true);
        notifyInfo(m.common.copied);
        window.setTimeout(() => setCopiedUrl(false), 1200);
      } catch (error) {
        notifyError(String(error));
      }
    }
  };

  return (
    <ModalPortal>
      <div
        className="subscription-settings-backdrop"
        onClick={onClose}
      >
        <div
          className="subscription-settings-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="subscription-settings-title"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="subscription-settings-header">
            <h2 id="subscription-settings-title">{m.profiles.subscriptionSettings}</h2>
            <button
              type="button"
              onClick={onClose}
              className="subscription-settings-close"
              title={m.common.close}
              aria-label={m.common.close}
            >
              <XIcon />
            </button>
          </div>

          <div className="subscription-settings-body">
            <label className="subscription-settings-field">
              <span>{m.profiles.displayName}</span>
              <div className="subscription-settings-input-wrap">
                <ShieldIcon />
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="subscription-settings-input"
                  placeholder={m.profiles.subscriptionNamePlaceholder}
                />
              </div>
              <small>{m.profiles.displayNameHint}</small>
            </label>

            <div className="subscription-settings-toggle-row">
              <div>
                <div className="subscription-settings-row-title">{m.profiles.showOnHome}</div>
                <div className="subscription-settings-row-subtitle">{m.profiles.showOnHomeDescription}</div>
              </div>
              <button
                type="button"
                onClick={() => setVisible((value) => !value)}
                className={["settings-toggle subscription-settings-switch", visible ? "settings-toggle-on" : ""].join(" ")}
                title={m.profiles.showOnHome}
                aria-pressed={visible}
              >
                <span />
              </button>
            </div>

            <div className="subscription-settings-toggle-row">
              <div>
                <div className="subscription-settings-row-title">{m.profiles.customUpdateInterval}</div>
                <div className="subscription-settings-row-subtitle">{m.profiles.customUpdateIntervalDescription}</div>
              </div>
              <button
                type="button"
                className="settings-toggle subscription-settings-switch settings-toggle-on"
                aria-pressed="true"
                title={m.profiles.customUpdateInterval}
              >
                <span />
              </button>
            </div>

            <div className="subscription-settings-interval-grid">
              {[30, 60, 120, 360, 720, 1440].map((value) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setInterval(value)}
                  className={interval === value ? "subscription-settings-interval-active" : ""}
                >
                  {intervalLabel(value, m)}
                </button>
              ))}
            </div>

            <div className="subscription-settings-url">
              <div className="subscription-settings-label">{m.profiles.subscriptionUrl}</div>
              <div className="subscription-settings-url-row">
                <div className="subscription-settings-copy-field">{sourceUrl}</div>
                <button
                  type="button"
                  onClick={() => void copyUrl()}
                  className="subscription-settings-copy-button"
                  title={m.common.copy}
                  aria-label={m.common.copy}
                >
                  {copiedUrl ? <CheckIcon /> : <ClipboardIcon />}
                </button>
              </div>
            </div>

            <section className="subscription-settings-provider-links">
              <div className="subscription-settings-label">{m.profiles.providerLinks}</div>
              <div className="subscription-settings-provider-grid">
                <a href={supportUrl} target="_blank" rel="noreferrer">
                  <SupportIcon />
                  {m.common.support}
                </a>
                {siteUrl && (
                  <a href={siteUrl} target="_blank" rel="noreferrer">
                    <GlobeIcon className="h-5 w-5" />
                    {m.common.site}
                  </a>
                )}
              </div>
            </section>

            {description && (
              <section className="subscription-settings-announcement">
                <div className="subscription-settings-label">{m.profiles.providerAnnouncement}</div>
                <div>{description}</div>
              </section>
            )}
          </div>

          <div className="subscription-settings-footer">
            <button
              type="button"
              onClick={onDelete}
              className="subscription-settings-delete"
            >
              {m.profiles.delete}
            </button>
            <div className="subscription-settings-footer-actions">
              <button
                type="button"
                onClick={onClose}
                className="subscription-settings-cancel"
              >
                {m.common.cancel}
              </button>
              <button
                type="button"
                onClick={save}
                disabled={saving}
                className="subscription-settings-save"
              >
                {saving ? m.common.saving : m.common.save}
              </button>
            </div>
          </div>
        </div>
      </div>
    </ModalPortal>
  );
}

function networkBadge(protocol: Server["protocol"]): string {
  if (protocol.kind === "shadowsocks") return "SHADOWSOCKS";
  const value = transportLabel(protocol).replace(" · ", " • ").trim();
  return value ? value.toUpperCase() : "JSON";
}

function formatFetchedAt(value: number, m: Messages): string {
  if (!value) return m.common.never;
  const millis = value > 10_000_000_000 ? value : value * 1000;
  return new Intl.DateTimeFormat(m.common.locale, {
    day: "2-digit",
    month: "2-digit",
    year: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(millis));
}

function intervalLabel(minutes: number, m: Messages): string {
  if (minutes < 60) return `${minutes} ${m.profiles.minutesShort}`;
  const hours = minutes / 60;
  if (m.common.locale === "en-US") return `${hours} h`;
  return `${hours} ${pluralRu(hours, "час", "часа", "часов")}`;
}

function pluralRu(value: number, one: string, few: string, many: string): string {
  const mod10 = value % 10;
  const mod100 = value % 100;
  if (mod10 === 1 && mod100 !== 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
  return many;
}

function ConfirmDialog({
  title,
  description,
  confirmLabel,
  danger = false,
  busy = false,
  onConfirm,
  onClose,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const m = useMessages();
  return (
    <ModalPortal>
      <div
        className="fixed inset-0 z-50 grid place-items-center p-5"
        style={{ background: "rgba(0,0,0,0.58)", backdropFilter: "blur(9px)" }}
        onClick={onClose}
      >
        <div
          className="panel w-full max-w-md bg-[rgba(26,26,46,0.96)] p-5 shadow-[0_28px_90px_rgba(0,0,0,0.46)]"
          onClick={(e) => e.stopPropagation()}
        >
        <div className="mb-2 text-xl font-bold text-white">{title}</div>
        <div className="mb-5 text-sm leading-relaxed text-[var(--color-text-dim)]">{description}</div>
        <div className="grid grid-cols-2 gap-3">
          <button
            onClick={onClose}
            disabled={busy}
            className="interactive rounded-xl border border-[var(--color-border)] px-4 py-3 text-sm font-semibold text-[var(--color-text-dim)] disabled:opacity-50"
          >
            {m.common.cancel}
          </button>
          <button
            onClick={onConfirm}
            disabled={busy}
            className={[
              "interactive rounded-xl px-4 py-3 text-sm font-bold disabled:opacity-50",
              danger ? "bg-[var(--color-status-error)] text-white" : "primary-button",
            ].join(" ")}
          >
            {busy ? m.profiles.wait : confirmLabel}
          </button>
        </div>
      </div>
      </div>
    </ModalPortal>
  );
}

function ModalPortal({ children }: { children: ReactNode }) {
  return createPortal(children, document.body);
}

function MiniStat({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div
      className={[
        "rounded-xl px-3 py-2",
        accent
          ? "border border-[rgba(245,166,35,0.26)] bg-[rgba(245,166,35,0.09)]"
          : "bg-[var(--color-glass-bg)]",
      ].join(" ")}
    >
      <div className="mb-1 text-[9px] uppercase tracking-wider text-[var(--color-text-faint)]">
        {label}
      </div>
      <div className="truncate text-xs text-[var(--color-text-dim)]">{value}</div>
    </div>
  );
}

function ImportDialog({ onClose }: { onClose: () => void }) {
  const m = useMessages();
  const add = useAppStore((s) => s.addSubscription);
  const source = useAppStore((s) => s.importDialogSource);
  const setSource = useAppStore((s) => s.setImportDialogSource);
  const fileInput = useRef<HTMLInputElement | null>(null);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [importedCount, setImportedCount] = useState<number | null>(null);

  useEffect(() => {
    setErr(null);
    setImportedCount(null);
    setName("");
  }, [source]);

  const importSource = async (value = source) => {
    const cleaned = value.trim();
    if (!cleaned) {
      setErr(m.profiles.pasteError);
      return;
    }
    setBusy(true);
    setErr(null);
    setImportedCount(null);
    try {
      const imported = await add(cleaned, name.trim() || undefined);
      setImportedCount(imported.servers.length);
    } catch (e) {
      setErr(String(e));
    } finally {
      setBusy(false);
    }
  };

  const pasteFromClipboard = async () => {
    try {
      const text = await api.readClipboardText();
      setSource(text);
      await importSource(text);
    } catch (e) {
      const message = String(e);
      setErr(message);
      notifyError(message);
    }
  };

  const importFile = async (file: File | undefined) => {
    if (!file) return;
    try {
      const text = await file.text();
      setSource(text);
      await importSource(text);
    } catch {
      setErr(m.profiles.fileReadError);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center p-5"
      style={{ background: "rgba(0,0,0,0.55)", backdropFilter: "blur(8px)" }}
      onClick={onClose}
    >
      <div
        className="panel w-full max-w-2xl p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-xl font-semibold text-white">{m.profiles.importTitle}</h2>
            <p className="text-sm text-[var(--color-text-faint)]">
              {m.profiles.importSubtitle}
            </p>
          </div>
          <button
            onClick={onClose}
            className="rounded-lg bg-[var(--color-glass-bg)] px-3 py-2 text-sm text-[var(--color-text-dim)]"
          >
            {m.common.close}
          </button>
        </div>

        <div className="grid grid-cols-[1fr_150px] gap-3 mobile-stack">
          <input
            value={source}
            onChange={(e) => setSource(e.target.value)}
            placeholder={m.profiles.sourcePlaceholder}
            className="dark-input px-4 py-3 text-sm font-mono"
          />
          <button
            onClick={() => importSource()}
            disabled={busy || !source.trim()}
            className="primary-button interactive rounded-xl px-4 py-3 text-sm disabled:opacity-40"
          >
            {busy ? m.common.importing : m.common.import}
          </button>
        </div>

        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={m.profiles.namePlaceholder}
          className="dark-input mt-3 px-4 py-3 text-sm"
        />

        <div className="mt-4 grid grid-cols-3 gap-3 mobile-stack">
          <SmallImportButton onClick={pasteFromClipboard} label={m.profiles.fromClipboard} icon={<ClipboardIcon />} />
          <SmallImportButton onClick={() => fileInput.current?.click()} label={m.profiles.fromFile} icon={<FolderIcon />} />
          <SmallImportButton onClick={() => setErr(m.profiles.qrLater)} label="QR" icon={<QrIcon />} />
        </div>

        <input
          ref={fileInput}
          type="file"
          accept=".json,.txt,.conf,.yaml,.yml"
          className="hidden"
          onChange={(e) => void importFile(e.target.files?.[0])}
        />

        {err && (
          <div className="mt-4 rounded-xl border border-[rgba(244,67,54,0.35)] bg-[rgba(244,67,54,0.12)] px-4 py-3 text-sm text-[var(--color-status-error)]">
            {err}
          </div>
        )}

        {importedCount !== null && !err && (
          <div className="mt-4 rounded-xl border border-[var(--color-accent)] bg-[var(--color-accent-active-bg)] px-4 py-3 text-base font-semibold text-[var(--color-accent-bright)]">
            {fillTemplate(m.profiles.importedServers, { count: importedCount })}
          </div>
        )}
      </div>
    </div>
  );
}

function subscriptionSiteUrl(source: string): string | null {
  try {
    const u = new URL(source);
    return `${u.protocol}//${u.host}`;
  } catch {
    return null;
  }
}

function SmallImportButton({
  label,
  icon,
  onClick,
}: {
  label: string;
  icon: ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className="interactive flex items-center justify-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-glass-bg)] px-4 py-3 text-sm font-semibold text-[var(--color-text-dim)] hover:text-white"
    >
      <span className="h-4 w-4 text-[var(--color-accent-bright)]">{icon}</span>
      {label}
    </button>
  );
}

function IconButton({
  icon,
  title,
  onClick,
  accent = false,
  active = false,
  disabled = false,
  compact = false,
}: {
  icon: ReactNode;
  title: string;
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
  accent?: boolean;
  active?: boolean;
  disabled?: boolean;
  compact?: boolean;
}) {
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      disabled={disabled}
      className={[
        "interactive grid place-items-center rounded-xl border bg-[var(--color-glass-bg)]",
        compact ? "h-9 w-9" : "h-12 w-12",
        active
          ? "border-[var(--color-accent)] bg-[var(--color-accent-active-bg)] text-[var(--color-accent-bright)]"
          : "border-[var(--color-border)] text-[var(--color-text-dim)]",
        accent ? "text-[var(--color-accent-bright)]" : "",
        disabled ? "cursor-not-allowed opacity-50" : "",
      ].join(" ")}
    >
      {icon}
    </button>
  );
}

function ChevronIcon({ open }: { open: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={["h-5 w-5 transition-transform", open ? "rotate-90" : ""].join(" ")}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}

function DotsIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-hidden="true">
      <circle cx="5" cy="12" r="1.8" />
      <circle cx="12" cy="12" r="1.8" />
      <circle cx="19" cy="12" r="1.8" />
    </svg>
  );
}

function SettingsIcon({ large = false }: { large?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={large ? "h-8 w-8" : "h-5 w-5"}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M12 15.5A3.5 3.5 0 1 0 12 8a3.5 3.5 0 0 0 0 7.5Z" />
      <path d="M19.4 15a1.8 1.8 0 0 0 .36 1.98l.04.04a2.1 2.1 0 0 1-2.98 2.98l-.04-.04a1.8 1.8 0 0 0-1.98-.36 1.8 1.8 0 0 0-1.1 1.66V21a2.1 2.1 0 0 1-4.2 0v-.06A1.8 1.8 0 0 0 8.4 19.3a1.8 1.8 0 0 0-1.98.36l-.04.04a2.1 2.1 0 1 1-2.98-2.98l.04-.04A1.8 1.8 0 0 0 3.8 14.7 1.8 1.8 0 0 0 2.14 13H2a2.1 2.1 0 0 1 0-4.2h.06A1.8 1.8 0 0 0 3.7 7.7a1.8 1.8 0 0 0-.36-1.98L3.3 5.68A2.1 2.1 0 1 1 6.28 2.7l.04.04A1.8 1.8 0 0 0 8.3 3.1 1.8 1.8 0 0 0 10 1.44V1.4a2.1 2.1 0 0 1 4.2 0v.06a1.8 1.8 0 0 0 1.1 1.64 1.8 1.8 0 0 0 1.98-.36l.04-.04a2.1 2.1 0 1 1 2.98 2.98l-.04.04a1.8 1.8 0 0 0-.36 1.98 1.8 1.8 0 0 0 1.66 1.1H22a2.1 2.1 0 0 1 0 4.2h-.06A1.8 1.8 0 0 0 19.4 15Z" />
    </svg>
  );
}

function EditIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5Z" />
    </svg>
  );
}

function SignalIcon({ pulse = false, small = false }: { pulse?: boolean; small?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={[small ? "h-4 w-4" : "h-5 w-5", pulse ? "animate-pulse" : ""].join(" ")}
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

function RefreshIcon({ spin = false }: { spin?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={["h-5 w-5", spin ? "animate-spin" : ""].join(" ")}
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

function TrashIcon({ pulse = false }: { pulse?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={["h-5 w-5", pulse ? "animate-pulse" : ""].join(" ")}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M6 6l1 15h10l1-15" />
      <path d="M10 11v6" />
      <path d="M14 11v6" />
    </svg>
  );
}

function GlobeIcon({ className }: { className: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18" />
      <path d="M12 3a13.5 13.5 0 0 1 0 18" />
      <path d="M12 3a13.5 13.5 0 0 0 0 18" />
    </svg>
  );
}

function ClipboardIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-full w-full" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 4h6" />
      <path d="M9 4a3 3 0 0 0 6 0" />
      <rect x="6" y="5" width="12" height="16" rx="2" />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-full w-full" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="m20 6-11 11-5-5" />
    </svg>
  );
}

function FolderIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-full w-full" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 7.5A2.5 2.5 0 0 1 5.5 5H10l2 2h6.5A2.5 2.5 0 0 1 21 9.5v7A2.5 2.5 0 0 1 18.5 19h-13A2.5 2.5 0 0 1 3 16.5v-9Z" />
    </svg>
  );
}

function QrIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-full w-full" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 4h6v6H4z" />
      <path d="M14 4h6v6h-6z" />
      <path d="M4 14h6v6H4z" />
      <path d="M14 14h2" />
      <path d="M20 14v2" />
      <path d="M16 18h4" />
      <path d="M14 20h2" />
    </svg>
  );
}

function SupportIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 12a9 9 0 0 1-9 9 8.9 8.9 0 0 1-4.2-1L3 21l1.2-4.1A9 9 0 1 1 21 12Z" />
    </svg>
  );
}

function StarIcon({ filled = false }: { filled?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill={filled ? "currentColor" : "none"}
      stroke="currentColor"
      strokeWidth={filled ? "0" : "1.8"}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9L12 3Z" />
    </svg>
  );
}

function HeartIcon({ filled = false }: { filled?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill={filled ? "currentColor" : "none"}
      stroke="currentColor"
      strokeWidth={filled ? "0" : "1.9"}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20.8 4.6a5.2 5.2 0 0 0-7.4 0L12 6l-1.4-1.4a5.2 5.2 0 1 0-7.4 7.4L12 20.8 20.8 12a5.2 5.2 0 0 0 0-7.4Z" />
    </svg>
  );
}

function ShieldIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  );
}

function PlusIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </svg>
  );
}

function XIcon() {
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

function AdminRestartDialog({ onClose }: { onClose: () => void }) {
  const m = useMessages();
  const [restarting, setRestarting] = useState(false);

  const restart = async () => {
    if (restarting) return;
    setRestarting(true);
    try {
      await api.restartAsAdmin();
    } catch (e) {
      setRestarting(false);
      notifyError(String(e));
    }
  };

  return (
    <ModalPortal>
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
              onClick={() => void restart()}
              disabled={restarting}
              className="primary-button interactive rounded-2xl py-4 text-lg font-bold"
            >
              {m.common.ok}
            </button>
          </div>
        </div>
      </div>
    </ModalPortal>
  );
}

function deduplicateById<T extends { id: string }>(items: T[]): T[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    if (seen.has(item.id)) return false;
    seen.add(item.id);
    return true;
  });
}

function isAdminRestartError(message: string): boolean {
  const normalized = message.toLowerCase();
  return normalized.includes("tun") && normalized.includes("администратор");
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
