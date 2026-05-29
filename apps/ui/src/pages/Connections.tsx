import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { notifyError, notifyInfo } from "../lib/notify";
import {
  api,
  serverDisplayName,
  type ActiveConnection,
  type ActiveConnectionRoute,
  type RoutingProfile,
} from "../lib/api";
import { CountryFlag } from "../components/CountryFlag";
import { useMessages, type Messages } from "../lib/i18n";
import { useAppStore } from "../store";
import { BackButton } from "../components/BackButton";

type FirewallAction = "block" | "direct" | "proxy";
type RuleKind = "domain" | "ip";
type FirewallRule = {
  target: string;
  action: FirewallAction;
  kind: RuleKind;
};
type ConnectionsTab = "live" | "rules";

export function Connections() {
  const m = useMessages();
  const status = useAppStore((s) => s.status);
  const activeServerId = useAppStore((s) => s.activeServerId);
  const connectServer = useAppStore((s) => s.connectServer);
  const [profile, setProfile] = useState<RoutingProfile | null>(null);
  const [activeProfileName, setActiveProfileName] = useState("");
  const [target, setTarget] = useState("");
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState<ConnectionsTab>("live");
  const [connections, setConnections] = useState<ActiveConnection[]>([]);
  const [connectionsBusy, setConnectionsBusy] = useState(false);
  const [connectionsQuery, setConnectionsQuery] = useState("");
  const [connectionsUpdatedAt, setConnectionsUpdatedAt] = useState<Date | null>(null);
  const connectedRef = useRef(false);
  const loadingConnectionsRef = useRef(false);
  const connectionsGenerationRef = useRef(0);
  const connectionsSignatureRef = useRef("");

  const loadProfile = async () => {
    const list = await api.listRoutingProfiles();
    const active = await api.getRoutingProfile(list.active);
    setProfile(active);
    setActiveProfileName(active.name || list.active);
  };

  const loadConnections = useCallback(async (silent = false) => {
    if (!connectedRef.current) {
      connectionsSignatureRef.current = "";
      setConnections([]);
      setConnectionsUpdatedAt(null);
      setConnectionsBusy(false);
      return;
    }
    if (loadingConnectionsRef.current) return;
    loadingConnectionsRef.current = true;
    const generation = connectionsGenerationRef.current;
    if (!silent) setConnectionsBusy(true);
    try {
      const list = await api.listActiveConnections();
      if (!connectedRef.current || generation !== connectionsGenerationRef.current) return;
      const signature = activeConnectionsSignature(list);
      if (signature !== connectionsSignatureRef.current) {
        connectionsSignatureRef.current = signature;
        setConnections(list);
      }
      setConnectionsUpdatedAt(new Date());
    } catch (error) {
      if (!silent) notifyError(String(error));
    } finally {
      loadingConnectionsRef.current = false;
      if (!silent) setConnectionsBusy(false);
    }
  }, []);

  useEffect(() => {
    void loadProfile().catch((error) => notifyError(String(error)));
  }, [loadConnections]);

  useEffect(() => {
    const connected = status?.state === "connected";
    connectionsGenerationRef.current += 1;
    connectedRef.current = connected;
    if (connected && tab === "live") {
      void loadConnections();
    } else if (!connected) {
      connectionsSignatureRef.current = "";
      setConnections([]);
      setConnectionsUpdatedAt(null);
      setConnectionsBusy(false);
    }
  }, [loadConnections, status?.state, tab]);

  useEffect(() => {
    if (tab !== "live" || status?.state !== "connected") return;
    const id = window.setInterval(() => void loadConnections(true), 1500);
    return () => window.clearInterval(id);
  }, [loadConnections, status?.state, tab]);

  const rules = useMemo(() => {
    if (!profile) return [];
    return [
      ...profile.block_domains.map((item) => toRule(item, "block", "domain")),
      ...profile.block_ips.map((item) => toRule(item, "block", "ip")),
      ...profile.proxy_domains.map((item) => toRule(item, "proxy", "domain")),
      ...profile.proxy_ips.map((item) => toRule(item, "proxy", "ip")),
      ...profile.direct_domains.map((item) => toRule(item, "direct", "domain")),
      ...profile.direct_ips.map((item) => toRule(item, "direct", "ip")),
    ].filter(Boolean) as FirewallRule[];
  }, [profile]);

  const filteredConnections = useMemo(() => {
    const query = connectionsQuery.trim().toLowerCase();
    if (!query) return connections;
    return connections.filter((connection) =>
      [
        connection.destination,
        connection.source,
        connection.remote_address,
        connection.process,
        connection.process_path ?? "",
        connection.rule,
        connection.server_name ?? "",
        connection.server_protocol ?? "",
        connection.state,
      ]
        .join(" ")
        .toLowerCase()
        .includes(query),
    );
  }, [connections, connectionsQuery]);

  const applyTarget = async (action: FirewallAction | "remove", raw = target) => {
    if (!profile || busy) return;
    const parsed = normalizeTarget(raw);
    if (!parsed) {
      notifyError(m.connectionsPage.invalidTarget);
      return;
    }

    const next = removeTargetFromProfile(profile, parsed.target);
    if (action !== "remove") {
      addTargetToProfile(next, parsed.target, parsed.kind, action);
    }

    setBusy(true);
    try {
      const saved = await api.updateRoutingProfile(next);
      setProfile(saved);
      setTarget("");
      notifyInfo(action === "remove" ? m.connectionsPage.removed : m.connectionsPage.saved);
      if (status?.state === "connected" && activeServerId) {
        notifyInfo(m.connectionsPage.reapplying);
        await connectServer(activeServerId);
        await loadConnections(true);
      }
    } catch (error) {
      notifyError(String(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page-view connections-page">
      <BackButton />
      <div className="connections-header">
        <div>
          <h1 className="page-title">{m.connectionsPage.title}</h1>
          <div className="connections-active-profile">
            {m.connectionsPage.activeProfile}: {activeProfileName || "..."}
          </div>
        </div>
        <div className="connections-tabs" role="tablist" aria-label={m.connectionsPage.title}>
          <button
            type="button"
            role="tab"
            aria-selected={tab === "live"}
            className={["connections-tab", tab === "live" ? "connections-tab-active" : ""].join(" ")}
            onClick={() => setTab("live")}
          >
            {m.connectionsPage.liveTab}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === "rules"}
            className={["connections-tab", tab === "rules" ? "connections-tab-active" : ""].join(" ")}
            onClick={() => setTab("rules")}
          >
            {m.connectionsPage.rulesTab}
          </button>
        </div>
      </div>

      {tab === "live" ? (
        <section className="connections-section">
          <div className="connections-section-header">
            <div>
              <h2>{m.connectionsPage.liveTitle}</h2>
              <p>
                {filteredConnections.length} {m.connectionsPage.connectionCount}
                {connectionsUpdatedAt ? ` · ${connectionsUpdatedAt.toLocaleTimeString()}` : ""}
              </p>
            </div>
            <button
              type="button"
              className="connections-icon-button"
              title={m.connectionsPage.refreshConnections}
              aria-label={m.connectionsPage.refreshConnections}
              disabled={connectionsBusy || status?.state !== "connected"}
              onClick={() => void loadConnections()}
            >
              <RefreshIcon spinning={connectionsBusy} />
            </button>
          </div>

          <div className="connections-live-toolbar">
            <input
              value={connectionsQuery}
              onChange={(event) => setConnectionsQuery(event.target.value)}
              placeholder={m.connectionsPage.liveSearch}
              className="dark-input connections-input"
            />
          </div>

          <div className="connections-table connections-live-table">
            <div className="connections-live-head">
              <span>{m.connectionsPage.destination}</span>
              <span>{m.connectionsPage.process}</span>
              <span>{m.connectionsPage.route}</span>
              <span>{m.connectionsPage.server}</span>
              <span>{m.connectionsPage.source}</span>
              <span>{m.connectionsPage.state}</span>
            </div>
            {filteredConnections.length === 0 ? (
              <div className="connections-empty">
                {connectionsQuery.trim()
                  ? m.connectionsPage.liveEmptyFiltered
                  : m.connectionsPage.liveEmpty}
              </div>
            ) : (
              filteredConnections.map((connection) => (
                <div key={connection.id} className="connections-live-row">
                  <div className="connections-endpoint">
                    <span className="connections-target">{connection.destination}</span>
                    <span>{connection.protocol} · {connection.remote_port}</span>
                  </div>
                  <div className="connections-process">
                    <span title={processTitle(connection)}>{displayProcessName(connection)}</span>
                    <small title={processTitle(connection)}>{processDetail(connection)}</small>
                  </div>
                  <span className={`connections-badge connections-badge-${routeBadgeClass(connection.route)}`}>
                    {routeLabel(connection.route, m)}
                  </span>
                  <div className="connections-server">
                    <span className="connections-server-name">
                      {connection.server_name ? (
                        <CountryFlag
                          serverName={connection.server_name}
                          fallback={null}
                          className="country-flag-xs"
                        />
                      ) : null}
                      <span>{connection.server_name ? serverDisplayName(connection.server_name) : serverFallback(connection.route, m)}</span>
                    </span>
                    <small>{connection.server_protocol || connection.rule}</small>
                  </div>
                  <span className="connections-source-address">{connection.source}</span>
                  <span className="connections-kind">{connection.state}</span>
                </div>
              ))
            )}
          </div>
        </section>
      ) : (
        <section className="connections-section">
          <div className="connections-section-header">
            <div>
              <h2>{m.connectionsPage.rulesTitle}</h2>
              <p>{rules.length} {m.connectionsPage.ruleCount}</p>
            </div>
          </div>

          <div className="connections-toolbar">
            <input
              value={target}
              onChange={(event) => setTarget(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") void applyTarget("block");
              }}
              placeholder={m.connectionsPage.targetPlaceholder}
              className="dark-input connections-input"
            />
            <button type="button" className="connections-action connections-action-block" disabled={busy} onClick={() => void applyTarget("block")}>
              <BlockIcon />
              <span>{m.connectionsPage.block}</span>
            </button>
            <button type="button" className="connections-action" disabled={busy} onClick={() => void applyTarget("direct")}>
              <DirectIcon />
              <span>{m.connectionsPage.direct}</span>
            </button>
            <button type="button" className="connections-action" disabled={busy} onClick={() => void applyTarget("proxy")}>
              <ProxyIcon />
              <span>{m.connectionsPage.proxy}</span>
            </button>
          </div>

          <div className="connections-table">
            <div className="connections-table-head">
              <span>{m.connectionsPage.target}</span>
              <span>{m.connectionsPage.action}</span>
              <span>{m.connectionsPage.source}</span>
              <span />
            </div>
            {rules.length === 0 ? (
              <div className="connections-empty">{m.connectionsPage.empty}</div>
            ) : (
              rules.map((rule) => (
                <div key={`${rule.action}-${rule.kind}-${rule.target}`} className="connections-row">
                  <span className="connections-target">{displayTarget(rule.target)}</span>
                  <span className={`connections-badge connections-badge-${rule.action}`}>
                    {actionLabel(rule.action, m)}
                  </span>
                  <span className="connections-kind">
                    {rule.kind === "domain" ? m.connectionsPage.domain : m.connectionsPage.ip}
                  </span>
                  <button
                    type="button"
                    className="connections-remove"
                    title={m.connectionsPage.remove}
                    aria-label={m.connectionsPage.remove}
                    disabled={busy}
                    onClick={() => void applyTarget("remove", rule.target)}
                  >
                    <TrashIcon />
                  </button>
                </div>
              ))
            )}
          </div>
        </section>
      )}
    </div>
  );
}

function toRule(target: string, action: FirewallAction, kind: RuleKind): FirewallRule | null {
  const value = target.trim();
  return value ? { target: value, action, kind } : null;
}

function actionLabel(action: FirewallAction, m: Messages): string {
  if (action === "block") return m.connectionsPage.actionBlock;
  if (action === "direct") return m.connectionsPage.actionDirect;
  return m.connectionsPage.actionProxy;
}

function routeLabel(route: ActiveConnectionRoute, m: Messages): string {
  if (route === "proxy") return m.connectionsPage.routeProxy;
  if (route === "direct") return m.connectionsPage.routeDirect;
  if (route === "block") return m.connectionsPage.routeBlock;
  return m.connectionsPage.routeUnknown;
}

function routeBadgeClass(route: ActiveConnectionRoute): string {
  if (route === "proxy") return "proxy";
  if (route === "direct") return "direct";
  if (route === "block") return "block";
  return "unknown";
}

function serverFallback(route: ActiveConnectionRoute, m: Messages): string {
  if (route === "direct") return m.connectionsPage.routeDirect;
  if (route === "block") return m.connectionsPage.routeBlock;
  return m.connectionsPage.noServer;
}

function activeConnectionsSignature(connections: ActiveConnection[]): string {
  return connections
    .map((connection) => [
      connection.id,
      connection.process,
      connection.process_path ?? "",
      connection.route,
      connection.rule,
      connection.state,
      connection.server_name ?? "",
    ].join("|"))
    .join("\n");
}

function displayProcessName(connection: ActiveConnection): string {
  const process = connection.process.trim();
  if (process && process !== "Unknown process") return process;
  return fileNameFromPath(connection.process_path) ?? (process || "Unknown process");
}

function processDetail(connection: ActiveConnection): string {
  const path = connection.process_path?.trim();
  if (path) return compactPath(path);
  return connection.rule;
}

function processTitle(connection: ActiveConnection): string {
  return connection.process_path?.trim() || connection.process || connection.rule;
}

function fileNameFromPath(path?: string | null): string | null {
  const value = path?.trim();
  if (!value) return null;
  const parts = value.split(/[\\/]/).filter(Boolean);
  return parts.length ? parts[parts.length - 1] : null;
}

function compactPath(path: string): string {
  const parts = path.split(/[\\/]/).filter(Boolean);
  if (parts.length <= 2) return path;
  return `...\\${parts.slice(-2).join("\\")}`;
}

function normalizeTarget(raw: string): { target: string; kind: RuleKind } | null {
  const value = raw.trim();
  if (!value) return null;
  if (isIpMatcher(value)) return { target: value, kind: "ip" };
  if (/^(domain|geosite|regexp|keyword|full):/i.test(value)) {
    return { target: value, kind: "domain" };
  }

  const candidate = /^[a-z][a-z0-9+.-]*:\/\//i.test(value) ? value : `https://${value}`;
  try {
    const url = new URL(candidate);
    const hostname = url.hostname.toLowerCase().replace(/\.$/, "");
    if (!hostname || !hostname.includes(".")) return null;
    return { target: `domain:${hostname}`, kind: "domain" };
  } catch {
    return null;
  }
}

function isIpMatcher(value: string): boolean {
  return /^geoip:/i.test(value)
    || value.includes("/")
    || /^(\d{1,3}\.){3}\d{1,3}$/.test(value)
    || /^[a-f0-9:]+$/i.test(value);
}

function removeTargetFromProfile(profile: RoutingProfile, target: string): RoutingProfile {
  const remove = (items: string[]) => items.filter((item) => item !== target);
  return {
    ...profile,
    block_domains: remove(profile.block_domains),
    block_ips: remove(profile.block_ips),
    proxy_domains: remove(profile.proxy_domains),
    proxy_ips: remove(profile.proxy_ips),
    direct_domains: remove(profile.direct_domains),
    direct_ips: remove(profile.direct_ips),
  };
}

function addTargetToProfile(
  profile: RoutingProfile,
  target: string,
  kind: RuleKind,
  action: FirewallAction,
) {
  const key = `${action}_${kind === "domain" ? "domains" : "ips"}` as
    | "block_domains"
    | "block_ips"
    | "direct_domains"
    | "direct_ips"
    | "proxy_domains"
    | "proxy_ips";
  profile[key] = [...new Set([...profile[key], target])];
}

function displayTarget(target: string): string {
  return target.replace(/^domain:/i, "");
}

function BlockIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="8" />
      <path d="m7 17 10-10" />
    </svg>
  );
}

function DirectIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 12h14" />
      <path d="m13 7 5 5-5 5" />
    </svg>
  );
}

function ProxyIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M6 17a6 6 0 0 1 6-6h7" />
      <path d="m15 7 4 4-4 4" />
      <path d="M6 7h4" />
    </svg>
  );
}

function RefreshIcon({ spinning }: { spinning: boolean }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={spinning ? "connections-spin" : ""}>
      <path d="M20 6v5h-5" />
      <path d="M4 18v-5h5" />
      <path d="M18.5 9A7 7 0 0 0 6.3 6.7L4 9" />
      <path d="M5.5 15A7 7 0 0 0 17.7 17.3L20 15" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 7h16" />
      <path d="M10 11v6M14 11v6" />
      <path d="M6 7l1 14h10l1-14" />
      <path d="M9 7V4h6v3" />
    </svg>
  );
}
