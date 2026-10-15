# Cross-platform interaction and navigation polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Address the supplied desktop, Android and iOS screenshots without hiding functionality or claiming preview renders are device validation.

**Architecture:** Preserve the existing Nimbo design system and real telemetry. Divide independent shell, subscription-view, subscription-core and mobile work into disjoint changes; integrate controls and run unified tests locally. Core-specific profile refresh must preserve subscription identity and active-session intent, never silently change the host network.

**Tech Stack:** React 19 / TypeScript / Tauri Rust; Kotlin Compose Multiplatform; Swift iOS host; Node and mocked Chromium tests; Gradle desktop interaction tests.

## Task 1: Shell, settings information architecture and tray

**Files:** `apps/ui/src/App.tsx`, `apps/ui/src/lib/desktopNavigation.ts`, `apps/ui/src/pages/Settings.tsx`, `apps/ui/src/tray-menu/TrayMenu.tsx`, `apps/ui/src/tray-menu/tray-menu.css`; focused new shell CSS and tests under `apps/ui/tests/`.

- [x] Add navigation contracts: no separate connections/logs sidebar entries, both remain reachable via icon-labelled activity tabs; Application group follows all other settings groups. For narrow layouts keep destinations that have no visible sidebar reachable in Settings.
- [x] Correct responsive layout at 360, 600, 900 and 1280px: use four bottom destinations at narrow widths; content must retain a positive width and scroll independently above the bar.
- [x] Simplify tray to connection state, selected server, connect/disconnect, a bounded favourites list, open and quit. Required recovery/admin warnings stay actionable, no routine navigation grid.
- [x] Run `npm test` and focused mocked browser geometry/keyboard tests. Assert `document.documentElement.scrollWidth <= innerWidth` and tray height fits its configured native window.

## Task 2: Home and profile interaction

**Files:** `apps/ui/src/pages/Home.tsx`, `apps/ui/src/pages/home/HomeSubscriptions.tsx`, `apps/ui/src/pages/Subscriptions.tsx`, `apps/ui/src/pages/profiles/`; focused new interaction CSS/hooks and tests.

- [x] Add isolated refresh-gesture regression tests: start away from scroll top, horizontal drag, cancelled pointer and repeated drag while refreshing must not refresh; a downward release past threshold at top refreshes once.
- [x] Place connection card above subscriptions, give its main action a clear label and consistent press feedback; retain selected server and measured session information.
- [x] Render provider announcement as escaped text from subscription metadata, with disclosure for long descriptions. No fabricated subscription quota or expiry.
- [x] Give selected server and auto rows a full-row rounded border without layout shift. Keep long-press menus anchored to the title and independent favourite targets.
- [x] Use the same refresh gesture on Home and profile pages, coalesce concurrent refresh, report real errors; disable gesture when a dialog or editable field owns the pointer.
- [x] Run `npm test`, `npm run build` and isolated Home/profile browser tests at narrow/wide widths with provider description fixtures.

## Task 3: Core-aware subscription representation

**Files:** `apps/ui/src/coreStore.ts`, `apps/ui/src-tauri/src/mihomo_runtime.rs`, `apps/ui/src-tauri/src/commands.rs`, subscription/core Rust modules and focused tests as required.

- [x] Trace preference save, fetch content negotiation, stored templates, profile admission and connect routing before editing. Document the concrete missing bridge.
- [x] Add tests for Mihomo selection on a subscribed URL and switching back to Xray, rejected/unsupported provider response, stable subscription identity, persisted preferred core, no active-session mutation, and failed-refresh preservation.
- [x] Implement the bounded missing bridge using the existing parsers and verified adapters. Preserve complete provider rules/groups when available; do not relabel an Xray-only payload as a working Mihomo profile.
- [x] Run crate tests and native compile checks; no host VPN connection or privileged service mutation.

## Task 4: Nimbo dropdowns and concise descriptions (parent-owned)

**Files:** create `apps/ui/src/components/NimboSelect.tsx` and its CSS; modify `CorePreferenceSetting.tsx`, selector CSS and desktop settings select consumers after coordinating ownership.

- [x] Add a controlled button/listbox selector with Arrow/Home/End navigation, disabled options, Enter/Space selection, Escape/outside dismissal, focus return and viewport-bounded portal positioning.
- [x] Replace native select presentation without changing stored values or availability guards. Use concise inline hints and details for compatibility; remove redundant core choice from latency settings if connection settings provide it.
- [x] Browser acceptance snippet:
```js
await page.getByRole('combobox', {name: /Ядро/}).click();
await page.getByRole('option', {name: 'Xray', exact: true}).click();
if (await page.locator('select').count()) throw Error('native select remains');
```
- [x] Run TypeScript build and keyboard/disabled/focus tests with mocked IPC only.

