import { useEffect } from "react";

/** Keep existing native-action dialogs keyboard-accessible while they share the universal surfaces. */
export function DialogFocusManager() {
  useEffect(() => {
    const selector = 'button:not(:disabled), a[href], input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [tabindex="0"]';
    const visible = (node: HTMLElement) => node.getClientRects().length > 0 && !node.closest('[hidden], [inert]');
    let active: HTMLElement | null = null;
    const previous = new WeakMap<HTMLElement, HTMLElement | null>();
    const items = () => active ? Array.from(active.querySelectorAll<HTMLElement>(selector)).filter(visible) : [];
    const focusFirst = () => {
      if (!active || active instanceof HTMLDialogElement) return;
      const target = items()[0] ?? active;
      if (target === active) active.tabIndex = -1;
      target.focus();
    };
    const update = () => {
      const dialogs = Array.from(document.querySelectorAll<HTMLElement>('[role="dialog"],dialog[open]')).filter(visible);
      const next = dialogs[dialogs.length - 1] ?? null;
      if (next === active) return;
      const old = active;
      active = next;
      if (next) {
        if (!previous.has(next)) previous.set(next, document.activeElement as HTMLElement | null);
        if (!next.contains(document.activeElement)) focusFirst();
      } else if (old) {
        const target = previous.get(old);
        if (target?.isConnected) target.focus();
      }
    };
    const onKey = (event: KeyboardEvent) => {
      if (!active || active instanceof HTMLDialogElement || event.key !== "Tab") return;
      const targets = items();
      const first = targets[0], last = targets[targets.length - 1];
      if (!first) { event.preventDefault(); active.focus(); return; }
      if (event.shiftKey && (document.activeElement === first || !active.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || !active.contains(document.activeElement))) { event.preventDefault(); first.focus(); }
    };
    const onFocus = (event: FocusEvent) => { if (active && !(active instanceof HTMLDialogElement) && !active.contains(event.target as Node)) focusFirst(); };
    const observer = new MutationObserver(update);
    observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ["open", "hidden"] });
    document.addEventListener("keydown", onKey, true);
    document.addEventListener("focusin", onFocus);
    update();
    return () => { observer.disconnect(); document.removeEventListener("keydown", onKey, true); document.removeEventListener("focusin", onFocus); };
  }, []);
  return null;
}
