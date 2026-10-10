# Windows Installer Permissions Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover the known unsafe volume-root grant with explicit user consent, backup and verification, without touching child ACLs or weakening service authentication.

**Architecture:** Keep the strict native-service trust checks. A separate, opt-in helper CLI can convert exactly one explicit OI|CI FullControl grant for its own current user into an inherit-only grant. Use the documented non-propagating SetFileSecurityW backup API on the fixed, non-renameable local NTFS volume root, with an elevated same-account token and retained administrator rollback authority. Preserve the existing AUTO_INHERITED control flag by setting AUTO_INHERIT_REQ on the in-memory copy before both apply and rollback. Retain handles, descriptor snapshots and create-new private backup. The installer distinguishes repairable and unsupported permission errors and presents an accessible consent screen before requesting elevation.

**Tech Stack:** Rust/windows-sys 0.59, existing helper UAC relaunch, React/TypeScript, production-asset Playwright layout tests.

---

## Boundaries

No recursive ACL resets, no arbitrary path/SID/SDDL input, no service/firewall/network changes in the repair command. No implicit permission repair on install, upgrade, launch or IPC. A different administrative account, extra unsafe ACE, untrusted owner, reparse, non-NTFS root, unsafe Program Files or existing native image must fail closed. The host repair is separately authorized; backup and isolated-directory rehearsal precede it.

### Task 1: Constrained ACL transformation and handle transaction

**Files:**
- Modify: `crates/ipc/src/windows.rs` (extract unchanged descriptor trust predicate)
- Create: `crates/ipc/src/windows/permissions.rs` (eligibility, snapshot, backup, non-propagating transaction and tests)

- [x] Write pure fixture tests with owner SYSTEM and a fake user SID: exact FullControl OI|CI changes only ACE flag byte 3 to 11; foreign grants, duplicate grants, root-only grants, inherited grants and untrusted owner are rejected.

```rust
assert_eq!(changed_flags, OBJECT_INHERIT_ACE | CONTAINER_INHERIT_ACE | INHERIT_ONLY_ACE);
assert_eq!(before_mask, after_mask);
assert_eq!(before_sid, after_sid);
```

- [x] Run `cargo test --locked -p nimbo-ipc`; confirm fixture tests fail before implementation.
- [x] Implement snapshot from GetSecurityInfo on retained handles. Preserve owner, group, DACL control, all other ACEs and SACL (never request/set SACL). Use exactly the existing write-rights predicate after simulated transformation. Reject non-elevated mutation before any root/backup write.

```rust
let information = DACL_SECURITY_INFORMATION;
// Fixed volume root cannot be renamed. No recursive TreeSetNamedSecurityInfo.
let ok = unsafe { SetFileSecurityW(wide(path.as_os_str()).as_ptr(), information, descriptor) };
```


- [x] Persist the original owner/group/DACL SDDL to a create-new, private root backup file before applying. Verify readback and retain original handle for rollback. Recheck complete service chain after apply.
- [x] Add an isolated temporary-directory transaction test, seeded with real P|AI control flags: child descriptor bytes equal before/after and after rollback; repeated repair is rejected. Never mutate the actual volume root in automated tests.
- [x] Run IPC tests and `cargo clippy --locked -p nimbo-ipc --all-targets -- -D warnings`; expect all pass.

### Task 2: Helper recovery CLI and installer consent UX

**Files:**
- Modify: `apps/service/src/platform.rs`
- Modify: `apps/installer/src-tauri/src/payload.rs`
- Modify: `apps/installer/src-tauri/src/main.rs`
- Modify: `apps/installer/src/main.tsx`
- Modify: `apps/installer/src/universal.css`
- Modify: `apps/installer/tests/layout.mjs`

- [x] Add exit code 24 for `PERMISSIONS_REPAIR_AVAILABLE` and preserve through UAC. Existing 21 remains an unsupported unsafe chain; existing 22/23/1223 remain unchanged.
- [x] Add an explicit `--repair-install-permissions` mode before log initialization. Elevate only after this command is invoked; pass and validate the original process user SID across UAC so alternate administrator credentials cannot repair another account. No service start/stop or runtime guard.
- [x] Refactor temporary embedded helper extraction into a shared action runner; read-only preflight remains before any installed-file replacement or service stop.
- [x] Add `repair_install_permissions` Tauri command accepting only `consent: bool`. Reject false; execute the fixed embedded helper action and repeat read-only preflight.

