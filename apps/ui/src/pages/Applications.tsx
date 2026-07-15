import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import {
  api,
  type AppProxyMode,
  type AppProxyRule,
  type InstalledApp,
} from "../lib/api";
import { fillTemplate, useMessages } from "../lib/i18n";
import { notifyError, notifyInfo } from "../lib/notify";
import { useAppStore } from "../store";

type AppMode = "direct" | "proxy";
type FilterMode = "all" | "enabled" | "disabled" | "subscription";
type SortMode = "default" | "az" | "za";
const DOMAIN_ENTRY_PREFIX = "__domain__:";
const APP_ROUTING_MODE_KEY = "nimbo.appRoutingMode";

function canonicalRuleKey(path: string): string {
  const trimmed = path.trim();
  if (!trimmed) return "";
  if (trimmed.startsWith(DOMAIN_ENTRY_PREFIX)) {
    return DOMAIN_ENTRY_PREFIX + trimmed.slice(DOMAIN_ENTRY_PREFIX.length).trim().toLowerCase();
  }
  const normalized = trimmed.replace(/\\/g, "/").toLowerCase();
  const parts = normalized.split("/");
  return (parts[parts.length - 1] ?? normalized).trim();
}

// ── Icon cache & concurrency limiter ─────────────────────────
const iconCache = new Map<string, string | null>();
const iconInflight = new Map<string, Promise<string | null>>();
let concurrentLoads = 0;
const MAX_CONCURRENT = 6;
const waitQueue: Array<() => void> = [];

async function fetchIconThrottled(path: string): Promise<string | null> {
  if (iconCache.has(path)) return iconCache.get(path) ?? null;
  if (iconInflight.has(path)) return iconInflight.get(path)!;

  const run = async (): Promise<string | null> => {
    if (concurrentLoads >= MAX_CONCURRENT) {
      await new Promise<void>((res) => waitQueue.push(res));
    }
    concurrentLoads++;
    try {
      const result = await api.getAppIcon(path).catch(() => null);
      iconCache.set(path, result);
      return result;
    } finally {
      concurrentLoads--;
      waitQueue.shift()?.();
      iconInflight.delete(path);
    }
  };

  const promise = run();
  iconInflight.set(path, promise);
  return promise;
}

function useAppIconLazy(executablePath: string) {
  const [src, setSrc] = useState<string | null>(() => iconCache.get(executablePath) ?? null);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (domainFromTarget(executablePath)) {
      setSrc(null);
      setLoading(false);
      return;
    }
    if (iconCache.has(executablePath)) {
      setSrc(iconCache.get(executablePath) ?? null);
      return;
    }

    const el = containerRef.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return;
        observer.disconnect();
        setLoading(true);
        fetchIconThrottled(executablePath).then((result) => {
          setSrc(result);
          setLoading(false);
        });
      },
      { rootMargin: "300px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [executablePath]);

  return { src, loading, containerRef };
}

