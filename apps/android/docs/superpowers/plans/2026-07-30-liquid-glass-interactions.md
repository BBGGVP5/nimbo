# Liquid Glass Interactions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add iOS-style elastic press deformation to Liquid Glass controls and a draggable, morphing selection bubble to the Android bottom navigation.

**Architecture:** Keep gesture math independent from Compose in a small tested policy file. A non-consuming pointer modifier observes press position, cancels deformation when scrolling starts, and applies spring-driven scale, translation, and pivot changes only to Liquid Glass surfaces. The bottom bar uses a dedicated continuous index model so taps still work normally while horizontal dragging moves and stretches one shared glass bubble before committing the selected destination.

**Tech Stack:** Kotlin 2.2, Jetpack Compose pointer input and spring animations, Material 3, JUnit 4, Gradle.

---

### Task 1: Test interaction geometry

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/components/LiquidInteractionPolicy.kt`
- Create: `app/src/test/java/com/danila/nimbo/ui/components/LiquidInteractionPolicyTest.kt`

- [x] Add failing tests for an identity transform when released.
- [x] Add failing tests for centered press expansion and edge-directed translation/pivot.
- [x] Add failing tests proving controls deform more than panels.
- [x] Add failing tests mapping drag coordinates to continuous and nearest tab indices with boundary clamping.
- [x] Run `.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.ui.components.LiquidInteractionPolicyTest"` and confirm compilation fails before implementation.
- [x] Implement immutable transform data and pure press/tab-bar policy functions.
- [x] Re-run the focused test and confirm it passes.

### Task 2: Add reusable elastic Liquid Glass interaction

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/components/LiquidInteraction.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassSurface.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [x] Observe pointer-down and movement at the Initial pass without consuming child clicks.
- [x] Cancel deformation after touch slop so normal vertical scrolling remains unaffected.
- [x] Animate scale, translation, and transform origin with spring physics.
- [x] Respect reduced motion and expose an `interactive` switch for animated elements driven by another gesture.
- [x] Apply the modifier automatically to Liquid Glass surfaces and custom Nimbo clickable controls.

### Task 3: Build the draggable bottom navigation bubble

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [x] Measure the inner navigation width and convert horizontal touch coordinates to a continuous tab index.
- [x] Draw one shared floating Liquid Glass capsule behind the four navigation items.
- [x] Move the capsule continuously during horizontal drag and stretch it between tab centers.
- [x] Preview icon selection and emit haptic ticks when the nearest tab changes.
- [x] Commit the destination on drag end and spring the bubble to the exact selected center.
- [x] Preserve normal taps and remove duplicate per-item selection backgrounds in iOS Liquid Glass mode.

### Task 4: Tune important controls and accessibility

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassSurface.kt`

- [x] Give controls, floating navigation, and panels distinct deformation intensities.
- [x] Keep interactions visually quiet when reduced motion or reduced transparency is enabled.
- [x] Ensure drag gestures do not trigger multiple destination changes before release.
- [x] Keep touch targets, semantic tab selection, and existing haptic settings intact.

### Task 5: Verify and document

**Files:**
- Modify: `CHANGELOG.md`

- [x] Add a user-facing changelog entry for elastic controls and draggable bottom navigation.
- [x] Run the focused policy tests.
- [x] Run the complete debug unit test suite.
- [x] Assemble all debug APK variants.
