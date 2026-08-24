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
}: SignalSidebarProps) {
  return (
    <aside
      className="signal-rail"
      style={{ "--sidebar-width": `${width}px` } as React.CSSProperties}
    >
      <div className="signal-rail-brand">
        <span className="signal-rail-mark" aria-hidden="true">
          N
        </span>
        <span className="signal-rail-name">Nimbo</span>
      </div>

      <nav className="signal-rail-nav">
        {items.map((item) => (
          <div key={item.to} className="signal-rail-slot">
            {item.group && <span className="signal-rail-sep" aria-hidden="true" />}
            <NavLink
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
