import { useEffect, useRef, useState } from "react";
import type { CSSProperties, ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  api,
  APP_VERSION,
  DEFAULT_ACCENT_COLOR,
  DEFAULT_ACCENT_PALETTE,
  defaultAppPreferences,
  formatBytes,
  type AppPreferences,
  type AppUpdateInfo,
  type ConnectButtonStyle,
  type ConnectionMode,
  type DeviceInfo,
  type ProxySettingsPatch,
  type Subscription,
  type SubscriptionTheme,
  type ThemeMode,
} from "../lib/api";
import { fillTemplate, useMessages, type Messages } from "../lib/i18n";
import { notifyError, notifyInfo } from "../lib/notify";
import { useCachedSubscriptionLogo } from "../lib/subscriptionLogo";
import { cachedSubscriptionTheme } from "../lib/subscriptionTheme";
import {
  BACKGROUND_PRESETS,
  accentGradientCss,
  clearBackgroundBlob,
  removePalettePreset,
  saveBackgroundBlob,
  savePalettePreset,
  setAppearance,
  useAppearance,
  type AppearanceState,
  type PalettePreset,
} from "../lib/appearance";
import { useAppStore } from "../store";
import { showAppUpdateDialog } from "../App";

const NIMBO_UA_FALLBACK = `Nimbo/${APP_VERSION}`;
const HAPP_UA = "Happ/2.0.0";
const INCY_UA = "Incy/2.1.0";
const SOCKS_USERNAME_FALLBACK = "nimbo";
const SOCKS_PASSWORD_FALLBACK = "nmb-preview-password";
const VISUAL_PREFERENCE_SAVE_DELAY = 260;

type VisualPreferenceKey =
  | "interface_panel_brightness"
  | "interface_transparency"
  | "interface_blur"
  | "interface_rounding";

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
        </main>
      </div>
    </div>
  );
}

const accentPresets: Array<{
  color: string;
  labelKey:
    | "accentViolet"
    | "accentBlue"
    | "accentGreen"
    | "accentRose"
    | "accentAmber"
    | "accentCyan";
}> = [
  { color: DEFAULT_ACCENT_COLOR, labelKey: "accentBlue" },
  { color: "#7c5dfa", labelKey: "accentViolet" },
  { color: "#21a67a", labelKey: "accentGreen" },
  { color: "#e24d70", labelKey: "accentRose" },
  { color: "#f5a623", labelKey: "accentAmber" },
  { color: "#00a8c8", labelKey: "accentCyan" },
];

const extraAccentPresets: Array<{ color: string; label: string }> = [
  { color: "#ef4444", label: "Red" },
  { color: "#f97316", label: "Orange" },
  { color: "#eab308", label: "Gold" },
  { color: "#84cc16", label: "Lime" },
  { color: "#14b8a6", label: "Teal" },
  { color: "#6366f1", label: "Indigo" },
  { color: "#ec4899", label: "Pink" },
  { color: "#64748b", label: "Slate" },
];

const gradientAccentPresets: Array<{ label: string; colors: string[] }> = [
  { label: "Aurora", colors: ["#7c5dfa", "#4f8cff"] },
  { label: "Candy", colors: ["#e24d70", "#7c5dfa"] },
  { label: "Sunset", colors: ["#f5a623", "#e24d70"] },
  { label: "Lagoon", colors: ["#21a67a", "#00a8c8"] },
  { label: "Spectrum", colors: ["#7c5dfa", "#e24d70", "#f5a623"] },
  { label: "Reef", colors: ["#00a8c8", "#21a67a", "#4f8cff"] },
];

function usePersistentToggle(key: string, defaultValue: boolean) {
  const [value, setValue] = useState<boolean>(() => {
    if (typeof window === "undefined") return defaultValue;
    try {
      const raw = window.localStorage.getItem(key);
      return raw == null ? defaultValue : raw === "1";
    } catch {
      return defaultValue;
    }
  });
  const toggle = () =>
    setValue((current) => {
      const next = !current;
      try {
        window.localStorage.setItem(key, next ? "1" : "0");
      } catch {
        /* ignore */
      }
      return next;
    });
  return [value, toggle] as const;
}

