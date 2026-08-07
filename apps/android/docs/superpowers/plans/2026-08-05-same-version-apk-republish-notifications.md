# Same-Version APK Republish Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reliably notify Android users when the APK attached to their already-installed GitHub release is replaced, explain why the same version must be installed again, and remind them without hourly spam until the corrected artifact is installed.

**Architecture:** Keep exact artifact comparison in the existing pure update policy and add a pure notification-delivery policy for new artifacts and repair reminders. The background worker continues to perform network checks, while `UpdateManager` owns Android notification rendering and persists delivery only after Android accepts the post. The foreground launch check uses a shorter cache window so a same-release replacement becomes visible promptly inside the app as well.

**Tech Stack:** Kotlin, Android WorkManager, NotificationCompat, SharedPreferences, OkHttp, JUnit 4, Jetpack Compose.

---

### Task 1: Repair-specific changelog

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdatePolicy.kt`
- Modify: `app/src/test/java/com/danila/nimbo/network/UpdatePolicyTest.kt`

- [x] **Step 1: Add a failing test for release notes on a replaced APK**

```kotlin
assertTrue(
    UpdatePolicy.changelog("## Existing notes", UpdateKind.REPAIR, null, false)
        .startsWith("Файл этой версии был заменён")
)
```

- [x] **Step 2: Run the focused policy test**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.UpdatePolicyTest"`

Expected: FAIL because non-empty release notes are currently returned without a repair explanation.

- [x] **Step 3: Prefix repair notes with a localized explanation**

```kotlin
val repairNotice = if (isEnglish) {
    "The APK for this version was replaced: install the corrected build again."
} else {
    "Файл этой версии был заменён: установите исправленную сборку повторно."
}
return listOf(repairNotice, releaseNotes.trim()).filter(String::isNotBlank).joinToString("\n\n")
```

Keep ordinary version updates unchanged and retain the existing commit/fallback behavior when release notes are empty.

- [x] **Step 4: Re-run the focused policy test**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.UpdatePolicyTest"`

Expected: PASS.

### Task 2: New-artifact alert and bounded repair reminders

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateNotificationPolicy.kt`
- Modify: `app/src/test/java/com/danila/nimbo/network/UpdateNotificationPolicyTest.kt`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`

- [x] **Step 1: Add failing reminder-policy tests**

```kotlin
assertTrue(UpdateNotificationPolicy.shouldPost("new", "old", UpdateKind.REPAIR, 100L, 101L))
assertFalse(UpdateNotificationPolicy.shouldPost("same", "same", UpdateKind.REPAIR, 100L, 101L))
assertTrue(UpdateNotificationPolicy.shouldPost("same", "same", UpdateKind.REPAIR, 0L, 43_200_001L))
assertFalse(UpdateNotificationPolicy.shouldPost("same", "same", UpdateKind.VERSION, 0L, 86_400_000L))
```

- [x] **Step 2: Run the notification-policy test**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.UpdateNotificationPolicyTest"`

Expected: FAIL because reminder-aware deduplication does not exist.

- [x] **Step 3: Implement a twelve-hour repair reminder window**

```kotlin
const val REPAIR_REMINDER_INTERVAL_MS = 12L * 60L * 60L * 1000L

fun shouldPost(identity: String, lastIdentity: String?, kind: UpdateKind, lastAt: Long, now: Long): Boolean =
    identity != lastIdentity ||
        (kind == UpdateKind.REPAIR && now - lastAt >= REPAIR_REMINDER_INTERVAL_MS)
```

Clamp negative clock differences to zero so a system clock correction cannot trigger a notification loop.

- [x] **Step 4: Apply the policy and make replacement alerts audible**

In `showUpdateNotification`, evaluate `shouldPost` before rendering. Keep one update notification slot so obsolete cards do not accumulate, set `setOnlyAlertOnce(false)`, add a current timestamp, use the action label `Обновить` / `Update`, and describe a repair as “APK этой же версии заменён — установите исправленную сборку”. Persist the exact artifact and timestamp only after `notify()` succeeds.

- [x] **Step 5: Re-run notification tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.UpdateNotificationPolicyTest"`

Expected: PASS.

### Task 3: Fresher checks and background scheduling

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateWorkScheduler.kt`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateWorker.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [x] **Step 1: Prevent cached release metadata**

Append a millisecond cache-buster to the GitHub releases request and send both `Cache-Control: no-cache, no-store` and `Pragma: no-cache`. Continue validating the downloaded APK with GitHub SHA-256, package name, version code and signing certificate.

- [x] **Step 2: Refresh the periodic WorkManager definition**

Use `ExistingPeriodicWorkPolicy.UPDATE` so users upgrading from an older scheduler configuration receive the current one-hour interval and connected-network constraint instead of retaining an obsolete request forever.

- [x] **Step 3: Shorten the foreground check throttle**

Change the launch check window from six hours to thirty minutes. Manual checks on the update screen remain immediate.

- [x] **Step 4: Clarify worker logs**

Log `replacement APK` for `UpdateKind.REPAIR` and `new version` for `UpdateKind.VERSION`, without writing URLs or sensitive device data.

### Task 4: Verification

**Files:**
- Verify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Verify: `app/src/main/java/com/danila/nimbo/network/UpdateWorker.kt`
- Verify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [x] **Step 1: Run focused update tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.UpdatePolicyTest" --tests "com.danila.nimbo.network.UpdateNotificationPolicyTest" --tests "com.danila.nimbo.network.UpdateManagerTest"`

Expected: PASS.

- [x] **Step 2: Run the complete Android verification**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`

Expected: BUILD SUCCESSFUL and three ABI-specific debug APKs produced.

- [ ] **Step 3: Manual behavior check**

Install an APK through Nimbo, replace the matching GitHub release asset without changing the tag or version code, wait for the immediate/manual check, and confirm that the UI says the same-version APK was replaced. The first background detection must alert; dismissing it without installing may create one reminder only after twelve hours; installing the corrected APK must stop further repair notifications.
