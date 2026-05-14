import { getCurrent, onOpenUrl } from "@tauri-apps/plugin-deep-link";
import type { NavigateFunction } from "react-router-dom";
import { isTauriRuntime } from "./api";

type DeepLinkDeps = {
  navigate: NavigateFunction;
  openImportDialog: (source?: string) => void;
  connectServer: (serverId: string) => Promise<void>;
  disconnectServer: () => Promise<void>;
  refreshSubscription: (url: string) => Promise<unknown>;
};

type ParsedDeepLink =
  | { type: "navigate"; to: string }
  | { type: "open-import"; source?: string }
  | { type: "connect"; serverId: string }
  | { type: "disconnect" }
  | { type: "refresh"; url: string };

function decodeValue(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function normalizeBody(rawUrl: string): { action: string; tail: string; params: URLSearchParams } | null {
  const match = rawUrl.match(/^nimbo:(.*)$/i);
  if (!match) return null;

  const withoutScheme = match[1] ?? "";
  const [pathPart, queryPart = ""] = withoutScheme.split("?", 2);
  const normalized = pathPart.replace(/^\/+/, "");
  const slashIndex = normalized.indexOf("/");
  const rawAction = slashIndex >= 0 ? normalized.slice(0, slashIndex) : normalized;
  const rawTail = slashIndex >= 0 ? normalized.slice(slashIndex + 1) : "";

  return {
    action: decodeValue(rawAction).trim().toLowerCase(),
    tail: rawTail,
    params: new URLSearchParams(queryPart),
  };
}

export function parseNimboDeepLink(rawUrl: string): ParsedDeepLink | null {
  const body = normalizeBody(rawUrl);
  if (!body) return null;

  const sourceFromQuery =
    body.params.get("url") ??
    body.params.get("link") ??
    body.params.get("source") ??
    body.params.get("value") ??
    "";
  const source = decodeValue(body.tail || sourceFromQuery).trim();

  switch (body.action) {
    case "":
    case "home":
    case "main":
      return { type: "navigate", to: "/" };
    case "profiles":
    case "profile":
    case "subscriptions":
      return { type: "navigate", to: "/subscriptions" };
    case "apps":
    case "applications":
      return { type: "navigate", to: "/apps" };
    case "settings":
    case "preferences":
      return { type: "navigate", to: "/settings" };
    case "add":
    case "import":
    case "subscribe":
    case "subscription":
      return { type: "open-import", source };
    case "connect":
      return source ? { type: "connect", serverId: source } : { type: "navigate", to: "/" };
    case "disconnect":
      return { type: "disconnect" };
    case "refresh":
      return source ? { type: "refresh", url: source } : { type: "navigate", to: "/subscriptions" };
    default:
      if (source) {
        return { type: "open-import", source };
      }
      return null;
  }
}

async function handleDeepLink(rawUrl: string, deps: DeepLinkDeps): Promise<void> {
  const parsed = parseNimboDeepLink(rawUrl);
  if (!parsed) return;

  switch (parsed.type) {
    case "navigate":
      deps.navigate(parsed.to);
      return;
    case "open-import":
      deps.navigate("/subscriptions");
      deps.openImportDialog(parsed.source);
      return;
    case "connect":
      deps.navigate("/");
      await deps.connectServer(parsed.serverId);
      return;
    case "disconnect":
      deps.navigate("/");
      await deps.disconnectServer();
      return;
    case "refresh":
      deps.navigate("/subscriptions");
      await deps.refreshSubscription(parsed.url);
      return;
  }
}

export async function initNimboDeepLinks(deps: DeepLinkDeps): Promise<() => void> {
  if (!isTauriRuntime()) {
    return () => {};
  }

  const consume = async (urls: string[]) => {
    for (const url of urls) {
      await handleDeepLink(url, deps);
    }
  };

  const current = await getCurrent();
  if (current?.length) {
    await consume(current);
  }

  const unlisten = await onOpenUrl((urls) => {
    void consume(urls);
  });

  return () => {
    unlisten();
  };
}
