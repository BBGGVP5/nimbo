# VPN Network Handover Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the Android VPN working when the device changes its preferred upstream between Wi-Fi and mobile data, without stale Xray sockets, overlapping reconnects, premature health-check failures, or a permanently repeated “attempt 6”.

**Architecture:** Track one best non-VPN upstream instead of an unordered set of every available transport. Bind every protected Xray outbound socket to the selected Android `Network`, publish that same network as the VPN underlying network, and treat a best-network change during either CONNECTING or CONNECTED as a serialized full TUN/core rebuild. Keep network-switch recovery separate from ordinary server retry backoff, and reset the retry budget on a real handoff.

**Tech Stack:** Kotlin, Android `VpnService`, `ConnectivityManager.NetworkCallback`, libXray `DialerController`, coroutines, JUnit 4, Gradle.

---

## Scope and acceptance criteria

- A Wi-Fi → mobile or mobile → Wi-Fi change is detected even while both transports remain available.
- A handoff during CONNECTING cancels the stale attempt and performs one serialized rebuild; it does not wait for the normal 30-second server retry.
- Xray egress sockets are protected from the VPN and explicitly bound to the selected physical `Network` before connecting.
- `VpnService.Builder.setUnderlyingNetworks` receives only the network actually used by Xray.
- Initial health verification waits for the handoff to settle, but ordinary initial connections keep the existing fast path.
- Retry delays remain capped at 30 seconds, while the visible attempt number continues past 6 instead of reporting attempt 6 forever.
- Manual disconnect and screen pause still win over all automatic recovery work.

### Task 1: Make recovery policy distinguish handoff from capped backoff

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/VpnRecoveryPolicy.kt`
- Modify: `app/src/test/java/com/danila/nimbo/vpn/VpnRecoveryPolicyTest.kt`

- [x] **Step 1: Add failing policy tests**

Add tests proving that `NetworkHandoff` from `CONNECTING` emits `CancelRetry` and `RebuildTunnelForNetwork`, clears `connectPending`, and resets a previous retry count. Add a second test proving that repeated `ConnectFailed` events continue counting beyond six while `retryDelayMs` remains capped at 30 seconds.

- [x] **Step 2: Run the focused policy suite**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.vpn.VpnRecoveryPolicyTest"
```

Expected: the new assertions fail because handoff does not reset retries and `retryAttempt` is currently clamped to six.

- [x] **Step 3: Update the pure state machine**

Reset `retryAttempt` in the `NetworkHandoff` branch. Let the state counter increase beyond the backoff exponent cap, while keeping `retryDelayMs()` clamped to the existing maximum delay.

- [x] **Step 4: Re-run the focused policy suite**

Expected: `VpnRecoveryPolicyTest` passes and existing manual-disconnect, screen, sticky restore, and network-loss behavior remains unchanged.

### Task 2: Bind Xray sockets to the selected physical network

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/XrayManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`

- [x] **Step 1: Pass the selected upstream into Xray startup**

Add an optional `Network` argument to `XrayManager.connect()` and pass the service's current best upstream from `connectCandidate()`.

- [x] **Step 2: Bind protected outbound descriptors**

In the Xray dialer controller, call `VpnService.protect(fd)`, duplicate the raw descriptor with `ParcelFileDescriptor.fromFd`, and call `Network.bindSocket(FileDescriptor)` before Xray connects it. Keep the listener controller loopback-only and protected without binding it to an upstream.

- [x] **Step 3: Publish only the actual underlying network**

Replace the `allNetworks` scan in `establishTun()` with `Builder.setUnderlyingNetworks(arrayOf(selectedNetwork))`. If no selected network exists, leave the builder on the Android default behavior.

- [x] **Step 4: Compile the Android sources**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: successful compilation against minSdk 29 / compileSdk 37.

### Task 3: Track the best upstream and serialize handoff rebuilds

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`
- Modify: `app/src/test/java/com/danila/nimbo/vpn/VpnRecoveryPolicyTest.kt`

- [x] **Step 1: Replace the all-networks callback**

On API 31+, register `registerBestMatchingNetworkCallback` for `INTERNET + NOT_VPN`. On API 29–30, use one matching `requestNetwork` callback. Store only the callback's current best network and its ordered capabilities; do not synchronously query capabilities from `onAvailable`.

- [x] **Step 2: Detect handoff while connecting or connected**

After a debounce, compare the best network handle with the previous handle. If it changed and a connection is desired while `isConnecting || isConnected`, reduce `NetworkHandoff`. If the network disappeared, retain the existing waiting-for-network policy.

- [x] **Step 3: Serialize the full rebuild**

Add one tracked handoff job. Cancel the active connection job, wait for it to finish, close Xray and TUN state, wait for the physical network to settle, then start one recovery connection. Cancel this job on manual stop, screen pause, network loss, and service destruction.

- [x] **Step 4: Add a handoff-only health grace**

Record the monotonic time of a real best-network change. Before the HTTPS tunnel check, wait only for the remainder of a short handoff grace period. Do not add that delay to normal initial connections.

- [x] **Step 5: Improve diagnostics**

Log the old and new transport names, the selected network binding result, and failures with the actual Xray error before teardown clears it.

### Task 4: Verify the complete Android application

**Files:**
- Verify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`
- Verify: `app/src/main/java/com/danila/nimbo/vpn/XrayManager.kt`
- Verify: `app/src/main/java/com/danila/nimbo/vpn/VpnRecoveryPolicy.kt`
- Verify: `app/src/test/java/com/danila/nimbo/vpn/VpnRecoveryPolicyTest.kt`

- [x] **Step 1: Run the full unit-test suite**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all Android unit tests pass.

- [x] **Step 2: Assemble the debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and a new debug APK under `app/build/outputs/apk/debug/`.

- [x] **Step 3: Review logs and code paths**

Confirm that a handoff can no longer retain an old network handle, ordinary failures retain exponential retry, manual disconnect cancels every pending job, and no duplicate recovery connection can start.
