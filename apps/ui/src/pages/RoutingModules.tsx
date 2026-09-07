import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { api, type RoutingModule } from "../lib/api";

/**
 * Модули маршрутизации: наборы правил, написанные пользователем.
 *
 * Экран намеренно про текст, а не про конструктор: наборы приносят готовыми
 * из других приложений и правят целиком, а построчный редактор заставлял бы
 * вбивать сотню правил по одному.
 */
export default function RoutingModules() {
  const [modules, setModules] = useState<RoutingModule[]>([]);
  const [editing, setEditing] = useState<RoutingModule | null>(null);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);

  const reload = useCallback(async () => {
    try {
      setModules(await api.listRoutingModules());
    } catch {
      setModules([]);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const openEditor = (module: RoutingModule) => {
    setEditing(module);
    setDraft(module.text);
  };

  const save = async () => {
    if (!editing) return;
    setBusy(true);
    try {
      const parsed = parseModule(draft);
      setModules(await api.saveRoutingModule(editing.id, parsed.name ?? editing.name, draft));
      setEditing(null);
    } finally {
      setBusy(false);
    }
  };

  const parsedDraft = useMemo(() => parseModule(draft), [draft]);

  if (editing) {
    return (
      <div className="page-surface glass rounded-2xl h-full overflow-auto p-8">
        <div className="mb-5 flex items-center gap-3">
          <button type="button" className="btn" onClick={() => setEditing(null)}>
            ← Модули
          </button>
          <div className="min-w-0 flex-1">
            <h1 className="page-title truncate">{parsedDraft.name ?? editing.name}</h1>
            <p className="text-sm text-[var(--color-text-dim)]">
              {parsedDraft.rules} правил разобрано
              {parsedDraft.skipped > 0 ? ` · ${parsedDraft.skipped} строк не понято` : ""}
            </p>
          </div>
          <button type="button" className="primary-button btn" disabled={busy} onClick={() => void save()}>
            Сохранить
          </button>
        </div>

        {/* Моноширинный шрифт: правила читаются столбцами, пропорциональный
            превращает их в кашу. */}
        <textarea
          className="dark-input h-[420px] w-full font-mono text-sm leading-6"
          value={draft}
          spellCheck={false}
          onChange={(event) => setDraft(event.target.value)}
        />
        <p className="mt-3 text-xs text-[var(--color-text-faint)]">
          Поддерживаются DOMAIN, DOMAIN-SUFFIX, DOMAIN-KEYWORD, IP-CIDR, GEOIP и GEOSITE с политиками
          DIRECT, PROXY и REJECT. Секция [General] пропускается: её настройки относятся к другому движку.
        </p>
      </div>
    );
  }

  return (
    <div className="page-surface glass rounded-2xl h-full overflow-auto p-8">
      <div className="mb-5 flex items-center gap-3">
        <Link className="btn" to="/routing">
          ← Маршрутизация
        </Link>
        <div className="min-w-0 flex-1">
          <h1 className="page-title">Модули</h1>
          <p className="text-sm text-[var(--color-text-dim)]">
            Свои правила поверх профиля: домены и адреса, которые всегда идут напрямую, через VPN или в блок.
          </p>
        </div>
        <button
          type="button"
          className="primary-button btn"
          onClick={() =>
            openEditor({
              id: `module-${Date.now().toString(36)}`,
              name: "Мой модуль",
              enabled: true,
              text: NEW_MODULE_TEMPLATE,
            })
          }
        >
          + Новый модуль
        </button>
      </div>

      {modules.length === 0 ? (
        <div className="panel p-6">
          <h2 className="mb-2 text-lg font-bold">Модулей пока нет</h2>
          <p className="text-sm text-[var(--color-text-dim)]">
            Вставьте набор правил вида <code>DOMAIN-SUFFIX,ozon.ru,DIRECT</code> — подойдёт готовый список
            из другого приложения. Правила модуля применяются раньше правил профиля.
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {modules.map((module) => {
            const parsed = parseModule(module.text);
            return (
              <div key={module.id} className="panel flex items-center gap-4 p-4">
                <button
                  type="button"
                  className="min-w-0 flex-1 text-left"
                  onClick={() => openEditor(module)}
                >
                  <div className="truncate text-base font-bold">{parsed.name ?? module.name}</div>
                  <div
                    className={`text-xs ${
                      parsed.skipped > 0
                        ? "text-[var(--color-accent-bright)]"
                        : "text-[var(--color-text-faint)]"
                    }`}
                  >
                    {parsed.rules} правил
                    {parsed.skipped > 0 ? ` · ${parsed.skipped} строк не понято` : ""}
                    {module.enabled ? "" : " · выключен"}
                  </div>
                </button>
                <button
                  type="button"
                  className="btn"
                  onClick={async () => setModules(await api.toggleRoutingModule(module.id))}
                >
                  {module.enabled ? "Выключить" : "Включить"}
                </button>
                <button
                  type="button"
                  className="btn"
                  onClick={async () => setModules(await api.deleteRoutingModule(module.id))}
                >
                  Удалить
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

/**
 * Лёгкий разбор для подписей.
 *
 * Настоящий разбор живёт в ядре на Rust — здесь нужно лишь показать, сколько
 * правил получится и сколько строк не понято, не гоняя ради этого команду.
 */
function parseModule(text: string): { name: string | null; rules: number; skipped: number } {
  let name: string | null = null;
  let inRules = false;
  let rules = 0;
  let skipped = 0;

  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) continue;
    if (/^#!name=/i.test(line)) {
      name = line.slice(line.indexOf("=") + 1).trim() || null;
      continue;
    }
    if (line.startsWith("#") || line.startsWith("//") || line.startsWith(";")) continue;
    if (line.startsWith("[")) {
      inRules = line.toLowerCase() === "[rule]";
      continue;
    }
    if (!inRules) continue;
    if (isSupportedRule(line)) rules += 1;
    else skipped += 1;
  }

  return { name, rules, skipped };
}

const SUPPORTED_KINDS = [
  "DOMAIN",
  "DOMAIN-SUFFIX",
  "DOMAIN-KEYWORD",
  "IP-CIDR",
  "IP-CIDR6",
  "IP6-CIDR",
  "GEOIP",
  "GEOSITE",
  "RULE-SET",
];

const SUPPORTED_POLICIES = ["DIRECT", "PROXY", "REJECT", "REJECT-DROP", "REJECT-TINYGIF", "BLOCK"];

function isSupportedRule(line: string): boolean {
  const parts = line.split(",").map((part) => part.trim());
  if (parts.length < 2) return false;
  const kind = parts[0].toUpperCase();
  const policy = (parts[2] ?? parts[1] ?? "").toUpperCase();
  return SUPPORTED_KINDS.includes(kind) && SUPPORTED_POLICIES.includes(policy);
}

/** Заготовка нового модуля: формат виден сразу, искать пример не нужно. */
const NEW_MODULE_TEMPLATE = `#!name=Мой модуль
#!desc=Свои правила маршрутизации

[Rule]
DOMAIN-SUFFIX,ozon.ru,DIRECT
DOMAIN-KEYWORD,analytics,REJECT
GEOIP,ru,DIRECT
`;
