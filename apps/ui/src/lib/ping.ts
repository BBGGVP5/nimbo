import { api, type ServerPing } from "./api";

export async function pingServersProgressively(
  serverIds: string[],
  onResult: (result: ServerPing) => void,
  concurrency = 4,
): Promise<void> {
  const queue = Array.from(new Set(serverIds));
  let index = 0;

  const workerCount = Math.min(Math.max(1, concurrency), queue.length);
  const workers = Array.from({ length: workerCount }, async () => {
    while (index < queue.length) {
      const serverId = queue[index++];
      try {
        onResult(await api.pingServer(serverId));
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
