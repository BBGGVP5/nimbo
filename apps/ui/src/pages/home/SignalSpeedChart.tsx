import type { Messages } from "../../lib/i18n";

export interface SignalSpeedSample {
  upload: number;
  download: number;
  at: number;
}

/**
 * График скорости в стиле Signal — как на превью: две заливки под линиями
 * приёма и отдачи без рамок и подписей осей, скорость вынесена в шапку.
 *
 * Старая карточка мониторинга здесь не используется: она была из прежнего
 * стиля и выбивалась из панели.
 */

export interface SignalSpeedChartProps {
  labels: Messages;
  samples: SignalSpeedSample[];
  available: boolean;
  downloadLabel: string;
  uploadLabel: string;
}

const WIDTH = 600;
const HEIGHT = 96;
const POINTS = 40;

/** Нормирует ряд к общему максимуму, чтобы линии были сопоставимы. */
function paths(values: number[], max: number): { line: string; area: string } {
  if (values.length < 2) return { line: "", area: "" };
  const step = WIDTH / (values.length - 1);
  const coords = values.map((value, index) => {
    const x = index * step;
    const y = HEIGHT - 6 - (value / max) * (HEIGHT - 16);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  return {
    line: `M${coords.join(" L")}`,
    area: `M${coords.join(" L")} L${WIDTH},${HEIGHT} L0,${HEIGHT} Z`,
  };
}

export function SignalSpeedChart({
  labels: m,
  samples,
  available,
  downloadLabel,
  uploadLabel,
}: SignalSpeedChartProps) {
  const tail = samples.slice(-POINTS);
  const padded = tail.length >= 2 ? tail : [...Array(2 - tail.length).fill({ download: 0, upload: 0, at: 0 }), ...tail];
  const downloads = padded.map((sample) => Math.max(0, sample.download));
  const uploads = padded.map((sample) => Math.max(0, sample.upload));
  const max = Math.max(1, ...downloads, ...uploads);
  const down = paths(downloads, max);
  const up = paths(uploads, max);

  return (
    <div className="signal-speed">
      <div className="signal-speed-head">
        <span className="signal-section-kicker">{m.signal.speedTitle}</span>
        <span className="signal-speed-legend">
          <span>
            <i className="signal-flow-dot signal-flow-dot--down" />
            {available ? downloadLabel : "—"}
          </span>
          <span>
            <i className="signal-flow-dot signal-flow-dot--up" />
            {available ? uploadLabel : "—"}
          </span>
        </span>
      </div>
      <svg
        className="signal-speed-svg"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        preserveAspectRatio="none"
        role="img"
        aria-label={m.signal.speedTitle}
      >
        <defs>
          <linearGradient id="signal-speed-dl" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--signal-sky, #5bc0ff)" stopOpacity="0.34" />
            <stop offset="100%" stopColor="var(--signal-sky, #5bc0ff)" stopOpacity="0" />
          </linearGradient>
          <linearGradient id="signal-speed-ul" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--signal-lilac, #b08cff)" stopOpacity="0.3" />
            <stop offset="100%" stopColor="var(--signal-lilac, #b08cff)" stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={down.area} fill="url(#signal-speed-dl)" />
        <path d={down.line} className="signal-chart-line signal-chart-line--down" />
        <path d={up.area} fill="url(#signal-speed-ul)" />
        <path d={up.line} className="signal-chart-line signal-chart-line--up" />
      </svg>
    </div>
  );
}
