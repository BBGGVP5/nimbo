const THRESHOLD = 72;
const DIRECTION_SLOP = 12;

/** Direction locks once; retreat, cancellation and another pointer cannot commit a pull. */
export function pullRefreshGesture() {
  let origin: { id: number; x: number; y: number } | undefined;
  const cancel = () => { origin = undefined; };
  const move = (id: number, x: number, y: number) => {
    if (!origin || origin.id !== id) return 0;
    const dx = Math.abs(x - origin.x), dy = y - origin.y;
    if (dy < -DIRECTION_SLOP || (dx > DIRECTION_SLOP && dx >= Math.max(0, dy) * .8)) {
      cancel(); return 0;
    }
    return dy > DIRECTION_SLOP ? Math.min(1, dy / THRESHOLD) : 0;
  };
  return {
    down(start: { id: number; x: number; y: number; atTop: boolean; blocked: boolean; primary: boolean; button: number }) {
      cancel();
      if (start.atTop && !start.blocked && start.primary && start.button === 0) origin = start;
    },
    move,
    up(id: number, x: number, y: number) {
      const commit = move(id, x, y) >= 1;
      cancel(); return commit;
    },
    cancel,
  };
}

/** Shared across page mounts: each subscription has one request and a short post-completion cooldown. */
export function subscriptionRefreshCoordinator(now = Date.now) {
  const requests = new Map<string, Promise<void>>();
  const completedAt = new Map<string, number>();
  const listeners = new Set<() => void>();
  const changed = () => listeners.forEach(listener => listener());
  return {
    pending: () => new Set(requests.keys()),
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; },
    async refresh(urls: string[], action: (url: string) => Promise<unknown>, report: (error: unknown) => void) {
      await Promise.all([...new Set(urls)].map(url => {
        const current = requests.get(url);
        if (current) return current;
        const completed = completedAt.get(url);
        if (completed !== undefined && now() - completed < 1500) return;
        const request = Promise.resolve().then(() => action(url)).then(() => undefined, report).finally(() => {
          requests.delete(url); completedAt.set(url, now()); changed();
        });
        requests.set(url, request); changed();
        return request;
      }));
    },
  };
}
