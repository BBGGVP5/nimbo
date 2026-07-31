# Android Haptic Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add consistent, optional haptic feedback to Android navigation, sliders, VPN actions, subscription refreshes, and ping actions.

**Architecture:** Store one enabled-by-default preference in `PreferencesManager` and provide a preference-aware `HapticFeedback` implementation at the Compose root. Existing haptic calls then obey the setting automatically. Route slider changes through a shared bucket policy so continuous dragging feels tactile without vibrating on every pixel, and add feedback only to action paths that currently have none.

**Tech Stack:** Kotlin 2.2, Jetpack Compose Material 3, SharedPreferences, JUnit 4, Gradle.

---

### Task 1: Add a tested slider haptic policy

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/components/HapticFeedbackPolicy.kt`
- Create: `app/src/test/java/com/danila/nimbo/ui/components/HapticFeedbackPolicyTest.kt`

- [x] Write tests for continuous slider buckets, discrete slider steps, clamping, and unchanged buckets.
- [x] Run `.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.ui.components.HapticFeedbackPolicyTest"` and confirm the tests fail before implementation.
- [x] Implement the smallest pure policy that maps slider values to tactile buckets.
- [x] Re-run the focused test and confirm it passes.

### Task 2: Add the global haptic preference and provider

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/HapticUtils.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainActivity.kt`

- [x] Add an enabled-by-default `hapticFeedbackEnabled` preference and observable Compose state.
- [x] Add a preference-aware `HapticFeedback` delegate that suppresses every app haptic when disabled.
- [x] Add a shared haptic slider wrapper that emits one short tick per policy bucket.
- [x] Provide the delegate above the app theme from `MainActivity`.

### Task 3: Expose the setting and cover navigation/actions

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NetworkToolsScreens.kt`

- [x] Add the “Виброотклик / Haptic feedback” toggle to general settings.
- [x] Emit short feedback for real destination changes and in-app back navigation.
- [x] Keep existing connect, refresh, ping, tab, and menu feedback under the global provider.
- [x] Add feedback to the standalone custom ping action where no shared haptic button is used.

### Task 4: Add stepped feedback to sliders

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppearanceSettingsScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/HomeScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/SettingsScreen.kt`

- [x] Replace standard Material sliders with the shared haptic slider.
- [x] Route the custom HSV slider through the shared bucketed change handler.
- [x] Confirm programmatic value updates do not emit vibration.

### Task 5: Verify and document

**Files:**
- Modify: `CHANGELOG.md`

- [x] Run the focused haptic policy unit tests.
- [x] Run the full debug unit test suite.
- [x] Assemble the debug APK.
- [x] Add a concise user-facing changelog entry covering the toggle and tactile actions.
