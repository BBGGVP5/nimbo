# Post-update Motion and Background Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the post-update background flow slowly along full-screen trajectories and make update notifications reliably appear while the Android app is in the background.

**Architecture:** Keep the celebration UI in `PostUpdateDialog.kt`, but draw three fixed full-screen Bézier paths and animate only their wave deformation and highlights so the geometry no longer flies corner-to-corner. Split safe foreground update checks from throwing background checks, then let WorkManager retry network failures, check every 15 minutes, and enqueue a catch-up check when `MainActivity` leaves the foreground.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, WorkManager 2.11, OkHttp, JUnit 4.

---

### Task 1: Full-screen flowing update background

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/PostUpdateDialog.kt`

- [x] **Step 1: Replace moving ribbon centers with fixed full-screen paths**

Draw three paths from outside the lower-left edge to outside the upper-right edge. Give every path a different vertical lane, width, curvature, and rounded stroke cap; never translate the whole path across the viewport.

- [x] **Step 2: Animate flow rather than position**

Use a 14-second closed phase. Apply small sine-based control-point deformation and move low-opacity glints along each path. Phase `0f` and `1f` must produce identical geometry.

- [x] **Step 3: Keep Material You decoration calm**

Keep emojis distributed over the whole viewport, slow their drift to the same 14-second cycle, and retain the motion/reduced-transparency switches.

- [x] **Step 4: Compile the Compose UI**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileReleaseKotlin
```

Expected: `BUILD SUCCESSFUL` with no errors from `PostUpdateDialog.kt`.

### Task 2: Retriable background update checks

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateWorker.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdateNotificationPolicyTest.kt`

- [x] **Step 1: Preserve the safe UI API and add a throwing worker API**

Move the existing release lookup into one internal implementation. `checkUpdate(context)` catches and logs errors for UI callers; `checkUpdateInBackground(context)` propagates them so WorkManager can retry.

- [x] **Step 2: Make the worker retry transient failures**

Call `checkUpdateInBackground`. Return `Result.retry()` when GitHub metadata cannot be fetched and `Result.success()` only after a completed check, including the no-update case.

- [x] **Step 3: Retain notification delivery rules**

Keep artifact identity, permission, application notification, and channel checks unchanged so a failed delivery is not recorded as delivered.

- [x] **Step 4: Run policy tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all update policy and notification policy tests pass.

### Task 3: Faster persistent scheduling

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateWorkScheduler.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainActivity.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdateSchedulePolicyTest.kt`

- [x] **Step 1: Add testable schedule constants**

Define a versioned periodic work name, a 15-minute interval, a 5-minute flex window, and a separate foreground-exit work name. Test the exact cadence and distinct unique names.

- [x] **Step 2: Migrate from the old hourly job**

Enqueue the versioned 15-minute request with `ExistingPeriodicWorkPolicy.KEEP`, then cancel the legacy `update_check` job so repeated application starts do not reset the schedule.

- [x] **Step 3: Make immediate work replace stale queued work**

Use `ExistingWorkPolicy.REPLACE` and expedited execution with `RUN_AS_NON_EXPEDITED_WORK_REQUEST` fallback for boot/package-replaced/permission triggers.

- [x] **Step 4: Check once when the app enters background**

In `MainActivity.onStop`, enqueue a uniquely named one-time catch-up check. WorkManager persists it even if the process is removed immediately afterward.

- [x] **Step 5: Verify the full Android change**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin
```

Expected: `BUILD SUCCESSFUL`; the new schedule test passes and both debug and release Kotlin compile.

### Task 4: Manual device verification

**Files:**
- Inspect: Android system notification settings for Nimbo
- Inspect: Nimbo diagnostics log

- [ ] **Step 1: Verify animation**

Install the new APK, open the post-update screen, and confirm the three lines stay anchored across the full viewport while their highlights move slowly for at least 14 seconds.

- [ ] **Step 2: Verify background delivery**

Grant notification permission, put Nimbo in the background, publish or expose a different APK artifact identity, wait up to 15 minutes, and confirm the system notification opens the Updates screen.

- [ ] **Step 3: Verify failure recovery**

Start the check without internet, restore connectivity, and confirm WorkManager retries instead of waiting for the next full periodic interval.
