import { ConnectionStateIcon } from "./ConnectionStateIcon";
import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import type { Messages } from "../lib/i18n";

/**
 * Рельс навигации стиля Signal — ровно как в макете: марка с градиентом,
 * пункты с квадратной точкой, тонкие разделители между группами и два
 * чипа состояния внизу (ядро и версия приложения).
 *
 * Состав пунктов тот же, что и в обычной панели, поэтому ни один раздел
 * не пропадает при переключении стиля.
 */

export interface SignalNavItem {
  to: string;
  key: string;
  end: boolean;
  /** Иконка нужна нижней панели в узком окне: там подписи одни не читаются. */
  icon?: ReactNode;
  /** Разделитель рисуется перед пунктом — так в макете разбиты группы. */
  group?: boolean;
  compactHide?: boolean;
}

export interface SignalSidebarProps {
  labels: Messages;
  items: SignalNavItem[];
  label: (key: string) => string;
  unread: number;
  version: string;
  coreLabel: string;
  coreState: string;
  /** Подпись «обновить» появляется, когда есть свежая версия. */
  updateLabel?: string | null;
  onUpdate?: () => void;
  width: number;
  /** Свёрнутый рельс оставляет одни значки. */
  collapsed?: boolean;
  onToggleCollapsed?: () => void;
  collapseLabel?: string;
  expandLabel?: string;
}

export function SignalSidebar({
  labels: m,
  items,
  label,
  unread,
  version,
  coreLabel,
  coreState,
  updateLabel,
  onUpdate,
  width,
  collapsed = false,
  onToggleCollapsed,
  collapseLabel = "",
  expandLabel = "",
}: SignalSidebarProps) {
  const collapseTitle = collapsed ? expandLabel : collapseLabel;
  return (
    <aside
      className="signal-rail"
      data-collapsed={collapsed ? "true" : undefined}
      style={{ "--sidebar-width": `${width}px` } as React.CSSProperties}
    >
      <div className="signal-rail-brand">
        <span className="signal-rail-mark" aria-hidden="true">
          <ConnectionStateIcon connected busy={false} />
        </span>
        <span className="parity-brand-copy"><span className="signal-rail-name">nimbo</span><small>{version.replace(/^V/, "v").replace("-beta.", " β")}</small></span>
        {onToggleCollapsed && (
          <button
            type="button"
            className="app-sidebar-collapse"
            onClick={onToggleCollapsed}
            aria-label={collapseTitle}
            aria-expanded={!collapsed}
            title={collapseTitle}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path
                d="M14.5 6.5 9 12l5.5 5.5"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.9"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
        )}
      </div>

      <nav className="signal-rail-nav">
        {items.map((item) => (
          <div key={item.to} className="signal-rail-slot" data-nav-key={item.key} data-compact-hide={item.compactHide ? "true" : undefined}>
            {item.group && <span className="signal-rail-sep" aria-hidden="true" />}
            <NavLink
              title={label(item.key)}
              aria-label={label(item.key)}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `signal-nav-item${isActive ? " is-active" : ""}`
              }
            >
              <span className="signal-nav-dot" aria-hidden="true" />
              {item.icon && (
                <span className="signal-nav-icon" aria-hidden="true">
                  {item.icon}
                </span>
              )}
              <span className="signal-nav-label">{label(item.key)}</span>
              {item.key === "notifications" && unread > 0 && (
                <span className="signal-nav-badge" aria-label={`${unread} ${m.notifications.unread}`}>
                  {unread > 99 ? "99+" : unread}
                </span>
              )}
            </NavLink>
          </div>
        ))}
      </nav>

      <div className="signal-rail-foot">
        <span className="signal-core-chip">
          <span>{coreLabel}</span>
          <b>{coreState}</b>
        </span>
        <span className="signal-core-chip">
          <span>NIMBO {version}</span>
          {updateLabel && (
            <button type="button" className="signal-core-update" onClick={onUpdate}>
              {updateLabel}
            </button>
          )}
        </span>
      </div>
    </aside>
  );
}
