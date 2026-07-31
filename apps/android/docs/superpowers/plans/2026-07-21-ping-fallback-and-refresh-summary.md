# Ping Fallback and Subscription Refresh Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show useful latency for UDP/Hysteria and TCP-unreachable nodes when the host is still reachable, and always report successful subscription refreshes with subscription/server totals.

**Architecture:** Keep the selected ping method as the primary signal but define an explicit direct-network fallback order: Hysteria/TUIC nodes try ICMP before TCP, while ordinary TCP nodes fall back to ICMP. Carry transport metadata in each independent ping work item. Replace change-detection refresh messaging with a count-based refresh summary; successful network responses continue replacing and saving the profile every time.

**Tech Stack:** Kotlin, Android networking/coroutines, Jetpack ViewModel, JUnit 4, Gradle Android plugin.

---

### Task 1: Define and test ping fallback order

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/PingManager.kt`
- Modify: `app/src/test/java/com/danila/nimbo/network/PingManagerTest.kt`

- [ ] **Step 1: Add failing tests**

Test that direct Hysteria with the default TCP setting attempts `[ICMP, TCP]`, ordinary direct TCP attempts `[TCP, ICMP]`, and proxy mode keeps one HTTP attempt without direct fallbacks.

- [ ] **Step 2: Verify failure**

Run: `./gradlew.bat testDebugUnitTest --tests com.danila.nimbo.network.PingManagerTest`

Expected: compilation fails because `protocolAttempts` and transport-aware pinging do not exist.

- [ ] **Step 3: Implement transport-aware pinging**

Add `protocolAttempts(config, serverProtocol, network)` and `pingNode(host, port, serverProtocol, network, config)`. Execute attempts in order and return the first non-negative latency; keep HTTP proxy mode to one HTTP request.

- [ ] **Step 4: Verify focused tests pass**

Run the focused command from Step 2.

Expected: all `PingManagerTest` tests pass.

### Task 2: Carry node transport into ping jobs

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/PingWorkPlanner.kt`
- Modify: `app/src/test/java/com/danila/nimbo/network/PingWorkPlannerTest.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt`

- [ ] **Step 1: Extend planner tests**

Assert that a Hysteria candidate preserves `serverProtocol = "hysteria"` and `network = "hysteria"` in its work item.

- [ ] **Step 2: Extend candidate and item models**

Add nullable `serverProtocol` and `network` fields and copy them unchanged while preserving one work item per node.

- [ ] **Step 3: Use transport-aware ping execution**

Populate the fields from `Server` and call `PingManager.pingNode` in batch and single-node paths.

### Task 3: Replace “no changes” with refresh totals

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/SubscriptionRefreshPolicy.kt`
- Modify: `app/src/test/java/com/danila/nimbo/network/SubscriptionRefreshPolicyTest.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt`

- [ ] **Step 1: Write failing summary tests**

Test that every successful response counts as an updated subscription even when server fingerprints are identical, and that server totals are summed across refreshed profiles.

- [ ] **Step 2: Implement count-based summary**

Replace fingerprint comparison with `summarize(successfulServerCounts, failedCount)`, returning updated subscription, failed subscription, and total server counts.

- [ ] **Step 3: Update user messages**

For a single refresh, always show `<name> обновлена · <N серверов>`. For refresh-all, show updated subscription count and total server count; include the failure count only for partial failures. Remove all “изменений нет / no changes” messages from the refresh path.

### Task 4: Verify build artifacts

**Files:**
- Verify: all files above

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew.bat testDebugUnitTest`

Expected: zero failures.

- [ ] **Step 2: Run lint and inspect XML**

Run: `./gradlew.bat lintDebug`

Expected: zero lint errors.

- [ ] **Step 3: Rebuild debug APK**

Run: `./gradlew.bat assembleDebug`

Expected: `app/build/outputs/apk/debug/Nimbo_v1.0.1_universal_debug.apk` is regenerated.
