import type { Subscription } from "./api";

/** One reminder per subscription per stage; renewing (new expiry) re-arms them. */
export type WarningStage = "expire-3d" | "expire-1d" | "expired" | "traffic-90" | "traffic-100";

export interface SubscriptionWarning {
  url: string;
  name: string;
  stage: WarningStage;
  /** Whole days left for expiry stages, percent used for traffic stages. */
  value: number;
}

/** url → { expire the stages were armed for, stages already shown } */
export type WarnedState = Record<string, { expire: number; total: number; stages: WarningStage[] }>;

export const SUBSCRIPTION_WARNINGS_KEY = "nimbo.subscriptionWarnings";
const DAY_MS = 86_400_000;

function expiryStage(expireSeconds: number, now: number): { stage: WarningStage; days: number } | null {
  const left = expireSeconds * 1000 - now;
  if (left <= 0) return { stage: "expired", days: 0 };
  // Whole days, rounded down like the subscription card's "через N дн.".
  const days = Math.floor(left / DAY_MS);
  if (days < 1) return { stage: "expire-1d", days };
  if (days <= 3) return { stage: "expire-3d", days };
  return null;
}

function trafficStage(used: number, total: number): { stage: WarningStage; percent: number } | null {
  const percent = Math.floor((used / total) * 100);
  if (percent >= 100) return { stage: "traffic-100", percent };
  if (percent >= 90) return { stage: "traffic-90", percent };
  return null;
}

/**
 * Returns the reminders that are due now and the state to persist so each
 * one is shown once. A later stage implies the earlier ones, so jumping from
 * 10 days straight to 1 day shows only the 1-day reminder.
 */
export function dueSubscriptionWarnings(
  subscriptions: readonly Subscription[],
  warned: WarnedState,
  now: number,
): { due: SubscriptionWarning[]; next: WarnedState } {
  const due: SubscriptionWarning[] = [];
  const next: WarnedState = {};
  for (const sub of subscriptions) {
    const expire = sub.info?.expire ?? 0;
    const total = sub.info?.total ?? 0;
    const previous = warned[sub.url];
    // A renewed term or a new traffic plan re-arms every reminder.
    const stages = previous && previous.expire === expire && previous.total === total ? [...previous.stages] : [];
    const name = sub.name?.trim() || new URL(sub.url, "https://subscription.invalid").hostname;
    const expiry = expire > 0 ? expiryStage(expire, now) : null;
    if (expiry) {
      const order: WarningStage[] = ["expire-3d", "expire-1d", "expired"];
      const reached = order.slice(0, order.indexOf(expiry.stage) + 1);
      if (!stages.includes(expiry.stage)) due.push({ url: sub.url, name, stage: expiry.stage, value: expiry.days });
      for (const stage of reached) if (!stages.includes(stage)) stages.push(stage);
    }
    const used = (sub.info?.upload ?? 0) + (sub.info?.download ?? 0);
    const traffic = total > 0 ? trafficStage(used, total) : null;
    if (traffic) {
      const order: WarningStage[] = ["traffic-90", "traffic-100"];
      const reached = order.slice(0, order.indexOf(traffic.stage) + 1);
      if (!stages.includes(traffic.stage)) due.push({ url: sub.url, name, stage: traffic.stage, value: traffic.percent });
      for (const stage of reached) if (!stages.includes(stage)) stages.push(stage);
    }
    if (stages.length) next[sub.url] = { expire, total, stages };
  }
  return { due, next };
}

export function subscriptionWarningText(warning: SubscriptionWarning, ru: boolean): string {
  const { name, value } = warning;
  switch (warning.stage) {
    case "expired":
      return ru ? `Подписка «${name}» закончилась. Продлите её, чтобы VPN продолжил работать.` : `Subscription “${name}” has expired. Renew it to keep the VPN working.`;
    case "expire-1d":
      return ru ? `Подписка «${name}» закончится в течение суток.` : `Subscription “${name}” ends within a day.`;
    case "expire-3d":
      return ru ? `Подписка «${name}» закончится через ${value} дн.` : `Subscription “${name}” ends in ${value} days.`;
    case "traffic-100":
      return ru ? `Трафик подписки «${name}» исчерпан.` : `Traffic for “${name}” is used up.`;
    case "traffic-90":
      return ru ? `Израсходовано ${value}% трафика подписки «${name}».` : `${value}% of the traffic for “${name}” is used.`;
  }
}

export function readWarnedState(): WarnedState {
  try {
    const parsed = JSON.parse(localStorage.getItem(SUBSCRIPTION_WARNINGS_KEY) ?? "{}");
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

export function writeWarnedState(state: WarnedState): void {
  try {
    localStorage.setItem(SUBSCRIPTION_WARNINGS_KEY, JSON.stringify(state));
  } catch {
    /* storage unavailable: reminders may repeat next launch, nothing breaks */
  }
}
