import { HomeMetaIcon } from "../../components/HomeMetaIcon";
import { useId, useState, type ReactNode } from "react";
import { formatBytes, formatSubscriptionTerm, type Subscription } from "../../lib/api";
import { expireLabels, type Messages } from "../../lib/i18n";
import { useCachedSubscriptionLogo } from "../../lib/subscriptionLogo";
import { useAppStore } from "../../store";
import { ChevronIcon, DotsIcon, PingIcon, RefreshIcon } from "../home/SignalServerRail";
import { ActionMenu, InfoIcon } from "../../components/Universal";
import { SubscriptionInfo } from "../../components/SubscriptionInfo";
import { ProviderAnnouncement } from "./ProviderAnnouncement";
import { CoreSubscriptionControl } from "../../components/CoreSubscriptionControl";
import { useCoreStore } from "../../coreStore";

export interface SignalProfileCardProps {
  children?: ReactNode;
  labels: Messages;
  sub: Subscription;
  serverCount: number;
  onRefresh: () => void;
  onPing: () => void;
  onSettings?: () => void;
  onDelete?: () => void;
  onMoveUp?: () => void;
  onMoveDown?: () => void;
  canMoveUp?: boolean;
  canMoveDown?: boolean;
  refreshing: boolean;
  pinging: boolean;
  collapsed: boolean;
  onToggleCollapsed: () => void;
  updatedLabel: string;
  supportUrl?: string;
  siteUrl?: string | null;
}

export function SignalProfileCard({ labels: m, sub, serverCount, onRefresh, onPing, onSettings, onDelete,
  onMoveUp, onMoveDown, canMoveUp, canMoveDown, refreshing, pinging, collapsed, onToggleCollapsed,
  updatedLabel, supportUrl, siteUrl, children }: SignalProfileCardProps) {
  const [infoOpen, setInfoOpen] = useState(false);
  const mihomo = useCoreStore(state => state.data?.preferred_core === 'mihomo');
  const id = useId();
  const showLogo = useAppStore(state => state.preferences.show_subscription_logo);
  const logo = useCachedSubscriptionLogo(sub, showLogo);
  const name = sub.name?.trim() || m.common.subscription;
  const used = (sub.info?.upload ?? 0) + (sub.info?.download ?? 0);
  const total = sub.info?.total;
  return <article className={`signal-profile universal-subscription${collapsed ? " is-collapsed" : ""}`}
    onClick={event => {
      const target = event.target;
      // React portals bubble through this component too. Only the tile's own
      // non-interactive content discloses; disabled buttons remain excluded.
      if (event.defaultPrevented || !(target instanceof Element) || !event.currentTarget.contains(target)) return;
      if (target.closest("button,a,input,select,textarea,[role='button'],[role='menu'],[role='dialog'],[contenteditable],[data-no-toggle]")) return;
      onToggleCollapsed();
    }}>
    <header className="signal-profile-head">
      <button type="button" className="universal-subscription-toggle" onClick={onToggleCollapsed} aria-expanded={!collapsed} aria-controls={id}>
        <span className="signal-sub-logo">{logo ? <img src={logo} alt=""/> : name.slice(0, 2).toUpperCase()}</span>
        <span className="signal-profile-copy"><span className="signal-profile-name">{name}</span><span className="signal-sub-meta">{serverCount} {m.common.locale.startsWith("ru") ? (serverCount % 100 >= 11 && serverCount % 100 <= 14 ? "серверов" : serverCount % 10 === 1 ? "сервер" : serverCount % 10 >= 2 && serverCount % 10 <= 4 ? "сервера" : "серверов") : serverCount === 1 ? "server" : "servers"}</span></span>
        <span className={`signal-collapse-btn${collapsed ? " is-collapsed" : ""}`}><ChevronIcon direction="right"/></span>
      </button>
      <button type="button" className="signal-icon-btn" aria-label={`${m.common.description}: ${name}`} title={m.common.description} onClick={() => setInfoOpen(true)}><InfoIcon/></button>
      {onSettings && onDelete && onMoveUp && onMoveDown && <ActionMenu label={m.profiles.subscriptionMenu} actions={[
        { label: m.profiles.subscriptionSettings, onClick: onSettings },
        { label: m.profiles.moveUp, onClick: onMoveUp, disabled: !canMoveUp },
        { label: m.profiles.moveDown, onClick: onMoveDown, disabled: !canMoveDown },
        { label: m.profiles.delete, onClick: onDelete, danger: true },
      ]}><DotsIcon/></ActionMenu>}
    </header>
    <div className="universal-subscription-summary"><span><HomeMetaIcon kind="traffic" />{sub.info ? total ? `${formatBytes(Math.max(0,total-used))} / ${formatBytes(total)}` : "∞" : "—"}</span><span><HomeMetaIcon kind="calendar" />{sub.info ? formatSubscriptionTerm(sub.info, expireLabels(m)) : "—"}</span></div>
    {total ? <div className="signal-quota" aria-label={m.profiles.traffic}><i style={{width: `${Math.min(100, used / total * 100)}%`}}/></div> : null}
    <ProviderAnnouncement description={sub.meta?.description} labels={m}/>
    <footer className="universal-subscription-footer">
      {!mihomo && <button type="button" className="signal-btn signal-btn--sm signal-btn--ghost" onClick={onPing} title={pinging ? m.common.cancel : m.profiles.testLatency} aria-label={pinging ? m.common.cancel : m.profiles.testLatency}><PingIcon/>{pinging ? m.common.cancel : m.signal.columnPing}</button>}
      <button type="button" className="signal-btn signal-btn--sm signal-btn--ghost" onClick={onRefresh} disabled={refreshing} title={m.home.refreshSubscription} aria-label={m.home.refreshSubscription}><RefreshIcon/>{m.common.refresh}</button>
      <span>{updatedLabel}</span>
    </footer>
    <div id={id} hidden={collapsed} className="universal-subscription-servers" data-no-toggle>{mihomo ? <CoreSubscriptionControl sub={sub}/> : children}</div>
    {infoOpen && <SubscriptionInfo sub={sub} labels={m} onClose={() => setInfoOpen(false)} supportUrl={supportUrl} siteUrl={siteUrl}/>}
  </article>;
}
