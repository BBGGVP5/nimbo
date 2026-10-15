import { Children, Fragment, isValidElement, useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode, type SelectHTMLAttributes } from 'react';
import { createPortal } from 'react-dom';
import { findSelectOption, nextSelectOption, selectPlacement } from '../lib/selectNavigation';
import './nimbo-select.css';

type NimboSelectProps = Pick<SelectHTMLAttributes<HTMLSelectElement>, 'id' | 'name' | 'className' | 'style' | 'value' | 'disabled' | 'title' | 'required' | 'autoFocus' | 'aria-label' | 'aria-labelledby' | 'aria-describedby' | 'aria-invalid' | 'children'> & {
  onChange?: (event: { target: { value: string }; currentTarget: { value: string } }) => void;
};
interface Choice { value: string; label: string; content: ReactNode; disabled: boolean }
function textContent(content: ReactNode): string {
  return Children.toArray(content).map(node => isValidElement<{ children?: ReactNode }>(node) ? textContent(node.props.children) : String(node ?? '')).join('');
}
function choices(children: ReactNode, groupDisabled = false): Choice[] {
  return Children.toArray(children).flatMap(node => {
    if (!isValidElement<{ value?: string | number; disabled?: boolean; children?: ReactNode; label?: string }>(node)) return [];
    if (node.type === Fragment || node.type === 'optgroup') return choices(node.props.children, groupDisabled || Boolean(node.props.disabled));
    if (node.type !== 'option') return [];
    const label = node.props.label ?? textContent(node.props.children);
    return [{ value: String(node.props.value ?? label), label, content: node.props.children, disabled: groupDisabled || Boolean(node.props.disabled) }];
  });
}

/** Controlled Nimbo listbox. The small change payload intentionally mirrors select.target.value. */
export function NimboSelect({ children, value, onChange, className = '', name, required, ...props }: NimboSelectProps) {
  const options = choices(children);
  const selected = options.findIndex(option => option.value === String(value ?? ''));
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(selected);
  const [position, setPosition] = useState({ top: 0, left: 0, width: 180, maxHeight: 280 });
  const trigger = useRef<HTMLButtonElement>(null), panel = useRef<HTMLDivElement>(null);
  const generated = useId(), listId = `${props.id ?? generated}-options`;
  const typeahead = useRef({ query: '', at: 0 });
  const close = () => setOpen(false);
  const show = (key = '') => {
    if (props.disabled) return;
    const initial = key === 'Home' || key === 'End' ? nextSelectOption(options, -1, key)
      : selected >= 0 && !options[selected].disabled ? selected : nextSelectOption(options, -1, key === 'ArrowUp' ? 'End' : 'Home');
    setActive(initial); setOpen(true); typeahead.current = { query: '', at: 0 };
  };
  const choose = (index: number) => {
    const option = options[index];
    if (!option || option.disabled || props.disabled) return;
    close(); trigger.current?.focus();
    if (option.value !== String(value ?? '')) onChange?.({ target: { value: option.value }, currentTarget: { value: option.value } });
  };
  useEffect(() => { if (props.disabled) close(); }, [props.disabled]);
  useLayoutEffect(() => {
    if (!open || !trigger.current || !panel.current) return;
    const rect = trigger.current.getBoundingClientRect();
    setPosition(selectPlacement(rect, Math.min(panel.current.scrollHeight, 320), { width: innerWidth, height: innerHeight }));
    const outside = (event: Event) => {
      if (!trigger.current?.contains(event.target as Node) && !panel.current?.contains(event.target as Node)) close();
    };
    const scroll = (event: Event) => { if (!panel.current?.contains(event.target as Node)) close(); };
    document.addEventListener('pointerdown', outside);
    window.addEventListener('resize', close);
    window.addEventListener('scroll', scroll, true);
    return () => { document.removeEventListener('pointerdown', outside); window.removeEventListener('resize', close); window.removeEventListener('scroll', scroll, true); };
  }, [open, options.length]);
  useLayoutEffect(() => {
    const list = panel.current, item = list?.children[active] as HTMLElement | undefined;
    if (!open || !list || !item) return;
    if (item.offsetTop < list.scrollTop) list.scrollTop = item.offsetTop;
    else if (item.offsetTop + item.offsetHeight > list.scrollTop + list.clientHeight) list.scrollTop = item.offsetTop + item.offsetHeight - list.clientHeight;
  }, [active, open, position.maxHeight]);

  return <>
    {name && <input type="hidden" name={name} value={String(value ?? '')} disabled={props.disabled} />}
    <button {...props} ref={trigger} type="button" role="combobox" className={`nimbo-select ${className}`}
      aria-required={required} aria-haspopup="listbox" aria-expanded={open} aria-controls={open ? listId : undefined}
      aria-activedescendant={open && active >= 0 ? `${listId}-${active}` : undefined}
      onClick={() => open ? close() : show()}
      onKeyDown={event => {
        if (event.key === 'Tab') { close(); return; }
        if (event.key === 'Escape' && open) { event.preventDefault(); event.stopPropagation(); close(); return; }
        if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
          event.preventDefault(); if (!open) show(event.key); else setActive(nextSelectOption(options, active, event.key)); return;
        }
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault(); if (open) choose(active); else show(); return;
        }
        if (event.key.length === 1 && !event.altKey && !event.ctrlKey && !event.metaKey) {
          event.preventDefault(); const now = Date.now();
          const query = now - typeahead.current.at < 650 ? typeahead.current.query + event.key : event.key;
          typeahead.current = { query, at: now };
          const index = findSelectOption(options, query, open ? active : selected);
          if (index >= 0) { setActive(index); setOpen(true); }
        }
      }}>
      <span className="nimbo-select__value">{options[selected]?.content ?? '—'}</span>
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5" /></svg>
    </button>
    {open && createPortal(<div ref={panel} id={listId} role="listbox" className="nimbo-select__list" style={position}
      aria-label={props['aria-label']} aria-labelledby={props['aria-labelledby'] ?? (props['aria-label'] ? undefined : props.id)}
      onPointerDown={event => event.preventDefault()}>
      {options.map((option, index) => <div key={`${option.value}-${index}`} id={`${listId}-${index}`} role="option"
        aria-selected={index === selected} aria-disabled={option.disabled} className="nimbo-select__option" data-active={index === active}
        onPointerMove={() => { if (!option.disabled) setActive(index); }} onClick={() => choose(index)}>
        <span>{option.content}</span>{index === selected && <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4L19 6" /></svg>}
      </div>)}
    </div>, trigger.current?.closest('dialog,[role="dialog"]') ?? document.body)}
  </>;
}
