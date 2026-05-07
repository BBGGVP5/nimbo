import { useState } from "react";
import { Link } from "react-router-dom";
import { useAppStore } from "../store";
import { formatBytes, formatExpire, type Subscription } from "../lib/api";

export function Subscriptions() {
  const subs = useAppStore((s) => s.subscriptions);
  const [open, setOpen] = useState(false);

  return (
    <div className="glass rounded-2xl h-full p-10 overflow-auto">
      <div className="max-w-3xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-semibold mb-1">Подписки</h1>
            <p className="text-sm text-[var(--color-text-dim)]">
              Загрузите URL подписки — мы распарсим серверы.
            </p>
          </div>
          <button
            onClick={() => setOpen(true)}
            className="px-4 py-2 rounded-xl bg-[var(--color-accent)] hover:bg-[var(--color-accent-bright)] text-white text-sm font-medium transition-colors"
          >
            Добавить
          </button>
        </div>

        {subs.length === 0 ? (
          <div className="glass rounded-2xl p-12 text-center text-sm text-[var(--color-text-dim)]">
            Подписок пока нет. Нажмите «Добавить».
          </div>
        ) : (
          <div className="space-y-3">
            {subs.map((sub) => (
              <SubscriptionCard key={sub.url} sub={sub} />
            ))}
          </div>
        )}
      </div>

      {open && <AddSubscriptionDialog onClose={() => setOpen(false)} />}
    </div>
  );
}

function SubscriptionCard({ sub }: { sub: Subscription }) {
  const refresh = useAppStore((s) => s.refreshSubscription);
  const remove = useAppStore((s) => s.removeSubscription);
  const [busy, setBusy] = useState(false);

  const total = sub.info?.total ?? null;
  const used =
    (sub.info?.upload ?? 0) + (sub.info?.download ?? 0) || null;

  const onRefresh = async () => {
    setBusy(true);
    try {
      await refresh(sub.url);
    } catch (e) {
      alert(String(e));
    } finally {
      setBusy(false);
    }
  };

  const onRemove = async () => {
    if (!confirm(`Удалить подписку «${sub.name ?? sub.url}»?`)) return;
    setBusy(true);
    try {
      await remove(sub.url);
    } catch (e) {
      alert(String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="glass rounded-2xl p-5">
      <div className="flex items-start justify-between gap-4 mb-3">
        <div className="min-w-0">
          <div className="font-medium truncate">
            {sub.name ?? "Подписка"}
          </div>
          <div className="text-xs text-[var(--color-text-faint)] font-mono truncate">
            {sub.url}
          </div>
        </div>
        <div className="flex gap-1.5 shrink-0">
          <Link
            to={`/subscriptions/${encodeURIComponent(sub.url)}`}
            className="px-3 py-1.5 rounded-lg text-xs bg-[var(--color-glass-bg)] hover:bg-[var(--color-glass-bg-strong)] transition-colors"
          >
            Серверы
          </Link>
          <button
            onClick={onRefresh}
            disabled={busy}
            className="px-3 py-1.5 rounded-lg text-xs bg-[var(--color-glass-bg)] hover:bg-[var(--color-glass-bg-strong)] transition-colors disabled:opacity-40"
          >
            Обновить
          </button>
          <button
            onClick={onRemove}
            disabled={busy}
            className="px-3 py-1.5 rounded-lg text-xs bg-[var(--color-glass-bg)] hover:bg-[rgba(244,67,54,0.2)] hover:text-[var(--color-status-error)] transition-colors disabled:opacity-40"
          >
            Удалить
          </button>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-2 text-xs">
        <Cell label="Серверы" value={String(sub.servers.length)} />
        <Cell
          label="Трафик"
          value={
            total !== null
              ? `${formatBytes(used ?? 0)} / ${formatBytes(total)}`
              : "—"
          }
        />
        <Cell label="Истекает" value={formatExpire(sub.info?.expire)} />
      </div>
    </div>
  );
}

function Cell({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-[var(--color-glass-bg)] px-3 py-2">
      <div className="text-[9px] uppercase tracking-wider text-[var(--color-text-faint)] mb-0.5">
        {label}
      </div>
      <div className="text-xs">{value}</div>
    </div>
  );
}

function AddSubscriptionDialog({ onClose }: { onClose: () => void }) {
  const add = useAppStore((s) => s.addSubscription);
  const [url, setUrl] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      await add(url.trim(), name.trim() || undefined);
      onClose();
    } catch (e) {
      setErr(String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-6"
      style={{ background: "rgba(0,0,0,0.55)", backdropFilter: "blur(8px)" }}
      onClick={onClose}
    >
      <form
        onClick={(e) => e.stopPropagation()}
        onSubmit={onSubmit}
        className="glass rounded-2xl p-6 w-full max-w-md"
      >
        <h3 className="text-lg font-semibold mb-4">Новая подписка</h3>

        <label className="block mb-3">
          <div className="text-xs uppercase tracking-wider text-[var(--color-text-faint)] mb-1.5">
            URL подписки
          </div>
          <input
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://sub.example.com/…"
            required
            autoFocus
            className="w-full px-3 py-2 rounded-lg bg-[var(--color-glass-bg)] border border-[var(--color-border)] text-sm focus:outline-none focus:border-[var(--color-accent)] focus:bg-[var(--color-glass-bg-strong)] transition-colors"
          />
        </label>

        <label className="block mb-4">
          <div className="text-xs uppercase tracking-wider text-[var(--color-text-faint)] mb-1.5">
            Название (опционально)
          </div>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Мой провайдер"
            className="w-full px-3 py-2 rounded-lg bg-[var(--color-glass-bg)] border border-[var(--color-border)] text-sm focus:outline-none focus:border-[var(--color-accent)] focus:bg-[var(--color-glass-bg-strong)] transition-colors"
          />
        </label>

        {err && (
          <div className="mb-3 px-3 py-2 rounded-lg text-xs text-[var(--color-status-error)] bg-[rgba(244,67,54,0.12)] border border-[rgba(244,67,54,0.3)]">
            {err}
          </div>
        )}

        <div className="flex gap-2 justify-end">
          <button
            type="button"
            onClick={onClose}
            disabled={busy}
            className="px-4 py-2 rounded-xl text-sm bg-[var(--color-glass-bg)] hover:bg-[var(--color-glass-bg-strong)] transition-colors disabled:opacity-40"
          >
            Отмена
          </button>
          <button
            type="submit"
            disabled={busy || !url.trim()}
            className="px-4 py-2 rounded-xl text-sm bg-[var(--color-accent)] hover:bg-[var(--color-accent-bright)] text-white font-medium transition-colors disabled:opacity-40"
          >
            {busy ? "Загружаю…" : "Добавить"}
          </button>
        </div>
      </form>
    </div>
  );
}
