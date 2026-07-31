# Liquid Glass Material And Motion Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make dark Liquid Glass genuinely dark and transparency-adjustable, keep elastic deformation attached to horizontal finger movement, and drive optional highlights/refraction from physical device tilt.

**Architecture:** Keep material alpha, gesture intent, and gravity-to-screen mapping in pure policies covered by unit tests. Register one lifecycle-aware gravity sensor listener at the app theme root and publish its smoothed tilt through composition locals, rather than registering one listener per glass surface. Existing appearance preferences remain the source of truth: the transparency slider controls the actual glass material, while a new toggle disables highlights, colored refraction, and sensor use together.

**Tech Stack:** Kotlin 2.2, Jetpack Compose, Android SensorManager/TYPE_GRAVITY, Lifecycle, SharedPreferences, JUnit 4, Gradle.

---

### Task 1: Cover material alpha, gesture direction, and sensor mapping

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassMaterialPolicy.kt`
- Create: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassTiltPolicy.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidInteractionPolicy.kt`
- Create: `app/src/test/java/com/danila/nimbo/ui/components/LiquidGlassMaterialPolicyTest.kt`
- Create: `app/src/test/java/com/danila/nimbo/ui/components/LiquidGlassTiltPolicyTest.kt`
- Modify: `app/src/test/java/com/danila/nimbo/ui/components/LiquidInteractionPolicyTest.kt`

- [x] Test that dark panel, floating, and control alpha stay below their light equivalents and that a transparency-adjusted panel alpha lowers every glass depth.
- [x] Test that reduced transparency produces a deliberately opaque material.
- [x] Test portrait and landscape gravity mapping with clamping to `-1..1`.
- [x] Test that horizontal travel beyond touch slop keeps liquid deformation active while dominant vertical travel cancels it for scrolling.
- [x] Test stronger horizontal expansion and continued pulling for a touch outside the right edge.
- [x] Run the focused tests and confirm they fail before the policies exist.
- [x] Implement `LiquidGlassMaterialPolicy`, `LiquidGlassTiltPolicy`, and the revised gesture/transform functions.
- [x] Re-run the focused tests and confirm they pass.

### Task 2: Make deformation follow the finger

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidInteraction.kt`

- [x] Replace the all-direction touch-slop cancellation with `LiquidInteractionPolicy.shouldCancelForScroll`.
- [x] Classify the gesture once after touch slop so horizontal movement remains elastic for the rest of the press.
- [x] Continue updating the touch point outside the component bounds until release.
- [x] Increase panel intensity enough for horizontal stretch to remain visible while keeping controls stronger than panels.
- [x] Preserve non-consuming Initial-pass observation so clicks and the bottom navigation drag handler continue to work.

### Task 3: Add one lifecycle-aware device-tilt source

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassMotion.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/theme/Theme.kt`

- [x] Implement a single smoothed gravity listener using `TYPE_GRAVITY` with accelerometer fallback and `SENSOR_DELAY_UI`.
- [x] Transform gravity axes using the current display rotation via `LiquidGlassTiltPolicy`.
- [x] Register only while the activity lifecycle is resumed and unregister on pause/disposal.
- [x] Add `LocalLiquidGlassTilt` and `LocalLiquidRefractionEnabled`.
- [x] Enable sensor collection only for Liquid Glass when refraction, transparency, and motion settings allow it.

### Task 4: Darken and animate the material

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassSurface.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/theme/Theme.kt`

- [x] Replace the light navy dark-mode base with a near-black neutral glass tint.
- [x] Derive material and effect alpha from `NebulaColors.panelFill.alpha` so the existing transparency slider changes real Liquid Glass surfaces.
- [x] Reduce dark-theme white sheen and rim strength.
- [x] Shift sheen, border direction, accent refraction, and cool/warm refraction centers from `LocalLiquidGlassTilt`.
- [x] When refraction is disabled, draw only a calm translucent fill and subtle neutral rim without colored highlights.

### Task 5: Persist and expose the refraction toggle

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainActivity.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [x] Add a `liquid_refraction_enabled` preference/state/property defaulting to true and update it from the preference listener and reload paths.
- [x] Observe the preference in `MainActivity` and pass it to `NebulaGuardTheme`.
- [x] Add a Liquid Glass-only “Блики и преломление” switch in style details with text explaining device-tilt behavior.
- [x] Keep the existing transparency slider but update its help text so its direction and effect are unambiguous.
- [x] Include the toggle in the style reset state and reset action.

### Task 6: Verify and document

**Files:**
- Modify: `CHANGELOG.md`

- [x] Document darker adjustable glass, finger-following deformation, tilt highlights, and the new switch.
- [x] Run the focused policy tests.
- [x] Run `.\gradlew.bat testDebugUnitTest`.
- [x] Run `.\gradlew.bat assembleDebug`.
- [x] Confirm all test suites pass and list the generated APK variants.
