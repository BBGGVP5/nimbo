# Appearance Animation and Icon Gallery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every existing Android background animation selectable from the appearance UI, expose the complete built-in icon collection including neon variants, and remove the redundant update-protection copy from the desktop settings page.

**Architecture:** The Android app already renders each background through `BackgroundStyleMode`; the work wires the existing IDs into clear visual picker tiles and persists the complete ID range. Existing manifest launcher aliases are promoted into the public `AppIconManager` catalogue so selecting an icon continues to use Android's native component-alias mechanism. The desktop change is deliberately scoped to deleting only the duplicate explanatory row.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android component aliases, React, TypeScript, Vite.

---

### Task 1: Remove redundant desktop update copy

**Files:**
- Modify: `C:\Users\Danila\Desktop\nimbo-app-main\apps\ui\src\pages\Settings.tsx:1805-1807`
- Test: `C:\Users\Danila\Desktop\nimbo-app-main\apps\ui\package.json`

- [ ] **Step 1: Write the failing visual acceptance check**

Open the update settings section and confirm the standalone row below “Скачивать только по Wi‑Fi” is present:

```tsx
<div className="settings-row settings-row-block update-protection-copy">
  <div className="settings-row-description">{m.settings.updateDownloadProtection}</div>
</div>
```

- [ ] **Step 2: Remove the duplicate row**

Delete the exact `update-protection-copy` block while leaving the in-progress download hint intact:

```tsx
{installing && (
  <div className="update-progress-card" aria-live="polite">
    <div className="update-progress-heading">
      <div>
        <strong>{progress?.stage === "verifying" ? m.settings.verifyingUpdate : m.settings.downloadUpdate}</strong>
        <span>{m.settings.updateDownloadProtection}</span>
      </div>
    </div>
  </div>
)}
```

- [ ] **Step 3: Build the desktop UI**

Run: `npm run build` from `C:\Users\Danila\Desktop\nimbo-app-main\apps\ui`  
Expected: TypeScript and Vite finish without errors.

- [ ] **Step 4: Commit**

```bash
git add apps/ui/src/pages/Settings.tsx
git commit -m "fix: remove redundant desktop update copy"
```

### Task 2: Add a complete Android background-animation picker

**Files:**
- Modify: `C:\Users\Danila\AndroidStudioProjects\Nimbo\app\src\main\java\com\danila\nimbo\ui\screens\NimboMiniApp.kt:11762-11790,13282-13340`
- Modify: `C:\Users\Danila\AndroidStudioProjects\Nimbo\app\src\main\java\com\danila\nimbo\ui\screens\AppearanceSettingsScreen.kt:630-749,1310-1326`
- Modify: `C:\Users\Danila\AndroidStudioProjects\Nimbo\app\src\main\java\com\danila\nimbo\utils\PreferencesManager.kt:764-770`
- Test: `C:\Users\Danila\AndroidStudioProjects\Nimbo\app\src\test\java\com\danila\nimbo\utils\PreferencesManagerTest.kt`

- [ ] **Step 1: Write the failing persistence test**

Add a unit test that proves the “Нет” option (ID `15`) is kept instead of coerced to the prior final animation ID:

```kotlin
@Test
fun backgroundStyle_keeps_none_option() {
    preferencesManager.backgroundStyle = 15
    assertEquals(15, preferencesManager.backgroundStyle)
}
```

- [ ] **Step 2: Run the test to verify the current bug**

Run: `./gradlew.bat :app:testDebugUnitTest --tests '*PreferencesManagerTest.backgroundStyle_keeps_none_option' --no-daemon`  
Expected: FAIL because `backgroundStyle` is currently clamped to `14`.

- [ ] **Step 3: Preserve all selectable background IDs**

Replace the style clamp with the inclusive range used by `BackgroundStyleMode`:

```kotlin
var backgroundStyle: Int
    get() = sharedPreferences.getInt(KEY_BACKGROUND_STYLE, sharedPreferences.getInt(KEY_VISUAL_STYLE, 0))
    set(value) {
        val safe = value.coerceIn(0, 15)
        sharedPreferences.edit().putInt(KEY_BACKGROUND_STYLE, safe).apply()
        backgroundStyleState.value = safe
    }
```

- [ ] **Step 4: Replace generic presets with named animation cards**

Use a shared list of background choices with icon, Russian/English labels, and IDs `0..15`. Render it in three-column Compose flow grids on both appearance entry points; selecting a tile writes `preferencesManager.backgroundStyle`. The visual list must include circles, rings, dots, aurora, grid, shapes, waves, snow, neon, space, fire, lava, Nordic, blossom, and no background. Keep the existing movement switch, but rename it to a movement enable/disable control so it does not masquerade as the selector.

```kotlin
BackgroundAnimationTile(
    option = option,
    selected = backgroundStyle == option.id,
    onClick = { preferencesManager.backgroundStyle = option.id },
    modifier = Modifier.weight(1f)
)
```

- [ ] **Step 5: Run Android compilation and tests**

Run: `./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`  
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt app/src/main/java/com/danila/nimbo/ui/screens/AppearanceSettingsScreen.kt app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt app/src/test/java/com/danila/nimbo/utils/PreferencesManagerTest.kt
git commit -m "feat: add selectable animated backgrounds"
```

### Task 3: Expose the complete Android icon collection

**Files:**
- Modify: `C:\Users\Danila\AndroidStudioProjects\Nimbo\app\src\main\java\com\danila\nimbo\utils\AppIconManager.kt:23-75`
- Test: `C:\Users\Danila\AndroidStudioProjects\Nimbo\app\src\test\java\com\danila\nimbo\utils\AppIconManagerTest.kt`

- [ ] **Step 1: Write the failing catalogue test**

Add a test for the publicly selectable aliases:

```kotlin
@Test
fun icon_catalogue_includes_neon_aliases() {
    assertTrue(AppIconManager.ICON_OPTIONS.any { it.aliasSuffix == "AliasSprite0017" })
    assertTrue(AppIconManager.ICON_OPTIONS.size >= 19)
}
```

- [ ] **Step 2: Run the test to verify the current limitation**

Run: `./gradlew.bat :app:testDebugUnitTest --tests '*AppIconManagerTest.icon_catalogue_includes_neon_aliases' --no-daemon`  
Expected: FAIL because only six launcher aliases are in `ICON_OPTIONS`.

- [ ] **Step 3: Register every built-in alias as an icon option**

Move `AliasSprite0006` through `AliasSprite0018` into `SELECTABLE_ALIASES` and append one `IconOption` for each existing `R.mipmap.ic_alias_0006` … `R.mipmap.ic_alias_0018`. Give the gallery human-readable names including neon, chrome, ice, wood, pixel, and amber variants. Keep `ALL_ALIAS_SUFFIXES` as the single cleanup list so changing between any gallery item disables every older alias.

```kotlin
IconOption(
    aliasSuffix = "AliasSprite0017",
    previewRes = R.mipmap.ic_alias_0017,
    title = "Неон",
    description = "Контрастная неоновая молния",
    backgroundColor = 0xFF5A3CFF.toInt()
)
```

- [ ] **Step 4: Run Android compilation and tests**

Run: `./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`  
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/danila/nimbo/utils/AppIconManager.kt app/src/test/java/com/danila/nimbo/utils/AppIconManagerTest.kt
git commit -m "feat: expand launcher icon gallery"
```

## Self-review

- Background selection is addressed in Task 2 on both Android appearance screens; the existing renderer already supplies each selected effect.
- Neon and other previously packaged launcher assets become actionable system icons in Task 3, rather than merely visible previews.
- The desktop screenshot's redundant text and padding are removed in Task 1 without deleting useful update progress status.
- The scope intentionally does not add network, update, or installer behavior.