export function Applications() {
  const m = useMessages();
  const preferences = useAppStore((s) => s.preferences);
  const setPreferences = useAppStore((s) => s.setPreferences);
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [rules, setRules] = useState<AppProxyRule[]>([]);
  const [subRules, setSubRules] = useState<AppProxyRule[]>([]);
  const [installedPaths, setInstalledPaths] = useState<Set<string>>(() => new Set());
  const [mode, setMode] = useState<AppMode>(() => readAppRoutingMode(preferences.app_routing_mode));
  const [query, setQuery] = useState("");
  const [filterMode, setFilterMode] = useState<FilterMode>("all");
  const [sortMode, setSortMode] = useState<SortMode>("default");
  const [busy, setBusy] = useState(false);
  const [showAddCustom, setShowAddCustom] = useState(false);
  const importFileInput = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    setMode(readAppRoutingMode(preferences.app_routing_mode));
  }, [preferences.app_routing_mode]);

  useEffect(() => {
    Promise.all([
      api.listInstalledApps(),
      api.listAppProxyRules(),
      api.listSubscriptionAppProxyRules(),
    ])
      .then(([installed, saved, subscription]) => {
        const normalizedSaved = normalizeSavedRules(saved);
        const normalizedSub = normalizeSavedRules(subscription);
        setInstalledPaths(new Set(installed.map((app) => app.executable_path)));
        setApps(mergeAllSources(installed, normalizedSaved, normalizedSub));
        setRules(normalizedSaved);
        setSubRules(normalizedSub);
        if (normalizedSaved.some((rule, index) => rule.executable_path !== saved[index]?.executable_path)) {
          void api.setAppProxyRules(normalizedSaved);
        }
      })
      .catch(() => {
        setInstalledPaths(new Set());
        setApps([]);
        setRules([]);
        setSubRules([]);
      });
  }, []);

  const subscriptionByPath = useMemo(() => {
    const map = new Map<string, AppProxyRule>();
    for (const rule of subRules) map.set(canonicalRuleKey(rule.executable_path), rule);
    return map;
  }, [subRules]);

  const selectedPaths = useMemo(() => {
    const set = new Set<string>();
    for (const rule of subRules) {
      if (rule.enabled) set.add(canonicalRuleKey(rule.executable_path));
    }
    for (const rule of rules) {
      const key = canonicalRuleKey(rule.executable_path);
      if (rule.enabled) set.add(key);
      else set.delete(key);
    }
    return set;
  }, [rules, subRules]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    let source = apps;

    if (q) {
      source = source.filter(
        (app) =>
          app.name.toLowerCase().includes(q) ||
          app.executable_path.toLowerCase().includes(q),
      );
    }

    if (filterMode === "enabled") {
      source = source.filter((app) => selectedPaths.has(canonicalRuleKey(app.executable_path)));
    } else if (filterMode === "disabled") {
      source = source.filter((app) => !selectedPaths.has(canonicalRuleKey(app.executable_path)));
    } else if (filterMode === "subscription") {
      source = source.filter((app) => subscriptionByPath.has(canonicalRuleKey(app.executable_path)));
    }

    if (sortMode === "az" || sortMode === "za") {
      const collator = new Intl.Collator(undefined, { sensitivity: "base", numeric: true });
      const direction = sortMode === "az" ? 1 : -1;
      source = [...source].sort((a, b) => direction * collator.compare(a.name, b.name));
    }

    return source.slice(0, 180);
  }, [apps, query, filterMode, sortMode, selectedPaths, subscriptionByPath]);

  const setAllMode = async (next: AppMode) => {
    setMode(next);
    void writeAppRoutingMode(next, setPreferences);
    const updated = rules.map((rule) =>
      rule.enabled ? { ...rule, mode: next as AppProxyMode } : rule,
    );
    setRules(updated);
    await save(updated);
  };

  const toggleApp = async (app: InstalledApp) => {
    const path = app.executable_path;
    const key = canonicalRuleKey(path);
    const existing = rules.find((rule) => canonicalRuleKey(rule.executable_path) === key);
    const subRule = subscriptionByPath.get(key);
    const currentMode: AppProxyMode = existing?.enabled
      ? existing.mode
      : subRule?.enabled
        ? subRule.mode
        : mode;
    const currentlyEnabled = existing ? existing.enabled : subRule?.enabled ?? false;
    const wantsModeSwitch = currentlyEnabled && currentMode !== mode;
    const newEnabled = wantsModeSwitch ? true : !currentlyEnabled;
    const effectiveMode: AppProxyMode = wantsModeSwitch
      ? mode
      : currentlyEnabled
        ? existing?.mode ?? subRule?.mode ?? mode
        : mode;

    // Drop any other stale rules pointing at the same process — keep one canonical entry.
    const filtered = rules.filter(
      (rule) => canonicalRuleKey(rule.executable_path) !== key || rule.id === existing?.id,
    );

    const updated = existing
      ? filtered.map((rule) =>
          rule.id === existing.id
            ? { ...rule, enabled: newEnabled, mode: effectiveMode }
            : rule,
        )
      : [
          ...filtered,
          {
            id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            name: app.name,
            executable_path: path,
            mode: effectiveMode,
            enabled: newEnabled,
          },
        ];

    setRules(updated);
    await save(updated);
  };

  const deleteRule = async (app: InstalledApp) => {
    const key = canonicalRuleKey(app.executable_path);
    if (subscriptionByPath.has(key)) return;
    const updated = rules.filter((rule) => canonicalRuleKey(rule.executable_path) !== key);
    setRules(updated);
    if (!installedPaths.has(app.executable_path)) {
      setApps((prev) =>
        prev.filter((item) => canonicalRuleKey(item.executable_path) !== key),
      );
    }
    await save(updated);
  };

  const reapplyTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const scheduleReapply = () => {
    if (reapplyTimer.current) clearTimeout(reapplyTimer.current);
    reapplyTimer.current = setTimeout(() => {
      reapplyTimer.current = null;
      api.reapplyRuntimeConfig().catch((e) => console.error("reapply failed", e));
    }, 700);
  };

  useEffect(() => {
    return () => {
      if (!reapplyTimer.current) return;
      clearTimeout(reapplyTimer.current);
      reapplyTimer.current = null;
      api.reapplyRuntimeConfig().catch((e) => console.error("reapply failed", e));
    };
  }, []);

  const save = async (next: AppProxyRule[]) => {
    setBusy(true);
    try {
      await api.setAppProxyRules(next);
      scheduleReapply();
    } catch (e) {
      console.error(e);
    } finally {
      setBusy(false);
    }
  };

  const importRulesFile = async (file: File | undefined) => {
    if (!file) return;
    try {
      const text = await file.text();
      const imported = parseLocalRulesPayload(text, mode);
      if (imported.length === 0) {
        notifyError(m.appsPage.importFileEmpty);
        return;
      }

      const updated = mergeImportedRules(rules, imported);
      setRules(updated);
      setApps((prev) => mergeAllSources(prev, updated, subRules));
      await save(updated);
      notifyInfo(fillTemplate(m.appsPage.importedRules, { count: imported.length }));
    } catch (e) {
      console.error(e);
      notifyError(m.appsPage.fileReadError);
    }
  };

  const exportRulesFile = async () => {
    try {
      const { payload, count } = buildLocalRulesExport(rules, subRules, mode);
      if (count === 0) {
        notifyError(m.appsPage.exportFileEmpty);
        return;
      }

      const savedPath = await api.exportAppProxyRulesFile(
        payload,
        appRulesExportFileName(),
      );
      if (!savedPath) return;
      notifyInfo(fillTemplate(m.appsPage.exportedRules, { count }));
    } catch (e) {
      console.error(e);
      notifyError(m.appsPage.fileExportError);
    }
  };

  const addCustomEntry = async (path: string, name: string) => {
    const normalizedPath = normalizeCustomPath(path);
    const newApp: InstalledApp = {
      name: name.trim() || deriveName(normalizedPath),
      executable_path: normalizedPath,
    };
    setApps((prev) => {
      const exists = prev.some((a) => a.executable_path === normalizedPath);
      return exists ? prev : [...prev, newApp];
    });
    await toggleApp(newApp);
  };

  return (
    <div className="page-view">
      <h1 className="page-title">{m.appsPage.title}</h1>

      <div className="mt-7 mb-7 grid max-w-2xl grid-cols-2 gap-3 mobile-stack">
        <ModeButton active={mode === "direct"} onClick={() => void setAllMode("direct")}>
          {m.appsPage.direct}
        </ModeButton>
        <ModeButton active={mode === "proxy"} onClick={() => void setAllMode("proxy")}>
          {m.appsPage.proxy}
        </ModeButton>
      </div>

      <p className="page-subtitle mb-7">
        {mode === "direct" ? m.appsPage.directDescription : m.appsPage.proxyDescription}
      </p>

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="relative min-w-[220px] flex-1">
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={m.appsPage.search}
            className="dark-input w-full px-5 py-4 pr-12 text-lg"
          />
          {query && (
            <button
              type="button"
              aria-label={m.tunnelLogs.clear}
              title={m.tunnelLogs.clear}
              onClick={() => setQuery("")}
              className="absolute right-3 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-lg text-[var(--color-text-faint)] transition-colors hover:bg-[var(--color-glass-bg)] hover:text-white"
            >
              <XIcon />
            </button>
          )}
        </div>
        <button
          onClick={() => setShowAddCustom(true)}
          className="interactive flex shrink-0 items-center gap-2 rounded-2xl border border-[var(--color-border)] bg-[var(--color-glass-bg)] px-4 py-4 text-sm font-semibold text-[var(--color-text-dim)] hover:border-[var(--color-border-strong)] hover:text-white"
        >
          <PlusIcon />
          <span>{m.appsPage.addCustom}</span>
        </button>
        <button
          type="button"
          onClick={() => importFileInput.current?.click()}
          className="interactive flex shrink-0 items-center gap-2 rounded-2xl border border-[var(--color-border)] bg-[var(--color-glass-bg)] px-4 py-4 text-sm font-semibold text-[var(--color-text-dim)] hover:border-[var(--color-border-strong)] hover:text-white"
        >
          <ImportFileIcon />
          <span>{m.appsPage.importFromFile}</span>
        </button>
        <button
          type="button"
          onClick={() => void exportRulesFile()}
          className="interactive flex shrink-0 items-center gap-2 rounded-2xl border border-[var(--color-border)] bg-[var(--color-glass-bg)] px-4 py-4 text-sm font-semibold text-[var(--color-text-dim)] hover:border-[var(--color-border-strong)] hover:text-white"
        >
          <ExportFileIcon />
          <span>{m.appsPage.exportToFile}</span>
        </button>
        <input
          ref={importFileInput}
          type="file"
          className="hidden"
          accept=".txt,.csv,.json,.list,.conf,.yaml,.yml"
          onChange={(event) => {
            const file = event.currentTarget.files?.[0];
            event.currentTarget.value = "";
            void importRulesFile(file);
          }}
        />
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <FilterChip active={filterMode === "all"} onClick={() => setFilterMode("all")}>
          {m.appsPage.filterAll}
        </FilterChip>
        <FilterChip active={filterMode === "enabled"} onClick={() => setFilterMode("enabled")}>
          {m.appsPage.filterEnabled}
        </FilterChip>
        <FilterChip active={filterMode === "disabled"} onClick={() => setFilterMode("disabled")}>
          {m.appsPage.filterDisabled}
        </FilterChip>
        <FilterChip
          active={filterMode === "subscription"}
          onClick={() => setFilterMode("subscription")}
          disabled={subRules.length === 0}
        >
          {m.appsPage.filterSubscription}
        </FilterChip>
        <button
          type="button"
          onClick={() =>
            setSortMode((prev) => (prev === "default" ? "az" : prev === "az" ? "za" : "default"))
          }
          title={m.appsPage.sortLabel}
          className="interactive ml-auto flex items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-glass-bg)] px-3 py-2 text-sm font-semibold text-[var(--color-text-dim)] hover:border-[var(--color-border-strong)] hover:text-white"
        >
          <SortIcon />
          <span>
            {sortMode === "az"
              ? m.appsPage.sortAz
              : sortMode === "za"
                ? m.appsPage.sortZa
                : m.appsPage.sortDefault}
          </span>
        </button>
      </div>

      <div className="mb-5 text-sm font-bold text-[var(--color-text-faint)]">
        {m.common.selected} {selectedPaths.size} {m.common.from} {apps.length}
        {busy ? ` · ${m.common.savingProgress}` : ""}
      </div>

      <div className="panel overflow-hidden">
        {filtered.length === 0 ? (
          <div className="px-6 py-16 text-center text-[var(--color-text-faint)]">
            {m.appsPage.empty}
          </div>
        ) : (
          <div className="divide-y divide-[var(--color-border)]">
            {filtered.map((app) => {
              const key = canonicalRuleKey(app.executable_path);
              const subRule = subscriptionByPath.get(key);
              return (
                <AppRow
                  key={`${app.name}-${app.executable_path}`}
                  app={app}
                  selected={selectedPaths.has(key)}
                  hasRule={rules.some((rule) => canonicalRuleKey(rule.executable_path) === key)}
                  fromSubscription={Boolean(subRule)}
                  subscriptionMode={subRule?.mode}
                  onClick={() => void toggleApp(app)}
                  onDelete={() => void deleteRule(app)}
                />
              );
            })}
          </div>
        )}
      </div>

      {showAddCustom && (
        <AddCustomDialog
          onAdd={(path, name) => void addCustomEntry(path, name)}
          onClose={() => setShowAddCustom(false)}
        />
      )}
    </div>
  );
}

