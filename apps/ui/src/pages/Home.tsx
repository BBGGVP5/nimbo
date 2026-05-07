export function Home() {
  return (
    <div className="p-8 max-w-3xl">
      <h1 className="text-2xl font-semibold mb-1">Главная</h1>
      <p className="text-sm text-[var(--color-text-dim)] mb-8">
        Текущее подключение и быстрые действия.
      </p>

      <section className="rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] p-6 mb-6">
        <div className="flex items-center justify-between mb-6">
          <div>
            <div className="text-xs uppercase tracking-wider text-[var(--color-text-dim)] mb-1">
              Статус
            </div>
            <div className="text-xl font-medium">Не подключено</div>
          </div>
          <button
            className="px-5 py-2.5 rounded-lg bg-[var(--color-accent)] text-white text-sm font-medium hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
            disabled
            title="Будет доступно после Этапа 5"
          >
            Подключить
          </button>
        </div>

        <div className="grid grid-cols-3 gap-4 text-sm">
          <Stat label="Сервер" value="—" />
          <Stat label="Время" value="00:00:00" mono />
          <Stat label="Трафик" value="0 B / 0 B" mono />
        </div>
      </section>

      <section className="rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] p-6">
        <h2 className="text-sm font-medium mb-3">Что дальше</h2>
        <ul className="text-sm text-[var(--color-text-dim)] space-y-1.5">
          <li>· Этап 2 — парсер подписок (URL → серверы)</li>
          <li>· Этап 3 — билдер xray-конфига</li>
          <li>· Этап 4 — Windows Service + named pipe IPC</li>
          <li>· Этап 5 — запуск xray-core и TUN</li>
        </ul>
      </section>
    </div>
  );
}

function Stat({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="rounded-lg bg-[var(--color-surface-2)] px-4 py-3">
      <div className="text-[10px] uppercase tracking-wider text-[var(--color-text-dim)] mb-1">
        {label}
      </div>
      <div className={mono ? "font-mono text-sm" : "text-sm"}>{value}</div>
    </div>
  );
}
