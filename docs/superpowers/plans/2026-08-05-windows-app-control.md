# Windows App Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn active connections into immediate rules, add protected `Run through Nimbo`, and expose safe Windows controls through the tray, shortcuts, and notifications.

**Architecture:** Rust owns temporary-rule persistence, process launch orchestration, and privileged enforcement through the existing helper service. React renders live actions and settings. The initial protection backend uses reversible Windows Firewall application rules while preserving an interface boundary for a future native WFP backend.

**Tech Stack:** Rust, Tauri 2, React 19, TypeScript, Windows Firewall/WFP-compatible policy boundary, Windows notifications and shell integration.

---

### Task 1: Temporary quick-rule engine

**Files:**
- Create: `apps/ui/src-tauri/src/app_control.rs`
- Modify: `apps/ui/src-tauri/src/lib.rs`
- Modify: `apps/ui/src-tauri/src/state.rs`
- Modify: `apps/ui/src/lib/api.ts`
- Test: `apps/ui/src-tauri/src/app_control.rs`

- [ ] Define process/domain rules for proxy, direct, and block with permanent, timed, process-lifetime, and connection-session expiry.
- [ ] Normalize executable paths and domains, prune expired entries, and merge temporary rules before profile rules.
- [ ] Expose list/upsert/remove commands and emit a runtime-config refresh event.
- [ ] Test normalization, precedence, expiry, session cleanup, and corrupted-state recovery.

### Task 2: Active-connection actions

**Files:**
- Modify: `apps/ui/src/pages/Connections.tsx`
- Modify: `apps/ui/src/lib/i18n.ts`
- Modify: `apps/ui/src/styles.css`

- [ ] Open a focused action sheet from a live connection row.
- [ ] Offer VPN, direct, block, server selection, and 30-minute/session/permanent duration.
- [ ] Show the effective rule and allow one-click undo without requiring manual domain entry.
- [ ] Reapply Xray configuration after a rule change and preserve the current server whenever possible.
- [ ] Run `npm run build`; expect TypeScript and Vite builds to pass.

### Task 3: Protected application launch

**Files:**
- Modify: `apps/ui/src-tauri/src/app_control.rs`
- Modify: `apps/service/src/platform.rs`
- Modify: `apps/service/src/platform/pipe.rs`
- Modify: `crates/ipc/src/lib.rs`
- Modify: `apps/ui/src/lib/api.ts`
- Create: `apps/ui/src/pages/AppLauncher.tsx`

- [ ] Select an executable or shortcut, target server/profile, timeout, and post-exit behavior.
- [ ] Install a temporary outbound block for the exact executable before launch.
- [ ] Connect and pass the existing HTTPS health check before removing the block and starting the process.
- [ ] Restore the block on tunnel loss when leak protection is enabled, and always remove Nimbo-owned rules on cancellation/uninstall.
- [ ] Keep UI non-elevated and route privileged operations through the authenticated named-pipe helper.

### Task 4: Windows shell, tray, hotkeys, and notifications

**Files:**
- Modify: `apps/ui/src-tauri/src/tray.rs`
- Modify: `apps/ui/src/tray-menu/TrayMenu.tsx`
- Modify: `apps/ui/src-tauri/windows/nimbo-installer.nsi`
- Modify: `apps/ui/src/App.tsx`
- Modify: `apps/ui/src/pages/Settings.tsx`

- [ ] Add configurable global connect/disconnect and server-switch shortcuts.
- [ ] Add `Run through Nimbo` to the Windows executable/shortcut context menu with a reversible installer entry.
- [ ] Add reconnect, switch server, diagnose, and pause actions to native notifications.
- [ ] Show temporary rules and protected processes in the tray flyout with immediate cancel controls.
- [ ] Remove every shell/firewall artifact during uninstall and preserve user data unless removal was requested.

### Task 5: Verification

**Files:**
- Modify: `CHANGELOG_NIMBO.md`
- Test: Rust unit tests and frontend build.

- [ ] Run `cargo test --workspace`; expect all Rust tests to pass.
- [ ] Run `npm run build` in `apps/ui`; expect TypeScript and Vite builds to pass.
- [ ] Build the current Windows custom installer and verify install, protected launch, context menu, update, rollback, and uninstall in a non-admin UI session.