function readAppRoutingMode(preference?: string): AppMode {
  if (preference === "proxy") return "proxy";
  try {
    return localStorage.getItem(APP_ROUTING_MODE_KEY) === "proxy" ? "proxy" : "direct";
  } catch {
    return "direct";
  }
}

async function writeAppRoutingMode(
  mode: AppMode,
  setPreferences: ReturnType<typeof useAppStore.getState>["setPreferences"],
) {
  try {
    localStorage.setItem(APP_ROUTING_MODE_KEY, mode);
  } catch {}
  const current = useAppStore.getState().preferences;
  if (current.app_routing_mode === mode) return;
  await setPreferences({ ...current, app_routing_mode: mode }).catch((e) =>
    console.error("failed to save app routing mode", e),
  );
}

type DraftEntry = {
  id: string;
  path: string;
  name: string;
  iconSrc: string | null;
  iconLoading: boolean;
};

function newDraftEntry(): DraftEntry {
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    path: "",
    name: "",
    iconSrc: null,
    iconLoading: false,
  };
}

function normalizeSavedRules(rules: AppProxyRule[]): AppProxyRule[] {
  return rules.map((rule) => ({
    ...rule,
    executable_path: normalizeCustomPath(rule.executable_path),
  }));
}

type ImportedRuleDraft = {
  target: string;
  mode?: AppProxyMode;
  name?: string;
  enabled?: boolean;
};

type ExportRuleEntry = {
  name: string;
  target: string;
  mode: AppProxyMode;
  enabled: boolean;
  source: "local" | "subscription";
  executable_path?: string;
  domain?: string;
};

