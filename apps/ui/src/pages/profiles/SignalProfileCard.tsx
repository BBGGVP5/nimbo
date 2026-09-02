import { useState } from "react";
import {
  formatBytes,
  formatSubscriptionTerm,
  type Subscription,
} from "../../lib/api";
import { expireLabels, type Messages } from "../../lib/i18n";
import { useCachedSubscriptionLogo } from "../../lib/subscriptionLogo";
import { useAppStore } from "../../store";
import { ArrowIcon, ChevronIcon, DotsIcon, PingIcon, RefreshIcon } from "../home/SignalServerRail";

/**
 * Карточка подписки в стиле Signal.
 *
 * Содержит всё, что раньше жило в старой карточке профиля: остаток трафика,
 * срок, время обновления, описание провайдера, ссылки поддержки и сайта,
 * порядок, пинг, обновление и меню с настройками и удалением. Серверы здесь
 * не дублируются — они живут в общей таблице ниже.
 */

export interface SignalProfileCardProps {
  labels: Messages;
  sub: Subscription;
  serverCount: number;
  onRefresh: () => void;
  onPing: () => void;
  onSettings: () => void;
  onDelete: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
  canMoveUp: boolean;
  canMoveDown: boolean;
  refreshing: boolean;
  pinging: boolean;
  collapsed: boolean;
  onToggleCollapsed: () => void;
  updatedLabel: string;
  supportUrl: string;
  siteUrl: string | null;
}

export function SignalProfileCard({
  labels: m,
  sub,
  serverCount,
  onRefresh,
  onPing,
  onSettings,
  onDelete,
  onMoveUp,
  onMoveDown,
  canMoveUp,
  canMoveDown,
  refreshing,
  pinging,
  collapsed,
  onToggleCollapsed,
  updatedLabel,
  supportUrl,
  siteUrl,
}: SignalProfileCardProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const showSubscriptionLogo = useAppStore((state) => state.preferences.show_subscription_logo);
  const logoSrc = useCachedSubscriptionLogo(sub, showSubscriptionLogo);
  const used = (sub.info?.upload ?? 0) + (sub.info?.download ?? 0);
  const total = sub.info?.total ?? null;
  const ratio = total ? Math.min(1, used / total) : 0;
  const name = sub.name?.trim() || m.common.subscription;
  const description = sub.meta?.description?.trim() || "";
  const visibleDescription = /^описание подписки$/i.test(description) ? "" : description;

  return (
    <article
      className={`signal-profile${collapsed ? " is-collapsed" : ""}`}
      onClick={onToggleCollapsed}
      role="button"
      tabIndex={0}
      aria-expanded={!collapsed}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onToggleCollapsed();
        }
      }}
    >
      <header className="signal-profile-head">
        <button
          type="button"
          className={`signal-collapse-btn${collapsed ? " is-collapsed" : ""}`}
          onClick={(event) => {
            event.stopPropagation();
            onToggleCollapsed();
          }}
          title={collapsed ? m.signal.expandCard : m.signal.collapseCard}
          aria-label={collapsed ? m.signal.expandCard : m.signal.collapseCard}
          aria-expanded={!collapsed}
        >
          <ChevronIcon direction="right" />
        </button>
        <span className="signal-sub-logo">
          {logoSrc ? <img src={logoSrc} alt="" /> : name.slice(0, 2).toUpperCase()}
        </span>
        <span className="signal-profile-copy">
          <span className="signal-profile-name">
            {name}
            <span className="signal-profile-count">{serverCount}</span>
          </span>
          <span className="signal-sub-meta">{updatedLabel}</span>
        </span>

        <span className="signal-profile-actions" data-no-toggle onClick={(event) => event.stopPropagation()}>
          <button
            type="button"
            className="signal-icon-btn"
            onClick={onMoveUp}
            disabled={!canMoveUp}
            title={m.profiles.moveUp}
            aria-label={m.profiles.moveUp}
          >
            <ArrowIcon direction="up" />
          </button>
          <button
            type="button"
            className="signal-icon-btn"
            onClick={onMoveDown}
            disabled={!canMoveDown}
            title={m.profiles.moveDown}
            aria-label={m.profiles.moveDown}
          >
            <ArrowIcon direction="down" />
          </button>
          <button
            type="button"
            className={`signal-icon-btn${pinging ? " is-pinging" : ""}`}
            onClick={onPing}
            disabled={pinging}
            title={m.profiles.testLatency}
            aria-label={m.profiles.testLatency}
          >
            <PingIcon />
          </button>
          <button
            type="button"
            className={`signal-icon-btn${refreshing ? " is-busy" : ""}`}
            onClick={onRefresh}
            disabled={refreshing}
            title={m.home.refreshSubscription}
            aria-label={m.home.refreshSubscription}
          >
            <RefreshIcon />
          </button>
          <span className="signal-menu-wrap">
            <button
              type="button"
              className="signal-icon-btn"
              onClick={() => setMenuOpen((value) => !value)}
              aria-expanded={menuOpen}
              title={m.profiles.subscriptionMenu}
              aria-label={m.profiles.subscriptionMenu}
            >
              <DotsIcon />
            </button>
            {menuOpen && (
              <span className="signal-menu" onMouseLeave={() => setMenuOpen(false)}>
                <button
                  type="button"
                  onClick={() => {
                    setMenuOpen(false);
                    onSettings();
                  }}
                >
                  {m.profiles.subscriptionSettings}
                </button>
                <button
                  type="button"
                  className="is-danger"
                  onClick={() => {
                    setMenuOpen(false);
                    onDelete();
                  }}
                >
                  {m.profiles.delete}
                </button>
              </span>
            )}
          </span>
        </span>
      </header>

      {!collapsed && (
        <>
      <div className="signal-profile-meta">
        <div className="signal-meta-cell">
          <span className="signal-tile-key">{m.profiles.traffic}</span>
          <span className="signal-meta-value">
            {total ? `${formatBytes(used)} / ${formatBytes(total)}` : `${formatBytes(used)} / ∞`}
          </span>
          {total ? (
            <span className="signal-quota">
              <i style={{ width: `${Math.round(ratio * 100)}%` }} />
            </span>
          ) : null}
        </div>
        <div className="signal-meta-cell">
          <span className="signal-tile-key">{m.profiles.expires}</span>
          <span className="signal-meta-value">{formatSubscriptionTerm(sub.info, expireLabels(m))}</span>
        </div>
        <div className="signal-meta-cell">
          <span className="signal-tile-key">{m.profiles.updated}</span>
          <span className="signal-meta-value">{updatedLabel}</span>
        </div>
      </div>

      <div className="signal-profile-description">
        <span className="signal-tile-key">{m.common.description}</span>
        <p>{visibleDescription || m.common.noDescription}</p>
      </div>

      <div className="signal-profile-links" data-no-toggle onClick={(event) => event.stopPropagation()}>
        <a className="signal-btn signal-btn--sm signal-btn--ghost" href={supportUrl} target="_blank" rel="noreferrer">
          {m.common.support}
        </a>
        {siteUrl && (
          <a className="signal-btn signal-btn--sm signal-btn--ghost" href={siteUrl} target="_blank" rel="noreferrer">
            {m.common.site}
          </a>
        )}
      </div>
        </>
      )}
    </article>
  );
}
