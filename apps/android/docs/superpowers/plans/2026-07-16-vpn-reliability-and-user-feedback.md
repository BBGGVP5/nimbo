# VPN Reliability and User Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make VPN handoffs and per-app routing reliable, import VLESS/XHTTP Xray configs, make HTTP ping truthful and visible, and present every user-facing notification and diagnostic in the selected language.

**Architecture:** Keep Android's VPN allow-list as the authority for per-app routing, but stop publishing VPN DNS in VPN-only mode so unselected applications retain their underlying-network resolver. Model network handoff as a deliberate recovery-policy event, parse client Xray VLESS outbounds into the existing `LinkParser` format, and put presentation-only concerns (log source labels, localized strings, visual limits) at the UI boundary. Each part is independently testable and may be released separately.

**Tech Stack:** Kotlin, Android `VpnService`, Xray JSON, coroutines/Flow, Jetpack Compose Material 3, JUnit 4, Gradle.

---

## Scope and acceptance criteria

- Wi-Fi ↔ mobile-data handoffs rebuild an intentionally connected tunnel once, even if retry-after-failure is disabled; a manual disconnect never reconnects.
- In “VPN for selected apps”, selected applications use the tunnel while unselected applications resolve DNS and connect through the underlying network.
- A client Xray JSON containing a VLESS/XHTTP outbound creates a selectable server, preserves TLS SNI, fingerprint, transport, path and user UUID, and connects through the existing Xray generator.
- HTTP ping probes the configured HTTP URL (or an explicit `{host}` / `{port}` template), releases HTTP resources, and records a failed attempt as unavailable instead of silently retaining a stale latency.
- Top banners, Android update notifications, VPN-service events and log labels follow the selected RU/EN language. The log list displays “VPN connection”, “Subscription”, “Diagnostics”, etc., never implementation class names such as `MyVpnService`.
- Subscription refresh reports an update only when user-visible subscription content changed. App-update notifications are never generated for the installed version and are not reissued for the same newer release.
- Light-theme switches have a visible off-state outline; the global corner multiplier is capped at 2.0× so it cannot clip compact card and navigation content.

### Task 1: Import VLESS/XHTTP client Xray JSON as a normal selectable server

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/SubscriptionManager.kt:1042-1075`
- Modify: `app/src/test/java/com/danila/nimbo/network/SubscriptionManagerXrayBase64Test.kt`

- [ ] **Step 1: Write the failing Xray VLESS/XHTTP parser test**

Add this test to `SubscriptionManagerXrayBase64Test.kt`. The fixture intentionally uses example-only host and UUID values; do not add a real subscription endpoint or client credential to source control.

```kotlin
@Test
fun clientXrayConfig_extractsVlessXhttpOutbound() {
    val json = """
        {
          "outbounds": [{
            "tag": "Example XHTTP",
            "protocol": "vless",
            "settings": { "vnext": [{
              "address": "edge.example",
              "port": 443,
              "users": [{ "id": "11111111-2222-3333-4444-555555555555", "encryption": "none" }]
            }] },
            "streamSettings": {
              "network": "xhttp",
              "security": "tls",
              "tlsSettings": { "serverName": "front.example", "fingerprint": "edge" },
              "xhttpSettings": { "mode": "auto", "path": "/direct/" }
            }
          }, { "tag": "direct", "protocol": "freedom" }]
        }
    """.trimIndent()

    val link = SubscriptionManager.parseServerLinksFromClientJsonConfig(json).single()
    val server = LinkParser.parse(link)

    assertEquals("Example XHTTP", server.name)
    assertEquals("edge.example", server.host)
    assertEquals(443, server.port)
    assertEquals("vless", server.protocol)
    assertEquals("xhttp", server.network)
    assertEquals("/direct/", server.path)
    assertEquals("front.example", server.sni)
    assertEquals("edge", server.fingerprint)
    assertEquals("11111111-2222-3333-4444-555555555555", server.uuid)
}
```

- [ ] **Step 2: Run the focused test and verify the current failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.SubscriptionManagerXrayBase64Test"`

Expected: FAIL because `parseServerLinksFromClientJsonConfig` only calls the Hysteria extractors and returns an empty list for the VLESS outbound.

