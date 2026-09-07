# Beta Icon Gallery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the generated Nimbo Beta artwork the default launcher icon and add six ready-to-use custom icon designs plus an obvious custom-image upload flow.

**Architecture:** Keep Android launcher aliases for real launcher icon switching, use the generated Beta bitmap for the default adaptive launcher alias, and keep arbitrary user imagery in the existing safe pinned-shortcut path. Define preset metadata beside the renderer so previews and applied values cannot drift apart.

**Tech Stack:** Kotlin, Jetpack Compose, Android adaptive icons, ShortcutManager, JVM unit tests.

---

### Task 1: Define the ready-made icon gallery

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/utils/CustomAppIconManager.kt`
- Test: `app/src/test/java/com/danila/nimbo/utils/CustomAppIconPresetTest.kt`

- [ ] **Step 1: Write the failing preset test**

```kotlin
@Test fun `gallery exposes at least six unique presets`() {
    assertTrue(CustomAppIconManager.presets.size >= 6)
    assertEquals(CustomAppIconManager.presets.size, CustomAppIconManager.presets.map { it.config }.distinct().size)
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.utils.CustomAppIconPresetTest"`
Expected: failure because `presets` is not defined.

- [ ] **Step 3: Add six named, visually distinct preset configurations**

```kotlin
data class CustomAppIconPreset(val title: String, val config: CustomAppIconConfig)

val presets = listOf(
    CustomAppIconPreset("Nimbo", CustomAppIconConfig(CustomIconShape.SQUIRCLE, 0xFF1769E0.toInt(), 0xFFF4F7FF.toInt(), CustomCloudStyle.ORIGINAL, false, null)),
    CustomAppIconPreset("Полночь", CustomAppIconConfig(CustomIconShape.ROUNDED, 0xFF0C1738.toInt(), 0xFFF4F7FF.toInt(), CustomCloudStyle.SOLID, false, null)),
    CustomAppIconPreset("Аврора", CustomAppIconConfig(CustomIconShape.SQUIRCLE, 0xFF6A4CFF.toInt(), 0xFF9ED1FF.toInt(), CustomCloudStyle.SOLID, false, null)),
    CustomAppIconPreset("Мята", CustomAppIconConfig(CustomIconShape.CIRCLE, 0xFF008D78.toInt(), 0xFF78F0D0.toInt(), CustomCloudStyle.SOLID, false, null)),
    CustomAppIconPreset("Закат", CustomAppIconConfig(CustomIconShape.ROUNDED, 0xFFFF6B35.toInt(), 0xFFFFD166.toInt(), CustomCloudStyle.OUTLINE, false, null)),
    CustomAppIconPreset("Жемчуг", CustomAppIconConfig(CustomIconShape.SQUIRCLE, 0xFFF2F5FC.toInt(), 0xFF151A2F.toInt(), CustomCloudStyle.OUTLINE, false, null))
)
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.utils.CustomAppIconPresetTest"`
Expected: `BUILD SUCCESSFUL`.

### Task 2: Show presets and make custom upload immediately effective

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppIconSettingsScreen.kt`

- [ ] **Step 1: Render a two-row, three-column preset gallery**

```kotlin
IconPresetGrid(
    presets = CustomAppIconManager.presets,
    onSelected = { preset -> applyConfig(preset.config) }
)
```

- [ ] **Step 2: Apply all preset fields from one click**

```kotlin
customIconShape = config.shape.ordinal
customIconBackgroundColor = config.backgroundColor
customIconCloudColor = config.cloudColor
customIconCloudStyle = config.cloudStyle.ordinal
customIconUseImported = false
```

- [ ] **Step 3: Make gallery upload explicit and activate the imported image**

```kotlin
customIconBase64 = value
customIconUseImported = true
preferencesManager.customAppIconBase64 = value
preferencesManager.customIconUseImported = true
```

- [ ] **Step 4: Explain the Android launcher limitation beside the pin action**

Keep the existing message that arbitrary images require a system-confirmed pinned shortcut, while packaged aliases can replace the real launcher activity icon.

### Task 3: Keep Beta artwork as the packaged default

**Files:**
- Verify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_nimbo_blue_v2.xml`
- Verify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_nimbo_blue_v2_round.xml`
- Modify: `app/src/main/java/com/danila/nimbo/utils/AppIconManager.kt`

- [ ] **Step 1: Verify both adaptive icon files use the generated artwork**

```xml
<foreground android:drawable="@drawable/nimbo_beta_notification" />
```

- [ ] **Step 2: Rename the default gallery entry to Nimbo Beta**

```kotlin
0 -> "Nimbo Beta"
```

- [ ] **Step 3: Run full verification**

Run: `.\gradlew.bat testDebugUnitTest assembleRelease`
Expected: `BUILD SUCCESSFUL` for tests, R8, resources and release APK packaging.
