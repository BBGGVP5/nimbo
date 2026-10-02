# Native Mihomo Tunnel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans task by task. Unavailable helper skills are not claimed as executed.

**Goal:** Add actual Mihomo packet ownership on iOS and managed native TUN on desktop, without a hidden Xray/direct fallback or claims of protocol parity based only on a build.
**Architecture:** iOS uses public NEPacketTunnelFlow raw IP read/write callbacks connected to a bounded session-owned Go packet device and the pinned Mihomo/gVisor dispatcher. Desktop uses the platform service/helper for privileged interface/routes/DNS ownership, not a root GUI. Keep existing production admission closed until each platform's data plane and lifecycle are actually integrated.
**Tech Stack:** Swift/NetworkExtension, Go/Mihomo/sing-tun/gVisor, Rust service IPC, Windows networking and Linux systemd/Netlink.

## Task 1: Portable packet-flow device
Create tools/native/mihomo-core/packet_flow.go and packet_flow_test.go (with_gvisor).
- [x] Add a real raw-IP ingress/egress backend using gVisor channel.Endpoint, not an FD scan or a fake ready flag.
- [x] Validate IPv4/IPv6 framing, exact lengths and MTU; cap queued egress to 128 packets; never retain an Apple callback pointer or a foreign buffer.
- [x] Session cancellation/close wakes pending reads and rejects late writes. Concurrent stop must not race endpoint closure. Test malformed packets, copied data, bounded queue, stale packets and cancellation.
- [x] Run a real packet-stack fixture: client gVisor TCP/DNS exchanges travel through the backend and Mihomo handlers. Tests use only loopback, no host VPN/DNS/routes.

## Task 2: iOS native ownership and ABI
Files: tools/native/mihomo-core/runtime.go, mobile.go, platform.go; iosApp/GoBridge/nimbo_mihomo*.go; scripts/ci/test-libxray-mihomo-cabi.py.
- [x] Introduce a versioned session/generation-owned packet start/input/output ABI in the existing Go runtime. No second Go archive and no borrowed user pointers.
- [x] Wire source-preserving Mihomo runtime/protocol dispatch, managed DNS/IPv6/rules/groups and platform egress ownership. Do not broaden a narrow protocol admission silently.
- [ ] Define bounded cancellation, deadline, backpressure and stop/join, with late callbacks rejected by generation. Verify all exported symbols on Apple.

## Task 3: NetworkExtension integration
Files: iosApp/PacketTunnel/MihomoBridge.swift and PacketTunnelProvider.swift; both copies of NimboCoreSelection.swift; native profile screens.
- [x] Connect public readPackets/writePackets to owned Go device, with one outstanding read and bounded output batching; clear generation before stop.
- [ ] Native status/watchdog/path changes/ping/select/metrics must refer to the chosen real engine, not LibXray state. Rules/DNS must match platform settings, including disconnected startup and path changes without an Internet precondition.
- [ ] Enable Mihomo admission only with actual linked packet start/readiness. Validate compile, start/stop, per-group selection, TCP/UDP/DNS/IPv6, cancellation and no-direct behavior; real iPhone acceptance remains a distinct gate.

## Task 4: Desktop TUN
Files: apps/ui/src-tauri/src/mihomo_runtime.rs; crates/nimbo-svc platform engines; IPC; native adapter.
- [ ] Add privileged owned TUN and route/DNS lifecycle through the existing platform helper. Preflight permissions, missing native binary and unsupported config before replacing the working session.
- [ ] Test rollback, manual stop, service/client disconnect, stale generation, DNS restore and adapter replacement in guarded disposable CI hosts.
- [ ] Linux System Proxy requires an explicit platform backend; do not report it as implemented by adding a switch.

## Task 5: Artifact and protocol acceptance
- [ ] Rebuild Android merged AAR, Apple archive/IPA, Windows/Linux packages; retain source fingerprints and signatures without reading the user's signing key.
- [ ] Build a core/platform protocol + transport matrix and run real synthetic fixtures before widening UI/site claims. Mesh removal stays deliberate; unsupported protocols are not silently relabeled.
- [ ] Document measured memory and remaining device validation, publish only draft branch artifacts, no main merge/public release.

## Concrete packet integration slice
- Add packet_runtime.go (gVisor) plus no-gVisor stub and shared packet_api.go. Start uses a trusted local entry only. Native sing_tun.ListenerHandler dispatches to the actual tunnel, not the legacy IPv4 fixture matcher.
- Binary ABI: StartIOSPacketFlowV1(JSON); WriteIOSPacketV1(generation, pointer, length); ReadIOSPacketV1(generation, caller buffer, capacity, timeoutMs). Input copied; output caller-owned; 1500-byte MTU, 1-second maximum blocking read; generation validated both sides of read.
- Reuse mobile ownership validation/parser, force process mode off on iOS and reject process/UID rules. No listener, route, firewall, FD discovery or hidden system DNS fallback.
- Add Swift MihomoPacketBridge + public NEPacketTunnelFlow pump + underlying physical interface socket binding, engine-specific lifecycle/status/path cleanup. Preserve legacy FD ABI gate.
- Native lifecycle/packet/DNS fixtures, source contracts, Apple archive symbol gate and unsigned IPA CI; no claim of real-device acceptance from compilation.

## Verified implementation checkpoint (2026-10-02)
Native packet runtime and Swift provider/import/group controls are implemented in source. 159 native Go cases passed; four full-runtime ownership/DNS/IPv4+IPv6 TCP/live-selection fixtures repeated ten times. Pure source checks are distinct from Apple compilation and phone runtime verification. The merged archive now explicitly builds with_gvisor in all three Apple slices, adds packet C symbols and links the production Swift bridge. New IPA build required; no real iPhone acceptance yet. Desktop managed TUN is still task 4, not implicitly enabled by this iOS packet adapter.