- [ ] **Step 3: Convert an Xray VLESS outbound to a standard VLESS link**

In `SubscriptionManager.kt`, try the VLESS extractor before the existing Hysteria extractors, then add the helper below beside `xrayHysteriaLinkFromOutbound`. It must skip direct/block/DNS outbounds, use the first `vnext` entry and its first user, and retain the established link parser as the single source of `Server` construction.

```kotlin
private fun xrayVlessLinkFromOutbound(outbound: JSONObject): String? {
    if (!outbound.cleanString("protocol")?.equals("vless", ignoreCase = true)!!) return null
    val node = outbound.optJSONObject("settings")
        ?.optJSONArray("vnext")
        ?.optJSONObject(0)
        ?: return null
    val user = node.optJSONArray("users")?.optJSONObject(0) ?: return null
    val host = node.cleanString("address") ?: return null
    val port = node.cleanInt("port") ?: return null
    val id = user.cleanString("id") ?: return null
    val stream = outbound.optJSONObject("streamSettings") ?: JSONObject()
    val tls = stream.optJSONObject("tlsSettings")
    val xhttp = stream.optJSONObject("xhttpSettings")
    val params = linkedMapOf(
        "encryption" to (user.cleanString("encryption") ?: "none"),
        "type" to (stream.cleanString("network") ?: "tcp"),
        "security" to (stream.cleanString("security") ?: "none"),
        "sni" to tls?.cleanString("serverName"),
        "fp" to tls?.cleanString("fingerprint"),
        "alpn" to tls?.cleanStringArray("alpn"),
        "allowInsecure" to tls?.cleanBoolean("allowInsecure")?.toString(),
        "path" to xhttp?.cleanString("path")
    ).filterValues { !it.isNullOrBlank() }
    val query = params.entries.joinToString("&") { (key, value) ->
        "${Uri.encode(key)}=${Uri.encode(value)}"
    }
    val safeHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
    val tag = outbound.cleanString("remarks", "remark", "name", "tag") ?: "VLESS $host"
    return "vless://${Uri.encode(id)}@$safeHost:$port?$query#${Uri.encode(tag)}"
}
```

Replace the extraction chain in the existing loop with:

```kotlin
val link = xrayVlessLinkFromOutbound(outbound)
    ?: xrayHysteriaLinkFromOutbound(outbound)
    ?: singBoxHysteriaLinkFromOutbound(outbound)
    ?: continue
```

- [ ] **Step 4: Run the focused parser suite**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.SubscriptionManagerXrayBase64Test" --tests "com.danila.nimbo.network.SubscriptionManagerHysteriaJsonTest"`

Expected: `BUILD SUCCESSFUL`; the new test proves XHTTP survives import and the Hysteria cases remain unchanged.

### Task 2: Make network handoff a first-class recovery operation

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/VpnRecoveryPolicy.kt:34-55,149-186`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt:825-935`
- Modify: `app/src/test/java/com/danila/nimbo/vpn/VpnRecoveryPolicyTest.kt`

- [ ] **Step 1: Add a failing policy test for Wi-Fi/mobile handoff**

```kotlin
@Test
fun networkHandoff_rebuildsAnIntentionalConnectionWithoutRetrySetting() {
    val active = State(desiredConnected = true, phase = Phase.CONNECTED)

    val result = VpnRecoveryPolicy.reduce(
        active,
        Event.NetworkHandoff(hasServer = true)
    )

    assertEquals(Phase.WAITING_FOR_NETWORK, result.state.phase)
    assertEquals(
        listOf(Command.CancelRetry, Command.RebuildTunnelForNetwork),
        result.commands
    )
}

@Test
fun networkHandoff_afterManualDisconnectDoesNothing() {
    val result = VpnRecoveryPolicy.reduce(
        State(desiredConnected = false, phase = Phase.DISCONNECTED),
        Event.NetworkHandoff(hasServer = true)
    )

    assertTrue(result.commands.isEmpty())
}
```

- [ ] **Step 2: Verify it fails before adding the event**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.vpn.VpnRecoveryPolicyTest"`

