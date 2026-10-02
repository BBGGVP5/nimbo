import { AutoFastestLine } from "../components/AutoFastestLine";
import { SubscriptionInfo } from "../components/SubscriptionInfo";
import { Dialog } from "../components/Universal";

import { LatencyDisplay } from "../components/LatencyDisplay";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { CountryFlag } from "../components/CountryFlag";
import { notifyError, notifyInfo } from "../lib/notify";
import { expireLabels, fillTemplate, useMessages, type Messages } from "../lib/i18n";
import {
  serverDisplayLabel,
  useServerUiOverrides,
  type ServerUiOverrides,
} from "../lib/serverUiOverrides";
import { usePingActions } from "../lib/usePingActions";
import { ServerContextMenu } from "../components/ServerContextMenu";
import { useCachedSubscriptionLogo } from "../lib/subscriptionLogo";
import { useAppStore } from "../store";
import { SignalProfiles } from "./profiles/SignalProfiles";
import {
  api,
  formatBytes,
  formatSubscriptionTerm,
  protocolLabel,
  serverCustomDescription,
  serverListDescription,
  transportLabel,
  type Server,
  type Subscription,
} from "../lib/api";

const FAVORITES_KEY = "nimbo.favorites";

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

export function Subscriptions() {
  const m = useMessages();
  const subs = useAppStore((s) => s.subscriptions);
  const activeId = useAppStore((s) => s.activeServerId);
  const serverPings = useAppStore((s) => s.serverPings);
  const setPageServerPing = useAppStore((s) => s.setServerPing);
  const connectingServerId = useAppStore((s) => s.connectingServerId);
  const switchingServerId = useAppStore((s) => s.switchingServerId);
  const setActive = useAppStore((s) => s.setActiveServer);
  const refreshSubscription = useAppStore((s) => s.refreshSubscription);
  const updateSubscriptionSettings = useAppStore((s) => s.updateSubscriptionSettings);
  const removeSubscription = useAppStore((s) => s.removeSubscription);
  const reorderSubscriptions = useAppStore((s) => s.reorderSubscriptions);
  const importOpen = useAppStore((s) => s.importDialogOpen);
  const openImportDialog = useAppStore((s) => s.openImportDialog);
  const closeImportDialog = useAppStore((s) => s.closeImportDialog);
  const [query, setQuery] = useState("");
  const [showFavOnly, setShowFavOnly] = useState(false);
  const preferences = useAppStore((s) => s.preferences);
  const [refreshingUrl, setRefreshingUrl] = useState<string | null>(null);
  const pagePing = usePingActions();
  const pingingUrl = subs.find(sub => sub.servers.some(server => pagePing.pending.has(server.id)))?.url ?? null;
  const [signalSettingsUrl, setSignalSettingsUrl] = useState<string | null>(null);
  const [signalRemoveUrl, setSignalRemoveUrl] = useState<string | null>(null);
  const [signalRenameServerId, setSignalRenameServerId] = useState<string | null>(null);
  const [signalHideServerId, setSignalHideServerId] = useState<string | null>(null);
  const { favorites, toggle: toggleFavorite } = useFavoriteServers();
  const {
    overrides: serverOverrides,
    renameServer,
    hideServer,
    showAllServers,
    hiddenCount,
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

  const moveSubscription = (url: string, delta: -1 | 1) => {
    const index = subs.findIndex((sub) => sub.url === url);
    if (index < 0) return;
    const target = index + delta;
    if (target < 0 || target >= subs.length) return;
    const next = [...subs];
    const [moved] = next.splice(index, 1);
    next.splice(target, 0, moved);
    void reorderSubscriptions(next.map((sub) => sub.url));
  };

  const pageHead = (
    <div className="mb-7 flex items-start justify-between gap-4 mobile-column">
      <div>
        <h1 className="page-title">{m.profiles.title}</h1>
        <p className="page-subtitle">
          {serverCount} {m.common.servers} · {subs.length} {m.common.subscriptions}
        </p>
        {hiddenCount > 0 && (
          <p className="mt-1 text-[11px] text-[var(--color-text-faint)]">
            {fillTemplate(m.common.hiddenServers, { count: hiddenCount })}{" "}
            <button
              type="button"
              className="interactive font-semibold text-[var(--color-accent-bright)]"
              onClick={showAllServers}
            >
              {m.common.showHiddenServers}
            </button>
          </p>
        )}
      </div>
      <div className="flex gap-3">
        <IconButton
          title={showFavOnly ? m.home.showAll : m.profiles.favorite}
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
  );

  const searchField = (
    <div className="relative mb-6">
      <span className="pointer-events-none absolute left-4 top-1/2 grid -translate-y-1/2 place-items-center text-[var(--color-text-faint)]">
        <SearchIcon />
      </span>
      <input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder={m.profiles.searchServers}
        className="dark-input py-4 pl-12 pr-12 text-lg"
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
  );

  /** Пингует все серверы подписки — то же действие, что и в старой карточке. */
  const pingSubscriptionServers = async (url: string) => {
    const sub = subs.find((item) => item.url === url);
    if (!sub) return;
    try {
      await pagePing.toggle(sub.servers.map(server => server.id), result => setPageServerPing(result.server_id, result.latency_ms ?? null));
    } catch (error) { notifyError(String(error)); }
  };

  const pingSingleServer = async (serverId: string) => {
    try { await pagePing.toggle([serverId], result => setPageServerPing(result.server_id, result.latency_ms ?? null)); }
    catch (error) { notifyError(String(error)); }
  };

  // ── Signal ────────────────────────────────────────────────────
  // Профили — полноценными карточками (трафик, срок, описание, ссылки,
  // порядок, настройки и удаление), серверы — общей таблицей ниже.
  // Старый список карточек не дублируется.
  if (preferences.ui_style === "signal") {
    const settingsSub = signalSettingsUrl ? subs.find((item) => item.url === signalSettingsUrl) ?? null : null;
    const removeSub = signalRemoveUrl ? subs.find((item) => item.url === signalRemoveUrl) ?? null : null;
    const allServers = subs.flatMap((item) => item.servers);
    const renameServerTarget = signalRenameServerId
      ? allServers.find((item) => item.id === signalRenameServerId) ?? null
      : null;
    const hideServerTarget = signalHideServerId
      ? allServers.find((item) => item.id === signalHideServerId) ?? null
      : null;
    return (
      <div className="page-view page-view-wide">
        {subs.length === 0 ? (
          <>
            {pageHead}
            <EmptyProfiles onAdd={() => openImportDialog()} />
          </>
        ) : (
          <SignalProfiles
            labels={m}
            subs={filteredSubs}
            activeId={activeId}
            connectingId={connectingServerId || switchingServerId}
            pingByServer={serverPings}
            favorites={favorites}
            onPickServer={(_sub, server) => void onSelect(server)}
            onToggleFavorite={toggleFavorite}
            hiddenServerIds={new Set<string>()}
            serverOverrides={serverOverrides}
            onRenameServer={(id) => setSignalRenameServerId(id)}
            onHideServer={(id) => setSignalHideServerId(id)}
            pingingServerIds={pagePing.pending}
            onPingServer={(id) => void pingSingleServer(id)}
            query={query}
            head={
              <>
                {pageHead}
                {searchField}
              </>
            }
            order={subs.map((item) => item.url)}
            onRefreshSubscription={(url) => {
              setRefreshingUrl(url);
              void refreshSubscription(url).catch(error => notifyError(String(error))).finally(() => setRefreshingUrl(null));
            }}
            onPingSubscription={(url) => void pingSubscriptionServers(url)}
            onOpenSettings={(url) => setSignalSettingsUrl(url)}
            onDeleteSubscription={(url) => setSignalRemoveUrl(url)}
            onMoveSubscription={(url, direction) => moveSubscription(url, direction)}
            refreshingUrl={refreshingUrl}
            pingingUrl={pingingUrl}
            updatedLabel={(sub) => formatFetchedAt(sub.fetched_at, m)}
            supportUrl={(sub) => sub.meta?.support_url?.trim() || "https://t.me/nebulaguard_channel"}
            siteUrl={(sub) => sub.meta?.website_url?.trim() || subscriptionSiteUrl(sub.url)}
          />
        )}
        {settingsSub && (
          <SubscriptionSettingsDialog
            sub={settingsSub}
            showOnHome={settingsSub.meta?.show_on_home !== false}
            updateInterval={settingsSub.meta?.update_interval_minutes ?? 720}
            supportUrl={settingsSub.meta?.support_url?.trim() || "https://t.me/nebulaguard_channel"}
            siteUrl={settingsSub.meta?.website_url?.trim() || subscriptionSiteUrl(settingsSub.url)}
            description={settingsSub.meta?.description?.trim() || ""}
            sourceUrl={settingsSub.url}
            onDelete={() => {
              setSignalSettingsUrl(null);
              setSignalRemoveUrl(settingsSub.url);
            }}
            onSave={(settings) => updateSubscriptionSettings(settingsSub.url, settings)}
            onClose={() => setSignalSettingsUrl(null)}
          />
        )}
        {removeSub && (
          <ConfirmDialog
            title={m.profiles.deleteSubscriptionTitle}
            description={fillTemplate(m.profiles.deleteSubscriptionDescription, {
              name: removeSub.name?.trim() || m.profiles.thisSubscription,
            })}
            confirmLabel={m.profiles.delete}
            danger
            onConfirm={() => {
              void (async () => {
                try {
                  await removeSubscription(removeSub.url);
                  notifyInfo(m.profiles.deleted);
                } catch (e) {
                  notifyError(String(e));
                } finally {
                  setSignalRemoveUrl(null);
                }
              })();
            }}
            onClose={() => setSignalRemoveUrl(null)}
          />
        )}
        {renameServerTarget && (
          <RenameServerDialog
            initialName={serverDisplayLabel(renameServerTarget, serverOverrides)}
            onSave={(name) => {
              renameServer(renameServerTarget.id, name);
              notifyInfo(m.profiles.serverRenamed);
              setSignalRenameServerId(null);
            }}
            onClose={() => setSignalRenameServerId(null)}
          />
        )}
        {hideServerTarget && (
          <ConfirmDialog
            title={m.profiles.deleteServerTitle}
            description={fillTemplate(m.profiles.deleteServerDescription, {
              name: serverDisplayLabel(hideServerTarget, serverOverrides),
            })}
            confirmLabel={m.profiles.deleteServer}
            danger
            onConfirm={() => {
              hideServer(hideServerTarget.id);
              setSignalHideServerId(null);
            }}
            onClose={() => setSignalHideServerId(null)}
          />
        )}
        {importOpen && <ImportDialog onClose={closeImportDialog} />}
        {adminDialogOpen && <AdminRestartDialog onClose={() => setAdminDialogOpen(false)} />}
      </div>
    );
  }

  return (
    <div className="page-view page-view-wide">
      <div className="mb-7 flex items-start justify-between gap-4 mobile-column">
        <div>
          <h1 className="page-title">{m.profiles.title}</h1>
          <p className="page-subtitle">
            {serverCount} {m.common.servers} · {subs.length} {m.common.subscriptions}
          </p>
          {hiddenCount > 0 && (
            <p className="mt-1 text-[11px] text-[var(--color-text-faint)]">
              {fillTemplate(m.common.hiddenServers, { count: hiddenCount })}{" "}
              <button
                type="button"
                className="interactive font-semibold text-[var(--color-accent-bright)]"
                onClick={showAllServers}
              >
                {m.common.showHiddenServers}
              </button>
            </p>
          )}
        </div>
        <div className="flex gap-3">
          <IconButton
            title={showFavOnly ? m.profiles.allProfiles : m.profiles.favorite}
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
        <span className="pointer-events-none absolute left-4 top-1/2 grid -translate-y-1/2 place-items-center text-[var(--color-text-faint)]">
          <SearchIcon />
        </span>
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={m.profiles.searchServers}
          className="dark-input py-4 pl-12 pr-12 text-lg"
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
              onMoveUp={() => moveSubscription(sub.url, -1)}
              onMoveDown={() => moveSubscription(sub.url, 1)}
              canMoveUp={subs.findIndex((item) => item.url === sub.url) > 0}
              canMoveDown={subs.findIndex((item) => item.url === sub.url) < subs.length - 1}
            />
          ))}
        </div>
      )}

      {importOpen && <ImportDialog onClose={closeImportDialog} />}
      {adminDialogOpen && <AdminRestartDialog onClose={() => setAdminDialogOpen(false)} />}
    </div>
  );
}

