# Android Live Network Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Nimbo Liquid Glass react subtly to real VPN state, latency, recovery, upload, and download activity without adding fake telemetry or distracting motion.

**Architecture:** A pure policy maps existing `VpnManager` values to a compact visual signal. A Compose overlay renders low-alpha incoming/outgoing light, a slow latency wave, and fragmented recovery arcs only on the bottom glass capsule and connection control. Existing animation, refraction, and reduced-transparency policies remain authoritative.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, Compose animation, JUnit 4.

---

### Task 1: Test the visual signal policy

**Files:**
- Create: `app/src/test/java/com/danila/nimbo/ui/components/LiveNetworkGlassPolicyTest.kt`
- Create: `app/src/main/java/com/danila/nimbo/ui/components/LiveNetworkGlass.kt`

- [ ] **Step 1: Write the failing policy tests**

Test that disconnected state is dormant, connected idle state is calm, real byte rates produce bounded upload/download levels, high latency selects delayed mode, and any recovery state takes precedence.

```kotlin
assertEquals(LiveNetworkGlassMode.DORMANT, signal(VpnState.DISCONNECTED).mode)
assertEquals(LiveNetworkGlassMode.CALM, signal(VpnState.CONNECTED).mode)
assertEquals(LiveNetworkGlassMode.DELAYED, signal(VpnState.CONNECTED, pingMs = 480).mode)
assertEquals(LiveNetworkGlassMode.RECOVERING, signal(VpnState.CONNECTED, recovery = VpnRecoveryStatus.RETRYING).mode)
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.ui.components.LiveNetworkGlassPolicyTest"`

Expected: compilation failure because `LiveNetworkGlassPolicy` does not exist.

- [ ] **Step 3: Implement the bounded policy**

Create `LiveNetworkGlassMode`, `LiveNetworkGlassSignal`, and `LiveNetworkGlassPolicy.signal`. Use logarithmic traffic normalization so background sync remains quiet and large transfers approach, but never exceed, `1f`. Do not infer packet loss from latency.

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.ui.components.LiveNetworkGlassPolicyTest"`

Expected: all policy tests pass.

### Task 2: Render the restrained network overlay

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiveNetworkGlass.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [ ] **Step 1: Add the Compose signal reader**

Read `VpnManager.state`, `recoveryStatus`, `uploadSpeed`, `downloadSpeed`, and the active server ping. Return the pure policy result so recomposition follows actual runtime state.

- [ ] **Step 2: Add the Canvas overlay**

Render cyan incoming and warm outgoing glints with alpha capped below `0.14`. Draw a slow, thin wave only in delayed mode and broken rim arcs only while connecting/recovering. Stop all infinite motion when disabled or dormant.

- [ ] **Step 3: Attach only to primary glass surfaces**

Place the overlay behind content in `NimboBottomControls` for Liquid Glass only. Add a smaller version to the VPN FAB so traffic is visible near the action that owns the connection.

- [ ] **Step 4: Respect accessibility and existing switches**

Require `rememberMiniMotionEnabled()` and the existing Liquid Glass refraction local. Render nothing when reduced transparency/refraction is disabled. Keep the overlay non-interactive and without semantics.

### Task 3: Verify Android

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Document the user-visible behavior**

State that the effect is limited to Liquid Glass, uses real traffic/recovery state, remains restrained, and follows existing animation/refraction switches.

- [ ] **Step 2: Run tests and build**

Run: `gradlew.bat testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`, no test failures, and all three debug APK variants produced.

- [ ] **Step 3: Inspect packaging**

Confirm the final APKs remain split into ARM64, ARMv7, and universal variants and that the new work adds no native dependency.
