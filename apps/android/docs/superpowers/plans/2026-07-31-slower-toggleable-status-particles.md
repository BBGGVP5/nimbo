# Slower Toggleable Status Particles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the status burst into a slower, fuller particle stream and add an independent user setting that disables it.

**Architecture:** A small immutable visual specification defines duration, particle count, and trail density for deterministic tuning and tests. `PreferencesManager` persists a default-on `status_particles_enabled` flag; the root overlay observes it, while the Theme screen exposes it as a normal switch independent of the background-animation control.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, SharedPreferences, JUnit 4, Gradle.

---

### Task 1: Specify slower particle behavior

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/NetworkEdgeBurst.kt`
- Modify: `app/src/test/java/com/danila/nimbo/ui/components/NetworkEdgeBurstPolicyTest.kt`

- [ ] **Step 1: Add a failing visual-spec test**

```kotlin
@Test fun particle_spec_is_slow_and_dense() {
    assertTrue(NetworkEdgeBurstVisualSpec.durationMillis >= 1_800)
    assertTrue(NetworkEdgeBurstVisualSpec.particleCount >= 26)
    assertTrue(NetworkEdgeBurstVisualSpec.trailCount >= 3)
}
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.danila.nimbo.ui.components.NetworkEdgeBurstPolicyTest"`

Expected: compilation fails because `NetworkEdgeBurstVisualSpec` is unresolved.

- [ ] **Step 3: Implement and consume the visual specification**

```kotlin
internal object NetworkEdgeBurstVisualSpec {
    const val durationMillis = 1_900
    const val particleCount = 28
    const val trailCount = 3
}
```

Use the specification in the tween and particle loop. Draw three progressively smaller, dimmer trail particles along each source-aware curve and along edge-only fallback paths.

- [ ] **Step 4: Run the focused test and verify success**

Run: `./gradlew testDebugUnitTest --tests "com.danila.nimbo.ui.components.NetworkEdgeBurstPolicyTest"`

Expected: all focused tests pass.

### Task 2: Persist an independent toggle

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`

- [ ] **Step 1: Add the preference key and observable state**

```kotlin
private const val KEY_STATUS_PARTICLES_ENABLED = "status_particles_enabled"
val statusParticlesEnabledState = mutableStateOf(sharedPreferences.getBoolean(KEY_STATUS_PARTICLES_ENABLED, true))
```

- [ ] **Step 2: Add synchronized getter/setter and refresh paths**

```kotlin
var statusParticlesEnabled: Boolean
    get() = sharedPreferences.getBoolean(KEY_STATUS_PARTICLES_ENABLED, true)
    set(value) {
        sharedPreferences.edit().putBoolean(KEY_STATUS_PARTICLES_ENABLED, value).apply()
        statusParticlesEnabledState.value = value
    }
```

Update the shared-preference listener, full state refresh, and imported-settings refresh with the same default value.

### Task 3: Expose and apply the setting

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [ ] **Step 1: Gate the root overlay**

Observe `preferencesManager.statusParticlesEnabledState` and require both this value and the existing global animation value when setting `NetworkEdgeBurstOverlay.enabled`.

- [ ] **Step 2: Add a Theme switch**

```kotlin
ThemeSwitchRow(
    title = t("Статусные частицы", "Status particles"),
    subtitle = t("Вылетают из кнопок при сетевых действиях", "Launch from buttons during network actions"),
    icon = Icons.Default.Grain,
    checked = statusParticlesEnabled,
    onCheckedChange = { preferencesManager.statusParticlesEnabled = it }
)
```

- [ ] **Step 3: Document and verify**

Update `CHANGELOG.md`, then run `./gradlew testDebugUnitTest assembleDebug`.

Expected: all tests pass, the build succeeds, and all debug APK variants are rebuilt.

## Self-review

- Duration is materially slower than 1.15 seconds.
- The whole path reads as particles through multiple trailing tiles.
- The feature is default-on and independently switchable in Theme settings.
- Disabling it takes effect immediately and survives restart/import.
