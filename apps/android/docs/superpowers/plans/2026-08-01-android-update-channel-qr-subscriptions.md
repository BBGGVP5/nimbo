# Android Update Channel, QR, and Subscription Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android update channel selectable, prevent QR scanner crashes, and make subscription auto-refresh follow the user interval without duplicate or foreground notifications.

**Architecture:** Replace the read-only text-field dropdown with a click-only anchored menu. Move subscription scheduling to one persistent WorkManager chain constrained by connectivity, gate notifications on real changes and app background state, and feed worker changes back into the active ViewModel. Bundle the ML Kit scanner and close CameraX resources with the composable lifecycle.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, CameraX, bundled ML Kit Barcode Scanning, WorkManager, Kotlin Flow, JUnit 4.

---

### Task 1: Update-channel control

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`

- [ ] Replace `OutlinedTextField`/`ExposedDropdownMenuBox` with a `Box`, clickable `Surface`, and `DropdownMenu` so the value cannot enter Android text-selection mode.
- [ ] Keep the selected-channel icon, accent border, localized labels, and immediate update recheck.
- [ ] Run `./gradlew :app:compileDebugKotlin` and expect `BUILD SUCCESSFUL`.

### Task 2: QR scanner lifecycle

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/QrScannerScreen.kt`

- [ ] Replace the Play Services barcode dependency with `com.google.mlkit:barcode-scanning:17.3.0` so scanning does not depend on a separately downloaded module.
- [ ] Hold scanner, executor, provider, and analysis use case in remembered state; catch provider/client failures and show an in-app error state.
- [ ] Close the scanner, clear the analyzer, unbind the camera, and stop the executor in `DisposableEffect.onDispose`.
- [ ] Deliver only the first non-empty QR value on the main thread.
- [ ] Run `./gradlew :app:assembleDebug` and expect `BUILD SUCCESSFUL`.

### Task 3: Subscription scheduling policy

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/service/SubscriptionRefreshSchedulePolicy.kt`
- Create: `app/src/test/java/com/danila/nimbo/service/SubscriptionRefreshSchedulePolicyTest.kt`
- Modify: `app/src/main/java/com/danila/nimbo/service/SubscriptionUpdateScheduler.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainActivity.kt`

- [ ] Add pure policy functions that convert the saved seconds to a safe delay and decide whether a profile is due:

```kotlin
fun delaySeconds(configuredSeconds: Int): Long = configuredSeconds.coerceAtLeast(300).toLong()

fun isDue(nowMs: Long, lastSuccessMs: Long, configuredSeconds: Int): Boolean =
    lastSuccessMs <= 0L || nowMs - lastSuccessMs >= delaySeconds(configuredSeconds) * 1_000L
```

- [ ] Test first run, not-yet-due, due, and minimum-delay cases.
- [ ] Replace periodically replaced work with one unique connected one-time request; use `KEEP` when ensuring it exists and `APPEND_OR_REPLACE` when the worker schedules its successor.
- [ ] Remove the duplicate Activity scheduling call; keep application and boot scheduling.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*SubscriptionRefreshSchedulePolicyTest'` and expect all tests to pass.

### Task 4: Notification and foreground behavior

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/utils/AppVisibilityTracker.kt`
- Create: `app/src/main/java/com/danila/nimbo/service/SubscriptionUpdateEvents.kt`
- Modify: `app/src/main/java/com/danila/nimbo/NebulaGuardApplication.kt`
- Modify: `app/src/main/java/com/danila/nimbo/service/SubscriptionUpdateWorker.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt`

- [ ] Register an activity lifecycle tracker from `Application` and expose `isForeground`.
- [ ] In the worker, refresh only due profiles, record every successful check time, and count a notification-worthy update only when the saved profile actually changes.
- [ ] Show the system notification only when changes exist, the setting is enabled, and the app is not foreground.
- [ ] Emit a process-local event after saved changes and reload ViewModel state from preferences.
- [ ] Respect `updateSubOnStartup`; make automatic startup refresh silent and never tie it to VPN connection.
- [ ] Always enqueue the next connected refresh after a successful worker run.

### Task 5: Settings and filenames

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/SubscriptionSettingsScreen.kt`
- Modify: `app/build.gradle.kts`

- [ ] Reschedule work only from actual auto-update toggle/interval handlers, not from entering a settings screen.
- [ ] Use the global user-selected interval as the scheduling source of truth.
- [ ] Name release APKs `Nimbo_v<version>_<abi>.apk`; retain `_debug` only for debug builds.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:assembleRelease` and expect `BUILD SUCCESSFUL`.

### Task 6: Final verification

**Files:**
- Verify all modified files above.

- [ ] Run `./gradlew :app:lintDebug :app:testDebugUnitTest` and expect no errors.
- [ ] Verify the update channel opens without a selection toolbar, QR opens and closes repeatedly, foreground refresh has no system notification, and a delayed offline refresh runs after connectivity returns.
- [ ] Review `git diff --check` in the clean publishing checkout before pushing sources.
