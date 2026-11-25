// Данные и геометрия карты на главной («Вид главного экрана: Карта»).
//
// Источник — общий для всех платформ файл assets/geo/nimbo-world-map.json
// (формат описан в assets/geo/README.md). Копия лежит в src/assets/geo, чтобы
// Vite не ходил за пределы пакета; тест worldMap.test.mjs следит, что копия
// не разошлась с оригиналом.

export type LatLon = [number, number];

export interface WorldMapData {
  grid: { step: number; lonWest: number; latNorth: number; cols: number; rows: number };
  land: string[];
  countries: Record<string, LatLon>;
  timezones: Record<string, LatLon>;
}

/** Центры клеток суши в градусах: плоский массив [lon0, lat0, lon1, lat1, …]. */
export function decodeLand(data: WorldMapData): Float32Array {
  const { step, lonWest, latNorth, cols } = data.grid;
  const out: number[] = [];
  data.land.forEach((row, r) => {
    const lat = latNorth - (r + 0.5) * step;
    for (let nibble = 0; nibble < row.length; nibble++) {
      const value = parseInt(row[nibble], 16);
      if (!value) continue;
      for (let bit = 0; bit < 4; bit++) {
        if (!(value & (8 >> bit))) continue;
        const c = nibble * 4 + bit;
        if (c >= cols) continue;
        out.push(lonWest + (c + 0.5) * step, lat);
      }
    }
  });
  return Float32Array.from(out);
}

const MOSCOW: LatLon = [55.76, 37.62];

/**
 * «Вы здесь» без сети: часовой пояс устройства → страна из локали → Москва.
 */
export function resolveHomeLocation(
  data: WorldMapData,
  timeZone: string | undefined,
  locales: readonly string[],
): { point: LatLon; source: "timezone" | "locale" | "fallback"; country: string | null } {
  if (timeZone && data.timezones[timeZone]) {
    return { point: data.timezones[timeZone], source: "timezone", country: null };
  }
  for (const locale of locales) {
    const region = localeRegion(locale);
    if (region && data.countries[region]) return { point: data.countries[region], source: "locale", country: region };
  }
  return { point: MOSCOW, source: "fallback", country: "RU" };
}

function localeRegion(locale: string): string | null {
  try {
    const region = new Intl.Locale(locale).maximize().region;
    if (region && /^[A-Z]{2}$/.test(region)) return region;
  } catch {
    /* некорректная локаль — пробуем разбор строки */
  }
  const match = locale.match(/[-_]([A-Za-z]{2})(?:$|[-_])/);
  return match ? match[1].toUpperCase() : null;
}

export function deviceHomeLocation(data: WorldMapData) {
  let timeZone: string | undefined;
  try {
    timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    timeZone = undefined;
  }
  const locales = typeof navigator === "undefined"
    ? []
    : [...(navigator.languages ?? []), navigator.language].filter(Boolean);
  return resolveHomeLocation(data, timeZone, locales);
}

export interface MapViewport {
  /** Западная долгота и северная широта левого верхнего угла. */
  lonWest: number;
  latNorth: number;
  /** Пикселей на градус долготы / широты. */
  pxPerLon: number;
  pxPerLat: number;
}

const LAT_MIN = -56;
const LAT_MAX = 80;

/**
 * Окно карты по bbox точек с отступом. Равнопромежуточная проекция с
 * поправкой cos(центральной широты), окно расширяется до пропорций холста,
 * но не меньше minSpanLon градусов, чтобы одна страна не раздувалась на весь экран.
 */
export function fitViewport(
  points: readonly LatLon[],
  width: number,
  height: number,
  {
    padding = 0.18,
    minSpanLon = 46,
    insets = { top: 0, right: 0, bottom: 0, left: 0 },
  }: { padding?: number; minSpanLon?: number; insets?: { top: number; right: number; bottom: number; left: number } } = {},
): MapViewport {
  // Точки вписываются во внутренний прямоугольник (например, над карточкой
  // сервера), а суша рисуется на всём холсте.
  const innerWidth = Math.max(80, width - insets.left - insets.right);
  const innerHeight = Math.max(80, height - insets.top - insets.bottom);
  const inner = fitInner(points, innerWidth, innerHeight, padding, minSpanLon);
  return {
    ...inner,
    lonWest: inner.lonWest - insets.left / inner.pxPerLon,
    latNorth: inner.latNorth + insets.top / inner.pxPerLat,
  };
}

function fitInner(points: readonly LatLon[], width: number, height: number, padding: number, minSpanLon: number): MapViewport {
  const lats = points.map((p) => p[0]);
  const lons = points.map((p) => p[1]);
  let south = lats.length ? Math.min(...lats) : 30;
  let north = lats.length ? Math.max(...lats) : 60;
  let west = lons.length ? Math.min(...lons) : 0;
  let east = lons.length ? Math.max(...lons) : 60;

  const centerLat = (south + north) / 2;
  const kx = Math.max(0.35, Math.cos((centerLat * Math.PI) / 180));
  const aspect = width / Math.max(1, height);

  // Отступ и минимальный размер считаем в «экранных» градусах (долгота × cos).
  let spanX = Math.max((east - west) * kx, minSpanLon * kx);
  let spanY = Math.max(north - south, (minSpanLon * kx) / aspect);
  spanX *= 1 + padding * 2;
  spanY *= 1 + padding * 2;
  // Подгоняем под пропорции холста.
  if (spanX / spanY > aspect) spanY = spanX / aspect;
  else spanX = spanY * aspect;
  // Не шире мира.
  spanX = Math.min(spanX, 360 * kx);
  spanY = Math.min(spanY, (LAT_MAX - LAT_MIN) * 1.15);

  const centerLon = (west + east) / 2;
  const midLat = (south + north) / 2;
  west = centerLon - spanX / kx / 2;
  north = midLat + spanY / 2;
  // Держим окно в пределах данных, если есть место.
  if (north > LAT_MAX + 6) north = LAT_MAX + 6;
  if (north - spanY < LAT_MIN - 6) north = Math.min(LAT_MAX + 6, LAT_MIN - 6 + spanY);

  const pxPerLat = height / spanY;
  return { lonWest: west, latNorth: north, pxPerLat, pxPerLon: pxPerLat * kx };
}

export function project(viewport: MapViewport, lat: number, lon: number): [number, number] {
  return [(lon - viewport.lonWest) * viewport.pxPerLon, (viewport.latNorth - lat) * viewport.pxPerLat];
}

/** Контрольная точка дуги: середина, приподнятая перпендикулярно отрезку «вверх». */
export function arcControl(a: [number, number], b: [number, number]): [number, number] {
  const mx = (a[0] + b[0]) / 2;
  const my = (a[1] + b[1]) / 2;
  const dx = b[0] - a[0];
  const dy = b[1] - a[1];
  const length = Math.hypot(dx, dy) || 1;
  let nx = -dy / length;
  let ny = dx / length;
  if (ny > 0) { nx = -nx; ny = -ny; }
  const lift = Math.min(160, length * 0.28);
  return [mx + nx * lift, my + ny * lift];
}

export function quadPoint(a: [number, number], c: [number, number], b: [number, number], t: number): [number, number] {
  const u = 1 - t;
  return [u * u * a[0] + 2 * u * t * c[0] + t * t * b[0], u * u * a[1] + 2 * u * t * c[1] + t * t * b[1]];
}
