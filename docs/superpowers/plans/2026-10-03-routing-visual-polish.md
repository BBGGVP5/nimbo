# Routing visual polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Refine Routing and its ad-filter setting after the user's visual feedback, especially shared/iOS.

**Architecture:** Keep every setting and callback intact. Use grouped rows with a subtitle under the name, quiet section captions, unboxed 44dp information targets, and a plain back action. Align the desktop toolbar and compact ad setting without touching runtime/network behavior.

**Tech Stack:** Compose, React/CSS, existing mocked render/browser suites.

## Task 1: Compact grouped settings

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/{NimboAppearanceControls,NimboSettingsScreen,NimboAdBlockingSettings}.kt`
- Modify: `shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboRoutingScreen.kt` (24sp inline heading/help, including large text)
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AdBlockingSettings.kt`
- Test: `shared/src/desktopTest/kotlin/com/danila/nimbo/shared/ui/NimboTrafficDashboardTest.kt`

- [x] Add a surface tag and test a 360px ad-filter card height ≤135px at normal text; retain 320/360px large-text and all-style bounds checks:
```kotlin
val card = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "ad-blocking-settings" }
assertTrue(card.boundsInRoot.height <= 135)
```
- [x] Let `AppearanceToggle` accept an optional subtitle while keeping its existing named switch and callback. Put `Рекламные домены · не вся реклама` below the name and leave only current/next-connection state in the footer. Use a quiet `Фильтрация` group caption.
- [x] Remove the filled box from info/back actions, retaining a 44dp focusable information target and existing content description:
```kotlin
Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { open = true }
    .semantics { contentDescription = "Информация: $title" }, contentAlignment = Alignment.Center) {
    NimboIcon(NimboIconName.INFO, tint = NimboPalette.TextSecondary, modifier = Modifier.size(20.dp))
}
```
- [x] Keep Android's same short subtitle inside the switch row, move status to the small footer, and reduce the info glyph to 20dp. Do not change the existing preference listener or dialog save semantics.

## Task 2: Desktop toolbar and aligned description

**Files:**
- Create: `apps/ui/src/pages/routing-polish.css`
- Modify: `apps/ui/src/pages/Routing.tsx`
- Modify: `apps/ui/src/components/{AdBlockingControl.tsx,ad-blocking-control.css}`
- Test: `apps/ui/tests/{trafficDashboard.test.mjs,browser/traffic-dashboard.mjs}`

- [x] Add browser geometry assertions that all four toolbar actions have matching top/height and ≥44px targets; expect the old narrow toolbar to fail.
```javascript
assert.equal(new Set(actions.map(e => e.top)).size, 1);
assert(actions.every(e => e.height >= 44));
```
- [x] Give the Modules link the same baseline/height as icon actions; on narrow viewports use one row with one flexible label and three 44px actions. Import scoped styles from Routing, not global overrides.
- [x] Place the compact subtitle below the ad-blocking name; keep state below the header and details collapsed. Keep the native save, ignored-field, keyboard and no-reconnect checks.

## Task 3: Verify and deliver

- [x] Include `"${ROOT_DIR}/iosApp/Shared/NimboAdBlocking.swift"` in the production Swift link-check inputs in `scripts/ci/build-libxray-awg-apple.sh`, protected by the existing Apple dependency source contract. The previous unsigned IPA build exposed the missing `NimboAdBlockingError` dependency at this link-check stage, not in Compose compilation.
- [x] Run `npm test`, `npm run build`, `npm run test:traffic:browser`; review actual Routing screenshots at 360px in both themes and a shared render at 320px/text 125%.
- [x] Run offline Gradle Android compilation/common metadata and shared `NimboTrafficDashboardTest` / `NimboSettingsRedesignInteractionTest`; run all seven `iosApp/Tests/test_traffic_statistics_contracts.py` source contracts. Confirm info opens/closes without saving or connecting.
- [x] Mirror only ownership-checked files, commit explicit paths and push the existing branch. Call all screenshots mocked UI renders, never device screenshots or signed-package validation.
