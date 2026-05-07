import { Link, useNavigate, useParams } from "react-router-dom";
import { useAppStore } from "../store";
import {
  protocolLabel,
  serverEndpoint,
  transportLabel,
  type Server,
} from "../lib/api";

export function Servers() {
  const { url } = useParams<{ url: string }>();
  const navigate = useNavigate();
  const subs = useAppStore((s) => s.subscriptions);
  const activeId = useAppStore((s) => s.activeServerId);
  const setActive = useAppStore((s) => s.setActiveServer);

  const decoded = url ? decodeURIComponent(url) : "";
  const sub = subs.find((s) => s.url === decoded);

  if (!sub) {
    return (
      <div className="glass rounded-2xl h-full p-10 overflow-auto">
        <div className="max-w-3xl mx-auto text-center">
          <div className="text-sm text-[var(--color-text-dim)] mb-4">
            Подписка не найдена.
          </div>
          <Link
            to="/subscriptions"
            className="text-sm text-[var(--color-accent-bright)] hover:underline"
          >
            ← К подпискам
          </Link>
        </div>
      </div>
    );
  }

  const onSelect = async (server: Server) => {
    try {
      await setActive(activeId === server.id ? null : server.id);
    } catch (e) {
      alert(String(e));
    }
  };

  return (
    <div className="glass rounded-2xl h-full p-10 overflow-auto">
      <div className="max-w-3xl mx-auto">
        <div className="mb-6">
          <button
            onClick={() => navigate(-1)}
            className="text-xs text-[var(--color-text-dim)] hover:text-[var(--color-text)] mb-2"
          >
            ← Подписки
          </button>
          <h1 className="text-2xl font-semibold mb-1">
            {sub.name ?? "Подписка"}
          </h1>
          <p className="text-sm text-[var(--color-text-dim)] font-mono truncate">
            {sub.url}
          </p>
        </div>

        <div className="text-xs uppercase tracking-wider text-[var(--color-text-faint)] mb-3">
          {sub.servers.length} серверов
        </div>

        <div className="space-y-2">
          {sub.servers.map((server) => (
            <ServerRow
              key={server.id}
              server={server}
              active={activeId === server.id}
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
  active,
  onSelect,
}: {
  server: Server;
  active: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      onClick={onSelect}
      className={[
        "glass rounded-xl p-4 w-full text-left flex items-center gap-4 transition-all",
        active
          ? "ring-2 ring-[var(--color-accent)] shadow-[0_0_30px_var(--color-glow-accent)]"
          : "hover:bg-[var(--color-glass-bg-strong)]",
      ].join(" ")}
    >
      <div
        className={[
          "w-2.5 h-2.5 rounded-full shrink-0",
          active ? "bg-[var(--color-status-connected)]" : "bg-[var(--color-text-faint)]",
        ].join(" ")}
      />
      <div className="min-w-0 flex-1">
        <div className="font-medium truncate">{server.name}</div>
        <div className="text-xs text-[var(--color-text-faint)] font-mono truncate">
          {serverEndpoint(server.protocol)}
        </div>
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
