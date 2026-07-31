# Subscription Refresh Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make subscription refreshes complete sooner and clearly report progress, success, and failure through a more polished notification surface.

**Architecture:** Keep subscription parsing and profile ownership in the existing managers and view model. Reduce enrichment latency inside `SubscriptionManager`, make refresh orchestration await real jobs in `MainViewModel`, and render notification state transitions in the existing Compose overlay.

**Tech Stack:** Kotlin, coroutines, OkHttp, Jetpack Compose Material 3, JUnit/Gradle verification.

---

### Task 1: Stabilize notification state

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/TopSnackbars.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/MainScreen.kt`

- [x] **Step 1: Prevent stale auto-dismiss jobs from clearing newer notifications**

Keep one dismissal `Job`, cancel it when the notification changes, and only clear the matching notification id.

- [x] **Step 2: Report every manual refresh outcome**

Emit a success message containing the refreshed profile name and server count; emit a sanitized error for refresh failures.

- [x] **Step 3: Animate state changes inside the visible notification**

Use `AnimatedVisibility` for the container and `AnimatedContent` for progress-to-result transitions, plus a type-colored icon surface, progress accent, semantics, and close action.

- [x] **Step 4: Compile the app**

Run: `./gradlew.bat :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

### Task 2: Correct refresh orchestration

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt`

- [x] **Step 1: Return the launched subscription job**

Let callers await the exact work already tracked in `subscriptionJobs`.

- [x] **Step 2: Await all refresh jobs instead of sleeping for two seconds**

Launch profile refreshes concurrently, call `joinAll()`, calculate successful and failed profiles from their resulting state, then display one summary.

- [x] **Step 3: Keep post-refresh ping from replacing the success message**

Run the optional ping silently after the refreshed data has been committed so the success state remains visible.

- [x] **Step 4: Compile and run unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL` with all tests passing.

### Task 3: Reduce subscription enrichment latency

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/SubscriptionManager.kt`

- [x] **Step 1: Determine which enrichment data is actually missing**

Skip alternate clients when the primary response already contains Hysteria links, useful names, and descriptions.

- [x] **Step 2: Fetch a bounded priority list concurrently**

Probe only the client formats relevant to the missing data, run those independent requests on `Dispatchers.IO`, and reuse the responses for both Hysteria merging and metadata enrichment.

- [x] **Step 3: Preserve current fallback selection rules**

Prefer named Hysteria links, fall back to generic Hysteria only when necessary, and enrich names/descriptions without overwriting already useful values.

- [x] **Step 4: Run subscription-focused and full tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.*"`

Expected: `BUILD SUCCESSFUL`.

---

No commit steps are included because `C:/Users/Danila/AndroidStudioProjects/Nimbo` is not a Git worktree.