Expected: FAIL because `NetworkHandoff` and `RebuildTunnelForNetwork` do not yet exist.

- [ ] **Step 3: Add a policy event and one delayed service rebuild path**

Add the following sealed members to `VpnRecoveryPolicy`, and handle the event before the generic `NetworkChanged` branch. It deliberately does not use `autoReconnect`: this is continuity of an already requested tunnel, not retrying a failed connection.

```kotlin
data class NetworkHandoff(val hasServer: Boolean) : Event

data object RebuildTunnelForNetwork : Command
```

```kotlin
is Event.NetworkHandoff -> when {
    !state.desiredConnected || state.screenPaused || !event.hasServer -> Result(state)
    else -> Result(
        state.copy(
            phase = Phase.WAITING_FOR_NETWORK,
            networkAvailable = true,
            connectPending = false
        ),
        listOf(Command.CancelRetry, Command.RebuildTunnelForNetwork)
    )
}
```

In `MyVpnService`, replace the direct `pauseTunnelForNetwork(); delay(250); startRecoveryConnection()` branch with `applyRecoveryResult(VpnRecoveryPolicy.reduce(recoveryState, Event.NetworkHandoff(recoveryServer() != null)))`. Add the command handler and keep the settle delay in one method:

```kotlin
VpnRecoveryPolicy.Command.RebuildTunnelForNetwork -> rebuildTunnelForNetwork()
```

```kotlin
private fun rebuildTunnelForNetwork() {
    teardownTunnel(cancelConnectionJob = true)
    serviceScope.launch {
        delay(UNDERLYING_NETWORK_SETTLE_MS)
        if (preferencesManager.vpnConnectionDesired && hasUsableUnderlyingNetwork()) {
            startRecoveryConnection()
        }
    }
}
```

Define `UNDERLYING_NETWORK_SETTLE_MS = 750L` with the other service constants. In `scheduleUnderlyingNetworkEvaluation`, choose `connectivityManager.activeNetwork` when it is usable before falling back to the callback set; only add an `onAvailable` network after checking its capabilities. Compare its handle with `lastUnderlyingNetworkHandle` so a secondary available transport cannot repeatedly rebuild a working tunnel.

- [ ] **Step 4: Run recovery-policy tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.vpn.VpnRecoveryPolicyTest"`

Expected: `BUILD SUCCESSFUL`; existing loss/return and manual-disconnect tests still pass.

### Task 3: Preserve direct DNS in VPN-only app routing

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/vpn/VpnTunPolicy.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/XrayManager.kt:103-132,171-198`
- Create: `app/src/test/java/com/danila/nimbo/vpn/VpnTunPolicyTest.kt`

- [ ] **Step 1: Add the pure policy test**

```kotlin
class VpnTunPolicyTest {
    @Test
    fun vpnOnly_doesNotPublishTunnelDnsToUnselectedApps() {
        assertFalse(VpnTunPolicy.forProxyMode(proxyByApp = 2).publishTunnelDns)
    }

    @Test
    fun defaultAndBypassModes_publishTunnelDns() {
        assertTrue(VpnTunPolicy.forProxyMode(proxyByApp = 0).publishTunnelDns)
        assertTrue(VpnTunPolicy.forProxyMode(proxyByApp = 1).publishTunnelDns)
    }
}
```

- [ ] **Step 2: Run it to confirm the missing policy type**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.vpn.VpnTunPolicyTest"`

Expected: FAIL because `VpnTunPolicy` does not exist.

- [ ] **Step 3: Implement the policy and apply it before establishing the TUN interface**

Create `VpnTunPolicy.kt`:

```kotlin
package com.danila.nimbo.vpn

data class VpnTunPolicy(val publishTunnelDns: Boolean) {
    companion object {
        fun forProxyMode(proxyByApp: Int): VpnTunPolicy =
            VpnTunPolicy(publishTunnelDns = proxyByApp != 2)
    }
}
```

In `XrayManager.establishTun`, build the interface without unconditional DNS servers, then apply only when the policy permits it:

