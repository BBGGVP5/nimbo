import { Dialog } from "../components/Universal";
import { StatePanel, useSecondaryCopy } from "../components/Secondary";
import { useState } from "react";
import { serverDisplayLabel } from "../lib/serverUiOverrides";
import { SignalStatistics, type SignalStatsRange } from "./stats/SignalStatistics";
import { TrafficDashboard } from "./stats/TrafficDashboard";
import { api } from "../lib/api";
import { sessionMeasured } from "../lib/statisticsPresentation";
import { useMessages } from "../lib/i18n";
import { useAppStore } from "../store";
import { notifyError, notifyInfo } from "../lib/notify";
import { BackButton } from "../components/BackButton";

export function Statistics() {
  const m = useMessages();
  const copy = useSecondaryCopy();
  const [resetOpen, setResetOpen] = useState(false);
  const [resetBusy, setResetBusy] = useState(false);
  const [resetError, setResetError] = useState<string | null>(null);
  const [range, setRange] = useState<SignalStatsRange>("session");
  const status = useAppStore(s => s.status);
  const connected = status?.state === "connected";
  const stats = useAppStore(s => s.trafficStats);
  const speedAvailable = useAppStore(s => s.trafficMonitoringAvailable);
  const sessionStartedAt = useAppStore(s => s.sessionStartedAt);
  const setTrafficStats = useAppStore(s => s.setTrafficStats);
  const preferences = useAppStore(s => s.preferences);
  const speedHistory = useAppStore(s => s.trafficHistory);
  const activeServerId = useAppStore(s => s.activeServerId);
  const subscriptions = useAppStore(s => s.subscriptions);
  const serverPings = useAppStore(s => s.serverPings);
  const appTraffic = useAppStore(s => s.appTraffic);
  const sessionHistory = useAppStore(s => s.sessionHistory);
  const available = sessionMeasured(stats, connected);
  const activeServer = subscriptions.flatMap(sub => sub.servers).find(server => server.id === activeServerId);
  const activeServerName = activeServer ? serverDisplayLabel(activeServer) : "";
  const activePing = activeServerId ? serverPings[activeServerId] : undefined;
  const statusLabel = connected ? m.statistics.statusConnected : status?.state === "connecting" ? m.statistics.statusConnecting : m.statistics.statusDisconnected;
  const points = speedHistory.map(sample => ({ download: sample.download, upload: sample.upload }));
  const axis = speedHistory.length > 1
    ? [speedHistory[0].at, speedHistory[speedHistory.length - 1].at].map(at => new Date(at).toLocaleTimeString(m.common.locale)) : [];
  const apps = (sessionStartedAt
    ? available ? Object.entries(appTraffic).map(([name, value]) => ({ name, bytes: Math.round(value.download + value.upload) })) : []
    : sessionHistory[0]?.apps ?? [])
    .filter(item => item.bytes > 0).sort((a, b) => b.bytes - a.bytes).slice(0, 6);
  const current = sessionStartedAt ? [{
    id: "current", startedLabel: formatDurationShort(Date.now() - sessionStartedAt),
    server: activeServerName || m.signal.noServer, flag: m.signal.sessionNow,
    duration: formatDurationShort(Date.now() - sessionStartedAt),
    download: available ? stats!.session_download : null,
    upload: available ? stats!.session_upload : null, ping: activePing ?? null,
  }] : [];
  const sessions = [...current, ...sessionHistory.slice(0, 12).map(item => ({
    id: item.id, startedLabel: formatSessionStart(item.startedAt, m.common.locale),
    server: item.serverName || m.signal.noServer, flag: "",
    duration: formatDurationShort(item.endedAt - item.startedAt),
    download: item.download, upload: item.upload, ping: item.ping,
  }))];

  async function handleReset() {
    setResetBusy(true); setResetError(null);
    try {
      await api.resetTrafficTotals();
      const next = await api.getTrafficStats().catch(() => null);
      if (next) setTrafficStats(next);
      notifyInfo(m.statistics.totalsReset); setResetOpen(false);
    } catch (e) {
      setResetError(String(e)); notifyError(String(e));
    } finally { setResetBusy(false); }
  }

  return <>
    {preferences.ui_style !== "signal" && <BackButton />}
    <SignalStatistics labels={m} subtitle={statusLabel} range={range} onRange={setRange}
      dashboard={<TrafficDashboard stats={stats} range={range} connected={connected} speedAvailable={speedAvailable} />}
      points={points} axis={axis} apps={apps} sessions={sessions} onReset={() => setResetOpen(true)} />
    {resetOpen && <Dialog title={m.statistics.reset} closeLabel={m.common.close} onClose={() => setResetOpen(false)} closeDisabled={resetBusy}
      footer={<><button className="btn" disabled={resetBusy} onClick={() => setResetOpen(false)}>{m.common.cancel}</button><button className="primary-button btn" disabled={resetBusy} onClick={() => void handleReset()}>{resetBusy ? m.common.savingProgress : m.statistics.reset}</button></>}>
      <p>{copy.resetHint}</p>{resetError && <StatePanel title={copy.error} detail={resetError} error />}
    </Dialog>}
  </>;
}

function formatDurationShort(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  return [Math.floor(total / 3600), Math.floor((total % 3600) / 60), total % 60].map(value => String(value).padStart(2, "0")).join(":");
}

function formatSessionStart(at: number, locale: string): string {
  const date = new Date(at);
  const today = new Date();
  const time = date.toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" });
  if (date.toDateString() === today.toDateString()) return time;
  return `${date.toLocaleDateString(locale, { day: "2-digit", month: "2-digit" })} ${time}`;
}
