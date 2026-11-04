export function favoriteServers<T extends { id: string }>(servers: T[], raw: string | null): T[] {
  try {
    const ids: unknown = JSON.parse(raw ?? "[]");
    if (!Array.isArray(ids)) return [];
    const byId = new Map(servers.map(server => [server.id, server]));
    return [...new Set(ids.filter((id): id is string => typeof id === "string"))]
      .map(id => byId.get(id)).filter((server): server is T => server !== undefined).slice(0, 5);
  } catch { return []; }
}

/**
 * Servers for the tray's quick list: the favourites, or — when none are
 * starred — the active server followed by the fastest measured ones, so the
 * tray can switch servers without opening the window even on a fresh install.
 */
export function quickServers<T extends { id: string; latencyMs?: number | null }>(
  servers: T[],
  raw: string | null,
  activeId: string | null,
): { servers: T[]; fallback: boolean } {
  const favorites = favoriteServers(servers, raw);
  if (favorites.length) return { servers: favorites, fallback: false };
  const active = servers.find(server => server.id === activeId);
  const measured = servers
    .map((server, index) => ({ server, index }))
    .filter(({ server }) => server !== active)
    .sort((a, b) => {
      const la = a.server.latencyMs ?? Infinity, lb = b.server.latencyMs ?? Infinity;
      return la === lb ? a.index - b.index : la - lb;
    })
    .map(({ server }) => server);
  return { servers: (active ? [active, ...measured] : measured).slice(0, 5), fallback: true };
}
