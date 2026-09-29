import { api, type ServerPing } from "./api";

export async function pingServersProgressively(
  serverIds: string[],
  onResult: (result: ServerPing) => void,
  concurrency = 3,
  signal?: AbortSignal,
): Promise<void> {
  const cancel = () => { void api.cancelPings().catch(() => {}); };
  if (signal?.aborted) return;
  signal?.addEventListener("abort", cancel, { once: true });
  const queue = Array.from(new Set(serverIds));
  let index = 0;

  const workerCount = Math.min(Number.isFinite(concurrency) ? Math.max(1, Math.floor(concurrency)) : 3, queue.length);
  const workers = Array.from({ length: workerCount }, async () => {
    while (index < queue.length && !signal?.aborted) {
      const serverId = queue[index++];
      try {
        const result = await api.pingServer(serverId);
        if (signal?.aborted) return;
        if (result.server_id !== serverId) throw new Error("Ping response server mismatch");
        onResult(result.error ? { ...result, latency_ms: null } : result);
      } catch (error) {
        if (signal?.aborted) return;
        onResult({
          server_id: serverId,
          latency_ms: null,
          error: String(error),
        });
      }
    }
  });

  try { await Promise.all(workers); }
  finally { signal?.removeEventListener("abort", cancel); }
}
