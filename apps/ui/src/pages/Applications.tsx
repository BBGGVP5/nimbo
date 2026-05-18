import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import {
  api,
  type AppProxyMode,
  type AppProxyRule,
  type InstalledApp,
} from "../lib/api";
import { useMessages } from "../lib/i18n";

type AppMode = "direct" | "proxy";
const DOMAIN_ENTRY_PREFIX = "__domain__:";

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
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [rules, setRules] = useState<AppProxyRule[]>([]);
  const [mode, setMode] = useState<AppMode>("direct");
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const [showAddCustom, setShowAddCustom] = useState(false);

  useEffect(() => {
    Promise.all([api.listInstalledApps(), api.listAppProxyRules()])
      .then(([installed, saved]) => {
        const normalizedSaved = normalizeSavedRules(saved);
        setApps(mergeInstalledWithSavedRules(installed, normalizedSaved));
        setRules(normalizedSaved);
        if (normalizedSaved.some((rule, index) => rule.executable_path !== saved[index]?.executable_path)) {
          void api.setAppProxyRules(normalizedSaved);
        }
        const firstEnabled = normalizedSaved.find((rule) => rule.enabled);
        if (firstEnabled) setMode(firstEnabled.mode === "proxy" ? "proxy" : "direct");
      })
      .catch(() => {
        setApps([]);
        setRules([]);
      });
  }, []);

  const selectedPaths = useMemo(
    () => new Set(rules.filter((rule) => rule.enabled).map((rule) => rule.executable_path)),
    [rules],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const source = q
      ? apps.filter(
          (app) =>
            app.name.toLowerCase().includes(q) ||
            app.executable_path.toLowerCase().includes(q),
        )
      : apps;
    return source.slice(0, 180);
  }, [apps, query]);

  const setAllMode = async (next: AppMode) => {
    setMode(next);
    const updated = rules.map((rule) => ({ ...rule, mode: next as AppProxyMode }));
    setRules(updated);
    await save(updated);
  };

  const toggleApp = async (app: InstalledApp) => {
    const existing = rules.find((rule) => rule.executable_path === app.executable_path);
    const updated = existing
      ? rules.map((rule) =>
          rule.id === existing.id ? { ...rule, enabled: !rule.enabled, mode } : rule,
        )
      : [
          ...rules,
          {
            id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            name: app.name,
            executable_path: app.executable_path,
            mode,
            enabled: true,
          },
        ];

    setRules(updated);
    await save(updated);
  };

  const save = async (next: AppProxyRule[]) => {
    setBusy(true);
    try {
      await api.setAppProxyRules(next);
    } catch (e) {
      console.error(e);
    } finally {
      setBusy(false);
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

      <div className="mb-4 flex items-center gap-3">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={m.appsPage.search}
          className="dark-input flex-1 px-5 py-4 text-lg"
        />
        <button
          onClick={() => setShowAddCustom(true)}
          className="interactive flex shrink-0 items-center gap-2 rounded-2xl border border-[var(--color-border)] bg-[var(--color-glass-bg)] px-4 py-4 text-sm font-semibold text-[var(--color-text-dim)] hover:border-[var(--color-border-strong)] hover:text-white"
        >
          <PlusIcon />
          <span>{m.appsPage.addCustom}</span>
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
            {filtered.map((app) => (
              <AppRow
                key={`${app.name}-${app.executable_path}`}
                app={app}
                selected={selectedPaths.has(app.executable_path)}
                onClick={() => void toggleApp(app)}
              />
            ))}
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

function mergeInstalledWithSavedRules(
  installed: InstalledApp[],
  rules: AppProxyRule[],
): InstalledApp[] {
  const byPath = new Map<string, InstalledApp>();
  for (const app of installed) byPath.set(app.executable_path, app);
  for (const rule of rules) {
    if (!byPath.has(rule.executable_path)) {
      byPath.set(rule.executable_path, {
        name: rule.name.trim() || deriveName(rule.executable_path),
        executable_path: rule.executable_path,
      });
    }
  }
  return [...byPath.values()];
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
  onClick,
}: {
  app: InstalledApp;
  selected: boolean;
  onClick: () => void;
}) {
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

  return (
    <button
      onClick={onClick}
      className="grid w-full grid-cols-[54px_1fr_34px] items-center gap-4 px-6 py-4 text-left transition-colors hover:bg-[var(--color-glass-bg)]"
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
        <div className="truncate text-lg font-black text-white">{app.name}</div>
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
    </button>
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