```kotlin
val tunPolicy = VpnTunPolicy.forProxyMode(prefs.proxyByApp)
val builder = vpnService.Builder()
    .setSession("Nimbo")
    .addAddress("172.19.0.1", 30)
    .addRoute("0.0.0.0", 0)
    .setBlocking(false)

if (tunPolicy.publishTunnelDns) {
    builder.addDnsServer("1.1.1.1").addDnsServer("8.8.8.8")
}
```

Guard the IPv6 DNS additions with the same `tunPolicy.publishTunnelDns` condition. Leave `addAllowedApplication` as the exclusive selection mechanism in mode `2`; it is what guarantees unselected UIDs stay on the underlying network. Log the selected package count only after the allow-list has been applied, without exposing package names in the diagnostic UI.

- [ ] **Step 4: Run VPN policy tests and perform one device check**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.vpn.*"`

Expected: `BUILD SUCCESSFUL`.

Device check: choose only Chrome, connect, open a site in Chrome and then in an unselected browser. Both must resolve DNS; only Chrome's public IP must be the VPN exit. Repeat after disabling and re-enabling mobile data.

### Task 4: Make HTTP ping use its configured target and publish failures

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/PingManager.kt:45-78,167-205`
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt:827-890`
- Modify: `app/src/test/java/com/danila/nimbo/network/PingManagerTest.kt`

- [ ] **Step 1: Add URL-resolution tests**

```kotlin
@Test
fun httpPing_keepsConfiguredHealthUrlWhenItHasNoServerTemplate() {
    assertEquals(
        "https://www.gstatic.com/generate_204",
        PingManager.resolveHttpUrl("edge.example", 443, "https://www.gstatic.com/generate_204")
    )
}

@Test
fun httpPing_expandsExplicitServerTemplate() {
    assertEquals(
        "https://edge.example:8443/health",
        PingManager.resolveHttpUrl("edge.example", 8443, "https://{host}:{port}/health")
    )
}
```

- [ ] **Step 2: Run the focused test and verify the old URL rewrite fails it**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.PingManagerTest"`

Expected: FAIL because the existing code rewrites the default health URL into `https://edge.example:443/`.

- [ ] **Step 3: Use the configured HTTP target and clear stale latency after a failed run**

Change `resolveHttpUrl` to `internal`, remove the provider-domain heuristic, and keep only these cases: blank means `https://host:port/`; `{host}`/`{port}` means substitution; any other valid URL remains exactly the configured endpoint. Close both response streams and call `disconnect()` in a `finally` block in `pingHttp`.

In `computeUpdatedProfiles`, replace the retained-success branch with:

```kotlin
if (newPing >= 0) {
    s.copy(ping = newPing, pingTimestamp = now)
} else {
    s.copy(ping = -1, pingTimestamp = now)
}
```

This makes a failed HTTP probe visibly unavailable rather than leaving the previous TCP/ICMP value on screen. Keep `PingConfig.useProxy` so HTTP measures the configured target through the active VPN when that setting is enabled. Update the Ping settings help text to state that HTTP uses the configured URL and `{host}` / `{port}` can be used for an explicit server HTTP endpoint; VLESS/XHTTP transports are not ordinary HTTP origins.

- [ ] **Step 4: Run ping and full unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.network.PingManagerTest"`

Expected: `BUILD SUCCESSFUL`.

Then run: `./gradlew.bat :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

### Task 5: Localize notifications and present understandable log sources

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/i18n/LogPresentation.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/LogsScreen.kt:52-67,430-470`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/TopSnackbars.kt:111-155`
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt:530-563,1150-1204,1746-1760`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt:223-256,632-805,1204-1230`
- Modify: `app/src/main/java/com/danila/nimbo/utils/NotificationManager.kt:28-60,196-256`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt:333-415`
- Create: `app/src/test/java/com/danila/nimbo/ui/i18n/LogPresentationTest.kt`

- [ ] **Step 1: Add source-label tests that do not depend on Android UI**

