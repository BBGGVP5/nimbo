/** Both series share one scale. Empty/single-point samples do not invent a history. */
export function trafficPaths(points: { download: number; upload: number }[], width = 640, height = 180) {
  const clean = points.filter(p => Number.isFinite(p.download) && Number.isFinite(p.upload) && p.download >= 0 && p.upload >= 0);
  const max = Math.max(1, ...clean.flatMap(p => [p.download, p.upload]));
  const path = (key: "download" | "upload") => clean.length < 2 ? "" : clean.map((p, i) => `${i ? "L" : "M"}${i * width / (clean.length - 1)},${height - 8 - p[key] / max * (height - 16)}`).join(" ");
  return { download: path("download"), upload: path("upload"), max, count: clean.length };
}
