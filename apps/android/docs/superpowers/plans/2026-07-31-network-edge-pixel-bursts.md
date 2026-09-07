# Network Edge Pixel Bursts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace continuous live-network glass glows with a short, one-shot stream of colored pixel squares that climbs the screen edges on VPN, ping, and subscription-refresh events.

**Architecture:** A pure transition policy converts state changes into semantic burst triggers without firing on initial composition. A single root-level Compose overlay animates small squares up the left and right display edges; the screen owns one monotonic event id so identical consecutive actions replay correctly.

**Tech Stack:** Kotlin, Jetpack Compose Canvas/Animatable, JUnit 4, Gradle.

---

### Task 1: Specify one-shot event transitions

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/components/NetworkEdgeBurst.kt`
- Create: `app/src/test/java/com/danila/nimbo/ui/components/NetworkEdgeBurstPolicyTest.kt`

- [ ] **Step 1: Write the failing policy tests**

```kotlin
class NetworkEdgeBurstPolicyTest {
    @Test fun initial_snapshot_is_silent() {
        assertNull(NetworkEdgeBurstPolicy.trigger(null, EdgeBurstSnapshot(VpnState.DISCONNECTED, false, false)))
    }

    @Test fun vpn_transitions_have_status_triggers() {
        val idle = EdgeBurstSnapshot(VpnState.DISCONNECTED, false, false)
        assertEquals(EdgeBurstTrigger.CONNECTING, NetworkEdgeBurstPolicy.trigger(idle, idle.copy(vpnState = VpnState.CONNECTING)))
        assertEquals(EdgeBurstTrigger.CONNECTED, NetworkEdgeBurstPolicy.trigger(idle.copy(vpnState = VpnState.CONNECTING), idle.copy(vpnState = VpnState.CONNECTED)))
        assertEquals(EdgeBurstTrigger.DISCONNECTED, NetworkEdgeBurstPolicy.trigger(idle.copy(vpnState = VpnState.CONNECTED), idle))
    }

    @Test fun work_starts_only_on_rising_edge() {
        val idle = EdgeBurstSnapshot(VpnState.DISCONNECTED, false, false)
        assertEquals(EdgeBurstTrigger.PING, NetworkEdgeBurstPolicy.trigger(idle, idle.copy(isPinging = true)))
        assertEquals(EdgeBurstTrigger.REFRESH, NetworkEdgeBurstPolicy.trigger(idle, idle.copy(isRefreshing = true)))
        assertNull(NetworkEdgeBurstPolicy.trigger(idle.copy(isPinging = true), idle.copy(isPinging = true)))
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.danila.nimbo.ui.components.NetworkEdgeBurstPolicyTest"`

Expected: compilation fails because `NetworkEdgeBurstPolicy` does not exist.

- [ ] **Step 3: Implement the transition model and policy**

```kotlin
internal enum class EdgeBurstTrigger { CONNECTING, CONNECTED, DISCONNECTED, PING, REFRESH }

internal data class EdgeBurstSnapshot(
    val vpnState: VpnState,
    val isPinging: Boolean,
    val isRefreshing: Boolean
)

internal object NetworkEdgeBurstPolicy {
    fun trigger(previous: EdgeBurstSnapshot?, current: EdgeBurstSnapshot): EdgeBurstTrigger? {
        if (previous == null) return null
        if (previous.vpnState != current.vpnState) {
            return when (current.vpnState) {
                VpnState.CONNECTING -> EdgeBurstTrigger.CONNECTING
                VpnState.CONNECTED -> EdgeBurstTrigger.CONNECTED
                VpnState.DISCONNECTED -> EdgeBurstTrigger.DISCONNECTED
            }
        }
        if (!previous.isPinging && current.isPinging) return EdgeBurstTrigger.PING
        if (!previous.isRefreshing && current.isRefreshing) return EdgeBurstTrigger.REFRESH
        return null
    }
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.danila.nimbo.ui.components.NetworkEdgeBurstPolicyTest"`

Expected: all `NetworkEdgeBurstPolicyTest` tests pass.

### Task 2: Draw the one-shot edge animation

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/NetworkEdgeBurst.kt`

- [ ] **Step 1: Add a remembered event producer**

```kotlin
@Composable
internal fun rememberNetworkEdgeBurstEvent(snapshot: EdgeBurstSnapshot): EdgeBurstEvent? {
    var previous by remember { mutableStateOf<EdgeBurstSnapshot?>(null) }
    var sequence by remember { mutableLongStateOf(0L) }
    var event by remember { mutableStateOf<EdgeBurstEvent?>(null) }
    LaunchedEffect(snapshot) {
        NetworkEdgeBurstPolicy.trigger(previous, snapshot)?.let {
            sequence += 1L
            event = EdgeBurstEvent(sequence, it)
        }
        previous = snapshot
    }
    return event
}
```

- [ ] **Step 2: Add the pixel-square Canvas overlay**

```kotlin
@Composable
internal fun NetworkEdgeBurstOverlay(event: EdgeBurstEvent?, enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled || event == null) return
    val progress = remember { Animatable(1f) }
    LaunchedEffect(event.id) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        repeat(14) { index ->
            val local = ((progress.value - index * 0.025f) / 0.675f).coerceIn(0f, 1f)
            val y = size.height - local * (size.height + 8.dp.toPx())
            // Draw mirrored 3–5 dp rounded squares close to the left and right edges.
        }
    }
}
```

- [ ] **Step 3: Assign semantic colors**

Use `LocalNebulaColors.current.statusConnected` for `CONNECTED`, `statusError` for `DISCONNECTED`, and the selected accent for `CONNECTING`, `PING`, and `REFRESH`.

### Task 3: Integrate at the app boundary and remove continuous glass telemetry

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Delete: `app/src/main/java/com/danila/nimbo/ui/components/LiveNetworkGlass.kt`
- Delete: `app/src/test/java/com/danila/nimbo/ui/components/LiveNetworkGlassPolicyTest.kt`

- [ ] **Step 1: Observe real event inputs once at `NimboMiniApp` root**

Collect `mainViewModel.isPinging`, read `VpnManager.state`, and derive subscription refresh from `profiles.any { it.isLoading }`. Pass the resulting `EdgeBurstSnapshot` to `rememberNetworkEdgeBurstEvent`.

- [ ] **Step 2: Place one overlay above app pages and bottom controls**

```kotlin
NetworkEdgeBurstOverlay(
    event = edgeBurstEvent,
    enabled = miniMotionEnabled,
    modifier = Modifier.fillMaxSize()
)
```

- [ ] **Step 3: Remove every `LiveNetworkGlassOverlay` integration**

Remove the traffic-glow layer from the bottom bar, full connection button, compact connection button, and legacy FAB. Restore those components to their normal themed surfaces.

### Task 4: Verify behavior and build artifacts

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Document the user-visible behavior**

Add a concise entry explaining green connected, red disconnected, and accent-colored ping/refresh/connecting pixel bursts.

- [ ] **Step 2: Run the full Android verification**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`, all unit tests pass, and architecture-specific plus universal debug APKs are produced.

## Self-review

- Connection start, successful connection, disconnect/failure, ping, and subscription refresh are covered.
- Initial app composition is silent, and sustained loading states cannot loop the effect.
- The animation is one-shot, edge-scoped, uses small squares, and follows theme/status colors.
- Continuous traffic, latency waves, and live glass gradients are removed.
