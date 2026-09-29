import { useEffect, useLayoutEffect, useId, useRef, useState, type HTMLAttributes, type ReactNode } from "react";
import { createPortal } from "react-dom";

export function Surface({ children, className = "", ...props }: HTMLAttributes<HTMLElement>) {
  return <section {...props} className={`universal-surface ${className}`}>{children}</section>;
}

const focusable = 'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]';

/** Native modal semantics include focus containment, Escape, and background inertness. */
export function Dialog({ title, children, onClose, closeLabel, footer, status, subtitle, className = "", closeDisabled = false }: {
  title: string; children: ReactNode; onClose: () => void; closeLabel: string;
  footer?: ReactNode; status?: ReactNode; subtitle?: string; className?: string; closeDisabled?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const dialog = ref.current!;
    dialog.showModal();
    return () => { dialog.close(); previous?.focus(); };
  }, []);
  return createPortal(<dialog ref={ref} className={`universal-dialog ${className}`} data-has-status={Boolean(status)} aria-labelledby={titleId}
    onCancel={(event) => { event.preventDefault(); if (!closeDisabled) close.current(); }}
    onClick={(event) => { if (!closeDisabled && event.target === event.currentTarget) {
      const rect = event.currentTarget.getBoundingClientRect();
      if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) close.current();
    } }}>
    <header className="universal-dialog-head"><div><h2 id={titleId}>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>
      <button type="button" className="signal-icon-btn" aria-label={closeLabel} disabled={closeDisabled} onClick={onClose}>×</button>
    </header>{status && <div className="universal-dialog-status">{status}</div>}<div className="universal-dialog-body">{children}</div>
    {footer && <footer className="universal-dialog-footer">{footer}</footer>}
  </dialog>, document.body);
}

export function menuPosition(anchor: { right: number; bottom: number }, panel: { width: number; height: number }, viewport: { width: number; height: number }) {
  return { top: Math.max(12, Math.min(anchor.bottom + 6, viewport.height - panel.height - 12)), left: Math.max(12, Math.min(anchor.right - panel.width, viewport.width - panel.width - 12)) };
}

export interface MenuAction { label: string; onClick: () => void; disabled?: boolean; danger?: boolean }
export function ActionMenu({ label, children, actions }: { label: string; children: ReactNode; actions: MenuAction[] }) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const id = useId();
  const [position, setPosition] = useState({ top: 0, left: 0 });
  useLayoutEffect(() => {
    if (!open) return;
    const rect = trigger.current!.getBoundingClientRect();
    // Measure wrapped labels and the viewport-constrained panel before its first paint.
    const { width, height } = menu.current!.getBoundingClientRect();
    setPosition(menuPosition(rect, { width, height }, { width: window.innerWidth, height: window.innerHeight }));
    menu.current?.querySelector<HTMLElement>(focusable)?.focus();
    const dismiss = (event: Event) => {
      if (menu.current?.contains(event.target as Node) || trigger.current?.contains(event.target as Node)) return;
      setOpen(false);
    };
    const close = () => setOpen(false);
    document.addEventListener("pointerdown", dismiss);
    window.addEventListener("resize", close);
    return () => { document.removeEventListener("pointerdown", dismiss); window.removeEventListener("resize", close); };
  }, [open, actions.length]);
  const close = () => { setOpen(false); trigger.current?.focus(); };
  return <><button ref={trigger} type="button" className="signal-icon-btn" aria-label={label}
    title={label} aria-haspopup="menu" aria-controls={open ? id : undefined} aria-expanded={open}
    onClick={() => setOpen(value => !value)}>{children}</button>
    {open && createPortal(<div ref={menu} id={id} className="universal-menu" role="menu" aria-label={label} style={position}
      onKeyDown={event => {
        const items = Array.from(menu.current!.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'));
        const index = items.indexOf(document.activeElement as HTMLButtonElement);
        if (event.key === "Escape" || event.key === "Tab") { if (event.key === "Escape") event.preventDefault(); close(); }
        if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
          event.preventDefault();
          const next = event.key === "Home" ? 0 : event.key === "End" ? items.length - 1 : (index + (event.key === "ArrowDown" ? 1 : -1) + items.length) % items.length;
          items[next]?.focus();
        }
      }}>{actions.map((action, index) => <button key={index} type="button" role="menuitem" disabled={action.disabled}
        className={action.danger ? "is-danger" : ""} onClick={() => { close(); action.onClick(); }}>{action.label}</button>)}</div>, document.body)}
  </>;
}

export function InfoIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><circle cx="12" cy="12" r="8.5"/><path d="M12 11v6m0-10v1"/></svg>;
}
