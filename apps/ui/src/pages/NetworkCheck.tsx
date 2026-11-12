import { useEffect, useMemo, useRef, useState } from "react";
import { PageHeader } from "../components/Secondary";
import { Surface } from "../components/Universal";
import { api, type NetworkCheckItem, type NetworkCheckReport, type NetworkPingReport } from "../lib/api";
import { useMessages } from "../lib/i18n";
import "./network-check.css";

type Tool = "check" | "ping" | "speed";
type Tone = "ok" | "warn" | "bad" | "idle";

const GROUPS: Array<NetworkCheckItem["group"]> = ["international", "local", "telemetry", "infra"];
const QUICK_TARGETS = ["google.com", "ya.ru", "1.1.1.1", "8.8.8.8", "telegram.org"];
const TOOL_KEY = "nimbo.networkCheck.tool";
const TARGET_KEY = "nimbo.networkCheck.pingTarget";

function readStored(key: string, fallback: string) {
  try { return localStorage.getItem(key) || fallback; } catch { return fallback; }
}
function store(key: string, value: string) {
  try { localStorage.setItem(key, value); } catch { /* preview / blocked storage */ }
}

export function NetworkCheck() {
  const ru = useMessages().common.locale.startsWith("ru");
  const t = (r: string, e: string) => (ru ? r : e);
  const [tool, setTool] = useState<Tool>(() => {
    const saved = readStored(TOOL_KEY, "check");
    return saved === "ping" || saved === "speed" ? saved : "check";
  });
  const pick = (next: Tool) => { setTool(next); store(TOOL_KEY, next); };
  const tabs: Array<[Tool, string]> = [["check", t("Проверка", "Check")], ["ping", t("Пинг", "Ping")], ["speed", t("Скорость", "Speed")]];
  return (
    <div className="page-view secondary-page network-check-page">
      <PageHeader title={t("Проверка сети", "Network check")}
        description={t("Белые списки, пинг и скорость через текущее подключение.", "Allowlists, ping and speed through the current connection.")} />
      <div className="network-tabs" role="tablist" aria-label={t("Инструменты", "Tools")}>
        {tabs.map(([key, label]) => (
          <button key={key} type="button" role="tab" aria-selected={tool === key} data-active={tool === key}
            className="network-tab" onClick={() => pick(key)}>{label}</button>
        ))}
      </div>
      {tool === "check" && <CheckPane t={t} />}
      {tool === "ping" && <PingPane t={t} />}
      {tool === "speed" && <SpeedPane t={t} />}
    </div>
  );
}

type T = (ru: string, en: string) => string;

function Hero({ eyebrow, title, detail, tone, busy, children }: {
  eyebrow: string; title: string; detail?: string; tone: Tone; busy?: boolean; children?: React.ReactNode;
}) {
  return (
    <Surface className="network-hero" data-tone={tone} aria-live="polite">
      <div className="network-eyebrow"><span className="network-dot" data-busy={busy || undefined} />{eyebrow}</div>
      <h2 className="network-hero-title">{title}</h2>
      {detail && <p className="network-hero-detail">{detail}</p>}
      {children}
    </Surface>
  );
}

function Stat({ label, value, unit }: { label: string; value: string; unit: string }) {
  return (
    <div className="network-stat">
      <span>{label}</span>
      <strong>{value}<small>{unit}</small></strong>
    </div>
  );
}

