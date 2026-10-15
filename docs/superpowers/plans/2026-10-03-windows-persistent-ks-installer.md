# Windows persistent Kill Switch and installer repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retain owned Mihomo Windows protection through reboot; repair offline installer TUN staging and initial layout.

**Architecture:** Persistent WFP provider/sublayer + persistent ALE block/loopback/DHCP rules, separate boot-time outbound transport block/loopback rules. Process/app and TUN-LUID permits remain static: never persist a recycled interface permit. Explicit Disconnect/reset releases protection; pipe loss, service stop and GUI exit only abandon the native lease. Protected v1 journals remain resettable. Installer verifies the bytes it already stages rather than calling an architecture-limited application installer; single-line path input and bounded responsive layout retain keyboard access and scrolling only when needed.

**Tech Stack:** Rust/windows-sys WFP; authenticated IPC; React/TypeScript/CSS; Python disposable Windows acceptance.

Execution: inline on the already authorized branch; the referenced superpowers execution skills are not installed. Never alter this user's network, services, ACL, proxy or reboot this host.

### Task 1: Lease exit semantics and persistent WFP objects
**Files:** `apps/service/src/mihomo_firewall.rs`, `apps/service/src/mihomo_windows.rs`, `apps/service/src/platform/mihomo_pipe.rs`, `crates/mihomo/src/helper_windows.rs`, `crates/mihomo/src/process.rs`, `apps/ui/src-tauri/src/commands.rs`.
- [ ] Add failing unit tests: `filter_flags(0) == FWPM_FILTER_FLAG_PERSISTENT`, `filter_flags(4) == 0`, `filter_flags(5) == FWPM_FILTER_FLAG_BOOTTIME`; every v2 key distinct; v1/v2 authorize only same SID; dropping a Lease yields EOF without a MihomoDown frame.
- [ ] Run `cargo test --locked -p nimbo-svc -p nimbo-mihomo`; expect missing filter_flags and Lease-drop assertion failure.
- [ ] Add private persistent provider (no serviceName, so disabling helper does not disable protection), persistent sublayer; `f.flags = filter_flags(role); f.providerKey = &mut provider;`. Role 5 blocks outbound transport, role 6 permits loopback; runtime roles 1 and 4 remain static.
- [ ] Change `Running::stop(explicit: bool)` and call release only when explicit; `Drop`, disconnected pipe and service shutdown pass false; authenticated MihomoDown passes true. Drop/exit closes the Lease without sending Down; ordinary disconnect still sends Down. GUI exit retains pending Kill Switch state.
- [ ] Release deletes exact journal-derived filters before sublayer/provider, supports legacy v1; stale/recycled identity still fails closed.
- [ ] Run service/IPC/Mihomo unit tests, fmt and scoped clippy; commit only listed files.

### Task 2: Offline TUN install and layout
**Files:** `apps/installer/src-tauri/src/payload.rs`, `apps/installer/src/main.tsx`, `apps/installer/src/universal.css`, `apps/installer/tests/layout.mjs`, `apps/installer/package.json`.
- [ ] Add failing Rust tests: exact staged file verifies; missing, empty or corrupted file is rejected. Run `cargo test --locked -p nimbo-installer --bin nimbo-installer`; expect missing verifier.
- [ ] Replace `run_status(Nimbo.exe, [--install-tun])` with direct SHA256/length verification of staged embedded TUN files; x86/ARM64 must not advertise unavailable native Mihomo TUN. Preserve health check and rollback.
- [ ] Replace textarea with `<input className="path-input" type="text" ... />`; one enclosing field, center-aligned browse button, no nested input border.
- [ ] Use 24px panel padding, 14px inter-block gap, no legacy top margins; compact two-column step details; actions stay accessible. Preserve vertical overflow for smaller windows/error/accessibility zoom; no horizontal overflow.
- [ ] Run npm build and browser assertions at 1080x680, 900x600 and 780x520 (install/update; themes; long path), plus short/narrow scrolling checks. Save screenshots and inspect before commit.

