import { AppearanceThemePreview } from "../components/AppearanceThemePreview";
import { OnDemandSetting } from "../components/OnDemandSetting";
import { CorePreferenceSetting } from "../components/CorePreferenceSetting";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import type { CSSProperties, ReactNode } from "react";
import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { showAppUpdateDialog } from "../App";
import { OperationPhrase } from "../components/OperationPhrase";
import { Surface } from "../components/Universal";
import {
  api,
  APP_VERSION,
  DEFAULT_ACCENT_COLOR,
  formatBytes,
  isTauriRuntime,
  type AppPreferences,
  type AppUpdateInfo,
  type AppUpdateProgress,
  type ConnectionMode,
  type DeviceInfo,
  type ProxySettingsPatch,
  type Subscription,
  type SubscriptionTheme,
} from "../lib/api";
import { fillTemplate, useMessages, type Messages } from "../lib/i18n";
import { LATENCY_URL_PRESETS, normalizeLatencyTimeout, normalizeLatencyUrl } from "../lib/latency";
import { notifyError, notifyInfo } from "../lib/notify";
import { useCachedSubscriptionLogo } from "../lib/subscriptionLogo";
import { cachedSubscriptionTheme } from "../lib/subscriptionTheme";
import { useAppStore } from "../store";

const NIMBO_UA_FALLBACK = `Nimbo/${APP_VERSION}`;
const HAPP_UA = "Happ/2.0.0";
const INCY_UA = "Incy/2.1.0";
const SOCKS_USERNAME_FALLBACK = "nimbo";
const SOCKS_PASSWORD_FALLBACK = "nmb-preview-password";

function withFallback(value: string | null | undefined, fallback: string): string {
  const trimmed = value?.trim();
  return trimmed ? trimmed : fallback;
}

type UaMode = "default" | "happ" | "incy" | "custom";
type SettingsSection =
  | "general"
  | "appearance"
  | "connection"
  | "tunnel"
  | "lan"
  | "subscriptions"
  | "servers"
  | "latency"
  | "backup"
  | "updates"
  | "about";

const sectionItems: Array<{
  id: SettingsSection;
  labelKey: keyof ReturnType<typeof useMessages>["settings"];
  icon: ReactNode;
}> = [
  { id: "general", labelKey: "general", icon: <SlidersIcon /> },
  { id: "appearance", labelKey: "appearance", icon: <PaletteIcon /> },
  { id: "connection", labelKey: "connection", icon: <PlugIcon /> },
  { id: "tunnel", labelKey: "tunnel", icon: <ShieldIcon /> },
  { id: "lan", labelKey: "lan", icon: <HomeIcon /> },
  { id: "subscriptions", labelKey: "subscriptions", icon: <RefreshIcon /> },
  { id: "servers", labelKey: "servers", icon: <ListIcon /> },
  { id: "latency", labelKey: "latency", icon: <SignalIcon /> },
  { id: "backup", labelKey: "backup", icon: <ArchiveIcon /> },
  { id: "updates", labelKey: "updates", icon: <DownloadIcon /> },
  { id: "about", labelKey: "about", icon: <InfoIcon /> },
];

function detectMode(override: string | null, defaultUa: string): UaMode {
  if (!override) return "default";
  if (override === defaultUa) return "default";
  if (override === HAPP_UA) return "happ";
  if (override === INCY_UA) return "incy";
  return "custom";
}

function happCompatibleUserAgent(userAgent: string): string {
  const trimmed = userAgent.trim() || NIMBO_UA_FALLBACK;
  return trimmed.toLowerCase().includes("happ") ? trimmed : `${HAPP_UA} ${trimmed}`;
}

