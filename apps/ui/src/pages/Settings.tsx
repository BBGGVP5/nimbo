import { useEffect, useState } from "react";
import { api, type DeviceInfo } from "../lib/api";

const HAPP_UA = "Happ/2.0.0";

type UaMode = "default" | "happ" | "custom";

function detectMode(override: string | null, defaultUa: string): UaMode {
  if (!override) return "default";
  if (override === defaultUa) return "default";
  if (override === HAPP_UA) return "happ";
  return "custom";
}

export function Settings() {
  const [device, setDevice] = useState<DeviceInfo | null>(null);
  const [override, setOverride] = useState<string | null>(null);
  const [mode, setMode] = useState<UaMode>("default");
  const [customUa, setCustomUa] = useState("");
  const [copied, setCopied] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [savingUa, setSavingUa] = useState(false);

  useEffect(() => {
    Promise.all([api.getDeviceInfo(), api.getUserAgentOverride()])
      .then(([d, ov]) => {
        setDevice(d);
        setOverride(ov);
        const m = detectMode(ov, d.user_agent);
        setMode(m);
        if (m === "custom" && ov) setCustomUa(ov);
      })
      .catch(() => setDevice(null));
  }, []);

  const onCopyHwid = async () => {
    if (!device) return;
    try {
      await navigator.clipboard.writeText(device.hwid);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* ignore */
    }
  };

  const onResetHwid = async () => {
    if (
      !confirm(
        "Сбросить идентификатор? Файл-кеш удалится; новый HWID применится после перезапуска приложения. На стороне панели может понадобиться удалить старое устройство.",
      )
    ) {
      return;
    }
    setResetting(true);
    try {
      const next = await api.resetDeviceId();
      setDevice(next);
    } catch (e) {
      alert(String(e));
    } finally {
      setResetting(false);
    }
  };

  const applyUa = async (next: UaMode, customValue?: string) => {
    if (!device) return;
    setSavingUa(true);
    try {
      let value: string | null = null;
      if (next === "happ") value = HAPP_UA;
      else if (next === "custom")
        value = (customValue ?? customUa).trim() || null;
      else value = null;
      await api.setUserAgentOverride(value);
      setOverride(value);
      setMode(next);
    } catch (e) {
      alert(String(e));
    } finally {
      setSavingUa(false);
    }
  };

  const effectiveUa =
    mode === "default"
      ? (device?.user_agent ?? "—")
      : mode === "happ"
        ? HAPP_UA
        : customUa.trim() || "(пусто — будет Default)";

  return (
    <div className="glass rounded-2xl h-full p-10 overflow-auto">
      <div className="max-w-3xl mx-auto">
        <h1 className="text-2xl font-semibold mb-1">Настройки</h1>
        <p className="text-sm text-[var(--color-text-dim)] mb-8">
          Поведение приложения и сети.
        </p>

        <div className="space-y-3">
          <Group title="Устройство">
            <div className="px-5 py-4 space-y-3">
              <Field label="HWID" mono>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs truncate flex-1">
                    {device?.hwid ?? "—"}
                  </span>
                  <button
                    onClick={onCopyHwid}
                    disabled={!device}
                    className="px-2.5 py-1 rounded-md text-[11px] bg-[var(--color-glass-bg)] hover:bg-[var(--color-glass-bg-strong)] transition-colors disabled:opacity-40"
                  >
                    {copied ? "✓" : "Копировать"}
                  </button>
                  <button
                    onClick={onResetHwid}
                    disabled={!device || resetting}
                    className="px-2.5 py-1 rounded-md text-[11px] bg-[var(--color-glass-bg)] hover:bg-[rgba(244,67,54,0.2)] hover:text-[var(--color-status-error)] transition-colors disabled:opacity-40"
                  >
                    Сбросить
                  </button>
                </div>
              </Field>
              <Field label="ОС" mono>
                {device ? `${device.os} · ${device.os_version}` : "—"}
              </Field>
              <Field label="Hostname" mono>
                {device?.hostname ?? "—"}
              </Field>
            </div>
          </Group>

          <Group title="User-Agent">
            <div className="px-5 py-4 space-y-4">
              <p className="text-xs text-[var(--color-text-dim)] leading-relaxed">
                Remnawave-панель решает что отдать в подписке по заголовку User-Agent.
                По умолчанию шлём <span className="font-mono">Nimbo/…</span> — для
                него нужно своё правило в панели. Если такого правила нет — можно
                замаскироваться под Happ (правило для Happ обычно есть в любой
                стандартной конфигурации).
              </p>

              <div className="space-y-2">
                <UaOption
                  selected={mode === "default"}
                  disabled={savingUa}
                  onClick={() => applyUa("default")}
                  title="Default (Nimbo)"
                  subtitle={device?.user_agent ?? ""}
                />
                <UaOption
                  selected={mode === "happ"}
                  disabled={savingUa}
                  onClick={() => applyUa("happ")}
                  title="Маскировка под Happ"
                  subtitle={HAPP_UA}
                />
                <UaOption
                  selected={mode === "custom"}
                  disabled={savingUa}
                  onClick={() => applyUa("custom", customUa)}
                  title="Свой User-Agent"
                  subtitle={mode === "custom" ? "введите ниже" : "ручной ввод"}
                />
              </div>

              {mode === "custom" && (
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={customUa}
                    onChange={(e) => setCustomUa(e.target.value)}
                    placeholder="MyClient/1.0"
                    className="flex-1 px-3 py-2 rounded-lg bg-[var(--color-glass-bg)] border border-[var(--color-border)] text-sm font-mono focus:outline-none focus:border-[var(--color-accent)] focus:bg-[var(--color-glass-bg-strong)] transition-colors"
                  />
                  <button
                    disabled={savingUa || customUa.trim() === (override ?? "")}
                    onClick={() => applyUa("custom", customUa)}
                    className="px-3 py-2 rounded-lg text-xs bg-[var(--color-accent)] hover:bg-[var(--color-accent-bright)] text-white font-medium transition-colors disabled:opacity-40"
                  >
                    Применить
                  </button>
                </div>
              )}

              <div className="text-[10px] uppercase tracking-wider text-[var(--color-text-faint)] mb-1">
                Будет отправлено
              </div>
              <div className="font-mono text-xs px-3 py-2 rounded-lg bg-[var(--color-glass-bg)] truncate">
                {effectiveUa}
              </div>
            </div>
          </Group>

          <Group title="Подключение">
            <Row label="Режим туннеля" value="TUN (через Windows Service)" />
            <Row label="DNS" value="1.1.1.1, 1.0.0.1" />
            <Row label="Kill switch" value="Выключен" />
          </Group>

          <Group title="Подписки">
            <Row label="Авто-обновление" value="Каждые 12 часов" />
          </Group>

          <Group title="Приложение">
            <Row label="Запуск с Windows" value="Выключен" />
            <Row label="Свернуть в трей" value="Включён" />
          </Group>
        </div>
      </div>
    </div>
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
        "w-full text-left px-4 py-3 rounded-xl transition-all flex items-center gap-3",
        selected
          ? "bg-[var(--color-glass-bg-strong)] ring-1 ring-[var(--color-accent)] shadow-[0_0_20px_var(--color-glow-accent)]"
          : "bg-[var(--color-glass-bg)] hover:bg-[var(--color-glass-bg-strong)]",
        "disabled:opacity-40 disabled:cursor-not-allowed",
      ].join(" ")}
    >
      <span
        className={[
          "w-3 h-3 rounded-full shrink-0 border-2",
          selected
            ? "bg-[var(--color-accent)] border-[var(--color-accent-bright)]"
            : "border-[var(--color-text-faint)]",
        ].join(" ")}
      />
      <span className="min-w-0 flex-1">
        <span className="block text-sm">{title}</span>
        <span className="block text-[11px] text-[var(--color-text-faint)] font-mono truncate">
          {subtitle}
        </span>
      </span>
    </button>
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="glass rounded-2xl overflow-hidden">
      <div className="px-5 py-3 border-b border-[var(--color-border)] text-[10px] uppercase tracking-wider text-[var(--color-text-faint)]">
        {title}
      </div>
      <div className="divide-y divide-[var(--color-border)]">{children}</div>
    </section>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between px-5 py-3 text-sm">
      <span>{label}</span>
      <span className="text-[var(--color-text-dim)]">{value}</span>
    </div>
  );
}

function Field({
  label,
  children,
  mono = false,
}: {
  label: string;
  children: React.ReactNode;
  mono?: boolean;
}) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wider text-[var(--color-text-faint)] mb-1">
        {label}
      </div>
      <div className={mono ? "font-mono text-xs" : "text-sm"}>{children}</div>
    </div>
  );
}
