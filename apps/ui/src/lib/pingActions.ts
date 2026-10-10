import { type ServerPing } from "./api";

/** One active user operation. Cancellation invalidates late results before stopping IPC. */
export function pingActions(measure: (ids: string[], publish: (result: ServerPing) => void, signal: AbortSignal) => Promise<void>,
  stop: () => Promise<void>, changed: (ids: Set<string>) => void) {
  let active: { ids: Set<string>; controller: AbortController } | undefined;
  let retiring = Promise.resolve();
  const cancel = () => {
    if (!active) return;
    active.controller.abort(); active = undefined; changed(new Set());
    retiring = retiring.then(stop).catch(() => {});
  };
  return {
    cancel,
    async toggle(ids: string[], publish: (result: ServerPing) => void) {
      const keys = new Set(ids);
      if (!keys.size) return;
      if (active && [...keys].every(id => active!.ids.has(id))) { cancel(); return; }
      cancel();
      const run = { ids: keys, controller: new AbortController() };
      active = run; changed(new Set(keys));
      try {
        await retiring;
        if (active !== run || run.controller.signal.aborted) return;
        await measure([...keys], result => {
          if (active !== run || run.controller.signal.aborted) return;
          publish(result); run.ids.delete(result.server_id); changed(new Set(run.ids));
        }, run.controller.signal);
      } finally {
        if (active === run) { active = undefined; changed(new Set()); }
      }
    },
  };
}
