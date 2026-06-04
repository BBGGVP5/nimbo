import { useState, useCallback, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useNavigate, useParams } from "react-router-dom";
import { CountryFlag } from "../components/CountryFlag";
import { notifyError, notifyInfo } from "../lib/notify";
import { useAppStore } from "../store";
import {
  api,
  formatBytes,
  formatExpire,
  protocolLabel,
  serverDisplayName,
  serverCustomDescription,
  transportLabel,
  type Server,
} from "../lib/api";
import { useMessages } from "../lib/i18n";

const FAVORITES_KEY = "nimbo.favorites";
function readFavoriteServers(): Set<string> {
  try {
    const raw = localStorage.getItem(FAVORITES_KEY);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw);
    return new Set(Array.isArray(parsed) ? parsed.map(String) : []);
  } catch {
    return new Set();
  }
}
function writeFavoriteServers(value: Set<string>) {
  try {
    localStorage.setItem(FAVORITES_KEY, JSON.stringify([...value]));
  } catch {
    /* ignore */
  }
}
function useFavoriteServers() {
  const [favorites, setFavorites] = useState<Set<string>>(readFavoriteServers);
  const toggle = (serverId: string) => {
    setFavorites((previous) => {
      const next = new Set(previous);
      if (next.has(serverId)) {
        next.delete(serverId);
      } else {
        next.add(serverId);
      }
      writeFavoriteServers(next);
      return next;
    });
  };
  return { favorites, toggle };
}

