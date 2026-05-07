export function Subscriptions() {
  return (
    <div className="glass rounded-2xl h-full p-10 overflow-auto">
      <div className="max-w-3xl mx-auto">
        <h1 className="text-2xl font-semibold mb-1">Подписки</h1>
        <p className="text-sm text-[var(--color-text-dim)] mb-8">
          Управление источниками конфигов.
        </p>

        <div className="glass rounded-2xl p-12 text-center">
          <div className="text-sm text-[var(--color-text-dim)] mb-4">
            Подписок пока нет.
          </div>
          <button
            className="px-5 py-2.5 rounded-xl bg-[var(--color-accent)] text-white text-sm font-medium hover:bg-[var(--color-accent-bright)] transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            disabled
            title="Будет доступно после Этапа 2"
          >
            Добавить подписку
          </button>
        </div>
      </div>
    </div>
  );
}
