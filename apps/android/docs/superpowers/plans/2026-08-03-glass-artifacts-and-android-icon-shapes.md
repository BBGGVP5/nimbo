# Glass Artifacts and Android Icon Shapes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove the remaining rectangular glass artifacts, make launcher-icon previews match installed resources, and provide Android-style visual icon shape selection.

**Architecture:** Keep `LiquidGlassSurface` as the single renderer for liquid-glass depth and remove secondary blurred radial layers from consumers. Render ready launcher icons directly from Android resources. Extend the custom icon renderer with stable persisted shape indices and use the same paths for both launcher generation and selector swatches.

**Tech Stack:** Kotlin, Jetpack Compose, Android Canvas/Path, Android adaptive icon resources, JUnit.

---

### Task 1: Remove duplicate rectangular glass layers

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/GlassHeader.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/SettingsComponents.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/SubscriptionInfoCard.kt`

1. Delete the blurred radial overlay from `GlassPanel`; `liquidGlassSurface` already owns the glass rendering.
2. Replace remaining clipped radial backgrounds in headers and settings icons with shape-safe linear or solid fills.
3. Compile the Android sources to catch modifier/import regressions.

### Task 2: Make ready-icon previews exact

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppIconSettingsScreen.kt`

1. Render each ready icon from `AppIconManager.IconOption.previewRes` through `AppIconResourceImage`.
2. Remove the separately composed orange Beta badge so its scale and placement come from the real launcher resource.
3. Keep the full adaptive-icon safe zone visible in both the selected preview and gallery tiles.

### Task 3: Add Android-style icon shape masks and picker

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/utils/CustomAppIconManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppIconSettingsScreen.kt`
- Test: `app/src/test/java/com/danila/nimbo/utils/CustomIconShapeTest.kt`

1. Preserve indices 0–2 and append clover, flower, and arch masks.
2. Expose a shape-only bitmap renderer based on the production mask path.
3. Replace text/full-icon shape cards with a compact horizontal Android-style swatch tray.
4. Clamp persisted values using `CustomIconShape.entries.lastIndex`.
5. Verify stable old indices, new round trips, and invalid-index fallback.

### Task 4: Verify and publish sources

**Files:**
- Sync the modified Android files into `C:/Users/Danila/Desktop/nimbo-beta2-publish/apps/android/`

1. Run focused unit tests and `compileDebugKotlin` (or the closest available Gradle task).
2. Inspect the diff for unrelated changes.
3. Commit and push only the intended source changes; request a fresh signed APK build afterward.