type ExportableRule = AppProxyRule & {
  source: "local" | "subscription";
};

function buildLocalRulesExport(
  localRules: AppProxyRule[],
  subscriptionRules: AppProxyRule[],
  fallbackMode: AppMode,
): { payload: string; count: number } {
  const byKey = new Map<string, ExportableRule>();

  for (const rule of subscriptionRules) {
    const key = canonicalRuleKey(rule.executable_path);
    if (key) byKey.set(key, { ...rule, source: "subscription" });
  }
  for (const rule of localRules) {
    const key = canonicalRuleKey(rule.executable_path);
    if (key) byKey.set(key, { ...rule, source: "local" });
  }

  const processes: ExportRuleEntry[] = [];
  const sites: ExportRuleEntry[] = [];
  for (const rule of byKey.values()) {
    const executablePath = normalizeCustomPath(rule.executable_path);
    const domain = domainFromTarget(executablePath);
    const base = {
      name: rule.name.trim() || deriveName(executablePath),
      mode: rule.mode,
      enabled: rule.enabled,
      source: rule.source,
    };

    if (domain) {
      sites.push({
        ...base,
        target: domain,
        domain,
      });
    } else {
      processes.push({
        ...base,
        target: executablePath,
        executable_path: executablePath,
      });
    }
  }

  const payload = {
    schema: "nimbo-app-routing-rules-v1",
    app: "Nimbo",
    exported_at: new Date().toISOString(),
    default_mode: fallbackMode,
    counts: {
      processes: processes.length,
      sites: sites.length,
      total: processes.length + sites.length,
    },
    processes,
    sites,
  };

  return {
    payload: JSON.stringify(payload, null, 2),
    count: processes.length + sites.length,
  };
}

