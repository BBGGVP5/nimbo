# Mihomo proxy ping implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the duplicate Mihomo connection header and add category-wide and per-card native latency checks without changing the selected proxy.

**Architecture:** Use the existing session-bound `mihomo_delay` IPC, not legacy Xray probes or a hidden VPN start. A small sequential, cancellable queue holds ephemeral results; profile digest/revision, subscription version, session/generation, current graph and latency settings scope all results. Disconnected cards retain ping controls with a connection-required tooltip. No native admission policy changes or fake offline measurements.

**Tech Stack:** React, TypeScript, existing Tauri API, Node test runner, isolated Playwright/Vite fixtures.

---

### Task 1: Cancellable latency queue

**Files:** Create `apps/ui/src/lib/mihomoPing.ts`, `apps/ui/tests/mihomoPing.test.mjs`.

- [ ] Write and run failing Node tests using the existing TypeScript transpile/data-URL pattern: `node --test tests/mihomoPing.test.mjs`. Check sequential deduplication, zero latency, invalid replies, timeout/error sanitization, cancellation, context replacement, and no further dispatch after context changes.
- [ ] Implement `MihomoPingQueue` with `setContext(key)`, `cancel()` and `run(names, probe, current)`; expose immutable `Map<string, PingResult>` snapshots to the subscriber. Capture a revision before dispatch and reject late results after cancellation or a different key. Validate latency with `Number.isInteger(ms) && ms >= 0 && ms <= 65535`; display only fixed error states.
- [ ] Run the queue tests; all pass without network access.

### Task 2: Session-aware hook and card controls

**Files:** Create `apps/ui/src/components/useMihomoPing.ts`; modify `apps/ui/src/components/CoreSubscriptionControl.tsx`, `apps/ui/src/components/MihomoProxyGroups.tsx`, `apps/ui/src/components/core-subscription-groups.css`.

- [ ] Remove `.core-subscription-head` and its connect/disconnect button entirely. Preserve automatic inspection, errors and the separate Home connection action.
- [ ] Bind the queue to `coreApi.delay(session, name, preferences.latency_test_url, timeout)` with timeout clamped to native 100..30000 ms. Before publishing, reread `coreApi.runtime()` and compare profile/session/generation. Invalidate on source, session, subscription, graph or setting changes and on unmount; do not occupy `core.busy` for an entire batch.
- [ ] Place bulk Ping/Stop beside category navigation. Snapshot and deduplicate the open category's members at click time. Keep category navigation usable during checks.
- [ ] Refactor each card to a wrapper containing the existing full-surface selection button and a separate sibling ping button (never nested buttons). Use original native member names. Add pending, ms and failure output next to protocol information, 44px ping targets, equal card heights, focus/press/reduced-motion styles and narrow-screen wrapping.

### Task 3: Verification and delivery

**Files:** Modify `apps/ui/tests/browser/cross-platform-polish.tsx`, `apps/ui/tests/browser/cross-platform-polish.mjs`.

- [ ] Replace obsolete tests that click the removed header with explicit isolated running-session fixtures; retain an actual Home connect-path test and native admission error coverage.
- [ ] Add controlled delay IPC for zero/success/error/timeout/invalid replies and deferred completion. Assert exact session/name/URL/timeout args, one probe at a time, unchanged selection, no connect IPC, group-wide results, individual retry, keyboard support, cancellation and stale-result rejection on refresh/settings/session changes.
- [ ] Run `npm test`, `npm run build`, `npm run test:polish`, `npm run test:settings`, `npm run test:traffic:browser`. Capture and visually inspect 360px and 1440px dark/light card previews with controlled data.
- [ ] Mirror only owned files after normalized committed-baseline and SHA256 preimage checks; stage only those paths, commit, push the existing branch, verify remote HEAD and mirrored bytes. Dispatch artifact-only Windows release with `tag=v1.3.0-beta.1`, `publish=false`, `platform=windows` and report its actual status.

## Verified implementation

- `npm test`: 126 passing Node cases, including six queue tests; the first focused run failed because the implementation did not exist yet.
- `npm run build`: TypeScript and production Vite build passed.
- `npm run test:polish`: 90 passing isolated browser cases, including individual/bulk pings, zero latency, timeout/failure, invalid replies, cancellation/draining, refreshed subscriptions/settings/sessions and unmount cleanup.
- `npm run test:settings`: 18 passing browser cases.
- `npm run test:traffic:browser`: 44 passing browser cases.
- 360px dark and 1440px light proxy captures were visually inspected; dark/light captures exist for both widths. All delay values are controlled test data, not host VPN measurements. No native code, binaries, VPN, routes, proxy settings, subscription credentials or installation were changed by verification.
- Stop immediately clears queued pending results and stops further dispatch. A previously dispatched native invoke drains under its configured timeout; replacement runs stay blocked until it settles, avoiding an operation-lock backlog.
