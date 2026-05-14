import { Link, useNavigate, useParams } from "react-router-dom";
import { CountryFlag } from "../components/CountryFlag";
import { notifyError } from "../lib/notify";
import { useAppStore } from "../store";
import {
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

  const decoded = url ? decodeURIComponent(url) : "";
  const sub = subs.find((s) => s.url === decoded);

  if (!sub) {
    return (
      <div className="page-surface glass rounded-2xl h-full p-10 overflow-auto">
        <div className="mx-auto max-w-5xl text-center">
          <div className="text-sm text-[var(--color-text-dim)] mb-4">
            {m.common.subscriptionNotFound}
          </div>
          <Link
            to="/subscriptions"
            className="text-sm text-[var(--color-accent-bright)] hover:underline"
          >
            ← {m.app.profiles}
          </Link>
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

  return (
    <div className="page-surface glass rounded-2xl h-full p-10 overflow-auto">
      <div className="mx-auto w-full max-w-none">
        <div className="mb-6">
          <button
            onClick={() => navigate(-1)}
            className="text-xs text-[var(--color-text-dim)] hover:text-[var(--color-text)] mb-2"
          >
            ← {m.app.profiles}
          </button>
          <h1 className="text-2xl font-semibold mb-1">
            {sub.name ?? m.common.subscription}
          </h1>
          <p className="text-sm text-[var(--color-text-dim)] font-mono truncate">
            {sub.url}
          </p>
        </div>

        <div className="text-xs uppercase tracking-wider text-[var(--color-text-faint)] mb-3">
          {sub.servers.length} {m.common.servers}
        </div>

        <div className="space-y-2">
          {sub.servers.map((server) => (
            <ServerRow
              key={server.id}
              server={server}
              servers={sub.servers}
              active={activeId === server.id}
              ping={serverPings[server.id]}
              onSelect={() => onSelect(server)}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

function ServerRow({
  server,
  servers,
  active,
  ping,
  onSelect,
}: {
  server: Server;
  servers: Server[];
  active: boolean;
  ping?: number;
  onSelect: () => void;
}) {
  const label = serverDisplayName(server.name);
  const description = serverListDescription(server, servers);
  return (
    <button
      onClick={onSelect}
      className={[
        "mobile-column glass flex w-full items-center gap-3 rounded-xl p-3.5 text-left transition-all",
        active
          ? "ring-2 ring-[var(--color-accent)] shadow-[0_0_30px_var(--color-glow-accent)]"
          : "hover:bg-[var(--color-glass-bg-strong)]",
      ].join(" ")}
    >
      <div className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-[var(--color-glass-bg)] text-[var(--color-text-faint)]">
        <CountryFlag serverName={server.name} fallback={<GlobeIcon />} />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex min-w-0 items-center gap-2">
          <div className="truncate font-medium">{label}</div>
          <PingBadge ping={ping} />
        </div>
        {description && (
          <div className="truncate text-xs text-[var(--color-text-faint)]">
            {description}
          </div>
        )}
      </div>
      <div className="text-right shrink-0">
        <div className="text-xs font-medium text-[var(--color-accent-bright)]">
          {protocolLabel(server.protocol)}
        </div>
        <div className="text-[10px] text-[var(--color-text-faint)] font-mono">
          {transportLabel(server.protocol)}
        </div>
      </div>
    </button>
  );
}

function PingBadge({ ping }: { ping?: number }) {
  if (ping == null) return null;
  return (
    <span className="shrink-0 rounded-full bg-[var(--color-accent-active-bg)] px-2 py-1 text-[10px] font-semibold text-[var(--color-accent-bright)]">
      {ping} ms
    </span>
  );
}

function GlobeIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18" />
      <path d="M12 3a13.5 13.5 0 0 1 0 18" />
      <path d="M12 3a13.5 13.5 0 0 0 0 18" />
    </svg>
  );
}