function appRulesExportFileName(): string {
  const date = new Date();
  const stamp = [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
  return `nimbo-app-rules-${stamp}.json`;
}

function parseLocalRulesPayload(payload: string, fallbackMode: AppMode): AppProxyRule[] {
  const trimmed = payload.trim();
  if (!trimmed) return [];

  const drafts: ImportedRuleDraft[] = [];
  try {
    const parsed = JSON.parse(trimmed) as unknown;
    collectJsonRules(parsed, fallbackMode, drafts);
    if (drafts.length > 0) {
      return finalizeImportedRules(drafts, fallbackMode);
    }
  } catch {
    /* plain text import */
  }

  parseTextRules(trimmed, fallbackMode, undefined, drafts);
  return finalizeImportedRules(drafts, fallbackMode);
}

function collectJsonRules(value: unknown, fallbackMode: AppMode, out: ImportedRuleDraft[]) {
  if (typeof value === "string") {
    parseTextRules(value, fallbackMode, undefined, out);
    return;
  }
  if (Array.isArray(value)) {
    for (const item of value) collectJsonRules(item, fallbackMode, out);
    return;
  }
  if (!value || typeof value !== "object") return;

  const record = value as Record<string, unknown>;
  const objectMode = firstMode(record, ["mode", "action", "outbound", "outboundTag", "policy"]) ?? fallbackMode;
  const objectEnabled = firstBoolean(record, ["enabled", "active"]);
  const objectName = firstString(record, ["name", "title", "label", "displayName", "display_name"]);
  const target = firstString(record, [
    "executable_path",
    "executablePath",
    "path",
    "process",
    "process_name",
    "processName",
    "target",
    "domain",
    "url",
    "app",
  ]);

  if (target) {
    out.push({
      target,
      mode: objectMode,
      name: objectName,
      enabled: objectEnabled,
    });
  }

  for (const [key, nested] of Object.entries(record)) {
    const keyMode = modeFromKey(key) ?? objectMode;
    if (isRuleCollectionKey(key)) {
      collectJsonTargets(nested, keyMode, objectName, objectEnabled, out);
    } else if (!isScalarRuleField(key)) {
      collectJsonRules(nested, keyMode, out);
    }
  }
}

function collectJsonTargets(
  value: unknown,
  mode: AppProxyMode,
  name: string | undefined,
  enabled: boolean | undefined,
  out: ImportedRuleDraft[],
) {
  if (typeof value === "string") {
    const before = out.length;
    parseTextRules(value, mode, name, out);
    if (enabled !== undefined) {
      for (let index = before; index < out.length; index += 1) {
        out[index].enabled = enabled;
      }
    }
    return;
  }
  if (Array.isArray(value)) {
    for (const item of value) collectJsonTargets(item, mode, name, enabled, out);
    return;
  }
  if (value && typeof value === "object") {
    collectJsonRules(value, mode, out);
  }
}

function parseTextRules(
  payload: string,
  fallbackMode: AppMode,
  defaultName: string | undefined,
  out: ImportedRuleDraft[],
) {
  for (const rawLine of payload.split(/\r?\n/)) {
    const line = cleanRuleLine(rawLine);
    if (!line || line.startsWith("#") || line.startsWith("//")) continue;

    const directValue = splitNamedRuleValue(line, [
      "process-direct",
      "app-direct",
      "apps-direct",
      "direct-apps",
      "direct-domains",
      "process-bypass",
      "app-bypass",
    ]);
    if (directValue) {
      parseTextRules(directValue, "direct", defaultName, out);
      continue;
    }

    const proxyValue = splitNamedRuleValue(line, [
      "process-proxy",
      "app-proxy",
      "apps-proxy",
      "proxy-apps",
      "proxy-domains",
    ]);
    if (proxyValue) {
      parseTextRules(proxyValue, "proxy", defaultName, out);
      continue;
    }

    const nestedValue = splitNamedRuleValue(line, [
      "process-rules",
      "app-rules",
      "apps-rules",
      "rules",
    ]);
    if (nestedValue) {
      parseTextRules(nestedValue, fallbackMode, defaultName, out);
      continue;
    }

    const tokens = splitRuleTokens(line);
    if (tokens.length === 0) continue;

    if (tokens[0].toUpperCase() === "PROCESS-NAME" && tokens[1]) {
      const lineMode = firstParsedMode(tokens.slice(2));
      out.push({
        target: tokens[1],
        mode: lineMode ?? fallbackMode,
        name: defaultName,
        enabled: true,
      });
      continue;
    }

    const lineMode = firstParsedMode(tokens) ?? fallbackMode;
    const nonModeTokens = tokens.filter((token) => !appModeFromText(token));
    for (const item of targetsFromTokens(nonModeTokens, defaultName)) {
      out.push({ ...item, mode: lineMode, enabled: true });
    }
  }
}

function splitRuleTokens(line: string): string[] {
  return line
    .split(/[;,]/)
    .map(cleanRuleLine)
    .filter(Boolean);
}

function targetsFromTokens(tokens: string[], defaultName: string | undefined): ImportedRuleDraft[] {
  const likelyTargets = tokens.filter(isLikelyRuleTarget);
  if (likelyTargets.length > 0 && likelyTargets.length < tokens.length) {
    const name = tokens.find((token) => !likelyTargets.includes(token)) ?? defaultName;
    return likelyTargets.map((target) => ({ target, name }));
  }
  return tokens.map((target) => ({ target, name: defaultName }));
}

function splitNamedRuleValue(line: string, keys: string[]): string | null {
  const wanted = new Set(keys.map(normalizeRuleKey));
  for (const separator of [":", "="]) {
    const index = line.indexOf(separator);
    if (index <= 0) continue;
    const key = normalizeRuleKey(line.slice(0, index));
    if (wanted.has(key)) {
      const value = line.slice(index + 1).trim();
      return value ? value : null;
    }
  }

  const match = line.match(/^([a-z][a-z0-9_-]*)\s+(.+)$/i);
  if (!match) return null;
  return wanted.has(normalizeRuleKey(match[1])) ? match[2].trim() || null : null;
}

function cleanRuleLine(value: string): string {
  return value
    .trim()
    .replace(/^\s*[-*]\s+/, "")
    .trim()
    .replace(/^["'\[]+|["'\]]+$/g, "")
    .trim();
}

function appModeFromText(value: string): AppProxyMode | undefined {
  switch (normalizeRuleKey(value)) {
    case "direct":
    case "bypass":
    case "bypasslan":
      return "direct";
    case "proxy":
    case "vpn":
    case "proxied":
      return "proxy";
    default:
      return undefined;
  }
}

function firstParsedMode(values: string[]): AppProxyMode | undefined {
  for (const value of values) {
    const mode = appModeFromText(value);
    if (mode) return mode;
  }
  return undefined;
}

function normalizeRuleKey(value: string): string {
  return value.replace(/[^a-z0-9]/gi, "").toLowerCase();
}

function modeFromKey(key: string): AppProxyMode | undefined {
  const normalized = normalizeRuleKey(key);
  if (normalized.includes("direct") || normalized.includes("bypass")) return "direct";
  if (normalized.includes("proxy") || normalized.includes("vpn")) return "proxy";
  return undefined;
}

function isRuleCollectionKey(key: string): boolean {
  return new Set([
    "appproxyrules",
    "rules",
    "apps",
    "appnames",
    "processes",
    "processnames",
    "domains",
    "domainnames",
    "sites",
    "urls",
    "processdirect",
    "processproxy",
    "processbypass",
    "appdirect",
    "appproxy",
    "appbypass",
    "directapps",
    "proxyapps",
    "directdomains",
    "proxydomains",
    "directsites",
    "proxysites",
    "items",
  ]).has(normalizeRuleKey(key));
}

function isScalarRuleField(key: string): boolean {
  return new Set([
    "schema",
    "version",
    "appname",
    "exportedat",
    "generatedat",
    "defaultmode",
    "routingmode",
    "counts",
    "count",
    "total",
    "type",
    "id",
    "name",
    "title",
    "label",
    "displayname",
    "executablepath",
    "path",
    "process",
    "processname",
    "target",
    "domain",
    "url",
    "app",
    "mode",
    "action",
    "outbound",
    "outboundtag",
    "policy",
    "enabled",
    "active",
    "source",
  ]).has(normalizeRuleKey(key));
}

function firstString(record: Record<string, unknown>, keys: string[]): string | undefined {
  for (const key of keys) {
    const value = looseGet(record, key);
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return undefined;
}

function firstBoolean(record: Record<string, unknown>, keys: string[]): boolean | undefined {
  for (const key of keys) {
    const value = looseGet(record, key);
    if (typeof value === "boolean") return value;
    if (typeof value === "string") {
      const normalized = normalizeRuleKey(value);
      if (["true", "yes", "on", "enabled", "1"].includes(normalized)) return true;
      if (["false", "no", "off", "disabled", "0"].includes(normalized)) return false;
    }
  }
  return undefined;
}

function firstMode(record: Record<string, unknown>, keys: string[]): AppProxyMode | undefined {
  const value = firstString(record, keys);
  return value ? appModeFromText(value) : undefined;
}

function looseGet(record: Record<string, unknown>, key: string): unknown {
  if (Object.prototype.hasOwnProperty.call(record, key)) return record[key];
  const normalized = normalizeRuleKey(key);
  const found = Object.entries(record).find(([candidate]) => normalizeRuleKey(candidate) === normalized);
  return found?.[1];
}

function isLikelyRuleTarget(value: string): boolean {
  const trimmed = value.trim();
  if (!trimmed) return false;
  if (trimmed.startsWith(DOMAIN_ENTRY_PREFIX) || domainFromTarget(trimmed)) return true;
  if (/^[a-zA-Z]:[\\/]/.test(trimmed) || trimmed.includes("\\") || trimmed.includes("/")) return true;
  return /\.(exe|bat|cmd|msi|lnk)$/i.test(trimmed);
}

function finalizeImportedRules(drafts: ImportedRuleDraft[], fallbackMode: AppMode): AppProxyRule[] {
  const byKey = new Map<string, AppProxyRule>();
  const batchId = Date.now().toString(36);
  drafts.forEach((draft, index) => {
    const executablePath = normalizeCustomPath(draft.target);
    const key = canonicalRuleKey(executablePath);
    if (!key) return;
    byKey.set(key, {
      id: `local-file-${stableTextId(key)}-${batchId}-${index}`,
      name: draft.name?.trim() || deriveName(executablePath),
      executable_path: executablePath,
      mode: draft.mode ?? fallbackMode,
      enabled: draft.enabled ?? true,
    });
  });
  return [...byKey.values()];
}

function mergeImportedRules(existing: AppProxyRule[], imported: AppProxyRule[]): AppProxyRule[] {
  const byKey = new Map<string, AppProxyRule>();
  for (const rule of existing) {
    const key = canonicalRuleKey(rule.executable_path);
    if (key) byKey.set(key, rule);
  }
  for (const rule of imported) {
    const key = canonicalRuleKey(rule.executable_path);
    if (key) byKey.set(key, rule);
  }
  return [...byKey.values()];
}

function stableTextId(value: string): string {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(36);
}

function mergeAllSources(
  installed: InstalledApp[],
  localRules: AppProxyRule[],
  subscriptionRules: AppProxyRule[],
): InstalledApp[] {
  const byKey = new Map<string, InstalledApp>();
  const addCandidate = (candidate: InstalledApp, preferOver: boolean) => {
    const key = canonicalRuleKey(candidate.executable_path);
    if (!key) return;
    if (!byKey.has(key) || preferOver) {
      byKey.set(key, candidate);
    }
  };
  // Installed apps come first — they carry the real path and the OS icon.
  for (const app of installed) addCandidate(app, false);
  // Subscription rules: only add if no installed entry covers the process.
  for (const rule of subscriptionRules) {
    addCandidate(
      {
        name: rule.name.trim() || deriveName(rule.executable_path),
        executable_path: rule.executable_path,
      },
      false,
    );
  }
  // Local rules: same — only fill in gaps so toggled custom entries stay visible.
  for (const rule of localRules) {
    addCandidate(
      {
        name: rule.name.trim() || deriveName(rule.executable_path),
        executable_path: rule.executable_path,
      },
      false,
    );
  }
  return [...byKey.values()];
}

function deriveName(path: string): string {
  const t = path.trim();
  const domain = domainFromTarget(t);
  if (domain) {
    const base = domain.replace(/^www\./, "").split(".")[0];
    return base.charAt(0).toUpperCase() + base.slice(1);
  }
  if (t.includes("\\") || t.includes("/")) {
    return t.split(/[/\\]/).pop()?.replace(/\.exe$/i, "") ?? t;
  }
  return t;
}

function normalizeCustomPath(path: string): string {
  const domain = domainFromTarget(path);
  return domain ? `${DOMAIN_ENTRY_PREFIX}${domain}` : path.trim();
}

function isDomainEntry(path: string): boolean {
  return domainFromTarget(path) !== null;
}

function domainFromTarget(path: string): string | null {
  const t = path.trim();
  if (!t) return null;

  const unprefixed = t.startsWith(DOMAIN_ENTRY_PREFIX)
    ? t.slice(DOMAIN_ENTRY_PREFIX.length).trim()
    : t;

  if (!unprefixed || unprefixed.includes("\\") || /^[a-zA-Z]:[\\/]/.test(unprefixed)) {
    return null;
  }
  if (/\.(exe|bat|msi|cmd|lnk)(?:$|[?#])/i.test(unprefixed)) {
    return null;
  }

  const candidate = /^[a-z][a-z0-9+.-]*:\/\//i.test(unprefixed)
    ? unprefixed
    : `https://${unprefixed}`;

  try {
    const url = new URL(candidate);
    if (url.protocol !== "http:" && url.protocol !== "https:") return null;
    const hostname = url.hostname.toLowerCase().replace(/\.$/, "");
    return hostname.includes(".") ? hostname : null;
  } catch {
    return null;
  }
}

function siteIconUrl(domain: string): string {
  return `https://www.google.com/s2/favicons?domain=${encodeURIComponent(domain)}&sz=128`;
}

function AddCustomDialog({
  onAdd,
  onClose,
}: {
  onAdd: (path: string, name: string) => void;
  onClose: () => void;
}) {
  const m = useMessages();
  const [entries, setEntries] = useState<DraftEntry[]>([newDraftEntry()]);
  const timers = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const updatePath = (id: string, value: string) => {
    setEntries((prev) =>
      prev.map((e) => (e.id === id ? { ...e, path: value, iconSrc: null, iconLoading: false } : e)),
    );
    const existing = timers.current.get(id);
    if (existing) clearTimeout(existing);
    const trimmed = value.trim();
    if (!trimmed || isDomainEntry(trimmed)) return;
    const t = setTimeout(async () => {
      setEntries((prev) => prev.map((e) => (e.id === id ? { ...e, iconLoading: true } : e)));
      const icon = await fetchIconThrottled(trimmed).catch(() => null);
      setEntries((prev) => prev.map((e) => (e.id === id ? { ...e, iconSrc: icon, iconLoading: false } : e)));
    }, 450);
    timers.current.set(id, t);
  };

  const updateName = (id: string, value: string) => {
    setEntries((prev) => prev.map((e) => (e.id === id ? { ...e, name: value } : e)));
  };

  const removeEntry = (id: string) => {
    setEntries((prev) => (prev.length === 1 ? prev : prev.filter((e) => e.id !== id)));
  };

  const pickExecutable = async (id: string) => {
    const selected = await api.pickAppExecutable().catch(() => null);
    if (!selected) return;
    setEntries((prev) =>
      prev.map((entry) => {
        if (entry.id !== id) return entry;
        return {
          ...entry,
          path: selected,
          name: entry.name.trim() ? entry.name : deriveName(selected),
          iconSrc: null,
          iconLoading: false,
        };
      }),
    );
    updatePath(id, selected);
  };

  const handleAdd = () => {
    const valid = entries.filter((e) => e.path.trim());
    if (!valid.length) return;
    for (const entry of valid) {
      const v = entry.path.trim();
      onAdd(v, entry.name.trim() || deriveName(v));
    }
    onClose();
  };

  const validCount = entries.filter((e) => e.path.trim()).length;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-5"
      style={{ background: "rgba(0,0,0,0.75)", backdropFilter: "blur(12px)" }}
      role="presentation"
      onClick={onClose}
    >
      <div
        className="panel w-full max-w-lg p-6"
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="mb-5 text-lg font-black text-white">{m.appsPage.addCustom}</h2>

        <div className="mb-3 flex max-h-[55vh] flex-col gap-2.5 overflow-y-auto pr-1">
          {entries.map((entry, idx) => (
            <DraftEntryRow
              key={entry.id}
              entry={entry}
              autoFocus={idx === entries.length - 1}
              showRemove={entries.length > 1}
              onPathChange={(v) => updatePath(entry.id, v)}
              onNameChange={(v) => updateName(entry.id, v)}
              onPickFile={() => void pickExecutable(entry.id)}
              onRemove={() => removeEntry(entry.id)}
              onEnter={handleAdd}
            />
          ))}
        </div>

        <button
          onClick={() => setEntries((prev) => [...prev, newDraftEntry()])}
          className="mb-5 flex items-center gap-1.5 text-sm font-semibold text-[var(--color-text-faint)] transition-colors hover:text-[var(--color-accent-bright)]"
        >
          <PlusIcon />
          Добавить ещё
        </button>

        <div className="flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 rounded-xl border border-[var(--color-border)] py-3 text-[var(--color-text-dim)] transition-colors hover:text-white"
          >
            {m.common.cancel}
          </button>
          <button
            onClick={handleAdd}
            disabled={validCount === 0}
            className="primary-button interactive flex-1 rounded-xl py-3 font-bold disabled:opacity-40"
          >
            {validCount > 1 ? `Добавить ${validCount}` : m.appsPage.customAdd}
          </button>
        </div>
      </div>
    </div>
  );
}

function DraftEntryRow({
  entry,
  autoFocus,
  showRemove,
  onPathChange,
  onNameChange,
  onPickFile,
  onRemove,
  onEnter,
}: {
  entry: DraftEntry;
  autoFocus: boolean;
  showRemove: boolean;
  onPathChange: (v: string) => void;
  onNameChange: (v: string) => void;
  onPickFile: () => void;
  onRemove: () => void;
  onEnter: () => void;
}) {
  const m = useMessages();
  const domain = domainFromTarget(entry.path);
  const isDomain = Boolean(domain);
  const letterBg = entry.path ? appLetterColor(entry.path) : "rgba(255,255,255,0.06)";
  const hasBg = !entry.iconSrc && !isDomain;

  return (
    <div className="flex items-start gap-3">
      {/* Icon preview */}
      <div
        className="mt-0.5 grid h-11 w-11 shrink-0 place-items-center overflow-hidden rounded-xl text-[var(--color-text-faint)] transition-all"
        style={{
          background:
            entry.iconSrc || isDomain
              ? "transparent"
              : entry.iconLoading
                ? "rgba(255,255,255,0.06)"
                : hasBg && entry.path
                  ? letterBg
                  : "rgba(255,255,255,0.06)",
        }}
      >
        {entry.iconSrc ? (
          <img src={entry.iconSrc} alt="" className="h-full w-full object-contain" />
        ) : entry.iconLoading ? (
          <span className="h-5 w-5 animate-pulse rounded-md bg-[rgba(255,255,255,0.12)]" />
        ) : domain ? (
          <SiteIcon domain={domain} />
        ) : entry.path ? (
          <span className="text-base font-black text-white">
            {entry.path.trim().charAt(0).toUpperCase()}
          </span>
        ) : (
          <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="3" y="3" width="18" height="18" rx="3" />
            <path d="M12 8v8M8 12h8" />
          </svg>
        )}
      </div>

      {/* Inputs */}
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <div className="flex min-w-0 gap-2">
          <input
            value={entry.path}
            onChange={(e) => onPathChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") onEnter();
            }}
            placeholder={m.appsPage.customPlaceholder}
            className="dark-input min-w-0 flex-1 px-4 py-2.5 text-sm"
            autoFocus={autoFocus}
          />
          <button
            type="button"
            title={m.appsPage.pickExecutable}
            aria-label={m.appsPage.pickExecutable}
            onClick={onPickFile}
            className="grid h-11 w-11 shrink-0 place-items-center rounded-xl border border-[var(--color-border)] bg-[var(--color-glass-bg)] text-[var(--color-accent-bright)] transition-all hover:border-[var(--color-border-strong)] hover:bg-[var(--color-glass-bg-strong)] hover:text-white active:scale-95"
          >
            <FolderIcon />
          </button>
        </div>
        <input
          value={entry.name}
          onChange={(e) => onNameChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") onEnter();
          }}
          placeholder={m.appsPage.customNamePlaceholder}
          className="dark-input w-full px-4 py-2.5 text-sm"
        />
      </div>

      {/* Remove */}
      {showRemove ? (
        <button
          onClick={onRemove}
          className="mt-0.5 grid h-11 w-7 shrink-0 place-items-center rounded-lg text-[var(--color-text-faint)] transition-colors hover:text-white"
        >
          <XIcon />
        </button>
      ) : (
        <div className="w-7 shrink-0" />
      )}
    </div>
  );
}

function ModeButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={[
        "interactive rounded-2xl border px-5 py-4 text-base font-black",
        active
          ? "border-[var(--color-accent)] bg-[var(--color-glass-bg-strong)] text-[var(--color-accent-bright)] shadow-[0_0_20px_var(--color-glow-accent)]"
          : "border-[var(--color-border)] bg-[var(--color-glass-bg)] text-[var(--color-text-faint)] hover:text-white",
      ].join(" ")}
    >
      {children}
    </button>
  );
}

function appLetterColor(name: string): string {
  const palette = [
    "#7c5dfa",
    "#2563eb",
    "#0891b2",
    "#059669",
    "#d97706",
    "#dc2626",
    "#db2777",
    "#7c3aed",
  ];
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = ((hash * 31) + name.charCodeAt(i)) >>> 0;
  }
  return palette[hash % palette.length];
}

