import type { Server, ServerPing } from "./api";
import { pingServersProgressively } from "./ping";

/**
 * Выбор самого быстрого узла.
 *
 * Список серверов подписки почти всегда длиннее, чем стоит читать, и разница
 * между узлами — это задержка, а не название страны. Поэтому выбор сводится к
 * одному действию: замерить и подключиться к лучшему.
 */

/** Лучший из тех, кто вообще ответил. Молчащий узел — не «ноль миллисекунд». */
export function fastestServerId(
  servers: Server[],
  pings: Record<string, number>,
): string | null {
  let best: string | null = null;
  let bestLatency = Number.POSITIVE_INFINITY;
  for (const server of servers) {
    const latency = pings[server.id];
    if (typeof latency !== "number" || !Number.isFinite(latency) || latency <= 0) continue;
    if (latency < bestLatency) {
      bestLatency = latency;
      best = server.id;
    }
  }
  return best;
}

/**
 * Замеряет узлы заново и возвращает лучший.
 *
 * Сохранённые числа для выбора не годятся: они могли быть сняты вчера, а узел
 * с тех пор успел деградировать — именно от такого выбора человек и уходит,
 * нажимая «авто».
 */
export async function measureFastestServer(
  servers: Server[],
  onResult?: (result: ServerPing) => void,
): Promise<{ id: string; latencyMs: number } | null> {
  const measured: Record<string, number> = {};
  await pingServersProgressively(
    servers.map((server) => server.id),
    (result) => {
      if (result.latency_ms != null && result.latency_ms > 0) {
        measured[result.server_id] = result.latency_ms;
      }
      onResult?.(result);
    },
  );
  const best = fastestServerId(servers, measured);
  return best ? { id: best, latencyMs: measured[best] } : null;
}
