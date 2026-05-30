import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  api,
  defaultAppPreferences,
  formatBytes,
  type AppPreferences,
  type AppUpdateInfo,
  type ConnectionMode,
  type DeviceInfo,
  type ProxySettingsPatch,
} from "../lib/api";
import { fillTemplate, useMessages } from "../lib/i18n";
import { notifyError, notifyInfo } from "../lib/notify";
import { useAppStore } from "../store";
import { showAppUpdateDialog } from "../App";

const NIMBO_UA_FALLBACK = "Nimbo/0.1.0";
const HAPP_UA = "Happ/2.0.0";
const INCY_UA = "Incy/2.1.0";
const APP_VERSION = "0.1.0";
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
  const setPreferences = useAppStore((s) => s.setPreferences);
  const refreshSubscription = useAppStore((s) => s.refreshSubscription);
  const status = useAppStore((s) => s.status);
  const hydrate = useAppStore((s) => s.hydrate);
  const [section, setSection] = useState<SettingsSection>("general");
  const [device, setDevice] = useState<DeviceInfo | null>(null);
  const [override, setOverride] = useState<string | null>(null);
  const [mode, setMode] = useState<UaMode>("default");
  const [customUa, setCustomUa] = useState("");
  const [copied, setCopied] = useState(false);
  const [savingUa, setSavingUa] = useState(false);
  const [refreshingSubscriptions, setRefreshingSubscriptions] = useState(false);
  const [checkingUpdates, setCheckingUpdates] = useState(false);
  const [updateInfo, setUpdateInfo] = useState<AppUpdateInfo | null>(null);
  const connectionMode = status?.connection_mode ?? "tun";

  const updatePreferences = async (patch: Partial<AppPreferences>) => {
    try {
      await setPreferences({ ...preferences, ...patch });
      notifyInfo(m.settings.saved);
    } catch (e) {
      notifyError(String(e));
    }
  };

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
      const info = await api.checkAppUpdate();
      setUpdateInfo(info);
      if (info.available) {
        showAppUpdateDialog(info);
      }
      notifyInfo(
        info.available
          ? fillTemplate(m.settings.updateReady, { version: info.latest_version })
          : m.settings.updateCurrent,
      );
    } catch (e) {
      notifyError(`${m.settings.updateCheckFailed} ${String(e)}`);
    } finally {
      setCheckingUpdates(false);
    }
  };

  const downloadUpdate = async (info: AppUpdateInfo) => {
    const url = info.download_url ?? info.release_url;
    try {
      await api.openUpdateDownload(url);
    } catch (e) {
      notifyError(String(e));
    }
  };

  const updateConnectionMode = async (nextMode: ConnectionMode) => {
    try {
      if (nextMode === "tun") {
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
      await hydrate();
    } catch (e) {
      notifyError(String(e));
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
      <h1 className="page-title">{m.settings.title}</h1>

      <nav className="settings-tools-row" aria-label={m.app.settings}>
        <Link to="/routing" className="settings-tools-item interactive">
          <span className="settings-tools-icon"><RouteIcon /></span>
          <span className="settings-tools-label">{m.app.routing}</span>
        </Link>
        <Link to="/connections" className="settings-tools-item interactive">
          <span className="settings-tools-icon"><ConnectionsIcon /></span>
          <span className="settings-tools-label">{m.app.connections}</span>
        </Link>
        <Link to="/statistics" className="settings-tools-item interactive">
          <span className="settings-tools-icon"><StatsBarsIcon /></span>
          <span className="settings-tools-label">{m.app.statistics}</span>
        </Link>
        <Link to="/tunnel-logs" className="settings-tools-item interactive">
          <span className="settings-tools-icon"><LogsIcon /></span>
          <span className="settings-tools-label">{m.app.tunnelLogs}</span>
        </Link>
      </nav>

      <div className="settings-layout">
        <aside className="settings-side liquid-card">
          {sectionItems.map((item) => (
            <button
              key={item.id}
              onClick={() => setSection(item.id)}
              aria-label={m.settings[item.labelKey]}
              title={m.settings[item.labelKey]}
              className={[
                "settings-side-item interactive",
                section === item.id ? "settings-side-item-active" : "",
              ].join(" ")}
            >
              <span className="settings-side-icon">{item.icon}</span>
              <span className="settings-side-label">{m.settings[item.labelKey]}</span>
            </button>
          ))}
        </aside>

        <main className="settings-content">
          {section === "general" && (
            <GeneralSection
              preferences={preferences}
              onChange={updatePreferences}
            />
          )}
          {section === "appearance" && (
            <AppearanceSection
              preferences={preferences}
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
              checking={checkingUpdates}
              onChange={updatePreferences}
              onCheck={checkForUpdates}
              onDownload={downloadUpdate}
            />
          )}
          {section === "about" && (
            <AboutSection
              device={device}
              copied={copied}
              onCopyHwid={onCopyHwid}
            />
          )}
        </main>
      </div>
    </div>
  );
}

const accentPresets = [
  "#7c5dfa",
  "#4f8cff",
  "#21a67a",
  "#e24d70",
  "#f5a623",
  "#00a8c8",
];

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
        />
        <ToggleRow
          label={m.settings.autoConnect}
          description={m.settings.autoConnectDescription}
          enabled={preferences.auto_connect_on_launch}
          onToggle={(auto_connect_on_launch) => onChange({ auto_connect_on_launch })}
        />
        <ToggleRow
          label={m.settings.startMinimized}
          enabled={preferences.start_minimized}
          onToggle={(start_minimized) => onChange({ start_minimized })}
        />
        <ToggleRow
          label={m.settings.minimizeToTray}
          description={m.settings.minimizeToTrayDescription}
          enabled={preferences.minimize_to_tray}
          onToggle={(minimize_to_tray) => onChange({ minimize_to_tray })}
        />
        <ToggleRow
          label={m.settings.pingOnLaunch}
          enabled={preferences.ping_on_launch}
          onToggle={(ping_on_launch) => onChange({ ping_on_launch })}
        />
        <ToggleRow
          label={m.settings.showSpeedChart}
          description={m.settings.showSpeedChartDescription}
          enabled={preferences.show_speed_chart}
          onToggle={(show_speed_chart) => onChange({ show_speed_chart })}
        />
        <ToggleRow
          label={m.settings.showMemoryUsage}
          description={m.settings.showMemoryUsageDescription}
          enabled={preferences.show_memory_usage}
          onToggle={(show_memory_usage) => onChange({ show_memory_usage })}
        />
      </SettingsCard>
    </Section>
  );
}