function AppRow({
  app,
  selected,
  hasRule,
  fromSubscription,
  subscriptionMode,
  onClick,
  onDelete,
}: {
  app: InstalledApp;
  selected: boolean;
  hasRule: boolean;
  fromSubscription: boolean;
  subscriptionMode?: AppProxyMode;
  onClick: () => void;
  onDelete: () => void;
}) {
  const m = useMessages();
  const letter = app.name.trim().charAt(0).toUpperCase() || "A";
  const domain = domainFromTarget(app.executable_path);
  const displayPath = domain ?? app.executable_path;
  const letterBg = appLetterColor(app.name);
  const { src: iconSrc, loading: iconLoading, containerRef } = useAppIconLazy(app.executable_path);

  const hasIcon = Boolean(iconSrc);
  const iconBg = hasIcon || domain
    ? "transparent"
    : iconLoading
      ? "rgba(255,255,255,0.06)"
      : letterBg;

  const showTrash = hasRule && !fromSubscription;
  const subscriptionModeLabel = subscriptionMode === "proxy" ? m.appsPage.modeProxy : m.appsPage.modeDirect;

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onClick();
        }
      }}
      className="grid w-full grid-cols-[54px_minmax(0,1fr)_34px_34px] items-center gap-4 px-6 py-4 text-left transition-colors hover:bg-[var(--color-glass-bg)]"
    >
      <div
        ref={containerRef}
        className="relative grid h-11 w-11 place-items-center overflow-hidden rounded-xl text-lg font-black text-white transition-colors"
        style={{ background: iconBg }}
      >
        {iconSrc ? (
          <img src={iconSrc} alt="" className="h-full w-full object-contain" />
        ) : iconLoading ? (
          <span className="h-5 w-5 animate-pulse rounded-md bg-[rgba(255,255,255,0.12)]" />
        ) : domain ? (
          <SiteIcon domain={domain} />
        ) : (
          letter
        )}
      </div>
      <div className="min-w-0">
        <div className="flex min-w-0 items-center gap-2">
          <span className="truncate text-lg font-black text-white">{app.name}</span>
          {fromSubscription && (
            <span
              className="shrink-0 rounded-md border border-[var(--color-accent)]/40 bg-[var(--color-accent)]/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-[var(--color-accent-bright)]"
              title={`${m.appsPage.fromSubscription} · ${subscriptionModeLabel}`}
            >
              {m.appsPage.fromSubscription} · {subscriptionModeLabel}
            </span>
          )}
        </div>
        <div className="truncate font-mono text-sm text-[var(--color-text-faint)]">
          {displayPath}
        </div>
      </div>
      <div
        className={[
          "grid h-7 w-7 place-items-center rounded-lg border-2 transition-all",
          selected
            ? "border-[var(--color-accent)] bg-[var(--color-accent)] shadow-[0_0_16px_var(--color-glow-accent)]"
            : "border-[var(--color-border-strong)]",
        ].join(" ")}
      >
        {selected && (
          <svg viewBox="0 0 12 10" className="h-3.5 w-3.5" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polyline points="1,5 4.5,8.5 11,1" />
          </svg>
        )}
      </div>
      {showTrash ? (
        <button
          type="button"
          title={m.appsPage.deleteRule}
          aria-label={m.appsPage.deleteRule}
          onClick={(event) => {
            event.stopPropagation();
            onDelete();
          }}
          className="grid h-8 w-8 place-items-center rounded-lg text-[var(--color-text-faint)] transition-all hover:bg-[rgba(255,80,80,0.12)] hover:text-[#ff6b6b]"
        >
          <TrashIcon />
        </button>
      ) : (
        <span className="h-8 w-8" />
      )}
    </div>
  );
}

function XIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M18 6 6 18M6 6l12 12" />
    </svg>
  );
}

function FilterChip({
  active,
  onClick,
  disabled,
  children,
}: {
  active: boolean;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={[
        "interactive rounded-xl border px-3 py-2 text-sm font-bold transition-colors",
        active
          ? "border-[var(--color-accent)] bg-[var(--color-glass-bg-strong)] text-[var(--color-accent-bright)] shadow-[0_0_12px_var(--color-glow-accent)]"
          : "border-[var(--color-border)] bg-[var(--color-glass-bg)] text-[var(--color-text-faint)] hover:border-[var(--color-border-strong)] hover:text-white",
        disabled ? "cursor-not-allowed opacity-40 hover:border-[var(--color-border)] hover:text-[var(--color-text-faint)]" : "",
      ].join(" ")}
    >
      {children}
    </button>
  );
}

function SortIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M7 5v14M7 5l-3 3M7 5l3 3" />
      <path d="M17 19V5M17 19l-3-3M17 19l3-3" />
    </svg>
  );
}

function PlusIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M4 7h16" />
      <path d="M10 11v6M14 11v6" />
      <path d="M6 7l1 14h10l1-14" />
      <path d="M9 7V4h6v3" />
    </svg>
  );
}

function ImportFileIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z" />
      <path d="M14 3v5h5" />
      <path d="M12 17V10" />
      <path d="M9 13l3-3 3 3" />
    </svg>
  );
}

function ExportFileIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z" />
      <path d="M14 3v5h5" />
      <path d="M12 10v7" />
      <path d="M9 14l3 3 3-3" />
    </svg>
  );
}

function FolderIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M3 7.5A2.5 2.5 0 0 1 5.5 5H10l2 2h6.5A2.5 2.5 0 0 1 21 9.5v7A2.5 2.5 0 0 1 18.5 19h-13A2.5 2.5 0 0 1 3 16.5v-9Z" />
    </svg>
  );
}

function SiteIcon({ domain }: { domain: string }) {
  const [failed, setFailed] = useState(false);

  return failed ? (
    <GlobeSmallIcon />
  ) : (
    <img
      src={siteIconUrl(domain)}
      alt=""
      className="h-full w-full object-contain"
      onError={() => setFailed(true)}
    />
  );
}

function GlobeSmallIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18" />
      <path d="M12 3a13.5 13.5 0 0 1 0 18" />
      <path d="M12 3a13.5 13.5 0 0 0 0 18" />
    </svg>
  );
}
