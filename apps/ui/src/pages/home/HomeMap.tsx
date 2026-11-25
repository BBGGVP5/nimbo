import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import worldMapJson from "../../assets/geo/nimbo-world-map.json";
import "./home-map.css";
import {
  arcControl,
  decodeLand,
  deviceHomeLocation,
  fitViewport,
  project,
  quadPoint,
  type LatLon,
  type MapViewport,
  type WorldMapData,
} from "../../lib/worldMap";
import type { SignalConnectionState } from "./SignalHome";

const WORLD = worldMapJson as unknown as WorldMapData;
let landCache: Float32Array | null = null;
const landPoints = () => (landCache ??= decodeLand(WORLD));

export interface HomeMapServer {
  id: string;
  /** ISO 3166-1 alpha-2, уже определённый по имени сервера. */
  country: string;
  ping?: number | null;
}

export interface HomeMapProps {
  servers: HomeMapServer[];
  /** Сервер, к которому тянется дуга: подключённый, выбранный или первый. */
  targetId: string | null;
  state: SignalConnectionState;
  locale: string;
  youAreHereLabel: string;
  regionLabel: string;
  pickLabel: (countryName: string, count: number) => string;
  onPickServer?: (serverId: string) => void;
  /** Карточка сервера с кнопкой — поверх карты. */
  card: ReactNode;
}

interface Palette { land: string; landStrong: string; accent: string; bg: string; dark: boolean }

function readPalette(el: HTMLElement): Palette {
  const style = getComputedStyle(el);
  const get = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
  const dark = document.body.dataset.theme !== "light";
  return {
    land: get("--color-text", dark ? "#f1f1f1" : "#202020"),
    landStrong: get("--color-text", dark ? "#f1f1f1" : "#202020"),
    accent: get("--universal-primary", dark ? "#e8e8e8" : "#202020"),
    bg: get("--color-bg", dark ? "#121212" : "#f5f4f1"),
    dark,
  };
}

function prefersReducedMotion() {
  return typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches === true;
}

function countryName(iso: string, locale: string): string {
  try {
    return new Intl.DisplayNames([locale], { type: "region" }).of(iso) ?? iso;
  } catch {
    return iso;
  }
}