## Task 5: Mobile typography, refresh and Android ad location

**Files:** `shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboDesignSystem.kt`, `NimboHomeScreen.kt`, `NimboProfilesScreen.kt`, `NimboSettingsScreen.kt`, related focused shared tests; `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`; `iosApp/Tests/test_traffic_statistics_contracts.py`.

- [x] Trace actual screen hosts; do not assume legacy Android Home is used. Keep native iOS navigation and safe areas, no second bottom bar.
- [x] Use tighter semantic heading/body styles and compact grouped rows, preserve 44dp touch targets and scalable text. Implement real Home/profile pull refresh bound to existing refresh actions and loading state.
- [x] Remove only Android root Settings ad control. Keep Settings → Routing ad control once and retain its default-off saved policy.
- [x] Add source regression:
```python
assert 'AdBlockingSettingsCard(preferencesManager)' not in root_settings_function
```
- [x] Verify 320/360/390px and 125% text-scale interaction tests, refresh dispatch, selected rows, no clipping; clearly label Compose renders as test previews, not iPhone screenshots.

## Task 6: Integration, mirror and builds

- [x] Review all diffs against the initial dirty-file inventory; stage only owned paths.
- [x] Run `npm test`, `npm run build`, focused browser suites, Python source contracts, Android Kotlin compilation, shared metadata compilation and desktop interaction tests. Record failing pre-existing gates separately.
- [x] Mirror owned source files to the primary workspace only when previous receipt hashes or original source content match; preserve unrelated differences and write a new SHA256 receipt.
- [x] Commit/push to existing PR 78 and dispatch unsigned iOS plus desktop artifact builds with `publish=false`. Report actual run status, no public release and no unsupported claim of live hardware validation.

## Implementation and verification record — 2026-10-04

The missing desktop bridge was content negotiation plus ownership: the core preference only changed admission checks, while URL subscriptions always requested Happ/Xray and never linked a complete Mihomo profile. Fetch options now request the preferred representation. A saved subscription owns a companion full YAML profile through `meta.mihomo_profile_id`; source bytes, providers, groups and rules remain intact. Preference changes refresh HTTP subscriptions in bounded batches and reconcile saved data. Connect and tray actions use the full profile adapter; controller group changes require native acknowledgement. No legacy Xray node is relabelled as a Mihomo group. Validation remains at native inspection/connection, not falsely at download. Unsupported responses preserve cached profiles and running sessions. Transactions reject removed subscriptions, changed preference/revision, and replacement of an actively connected YAML source.

### Passed locally
- UI: 112 Node tests, production TypeScript/Vite build.
- Browser: 17 isolated shell/Home/profile/dropdown/Mihomo/tray cases; 18 settings cases; 44 traffic cases. Widths include 320, 360, 600, 800, 900, 1100, 1280 and 1440px across the suites. Controls use mock IPC, not host networking. Full-width mobile connect geometry is asserted, not inferred from CSS.
- Rust: 40 Mihomo tests; 87 subscription unit tests plus 3 loopback representation tests; native `nimbo-ui` compile check. Local native check warns that the AWG binary is not staged; artifact workflows stage native dependencies.
- Android: `:app:compileDebugKotlin`; shared common metadata and five focused desktop Compose interaction/preview suites. The additional `NimboPullRefreshTest` sends actual touch drags to Home and Profiles and verifies exactly one refresh and no repeat while loading.
- iOS source gates: 8 traffic/ad/loading contracts and 11 packet-flow contracts. Compose 320/390px and scaled-text renders are test previews, not iPhone screenshots.

### Existing limitations, not reported as passing
Broad Python iOS discovery: 57 tests, 4 failures and 2 import errors. Branding still expects the custom symbol in About; Mihomo storage gates expect missing backup integration, repository functions and Swift scenarios. The files/fragments responsible were checked against the original HEAD and are unchanged by this task. Two other modules cannot import the local missing `tree_sitter` dependency. No Swift compiler, real iPhone VPN session, Windows TUN connection, persistent firewall/service installation, or host-network mutation was used for this verification.

### Delivery
Only owned paths are mirrored after original-content/previous-receipt SHA256 admission; unrelated native resources and pre-existing scratch files stay untouched. Source commit is pushed to the existing PR 78. The unsigned iOS and Windows/Linux artifact workflows are dispatched on that commit, with desktop `publish=false`; actual run IDs/status are recorded in the ignored delivery receipt rather than claimed as completed here.
