import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import {
  api,
  type AppProxyMode,
  type AppProxyRule,
  type InstalledApp,
} from "../lib/api";
import { useMessages } from "../lib/i18n";

type AppMode = "direct" | "proxy";

export function Applications() {
  const m = useMessages();
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [rules, setRules] = useState<AppProxyRule[]>([]);
  const [mode, setMode] = useState<AppMode>("direct");
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    Promise.all([api.listInstalledApps(), api.listAppProxyRules()])
      .then(([installed, saved]) => {
        setApps(installed);
        setRules(saved);
        const firstEnabled = saved.find((rule) => rule.enabled);
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

  return (
    <div className="page-view">
      <h1 className="page-title">{m.appsPage.title}</h1>

      <div className="mt-7 mb-7 grid max-w-2xl grid-cols-2 gap-3 mobile-stack">
        <ModeButton
          active={mode === "direct"}
          onClick={() => void setAllMode("direct")}
        >
          {m.appsPage.direct}
        </ModeButton>
        <ModeButton
          active={mode === "proxy"}
          onClick={() => void setAllMode("proxy")}
        >
          {m.appsPage.proxy}
        </ModeButton>
      </div>

      <p className="page-subtitle mb-7">
        {mode === "direct"
          ? m.appsPage.directDescription
          : m.appsPage.proxyDescription}
      </p>

      <input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder={m.appsPage.search}
        className="dark-input mb-6 px-5 py-4 text-lg"
      />

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

  return (
    <button
      onClick={onClick}
      className="grid w-full grid-cols-[54px_1fr_34px] items-center gap-4 px-6 py-4 text-left transition-colors hover:bg-[var(--color-glass-bg)]"
    >
      <div className="grid h-11 w-11 place-items-center rounded-xl bg-[var(--color-glass-bg-strong)] text-lg font-black text-[var(--color-accent-bright)]">
        {letter}
      </div>
      <div className="min-w-0">
        <div className="truncate text-lg font-black text-white">{app.name}</div>
        <div className="truncate font-mono text-sm text-[var(--color-text-faint)]">
          {app.executable_path}
        </div>
      </div>
      <div
        className={[
          "h-7 w-7 rounded-lg border-2",
          selected
            ? "border-[var(--color-accent)] bg-[var(--color-accent)] shadow-[0_0_20px_var(--color-glow-accent)]"
            : "border-[var(--color-border-strong)]",
        ].join(" ")}
      />
    </button>
  );
}
