# iOS Mihomo start responsiveness implementation plan

> **For agentic workers:** Execute inline in the existing checkout; no delegation or new worktree is authorised. Preserve unrelated native-resource changes.

**Goal:** A slow/full Mihomo admission must not freeze the main run loop, swallow cancellation or authorise late VPN writes.

**Architecture:** Add a single-owner serial worker with bounded caller waiting. Timeout/cancel resumes the caller but retains native ownership until actual return. Prepare immutable data/engine/group/ping-identity snapshots off-main; a cheap storage revision prevents publishing old snapshots after profile changes. MainActor owns only UI/system orchestration. The native extension keeps its independent admission and lifecycle.

**Tech Stack:** Swift/Foundation, NetworkExtension, existing Go Mihomo C ABI, Python source contracts and Apple Swift tests.

---

### Task 1: Bounded read-only native worker
**Files:** `iosApp/Shared/NimboCoreValidationWorker.swift`, `iosApp/Tests/CoreValidationWorkerTests.swift`.
- [x] Test a semaphore-blocked fake native operation while five MainActor UI ticks execute.
- [x] Test timeout and cancel return to the UI before native returns; the second operation must throw `busy` until retirement, and a late result cannot authorise a staged write.
- [x] Implement `perform<T: Sendable>(timeout: TimeInterval = 12, _ work: @escaping @Sendable () throws -> T) async throws -> T` and `cancelCurrent()` with exactly-once completion. Never hold the ownership lock during work or a continuation callback.
- [x] Test successful retirement before the caller starts the next operation, and error propagation.

### Task 2: Immutable profile preparation and awaited admission
**Files:** `iosApp/Nimbo/NimboVpnProfilePreparation.swift`, `iosApp/Nimbo/VpnController.swift`, `iosApp/Nimbo/NimboConfigurationStore.swift`, `iosApp/Nimbo/NimboProfileSelection.swift`, `iosApp/Nimbo/RootView.swift`, matching CoreSelection policies, core-settings UI.
- [x] Move Keychain/full-document/profile reading, native preflight, ad-rule validation and ping attribution to background preparation. Full documents retain precedence; do not convert Xray subscriptions into Mihomo.
- [x] Return `{data, preference, engine, mihomoSelections, pingServerID, storeRevision}`. Verify revision/preference and cancelled attempt after awaits and before NetworkExtension writes.
- [x] Await validation before any disconnect/selection/profile write. Make `NimboProfileSelection.apply(validate:)` async and retain the existing strict admission matrix.
- [x] Cancel the worker waiter on manual disconnect/watchdog; retain real worker ownership until cleanup. Do not touch DNS defaults, native engines or published release assets.

### Task 3: Mihomo card reads and regression gates
**Files:** `iosApp/Nimbo/NimboMihomoControl.swift`, `iosApp/Nimbo/NimboMihomoProfileCard.swift`, new preparation stubs/Swift tests, source contracts, `scripts/ci/test-ios-reconnect.py`, registered iOS workflow.
- [x] Fetch current full document and declared graph on a bounded background worker, returning typed groups/types rather than invoking native inspection on MainActor.
- [x] Preserve request/source/session generation checks after awaits. Do not restore stale graphs or cached ping results.
- [x] Execute worker/preparation/admission/profile-selection tests on macOS; run source guards locally and full iOS packaging in CI.
- [x] Synchronise only changed files to the primary workspace after verifying the baseline. Report physical-device testing as pending; this patch removes verified blocking paths, not a proven reproduction of the user's device hang.

## Verification

- Canonical source regression suite: 115 passed; primary workspace subset plus adapted attribution guard: 96 passed. These are source assertions, not device tests.
- [Apple Swift policies and blocking/cancellation tests](https://github.com/BBGGVP5/nimbo/actions/runs/38607448073): passed at `a8ee155`.
- [Full iOS build and Apple regression gates](https://github.com/BBGGVP5/nimbo/actions/runs/38607777340): passed at runtime revision `e7bfd64526ef8b38d4920aa9a0a743e80d06de41`.
- Native code, DNS/routing defaults and public Beta assets were not changed. Full source bytes and strict admission remain authoritative; plain VLESS/SS entries are not silently converted into Mihomo.
- A physical iPhone reproduction/retest is still pending. Removing known synchronous MainActor paths and passing synthetic blocking tests is not proof that every reported device freeze is fixed.
