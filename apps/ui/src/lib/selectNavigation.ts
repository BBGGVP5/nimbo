export interface SelectOption { label: string; disabled?: boolean }

export function nextSelectOption(options: readonly Pick<SelectOption, 'disabled'>[], current: number, key: string): number {
  const enabled = options.map((item, index) => item.disabled ? -1 : index).filter(index => index >= 0);
  if (!enabled.length) return -1;
  if (key === 'Home') return enabled[0];
  if (key === 'End') return enabled[enabled.length - 1];
  const at = enabled.indexOf(current);
  if (at < 0) return key === 'ArrowUp' ? enabled[enabled.length - 1] : enabled[0];
  return enabled[(at + (key === 'ArrowUp' ? -1 : 1) + enabled.length) % enabled.length];
}

export function findSelectOption(options: readonly SelectOption[], query: string, current: number): number {
  const match = query.trim().toLocaleLowerCase();
  if (!match) return -1;
  for (let step = 1; step <= options.length; step++) {
    const index = (current + step + options.length) % options.length;
    if (!options[index].disabled && options[index].label.toLocaleLowerCase().startsWith(match)) return index;
  }
  return -1;
}

export function selectPlacement(anchor: { left: number; top: number; bottom: number; width: number }, height: number, viewport: { width: number; height: number }) {
  const margin = 12, gap = 6;
  const width = Math.min(Math.max(anchor.width, 180), Math.max(0, viewport.width - 2 * margin));
  const below = Math.max(0, viewport.height - anchor.bottom - gap - margin);
  const above = Math.max(0, anchor.top - gap - margin);
  const up = below < Math.min(height, 160) && above > below;
  const maxHeight = Math.min(height, up ? above : below);
  return { width, maxHeight, left: Math.max(margin, Math.min(anchor.left, viewport.width - width - margin)),
    top: Math.max(margin, up ? anchor.top - gap - maxHeight : anchor.bottom + gap) };
}
