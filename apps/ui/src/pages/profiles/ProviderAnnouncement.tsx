import { useId, useState } from "react";
import type { Messages } from "../../lib/i18n";
import "../home/home-profile-polish.css";

const PREVIEW_LENGTH = 180;

/** Provider content stays plain React text, including HTML-like announcement payloads. */
export function ProviderAnnouncement({ description, labels: m }: { description?: string | null; labels: Messages }) {
  const [expanded, setExpanded] = useState(false);
  const id = useId();
  const text = description?.trim();
  if (!text) return null;
  const long = text.length > PREVIEW_LENGTH || text.split("\n").length > 3;
  const preview = text.replace(/\s+/g, " ").slice(0, PREVIEW_LENGTH).trimEnd();
  const ru = m.common.locale.startsWith("ru");
  return <section className="nimbo-provider-announcement" data-no-toggle>
    <h3>{m.common.description}</h3>
    <p id={id}>{long && !expanded ? `${preview}${preview.length < text.length ? "…" : ""}` : text}</p>
    {long && <button type="button" aria-expanded={expanded} aria-controls={id} onClick={() => setExpanded(value => !value)}>
      {expanded ? (ru ? "Свернуть" : "Show less") : (ru ? "Читать полностью" : "Read more")}
    </button>}
  </section>;
}
