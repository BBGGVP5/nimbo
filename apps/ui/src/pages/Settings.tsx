export function Settings() {
  return (
    <div className="p-8 max-w-3xl">
      <h1 className="text-2xl font-semibold mb-1">Настройки</h1>
      <p className="text-sm text-[var(--color-text-dim)] mb-8">
        Поведение приложения и сети.
      </p>

      <div className="space-y-4">
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
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] overflow-hidden">
      <div className="px-5 py-3 border-b border-[var(--color-border)] text-xs uppercase tracking-wider text-[var(--color-text-dim)]">
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
