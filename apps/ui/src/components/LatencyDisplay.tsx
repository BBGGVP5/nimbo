import type { LatencyDisplayFormat, LatencyProtocol } from "../lib/latency";
import { latencyPresentation } from "../lib/latency";
import { fillTemplate, getMessages } from "../lib/i18n";
import { useAppStore } from "../store";

/** Presentation only: outer cards, pills and typography belong to each design. */
export function LatencyDisplay({ value, loading = false, format, error, protocol, language }: {
  value?: number | null; loading?: boolean; format?: LatencyDisplayFormat; error?: string | null;
  protocol?: LatencyProtocol; language?: "ru" | "en" | "system";
}) {
  const preferences = useAppStore(s => s.preferences);
  const display = format ?? preferences.latency_display_format;
  const presentation = latencyPresentation(error ? null : value, protocol ?? preferences.latency_protocol, loading);
  const { bars } = presentation;
  const label = loading ? "…" : presentation.label;
  if (!bars) return <span role="status" aria-busy={loading} title={error ?? undefined}>{label}</span>;
  const explanation = presentation.approximate
    ? fillTemplate(getMessages(language ?? preferences.language).settings.latencyEstimateLabel, { estimate: label, raw: value! })
    : label;
  const visual = display === "bars" || display === "both" || display === "dots" || display === "badge";
  const numeric = !visual || display === "both";
  const dots = display === "dots" || display === "badge";
  return (
    <span role="img" aria-label={explanation} title={explanation} data-latency-format={display}
      style={{ display: "inline-flex", alignItems: "center", gap: 5, whiteSpace: "nowrap" }}>
      {presentation.approximate && !numeric && <span aria-hidden="true">≈</span>}
      {visual && <svg aria-hidden="true" width="22" height="14" viewBox="0 0 22 14" fill="currentColor" data-latency-bars={bars}>
        {[0, 1, 2, 3].map(index => dots
          ? <circle key={index} cx={2.5 + index * 5.5} cy="7" r="2" opacity={index < bars ? 1 : 0.2} />
          : <rect key={index} x={index * 5.5} y={10 - index * 3} width="4" height={4 + index * 3} rx="0.7" opacity={index < bars ? 1 : 0.2} />)}
      </svg>}
      {numeric && <span aria-hidden="true">{label}</span>}
    </span>
  );
}
