import { formatBytes, formatSubscriptionTerm, type Subscription } from "../lib/api";
import { expireLabels, type Messages } from "../lib/i18n";
import { Dialog } from "./Universal";
export function SubscriptionInfo({ sub, labels: m, onClose, supportUrl, siteUrl }: {
  sub: Subscription; labels: Messages; onClose: () => void; supportUrl?: string | null; siteUrl?: string | null;
}) {
  const used = (sub.info?.upload ?? 0) + (sub.info?.download ?? 0);
  const safeLink = (value?: string | null) => value && /^(https?:|tg:)/i.test(value) ? value : null;
  const support = safeLink(supportUrl ?? sub.meta?.support_url);
  const site = safeLink(siteUrl ?? sub.meta?.website_url);
  return <Dialog title={sub.name?.trim() || m.common.subscription} closeLabel={m.common.close} onClose={onClose}>
    <dl className="universal-facts">
      <div><dt>{m.profiles.traffic}</dt><dd>{sub.info ? `${formatBytes(used)} / ${sub.info.total ? formatBytes(sub.info.total) : "∞"}` : "—"}</dd></div>
      <div><dt>{m.profiles.expires}</dt><dd>{formatSubscriptionTerm(sub.info, expireLabels(m))}</dd></div>
      <div><dt>{m.profiles.updated}</dt><dd>{sub.fetched_at ? new Date(sub.fetched_at * 1000).toLocaleString() : "—"}</dd></div>
    </dl>
    <section className="universal-provider-description"><h3>{m.common.description}</h3><p>{sub.meta?.description?.trim() || m.common.noDescription}</p></section>
    <div className="universal-actions">{support && <a className="signal-btn" href={support} target="_blank" rel="noreferrer">{m.common.support}</a>}
      {site && <a className="signal-btn" href={site} target="_blank" rel="noreferrer">{m.common.site}</a>}</div>
  </Dialog>;
}
