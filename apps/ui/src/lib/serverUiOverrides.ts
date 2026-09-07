import { useCallback, useEffect, useState } from "react";
import { serverDisplayName, type Server } from "./api";

export type ServerUiOverride = {
  name?: string;
  hidden?: boolean;
};

export type ServerUiOverrides = Record<string, ServerUiOverride>;

const STORAGE_KEY = "nimbo.serverUiOverrides";
// Страница «Серверы» раньше писала переименования в собственный ключ, поэтому
// правки с одного экрана не были видны на другом. Старое хранилище читается один
// раз и вливается в общее.
const LEGACY_STORAGE_KEY = "nimbo.serverOverrides";
const CHANGE_EVENT = "nimbo:server-ui-overrides";

function parseOverrides(raw: string | null): ServerUiOverrides {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw) as ServerUiOverrides;
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function mergeOverrides(base: ServerUiOverrides, patch: ServerUiOverrides): ServerUiOverrides {
  const merged: ServerUiOverrides = { ...base };
  for (const [serverId, value] of Object.entries(patch)) {
    merged[serverId] = { ...(merged[serverId] ?? {}), ...value };
  }
  return merged;
}

let cache: ServerUiOverrides = {};

export function readServerUiOverrides(): ServerUiOverrides {
  if (typeof window === "undefined") return {};
  try {
    const current = parseOverrides(localStorage.getItem(STORAGE_KEY));
    const legacyRaw = localStorage.getItem(LEGACY_STORAGE_KEY);
    if (legacyRaw == null) {
      cache = current;
      return current;
    }
    // Значения из общего ключа новее — они выигрывают у унаследованных.
    const merged = mergeOverrides(parseOverrides(legacyRaw), current);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
    localStorage.removeItem(LEGACY_STORAGE_KEY);
    cache = merged;
    return merged;
  } catch {
    return cache;
  }
}

export function writeServerUiOverrides(value: ServerUiOverrides): void {
  cache = value;
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  } catch {
    /* ignore quota errors */
  }
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

/** Имя сервера с учётом пользовательского переименования. */
export function serverDisplayLabel(server: Server, overrides: ServerUiOverrides = cache): string {
  const customName = overrides[server.id]?.name?.trim();
  return customName || serverDisplayName(server.name);
}

export function isServerHidden(serverId: string, overrides: ServerUiOverrides = cache): boolean {
  return Boolean(overrides[serverId]?.hidden);
}

cache = readServerUiOverrides();

/**
 * Переименования и скрытие серверов общие для всех экранов: список на «Главной»,
 * карточки профилей и страница подписки читают и пишут одно хранилище.
 */
export function useServerUiOverrides() {
  const [overrides, setOverrides] = useState<ServerUiOverrides>(readServerUiOverrides);

  useEffect(() => {
    const sync = () => setOverrides(readServerUiOverrides());
    window.addEventListener(CHANGE_EVENT, sync);
    // storage-событие приходит из других окон приложения (например, из трея).
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener(CHANGE_EVENT, sync);
      window.removeEventListener("storage", sync);
    };
  }, []);

  const commit = useCallback((producer: (current: ServerUiOverrides) => ServerUiOverrides) => {
    setOverrides((current) => {
      const next = producer(current);
      writeServerUiOverrides(next);
      return next;
    });
  }, []);

  const renameServer = useCallback((serverId: string, name: string) => {
    const trimmed = name.trim();
    if (!trimmed) return;
    commit((current) => ({
      ...current,
      [serverId]: {
        ...(current[serverId] ?? {}),
        name: trimmed,
      },
    }));
  }, [commit]);

  const hideServer = useCallback((serverId: string) => {
    commit((current) => ({
      ...current,
      [serverId]: {
        ...(current[serverId] ?? {}),
        hidden: true,
      },
    }));
  }, [commit]);

  const showServer = useCallback((serverId: string) => {
    commit((current) => {
      const entry = current[serverId];
      if (!entry) return current;
      const { hidden: _hidden, ...rest } = entry;
      const next = { ...current };
      if (Object.keys(rest).length > 0) next[serverId] = rest;
      else delete next[serverId];
      return next;
    });
  }, [commit]);

  // Скрытый сервер иначе не вернуть: отдельного экрана со скрытыми серверами нет.
  const showAllServers = useCallback(() => {
    commit((current) => {
      const next: ServerUiOverrides = {};
      for (const [serverId, value] of Object.entries(current)) {
        const { hidden: _hidden, ...rest } = value;
        if (Object.keys(rest).length > 0) next[serverId] = rest;
      }
      return next;
    });
  }, [commit]);

  const hiddenCount = Object.values(overrides).filter((value) => value.hidden).length;

  return { overrides, renameServer, hideServer, showServer, showAllServers, hiddenCount };
}
