import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import worldMapJson from "../../assets/geo/nimbo-world-map.json";
import "./home-map.css";
import { api } from "../../lib/api";
import {
  approachView,
  arcControl,
  clampView,
  clusterMarkers,
  decodeLand,
  deviceHomeLocation,
  distanceToPolyline,
  edgeAnchor,
  fitView,
  project,
  quadPoint,
  unproject,
  viewToViewport,
  viewsClose,
  type LatLon,
  type MapView,
  type WorldMapData,
} from "../../lib/worldMap";
import type { SignalConnectionState } from "./SignalHome";

const WORLD = worldMapJson as unknown as WorldMapData;
let landCache: Float32Array | null = null;
const landPoints = () => (landCache ??= decodeLand(WORLD));

/** Кружок флага 30px + бейдж: столько занимает маркер на экране (радиус). */
const MARKER_RADIUS = 21;
const TARGET_RADIUS = 26;
const EDGE_INSET = 24;
/** Минимальный охват авто-окна по долготе: соседние страны различимы. */
const ROUTE_MIN_SPAN = 25;

export interface HomeMapServer {
  id: string;
  /** ISO 3166-1 alpha-2, уже определённый по имени сервера. */
  country: string;
  ping?: number | null;
}

export interface HomeMapLabels {
  youAreHere: string;
  region: string;
  pickCountry: (countryName: string, count: number) => string;
  cluster: (countryNames: string) => string;
  showServers: (countryName: string) => string;
  resetView: string;
  zoomIn: string;
  zoomOut: string;
}

export interface HomeMapDockInfo {
  /** Страна выхода по trace, если она отличается от страны сервера. */
  exitCountryName: string | null;
}

export interface HomeMapProps {
  servers: HomeMapServer[];
  /** Сервер, к которому тянется дуга: подключённый, выбранный или первый. */
  targetId: string | null;
  state: SignalConnectionState;
  locale: string;
  labels: HomeMapLabels;
  onPickServer?: (serverId: string) => void;
  /** Суммарная скорость трафика, байт/с; null — ядро её не сообщает. */
  trafficRate: number | null;
  /** Меняется на каждое новое подключение — для кеша страны выхода. */
  sessionKey: string | number | null;
  /** Панель сервера под картой (не поверх неё). */
  renderDock: (info: HomeMapDockInfo) => ReactNode;
}

interface Palette { land: string; accent: string; bg: string; dark: boolean }

function readPalette(el: HTMLElement): Palette {
  const style = getComputedStyle(el);
  const get = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
  const dark = document.body.dataset.theme !== "light";
  return {
    land: get("--color-text", dark ? "#f1f1f1" : "#202020"),
    accent: get("--universal-primary", dark ? "#e8e8e8" : "#202020"),
    bg: get("--signal-panel", dark ? "#1c1c1c" : "#ffffff"),
    dark,
  };
}

function useReducedMotion() {
  const query = typeof window !== "undefined" ? window.matchMedia?.("(prefers-reduced-motion: reduce)") : undefined;
  const [reduced, setReduced] = useState(() => query?.matches === true);
  useEffect(() => {
    if (!query) return;
    const update = () => setReduced(query.matches);
    query.addEventListener?.("change", update);
    return () => query.removeEventListener?.("change", update);
  }, [query]);
  return reduced;
}

function countryName(iso: string, locale: string): string {
  try {
    return new Intl.DisplayNames([locale], { type: "region" }).of(iso) ?? iso;
  } catch {
    return iso;
  }
}

/** Страна выхода: один запрос через туннель на каждое подключение/смену сервера. */
function useExitCountry(state: SignalConnectionState, targetId: string | null, sessionKey: string | number | null) {
  const key = `${targetId ?? ""}|${sessionKey ?? ""}`;
  const [exit, setExit] = useState<{ key: string; iso: string } | null>(null);
  useEffect(() => {
    if (state !== "connected") return;
    let alive = true;
    // Небольшая пауза: маршруты туннеля успевают подняться.
    const timer = window.setTimeout(() => {
      void api.networkExitCountry().then((iso) => {
        if (alive && iso && WORLD.countries[iso]) setExit({ key, iso });
      }).catch(() => undefined);
    }, 700);
    return () => { alive = false; window.clearTimeout(timer); };
  }, [state, key]);
  return state === "connected" && exit?.key === key ? exit.iso : null;
}

