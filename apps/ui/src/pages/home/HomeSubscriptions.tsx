import { useState, type ReactNode } from "react";
import type { Subscription } from "../../lib/api";
import type { Messages } from "../../lib/i18n";
import { SignalProfileCard } from "../profiles/SignalProfileCard";
export function HomeSubscriptions({ subs, labels, renderServers, onRefresh, onPing, refreshingUrls, pinging }: {
  subs: Subscription[]; labels: Messages; renderServers: (sub: Subscription) => ReactNode;
  onRefresh: (sub: Subscription) => void; onPing: (sub: Subscription) => void; refreshingUrls: ReadonlySet<string>;
  pinging: boolean;
}) {
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  return <div className="universal-home-subscriptions">{subs.map(sub => <SignalProfileCard key={sub.url}
    labels={labels} sub={sub} serverCount={sub.servers.length} onRefresh={() => onRefresh(sub)} onPing={() => onPing(sub)}
    refreshing={refreshingUrls.has(sub.url)} pinging={pinging} collapsed={!expanded.has(sub.url)}
    onToggleCollapsed={() => setExpanded(current => { const next = new Set(current); next.has(sub.url) ? next.delete(sub.url) : next.add(sub.url); return next; })}
    updatedLabel={sub.fetched_at ? new Date(sub.fetched_at * 1000).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—"}>
    {expanded.has(sub.url) && renderServers(sub)}
  </SignalProfileCard>)}</div>;
}
