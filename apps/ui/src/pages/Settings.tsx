import { useEffect, useState } from "react";
import { api, type DeviceInfo } from "../lib/api";

export function Settings() {
  const [device, setDevice] = useState<DeviceInfo | null>(null);
  const [copied, setCopied] = useState(false);
  const [resetting, setResetting] = useState(false);

  useEffect(() => {
    api.getDeviceInfo().then(setDevice).catch(() => setDevice(null));
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
              <Field label="User-Agent" mono>
                {device?.user_agent ?? "—"}
              </Field>
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
