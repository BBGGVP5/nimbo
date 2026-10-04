import { useEffect, useId, useRef, useState } from "react";
import type { Messages } from "../../lib/i18n";
import "../home/home-profile-polish.css";

/** Provider content stays plain React text, including HTML-like announcement payloads. */
export function ProviderAnnouncement({ description, labels: m }: { description?: string | null; labels: Messages }) {
  const [expanded, setExpanded] = useState(false);
  const [overflowing, setOverflowing] = useState(false);
  const paragraph = useRef<HTMLParagraphElement>(null);
  const id = useId();
  useEffect(() => { setExpanded(false); }, [description]);
  useEffect(() => {
    const element = paragraph.current;
    if (!element) return;
    const measure = () => {
      const lineHeight = parseFloat(getComputedStyle(element).lineHeight);
      setOverflowing(element.scrollHeight > lineHeight * 3 + 1);
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [description, expanded]);
  if (!description?.trim()) return null;
  const ru = m.common.locale.startsWith("ru");
  return <section className={`nimbo-provider-announcement${expanded ? "" : " is-collapsed"}`} data-no-toggle>
    <p id={id} ref={paragraph}>{description}</p>
    {overflowing && <button type="button" aria-expanded={expanded} aria-controls={id} onClick={() => setExpanded(value => !value)}>
      {expanded ? (ru ? "Свернуть" : "Show less") : (ru ? "Читать полностью" : "Read more")}
    </button>}
  </section>;
}
