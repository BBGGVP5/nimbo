import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { CountryFlag } from "../components/CountryFlag";
import { notifyError } from "../lib/notify";
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
} from "../lib/api";
import { useMessages } from "../lib/i18n";

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
              {deduplicateById(sub.servers).map((server) => (
                <ServerRow
                  key={server.id}
                  server={server}
                  servers={sub.servers}
                  active={activeId === server.id}
                  ping={serverPings[server.id]}
                  pinging={pingingServerIds.has(server.id)}
                  onSelect={() => onSelect(server)}
                  onPing={() => onPingServer(server.id)}
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
  servers,
  active,
  ping,
  pinging,
  onSelect,
  onPing,
}: {
  server: Server;
  servers: Server[];
  active: boolean;
  ping?: number;
  pinging: boolean;
  onSelect: () => void;
  onPing: () => void;
}) {
  const m = useMessages();
  const label = serverDisplayName(server.name);
  const description = serverListDescription(server, servers);
  const proto = protocolLabel(server.protocol);
  const transport = networkBadge(server.protocol);

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
      </div>
      <div className="server-profile-main">
        <div className="server-profile-title-line">
          <div className="server-profile-title">{label}</div>
          <PingBadge ping={ping} loading={pinging} />
        </div>
        {description && (
          <div className="server-detail-row-description">
            {description}
          </div>
        )}
      </div>
      <div className="server-profile-actions server-detail-profile-actions">
        <span className="server-row-pill server-row-pill-proto">{proto}</span>
        <span className="server-row-pill server-row-pill-transport">{transport}</span>
        {active && (
          <span className="server-row-pill server-row-pill-selected">{m.common.selected}</span>
        )}
        <button
          type="button"
          title={m.home.pingServers}
          aria-label={m.home.pingServers}
          onClick={(event) => {
            event.stopPropagation();
            void onPing();
          }}
          disabled={pinging}
          className={[
            "server-row-icon-button server-row-dots-button",
            pinging ? "text-[var(--color-accent-bright)] opacity-80" : "",
          ].join(" ")}
        >
          <SignalIcon pulse={pinging} />
        </button>
      </div>
    </div>
  );
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

function SignalIcon({ pulse = false }: { pulse?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={["h-4 w-4", pulse ? "animate-pulse" : ""].join(" ")}
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
