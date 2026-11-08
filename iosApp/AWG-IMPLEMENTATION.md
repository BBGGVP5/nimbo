# iOS AmneziaWG implementation plan

**Goal:** Import raw single-peer AWG/WireGuard INI and carry PacketTunnel TCP/UDP through AWG 3.1.

**Architecture:** Compile pinned LibXray's existing cgo main and the AWG exports together. Xray retains the system TUN descriptor and routes to an authenticated loopback AWG SOCKS5 listener. On wake/network change only the AWG netstack is recreated on its existing port; network settings and Xray's TUN descriptor stay fixed.

**Scope:** iosApp, scripts/ci and the iOS workflow. No shared runtime edits or commits.

- [x] Add Foundation-only INI recognition/validation shared by importer and extension; preserve every AWG parameter in staged raw text.
- [x] Add C start/stop/stats exports and serialized Swift lifecycle, sanitized failures and statistics.
- [x] Dispatch AWG before share-link conversion; construct authenticated SOCKS egress and honor configured DNS/MTU.
- [x] Close AWG on startup failure, stop and sleep; recreate it on wake and network transitions without resetting TUN settings.
- [x] Add one XCFramework build from checksum-pinned LibXray sources and local awgcore, preserving CGoInvoke/CGoFree.
- [x] Run Windows contract checks and Go package checks; configure native Swift tests and archive validation in macOS CI.
- [ ] Execute macOS C/Swift linking and entitled device traffic/lifecycle tests.

Device verification still requires an entitled iOS installation and reachable AWG peer: TCP, DNS/UDP, wrong credentials, repeated connect/disconnect, sleep/wake and Wi-Fi/cellular transitions. macOS must compile all XCFramework slices and the IPA.