/** Хост ссылки — для короткой подписи активного зеркала. */
function hostOfUrl(value: string | null | undefined): string | null {
  const raw = value?.trim();
  if (!raw) return null;
  try {
    return new URL(raw).host;
  } catch {
    return null;
  }
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
  onMoveUp,
  onMoveDown,
  canMoveUp,
  canMoveDown,
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
  onMoveUp: () => void;
  onMoveDown: () => void;
  canMoveUp: boolean;
  canMoveDown: boolean;
}) {
  const m = useMessages();
  const used = (sub.info?.upload ?? 0) + (sub.info?.download ?? 0);
  const total = sub.info?.total ?? null;
  const expires = formatSubscriptionTerm(sub.info, expireLabels(m));
  const mirrorCount = sub.meta?.mirrors?.length ?? 0;
  // Активный домен показываем, только когда подписка реально уехала на зеркало:
  // при работе основного домена active_url пустой.
  const activeMirrorHost = hostOfUrl(sub.meta?.active_url);
  const mirrorHint = [sub.meta?.mirrors?.join(", "), sub.meta?.active_url]
    .filter(Boolean)
    .join(" — ");
  const supportUrl = sub.meta?.support_url?.trim() || "https://t.me/nebulaguard_channel";
  const siteUrl = sub.meta?.website_url?.trim() || subscriptionSiteUrl(sub.url);
  const description = sub.meta?.description?.trim() || "";
  const visibleDescription = /^описание подписки$/i.test(description) ? "" : description;
  const showOnHome = sub.meta?.show_on_home !== false;
  const updateInterval = sub.meta?.update_interval_minutes ?? 720;
  const updatedAt = formatFetchedAt(sub.fetched_at, m);
  const [refreshing, setRefreshing] = useState(false);
  const [removing, setRemoving] = useState(false);
  const cardPing = usePingActions();
  const pinging = sub.servers.some(server => cardPing.pending.has(server.id));
  const pingingServerIds = cardPing.pending;
  const [expanded, setExpanded] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [confirmRemoveOpen, setConfirmRemoveOpen] = useState(false);
  const setServerPing = useAppStore((s) => s.setServerPing);
  const showSubscriptionLogo = useAppStore((s) => s.preferences.show_subscription_logo);
  const autoSelected = useAppStore((s) =>
    s.status?.state === "connected" && s.status.auto_subscription_url === sub.url);
  const logoSrc = useCachedSubscriptionLogo(sub, showSubscriptionLogo);

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
    try { await cardPing.toggle(sub.servers.map(server => server.id), result => setServerPing(result.server_id, result.latency_ms ?? null)); }
    catch (error) { notifyError(String(error)); }
  };

  const onPingServerClick = async (serverId: string) => {
    try { await cardPing.toggle([serverId], result => setServerPing(result.server_id, result.latency_ms ?? null)); }
    catch (error) { notifyError(String(error)); }
  };

  return (
    <section className="subscription-card panel relative">
      <div
        className="subscription-card-body cursor-pointer p-4"
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
          className="subscription-summary mb-4 grid grid-cols-[24px_minmax(0,1fr)_auto] items-start gap-3 rounded-xl text-left"
        >
          <div className="pt-1 text-[var(--color-text-faint)]">
            <ChevronIcon open={expanded} />
          </div>
          <div className="subscription-summary-main min-w-0">
            <div className="flex min-w-0 items-center gap-3">
              {logoSrc && (
                <img
                  key={logoSrc}
                  src={logoSrc}
                  alt=""
                  className="subscription-logo-image h-8 w-8 shrink-0 object-cover ring-1 ring-white/15"
                  onError={(e) => {
                    e.currentTarget.style.display = "none";
                  }}
                />
              )}
              <div className="truncate text-lg font-semibold text-white">{sub.name ?? m.common.subscription}</div>
              <span className="shrink-0 rounded-full bg-[rgba(255,255,255,0.08)] px-2.5 py-1 text-xs font-bold text-[var(--color-text-dim)]">
                {sub.servers.length}
              </span>
            </div>
            <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[var(--color-text-faint)]">
              <span>{intervalLabel(updateInterval, m)}</span>
              <span>{m.profiles.updated}: {updatedAt}</span>
              {!showOnHome && <span>{m.profiles.hiddenFromHome}</span>}
              {mirrorCount > 0 && (
                <span title={mirrorHint}>
                  {fillTemplate(m.profiles.mirrors, { count: mirrorCount })}
                  {activeMirrorHost ? ` · ${activeMirrorHost}` : ""}
                </span>
              )}
            </div>
          </div>
          <div className="subscription-summary-actions flex items-center gap-1.5" data-no-toggle>
            <div className="subscription-reorder-control" role="group" aria-label={m.profiles.reorder} data-no-toggle>
              <span className="subscription-reorder-label">{m.profiles.reorder}</span>
              <IconButton compact title={m.profiles.moveUp} icon={<ArrowUpIcon />} onClick={() => void onMoveUp()} disabled={!canMoveUp} />
              <IconButton compact title={m.profiles.moveDown} icon={<ArrowDownIcon />} onClick={() => void onMoveDown()} disabled={!canMoveDown} />
            </div>
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
                <div className="server-row-menu subscription-action-menu">
                  <button
                    onClick={() => {
                      setMenuOpen(false);
                      setSettingsOpen(true);
                    }}
                    className="subscription-action-menu-item"
                  >
                    <SettingsIcon />
                    {m.profiles.subscriptionSettings}
                  </button>
                  <button
                    onClick={() => {
                      setMenuOpen(false);
                      void onRemoveClick();
                    }}
                    className="subscription-action-menu-item server-row-menu-danger"
                  >
                    <TrashIcon pulse={removing} />
                    {m.profiles.delete}
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="subscription-stats mobile-stack mb-3 grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,0.72fr)] gap-2 text-xs">
          <MiniStat label={m.profiles.traffic} value={trafficValue} />
          <MiniStat label={m.profiles.expires} value={expires} />
          <MiniStat label={m.profiles.updated} value={updatedAt} />
        </div>

        <div className="subscription-description mb-3 rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-accent-panel)] px-3 py-3">
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
          <AutoFastestLine
            servers={sub.servers}
            subscriptionUrl={sub.url}
            autoSelected={autoSelected}
            activeId={activeId}
            pings={serverPings}
            displayName={(server) => serverDisplayLabel(server, serverOverrides)}
          />
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

/**
 * Первая строка списка: подключение к лучшему узлу одним нажатием.
 *
 * Место выбрано намеренно — именно здесь человек выбирает сервер, и «авто»
 * читается как ещё один вариант выбора, а не как настройка, спрятанная
 * страницей глубже.
 */
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
  const description = serverCustomDescription(server);
  const [renameOpen, setRenameOpen] = useState(false);
  const [confirmHideOpen, setConfirmHideOpen] = useState(false);
  void servers;

  return (
    <ServerContextMenu
      label={m.profiles.serverMenu} actions={[
        { label: pinging ? (m.common.locale.startsWith("ru") ? "Остановить пинг" : "Stop ping") : m.profiles.testLatency, onClick: onPing },
        { label: m.profiles.renameServer, onClick: () => setRenameOpen(true) },
        { label: m.profiles.deleteServer, onClick: () => setConfirmHideOpen(true), danger: true },
      ]}
      role="button"
      aria-pressed={active}
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
          <div className="server-profile-title" data-server-menu-anchor>{label}</div>
          {active && !connecting && <span className="server-selection-badge">
            ✓ {m.common.locale.startsWith("ru") ? "Выбран" : "Selected"}
          </span>}
          {connecting && (
            <span className="server-row-pill server-row-pill-selected">
              {m.common.connecting}
            </span>
          )}
        </div>
        {description && <div className="server-profile-description">{description}</div>}
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
    </ServerContextMenu>
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
  if (ping == null) return <LatencyDisplay value={ping} />;
  return (
    <span className="server-ping-badge">
      <LatencyDisplay value={ping} />
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

  return <Dialog title={m.profiles.renameServer} closeLabel={m.common.close} onClose={onClose}
    footer={<><button className="btn" onClick={onClose}>{m.common.cancel}</button><button className="primary-button btn" disabled={!canSave} onClick={() => canSave && onSave(name)}>{m.common.save}</button></>}>
    <p className="universal-confirm-description">{m.profiles.renameServerDescription}</p>
    <input aria-label={m.profiles.renameServer} value={name} onChange={event => setName(event.target.value)} className="dark-input w-full px-4 py-3" autoFocus />
  </Dialog>;
}

function SubscriptionSettingsDialog({
  sub,
  showOnHome,
  updateInterval,
  supportUrl,
  siteUrl,
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
  const [infoOpen, setInfoOpen] = useState(false);

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

  return <>
    <Dialog title={m.profiles.subscriptionSettings} closeLabel={m.common.close} onClose={onClose} closeDisabled={saving} footer={<div className="subscription-settings-footer">
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
          </div>}>
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

            <button type="button" className="signal-btn signal-btn--ghost" onClick={() => setInfoOpen(true)}>{m.profiles.providerLinks} · {m.common.description}</button>

          </div>

    </Dialog>
    {infoOpen && <SubscriptionInfo sub={sub} labels={m} supportUrl={supportUrl} siteUrl={siteUrl} onClose={() => setInfoOpen(false)}/>}
  </>;
}

function networkBadge(protocol: Server["protocol"]): string {
  if (protocol.kind === "shadowsocks") return "SHADOWSOCKS";
  if (protocol.kind === "naive") return "NAIVEPROXY";
  if (protocol.kind === "awg") return "AWG";
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
  return <Dialog title={title} closeLabel={m.common.close} onClose={onClose} closeDisabled={busy} footer={<>
      <button type="button" onClick={onClose} disabled={busy} className="signal-btn signal-btn--ghost">{m.common.cancel}</button>
      <button type="button" onClick={onConfirm} disabled={busy} className={`signal-btn ${danger ? "universal-danger" : "signal-btn--primary"}`}>{busy ? m.profiles.wait : confirmLabel}</button>
    </>}>
    <p className="universal-confirm-description">{description}</p>

  </Dialog>;
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
    <Dialog title={m.profiles.importTitle} closeLabel={m.common.close} onClose={onClose} closeDisabled={busy} footer={<><button className="btn" disabled={busy} onClick={onClose}>{m.common.cancel}</button><button
            onClick={() => importSource()}
            disabled={busy || !source.trim()}
            className="primary-button interactive rounded-xl px-4 py-3 text-sm disabled:opacity-40"
          >
            {busy ? m.common.importing : m.common.import}
          </button></>}>
      <p className="universal-confirm-description">{m.profiles.importSubtitle}</p>
        <div className="grid grid-cols-1 gap-3">
          <textarea
            rows={3}
            spellCheck={false}
            autoComplete="off"
            value={source}
            onChange={(e) => setSource(e.target.value)}
            aria-label={m.profiles.sourcePlaceholder}
            placeholder={m.profiles.sourcePlaceholder}
            className="dark-input px-4 py-3 text-sm font-mono"
          />

        </div>

        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          aria-label={m.profiles.namePlaceholder}
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
          <div role="alert" className="mt-4 rounded-xl border border-[rgba(244,67,54,0.35)] bg-[rgba(244,67,54,0.12)] px-4 py-3 text-sm text-[var(--color-status-error)]">
            {err}
          </div>
        )}

        {importedCount !== null && !err && (
          <div className="mt-4 rounded-xl border border-[var(--color-accent)] bg-[var(--color-accent-active-bg)] px-4 py-3 text-base font-semibold text-[var(--color-accent-bright)]">
            {fillTemplate(m.profiles.importedServers, { count: importedCount })}
          </div>
        )}
    </Dialog>
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

function ArrowUpIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="m5 15 7-7 7 7" />
    </svg>
  );
}

function ArrowDownIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="m5 9 7 7 7-7" />
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

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.2-3.2" />
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
  const [error, setError] = useState<string | null>(null);

  const restart = async () => {
    if (restarting) return;
    setRestarting(true); setError(null);
    try {
      await api.restartAsAdmin();
    } catch (e) {
      setRestarting(false); setError(String(e));
      notifyError(String(e));
    }
  };

  return <Dialog title={m.home.adminTitle} closeLabel={m.common.close} onClose={onClose} closeDisabled={restarting}
    footer={<><button className="btn" disabled={restarting} onClick={onClose}>{m.common.cancel}</button><button className="primary-button btn" onClick={() => void restart()} disabled={restarting}>{restarting ? m.common.savingProgress : m.common.ok}</button></>}>
    <p className="universal-confirm-description">{m.home.adminText}</p>{error && <p role="alert" className="secondary-note">{error}</p>}
  </Dialog>;
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

