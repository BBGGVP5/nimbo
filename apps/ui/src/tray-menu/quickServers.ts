export function favoriteServers<T extends { id: string }>(servers: T[], raw: string | null): T[] {
  try {
    const ids: unknown = JSON.parse(raw ?? "[]");
    if (!Array.isArray(ids)) return [];
    const byId = new Map(servers.map(server => [server.id, server]));
    return [...new Set(ids.filter((id): id is string => typeof id === "string"))]
      .map(id => byId.get(id)).filter((server): server is T => server !== undefined).slice(0, 5);
  } catch { return []; }
}
