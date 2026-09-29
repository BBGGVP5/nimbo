import { CountryFlag } from "../../components/CountryFlag";
import { AutoFastestLine } from "../../components/AutoFastestLine";
import { useAppStore } from "../../store";
import { LatencyDisplay } from "../../components/LatencyDisplay";
import { useState, type ReactNode } from "react";
import {
  protocolLabel,
  transportLabel,
  type Server,
  type Subscription,
} from "../../lib/api";
import { type Messages } from "../../lib/i18n";
import { SignalProfileCard } from "./SignalProfileCard";
import { serverDisplayLabel, type ServerUiOverrides } from "../../lib/serverUiOverrides";
import { ActionMenu } from "../../components/Universal";
import { DotsIcon, StarIcon } from "../home/SignalServerRail";

export interface SignalProfilesProps {
  labels: Messages;
  subs: Subscription[];
  activeId: string | null;
  connectingId: string | null;
  pingByServer: Record<string, number | undefined>;
  favorites: Set<string>;
  onPickServer: (sub: Subscription, server: Server) => void;
  onToggleFavorite: (id: string) => void;
  hiddenServerIds: Set<string>;
  serverOverrides: ServerUiOverrides;
  onRenameServer: (serverId: string) => void;
  onHideServer: (serverId: string) => void;
  onPingServer: (serverId: string) => void;
  query: string;
  head: ReactNode;
  onRefreshSubscription: (url: string) => void;
  onPingSubscription: (url: string) => void;
  onOpenSettings: (url: string) => void;
  onDeleteSubscription: (url: string) => void;
  onMoveSubscription: (url: string, direction: -1 | 1) => void;
  refreshingUrl: string | null;
  pingingUrl: string | null;
  updatedLabel: (sub: Subscription) => string;
  supportUrl: (sub: Subscription) => string;
  siteUrl: (sub: Subscription) => string | null;
  order: string[];
}

export function SignalProfiles({
  labels: m,
  subs,
  activeId,
  connectingId,
  pingByServer,
  favorites,
  onPickServer,
  onToggleFavorite,
  hiddenServerIds,
  serverOverrides,
  onRenameServer,
  onHideServer,
  onPingServer,
  query,
  head,
  onRefreshSubscription,
  onPingSubscription,
  onOpenSettings,
  onDeleteSubscription,
  onMoveSubscription,
  refreshingUrl,
  pingingUrl,
  updatedLabel,
  supportUrl,
  siteUrl,
  order,
}: SignalProfilesProps) {
  const [collapsedUrls, setCollapsedUrls] = useState<Set<string>>(() => new Set());
  const autoSubscription = useAppStore(state => state.status?.auto_subscription_url);
  const needle = query.trim().toLowerCase();
  const rows = subs
    .filter((sub) => !collapsedUrls.has(sub.url))
    .flatMap((sub) =>
      sub.servers
        .filter((server) => !hiddenServerIds.has(server.id))
        .filter((server) => {
          if (!needle) return true;
          const label = `${serverDisplayLabel(server, serverOverrides)} ${protocolLabel(server.protocol)}`.toLowerCase();
          return label.includes(needle);
        })
        .map((server) => ({ sub, server })),
    );

  return (
    <div className="signal-profiles">
      {head}

      <div className="signal-profiles-list">
        {subs.map((sub) => (
          <SignalProfileCard
            key={sub.url}
            labels={m}
            sub={sub}
            serverCount={sub.servers.length}
            onRefresh={() => onRefreshSubscription(sub.url)}
            onPing={() => onPingSubscription(sub.url)}
            onSettings={() => onOpenSettings(sub.url)}
            onDelete={() => onDeleteSubscription(sub.url)}
            onMoveUp={() => onMoveSubscription(sub.url, -1)}
            onMoveDown={() => onMoveSubscription(sub.url, 1)}
            canMoveUp={order.indexOf(sub.url) > 0}
            canMoveDown={order.indexOf(sub.url) < order.length - 1}
            refreshing={refreshingUrl === sub.url}
            pinging={pingingUrl === sub.url}
            collapsed={collapsedUrls.has(sub.url)}
            onToggleCollapsed={() =>
              setCollapsedUrls((current) => {
                const next = new Set(current);
                if (next.has(sub.url)) next.delete(sub.url);
                else next.add(sub.url);
                return next;
              })
            }
            updatedLabel={updatedLabel(sub)}
            supportUrl={supportUrl(sub)}
            siteUrl={siteUrl(sub)}
          >
            <div className="parity-server-list">
              {!needle && <AutoFastestLine servers={sub.servers.filter(server => !hiddenServerIds.has(server.id))} subscriptionUrl={sub.url} autoSelected={autoSubscription === sub.url} activeId={activeId} pings={Object.fromEntries(Object.entries(pingByServer).filter((entry): entry is [string, number] => entry[1] !== undefined))} displayName={server => serverDisplayLabel(server, serverOverrides)}/>}
              {!rows.some(row => row.sub.url === sub.url) && <p className="parity-list-empty">{m.profiles.emptyTitle}</p>}
              {rows.filter(row => row.sub.url === sub.url).map(({ server }) => {
                const active = server.id === activeId;
                const connecting = server.id === connectingId;
                return <div key={server.id} className={`parity-server-row${active ? " is-active" : ""}`}>
                  <button className="parity-server-select" type="button" onClick={() => onPickServer(sub, server)} aria-pressed={active} disabled={connecting}>
                    <span className="parity-server-flag"><CountryFlag serverName={server.name} fallback={<span aria-hidden="true">◎</span>}/></span>
                    <span className="parity-server-copy"><strong>{serverDisplayLabel(server, serverOverrides)}</strong><small>{protocolLabel(server.protocol)} · {transportLabel(server.protocol) || "JSON"}</small></span>
                    <span className="parity-server-latency">{connecting ? m.home.connecting : <LatencyDisplay value={pingByServer[server.id]} />}</span>
                    {active && <span aria-label={m.signal.active}>✓</span>}
                  </button>
                  <button type="button" className={`signal-star${favorites.has(server.id) ? " is-on" : ""}`} onClick={() => onToggleFavorite(server.id)} aria-label={m.profiles.favorite} aria-pressed={favorites.has(server.id)}><StarIcon filled={favorites.has(server.id)}/></button>
                  <SignalRowMenu labels={m} onPing={() => onPingServer(server.id)} onRename={() => onRenameServer(server.id)} onHide={() => onHideServer(server.id)}/>
                </div>;
              })}
            </div>
          </SignalProfileCard>
        ))}
      </div>


    </div>
  );
}

/** Меню строки сервера: пинг, переименование и скрытие — как в старом списке. */
function SignalRowMenu({
  labels: m,
  onPing,
  onRename,
  onHide,
}: {
  labels: Messages;
  onPing: () => void;
  onRename: () => void;
  onHide: () => void;
}) {
  return <ActionMenu label={m.profiles.serverMenu} actions={[
    { label: m.profiles.testLatency, onClick: onPing },
    { label: m.profiles.renameServer, onClick: onRename },
    { label: m.profiles.deleteServer, onClick: onHide, danger: true },
  ]}><DotsIcon/></ActionMenu>;
}