function readSystemAccentColor(): string {
  if (typeof document === "undefined") return DEFAULT_ACCENT_COLOR;
  try {
    const sample = document.createElement("span");
    sample.style.color = "Highlight";
    sample.style.position = "fixed";
    sample.style.visibility = "hidden";
    document.body.appendChild(sample);
    const value = getComputedStyle(sample).color;
    sample.remove();
    const match = value.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
    if (!match) return DEFAULT_ACCENT_COLOR;
    const toHex = (n: number) => Math.max(0, Math.min(255, n)).toString(16).padStart(2, "0");
    return `#${toHex(Number(match[1]))}${toHex(Number(match[2]))}${toHex(Number(match[3]))}`;
  } catch {
    return DEFAULT_ACCENT_COLOR;
  }
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

function AppearanceSection({
  preferences,
  previewSubscription,
  onChange,
}: {
  preferences: AppPreferences;
  previewSubscription: Subscription | null;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
}) {
  const m = useMessages();
  const providerThemePreview = cachedSubscriptionTheme(previewSubscription);
  const providerLogoPreview = useCachedSubscriptionLogo(previewSubscription, true);
  const [systemAccent, setSystemAccent] = useState(DEFAULT_ACCENT_COLOR);
  const appearance = useAppearance();
  const [interfaceOpen, toggleInterface] = usePersistentToggle("nimbo.collapse.interface", true);
  const [detailsOpen, toggleDetails] = usePersistentToggle("nimbo.collapse.details", false);
  const [themeOpen, toggleTheme] = usePersistentToggle("nimbo.collapse.theme", false);
  const [accentOpen, toggleAccent] = usePersistentToggle("nimbo.collapse.accent", false);
  const [backgroundOpen, toggleBackground] = usePersistentToggle("nimbo.collapse.background", true);
  const [connectionStyleOpen, toggleConnectionStyle] = usePersistentToggle("nimbo.collapse.connectionStyle", true);
  const [languageOpen, toggleLanguage] = usePersistentToggle("nimbo.collapse.language", true);
  const [providerThemeOpen, toggleProviderTheme] = usePersistentToggle("nimbo.collapse.providerThemeAndLogo", true);

  const samePalette = (a: string[], b: string[]) =>
    a.join(",").toLowerCase() === b.join(",").toLowerCase();
  const matchedGradient = gradientAccentPresets.find((g) => samePalette(g.colors, appearance.palette));

  const [isCustomActive, setIsCustomActive] = useState(() => preferences.accent_mode === "custom" && !matchedGradient);

  const livePalette = (colors: string[]) => {
    setIsCustomActive(true);
    setAppearance({ palette: colors.length ? colors.slice(0, 3) : [...DEFAULT_ACCENT_PALETTE] });
  };
  const commitPalette = (colors: string[], fromPreset = false) => {
    const next = colors.length ? colors.slice(0, 3) : [...DEFAULT_ACCENT_PALETTE];
    setAppearance({ palette: next });
    void onChange({ accent_mode: "custom", accent_color: next[0] });
    if (!fromPreset) {
      setIsCustomActive(true);
    }
  };
  const [visualDraft, setVisualDraft] = useState({
    interface_panel_brightness: preferences.interface_panel_brightness,
    interface_transparency: preferences.interface_transparency,
    interface_blur: preferences.interface_blur,
    interface_rounding: preferences.interface_rounding,
  });
  const visualTimers = useRef<Partial<Record<VisualPreferenceKey, ReturnType<typeof setTimeout>>>>({});

  useEffect(() => {
    setSystemAccent(readSystemAccentColor());
  }, []);

  useEffect(() => {
    setVisualDraft({
      interface_panel_brightness: preferences.interface_panel_brightness,
      interface_transparency: preferences.interface_transparency,
      interface_blur: preferences.interface_blur,
      interface_rounding: preferences.interface_rounding,
    });
  }, [
    preferences.interface_panel_brightness,
    preferences.interface_transparency,
    preferences.interface_blur,
    preferences.interface_rounding,
  ]);

  useEffect(() => {
    return () => {
      Object.values(visualTimers.current).forEach((timer) => {
        if (timer) clearTimeout(timer);
      });
    };
  }, []);

  const saveVisualPreference = (key: VisualPreferenceKey, value: number, immediate = false) => {
    setVisualDraft((current) => ({ ...current, [key]: value }));
    const timer = visualTimers.current[key];
    if (timer) clearTimeout(timer);

    const commit = () => {
      if (preferences[key] === value) return;
      void onChange({ [key]: value } as Partial<AppPreferences>);
    };

    if (immediate) {
      commit();
      return;
    }

    visualTimers.current[key] = setTimeout(commit, VISUAL_PREFERENCE_SAVE_DELAY);
  };

  const hasGlobalChanges =
    visualDraft.interface_panel_brightness !== defaultAppPreferences.interface_panel_brightness ||
    visualDraft.interface_transparency !== defaultAppPreferences.interface_transparency ||
    visualDraft.interface_blur !== defaultAppPreferences.interface_blur ||
    visualDraft.interface_rounding !== defaultAppPreferences.interface_rounding;

  const resetAllVisuals = () => {
    // 1. Update React visual draft immediately so UI reflects reset instantly
    setVisualDraft({
      interface_panel_brightness: defaultAppPreferences.interface_panel_brightness,
      interface_transparency: defaultAppPreferences.interface_transparency,
      interface_blur: defaultAppPreferences.interface_blur,
      interface_rounding: defaultAppPreferences.interface_rounding,
    });

    // 2. Clear all scheduled saving timers
    Object.values(visualTimers.current).forEach((timer) => {
      if (timer) clearTimeout(timer);
    });

    // 3. Perform a single unified API save call
    void onChange({
      interface_panel_brightness: defaultAppPreferences.interface_panel_brightness,
      interface_transparency: defaultAppPreferences.interface_transparency,
      interface_blur: defaultAppPreferences.interface_blur,
      interface_rounding: defaultAppPreferences.interface_rounding,
    });
  };

  const accentPresetActive = (color: string) =>
    preferences.accent_mode === "preset" && preferences.accent_color.toLowerCase() === color.toLowerCase();

  return (
    <Section title={m.settings.appearance}>
      <SettingsCard>
        <CollapsibleSection
          title={m.settings.interfaceStyle}
          description={m.settings.interfaceStyleDescription}
          open={interfaceOpen}
          onToggle={toggleInterface}
        >
          <div className="settings-interface-grid" role="radiogroup" aria-label={m.settings.interfaceStyle}>
            <InterfaceStyleOption
              styleId="nimbo"
              title={m.settings.nimboStyle}
              subtitle={m.settings.nimboStyleSubtitle}
              selected={preferences.ui_style === "nimbo"}
              onClick={() => onChange({ ui_style: "nimbo" })}
            />
            <InterfaceStyleOption
              styleId="material_you"
              title={m.settings.materialYouStyle}
              subtitle={m.settings.materialYouStyleSubtitle}
              selected={preferences.ui_style === "material_you"}
              onClick={() => onChange({ ui_style: "material_you" })}
            />
          </div>
        </CollapsibleSection>

        <CollapsibleSection
          title={m.settings.styleDetails}
          description={m.settings.styleDetailsDescription}
          open={detailsOpen}
          onToggle={toggleDetails}
          action={
            hasGlobalChanges ? (
              <button onClick={resetAllVisuals} className="settings-reset-all-btn" title={m.settings.resetAll}>
                <RotateCcwIcon />
                <span>{m.settings.resetAll}</span>
              </button>
            ) : undefined
          }
        >
          <div className="appearance-slider-stack">
            <VisualSliderRow
              label={m.settings.panelBrightness}
              description={m.settings.panelBrightnessDescription}
              value={visualDraft.interface_panel_brightness}
              min={60}
              max={140}
              step={5}
              formatValue={(value) => `${value}%`}
              onChange={(value) => saveVisualPreference("interface_panel_brightness", value)}
              onCommit={(value) => saveVisualPreference("interface_panel_brightness", value, true)}
              defaultValue={defaultAppPreferences.interface_panel_brightness}
              onReset={() => saveVisualPreference("interface_panel_brightness", defaultAppPreferences.interface_panel_brightness, true)}
            />
            <VisualSliderRow
              label={m.settings.elementTransparency}
              description={m.settings.elementTransparencyDescription}
              value={visualDraft.interface_transparency}
              min={0}
              max={80}
              step={5}
              formatValue={(value) => `${value}%`}
              onChange={(value) => saveVisualPreference("interface_transparency", value)}
              onCommit={(value) => saveVisualPreference("interface_transparency", value, true)}
              defaultValue={defaultAppPreferences.interface_transparency}
              onReset={() => saveVisualPreference("interface_transparency", defaultAppPreferences.interface_transparency, true)}
            />
            <VisualSliderRow
              label={m.settings.blurRadius}
              description={m.settings.blurRadiusDescription}
              value={visualDraft.interface_blur}
              min={0}
              max={48}
              step={1}
              formatValue={(value) => `${value} px`}
              onChange={(value) => saveVisualPreference("interface_blur", value)}
              onCommit={(value) => saveVisualPreference("interface_blur", value, true)}
              defaultValue={defaultAppPreferences.interface_blur}
              onReset={() => saveVisualPreference("interface_blur", defaultAppPreferences.interface_blur, true)}
            />
            <VisualSliderRow
              label={m.settings.elementRounding}
              description={m.settings.elementRoundingDescription}
              value={visualDraft.interface_rounding}
              min={50}
              max={180}
              step={5}
              formatValue={(value) => `${(value / 100).toFixed(2)}x`}
              onChange={(value) => saveVisualPreference("interface_rounding", value)}
              onCommit={(value) => saveVisualPreference("interface_rounding", value, true)}
              defaultValue={defaultAppPreferences.interface_rounding}
              onReset={() => saveVisualPreference("interface_rounding", defaultAppPreferences.interface_rounding, true)}
            />
          </div>
        </CollapsibleSection>

        <CollapsibleSection
          title={m.settings.theme}
          description={m.settings.themeDescription}
          open={themeOpen}
          onToggle={toggleTheme}
        >
          <div className="settings-theme-grid settings-theme-grid-4" role="radiogroup" aria-label={m.settings.theme}>
            <ThemePreviewOption
              title={m.settings.lightTheme}
              value="light"
              selected={preferences.theme_mode === "light"}
              onClick={() => onChange({ theme_mode: "light" })}
            />
            <ThemePreviewOption
              title={m.settings.darkTheme}
              value="dark"
              selected={preferences.theme_mode === "dark"}
              onClick={() => onChange({ theme_mode: "dark" })}
            />
            <ThemePreviewOption
              title={m.settings.blackTheme}
              value="black"
              selected={preferences.theme_mode === "black"}
              onClick={() => onChange({ theme_mode: "black" })}
            />
            <ThemePreviewOption
              title={m.settings.systemTheme}
              value="system"
              selected={preferences.theme_mode === "system"}
              onClick={() => onChange({ theme_mode: "system" })}
            />
          </div>
        </CollapsibleSection>

        <CollapsibleSection
          title={m.settings.accentColor}
          description={m.settings.accentDescription}
          open={accentOpen}
          onToggle={toggleAccent}
        >
          <div className="settings-accent-grid" role="radiogroup" aria-label={m.settings.accentColor}>
            <AccentPreviewOption
              title={m.settings.systemAccent}
              color={systemAccent}
              selected={preferences.accent_mode === "system"}
              onClick={() => {
                setIsCustomActive(false);
                onChange({ accent_mode: "system" });
              }}
            />
            {accentPresets.map(({ color, labelKey }) => (
              <AccentPreviewOption
                key={color}
                title={m.settings[labelKey]}
                color={color}
                selected={accentPresetActive(color)}
                onClick={() => {
                  setIsCustomActive(false);
                  onChange({ accent_mode: "preset", accent_color: color });
                }}
              />
            ))}
            {extraAccentPresets.map(({ color, label }) => (
              <AccentPreviewOption
                key={color}
                title={label}
                color={color}
                selected={accentPresetActive(color)}
                onClick={() => {
                  setIsCustomActive(false);
                  onChange({ accent_mode: "preset", accent_color: color });
                }}
              />
            ))}
            {gradientAccentPresets.map((gradient) => (
              <GradientAccentOption
                key={gradient.label}
                label={gradient.label}
                colors={gradient.colors}
                selected={preferences.accent_mode === "custom" && !isCustomActive && samePalette(gradient.colors, appearance.palette)}
                onClick={() => {
                  setIsCustomActive(false);
                  commitPalette(gradient.colors, true);
                }}
              />
            ))}
            <AccentCustomOption
              title={m.settings.customAccent}
              colors={appearance.palette}
              selected={preferences.accent_mode === "custom" && (isCustomActive || !matchedGradient)}
              onClick={() => {
                setIsCustomActive(true);
                commitPalette(appearance.palette);
              }}
            />
          </div>
          {preferences.accent_mode === "custom" && (
            <CustomPaletteEditor
              palette={appearance.palette}
              presets={appearance.presets}
              onLive={livePalette}
              onCommit={commitPalette}
            />
          )}
        </CollapsibleSection>

        <CollapsibleSection
          title={m.settings.backgroundTitle}
          description={m.settings.backgroundDescription}
          open={backgroundOpen}
          onToggle={toggleBackground}
        >
          <BackgroundChooser appearance={appearance} />
        </CollapsibleSection>

        <CollapsibleSection
          title={m.settings.connectionStyle}
          description={m.settings.connectionStyleDescription}
          open={connectionStyleOpen}
          onToggle={toggleConnectionStyle}
        >
          <div className="settings-connect-style-grid" role="radiogroup" aria-label={m.settings.connectionStyle}>
            <ConnectionStyleOption
              title={m.profiles.classic}
              description={m.settings.classicConnectStyleDescription}
              value="classic"
              selected={preferences.servers_connect_button === "classic"}
              icon={<ClassicButtonIcon />}
              onClick={() => onChange({ servers_connect_button: "classic" })}
            />
            <ConnectionStyleOption
              title={m.settings.compact}
              description={m.settings.compactConnectStyleDescription}
              value="compact"
              selected={preferences.servers_connect_button === "compact"}
              icon={<CompactButtonIcon />}
              onClick={() => onChange({ servers_connect_button: "compact" })}
            />
          </div>
        </CollapsibleSection>

        <CollapsibleSection
          title={m.settings.language}
          description={m.settings.languageDescription}
          open={languageOpen}
          onToggle={toggleLanguage}
        >
          <div className="settings-theme-grid settings-language-grid" role="radiogroup" aria-label={m.settings.language}>
            <LanguagePreviewOption
              flag="ru"
              title="Русский"
              sampleTitle="Подключено"
              sampleLine="Выберите сервер"
              sampleChip="Серверы"
              selected={preferences.language === "ru"}
              onClick={() => onChange({ language: "ru" })}
            />
            <LanguagePreviewOption
              flag="gb"
              title="English"
              sampleTitle="Connected"
              sampleLine="Choose a server"
              sampleChip="Servers"
              selected={preferences.language === "en"}
              onClick={() => onChange({ language: "en" })}
            />
            <LanguagePreviewOption
              icon={<GlobeIcon />}
              title={m.settings.systemLanguage}
              sampleTitle="RU · EN"
              sampleLine={m.settings.systemLanguageSubtitle}
              sampleChip="OS"
              selected={preferences.language === "system"}
              onClick={() => onChange({ language: "system" })}
            />
          </div>
        </CollapsibleSection>

        <CollapsibleSection
          title={m.settings.providerThemeAndLogo}
          description={m.settings.providerThemeAndLogoDescription}
          open={providerThemeOpen}
          onToggle={toggleProviderTheme}
        >
          <SubscriptionProviderPreview
            sub={previewSubscription}
            theme={providerThemePreview}
            logoSrc={providerLogoPreview}
            themeEnabled={preferences.provider_theme}
            logoEnabled={preferences.show_subscription_logo}
            labels={m}
          />
          <ProviderThemeRow
            label={m.settings.providerTheme}
            description={m.settings.providerThemeDescription}
            enabled={preferences.provider_theme}
            onToggle={(provider_theme) => onChange({ provider_theme })}
          />
          <ProviderThemeRow
            label={m.settings.showSubscriptionLogo}
            description={m.settings.showSubscriptionLogoDescription}
            enabled={preferences.show_subscription_logo}
            onToggle={(show_subscription_logo) => onChange({ show_subscription_logo })}
          />
        </CollapsibleSection>
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
      </SettingsCard>
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
          description="-1 — выкл, 0+ — включён"
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
          icon={<SignalIcon />}
        />
        {preferences.latency_protocol === "http_head" && (
          <SettingsInputRow
            label={m.settings.testUrl}
            value={testUrlDraft}
            inputMode="url"
            onChange={setTestUrlDraft}
            onCommit={saveTestUrl}
            icon={<GlobeIcon />}
          />
        )}
        <SettingsInputRow
          label={m.settings.timeoutMs}
          value={timeoutDraft}
          inputMode="numeric"
          onChange={setTimeoutDraft}
          onCommit={saveTimeout}
          icon={<SlidersIcon />}
        />
        <SettingsChoiceRow
          label={m.settings.displayFormat}
          value={preferences.latency_display_format}
          options={[
            { value: "ms", label: m.settings.latencyMs },
            { value: "badge", label: m.settings.latencyBadge },
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
  onChange,
  onCheck,
  onDownload,
}: {
  preferences: AppPreferences;
  updateInfo: AppUpdateInfo | null;
  appVersion: string;
  checking: boolean;
  onChange: (patch: Partial<AppPreferences>) => Promise<void>;
  onCheck: () => Promise<void>;
  onDownload: (info: AppUpdateInfo) => Promise<void>;
}) {
  const m = useMessages();
  const versionValue = updateInfo
    ? `${updateInfo.current_version} -> ${updateInfo.latest_version}`
    : appVersion;
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
          icon={<DownloadIcon />}
        />
        <ValueRow label={m.settings.version} value={versionValue} icon={<InfoIcon />} />
        <ValueRow label={m.settings.systemTarget} value={updateInfo?.target ?? "—"} icon={<InfoIcon />} />
        <ValueRow label={m.settings.latestVersion} value={updateInfo?.latest_version ?? "—"} icon={<InfoIcon />} />
        <ValueRow label={m.settings.releaseAsset} value={assetValue} mono={Boolean(updateInfo?.asset)} icon={<InfoIcon />} />
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

function VisualSliderRow({
  label,
  description,
  value,
  min,
  max,
  step,
  formatValue,
  onChange,
  onCommit,
  defaultValue,
  onReset,
}: {
  label: string;
  description?: string;
  value: number;
  min: number;
  max: number;
  step: number;
  formatValue: (value: number) => string;
  onChange: (value: number) => void;
  onCommit: (value: number) => void;
  defaultValue?: number;
  onReset?: () => void;
}) {
  const m = useMessages();
  const progress = max > min ? ((value - min) / (max - min)) * 100 : 0;
  const formattedValue = formatValue(value);
  const commitFromInput = (input: HTMLInputElement) => onCommit(Number(input.value));
  const hasChanges = defaultValue !== undefined && value !== defaultValue;

  return (
    <div className="appearance-slider-row">
      <div className="appearance-slider-copy">
        <div className="settings-row-title">{label}</div>
        {description && <div className="settings-row-description">{description}</div>}
      </div>
      <div
        className="appearance-slider-control"
        style={{ "--appearance-slider-progress": `${Math.min(100, Math.max(0, progress))}%` } as CSSProperties}
      >
        <input
          className="appearance-slider-input"
          type="range"
          min={min}
          max={max}
          step={step}
          value={value}
          aria-label={label}
          aria-valuetext={formattedValue}
          onChange={(event) => onChange(Number(event.currentTarget.value))}
          onBlur={(event) => commitFromInput(event.currentTarget)}
          onPointerUp={(event) => commitFromInput(event.currentTarget)}
          onKeyUp={(event) => {
            if (["ArrowLeft", "ArrowRight", "Home", "End", "PageUp", "PageDown"].includes(event.key)) {
              commitFromInput(event.currentTarget);
            }
          }}
        />
      </div>
      <div className="appearance-slider-value">
        <span>{formattedValue}</span>
        {hasChanges && onReset && (
          <button
            onClick={onReset}
            className="appearance-slider-reset"
            title={m.settings.resetValue}
          >
            <RotateCcwIcon />
          </button>
        )}
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
  inputMode: "text" | "url" | "numeric";
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

function ThemePreviewOption({
  title,
  value,
  selected,
  onClick,
}: {
  title: string;
  value: Extract<ThemeMode, "light" | "dark" | "black" | "system">;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      className={[
        "settings-theme-card",
        `settings-theme-card-${value}`,
        selected ? "settings-theme-card-active" : "",
      ].join(" ")}
    >
      <span className="settings-theme-preview" aria-hidden="true">
        <span className="settings-theme-preview-rail">
          <span />
          <span />
        </span>
        <span className="settings-theme-preview-canvas">
          <span className="settings-theme-preview-top" />
          <span className="settings-theme-preview-row">
            <span />
            <span />
          </span>
        </span>
      </span>
      <span className="settings-theme-card-label">{title}</span>
    </button>
  );
}

function AccentPreviewArt() {
  return (
    <span className="settings-theme-preview" aria-hidden="true">
      <span className="settings-theme-preview-rail">
        <span />
        <span />
      </span>
      <span className="settings-theme-preview-canvas">
        <span className="settings-theme-preview-top" />
        <span className="settings-theme-preview-row">
          <span />
          <span />
        </span>
      </span>
    </span>
  );
}

function AccentPreviewOption({
  title,
  color,
  selected,
  onClick,
}: {
  title: string;
  color: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      style={{ "--theme-preview-accent": color, "--accent-card-color": color } as CSSProperties}
      className={[
        "settings-theme-card settings-accent-card",
        selected ? "settings-accent-card-active" : "",
      ].join(" ")}
    >
      <AccentPreviewArt />
      <span className="settings-theme-card-label">{title}</span>
    </button>
  );
}

function AccentSplitPreview({ colors }: { colors: string[] }) {
  const accentColor = colors[0] ?? DEFAULT_ACCENT_COLOR;
  if (colors.length <= 1) {
    return (
      <span
        className="settings-theme-preview"
        style={{ "--theme-preview-accent": accentColor } as CSSProperties}
        aria-hidden="true"
      >
        <span className="settings-theme-preview-rail">
          <span />
          <span />
        </span>
        <span className="settings-theme-preview-canvas">
          <span className="settings-theme-preview-top" />
          <span className="settings-theme-preview-row">
            <span />
            <span />
          </span>
        </span>
      </span>
    );
  }
  return (
    <span className="settings-accent-split" aria-hidden="true">
      <span
        className="settings-accent-split-preview settings-theme-preview"
        style={{ "--theme-preview-accent": accentColor } as CSSProperties}
      >
        <span className="settings-theme-preview-rail">
          <span />
          <span />
        </span>
        <span className="settings-theme-preview-canvas">
          <span className="settings-theme-preview-top" />
          <span className="settings-theme-preview-row">
            <span />
            <span />
          </span>
        </span>
      </span>
      <span className="settings-accent-split-colors">
        {colors.map((color, index) => (
          <span key={index} style={{ background: color }} />
        ))}
      </span>
    </span>
  );
}

function GradientAccentOption({
  label,
  colors,
  selected,
  onClick,
}: {
  label: string;
  colors: string[];
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      style={{ "--accent-card-color": colors[0] } as CSSProperties}
      className={[
        "settings-theme-card settings-accent-card settings-accent-card-split",
        selected ? "settings-accent-card-active" : "",
      ].join(" ")}
    >
      <AccentSplitPreview colors={colors} />
      <span className="settings-theme-card-label">{label}</span>
    </button>
  );
}

function AccentCustomOption({
  title,
  colors,
  selected,
  onClick,
}: {
  title: string;
  colors: string[];
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      style={{ "--accent-card-color": colors[0] ?? DEFAULT_ACCENT_COLOR } as CSSProperties}
      className={[
        "settings-theme-card settings-accent-card settings-accent-card-split settings-accent-card-custom",
        selected ? "settings-accent-card-active" : "",
      ].join(" ")}
    >
      <AccentSplitPreview colors={colors.length ? colors : [...DEFAULT_ACCENT_PALETTE]} />
      <span className="settings-theme-card-label">{title}</span>
    </button>
  );
}

function CustomPaletteEditor({
  palette,
  presets,
  onLive,
  onCommit,
}: {
  palette: string[];
  presets: PalettePreset[];
  onLive: (colors: string[]) => void;
  onCommit: (colors: string[]) => void;
}) {
  const m = useMessages();
  const colors = palette.length ? palette : [...DEFAULT_ACCENT_PALETTE];

  const setColorAt = (index: number, value: string, commit: boolean) => {
    const next = colors.map((c, i) => (i === index ? value : c));
    (commit ? onCommit : onLive)(next);
  };
  const addColor = () => {
    if (colors.length >= 3) return;
    onCommit([...colors, colors[colors.length - 1] ?? "#4f8cff"]);
  };
  const removeColorAt = (index: number) => {
    if (colors.length <= 1) return;
    onCommit(colors.filter((_, i) => i !== index));
  };

  return (
    <div className="settings-palette-editor">
      <span className="settings-palette-bar" style={{ backgroundImage: accentGradientCss(colors) }} aria-hidden="true" />
      <div className="settings-palette-slots">
        {colors.map((color, index) => (
          <div key={index} className="settings-palette-slot">
            <label className="settings-palette-swatch" style={{ backgroundColor: color }}>
              <input
                type="color"
                value={color}
                onChange={(event) => setColorAt(index, event.target.value, false)}
                onBlur={(event) => setColorAt(index, event.target.value, true)}
                aria-label={`${m.settings.customPaletteTitle} ${index + 1}`}
              />
            </label>
            {colors.length > 1 && (
              <button
                type="button"
                className="settings-palette-remove"
                onClick={() => removeColorAt(index)}
                title={m.settings.removeColor}
                aria-label={m.settings.removeColor}
              >
                <XMarkIcon />
              </button>
            )}
          </div>
        ))}
        {colors.length < 3 && (
          <button
            type="button"
            className="settings-palette-add"
            onClick={addColor}
            title={m.settings.addColor}
            aria-label={m.settings.addColor}
          >
            <PlusSmIcon />
          </button>
        )}
      </div>
      <div className="settings-palette-actions">
        <span className="settings-palette-hint">{m.settings.customPaletteHint}</span>
        <button
          type="button"
          className="settings-action"
          onClick={() => {
            savePalettePreset(colors);
            notifyInfo(m.settings.presetSaved);
          }}
        >
          {m.settings.savePreset}
        </button>
      </div>
      {presets.length > 0 && (
        <div className="settings-palette-presets">
          <div className="settings-palette-presets-label">{m.settings.savedPresets}</div>
          <div className="settings-palette-presets-list">
            {presets.map((preset) => (
              <div key={preset.id} className="settings-palette-preset">
                <button
                  type="button"
                  className="settings-palette-preset-swatch"
                  style={{ backgroundImage: accentGradientCss(preset.colors) }}
                  onClick={() => onCommit(preset.colors)}
                  title={preset.colors.join(", ")}
                  aria-label={preset.colors.join(", ")}
                />
                <button
                  type="button"
                  className="settings-palette-preset-remove"
                  onClick={() => removePalettePreset(preset.id)}
                  title={m.settings.deletePreset}
                  aria-label={m.settings.deletePreset}
                >
                  <XMarkIcon />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function CollapsibleSection({
  title,
  description,
  open,
  onToggle,
  action,
  children,
}: {
  title: string;
  description: string;
  open: boolean;
  onToggle: () => void;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <>
      <div className="appearance-collapse-head">
        <button
          type="button"
          className="appearance-collapse-toggle"
          aria-expanded={open}
          onClick={onToggle}
        >
          <span className="appearance-collapse-titles">
            <span className="settings-row-title">{title}</span>
            <span className="settings-row-description">{description}</span>
          </span>
          <span className={["appearance-collapse-chevron", open ? "is-open" : ""].join(" ")} aria-hidden="true">
            <ChevronDownIcon />
          </span>
        </button>
        {action}
      </div>
      <div className={["appearance-collapse-body", open ? "is-open" : ""].join(" ")}>
        <div className="appearance-collapse-inner">{children}</div>
      </div>
    </>
  );
}

function InterfaceStyleOption({
  styleId,
  title,
  subtitle,
  selected,
  onClick,
}: {
  styleId: "nimbo" | "material_you";
  title: string;
  subtitle: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      className={[
        "settings-theme-card settings-interface-card",
        `settings-interface-card-${styleId}`,
        selected ? "settings-theme-card-active" : "",
      ].join(" ")}
    >
      <span className="settings-interface-preview" aria-hidden="true">
        <span className="settings-interface-preview-panel">
          <span className="settings-interface-preview-pill" />
          <span className="settings-interface-preview-line" />
          <span className="settings-interface-preview-line settings-interface-preview-line-sm" />
        </span>
      </span>
      <span className="settings-interface-card-copy">
        <span className="settings-theme-card-label">{title}</span>
        <span className="settings-interface-card-subtitle">{subtitle}</span>
      </span>
    </button>
  );
}

function BackgroundChooser({ appearance }: { appearance: AppearanceState }) {
  const m = useMessages();
  const fileRef = useRef<HTMLInputElement | null>(null);

  const onFile = async (file: File) => {
    const isVideo = file.type.startsWith("video");
    const isImage = file.type.startsWith("image");
    if (!isVideo && !isImage) {
      notifyError(m.settings.backgroundUploadError);
      return;
    }
    try {
      await saveBackgroundBlob(file);
      setAppearance({
        background: "custom",
        customType: isVideo ? "video" : "image",
        customName: file.name,
      });
    } catch {
      notifyError(m.settings.backgroundUploadError);
    }
  };

  const removeCustom = async () => {
    try {
      await clearBackgroundBlob();
    } catch {
      /* ignore */
    }
    setAppearance({ background: "none", customType: null, customName: null });
  };

  return (
    <div className="settings-background-block">
      <div className="settings-background-grid" role="radiogroup" aria-label={m.settings.backgroundTitle}>
        {BACKGROUND_PRESETS.map((preset) => (
          <button
            key={preset.id}
            type="button"
            role="radio"
            aria-checked={appearance.background === preset.id}
            onClick={() => setAppearance({ background: preset.id })}
            className={[
              "settings-background-card",
              appearance.background === preset.id ? "settings-background-card-active" : "",
            ].join(" ")}
          >
            <span className={["settings-background-thumb", `settings-background-thumb-${preset.id}`].join(" ")} aria-hidden="true">
              {preset.animated && preset.id !== "none" && (
                <span className="settings-background-anim-badge">
                  <AnimIcon />
                </span>
              )}
            </span>
            <span className="settings-background-label">{preset.id === "none" ? m.settings.backgroundNone : preset.label}</span>
          </button>
        ))}
        <button
          type="button"
          onClick={() => fileRef.current?.click()}
          className={[
            "settings-background-card settings-background-card-custom",
            appearance.background === "custom" ? "settings-background-card-active" : "",
          ].join(" ")}
        >
          <span className="settings-background-thumb settings-background-thumb-upload" aria-hidden="true">
            <UploadIcon />
          </span>
          <span className="settings-background-label">{m.settings.backgroundCustom}</span>
        </button>
      </div>
      <input
        ref={fileRef}
        type="file"
        accept="image/*,video/*"
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) void onFile(file);
          event.target.value = "";
        }}
      />
      <div className="settings-background-formats">{m.settings.backgroundFormats}</div>

      {appearance.background === "custom" && (
        <div className="settings-background-custom-row">
          <span className="settings-background-custom-name">{appearance.customName ?? "—"}</span>
          <div className="settings-background-custom-actions">
            <button type="button" className="settings-action" onClick={() => fileRef.current?.click()}>
              {m.settings.backgroundReplace}
            </button>
            <button type="button" className="settings-action" onClick={() => void removeCustom()}>
              {m.settings.backgroundRemove}
            </button>
          </div>
        </div>
      )}

      {appearance.background !== "none" && (
        <div className="settings-background-sliders">
          <VisualSliderRow
            label={m.settings.backgroundDim}
            value={appearance.backgroundDim}
            min={0}
            max={90}
            step={5}
            formatValue={(value) => `${value}%`}
            onChange={(value) => setAppearance({ backgroundDim: value })}
            onCommit={(value) => setAppearance({ backgroundDim: value })}
          />
          <VisualSliderRow
            label={m.settings.backgroundBlur}
            value={appearance.backgroundBlur}
            min={0}
            max={40}
            step={1}
            formatValue={(value) => `${value} px`}
            onChange={(value) => setAppearance({ backgroundBlur: value })}
            onCommit={(value) => setAppearance({ backgroundBlur: value })}
          />
        </div>
      )}
    </div>
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

function ProviderThemeRow({
  label,
  description,
  enabled,
  onToggle,
}: {
  label: string;
  description: string;
  enabled: boolean;
  onToggle: (enabled: boolean) => void;
}) {
  return (
    <div className="settings-provider-theme">
      <div className="settings-row">
        <div className="settings-row-label-container">
          <div>
            <div className="settings-row-title">{label}</div>
            <div className="settings-row-description">{description}</div>
          </div>
        </div>
        <button
          type="button"
          aria-pressed={enabled}
          onClick={() => onToggle(!enabled)}
          className={["settings-toggle", enabled ? "settings-toggle-on" : ""].join(" ")}
        >
          <span />
        </button>
      </div>
    </div>
  );
}

function LanguagePreviewOption({
  flag,
  icon,
  title,
  sampleTitle,
  sampleLine,
  sampleChip,
  selected,
  onClick,
}: {
  flag?: string;
  icon?: ReactNode;
  title: string;
  sampleTitle: string;
  sampleLine: string;
  sampleChip: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      aria-label={title}
      onClick={onClick}
      className={[
        "settings-theme-card",
        "settings-language-card",
        selected ? "settings-theme-card-active" : "",
      ].join(" ")}
    >
      <span className="settings-theme-preview settings-language-preview" aria-hidden="true">
        <span
          className={[
            "settings-language-preview-flag",
            icon ? "settings-language-preview-flag-icon" : "",
          ].join(" ")}
        >
          {icon ?? <span className={`fi fi-${flag}`} />}
        </span>
        <span className="settings-language-preview-body">
          <span className="settings-language-preview-title">{sampleTitle}</span>
          <span className="settings-language-preview-line">{sampleLine}</span>
          <span className="settings-language-preview-chip">{sampleChip}</span>
        </span>
      </span>
      <span className="settings-theme-card-label">{title}</span>
    </button>
  );
}

function ConnectionStyleOption({
  title,
  description,
  value,
  selected,
  icon,
  onClick,
}: {
  title: string;
  description: string;
  value: ConnectButtonStyle;
  selected: boolean;
  icon: ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      aria-label={`${title}. ${description}`}
      data-style={value}
      onClick={onClick}
      className={[
        "settings-connect-style-card",
        selected ? "settings-connect-style-card-active" : "",
      ].join(" ")}
    >
      <span className="settings-connect-style-icon" aria-hidden="true">{icon}</span>
      <span className="settings-connect-style-copy">
        <span className="settings-connect-style-title">{title}</span>
      </span>
    </button>
  );
}

function XMarkIcon() {
  return <Icon><path d="M6 6l12 12M18 6 6 18" /></Icon>;
}
function PlusSmIcon() {
  return <Icon><path d="M12 5v14" /><path d="M5 12h14" /></Icon>;
}
function UploadIcon() {
  return <Icon><path d="M12 16V4" /><path d="m7 9 5-5 5 5" /><path d="M5 20h14" /></Icon>;
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
function RotateCcwIcon() {
  return (
    <Icon>
      <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
      <path d="M3 3v5h5" />
    </Icon>
  );
}
function ChevronDownIcon() {
  return <Icon><path d="m6 9 6 6 6-6" /></Icon>;
}

function AnimIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-full w-full" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21.5 2v6h-6" />
      <path d="M21.34 15.57a10 10 0 1 1-.57-8.38l.73-.73" />
    </svg>
  );
}

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" className="h-full w-full" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      {children}
    </svg>
  );
}
