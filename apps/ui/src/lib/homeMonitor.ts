/** Coordinates use actual observation times, never an assumed polling cadence. */
export interface MemorySample { bytes: number; at: number }
export function sampleX(at: number, first: number, last: number, width: number, padding = 0) {
  return padding + (at - first) / Math.max(1, last - first) * (width - 2 * padding);
}
export function measuredTimeLabels(samples: { at: number }[], locale: string) {
  if (!samples.length) return [];
  const selected = samples.length < 3 ? samples : [samples[0], samples[Math.floor((samples.length - 1) / 2)], samples[samples.length - 1]];
  return selected.map(({ at }) => ({ at, position: sampleX(at, samples[0].at, samples[samples.length - 1].at, 100), label: new Date(at).toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit", second: "2-digit" }) }));
}
export function memoryPaths(samples: MemorySample[], width = 320, height = 72, padding = 5) {
  const values = samples.filter(p => Number.isFinite(p.bytes) && p.bytes >= 0 && Number.isFinite(p.at));
  const peak = values.length ? Math.max(...values.map(p => p.bytes)) : null;
  if (values.length < 2 || values[values.length - 1].at <= values[0].at) return { line: "", area: "", peak };
  const min = Math.min(...values.map(p => p.bytes));
  const range = Math.max(1, peak! - min, peak! * .08);
  const line = values.map((p, i) => {
    const x = sampleX(p.at, values[0].at, values[values.length - 1].at, width, padding).toFixed(2);
    const y = (height - padding - (p.bytes - min) / range * (height - padding * 2)).toFixed(2);
    return i ? `H ${x} V ${y}` : `M ${x} ${y}`;
  }).join(" ");
  return { line, area: `${line} L ${width - padding} ${height - padding} L ${padding} ${height - padding} Z`, peak };
}
