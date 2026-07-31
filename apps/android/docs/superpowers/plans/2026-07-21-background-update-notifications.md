# Background Update Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver new-version notifications from WorkManager without requiring the user to open Nimbo, while never consuming an update notification when Android has blocked notifications.

**Architecture:** Move update work registration into a dedicated scheduler that preserves the periodic cadence and can enqueue an immediate check after boot or package replacement. Gate notification persistence behind Android permission/app/channel availability, so an undeliverable notification remains eligible for the next background run.

**Tech Stack:** Kotlin, Android WorkManager 2.11, BroadcastReceiver, NotificationCompat, JUnit 4.

---

### Task 1: Persistent periodic and system-event scheduling

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/network/UpdateWorkScheduler.kt`
- Modify: `app/src/main/java/com/danila/nimbo/NebulaGuardApplication.kt`
- Modify: `app/src/main/java/com/danila/nimbo/BootRestoreReceiver.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdateWorkSchedulerTest.kt`

- [x] **Step 1: Write the failing scheduling-policy test**

```kotlin
@Test fun `normal boot and package replacement request an immediate check`() {
    assertTrue(UpdateWorkScheduler.shouldEnqueueImmediate(Intent.ACTION_BOOT_COMPLETED))
    assertTrue(UpdateWorkScheduler.shouldEnqueueImmediate(Intent.ACTION_MY_PACKAGE_REPLACED))
    assertFalse(UpdateWorkScheduler.shouldEnqueueImmediate(Intent.ACTION_LOCKED_BOOT_COMPLETED))
}
```

- [x] **Step 2: Run the test and verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests com.danila.nimbo.network.UpdateWorkSchedulerTest`

Expected: compilation failure because `UpdateWorkScheduler` does not exist.

- [x] **Step 3: Implement the scheduler**

```kotlin
object UpdateWorkScheduler {
    private const val PERIODIC_WORK = "update_check"
    private const val IMMEDIATE_WORK = "update_check_immediate"

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    internal fun shouldEnqueueImmediate(action: String): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
}
```

- [x] **Step 4: Wire application and receiver entry points**

`NebulaGuardApplication.onCreate()` calls `schedulePeriodic()`. `BootRestoreReceiver` calls `schedulePeriodic()` for normal boot/package replacement and `enqueueImmediate()` when `shouldEnqueueImmediate()` is true, before applying the independent VPN restore policy. Locked boot does not touch WorkManager's credential-protected database.

- [x] **Step 5: Run the focused scheduling test**

Run: `.\gradlew.bat testDebugUnitTest --tests com.danila.nimbo.network.UpdateWorkSchedulerTest`

Expected: all scheduler policy tests pass.

### Task 2: Do not consume blocked notifications

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/network/UpdateNotificationPolicy.kt`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt:530-598`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateWorker.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainActivity.kt:74-78`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdateNotificationPolicyTest.kt`

- [x] **Step 1: Write failing delivery-policy tests**

```kotlin
@Test fun `notification is recorded only when every Android gate is open`() {
    assertTrue(UpdateNotificationPolicy.canPost(true, true, true))
    assertFalse(UpdateNotificationPolicy.canPost(false, true, false))
    assertFalse(UpdateNotificationPolicy.canPost(true, false, true))
}
```

- [x] **Step 2: Run the test and verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests com.danila.nimbo.network.UpdateNotificationPolicyTest`

Expected: compilation failure because `UpdateNotificationPolicy` does not exist.

- [x] **Step 3: Implement the delivery policy**

```kotlin
internal object UpdateNotificationPolicy {
    fun canPost(permissionGranted: Boolean, appNotificationsEnabled: Boolean, channelEnabled: Boolean): Boolean =
        permissionGranted && appNotificationsEnabled && channelEnabled
}
```

- [x] **Step 4: Gate and reorder notification persistence**

Create the update channel, evaluate `POST_NOTIFICATIONS` on API 33+, `NotificationManagerCompat.areNotificationsEnabled()`, and channel importance. Return `false` without changing `lastUpdateNotifiedArtifactId` when blocked. Call `notify()` first, then persist the exact artifact identity and return `true`.

- [x] **Step 5: Make worker delivery observable**

`UpdateWorker` logs whether the notification was posted or deferred because Android notifications are unavailable. A deferred release remains eligible for the next periodic check. When Android 13+ permission is granted, `MainActivity` enqueues an immediate check so the deferred artifact is retried without waiting an hour.

- [x] **Step 6: Run focused and full verification**

Run: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug`

Expected: all unit tests pass, lint has zero errors, and debug APK assembly succeeds.

This workspace has no `.git` directory, so the green focused tests are the implementation checkpoints instead of commits.
