# Windows Mihomo TUN Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Run the native Windows Mihomo TUN through an authenticated, protected LocalSystem broker, with cancellation and joined teardown.

**Architecture:** Keep the legacy process-kill pipe unchanged and deny network commands there. A separate local-only pipe grants interactive users data access, not permission to create server instances; the client verifies its server PID against SCM, and the server identifies the client token after reading each request. Only a compiled-hash core in an ACL-protected Program Files directory is executed; native Wintun owns its own adapter, DNS, routes and dynamic WFP session.

**Tech Stack:** Rust, Tokio named pipes, Windows SCM/security/job APIs, pinned Go Mihomo/sing-tun/Wintun, PowerShell/Python acceptance tests.

---

Execution is inline in the existing branch. The two required execution sub-skills are not installed; no claim of using them and no separate chat is created. Never install a service or create a TUN on the user's PC during verification. Live acceptance runs only in a disposable GitHub Windows VM.

### Task 1: Windows trust and pipe boundary
**Files:** Create `crates/ipc/src/windows.rs`; modify `crates/ipc/src/lib.rs`, `crates/ipc/Cargo.toml`.
- [x] Write and run non-network tests for client mask, protected DACL and ownership policy before implementing it.
```rust
assert_eq!(CLIENT_ACCESS & 4, 0); // FILE_CREATE_PIPE_INSTANCE is prohibited
assert!(!trusted_writer("S-1-1-0"));
assert!(trusted_writer("S-1-5-18"));
```
Run `cargo test --locked -p nimbo-ipc`; expected initial unresolved symbols, then passing tests.
- [x] Implement fixed Known Folder paths, no-reparse/protected ACL checks, framed Tokio pipe I/O, first-instance server ownership, SCM PID authentication and per-request token identity. Client opens with identification-only SQOS; service always reverts before privileged work.
```rust
if pipe_server_pid != scm_service_pid || scm_service_pid == 0 {
    return Err("HELPER_AUTH_FAILED".into());
}
```
- [x] Run Windows unit tests and `cargo clippy --locked -p nimbo-ipc --all-targets -- -D warnings`.

### Task 2: Protected Windows native broker
**Files:** Create `apps/service/src/mihomo_windows.rs`, `apps/service/src/platform/mihomo_pipe.rs`; modify `apps/service/src/platform.rs`, `apps/service/src/main.rs`, `apps/service/build.rs`, `apps/service/Cargo.toml`.
- [x] Test binary/source digest rejection and owner isolation without spawning native networking.
```rust
if request.binary_sha256 != expected_hash() { return Err("CORE_HASH_MISMATCH".into()); }
if running.client != client { return Err("LEASE_NOT_OWNED".into()); }
```
- [x] Install the helper and source-built core into a protected fixed directory before SCM registration. Never accept a client executable, data directory, inherited environment or service-install command over IPC.
- [x] Start `serve-tun` under a kill-on-close Job before sending its start frame. Retain stdin and authenticated client connection for the full lease. On EOF/cancellation close stdin, wait for native teardown; forced termination is an error, never cleanup success. Retain failed owner state.
- [x] Run `cargo test --locked -p nimbo-svc` and scoped Clippy.

### Task 3: Native Windows rollback and exclusivity
**Files:** Modify `tools/native/mihomo-core/mihomo-rule-journal.patch`, `sing-tun-rule-journal.patch`, `desktop_tun_windows.go`; add Windows native ownership tests; modify `scripts/ci/prepare-mihomo-merged.py`.
- [x] Add trusted, non-YAML Windows-exclusive adapter option. Refuse upstream fallback adopting an existing adapter. Roll back every partially constructed Wintun session, adapter and dynamic WFP handle on failure.
```go
if options.NimboWindowsExclusive { return nil, createErr }
```
- [x] Verify staged dependency allowlist and build pinned Windows source with `python scripts/ci/build-mihomo-desktop.py --target windows/amd64 --output <scratch>`; host unit tests and vet must pass. Do not edit Go module cache.

### Task 4: GUI-owned Windows TUN lease
**Files:** Create `crates/mihomo/src/helper_windows.rs`; modify `lib.rs`, `process.rs`, `apps/ui/src-tauri/src/mihomo_runtime.rs`, `helper.rs`, `apps/ui/src/lib/coreProfiles.ts`.
- [x] Extend the existing Rust session controller to Windows authenticated pipe leases. Async cancellation must close the actual connection; no worker thread remains with an owning handle.
- [x] Prepare helper via an explicit installation action, report availability only when protected native SHA and service match. Permit TUN without claiming persistent Kill Switch or Both mode. Preserve source, selections, generation and preflight-before-disconnect semantics.
- [x] Run desktop tests, frontend tests/build and Rust format checks.

### Task 5: Disposable native acceptance and delivery
**Files:** Create `scripts/ci/test-mihomo-windows-tun.py`; modify `.github/workflows/mihomo-desktop-tun.yml`, readiness/verification docs.
- [x] Guard live tests with `NIMBO_DISPOSABLE_WINDOWS=1` and `GITHUB_ACTIONS=true`; snapshot physical DNS/routes before running the fixed service, ensure finally-stop/uninstall. Test real TCP/UDP, controller selection, retained lease, EOF, repeated connect/stop, adapter retirement and unchanged physical DNS.
- [x] Push only intentional source files to existing draft PR78, mirror baseline-matching files to primary with a receipt, launch native acceptance and Windows packages with `publish=false`.
- [x] Report actual passing results separately from pending VM/hardware acceptance. Do not state iOS/all-platform work is complete.

## Execution checkpoint

Implemented tasks 1–4 in source. The initial IPC tests failed on undefined trust
symbols and then passed. Local Windows checks: IPC 10, Mihomo 34, service 4,
desktop 130 tests, scoped Clippy and frontend build. Linux regression tests:
IPC 7, Mihomo 34, service 10. Live Windows acceptance is not run on the host;
the fixture's guard was exercised and refused execution before network access.
A new hosted Windows fixture and workflow are added; final result is pending.
No claim of hard-crash recovery, ARM64/x86 Mihomo, Both or persistent Kill Switch.

Read-only inspection on the user's host found a custom user FullControl grant on
the volume root; strict privileged installation admission rejects that ancestry.
The existing Program Files directory itself passes. Do not weaken admission or
change volume permissions without a separate explicit decision. Hosted live
acceptance must use the runner's actual protected directory chain.

## Verified hosted result

Native implementation `c3b0f93` passed all three native jobs in
https://github.com/BBGGVP5/nimbo/actions/runs/37105907646 . Actual Windows SCM /
Rust Session / TCP4+6 / UDP / DNS / selection / repeated stop+EOF passed, including
adapter retirement and unchanged physical DNS/routes. The initial UDP failure
was reproduced locally with a no-TUN regression and fixed by retaining the remote
packet peer through the immutable socket hook. Native Go suite/vet/build passed.
Linux x64 and ARM64 broker/crash regression fixtures also passed on this revision.
Project and Android/shared at `0e9eb15` are confirmed SUCCESS. Exact source patch
scope remains checked, now six reviewed lifecycle/dialer files rather than four.

Windows packages: 37105908442, still building, publish=false. Do not describe
that installer build as complete yet. No host service/network/ACL mutation;
no ordinary-user/physical-device/crash/Both/KS/ARM64-x86 Mihomo claim.
