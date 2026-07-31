# Haptic Strength and iOS Liquid Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore bottom-navigation haptics, let users select haptic strength, and upgrade Nimbo Glass into a clearly previewed iOS-style Liquid Glass theme.

**Architecture:** Keep the existing global Compose haptic provider, but have it drive Android's vibrator with a tested three-level pulse policy and fall back to platform haptics when custom vibration is unavailable. Add the missing haptic call at the active mini-app bottom navigation path. Reuse the shared Liquid Glass modifier in the mini-app panels and bottom bar, and update the interface-style preview so Nimbo Glass visibly represents the iOS-inspired material.

**Tech Stack:** Kotlin 2.2, Jetpack Compose Material 3, Android Vibrator/VibrationEffect APIs, SharedPreferences, JUnit 4, Gradle.

---

### Task 1: Define and test haptic strength

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/components/HapticStrength.kt`
- Create: `app/src/test/java/com/danila/nimbo/ui/components/HapticStrengthTest.kt`

- [x] Add failing tests proving persisted values map to Light, Medium, and Strong while invalid values fall back to Medium.
- [x] Add failing tests proving tick and confirmation pulses increase in duration and amplitude with strength.
- [x] Run `.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.ui.components.HapticStrengthTest"` and confirm the missing model fails compilation.
- [x] Implement `HapticStrength`, `HapticPulse`, and `HapticPulsePolicy`.
- [x] Re-run the focused test and confirm it passes.

### Task 2: Persist and perform custom-strength haptics

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/HapticUtils.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainActivity.kt`

- [x] Add the normal `android.permission.VIBRATE` permission.
- [x] Add an enabled-by-default Medium strength preference and observable state.
- [x] Extend the root haptic provider to use amplitude-controlled one-shot vibrations on supported devices.
- [x] Fall back to platform haptics if the vibrator is missing, fails, or cannot be addressed.
- [x] Pass the observed strength from `MainActivity` into the provider.

### Task 3: Fix bottom navigation and expose strength

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [x] Emit a haptic tick in `NimboBottomControls` only when the target destination differs from the current destination.
- [x] Add Light, Medium, and Strong controls below the global vibration switch.
- [x] Keep the strength selector hidden while vibration is disabled.
- [x] Ensure selecting a new strength itself produces one tick at the previous active strength.

### Task 4: Upgrade Nimbo Glass to iOS Liquid Glass

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassSurface.kt`

- [x] Route mini-app `GlassPanel` surfaces through the shared Liquid Glass material when Nimbo Glass is selected.
- [x] Layer the shared floating Liquid Glass material over the real captured backdrop in the bottom navigation.
- [x] Strengthen directional highlights, refracted accent light, and inner rim while respecting reduced transparency.
- [x] Rename the preview kind to iOS Glass and update the Nimbo Glass subtitle to “iOS Liquid Glass”.
- [x] Render a colorful background, translucent controls, glossy borders, and floating navigation in the iOS Glass preview.

### Task 5: Verify and document

**Files:**
- Modify: `CHANGELOG.md`

- [x] Add user-facing changelog entries for bottom-navigation haptics, strength selection, and upgraded Nimbo Glass.
- [x] Run the focused strength tests.
- [x] Run the complete debug unit test suite.
- [x] Assemble all debug APK variants.