const SERVER_OVERRIDES_KEY = "nimbo.serverOverrides";
type ServerUiOverrides = Record<string, { name?: string; hidden?: boolean }>;
function readServerUiOverrides(): ServerUiOverrides {
  try {
    const raw = localStorage.getItem(SERVER_OVERRIDES_KEY);
    if (!raw) return {};
    return JSON.parse(raw);
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

export function Servers() {
  const m = useMessages();
  const { url } = useParams<{ url: string }>();
  const navigate = useNavigate();
  const subs = useAppStore((s) => s.subscriptions);
  const activeId = useAppStore((s) => s.activeServerId);
  const serverPings = useAppStore((s) => s.serverPings);
  const setActive = useAppStore((s) => s.setActiveServer);
  const setServerPing = useAppStore((s) => s.setServerPing);
  const [pingingServerIds, setPingingServerIds] = useState<Set<string>>(() => new Set());
  const { favorites, toggle: toggleFavorite } = useFavoriteServers();
  const { overrides: serverOverrides, renameServer, hideServer } = useServerUiOverrides();

  const decoded = url ? decodeURIComponent(url) : "";
  const sub = subs.find((s) => s.url === decoded);

  if (!sub) {
    return (
      <div className="page-surface glass rounded-2xl h-full p-10 overflow-auto">
        <div className="mx-auto max-w-5xl text-center">
          <p className="text-sm text-[var(--color-text-dim)] mb-4">
            {m.common.subscriptionNotFound}
          </p>
          <button
            onClick={() => navigate(-1)}
            className="text-sm text-[var(--color-accent-bright)] hover:underline"
          >
            ← {m.app.profiles}
          </button>
        </div>
      </div>
    );
  }

  const onSelect = async (server: Server) => {
    try {
      await setActive(activeId === server.id ? null : server.id);
    } catch (e) {
      notifyError(String(e));
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

  const used = (sub.info?.upload ?? 0) + (sub.info?.download ?? 0);
  const total = sub.info?.total ?? null;
  const expires = formatExpire(sub.info?.expire);
  const description = sub.meta?.description?.trim() || "";
  const visibleDescription = /^описание подписки$/i.test(description) ? "" : description;
  const supportUrl = sub.meta?.support_url?.trim() || "";
  const siteUrl = sub.meta?.website_url?.trim() || "";
  const trafficValue = total
    ? `${formatBytes(used)} / ${formatBytes(total)}`
    : `${formatBytes(used)} / ∞`;

  return (
    <div className="server-detail-page page-surface glass rounded-2xl h-full overflow-hidden">
      <div className="server-detail-scroll h-full overflow-auto">
        <div className="server-detail-inner mx-auto w-full max-w-none p-6">

          {/* Back button */}
          <button
            onClick={() => navigate(-1)}
            className="mb-5 flex items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-glass-bg)] px-3 py-2 text-sm font-semibold text-[var(--color-text-dim)] transition-all hover:border-[var(--color-border-strong)] hover:bg-[var(--color-glass-bg-strong)] hover:text-white"
          >
            <ChevronLeftIcon />
            {m.app.profiles}
          </button>

          {/* Profile header card */}
          <div className="server-detail-card panel mb-4 p-4">
            <div className="mb-3 flex min-w-0 items-center gap-3">
              <div className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-[var(--color-accent-active-bg)] text-[var(--color-accent-bright)]">
                <ShieldIcon />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex min-w-0 items-center gap-2">
                  <div className="truncate text-lg font-bold text-white">
                    {sub.name ?? m.common.subscription}
                  </div>
                  <span className="shrink-0 rounded-full bg-[rgba(255,255,255,0.08)] px-2.5 py-0.5 text-xs font-bold text-[var(--color-text-dim)]">
                    {sub.servers.length}
                  </span>
                </div>
              </div>
            </div>

            {/* Stats grid */}
            <div className="mb-3 grid grid-cols-2 gap-2 text-xs">
              <MiniStat label={m.profiles.traffic} value={trafficValue} />
              <MiniStat label={m.profiles.expires} value={expires} />
            </div>

            {/* Description */}
            <div className="mb-3 rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-accent-panel)] px-3 py-3">
              <div className="mb-1 text-[9px] uppercase tracking-wider text-[var(--color-text-faint)]">
                {m.common.description}
              </div>
              <div className="whitespace-pre-wrap text-sm leading-relaxed text-[var(--color-text-dim)]">
                {visibleDescription || m.common.noDescription}
              </div>
            </div>

            {/* Support / Site links */}
            {(supportUrl || siteUrl) && (
              <div className="flex flex-wrap gap-2">
                {supportUrl && (
                  <a
                    href={supportUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="interactive inline-flex items-center gap-2 rounded-xl border border-[var(--color-accent)] bg-[var(--color-accent-panel)] px-3 py-2 text-sm font-semibold text-[var(--color-accent-bright)]"
                  >
                    <SupportIcon />
                    {m.common.support}
                  </a>
                )}
                {siteUrl && (
                  <a
                    href={siteUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="interactive inline-flex items-center gap-2 rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-accent-panel)] px-3 py-2 text-sm font-semibold text-[var(--color-accent-bright)]"
                  >
                    <GlobeIcon />
                    {m.common.site}
                  </a>
                )}
              </div>
            )}
          </div>

          {/* Server count label */}
          <div className="mb-2 px-1 text-[10px] uppercase tracking-wider text-[var(--color-text-faint)]">
            {sub.servers.length} {m.common.servers}
          </div>

          {/* Server list */}
          <div className="server-detail-list panel overflow-hidden">
            <div className="divide-y divide-[var(--color-border)] bg-[rgba(255,255,255,0.018)]">
              {deduplicateById(sub.servers)
                .filter((server) => !serverOverrides[server.id]?.hidden)
                .map((server) => (
                  <ServerRow
                    key={server.id}
                    server={server}
                    active={activeId === server.id}
                    ping={serverPings[server.id]}
                    pinging={pingingServerIds.has(server.id)}
                    displayName={serverDisplayLabel(server, serverOverrides)}
                    favorite={favorites.has(server.id)}
                    onSelect={() => onSelect(server)}
                    onPing={() => onPingServer(server.id)}
                    onToggleFavorite={() => toggleFavorite(server.id)}
                    onRename={(name) => renameServer(server.id, name)}
                    onHide={() => hideServer(server.id)}
                  />
                ))}
            </div>
          </div>
        </div>
      </div>
    </div>
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

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-glass-bg)] px-3 py-2">
      <div className="mb-1 text-[9px] uppercase tracking-wider text-[var(--color-text-faint)]">
        {label}
      </div>
      <div className="truncate text-xs font-semibold text-[var(--color-text-dim)]">{value}</div>
    </div>
  );
}

function ServerRow({
  server,
  active,
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
  active: boolean;
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
  const proto = protocolLabel(server.protocol);
  const transport = networkBadge(server.protocol);

  const [menuOpen, setMenuOpen] = useState(false);
  const [renameOpen, setRenameOpen] = useState(false);
  const [confirmHideOpen, setConfirmHideOpen] = useState(false);

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
        "server-profile-row server-detail-profile-row group",
        active ? "server-profile-row-active server-detail-row-active" : "",
      ].join(" ")}
    >
      <div className="server-profile-logo server-detail-profile-logo">
        <CountryFlag
          serverName={server.name}
          fallback={<GlobeIcon />}
          className="country-flag-server"
        />
        {favorite && (
          <span className="server-favorite-mark" aria-hidden="true">
            <HeartIcon filled />
          </span>
        )}
      </div>
      <div className="server-profile-main">
        <div className="server-profile-title-line">
          <div className="server-profile-title">{label}</div>
        </div>
        {description && (
          <div className="server-detail-row-description">
            {description}
          </div>
        )}
      </div>
      <div className="server-profile-actions server-detail-profile-actions" data-no-toggle>
        <span className="server-row-pill server-row-pill-proto">{proto}</span>
        <span className="server-row-pill server-row-pill-transport">{transport}</span>
        {active && (
          <span className="server-row-pill server-row-pill-selected">{m.common.selected}</span>
        )}
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

function fillTemplate(template: string, values: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (_, key) => values[key] ?? `{${key}}`);
}

function PingBadge({ ping, loading = false }: { ping?: number; loading?: boolean }) {
  if (loading) {
    return (
      <span className="server-ping-badge server-detail-ping-badge server-ping-badge-loading">
        ...
      </span>
    );
  }
  if (ping == null) return null;
  const tier = pingTier(ping);
  return (
    <span
      className="server-ping-badge server-detail-ping-badge"
      style={{ background: tier.bg, color: tier.fg }}
    >
      {ping} ms
    </span>
  );
}

function pingTier(ping: number): { bg: string; fg: string } {
  if (ping < 100) return { bg: "rgba(76,217,100,0.16)", fg: "#7be084" };
  if (ping < 300) return { bg: "rgba(245,192,64,0.16)", fg: "#f5c040" };
  return { bg: "rgba(239,83,80,0.18)", fg: "#ff8080" };
}

function networkBadge(protocol: Server["protocol"]): string {
  if (protocol.kind === "shadowsocks") return "SHADOWSOCKS";
  const value = transportLabel(protocol).replace(" · ", " • ").trim();
  return value ? value.toUpperCase() : "JSON";
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
                danger ? "bg-[var(--color-status-error)] text-white hover:bg-[var(--color-status-error-hover)]" : "primary-button",
              ].join(" ")}
            >
              {confirmLabel}
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

// ── Icons ─────────────────────────────────────────────────────

function ChevronLeftIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m15 18-6-6 6-6" />
    </svg>
  );
}

function ShieldIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  );
}

function GlobeIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
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

function SupportIcon() {
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
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
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
      aria-hidden="true"
    >
      <path d="M4 20v-2" />
      <path d="M8 20v-5" />
      <path d="M12 20v-8" />
      <path d="M16 20v-11" />
      <path d="M20 20V5" />
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

function TrashIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M6 6l1 15h10l1-15" />
      <path d="M10 11v6" />
      <path d="M14 11v6" />
    </svg>
  );
}

function EditIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5Z" />
    </svg>
  );
}