interface CountryGroup { iso: string; servers: HomeMapServer[]; point: LatLon }

export function HomeMap({ servers, targetId, state, locale, labels, onPickServer, trafficRate, sessionKey, renderDock }: HomeMapProps) {
  const frameRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [themeTick, setThemeTick] = useState(0);
  const [view, setView] = useState<MapView | null>(null);
  const [manual, setManual] = useState<MapView | null>(null);
  const [visible, setVisible] = useState(true);
  const reduced = useReducedMotion();
  const home = useMemo(() => deviceHomeLocation(WORLD), []);

  const countries = useMemo<CountryGroup[]>(() => {
    const groups = new Map<string, HomeMapServer[]>();
    for (const server of servers) {
      const iso = server.country.toUpperCase();
      if (!WORLD.countries[iso]) continue;
      const list = groups.get(iso) ?? [];
      list.push(server);
      groups.set(iso, list);
    }
    return [...groups.entries()].map(([iso, list]) => ({ iso, servers: list, point: WORLD.countries[iso] }));
  }, [servers]);

  const target = servers.find((server) => server.id === targetId) ?? null;
  const targetIso = target && WORLD.countries[target.country.toUpperCase()] ? target.country.toUpperCase() : null;
  const exitIso = useExitCountry(state, targetId, sessionKey);
  // Дуга идёт в реальную страну выхода, когда она известна.
  const routeIso = exitIso ?? targetIso;
  const routeEnd: LatLon | null = routeIso ? WORLD.countries[routeIso] : null;

  // ── Размер, тема, видимость ────────────────────────────────────────────
  useEffect(() => {
    const frame = frameRef.current;
    if (!frame) return;
    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect;
      setSize((prev) => (Math.abs(prev.width - width) < 1 && Math.abs(prev.height - height) < 1
        ? prev
        : { width: Math.round(width), height: Math.round(height) }));
    });
    observer.observe(frame);
    const themeObserver = new MutationObserver(() => setThemeTick((tick) => tick + 1));
    themeObserver.observe(document.body, { attributes: true, attributeFilter: ["data-theme", "style", "data-ui-style"] });
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["style", "class"] });
    // Карта ушла из вида (прокрутка) — кадры не считаем.
    const visibility = new IntersectionObserver(([entry]) => setVisible(entry.isIntersecting));
    visibility.observe(frame);
    return () => { observer.disconnect(); themeObserver.disconnect(); visibility.disconnect(); };
  }, []);

  // ── Авто-окно: «вы здесь» + маршрут, плавный подлёт при смене ─────────
  const autoView = useMemo<MapView | null>(() => {
    if (!size.width || !size.height) return null;
    const points: LatLon[] = routeEnd
      ? [home.point, routeEnd, ...(targetIso && targetIso !== routeIso ? [WORLD.countries[targetIso]] : [])]
      : [home.point, ...countries.map((c) => c.point)];
    const inset = { top: 34, right: 30, bottom: 30, left: 30 };
    // 25° — минимум для телефона; на широком окне охват растёт с шириной,
    // иначе точки суши становятся редкой ровной сеткой без очертаний.
    const minSpan = Math.max(ROUTE_MIN_SPAN, size.width / 26);
    return clampView(fitView(points, size.width, size.height, { padding: 0.2, minSpanLon: minSpan, insets: inset }), size.width, size.height);
  }, [size.width, size.height, home.point, routeEnd, routeIso, targetIso, countries]);

  // Новый маршрут или начало подключения возвращают авто-окно.
  useEffect(() => { setManual(null); }, [routeIso, targetId]);
  useEffect(() => { if (state === "connecting") setManual(null); }, [state]);

  const goalView = manual ?? autoView;
  const goalRef = useRef<MapView | null>(goalView);
  goalRef.current = goalView;
  const viewRef = useRef<MapView | null>(null);

  // ── Состояние анимации маршрута (живёт между кадрами) ───────────────────
  const anim = useRef({
    end: null as LatLon | null,
    progress: 0,
    solid: 0,
    retracting: false,
    lastState: state as SignalConnectionState,
    stateSince: 0,
  });
  const live = useRef({ state, routeEnd, trafficRate, reduced });
  live.current = { state, routeEnd, trafficRate, reduced };

  useEffect(() => {
    const a = anim.current;
    const prev = a.lastState;
    if (prev === state) return;
    a.lastState = state;
    a.stateSince = performance.now();
    if (state === "connecting" && (prev === "idle" || prev === "disconnecting")) {
      a.progress = 0;
      a.retracting = false;
    }
    if ((state === "disconnecting" || state === "idle") && (prev === "connected" || prev === "switching" || prev === "connecting")) {
      a.retracting = true;
    }
  }, [state]);

  // ── Кадровый цикл: вид + суша + маршрут ─────────────────────────────────
  useEffect(() => {
    const canvas = canvasRef.current;
    const frame = frameRef.current;
    if (!canvas || !frame || !size.width || !size.height || !visible) return;
    const palette = readPalette(frame);
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    canvas.width = Math.round(size.width * dpr);
    canvas.height = Math.round(size.height * dpr);
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const layer = document.createElement("canvas");
    layer.width = canvas.width;
    layer.height = canvas.height;
    const lctx = layer.getContext("2d");
    if (!lctx) return;
    const land = landPoints();
    let layerKey = "";
    let raf = 0;
    let last = 0;
    let lastPublished: MapView | null = null;

    const drawLand = (current: MapView, path: [number, number][] | null, corridor: number) => {
      const viewport = viewToViewport(current, size.width, size.height);
      lctx.setTransform(1, 0, 0, 1, 0, 0);
      lctx.clearRect(0, 0, layer.width, layer.height);
      lctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const cell = viewport.pxPerLat * WORLD.grid.step;
      const radius = Math.max(0.6, Math.min(3.8, cell * 0.22));
      const baseAlpha = palette.dark ? 0.19 : 0.24;
      // Три корзины яркости — три заливки вместо тысяч.
      const buckets: number[][] = [[], [], []];
      const reach = 34;
      for (let i = 0; i < land.length; i += 2) {
        const lat = land[i + 1];
        const lon = land[i];
        const x = (lon - viewport.lonWest) * viewport.pxPerLon;
        const y = (viewport.latNorth - lat) * viewport.pxPerLat;
        if (x < -6 || y < -6 || x > size.width + 6 || y > size.height + 6) continue;
        let bucket = 0;
        if (path && corridor > 0) {
          const d = distanceToPolyline(x, y, path);
          if (d < reach * 0.45) bucket = 2;
          else if (d < reach) bucket = 1;
        }
        buckets[bucket].push(x, y);
      }
      const fills = [
        { color: palette.land, alpha: baseAlpha },
        { color: palette.accent, alpha: baseAlpha + (palette.dark ? 0.14 : 0.12) * corridor },
        { color: palette.accent, alpha: baseAlpha + (palette.dark ? 0.32 : 0.26) * corridor },
      ];
      buckets.forEach((points, index) => {
        if (!points.length) return;
        lctx.globalAlpha = fills[index].alpha;
        lctx.fillStyle = fills[index].color;
        lctx.beginPath();
        for (let p = 0; p < points.length; p += 2) {
          lctx.moveTo(points[p] + radius, points[p + 1]);
          lctx.arc(points[p], points[p + 1], radius, 0, Math.PI * 2);
        }
        lctx.fill();
      });
      lctx.globalAlpha = 1;
    };

    const draw = (now: number, dt: number) => {
      const { state: s, routeEnd: goalEnd, trafficRate: rate, reduced: still } = live.current;
      const a = anim.current;
      const goal = goalRef.current;
      if (!goal) return;

      // Вид: экспоненциальное сближение с целью (~200 мс).
      let current = viewRef.current ?? goal;
      current = still ? goal : approachView(current, goal, 1 - Math.exp(-dt / 190));
      if (viewsClose(current, goal)) current = goal;
      viewRef.current = current;
      if (!lastPublished || !viewsClose(lastPublished, current) || current === goal && lastPublished !== goal) {
        lastPublished = current;
        setView(current);
      }

      // Конец дуги перетекает к новой стране (морфинг), а не прыгает.
      if (goalEnd) {
        if (!a.end || still) a.end = goalEnd;
        else {
          const k = 1 - Math.exp(-dt / 260);
          a.end = [a.end[0] + (goalEnd[0] - a.end[0]) * k, a.end[1] + (goalEnd[1] - a.end[1]) * k];
        }
      }
      const tunnel = s === "connected" || s === "connecting" || s === "switching";
      // Прорисовка / втягивание дуги.
      if (a.retracting) {
        a.progress = still ? 0 : Math.max(0, a.progress - dt / 650);
        if (a.progress <= 0) a.retracting = false;
      } else if (s === "connecting" || s === "switching") {
        a.progress = still ? 1 : Math.min(1, a.progress + dt / 1000);
      } else if (s === "connected") {
        a.progress = 1;
      } else if (s === "disconnecting") {
        a.progress = still ? 0 : Math.max(0, a.progress - dt / 650);
      } else {
        a.progress = still ? 1 : Math.min(1, a.progress + dt / 700);
      }
      const solidGoal = tunnel || a.retracting || s === "disconnecting" ? 1 : 0;
      a.solid = still ? solidGoal : a.solid + (solidGoal - a.solid) * (1 - Math.exp(-dt / 220));

      const viewport = viewToViewport(current, size.width, size.height);
      const from = project(viewport, home.point[0], home.point[1]);
      const to = a.end && goalEnd ? project(viewport, a.end[0], a.end[1]) : null;
      const control = to ? arcControl(from, to) : null;
      const steps = 48;
      const fullPath: [number, number][] | null = to && control
        ? Array.from({ length: 17 }, (_, i) => quadPoint(from, control, to, i / 16))
        : null;

      const corridor = fullPath ? 0.45 + 0.55 * a.solid : 0;
      const key = `${current.cx.toFixed(3)}|${current.cy.toFixed(3)}|${current.ppd.toFixed(3)}|${to ? `${to[0].toFixed(0)},${to[1].toFixed(0)}` : "-"}|${corridor.toFixed(2)}`;
      if (key !== layerKey) {
        layerKey = key;
        drawLand(current, fullPath, corridor);
      }

      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(layer, 0, 0);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const t = now / 1000;

      if (to && control && a.progress > 0.001) {
        const drawn = a.progress;
        const eased = still ? 1 : 1 - Math.pow(1 - drawn, 3);
        const tracePath = () => {
          ctx.beginPath();
          ctx.moveTo(from[0], from[1]);
          const count = Math.max(1, Math.round(steps * eased));
          for (let i = 1; i <= count; i++) {
            const [x, y] = quadPoint(from, control, to, (i / steps) * (eased * steps / count));
            ctx.lineTo(x, y);
          }
        };
        ctx.lineCap = "round";
        ctx.strokeStyle = palette.accent;
        // Подсказка «не подключено»: заметный пунктир.
        const hint = 1 - a.solid;
        if (hint > 0.01) {
          ctx.globalAlpha = (palette.dark ? 0.78 : 0.7) * hint;
          ctx.lineWidth = 2;
          ctx.setLineDash([6, 7]);
          ctx.lineDashOffset = still ? 0 : -t * 14;
          tracePath();
          ctx.stroke();
          ctx.setLineDash([]);
        }
        if (a.solid > 0.01) {
          ctx.save();
          ctx.shadowColor = palette.accent;
          ctx.shadowBlur = 16;
          ctx.globalAlpha = 0.42 * a.solid;
          ctx.lineWidth = 5;
          tracePath();
          ctx.stroke();
          ctx.restore();
          ctx.globalAlpha = 0.95 * a.solid;
          ctx.lineWidth = 2.2;
          tracePath();
          ctx.stroke();
        }
        const glowDot = (x: number, y: number, r: number, alpha: number) => {
          const g = ctx.createRadialGradient(x, y, 0, x, y, r);
          g.addColorStop(0, palette.accent);
          g.addColorStop(1, "transparent");
          ctx.globalAlpha = alpha;
          ctx.fillStyle = g;
          ctx.beginPath();
          ctx.arc(x, y, r, 0, Math.PI * 2);
          ctx.fill();
        };
        if (!still) {
          // Комета: голова прорисовки, затем петли по готовой дуге.
          if (s === "connecting" || s === "switching") {
            const head = drawn < 1 ? eased : (t / 1.1) % 1;
            for (let k = 0; k < 6; k++) {
              const p = Math.max(0, head - k * 0.018);
              const [x, y] = quadPoint(from, control, to, p);
              glowDot(x, y, 11 - k * 1.3, 0.9 - k * 0.14);
            }
          }
          // Поток частиц от вас к серверу; плотнее и быстрее при трафике.
          if (s === "connected" && drawn >= 1) {
            const kb = rate == null ? 40 : Math.max(0, rate) / 1024;
            const count = rate == null ? 12 : Math.round(8 + Math.min(18, Math.log2(1 + kb / 16) * 2.4));
            const speed = rate == null ? 0.32 : 0.2 + Math.min(0.75, Math.log10(1 + kb) * 0.17);
            for (let k = 0; k < count; k++) {
              const p = (k / count + t * speed) % 1;
              const [x, y] = quadPoint(from, control, to, p);
              const fade = Math.sin(Math.PI * p);
              glowDot(x, y, 5.5, 0.85 * fade);
            }
          }
        }
        // Конечная точка: пульс при подключении, кольца при работающем туннеле.
        if (drawn >= 0.98) {
          const [ex, ey] = to;
          ctx.lineWidth = 1.6;
          ctx.strokeStyle = palette.accent;
          if (s === "connected") {
            for (let k = 0; k < 2; k++) {
              const phase = still ? 0.4 + k * 0.3 : ((t / 2.4) + k / 2) % 1;
              ctx.globalAlpha = 0.55 * (1 - phase);
              ctx.beginPath();
              ctx.arc(ex, ey, TARGET_RADIUS * 0.6 + phase * 30, 0, Math.PI * 2);
              ctx.stroke();
            }
          } else if ((s === "connecting" || s === "switching") && !still) {
            const phase = (t / 0.9) % 1;
            ctx.globalAlpha = 0.7 * (1 - phase);
            ctx.beginPath();
            ctx.arc(ex, ey, TARGET_RADIUS * 0.6 + phase * 18, 0, Math.PI * 2);
            ctx.stroke();
          }
          if (exitDiffers.current) {
            ctx.globalAlpha = 1;
            ctx.fillStyle = palette.bg;
            ctx.beginPath();
            ctx.arc(ex, ey, 6, 0, Math.PI * 2);
            ctx.fill();
            ctx.fillStyle = palette.accent;
            ctx.beginPath();
            ctx.arc(ex, ey, 3.8, 0, Math.PI * 2);
            ctx.fill();
          }
        }
        ctx.globalAlpha = 1;
      }

      // «Вы здесь»: дыхание без туннеля, расходящееся кольцо с туннелем.
      ctx.strokeStyle = palette.accent;
      ctx.fillStyle = palette.accent;
      if (still) {
        ctx.globalAlpha = 0.25;
        ctx.beginPath();
        ctx.arc(from[0], from[1], 11, 0, Math.PI * 2);
        ctx.fill();
      } else if (tunnel) {
        const phase = (t / 2.4) % 1;
        ctx.lineWidth = 1.5;
        ctx.globalAlpha = 0.6 * (1 - phase);
        ctx.beginPath();
        ctx.arc(from[0], from[1], 7 + phase * 16, 0, Math.PI * 2);
        ctx.stroke();
      } else {
        const breath = (Math.sin(t * 1.6) + 1) / 2;
        ctx.globalAlpha = 0.16 + breath * 0.16;
        ctx.beginPath();
        ctx.arc(from[0], from[1], 9 + breath * 4, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;
      ctx.fillStyle = palette.bg;
      ctx.beginPath();
      ctx.arc(from[0], from[1], 6.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = palette.accent;
      ctx.beginPath();
      ctx.arc(from[0], from[1], 4.2, 0, Math.PI * 2);
      ctx.fill();
    };

    // ~30 кадров/с: дуге и частицам хватает, процессор отдыхает.
    const tick = (now: number) => {
      raf = requestAnimationFrame(tick);
      if (last && now - last < 32) return;
      const dt = last ? Math.min(100, now - last) : 16;
      last = now;
      draw(now, dt);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
    // themeTick — перечитать цвета после смены темы/акцента.
  }, [size.width, size.height, visible, home.point, themeTick]);

  const exitDiffers = useRef(false);
  exitDiffers.current = !!exitIso && exitIso !== targetIso;

  // ── Жесты: колесо — зум, перетаскивание — сдвиг, двойной клик — авто ────
  const baseView = () => viewRef.current ?? goalRef.current;
  const zoomAt = useCallback((factor: number, x: number, y: number) => {
    const base = manual ?? goalRef.current;
    if (!base || !size.width) return;
    const vp = viewToViewport(base, size.width, size.height);
    const [lat, lon] = unproject(vp, x, y);
    const ppd = base.ppd * factor;
    const next = clampView({
      ...base,
      ppd,
      cx: lon - (x - size.width / 2) / (ppd * base.kx),
      cy: lat + (y - size.height / 2) / ppd,
    }, size.width, size.height);
    setManual(next);
  }, [manual, size.width, size.height]);

  useEffect(() => {
    const frame = frameRef.current;
    if (!frame) return;
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const rect = frame.getBoundingClientRect();
      zoomAt(Math.exp(-event.deltaY * 0.0016), event.clientX - rect.left, event.clientY - rect.top);
    };
    frame.addEventListener("wheel", onWheel, { passive: false });
    return () => frame.removeEventListener("wheel", onWheel);
  }, [zoomAt]);

  const pointers = useRef(new Map<number, { x: number; y: number }>());
  const gesture = useRef<{ view: MapView; x: number; y: number; distance: number } | null>(null);
  const [dragging, setDragging] = useState(false);

  const startGesture = () => {
    const base = baseView();
    const list = [...pointers.current.values()];
    if (!base || !list.length) return;
    const x = list.reduce((sum, p) => sum + p.x, 0) / list.length;
    const y = list.reduce((sum, p) => sum + p.y, 0) / list.length;
    const distance = list.length > 1 ? Math.hypot(list[0].x - list[1].x, list[0].y - list[1].y) : 0;
    gesture.current = { view: base, x, y, distance };
  };

  const onPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if ((event.target as HTMLElement).closest("button")) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    pointers.current.set(event.pointerId, { x: event.clientX, y: event.clientY });
    startGesture();
    setDragging(true);
  };
  const onPointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!pointers.current.has(event.pointerId) || !gesture.current) return;
    pointers.current.set(event.pointerId, { x: event.clientX, y: event.clientY });
    const list = [...pointers.current.values()];
    const g = gesture.current;
    const x = list.reduce((sum, p) => sum + p.x, 0) / list.length;
    const y = list.reduce((sum, p) => sum + p.y, 0) / list.length;
    const scale = list.length > 1 && g.distance > 0 ? Math.hypot(list[0].x - list[1].x, list[0].y - list[1].y) / g.distance : 1;
    const ppd = g.view.ppd * scale;
    const next = clampView({
      ...g.view,
      ppd,
      cx: g.view.cx - (x - g.x) / (ppd * g.view.kx),
      cy: g.view.cy + (y - g.y) / ppd,
    }, size.width, size.height);
    viewRef.current = next; // без инерции: карта идёт за пальцем
    setManual(next);
  };
  const onPointerUp = (event: React.PointerEvent<HTMLDivElement>) => {
    pointers.current.delete(event.pointerId);
    if (pointers.current.size) startGesture();
    else { gesture.current = null; setDragging(false); }
  };

  const flyTo = (points: LatLon[], minSpanLon = 8) => {
    if (!size.width) return;
    setManual(clampView(fitView(points, size.width, size.height, { padding: 0.3, minSpanLon, insets: { top: 34, right: 30, bottom: 30, left: 30 } }), size.width, size.height));
  };

  // ── Флаги, кластеры, стрелки ────────────────────────────────────────────
  const layout = useMemo(() => {
    if (!view || !size.width) return null;
    const viewport = viewToViewport(view, size.width, size.height);
    const inner = { left: EDGE_INSET, top: EDGE_INSET, right: size.width - EDGE_INSET, bottom: size.height - EDGE_INSET };
    const projected = countries.map((group) => {
      const [x, y] = project(viewport, group.point[0], group.point[1]);
      return { group, x, y };
    });
    const targetMarker = projected.find((m) => m.group.iso === targetIso) ?? null;
    const onScreen = projected.filter((m) => m !== targetMarker && !edgeAnchor(m.x, m.y, inner));
    const offScreen = projected.filter((m) => m !== targetMarker && edgeAnchor(m.x, m.y, inner));
    // Флаги, которые легли бы под активный, прячутся в его бейдж «+N».
    const underTarget = targetMarker && !edgeAnchor(targetMarker.x, targetMarker.y, inner)
      ? onScreen.filter((m) => Math.hypot(m.x - targetMarker.x, m.y - targetMarker.y) < MARKER_RADIUS + TARGET_RADIUS)
      : [];
    const free = onScreen.filter((m) => !underTarget.includes(m));
    const clusters = clusterMarkers(free.map((m) => ({ x: m.x, y: m.y, weight: m.group.servers.length, item: m.group })), MARKER_RADIUS);
    const arrows = clusterMarkers(
      offScreen.map((m) => {
        const anchor = edgeAnchor(m.x, m.y, inner)!;
        return { x: anchor.x, y: anchor.y, weight: m.group.servers.length, item: { group: m.group, angle: anchor.angle } };
      }),
      16,
    );
    // Стрелка у края не должна лечь на флаг: если задевает — её страны
    // уходят в этот кластер (или в бейдж активного флага).
    const underTargetGroups = underTarget.map((m) => m.group);
    const targetOnScreen = targetMarker && !edgeAnchor(targetMarker.x, targetMarker.y, inner) ? targetMarker : null;
    const keptArrows = arrows.filter((arrow) => {
      const groups = arrow.members.map((member) => member.item.group);
      if (targetOnScreen && Math.hypot(arrow.x - targetOnScreen.x, arrow.y - targetOnScreen.y) < TARGET_RADIUS + 16 + 2) {
        underTargetGroups.push(...groups);
        return false;
      }
      const hit = clusters.find((cluster) => Math.hypot(cluster.x - arrow.x, cluster.y - arrow.y) < MARKER_RADIUS + 16 + 2);
      if (!hit) return true;
      hit.members.push(...groups.map((group) => ({ x: arrow.x, y: arrow.y, weight: group.servers.length, item: group })));
      return false;
    });
    const youAt = project(viewport, home.point[0], home.point[1]);
    // Подпись «Вы здесь» — в ту сторону, где нет флагов.
    const obstacles = [
      ...clusters.map((c) => ({ x: c.x, y: c.y, r: MARKER_RADIUS })),
      ...(targetMarker ? [{ x: targetMarker.x, y: targetMarker.y, r: TARGET_RADIUS }] : []),
      ...keptArrows.map((a) => ({ x: a.x, y: a.y, r: 16 })),
    ];
    const labelWidth = 84;
    const candidates = [
      { dx: 0, dy: 26, align: "center" },
      { dx: 0, dy: -26, align: "center" },
      { dx: 16 + labelWidth / 2, dy: 0, align: "center" },
      { dx: -16 - labelWidth / 2, dy: 0, align: "center" },
    ];
    const fits = (c: { dx: number; dy: number }) => {
      const lx = youAt[0] + c.dx;
      const ly = youAt[1] + c.dy;
      if (lx - labelWidth / 2 < 4 || lx + labelWidth / 2 > size.width - 4 || ly < 10 || ly > size.height - 10) return false;
      return obstacles.every((o) => {
        const nx = Math.max(lx - labelWidth / 2, Math.min(o.x, lx + labelWidth / 2));
        const ny = Math.max(ly - 10, Math.min(o.y, ly + 10));
        return Math.hypot(o.x - nx, o.y - ny) > o.r + 2;
      });
    };
    const label = candidates.find(fits) ?? candidates[0];
    return {
      clusters,
      arrows: keptArrows,
      targetMarker,
      underTarget: underTargetGroups,
      you: { x: youAt[0] + label.dx, y: youAt[1] + label.dy, visible: !edgeAnchor(youAt[0], youAt[1], { left: 0, top: 0, right: size.width, bottom: size.height }) },
    };
  }, [view, size.width, size.height, countries, targetIso, home.point]);

  const pickInCountry = (group: CountryGroup) => {
    if (!onPickServer) return;
    if (group.servers.some((server) => server.id === targetId)) return;
    const ranked = [...group.servers].sort((a, b) => (a.ping ?? Infinity) - (b.ping ?? Infinity));
    onPickServer(ranked[0].id);
  };
  const atMaxZoom = !!view && !!size.width && clampView({ ...view, ppd: view.ppd * 1.5 }, size.width, size.height).ppd <= view.ppd * 1.01;
  const exitName = exitIso && exitIso !== targetIso ? countryName(exitIso, locale) : null;

  return (
    <section className="home-map" aria-label={labels.region} data-state={state}>
      <div
        className={`home-map-frame${dragging ? " is-dragging" : ""}`}
        ref={frameRef}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onDoubleClick={(event) => { if (!(event.target as HTMLElement).closest("button")) setManual(null); }}
      >
        <canvas ref={canvasRef} className="home-map-canvas" aria-hidden="true" style={{ width: size.width, height: size.height }} />
        {layout?.you.visible && (
          <span className="home-map-you" style={{ left: layout.you.x, top: layout.you.y }}>{labels.youAreHere}</span>
        )}
        {layout?.clusters.map((cluster) => {
          const main = cluster.members[0].item;
          const name = countryName(main.iso, locale);
          if (cluster.members.length === 1) {
            return (
              <button
                key={main.iso}
                type="button"
                className="home-map-flag"
                style={{ left: cluster.x, top: cluster.y }}
                title={labels.pickCountry(name, main.servers.length)}
                aria-label={labels.pickCountry(name, main.servers.length)}
                onClick={() => pickInCountry(main)}
              >
                <span className={`fi fis fi-${main.iso.toLowerCase()}`} aria-hidden="true" />
                {main.servers.length > 1 && <small aria-hidden="true">{main.servers.length}</small>}
              </button>
            );
          }
          const names = cluster.members.map((member) => countryName(member.item.iso, locale)).join(", ");
          return (
            <button
              key={`cluster-${main.iso}`}
              type="button"
              className="home-map-flag is-cluster"
              style={{ left: cluster.x, top: cluster.y }}
              title={labels.cluster(names)}
              aria-label={labels.cluster(names)}
              onClick={() => (atMaxZoom ? pickInCountry(main) : flyTo(cluster.members.map((member) => member.item.point), 4))}
            >
              <span className={`fi fis fi-${main.iso.toLowerCase()}`} aria-hidden="true" />
              <small aria-hidden="true">+{cluster.members.length - 1}</small>
            </button>
          );
        })}
        {layout?.targetMarker && (() => {
          const { group, x, y } = layout.targetMarker;
          const name = countryName(group.iso, locale);
          const hidden = layout.underTarget;
          const anchor = edgeAnchor(x, y, { left: EDGE_INSET, top: EDGE_INSET, right: size.width - EDGE_INSET, bottom: size.height - EDGE_INSET });
          return (
            <button
              type="button"
              className="home-map-flag is-target"
              style={{ left: anchor ? anchor.x : x, top: anchor ? anchor.y : y }}
              title={hidden.length ? labels.cluster([name, ...hidden.map((g) => countryName(g.iso, locale))].join(", ")) : labels.pickCountry(name, group.servers.length)}
              aria-label={labels.pickCountry(name, group.servers.length)}
              aria-pressed="true"
              onClick={() => { if (hidden.length && !atMaxZoom) flyTo([group.point, ...hidden.map((g) => g.point)], 4); else if (anchor) flyTo([home.point, group.point], ROUTE_MIN_SPAN); }}
            >
              <span className={`fi fis fi-${group.iso.toLowerCase()}`} aria-hidden="true" />
              {hidden.length > 0 && <small aria-hidden="true">+{hidden.length}</small>}
            </button>
          );
        })()}
        {layout?.arrows.map((arrow) => {
          const main = arrow.members[0].item;
          const groups = arrow.members.map((member) => member.item.group);
          const names = groups.map((g) => countryName(g.iso, locale)).join(", ");
          return (
            <button
              key={`edge-${main.group.iso}`}
              type="button"
              className="home-map-edge"
              style={{ left: arrow.x, top: arrow.y }}
              title={labels.showServers(names)}
              aria-label={labels.showServers(names)}
              onClick={() => flyTo([home.point, ...groups.map((g) => g.point)], ROUTE_MIN_SPAN)}
            >
              <i aria-hidden="true" style={{ transform: `rotate(${main.angle}rad)` }} />
              <span className={`fi fis fi-${main.group.iso.toLowerCase()}`} aria-hidden="true" />
              {groups.length > 1 && <small aria-hidden="true">+{groups.length - 1}</small>}
            </button>
          );
        })}
        <div className="home-map-tools">
          <button type="button" onClick={() => zoomAt(1.5, size.width / 2, size.height / 2)} aria-label={labels.zoomIn} title={labels.zoomIn}>+</button>
          <button type="button" onClick={() => zoomAt(1 / 1.5, size.width / 2, size.height / 2)} aria-label={labels.zoomOut} title={labels.zoomOut}>−</button>
          {manual && (
            <button type="button" className="home-map-reset" onClick={() => setManual(null)} aria-label={labels.resetView} title={labels.resetView}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 12a8 8 0 1 0 2.4-5.7M4 4v4h4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
            </button>
          )}
        </div>
      </div>
      <div className="home-map-dock">{renderDock({ exitCountryName: exitName })}</div>
    </section>
  );
}
