# Native iOS NaiveProxy Implementation Plan

> **For agentic workers:** Execute this plan task-by-task with checked tests before integration. Work remains inline in this task.

**Goal:** Connect Naive HTTPS/QUIC share links through a real Chromium-based client inside the iOS Packet Tunnel.

**Architecture:** Pin SagerNet cronet-go and its Apple static libraries to one immutable commit. Add Naive to the existing single Go runtime, exposing an authenticated loopback SOCKS adapter to Xray's existing TUN/router. Do not fork a process, emulate Chromium with URLSession, disable TLS verification, or silently send unsupported UDP directly.

**Tech Stack:** Go, Cronet/Chromium, C ABI, Swift/NetworkExtension, macOS CI.

## Tasks
- [x] Add `tools/native/naive-core/{config,runtime,socks,client_cronet}.go` and tests. Parse credentials without treating plus as space; preserve peer/SNI separately from the dial address. Restrict local listener to loopback/authenticated CONNECT, bound connection counts and deadlines, close all sessions on stop. Tests reject wrong auth, UDP, malformed URLs and secret-bearing errors.
- [x] Pin cronet-go and Apple slice modules at `0d28acc44093df24b2526dea3d6ffefd6b0a54f0`, with Go sum verification; preserve notices. Add platform-specific imports and memory-bounded single-engine options. Run pure-Go unit tests and a Windows native loopback smoke using the matching checked DLL; never change system proxy/routes.
- [x] Add `iosApp/GoBridge/nimbo_naive.go` start/stop/health/reset exports; build with the other engines in one Go archive. Merge the matching Cronet static library into each Apple slice, use `iossimulator` tags for simulator slices, and explicitly link required Apple frameworks. Assert exported symbols and compile the real Swift bridge.
- [x] Add `iosApp/Shared/NimboNaiveConfiguration.swift` and `PacketTunnel/NaiveProxyBridge.swift`. Admit Naive under Auto/Xray only; preserve its identity. Integrate cleanup on failure/stop, watchdog health and socket-pool reset on network change/wake without replacing TUN.
- [x] Route intercepted DNS through TCP over the authenticated Naive path; reject other unsupported UDP rather than leaking it. Preserve user routing and ad-blocking. Test the generated production Xray configuration and safe credential redaction.
- [x] Run Go, native C ABI, Swift portable/source checks, and existing regressions; compile an unsigned IPA on macOS. Keep source and main workspace synchronized after concurrent-change checks. Do not publish a verified release without physical iPhone testing.

## Verification commands
`go test ./...` in `tools/native/naive-core` covers parser/lifecycle/SOCKS with injected test clients; `go test -tags with_naive,with_purego ./...` executes the same adapter with Cronet available on Windows. `python -m unittest discover -s iosApp/Tests -p 'test_*contracts.py'` checks native source integration. The Apple build compiles/links all device/simulator slices and runs native host tests before packaging.

## Boundaries
Naive's standard CONNECT transport carries TCP. Generic UDP is not claimed; DNS is carried over TCP. The official Chromium engine validates certificates with the platform trust store. Credentials stay in memory/private ignored smoke fixtures, never test snapshots or release notes. Physical iOS NetworkExtension routing, memory ceiling and Wi-Fi/cellular handoff need device verification even after CI passes.

## Execution evidence

Implementation is in the iOS Packet Tunnel, Go C ABI and pinned native module. The app-side isolated diagnostic path supports Naive without turning on VPN. On Windows: 9 native transport tests, pure Go tests/vet, merged bridge tests, and both private HTTPS fixtures through the complete diagnostic path passed. 101 iOS source contracts and 11 packet-flow source checks passed; these do not substitute for an iPhone test.

Final Apple build for commit `085374a010b7c2eb9b4fc1796a42dee208e7578d`: https://github.com/BBGGVP5/nimbo/actions/runs/37896866798 — passed. Native C ABI gates, the production Swift DNS configuration through real Xray, and production Swift bridge links for iPhone arm64 / simulator arm64 / simulator x86_64 passed. The downloaded IPA passed ZIP integrity and SHA-256 verification; both app and Packet Tunnel contain real Naive/Chromium symbols. All five native notice files match their repository bytes, and both VPN executables retain the packet-tunnel entitlement. Packaging now fails if these notices are omitted.

The pinned Cronet iOS archives contain an upstream feature-init/BUILD.gn mismatch. `tools/native/naive-apple-compat` documents the exact source evidence and guards its narrowly scoped unused-kqueue initializer with archive SHA-256 and symbol checks. The actual iOS I/O pump remains CFRunLoop; no TLS or routing checks were bypassed. Four guard regression tests pass.

IPA: `artifacts/ios/native-naive-20261009-085374a/Nimbo_v1.3.0-beta.1_ios_resignable.ipa` in the primary workspace. SHA-256: `fe4abee16eaa0ef88eed6578866bc4710030778debd7f9cdbb13d63f76def421`. This is a re-signable private test package, not an Apple-signed or device-validated release. Runtime code is mirrored to the primary workspace; its unrelated ping-test variant is preserved. No published release asset or Telegram post has been replaced.

## Remaining device / release acceptance
- Install with valid Packet Tunnel / Network Extension signing, then select an individual Naive share under Auto or Xray.
- Confirm page loading and DNS, idle/reconnect, stop, screen lock/wake, and Wi-Fi/cellular changes on a physical iPhone. Watch extension memory pressure.
- Verify an untrusted certificate cannot connect and unsupported UDP cannot escape through a direct fallback. Explicit user direct rules are separate.
- Verify offline and connected latency probes do not replace or stop the active tunnel.
- Finish the transitive Chromium notice audit before any public distribution. Host tests and successful compilation do not establish physical NetworkExtension behavior.
