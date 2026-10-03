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
- [x] Define bounded cancellation, deadline, backpressure and stop/join, with late callbacks rejected by generation. Verify all exported symbols on Apple (37024926758); live/pre-dispatch probe cancellation regression tests pass.

## Task 3: NetworkExtension integration
Files: iosApp/PacketTunnel/MihomoBridge.swift and PacketTunnelProvider.swift; both copies of NimboCoreSelection.swift; native profile screens.
- [x] Connect public readPackets/writePackets to owned Go device, with one outstanding read and bounded output batching; clear generation before stop.
- [ ] Native status/watchdog/path changes/ping/select/metrics must refer to the chosen real engine, not LibXray state. Rules/DNS must match platform settings, including disconnected startup and path changes without an Internet precondition.
- [ ] Enable Mihomo admission only with actual linked packet start/readiness. Validate compile, start/stop, per-group selection, TCP/UDP/DNS/IPv6, cancellation and no-direct behavior; real iPhone acceptance remains a distinct gate.

## Task 4: Desktop TUN
Files: apps/ui/src-tauri/src/mihomo_runtime.rs; apps/service/src/platform_linux.rs and platform.rs; crates/ipc/src/lib.rs; native adapter.
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

## Follow-up evidence

62c2cec adds real live probe cancellation, opaque source-scoped iOS ping cache,
settings-bound GET/HEAD/deadlines and desktop future-drop cancellation. 31 Rust
unit tests pass; four explicitly staged native-helper fixtures were not executed
in that local run. Apple 37027205853 passed Swift core admission and cache
round-trip; full IPA/package builds remain separate. Primary code was mirrored
only when baseline hashes matched, with unrelated local differences preserved.

## Desktop execution slice (privileged native boundary)
Files: tools/native/mihomo-core/desktop_runtime.go, desktop_tun_{linux,windows,stub}.go,
runtime.go, platform.go, cmd/nimbo-mihomo/main.go, desktop_runtime_test.go;
scripts/ci/test-mihomo-desktop-netns.py and its workflow.
- [x] Add failing source-admission tests: desktop-tun cannot be forged through Invoke;
  preflight retains exact source/hash and does not start listeners or mutate native globals;
  reject arbitrary FD/device/routes/table indices, source host listeners and unowned DNS.
- [x] Add trusted StartDesktopTun, explicit desktop IPv6, native system stack, full native
  rules/resolver/sniffer/providers and physical interface finder; do not use mobile protection
  callbacks or flatten into a TCP proxy. Only the privileged local owner may call this entry.
- [x] Use the upstream sing-tun listener for interface/routes/DNS lifetime. Validate that
  the named interface and reserved routing table/rules are vacant before construction.
  Partial startup closes the upstream listener and restores all captured process globals.
- [x] Add serve-tun framed startup with retained stdin lease: EOF/cancel/SIGTERM stops the
  actual native session and joins cleanup, including EOF during provider initialization.
  Leave legacy inspect/serve unchanged; never send secrets/configuration to log output.
- [x] Test actual Linux TCP/UDP/DNS and teardown in a separate network namespace with
  explicit disposable-CI opt-in. Never change the developer machine's routes/DNS.
- [ ] Integrate protected pinned binary installation + authenticated service lease and
  GUI endpoint ownership before marking TUN available. Windows pipe currently only
  authorizes legacy process operations: do not expose new root operations through its
  existing broad ACL. Artifact compilation alone is not TUN acceptance.

Desktop native evidence: 182 Go cases passed, one private provider fixture skipped;
default + with_gvisor builds tested, go vet passed. Three isolated namespace rounds
(nine native lifecycles) passed TCP4/TCP6/UDP/DNS-UDP/DNS-TCP/native selection/REJECT
without direct fallback/stale generation and exact route/rule restoration. Windows
native compiles; no Windows adapter acceptance or helper/UI availability flip.
Real test found/fixed the mobile-hook auto-binding suppression and empty UDP host.
The first repeated snapshot test also caught delayed physical IPv6 link-local DAD;
the fixture now disables automatic address generation before the baseline, without
filtering out route differences. Service hard-crash recovery remains explicit work.

Partial controller-start failure after TUN construction now has an actual namespace
rollback fixture. It caught asynchronous interface retirement; the owner lock stays
held until bounded device deletion and cleanup errors propagate to API/CLI failure.

## Authenticated desktop helper integration slice

Files: crates/ipc/src/lib.rs; apps/service/src/mihomo_owner.rs, build.rs and
platform_linux.rs; crates/mihomo/src/helper.rs, process.rs, wire.rs;
apps/ui/src-tauri/src/mihomo_runtime.rs, mihomo_build.rs; portable staging and
release workflow. Windows privileged commands stay denied until SID/pipe and
protected installation receive their own runtime tests, not an ACL shortcut.

- [x] Test framed source/binary identity commands and owner-specific cleanup;
  malformed source or wrong binary identity must not replace an active owner.
  `cargo test -p nimbo-ipc -p nimbo-mihomo -p nimbo-svc` must pass.
- [x] Native `validate-tun` performs pure preflightDesktopTun, never start.
  `nimbo-mihomo validate-tun < fixture.yaml` returns the original source SHA.
- [x] Root helper uses only a build-pinned installed inode and a root-private
  per-session home. No client executable/data path, inherited proxy/path override
  or native diagnostic message is trusted. Preflight precedes ownership transfer.
- [x] GUI retains an authenticated Unix connection as a lease; native stdin stays
  retained by the service. EOF closes stdin and joins native cleanup before owner
  release. Other clients and old leases cannot stop a newer owner.
- [x] Session runtime validates desktop-tun, tunReady, native generation, exact
  source and loopback controller. Live group/ping APIs use the same controller.
  Connect chooses TUN or proxy explicitly and keeps proxy recovery journal intact.
- [x] Stage source-built Linux native/helper and fingerprint resources together;
  missing/mismatched helper means unavailable, not silent Xray/TCP fallback.
- [x] Exercise real helper IPC in a disposable network+mount namespace: bad UID,
  source/hash preflight, ownership, client EOF, stale client and native traffic.
  No host installation, routes, DNS or GUI elevation. Native SIGKILL journal
  recovery, Windows adapter/DNS acceptance and external firewall remain gates.


### Broker evidence — 3 October

Linux isolated real helper/Rust Session acceptance passed: root UID admission,
wrong source/binary/client-path rejection before teardown, TCP4/TCP6/UDP/DNS,
live REJECT/readback, client EOF, old-client/new-owner isolation, service SIGKILL,
provider-start cancellation and exact route/rule restoration. No host installation.
GUI/installer Linux check --tests passes; Windows GUI 130 pass/4 opt-in skip,
portable frontend 85 pass/build. Production package CI remains a separate gate.
Windows TUN/SID authorization, external KS, native SIGKILL/power-loss journal and
physical iPhone/device acceptance are deliberately not checked off by this slice.

## Windows source checkpoint — 3 October 2026

The separate protected Windows pipe, SCM/SID admission, pinned Program Files
installation and GUI-owned native lease are implemented. Legacy broad-pipe network
commands stay denied. Native constructor exclusivity/rollback is patched and
source-verified. Local tests pass; disposable Windows adapter/DNS acceptance is
pending in the new workflow. See 2026-10-03-windows-mihomo-tun.md. Hardware roaming,
Windows hard crash, Both and persistent Kill Switch are not completed.
