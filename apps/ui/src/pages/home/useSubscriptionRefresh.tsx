import { useEffect, useRef, useState, type PointerEvent } from "react";
import { notifyError } from "../../lib/notify";
import { pullRefreshGesture, subscriptionRefreshCoordinator } from "./pullRefresh";
import "./home-profile-polish.css";

const coordinator = subscriptionRefreshCoordinator();
const excluded = "button,a,input,select,textarea,[contenteditable]:not([contenteditable='false']),[role='textbox'],[role='combobox'],[role='button'],[role='dialog'],dialog,[role='menu'],[role='listbox'],[data-no-refresh]";

/** Check every scrolling ancestor, including nested server lists and the app's independent main scroller. */
function atScrollTop(target: Element) {
  for (let element: Element | null = target; element; element = element.parentElement) {
    if (element.scrollTop > 1 && /auto|scroll|overlay/.test(getComputedStyle(element).overflowY)) return false;
  }
  return (document.scrollingElement?.scrollTop ?? 0) <= 1;
}

export function useSubscriptionRefresh(urls: string[], action: (url: string) => Promise<unknown>, disabled = false) {
  const root = useRef<HTMLDivElement>(null);
  const latest = useRef({ urls, action, disabled }); latest.current = { urls, action, disabled };
  const [refreshingUrls, setRefreshingUrls] = useState(() => coordinator.pending());
  const [progress, setProgress] = useState(0);
  const [gesture] = useState(pullRefreshGesture);
  const suppressClick = useRef(false);
  const target = useRef<Element | null>(null);
  const refresh = (selected = latest.current.urls) => coordinator.refresh(selected, latest.current.action, error => notifyError(String(error)));
  const blocked = () => latest.current.disabled || !latest.current.urls.length || coordinator.pending().size > 0 ||
    !!document.querySelector("[role='dialog'],dialog[open],[role='menu'],[role='listbox']");
  const cancel = () => { gesture.cancel(); target.current = null; setProgress(0); };
  const down = (id: number, x: number, y: number, element: EventTarget | null, primary: boolean, button: number) => {
    suppressClick.current = false;
    target.current = element instanceof Element ? element : null;
    gesture.down({ id, x, y, primary, button, atTop: !!target.current && atScrollTop(target.current),
      blocked: blocked() || !!target.current?.closest(excluded) });
    setProgress(0);
  };
  const move = (id: number, x: number, y: number) => {
    if (blocked() || !target.current || !atScrollTop(target.current)) { cancel(); return 0; }
    const value = gesture.move(id, x, y); setProgress(value); return value;
  };
  const up = (id: number, x: number, y: number) => {
    const commit = !blocked() && !!target.current && atScrollTop(target.current) && gesture.up(id, x, y);
    cancel();
    if (commit) { suppressClick.current = true; void refresh(); }
  };
  const handlers = useRef({ down, move, up, cancel }); handlers.current = { down, move, up, cancel };
  useEffect(() => {
    const update = () => setRefreshingUrls(coordinator.pending());
    const unsubscribe = coordinator.subscribe(update); update();
    return unsubscribe;
  }, []);
  useEffect(() => {
    const element = root.current;
    if (!element) return;
    // Keep ordinary vertical scrolling native. Only a downward pull begun at top
    // prevents touch scrolling; pointer touch would otherwise be cancelled by Chromium.
    const start = (event: TouchEvent) => {
      if (event.touches.length !== 1) { handlers.current.cancel(); return; }
      const touch = event.touches[0];
      handlers.current.down(touch.identifier, touch.clientX, touch.clientY, event.target, true, 0);
    };
    const moveTouch = (event: TouchEvent) => {
      if (event.touches.length !== 1) { handlers.current.cancel(); return; }
      const touch = event.touches[0];
      if (handlers.current.move(touch.identifier, touch.clientX, touch.clientY) > 0 && event.cancelable) event.preventDefault();
    };
    const end = (event: TouchEvent) => {
      const touch = event.changedTouches[0];
      if (touch) handlers.current.up(touch.identifier, touch.clientX, touch.clientY);
    };
    const abort = () => handlers.current.cancel();
    element.addEventListener("touchstart", start, { passive: true });
    element.addEventListener("touchmove", moveTouch, { passive: false });
    element.addEventListener("touchend", end);
    element.addEventListener("touchcancel", abort);
    return () => {
      gesture.cancel();
      element.removeEventListener("touchstart", start); element.removeEventListener("touchmove", moveTouch);
      element.removeEventListener("touchend", end); element.removeEventListener("touchcancel", abort);
    };
  }, [gesture]);
  const pointer = (event: PointerEvent<HTMLDivElement>, callback: () => void) => { if (event.pointerType !== "touch") callback(); };
  return {
    refresh, refreshingUrls, progress,
    refreshing: urls.some(url => refreshingUrls.has(url)),
    gestureProps: {
      ref: root,
      onPointerDown: (event: PointerEvent<HTMLDivElement>) => pointer(event, () => down(event.pointerId, event.clientX, event.clientY, event.target, event.isPrimary, event.button)),
      onPointerMove: (event: PointerEvent<HTMLDivElement>) => pointer(event, () => move(event.pointerId, event.clientX, event.clientY)),
      onPointerUp: (event: PointerEvent<HTMLDivElement>) => pointer(event, () => up(event.pointerId, event.clientX, event.clientY)),
      onPointerCancel: cancel,
      onPointerLeave: cancel,
      onClickCapture: (event: React.MouseEvent<HTMLDivElement>) => {
        if (suppressClick.current) { suppressClick.current = false; event.preventDefault(); event.stopPropagation(); }
      },
    },
  };
}

export function RefreshFeedback({ progress, refreshing, locale }: { progress: number; refreshing: boolean; locale: string }) {
  const ru = locale.startsWith("ru");
  return <div className="nimbo-pull-feedback" role="status" aria-live="polite" hidden={!refreshing && progress === 0}>
    <span aria-hidden="true">{refreshing ? "↻" : "↓"}</span>
    {refreshing ? (ru ? "Обновляем подписки…" : "Refreshing subscriptions…") : progress >= 1
      ? (ru ? "Отпустите для обновления" : "Release to refresh") : (ru ? "Потяните для обновления" : "Pull to refresh")}
  </div>;
}
