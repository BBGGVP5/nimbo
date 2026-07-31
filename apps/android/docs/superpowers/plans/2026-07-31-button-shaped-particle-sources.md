# Button-Shaped Particle Sources Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make particles emerge from underneath each initiating button’s perimeter, with a larger circular burst for the main VPN button and compact rounded-square bursts for ping/refresh controls.

**Architecture:** Events carry a geometric `EdgeBurstSource` instead of a center point alone. The Canvas computes a ray intersection with either a circle or rounded control bounds so every particle starts outside the visible button; a source density multiplier increases particle count for the large VPN control while preserving the same status/result source through the controller.

**Tech Stack:** Kotlin, Jetpack Compose layout coordinates and Canvas, JUnit 4, Gradle.

---

### Task 1: Model button geometry

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/NetworkEdgeBurst.kt`
- Modify: `app/src/test/java/com/danila/nimbo/ui/components/NetworkEdgeBurstPolicyTest.kt`

- [ ] **Step 1: Add failing source tests**

```kotlin
@Test fun circular_source_starts_on_button_perimeter() {
    val source = EdgeBurstSource(Offset(100f, 200f), 80f, 80f, EdgeBurstSourceShape.CIRCLE, 1.5f)
    assertEquals(Offset(180f, 200f), NetworkEdgeBurstGeometry.startPoint(source, 0f))
}

@Test fun large_source_requests_more_particles() {
    assertTrue(NetworkEdgeBurstGeometry.particleCount(1.5f) > NetworkEdgeBurstVisualSpec.particleCount)
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.danila.nimbo.ui.components.NetworkEdgeBurstPolicyTest"`

Expected: unresolved `EdgeBurstSource` and `NetworkEdgeBurstGeometry`.

- [ ] **Step 3: Implement source and geometry**

```kotlin
internal enum class EdgeBurstSourceShape { CIRCLE, ROUNDED_RECT }
internal data class EdgeBurstSource(
    val center: Offset,
    val halfWidth: Float,
    val halfHeight: Float,
    val shape: EdgeBurstSourceShape,
    val densityMultiplier: Float = 1f
)
```

For circles, offset by `min(halfWidth, halfHeight)` along the particle angle. For rounded rectangles, intersect the angle ray with the half-width/half-height bounds and move two pixels farther outward.

### Task 2: Carry sources through events

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/NetworkEdgeBurst.kt`

- [ ] **Step 1: Replace event origin with source geometry**

```kotlin
internal data class EdgeBurstEvent(val id: Long, val trigger: EdgeBurstTrigger, val source: EdgeBurstSource?)
```

- [ ] **Step 2: Preserve the VPN source**

Store `connectionSource` on direct `CONNECTING`, reuse it for `CONNECTED` and observed `DISCONNECTED`, and keep immediate-echo suppression unchanged.

- [ ] **Step 3: Start Canvas curves at the source perimeter**

Use `NetworkEdgeBurstGeometry.startPoint(source, angle)` as each quadratic path’s start and multiply the base 28 particles by `source.densityMultiplier`.

### Task 3: Report real button bounds

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [ ] **Step 1: Emit rounded-square ping/refresh sources**

Build `EdgeBurstSource` from the measured width/height of `MiniSquareIconButton`, use `ROUNDED_RECT`, and density `1f`.

- [ ] **Step 2: Emit large circular VPN source**

Build a `CIRCLE` source from `WindowsConnectionButton` bounds with density `1.5f`, producing 42 particles.

- [ ] **Step 3: Emit compact VPN source**

Build a `ROUNDED_RECT` source from compact button bounds with density `1.2f`.

- [ ] **Step 4: Verify**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: all tests pass and all debug APK variants rebuild successfully.

## Self-review

- No source-aware particle begins inside or above the button fill.
- Main circular VPN control receives the largest burst.
- Ping/refresh follow their actual rounded-square bounds.
- Green/red completion events reuse the same initiating button geometry.