```kotlin
class LogPresentationTest {
    @Test
    fun knownImplementationTagsBecomeUserFacingRussianSources() {
        assertEquals("VPN-подключение", LogPresentation.source("MyVpnService", isEnglish = false))
        assertEquals("Диагностика", LogPresentation.source("Logger", isEnglish = false))
    }

    @Test
    fun knownImplementationTagsBecomeUserFacingEnglishSources() {
        assertEquals("VPN connection", LogPresentation.source("XrayManager", isEnglish = true))
        assertEquals("Subscription", LogPresentation.source("SubscriptionUpdateWorker", isEnglish = true))
    }
}
```

- [ ] **Step 2: Run the test before adding the presentation mapper**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.ui.i18n.LogPresentationTest"`

Expected: FAIL because `LogPresentation` does not exist.

- [ ] **Step 3: Add the mapper and use it only at display/export boundaries**

Create `LogPresentation.kt`:

```kotlin
package com.danila.nimbo.ui.i18n

object LogPresentation {
    fun source(tag: String, isEnglish: Boolean): String = when (tag) {
        "MyVpnService", "VpnManager", "XrayManager" -> if (isEnglish) "VPN connection" else "VPN-подключение"
        "SubscriptionManager", "SubscriptionUpdateWorker" -> if (isEnglish) "Subscription" else "Подписка"
        "PingManager" -> if (isEnglish) "Connection check" else "Проверка связи"
        "UpdateManager", "UpdateWorker" -> if (isEnglish) "App updates" else "Обновления приложения"
        "Logger" -> if (isEnglish) "Diagnostics" else "Диагностика"
        else -> if (isEnglish) "System" else "Система"
    }
}
```

In `LogsScreen`, derive `isEnglish` from `LocalConfiguration.current.locales[0].language == "en"`; show `LogPresentation.source(log.tag, isEnglish)` in `LogRow`, use the same text in search matching, and pass mapped source labels to `Logger.getLogsAsText` through a new optional `tagMapper: (String) -> String` argument. Raw tags remain available only to Logcat, not to the user-visible log list or exported diagnostic report.

Replace fixed Russian UI strings in `TopSnackbars` with `t`, for example:

```kotlin
val title = when (type) {
    NotificationType.UPDATE -> t("ОБНОВЛЕНИЕ", "UPDATE")
    NotificationType.PING -> t("ПРОВЕРКА СЕТИ", "NETWORK CHECK")
    NotificationType.SUCCESS -> t("ГОТОВО", "DONE")
    NotificationType.ERROR -> t("НУЖНО ВНИМАНИЕ", "ACTION NEEDED")
    NotificationType.NORMAL -> "NIMBO"
}
```

For `MainViewModel`, `MyVpnService`, `NotificationManager`, and `UpdateManager`, use `tNon(context, ru, en)` for strings constructed outside Compose. Convert every message passed to `showTopNotification`, VPN foreground notification, Android update notification, and `Logger` that represents a user-visible state change. Keep only developer diagnostics such as socket details in English Logcat; do not show them in the app log list.

- [ ] **Step 4: Run localization and compilation checks**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.ui.i18n.*"`

Expected: `BUILD SUCCESSFUL`.

Then run: `./gradlew.bat :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

### Task 6: Suppress false subscription and duplicate app-update notices

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/model/SubscriptionRefreshPolicy.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt:1214-1220,1686-1760`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt:333-356`
- Create: `app/src/test/java/com/danila/nimbo/model/SubscriptionRefreshPolicyTest.kt`
- Modify: `app/src/test/java/com/danila/nimbo/network/UpdateManagerVersionTest.kt`

- [ ] **Step 1: Write content-change tests**

```kotlin
@Test
fun sameServerSelectionAndMetadata_isNotANotificationWorthyRefresh() {
    val server = Server("Finland", "fi.example", 443, "id", "vless", network = "xhttp")
    assertFalse(SubscriptionRefreshPolicy.hasUserVisibleChange(listOf(server), listOf(server.copy(ping = 42))))
}

@Test
fun changedTransportPath_isANotificationWorthyRefresh() {
    val before = Server("Finland", "fi.example", 443, "id", "vless", network = "xhttp", path = "/a")
    val after = before.copy(path = "/b")
    assertTrue(SubscriptionRefreshPolicy.hasUserVisibleChange(listOf(before), listOf(after)))
}
```

- [ ] **Step 2: Implement a stable, ping-free subscription-content comparison**

Create `SubscriptionRefreshPolicy.kt` with this implementation:

```kotlin
package com.danila.nimbo.model