function CheckPane({ t }: { t: T }) {
  const [report, setReport] = useState<NetworkCheckReport | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [checkedAt, setCheckedAt] = useState<Date | null>(null);
  const [copied, setCopied] = useState(false);
  const started = useRef(false);

  const run = async () => {
    if (busy) return;
    setBusy(true); setError(null);
    try {
      setReport(await api.networkCheckRun());
      setCheckedAt(new Date());
    } catch (e) {
      setError(String(e));
    } finally { setBusy(false); }
  };
  useEffect(() => { if (!started.current) { started.current = true; void run(); } }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const groupName: Record<NetworkCheckItem["group"], string> = {
    international: t("Международные", "International"), local: t("Локальные", "Local"),
    telemetry: t("Статистика", "Analytics"), infra: t("Инфраструктура", "Infrastructure"),
  };

  const verdict = useMemo((): { title: string; detail: string; tone: Tone } => {
    if (busy) return { title: t("Проверяем…", "Checking…"), detail: t("Опрашиваем контрольные адреса", "Probing reference hosts"), tone: "idle" };
    if (error) return { title: t("Не удалось проверить", "Check failed"), detail: error, tone: "bad" };
    if (!report) return { title: t("Не проверено", "Not checked"), detail: "", tone: "idle" };
    const items = report.items;
    const ok = items.filter((i) => i.available).length;
    const has = (g: NetworkCheckItem["group"]) => items.some((i) => i.group === g && i.available);
    const counts = t(`Доступно ${ok} из ${items.length} адресов`, `${ok} of ${items.length} hosts reachable`);
    if (ok === 0) return { title: t("Нет интернета", "No internet"), detail: t("Ни один контрольный адрес не ответил.", "No reference host answered."), tone: "bad" };
    if (has("international") && has("local") && ok / items.length >= 0.7) return { title: t("Сеть в норме", "Network is fine"), detail: counts, tone: "ok" };
    if (!has("international") && has("local")) return { title: t("Белые списки", "Allowlist only"), detail: t("Открываются только локальные сайты — похоже на ограничение по белому списку.", "Only local sites open — looks like an allowlist restriction."), tone: "warn" };
    return { title: t("Ограниченный доступ", "Limited access"), detail: counts, tone: "warn" };
  }, [busy, error, report]); // eslint-disable-line react-hooks/exhaustive-deps

  const copyReport = async () => {
    if (!report) return;
    const lines = [`Nimbo — ${t("проверка сети", "network check")}`, verdict.title, checkedAt?.toLocaleString() ?? "",
      ...report.items.map((i) => `${i.available ? "✓" : "✗"} ${i.id}: ${i.available ? `${i.elapsed_ms} ms` : i.detail}`)];
    try { await navigator.clipboard.writeText(lines.join("\n")); setCopied(true); setTimeout(() => setCopied(false), 1600); } catch { /* clipboard blocked */ }
  };

  return (
    <>
      <Hero eyebrow={t("Состояние сети", "Network status")} title={verdict.title} detail={verdict.detail} tone={verdict.tone} busy={busy}>
        <div className="network-hero-foot">
          {report && !busy && <span>{report.via_proxy ? t("Через прокси Nimbo", "Through Nimbo proxy") : t("Через системный маршрут", "Through the system route")}{checkedAt && ` · ${checkedAt.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`}</span>}
          <div className="network-actions">
            {report && !busy && <button type="button" className="btn" onClick={copyReport}>{copied ? t("Скопировано", "Copied") : t("Скопировать отчёт", "Copy report")}</button>}
            <button type="button" className="btn primary-button" onClick={run} disabled={busy}>{busy ? t("Проверяем…", "Checking…") : report ? t("Проверить снова", "Check again") : t("Проверить", "Check")}</button>
          </div>
        </div>
      </Hero>
      <div className="network-groups">
        {GROUPS.map((group) => {
          const rows = report?.items.filter((i) => i.group === group) ?? [];
          return (
            <Surface className="network-group" key={group}>
              <div className="network-eyebrow">{groupName[group]}</div>
              {rows.length === 0 ? <p className="network-empty">{busy ? "…" : "—"}</p> : (
                <ul>
                  {rows.map((i) => (
                    <li key={i.id} data-ok={i.available}>
                      <span className="network-dot" data-tone={i.available ? "ok" : "bad"} />
                      <span className="network-row-name">{i.id}</span>
                      <span className="network-row-value">{i.available ? `${i.elapsed_ms} ${t("мс", "ms")}` : i.detail}</span>
                    </li>
                  ))}
                </ul>
              )}
            </Surface>
          );
        })}
      </div>
      <p className="secondary-note">{t("Это доступность контрольных адресов на этом компьютере. Проверка не доказывает доступность всех приложений или отсутствие блокировки на другом маршруте.",
        "This is the reachability of reference hosts on this computer. It does not prove every app works or that another route is unblocked.")}</p>
    </>
  );
}

function PingPane({ t }: { t: T }) {
  const [target, setTarget] = useState(() => readStored(TARGET_KEY, "google.com"));
  const [report, setReport] = useState<NetworkPingReport | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const received = (report?.samples ?? []).filter((s): s is number => s !== null);
  const avg = received.length ? Math.round(received.reduce((a, b) => a + b, 0) / received.length) : null;
  const jitter = received.length > 1 ? Math.round(received.slice(1).reduce((sum, v, i) => sum + Math.abs(v - received[i]), 0) / (received.length - 1)) : null;
  const loss = report ? Math.round(((report.samples.length - received.length) * 100) / report.samples.length) : null;
  const tone: Tone = busy || !report ? (error ? "bad" : "idle") : avg === null || (loss ?? 0) > 20 || avg > 300 ? "bad" : (loss ?? 0) > 0 || avg > 120 ? "warn" : "ok";

  const run = async (value = target) => {
    if (busy || !value.trim()) return;
    store(TARGET_KEY, value.trim());
    setBusy(true); setError(null);
    try { setReport(await api.networkPing(value)); } catch (e) { setError(String(e)); setReport(null); } finally { setBusy(false); }
  };

  return (
    <>
      <Hero eyebrow={t("Задержка · TCP 443", "Latency · TCP 443")}
        title={busy ? t("Измеряем…", "Measuring…") : avg !== null ? `${avg} ${t("мс", "ms")}` : report ? t("Нет ответа", "No response") : "—"}
        detail={error ?? (report ? `${report.host} · ${received.length} ${t("из", "of")} ${report.samples.length} ${t("ответов", "replies")}` : t("Время TCP-рукопожатия с адресом через текущий маршрут.", "TCP handshake time to the host through the current route."))}
        tone={tone} busy={busy}>
        {report && (
          <div className="network-stats">
            <Stat label={t("Мин", "Min")} value={received.length ? String(Math.min(...received)) : "—"} unit={t("мс", "ms")} />
            <Stat label={t("Джиттер", "Jitter")} value={jitter === null ? "—" : String(jitter)} unit={t("мс", "ms")} />
            <Stat label={t("Потери", "Loss")} value={loss === null ? "—" : String(loss)} unit="%" />
          </div>
        )}
      </Hero>
      <Surface className="network-ping-form">
        <form onSubmit={(e) => { e.preventDefault(); void run(); }}>
          <label className="network-eyebrow" htmlFor="network-ping-target">{t("Адрес", "Host")}</label>
          <div className="network-ping-row">
            <input id="network-ping-target" className="network-input" value={target} spellCheck={false} autoComplete="off"
              placeholder={t("Домен или IP-адрес", "Domain or IP address")} onChange={(e) => setTarget(e.target.value)} />
            <button type="submit" className="btn primary-button" disabled={busy || !target.trim()}>{busy ? t("Измеряем…", "Measuring…") : t("Запустить", "Run")}</button>
          </div>
          <div className="network-eyebrow">{t("Быстрый выбор", "Quick pick")}</div>
          <div className="network-chips">
            {QUICK_TARGETS.map((host) => (
              <button key={host} type="button" className="network-chip" aria-pressed={target.trim() === host}
                onClick={() => { setTarget(host); void run(host); }} disabled={busy}>{host}</button>
            ))}
          </div>
        </form>
        {report && (
          <ol className="network-samples">
            {report.samples.map((s, i) => (
              <li key={i} data-lost={s === null || undefined}><span>#{i + 1}</span><span>{s === null ? t("нет ответа", "no reply") : `${s} ${t("мс", "ms")}`}</span></li>
            ))}
          </ol>
        )}
      </Surface>
    </>
  );
}

type Phase = "idle" | "latency" | "download" | "upload" | "done" | "failed";

function SpeedPane({ t }: { t: T }) {
  const [phase, setPhase] = useState<Phase>("idle");
  const [latency, setLatency] = useState<number | null>(null);
  const [down, setDown] = useState<number | null>(null);
  const [up, setUp] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const run = useRef(0);
  const running = phase === "latency" || phase === "download" || phase === "upload";
  const headline = phase === "upload" ? up : down ?? up;
  const tone: Tone = phase === "failed" ? "bad" : phase !== "done" || down === null ? "idle" : down >= 20 ? "ok" : down >= 5 ? "warn" : "bad";
  const phaseLabel: Record<Phase, string> = {
    idle: t("Скорость", "Speed"), latency: t("Пинг", "Latency"), download: t("Загрузка", "Download"),
    upload: t("Отдача", "Upload"), done: t("Готово", "Done"), failed: t("Ошибка", "Error"),
  };

  const start = async () => {
    const id = ++run.current;
    setLatency(null); setDown(null); setUp(null); setError(null);
    const step = async (stage: "latency" | "download" | "upload", set: (v: number) => void) => {
      setPhase(stage);
      const value = await api.networkSpeed(stage);
      if (run.current !== id) throw new Error("cancelled");
      set(value);
    };
    try {
      await step("latency", setLatency);
      await step("download", setDown);
      await step("upload", setUp);
      setPhase("done");
    } catch (e) {
      if (run.current !== id) return;
      setError(String(e)); setPhase("failed");
    }
  };
  const stop = () => { run.current++; setPhase("idle"); };

  return (
    <>
      <Hero eyebrow={phaseLabel[phase]} tone={tone} busy={running}
        title={headline !== null ? `${headline.toFixed(1)} ${t("Мбит/с", "Mbit/s")}` : running ? t("Измеряем…", "Measuring…") : "—"}
        detail={error ?? (phase === "idle" ? t("Загрузка и отдача через текущий маршрут, включая VPN.", "Download and upload through the current route, VPN included.") : undefined)}>
        <Gauge value={headline ?? 0} active={running} />
        <div className="network-stats">
          <Stat label={t("Загрузка", "Download")} value={down === null ? "—" : down.toFixed(1)} unit={t("Мбит/с", "Mbit/s")} />
          <Stat label={t("Отдача", "Upload")} value={up === null ? "—" : up.toFixed(1)} unit={t("Мбит/с", "Mbit/s")} />
          <Stat label={t("Пинг", "Latency")} value={latency === null ? "—" : String(Math.round(latency))} unit={t("мс", "ms")} />
        </div>
        <div className="network-actions">
          {running
            ? <button type="button" className="btn" onClick={stop}>{t("Остановить", "Stop")}</button>
            : <button type="button" className="btn primary-button" onClick={start}>{phase === "idle" ? t("Начать тест", "Start test") : t("Повторить", "Run again")}</button>}
        </div>
      </Hero>
      <p className="secondary-note">{t("Тест расходует около 33 МБ (25 МБ загрузки и 8 МБ отдачи) через speed.cloudflare.com.",
        "The test uses about 33 MB (25 MB down, 8 MB up) via speed.cloudflare.com.")}</p>
    </>
  );
}

function Gauge({ value, active }: { value: number; active: boolean }) {
  const max = [50, 100, 250, 500, 1000].find((m) => value <= m * 0.9) ?? 1000;
  const share = Math.min(value / max, 1);
  const r = 80, length = Math.PI * r;
  return (
    <div className="network-gauge" data-active={active || undefined} aria-hidden="true">
      <svg viewBox="0 0 200 110">
        <path d="M20 100 A80 80 0 0 1 180 100" className="network-gauge-track" />
        <path d="M20 100 A80 80 0 0 1 180 100" className="network-gauge-fill" strokeDasharray={length} strokeDashoffset={length * (1 - share)} />
      </svg>
      <div className="network-gauge-scale"><span>0</span><span>{max}</span></div>
    </div>
  );
}