```typescript
await invoke("repair_install_permissions", { consent: true });
await install();
```

- [x] Display a compact permission recovery panel instead of raw errors. Explain root-only change, retained child permissions, backup and administrator request. Require an unchecked checkbox and provide Cancel; primary button is disabled until checked. Block double-click/retry/close while recovery runs; no auto-start on panel mount.
- [x] Browser mock tests: actionable repairable code, zero repair calls before consent, cancel leaves installation untouched, consent invokes exactly once then retries install, cancelled UAC preserves error and permits retry; unsupported code never exposes repair. Test dark/light, narrow viewport, keyboard and long paths without host operations.
- [x] Run `npm run build`, `npm run test:layout`; expect existing 55 layouts and new recovery cases pass. Run helper/installer unit tests and scoped Clippy with existing test-only fixtures restored byte-for-byte.

### Task 3: Authorized host change, mirror and CI

**Files:**
- Private evidence under the primary workspace `.codex-tmp/permissions-recovery-20261003/` (never commit host SID/ACL)
- Modify: `docs/platform-readiness-2026-10-02.md`
- Update: this plan checkboxes and primary PR status file

- [x] Save root binary descriptor/SDDL plus Program Files, Windows, Users, profile and inheriting-child descriptor snapshots. Rehearse flag-only apply and rollback in an owned temporary directory using the documented non-propagating API and a same-account administrator token.
- [ ] Apply the authorized host change only with verified administrator rollback authority. The original MAXIMUM_ALLOWED approach encountered sharing violation 32 before any write. Host root and all 28 sampled child ACLs remain unchanged; a backup is saved privately. Current session is not elevated, so do not apply a non-rollbackable fallback. Product implementation requires explicit same-account UAC confirmation.
- [x] Read-only verify root/native service-chain trust. Do not install or restart any service on the host.
- [x] Mirror only intentional changed files after comparing primary hashes to existing verified mirror receipt. Save new hash receipt. Preserve unrelated native assets and notices.
- [x] Commit explicit changed files, push existing authorized branch; run native Windows/Linux validation and installer workflow with `publish=false`. Report actual run status, not an assumed completed build. Previous installer run 37120072035 passed but does not contain this recovery feature.

## Verification claims

Product repair is limited to the exact supported ACL shape and requires informed consent; it is not a universal Windows permissions reset. Local tests do not install TUN/service or reboot Windows. Hosted native smoke tests must remain green. No new cold-boot traffic claim.

## Execution checkpoint

Inline execution (execution subskills are not installed). Local Windows IPC18 + helper16 + installer16 tests and scoped Clippy/fmt pass; 75 production-asset mock-IPC layout/recovery scenarios pass. The elevated private backup test is intentionally guarded locally; it must execute on the disposable elevated Windows runner. Full host repair is deferred until an interactive same-account UAC confirmation is possible. No host ACL, service, network, proxy or firewall mutations.

Rehearsal found two issues before any host mutation: opening a busy volume root with MAXIMUM_ALLOWED fails, and the obsolete backup API clears AUTO_INHERITED unless AUTO_INHERIT_REQ is supplied. The final isolated transaction proves exact P|AI metadata and child ACL preservation, including forced rollback. The backup API is intentionally used for its documented no-child-propagation behavior: https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-setfilesecuritya . Control flag semantics: https://learn.microsoft.com/en-us/windows/win32/secauthz/security-descriptor-control . No automatic disk ACL resets.

Runtime source pushed as `7ce8d23`. Ten intentional files mirrored and SHA256-readback verified; unrelated native resources/notices preserved. [native matrix 37125442076](https://github.com/BBGGVP5/nimbo/actions/runs/37125442076), [project 37125444708](https://github.com/BBGGVP5/nimbo/actions/runs/37125444708), [Android/shared source 37125444797](https://github.com/BBGGVP5/nimbo/actions/runs/37125444797), and [Windows x64/x86/ARM64 installer 37125468282](https://github.com/BBGGVP5/nimbo/actions/runs/37125468282) dispatched at runtime source `7ce8d23`, currently in progress; installer publish=false. No main merge or public release.
