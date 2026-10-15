# iOS Native Launch Recovery Implementation Plan

> **Worker-scoped instructions:** Execute this delegated implementation inline. This worker must not create subworkers, commit, push, mirror, edit workflows, dispatch CI or perform cleanup. The parent owns authorized source snapshots, CI workflow changes and dispatches. These scope limits override the writing-plans template's worker/commit suggestions for this worker only.

**Goal:** Show an independent native startup/recovery surface before realizing RootView, VPN ownership, custom fonts, defaults cleanup or Kotlin; record only safe startup phases without claiming the black-screen cause is fixed.

**Architecture:** A Foundation state machine admits one lazy root attempt only after the native appearance/display acknowledgement and (in diagnostics/recovery) explicit Continue. A native SwiftUI coordinator asynchronously reads/writes a new bounded diagnostic journal outside UserDefaults and configuration locks; a separate root owner retains the existing migration/restore task. A watchdog can report a suspended asynchronous stage, but cannot unfreeze a blocked main thread; incomplete attempts return to native recovery next launch.

**Tech Stack:** Swift 5-compatible Foundation, SwiftUI, UIKit/CADisplayLink, Python unittest and swiftc. All paths below are relative to `C:/Users/Danila/AndroidStudioProjects/Nimbo/.codex-tmp/github-ios-build-20260928`.

---

### Task 1: Capture the dirty baseline and establish executable tests

**Files:** Evidence only under root `.codex-tmp/ios-native-launch-recovery-20261015/`; create `iosApp/Tests/NativeLaunchRecoveryTests.swift`, `iosApp/Tests/test_native_launch_contracts.py`, `scripts/ios/test_native_launch_recovery.py`.

- [x] Save original bytes and SHA-256 for all existing owned files, plus index hash, HEAD/branch and protected RootView/VPN/storage/repository/Kotlin hashes. Do not stage anything.
- [x] Add Foundation tests with injected factories and deterministic elapsed times. Test actual `NimboLaunchRootGate.begin`/`enter` behavior: count stays zero before native acknowledgement and explicit Continue; duplicate Continue cannot admit a second owner; timed-out owners retain ownership until preparation returns; stale UUID callbacks cannot advance a later attempt; incomplete journals require explicit recovery; completed/native-only attempts do not imply failure.
- [x] Add journal round-trip, malformed/oversized record and metadata/privacy tests using synthetic fixtures in a temporary directory; no real configuration storage.
- [x] Run `python3 -B scripts/ios/test_native_launch_recovery.py --swiftc swiftc`; initial compile must fail because production model/journal files do not exist.

### Task 2: Implement the native Foundation gate and minimal journal

**Files:** Create `iosApp/Nimbo/NimboLaunchModel.swift`, `iosApp/Nimbo/NimboLaunchJournal.swift`.

- [x] Define Codable phase enums and a bounded record containing only version/build/revision and enum/time events. Derive incomplete status from unsafe phases and the terminal handoff marker; never serialize profiles, URLs, names, keys or raw errors.
- [x] Implement the model as a value type and an injected root gate with separate admission and actual factory entry:

```swift
// Admission never invokes the factory; entry is called after journal acknowledgement.
let token = gate.begin(explicit: true)
let entered = token.map { gate.enter($0, factory: factory) } ?? false
```

- [x] Retain ownership on timeout. Ignore stale/terminal callbacks; permit preparation retry only after the previous pre-root operation actually returns. Once a root is mounted, keep that owner for the process lifetime; staying native must not destroy/remount it or duplicate migrations.
- [x] Implement asynchronous serial file I/O under a new Application Support/NimboLaunchDiagnostics directory, bounded decode size/events, atomic writes and enum-only failure results. Initial coordinator creation must perform no I/O. Persist the root-preparation boundary before executing fonts/root factory; later markers are asynchronous and cannot promise survival after fatal termination.
- [x] Run the Foundation runner and inspect the assertions rather than changing expectations to match implementation.

### Task 3: Wire the native view, display observer and lazy root owner

**Files:** Modify `iosApp/Nimbo/NimboApp.swift`, `iosApp/Nimbo/ComposeScreen.swift`; create `iosApp/Nimbo/NimboLaunchCoordinator.swift`, `iosApp/Nimbo/NimboLaunchView.swift`, `iosApp/Nimbo/NimboLaunchAppearanceObserver.swift`, `iosApp/Nimbo/NimboRootOwnerView.swift`.

