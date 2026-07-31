# Technical Review Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Nimbo's connection health checks and bypass ranking actually traverse the selected Xray outbound, bound recovery time, and describe latency metrics honestly.

**Architecture:** Add a loopback-only HTTP inbound to every generated or sanitized Xray client config and route its tag explicitly to the selected proxy outbound or balancer. Extract JSON wiring, health decisions, restricted-network detection, and attempt budgets into small pure Kotlin policies so they can be unit-tested without Android. Keep `MyVpnService` as the orchestrator and publish `CONNECTED` only after an end-to-end request through the loopback proxy succeeds.

**Tech Stack:** Kotlin, Android `VpnService`, libXray/Xray JSON, coroutines, `HttpURLConnection`, JUnit 4, `org.json`.

---

### Task 1: Loopback probe inbound and deterministic Xray routing

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/vpn/LocalProxyConfig.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/XrayManager.kt:277-504`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/XrayManager.kt:813-847`
- Test: `app/src/test/java/com/danila/nimbo/vpn/LocalProxyConfigTest.kt`

- [x] **Step 1: Write failing JSON-policy tests**

```kotlin
@Test fun `inbound is loopback-only and unique`() {
    val inbounds = JSONArray().put(JSONObject().put("tag", LocalProxyConfig.INBOUND_TAG))
    LocalProxyConfig.ensureInbound(inbounds)
    assertEquals(1, inbounds.objects().count { it.optString("tag") == LocalProxyConfig.INBOUND_TAG })
    assertEquals("127.0.0.1", inbounds.getJSONObject(0).getString("listen"))
}

@Test fun `probe rule wins over imported catch-all`() {
    val rules = JSONArray().put(JSONObject().put("type", "field").put("outboundTag", "direct"))
    val result = LocalProxyConfig.prependRoute(rules, outboundTag = "proxy", balancerTag = null)
    assertEquals(LocalProxyConfig.INBOUND_TAG, result.getJSONObject(0).getJSONArray("inboundTag").getString(0))
    assertEquals("proxy", result.getJSONObject(0).getString("outboundTag"))
}
```

- [x] **Step 2: Run the focused test and confirm it fails**

Run: `./gradlew.bat testDebugUnitTest --tests com.danila.nimbo.vpn.LocalProxyConfigTest`

Expected: compilation failure because `LocalProxyConfig` does not exist.

- [x] **Step 3: Implement the loopback config component**

```kotlin
internal object LocalProxyConfig {
    const val HOST = "127.0.0.1"
    const val PORT = 2080
    const val INBOUND_TAG = "nimbo-health-in"

    fun ensureInbound(inbounds: JSONArray) {
        val retained = (0 until inbounds.length())
            .mapNotNull(inbounds::optJSONObject)
            .filterNot { it.optString("tag") == INBOUND_TAG || it.optInt("port", -1) == PORT }
        while (inbounds.length() > 0) inbounds.remove(inbounds.length() - 1)
        retained.forEach(inbounds::put)
        inbounds.put(
            JSONObject().put("tag", INBOUND_TAG).put("listen", HOST).put("port", PORT)
                .put("protocol", "http").put("settings", JSONObject().put("allowTransparent", false))
        )
    }

    fun prependRoute(rules: JSONArray, outboundTag: String?, balancerTag: String?): JSONArray {
        val result = JSONArray()
        val route = JSONObject().put("type", "field").put("inboundTag", JSONArray().put(INBOUND_TAG))
        when {
            !outboundTag.isNullOrBlank() -> result.put(route.put("outboundTag", outboundTag))
            !balancerTag.isNullOrBlank() -> result.put(route.put("balancerTag", balancerTag))
        }
        for (index in 0 until rules.length()) result.put(rules.optJSONObject(index))
        return result
    }

    fun firstProxyOutboundTag(outbounds: JSONArray): String? =
        (0 until outbounds.length()).mapNotNull(outbounds::optJSONObject).firstNotNullOfOrNull { outbound ->
            val protocol = outbound.optString("protocol").lowercase()
            outbound.optString("tag").takeIf { it.isNotBlank() && protocol !in setOf("freedom", "blackhole", "dns", "loopback") }
        }
}
```

The HTTP inbound must listen only on `127.0.0.1`; it must not expose the proxy on LAN. Imported rules are preserved after the tagged probe rule.

- [x] **Step 4: Wire both Xray config paths**

