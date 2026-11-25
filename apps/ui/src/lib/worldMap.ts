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
  // Охват не обрезаем отдельно по осям: иначе пропорции окна расходились с
  // холстом, масштаб рос и точки по краям уходили за рамку.

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

// ── Вид карты (Beta 2): зум, сдвиг, плавные перелёты ─────────────────────

/** Центр окна (градусы), пикселей на градус широты и поправка долготы. */
export interface MapView {
  cx: number;
  cy: number;
  ppd: number;
  kx: number;
}

export function viewToViewport(view: MapView, width: number, height: number): MapViewport {
  const pxPerLon = view.ppd * view.kx;
  return {
    lonWest: view.cx - width / 2 / pxPerLon,
    latNorth: view.cy + height / 2 / view.ppd,
    pxPerLat: view.ppd,
    pxPerLon,
  };
}

export function viewportToView(viewport: MapViewport, width: number, height: number): MapView {
  return {
    cx: viewport.lonWest + width / 2 / viewport.pxPerLon,
    cy: viewport.latNorth - height / 2 / viewport.pxPerLat,
    ppd: viewport.pxPerLat,
    kx: viewport.pxPerLon / viewport.pxPerLat,
  };
}

export function fitView(
  points: readonly LatLon[],
  width: number,
  height: number,
  options?: Parameters<typeof fitViewport>[3],
): MapView {
  return viewportToView(fitViewport(points, width, height, options), width, height);
}

export function unproject(viewport: MapViewport, x: number, y: number): LatLon {
  return [viewport.latNorth - y / viewport.pxPerLat, viewport.lonWest + x / viewport.pxPerLon];
}

/** Самый мелкий масштаб — весь мир по ширине; самый крупный — ~6° на экран. */
export function clampView(view: MapView, width: number, height: number): MapView {
  const minPpd = Math.max(width / (360 * view.kx), height / ((LAT_MAX - LAT_MIN) + 24)) * 0.98;
  const maxPpd = Math.max(minPpd, Math.min(width, height) / 6);
  const ppd = Math.min(maxPpd, Math.max(minPpd, view.ppd));
  const halfLon = width / 2 / (ppd * view.kx);
  const halfLat = height / 2 / ppd;
  const cx = halfLon >= 180 ? 0 : Math.min(180 - halfLon, Math.max(-180 + halfLon, view.cx));
  const top = LAT_MAX + 8;
  const bottom = LAT_MIN - 8;
  const cy = halfLat * 2 >= top - bottom ? (top + bottom) / 2 : Math.min(top - halfLat, Math.max(bottom + halfLat, view.cy));
  return { ...view, ppd, cx, cy };
}

/** Шаг сглаживания к цели; масштаб — в логарифме, чтобы зум шёл равномерно. */
export function approachView(current: MapView, target: MapView, amount: number): MapView {
  const t = Math.min(1, Math.max(0, amount));
  return {
    cx: current.cx + (target.cx - current.cx) * t,
    cy: current.cy + (target.cy - current.cy) * t,
    ppd: Math.exp(Math.log(current.ppd) + (Math.log(target.ppd) - Math.log(current.ppd)) * t),
    kx: current.kx + (target.kx - current.kx) * t,
  };
}

export function viewsClose(a: MapView, b: MapView): boolean {
  const px = a.ppd;
  return Math.abs(a.cx - b.cx) * px * a.kx < 0.5 && Math.abs(a.cy - b.cy) * px < 0.5 && Math.abs(a.ppd / b.ppd - 1) < 0.002;
}

// ── Кластеры флагов в экранных координатах ───────────────────────────────

export interface ScreenMarker<T> {
  x: number;
  y: number;
  /** Чем больше, тем вероятнее стать главным флагом кластера. */
  weight: number;
  item: T;
}

export interface MarkerCluster<T> {
  x: number;
  y: number;
  members: ScreenMarker<T>[];
}

/**
 * Жадная кластеризация: маркер, чей кружок (radius — вместе с бейджем)
 * пересекается с уже стоящим кластером, входит в него. Центр кластера —
 * его главный (самый тяжёлый) маркер, поэтому флаг не «уплывает» от страны.
 */
export function clusterMarkers<T>(markers: readonly ScreenMarker<T>[], radius: number): MarkerCluster<T>[] {
  const sorted = [...markers].sort((a, b) => b.weight - a.weight);
  const clusters: MarkerCluster<T>[] = [];
  const minDistance = radius * 2;
  for (const marker of sorted) {
    const hit = clusters.find((cluster) => Math.hypot(cluster.x - marker.x, cluster.y - marker.y) < minDistance);
    if (hit) hit.members.push(marker);
    else clusters.push({ x: marker.x, y: marker.y, members: [marker] });
  }
  // Второй проход: два кластера, которые после слияния всё ещё касаются, объединяем.
  for (let i = 0; i < clusters.length; i++) {
    for (let j = clusters.length - 1; j > i; j--) {
      if (Math.hypot(clusters[i].x - clusters[j].x, clusters[i].y - clusters[j].y) < minDistance) {
        clusters[i].members.push(...clusters[j].members);
        clusters.splice(j, 1);
      }
    }
  }
  return clusters;
}

/**
 * Точка у края окна в направлении внеэкранного маркера (для стрелки).
 * Возвращает null, если маркер и так внутри прямоугольника.
 */
export function edgeAnchor(
  x: number,
  y: number,
  rect: { left: number; top: number; right: number; bottom: number },
): { x: number; y: number; angle: number } | null {
  if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) return null;
  const cx = (rect.left + rect.right) / 2;
  const cy = (rect.top + rect.bottom) / 2;
  const dx = x - cx;
  const dy = y - cy;
  const sx = dx === 0 ? Infinity : ((dx > 0 ? rect.right : rect.left) - cx) / dx;
  const sy = dy === 0 ? Infinity : ((dy > 0 ? rect.bottom : rect.top) - cy) / dy;
  const s = Math.min(sx, sy);
  return { x: cx + dx * s, y: cy + dy * s, angle: Math.atan2(dy, dx) };
}

/** `loc=DE` из ответа https://www.cloudflare.com/cdn-cgi/trace. */
export function parseTraceCountry(text: string): string | null {
  const line = text.split(/\r?\n/).find((value) => value.trim().startsWith("loc="));
  const code = line?.trim().slice(4).trim().toUpperCase() ?? "";
  return /^[A-Z]{2}$/.test(code) && code !== "XX" && code !== "T1" ? code : null;
}

/** Расстояние от точки до ломаной (для подсветки суши вдоль дуги). */
export function distanceToPolyline(x: number, y: number, points: readonly [number, number][]): number {
  let best = Infinity;
  for (let i = 1; i < points.length; i++) {
    const [ax, ay] = points[i - 1];
    const [bx, by] = points[i];
    const dx = bx - ax;
    const dy = by - ay;
    const length = dx * dx + dy * dy;
    const t = length ? Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / length)) : 0;
    best = Math.min(best, Math.hypot(x - (ax + dx * t), y - (ay + dy * t)));
  }
  return best;
}
