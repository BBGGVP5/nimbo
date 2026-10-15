/** One gesture owns one timer. Movement/scroll/cancel never opens a server menu. */
export function serverHold(open: (x: number, y: number) => void,
  schedule: (callback: () => void, ms: number) => unknown = setTimeout,
  unschedule: (timer: any) => void = clearTimeout) {
  let timer: unknown, origin: { x: number; y: number } | undefined, held = false;
  const cancel = () => { if (timer !== undefined) unschedule(timer); timer = undefined; origin = undefined; };
  return {
    down(x: number, y: number) {
      cancel(); held = false; origin = { x, y };
      timer = schedule(() => { timer = undefined; held = true; open(x, y); }, 500);
    },
    move(x: number, y: number) { if (origin && Math.hypot(x - origin.x, y - origin.y) > 8) cancel(); },
    up: cancel,
    cancel,
    consumeClick() { const suppress = held; held = false; return suppress; },
  };
}
