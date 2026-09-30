# On-demand and native memory implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add opt-in iOS Wi-Fi/cellular VPN On Demand, reduce avoidable native GC work, and use NIMBO in the status pill.

**Architecture:** Pure validated settings produce ordered NetworkExtension rules (trusted SSID disconnect first, selected transport connect next, ignore last). Manual stop disables the system policy without forgetting the user's saved settings; only explicit resume re-arms it. The merged Go runtime uses a soft memory budget rather than recurring forced GC; no protocol, DNS, route or upstream version is changed.

**Tech Stack:** SwiftUI/NetworkExtension, Go 1.27.1, Kotlin/Compose, Python source contracts, existing native build pipelines.

---

### Task 1: iOS on-demand policy and settings
Files: `iosApp/Shared/NimboOnDemandPolicy.swift`, `iosApp/Shared/NimboOnDemandRules.swift`, `iosApp/Nimbo/NimboOnDemandSettingsView.swift`, `iosApp/Nimbo/VpnController.swift`, `iosApp/Shared/NimboTunnelControl.swift`, `iosApp/Nimbo/RootView.swift`.
- [x] Write pure Swift tests: default disabled; no transports means disabled; trim/deduplicate SSIDs, preserve case; reject excessive/invalid input; ordered trusted-disconnect/transport-connect/ignore; no HTTP or DNS probe requirement.
- [x] Implement Codable settings with `enabled = false`, `wifi = true`, `cellular = true`, `trustedSSIDs = []`; `rulePlan` emits only selected transports. Require staged config/core admission before applying, save/load errors propagate to UI, changes serialized, manual disconnect disables rules before stopping.
- [x] Display native settings via sheet from main Settings; don't log SSIDs. Restoring manager must preserve already configured rules; clearing config disables them.
- [x] Run `python iosApp/Tests/test_on_demand_contracts.py`; add `--swift` CI compilation against iOS16 SDK and execute pure tests. Real network transition remains device-only verification.

### Task 2: native memory and periodic work
Files: `tools/native/mihomo-core/runtime_memory*.go`, `tools/native/android-bridge/nimbo_runtime_memory.go`, `tools/native/libxray-memory/memory_ios.go`, native build scripts, `MyVpnService.kt`.
- [x] Test Android 96MiB / desktop256MiB / iOS30MiB soft budgets, honor explicit environment and validate Android override32..512MiB. Stats expose only numeric runtime memory counters.
- [x] Replace upstream iOS one-second `FreeOSMemory` loop with once-only GC100 /30MiB soft budget. Apply tracked replacement to both Apple and Android source builds; never edit upstream module cache.
- [x] Android service configures the real Go budget at creation and preference changes; remove repeated Java GC/finalization, which cannot enforce Go heap limits. Helpers use 1s instead of 100ms lifecycle poll; no GC timer.
- [x] Run native tests and rebuild the merged AAR with preserved API3, four ABIs and16KiB alignment. Record new hash only after verified artifact; run Android build/unit tests.

### Task 3: pill identity
Files: Android `VpnLiveUpdate.kt`, `VpnLiveUpdateSettings.kt` and tests; iOS live policy/widget/settings.
- [x] Replace connected short text `VPN` with `NIMBO`, retain six-second Android icon-only collapse. Use NIMBO branding in Apple visible titles/preview, retain stale-status honesty and no looping animations.
- [x] Use a short slide/fade transition in labelled Compose preview, respecting reduced motion; Android system still owns real chip animation.
- [x] Run pill unit/source tests and include native SDK typechecks in iOS CI.

### Delivery boundaries
Android network-specific on-demand requires a separate lifecycle-aware implementation (and background-start/SSID permission policy); do not claim iOS rules provide it. Desktop standalone upstream Xray binary is not recompiled by tuning the merged mobile runtime. No measured RSS/battery improvement is asserted without device measurements. No phone network changes or automatic installation. Publish only relevant tested files, preserving unrelated work.


## Verification record (2026-09-30)
- Native Go policy tests and full Mihomo helper tests passed. Source-built Windows-x64 helper: 47,940,608 bytes, SHA256 `166f6550522e6ebfd4c946ced6d55e371dda888303f209e428597e23e1e2b46d`; 45 corresponding-source files byte-verified. Mesh exclusion tags retained; WireGuard still linked.
- Combined Android AAR: 190,618,346 bytes, SHA256 `384bc90bfe26ba50a9791bb9dfd3ecbd8ca660fe5398912458b6130795db4a4a`. Native verifier passed API preservation, four ABIs, 16KiB alignment, and added memory JNI methods. Previous library saved to an artifacts backup before promotion.
- Android `assembleDebug`, `testDebugUnitTest`, `testAndroidHostTest` passed twice after changes; final XML totals: 663 tests, zero failures/errors/skips.
- Arm64 debug APK: 140,797,372 bytes, SHA256 `4985cc559e6cf004f44eb073f22a6d754aeeaf34e8f1fab3d79a70cbf46d2145`. Debug package/signature and arm64-only ABI checked. An independent NDK strip of the verified AAR exactly matches packaged JNI bytes (AAR-vs-APK hashes differ legitimately because AGP strips debug symbols).
- Cross-host on-demand, live activity, native source, iOS packaging and AAR verifier tests passed. Initial macOS run passed executable Swift policy plus actual iOS16 on-demand/settings and iOS18 widget-control typechecks. Final revision full IPA build is separately dispatched; do not claim full IPA/device success from source checks alone.
- Latest pre-final Windows Rust CI: 120 passed, 1 failed, 3 intentionally ignored. Failure is the pre-existing four-second PowerShell large-output fixture timeout, not an on-demand/native assertion; final-head CI must rerun before merge. Linux Rust/frontend and shared contracts passed.
- No device network changes, installation, RSS benchmarks or battery measurement performed. iOS on-demand transitions and real system pill rendering require device verification.
- Android standalone `libwg-go.so` AWG runtime is separate and not tuned by the LibXray memory JNI call. Standalone desktop upstream Xray remains unchanged; desktop budget applies to Nimbo's source-built Mihomo helper. Android network-specific on-demand remains a separate unfinished task.

### Task 4: explicit pill-off presentation (Android and iOS)
Files: Android `VpnLiveUpdate.kt`, `NotificationManager.kt`, `VpnLiveUpdateSettings.kt`, `VpnLiveUpdateTest.kt`; iOS policy/controller/settings and Live Activity tests.
- [x] Add Android policy tests for every state with the preference off: `promoted == false`, `shortCriticalText == null`; preserve the existing foreground notification and service preference listener.
- [x] Add Swift `shouldEnd(phase:enabled:authorized:)` policy tests: disabling ends every phase; background alone does not end an active activity.
- [x] Use the tested policies in the notification builder and ActivityKit controller. Show an ordinary-notification preview when disabled on Android; explicitly label Dynamic Island / Live Activity and hide its preview when disabled on iOS. Neither toggle touches tunnel control.
- [x] Run Android debug build / unit tests and Python source contracts; add an iOS16 settings-view SDK typecheck to the existing macOS CI gate. Commit only task files and dispatch the latest iOS build.

Pill-off verification: Android debug APK and 665 unit/host tests passed; 5 Live Activity and 4 On Demand source contracts passed. New macOS settings-view SDK gate and latest IPA build are dispatched with this change; device/SystemUI verification remains pending.