export function Settings() {
  const m = useMessages();
  const subscriptions = useAppStore((s) => s.subscriptions);
  const preferences = useAppStore((s) => s.preferences);
  const activeSubscriptionUrl = useAppStore((s) => s.activeSubscriptionUrl);
  const activeServerId = useAppStore((s) => s.activeServerId);
  const setPreferences = useAppStore((s) => s.setPreferences);
  const refreshSubscription = useAppStore((s) => s.refreshSubscription);
  const status = useAppStore((s) => s.status);
  const hydrate = useAppStore((s) => s.hydrate);
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedSection = searchParams.get("section");
  const section = sectionItems.find(item => item.id === requestedSection)?.id ?? null;
  const setSection = (next: SettingsSection | null) => setSearchParams(next ? { section: next } : {});
  const ru = m.common.locale.startsWith("ru");
  const [device, setDevice] = useState<DeviceInfo | null>(null);
  const [override, setOverride] = useState<string | null>(null);
  const [mode, setMode] = useState<UaMode>("default");
  const [customUa, setCustomUa] = useState("");
  const [copied, setCopied] = useState(false);
  const [savingUa, setSavingUa] = useState(false);
  const [refreshingSubscriptions, setRefreshingSubscriptions] = useState(false);
  const [checkingUpdates, setCheckingUpdates] = useState(false);
  const [installingUpdate, setInstallingUpdate] = useState(false);
  const [updateInfo, setUpdateInfo] = useState<AppUpdateInfo | null>(null);
  const [updateProgress, setUpdateProgress] = useState<AppUpdateProgress | null>(null);
  const [appVersion, setAppVersion] = useState(APP_VERSION);
  const connectionMode = status?.connection_mode ?? "tun";
  const previewSubscription =
    subscriptions.find((sub) => sub.url === (activeSubscriptionUrl ?? status?.active_subscription_url)) ??
    subscriptions.find((sub) =>
      sub.servers.some((server) => server.id === (activeServerId ?? status?.active_server_id)),
    ) ??
    subscriptions[0] ??
    null;

  const updatePreferences = async (patch: Partial<AppPreferences>) => {
    try {
      const latestPreferences = useAppStore.getState().preferences;
      await setPreferences({ ...latestPreferences, ...patch });
      notifyInfo(m.settings.saved);
    } catch (e) {
      notifyError(String(e));
    }
  };

  useEffect(() => {
    let cancelled = false;
    api.getAppVersion()
      .then((version) => {
        if (!cancelled) setAppVersion(version);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!isTauriRuntime()) return;
    let active = true;
    let unlisten: UnlistenFn | null = null;
    void listen<AppUpdateProgress>("nimbo:update-progress", (event) => {
      if (active) setUpdateProgress(event.payload);
    }).then((dispose) => {
      if (active) unlisten = dispose;
      else dispose();
    }).catch(() => undefined);
    return () => {
      active = false;
      unlisten?.();
    };
  }, []);

  useEffect(() => {
    Promise.all([
      api.getDeviceInfo(),
      api.getUserAgentOverride(),
    ])
      .then(([d, ov]) => {
        setDevice(d);
        setOverride(ov);
        const nextMode = detectMode(ov, d.user_agent);
        setMode(nextMode);
        if (nextMode === "custom" && ov) setCustomUa(ov);
      })
      .catch(() => setDevice(null));
  }, []);

  const defaultUa = device?.user_agent ?? NIMBO_UA_FALLBACK;
  const effectiveUa =
    mode === "default"
      ? defaultUa
      : mode === "happ"
        ? HAPP_UA
        : mode === "incy"
          ? INCY_UA
          : customUa.trim() || defaultUa;
  const subscriptionUa = happCompatibleUserAgent(effectiveUa);

  const onCopyHwid = async () => {
    if (!device) return;
    try {
      await api.writeClipboardText(device.hwid);
      setCopied(true);
      notifyInfo(m.settings.hwidCopied);
      setTimeout(() => setCopied(false), 1500);
    } catch (e) {
      notifyError(String(e));
    }
  };

  const applyUa = async (next: UaMode, customValue?: string) => {
    if (!device) return;
    setSavingUa(true);
    try {
      let value: string | null = null;
      if (next === "happ") value = HAPP_UA;
      if (next === "incy") value = INCY_UA;
      if (next === "custom") value = (customValue ?? customUa).trim() || null;
      await api.setUserAgentOverride(value);
      setOverride(value);
      setMode(next);
    } catch (e) {
      notifyError(String(e));
    } finally {
      setSavingUa(false);
    }
  };

  const refreshRemoteSubscriptions = async () => {
    setRefreshingSubscriptions(true);
    try {
      const remoteSubscriptions = subscriptions.filter((sub) => /^https?:\/\//i.test(sub.url));
      const refreshResults = await Promise.allSettled(
        remoteSubscriptions.map((sub) => refreshSubscription(sub.url)),
      );
      const failedRefreshes = refreshResults.filter((result) => result.status === "rejected");
      if (failedRefreshes.length > 0) {
        throw new Error(m.settings.partialRefreshFailed);
      }
    } catch (e) {
      notifyError(String(e));
    } finally {
      setRefreshingSubscriptions(false);
    }
  };

  const checkForUpdates = async () => {
    setCheckingUpdates(true);
    try {
      const info = await api.checkAppUpdate(preferences.update_channel);
      setUpdateInfo(info);
      if (info.available) {
        showAppUpdateDialog(info);
      }
      notifyInfo(
        info.available
          ? fillTemplate(
              info.reason === "reissued" ? m.settings.updateReissued : m.settings.updateReady,
              { version: info.latest_version },
            )
          : m.settings.updateCurrent,
      );
    } catch (e) {
      notifyError(`${m.settings.updateCheckFailed} ${String(e)}`);
    } finally {
      setCheckingUpdates(false);
    }
  };

  const downloadUpdate = async (info: AppUpdateInfo) => {
    setInstallingUpdate(true);
    setUpdateProgress({ downloaded_bytes: 0, total_bytes: info.asset?.size ?? 0, percent: 0, stage: "downloading" });
    try {
      const result = await api.installAppUpdate(info);
      notifyInfo(
        result.rollback_supported
          ? m.settings.updateVerified
          : m.settings.updateVerifiedNoRollback,
      );
    } catch (e) {
      notifyError(String(e));
    } finally {
      setInstallingUpdate(false);
    }
  };

  const updateConnectionMode = async (nextMode: ConnectionMode) => {
    try {
      if (nextMode === "tun" || nextMode === "both") {
        const status = await api.getTunStatus();
        if (!status.installed) {
          if (!status.can_install) {
            throw new Error(status.message);
          }
          notifyInfo(m.settings.installingTun);
          const installed = await api.installTun();
          if (!installed.installed) {
            throw new Error(installed.message);
          }
          notifyInfo(
            installed.needs_admin_restart
              ? m.settings.tunInstalledAdmin
              : installed.message,
          );
        } else if (status.needs_admin_restart) {
          notifyInfo(status.message);
        }
      }
      await api.setConnectionMode(nextMode);
    } catch (e) {
      notifyError(String(e));
    } finally {
      await hydrate().catch(() => undefined);
    }
  };

  const updateProxySettings = async (settings: ProxySettingsPatch) => {
    try {
      await api.setProxySettings(settings);
      await hydrate();
      notifyInfo(m.settings.saved);
    } catch (e) {
      notifyError(String(e));
    }
  };

  return (
    <div className="settings-page h-full overflow-auto">
      {section ? <><button type="button" className="parity-back" onClick={() => setSection(null)} aria-label={m.common.locale.startsWith("ru") ? "Назад к настройкам" : "Back to settings"}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="m14 6-6 6 6 6M8 12h12"/></svg><span>{m.settings.title}</span></button>
        <h1 className="page-title">{m.settings[sectionItems.find(item => item.id === section)!.labelKey]}</h1></> :
        <header className="parity-page-heading"><h1 className="page-title">{m.settings.title}</h1><p>{ru ? "Всё нужное — на своём месте." : "Everything you need, in its place."}</p></header>}
      {!section && <SettingsOverview preferences={preferences} version={appVersion} onSelect={setSection} />}
      {section && <div className="parity-settings-detail">
        <div className="settings-content">
          {section === "general" && (
            <GeneralSection
              preferences={preferences}
              onChange={updatePreferences}
            />
          )}
          {section === "appearance" && (
            <AppearanceSection
              preferences={preferences}
              previewSubscription={previewSubscription}
              onChange={updatePreferences}
            />
          )}
          {section === "connection" && (
            <ConnectionSection
              mode={connectionMode}
              socksPort={status?.socks_port ?? 10808}
              httpPort={status?.http_port ?? 10809}
              socksUsername={withFallback(status?.socks_username, SOCKS_USERNAME_FALLBACK)}
              socksPassword={withFallback(status?.socks_password, SOCKS_PASSWORD_FALLBACK)}
              requireSocksAuth={status?.require_socks_auth ?? false}
              blockSocksUdp={status?.block_socks_udp ?? false}
              killSwitch={preferences.connection_kill_switch}
              onMode={updateConnectionMode}
              onProxySettings={updateProxySettings}
              onPreferences={updatePreferences}
            />
          )}
          {section === "tunnel" && (
            <TunnelSection
              preferences={preferences}
              onChange={updatePreferences}
            />
          )}
          {section === "lan" && (
            <LanSection
              preferences={preferences}
              onChange={updatePreferences}
            />
          )}
          {section === "subscriptions" && (
            <SubscriptionsSection
              preferences={preferences}
              mode={mode}
              customUa={customUa}
              override={override}
              effectiveUa={subscriptionUa}
              savingUa={savingUa}
              deviceUa={defaultUa}
              appVersion={appVersion}
              refreshingSubscriptions={refreshingSubscriptions}
              onChange={updatePreferences}
              onMode={applyUa}
              onCustomUa={setCustomUa}
              onRefreshSubscriptions={refreshRemoteSubscriptions}
            />
          )}
          {section === "servers" && (
            <ServersSection
              preferences={preferences}
              onChange={updatePreferences}
            />
          )}
          {section === "latency" && (
            <LatencySection
              preferences={preferences}
              onChange={updatePreferences}
            />
          )}
          {section === "backup" && <BackupSection onImported={hydrate} />}
          {section === "updates" && (
            <UpdatesSection
              preferences={preferences}
              updateInfo={updateInfo}
              appVersion={appVersion}
              checking={checkingUpdates}
              installing={installingUpdate}
              progress={updateProgress}
              onChange={updatePreferences}
              onCheck={checkForUpdates}
              onDownload={downloadUpdate}
            />
          )}
          {section === "about" && (
            <AboutSection
              device={device}
              appVersion={appVersion}
              copied={copied}
              onCopyHwid={onCopyHwid}
            />
          )}
        </div>
      </div>}
    </div>
  );
}

function SettingsOverview({ preferences, version, onSelect }: { preferences: AppPreferences; version: string; onSelect: (section: SettingsSection) => void }) {
  const m = useMessages();
  const ru = m.common.locale.startsWith("ru");
  const row = (id: SettingsSection, detail?: string) => {
    const item = sectionItems.find(item => item.id === id)!;
    return <button type="button" className="parity-setting-link" key={id} onClick={() => onSelect(id)}><span className="parity-setting-icon">{item.icon}</span><span><strong>{m.settings[item.labelKey]}</strong>{detail && <small>{detail}</small>}</span><span aria-hidden="true">›</span></button>;
  };
  const link = (to: string, title: string, icon: ReactNode, detail?: string) => <Link className="parity-setting-link" to={to}><span className="parity-setting-icon">{icon}</span><span><strong>{title}</strong>{detail && <small>{detail}</small>}</span><span aria-hidden="true">›</span></Link>;
  return <div className="parity-settings-overview">
    <div><section className="parity-settings-group"><h2>{m.settings.connection}</h2><div className="parity-settings-list">
      {row("latency", preferences.latency_protocol === "nimbo" ? "Nimbo Ping" : ({ tcp_connect: "TCP Connect", http_get: "HTTP GET", http_head: "HTTP HEAD", icmp: "ICMP" }[preferences.latency_protocol] ?? preferences.latency_protocol))}
      {link("/routing", m.app.routing, <RouteIcon />, ru ? "Правила и модули" : "Rules and modules")}
      {row("tunnel", "DNS · TLS · MTU")}{row("connection", ru ? "Режим, прокси и Kill Switch" : "Mode, proxy and Kill Switch")}{row("lan")}
    </div></section><section className="parity-settings-group"><h2>{m.settings.subscriptions}</h2><div className="parity-settings-list">
      {row("subscriptions", ru ? "Обновление и импорт" : "Refresh and import")}{row("servers")}{link("/sync", m.app.sync, <RefreshIcon />)}{row("backup")}
    </div></section></div>
    <div><section className="parity-settings-group"><h2>{ru ? "Приложение" : "Application"}</h2><div className="parity-settings-list">
      {row("appearance", ru ? "Тема и цвет" : "Theme and colour")}{row("updates", `v${version}`)}{row("general")}
      {link("/apps", m.app.apps, <ConnectionsIcon />)}{link("/notifications", m.app.notifications, <InfoIcon />)}
    </div></section><section className="parity-settings-group"><h2>{ru ? "Диагностика" : "Diagnostics"}</h2><div className="parity-settings-list">
      {link("/connections", m.app.connections, <ConnectionsIcon />)}{link("/statistics", m.app.statistics, <StatsBarsIcon />)}{link("/tunnel-logs", m.app.tunnelLogs, <LogsIcon />)}{row("about", `Nimbo · ${version}`)}
    </div></section></div>
  </div>;
}

function GeneralSection({
  preferences,
  onChange,
}: {
  preferences: AppPreferences;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
}) {
  const m = useMessages();
  return (
    <Section title={m.settings.general}>
      <SettingsCard>
        <ToggleRow
          label={m.settings.launchAtLogin}
          description={m.settings.launchAtLoginDescription}
          enabled={preferences.launch_at_login}
          onToggle={(launch_at_login) => onChange({ launch_at_login })}
          icon={<PowerIcon />}
        />
        <ToggleRow
          label={m.settings.autoConnect}
          description={m.settings.autoConnectDescription}
          enabled={preferences.auto_connect_on_launch}
          onToggle={(auto_connect_on_launch) => onChange({ auto_connect_on_launch })}
          icon={<ZapIcon />}
        />
        <ToggleRow
          label={m.settings.autoConnectFastest}
          description={m.settings.autoConnectFastestDescription}
          enabled={preferences.auto_connect_fastest}
          onToggle={(auto_connect_fastest) => onChange({ auto_connect_fastest })}
          icon={<ZapIcon />}
        />
        <ToggleRow
          label={m.settings.startMinimized}
          enabled={preferences.start_minimized}
          onToggle={(start_minimized) => onChange({ start_minimized })}
          icon={<MinimizeIcon />}
        />
        <ToggleRow
          label={m.settings.minimizeToTray}
          description={m.settings.minimizeToTrayDescription}
          enabled={preferences.minimize_to_tray}
          onToggle={(minimize_to_tray) => onChange({ minimize_to_tray })}
          icon={<TrayIcon />}
        />
        <ToggleRow
          label={m.settings.pingOnLaunch}
          enabled={preferences.ping_on_launch}
          onToggle={(ping_on_launch) => onChange({ ping_on_launch })}
          icon={<SignalIcon />}
        />
        <ToggleRow
          label={m.settings.showSpeedChart}
          description={m.settings.showSpeedChartDescription}
          enabled={preferences.show_speed_chart}
          onToggle={(show_speed_chart) => onChange({ show_speed_chart })}
          icon={<ActivityIcon />}
        />
        <ToggleRow
          label={m.settings.showMemoryUsage}
          description={m.settings.showMemoryUsageDescription}
          enabled={preferences.show_memory_usage}
          onToggle={(show_memory_usage) => onChange({ show_memory_usage })}
          icon={<CpuIcon />}
        />
      </SettingsCard>
    </Section>
  );
}

function AppearanceSection({ preferences, previewSubscription, onChange }: {
  preferences: AppPreferences; previewSubscription: Subscription | null;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
}) {
  const m = useMessages();
  const ru = m.common.locale.startsWith("ru");
  const logo = useCachedSubscriptionLogo(previewSubscription, true);
  const theme = cachedSubscriptionTheme(previewSubscription);
  return <Section title={m.settings.appearance}>
    <SettingsCard>
      <div className="parity-appearance-block"><h3>{m.settings.theme}</h3><div className="parity-theme-options" role="radiogroup" aria-label={m.settings.theme}>
        {([ ["system", m.settings.systemTheme], ["light", m.settings.lightTheme], ["dark", m.settings.darkTheme], ["black", m.settings.blackTheme] ] as const).map(([value,label]) => <AppearanceThemePreview key={value} title={label} value={value} selected={preferences.theme_mode === value} onClick={() => void onChange({theme_mode:value})}/>)}
      </div></div>
      <div className="parity-appearance-block"><h3>{m.settings.accentColor}</h3><div className="parity-palette" role="radiogroup" aria-label={m.settings.accentColor}>
        {[{color:DEFAULT_ACCENT_COLOR,label:ru ? "Чёрно-белая" : "Monochrome"},{color:"#7298ee",label:ru ? "Кобальт" : "Cobalt"},{color:"#55b8b0",label:ru ? "Лагуна" : "Lagoon"},{color:"#c77e67",label:ru ? "Терракота" : "Terracotta"}].map(({color,label},i) => <button type="button" role="radio" aria-checked={preferences.accent_mode === "preset" && preferences.accent_color === color} key={color} onClick={() => void onChange({accent_mode:"preset",accent_color:color})}><i style={{background:i===0 ? "linear-gradient(135deg,#333 50%,#eee 50%)" : color}}/><span>{label}</span><span className="parity-selection-mark" aria-hidden="true">{preferences.accent_mode === "preset" && preferences.accent_color === color ? "✓" : ""}</span></button>)}
      </div><label className={`parity-custom-color${preferences.accent_mode === "custom" ? " is-selected" : ""}`}><span className="parity-color-swatch" style={{background:preferences.accent_color}}/><span>{m.settings.customAccent}<small>{preferences.accent_mode === "custom" ? preferences.accent_color.toUpperCase() : (ru ? "Выбрать цвет" : "Choose colour")}</small></span><span className="parity-selection-mark" aria-hidden="true">{preferences.accent_mode === "custom" ? "✓" : "+"}</span><input type="color" aria-label={m.settings.customAccent} value={preferences.accent_color} onChange={event => void onChange({accent_mode:"custom",accent_color:event.target.value})}/></label></div>
      <SettingsChoiceRow label={m.settings.language} value={preferences.language} options={[{value:"system",label:m.settings.systemLanguage},{value:"ru",label:"Русский"},{value:"en",label:"English"}]} onChange={language => onChange({language})}/>
      <ToggleRow label={m.settings.navIconMotion} description={m.settings.navIconMotionDescription} enabled={preferences.nav_icon_motion} onToggle={nav_icon_motion => onChange({nav_icon_motion})}/>
      <ToggleRow label={m.settings.showSubscriptionLogo} enabled={preferences.show_subscription_logo} onToggle={show_subscription_logo => onChange({show_subscription_logo})}/>
      <ToggleRow label={m.settings.providerTheme} description={m.settings.providerThemeDescription} enabled={preferences.provider_theme} onToggle={provider_theme => onChange({provider_theme})}/>
      {preferences.provider_theme && <SubscriptionProviderPreview sub={previewSubscription} theme={theme} logoSrc={logo} themeEnabled logoEnabled={preferences.show_subscription_logo} labels={m}/>}
    </SettingsCard>
  </Section>;
}

function ConnectionSection({
  mode,
  socksPort,
  httpPort,
  socksUsername,
  socksPassword,
  requireSocksAuth,
  blockSocksUdp,
  killSwitch,
  onMode,
  onProxySettings,
  onPreferences,
}: {
  mode: ConnectionMode;
  socksPort: number;
  httpPort: number;
  socksUsername: string;
  socksPassword: string;
  requireSocksAuth: boolean;
  blockSocksUdp: boolean;
  killSwitch: boolean;
  onMode: (mode: ConnectionMode) => Promise<void>;
  onProxySettings: (settings: ProxySettingsPatch) => Promise<void>;
  onPreferences: (patch: Partial<AppPreferences>) => Promise<void>;
}) {
  const m = useMessages();
  const [socksUsernameDraft, setSocksUsernameDraft] = useState(socksUsername);
  const [socksPasswordDraft, setSocksPasswordDraft] = useState(socksPassword);
  const systemProxyOn = mode === "system_proxy" || mode === "both";
  const tunOn = mode === "tun" || mode === "both";
  const httpProxy = `127.0.0.1:${httpPort}`;
  const socksProxy = `127.0.0.1:${socksPort}`;

  useEffect(() => {
    setSocksUsernameDraft(socksUsername);
  }, [socksUsername]);

  useEffect(() => {
    setSocksPasswordDraft(socksPassword);
  }, [socksPassword]);

  const toggleSystemProxy = (next: boolean) => {
    const nextMode: ConnectionMode = next
      ? tunOn
        ? "both"
        : "system_proxy"
      : tunOn
        ? "tun"
        : "tun";
    void onMode(nextMode);
  };

  const toggleTun = (next: boolean) => {
    const nextMode: ConnectionMode = next
      ? systemProxyOn
        ? "both"
        : "tun"
      : systemProxyOn
        ? "system_proxy"
        : "system_proxy";
    void onMode(nextMode);
  };

  return (
    <Section title={m.settings.connection}>
      <SettingsCard>
        <CorePreferenceSetting />
        <div className="settings-row settings-row-block">
          <div>
            <div className="settings-row-title">{m.settings.connectionMode}</div>
            <div className="settings-row-description">
              {m.settings.connectionModeDescription}
            </div>
          </div>
        </div>
        <ToggleRow
          label="System Proxy"
          description={`HTTP ${httpProxy} · SOCKS ${socksProxy}`}
          enabled={systemProxyOn}
          onToggle={toggleSystemProxy}
          icon={<GlobeIcon />}
        />
        <ToggleRow
          label="TUN"
          description={m.settings.tunSubtitle}
          enabled={tunOn}
          onToggle={toggleTun}
          icon={<ShieldIcon />}
        />
        <ToggleRow
          label="Kill switch"
          enabled={killSwitch}
          onToggle={(connection_kill_switch) => onPreferences({ connection_kill_switch })}
          icon={<ZapIcon />}
        />
        <ResetKillSwitchRow />
      </SettingsCard>
      <SettingsCard><OnDemandSetting /></SettingsCard>
      <SettingsCard>
        <ValueRow label="HTTP proxy" value={httpProxy} copyValue={httpProxy} mono icon={<PlugIcon />} />
        <ValueRow label="SOCKS5 proxy" value={socksProxy} copyValue={socksProxy} mono icon={<PlugIcon />} />
        <SettingsInputRow
          label={m.settings.socksUsername}
          value={socksUsernameDraft}
          copyValue={socksUsernameDraft}
          compact
          inputMode="text"
          onChange={setSocksUsernameDraft}
          onCommit={() => {
            const next = socksUsernameDraft.trim() || SOCKS_USERNAME_FALLBACK;
            setSocksUsernameDraft(next);
            if (next !== socksUsername) void onProxySettings({ socks_username: next });
          }}
          icon={<UserIcon />}
        />
        <SettingsInputRow
          label={m.settings.socksPassword}
          value={socksPasswordDraft}
          copyValue={socksPasswordDraft}
          compact
          inputMode="text"
          type="password"
          onChange={setSocksPasswordDraft}
          onCommit={() => {
            const next = socksPasswordDraft.trim() || SOCKS_PASSWORD_FALLBACK;
            setSocksPasswordDraft(next);
            if (next !== socksPassword) void onProxySettings({ socks_password: next });
          }}
          icon={<LockIcon />}
        />
        <ToggleRow
          label={m.settings.requireSocksAuth}
          description={m.settings.requireSocksAuthDescription}
          enabled={requireSocksAuth}
          onToggle={(require_socks_auth) => onProxySettings({ require_socks_auth })}
          icon={<ShieldIcon />}
        />
        <ToggleRow
          label={m.settings.blockSocksUdp}
          description={m.settings.blockSocksUdpDescription}
          enabled={blockSocksUdp}
          onToggle={(block_socks_udp) => onProxySettings({ block_socks_udp })}
          icon={<ShieldIcon />}
        />
      </SettingsCard>
    </Section>
  );
}

function TunnelSection({
  preferences,
  onChange,
}: {
  preferences: AppPreferences;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
}) {
  const m = useMessages();
  return (
    <Section title={m.settings.tunnel}>
      <SettingsCard>
        <ToggleRow
          label={m.settings.sniffing}
          description={m.settings.sniffingDescription}
          enabled={preferences.tunnel_sniffing}
          onToggle={(tunnel_sniffing) => onChange({ tunnel_sniffing })}
          icon={<ShieldIcon />}
        />
        <ToggleRow
          label="Mux"
          description={m.settings.muxDescription}
          enabled={preferences.tunnel_mux_enabled}
          onToggle={(tunnel_mux_enabled) => onChange({ tunnel_mux_enabled })}
          icon={<ConnectionsIcon />}
        />
        <NumberPreferenceRow
          label="Mux concurrency"
          value={preferences.tunnel_mux_concurrency}
          min={1}
          max={1024}
          onCommit={(tunnel_mux_concurrency) => onChange({ tunnel_mux_concurrency })}
          icon={<SlidersIcon />}
        />
        <NumberPreferenceRow
          label="xUDP concurrency"
          description={m.settings.xudpDescription}
          value={preferences.tunnel_xudp_concurrency}
          min={-1}
          max={1024}
          onCommit={(tunnel_xudp_concurrency) => onChange({ tunnel_xudp_concurrency })}
          icon={<SlidersIcon />}
        />
        <SettingsChoiceRow
          label="xUDP UDP/443"
          value={preferences.tunnel_xudp_udp443}
          options={[
            { value: "reject", label: "Reject" },
            { value: "allow", label: "Allow" },
            { value: "skip", label: "Skip" },
          ]}
          onChange={(tunnel_xudp_udp443) => onChange({ tunnel_xudp_udp443 })}
          icon={<SignalIcon />}
        />
        <ToggleRow
          label={m.settings.tlsFragmentation}
          description={m.settings.tlsFragmentationDescription}
          enabled={preferences.tunnel_tls_fragmentation}
          onToggle={(tunnel_tls_fragmentation) => onChange({ tunnel_tls_fragmentation })}
          icon={<ListIcon />}
        />
        <TunnelDnsRow value={preferences.tunnel_dns} onChange={onChange} />
        <TunnelMtuRow value={preferences.tunnel_mtu} onChange={onChange} />
      </SettingsCard>
    </Section>
  );
}

function LanSection({
  preferences,
  onChange,
}: {
  preferences: AppPreferences;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
}) {
  const m = useMessages();
  return (
    <Section title={m.settings.lan}>
      <SettingsCard>
        <ToggleRow
          label={m.settings.allowLan}
          description={m.settings.allowLanDescription}
          enabled={preferences.lan_allow_connections}
          onToggle={(lan_allow_connections) => onChange({ lan_allow_connections })}
          icon={<HomeIcon />}
        />
        <ToggleRow
          label={m.settings.allowTethering}
          description={m.settings.allowTetheringDescription}
          enabled={preferences.lan_allow_tethering}
          onToggle={(lan_allow_tethering) => onChange({ lan_allow_tethering })}
          icon={<ConnectionsIcon />}
        />
        <ToggleRow
          label={m.settings.lanProxy}
          description={m.settings.lanProxyDescription}
          enabled={preferences.lan_proxy_enabled}
          onToggle={(lan_proxy_enabled) => onChange({ lan_proxy_enabled })}
          icon={<GlobeIcon />}
        />
      </SettingsCard>
      <SettingsCard>
        <NumberPreferenceRow
          label={m.settings.tcpIdleTimeout}
          value={preferences.lan_tcp_idle_timeout_sec}
          min={5}
          max={3600}
          onCommit={(lan_tcp_idle_timeout_sec) => onChange({ lan_tcp_idle_timeout_sec })}
          icon={<SlidersIcon />}
        />
        <NumberPreferenceRow
          label={m.settings.maxTcp}
          value={preferences.lan_max_tcp_connections}
          min={1}
          max={100000}
          onCommit={(lan_max_tcp_connections) => onChange({ lan_max_tcp_connections })}
          icon={<SlidersIcon />}
        />
        <NumberPreferenceRow
          label={m.settings.maxUdp}
          value={preferences.lan_max_udp_connections}
          min={1}
          max={100000}
          onCommit={(lan_max_udp_connections) => onChange({ lan_max_udp_connections })}
          icon={<SlidersIcon />}
        />
        <SettingsChoiceRow
          label={m.settings.preferredIpFamily}
          value={preferences.lan_preferred_ip_family}
          options={[
            { value: "auto", label: m.profiles.auto },
            { value: "ipv4", label: "IPv4" },
            { value: "ipv6", label: "IPv6" },
          ]}
          onChange={(lan_preferred_ip_family) => onChange({ lan_preferred_ip_family })}
          icon={<SignalIcon />}
        />
      </SettingsCard>
    </Section>
  );
}

function SubscriptionsSection({
  preferences,
  mode,
  customUa,
  override,
  effectiveUa,
  savingUa,
  deviceUa,
  appVersion,
  refreshingSubscriptions,
  onChange,
  onMode,
  onCustomUa,
  onRefreshSubscriptions,
}: {
  preferences: AppPreferences;
  mode: UaMode;
  customUa: string;
  override: string | null;
  effectiveUa: string;
  savingUa: boolean;
  deviceUa: string;
  appVersion: string;
  refreshingSubscriptions: boolean;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
  onMode: (mode: UaMode, custom?: string) => Promise<void>;
  onCustomUa: (value: string) => void;
  onRefreshSubscriptions: () => Promise<void>;
}) {
  const m = useMessages();
  return (
    <Section title={m.settings.subscriptions}>
      <SettingsCard>
        <ToggleRow
          label={m.settings.autoUpdateSubscriptions}
          enabled={preferences.subscriptions_auto_update}
          onToggle={(subscriptions_auto_update) => onChange({ subscriptions_auto_update })}
          icon={<RefreshIcon />}
        />
        <NumberPreferenceRow
          label={m.settings.updateInterval}
          value={preferences.subscriptions_update_interval_hours}
          min={1}
          max={168}
          suffix={m.settings.hoursSuffix}
          onCommit={(subscriptions_update_interval_hours) => onChange({ subscriptions_update_interval_hours })}
          icon={<SlidersIcon />}
        />
        <ToggleRow
          label={m.settings.notifyExpiration}
          enabled={preferences.subscriptions_notify_expiration}
          onToggle={(subscriptions_notify_expiration) => onChange({ subscriptions_notify_expiration })}
          icon={<InfoIcon />}
        />
        <NumberPreferenceRow
          label={m.settings.daysLeftThreshold}
          value={preferences.subscriptions_expiration_threshold_days}
          min={1}
          max={365}
          suffix={m.settings.daysSuffix}
          onCommit={(subscriptions_expiration_threshold_days) => onChange({ subscriptions_expiration_threshold_days })}
          icon={<SlidersIcon />}
        />
        <ToggleRow
          label={m.settings.notifySubscriptionUpdate}
          enabled={preferences.subscriptions_notify_updates}
          onToggle={(subscriptions_notify_updates) => onChange({ subscriptions_notify_updates })}
          icon={<InfoIcon />}
        />
        <ToggleRow
          label={m.settings.updateOnLaunch}
          enabled={preferences.subscriptions_update_on_launch}
          onToggle={(subscriptions_update_on_launch) => onChange({ subscriptions_update_on_launch })}
          icon={<PowerIcon />}
        />
        <ToggleRow
          label={m.settings.pingAfterUpdate}
          enabled={preferences.subscriptions_ping_after_update}
          onToggle={(subscriptions_ping_after_update) => onChange({ subscriptions_ping_after_update })}
          icon={<SignalIcon />}
        />
      </SettingsCard>

      <SettingsCard>
        <div className="settings-row settings-row-block">
          <div>
            <div className="settings-row-title">{m.settings.subscriptionMeta}</div>
            <div className="settings-row-description">
              {m.settings.subscriptionMetaDescription}
            </div>
          </div>
          <button
            disabled={refreshingSubscriptions}
            onClick={() => void onRefreshSubscriptions()}
            className="settings-action"
          >
            {refreshingSubscriptions ? m.common.refreshing : m.common.refresh}
          </button>
        </div>
      </SettingsCard>

      <SettingsCard>
        <div className="settings-row settings-row-block">
          <div>
            <div className="settings-row-title">User-Agent</div>
            <div className="settings-row-description">
              {fillTemplate(m.settings.uaDefaultFormat, { version: appVersion })}
            </div>
          </div>
        </div>
        <UaOption selected={mode === "default"} disabled={savingUa} title="Default (Nimbo)" subtitle={deviceUa} onClick={() => onMode("default")} />
        <UaOption selected={mode === "happ"} disabled={savingUa} title={m.settings.happMask} subtitle={HAPP_UA} onClick={() => onMode("happ")} />
        <UaOption selected={mode === "incy"} disabled={savingUa} title={m.settings.incyMask} subtitle={INCY_UA} onClick={() => onMode("incy")} />
        <UaOption selected={mode === "custom"} disabled={savingUa} title={m.settings.customUserAgent} subtitle={m.profiles.manualInput} onClick={() => onMode("custom", customUa)} />
        {mode === "custom" && (
          <div className="settings-row">
            <input
              value={customUa}
              onChange={(e) => onCustomUa(e.target.value)}
              placeholder={NIMBO_UA_FALLBACK}
              className="settings-input w-full"
            />
            <button
              disabled={savingUa || customUa.trim() === (override ?? "")}
              onClick={() => onMode("custom", customUa)}
              className="settings-action"
            >
              {m.settings.apply}
            </button>
          </div>
        )}
        <ValueRow label={m.settings.willSend} value={effectiveUa} mono />
      </SettingsCard>
    </Section>
  );
}

function ServersSection({
  preferences,
  onChange,
}: {
  preferences: AppPreferences;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
}) {
  const m = useMessages();
  return (
    <Section title={m.settings.servers}>
      <SettingsCard>
        <SettingsChoiceRow
          label={m.settings.serverSorting}
          value={preferences.servers_sorting}
          options={[
            { value: "provider", label: m.settings.serverSortProvider },
            { value: "name", label: m.home.sortName },
            { value: "ping", label: m.home.sortPing.replace(" ↑", "") },
            { value: "protocol", label: m.home.sortProtocol },
          ]}
          onChange={(servers_sorting) => onChange({ servers_sorting })}
          icon={<ListIcon />}
        />
        <NumberPreferenceRow
          label={m.settings.uiScale}
          value={preferences.servers_ui_scale}
          min={80}
          max={125}
          suffix="%"
          onCommit={(servers_ui_scale) => onChange({ servers_ui_scale })}
          icon={<SlidersIcon />}
        />
        <ToggleRow
          label={m.settings.proxyOnlyButton}
          enabled={preferences.servers_proxy_only_button}
          onToggle={(servers_proxy_only_button) => onChange({ servers_proxy_only_button })}
          icon={<ZapIcon />}
        />
      </SettingsCard>
    </Section>
  );
}

function LatencySection({
  preferences,
  onChange,
}: {
  preferences: AppPreferences;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
}) {
  const m = useMessages();
  const [testUrlDraft, setTestUrlDraft] = useState(preferences.latency_test_url);
  const [customUrl, setCustomUrl] = useState(!LATENCY_URL_PRESETS.some(p => p.value === preferences.latency_test_url));
  const [timeoutDraft, setTimeoutDraft] = useState(String(preferences.latency_timeout_ms / 1000));

  useEffect(() => {
    setTestUrlDraft(preferences.latency_test_url);
  }, [preferences.latency_test_url]);

  useEffect(() => {
    setTimeoutDraft(String(preferences.latency_timeout_ms / 1000));
  }, [preferences.latency_timeout_ms]);

  const saveTestUrl = () => {
    const next = normalizeLatencyUrl(testUrlDraft);
    setTestUrlDraft(next);
    if (next !== preferences.latency_test_url) {
      void onChange({ latency_test_url: next });
    }
  };

  const saveTimeout = () => {
    const next = normalizeLatencyTimeout(timeoutDraft.trim() ? Number(timeoutDraft.replace(",", ".")) * 1000 : undefined);
    setTimeoutDraft(String(next / 1000));
    if (next !== preferences.latency_timeout_ms) {
      void onChange({ latency_timeout_ms: next });
    }
  };

  return (
    <Section title={m.settings.latency}>
      <SettingsCard>
        <fieldset className="parity-radio-list"><legend>{m.settings.protocol}</legend>
          {[
            { value: "nimbo", label: "Nimbo Ping", detail: m.settings.latencyEstimateDescription },
            { value: "tcp_connect", label: m.settings.latencyTcpConnect, detail: "TCP" },
            { value: "http_get", label: "HTTP GET", detail: m.settings.latencyProtocolDescription },
            { value: "http_head", label: m.settings.latencyHttpHead, detail: "HTTP HEAD" },
            { value: "icmp", label: m.settings.latencyIcmp, detail: "ICMP" },
          ].map(option => <label key={option.value}><span><strong>{option.label}</strong><small>{option.detail}</small></span><input type="radio" name="latency-protocol" value={option.value} checked={preferences.latency_protocol === option.value} onChange={() => void onChange({ latency_protocol: option.value as AppPreferences["latency_protocol"] })}/></label>)}
        </fieldset>
        {["nimbo", "http_get", "http_head"].includes(preferences.latency_protocol) && (<>
          <SettingsChoiceRow
            label={m.settings.testUrl}
            description={m.settings.latencyActiveRouteOnly}
            value={customUrl ? "custom" : preferences.latency_test_url}
            options={[...LATENCY_URL_PRESETS, { value: "custom", label: m.settings.latencyCustomUrl }]}
            onChange={async value => {
              setCustomUrl(value === "custom");
              if (value !== "custom") { setTestUrlDraft(value); await onChange({ latency_test_url: value }); }
            }}
            icon={<GlobeIcon />}
          />
          {customUrl && <SettingsInputRow
            label={m.settings.testUrl}
            value={testUrlDraft}
            inputMode="url"
            onChange={setTestUrlDraft}
            onCommit={saveTestUrl}
            icon={<GlobeIcon />}
          />}
        </>)}
        <SettingsInputRow
          label={m.common.locale.startsWith("ru") ? "Таймаут, с" : "Timeout, s"}
          value={timeoutDraft}
          inputMode="decimal"
          onChange={setTimeoutDraft}
          onCommit={saveTimeout}
          icon={<SlidersIcon />}
        />
        <SettingsChoiceRow
          label={m.settings.displayFormat}
          value={preferences.latency_display_format === "ms" ? "numeric" : preferences.latency_display_format === "badge" ? "dots" : preferences.latency_display_format}
          options={[
            { value: "numeric", label: m.settings.latencyMs },
            { value: "bars", label: m.settings.latencyBars },
            { value: "both", label: m.settings.latencyBoth },
            { value: "dots", label: m.settings.latencyDots },
          ]}
          onChange={(latency_display_format) => onChange({ latency_display_format })}
          icon={<InfoIcon />}
        />
      </SettingsCard>
    </Section>
  );
}

function BackupSection({ onImported }: { onImported: () => Promise<void> }) {
  const m = useMessages();
  const [password, setPassword] = useState("");
  const [payload, setPayload] = useState("");
  const [busy, setBusy] = useState(false);

  const exportBackup = async () => {
    setBusy(true);
    try {
      const backup = await api.exportAppBackup();
      setPayload(backup);
      await api.writeClipboardText(backup);
      notifyInfo(m.settings.backupExported);
    } catch (e) {
      notifyError(String(e));
    } finally {
      setBusy(false);
    }
  };

  const importBackup = async () => {
    setBusy(true);
    try {
      const source = payload.trim() || (await api.readClipboardText()).trim();
      if (!source) throw new Error(m.settings.backupEmpty);
      await api.importAppBackup(source);
      await onImported();
      notifyInfo(m.settings.backupImported);
    } catch (e) {
      notifyError(String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Section title={m.settings.backup}>
      <SettingsCard className="settings-backup-card">
        <div className="settings-row settings-row-block settings-backup-intro">
          <div>
            <div className="settings-row-title">{m.settings.export}</div>
            <div className="settings-row-description">{m.settings.exportDescription}</div>
            <div className="settings-row-description">{m.settings.backupHint}</div>
          </div>
        </div>
        <SettingsInputRow
          label={m.settings.passwordOptional}
          value={password}
          inputMode="text"
          type="password"
          placeholder={m.settings.passwordEmpty}
          onChange={setPassword}
          onCommit={() => undefined}
        />
        <div className="settings-row settings-backup-payload-row">
          <textarea
            value={payload}
            onChange={(e) => setPayload(e.target.value)}
            placeholder={m.settings.backupPayloadPlaceholder}
            className="settings-input settings-textarea"
          />
        </div>
        <div className="settings-row settings-backup-actions">
          <button disabled={busy} onClick={() => void exportBackup()} className="settings-action">
            {m.settings.exportAction}
          </button>
          <button disabled={busy} onClick={() => void importBackup()} className="settings-action">
            {m.settings.importAction}
          </button>
        </div>
      </SettingsCard>
    </Section>
  );
}

function UpdatesSection({
  preferences,
  updateInfo,
  appVersion,
  checking,
  installing,
  progress,
  onChange,
  onCheck,
  onDownload,
}: {
  preferences: AppPreferences;
  updateInfo: AppUpdateInfo | null;
  appVersion: string;
  checking: boolean;
  installing: boolean;
  progress: AppUpdateProgress | null;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
  onCheck: () => Promise<void>;
  onDownload: (info: AppUpdateInfo) => Promise<void>;
}) {
  const m = useMessages();
  const releaseNotes = updateInfo?.release_notes?.trim() || m.settings.updateFallbackNotes;
  const totalBytes = progress?.total_bytes || updateInfo?.asset?.size || 0;
  const downloadedBytes = Math.min(progress?.downloaded_bytes ?? 0, totalBytes || Number.MAX_SAFE_INTEGER);
  const percent = Math.max(0, Math.min(100, progress?.percent ?? 0));
  const platformLabel = updateInfo?.target?.toLowerCase().includes("linux") ? "Linux" : "Windows";

  return (
    <Section title={m.settings.updates}>
      <SettingsCard className="update-center-card">
        <div className="update-center-hero">
          <div className="update-center-heading">
            <div className="update-center-icon" aria-hidden="true"><DownloadIcon /></div>
            <div className="update-center-title-wrap">
              <span className="update-center-eyebrow">{platformLabel} · Nimbo</span>
              <h3>{updateInfo?.available ? m.settings.updateAvailable : m.settings.appUpdates}</h3>
              <p>{updateInfo?.available
                ? `v${updateInfo.latest_version} · ${updateInfo.reason === "reissued" ? m.settings.updateReasonReissued : m.settings.updateReasonNewVersion}`
                : `${m.settings.version} ${appVersion}`}</p>
            </div>
            <span className="update-center-status">
              {preferences.update_channel === "beta" ? m.settings.updateChannelBeta : m.settings.updateChannelStable}
            </span>
          </div>

          {updateInfo && (
            <div className="update-center-content">
              <div className="update-center-notes">
                <span className="update-center-label">{m.settings.updateReleaseNotes}</span>
                <UpdateReleaseNotes content={releaseNotes} />
              </div>
              <div className="update-center-facts">
                <span>{updateInfo.asset?.size ? formatBytes(updateInfo.asset.size) : m.settings.updateNoAsset}</span>
                <span>{updateInfo.asset?.digest ? m.settings.updateVerificationSha256 : m.settings.updateDigestMissing}</span>
                <span>{updateInfo.target}</span>
              </div>
            </div>
          )}

          {installing && (
            <div className="update-progress-card" aria-live="polite">
              <div className="update-progress-heading">
                <div>
                  <strong>{progress?.stage === "ready" ? m.settings.readyUpdate : progress?.stage === "verifying" ? m.settings.verifyingUpdate : m.settings.downloadingUpdate}</strong>
                  <span>{m.settings.updateDownloadProtection}</span>
                </div>
                <b>{percent}%</b>
              </div>
              <div className="update-progress-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
                <span style={{ width: `${Math.max(percent, 0)}%` }} />
              </div>
              <div className="update-progress-meta">
                <span>{formatBytes(downloadedBytes)}</span>
                <span>{totalBytes > 0 ? formatBytes(totalBytes) : "—"}</span>
              </div>
            </div>
          )}

          <OperationPhrase active={installing && progress?.stage !== "ready"} kind="download" locale={m.common.locale} />
          <div className="update-center-actions">
            {updateInfo?.release_url && (
              <a className="settings-action" href={updateInfo.release_url} target="_blank" rel="noreferrer">
                {m.settings.releasePage}
              </a>
            )}
            <button disabled={checking || installing} onClick={() => void onCheck()} className="settings-action">
              {checking ? m.settings.checkingUpdates : m.settings.checkForUpdates}
            </button>
            {updateInfo?.available && (
              <button
                disabled={!updateInfo.asset?.digest || installing}
                onClick={() => void onDownload(updateInfo)}
                className="settings-action settings-action-primary"
              >
                {installing ? (progress?.stage === "verifying" ? m.settings.verifyingUpdate : progress?.stage === "ready" ? m.settings.readyUpdate : m.settings.downloadingUpdate) : m.settings.downloadUpdate}
              </button>
            )}
          </div>
        </div>

        <div className="settings-row update-channel-row">
          <div>
            <div className="settings-row-title">{m.settings.updateChannel}</div>
            <div className="settings-row-description">{m.settings.updateChannelDescription}</div>
          </div>
          <label className="update-channel-select-wrap">
            <select
              className="update-channel-select"
              value={preferences.update_channel}
              onChange={(event) => void onChange({ update_channel: event.target.value as AppPreferences["update_channel"] })}
              aria-label={m.settings.updateChannel}
            >
              <option value="stable">{m.settings.updateChannelStable}</option>
              <option value="beta">{m.settings.updateChannelBeta}</option>
            </select>
            <span aria-hidden="true">⌄</span>
          </label>
        </div>
        <ToggleRow
          label={m.settings.checkUpdatesOnLaunch}
          description={m.settings.checkUpdatesOnLaunchDescription}
          enabled={preferences.check_updates_on_launch}
          onToggle={(check_updates_on_launch) => onChange({ check_updates_on_launch })}
          icon={<DownloadIcon />}
        />
        <ToggleRow
          label={m.settings.updateWifiOnly}
          description={m.settings.updateWifiOnlyDescription}
          enabled={preferences.update_wifi_only}
          onToggle={(update_wifi_only) => onChange({ update_wifi_only })}
          icon={<DownloadIcon />}
        />
      </SettingsCard>
    </Section>
  );
}

function UpdateReleaseNotes({ content }: { content: string }) {
  const lines = content.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  return (
    <div className="update-release-notes">
      {lines.map((line, index) => {
        const heading = line.match(/^#{1,6}\s+(.+)/)?.[1];
        if (heading) return <strong key={`${heading}-${index}`}>{heading}</strong>;
        const bullet = line.match(/^[-*•]\s+(.+)/)?.[1];
        if (bullet) return <p key={`${bullet}-${index}`}><span aria-hidden="true" />{bullet}</p>;
        return <p key={`${line}-${index}`}>{line}</p>;
      })}
    </div>
  );
}

function AboutSection({
  device,
  appVersion,
  copied,
  onCopyHwid,
}: {
  device: DeviceInfo | null;
  appVersion: string;
  copied: boolean;
  onCopyHwid: () => void;
}) {
  const m = useMessages();
  return (
    <Section title={m.settings.about}>
      <SettingsCard>
        <ValueRow label={m.settings.version} value={appVersion} />
        <ValueRow label={m.settings.engine} value="Rust + React + Tauri" />
        <ValueRow label={m.settings.developer} value="BBGGVP5" />
        <div className="settings-row">
          <div className="settings-row-title">HWID</div>
          <div className="flex min-w-0 items-center gap-2">
            <span className="settings-code truncate">{device?.hwid ?? "—"}</span>
            <button onClick={onCopyHwid} disabled={!device} className="settings-icon-button" title={m.settings.copyHwid}>
              {copied ? <CheckIcon /> : <ClipboardIcon />}
            </button>
          </div>
        </div>
        <ValueRow label={m.settings.os} value={device ? `${device.os} · ${device.os_version}` : "—"} />
        <ValueRow label="Hostname" value={device?.hostname ?? "—"} />
      </SettingsCard>

      <Section title={m.settings.usefulLinks} nested>
        <SettingsCard>
          <LinkRow
            icon={<GlobeIcon />}
            label={m.common.site}
            value="nimboapp.pw"
            href="https://nimboapp.pw"
          />
          <LinkRow
            icon={<TelegramIcon />}
            label={m.settings.telegramChannel}
            value="@nebulaguard_channel"
            href="https://t.me/nebulaguard_channel"
          />
          <LinkRow
            icon={<StarIcon />}
            label={m.settings.recommendedVpn}
            value="@nebulaguardd_bot"
            href="https://t.me/nebulaguardd_bot"
          />
        </SettingsCard>
      </Section>
    </Section>
  );
}

/**
 * Строка с выпадающим списком.
 *
 * Пять вариантов с длинными подписями в ряд сегментов не помещаются, а список
 * из пяти строк с галочками занимал бы пол-экрана ради значения, которое
 * меняют раз в жизни. Свёрнутая строка показывает выбранное, список
 * раскрывается по нажатию.
 */
function SettingsDropdownRow({
  label,
  description,
  value,
  options,
  onChange,
  icon,
}: {
  label: string;
  description?: string;
  value: string;
  options: Array<{ value: string; label: string; hint?: string }>;
  onChange: (value: string) => Promise<void> | void;
  icon?: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const current = options.find((option) => option.value === value);
  return (
    <div className="settings-dropdown">
      <button
        type="button"
        className="settings-row settings-dropdown-summary"
        aria-expanded={open}
        onClick={() => setOpen((state) => !state)}
      >
        <div className="settings-row-label-container">
          {icon && <span className="settings-row-icon">{icon}</span>}
          <div className="min-w-0">
            <div className="settings-row-title">{label}</div>
            {description && <div className="settings-row-description">{description}</div>}
          </div>
        </div>
        <span className="settings-dropdown-value">
          {current?.label ?? "—"}
          <DropdownChevron open={open} />
        </span>
      </button>
      {open && (
        <div className="settings-dropdown-list">
          {options.map((option) => (
            <button
              key={option.value}
              type="button"
              className={[
                "settings-dropdown-option",
                option.value === value ? "settings-dropdown-option-active" : "",
              ].join(" ")}
              onClick={() => {
                setOpen(false);
                if (option.value !== value) void onChange(option.value);
              }}
            >
              <span className="min-w-0">
                <span className="settings-dropdown-option-label">{option.label}</span>
                {option.hint && <span className="settings-dropdown-option-hint">{option.hint}</span>}
              </span>
              {option.value === value && <span className="settings-dropdown-check">✓</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function DropdownChevron({ open }: { open: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className="settings-dropdown-chevron"
      data-open={open ? "true" : undefined}
      aria-hidden="true"
    >
      <path
        d="M6 9.5 12 15.5 18 9.5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** Наборы DNS: значения уходят в TUN-интерфейс ядра. */
const TUNNEL_DNS_PRESETS = [
  { value: "", label: "Cloudflare + Google", hint: "1.1.1.1, 8.8.8.8" },
  { value: "8.8.8.8,8.8.4.4", label: "Google DNS", hint: "8.8.8.8, 8.8.4.4" },
  { value: "1.1.1.1,1.0.0.1", label: "Cloudflare DNS", hint: "1.1.1.1, 1.0.0.1" },
  { value: "9.9.9.9,149.112.112.112", label: "Quad9", hint: "9.9.9.9, 149.112.112.112" },
];

/**
 * DNS внутри туннеля.
 *
 * Кроме готовых наборов есть свой: в корпоративных и домашних сетях резолвер
 * часто единственный, кто знает внутренние имена.
 */
function TunnelDnsRow({
  value,
  onChange,
}: {
  value: string;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
}) {
  const m = useMessages();
  const preset = TUNNEL_DNS_PRESETS.find((option) => option.value === value);
  const [draft, setDraft] = useState(preset ? "" : value);

  useEffect(() => {
    setDraft(TUNNEL_DNS_PRESETS.some((option) => option.value === value) ? "" : value);
  }, [value]);

  return (
    <>
      <SettingsDropdownRow
        label={m.settings.tunnelDns}
        description={m.settings.tunnelDnsDescription}
        value={preset ? preset.value : "custom"}
        options={[
          ...TUNNEL_DNS_PRESETS.map((option) => ({
            value: option.value,
            label: option.label,
            hint: option.hint,
          })),
          { value: "custom", label: m.settings.tunnelDnsCustom, hint: m.settings.tunnelDnsCustomHint },
        ]}
        onChange={async (next) => {
          if (next === "custom") {
            // Пока своё поле пустое, набор не меняем: иначе выбор «свой» молча
            // сбросил бы туннель на встроенные адреса.
            setDraft(value);
            return;
          }
          await onChange({ tunnel_dns: next });
        }}
        icon={<GlobeIcon />}
      />
      {!preset && (
        <SettingsInputRow
          label={m.settings.tunnelDnsCustom}
          value={draft}
          compact
          inputMode="text"
          placeholder={m.settings.tunnelDnsCustomHint}
          onChange={setDraft}
          onCommit={() => {
            const next = draft.trim();
            if (next !== value) void onChange({ tunnel_dns: next });
          }}
          icon={<GlobeIcon />}
        />
      )}
    </>
  );
}

/** MTU TUN-интерфейса: 0 — как у обычного Ethernet. */
function TunnelMtuRow({
  value,
  onChange,
}: {
  value: number;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
}) {
  const m = useMessages();
  return (
    <SettingsDropdownRow
      label={m.settings.tunnelMtu}
      description={m.settings.tunnelMtuDescription}
      value={String(value)}
      options={[
        { value: "0", label: m.settings.tunnelMtuAuto },
        { value: "1420", label: "1420" },
        { value: "1380", label: "1380" },
        { value: "1280", label: "1280" },
      ]}
      onChange={(next) => onChange({ tunnel_mtu: Number(next) })}
      icon={<SlidersIcon />}
    />
  );
}

/**
 * Ручной сброс Kill Switch.
 *
 * Если приложение упало с поднятым туннелем, правила брандмауэра остаются, и
 * интернета нет — а снять их изнутри приложения было нечем.
 */
function ResetKillSwitchRow() {
  const m = useMessages();
  const [busy, setBusy] = useState(false);
  return (
    <div className="settings-row">
      <div className="settings-row-label-container">
        <span className="settings-row-icon">
          <RefreshIcon />
        </span>
        <div className="min-w-0">
          <div className="settings-row-title">{m.settings.resetKillSwitch}</div>
          <div className="settings-row-description">{m.settings.resetKillSwitchDescription}</div>
        </div>
      </div>
      <button
        type="button"
        className="settings-action"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          try {
            await api.resetKillSwitch();
            notifyInfo(m.settings.resetKillSwitchDone);
          } catch (error) {
            notifyError(String(error));
          } finally {
            setBusy(false);
          }
        }}
      >
        {m.settings.resetKillSwitch}
      </button>
    </div>
  );
}

function Section({
  title,
  children,
  nested = false,
}: {
  title: string;
  children: ReactNode;
  nested?: boolean;
}) {
  return (
    <section className={nested ? "mt-8" : ""}>
      <div className="settings-section-title">{title}</div>
      <div className="space-y-4">{children}</div>
    </section>
  );
}

function SettingsCard({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <Surface className={["settings-card", className].filter(Boolean).join(" ")}>{children}</Surface>;
}

function ToggleRow({
  label,
  description,
  enabled = false,
  onToggle,
  icon,
}: {
  label: string;
  description?: string;
  enabled?: boolean;
  onToggle?: (enabled: boolean) => void;
  icon?: ReactNode;
}) {
  return (
    <div className="settings-row">
      <div className="settings-row-label-container">
        {icon && <span className="settings-row-icon">{icon}</span>}
        <div>
          <div className="settings-row-title">{label}</div>
          {description && <div className="settings-row-description">{description}</div>}
        </div>
      </div>
      <button
        type="button"
        aria-pressed={enabled}
        onClick={() => onToggle?.(!enabled)}
        className={[
          "settings-toggle",
          enabled ? "settings-toggle-on" : "",
          onToggle ? "" : "settings-toggle-readonly",
        ].join(" ")}
      >
        <span />
      </button>
    </div>
  );
}

function ValueRow({
  label,
  value,
  description,
  muted = false,
  mono = false,
  copyValue,
  icon,
}: {
  label: string;
  value: string;
  description?: string;
  muted?: boolean;
  mono?: boolean;
  copyValue?: string;
  icon?: ReactNode;
}) {
  return (
    <div className={["settings-row", muted ? "settings-row-muted" : ""].join(" ")}>
      <div className="settings-row-label-container">
        {icon && <span className="settings-row-icon">{icon}</span>}
        <div className="min-w-0">
          <div className="settings-row-title">{label}</div>
          {description && <div className="settings-row-description">{description}</div>}
        </div>
      </div>
      <div className="settings-value-actions">
        <span className={["settings-value", mono ? "font-mono" : ""].join(" ")}>{value}</span>
        {copyValue !== undefined && <CopyButton value={copyValue} />}
      </div>
    </div>
  );
}

function SettingsChoiceRow<T extends string>({
  label,
  description,
  value,
  options,
  onChange,
  icon,
}: {
  label: string;
  description?: string;
  value: T;
  options: Array<{ value: T; label: string; icon?: ReactNode }>;
  onChange: (value: T) => Promise<void>;
  icon?: ReactNode;
}) {
  return (
    <div className="settings-row">
      <div className="settings-row-label-container">
        {icon && <span className="settings-row-icon">{icon}</span>}
        <div className="min-w-0">
          <div className="settings-row-title">{label}</div>
          {description && <div className="settings-row-description">{description}</div>}
        </div>
      </div>
      <div
        className={[
          "settings-choice-control",
          options.length === 1 ? "settings-choice-control-single" : "",
          options.length >= 4 ? "settings-choice-control-wide" : "",
        ].join(" ")}
      >
        {options.map((option) => (
          <button
            key={option.value}
            type="button"
            aria-pressed={value === option.value}
            onClick={() => {
              if (option.value !== value) void onChange(option.value);
            }}
            className={[
              "settings-choice-button",
              value === option.value ? "settings-choice-button-active" : "",
            ].join(" ")}
          >
            {option.icon && (
              <span className="settings-choice-icon" aria-hidden="true">
                {option.icon}
              </span>
            )}
            <span className="settings-choice-label">{option.label}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

function NumberPreferenceRow({
  label,
  description,
  value,
  min,
  max,
  suffix,
  onCommit,
  icon,
}: {
  label: string;
  description?: string;
  value: number;
  min: number;
  max: number;
  suffix?: string;
  onCommit: (value: number) => Promise<void>;
  icon?: ReactNode;
}) {
  const [draft, setDraft] = useState(String(value));

  useEffect(() => {
    setDraft(String(value));
  }, [value]);

  const commit = () => {
    const parsed = Number.parseInt(draft, 10);
    const next = Number.isFinite(parsed)
      ? Math.min(max, Math.max(min, parsed))
      : value;
    setDraft(String(next));
    if (next !== value) void onCommit(next);
  };

  return (
    <div className="settings-row">
      <div className="settings-row-label-container">
        {icon && <span className="settings-row-icon">{icon}</span>}
        <div className="min-w-0">
          <div className="settings-row-title">{label}</div>
          {description && <div className="settings-row-description">{description}</div>}
        </div>
      </div>
      <div className="settings-value-actions">
        <input
          value={draft}
          inputMode="numeric"
          onChange={(e) => setDraft(e.target.value)}
          onBlur={commit}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.currentTarget.blur();
            }
          }}
          className="settings-input settings-field-input settings-field-input-numeric"
        />
        {suffix && <span className="settings-value">{suffix}</span>}
      </div>
    </div>
  );
}

function SettingsInputRow({
  label,
  value,
  inputMode,
  type = "text",
  placeholder,
  compact = false,
  copyValue,
  onChange,
  onCommit,
  icon,
}: {
  label: string;
  value: string;
  inputMode: "text" | "url" | "numeric" | "decimal";
  type?: "text" | "password";
  placeholder?: string;
  compact?: boolean;
  copyValue?: string;
  onChange: (value: string) => void;
  onCommit: () => void;
  icon?: ReactNode;
}) {
  return (
    <div className="settings-row">
      <div className="settings-row-label-container">
        {icon && <span className="settings-row-icon">{icon}</span>}
        <div className="min-w-0">
          <div className="settings-row-title">{label}</div>
        </div>
      </div>
      <div
        className={[
          "settings-value-actions settings-input-actions",
          compact ? "settings-input-actions-compact" : "",
          (inputMode === "numeric" || inputMode === "decimal") ? "settings-input-actions-numeric" : "",
        ].join(" ")}
      >
        <input
          type={type}
          aria-label={label}
          value={value}
          inputMode={inputMode}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
          onBlur={onCommit}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.currentTarget.blur();
            }
          }}
          className={[
            "settings-input settings-field-input",
            (inputMode === "numeric" || inputMode === "decimal") ? "settings-field-input-numeric" : "",
          ].join(" ")}
        />
        {copyValue !== undefined && <CopyButton value={copyValue} />}
      </div>
    </div>
  );
}

function CopyButton({ value }: { value: string }) {
  const m = useMessages();
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    const text = value.trim();
    if (!text) return;
    try {
      await api.writeClipboardText(text);
      setCopied(true);
      notifyInfo(m.settings.copiedValue);
      window.setTimeout(() => setCopied(false), 1300);
    } catch (e) {
      notifyError(String(e));
    }
  };

  return (
    <button
      type="button"
      onClick={() => void copy()}
      disabled={!value.trim()}
      className="settings-icon-button settings-copy-button"
      title={m.settings.copyValue}
      aria-label={m.settings.copyValue}
    >
      {copied ? <CheckIcon /> : <ClipboardIcon />}
    </button>
  );
}

function LinkRow({
  icon,
  label,
  value,
  href,
}: {
  icon: ReactNode;
  label: string;
  value: string;
  href: string;
}) {
  return (
    <a className="settings-row hover:bg-[var(--color-glass-bg)]" href={href} target="_blank" rel="noreferrer">
      <span className="settings-link-icon">{icon}</span>
      <span className="settings-row-title flex-1">{label}</span>
      <span className="settings-value">{value}</span>
    </a>
  );
}

function UaOption({
  selected,
  disabled,
  onClick,
  title,
  subtitle,
}: {
  selected: boolean;
  disabled: boolean;
  onClick: () => void;
  title: string;
  subtitle: string;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={[
        "settings-ua-option",
        selected ? "settings-ua-option-active" : "",
        disabled ? "opacity-50" : "",
      ].join(" ")}
    >
      <span className={["settings-radio", selected ? "settings-radio-on" : ""].join(" ")} />
      <span className="min-w-0">
        <span className="block text-sm font-semibold text-white">{title}</span>
        <span className="block truncate font-mono text-xs text-[var(--color-text-faint)]">{subtitle}</span>
      </span>
    </button>
  );
}

function isHexColor(value: string | null | undefined): value is string {
  return typeof value === "string" && /^#[0-9a-fA-F]{6}$/.test(value.trim());
}

function providerCssColor(value: string | null | undefined, fallback: string): string {
  return isHexColor(value) ? value.trim() : fallback;
}

function providerUiChip(theme: SubscriptionTheme | null): string {
  if (theme?.ui_style === "material_you") return "M3";
  if (theme?.ui_style === "nimbo") return "Nimbo";
  const filter = theme?.filter?.trim();
  return filter ? filter.slice(0, 8) : "Nimbo";
}

function SubscriptionProviderPreview({
  sub,
  theme,
  logoSrc,
  themeEnabled,
  logoEnabled,
  labels,
}: {
  sub: Subscription | null;
  theme: SubscriptionTheme | null;
  logoSrc: string | null;
  themeEnabled: boolean;
  logoEnabled: boolean;
  labels: Messages;
}) {
  const accent = providerCssColor(theme?.accent, "var(--color-accent)");
  const orb1 = providerCssColor(theme?.orb1, accent);
  const orb2 = providerCssColor(theme?.orb2, "var(--color-accent-bright)");
  const subscriptionName = sub?.name?.trim() || labels.common.subscription;
  const themeStyle = {
    "--provider-accent": accent,
    "--provider-orb-1": orb1,
    "--provider-orb-2": orb2,
  } as CSSProperties;

  return (
    <div className="settings-provider-preview" aria-label={`${labels.settings.providerTheme} · ${labels.settings.showSubscriptionLogo}`}>
      <div className={["settings-provider-preview-card", themeEnabled ? "is-active" : ""].join(" ")}>
        <div className="settings-provider-preview-art settings-provider-theme-art" style={themeStyle}>
          <span className="settings-provider-theme-orb settings-provider-theme-orb-one" />
          <span className="settings-provider-theme-orb settings-provider-theme-orb-two" />
          <span className="settings-provider-theme-panel">
            <span className="settings-provider-theme-line settings-provider-theme-line-strong" />
            <span className="settings-provider-theme-line" />
            <span className="settings-provider-theme-line settings-provider-theme-line-short" />
          </span>
          <span className="settings-provider-theme-chip">{providerUiChip(theme)}</span>
        </div>
        <div className="settings-provider-preview-label">
          {theme ? labels.settings.providerThemeOn : labels.settings.providerThemeOff}
        </div>
      </div>
      <div className={["settings-provider-preview-card", logoEnabled ? "is-active" : ""].join(" ")}>
        <div className="settings-provider-preview-art settings-provider-logo-art">
          {logoSrc ? (
            <img src={logoSrc} alt="" className="subscription-logo-image subscription-logo-image-lg settings-provider-logo-image" />
          ) : (
            <span className="settings-provider-logo-fallback">
              <GlobeIcon />
            </span>
          )}
        </div>
        <div className="settings-provider-preview-label">{subscriptionName}</div>
      </div>
    </div>
  );
}

function SlidersIcon() {
  return <Icon><path d="M4 6h16" /><path d="M4 12h16" /><path d="M4 18h16" /><path d="M8 4v4" /><path d="M16 10v4" /><path d="M11 16v4" /></Icon>;
}
function PaletteIcon() {
  return <Icon><path d="M12 21a9 9 0 1 1 9-9c0 1.4-.8 2-2 2h-1.5a2 2 0 0 0-2 2c0 .5.2 1 .5 1.4.3.4.5.8.5 1.3 0 1.5-1.6 2.3-4.5 2.3Z" /><path d="M7.5 10.5h.01" /><path d="M10 7.5h.01" /><path d="M14 7.5h.01" /><path d="M16.5 10.5h.01" /></Icon>;
}
function PlugIcon() {
  return <Icon><path d="M9 7V2" /><path d="M15 7V2" /><path d="M7 7h10v5a5 5 0 0 1-10 0V7Z" /><path d="M12 17v5" /></Icon>;
}
function ShieldIcon() {
  return <Icon><path d="M12 3 19 6v5.2c0 4.1-2.8 7.8-7 9.8-4.2-2-7-5.7-7-9.8V6l7-3Z" /></Icon>;
}
function GlobeIcon() {
  return <Icon><circle cx="12" cy="12" r="9" /><path d="M3 12h18" /><path d="M12 3a13.5 13.5 0 0 1 0 18" /><path d="M12 3a13.5 13.5 0 0 0 0 18" /></Icon>;
}
function HomeIcon() {
  return <Icon><path d="m3 11 9-8 9 8" /><path d="M5 10v10h14V10" /><path d="M10 20v-6h4v6" /></Icon>;
}
function RefreshIcon() {
  return <Icon><path d="M20 6v5h-5" /><path d="M4 18v-5h5" /><path d="M19 11a7 7 0 0 0-12-4l-3 3" /><path d="M5 13a7 7 0 0 0 12 4l3-3" /></Icon>;
}
function ListIcon() {
  return <Icon><path d="M8 6h13" /><path d="M8 12h13" /><path d="M8 18h13" /><path d="M3 6h.01" /><path d="M3 12h.01" /><path d="M3 18h.01" /></Icon>;
}
function SignalIcon() {
  return <Icon><path d="M4 20v-2" /><path d="M8 20v-5" /><path d="M12 20v-8" /><path d="M16 20v-11" /><path d="M20 20V5" /></Icon>;
}
function ArchiveIcon() {
  return <Icon><path d="M4 8h16" /><path d="M5 8l1 12h12l1-12" /><path d="M7 4h10l1 4H6l1-4Z" /><path d="M10 12h4" /></Icon>;
}
function DownloadIcon() {
  return <Icon><path d="M12 3v12" /><path d="m7 10 5 5 5-5" /><path d="M5 21h14" /></Icon>;
}
function InfoIcon() {
  return <Icon><circle cx="12" cy="12" r="9" /><path d="M12 11v5" /><path d="M12 8h.01" /></Icon>;
}
function ClipboardIcon() {
  return <Icon><path d="M9 4h6" /><path d="M9 4a3 3 0 0 0 6 0" /><rect x="6" y="5" width="12" height="16" rx="2" /></Icon>;
}
function CheckIcon() {
  return <Icon><path d="m5 12 4 4L19 6" /></Icon>;
}
function TelegramIcon() {
  return <Icon><path d="m21 4-4.5 16-5-6-6-2 15.5-8Z" /><path d="m11.5 14 3.5-3.5" /></Icon>;
}
function StarIcon() {
  return <Icon><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9L12 3Z" /></Icon>;
}
function RouteIcon() {
  return <Icon><circle cx="6" cy="19" r="2.4" /><circle cx="18" cy="5" r="2.4" /><path d="M16.6 6.4 7.4 17.6" /><path d="M8 7h5a3 3 0 0 1 0 6h-2a3 3 0 0 0 0 6h5" /></Icon>;
}
function StatsBarsIcon() {
  return <Icon><path d="M4 19V9" /><path d="M10 19V5" /><path d="M16 19v-7" /><path d="M3 21h18" /></Icon>;
}
function LogsIcon() {
  return <Icon><path d="M7 3h7l4 4v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z" /><path d="M14 3v4h4" /><path d="M9 12h7M9 16h5" /></Icon>;
}
function ConnectionsIcon() {
  return <Icon><path d="M4 7h16M4 12h16M4 17h16" /><circle cx="8" cy="7" r="1.5" /><circle cx="14" cy="12" r="1.5" /><circle cx="10" cy="17" r="1.5" /></Icon>;
}
function PowerIcon() {
  return <Icon><path d="M18.36 6.64a9 9 0 1 1-12.73 0" /><line x1="12" y1="2" x2="12" y2="12" /></Icon>;
}
function ZapIcon() {
  return <Icon><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" /></Icon>;
}
function MinimizeIcon() {
  return <Icon><path d="M4 14h6v6M20 10h-6V4M14 10l7-7M10 14l-7 7" /></Icon>;
}
function TrayIcon() {
  return <Icon><polyline points="22 12 16 12 14 15 10 15 8 12 2 12" /><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" /></Icon>;
}
function ActivityIcon() {
  return <Icon><polyline points="22 12 18 12 15 21 9 3 6 12 2 12" /></Icon>;
}
function CpuIcon() {
  return <Icon><rect x="4" y="4" width="16" height="16" rx="2" /><rect x="9" y="9" width="6" height="6" /><line x1="9" y1="1" x2="9" y2="4" /><line x1="15" y1="1" x2="15" y2="4" /><line x1="9" y1="20" x2="9" y2="23" /><line x1="15" y1="20" x2="15" y2="23" /><line x1="20" y1="9" x2="23" y2="9" /><line x1="20" y1="15" x2="23" y2="15" /><line x1="1" y1="9" x2="4" y2="9" /><line x1="1" y1="15" x2="4" y2="15" /></Icon>;
}
function UserIcon() {
  return <Icon><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /></Icon>;
}
function LockIcon() {
  return <Icon><rect x="3" y="11" width="18" height="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></Icon>;
}

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" className="h-full w-full" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      {children}
    </svg>
  );
}
