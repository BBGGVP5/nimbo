# Android Beta 2 Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize every update-size string, repair same-version APK replacement handling, correct the Beta launcher badge, add selectable haptic profiles with a connection-success confirmation, and refresh the shared update/settings presentation without reworking navigation or data models.

**Architecture:** Keep localization and haptic pattern decisions in small pure Kotlin policies covered by unit tests. Compose remains responsible for presentation and selected-language lookup, while the VPN service invokes the same preference-aware haptic engine after the state reaches `CONNECTED`. Visual changes stay inside shared subpage/update/settings primitives so they improve multiple screens consistently and do not duplicate styling.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android adaptive icons, SharedPreferences, Vibrator/VibrationEffect, JUnit 4, Gradle.

---

### Task 1: Localized update sizes and messages

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateUiText.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`
- Test: `app/src/test/java/com/danila/nimbo/ui/screens/UpdateUiTextTest.kt`

- [ ] **Step 1: Write failing formatter tests**

```kotlin
assertEquals("29,31 МБ", UpdateUiText.fileSize(30_733_000, "ru", 2))
assertEquals("29.31 MB", UpdateUiText.fileSize(30_733_000, "en", 2))
assertEquals("Размер APK не совпадает с данными GitHub", UpdateUiText.error("APK_SIZE_MISMATCH", "ru"))
assertEquals("The APK size does not match GitHub data", UpdateUiText.error("APK_SIZE_MISMATCH", "en"))
```

- [ ] **Step 2: Run the targeted test and confirm it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.ui.screens.UpdateUiTextTest"`

Expected: FAIL because `UpdateUiText` does not exist.

- [ ] **Step 3: Implement locale-aware byte units and update-error mapping**

```kotlin
internal object UpdateUiText {
    fun fileSize(bytes: Long, language: String, decimals: Int = 1): String {
        val locale = if (language == "en") Locale.US else Locale.forLanguageTag("ru-RU")
        val unit = if (language == "en") "MB" else "МБ"
        val formatter = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
        return "${formatter.format(bytes / 1_048_576.0)} $unit"
    }
}
```

Use the formatter for release size, downloaded bytes, and total bytes. Localize internal update errors before rendering them.

- [ ] **Step 4: Run the formatter tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.ui.screens.UpdateUiTextTest"`

Expected: PASS.

### Task 2: Robust APK replacement and refreshed update card

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateDownloadPolicy.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdateDownloadPolicyTest.kt`

- [ ] **Step 1: Add a failing response-size reconciliation test**

```kotlin
assertEquals(30_733_000L, UpdateDownloadPolicy.resolvedExpectedBytes(30_700_000L, 30_733_000L))
assertEquals(30_700_000L, UpdateDownloadPolicy.resolvedExpectedBytes(30_700_000L, null))
```

- [ ] **Step 2: Run the policy test and confirm it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.UpdateDownloadPolicyTest"`

Expected: FAIL because `resolvedExpectedBytes` does not exist.

- [ ] **Step 3: Reconcile stale GitHub metadata safely**

Use HTTP `Content-Length`/`Content-Range` only to refresh the expected length for the current response. Keep SHA-256, APK package name, version, versionCode, and signing-certificate checks mandatory; never accept a size change as a substitute for cryptographic verification.

- [ ] **Step 4: Refresh update-card presentation**

Render size, architecture/file, channel/date, and verification as compact icon-labelled metadata rows; use the selected language everywhere. Remove the blurred rectangular overlay from `NimboGlassSection`, keep one bounded gradient, improve progress contrast, and add bottom spacing so navigation never covers the channel control.

- [ ] **Step 5: Run update policy and UI unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.UpdateDownloadPolicyTest" --tests "com.danila.nimbo.ui.screens.UpdateUiTextTest"`

Expected: PASS.

### Task 3: Correct adaptive Beta badge

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_beta_foreground.xml`
- Verify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_nimbo_blue_v2.xml`
- Verify: `app/src/main/res/mipmap-anydpi/ic_launcher_nimbo_blue_v2.xml`

- [ ] **Step 1: Reduce and reposition the badge**

Set the badge item to a compact `23dp × 13dp`, anchored at `top|right` with a safe inset that keeps it in the upper-right blue area instead of covering the cloud.

- [ ] **Step 2: Verify all launcher aliases share the corrected foreground**

Run: `rg -n "ic_launcher_beta_foreground" app/src/main/res/mipmap-anydpi*`

Expected: the Beta aliases and main Beta icon point to the same foreground; monochrome resources contain no colored badge.

- [ ] **Step 3: Compile Android resources**

Run: `./gradlew.bat :app:processDebugResources`

Expected: BUILD SUCCESSFUL.

### Task 4: Haptic profiles and connection confirmation

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/HapticStrength.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/HapticUtils.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainActivity.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`
- Test: `app/src/test/java/com/danila/nimbo/ui/components/HapticStrengthTest.kt`

- [ ] **Step 1: Add failing profile/pattern tests**

```kotlin
assertEquals(HapticStyle.Crisp, HapticStyle.fromPersistedValue(1))
assertTrue(HapticPatternPolicy.confirmation(HapticStrength.Medium, HapticStyle.Double).timings.size > 2)
assertTrue(HapticPatternPolicy.tick(HapticStrength.Medium, HapticStyle.Soft).amplitudes.max() < HapticPatternPolicy.tick(HapticStrength.Medium, HapticStyle.Crisp).amplitudes.max())
```

- [ ] **Step 2: Run haptic tests and confirm they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.ui.components.HapticStrengthTest"`

Expected: FAIL because style-aware patterns do not exist.

- [ ] **Step 3: Implement three haptic profiles**

Add persisted profiles `Soft`, `Crisp`, and `Double`. Generate one-shot or waveform effects from strength plus profile, retaining the platform fallback when custom vibration is unavailable.

- [ ] **Step 4: Add profile selection with live preview**

Extend the existing expandable vibration-strength setting with three labelled profile chips. Updating a chip persists immediately and plays that profile's confirmation preview.

- [ ] **Step 5: Vibrate exactly once after successful connection**

Call the preference-aware confirmation helper immediately after `VpnManager.state` transitions to `CONNECTED`; do not trigger it when the service merely starts or refreshes its notification.

- [ ] **Step 6: Run haptic tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.ui.components.HapticStrengthTest" --tests "com.danila.nimbo.ui.components.HapticFeedbackPolicyTest"`

Expected: PASS.

### Task 5: App-wide visual QA and verification

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`
- Modify only if required by compiler/audit: shared components under `app/src/main/java/com/danila/nimbo/ui/components/`

- [ ] **Step 1: Audit shared surfaces, navigation overlap, contrast, and press states**

Check `NimboSubPageScaffold`, `GlassPanel`, `SettingsCompactCard`, update progress, and haptic selectors at compact width and in light/dark themes. Keep effects clipped to their shapes and remove any full rectangular blur layers.

- [ ] **Step 2: Build and run the complete unit suite**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`

Expected: BUILD SUCCESSFUL and all unit tests pass.

- [ ] **Step 3: Verify release build inputs without publishing**

Run: `./gradlew.bat :app:processReleaseResources :app:compileReleaseKotlin`

Expected: BUILD SUCCESSFUL. The user can then create the signed APK from Android Studio.
