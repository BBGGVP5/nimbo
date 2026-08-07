# Android Network Intelligence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add truthful active-app rules, exact network presets, captive-portal assistance, disconnect explanations, smart server groups, and enforceable traffic budgets to Nimbo Android.

**Architecture:** Pure policy classes own matching, scoring, journal formatting, and budget decisions. Android framework adapters collect network capabilities and traffic counters, while Compose screens only render state and dispatch explicit actions. Persistent JSON is versioned and normalized on read so existing Beta installations migrate safely.

**Tech Stack:** Kotlin, Jetpack Compose, Android VpnService/ConnectivityManager/TrafficStats, Gson, JUnit 4.

---

### Task 1: Network preset matching

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/model/NetworkPreset.kt`
- Create: `app/src/main/java/com/danila/nimbo/network/NetworkContextSnapshot.kt`
- Create: `app/src/main/java/com/danila/nimbo/network/NetworkPresetMatcher.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/NetworkProfileManager.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/NetworkPresetMatcherTest.kt`

- [ ] Define optional SSID, transport, metered, roaming, captive-portal, charging, and minimum-battery matchers on `NetworkPreset`.
- [ ] Add a pure `NetworkPresetMatcher.match(presets, snapshot)` that ranks exact SSID/SIM rules over generic transport rules.
- [ ] Verify exact matches, wildcard fallback, missing permissions, and tie-breaking with JUnit.
- [ ] Collect a `NetworkContextSnapshot` from ordered `NetworkCallback` capability updates and normalize legacy presets.
- [ ] Run `./gradlew.bat testDebugUnitTest` and expect all unit tests to pass.

### Task 2: Captive portal assistance and network event journal

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/network/NetworkEventJournal.kt`
- Create: `app/src/main/java/com/danila/nimbo/network/CaptivePortalPolicy.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/ConnectivityObserver.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/CaptivePortalPolicyTest.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/NetworkEventJournalTest.kt`

- [ ] Model redacted events for transport changes, portal detection, tunnel lifecycle, health failures, retries, server switches, and recovery.
- [ ] Persist a bounded 200-event journal without server credentials, subscription URLs, UUIDs, or query strings.
- [ ] Detect `NET_CAPABILITY_CAPTIVE_PORTAL` and produce an explicit `Open login` action instead of treating the network as dead.
- [ ] Allow a user-confirmed temporary VPN pause for portal login, then request recovery when validation succeeds.
- [ ] Test redaction, ring-buffer trimming, portal state transitions, and recovery decisions.

### Task 3: Smart server groups

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/model/SmartServerGroup.kt`
- Create: `app/src/main/java/com/danila/nimbo/network/SmartServerSelector.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/SmartServerSelectorTest.kt`

- [ ] Persist named groups of stable `Server.selectionKey()` values with failure threshold, cooldown, minimum improvement, and recovery window.
- [ ] Score candidates using fresh ping, consecutive health failures, recent success, packet-loss estimate, and cooldown.
- [ ] Keep the current server when an alternative is only marginally better, and fail over after the configured health threshold.
- [ ] Record every automatic decision in the network journal and keep provider-defined Xray balancers unchanged.
- [ ] Test scoring, hysteresis, cooldown, all-failed fallback, and deleted-server migration.

### Task 4: Traffic budgets

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/model/TrafficBudget.kt`
- Create: `app/src/main/java/com/danila/nimbo/network/TrafficBudgetPolicy.kt`
- Create: `app/src/main/java/com/danila/nimbo/network/TrafficBudgetStore.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/TrafficBudgetPolicyTest.kt`

- [ ] Support device and per-app daily/monthly budgets, warning percentage, Wi-Fi/mobile scope, and warn/block/disconnect actions.
- [ ] Account only monotonic positive deltas and reset counters at local day/month boundaries.
- [ ] Evaluate budgets from real tunnel counters; never synthesize traffic.
- [ ] Surface warnings once per threshold period and enforce only actions the user explicitly enabled.
- [ ] Test resets, counter rollback, transport scoping, warning deduplication, and enforcement decisions.

### Task 5: Truthful active apps and quick rules

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/network/ActiveAppTrafficRepository.kt`
- Create: `app/src/main/java/com/danila/nimbo/network/TemporaryAppRuleStore.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/vpn/RoutingConfigurationApplier.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/TemporaryAppRuleStoreTest.kt`

- [ ] Replace simulated applications, domains, and rates with supported real counters and an honest unavailable state.
- [ ] Add quick actions for VPN, direct, block, and expiry values of 15/30/60 minutes, until disconnect, or permanent.
- [ ] Merge temporary rules ahead of persistent app rules and remove expired rules before building a VPN interface.
- [ ] Explain that Android must re-establish the VPN interface when its allowed/disallowed application list changes.
- [ ] Test expiry, precedence, disconnect cleanup, and serialization.

### Task 6: Compose screens and navigation

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NetworkPresetsScreen.kt`
- Create: `app/src/main/java/com/danila/nimbo/ui/screens/NetworkHistoryScreen.kt`
- Create: `app/src/main/java/com/danila/nimbo/ui/screens/SmartServerGroupsScreen.kt`
- Create: `app/src/main/java/com/danila/nimbo/ui/screens/TrafficBudgetsScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [ ] Add visual condition editors and current-network previews to network presets.
- [ ] Add the `Why disconnected?` timeline with reason summaries and a sanitized share action.
- [ ] Add group creation, member selection, live score explanation, and manual failover testing.
- [ ] Add budget progress, period, scope, threshold, and action controls.
- [ ] Add captive-portal banners with explicit pause/open/recover controls.
- [ ] Run `./gradlew.bat lintDebug testDebugUnitTest assembleDebug`; expect successful lint, tests, and APK assembly.

