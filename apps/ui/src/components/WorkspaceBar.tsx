import { useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { HomeMetaIcon } from "./HomeMetaIcon";
import { useMessages } from "../lib/i18n";
import { notifyError } from "../lib/notify";
import { useAppStore } from "../store";

/** One workspace shell, including detail and utility pages. */
export function WorkspaceBar() {
  const m = useMessages();
  const ru = m.common.locale.startsWith("ru");
  const { pathname } = useLocation();
  const [busy, setBusy] = useState(false);
  const active = useAppStore(s => s.subscriptions.find(sub => sub.url === s.activeSubscriptionUrl)?.name);
  const routes = [["/mihomo", ru ? "Профили Mihomo" : "Mihomo profiles"], ["/subscriptions", m.app.profiles], ["/routing", m.app.routing], ["/apps", m.app.apps], ["/statistics", m.app.statistics], ["/connections", m.app.connections], ["/tunnel-logs", m.app.tunnelLogs], ["/sync", m.app.sync], ["/notifications", m.app.notifications], ["/settings", m.app.settings]];
  const title = routes.find(([path]) => pathname.startsWith(path))?.[1] ?? m.app.home;
  const activity = ["/statistics", "/connections", "/tunnel-logs"].includes(pathname);
  const toggleTheme = async () => {
    setBusy(true);
    try {
      const store = useAppStore.getState();
      await store.setPreferences({ ...store.preferences, theme_mode: document.body.dataset.theme === "light" ? "dark" : "light" });
    } catch (e) { notifyError(String(e)); }
    finally { setBusy(false); }
  };
  return <>
    <header className="universal-workspace-bar parity-workspace-bar">
      <div><span>{ru ? "Рабочее пространство" : "Workspace"}</span><span aria-hidden="true">/</span><strong>{title}</strong></div>
      <div><span className="universal-workspace-profile">{active || "Nimbo"}</span><Link to="/notifications" className="parity-notifications" aria-label={m.app.notifications}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M5 17h14l-2-3V9a5 5 0 0 0-10 0v5l-2 3Zm5 3h4"/></svg></Link><button type="button" className="signal-icon-btn" disabled={busy} onClick={() => void toggleTheme()} aria-label={ru ? "Сменить тему" : "Toggle theme"}><HomeMetaIcon kind="theme"/></button></div>
    </header>
    {activity && <nav className="parity-activity-nav" aria-label={ru ? "Мониторинг" : "Monitoring"}>{routes.filter(([path]) => ["/statistics", "/connections", "/tunnel-logs"].includes(path)).map(([path, label]) => <NavLink key={path} to={path}>{label}</NavLink>)}</nav>}
  </>;
}
