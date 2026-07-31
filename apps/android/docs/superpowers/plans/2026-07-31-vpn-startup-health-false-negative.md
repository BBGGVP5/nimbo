# VPN Startup Health False-Negative Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop an unavailable or slow third-party HTTPS endpoint from tearing down a successfully started VPN tunnel while keeping real Xray and physical-network failures blocking.

**Architecture:** Move startup acceptance into a pure policy that distinguishes confirmed, provisional, and rejected startup states. Probe independent HTTPS targets concurrently through the local Xray proxy; use a successful response as confirmation, but accept a live core and usable underlying network provisionally when all external probes time out.

**Tech Stack:** Kotlin, Android `VpnService`, Kotlin coroutines, libXray, JUnit 4, Gradle.

---

### Task 1: Specify startup acceptance without external-endpoint coupling

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/TunnelHealthPolicy.kt`
- Modify: `app/src/test/java/com/danila/nimbo/vpn/TunnelHealthPolicyTest.kt`

- [x] **Step 1: Add failing policy tests**

Add assertions for the following exact states:

```kotlin
assertEquals(
    TunnelHealthPolicy.StartupAcceptance.CONFIRMED,
    TunnelHealthPolicy.startupAcceptance(true, true, listOf(-1, 120))
)
assertEquals(
    TunnelHealthPolicy.StartupAcceptance.PROVISIONAL,
    TunnelHealthPolicy.startupAcceptance(true, true, listOf(-1, -1))
)
assertEquals(
    TunnelHealthPolicy.StartupAcceptance.REJECTED,
    TunnelHealthPolicy.startupAcceptance(false, true, listOf(120))
)
assertEquals(
    TunnelHealthPolicy.StartupAcceptance.REJECTED,
    TunnelHealthPolicy.startupAcceptance(true, false, listOf(120))
)
```

- [x] **Step 2: Run the focused test and confirm failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.vpn.TunnelHealthPolicyTest"
```

Expected: compilation fails because `StartupAcceptance` and `startupAcceptance` do not exist.

- [x] **Step 3: Implement the pure acceptance policy**

Add this API to `TunnelHealthPolicy`:

```kotlin
enum class StartupAcceptance { CONFIRMED, PROVISIONAL, REJECTED }

fun startupAcceptance(
    coreRunning: Boolean,
    underlyingNetworkAvailable: Boolean,
    latenciesMs: List<Int>
): StartupAcceptance = when {
    !coreRunning || !underlyingNetworkAvailable -> StartupAcceptance.REJECTED
    isHealthy(latenciesMs) -> StartupAcceptance.CONFIRMED
    else -> StartupAcceptance.PROVISIONAL
}
```

- [x] **Step 4: Re-run the focused policy test**

Expected: all `TunnelHealthPolicyTest` tests pass.

### Task 2: Make HTTPS verification diagnostic and bounded

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`

- [x] **Step 1: Probe startup endpoints concurrently**

Replace the sequential `hasHealthySelectedOutbound()` loop with the existing `probeThroughSelectedOutbound()` coroutine fan-out so the whole check is bounded by one endpoint timeout rather than their sum.

- [x] **Step 2: Apply the acceptance policy**

After the stabilization delay, preserve hard rejection when Xray stopped or the physical network disappeared. For a live core and network, return success for both `CONFIRMED` and `PROVISIONAL`; log the latter as an inconclusive external check and do not call `XrayManager.disconnect()` or `recordConnectionFailure()`.

```kotlin
return when (TunnelHealthPolicy.startupAcceptance(
    coreRunning = XrayManager.isConnected,
    underlyingNetworkAvailable = hasUsableUnderlyingNetwork(),
    latenciesMs = latencies
)) {
    TunnelHealthPolicy.StartupAcceptance.CONFIRMED -> true
    TunnelHealthPolicy.StartupAcceptance.PROVISIONAL -> true
    TunnelHealthPolicy.StartupAcceptance.REJECTED -> false
}
```

- [x] **Step 3: Keep handoff settling bounded**

When a real Wi-Fi/mobile handoff is within its grace period, retry the concurrent diagnostic once after the existing short delay. Never restart Xray solely because both external endpoints remain unavailable.

### Task 3: Verify regression safety and produce an APK

**Files:**
- Verify: `app/src/main/java/com/danila/nimbo/vpn/TunnelHealthPolicy.kt`
- Verify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`
- Verify: `app/src/test/java/com/danila/nimbo/vpn/TunnelHealthPolicyTest.kt`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Update the user changelog**

Document that unavailable control websites no longer cause a false connection failure and that checks run in parallel.

- [x] **Step 2: Run all Android unit tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: zero failures and zero errors.

- [x] **Step 3: Assemble the debug APK**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL` and architecture-specific plus universal APK files under `app/build/outputs/apk/debug/`.
