import { useEffect, useId, useLayoutEffect, useRef, useState, type HTMLAttributes, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { menuPosition, type MenuAction } from "./Universal";
import { serverHold } from "../lib/serverHold";

/** No permanent dots: hold, right click, or Shift+F10 on the server opens its actions. */
export function ServerContextMenu({ actions, label, children, ...props }: HTMLAttributes<HTMLDivElement> & {
  actions: MenuAction[]; label: string; children: ReactNode;
}) {
  const [anchor, setAnchor] = useState<{ right: number; bottom: number }>();
  const [position, setPosition] = useState({ top: 0, left: 0 });
  const row = useRef<HTMLDivElement>(null), menu = useRef<HTMLDivElement>(null);
  const previousFocus = useRef<HTMLElement | null>(null);
  const id = useId();
  const open = (x: number, y: number) => {
    previousFocus.current = document.activeElement as HTMLElement;
    setAnchor({ right: x, bottom: y });
  };
  const [hold] = useState(() => serverHold(open));
  const close = () => { setAnchor(undefined); previousFocus.current?.focus(); };
  useEffect(() => () => hold.cancel(), [hold]);
  useLayoutEffect(() => {
    if (!anchor || !menu.current) return;
    setPosition(menuPosition(anchor, menu.current.getBoundingClientRect(), { width: innerWidth, height: innerHeight }));
    menu.current.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus();
    const outside = (event: Event) => { if (!menu.current?.contains(event.target as Node)) setAnchor(undefined); };
    const dismiss = () => { hold.cancel(); setAnchor(undefined); };
    document.addEventListener("pointerdown", outside);
    window.addEventListener("resize", dismiss);
    window.addEventListener("scroll", dismiss, true);
    return () => { document.removeEventListener("pointerdown", outside); window.removeEventListener("resize", dismiss); window.removeEventListener("scroll", dismiss, true); };
  }, [anchor, hold]);
  const excluded = (target: EventTarget) => (target as HTMLElement).closest('[data-server-action], .signal-star, .server-row-icon-button, .server-side-action-button, input, a, [role="dialog"], dialog');
  return <><div {...props} ref={row} aria-haspopup="menu" aria-controls={anchor ? id : undefined} aria-expanded={Boolean(anchor)}
    onPointerDown={event => { if (event.isPrimary && event.button === 0 && !excluded(event.target)) hold.down(event.clientX, event.clientY); }}
    onPointerMove={event => hold.move(event.clientX, event.clientY)} onPointerUp={() => hold.up()}
    onPointerCancel={() => hold.cancel()} onPointerLeave={() => hold.cancel()}
    onClickCapture={event => { if (hold.consumeClick()) { event.preventDefault(); event.stopPropagation(); } }}
    onContextMenu={event => { if (excluded(event.target)) return; event.preventDefault(); hold.cancel(); open(event.clientX, event.clientY); }}
    onKeyDown={event => {
      if (event.key === "ContextMenu" || (event.shiftKey && event.key === "F10")) {
        event.preventDefault(); event.stopPropagation(); const rect = row.current!.getBoundingClientRect(); open(rect.right, rect.bottom);
      } else props.onKeyDown?.(event);
    }}>{children}</div>
    {anchor && createPortal(<div ref={menu} id={id} role="menu" aria-label={label} className="universal-menu" style={position}
      onClick={event => event.stopPropagation()} onContextMenu={event => event.preventDefault()}
      onKeyDown={event => {
        event.stopPropagation();
        const items = Array.from(menu.current!.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'));
        if (event.key === "Escape" || event.key === "Tab") { if (event.key === "Escape") event.preventDefault(); close(); }
        if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
          event.preventDefault(); const index = items.indexOf(document.activeElement as HTMLButtonElement);
          const next = event.key === "Home" ? 0 : event.key === "End" ? items.length - 1 : (index + (event.key === "ArrowDown" ? 1 : -1) + items.length) % items.length;
          items[next]?.focus();
        }
      }}>{actions.map((action, index) => <button key={index} type="button" role="menuitem" disabled={action.disabled}
        className={action.danger ? "is-danger" : ""} onClick={() => { close(); action.onClick(); }}>{action.label}</button>)}</div>, row.current?.closest("dialog") ?? document.body)}
  </>;
}
