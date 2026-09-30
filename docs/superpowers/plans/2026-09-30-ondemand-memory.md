# On-demand and native memory implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in iOS Wi-Fi/cellular VPN On Demand, reduce avoidable native GC work, and use NIMBO in the status pill.

**Architecture:** Pure validated settings produce ordered NetworkExtension rules (trusted SSID disconnect first, selected transport connect next, ignore last). Manual stop disables the system policy without forgetting the user's saved settings; only explicit resume re-arms it. The merged Go runtime uses a soft memory budget rather than recurring forced GC; no protocol, DNS, route or upstream version is changed.

**Tech Stack:** SwiftUI/NetworkExtension, Go 1.27.1, Kotlin/Compose, Python source contracts, existing native build pipelines.

---

### Task 1: iOS on-demand policy and settings
Files: `iosApp/Shared/NimboOnDemandPolicy.swift`, `iosApp/Shared/NimboOnDemandRules.swift`, `iosApp/Nimbo/NimboOnDemandSettingsView.swift`, `iosApp/Nimbo/VpnController.swift`, `iosApp/Shared/NimboTunnelControl.swift`, `iosApp/Nimbo/RootView.swift`.
- [ ] Write pure Swift tests: default disabled; no transports means disabled; trim/deduplicate SSIDs, preserve case; reject excessive/invalid input; ordered trusted-disconnect/transport-connect/ignore; no HTTP or DNS probe requirement.
- [ ] Implement Codable settings with `enabled = false`, `wifi = true`, `cellular = true`, `trustedSSIDs = []`; `rulePlan` emits only selected transports. Require staged config/core admission before applying, save/load errors propagate to UI, changes serialized, manual disconnect disables rules before stopping.
- [ ] Display native settings via sheet from main Settings; don't log SSIDs. Restoring manager must preserve already configured rules; clearing config disables them.
- [ ] Run `python iosApp/Tests/test_on_demand_contracts.py`; add `--swift` CI compilation against iOS16 SDK and execute pure tests. Real network transition remains device-only verification.

### Task 2: native memory and periodic work
Files: `tools/native/mihomo-core/runtime_memory*.go`, `tools/native/android-bridge/nimbo_runtime_memory.go`, `tools/native/libxray-memory/memory_ios.go`, native build scripts, `MyVpnService.kt`.
- [ ] Test Android 96MiB / desktop256MiB / iOS30MiB soft budgets, honor explicit environment and validate Android override32..512MiB. Stats expose only numeric runtime memory counters.
- [ ] Replace upstream iOS one-second `FreeOSMemory` loop with once-only GC100 /30MiB soft budget. Apply tracked replacement to both Apple and Android source builds; never edit upstream module cache.
- [ ] Android service configures the real Go budget at creation and preference changes; remove repeated Java GC/finalization, which cannot enforce Go heap limits. Helpers use 1s instead of 100ms lifecycle poll; no GC timer.
- [ ] Run native tests and rebuild the merged AAR with preserved API3, four ABIs and16KiB alignment. Record new hash only after verified artifact; run Android build/unit tests.

### Task 3: pill identity
Files: Android `VpnLiveUpdate.kt`, `VpnLiveUpdateSettings.kt` and tests; iOS live policy/widget/settings.
- [ ] Replace connected short text `VPN` with `NIMBO`, retain six-second Android icon-only collapse. Use NIMBO branding in Apple visible titles/preview, retain stale-status honesty and no looping animations.
- [ ] Use a short slide/fade transition in labelled Compose preview, respecting reduced motion; Android system still owns real chip animation.
- [ ] Run pill unit/source tests and include native SDK typechecks in iOS CI.

### Delivery boundaries
Android network-specific on-demand requires a separate lifecycle-aware implementation (and background-start/SSID permission policy); do not claim iOS rules provide it. Desktop standalone upstream Xray binary is not recompiled by tuning the merged mobile runtime. No measured RSS/battery improvement is asserted without device measurements. No phone network changes or automatic installation. Publish only relevant tested files, preserving unrelated work.
