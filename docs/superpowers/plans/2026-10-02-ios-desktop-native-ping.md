# iOS / Desktop Native Ping Completion Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Unavailable helper skills are not claimed as executed.

**Goal:** Repair the Apple compiler blocker, retain ordinary iOS Xray fragment routes, and enable narrowly isolated desktop leastPing probes without measuring an arbitrary first member.
**Architecture:** Separate cross-file notification identity from private RootView actions. iOS projects only one provable proxy and an optional terminal fragment helper. Desktop runs its real selected balancer with a private observer, deny fallback and one total cancellation/deadline owner; unsupported graphs continue failing closed.
**Tech Stack:** Swift/Foundation, Go/libXray, Rust/Tokio, Python contracts, GitHub Actions.

## Task 1: Cross-file server action
Files: iosApp/Nimbo/NimboServerActions.swift; iosApp/Nimbo/RootView.swift; iosApp/Tests/test_profile_screen_contracts.py.
- [x] Add a contract asserting the ping notification is not private, then run `python iosApp/Tests/test_profile_screen_contracts.py` expecting failure.
- [x] Move only the ping identity to `extension Notification.Name { static let nimboPingServer = Notification.Name("com.nimbo.action.ping-server") }` in its own production file; keep unrelated actions private.
- [x] Add a macOS `swiftc` two-file smoke test accessing `.nimboPingServer` and run it in the existing --swift profile-contract step.
- [x] Mirror verified baseline files into the primary workspace, commit, push and dispatch IPA.

## Task 2: Ordinary iOS proxy projection
Files: iosApp/GoBridge/nimbo_diagnostic.go; iosApp/GoBridge/nimbo_diagnostic_test.go; iosApp/GoBridge/nimbo_diagnostic_projection.go.
- [x] Add tests preserving one proxy with a freedom fragment helper and rejecting redirects, nested/chained dialers, duplicate tags and multiple proxy routes.
- [x] Return a fresh `conf.Config` containing deny-default, the selected raw proxy under the forced diagnostic tag, and only its verified fragment helper. Reject unsupported binds and unsafe file/interface options on every retained outbound. Do not copy provider listeners, Env, routes or autonomous observers.
- [x] Run local pinned-upstream Go projection tests and existing request/cancel contracts; retain fail-closed balancer limitation explicitly.

## Task 3: Isolated desktop leastPing
Files: apps/ui/src-tauri/src/diagnostic_template.rs; apps/ui/src-tauri/src/diagnostics.rs; apps/ui/src-tauri/src/diagnostics_tests.rs; tools/release/run-native-ping-tests.py; .github/workflows/ci.yml.
- [x] Add tests for leastPing observer selectors/URL, deny fallback and rejection of unsupported strategies/direct/loopback fallback.
- [x] Accept only random/roundRobin/leastPing. For leastPing inject a new observer with exact selected-member prefixes and the current validated probe URL, never provider observer URLs; create a deny fallback when absent. Preserve raw members/transport. Do not flatten leastLoad or backup-loopback graphs.
- [x] Retry isolated health requests until the first success (the private Xray listener may return HTTP 503 during deny-fallback startup) through the authenticated private route, with 100ms pacing inside the existing total timeout/cancel owner; report only a successful request latency, never startup time or a first-member TCP result.
- [x] Run Linux native unit/integration tests and Clippy in WSL; no host services/TUN/routes changes.

## Task 4: Publication and evidence
Files: docs/platform-readiness-2026-10-02.md and this plan.
- [x] Run targeted iOS contracts and desktop frontend/native suites, record actual results.
- [x] Commit only these files, push draft PR78, dispatch IPA 37017139855 and artifact-only desktop packaging 37017144971 (publish=false) at 2f5041a.
- [x] Record unfinished native Mihomo TUN, iOS health balancers, desktop backup-loopback leastPing and real-device verification. No main merge and no universal feature-complete claim.

## Task 5: User-requested live server switching
Files: iosApp/Nimbo/VpnController.swift; iosApp/Nimbo/NimboProfileSelection.swift; iosApp/Nimbo/ProfilesContainerView.swift; iosApp/Nimbo/RootView.swift; iosApp/Tests/ProfileSelectionTests.swift; apps/ui/src/store.ts; apps/ui/tests/coreSelection.test.mjs; app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt.
- [x] iOS: centralize `selectServer(_ serverID: String) async throws -> (server: NimboSubscriptionServer, reconnecting: Bool)` under one selection owner. Validate the new core/config before stopping; await observed NetworkExtension stop for at most 15 seconds, then persist/stage and request start. A manual stop invalidates selection intent and cannot resurrect VPN. Keep success phrased as a start request, not verified connectivity.
- [x] Extend production `NimboProfileSelection.apply` with awaited `stop` and `restart` closures (empty defaults for staging-only consumers); Swift tests prove validation/stop failures never persist, and successful active selection orders validate → stop → persist → stage → restart.
- [x] Native and Compose iOS entry points call the same controller method. Duplicate selection is rejected while switching; same staged configuration is a no-op.
- [x] Desktop: retain native preflight and serialized reconnection; reject a second row action while an owned switch is pending. Test mismatch preservation and concurrent actions without frontend stop.
- [x] Android: enable the existing service-owned hot-switch path by default for preferences without a saved choice, preserving explicit user opt-out. Existing Mihomo live group selection stays live. No native Mihomo TUN support is invented on iOS.
- [x] Run targeted regression tests, mirror baseline-safe changes, rebuild and publish only on the existing draft branch. 2f5041a pushed and packaging dispatched; completed packaging/device verification are separate gates.

## Task 6: Embedded Mihomo live connection handoff
Files: tools/native/mihomo-core/mobile_session.go; runtime.go; selection.go; selection_test.go; mobile_test.go; API.md.
- [x] Reproduce live TCP graph lock contention with a real echo relay, then scope RLock to route/dial/registration. Register UDP before unlocking.
- [x] Capture already established exact-group connections before committing a manual/one-shot choice. Close only after successful changed selection, outside sockets.mu. Preserve same/invalid choices and unrelated groups.
- [x] Verify mobile real relay selection does not block until connection EOF; valid changed choice ends old relay and a fresh relay works. Desktop real CONNECT fixtures verify exact-group closure and unrelated group survival.
- [x] Run full verified-source Mihomo Go suite: 148 tests, zero failures. Linux native CLI runner: 2 tests, zero failures. Android 597 + frontend 83 + native Rust 134 passed.
- [ ] Rebuilt Android AAR is a separate native-artifact gate; do not attribute the new adapter to the existing debug APK. Apple/desktop builders consume the new source at packaging.
