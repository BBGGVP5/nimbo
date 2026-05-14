import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { api, formatBytes, type AppPreferences, type AppUpdateInfo, type ConnectionMode, type DeviceInfo } from "../lib/api";
import { fillTemplate, useMessages } from "../lib/i18n";
import { notifyError, notifyInfo } from "../lib/notify";
import { useAppStore } from "../store";

const HAPP_UA = "Happ/2.0.0";
const APP_VERSION = "0.1.0";

type UaMode = "default" | "happ" | "custom";
type SettingsSection =
  | "general"
  | "appearance"
  | "connection"
  | "tunnel"
  | "dns"
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
  { id: "dns", labelKey: "dns", icon: <GlobeIcon /> },
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
  return "custom";
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
  const [confirmResetOpen, setConfirmResetOpen] = useState(false);
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

  const effectiveUa =
    mode === "default"
      ? (device?.user_agent ?? "Nimbo/0.1.0/Windows")
      : mode === "happ"
        ? HAPP_UA
        : customUa.trim() || "Nimbo/0.1.0/Windows";

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

  const onResetHwid = async () => {
    setConfirmResetOpen(true);
  };

  const confirmResetHwid = async () => {
    try {
      const next = await api.resetDeviceId();
      setDevice(next);
      notifyInfo(m.settings.hwidReset);
      setConfirmResetOpen(false);
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

  return (
    <div className="settings-page h-full overflow-auto">
      <h1 className="page-title">{m.settings.title}</h1>

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
              onMode={updateConnectionMode}
            />
          )}
          {section === "tunnel" && <TunnelSection />}
          {section === "dns" && <DnsSection />}
          {section === "lan" && <LanSection />}
          {section === "subscriptions" && (
            <SubscriptionsSection
              mode={mode}
              customUa={customUa}
              override={override}
              effectiveUa={effectiveUa}
              savingUa={savingUa}
              deviceUa={device?.user_agent ?? "Nimbo/0.1.0/Windows"}
              refreshingSubscriptions={refreshingSubscriptions}
              onMode={applyUa}
              onCustomUa={setCustomUa}
              onRefreshSubscriptions={refreshRemoteSubscriptions}
            />
          )}
          {section === "servers" && <ServersSection />}
          {section === "latency" && <LatencySection />}
          {section === "backup" && <BackupSection />}
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
              onResetHwid={onResetHwid}
            />
          )}
        </main>
      </div>
      {confirmResetOpen && (
        <SettingsConfirmDialog
          title={m.settings.resetHwidTitle}
          description={m.settings.resetHwidDescription}
          confirmLabel={m.settings.reset}
          onConfirm={confirmResetHwid}
          onClose={() => setConfirmResetOpen(false)}
        />
      )}
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
  onMode,
}: {
  mode: ConnectionMode;
  socksPort: number;
  httpPort: number;
  onMode: (mode: ConnectionMode) => Promise<void>;
}) {
  const m = useMessages();
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
        <div className="settings-choice-grid">
          <ModeOption
            title="System Proxy"
            subtitle={`HTTP 127.0.0.1:${httpPort} · SOCKS 127.0.0.1:${socksPort}`}
            selected={mode === "system_proxy"}
            onClick={() => onMode("system_proxy")}
          />
          <ModeOption
            title="TUN"
            subtitle={m.settings.tunSubtitle}
            selected={mode === "tun"}
            onClick={() => onMode("tun")}
          />
        </div>
        <ToggleRow label="Kill switch" />
      </SettingsCard>
      <SettingsCard>
        <ValueRow label="HTTP proxy" value={`127.0.0.1:${httpPort}`} mono />
        <ValueRow label="SOCKS5 proxy" value={`127.0.0.1:${socksPort}`} mono />
        <ValueRow label={m.settings.socksUsername} value="-" description={m.settings.noAuthProxy} />
        <ValueRow label={m.settings.socksPassword} value="-" />
        <ToggleRow label={m.settings.requireSocksAuth} description={m.settings.requireSocksAuthDescription} />
        <ToggleRow label={m.settings.blockSocksUdp} description={m.settings.blockSocksUdpDescription} />
      </SettingsCard>
    </Section>
  );
}