export function HomeMap({ servers, targetId, state, locale, youAreHereLabel, regionLabel, pickLabel, onPickServer, card }: HomeMapProps) {
  const frameRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const cardRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });
  // Высота карточки поверх карты: точки вписываются над ней.
  const [cardInset, setCardInset] = useState(0);
  const [themeTick, setThemeTick] = useState(0);
  const home = useMemo(() => deviceHomeLocation(WORLD), []);

  // Серверы группируются по стране: один флаг на страну.
  const countries = useMemo(() => {
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

  const viewport = useMemo<MapViewport | null>(() => {
    if (!size.width || !size.height) return null;
    const points: LatLon[] = [home.point, ...countries.map((c) => c.point)];
    return fitViewport(points, size.width, size.height, {
      insets: { top: 8, right: 0, bottom: cardInset, left: 0 },
    });
  }, [size.width, size.height, home.point, countries, cardInset]);

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
    const card = cardRef.current;
    const cardObserver = new ResizeObserver(() => {
      if (!card) return;
      const overlay = getComputedStyle(card).position === "absolute";
      const next = overlay ? Math.round(card.offsetHeight + 24) : 0;
      setCardInset((prev) => (Math.abs(prev - next) < 2 ? prev : next));
    });
    if (card) cardObserver.observe(card);
    // Тема и акцент меняются атрибутами/стилями на body и html.
    const themeObserver = new MutationObserver(() => setThemeTick((tick) => tick + 1));
    themeObserver.observe(document.body, { attributes: true, attributeFilter: ["data-theme", "style", "data-ui-style"] });
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["style", "class"] });
    return () => { observer.disconnect(); cardObserver.disconnect(); themeObserver.disconnect(); };
  }, []);

  // Слой суши рисуется один раз на размер/тему, анимация только накладывает дугу.
  const landLayer = useMemo(() => {
    const frame = frameRef.current;
    if (!viewport || !frame) return null;
    const palette = readPalette(frame);
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const layer = document.createElement("canvas");
    layer.width = Math.round(size.width * dpr);
    layer.height = Math.round(size.height * dpr);
    const ctx = layer.getContext("2d");
    if (!ctx) return null;
    ctx.scale(dpr, dpr);
    const cell = viewport.pxPerLat * WORLD.grid.step;
    const radius = Math.max(0.7, Math.min(3.1, cell * 0.27));
    ctx.fillStyle = palette.land;
    ctx.globalAlpha = palette.dark ? 0.2 : 0.26;
    const land = landPoints();
    ctx.beginPath();
    for (let i = 0; i < land.length; i += 2) {
      const [x, y] = project(viewport, land[i + 1], land[i]);
      if (x < -4 || y < -4 || x > size.width + 4 || y > size.height + 4) continue;
      ctx.moveTo(x + radius, y);
      ctx.arc(x, y, radius, 0, Math.PI * 2);
    }
    ctx.fill();
    // Подсветка стран с серверами: точки вокруг города чуть ярче.
    ctx.globalAlpha = palette.dark ? 0.42 : 0.38;
    ctx.fillStyle = palette.accent;
    ctx.beginPath();
    for (const { point } of countries) {
      for (let i = 0; i < land.length; i += 2) {
        const dLat = land[i + 1] - point[0];
        const dLon = land[i] - point[1];
        if (dLat * dLat + dLon * dLon > 9) continue;
        const [x, y] = project(viewport, land[i + 1], land[i]);
        ctx.moveTo(x + radius, y);
        ctx.arc(x, y, radius, 0, Math.PI * 2);
      }
    }
    ctx.fill();
    return { canvas: layer, palette, dpr };
    // themeTick — намеренная зависимость: перечитать цвета после смены темы.
  }, [viewport, size.width, size.height, countries, themeTick]);

  // Анимация дуги и «вы здесь».
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !viewport || !landLayer) return;
    canvas.width = landLayer.canvas.width;
    canvas.height = landLayer.canvas.height;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const { palette, dpr } = landLayer;
    const from = project(viewport, home.point[0], home.point[1]);
    const to = targetIso ? project(viewport, WORLD.countries[targetIso][0], WORLD.countries[targetIso][1]) : null;
    const control = to ? arcControl(from, to) : null;
    const reduced = prefersReducedMotion();
    const started = performance.now();
    const live = state === "connected" || state === "connecting" || state === "switching";
    let raf = 0;

    const draw = (now: number) => {
      const elapsed = now - started;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(landLayer.canvas, 0, 0);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

      if (to && control) {
        const grow = reduced ? 1 : Math.min(1, elapsed / 900);        const eased = 1 - Math.pow(1 - grow, 3);
        const steps = 64;
        const path = () => {
          ctx.beginPath();
          ctx.moveTo(from[0], from[1]);
          for (let i = 1; i <= steps * eased; i++) {
            const [x, y] = quadPoint(from, control, to, i / steps);
            ctx.lineTo(x, y);
          }
        };
        ctx.lineCap = "round";
        ctx.strokeStyle = palette.accent;
        if (state === "connected") {
          // Мягкое свечение под линией.
          ctx.save();
          ctx.shadowColor = palette.accent;
          ctx.shadowBlur = 14;
          ctx.globalAlpha = 0.5;
          ctx.lineWidth = 3.5;
          path();
          ctx.stroke();
          ctx.restore();
          ctx.globalAlpha = 0.95;
          ctx.lineWidth = 2;
          ctx.setLineDash([]);
          path();
          ctx.stroke();
        } else {
          ctx.globalAlpha = live ? 0.85 : 0.55;
          ctx.lineWidth = 1.6;
          ctx.setLineDash([5, 6]);
          ctx.lineDashOffset = live && !reduced ? -elapsed / 28 : 0;
          path();
          ctx.stroke();
          ctx.setLineDash([]);
        }
        // Бегущий огонёк по дуге, пока туннель поднят.
        if (state === "connected" && grow >= 1 && !reduced) {
          const t = (elapsed / 2200) % 1;
          const [px, py] = quadPoint(from, control, to, t);
          const glow = ctx.createRadialGradient(px, py, 0, px, py, 10);
          glow.addColorStop(0, palette.accent);
          glow.addColorStop(1, "transparent");
          ctx.globalAlpha = 0.9;
          ctx.fillStyle = glow;
          ctx.beginPath();
          ctx.arc(px, py, 10, 0, Math.PI * 2);
          ctx.fill();
        }
        ctx.globalAlpha = 1;
      }

      // «Вы здесь»: точка с расходящимся кольцом.
      const pulse = reduced ? 0.5 : (elapsed % 2400) / 2400;
      ctx.strokeStyle = palette.accent;
      ctx.lineWidth = 1.5;
      ctx.globalAlpha = 0.6 * (1 - pulse);
      ctx.beginPath();
      ctx.arc(from[0], from[1], 6 + pulse * 14, 0, Math.PI * 2);
      ctx.stroke();
      ctx.globalAlpha = 1;
      ctx.fillStyle = palette.bg;
      ctx.beginPath();
      ctx.arc(from[0], from[1], 6.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = palette.accent;
      ctx.beginPath();
      ctx.arc(from[0], from[1], 4, 0, Math.PI * 2);
      ctx.fill();

      // Пульс «вы здесь» живой всегда; при «меньше движения» рисуем один кадр.
      if (!reduced) raf = requestAnimationFrame(tick);
    };
    // ~30 кадров/с достаточно для дуги и пульса и вдвое дешевле для GPU.
    let last = 0;
    const tick = (now: number) => {
      if (now - last < 32) { raf = requestAnimationFrame(tick); return; }
      last = now;
      draw(now);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [viewport, landLayer, home.point, targetIso, state]);

  const youAt = viewport ? project(viewport, home.point[0], home.point[1]) : null;

  return (
    <section className="home-map" aria-label={regionLabel} data-state={state}>
      <div className="home-map-frame" ref={frameRef}>
        <canvas ref={canvasRef} className="home-map-canvas" aria-hidden="true" style={{ width: size.width, height: size.height }} />
        {viewport && youAt && (
          <span className="home-map-you" style={{ left: youAt[0], top: youAt[1] }}>{youAreHereLabel}</span>
        )}
        {viewport && countries.map(({ iso, servers: list, point }) => {
          const [x, y] = project(viewport, point[0], point[1]);
          const name = countryName(iso, locale);
          const isTarget = iso === targetIso;
          const pick = () => {
            if (!onPickServer) return;
            const current = list.find((server) => server.id === targetId);
            if (current) return;
            const ranked = [...list].sort((a, b) => (a.ping ?? Infinity) - (b.ping ?? Infinity));
            onPickServer(ranked[0].id);
          };
          return (
            <button
              key={iso}
              type="button"
              className={`home-map-flag${isTarget ? " is-target" : ""}`}
              style={{ left: Math.min(size.width - 18, Math.max(18, x)), top: Math.min(size.height - 18, Math.max(18, y)) }}
              title={pickLabel(name, list.length)}
              aria-label={pickLabel(name, list.length)}
              aria-pressed={isTarget}
              onClick={pick}
            >
              <span className={`fi fis fi-${iso.toLowerCase()}`} aria-hidden="true" />
              {list.length > 1 && <small aria-hidden="true">{list.length}</small>}
            </button>
          );
        })}
      </div>
      <div className="home-map-card" ref={cardRef}>{card}</div>
    </section>
  );
}

export function mapCountryName(iso: string | null, locale: string): string | null {
  return iso ? countryName(iso, locale) : null;
}