object SubscriptionRefreshPolicy {
    fun hasUserVisibleChange(before: List<Server>, after: List<Server>): Boolean =
        before.map(::fingerprint).sorted() != after.map(::fingerprint).sorted()

    private fun fingerprint(server: Server): String = listOf(
        server.name.trim(), server.host.trim().lowercase(), server.port, server.uuid.trim(),
        server.protocol.trim().lowercase(), server.network.orEmpty().lowercase(),
        server.security.orEmpty().lowercase(), server.path.orEmpty(), server.hostHeader.orEmpty(),
        server.serviceName.orEmpty(), server.sni.orEmpty().lowercase(),
        server.fingerprint.orEmpty().lowercase(), server.publicKey.orEmpty(), server.shortId.orEmpty()
    ).joinToString("|")
}
```

In `loadSubscription`, capture `existingProfile?.servers.orEmpty()` before building `updated`. Show the successful refresh banner only if `SubscriptionRefreshPolicy.hasUserVisibleChange(previousServers, updated.servers)` is true; otherwise leave the refreshed timestamp and UI state intact without showing “updated”. Add an informational, localized “No changes in subscription” banner only for a user-initiated manual refresh, not for background refreshes.

For app updates, normalize `currentTag` before comparing and remove the 24-hour “persistence” branch: a release can generate a system notification once per normalized newer version only. `checkUpdate()` remains the gate; it must return `null` for an equal installed release. Add this test:

```kotlin
@Test
fun normalizedCurrentReleaseIsNeverNewerOrNotified() {
    assertFalse(UpdateManager.isSemanticVersionNewer("v1.0.0", "1.0.0"))
    assertFalse(UpdateManager.isSemanticVersionNewer("1.0.0", "v1.0.0"))
}
```

- [ ] **Step 3: Run change-detection and version tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.model.SubscriptionRefreshPolicyTest" --tests "com.danila.nimbo.network.UpdateManagerVersionTest"`

Expected: `BUILD SUCCESSFUL`.

### Task 7: Fix light-theme controls and unsafe corner scaling

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/SettingsComponents.kt:157-181`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt:10918-10929`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt:218,483,538,2062-2067`

- [ ] **Step 1: Give unchecked switches an opaque, contrasting track and border in light themes**

Replace the unchecked switch colors in `SettingsSwitch` with the light-aware values below. Keep the selected colors unchanged.

```kotlin
val isLight = nebulaColors.background.luminance() > 0.5f
val uncheckedTrack = if (isLight) {
    nebulaColors.onSurface.copy(alpha = 0.20f)
} else {
    nebulaColors.textTertiary.copy(alpha = 0.28f)
}
val uncheckedBorder = if (isLight) {
    nebulaColors.onSurface.copy(alpha = 0.30f)
} else {
    Color.Transparent
}
```

Use `uncheckedTrack` and `uncheckedBorder` in `SwitchDefaults.colors`. Apply the same values to each direct `SwitchDefaults.colors` call in the mini-app theme settings so the right-hand control has a visible boundary in every style.

- [ ] **Step 2: Cap persisted and selectable corner scale at 2.0×**

Use `2.0f` as the upper bound in every `globalCorners` `coerceIn` call and change the slider range:

```kotlin
valueRange = 0.25f..2.0f
```

The preference getter must clamp existing stored `4.0f` values to `2.0f`, so users with a prior setting recover without clearing app data. Do not alter the independent bottom-bar base radius; at the new maximum it remains a deliberate pill without clipping its 80 dp content.

- [ ] **Step 3: Compile and complete the visual acceptance pass**

Run: `./gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

On a light theme, inspect off and on switches at the right edge of Settings and Theme. Set corner scale to 0.25×, 1.0× and 2.0×; open Profiles, Apps, Ping settings and the bottom navigation. Text, icons and selected indicators must remain fully visible at each setting.

---

No commit steps are included because `C:/Users/Danila/AndroidStudioProjects/Nimbo` is not a Git worktree.