function TunnelSection() {
  const m = useMessages();
  return (
    <Section title={m.settings.tunnel}>
      <SettingsCard>
        <ToggleRow label={m.settings.sniffing} description={m.settings.sniffingDescription} enabled />
        <ToggleRow label="Mux" description={m.settings.muxDescription} />
        <ValueRow label="Mux concurrency" value="8" muted />
        <ValueRow label="xUDP concurrency" value="-1" description="-1 — выкл, 0+ — включён" muted />
        <ValueRow label="xUDP UDP/443" value="Reject" muted />
        <ToggleRow label={m.settings.tlsFragmentation} description={m.settings.tlsFragmentationDescription} />
      </SettingsCard>
    </Section>
  );
}

function DnsSection() {
  const m = useMessages();
  return (
    <Section title="DNS">
      <SettingsCard>
        <ValueRow label={m.settings.remoteDns} value="https://1.1.1.1/dns-query" description={m.settings.remoteDnsDescription} />
        <ValueRow label={m.settings.localDns} value="https://8.8.8.8/dns-query" description={m.settings.localDnsDescription} />
        <ValueRow label={m.settings.tunDns} value="1.1.1.1,8.8.8.8" description={m.settings.tunDnsDescription} />
        <ValueRow label={m.settings.queryStrategy} value="UseIP" />
        <ToggleRow label="FakeDNS" description={m.settings.fakeDnsDescription} />
        <ToggleRow label={m.settings.globalProxy} description={m.settings.globalProxyDescription} />
        <ToggleRow label={m.settings.bypassPrivateIp} description={m.settings.bypassPrivateIpDescription} enabled />
      </SettingsCard>
    </Section>
  );
}

function LanSection() {
  const m = useMessages();
  return (
    <Section title={m.settings.lan}>
      <SettingsCard>
        <ToggleRow label={m.settings.allowLan} description={m.settings.allowLanDescription} enabled />
        <ToggleRow label={m.settings.allowTethering} description={m.settings.allowTetheringDescription} />
        <ToggleRow label={m.settings.lanProxy} description={m.settings.lanProxyDescription} />
      </SettingsCard>
      <SettingsCard>
        <ValueRow label={m.settings.tcpIdleTimeout} value="60" />
        <ValueRow label={m.settings.maxTcp} value="256" />
        <ValueRow label={m.settings.maxUdp} value="128" />
        <ValueRow label={m.settings.preferredIpFamily} value={m.profiles.auto} />
      </SettingsCard>
    </Section>
  );
}

