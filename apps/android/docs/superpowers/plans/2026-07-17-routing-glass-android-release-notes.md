# Routing, Glass, and Android Release Notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make domain-based direct routing work reliably, apply the global blur setting to the app's glass surfaces, and keep Android update notes free of desktop installer details.

**Architecture:** Xray field rules must express domain and IP matching as independent alternatives; domain matches must not depend on the geolocation of the resolved IP. The existing Compose theme provides global blur and transparency values, so the shared glass-surface rendering paths will consume those values on a decorative background layer while leaving content sharp. UpdateManager will sanitize the GitHub release body once, before either Android update screen renders it.

**Tech Stack:** Kotlin, Android Jetpack Compose, Xray JSON, JUnit 4, OkHttp/Gson.

---

### Task 1: Emit independent domain and IP rules for every routing action

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/RoutingProfileRules.kt`
- Modify: `app/src/test/java/com/danila/nimbo/vpn/RoutingProfileRulesTest.kt`

- [ ] **Step 1: Add a failing regression test for the Russia-direct shape**

```kotlin
@Test
fun `domain and GeoIP direct matches are independent Xray rules`() {
    val rules = RoutingProfileRules.build(
        RoutingProfile(
            bypassLocalIp = "false",
            directSites = listOf("domain:ru"),
            directIp = listOf("geoip:ru")
        ),
        includeFallback = false
    )

    assertEquals(2, rules.length())
    assertEquals("direct", rules.getJSONObject(0).getString("outboundTag"))
    assertEquals("domain:ru", rules.getJSONObject(0).getJSONArray("domain").getString(0))
    assertFalse(rules.getJSONObject(0).has("ip"))
    assertEquals("geoip:ru", rules.getJSONObject(1).getJSONArray("ip").getString(0))
    assertFalse(rules.getJSONObject(1).has("domain"))
}
```

- [ ] **Step 2: Run the focused test and verify the current combined rule fails it**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.danila.nimbo.vpn.RoutingProfileRulesTest`

Expected: FAIL because the generated direct rule contains both `domain` and `ip`.

- [ ] **Step 3: Split `addRule` into domain and IP rule emission**

```kotlin
private fun JSONArray.addRule(domains: List<String>?, ips: List<String>?, outboundTag: String) {
    val normalizedDomains = domains.orEmpty().map(String::trim).filter(String::isNotBlank)
    val normalizedIps = ips.orEmpty().map(String::trim).filter(String::isNotBlank)
    if (normalizedDomains.isNotEmpty()) addDomainRule(normalizedDomains, outboundTag)
    if (normalizedIps.isNotEmpty()) addIpRule(normalizedIps, outboundTag)
}

private fun JSONArray.addDomainRule(domains: List<String>, outboundTag: String) {
    put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put("tun-in"))
        .put("domain", JSONArray(domains)).put("outboundTag", outboundTag))
}

private fun JSONArray.addIpRule(ips: List<String>, outboundTag: String) {
    put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put("tun-in"))
        .put("ip", JSONArray(ips)).put("outboundTag", outboundTag))
}
```

- [ ] **Step 4: Run the focused routing tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.danila.nimbo.vpn.RoutingProfileRulesTest`

Expected: PASS.

### Task 2: Apply the global blur slider to primary glass surfaces without blurring text

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`

- [ ] **Step 1: Use the existing composition locals in the shared `GlassPanel` surface**

```kotlin
val reducedTransparency = LocalReducedTransparencyEnabled.current
val blurRadius = LocalGlobalBlurRadius.current.coerceIn(0f, 80f)
val glassBlur = if (reducedTransparency) 0.dp else blurRadius.dp

Box(modifier = modifier.clip(resolvedShape).border(1.dp, resolvedBorder, resolvedShape)) {
    Box(
        modifier = Modifier.matchParentSize().clip(resolvedShape)
            .background(fill)
    )
    Box(
        modifier = Modifier.matchParentSize().clip(resolvedShape)
            .background(Brush.radialGradient(listOf(
                nebulaColors.accent.copy(alpha = 0.12f), Color.Transparent
            )))
            .blur(glassBlur)
    )
    Box(modifier = Modifier.fillMaxWidth()) { content() }
}
```

