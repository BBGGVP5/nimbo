# Complete Routing Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure every routing profile is translated correctly, domain rules remain usable, and any routing change takes effect in an active Android VPN tunnel.

**Architecture:** Keep `RoutingProfileRules` as the sole profile-to-Xray translator and normalize misplaced IP selectors before it emits field rules. Add a small routing runtime policy that determines whether sniffing is compulsory and whether a running tunnel must rebuild; `MyVpnService` exposes an explicit reload action, while routing screens invoke it only after saved configuration changes. The same runtime policy is used for generated and imported Xray configurations.

**Tech Stack:** Kotlin, Android `VpnService`, Jetpack Compose, Xray JSON, JUnit 4.

---

### Task 1: Normalize all domain and IP selectors before emitting Xray rules

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/RoutingProfileRules.kt`
- Modify: `app/src/main/java/com/danila/nimbo/model/BuiltinRoutingProfiles.kt`
- Modify: `app/src/test/java/com/danila/nimbo/vpn/RoutingProfileRulesTest.kt`
- Modify: `app/src/test/java/com/danila/nimbo/model/BuiltinRoutingProfilesTest.kt`

- [ ] **Step 1: Add regression tests for a misplaced GeoIP selector and built-in profiles**

```kotlin
@Test
fun `GeoIP selector mistakenly stored as a site is emitted as an IP rule`() {
    val rules = RoutingProfileRules.build(
        RoutingProfile(bypassLocalIp = "false", directSites = listOf("domain:ru", "geoip:ru")),
        includeFallback = false
    )

    assertEquals("domain:ru", rules.getJSONObject(0).getJSONArray("domain").getString(0))
    assertEquals("geoip:ru", rules.getJSONObject(1).getJSONArray("ip").getString(0))
    assertFalse(rules.getJSONObject(1).has("domain"))
}

@Test
fun `built in site lists do not contain GeoIP selectors`() {
    assertTrue(BuiltinRoutingProfiles.defaults().all { profile ->
        profile.directSites.orEmpty().none { it.startsWith("geoip:", ignoreCase = true) }
    })
}
```

- [ ] **Step 2: Run the focused routing tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.danila.nimbo.vpn.RoutingProfileRulesTest --tests com.danila.nimbo.model.BuiltinRoutingProfilesTest`

Expected: FAIL because `geoip:ru` remains in a `domain` array and the RoscomVPN preset contains it in `directSites`.

- [ ] **Step 3: Move `geoip:*` selectors to the IP rule list during translation**

```kotlin
val rawDomains = domains.orEmpty().map(String::trim).filter(String::isNotBlank)
val normalizedDomains = rawDomains.filterNot(::isIpSelector)
val normalizedIps = (ips.orEmpty() + rawDomains.filter(::isIpSelector))
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

private fun isIpSelector(value: String): Boolean =
    value.startsWith("geoip:", ignoreCase = true)
```

Keep domain and IP output as separate field rules, then remove the invalid `"geoip:ru"` value from `BuiltinRoutingProfiles.ROSCOMVPN.directSites`; it is already represented by `directIp`.

- [ ] **Step 4: Run the focused routing tests again**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.danila.nimbo.vpn.RoutingProfileRulesTest --tests com.danila.nimbo.model.BuiltinRoutingProfilesTest`

Expected: PASS.

### Task 2: Require domain sniffing whenever site routing is enabled

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/vpn/RoutingRuntimePolicy.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/XrayManager.kt`
- Create: `app/src/test/java/com/danila/nimbo/vpn/RoutingRuntimePolicyTest.kt`

- [ ] **Step 1: Add policy tests for routing and VPN states**

```kotlin
@Test fun routingForcesSniffingEvenWhenTheAdvancedToggleIsOff() {
    assertTrue(RoutingRuntimePolicy.shouldEnableSniffing(false, true))
    assertFalse(RoutingRuntimePolicy.shouldEnableSniffing(false, false))
}

@Test fun activeTunnelStatesRequireAConfigReload() {
    assertFalse(RoutingRuntimePolicy.shouldReloadTunnel(VpnState.DISCONNECTED))
    assertTrue(RoutingRuntimePolicy.shouldReloadTunnel(VpnState.CONNECTING))
    assertTrue(RoutingRuntimePolicy.shouldReloadTunnel(VpnState.CONNECTED))
}
```

