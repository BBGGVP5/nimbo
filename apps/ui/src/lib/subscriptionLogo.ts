import { useEffect, useMemo, useState } from "react";
import { api, type Subscription } from "./api";

const memoryCache = new Map<string, string | null>();

function logoCacheKey(sub: Subscription): string | null {
  const logoUrl = sub.meta?.logo_url?.trim();
  if (!logoUrl) return null;
  return `${sub.url}\n${sub.fetched_at}\n${logoUrl}`;
}

export function useCachedSubscriptionLogo(
  sub: Subscription | null | undefined,
  enabled = true,
): string | null {
  const subscriptionUrl = sub?.url ?? null;
  const fetchedAt = sub?.fetched_at ?? null;
  const logoUrl = sub?.meta?.logo_url ?? null;
  const key = useMemo(
    () => (sub && enabled ? logoCacheKey(sub) : null),
    [enabled, subscriptionUrl, fetchedAt, logoUrl],
  );
  const [src, setSrc] = useState<string | null>(() => {
    if (!key) return null;
    return memoryCache.get(key) ?? null;
  });

  useEffect(() => {
    let cancelled = false;

    if (!subscriptionUrl || !enabled || !key) {
      setSrc(null);
      return () => {
        cancelled = true;
      };
    }

    if (memoryCache.has(key)) {
      setSrc(memoryCache.get(key) ?? null);
      return () => {
        cancelled = true;
      };
    }

    setSrc(null);
    void api
      .getSubscriptionLogo(subscriptionUrl, logoUrl, fetchedAt)
      .then((next) => {
        memoryCache.set(key, next);
        if (!cancelled) setSrc(next);
      })
      .catch(() => {
        memoryCache.set(key, null);
        if (!cancelled) setSrc(null);
      });

    return () => {
      cancelled = true;
    };
  }, [enabled, fetchedAt, key, logoUrl, subscriptionUrl]);

  return src;
}
