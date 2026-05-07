export function Home() {
  return (
    <div className="glass rounded-2xl h-full p-10 overflow-auto">
      <div className="max-w-3xl mx-auto">
        <div className="text-center mb-10">
          <h1 className="text-2xl font-semibold mb-1">Главная</h1>
          <p className="text-sm text-[var(--color-text-dim)]">
            Текущее подключение
          </p>
        </div>

        <div className="flex flex-col items-center mb-12">
          <PowerButton />
          <div className="mt-6 text-sm text-[var(--color-text-faint)]">
            Нажмите для подключения
          </div>
        </div>

        <div className="grid grid-cols-3 gap-3 mb-6">
          <Stat label="Сервер" value="—" />
          <Stat label="Время" value="00:00:00" mono />
          <Stat label="Трафик" value="0 B / 0 B" mono />
        </div>

        <section className="glass rounded-2xl p-6">
          <h2 className="text-sm font-medium mb-3">Что дальше</h2>
          <ul className="text-sm text-[var(--color-text-dim)] space-y-1.5">
            <li>· Этап 2 — парсер подписок (URL → серверы)</li>
            <li>· Этап 3 — билдер xray-конфига</li>
            <li>· Этап 4 — Windows Service + named pipe IPC</li>
            <li>· Этап 5 — запуск xray-core и TUN</li>
          </ul>
        </section>
      </div>
    </div>
  );
}

function PowerButton() {
  return (
    <button
      type="button"
      disabled
      title="Будет доступно после Этапа 5"
      className="
        relative w-44 h-44 rounded-full
        flex items-center justify-center
        transition-transform duration-300
        disabled:opacity-90 disabled:cursor-not-allowed
        group
      "
      style={{
        background:
          "radial-gradient(circle at 50% 35%, rgba(107,114,128,0.55) 0%, rgba(107,114,128,0.25) 35%, rgba(26,26,46,0.85) 70%, rgba(30,30,58,1) 100%)",
        boxShadow:
          "0 0 60px rgba(107,114,128,0.35), inset 0 1px 0 rgba(255,255,255,0.10), inset 0 -1px 0 rgba(0,0,0,0.30)",
        border: "2px solid rgba(255,255,255,0.10)",
      }}
    >
      <div
        className="absolute inset-2 rounded-full"
        style={{
          background:
            "radial-gradient(circle, rgba(107,114,128,0.18) 0%, transparent 60%)",
        }}
      />
      <svg
        viewBox="0 0 24 24"
        className="w-16 h-16 relative z-10"
        fill="none"
        stroke="rgba(107,114,128,0.95)"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M12 2v10" />
        <path d="M5.6 6.6a9 9 0 1 0 12.8 0" />
      </svg>
    </button>
  );
}

function Stat({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="glass rounded-xl px-4 py-3">
      <div className="text-[10px] uppercase tracking-wider text-[var(--color-text-faint)] mb-1">
        {label}
      </div>
      <div className={mono ? "font-mono text-sm" : "text-sm"}>{value}</div>
    </div>
  );
}
