/** UI telemetry only: never use this to drive VPN lifetime or network recovery.
 * Requests are sequential; the delay starts after completion. Callers own error
 * presentation and must ignore late results after their component is disposed.
 */
export function startVisiblePolling(
  task: () => Promise<void> | void,
  intervalMs: number,
): () => void {
  let disposed = false;
  let running = false;
  let timer: number | undefined;

  const clearTimer = () => {
    if (timer !== undefined) window.clearTimeout(timer);
    timer = undefined;
  };
  const visible = () => document.visibilityState === "visible";
  const tick = async () => {
    if (disposed || running || !visible()) return;
    clearTimer();
    running = true;
    try {
      await task();
    } catch {
      // A failed sample must not stop future samples. UI tasks report errors.
    } finally {
      running = false;
      if (!disposed && visible()) timer = window.setTimeout(() => void tick(), intervalMs);
    }
  };
  const visibilityChanged = () => {
    clearTimer();
    if (visible()) void tick();
  };

  document.addEventListener("visibilitychange", visibilityChanged);
  void tick();
  return () => {
    disposed = true;
    clearTimer();
    document.removeEventListener("visibilitychange", visibilityChanged);
  };
}
