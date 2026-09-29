import { ConnectionStateIcon } from "./ConnectionStateIcon";
import type { ThemeMode } from "../lib/api";

/** A miniature of the current workspace, not the retired glass dashboard. */
export function AppearanceThemePreview({ title, value, selected, onClick }: {
  title: string; value: ThemeMode; selected: boolean; onClick: () => void;
}) {
  return <button type="button" className="parity-theme-card" role="radio" aria-checked={selected} onClick={onClick}>
    <span className={`parity-theme-mini parity-theme-mini--${value}`} aria-hidden="true">
      <span className="parity-mini-rail"><ConnectionStateIcon connected busy={false}/><i/><i/><i/><i/></span>
      <span className="parity-mini-workspace"><span className="parity-mini-top"><i/><i/></span><span className="parity-mini-heading"/>
        <span className="parity-mini-columns"><span className="parity-mini-connection"><i/><b><ConnectionStateIcon connected busy={false}/></b><em/></span><span className="parity-mini-subscriptions"><i/><i/></span></span>
        <span className="parity-mini-chart"><svg viewBox="0 0 120 26"><path d="M0 22 10 19 20 22 30 10 40 14 50 6 60 10 70 4 80 13 90 8 100 11 110 5 120 7" fill="none" stroke="currentColor" strokeWidth="1.5"/></svg></span>
      </span>
    </span>
    <span className="parity-theme-caption"><span>{title}</span><span className="parity-selection-mark" aria-hidden="true">{selected ? "✓" : ""}</span></span>
  </button>;
}
