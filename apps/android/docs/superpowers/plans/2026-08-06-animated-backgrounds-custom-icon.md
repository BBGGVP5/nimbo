# Анимированные фоны и пользовательская иконка — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Сделать пресеты фоновой анимации в разделе «Внешний вид» действительно живыми и визуально разными, а пользовательскую иконку из конструктора надежно добавлять на рабочий стол Android.

**Architecture:** `AnimatedGradientBackground` будет единой Canvas-точкой отрисовки: один бесшовный phase управляет всеми пресетами, при этом отключение анимации оставляет статичный кадр. Для произвольной иконки используется поддерживаемый Android `ShortcutManager`: bitmap сохраняется для уведомлений, а стабильный pinned shortcut обновляется по одному ID; предустановленные иконки продолжают работать через activity-alias.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, Material 3, Android ShortcutManager, existing PreferencesManager/AppIconSettingsScreen.

---

### Task 1: Развести названия и режимы фоновых пресетов

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppearanceSettingsScreen.kt`
- Test: `app/src/test/java/com/danila/nimbo/ui/theme/BackgroundStyleModeTest.kt`

- [x] **Step 1: Add a stable `NONE` mode and keep persisted indexes backward compatible.**
  `BackgroundStyleMode.NONE` is appended to the enum; existing indexes 0–14 keep their meaning and index 15 maps to it.
- [x] **Step 2: Replace technical labels with user-facing preset names.**
  The selector labels become `Круги`, `Кольца`, `Точки`, `Аврора`, `Сетка`, `Формы`, `Волны`, `Снег`, `Неон`, `Космос`, `Огонь`, `Лава`, `Неон+`, `Север`, `Цветение`, `Нет` while keeping the same numeric preference values.
- [x] **Step 3: Add a pure mapping test.**
  Assert that 0 maps to `MORPHISM`, 14 maps to `BLOSSOM`, 15 maps to `NONE`, and an unknown value falls back to `MORPHISM`.
- [x] **Step 4: Run the focused test.**
  Run `.\gradlew.bat :app:testDebugUnitTest --tests '*BackgroundStyleModeTest'`; expect PASS.

### Task 2: Implement low-noise, continuously animated background presets

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/AnimatedGradientBackground.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/theme/Theme.kt`

- [x] **Step 1: Keep a single 38-second looping phase and remove the early return that suppresses all patterns when reduced transparency is enabled.**
  Reduced transparency must lower opacity/blur only; `NONE` remains the only mode that draws no pattern.
- [x] **Step 2: Add deterministic primitives for the reference styles.**
  Use fixed seeded coordinates and `sin/cos` drift so circles, rings, diagonal grid, polygons, snowflakes, and particles move continuously without jumps. Draw all strokes with low alpha behind cards and never over the foreground content.
- [x] **Step 3: Make each selected mode render its own pattern.**
  Circles use slow orbiting blobs; rings use expanding/contracting outlines; dots use staggered drift; grid uses diagonal lines; forms use translucent polygons; snow uses sparse falling flakes; particles use sparse points with short trails; other existing gradient presets keep their current palette.
- [x] **Step 4: Honor the animation switch.**
  When disabled, phase is fixed and every element remains at its initial position; when enabled, the same frame values animate from the phase without a visible restart seam.
- [x] **Step 5: Compile the UI.**
  Run `.\gradlew.bat :app:compileDebugKotlin`; expect BUILD SUCCESSFUL.

### Task 3: Make custom launcher artwork a real Android desktop shortcut

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/utils/CustomAppIconManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/AppIconManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppIconSettingsScreen.kt`
- Test: `app/src/test/java/com/danila/nimbo/utils/CustomAppIconManagerTest.kt`

- [x] **Step 1: Build one stable shortcut descriptor.**
  Add `CUSTOM_SHORTCUT_ID = "nimbo_custom_icon"` and a helper that creates a `ShortcutInfo` with the rendered bitmap, `MainActivity` launch intent, and localized labels.
- [x] **Step 2: Update existing pinned shortcut or request it once.**
  `applyCustomLauncherIcon` writes the PNG/cache used by notifications, calls `ShortcutManager.updateShortcuts` when the stable ID is already pinned, otherwise calls `requestPinShortcut`; it returns a result describing `updated`, `requested`, or `unsupported`.
- [x] **Step 3: Stop relying on a launcher-process drawable reading app-private storage.**
  Keep the custom aliases only as a compatibility fallback, but do not report them as the successful desktop result. Preset aliases continue through `setAppIcon` unchanged.
- [x] **Step 4: Wire the constructor button to the result.**
  Show “Ярлык добавлен/обновлён”, “Подтвердите добавление ярлыка в системном окне”, or a clear unsupported message. Keep notification preview and custom notification icon behavior intact.
- [x] **Step 5: Add pure tests for shortcut labels/ID and result mapping.**
  Test that the ID is stable and that repeated application selects update semantics rather than creating a second ID.
- [x] **Step 6: Run tests and compile.**
  Run `.\gradlew.bat :app:testDebugUnitTest :app:compileReleaseKotlin`; expect BUILD SUCCESSFUL.

### Task 4: Final verification

**Files:**
- Verify: `app/src/main/java/com/danila/nimbo/ui/components/AnimatedGradientBackground.kt`
- Verify: `app/src/main/java/com/danila/nimbo/utils/CustomAppIconManager.kt`
- Verify: `app/src/main/java/com/danila/nimbo/ui/screens/AppIconSettingsScreen.kt`

- [x] **Step 1: Check all persisted background indexes.**
  Confirm existing users keep their selected style and new `Нет` is selectable.
- [ ] **Step 2: Check visual behavior manually on a debug APK.**
  Select each preset, wait through a full loop, toggle “Анимация фона”, and verify cards/text remain readable.
- [ ] **Step 3: Check custom icon flow manually on Android 8+.**
  Apply a custom constructor icon, accept the system pin dialog, apply a second design, and verify the same desktop icon updates instead of creating duplicates.
- [x] **Step 4: Record the changed files and tell the user that a fresh APK build is required.**