- [x] Replace the app entry body with `WindowGroup { NimboLaunchView() }`; App has no custom initializer or VPN StateObject.
- [x] Use only system fonts/colors and in-memory coordinator state in the first native surface. A UIKit child reports appearance plus subsequent display cycles, dispatching after the display callback; wording must not claim GPU presentation proof. The diagnostic mode remains native until the user presses Continue, and its journal stays incomplete until explicit “Интерфейс виден” confirmation; retain the diagnostic overlay.
- [x] Load the previous diagnostic-only journal after native acknowledgement. Interrupted/corrupt journal states cannot auto-enter root. Offer native-only stay, explicit Continue/retry when ownership permits, and ShareLink of sanitized local JSON text. Do not clear user data or silently suppress startup failure.
- [x] After an acknowledged pre-root journal write, register existing fonts and retire the existing nine appearance defaults, then mount the separate child which owns `@StateObject private var vpnController = VpnController()`. The root child is created only by the gated conditional; do not preconstruct it, hide an eager root, or put its StateObject in the launch parent.
- [x] Preserve the original asynchronous migration and observe-only VPN restore in that child. Optional shortcut indexing starts only after root host appearance/display acknowledgement. RootView's existing ActivityKit task necessarily runs only after the first native gate because RootView itself does not exist earlier. Do not edit RootView, VPN controller, storage, repository or shared Kotlin.
- [x] Add optional environment-based Compose factory begin/return markers in ComposeScreen without changing the returned controller or introducing required environment objects. Do not treat Swift catch as a handler for Kotlin fatal errors. Native host display-cycle acknowledgement is not a Compose/GPU draw acknowledgement.
- [x] Keep native failure visible on async watchdog expiry; a main-thread freeze can only be diagnosed from the last incomplete phase after relaunch. Never remount the root while its old task can still write.

### Task 4: Build flag and focused integration contracts

**Files:** Modify `iosApp/Nimbo/Info.plist`, `iosApp/project.yml`, `iosApp/Tests/test_app_startup_contracts.py`; focused tests/runner from Task 1.

- [x] Set project default `NIMBO_LAUNCH_DIAGNOSTICS: '0'`; put `NimboLaunchDiagnostics: '$(NIMBO_LAUNCH_DIAGNOSTICS)'` in generated plist properties and the actual source plist. Parse the Bundle string using `value == "1"`. Include the existing build revision placeholder in the source plist for diagnostic identity.
- [x] Replace old eager-root source expectations with native-entry, lazy-child, after-gate font cleanup, preserved migration/restore and optional-shortcut gating expectations. Preserve existing font/resource and display-key contracts.
- [x] Run `python -B iosApp/Tests/test_app_startup_contracts.py -v` and `python3 -B scripts/ios/test_native_launch_recovery.py --swiftc swiftc`. On this Windows host use the existing WSL Swift toolchain; Mac CI uses swiftc directly.
- [x] Produce a scoped patch against the captured dirty baseline, after-hashes and a report listing actual checks and platform limitations. Verify protected file/index hashes unchanged. Parent owns build number 182, workflow inputs/validation/forwarding, independent iOS branch and standard Xcode build/UI smoke. No local device/UI execution is assumed.

### Acceptance and limits

- [x] Initial native frame path contains no RootView/VpnController/custom fonts/defaults cleanup/Kotlin/Keychain/native core calls; factory tests remain at zero before the gate.
- [x] Diagnostics=1 and interrupted launch both require explicit Continue; normal mode may advance only after appearance/display acknowledgement and journal load.
- [x] Root-owner lifetime, watchdog and late callbacks cannot create overlapping migrations or falsely clear a failed attempt.
- [x] Export contains only safe Bundle identity and enum/time phases; persistence never uses configuration/defaults locks.
- [x] Report does not claim GPU rendering, device verification or the user's black-screen cause is fixed.

Execution evidence (2026-10-15): Foundation tests passed in 9 scenario groups; all 171 iOS source contracts passed without skips; standalone arm64 iOS SDK UI typecheck passed with application-service seams. These checks do not execute SwiftUI or prove actual pixels. Parent-owned Xcode build 182 / diagnostics 1 and UI smoke remain outside this worker scope. Baseline, scoped patch and hashes are in root `.codex-tmp/ios-native-launch-recovery-20261015/`.
