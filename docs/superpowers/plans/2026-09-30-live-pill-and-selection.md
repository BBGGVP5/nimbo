# Live pill, selection and protocol disclosure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Show an Android system status chip, correctly explain Mihomo automatic selection, visibly mark selected nodes, and disclose actual platform/core support on the website.

**Architecture:** Keep the foreground VPN notification as the single lifecycle-owned notification. Request Android 16+ promotion using AndroidX; short chip text disappears after the initial connected interval while detailed notification content remains. Derive home labels from stored choice and authoritative live group snapshot, never invent a single outbound for rule-based routing.

**Tech Stack:** Kotlin/Compose, AndroidX core 1.18, JUnit, Go/Mihomo 1.19.31, static HTML/CSS and Node tests.

---

### Task 1: Native Android status chip

**Files:** Create `app/src/main/java/com/danila/nimbo/utils/VpnLiveUpdate.kt`, test `app/src/test/java/com/danila/nimbo/utils/VpnLiveUpdateTest.kt`; modify `app/src/main/java/com/danila/nimbo/utils/NotificationManager.kt`, `app/src/main/AndroidManifest.xml`.

- [ ] Test the chip presentation independently from Android: connecting/recovering shows an ellipsis; a just-connected session shows VPN; after 6 seconds the chip is icon-only.
- [ ] Implement the pure policy:
```kotlin
internal fun vpnLiveUpdateText(connected: Boolean, seconds: Int, recovering: Boolean): String? = when {
    recovering || !connected -> "…"
    seconds < 6 -> "VPN"
    else -> null
}
```
- [ ] On API 36+ set `setRequestPromotedOngoing(true)` and `setShortCriticalText(...)`, preserving existing title, BigTextStyle, actions and private visibility. Add `POST_PROMOTED_NOTIFICATIONS` manifest permission. On older APIs keep the existing foreground notification. Do not request overlay access or force SystemUI animations.
- [ ] Run `gradlew.bat :app:testDebugUnitTest --tests '*VpnLiveUpdateTest' :app:compileDebugKotlin --no-daemon`; require successful tests and compilation before claiming the feature builds.

### Task 2: Mihomo home and node selection

**Files:** Create `app/src/main/java/com/danila/nimbo/mihomo/MihomoHomePresentation.kt` and corresponding JUnit test; modify `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`, `MihomoProxiesScreen.kt`, `app/src/main/java/com/danila/nimbo/ui/components/UniversalPrimitives.kt`, `shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboProfilesScreen.kt`.

- [ ] Add tests for no manual selection (automatic profile groups, not choose-location), saved manual choice, nested live choice and per-connection balancing, in Russian and English.
- [ ] Implement a pure presentation object with title/subtitle. Prefer live member, then stored member; without either explain that profile groups choose a server when connecting. A load-balancing group must not claim one server.
- [ ] Use it on Home. Increase selected-node border to 2dp+, tinted fill and a filled check badge; expose `selected` semantics. Preserve uniform card height, dedicated ping buttons and current click behavior.
- [ ] Apply a stronger accent border/fill to shared profile rows. Run targeted JUnit tests and shared compile/tests.

### Task 3: Protocol evidence and website

**Files:** Add source-level tests under `tools/native/mihomo-core/preflight_test.go`; modify `C:/Users/Danila/Documents/Nimbo-site/public/index.html` and `public/styles.css`; create `test/protocol-support.test.mjs` in the site workspace.

- [ ] Cover all screenshot names in Android preflight: VLESS/Reality, HY2, VMess, Trojan, SS, WG, TUIC, AnyTLS, Mieru. Check option schemas only: this is not a remote-server or device tunnel verification.
- [ ] Add a responsive, horizontally scrollable support matrix to the website using the existing neutral visual system. Label Android Mihomo YAML, managed desktop proxy, iOS Xray/AWG and unavailable iOS Mihomo distinctly. List TCP, WS, gRPC and XHTTP only with applicable core/version caveats. Distinguish Reality from a separate protocol and support from device-tested behavior.
- [ ] Do not advertise every protocol on every OS: iOS Mihomo TUN and some managed desktop protocol lifecycles remain unfinished. Those require a separate native implementation and real-device verification, not an admission-check bypass.
- [ ] Run native preflight tests using the existing pinned patched module/cache, and site `npm run check` / `npm test`. Inspect the rendered matrix on mobile and desktop if browser access is available.

### Task 4: Verification and delivery

- [ ] Compile Android debug, run targeted Android/shared tests, retain failure evidence if memory prevents compilation. Never install on the USB device.
- [ ] Verify original source changes only; preserve unrelated workspaces and credentials. Provide explicit completed vs remaining platform work. Website source updates are not a live deployment.

Execution: inline in the current task, as requested by the user. Unavailable superpowers execution tools are not prerequisites for using the normal file/test tools.
