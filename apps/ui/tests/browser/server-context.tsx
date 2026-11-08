import { useState } from "react";
import { createRoot } from "react-dom/client";
import { SignalProfiles } from "../../src/pages/profiles/SignalProfiles";
import { Dialog } from "../../src/components/Universal";
import { messages } from "../../src/lib/i18n";
import { useAppStore } from "../../src/store";
import type { Server, Subscription } from "../../src/lib/api";
import "flag-icons/css/flag-icons.min.css";
import "../../src/styles.css";
import "../../src/universal.css";
import "../../src/secondary.css";
import "../../src/preview-parity.css";
import "../../src/preview-fonts.css";

const servers: Server[] = ["Финляндия", "Финляндия · XHTTP 🚀"].map((name, index) => ({
  id: `fixture-${index}`, name, protocol: { kind: "vless", address: "fixture.invalid", port: 443,
    uuid: "fixture-only", encryption: "none", stream: { security: "reality", network: index ? "xhttp" : "tcp" } },
}));
const sub: Subscription = { url: "https://fixture.invalid/sub", name: "NIMBO fixture", servers, fetched_at: 1, info: null };
useAppStore.setState(state => ({ preferences: { ...state.preferences, language: "ru", latency_protocol: "tcp_connect" } }));
function Fixture() {
  const [inDialog, setInDialog] = useState(false);
  const [active, setActive] = useState(servers[0].id), [pending, setPending] = useState(new Set<string>());
  const [calls, setCalls] = useState({ selection: 0, ping: "", cancelled: 0, favorite: 0 });
  const profiles = <SignalProfiles labels={messages.ru} subs={[sub]} activeId={active} connectingId={null}
      pingByServer={{ "fixture-0": 151, "fixture-1": 87 }} pingingServerIds={pending} favorites={new Set()}
      onPickServer={(_, server) => { setActive(server.id); setCalls(value => ({ ...value, selection: value.selection + 1 })); }}
      onPingServer={id => { setPending(pending.has(id) ? new Set() : new Set([id])); setCalls(value => ({ ...value, ping: id, cancelled: value.cancelled + (pending.has(id) ? 1 : 0) })); }}
      onToggleFavorite={() => setCalls(value => ({ ...value, favorite: value.favorite + 1 }))}
      hiddenServerIds={new Set()} serverOverrides={{}} onRenameServer={() => {}} onHideServer={() => {}}
      query="" head={null} onRefreshSubscription={() => {}} onPingSubscription={() => {}}
      onOpenSettings={() => {}} onDeleteSubscription={() => {}} onMoveSubscription={() => {}}
      refreshingUrl={null} pingingUrl={null} updatedLabel={() => "Сегодня"} supportUrl={() => ""}
      siteUrl={() => null} order={[sub.url]}/>;
  return <main style={{ maxWidth: 720, margin: "24px auto", padding: 16 }}>
    <output id="server-context-counts">{JSON.stringify(calls)}</output>
    <button type="button" onClick={() => setInDialog(true)}>Проверить меню в диалоге</button>
    {inDialog ? <Dialog title="Серверы" closeLabel="Закрыть" onClose={() => setInDialog(false)}>{profiles}</Dialog> : profiles}
  </main>;
}
createRoot(document.getElementById("root")!).render(<Fixture/>);
