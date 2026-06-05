import type { Subscription, SubscriptionTheme } from "./api";

function themeHash(value: string): string {
  let hash = 0x811c9dc5;
  for (let i = 0; i < value.length; i += 1) {
    hash ^= value.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}

function themeCachePrefix(subscriptionUrl: string): string {
  return `nimbo.subscriptionTheme.${themeHash(subscriptionUrl)}.`;
}

function hasThemeValue(theme: SubscriptionTheme | null | undefined): theme is SubscriptionTheme {
  if (!theme) return false;
  return Boolean(
    theme.filter ||
      theme.accent ||
      theme.orb1 ||
      theme.orb2 ||
      theme.blur != null ||
      theme.ui_style,
  );
}

function cleanupThemeCache(prefix: string, keepKey: string) {
  try {
    for (let i = localStorage.length - 1; i >= 0; i -= 1) {
      const key = localStorage.key(i);
      if (key?.startsWith(prefix) && key !== keepKey) {
        localStorage.removeItem(key);
      }
    }
  } catch {}
}

function readThemeCache(key: string): SubscriptionTheme | null {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as SubscriptionTheme;
    return hasThemeValue(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export function cachedSubscriptionTheme(
  sub: Subscription | null | undefined,
): SubscriptionTheme | null {
  if (!sub) return null;

  const prefix = themeCachePrefix(sub.url);
  const key = `${prefix}${sub.fetched_at}`;
  const theme = sub.meta?.theme ?? null;

  if (hasThemeValue(theme)) {
    cleanupThemeCache(prefix, key);
    try {
      localStorage.setItem(key, JSON.stringify(theme));
    } catch {}
    return theme;
  }

  cleanupThemeCache(prefix, key);
  return readThemeCache(key);
}
