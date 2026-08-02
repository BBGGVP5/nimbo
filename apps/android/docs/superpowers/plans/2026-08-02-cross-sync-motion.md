# Cross-Platform Sync Motion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Android and Desktop synchronization clear, restrained motion for session lifetime, device discovery, secure connection, transfer and completion.

**Architecture:** Derive every animation from the existing sync state and QR expiry rather than inventing a separate state machine. Android uses Compose animation primitives and a pure countdown policy; Desktop uses the existing one-second clock plus CSS custom properties, with reduced-motion fallbacks on both sides.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit, React 19, TypeScript, CSS animations, Vite.

---

### Task 1: Add a tested session countdown policy on Android

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/screens/SyncMotionPolicy.kt`
- Create: `app/src/test/java/com/danila/nimbo/ui/screens/SyncMotionPolicyTest.kt`

- [ ] **Step 1: Add tests for remaining seconds and normalized progress**

```kotlin
@Test fun `countdown rounds partial seconds up`() {
    assertEquals(2, SyncMotionPolicy.secondsLeft(nowMs = 1_000, expiresAtMs = 2_001))
}

@Test fun `progress is clamped`() {
    assertEquals(0f, SyncMotionPolicy.progress(2_000, 2_000, 1_000), 0f)
    assertEquals(1f, SyncMotionPolicy.progress(500, 2_000, 1_000), 0f)
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.ui.screens.SyncMotionPolicyTest"`
Expected: failure because `SyncMotionPolicy` does not exist.

- [ ] **Step 3: Implement the pure countdown calculations**

```kotlin
internal object SyncMotionPolicy {
    fun secondsLeft(nowMs: Long, expiresAtMs: Long): Int =
        (((expiresAtMs - nowMs).coerceAtLeast(0L) + 999L) / 1_000L).toInt()

    fun progress(nowMs: Long, expiresAtMs: Long, lifetimeMs: Long): Float =
        ((expiresAtMs - nowMs).toFloat() / lifetimeMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
}
```

- [ ] **Step 4: Run the focused test and confirm success**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.ui.screens.SyncMotionPolicyTest"`
Expected: `BUILD SUCCESSFUL`.

### Task 2: Animate Android synchronization states

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/CrossPlatformSyncScreen.kt`

- [ ] **Step 1: Track the scanned session lifetime at 250 ms resolution**

```kotlin
var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
var sessionLifetimeMs by remember { mutableLongStateOf(75_000L) }
LaunchedEffect(qr?.expiresAtMs, stage) {
    while (qr != null && stage != MobileSyncStage.COMPLETED) {
        nowMs = System.currentTimeMillis()
        delay(250L)
    }
}
```

- [ ] **Step 2: Add an animated phone-to-desktop signal bridge**

Use one moving accent pulse and three fading packets between the existing phone and computer icons. Hold all values still when `LocalBackgroundAnimationEnabled` is false.

- [ ] **Step 3: Replace the static progress indicator with a countdown ring**

Show remaining seconds inside the ring and animate its arc toward zero. Change the ring tint to the warning colour below 15 seconds.

- [ ] **Step 4: Animate stage feedback**

Rotate the header sync icon only while a session is active, pulse the verification code, and scale/fade the completed check once when completion is reached.

### Task 3: Animate Desktop QR and transfer feedback

**Files:**
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/pages/CrossPlatformSync.tsx`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/styles.css`

- [ ] **Step 1: Replace the plain expiry sentence with a visual timer**

```tsx
<div className={`cross-sync-timer${secondsLeft <= 15 ? " is-urgent" : ""}`}
  style={{ "--sync-progress": `${Math.min(1, secondsLeft / 75) * 360}deg` } as CSSProperties}>
  <strong>{secondsLeft}</strong><span>сек</span>
</div>
```

- [ ] **Step 2: Add a restrained scanner sweep and QR halo**

Animate a thin accent line across the white QR surface and a slow halo behind it without covering QR modules or reducing scan contrast.

- [ ] **Step 3: Add a device signal bridge and staged card entrance**

Render a compact `ПК → защищённый сигнал → Android` strip above the QR and animate packets only while the session is waiting.

- [ ] **Step 4: Respect reduced-motion**

```css
@media (prefers-reduced-motion: reduce) {
  .cross-sync-page *, .cross-sync-page *::before, .cross-sync-page *::after {
    animation-duration: .001ms !important;
    animation-iteration-count: 1 !important;
  }
}
```

### Task 4: Verify both applications

**Files:**
- Verify: Android and Desktop build outputs only

- [ ] **Step 1: Run Android tests and release compilation**

Run: `.\gradlew.bat testDebugUnitTest assembleRelease`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run Desktop TypeScript and Vite build**

Run: `npm run build` from `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui`
Expected: TypeScript succeeds and Vite emits `dist`.