In `sanitizeOverrideXrayConfig`, call `ensureInbound`, resolve the explicit remote outbound or balancer, fall back to `firstProxyOutboundTag`, and prepend the probe route before imported rules. In `generateXrayConfig`, add the HTTP inbound next to `tun-in` and prepend a route from `nimbo-health-in` to `proxy`.

- [x] **Step 5: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests com.danila.nimbo.vpn.LocalProxyConfigTest`

Expected: all `LocalProxyConfigTest` tests pass.

### Task 2: Honest direct and through-VPN latency modes

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/PingManager.kt:17-56`
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt:834-849`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt:10089-10248`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/PingSettingsScreen.kt:88-238`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NetworkToolsScreens.kt:238-260`
- Test: `app/src/test/java/com/danila/nimbo/network/PingManagerTest.kt`

- [x] **Step 1: Add a failing transport-selection test**

```kotlin
@Test fun `proxy mode converts direct-only protocols to HTTP HEAD`() {
    assertEquals(PingProtocol.HTTP_HEAD, PingManager.effectiveProtocol(PingConfig(PingProtocol.TCP, useProxy = true)))
    assertEquals(PingProtocol.HTTP_HEAD, PingManager.effectiveProtocol(PingConfig(PingProtocol.ICMP, useProxy = true)))
    assertEquals(PingProtocol.TCP, PingManager.effectiveProtocol(PingConfig(PingProtocol.TCP, useProxy = false)))
}
```

- [x] **Step 2: Run the focused test and confirm it fails**

Run: `./gradlew.bat testDebugUnitTest --tests com.danila.nimbo.network.PingManagerTest`

Expected: failure because `effectiveProtocol` is missing.

- [x] **Step 3: Implement proxy-aware transport selection**

When `useProxy` is true, TCP and ICMP cannot target an HTTP proxy directly, so execute `HTTP_HEAD` against the configured health URL. Preserve the selected protocol for direct mode and replace all hard-coded `2080` defaults with `LocalProxyConfig.PORT`.

- [x] **Step 4: Make the UI terminology explicit**

Use `TCP до ноды`/`Direct TCP to node` for direct socket measurements and `Через VPN`/`Through VPN` for end-to-end HTTP measurements. Add the missing through-VPN switch to `NimboPingSettingsScreen`; explain that it works only with an active tunnel and measures the configured HTTP endpoint rather than the node socket.

- [x] **Step 5: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests com.danila.nimbo.network.PingManagerTest`

Expected: all ping policy tests pass.

### Task 3: End-to-end tunnel confirmation and real bypass probes

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/vpn/TunnelHealthPolicy.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt:1436-1489`
- Test: `app/src/test/java/com/danila/nimbo/vpn/TunnelHealthPolicyTest.kt`

- [x] **Step 1: Write failing health-decision tests**

```kotlin
@Test fun `one independent endpoint is enough to confirm the tunnel`() {
    assertTrue(TunnelHealthPolicy.isHealthy(listOf(-1, 123)))
    assertFalse(TunnelHealthPolicy.isHealthy(listOf(-1, -1)))
}

@Test fun `service score only counts successful through-proxy probes`() {
    assertEquals(2, TunnelHealthPolicy.successCount(listOf(40, -1, 90)))
}
```

- [x] **Step 2: Run the focused test and confirm it fails**

Run: `./gradlew.bat testDebugUnitTest --tests com.danila.nimbo.vpn.TunnelHealthPolicyTest`

Expected: compilation failure because `TunnelHealthPolicy` does not exist.

- [x] **Step 3: Implement bounded multi-endpoint health policy**

Define two independent HTTPS connectivity endpoints, a 2.5-second per-request timeout, and the rule “at least one successful 2xx/3xx response through the loopback proxy”. The endpoints are attempted concurrently so a dead endpoint does not add serial delay.

- [x] **Step 4: Require end-to-end success before `CONNECTED`**

After the existing core and physical-network checks, probe the endpoints with `useProxy = true` and `proxyPort = LocalProxyConfig.PORT`. Disconnect and reject the candidate when all endpoints fail. Probe-mode candidate ranking may skip this duplicate health stage because the full service suite immediately follows it.

- [x] **Step 5: Route all bypass service probes through the candidate**

Change every call in `runBypassServiceProbeSuite` to `useProxy = true`, run targets concurrently, and keep `server` only as report identity. Remove both dead `bypassBlockedByDomain = false` branches.

