# Liquid Glass Elastic Pull And Perimeter Rim Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace mechanical affine stretching with a resistant gel-like pull and make tilt-driven highlights flow around the complete glass perimeter.

**Architecture:** Keep the elastic resistance curve and perimeter-light intensity as pure functions so their behavior is deterministic and unit-tested. Compose observes the raw pointer non-destructively, spring-filters its position, and applies the policy transform. Liquid Glass uses a sampled sweep gradient with a nonzero base rim around the whole shape, replacing the two hard-coded top/right highlight lines.

**Tech Stack:** Kotlin 2.2, Jetpack Compose animation and drawing, sweep gradients, JUnit 4, Gradle.

---

### Task 1: Test natural elastic resistance

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidInteractionPolicy.kt`
- Modify: `app/src/test/java/com/danila/nimbo/ui/components/LiquidInteractionPolicyTest.kt`

- [x] Add a test proving horizontal pull stretches the horizontal axis more than the perpendicular axis.
- [x] Add a test proving movement beyond the edge still increases pull but with diminishing resistance.
- [x] Run the focused test and confirm the linear transform fails the diminishing-resistance assertion.
- [x] Replace hard clamping with a saturating elastic curve and add a small perpendicular squeeze.
- [x] Re-run the focused test and confirm it passes.

### Task 2: Add spring-filtered finger tracking

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidInteraction.kt`

- [x] Spring-filter pointer X and Y before calculating the transform so the surface lags slightly behind the finger.
- [x] Use a controlled damping ratio for press/release without repeated wobble.
- [x] Preserve horizontal tracking outside bounds and dominant-vertical scroll cancellation.
- [x] Keep control, floating, and panel intensities distinct.

### Task 3: Test the complete moving perimeter

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassRimPolicy.kt`
- Create: `app/src/test/java/com/danila/nimbo/ui/components/LiquidGlassRimPolicyTest.kt`

- [x] Test that every sampled perimeter position retains a visible base intensity.
- [x] Test that rotating the tilt vector moves the brightest sample to a different perimeter sector.
- [x] Test that the first and final sample join continuously.
- [x] Run the focused test and confirm it fails before the policy exists.
- [x] Implement angular distance, tilt light angle, and sampled white/color intensity functions.
- [x] Re-run the focused test and confirm it passes.

### Task 4: Draw the sweep rim

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassSurface.kt`

- [x] Build a sweep-gradient brush from `LiquidGlassRimPolicy` samples.
- [x] Keep a low but nonzero neutral/colored rim over the complete outline.
- [x] Move white, cool, warm, and accent peaks around the perimeter using the shared device tilt.
- [x] Remove the hard-coded top and right drawLine highlights.
- [x] Preserve the static neutral rim when highlights and refraction are disabled.

### Task 5: Verify and document

**Files:**
- Modify: `CHANGELOG.md`

- [x] Add user-facing notes for resistant elastic pull and full-perimeter highlights.
- [x] Run the focused policy tests.
- [x] Run `.\gradlew.bat testDebugUnitTest assembleDebug`.
- [x] Confirm all tests pass and all debug APK variants are generated.
