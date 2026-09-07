# Origin-Aware Pixel Bursts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make status pixels visibly launch from the exact connection, ping, or refresh button that initiated an action, then finish along the screen edges.

**Architecture:** A remembered controller owns the current event, the last connection-button origin, and short echo suppression so direct taps do not duplicate observed state transitions. A CompositionLocal exposes a scoped emitter to reusable buttons; the Canvas uses a source-aware quadratic trajectory and retains the existing bottom-to-top edge fallback for non-interactive events.

**Tech Stack:** Kotlin, Jetpack Compose coordinates/CompositionLocal/Canvas, JUnit 4, Gradle.

---

### Task 1: Add a source-aware event controller

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/NetworkEdgeBurst.kt`
- Modify: `app/src/test/java/com/danila/nimbo/ui/components/NetworkEdgeBurstPolicyTest.kt`

- [ ] **Step 1: Test that the controller preserves the initiating connection origin**

```kotlin
@Test fun connected_result_reuses_connection_origin() {
    val controller = NetworkEdgeBurstController(clockMillis = { 1_000L })
    val origin = Offset(120f, 640f)
    controller.emit(EdgeBurstTrigger.CONNECTING, origin)
    controller.observe(EdgeBurstSnapshot(VpnState.DISCONNECTED, false, false))
    controller.observe(EdgeBurstSnapshot(VpnState.CONNECTED, false, false))
    assertEquals(origin, controller.event?.origin)
    assertEquals(EdgeBurstTrigger.CONNECTED, controller.event?.trigger)
}
```

- [ ] **Step 2: Test that a direct ping suppresses its immediate state echo**

```kotlin
@Test fun direct_ping_is_not_duplicated_by_state_echo() {
    var now = 1_000L
    val controller = NetworkEdgeBurstController(clockMillis = { now })
    controller.observe(EdgeBurstSnapshot(VpnState.DISCONNECTED, false, false))
    controller.emit(EdgeBurstTrigger.PING, Offset(300f, 80f))
    val directId = controller.event!!.id
    now += 50L
    controller.observe(EdgeBurstSnapshot(VpnState.DISCONNECTED, true, false))
    assertEquals(directId, controller.event!!.id)
}
```

- [ ] **Step 3: Implement controller and scoped emitter**

```kotlin
internal class NetworkEdgeBurstController(private val clockMillis: () -> Long) {
    var event by mutableStateOf<EdgeBurstEvent?>(null)
        private set
    fun emit(trigger: EdgeBurstTrigger, origin: Offset?) { /* publish direct event */ }
    fun observe(snapshot: EdgeBurstSnapshot) { /* publish non-duplicate transition */ }
}

internal val LocalNetworkEdgeBurstEmitter = staticCompositionLocalOf<(EdgeBurstTrigger, Offset?) -> Unit> {
    { _, _ -> }
}
```

- [ ] **Step 4: Run focused tests**

Run: `./gradlew testDebugUnitTest --tests "com.danila.nimbo.ui.components.NetworkEdgeBurstPolicyTest"`

Expected: all policy and controller tests pass.

### Task 2: Emit from real buttons

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [ ] **Step 1: Provide the emitter at the root**

Wrap the app `Box` in `CompositionLocalProvider(LocalNetworkEdgeBurstEmitter provides controller::emit)` and render `controller.event` in `NetworkEdgeBurstOverlay`.

- [ ] **Step 2: Capture reusable ping/refresh button centers**

```kotlin
var burstOrigin by remember { mutableStateOf<Offset?>(null) }
Modifier.onGloballyPositioned { coordinates ->
    val topLeft = coordinates.positionInRoot()
    burstOrigin = topLeft + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
}
```

Before `MiniSquareIconButton` invokes its action, emit `PING` for `MiniIconMotion.Ping` and `REFRESH` for `MiniIconMotion.Refresh`.

- [ ] **Step 3: Capture both connection-button centers**

Apply the same coordinate capture to `WindowsConnectionButton` and `WindowsConnectionButtonCompact`, then emit `CONNECTING` before invoking the VPN action.

### Task 3: Animate from origin to boundary

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/NetworkEdgeBurst.kt`

- [ ] **Step 1: Add origin to the event**

```kotlin
internal data class EdgeBurstEvent(val id: Long, val trigger: EdgeBurstTrigger, val origin: Offset?)
```

- [ ] **Step 2: Use quadratic particle paths**

For source-aware events, start each square at `origin`, push it outward using a radial control point, and end it in one of three lanes near the left or right screen edge above the viewport. Keep the current vertical side stream when `origin == null`.

- [ ] **Step 3: Verify complete build**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`, all tests pass, and all debug APK variants are rebuilt.

## Self-review

- Connection, ping, and refresh taps use their real global centers.
- Connected green and disconnected red results reuse the last connection origin.
- State observation remains a fallback for menu actions and background operations.
- Direct button actions do not create a second duplicate burst.
