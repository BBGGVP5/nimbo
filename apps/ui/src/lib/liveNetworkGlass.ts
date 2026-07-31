import type { ConnectionState } from "./api";

export type LiveNetworkGlassMode =
  | "dormant"
  | "connecting"
  | "calm"
  | "active"
  | "delayed"
  | "recovering";

export interface LiveNetworkGlassSignal {
  mode: LiveNetworkGlassMode;
  uploadLevel: number;
  downloadLevel: number;
  latencyLevel: number;
}

export interface LiveNetworkGlassInput {
  connectionState: ConnectionState | null | undefined;
  transitioning?: boolean;
  uploadBytesPerSecond?: number;
  downloadBytesPerSecond?: number;
  pingMs?: number | null;
}

const MAX_VISUAL_TRAFFIC_BYTES = 64_000_000;

function trafficLevel(bytesPerSecond: number | null | undefined): number {
  if (!Number.isFinite(bytesPerSecond) || !bytesPerSecond || bytesPerSecond <= 0) return 0;
  if (bytesPerSecond >= MAX_VISUAL_TRAFFIC_BYTES) return 1;
  const floorBytes = 4096;
  return Math.min(1, Math.max(0,
    Math.log1p(bytesPerSecond / floorBytes) /
      Math.log1p(MAX_VISUAL_TRAFFIC_BYTES / floorBytes),
  ));
}

function latencyLevel(pingMs: number | null | undefined): number {
  if (!Number.isFinite(pingMs) || pingMs == null || pingMs < 0) return 0;
  return Math.min(1, Math.max(0, (pingMs - 90) / 390));
}

export function liveNetworkGlassSignal(input: LiveNetworkGlassInput): LiveNetworkGlassSignal {
  const uploadLevel = trafficLevel(input.uploadBytesPerSecond);
  const downloadLevel = trafficLevel(input.downloadBytesPerSecond);
  const latency = latencyLevel(input.pingMs);

  let mode: LiveNetworkGlassMode;
  if (input.connectionState === "service_unavailable") {
    mode = "recovering";
  } else if (input.transitioning || input.connectionState === "connecting") {
    mode = "connecting";
  } else if (input.connectionState !== "connected") {
    mode = "dormant";
  } else if (latency >= 0.55) {
    mode = "delayed";
  } else if (Math.max(uploadLevel, downloadLevel) >= 0.08) {
    mode = "active";
  } else {
    mode = "calm";
  }

  return { mode, uploadLevel, downloadLevel, latencyLevel: latency };
}
