import type { Server } from "../lib/api";
import { fillTemplate, useMessages } from "../lib/i18n";
import { latencyPresentation } from "../lib/latency";
import { notifyError } from "../lib/notify";
import { useAppStore } from "../store";
import { PingIcon } from "../pages/home/SignalServerRail";

export function AutoFastestLine({
  servers,
  subscriptionUrl,
  autoSelected,
  activeId,
  pings,
  displayName,
}: {
  servers: Server[];
  subscriptionUrl: string;
  autoSelected: boolean;
  activeId: string | null;
  pings: Record<string, number>;
  displayName: (server: Server) => string;
}) {
  const m = useMessages();
  const pingProtocol = useAppStore((s) => s.preferences.latency_protocol);
  const searching = useAppStore((s) => s.searchingFastest);
  const connectFastest = useAppStore((s) => s.connectFastestServer);
  const active = servers.find((server) => server.id === activeId) ?? null;
  const canAuto = servers.length >= 2;
  const activePing = active ? pings[active.id] : undefined;

  const subtitle = searching
    ? m.profiles.fastestSearching
    : !canAuto ? m.profiles.autoNeedsTwo
    : autoSelected && active
      ? fillTemplate(m.profiles.autoCurrent, { name: displayName(active) })
    : active && typeof activePing === "number" && Number.isFinite(activePing) && activePing >= 0
      ? fillTemplate(m.profiles.fastestCurrent, {
          name: displayName(active),
          ping: latencyPresentation(activePing, pingProtocol).number,
        })
      : m.profiles.fastestHint;

  const run = () => {
    if (searching || !canAuto) return;
    void connectFastest(subscriptionUrl).catch((error) => notifyError(String(error)));
  };

  return (
    <div
      role="button"
      aria-disabled={!canAuto}
      title={latencyPresentation(activePing, pingProtocol).approximate ? m.settings.latencyEstimateDescription : undefined}
      tabIndex={canAuto ? 0 : -1}
      onClick={run}
      onKeyDown={(event) => {
        if (event.currentTarget !== event.target) return;
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          run();
        }
      }}
      className={[
        "server-profile-row server-profile-row-auto group",
        !canAuto ? "server-profile-row-auto-unavailable" : "",
        autoSelected ? "server-profile-row-auto-selected" : "",
        searching ? "server-profile-row-connecting" : "",
      ].join(" ")}
    >
      <div className="server-profile-logo">
        <BoltIcon />
      </div>
      <div className="server-profile-main">
        <div className="server-profile-title-line">
          <div className="server-profile-title">{m.profiles.fastestTitle}</div>
          {searching && (
            <span className="server-row-pill server-row-pill-selected">
              {m.profiles.fastestSearching}
            </span>
          )}
          {autoSelected && !searching && (
            <span className="server-row-pill server-row-pill-selected">✓ {m.profiles.autoActive}</span>
          )}
        </div>
        <div className="server-profile-description">{subtitle}</div>
      </div>
      <div className="server-profile-actions" data-no-toggle>
        <span aria-hidden="true" className={searching ? "animate-pulse" : undefined}><PingIcon /></span>
      </div>
    </div>
  );
}

function BoltIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" aria-hidden="true">
      <path
        d="M13 2 4.5 13.5H11l-1 8.5 8.5-11.5H12l1-8.5Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinejoin="round"
      />
    </svg>
  );
}

