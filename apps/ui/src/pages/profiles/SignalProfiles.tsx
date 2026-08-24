import { useEffect, useState, type ReactNode } from "react";
import {
  protocolLabel,
  serverListDescription,
  transportLabel,
  type Server,
  type Subscription,
} from "../../lib/api";
import { type Messages } from "../../lib/i18n";
import { SignalProfileCard } from "./SignalProfileCard";
import { serverDisplayLabel, type ServerUiOverrides } from "../../lib/serverUiOverrides";
import { DotsIcon, StarIcon } from "../home/SignalServerRail";

/**
 * Профили в стиле Signal: подписка — одна карточка, где собраны остаток
 * трафика, срок, описание провайдера, ссылки и всё управление (обновить,
 * пинг, порядок, настройки, удаление). Серверы всех подписок — одной
 * таблицей ниже, где протокол, транспорт и пинг стоят в своих колонках.
 *
 * Клик по карточке фильтрует таблицу по этой подписке, поэтому список
 * серверов не дублируется внутри карточек.
 */

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
  const needle = query.trim().toLowerCase();
  // Таблица нужна, только пока хоть одна показанная подписка развёрнута:
  // иначе под свёрнутыми карточками висела пустая шапка с «серверов нет».
  const expandedInScope = subs.some((sub) => !collapsedUrls.has(sub.url));
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
          />
        ))}
      </div>

      {expandedInScope && (
      <div className="signal-table-wrap">
        <table className="signal-table">
          <thead>
            <tr>
              <th className="signal-table-name">{m.signal.columnServer}</th>
              <th>{m.signal.columnProtocol}</th>
              <th>{m.signal.columnTransport}</th>
              <th>{m.signal.columnPing}</th>
              <th className="signal-table-action">{m.signal.columnAction}</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td colSpan={5} className="signal-table-empty">
                  {m.profiles.emptyTitle}
                </td>
              </tr>
            )}
            {rows.map(({ sub, server }) => {
              const isActive = server.id === activeId;
              const isConnecting = server.id === connectingId;
              const ping = pingByServer[server.id];
              const description = serverListDescription(server, sub.servers);
              return (
                <tr
                  key={`${sub.url}:${server.id}`}
                  className={isActive ? "is-active" : isConnecting ? "is-connecting" : ""}
                >
                  <td className="signal-table-name">
                    <button
                      type="button"
                      className={`signal-star${favorites.has(server.id) ? " is-on" : ""}`}
                      onClick={() => onToggleFavorite(server.id)}
                      title={m.profiles.favorite}
                      aria-pressed={favorites.has(server.id)}
                    >
                      <StarIcon filled={favorites.has(server.id)} />
                    </button>
                    <span className="signal-table-copy">
                      <span className="signal-table-title">{serverDisplayLabel(server, serverOverrides)}</span>
                      {description && <span className="signal-table-description">{description}</span>}
                    </span>
                  </td>
                  <td>
                    <span className="signal-tag">{protocolLabel(server.protocol)}</span>
                  </td>
                  <td className="signal-num">{transportLabel(server.protocol) || "JSON"}</td>
                  <td className="signal-num">{ping != null ? `${ping} ms` : "—"}</td>
                  <td className="signal-table-action">
                    <span className="signal-row-actions">
                      <button
                        type="button"
                        className={`signal-chip${isActive ? " is-on" : ""}`}
                        onClick={() => onPickServer(sub, server)}
                      >
                        {isActive ? m.signal.active : isConnecting ? m.home.connecting : m.home.connect}
                      </button>
                      <SignalRowMenu
                        labels={m}
                        onPing={() => onPingServer(server.id)}
                        onRename={() => onRenameServer(server.id)}
                        onHide={() => onHideServer(server.id)}
                      />
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      )}
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
  // Таблица прокручивается, поэтому меню рисуется фиксированно от кнопки —
  // иначе его обрезает контейнер со скроллом.
  const [anchor, setAnchor] = useState<{ top: number; right: number } | null>(null);
  const pick = (action: () => void) => () => {
    setAnchor(null);
    action();
  };

  useEffect(() => {
    if (!anchor) return;
    const close = () => setAnchor(null);
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);
    return () => {
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [anchor]);

  return (
    <>
      <button
        type="button"
        className="signal-icon-btn"
        onClick={(event) => {
          if (anchor) {
            setAnchor(null);
            return;
          }
          const rect = event.currentTarget.getBoundingClientRect();
          setAnchor({ top: rect.bottom + 6, right: window.innerWidth - rect.right });
        }}
        aria-expanded={anchor != null}
        title={m.profiles.serverMenu}
        aria-label={m.profiles.serverMenu}
      >
        <DotsIcon />
      </button>
      {anchor && (
        <>
          <span className="signal-menu-scrim" onClick={() => setAnchor(null)} />
          <span className="signal-menu signal-menu--floating" style={{ top: anchor.top, right: anchor.right }}>
            <button type="button" onClick={pick(onPing)}>
              {m.profiles.testLatency}
            </button>
            <button type="button" onClick={pick(onRename)}>
              {m.profiles.renameServer}
            </button>
            <button type="button" className="is-danger" onClick={pick(onHide)}>
              {m.profiles.deleteServer}
            </button>
          </span>
        </>
      )}
    </>
  );
}
