# Independent Node Pings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Measure and cache ping independently for every node, even when multiple nodes resolve to the same `host:port`.

**Architecture:** Separate a node's ping-result identity from the older general-purpose `pingKey`, then make the batch planner preserve one work item per node instead of grouping by network endpoint. Apply every result only to its originating node; JSON and route equivalence will not be used to copy results.

**Tech Stack:** Kotlin, Android ViewModel/coroutines, JUnit 4, Gradle Android plugin.

---

### Task 1: Define node-specific ping identity and work planning

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/model/Server.kt`
- Create: `app/src/main/java/com/danila/nimbo/network/PingWorkPlanner.kt`
- Modify: `app/src/test/java/com/danila/nimbo/model/ServerSelectionTest.kt`
- Create: `app/src/test/java/com/danila/nimbo/network/PingWorkPlannerTest.kt`

- [ ] **Step 1: Write failing identity and planner tests**

Add assertions that two named CDN nodes with the same endpoint retain different `pingMeasurementKey()` values, and that two candidates with the same endpoint produce two `PingWorkItem` values.

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew.bat testDebugUnitTest --tests com.danila.nimbo.model.ServerSelectionTest --tests com.danila.nimbo.network.PingWorkPlannerTest`

Expected: compilation fails because `pingMeasurementKey` and `PingWorkPlanner` do not exist.

- [ ] **Step 3: Implement the minimal policy**

Add `Server.pingMeasurementKey()` as the existing stable ping identity plus normalized node name and remote template hints. Add `PingWorkPlanner.build`, which maps each resolved candidate directly to one work item and reports unresolved keys separately without grouping by endpoint.

- [ ] **Step 4: Re-run focused tests**

Run the focused command from Step 2.

Expected: both test classes pass.

### Task 2: Remove endpoint-based result reuse

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt`

- [ ] **Step 1: Replace batch endpoint grouping**

Build a `PingWorkPlan` for each five-node chunk and launch one coroutine per `PingWorkItem`; enqueue each measurement only under its `resultKey`.

- [ ] **Step 2: Stop spreading a single-node result**

Replace the `host:port` scan in `pingSingleServer` with `updateServersPings(mapOf(server.pingMeasurementKey() to pingValue))`.

- [ ] **Step 3: Use measurement identity throughout ping state**

Use `pingMeasurementKey()` for active ping animation keys, pending results, owner lookup, state updates, selected-server refresh, and persistent ping cache calls. Keep `pingKey()` for unrelated pinned, hidden, naming, and selection preferences.

### Task 3: Align cache and UI lookup keys

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/HomeScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/ProfileServersScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [ ] **Step 1: Persist pings per measurement identity**

Save and restore cached pings with `pingMeasurementKey()` so two nodes sharing an endpoint cannot overwrite each other.

- [ ] **Step 2: Render and animate per-node values**

Build ping maps and check `activePingKeys` using `pingMeasurementKey()`. Do not change keys used for pins, hidden servers, display-name overrides, or list selection.

### Task 4: Verify the Android build

**Files:**
- Verify: all files above

- [ ] **Step 1: Run unit tests**

Run: `./gradlew.bat testDebugUnitTest`

Expected: all tests pass with zero failures.

- [ ] **Step 2: Run lint**

Run: `./gradlew.bat lintDebug`

Expected: Gradle succeeds and the lint XML contains zero errors.

- [ ] **Step 3: Build APK**

Run: `./gradlew.bat assembleDebug`

Expected: `app/build/outputs/apk/debug/Nimbo_v1.0.1_universal_debug.apk` is rebuilt successfully.
