# iOS / Desktop Native Ping Completion Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Unavailable helper skills are not claimed as executed.

**Goal:** Repair the Apple compiler blocker, retain ordinary iOS Xray fragment routes, and enable narrowly isolated desktop leastPing probes without measuring an arbitrary first member.
**Architecture:** Separate cross-file notification identity from private RootView actions. iOS projects only one provable proxy and an optional terminal fragment helper. Desktop runs its real selected balancer with a private observer, deny fallback and one total cancellation/deadline owner; unsupported graphs continue failing closed.
**Tech Stack:** Swift/Foundation, Go/libXray, Rust/Tokio, Python contracts, GitHub Actions.

## Task 1: Cross-file server action
Files: iosApp/Nimbo/NimboServerActions.swift; iosApp/Nimbo/RootView.swift; iosApp/Tests/test_profile_screen_contracts.py.
- [ ] Add a contract asserting the ping notification is not private, then run `python iosApp/Tests/test_profile_screen_contracts.py` expecting failure.
- [ ] Move only the ping identity to `extension Notification.Name { static let nimboPingServer = Notification.Name("com.nimbo.action.ping-server") }` in its own production file; keep unrelated actions private.
- [ ] Add a macOS `swiftc` two-file smoke test accessing `.nimboPingServer` and run it in the existing --swift profile-contract step.
- [ ] Mirror verified baseline files into the primary workspace, commit, push and dispatch IPA.

## Task 2: Ordinary iOS proxy projection
Files: iosApp/GoBridge/nimbo_diagnostic.go; iosApp/GoBridge/nimbo_diagnostic_test.go; iosApp/GoBridge/nimbo_diagnostic_projection.go.
- [ ] Add tests preserving one proxy with a freedom fragment helper and rejecting redirects, nested/chained dialers, duplicate tags and multiple proxy routes.
- [ ] Return a fresh `conf.Config` containing deny-default, the selected raw proxy under the forced diagnostic tag, and only its verified fragment helper. Reject unsupported binds and unsafe file/interface options on every retained outbound. Do not copy provider listeners, Env, routes or autonomous observers.
- [ ] Run local pinned-upstream Go projection tests and existing request/cancel contracts; retain fail-closed balancer limitation explicitly.

## Task 3: Isolated desktop leastPing
Files: apps/ui/src-tauri/src/diagnostic_template.rs; apps/ui/src-tauri/src/diagnostics.rs; apps/ui/src-tauri/src/diagnostics_tests.rs.
- [ ] Add tests for leastPing observer selectors/URL, deny fallback and rejection of unsupported strategies/direct/loopback fallback.
- [ ] Accept only random/roundRobin/leastPing. For leastPing inject a new observer with exact selected-member prefixes and the current validated probe URL, never provider observer URLs; create a deny fallback when absent. Preserve raw members/transport. Do not flatten leastLoad or backup-loopback graphs.
- [ ] Retry only isolated health startup failures through the authenticated private route, with 100ms pacing inside the existing total timeout/cancel owner; report only a successful request latency, never startup time or a first-member TCP result.
- [ ] Run Linux native unit/integration tests and Clippy in WSL; no host services/TUN/routes changes.

## Task 4: Publication and evidence
Files: docs/platform-readiness-2026-10-02.md and this plan.
- [ ] Run targeted iOS contracts and desktop frontend/native suites, record actual results.
- [ ] Commit only these files, push draft PR78, dispatch latest IPA and artifact-only desktop packaging (publish=false).
- [ ] Record unfinished native Mihomo TUN, iOS health balancers, desktop backup-loopback leastPing and real-device verification. No main merge and no universal feature-complete claim.
