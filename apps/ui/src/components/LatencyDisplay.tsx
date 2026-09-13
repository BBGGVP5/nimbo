import type { LatencyDisplayFormat } from "../lib/latency";
import { latencyBars } from "../lib/latency";
import { useAppStore } from "../store";

/** Presentation only: outer cards, pills and typography belong to each design. */
export function LatencyDisplay({ value, loading = false, format, error }: {
  value?: number | null; loading?: boolean; format?: LatencyDisplayFormat; error?: string | null;
}) {
  const savedFormat = useAppStore(s => s.preferences.latency_display_format);
  const display = format ?? savedFormat;
  const bars = latencyBars(error ? null : value, loading);
  const label = loading ? "…" : bars ? `${value} ms` : "—";
  if (!bars) return <span role="status" aria-busy={loading} title={error ?? undefined}>{label}</span>;
  const visual = display === "bars" || display === "both" || display === "dots" || display === "badge";
  const numeric = !visual || display === "both";
  const dots = display === "dots" || display === "badge";
  return (
    <span role="img" aria-label={label} title={label} data-latency-format={display}
      style={{ display: "inline-flex", alignItems: "center", gap: 5, whiteSpace: "nowrap" }}>
      {visual && <svg aria-hidden="true" width="22" height="14" viewBox="0 0 22 14" fill="currentColor" data-latency-bars={bars}>
        {[0, 1, 2, 3].map(index => dots
          ? <circle key={index} cx={2.5 + index * 5.5} cy="7" r="2" opacity={index < bars ? 1 : 0.2} />
          : <rect key={index} x={index * 5.5} y={10 - index * 3} width="4" height={4 + index * 3} rx="0.7" opacity={index < bars ? 1 : 0.2} />)}
      </svg>}
      {numeric && <span aria-hidden="true">{label}</span>}
    </span>
  );
}
