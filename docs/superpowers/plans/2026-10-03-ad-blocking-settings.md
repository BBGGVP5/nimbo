# Ad blocking settings placement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Put ad blocking only in Settings → Routing on Android, iOS/shared and desktop, with a short visible description.

**Architecture:** Reuse the existing persisted default-off switch, without modifying core policies or reconnect behavior. Remove it from statistics. Keep detailed limitations behind the existing shared info dialog / desktop disclosure and an Android info dialog.

**Tech Stack:** Kotlin/Compose, React/TypeScript, Python source contracts, headless Chromium.

## Task 1: Placement regression and compact control

**Files:**
- Test: `iosApp/Tests/test_traffic_statistics_contracts.py`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/{TrafficDashboard,NimboMiniApp,AdBlockingSettings}.kt`
- Modify: `shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/{NimboStatsScreen,NimboTrafficDashboard,NimboRoutingScreen}.kt`
- Create: `shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboAdBlockingSettings.kt`
- Modify: `apps/ui/src/pages/stats/TrafficDashboard.tsx`
- Modify: `apps/ui/src/components/{AdBlockingControl.tsx,ad-blocking-control.css}`

- [x] Add and run a failing cross-platform placement contract:
```python
for path, marker in [
    ("app/src/main/java/com/danila/nimbo/ui/screens/TrafficDashboard.kt", "AdBlocking"),
    ("shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboStatsScreen.kt", "AdBlocking"),
    ("shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboTrafficDashboard.kt", "AdBlocking"),
    ("apps/ui/src/pages/stats/TrafficDashboard.tsx", "AdBlocking")]:
    self.assertNotIn(marker, read(path), path)
for path, marker in [
    ("app/src/main/java/com/danila/nimbo/ui/screens/RoutingScreen.kt", "AdBlockingSettingsCard(preferencesManager)"),
    ("shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboRoutingScreen.kt", "AdBlockingSettingsCard(state, actions)"),
    ("apps/ui/src/pages/Routing.tsx", "<AdBlockingControl />")]:
    self.assertEqual(read(path).count(marker), 1, path)
```
Run: `python iosApp/Tests/test_traffic_statistics_contracts.py`; expect failure on existing Statistics references.
- [x] Remove the statistics calls/imports, change Android's call to `TrafficDashboard()`, and place the shared control after the Routing heading:
```kotlin
AdBlockingSettingsCard(state, actions)
```
- [x] Use the short visible copy below, keeping next-connection/current-session status and detailed limitations in an opt-in explanation:
```text
Фильтрует рекламные домены. Не убирает всю рекламу.
Filters ad domains. Does not remove all ads.
```
Desktop: remove the repeated next-connection hint paragraph, update `aria-describedby` to the description ID. Shared: use `AppearanceToggle(info = ...)`. Android: provide an info icon and dismissed-by-default dialog.
- [x] Run the contract again; expect all source guards to pass.

## Task 2: Render, persistence and integration verification

**Files:**
- Test: `apps/ui/tests/trafficDashboard.test.mjs`
- Test: `apps/ui/tests/browser/traffic-dashboard.mjs`
- Test: `shared/src/desktopTest/kotlin/com/danila/nimbo/shared/ui/NimboTrafficDashboardTest.kt`
- Modify: `apps/ui/tests/browser/traffic-dashboard.README.md`

- [x] Prove statistics renders no ad-control sentinel:
```javascript
AdBlockingControl: () => createElement('div', null, 'ad-settings-sentinel')
assert.doesNotMatch(render(stats), /ad-settings-sentinel/);
```
- [x] Move the browser switch/persistence/keyboard/failure checks to `page=routing`; keep Statistics asserting zero ad switches. Render Routing at 360/800/1100 widths in both themes and all three styles, check one switch, compact visible copy and no overflow, and capture screenshots.
```javascript
assert.equal(await page.getByRole('switch', { name: 'Ad blocking' }).count(), 1);
assert(await page.getByText('Filters ad domains. Does not remove all ads.', { exact: true }).isVisible());
```
- [x] Render shared Statistics and Routing to prove absence / one named switch; invoke the real switch handler and assert one saved preference callback and no connect callback. Preserve active-session status tests and phone geometry.
- [x] Verify:
```powershell
python iosApp/Tests/test_traffic_statistics_contracts.py
# apps/ui
npm test
npm run build
npm run test:traffic:browser
# repository root, with existing JAVA_HOME and ANDROID_HOME
.\gradlew.bat --offline :app:compileDebugKotlin :shared:compileCommonMainKotlinMetadata :shared:desktopTest --tests '*NimboTrafficDashboardTest*' --tests '*NimboSettingsRedesignInteractionTest*' --no-daemon --max-workers=2
```
Expected: successful compilation, source/unit/render/browser tests, preserved default-off/persistence/failure behavior. Review captured Routing screenshots. No live tunnel, service or firewall changes.
- [x] Mirror only these owned files after checking prior source hashes; retain unrelated primary edits. Commit only explicit changed paths and push the existing authorized branch. UI-source checks are not device or signed-package validation.