function AppearanceSection({
  preferences,
  onChange,
}: {
  preferences: AppPreferences;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
}) {
  const m = useMessages();
  const [customAccentDraft, setCustomAccentDraft] = useState(preferences.accent_color);
  const customAccentTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (preferences.accent_mode !== "custom") {
      setCustomAccentDraft(preferences.accent_color);
    }
  }, [preferences.accent_color, preferences.accent_mode]);

  useEffect(() => {
    return () => {
      if (customAccentTimer.current) clearTimeout(customAccentTimer.current);
    };
  }, []);

  const saveCustomAccent = (color: string, immediate = false) => {
    if (customAccentTimer.current) clearTimeout(customAccentTimer.current);
    const commit = () => {
      if (
        preferences.accent_mode === "custom" &&
        preferences.accent_color.toLowerCase() === color.toLowerCase()
      ) {
        return;
      }
      void onChange({ accent_mode: "custom", accent_color: color });
    };

    if (immediate) {
      commit();
      return;
    }

    customAccentTimer.current = setTimeout(commit, 280);
  };

  return (
    <Section title={m.settings.appearance}>
      <SettingsCard>
        <div className="settings-row settings-row-block">
          <div>
            <div className="settings-row-title">{m.settings.interfaceStyle}</div>
            <div className="settings-row-description">{m.settings.interfaceStyleDescription}</div>
          </div>
        </div>
        <div className="settings-segment px-6 pb-4">
          <ModeOption
            title={m.settings.nebulaStyle}
            subtitle={m.settings.nebulaStyleSubtitle}
            selected={preferences.ui_style === "nebula"}
            onClick={() => onChange({ ui_style: "nebula" })}
          />
          <ModeOption
            title={m.settings.materialYouStyle}
            subtitle={m.settings.materialYouStyleSubtitle}
            selected={preferences.ui_style === "material_you"}
            onClick={() => onChange({ ui_style: "material_you" })}
          />
        </div>

        <div className="settings-row settings-row-block">
          <div>
            <div className="settings-row-title">{m.settings.theme}</div>
            <div className="settings-row-description">{m.settings.themeDescription}</div>
          </div>
        </div>
        <div className="settings-segment px-6 pb-4">
          <ModeOption
            title={m.settings.systemTheme}
            subtitle={m.settings.systemThemeSubtitle}
            selected={preferences.theme_mode === "system"}
            onClick={() => onChange({ theme_mode: "system" })}
          />
          <ModeOption
            title={m.settings.darkTheme}
            subtitle={m.settings.darkThemeSubtitle}
            selected={preferences.theme_mode === "dark"}
            onClick={() => onChange({ theme_mode: "dark" })}
          />
          <ModeOption
            title={m.settings.blackTheme}
            subtitle={m.settings.blackThemeSubtitle}
            selected={preferences.theme_mode === "black"}
            onClick={() => onChange({ theme_mode: "black" })}
          />
          <ModeOption
            title={m.settings.lightTheme}
            subtitle={m.settings.lightThemeSubtitle}
            selected={preferences.theme_mode === "light"}
            onClick={() => onChange({ theme_mode: "light" })}
          />
        </div>

        <div className="settings-row settings-row-block">
          <div>
            <div className="settings-row-title">{m.settings.accentColor}</div>
            <div className="settings-row-description">{m.settings.accentDescription}</div>
          </div>
        </div>
        <div className="settings-accent-panel">
          <button
            onClick={() => onChange({ accent_mode: "system" })}
            aria-pressed={preferences.accent_mode === "system"}
            className={[
              "settings-accent-system",
              preferences.accent_mode === "system" ? "settings-accent-active" : "",
            ].join(" ")}
          >
            <span className="settings-accent-system-swatch" />
            {m.settings.systemAccent}
          </button>
          <div className="settings-color-grid">
            {accentPresets.map((color) => (
              <button
                key={color}
                title={color}
                onClick={() => onChange({ accent_mode: "preset", accent_color: color })}
                aria-pressed={preferences.accent_mode === "preset" && preferences.accent_color.toLowerCase() === color}
                className={[
                  "settings-color-swatch",
                  preferences.accent_mode === "preset" && preferences.accent_color.toLowerCase() === color
                    ? "settings-color-swatch-active"
                    : "",
                ].join(" ")}
                style={{ backgroundColor: color }}
              />
            ))}
            <label
              className={[
                "settings-color-custom",
                preferences.accent_mode === "custom" ? "settings-color-custom-active" : "",
              ].join(" ")}
            >
              <span className="settings-color-custom-preview" style={{ backgroundColor: customAccentDraft }} />
              <input
                type="color"
                value={customAccentDraft}
                onChange={(e) => {
                  const color = e.target.value;
                  setCustomAccentDraft(color);
                  saveCustomAccent(color);
                }}
                onBlur={() => saveCustomAccent(customAccentDraft, true)}
              />
              {m.settings.customAccent}
            </label>
          </div>
        </div>

        <div className="settings-row settings-row-block">
          <div>
            <div className="settings-row-title">{m.settings.language}</div>
            <div className="settings-row-description">{m.settings.languageDescription}</div>
          </div>
        </div>
        <div className="settings-segment px-6 pb-4">
          <ModeOption
            title="Русский"
            subtitle="ru"
            selected={preferences.language === "ru"}
            onClick={() => onChange({ language: "ru" })}
          />
          <ModeOption
            title="English"
            subtitle="en"
            selected={preferences.language === "en"}
            onClick={() => onChange({ language: "en" })}
          />
        </div>
        <ToggleRow
          label={m.settings.providerTheme}
          description={m.settings.providerThemeDescription}
          enabled={preferences.provider_theme}
          onToggle={(provider_theme) => onChange({ provider_theme })}
        />
      </SettingsCard>
    </Section>
  );
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
        />
        <ToggleRow
          label="TUN"
          description={m.settings.tunSubtitle}
          enabled={tunOn}
          onToggle={toggleTun}
        />
        <ToggleRow
          label="Kill switch"
          enabled={killSwitch}
          onToggle={(connection_kill_switch) => onPreferences({ connection_kill_switch })}
        />
      </SettingsCard>
      <SettingsCard>
        <ValueRow label="HTTP proxy" value={httpProxy} copyValue={httpProxy} mono />
        <ValueRow label="SOCKS5 proxy" value={socksProxy} copyValue={socksProxy} mono />
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
        />
        <ToggleRow
          label={m.settings.requireSocksAuth}
          description={m.settings.requireSocksAuthDescription}
          enabled={requireSocksAuth}
          onToggle={(require_socks_auth) => onProxySettings({ require_socks_auth })}
        />
        <ToggleRow
          label={m.settings.blockSocksUdp}
          description={m.settings.blockSocksUdpDescription}
          enabled={blockSocksUdp}
          onToggle={(block_socks_udp) => onProxySettings({ block_socks_udp })}
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
        />
        <ToggleRow
          label="Mux"
          description={m.settings.muxDescription}
          enabled={preferences.tunnel_mux_enabled}
          onToggle={(tunnel_mux_enabled) => onChange({ tunnel_mux_enabled })}
        />
        <NumberPreferenceRow
          label="Mux concurrency"
          value={preferences.tunnel_mux_concurrency}
          min={1}
          max={1024}
          onCommit={(tunnel_mux_concurrency) => onChange({ tunnel_mux_concurrency })}
        />
        <NumberPreferenceRow
          label="xUDP concurrency"
          description="-1 — выкл, 0+ — включён"
          value={preferences.tunnel_xudp_concurrency}
          min={-1}
          max={1024}
          onCommit={(tunnel_xudp_concurrency) => onChange({ tunnel_xudp_concurrency })}
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
        />
        <ToggleRow
          label={m.settings.tlsFragmentation}
          description={m.settings.tlsFragmentationDescription}
          enabled={preferences.tunnel_tls_fragmentation}
          onToggle={(tunnel_tls_fragmentation) => onChange({ tunnel_tls_fragmentation })}
        />
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
        />
        <ToggleRow
          label={m.settings.allowTethering}
          description={m.settings.allowTetheringDescription}
          enabled={preferences.lan_allow_tethering}
          onToggle={(lan_allow_tethering) => onChange({ lan_allow_tethering })}
        />
        <ToggleRow
          label={m.settings.lanProxy}
          description={m.settings.lanProxyDescription}
          enabled={preferences.lan_proxy_enabled}
          onToggle={(lan_proxy_enabled) => onChange({ lan_proxy_enabled })}
        />
      </SettingsCard>
      <SettingsCard>
        <NumberPreferenceRow
          label={m.settings.tcpIdleTimeout}
          value={preferences.lan_tcp_idle_timeout_sec}
          min={5}
          max={3600}
          onCommit={(lan_tcp_idle_timeout_sec) => onChange({ lan_tcp_idle_timeout_sec })}
        />
        <NumberPreferenceRow
          label={m.settings.maxTcp}
          value={preferences.lan_max_tcp_connections}
          min={1}
          max={100000}
          onCommit={(lan_max_tcp_connections) => onChange({ lan_max_tcp_connections })}
        />
        <NumberPreferenceRow
          label={m.settings.maxUdp}
          value={preferences.lan_max_udp_connections}
          min={1}
          max={100000}
          onCommit={(lan_max_udp_connections) => onChange({ lan_max_udp_connections })}
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
        />
        <NumberPreferenceRow
          label={m.settings.updateInterval}
          value={preferences.subscriptions_update_interval_hours}
          min={1}
          max={168}
          suffix="h"
          onCommit={(subscriptions_update_interval_hours) => onChange({ subscriptions_update_interval_hours })}
        />
        <ToggleRow
          label={m.settings.notifyExpiration}
          enabled={preferences.subscriptions_notify_expiration}
          onToggle={(subscriptions_notify_expiration) => onChange({ subscriptions_notify_expiration })}
        />
        <NumberPreferenceRow
          label={m.settings.daysLeftThreshold}
          value={preferences.subscriptions_expiration_threshold_days}
          min={1}
          max={365}
          suffix="d"
          onCommit={(subscriptions_expiration_threshold_days) => onChange({ subscriptions_expiration_threshold_days })}
        />
        <ToggleRow
          label={m.settings.notifySubscriptionUpdate}
          enabled={preferences.subscriptions_notify_updates}
          onToggle={(subscriptions_notify_updates) => onChange({ subscriptions_notify_updates })}
        />
        <ToggleRow
          label={m.settings.updateOnLaunch}
          enabled={preferences.subscriptions_update_on_launch}
          onToggle={(subscriptions_update_on_launch) => onChange({ subscriptions_update_on_launch })}
        />
        <ToggleRow
          label={m.settings.pingAfterUpdate}
          enabled={preferences.subscriptions_ping_after_update}
          onToggle={(subscriptions_ping_after_update) => onChange({ subscriptions_ping_after_update })}
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
              {m.settings.uaDefaultFormat}
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
        />
        <SettingsChoiceRow
          label={m.settings.connectButton}
          value={preferences.servers_connect_button}
          options={[
            { value: "classic", label: m.profiles.classic, icon: <ClassicButtonIcon /> },
            { value: "compact", label: m.settings.compact, icon: <CompactButtonIcon /> },
          ]}
          onChange={(servers_connect_button) => onChange({ servers_connect_button })}
        />
        <NumberPreferenceRow
          label={m.settings.uiScale}
          value={preferences.servers_ui_scale}
          min={80}
          max={125}
          suffix="%"
          onCommit={(servers_ui_scale) => onChange({ servers_ui_scale })}
        />
        <ToggleRow
          label={m.settings.proxyOnlyButton}
          enabled={preferences.servers_proxy_only_button}
          onToggle={(servers_proxy_only_button) => onChange({ servers_proxy_only_button })}
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
  const [timeoutDraft, setTimeoutDraft] = useState(String(preferences.latency_timeout_ms));

  useEffect(() => {
    setTestUrlDraft(preferences.latency_test_url);
  }, [preferences.latency_test_url]);

  useEffect(() => {
    setTimeoutDraft(String(preferences.latency_timeout_ms));
  }, [preferences.latency_timeout_ms]);

  const saveTestUrl = () => {
    const next = /^https?:\/\//i.test(testUrlDraft.trim())
      ? testUrlDraft.trim()
      : defaultAppPreferences.latency_test_url;
    setTestUrlDraft(next);
    if (next !== preferences.latency_test_url) {
      void onChange({ latency_test_url: next });
    }
  };

  const saveTimeout = () => {
    const parsed = Number.parseInt(timeoutDraft, 10);
    const next = Number.isFinite(parsed)
      ? Math.min(60000, Math.max(500, parsed))
      : defaultAppPreferences.latency_timeout_ms;
    setTimeoutDraft(String(next));
    if (next !== preferences.latency_timeout_ms) {
      void onChange({ latency_timeout_ms: next });
    }
  };

  return (
    <Section title={m.settings.latency}>
      <SettingsCard>
        <SettingsChoiceRow
          label={m.settings.protocol}
          description={m.settings.latencyProtocolDescription}
          value={preferences.latency_protocol}
          options={[
            { value: "tcp_connect", label: m.settings.latencyTcpConnect },
            { value: "icmp", label: m.settings.latencyIcmp },
            { value: "http_head", label: m.settings.latencyHttpHead },
          ]}
          onChange={(latency_protocol) => onChange({ latency_protocol })}
        />
        {preferences.latency_protocol === "http_head" && (
          <SettingsInputRow
            label={m.settings.testUrl}
            value={testUrlDraft}
            inputMode="url"
            onChange={setTestUrlDraft}
            onCommit={saveTestUrl}
          />
        )}
        <SettingsInputRow
          label={m.settings.timeoutMs}
          value={timeoutDraft}
          inputMode="numeric"
          onChange={setTimeoutDraft}
          onCommit={saveTimeout}
        />
        <SettingsChoiceRow
          label={m.settings.displayFormat}
          value={preferences.latency_display_format}
          options={[
            { value: "ms", label: m.settings.latencyMs },
            { value: "badge", label: m.settings.latencyBadge },
          ]}
          onChange={(latency_display_format) => onChange({ latency_display_format })}
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
  checking,
  onChange,
  onCheck,
  onDownload,
}: {
  preferences: AppPreferences;
  updateInfo: AppUpdateInfo | null;
  checking: boolean;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
  onCheck: () => Promise<void>;
  onDownload: (info: AppUpdateInfo) => Promise<void>;
}) {
  const m = useMessages();
  const versionValue = updateInfo
    ? `${updateInfo.current_version} -> ${updateInfo.latest_version}`
    : APP_VERSION;
  const assetValue = updateInfo?.asset
    ? `${updateInfo.asset.name}${updateInfo.asset.size ? ` · ${formatBytes(updateInfo.asset.size)}` : ""}`
    : updateInfo?.available
      ? m.settings.updateNoAsset
      : "—";

  return (
    <Section title={m.settings.updates}>
      <SettingsCard>
        <div className="settings-row settings-row-block">
          <div>
            <div className="settings-row-title">{m.settings.appUpdates}</div>
            <div className="settings-row-description">{m.settings.appUpdatesDescription}</div>
          </div>
          <button
            disabled={checking}
            onClick={() => void onCheck()}
            className="settings-action"
          >
            {checking ? m.settings.checkingUpdates : m.settings.checkForUpdates}
          </button>
        </div>
        <ToggleRow
          label={m.settings.checkUpdatesOnLaunch}
          description={m.settings.checkUpdatesOnLaunchDescription}
          enabled={preferences.check_updates_on_launch}
          onToggle={(check_updates_on_launch) => onChange({ check_updates_on_launch })}
        />
        <ValueRow label={m.settings.version} value={versionValue} />
        <ValueRow label={m.settings.systemTarget} value={updateInfo?.target ?? "—"} />
        <ValueRow label={m.settings.latestVersion} value={updateInfo?.latest_version ?? "—"} />
        <ValueRow label={m.settings.releaseAsset} value={assetValue} mono={Boolean(updateInfo?.asset)} />
        {updateInfo && (
          <div className="settings-row justify-end gap-3">
            <a className="settings-action" href={updateInfo.release_url} target="_blank" rel="noreferrer">
              {m.settings.releasePage}
            </a>
            <button
              disabled={!updateInfo.download_url && !updateInfo.release_url}
              onClick={() => void onDownload(updateInfo)}
              className="settings-action settings-action-primary"
            >
              {m.settings.downloadUpdate}
            </button>
          </div>
        )}
      </SettingsCard>
    </Section>
  );
}

