# Offline Mihomo ping and subscription actions implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put the shared Ping action beside Refresh and enable real individual/bulk Mihomo checks while disconnected, without creating a VPN session.

**Architecture:** Active matching sessions retain `mihomo_delay`. Offline `mihomo_probe` resolves the native stored profile by ID plus expected source digest/revision, spawns an owned verified one-shot helper, and checks a named static outbound using HTTP GET; the original YAML never crosses frontend IPC. A restricted native `probeDesktop` operation constructs only named outbound adapters and temporary DNS, not a full runtime, providers, listeners, TUN, cache, routes or system proxy. Every result proves source identity and `vpnStarted:false`; connection intent cancels the owned child. Unsupported dynamic provider/chained outbounds fail explicitly instead of substituting Xray or direct traffic.

**Tech Stack:** Existing Go Mihomo adapter, Rust owned-child/wire/Tauri, React/TypeScript, Node and isolated Playwright tests.

---

### Task 1: One-shot native probe
**Files:** Create `tools/native/mihomo-core/desktop_probe.go`, `desktop_probe_test.go`; modify `runtime.go`, `cmd/nimbo-mihomo/main.go`, `API.md`; modify `crates/mihomo/src/process.rs`, `wire.rs`; create `crates/mihomo/tests/offline_probe.rs`.

- [ ] Write failing native tests: controlled HTTP CONNECT relay proves a named outbound, a nested fallback resolves a declared static member, DIRECT checks the endpoint, invalid/cyclic/missing names fail, timeout/cancel restore global state, and listener/TUN/provider fields never start or download anything. Run `go test -run TestDesktopProbe ./...` using the pinned 1.27.1 toolchain and verified source replacements.
- [ ] Add CLI `probe` accepting only `operation:"probeDesktop"`; it returns exactly one bounded envelope and exits. Native code checks timeout 100..30000, URL, graph names, supported outbound mapping, source hash, generation/cancellation and stopped ownership. Resolve static group leaves only; reject dynamic providers and dialer chains explicitly. Use `nimboProbeGET`, close each adapter, restore DNS/log/IPv6 and never execute native start.
- [ ] Rust adds `probe(binary, profile, name, url, timeout)` using `OwnedChild::spawn(binary,"probe")` and `.exchange(...)` under deadline. Decode `{delayMs,sourceSHA256,scope:"desktop-offline-probe",vpnStarted:false}` with `deny_unknown_fields`, source equality, integer latency and generation zero. `Drop` must reap the process on cancellation.
- [ ] Run wire/request unit tests plus ignored real-helper loopback tests after source building; never start the desktop app or change host network settings.

### Task 2: Read-only Tauri command
**Files:** Modify `apps/ui/src-tauri/src/mihomo_runtime.rs`, `apps/ui/src-tauri/src/lib.rs`, `apps/ui/src/lib/coreApi.ts`.

- [ ] Register `mihomo_probe(profileId, sourceDigest, revision, name, url, timeoutMs)`. Resolve original source privately, verify digest/revision and stopped connection ownership, acquire `CONNECTION_OPERATION`, use existing `await_current` to cancel/reap on Connect/Disconnect, and recheck profile identity after the reply. Do not invoke connect/stop, persist selections or alter proxy settings. Add pure native profile-admission tests.
- [ ] Frontend API accepts the identity and returns latency plus verified offline scope/hash/ownership proof. Reject missing or mismatched proof, not just bad latency.

### Task 3: Footer controls and shared queue
**Files:** Modify `useMihomoPing.ts`, `CoreSubscriptionControl.tsx`, `MihomoProxyGroups.tsx`, `core-subscription-groups.css`, `pages/profiles/SignalProfileCard.tsx`, `lib/mihomoPing.ts` and its tests.

- [ ] Lift one `useMihomoPing` instance to each subscription card. Pass it into the group browser. Remove the Ping action from category navigation; render one Ping/Cancel button next to Refresh with identical button styling. The footer action checks deduplicated members across visible subscription groups, including when collapsed.
- [ ] Scope offline results by profile digest/revision, subscription refresh version, URL/timeout and disconnected ownership. Active results still include session/generation/graph. On native readback/session/source changes, clear stale results and stop further dispatch. Per-server buttons work in both modes; selection remains disabled offline.
- [ ] Extend queue stale cancellation codes for changed source/revision and offline child cancellation. Do not publish raw native diagnostic text. Preserve sequential/cancel/draining behavior.

### Task 4: Verify and deliver
**Files:** Modify `apps/ui/tests/browser/cross-platform-polish.tsx`, `cross-platform-polish.mjs`.

- [ ] Add isolated offline probe IPC with proof and wrong-proof fixtures. Assert Ping/Refresh same footer/aligned dimensions, no navigation duplicate, individual and bulk offline requests, exact profile identity and no connect/select/legacy/export IPC. Cover collapsed footer ping, foreign session blocking, invalid proof, subscription/settings/session invalidation and cancellation.
- [ ] Run `npm test`, `npm run build`, `npm run test:polish`, `npm run test:settings`, `npm run test:traffic:browser`, Rust tests and pinned source-built native tests. Visually inspect controlled 360/1440 dark/light subscription screenshots.
- [ ] Verify normalized primary preimages against committed HEAD, mirror owned paths only with SHA256 receipts, commit/push the existing branch, verify remote/mirror content and dispatch artifact-only Windows release (`publish=false`). No installation, release publication or unrelated native-resource changes.

## Verification record

- Initial focused Go tests failed with `NOT_RUNNING`; initial Rust request/proof tests failed on missing implementation. Both were made green before integration.
- Pinned Go 1.27.1, source-checksum-verified dependency replacements: complete native Go suite and one-shot CLI source build passed. Test executable SHA256: `540a4c959ec96ed9d4ac46f722e6228e30bace71775d9b19c13816500a75171e` (not copied into installed/resources binaries).
- Rust Mihomo crate: 40 unit tests plus two offline request/proof tests passed. All four offline-helper tests passed with explicit opt-in, including actual GET latency and owned-child cancellation; Windows TCP reset is accepted as a valid process-termination readback. Existing host-TUN/WFP mutation tests stayed ignored.
- Tauri Mihomo runtime: five tests passed, including unchanged connection preflight, readonly offline admission and connection-intent cancellation.
- UI: 127 Node tests, 102 polish browser cases, 18 settings cases and 44 traffic cases passed; production TypeScript/Vite build passed.
- Controlled 360px dark and 1440px light subscription screenshots were visually inspected. Ping and Refresh are adjacent, equally tall footer actions; no category-row duplicate exists. The browser fixtures contain fake delays/profiles, not personal subscription measurements.
- Native checks used only controlled loopback HTTP/CONNECT endpoints; no Nimbo desktop app, host VPN, system proxy, route, firewall, installation or real subscription was changed by tests.
- Limits are explicit: static group checks use the first healthy declared leaf, not a saved/active selector; provider-dependent/filter/chained/runtime-owned nodes require a live graph and fail closed rather than silently testing a different protocol/route.
