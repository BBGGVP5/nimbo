# iOS Session Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans task by task if available. Unavailable helper skills are not claimed as executed.

**Goal:** Reject mismatched native readiness, reset Mihomo sockets on physical path return, and close failed Xray recovery rather than leave a dead connected tunnel.

**Architecture:** A Foundation-only policy validates the pinned native envelope/source/generation and models physical binding transitions. NetworkExtension keeps sole route ownership. Failure cleanup and delayed wake checks remain serialized and generation-bound; no Internet precondition or engine fallback is added.

**Tech Stack:** Swift 5/Foundation/NetworkExtension, Python source contracts, macOS Swift executables and full IPA CI.

## Task 1: Executable ownership/path regression tests
Files: `iosApp/Shared/NimboMihomoSessionPolicy.swift`, `iosApp/Tests/MihomoSessionPolicyTests.swift`, `scripts/ci/test-ios-packet-flow.py`.
- [x] Add executable cases for valid readiness and wrong API/request ID/core/source/generation/owner/failed/TUN status. Use fixture replies with `apiVersion=1`, `requestId=fixture`, `generation=7`, exact pinned core and a 64-character source hash. Mutating any ownership field must reject readiness.
- [x] Add transition cases: initial missing path -> first physical binding (no reset before startup), unchanged -> unchanged (no reset), Wi-Fi -> missing -> Wi-Fi (two resets), Wi-Fi -> cellular (reset), IPv6 capability change (reset).
- [ ] Execute `python scripts/ci/test-ios-packet-flow.py --swift` on macOS. Keep source-only runs labelled as source contracts, not Swift runtime tests.

## Task 2: Apply pure policy to actual bridge
Files: `iosApp/PacketTunnel/MihomoPacketBridge.swift`, `scripts/ci/build-libxray-awg-apple.sh`.
- [x] Compute SHA256 from original UTF-8 data before native start; commit generation only after exact reply validation. Ordinary commands validate request ID and captured generation before returning.
- [x] The binder updates a policy state under its lock. Physical disappearance/return updates binding before notifying the provider. Never use `NWPath.status` as a dialing admission gate.
- [x] Cancel only the current native probe before scheduling network-change reset; preserve persisted ping values and source selections.
- [x] Include the policy in all three real Swift/C archive link checks. Run source tests plus full unsigned IPA CI, not a fake C ABI stub.

## Task 3: Failure cleanup and generation-safe wake
File: `iosApp/PacketTunnel/PacketTunnelProvider.swift`.
- [x] Create serialized failure cleanup: invalidate lifecycle generation, cancel pending probes, stop timers/path observers, stop actual cores, clear retained startup config/FD/assets and flags, then cancel system tunnel.
- [x] Apply cleanup after failed watchdog recovery, Mihomo pump failure, and failed wake/restart. Capture wake generation before a three-second delayed check so an old callback cannot restart a replacement session.
- [x] Wake a healthy Mihomo session with `networkChanged` to retire pre-sleep pools. Stop/error releases retained Xray configuration to reduce idle memory without invented RSS figures.
- [x] Add source-contract assertions for cleanup callers and delayed generation guard. Run `python scripts/ci/test-ios-packet-flow.py`, `python scripts/ci/test-ios-mihomo-source.py`, existing profile/ping/on-demand contracts and `git diff --check`.

## Task 4: Integration and honest artifact status
- [ ] Mirror only these intentional source files to the primary workspace, preserving unrelated Android edits. Commit to the current draft branch and start a new IPA build.
- [ ] Record prior Linux x64/ARM64 real helper/TUN CI success separately. Keep Windows TUN/native SIGKILL recovery and physical iPhone acceptance explicitly open; this plan does not implement them.
