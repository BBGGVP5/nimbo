import { Link } from "react-router-dom";
import { notifyError } from "../lib/notify";
import { useAppStore } from "../store";
import {
  formatBytes,
  protocolLabel,
  serverListDescription,
  transportLabel,
  type Server,
  type Subscription,
} from "../lib/api";
import { useMessages } from "../lib/i18n";

export function ServersOverview() {
  const m = useMessages();
  const subs = useAppStore((s) => s.subscriptions);
  const activeId = useAppStore((s) => s.activeServerId);
  const serverPings = useAppStore((s) => s.serverPings);
  const setActive = useAppStore((s) => s.setActiveServer);
  const refresh = useAppStore((s) => s.refreshSubscription);
  const serverCount = subs.reduce((sum, sub) => sum + sub.servers.length, 0);

  const onSelect = async (server: Server) => {
    try {
      await setActive(activeId === server.id ? null : server.id);
    } catch (e) {
      notifyError(String(e));
    }
  };

  return (
    <div className="page-view">
      <div className="mb-8 flex items-start justify-between gap-4 mobile-column">
        <div>
          <h1 className="page-title">{m.settings.servers}</h1>
          <p className="page-subtitle">
            {serverCount} {m.common.servers} · {subs.length} {m.common.subscriptions}
          </p>
        </div>
        <div className="flex gap-3">
          <IconButton label={m.common.refresh} icon="↻" onClick={() => subs[0] && refresh(subs[0].url)} />
          <Link to="/subscriptions" className="grid h-12 w-12 place-items-center rounded-xl bg-[var(--color-accent-active-bg)] text-2xl font-bold text-[var(--color-accent)]">
            +
          </Link>
        </div>
      </div>

      <input
        placeholder={m.profiles.searchServers}
        className="dark-input mb-8 px-5 py-4 text-lg"
      />

      {subs.length === 0 ? (
        <div className="panel py-28 text-center text-lg text-[var(--color-text-faint)]">
          {m.profiles.emptyTitle}. {m.profiles.emptyDescription}.
        </div>
      ) : (
        <div className="space-y-6">
          {subs.map((sub) => (
            <SubscriptionServers
              key={sub.url}
              sub={sub}
              activeId={activeId}
              serverPings={serverPings}
              onSelect={onSelect}
              onRefresh={() => refresh(sub.url)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function SubscriptionServers({
  sub,
  activeId,
  serverPings,
  onSelect,
  onRefresh,
}: {
  sub: Subscription;
  activeId: string | null;
  serverPings: Record<string, number>;
  onSelect: (server: Server) => void;
  onRefresh: () => void;
}) {
  const m = useMessages();
  const used = (sub.info?.upload ?? 0) + (sub.info?.download ?? 0);
  const total = sub.info?.total ?? null;
  const days = sub.info?.expire
    ? Math.max(0, Math.floor((sub.info.expire * 1000 - Date.now()) / 86400000))
    : null;

  return (
    <section className="panel overflow-hidden">
      <div className="p-6">
        <div className="mb-4 flex items-center justify-between gap-4">
          <div className="min-w-0">
            <div className="flex items-center gap-3">
              <span className="text-xl">🛡️</span>
              <h2 className="truncate text-xl font-black text-white">
                {sub.name ?? m.common.subscription}
              </h2>
              <span className="rounded-full bg-[var(--color-glass-bg-strong)] px-3 py-1 text-sm font-bold text-[var(--color-text-dim)]">
                {sub.servers.length}
              </span>
            </div>
          </div>
          <button
            onClick={onRefresh}
            className="rounded-xl bg-[var(--color-glass-bg)] px-4 py-3 text-lg text-[var(--color-text-dim)] hover:text-white"
          >
            ↻
          </button>
        </div>

        <div className="mb-4 flex items-center gap-4 text-sm font-bold text-[var(--color-text-faint)]">
          <span>{days !== null ? `${days} ${m.profiles.daysShort}` : "∞"}</span>
          <div className="h-1 flex-1 rounded-full bg-[var(--color-glass-bg-strong)]" />
          <span>
            {total ? `${formatBytes(used)} / ${formatBytes(total)}` : `${formatBytes(used)} / ∞`}
          </span>
        </div>

        <div className="rounded-2xl border border-[rgba(245,166,35,0.28)] bg-[rgba(245,166,35,0.08)] p-5 text-[var(--color-text-dim)]">
          <div>🛡️ @{sub.name ?? "provider"}</div>
          <div>🗓️ {m.profiles.expires}: {days ?? "∞"}</div>
          <div>📊 {m.profiles.traffic}: {formatBytes(used)}</div>
          <div className="truncate">🔗 {sub.url}</div>
        </div>
      </div>

      <div className="divide-y divide-[var(--color-border)] border-t border-[var(--color-border)]">
        {sub.servers.map((server) => (
          <ServerLine
            key={server.id}
            server={server}
            servers={sub.servers}
            active={activeId === server.id}
            latency={serverPings[server.id]}
            onSelect={() => onSelect(server)}
          />
        ))}
      </div>
    </section>
  );
}

function ServerLine({
  server,
  servers,
  active,
  latency,
  onSelect,
}: {
  server: Server;
  servers: Server[];
  active: boolean;
  latency?: number;
  onSelect: () => void;
}) {
  const description = serverListDescription(server, servers);

  return (
    <button
      onClick={onSelect}
      className={[
        "grid w-full grid-cols-[46px_1fr_auto] items-center gap-3 px-5 py-4 text-left transition-colors",
        active ? "bg-[var(--color-accent-active-bg)]" : "hover:bg-[var(--color-glass-bg)]",
      ].join(" ")}
    >
      <div className="grid h-10 w-10 place-items-center rounded-lg bg-[var(--color-glass-bg)] text-xl">
        ◎
      </div>
      <div className="min-w-0">
        <div className="flex min-w-0 items-center gap-2">
          <div className="truncate text-lg font-black text-white">{server.name}</div>
          <PingBadge ping={latency} />
        </div>
        {description && (
          <div className="mt-1 truncate text-xs text-[var(--color-text-faint)]">
            {description}
          </div>
        )}
        <div className="mt-1 flex flex-wrap gap-2">
          <span className="tag-pill px-2.5 py-1 text-xs">{protocolLabel(server.protocol)}</span>
          <span className="rounded-full bg-[rgba(245,166,35,0.16)] px-2.5 py-1 text-xs font-black text-[#f5a623]">
            {transportLabel(server.protocol) || "JSON"}
          </span>
        </div>
      </div>
      <div className="w-1" />
    </button>
  );
}

function PingBadge({ ping }: { ping?: number }) {
  if (ping == null) return null;
  return (
    <span className="shrink-0 rounded-full bg-[var(--color-accent-active-bg)] px-2.5 py-1 text-xs font-black text-[var(--color-accent-bright)]">
      {ping} ms
    </span>
  );
}

function IconButton({
  icon,
  label,
  onClick,
}: {
  icon: string;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      title={label}
      onClick={onClick}
      className="grid h-12 w-12 place-items-center rounded-xl bg-[var(--color-glass-bg)] text-xl text-[var(--color-text-dim)] hover:text-white"
    >
      {icon}
    </button>
  );
}
