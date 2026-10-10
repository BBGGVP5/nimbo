import { useState } from "react";
import { createRoot } from "react-dom/client";
import { SignalProfileCard } from "../../src/pages/profiles/SignalProfileCard";
import { messages } from "../../src/lib/i18n";
import type { Subscription } from "../../src/lib/api";
import "flag-icons/css/flag-icons.min.css";
import "../../src/styles.css";
import "../../src/universal.css";
import "../../src/secondary.css";
import "../../src/preview-parity.css";
import "../../src/preview-fonts.css";

// Isolated production component: no hydrate, native IPC, network, or real profiles.
const sub: Subscription = { url: "https://fixture.invalid/subscription", name: "Pointer fixture", servers: [],
  fetched_at: 1, info: { upload: 0, download: 1024, total: 4096 }, meta: { description: "Provider information in a portal" } };

function Fixture() {
  const [collapsed, setCollapsed] = useState(true);
  const [busy, setBusy] = useState(false);
  const [counts, setCounts] = useState({ toggles: 0, ping: 0, refresh: 0, menu: 0, server: 0 });
  const count = (key: keyof typeof counts) => setCounts(value => ({ ...value, [key]: value[key] + 1 }));
  return <main style={{ maxWidth: 640, margin: "24px auto", padding: 16 }}>
    <h1>Subscription tile pointer regression</h1>
    <p>Click tile regions, actions, disabled targets and expanded server space. Counters expose double toggles.</p>
    <label style={{ display: "block", padding: "12px 0" }}><input type="checkbox" checked={busy} onChange={event => setBusy(event.target.checked)}/> Ping in progress / disable refresh</label>
    <output id="counts" style={{ display: "block", padding: "12px 0" }}>{JSON.stringify({ ...counts, collapsed, busy })}</output>
    <SignalProfileCard labels={messages.en} sub={sub} serverCount={1} collapsed={collapsed}
      onToggleCollapsed={() => { count("toggles"); setCollapsed(value => !value); }}
      onPing={() => count("ping")} onRefresh={() => count("refresh")} pinging={busy} refreshing={busy}
      onSettings={() => count("menu")} onDelete={() => count("menu")}
      onMoveUp={() => count("menu")} onMoveDown={() => count("menu")} canMoveUp={false} canMoveDown={false}
      updatedLabel="Updated today">
      <div style={{ padding: 12 }}>
        <button type="button" className="signal-btn" onClick={() => count("server")}>Select server</button>
        <button type="button" className="signal-btn" disabled onClick={() => count("server")}>Disabled server</button>
        <p style={{ padding: "20px 0" }}>Blank server region — must not collapse</p>
      </div>
    </SignalProfileCard>
  </main>;
}
createRoot(document.getElementById("root")!).render(<Fixture/>);
