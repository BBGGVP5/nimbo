import type { Messages } from "../../lib/i18n";
import "../home/home-profile-polish.css";

/** Provider content stays plain React text, including HTML-like announcement payloads. */
export function ProviderAnnouncement({ description }: { description?: string | null; labels: Messages }) {
  if (!description?.trim()) return null;
  return <section className="nimbo-provider-announcement" data-no-toggle>
    <p>{description}</p>
  </section>;
}
