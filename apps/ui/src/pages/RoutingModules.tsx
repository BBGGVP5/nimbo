import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api, type RoutingModule } from "../lib/api";
import { useMessages } from "../lib/i18n";
import { Dialog, Surface } from "../components/Universal";
import { PageHeader, StatePanel, Metric, useSecondaryCopy } from "../components/Secondary";

export default function RoutingModules() {
  const m = useMessages(); const copy = useSecondaryCopy(); const ru = m.common.locale.startsWith("ru");
  const [modules, setModules] = useState<RoutingModule[]>([]);
  const [editing, setEditing] = useState<RoutingModule | null>(null);
  const [deleting, setDeleting] = useState<RoutingModule | null>(null);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const reload = useCallback(async () => {
    setLoading(true); setError(null);
    try { setModules(await api.listRoutingModules()); }
    catch (e) { setError(String(e)); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void reload(); }, [reload]);
  const openEditor = (module: RoutingModule) => { setError(null); setEditing(module); setDraft(module.text); };
  const save = async () => {
    if (!editing) return;
    setBusy(true); setError(null);
    try { const parsed = parseModule(draft); setModules(await api.saveRoutingModule(editing.id, parsed.name ?? editing.name, draft)); setEditing(null); }
    catch (e) { setError(String(e)); }
    finally { setBusy(false); }
  };
  const mutate = async (action: () => Promise<RoutingModule[]>) => {
    setBusy(true); setError(null);
    try { setModules(await action()); setDeleting(null); }
    catch (e) { setError(String(e)); }
    finally { setBusy(false); }
  };
  const parsedDraft = useMemo(() => parseModule(draft), [draft]);
  const title = ru ? "Модули маршрутизации" : "Routing modules";
  const newLabel = ru ? "Новый модуль" : "New module";
  return <div className="page-view secondary-page modules-page">
    <PageHeader title={title} description={ru ? "Свои правила поверх активного профиля — без изменения его настроек." : "Your rules on top of the active profile, without changing its settings."} actions={<><Link className="btn" to="/routing">{m.routing.title}</Link><button className="primary-button btn" onClick={() => openEditor({id: `module-${Date.now().toString(36)}`, name: newLabel, enabled: true, text: NEW_MODULE_TEMPLATE})}>{newLabel}</button></>} />
    <div className="secondary-metrics"><Metric label={title} value={loading ? "—" : modules.length} /><Metric label={ru ? "Включены" : "Enabled"} value={loading ? "—" : modules.filter(x => x.enabled).length} /><Metric label={m.routing.rulesLabel} value={loading ? "—" : modules.reduce((n,x) => n + parseModule(x.text).rules, 0)} /></div>
    {error && !editing && !deleting && <StatePanel title={copy.error} detail={error} error action={<button className="btn" onClick={() => void reload()}>{copy.retry}</button>} />}
    {loading ? <StatePanel title={copy.loading} busy /> : modules.length === 0 ? <Surface><StatePanel title={ru ? "Модулей пока нет" : "No modules yet"} detail={ru ? "Добавьте готовый список правил или создайте свой." : "Add an existing rule list or create your own."} action={<button className="btn" onClick={() => openEditor({id: `module-${Date.now().toString(36)}`, name:newLabel, enabled:true, text:NEW_MODULE_TEMPLATE})}>{newLabel}</button>} /></Surface> : <div className="module-list">{modules.map(module => <Surface className="module-card" key={module.id}>
      <div><h2>{parseModule(module.text).name ?? module.name}</h2><p className="secondary-note">{parseModule(module.text).rules} {m.routing.rulesLabel} · {module.enabled ? (ru ? "Включён" : "Enabled") : (ru ? "Выключен" : "Disabled")}</p></div>
      <div className="module-card-actions"><button className="btn" onClick={() => openEditor(module)}>{ru ? "Редактировать" : "Edit"}</button><button className="btn" aria-pressed={module.enabled} disabled={busy} onClick={() => void mutate(() => api.toggleRoutingModule(module.id))}>{module.enabled ? (ru ? "Выключить" : "Disable") : (ru ? "Включить" : "Enable")}</button><button className="btn" disabled={busy} onClick={() => {setError(null); setDeleting(module);}}>{m.routing.delete}</button></div>
    </Surface>)}</div>}
    {editing && <Dialog className="module-editor" title={parsedDraft.name ?? editing.name} closeLabel={m.common.close} onClose={() => setEditing(null)} closeDisabled={busy} footer={<><button className="btn" disabled={busy} onClick={() => setEditing(null)}>{m.common.cancel}</button><button className="primary-button btn" disabled={busy} onClick={() => void save()}>{busy ? m.common.saving : m.common.save}</button></>}>
      <p className="secondary-note">{parsedDraft.rules} {m.routing.rulesLabel} · {parsedDraft.skipped} {ru ? "нераспознанных строк" : "unrecognized lines"}</p>
      <textarea className="dark-input" aria-label={title} value={draft} spellCheck={false} onChange={e => setDraft(e.target.value)} />
      <p className="secondary-note">DOMAIN, DOMAIN-SUFFIX, DOMAIN-KEYWORD, IP-CIDR, GEOIP, GEOSITE · DIRECT / PROXY / REJECT</p>
      {error && <StatePanel title={copy.error} detail={error} error />}
    </Dialog>}
    {deleting && <Dialog title={m.routing.deleteTitle} closeLabel={m.common.close} onClose={() => setDeleting(null)} closeDisabled={busy} footer={<><button className="btn" disabled={busy} onClick={() => setDeleting(null)}>{m.common.cancel}</button><button className="routing-editor-danger" disabled={busy} onClick={() => void mutate(() => api.deleteRoutingModule(deleting.id))}>{m.routing.delete}</button></>}><p>{deleting.name}</p>{error && <StatePanel title={copy.error} detail={error} error />}</Dialog>}
  </div>;
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
