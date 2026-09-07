# Liquid Glass Smooth Floating Rim Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make tilt-driven highlights move more smoothly and remain visibly continuous around the entire long bottom navigation capsule.

**Architecture:** Stabilize the rim light direction with a biased resting vector so small sensor noise cannot rotate the gradient abruptly, then spring-filter the shared tilt state. Increase sweep sampling and layer a subtle complete base outline under the colored rim, with a small width/alpha boost only for floating glass.

**Tech Stack:** Kotlin 2.2, Jetpack Compose animation/drawing, Android gravity sensor, JUnit 4, Gradle.

---

### Task 1: Test stable rim direction

**Files:**
- Modify: `app/src/test/java/com/danila/nimbo/ui/components/LiquidGlassRimPolicyTest.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassRimPolicy.kt`

- [x] Add a failing test proving a tiny tilt changes the brightest perimeter sector by no more than two of 64 samples.
- [x] Replace the hard magnitude threshold with a stable resting light vector biased toward the upper-right.
- [x] Increase default sweep sampling from 32 to 64 points.
- [x] Re-run the focused test and confirm all rim policy tests pass.

### Task 2: Smooth the shared tilt

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassMotion.kt`

- [x] Keep the sensor low-pass filter but expose its target through non-bouncy spring animations for X and Y.
- [x] Return the animated tilt to the theme so every surface moves coherently.
- [x] Preserve lifecycle unregistering and the disabled zero state.

### Task 3: Strengthen the complete floating outline

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassSurface.kt`

- [x] Draw a low-alpha neutral border around the complete shape before the sweep rim.
- [x] Increase dynamic rim alpha and width only for `FLOATING` depth.
- [x] Keep panel/control borders quieter and keep the disabled refraction style unchanged.
- [x] Confirm the long bottom-navigation capsule uses the floating depth.

### Task 4: Verify and document

**Files:**
- Modify: `CHANGELOG.md`

- [x] Add a user-facing note about smoother tilt and the continuous bottom-panel outline.
- [x] Run the focused rim tests.
- [x] Run `.\gradlew.bat testDebugUnitTest assembleDebug`.
- [x] Confirm all tests pass and all debug APK variants are generated.