- [x] **Step 6: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests com.danila.nimbo.vpn.TunnelHealthPolicyTest`

Expected: all tunnel health tests pass.

### Task 4: Restricted-network signal and bounded recovery

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/vpn/ConnectionAttemptPolicy.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt:88-96`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt:413-472`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt:1334-1434`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt:1491-1598`
- Test: `app/src/test/java/com/danila/nimbo/vpn/ConnectionAttemptPolicyTest.kt`

- [x] **Step 1: Write failing policy tests**

```kotlin
@Test fun `attempt timeout never exceeds remaining cycle budget`() {
    assertEquals(4_000L, ConnectionAttemptPolicy.attemptTimeoutMs(4_000L))
    assertEquals(15_000L, ConnectionAttemptPolicy.attemptTimeoutMs(60_000L))
}

@Test fun `failed alternatives cool down but explicit selection is still allowed`() {
    assertTrue(ConnectionAttemptPolicy.isCoolingDown(10_000L, 20_000L, explicitSelection = false))
    assertFalse(ConnectionAttemptPolicy.isCoolingDown(10_000L, 20_000L, explicitSelection = true))
}

@Test fun `restriction requires working internet and blocked target services`() {
    assertTrue(ConnectionAttemptPolicy.isRestricted(listOf(true, false), listOf(false, false)))
    assertFalse(ConnectionAttemptPolicy.isRestricted(listOf(false, false), listOf(false, false)))
}
```

- [x] **Step 2: Run the focused test and confirm it fails**

Run: `./gradlew.bat testDebugUnitTest --tests com.danila.nimbo.vpn.ConnectionAttemptPolicyTest`

Expected: compilation failure because `ConnectionAttemptPolicy` does not exist.

- [x] **Step 3: Implement explicit budgets and cooldown**

Use a 60-second total connection-cycle budget, a 15-second per-attempt cap, two attempts for a normal candidate, one for probe mode, and a two-minute cooldown for automatically selected failed candidates. An explicitly selected server bypasses cooldown. Calculate deadlines with `SystemClock.elapsedRealtime()`.

- [x] **Step 4: Apply the budget to candidate iteration**

Pass one cycle deadline into ranking and `connectCandidate`; stop retries when the remaining budget is zero. Record failures in a service-owned cooldown map, clear a candidate on success, reserve time for the final selected connection, and log `Переходим на резервный сервер: …` before alternatives.

- [x] **Step 5: Replace the hard-coded restricted-network result**

Before VPN start, concurrently test independent baseline endpoints and restricted target services without a proxy. Report “restricted” only when at least one baseline succeeds and all target-service checks fail, avoiding a false restriction result during total internet loss.

- [x] **Step 6: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests com.danila.nimbo.vpn.ConnectionAttemptPolicyTest`

Expected: all attempt-policy tests pass.

### Task 5: Reproducible validation documentation and full regression check

**Files:**
- Create: `docs/network-validation.md`
- Modify: `README_QUICKSTART.md`

- [x] **Step 1: Document device-level verification**

Record the controlled endpoint procedure that distinguishes the phone IP from each VPN node IP, confirms the loopback listener, exercises Wi-Fi/LTE transitions and node failure, and captures connect-to-first-success latency plus median/p95/p99/jitter/loss over at least 30 randomized runs.

- [x] **Step 2: Replace the obsolete quick-start architecture text**

Describe the actual libXray + Android TUN architecture, remove instructions for a missing `libbox.aar` and missing `XRAY_INTEGRATION.md`, and link to `docs/network-validation.md`.

- [x] **Step 3: Run all unit tests**

Run: `./gradlew.bat testDebugUnitTest`

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [x] **Step 4: Run lint and build a debug APK**

Run: `./gradlew.bat lintDebug assembleDebug`

Expected: `BUILD SUCCESSFUL`; the debug APK is produced under `app/build/outputs/apk/debug/`.

- [x] **Step 5: Record the licensing boundary**

Do not invent a license. Verify whether the current checkout contains `LICENSE`, `Cargo.toml`, or an open-source claim. If those files are absent, report that the reviewed monorepo's license inconsistency cannot be resolved from this Android-only snapshot and requires the owner's explicit license choice.

The workspace has no `.git` metadata, so commit checkpoints from the generic workflow cannot be executed in this snapshot; each green focused test is the corresponding rollback-safe checkpoint.
