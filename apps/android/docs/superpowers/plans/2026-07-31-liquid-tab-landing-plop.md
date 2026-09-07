# Liquid Tab Landing Plop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Nimbo Glass tab bubble follow the finger fluidly and perform an iOS-like squash, rebound, halo, and haptic “plop” on the exact tab where the drag ends.

**Architecture:** Keep landing target, velocity clamping, delay, and impact strength in the pure `LiquidInteractionPolicy`. The composable keeps direct drag state but uses `Animatable` for release position, impact, and expanding halo so the destination is committed once while visual physics continue independently. Reduced-motion mode snaps directly and skips the landing effect.

**Tech Stack:** Kotlin 2.2, Jetpack Compose `Animatable`, pointer timing, coroutines, Material 3, JUnit 4, Gradle.

---

### Task 1: Test landing physics

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidInteractionPolicy.kt`
- Modify: `app/src/test/java/com/danila/nimbo/ui/components/LiquidInteractionPolicyTest.kt`

- [x] Add tests proving release always selects the nearest tab, regardless of fling velocity.
- [x] Add tests for clamped index velocity and stronger impact after faster or longer travel.
- [x] Add tests for a bounded landing delay based on remaining distance.
- [x] Run the focused test and confirm the new functions are unresolved.
- [x] Implement landing target, velocity, impact, and delay policy functions.
- [x] Re-run the focused test and confirm it passes.

### Task 2: Replace simple settle with liquid landing

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [x] Track drag duration, last position, and smoothed horizontal release velocity without changing the chosen nearest tab.
- [x] Replace `animateFloatAsState` settling with a position `Animatable` that accepts bounded initial velocity.
- [x] Spring-filter the drag position so the bubble follows with a small fluid lag.
- [x] Animate a wide/flat impact squash followed by a damped rebound.
- [x] Trigger one confirmation haptic at visual contact with the destination.
- [x] Keep taps functional and synchronize external destination changes.

### Task 3: Draw the landing “plop”

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [x] Draw an accent/white halo behind the selected capsule when it lands.
- [x] Expand and fade the halo while the capsule rebounds.
- [x] Intensify the capsule highlight briefly during impact.
- [x] Keep the halo clipped inside the bottom navigation and non-interactive.
- [x] Skip impact/halo animation when motion is disabled.

### Task 4: Verify and document

**Files:**
- Modify: `CHANGELOG.md`

- [x] Add a user-facing entry for fluid drag and the tab landing “plop”.
- [x] Run focused landing policy tests.
- [x] Run `.\gradlew.bat testDebugUnitTest assembleDebug`.
- [x] Confirm all tests pass and all debug APK variants are generated.