function AboutSection({
  device,
  copied,
  onCopyHwid,
}: {
  device: DeviceInfo | null;
  copied: boolean;
  onCopyHwid: () => void;
}) {
  const m = useMessages();
  return (
    <Section title={m.settings.about}>
      <SettingsCard>
        <ValueRow label={m.settings.version} value={APP_VERSION} />
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
          <LinkRow icon={<GlobeIcon />} label={m.common.site} value="nimbo.local" href="#" />
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
  return <div className={["settings-card", className].filter(Boolean).join(" ")}>{children}</div>;
}

function ToggleRow({
  label,
  description,
  enabled = false,
  onToggle,
}: {
  label: string;
  description?: string;
  enabled?: boolean;
  onToggle?: (enabled: boolean) => void;
}) {
  return (
    <div className="settings-row">
      <div>
        <div className="settings-row-title">{label}</div>
        {description && <div className="settings-row-description">{description}</div>}
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
}: {
  label: string;
  value: string;
  description?: string;
  muted?: boolean;
  mono?: boolean;
  copyValue?: string;
}) {
  return (
    <div className={["settings-row", muted ? "settings-row-muted" : ""].join(" ")}>
      <div className="min-w-0">
        <div className="settings-row-title">{label}</div>
        {description && <div className="settings-row-description">{description}</div>}
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
}: {
  label: string;
  description?: string;
  value: T;
  options: Array<{ value: T; label: string; icon?: ReactNode }>;
  onChange: (value: T) => Promise<void>;
}) {
  return (
    <div className="settings-row">
      <div className="min-w-0">
        <div className="settings-row-title">{label}</div>
        {description && <div className="settings-row-description">{description}</div>}
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
}: {
  label: string;
  description?: string;
  value: number;
  min: number;
  max: number;
  suffix?: string;
  onCommit: (value: number) => Promise<void>;
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
      <div className="min-w-0">
        <div className="settings-row-title">{label}</div>
        {description && <div className="settings-row-description">{description}</div>}
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
}: {
  label: string;
  value: string;
  inputMode: "text" | "url" | "numeric";
  type?: "text" | "password";
  placeholder?: string;
  compact?: boolean;
  copyValue?: string;
  onChange: (value: string) => void;
  onCommit: () => void;
}) {
  return (
    <div className="settings-row">
      <div className="min-w-0">
        <div className="settings-row-title">{label}</div>
      </div>
      <div
        className={[
          "settings-value-actions settings-input-actions",
          compact ? "settings-input-actions-compact" : "",
          inputMode === "numeric" ? "settings-input-actions-numeric" : "",
        ].join(" ")}
      >
        <input
          type={type}
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
            inputMode === "numeric" ? "settings-field-input-numeric" : "",
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

function ModeOption({
  title,
  subtitle,
  selected,
  onClick,
}: {
  title: string;
  subtitle: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={[
        "settings-mode-option",
        selected ? "settings-mode-option-active" : "",
      ].join(" ")}
    >
      <span className={["settings-radio", selected ? "settings-radio-on" : ""].join(" ")} />
      <span className="min-w-0">
        <span className="block text-sm font-semibold text-white">{title}</span>
        <span className="block truncate text-xs text-[var(--color-text-faint)]">{subtitle}</span>
      </span>
    </button>
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
function ClassicButtonIcon() {
  return <Icon><circle cx="12" cy="12" r="8" /><circle cx="12" cy="12" r="3.2" fill="currentColor" stroke="none" /></Icon>;
}
function CompactButtonIcon() {
  return <Icon><rect x="3" y="8" width="18" height="8" rx="4" /><circle cx="16" cy="12" r="2.4" fill="currentColor" stroke="none" /></Icon>;
}

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" className="h-full w-full" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      {children}
    </svg>
  );
}
