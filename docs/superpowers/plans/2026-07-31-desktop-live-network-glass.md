# Desktop Live Network Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Desktop Nimbo glass a restrained, data-driven network pulse consistent with Android while preserving the native desktop layout and performance.

**Architecture:** A small TypeScript policy converts Zustand connection, traffic, and active-server latency into CSS state and normalized custom properties. CSS pseudo-layers animate only the sidebar glass and home connection area. Reduced-motion and non-Nimbo themes disable the moving layer.

**Tech Stack:** React 19, TypeScript, Zustand, CSS custom properties, Vite.

---

### Task 1: Define the Desktop network-glass signal

**Files:**
- Create: `apps/ui/src/lib/liveNetworkGlass.ts`
- Modify: `apps/ui/src/App.tsx`

- [ ] **Step 1: Implement the pure policy**

Define `LiveNetworkGlassMode` and a function accepting connection state, transition flags, byte rates, and ping. Use the same semantic modes as Android and return bounded `upload`, `download`, and `latency` values.

- [ ] **Step 2: Bind actual store values**

Read `trafficSpeed`, `serverPings`, `connectingServerId`, `switchingServerId`, and `disconnecting` in `App`. Set `data-network-glass` plus `--network-upload`, `--network-download`, and `--network-latency` on `.app-shell`.

- [ ] **Step 3: Mark only two reactive surfaces**

Add `network-glass-reactive` to the sidebar glass and a `network-glass-home` hook around the Home connection region. Do not modify every card.

### Task 2: Create the restrained visual language

**Files:**
- Modify: `apps/ui/src/styles.css`
- Modify: `apps/ui/src/pages/Home.tsx`

- [ ] **Step 1: Add isolated pseudo-layers**

Use clipped `::before`/`::after` layers for a cool download drift and warm upload drift. Keep combined opacity below `0.18` and use `pointer-events: none`.

- [ ] **Step 2: Add state-specific motion**

Calm mode uses a 12-second breath. Active mode follows normalized traffic variables. Delayed mode slows and widens the wave. Connecting/recovering uses a sparse segmented rim rather than flashing color.

- [ ] **Step 3: Honor accessibility**

Disable animations under `prefers-reduced-motion`, hide reactive layers under `prefers-reduced-transparency`, and scope all selectors to `body[data-ui-style="nimbo"]`.

### Task 3: Verify Desktop

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Document the effect**

Describe that network glass uses measured traffic and latency, is not shown in Material You, and is reduced automatically by OS accessibility settings.

- [ ] **Step 2: Run production checks**

Run: `npm run build`

Expected: TypeScript and Vite build successfully.

Run: `cargo test --workspace`

Expected: all Rust tests pass; the frontend-only effect changes no tunnel behavior.
