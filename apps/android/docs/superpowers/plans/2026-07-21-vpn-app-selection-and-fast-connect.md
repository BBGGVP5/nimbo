# VPN App Selection, Fast Connect, and Home Scroll Implementation Plan

> **For Codex:** Execute this plan inline, one task at a time, using test-driven development for policy changes and running the full Android verification suite before handoff.

**Goal:** Prevent VPN-only mode from starting with no usable applications, show the error in Nimbo's custom in-app notification, remove avoidable connection latency, and allow the home screen to scroll slightly farther down.

**Architecture:** Put application-selection validation in the pure `VpnTunPolicy` layer so both `MainActivity` and `MyVpnService` use identical rules. Reject invalid UI requests before Android starts the foreground service; retain a defensive service-side rejection without a system Toast. Gate expensive restricted-network probes behind auto-rotation, shorten post-start settling, and make tunnel health verification return after the first successful endpoint while preserving a fallback endpoint.

**Tech Stack:** Kotlin, Android `VpnService`, Jetpack Compose, coroutines, JUnit 4, Gradle Android plugin.

---

### Task 1: Share and test VPN-only application validation

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/VpnTunPolicy.kt`
- Modify: `app/src/test/java/com/danila/nimbo/vpn/VpnTunPolicyTest.kt`

1. Add failing tests for an empty VPN-only selection, a selection containing only the app itself or missing packages, and a valid installed package.
2. Run `./gradlew.bat testDebugUnitTest --tests com.danila.nimbo.vpn.VpnTunPolicyTest` and confirm the new tests fail before implementation.
3. Add `hasUsableVpnOnlySelection(proxyByApp, ownPackage, selectedPackages, isInstalled)` to `VpnTunPolicy`, trimming entries, excluding the owner package, and accepting non-VPN-only modes without a selection.
4. Re-run the focused tests and confirm they pass.

### Task 2: Reject invalid connections before foreground-service startup

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/MainActivity.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`

1. Pass `MainViewModel` into the activity connection callback.
2. Before storing the pending server or requesting Android VPN permission, validate the selected application packages with the shared policy.
3. On validation failure, leave the current VPN state unchanged and invoke `MainViewModel.showTopNotification` with `NotificationType.ERROR` and a localized message.
4. Replace the service's private duplicate validator with the shared policy and remove the Android `Toast`; stop an unexpected invalid service request cleanly and log it.
5. Search the modified connection path to ensure no system Toast remains.

### Task 3: Reduce avoidable connection latency without weakening tunnel checks

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/ConnectionAttemptPolicy.kt`
- Modify: `app/src/test/java/com/danila/nimbo/vpn/ConnectionAttemptPolicyTest.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/TunnelHealthPolicy.kt`
- Modify: `app/src/test/java/com/danila/nimbo/vpn/TunnelHealthPolicyTest.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`

1. Add failing policy tests proving restricted-network detection is skipped unless auto-rotation can use bypass candidates, and proving the health timeout stays within the intended fast-connect budget.
2. Run the two focused test classes and confirm the new assertions fail.
3. Add a pure `shouldDetectRestrictedNetwork` decision to `ConnectionAttemptPolicy`; call the expensive direct reachability suite only when it can change candidate selection.
4. Reduce post-connect stabilization from 650 ms to 250 ms and health request timeout from 2500 ms to 1500 ms.
5. For connection health, probe endpoints in order and return immediately after the first success; retain the existing concurrent all-endpoint probe for candidate ranking statistics.
6. Re-run focused policy tests and confirm they pass.

### Task 4: Increase home-screen bottom scroll space

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

1. Increase the home column's bottom content padding from 112 dp to 140 dp so the last widgets can scroll slightly farther above the bottom navigation.
2. Verify only the main home column was changed.

### Task 5: Full verification and handoff

**Files:**
- Verify: all files above

1. Run `./gradlew.bat testDebugUnitTest`.
2. Run `./gradlew.bat lintDebug` and check that it reports zero errors.
3. Run `./gradlew.bat assembleDebug` and confirm the APK is produced.
4. Report the root cause, the user-visible changes, test/lint/build results, and the absolute APK path.
