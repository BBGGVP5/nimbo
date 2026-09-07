# Android Updater and Whitelist Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add architecture-aware APK selection, resumable and storage/network-aware update downloads, a post-install “What changed” prompt, and a settings screen that checks likely whitelist-accessible services, keeps history, and pings a user-supplied host.

**Architecture:** Keep GitHub release parsing and installation orchestration in `UpdateManager`, but move deterministic asset/download decisions into small pure policy objects covered by JVM tests. Extend the existing connectivity diagnostics screen instead of introducing a duplicate network tool: it already owns whitelist-style probes, history, and navigation, so the new implementation adds missing targets and an embedded custom ping while exposing the screen directly from current and legacy settings UIs.

**Tech Stack:** Kotlin 2.x, Android SDK 37, Jetpack Compose Material 3, OkHttp 5, WorkManager, SharedPreferences/Gson, JUnit 4.

---

### Task 1: Make APK and download decisions independently testable

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/network/UpdateDownloadPolicy.kt`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdateDownloadPolicyTest.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdateManagerTest.kt`

- [ ] **Step 1: Write failing ABI-selection tests**

Add cases proving exact priority for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and universal fallback:

```kotlin
@Test
fun parseReleaseCandidate_prefersPrimaryDeviceAbiBeforeUniversal() {
    val release = releaseWithAssets(
        "Nimbo_v1.0.3_universal_release.apk",
        "Nimbo_v1.0.3_armeabi_v7a_release.apk",
        "Nimbo_v1.0.3_arm64_v8a_release.apk"
    )

    val candidate = requireNotNull(
        UpdateManager.parseReleaseCandidate(release, listOf("arm64-v8a", "armeabi-v7a"))
    )

    assertEquals("Nimbo_v1.0.3_arm64_v8a_release.apk", candidate.asset.name)
}
```

- [ ] **Step 2: Run the focused test and verify failure or missing coverage**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.network.UpdateManagerTest"
```

Expected: the new tests compile and expose any ambiguous ABI matching.

- [ ] **Step 3: Add the pure download policy**

Create:

```kotlin
package com.danila.nimbo.network

internal object UpdateDownloadPolicy {
    const val STORAGE_RESERVE_BYTES = 32L * 1024L * 1024L

    data class ResumePlan(
        val existingBytes: Long,
        val requestRangeStart: Long?,
        val appendResponse: Boolean
    )

    fun requestRangeStart(partialBytes: Long, expectedBytes: Long): Long? =
        partialBytes.takeIf { it > 0L && (expectedBytes <= 0L || it < expectedBytes) }

    fun shouldAppend(httpCode: Int, requestedStart: Long?): Boolean =
        requestedStart != null && httpCode == 206

    fun requiredFreeBytes(expectedBytes: Long, partialBytes: Long): Long {
        val remaining = if (expectedBytes > 0L) {
            (expectedBytes - partialBytes).coerceAtLeast(0L)
        } else {
            0L
        }
        return remaining + STORAGE_RESERVE_BYTES
    }

    fun hasEnoughSpace(
        availableBytes: Long,
        expectedBytes: Long,
        partialBytes: Long
    ): Boolean = availableBytes >= requiredFreeBytes(expectedBytes, partialBytes)
}
```

- [ ] **Step 4: Test Range and storage decisions**

Cover fresh downloads, resumable partial files, full partial files, server `206`, server `200` fallback, and the 32 MiB reserve:

```kotlin
@Test
fun partialDownloadRequestsRangeAndRequiresOnlyRemainingBytesPlusReserve() {
    assertEquals(400L, UpdateDownloadPolicy.requestRangeStart(400L, 1_000L))
    assertTrue(
        UpdateDownloadPolicy.hasEnoughSpace(
            availableBytes = 600L + UpdateDownloadPolicy.STORAGE_RESERVE_BYTES,
            expectedBytes = 1_000L,
            partialBytes = 400L
        )
    )
}
```

- [ ] **Step 5: Run focused JVM tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.network.UpdateDownloadPolicyTest" --tests "com.danila.nimbo.network.UpdateManagerTest"
```

Expected: all asset and download policy tests pass.

### Task 2: Resume APK downloads and enforce free-space/Wi-Fi requirements

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`

- [ ] **Step 1: Add the Wi-Fi-only preference**

Add:

```kotlin
private const val KEY_UPDATE_WIFI_ONLY = "update_wifi_only"

var updateWifiOnly: Boolean
    get() = sharedPreferences.getBoolean(KEY_UPDATE_WIFI_ONLY, false)
    set(value) = sharedPreferences.edit().putBoolean(KEY_UPDATE_WIFI_ONLY, value).apply()
```

- [ ] **Step 2: Reject downloads when the selected policy is not satisfied**

Before opening the HTTP response:

```kotlin
val prefs = PreferencesManager(context)
if (prefs.updateWifiOnly && !isWifiConnected(context)) {
    throw IllegalStateException("Загрузка разрешена только по Wi‑Fi")
}

