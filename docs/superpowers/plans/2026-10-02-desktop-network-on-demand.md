# Desktop network on-demand implementation plan

> **For agentic workers:** Execute inline in the existing checkout; preserve unrelated files.

**Goal:** Add opt-in network-specific desktop connection rules and improve iOS rule-saving lifecycle.

**Architecture:** Native Windows/NetworkManager observations feed a pure policy. A sequential app-owned monitor uses the existing connection lock and intent generation, the last successfully connected target/core, persistent manual pause and bounded retries. No HTTP/DNS reachability precondition. Settings stay local to the device; disabled by default.

**Tech Stack:** Rust/Tauri, Windows WLAN/IP Helper, Linux NetworkManager nmcli, React, SwiftUI.

### Policy and detector
- [x] Create `apps/ui/src-tauri/src/on_demand/policy.rs`: validated settings, exact case-sensitive SSIDs, trusted-network decision, unknown/mixed-network behavior, manual pause and retry/debounce tests.
- [x] Create `on_demand/network.rs`: Windows operational gateway adapters plus WLAN current SSID; Linux bounded `nmcli` machine output without shell interpolation or scanning. No detected SSIDs in logs/status. Unknown/unsupported observation never authorizes disconnect.

### Native lifecycle
- [x] Create `on_demand/mod.rs`: durable settings command and status; one sequential monitor with five-second observation and two matching samples. Use `CONNECTION_OPERATION`, recheck intent/settings before mutations, exponential retry capped at five minutes.
- [x] Extend persisted state with default-disabled local policy and saved target. Manual disconnect persists pause; successful explicit connection captures the target and resumes. Coordinate launch, wake and Auto recovery with rules. Disabling rules leaves the current connection alone.
- [x] Add native behavior tests for validation, state migration/round trip, policy transitions and cancellation/retry, not UI string mirrors.

### Product surfaces and iOS
- [x] Add `OnDemandSetting.tsx`, typed API and compact existing-style settings card: network switches, one SSID per line, saved destination, armed/paused/wait/error status, explicit Save/Resume. Explain that desktop must remain running and Linux needs NetworkManager.
- [x] iOS: prevent duplicate save/dismiss while writing, recover editor state from staged system rules when local defaults are missing, preserve a deliberately paused policy. Extend executable Swift persistence tests and existing SDK checks.

### Verification and delivery
- [x] Run Rust policy/runtime tests, frontend build/tests and Clippy. Inspect the actual rendered settings page at normal/narrow widths. Run iOS contracts and macOS IPA workflow; keep device-only network transitions explicit.
- [x] Mirror verified iOS files into primary checkout after baseline comparison, update readiness, commit explicit paths, push PR78 and wait for CI. No main merge or device VPN manipulation.

### Release packaging regression found during verification
- [x] Build/stage pinned Mihomo in Windows CI using the installed Python/Go toolchains.
- [x] Include the exact Mihomo helper, frozen adapter source and licenses in the x64 custom installer; verify hashes and roll back with the application on failure. Other architectures remain explicitly unsupported by Mihomo.
- [x] Exercise packaging tests and dispatch artifact-only desktop release builds; do not publish or merge main.

- [ ] Wait for the full artifact-only desktop release run and inspect completed packages; native preparation passed, final installer packaging is still running.