function SubscriptionsSection({
  mode,
  customUa,
  override,
  effectiveUa,
  savingUa,
  deviceUa,
  refreshingSubscriptions,
  onMode,
  onCustomUa,
  onRefreshSubscriptions,
}: {
  mode: UaMode;
  customUa: string;
  override: string | null;
  effectiveUa: string;
  savingUa: boolean;
  deviceUa: string;
  refreshingSubscriptions: boolean;
  onMode: (mode: UaMode, custom?: string) => Promise<void>;
  onCustomUa: (value: string) => void;
  onRefreshSubscriptions: () => Promise<void>;
}) {
  const m = useMessages();
  return (
    <Section title={m.settings.subscriptions}>
      <SettingsCard>
        <ToggleRow label={m.settings.autoUpdateSubscriptions} enabled />
        <ValueRow label={m.settings.updateInterval} value="6 h" />
        <ToggleRow label={m.settings.notifyExpiration} enabled />
        <ValueRow label={m.settings.daysLeftThreshold} value="3 d" />
        <ToggleRow label={m.settings.notifySubscriptionUpdate} enabled />
        <ToggleRow label={m.settings.updateOnLaunch} />
        <ToggleRow label={m.settings.pingAfterUpdate} />
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
        <UaOption selected={mode === "custom"} disabled={savingUa} title={m.settings.customUserAgent} subtitle={m.profiles.manualInput} onClick={() => onMode("custom", customUa)} />
        {mode === "custom" && (
          <div className="settings-row">
            <input
              value={customUa}
              onChange={(e) => onCustomUa(e.target.value)}
              placeholder="MyClient/1.0/Windows"
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

function ServersSection() {
  const m = useMessages();
  return (
    <Section title={m.settings.servers}>
      <SettingsCard>
        <ValueRow label={m.settings.serverSorting} value={m.profiles.provider} />
        <ValueRow label={m.settings.connectButton} value={m.profiles.classic} />
        <ValueRow label={m.settings.uiScale} value="100%" />
        <ToggleRow label='Кнопка "proxy-only"' />
      </SettingsCard>
    </Section>
  );
}

function LatencySection() {
  const m = useMessages();
  return (
    <Section title={m.settings.latency}>
      <SettingsCard>
        <ValueRow label={m.settings.protocol} value="TCP connect" />
        <ValueRow label={m.settings.testUrl} value="https://www.gstatic.com/generate_204" muted />
        <ValueRow label={m.settings.timeoutMs} value="5000" />
        <ValueRow label={m.settings.displayFormat} value="ms" />
      </SettingsCard>
    </Section>
  );
}

function BackupSection() {
  const m = useMessages();
  return (
    <Section title={m.settings.backup}>
      <SettingsCard>
        <div className="settings-row settings-row-block">
          <div>
            <div className="settings-row-title">{m.settings.export}</div>
            <div className="settings-row-description">{m.settings.exportDescription}</div>
          </div>
        </div>
        <div className="settings-row settings-row-muted">
          {m.settings.backupHint}
        </div>
        <ValueRow label={m.settings.passwordOptional} value={m.settings.passwordEmpty} muted />
        <div className="settings-row justify-end gap-3">
          <button className="settings-action">{m.settings.exportAction}</button>
          <button className="settings-action">{m.settings.importAction}</button>
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
  onResetHwid,
}: {
  device: DeviceInfo | null;
  copied: boolean;
  onCopyHwid: () => void;
  onResetHwid: () => void;
}) {
  const m = useMessages();
  return (
    <Section title={m.settings.about}>
      <SettingsCard>
        <ValueRow label={m.settings.version} value={APP_VERSION} />
        <ValueRow label={m.settings.engine} value="Rust + React + Tauri" />
        <ValueRow label={m.settings.developer} value="Danila" />
        <div className="settings-row">
          <div className="settings-row-title">HWID</div>
          <div className="flex min-w-0 items-center gap-2">
            <span className="settings-code truncate">{device?.hwid ?? "—"}</span>
            <button onClick={onCopyHwid} disabled={!device} className="settings-icon-button" title={m.settings.copyHwid}>
              {copied ? <CheckIcon /> : <ClipboardIcon />}
            </button>
            <button onClick={onResetHwid} disabled={!device} className="settings-icon-button" title={m.settings.resetHwid}>
              <RefreshIcon />
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

function SettingsConfirmDialog({
  title,
  description,
  confirmLabel,
  onConfirm,
  onClose,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const m = useMessages();
  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center p-5"
      style={{ background: "rgba(0,0,0,0.58)", backdropFilter: "blur(9px)" }}
      onClick={onClose}
    >
      <div className="panel w-full max-w-md p-5" onClick={(e) => e.stopPropagation()}>
        <div className="mb-2 text-xl font-bold text-white">{title}</div>
        <div className="mb-5 text-sm leading-relaxed text-[var(--color-text-dim)]">{description}</div>
        <div className="grid grid-cols-2 gap-3">
          <button
            onClick={onClose}
            className="interactive rounded-xl border border-[var(--color-border)] px-4 py-3 text-sm font-semibold text-[var(--color-text-dim)]"
          >
            {m.common.cancel}
          </button>
          <button onClick={onConfirm} className="primary-button interactive rounded-xl px-4 py-3 text-sm font-bold">
            {confirmLabel}
          </button>
        </div>
      </div>
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

function SettingsCard({ children }: { children: ReactNode }) {
  return <div className="settings-card">{children}</div>;
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
}: {
  label: string;
  value: string;
  description?: string;
  muted?: boolean;
  mono?: boolean;
}) {
  return (
    <div className={["settings-row", muted ? "settings-row-muted" : ""].join(" ")}>
      <div className="min-w-0">
        <div className="settings-row-title">{label}</div>
        {description && <div className="settings-row-description">{description}</div>}
      </div>
      <span className={["settings-value", mono ? "font-mono" : ""].join(" ")}>{value}</span>
    </div>
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

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" className="h-full w-full" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      {children}
    </svg>
  );
}