The blurred layer is deliberately behind content, so labels, controls, and icons remain readable. It must be disabled by the existing reduced-transparency accessibility setting.

- [ ] **Step 2: Make the update screen's independent `NimboGlassSection` use the same values**

```kotlin
val reducedTransparency = LocalReducedTransparencyEnabled.current
val glassBlur = if (reducedTransparency) 0.dp else LocalGlobalBlurRadius.current.coerceIn(0f, 80f).dp
Box(modifier = Modifier.fillMaxWidth().clip(shape).border(1.dp, border, shape)) {
    Box(Modifier.matchParentSize().background(nebulaColors.surface))
    Box(
        Modifier.matchParentSize().clip(shape)
            .background(Brush.radialGradient(listOf(nebulaColors.accent.copy(alpha = 0.10f), Color.Transparent)))
            .blur(glassBlur)
    )
    content()
}
```

- [ ] **Step 3: Build the debug variant to check all Compose imports and modifier chains**

Run: `./gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

### Task 3: Filter desktop-only release details from Android update notes

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Modify: `app/src/test/java/com/danila/nimbo/network/UpdateManagerTest.kt`

- [ ] **Step 1: Add an Android release-body filtering test**

```kotlin
@Test
fun releaseNotesForAndroid_keepsChangesAndApkButDropsDesktopInstallers() {
    val notes = """
        ## What's new
        - Faster connection recovery
        ## Files
        - NimboSetup_1.0.1_x64.exe — Windows
        - Nimbo_1.0.1_arm64-v8a.apk — Android
    """.trimIndent()

    val filtered = UpdateManager.releaseNotesForAndroid(notes)

    assertTrue(filtered.contains("Faster connection recovery"))
    assertTrue(filtered.contains("arm64-v8a.apk"))
    assertFalse(filtered.contains(".exe"))
    assertFalse(filtered.contains("Windows"))
}
```

- [ ] **Step 2: Run the focused test and verify the helper is absent**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.danila.nimbo.network.UpdateManagerTest`

Expected: FAIL with unresolved reference `releaseNotesForAndroid`.

- [ ] **Step 3: Implement a pure `internal` release-note filter and use it in both API flows**

```kotlin
internal fun releaseNotesForAndroid(releaseBody: String): String = releaseBody
    .lineSequence()
    .filterNot { line ->
        val value = line.lowercase()
        (value.contains(".exe") || value.contains(".msi") || value.contains(".dmg") ||
            value.contains("appimage") || value.contains("windows") || value.contains("macos")) &&
            !value.contains(".apk")
    }
    .joinToString("\n")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()
```

Pass `releaseNotesForAndroid(releaseBody)` into `UpdateInfo.changelog` in both `checkUpdate` and `getReleaseInfoForTag`.

- [ ] **Step 4: Run the update unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.danila.nimbo.network.UpdateManagerTest --tests com.danila.nimbo.network.UpdateManagerVersionTest`

Expected: PASS.

### Task 4: Verify the complete Android change set

**Files:**
- Test: `app/src/test/java/com/danila/nimbo/vpn/RoutingProfileRulesTest.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdateManagerTest.kt`

- [ ] **Step 1: Run all debug unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL` with all routing and update tests passing.

- [ ] **Step 2: Manually verify the three user-visible paths on Android**

1. Select the built-in `Россия` routing profile, reconnect the VPN, then open `https://2ip.ru`; it must show the mobile/Wi-Fi public IP, not the VPN server IP.
2. On a non-flat animated background, set transparency above 0% and move blur from `0 dp` to `80 dp`; panels and update cards must visibly soften while their text stays sharp.
3. Open an Android update whose GitHub release body lists `.exe` and `.apk` files; only Android-relevant lines and shared change notes must be visible.

