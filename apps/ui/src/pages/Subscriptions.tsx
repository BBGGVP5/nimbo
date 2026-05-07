export function Subscriptions() {
  return (
    <div className="p-8 max-w-3xl">
      <h1 className="text-2xl font-semibold mb-1">Подписки</h1>
      <p className="text-sm text-[var(--color-text-dim)] mb-8">
        Управление источниками конфигов.
      </p>

      <div className="rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] p-12 text-center">
        <div className="text-sm text-[var(--color-text-dim)] mb-4">
          Подписок пока нет.
        </div>
        <button
          className="px-4 py-2 rounded-lg bg-[var(--color-surface-2)] border border-[var(--color-border)] text-sm hover:bg-[var(--color-border)]/40 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          disabled
          title="Будет доступно после Этапа 2"
        >
          Добавить подписку
        </button>
      </div>
    </div>
  );
}