- [ ] **Step 2: Run the policy test and verify it fails before the policy exists**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.danila.nimbo.vpn.RoutingRuntimePolicyTest`

Expected: FAIL with unresolved reference `RoutingRuntimePolicy`.

- [ ] **Step 3: Implement the shared pure policy**

```kotlin
object RoutingRuntimePolicy {
    fun shouldEnableSniffing(userEnabled: Boolean, routingEnabled: Boolean): Boolean =
        userEnabled || routingEnabled

    fun shouldReloadTunnel(state: VpnState): Boolean = state != VpnState.DISCONNECTED
}
```

- [ ] **Step 4: Use the policy in both Xray inbound paths**

Replace the `prefs.trafficSniffingEnabled` checks in `sanitizeOverrideXrayConfig` and `buildTunInbound` with:

```kotlin
if (RoutingRuntimePolicy.shouldEnableSniffing(prefs.trafficSniffingEnabled, prefs.isRoutingEnabled)) {
    inbound.put("sniffing", buildSniffingConfig())
}
```

For the generated TUN inbound, put the same `buildSniffingConfig()` when the policy returns true. This prevents a user-facing routing profile from silently losing HTTP/TLS/QUIC domain matching.

- [ ] **Step 5: Run policy and routing unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.danila.nimbo.vpn.RoutingRuntimePolicyTest --tests com.danila.nimbo.vpn.RoutingProfileRulesTest`

Expected: PASS.

### Task 3: Rebuild an active tunnel after a routing configuration change

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`
- Create: `app/src/main/java/com/danila/nimbo/vpn/RoutingConfigurationApplier.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/RoutingScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [ ] **Step 1: Add an explicit service action for configuration reloads**

```kotlin
const val ACTION_RELOAD_CONFIGURATION = "com.danila.nimbo.vpn.RELOAD_CONFIGURATION"

fun requestConfigurationReload(context: Context) {
    val intent = Intent(context, MyVpnService::class.java).setAction(ACTION_RELOAD_CONFIGURATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
    else context.startService(intent)
}
```

Handle the action in `onStartCommand`: if a current server exists and the service is connected or connecting, log a user-facing routing reload message and call `switchServer(currentServer)`. If no active tunnel exists, return without starting a connection; the saved profile will be used by the next normal connect.

- [ ] **Step 2: Add the UI-facing applier**

```kotlin
object RoutingConfigurationApplier {
    fun applyToActiveTunnel(context: Context): Boolean {
        if (!RoutingRuntimePolicy.shouldReloadTunnel(VpnManager.state.value)) return false
        MyVpnService.requestConfigurationReload(context)
        return true
    }
}
```

- [ ] **Step 3: Invoke the applier after every persisted routing change**

Call `RoutingConfigurationApplier.applyToActiveTunnel(context)` after:

```kotlin
preferencesManager.activateBuiltinRoutingProfile(preset.id)
preferencesManager.saveImportedRoutingProfile(newProfile)
preferencesManager.saveBuiltinRoutingProfile(edited)
preferencesManager.resetBuiltinRoutingProfile(profile.id.orEmpty())
preferencesManager.deleteBuiltinRoutingProfile(profile.id.orEmpty())
preferencesManager.isRoutingEnabled = true
preferencesManager.isRoutingEnabled = false
```

In `RoutingScreen`, show a localized toast only when the return value is true: `"Применяем правила: VPN переподключается"` / `"Applying rules: VPN is reconnecting"`.

- [ ] **Step 4: Build the debug APK**

Run: `./gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

### Task 4: Verify all routing modes end to end

**Files:**
- Test: `app/src/test/java/com/danila/nimbo/vpn/RoutingProfileRulesTest.kt`
- Test: `app/src/test/java/com/danila/nimbo/vpn/RoutingRuntimePolicyTest.kt`

- [ ] **Step 1: Run all debug unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Validate on an Android device after installing the debug APK**

1. Connect VPN, switch among Global, Bypass LAN, China, Russia, and RoscomVPN; each switch must show a short reconnect and must apply without manually toggling VPN.
2. In Russia, visit `2ip.ru`, `ya.ru`, and a non-Russian site. The first two must show the ISP address; the non-Russian site must show the VPN address.
3. In RoscomVPN, verify a `.ru` destination is direct and a configured blocked service is routed through VPN.
4. Turn off the advanced traffic-sniffing toggle, leave routing enabled, reconnect, and repeat the Russia check; domain rules must still work.
5. Disable routing in the in-app mode selector while connected; after its automatic reconnect, `2ip.ru` must show the VPN address under Global mode.

