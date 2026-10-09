# Native iOS NaiveProxy Implementation Plan

> **For agentic workers:** Execute this plan task-by-task with checked tests before integration. Work remains inline in this task.

**Goal:** Connect Naive HTTPS/QUIC share links through a real Chromium-based client inside the iOS Packet Tunnel.

**Architecture:** Pin SagerNet cronet-go and its Apple static libraries to one immutable commit. Add Naive to the existing single Go runtime, exposing an authenticated loopback SOCKS adapter to Xray's existing TUN/router. Do not fork a process, emulate Chromium with URLSession, disable TLS verification, or silently send unsupported UDP directly.

**Tech Stack:** Go, Cronet/Chromium, C ABI, Swift/NetworkExtension, macOS CI.

## Tasks
- [ ] Add `tools/native/naive-core/{config,runtime,socks,client_cronet}.go` and tests. Parse credentials without treating plus as space; preserve peer/SNI separately from the dial address. Restrict local listener to loopback/authenticated CONNECT, bound connection counts and deadlines, close all sessions on stop. Tests reject wrong auth, UDP, malformed URLs and secret-bearing errors.
- [ ] Pin cronet-go and Apple slice modules at `0d28acc44093df24b2526dea3d6ffefd6b0a54f0`, with Go sum verification; preserve notices. Add platform-specific imports and memory-bounded single-engine options. Run pure-Go unit tests and a Windows native loopback smoke using the matching checked DLL; never change system proxy/routes.
- [ ] Add `iosApp/GoBridge/nimbo_naive.go` start/stop/health/reset exports; build with the other engines in one Go archive. Merge the matching Cronet static library into each Apple slice, use `iossimulator` tags for simulator slices, and explicitly link required Apple frameworks. Assert exported symbols and compile the real Swift bridge.
- [ ] Add `iosApp/Shared/NimboNaiveConfiguration.swift` and `PacketTunnel/NaiveProxyBridge.swift`. Admit Naive under Auto/Xray only; preserve its identity. Integrate cleanup on failure/stop, watchdog health and socket-pool reset on network change/wake without replacing TUN.
- [ ] Route intercepted DNS through TCP over the authenticated Naive path; reject other unsupported UDP rather than leaking it. Preserve user routing and ad-blocking. Test the generated production Xray configuration and safe credential redaction.
- [ ] Run Go, native C ABI, Swift portable/source checks, and existing regressions; compile an unsigned IPA on macOS. Keep source and main workspace synchronized after concurrent-change checks. Do not publish a verified release without physical iPhone testing.

## Verification commands
`go test ./...` in `tools/native/naive-core` covers parser/lifecycle/SOCKS with injected test clients; `go test -tags with_naive,with_purego ./...` executes the same adapter with Cronet available on Windows. `python -m unittest discover -s iosApp/Tests -p 'test_*contracts.py'` checks native source integration. The Apple build compiles/links all device/simulator slices and runs native host tests before packaging.

## Boundaries
Naive's standard CONNECT transport carries TCP. Generic UDP is not claimed; DNS is carried over TCP. The official Chromium engine validates certificates with the platform trust store. Credentials stay in memory/private ignored smoke fixtures, never test snapshots or release notes. Physical iOS NetworkExtension routing, memory ceiling and Wi-Fi/cellular handoff need device verification even after CI passes.

## Execution evidence

Implementation is in the iOS Packet Tunnel, Go C ABI and pinned native module. The app-side isolated diagnostic path supports Naive without turning on VPN. On Windows: 9 native transport tests, pure Go tests/vet, merged bridge tests, and both private HTTPS fixtures through the complete diagnostic path passed. 100 iOS source contracts and 11 packet-flow source checks passed; these do not substitute for an iPhone test.

Apple build for commit 9d78854: https://github.com/BBGGVP5/nimbo/actions/runs/37891379768 . Swift prerequisite checks passed; native archive/IPA build is still pending at this checkpoint. No published release asset or Telegram post has been replaced. Runtime code is mirrored to the primary workspace; its unrelated ping-test variant is preserved.
