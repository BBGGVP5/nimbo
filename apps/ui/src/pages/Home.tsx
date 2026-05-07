import { Link } from "react-router-dom";
import { useAppStore } from "../store";
import {
  protocolLabel,
  serverEndpoint,
  type Server,
} from "../lib/api";

export function Home() {
  const subs = useAppStore((s) => s.subscriptions);
  const activeId = useAppStore((s) => s.activeServerId);
  const status = useAppStore((s) => s.status);

  const activeServer = activeId
    ? subs.flatMap((s) => s.servers).find((srv) => srv.id === activeId) ?? null
    : null;

  const serverCount = status?.server_count ?? 0;

  return (
    <div className="glass rounded-2xl h-full p-10 overflow-auto">
      <div className="max-w-3xl mx-auto">
        <div className="text-center mb-10">
          <h1 className="text-2xl font-semibold mb-1">Главная</h1>
          <p className="text-sm text-[var(--color-text-dim)]">
            Текущее подключение
          </p>
        </div>

        <div className="flex flex-col items-center mb-12">
          <PowerButton hasServer={activeServer !== null} />
          <div className="mt-6 text-center">
            <div className="text-sm text-[var(--color-text-faint)] mb-1">
              {activeServer
                ? "Сервер выбран — нужен сервис для подключения"
                : serverCount > 0
                  ? "Выберите сервер на странице подписок"
                  : "Добавьте подписку, чтобы начать"}
            </div>
            <div className="text-[11px] text-[var(--color-text-faint)] font-mono">
              VPN-туннель будет доступен после Этапа 5 (xray + TUN)
            </div>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-3 mb-6">
          <Stat
            label="Активный сервер"
            value={activeServer?.name ?? "—"}
            sub={activeServer ? serverEndpoint(activeServer.protocol) : undefined}
          />
          <Stat
            label="Подписки"
            value={String(subs.length)}
            sub={subs.length > 0 ? `${serverCount} серверов` : "нет"}
          />
          <Stat
            label="Время"
            value="00:00:00"
            mono
            sub="не подключено"
          />
        </div>

        {activeServer && (
          <ActiveServerCard server={activeServer} />
        )}
      </div>
    </div>
  );
}

function ActiveServerCard({ server }: { server: Server }) {
  return (
    <section className="glass rounded-2xl p-5 mb-3">
      <div className="text-[10px] uppercase tracking-wider text-[var(--color-text-faint)] mb-2">
        Активный сервер
      </div>
      <div className="flex items-center justify-between gap-4">
        <div className="min-w-0">
          <div className="font-medium truncate">{server.name}</div>
          <div className="text-xs text-[var(--color-text-faint)] font-mono truncate">
            {serverEndpoint(server.protocol)}
          </div>
        </div>
        <div className="text-xs px-2.5 py-1 rounded-md bg-[var(--color-glass-bg-strong)] text-[var(--color-accent-bright)] shrink-0">
          {protocolLabel(server.protocol)}
        </div>
      </div>
      <Link
        to="/subscriptions"
        className="block mt-3 text-xs text-[var(--color-text-dim)] hover:text-[var(--color-text)]"
      >
        Сменить сервер →
      </Link>
    </section>
  );
}

function PowerButton({ hasServer }: { hasServer: boolean }) {
  const tone = hasServer ? "ready" : "idle";

  const styles = {
    ready: {
      bg: "radial-gradient(circle at 50% 35%, rgba(124,93,250,0.55) 0%, rgba(124,93,250,0.30) 35%, rgba(26,26,46,0.85) 70%, rgba(30,30,58,1) 100%)",
      shadow:
        "0 0 60px rgba(124,93,250,0.45), inset 0 1px 0 rgba(255,255,255,0.10), inset 0 -1px 0 rgba(0,0,0,0.30)",
      stroke: "rgba(155,141,245,0.95)",
    },
    idle: {
      bg: "radial-gradient(circle at 50% 35%, rgba(107,114,128,0.45) 0%, rgba(107,114,128,0.20) 35%, rgba(26,26,46,0.85) 70%, rgba(30,30,58,1) 100%)",
      shadow:
        "0 0 60px rgba(107,114,128,0.30), inset 0 1px 0 rgba(255,255,255,0.10), inset 0 -1px 0 rgba(0,0,0,0.30)",
      stroke: "rgba(107,114,128,0.95)",
    },
  }[tone];

  return (
    <button
      type="button"
      disabled
      title="Будет доступно после Этапа 5"
      className="
        relative w-44 h-44 rounded-full
        flex items-center justify-center
        transition-transform duration-300
        disabled:opacity-90 disabled:cursor-not-allowed
      "
      style={{
        background: styles.bg,
        boxShadow: styles.shadow,
        border: "2px solid rgba(255,255,255,0.10)",
      }}
    >
      <svg
        viewBox="0 0 24 24"
        className="w-16 h-16 relative z-10"
        fill="none"
        stroke={styles.stroke}
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M12 2v10" />
        <path d="M5.6 6.6a9 9 0 1 0 12.8 0" />
      </svg>
    </button>
  );
}

function Stat({
  label,
  value,
  sub,
  mono = false,
}: {
  label: string;
  value: string;
  sub?: string;
  mono?: boolean;
}) {
  return (
    <div className="glass rounded-xl px-4 py-3 min-w-0">
      <div className="text-[10px] uppercase tracking-wider text-[var(--color-text-faint)] mb-1">
        {label}
      </div>
      <div className={`${mono ? "font-mono " : ""}text-sm truncate`}>{value}</div>
      {sub && (
        <div className="text-[10px] text-[var(--color-text-faint)] font-mono mt-0.5 truncate">
          {sub}
        </div>
      )}
    </div>
  );
}
