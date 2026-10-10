# Installer completion layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Group the completion icon, title, path and actions into a compact centered result instead of separating them across the window.

**Architecture:** Scope overrides to the completion screen, leaving wizard and permission-recovery spacing intact. Both install and uninstall use a bounded content wrapper and an independently labelled, selectable path; native commands stay unchanged.

**Tech Stack:** React, TypeScript, CSS, Vite, Playwright with mocked Tauri IPC.

### Task 1: Completion regression coverage
**Files:** Modify `apps/installer/tests/layout.mjs`.
- [x] Mock successful `install_nimbo` / `uninstall_nimbo` responses for install, update, retained-data uninstall and removed-data uninstall.
- [x] Add assertions: `assert(actions.top - path.bottom <= 40)`; desktop buttons share a top coordinate; content center matches panel center; long Unicode and unbroken paths have no horizontal overflow; narrow actions remain reachable. Check Open sends the result's installation directory, not the originally proposed path.
- [x] Run `npm run build` and `npm run test:layout` from `apps/installer`; confirm the existing completion fails these assertions before changing production code.

### Task 2: Compact completion composition
**Files:** Modify `apps/installer/src/main.tsx`, `apps/installer/src/universal.css`.
- [x] Wrap success content in `<div className="done-content">` within `.done-screen`. Replace the inline sentence path with `<div className="done-location"><span>Папка установки</span><span className="done-path">{result?.install_dir || installDir}</span></div>`; uninstall retains its accurate retained/deleted data message.
- [x] Use `.done-content { width:min(100%,480px); display:flex; flex-direction:column; align-items:center; gap:16px; }` and scoped `.done-screen .done-actions { display:flex; flex-direction:row; width:100%; margin:8px 0 0; }`. Let narrow buttons wrap; path uses `overflow-wrap:anywhere`.
- [x] Run the full layout matrix; inspect dark/light/DPI-2 completion screenshots. Do not invoke host installation, services or permission repair.
- [x] Stage only these files and this plan; commit `fix(installer): center compact completion content`.

### Task 3: Delivery
- [x] Mirror only verified owned source files into the primary workspace; record byte hashes in a private receipt.
- [x] Push the existing feature branch and dispatch `release.yml` for Windows with `publish=false`. Report the actual build state; do not claim an unfinished build is ready.

## Local execution evidence

Production build and 171 mocked IPC scenarios passed. New completion assertions failed on the original layout (327px detached actions and overflowing path), then passed on the compact layout. Dark/light DPI-2 screenshots inspected; reduced-motion success check explicitly remains visible. No host installation, service, network or ACL mutation.

## Delivery checkpoint

Source commits `dfb3d39` / `8ee2145` pushed to the existing feature branch; 20 intentional files SHA256-mirrored into the primary workspace. Windows rebuild [37131921013](https://github.com/BBGGVP5/nimbo/actions/runs/37131921013) dispatched at `8ee2145` with `publish=false`; dispatch is not completed artifact availability. No main merge or public release.
