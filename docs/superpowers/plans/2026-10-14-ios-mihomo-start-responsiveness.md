# iOS Mihomo start responsiveness implementation plan

> **For agentic workers:** Execute inline in the existing checkout; no delegation or new worktree is authorised. Preserve unrelated native-resource changes.

**Goal:** A slow/full Mihomo admission must not freeze the main run loop, swallow cancellation or authorise late VPN writes.

**Architecture:** Add a single-owner serial worker with bounded caller waiting. Timeout/cancel resumes the caller but retains native ownership until actual return. Prepare immutable data/engine/group/ping-identity snapshots off-main; a cheap storage revision prevents publishing old snapshots after profile changes. MainActor owns only UI/system orchestration. The native extension keeps its independent admission and lifecycle.

**Tech Stack:** Swift/Foundation, NetworkExtension, existing Go Mihomo C ABI, Python source contracts and Apple Swift tests.

---

### Task 1: Bounded read-only native worker
**Files:** `iosApp/Shared/NimboCoreValidationWorker.swift`, `iosApp/Tests/CoreValidationWorkerTests.swift`.
- [ ] Test a semaphore-blocked fake native operation while five MainActor UI ticks execute.
- [ ] Test timeout and cancel return to the UI before native returns; the second operation must throw `busy` until retirement, and a late result cannot authorise a staged write.
- [ ] Implement `perform<T: Sendable>(timeout: TimeInterval = 12, _ work: @escaping @Sendable () throws -> T) async throws -> T` and `cancelCurrent()` with exactly-once completion. Never hold the ownership lock during work or a continuation callback.
- [ ] Test successful retirement before the caller starts the next operation, and error propagation.

### Task 2: Immutable profile preparation and awaited admission
**Files:** `iosApp/Nimbo/NimboVpnProfilePreparation.swift`, `iosApp/Nimbo/VpnController.swift`, `iosApp/Nimbo/NimboConfigurationStore.swift`, `iosApp/Nimbo/NimboProfileSelection.swift`, `iosApp/Nimbo/RootView.swift`, matching CoreSelection policies, core-settings UI.
- [ ] Move Keychain/full-document/profile reading, native preflight, ad-rule validation and ping attribution to background preparation. Full documents retain precedence; do not convert Xray subscriptions into Mihomo.
- [ ] Return `{data, preference, engine, mihomoSelections, pingServerID, storeRevision}`. Verify revision/preference and cancelled attempt after awaits and before NetworkExtension writes.
- [ ] Await validation before any disconnect/selection/profile write. Make `NimboProfileSelection.apply(validate:)` async and retain the existing strict admission matrix.
- [ ] Cancel the worker waiter on manual disconnect/watchdog; retain real worker ownership until cleanup. Do not touch DNS defaults, native engines or published release assets.

### Task 3: Mihomo card reads and regression gates
**Files:** `iosApp/Nimbo/NimboMihomoControl.swift`, `iosApp/Nimbo/NimboMihomoProfileCard.swift`, new preparation stubs/Swift tests, source contracts, `scripts/ci/test-ios-reconnect.py`, registered iOS workflow.
- [ ] Fetch current full document and declared graph on a bounded background worker, returning typed groups/types rather than invoking native inspection on MainActor.
- [ ] Preserve request/source/session generation checks after awaits. Do not restore stale graphs or cached ping results.
- [ ] Execute worker/preparation/admission/profile-selection tests on macOS; run source guards locally and full iOS packaging in CI.
- [ ] Synchronise only changed files to the primary workspace after verifying the baseline. Report physical-device testing as pending; this patch removes verified blocking paths, not a proven reproduction of the user's device hang.
