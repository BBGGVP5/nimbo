import { useEffect, useState } from "react";

const PHRASES = {
  ru: { connection: ["Прокладываем путь…", "Готовим облако…", "Скоро на связи"], download: ["Забираем новую версию…", "Наводим порядок…", "Ещё немного…"], sync: ["Готовим облако…", "Наводим порядок…", "Ещё немного…"] },
  en: { connection: ["Finding a path…", "Getting the cloud ready…", "Almost connected"], download: ["Fetching the new version…", "Tidying up…", "A little longer…"], sync: ["Getting the cloud ready…", "Tidying up…", "A little longer…"] },
};
/** Decorative reassurance only. Keep real status/percent/error in a separate accessible element. */
export function OperationPhrase({ active, kind = "connection", locale = "ru" }: { active: boolean; kind?: "connection" | "download" | "sync"; locale?: string }) {
  const [index, setIndex] = useState(0);
  useEffect(() => {
    setIndex(0);
    if (!active) return;
    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    let timer: number | undefined;
    const update = () => {
      window.clearInterval(timer);
      timer = undefined;
      if (!motion.matches && !document.hidden) timer = window.setInterval(() => setIndex(value => (value + 1) % 3), 4500);
    };
    motion.addEventListener("change", update);
    document.addEventListener("visibilitychange", update);
    update();
    return () => { window.clearInterval(timer); motion.removeEventListener("change", update); document.removeEventListener("visibilitychange", update); };
  }, [active, kind, locale]);
  if (!active) return null;
  return <span className="operation-phrase" aria-hidden="true" aria-live="off">{PHRASES[locale.startsWith("ru") ? "ru" : "en"][kind][index]}</span>;
}
