# iOS Safe Native Tab Bar Implementation Plan

> **For agentic workers:** Execute inline; execution sub-skills are not installed. Parent owns production/test changes, snapshot, mirroring and CI. One authorized worker owns only the two simulator harness files. Never reset the index, modify user profiles or touch VPN/network policy.

**Goal:** Provide a tested compatibility candidate for the SwiftUI first-scene hang without claiming its cause proven.

**Architecture:** Keep the same floating native tab bar, routing, metrics, typography and accessibility behavior; remove only its optional Liquid Glass branch and reuse existing regular-material/opaque backgrounds. Add an honest bootstrap-task/logging boundary before its first await. Exercise actual tab-bar and tab-model source in the independent simulator launch harness while retaining synthetic application services.

**Tech Stack:** SwiftUI/UIKit, Foundation Swift tests, Python source contracts, conventional Xcode/macOS CI.

## Evidence and limits

Build181 matching-UUID report is a scene-create watchdog with sampled main thread in SwiftUI graph/layout and GlassContainer type metadata. Build182 user journal reaches ROOT_OWNER_APPEARED after COMPOSE_CONTROLLER_RETURNED, but has no migration marker. This proves neither successful Compose drawing nor that migration began: unmarked task entry and awaited diagnostics are also before that marker. Removing glass is a compatibility experiment, not causal proof. No profiles, keys, settings or native cores are removed or altered.

## Task 1: Preserve baseline and test the candidate

- [x] Save HEAD/branch/index and owned/protected bytes to root .codex-tmp/ios-safe-tabbar-20261015.
- [x] Change iosApp/Tests/test_final_native_routes.py to require no .glassEffect( call anywhere in the main app while retaining only NimboTabBar.swift material and the accessibility opaque fallback before material. Run red first.
- [x] Add a source contract in iosApp/Tests/test_native_launch_contracts.py requiring claimBootstrap then mark(.recordingAppStart) before await NimboDiagnostics.shared.record, before mark(.migratingProfile). Require typed enum phase RECORDING_APP_START. Run red first.

## Task 2: Narrow production change (parent)

- [x] In iosApp/Nimbo/NimboTabBar.swift remove only these two lines, falling through to the existing material fallback; retain dimensions, border, opaque accessibility fallback, tabs and selection:

```swift
} else if #available(iOS 26.0, *) {
    shape.fill(.clear).glassEffect(.regular, in: shape)
```

- [x] In iosApp/Nimbo/NimboLaunchModel.swift add `case recordingAppStart = "RECORDING_APP_START"` next to root-owner/migration phases. In iosApp/Nimbo/NimboRootOwnerView.swift add `launch.mark(.recordingAppStart, attempt: attempt)` immediately after claimBootstrap guard and before diagnostics await. Keep logging, migration/restore ordering and owner lifetime otherwise unchanged.
- [x] Run all iosApp/Tests/test_*.py source contracts and Foundation launch runner. These are not device/rendered UI proof. Leave historical preview receipts and audits outside canonical source untouched.

## Task 3: Actual tab bar component UI (worker)

- [x] Change only scripts/ios/test_native_launch_ui.py and scripts/ios/test_native_launch_ui_contracts.py. Add actual NimboTabBar.swift and NimboTab.swift to COMPONENTS. Provide explicit NimboNative colors/brand/system-font test doubles required by that source, with no real RootView, shared Kotlin, VPN, storage or profile calls.
- [x] In synthetic root host, lay out actual floating NimboTabBar over full-size synthetic content. Keep zero root/font counts before explicit Continue. Give selected synthetic content a test identifier and a heartbeat independent of tab changes.
- [x] Add XCUITest that continues, taps all four actual tab identifiers repeatedly, verifies each selected raw value and checks heartbeat advancement. Attach real screenshot. Keep diagnostic relaunch test. Do not call this full Nimbo/Compose/iOS27 device acceptance. Set smoke build183.
- [x] Run portable harness contracts; parent reviews and executes actual simulator CI.

## Task 4: Snapshot, build and report (parent)

- [x] Verify protected hashes and original index/HEAD/branch. Mirror only reviewed files after before-hash checks.
- [x] Create separate-index snapshot from7ef82b601053 with only reviewed files and this plan, on codex/ios-safe-tabbar-20261015. Do not switch/reset/stage real checkout. No public release update.
- [x] Dispatch standard build-ios-unsigned.yml with build_number183, launch_diagnostics=true and ping_only=false; require all existing gates and expanded component UI tests. Build-number default181 and release version remain unchanged.
- [x] Download immutable artifact, verify digest, all four build numbers183, revision, diagnostics flag, resources/entitlements/AppIntents and packaging. Preserve182 untouched.
- [x] Report compatibility candidate and checks without claiming the phone hang fixed until physical-device confirmation. Explain new RECORDING_APP_START boundary honestly.

## Parent checkpoint

Source178ab64f6b29 / run38783114289 passed all source gates and three actual component XCUITests on iPhone16e simulator runtime iOS26.2. Actual native tab bar/model were exercised through12 repeated taps and independent heartbeat advancement. Four unmodified screenshots from xcresult were inspected. Presentation/font/application services are test doubles; this is not full Nimbo/Compose or iOS27.2 phone evidence. Full conventional-Xcode IPA183 build remains in progress at this checkpoint.

## Final package checkpoint

Run38783114289 / source178ab64f6b29 succeeded. Conventional-Xcode build183 and all three extensions compiled. Immutable artifact ZIP digest and actual IPA hashes/arm64 load commands, all four build numbers183, source revision, diagnostics1, resources/fonts/geodata/notices, required entitlements and compiled AppIntents metadata passed read-only verification. IPA77,660,575bytes; SHA256b8cd8696c09fb8a34bae2093e77da3171329d35eb4721b18e02fa07d72d885c9. Original HEAD/branch/index and protected operational sources remained unchanged; both mirrors match reviewed code. No profile/settings reset or public release update. This is a compatibility candidate; no physical iOS27.2 test or causal proof is claimed.
