# Radial Button Particle Scatter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace converging button particles with a slow, immediate radial scatter in every direction.

**Architecture:** Source-aware particles no longer have shared screen-edge destinations. Pure geometry advances each particle from its own perimeter point along its radial direction with a small signed tangential bend; only source-less background events retain the vertical edge fallback.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, JUnit 4, Gradle.

---

### Task 1: Specify radial motion

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/NetworkEdgeBurst.kt`
- Modify: `app/src/test/java/com/danila/nimbo/ui/components/NetworkEdgeBurstPolicyTest.kt`

- [ ] **Step 1: Add failing geometry tests**

```kotlin
@Test fun radial_particle_moves_away_from_source() {
    val point = NetworkEdgeBurstGeometry.scatterPoint(source, 0f, 100f, 0.5f, 0f)
    assertTrue(point.x > NetworkEdgeBurstGeometry.startPoint(source, 0f).x)
}

@Test fun opposite_particles_diverge() {
    val right = NetworkEdgeBurstGeometry.scatterPoint(source, 0f, 100f, 0.7f, 0f)
    val left = NetworkEdgeBurstGeometry.scatterPoint(source, PI.toFloat(), 100f, 0.7f, 0f)
    assertTrue(right.x > source.center.x && left.x < source.center.x)
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.danila.nimbo.ui.components.NetworkEdgeBurstPolicyTest"`

Expected: `scatterPoint` is unresolved.

- [ ] **Step 3: Implement radial geometry**

```kotlin
fun scatterPoint(source: EdgeBurstSource, angle: Float, distance: Float, progress: Float, bend: Float): Offset {
    val start = startPoint(source, angle)
    val radial = Offset(cos(angle), sin(angle))
    val tangent = Offset(-radial.y, radial.x)
    return start + radial * distance * progress + tangent * sin(progress * PI).toFloat() * bend
}
```

- [ ] **Step 4: Run focused tests and verify success**

Run: `./gradlew testDebugUnitTest --tests "com.danila.nimbo.ui.components.NetworkEdgeBurstPolicyTest"`

Expected: all tests pass.

### Task 2: Replace converging Canvas paths

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/NetworkEdgeBurst.kt`

- [ ] **Step 1: Remove shared edge targets and quadratic curves**

For source-aware events, compute only the particle angle, individual distance, signed bend, and radial point. Delete the `quadraticPoint` helper after its last use.

- [ ] **Step 2: Start particles together**

Use at most five tiny 0–2.4% stagger groups rather than index-based 0–46% staggering, so the full perimeter emits immediately without particles first forming a cluster.

- [ ] **Step 3: Preserve slow motion and trails**

Keep the 1.9-second duration and three particle trails. Fade every radial particle continuously to zero near the end instead of removing a gathered stream at the top edge.

### Task 3: Verify and document

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Update the changelog**

Document immediate all-direction scatter without convergence.

- [ ] **Step 2: Run full verification**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: all tests pass and all debug APK variants rebuild.

## Self-review

- Source-aware particles have no common destination.
- All directions emit within the first few animation frames.
- Motion remains slow and trails remain particulate.
- Background events without a button still use the existing side-edge fallback.
