# Android Liquid Glass and Material Expressive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Turn the existing Morphism and Material 3 modes into a complete Liquid Glass mode and a current Material You Expressive mode with a floating rounded bottom navigation bar.

**Architecture:** Preserve persisted style indexes so existing installations keep their selected appearance. Centralize the Liquid Glass rendering in one Compose modifier and route the shared card, header, and navigation primitives through it; update the Material branch through theme shapes, tonal surfaces, and style-specific navigation behavior.

**Tech Stack:** Kotlin, Jetpack Compose, Compose Material 3 1.4.0, Android API 37, JUnit 4

---

### Task 1: Align Compose dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [x] **Step 1: Update the stable Compose BOM**

Set `composeBom` to `2026.06.00`, remove standalone Foundation and UI test version pins, and declare Compose Animation through the BOM.

- [x] **Step 2: Inspect resolved versions**

Run: `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath`

Expected: Compose Material 3 resolves to `1.4.0`, and Compose UI/Foundation/Animation resolve to mutually compatible stable versions.

### Task 2: Centralize visual style behavior

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/theme/VisualStyle.kt`
- Create: `app/src/test/java/com/danila/nimbo/ui/theme/VisualStyleTest.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/theme/NebulaColors.kt`

- [x] **Step 1: Write failing style resolution tests**

Test that stored value `0` resolves to Liquid Glass, stored value `1` resolves to Material You Expressive, and unknown values safely resolve to Liquid Glass.

- [x] **Step 2: Run the focused test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.ui.theme.VisualStyleTest"`

Expected: FAIL because the visual style resolver does not exist.

- [x] **Step 3: Implement stable style resolution and theme tokens**

Add `LIQUID_GLASS` and `MATERIAL_EXPRESSIVE` semantic style identities while preserving integer preferences. Supply expressive Compose `Shapes`, stronger tonal Material surfaces, and Liquid Glass accessibility/reduced-transparency tokens.

- [x] **Step 4: Run the focused test again**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.ui.theme.VisualStyleTest"`

Expected: PASS.

### Task 3: Build the Liquid Glass primitive

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassSurface.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/GlassCard.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/GlassHeader.kt`

- [x] **Step 1: Implement layered glass rendering**

Create a reusable modifier with translucent tint, saturation-like color lift, directional specular highlight, inner hairline, accent refraction, and depth shadow. Provide a more opaque fallback when reduced transparency is enabled.

- [x] **Step 2: Apply the primitive to shared surfaces**

Route `GlassCard` and `GlassHeader` through the modifier in Liquid Glass mode. Keep Material mode tonal and border-light, and preserve the other existing visual styles.

- [x] **Step 3: Compile the Android source**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

### Task 4: Replace bottom navigation visuals

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/BottomBar.kt`

- [x] **Step 1: Implement Liquid Glass navigation**

Render one floating capsule with a refractive rim, animated light sweep, translucent selected lens, spring press feedback, and readable labels.

- [x] **Step 2: Implement Material Expressive navigation**

Render a wide rounded tonal container, pill-shaped active destination, expressive spring motion, dynamic color, and no glass blur or hairline decoration.

- [x] **Step 3: Preserve navigation behavior**

Keep top-level state restoration, single-top navigation, accessibility labels, and haptics unchanged.

### Task 5: Update appearance settings

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppearanceSettingsScreen.kt`

- [x] **Step 1: Rename the style choices**

Present index `0` as `Liquid Glass` and index `1` as `Material You · Android 17`, with Russian descriptions that explain transparency and tonal Material behavior.

- [x] **Step 2: Update quick presets**

Rename `Neo Glass` to `Liquid Glass`, update the Material preset subtitle to `Material 3 Expressive`, and keep all stored indexes compatible.

### Task 6: Verify Android

**Files:**
- Modify if needed: files changed in Tasks 1–5

- [x] **Step 1: Run all unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: BUILD SUCCESSFUL with all tests passing.

- [x] **Step 2: Build debug APKs**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL and ABI plus universal debug APKs are produced.


