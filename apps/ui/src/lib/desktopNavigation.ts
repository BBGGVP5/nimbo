/** Existing routed pages shown in the wide desktop sidebar. */
export interface DesktopNavItem {
  to: string;
  key: string;
  icon: string;
  end: boolean;
  group?: boolean;
  /** Keep the narrow bottom bar to four primary destinations. */
  compactHide: boolean;
}

export const desktopNavItems: DesktopNavItem[] = [
  { to: '/', key: 'home', icon: 'home', end: true, compactHide: false },
  { to: '/subscriptions', key: 'profiles', icon: 'globe', end: false, compactHide: false },
  { to: '/statistics', key: 'statistics', icon: 'stats', end: true, compactHide: false },
  { to: '/routing', key: 'routing', icon: 'route', end: true, group: true, compactHide: true },
  { to: '/routing/modules', key: 'modules', icon: 'modules', end: true, compactHide: true },
  { to: '/apps', key: 'apps', icon: 'phone', end: true, compactHide: true },
  { to: '/notifications', key: 'notifications', icon: 'bell', end: true, compactHide: true },
  { to: '/sync', key: 'sync', icon: 'sync', end: true, group: true, compactHide: true },
  { to: '/settings', key: 'settings', icon: 'settings', end: true, group: true, compactHide: false },
];

/** Connections and logs belong to the activity workspace, not the sidebar. */
export const activityNavItems = [
  { to: '/statistics', key: 'statistics', icon: 'stats', end: true },
  { to: '/connections', key: 'connections', icon: 'connections', end: true },
  { to: '/tunnel-logs', key: 'tunnelLogs', icon: 'logs', end: true },
];

export const compactNavItems = desktopNavItems.filter(item => !item.compactHide);

/** Keep the owning destination selected on detail and utility pages. */
export function compactDestination(pathname: string): string {
  if (pathname === '/') return '/';
  if (pathname.startsWith('/subscriptions') || pathname === '/mihomo') return '/subscriptions';
  if (activityNavItems.some(item => item.to === pathname)) return '/statistics';
  return '/settings';
}
