import { api, type ServerPing } from "./api";

export async function pingServersProgressively(
  serverIds: string[],
  onResult: (result: ServerPing) => void,
  concurrency = 4,
): Promise<void> {
  const queue = Array.from(new Set(serverIds));
  let index = 0;

  const workerCount = Math.min(Number.isFinite(concurrency) ? Math.max(1, Math.floor(concurrency)) : 4, queue.length);
  const workers = Array.from({ length: workerCount }, async () => {
    while (index < queue.length) {
      const serverId = queue[index++];
      try {
        const result = await api.pingServer(serverId);
        if (result.server_id !== serverId) throw new Error("Ping response server mismatch");
        onResult(result.error ? { ...result, latency_ms: null } : result);
      } catch (error) {
        onResult({
          server_id: serverId,
          latency_ms: null,
          error: String(error),
        });
      }
    }
  });

  await Promise.all(workers);
}
