import { useEffect, useState } from "react";
import { api } from "./api";
import { pingActions } from "./pingActions";
import { pingServersProgressively } from "./ping";

// Backend cancellation is global, so all mounted lists share one operation owner.
let pending = new Set<string>();
const listeners = new Set<(ids: Set<string>) => void>();
const actions = pingActions(
  (ids, publish, signal) => pingServersProgressively(ids, publish, 3, signal, false),
  () => api.cancelPings(), ids => { pending = ids; listeners.forEach(listener => listener(ids)); });
export function usePingActions() {
  const [ids, setIds] = useState(() => pending);
  useEffect(() => {
    listeners.add(setIds); setIds(pending);
    return () => { listeners.delete(setIds); if (!listeners.size) actions.cancel(); };
  }, []);
  return { pending: ids, toggle: actions.toggle, cancel: actions.cancel };
}
