# Desktop settings polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix settings alignment, make core selection discoverable, remove duplicate Mihomo navigation, and display provider artwork without cropping or a second frame.

**Architecture:** Keep existing native capability-gated core selection and full-YAML import behavior. Settings groups become four equal-height grid cells with uniformly sized rows; updates sits immediately before about. Long ping routing details move into an accessible disclosure below a full-width control. Provider pixels remain unchanged.

**Tech Stack:** React, CSS, TypeScript, Node test runner, Playwright using isolated production-component fixtures.

### Task 1: Layout and navigation contracts
**Files:** Modify `apps/ui/tests/desktopNavigation.test.mjs`; create `apps/ui/tests/settingsPolish.test.mjs`, `apps/ui/tests/browser/settings-polish.html`, `apps/ui/tests/browser/settings-polish.tsx`, `apps/ui/tests/browser/settings-polish.mjs`.
- [x] Expect no `/mihomo` top-level navigation entry while retaining its route and breadcrumb. Use React static rendering to assert updates precedes about and the core control is capability gated.
- [x] Browser fixture mounts production Settings inside MemoryRouter with isolated store data and mocked IPC; no hydrate, user subscriptions, tunnel, service or installation calls. Measure equal row heights, aligned grid groups and a URL row shorter than 180px in wide windows. Check detailed routing information is initially collapsed and remains keyboard accessible.
- [x] Run `npm test` and the browser fixture against current UI; confirm new assertions fail before production changes.

### Task 2: Settings structure
**Files:** Modify `apps/ui/src/pages/Settings.tsx`, `apps/ui/src/preview-parity.css`, `apps/ui/src/lib/desktopNavigation.ts`, `apps/ui/src/components/CorePreferenceSetting.tsx`, `apps/ui/src/pages/MihomoProfiles.tsx`.
- [x] Flatten overview groups to four direct grid cells. Move `{row("updates", `v${version}`)}` directly before `{row("about", `Nimbo · ${version}`)}` in the Application group. Add a visible connection/core hint without changing the saved choice or live connection.
- [x] Use a stacked choice row: `<div className="settings-row settings-choice-row">` with control width 100%; long explanation becomes `<details className="latency-routing-help"><summary>Подробнее о проверке</summary><p>{m.settings.latencyActiveRouteOnly}</p></details>`.
- [x] Display a `CorePreferenceSetting` above latency settings labelled as connection core, not ping core. Explicitly state that Nimbo Ping uses its own temporary Xray probe; do not make a nonfunctional ping-core selector.
- [x] Remove only the redundant sidebar `/mihomo` item; keep access through the connection core section under a clearly labelled Advanced full-YAML link, without external-link arrows. Update Mihomo introductory copy to explain the distinction from subscriptions.
- [x] Uniform rows: `.parity-setting-link { min-height:76px; }`; overview grid `align-items:stretch`; groups are flex columns with list `flex:1`; responsive one-column fallback.

### Task 3: Provider artwork and validation
**Files:** Modify `apps/ui/src/universal.css`.
- [x] Apply `body[data-ui-style="signal"] .signal-sub-logo:has(img) { background:transparent; border:0; }` with its image using `object-fit:contain`; do not invert or repaint the provider icon. Include rectangular artwork in the isolated browser fixture.
- [x] Run `npm run build`, `npm test`, browser checks at wide/narrow dimensions in dark/light themes and inspect screenshots. Keep unavailable cores disabled; verify preference writes go through the native API once and do not connect.
- [x] Commit intentional source/test/plan files, mirror verified owned files, push the existing feature branch and include changes in the next non-published Windows build.

## Local execution evidence

Production build, all 89 Node tests, TypeScript fixture validation and 18 browser cases passed (Signal/Material You/Dotted, dark/light, 1100/800/360px). Original update ordering and narrow description/choice row failed new assertions. Full-width controls, native capability-gated core preference save/readback, retained compatibility text, and uncropped provider artwork inspected. No connect/install/repair IPC accepted by the fixture.

## Delivery checkpoint

Source commits `dfb3d39` / `8ee2145` pushed to the existing feature branch; 20 intentional files SHA256-mirrored into the primary workspace. Windows rebuild [37131921013](https://github.com/BBGGVP5/nimbo/actions/runs/37131921013) dispatched at `8ee2145` with `publish=false`; dispatch is not completed artifact availability. No main merge or public release.