### Task 3: Disposable acceptance and delivery
**Files:** `crates/mihomo/tests/windows_tun.rs`, `scripts/ci/test-mihomo-windows-tun.py`, `apps/ui/src/pages/MihomoProfiles.tsx`, `apps/ui/src/lib/coreProfiles.ts`, `docs/platform-readiness-2026-10-02.md`.
- [ ] Extend original acceptance with friendly service-stop and dropped lease cases; assert real physical TCP/UDP DNS denied, journal version2, exact persistent/boot WFP flags, no persistent app/LUID permit; same-SID explicit reset restores baseline. No host execution.
- [ ] Push branch and run hosted native matrix + Windows installer build with publish=false. Inspect results, repair failures; do not claim success for running/failed checks.
- [ ] Copy changed files to primary only when prior SHA256 matches receipts/HEAD; record exact readback hashes. Update PR78 current checkpoint with source and build evidence.
- [ ] Clearly distinguish verified service-restart/registered boot policy from actual cold reboot and pre-BFE traffic: hosted runner reboot cannot be inferred from a filter registration test. Never report actual reboot tested without evidence.

References: https://learn.microsoft.com/en-us/windows/win32/fwp/basic-operation (atomic boot/runtime transition), https://learn.microsoft.com/en-us/windows/win32/fwp/object-management (object lifetimes), https://learn.microsoft.com/en-us/windows/win32/api/fwpmtypes/ns-fwpmtypes-fwpm_filter0 (persistent and boot-time flags are mutually exclusive).

## Execution checkpoint

- Implemented Tasks 1–2, read-only helper install-directory preflight, static installer errors and full-resolution taskbar icon.
- Unit tests: IPC12 / Mihomo38 / helper15 / installer16 PASS. Scoped Rust Clippy and fmt PASS. Desktop frontend86 and both production frontend builds PASS.
- Observed red policy test before implementation and red layout at 780x520; fixed layout, 55 production-asset browser cases PASS and dark/light screenshots visually inspected. Rust staged-payload pre-implementation red run was blocked by absent native packaging fixtures; only the post-implementation verification is claimed.
- Test-only AWG payload plus module-cache LICENSE bytes were temporarily staged, then original workspace bytes were restored exactly. No product helper/TUN/service commands run locally.
- Hosted native acceptance passed at f4fd5f0 (37120070519, all three targets); project and Android/shared checks passed. 26 intentional files mirrored with exact SHA256 receipts and PR78 updated. New Windows installer archive is still building. Actual cold reboot/pre-BFE traffic remains a separate hardware/VM gate.

- Hosted first Windows attempt failed driver compilation (standalone WFP API needs Win32_System_Rpc, hidden by workspace feature unification); reproduced/fixed locally and CI now prints rendered JSON diagnostics. New helper capability defaults false for old services; availability and actual KS start both enforce it. Abnormal dead-owner stop cannot clear the pending marker by returning false success.

- acf6460 hosted actual Both/WFP five-case acceptance passed; overall Windows gate failed unchanged immediate emergency reset on BUSY. Bounded mutex serialization (2s) preserves ownership/fail-closed checks and fixes that EOF race without changing the driver or deadline. Added cleanup-contention/timeout/poison unit coverage; local abandoned-stop regression red then green, repeated Disconnect remains idempotent. Directory preflight returns before ProgramData log setup.

- Final runtime evidence: f4fd5f0 / native matrix 37120070519 SUCCESS on Windows x64, Linux amd64 and Linux ARM64. Windows all five Both/WFP cases, exact persistent/boot/static flags and unchanged immediate emergency reset pass; original route/DNS/global firewall/proxy baselines restored, TCP21 / UDP associations7 / UDP7 / DNS7. Project37120072614 and Android/shared37120072608 SUCCESS. Installer37120072035 is running, not an available artifact. Documentation-only follow-up does not change the tested runtime or require an older installer to be relabeled. Cold reboot remains unobserved.