val statFs = StatFs(context.cacheDir.absolutePath)
if (!UpdateDownloadPolicy.hasEnoughSpace(statFs.availableBytes, updateInfo.fileSize, partialFile.length())) {
    throw IllegalStateException("Недостаточно свободного места для загрузки и установки обновления")
}
```

`isWifiConnected()` must inspect all connected networks so an active VPN does not hide the underlying Wi-Fi transport:

```kotlin
private fun isWifiConnected(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return manager.allNetworks.any { network ->
        manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
```

- [ ] **Step 3: Implement HTTP Range resume**

Build a `Range: bytes=<partial-length>-` request when a usable `.part` file exists. Open `FileOutputStream(target, append = response.code == 206)` only for a valid partial response. If the server answers `200`, truncate and restart; if it answers `416` and the file length equals the expected length, verify the existing partial file.

Do not delete `.part` after connection loss or timeout. Delete it only when size/hash/APK verification proves that its content is invalid.

- [ ] **Step 4: Expose the selected APK and Wi-Fi toggle in the update screen**

Add a switch:

```kotlin
SettingsSwitch(
    icon = Icons.Default.Wifi,
    title = t("Скачивать только по Wi‑Fi", "Download over Wi-Fi only"),
    subtitle = t("Мобильная сеть не будет использоваться для APK", "Mobile data will not be used for APK files"),
    checked = updateWifiOnly,
    onCheckedChange = {
        updateWifiOnly = it
        preferencesManager.updateWifiOnly = it
    }
)
```

Show `updateInfo.assetName` next to file size so users can verify the automatically selected ABI.

- [ ] **Step 5: Run unit tests and assemble debug**

Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Expected: all tests pass and the debug APK is created.

### Task 3: Show “What changed” after a successful installation

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`
- Create: `app/src/main/java/com/danila/nimbo/ui/components/PostUpdateDialog.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdatePostInstallPolicyTest.kt`

- [ ] **Step 1: Persist pending release notes before launching Package Installer**

Add pending and confirmed fields:

```kotlin
var pendingUpdateChangelog: String?
var pendingUpdateReleaseUrl: String?
var lastInstalledUpdateVersion: String?
var lastInstalledUpdateChangelog: String?
var lastInstalledUpdateReleaseUrl: String?
var showPostUpdateChangelog: Boolean
```

Store the pending changelog in `recordPendingInstallation()`.

- [ ] **Step 2: Promote notes only when package replacement is confirmed**

Inside the successful branch of `confirmPendingInstallation()`:

```kotlin
prefs.lastInstalledUpdateVersion = expectedName
prefs.lastInstalledUpdateChangelog = prefs.pendingUpdateChangelog
prefs.lastInstalledUpdateReleaseUrl = prefs.pendingUpdateReleaseUrl
prefs.showPostUpdateChangelog = !prefs.pendingUpdateChangelog.isNullOrBlank()
```

Then clear all pending fields.

- [ ] **Step 3: Add a Nimbo-styled post-update dialog**

The dialog displays the installed version and two actions:

```kotlin
PostUpdateDialog(
    versionName = preferencesManager.lastInstalledUpdateVersion.orEmpty(),
    onDismiss = {
        preferencesManager.showPostUpdateChangelog = false
        showPostUpdatePrompt = false
    },
    onShowChanges = {
        preferencesManager.showPostUpdateChangelog = false
        showPostUpdatePrompt = false
        navigateTo(MiniDestination.Updates)
    }
)
```

- [ ] **Step 4: Use stored notes while offline**

In `UpdatesSettingsContent()`, initialize the installed-version changelog from `lastInstalledUpdateChangelog`; replace it with GitHub data when the request succeeds.

- [ ] **Step 5: Test promotion rules**

Verify that notes are promoted only after matching version/package time confirmation and that pending state clearing includes changelog fields.

### Task 4: Turn connectivity diagnostics into a complete whitelist-check screen

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/ConnectivityDiagnosticsScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NetworkToolsScreens.kt`
- Create: `app/src/main/java/com/danila/nimbo/network/ConnectivityProbePolicy.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/ConnectivityProbePolicyTest.kt`

- [ ] **Step 1: Add explicit whitelist targets**

The built-in list must include:

```kotlin
CheckTarget("Google", "www.google.com", group = CheckGroup.INTERNATIONAL, url = "https://www.google.com/generate_204"),
CheckTarget("Google Analytics", "www.google-analytics.com", group = CheckGroup.TELEMETRY, url = "https://www.google-analytics.com/g/collect"),
CheckTarget("Google Tag Manager", "www.googletagmanager.com", group = CheckGroup.TELEMETRY, url = "https://www.googletagmanager.com/"),
CheckTarget("Google Static", "www.gstatic.com", group = CheckGroup.TELEMETRY, url = "https://www.gstatic.com/generate_204"),
CheckTarget("Yandex", "ya.ru", group = CheckGroup.LOCAL, url = "https://ya.ru/"),
CheckTarget("Яндекс Метрика", "mc.yandex.ru", group = CheckGroup.TELEMETRY, url = "https://mc.yandex.ru/watch/1"),
CheckTarget("VK", "vk.com", group = CheckGroup.LOCAL, url = "https://vk.com/"),
CheckTarget("Telegram", "telegram.org", group = CheckGroup.INTERNATIONAL, url = "https://telegram.org/")
```

Any HTTP response below 500 proves network reachability for telemetry endpoints; they must not be marked blocked merely because an intentionally incomplete analytics request returns `400`, `404`, or `405`.

- [ ] **Step 2: Add a deterministic custom-target parser**

Create:

```kotlin
internal data class ParsedConnectivityTarget(
    val host: String,
    val port: Int,
    val url: String?
)

internal object ConnectivityProbePolicy {
    fun parseTarget(raw: String): ParsedConnectivityTarget? {
        val value = raw.trim()
        if (value.isBlank()) return null
        val uri = runCatching {
            java.net.URI(if ("://" in value) value else "https://$value")
        }.getOrNull() ?: return null
        val host = uri.host?.trim()?.takeIf(String::isNotBlank) ?: return null
        val port = when {
            uri.port in 1..65535 -> uri.port
            uri.scheme.equals("http", true) -> 80
            else -> 443
        }
        return ParsedConnectivityTarget(host, port, uri.toString())
    }
}
```

- [ ] **Step 3: Test domain, IP, URL, port, and invalid input parsing**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.network.ConnectivityProbePolicyTest"
```

Expected: all parser cases pass.

- [ ] **Step 4: Embed custom ping in the whitelist screen**

Add a `NebulaInputField`, “Проверить адрес” button, and result card. Use the current ping preferences and four attempts:

```kotlin
val config = PingConfig(
    protocol = preferencesManager.pingProtocol.toPingProtocol(),
    testUrl = parsed.url ?: "https://${parsed.host}/",
    timeoutMs = preferencesManager.pingTimeout.coerceIn(1, 10) * 1000,
    useProxy = preferencesManager.pingThroughProxy,
    proxyPort = LocalProxyConfig.PORT
)
```

Show successful attempts, packet loss, minimum/average/maximum latency, and a clear invalid-address error.

- [ ] **Step 5: Retain and improve history**

Keep the latest 20 whitelist checks, show timestamp/verdict/success count/average latency, and add “Очистить историю” with explicit confirmation.

### Task 5: Expose the whitelist check from both settings implementations

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NetworkSettingsScreen.kt`

- [ ] **Step 1: Add the current mini-app destination**

Add `WhitelistCheck` to `MiniDestination`, include it in `isSettingsSubPage()`, and render:

```kotlin
MiniDestination.WhitelistCheck -> ConnectivityDiagnosticsScreen(
    onNavigateBack = { navigateBackInMiniApp() },
    onNavigateToHistory = { navigateTo(MiniDestination.WhitelistHistory) }
)

MiniDestination.WhitelistHistory -> ConnectivityDiagnosticsHistoryScreen(
    onNavigateBack = { navigateBackInMiniApp() }
)
```

Change the three secondary-tool callbacks on `ConnectivityDiagnosticsScreen` to nullable callbacks with `null` defaults and render their buttons only when a callback is supplied. The embedded custom ping remains available in both navigation implementations.

- [ ] **Step 2: Add a visible settings action**

Add a Settings action tile named `Белые списки` / `Whitelist check`, with `Icons.Default.FactCheck`, next to Logs and Statistics.

- [ ] **Step 3: Keep the legacy NavGraph route working**

Retain `connectivity_diagnostics` and `connectivity_diagnostics_history`, and add a direct row under “Сеть и конфиг” in `SettingsScreen`.

- [ ] **Step 4: Compile all navigation branches**

Run:

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: every exhaustive `when (MiniDestination)` branch compiles.

### Task 6: Full validation and user-facing review

**Files:**
- Verify: all files listed in Tasks 1–5

- [ ] **Step 1: Run all JVM tests**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: zero failures and zero errors.

- [ ] **Step 2: Run Android Lint**

```powershell
.\gradlew.bat lintDebug
```

Expected: zero lint errors.

- [ ] **Step 3: Assemble the debug APK**

```powershell
.\gradlew.bat assembleDebug
```

Expected: `app/build/outputs/apk/debug/Nimbo_v1.0.2_universal_debug.apk` exists.

- [ ] **Step 4: Manually verify the critical flows**

1. Open Settings → Whitelist check.
2. Run built-in checks and confirm Google/Yandex/telemetry rows and history entry.
3. Ping a domain, IPv4 address, URL, and invalid value.
4. Enable Wi-Fi-only, disable Wi-Fi, and confirm APK download is rejected before file creation.
5. Re-enable Wi-Fi, interrupt a download, retry, and confirm the progress resumes from `.part`.
6. Confirm selected ABI asset name and file size are visible.
7. Simulate confirmed package replacement and verify the “Что изменилось” button opens stored release notes.

- [ ] **Step 5: Review plan coverage**

Confirm that all six user requirements map to working code and tests: ABI selection, resumable download, free-space check, Wi-Fi-only preference, post-install changelog button, and whitelist/history/custom-ping screen.
