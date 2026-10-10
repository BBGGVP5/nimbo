# iOS connection responsiveness implementation plan

> **For agentic workers:** Execute inline in the existing checkout, preserving unrelated changes.

**Goal:** Keep synchronous VPN start/stop IPC off the UI thread and update connection state without reloading subscriptions.

**Architecture:** Add one serial worker for synchronous NEVPNConnection commands, with a revocable start lease. A stop queued behind an in-flight start must execute after it; timeout/cancel must not release outstanding command ownership. Separate the lightweight Compose connection-state bridge from profile refresh. Record startup boundaries and source revision for diagnosable device reports.

**Tech Stack:** Swift concurrency/Dispatch, NetworkExtension, Kotlin Compose bridge, macOS CI.

- [x] Add `iosApp/Shared/NimboVpnCommandQueue.swift` and meaningful portable tests (`iosApp/Tests/VpnCommandQueueTests.swift`) for blocked commands with responsive MainActor, queued cancellation, ordered start/stop, cancellation during an in-flight command and error propagation.
- [x] Add `iosApp/Shared/NimboVpnSystemCommands.swift`: submit NEVPNConnection start/stop to the worker and recheck observed status before starting. Include both shared files in the widget target and standalone typechecks.
- [x] Wire `VpnController.swift` and `NimboTunnelControl.swift` to await commands. Revoke the start lease on explicit stop and watchdog timeout; retain write guards. Add before/after markers for configuration staging, On Demand and the actual start call.
- [x] In `RootView.swift`/`IosComposeController.kt`, send only connection/error state on VPN transitions. Keep metadata/profile refresh on existing explicit refresh/import/selection paths.
- [x] Embed `NimboBuildRevision` through `iosApp/project.yml`/`scripts/ci/build-unsigned-ios.sh`; include it in `NimboDiagnostics.swift` so equally numbered beta IPAs are distinguishable.
- [x] Adjust affected integration checks; run focused tests and Apple CI including actual Swift queue tests and complete IPA build. Verify SHA-256, symbols, embedded revision and extension packaging. Update only iOS assets of Beta 1 after verification, as already authorized.

## Evidence and limits
IMG_9574.MP4 is an 8.1s recording of a VLESS/XHTTP selection. The app's connecting animation becomes static after the button press. The user confirmed this recording uses the build preceding the previous fixes; it is not proof the latest fixes regressed. Without device stack samples, the exact blocked native frame remains unknown. No private profile material from this recording is included in tests or release notes.

## Verification and release outcome
108 Python integration/source contracts passed. Apple CI 37960235981 succeeded at c8a16606fffa1ec8a2409ad76fe4a9738b99e66a, including the executed blocked-command/MainActor/cancellation/stop-ordering regression, eight startup policy scenarios, extension typechecks, Kotlin bridge compilation and the final Xcode build. The first CI attempt stopped at an ambiguous test expression; explicit closure typing repaired it before the successful run.

Downloaded IPA ZIP integrity and SHA-256 verified: e947241813b827028018432e794259e6d3fd01fea462fa5bcba80cd583e13436, 76,310,182 bytes. Actual Info.plist embeds revision c8a16606fffa; NimboCloudSymbol is present in app, ControlWidget and LiveActivity catalogs. Replaced the iOS IPA and normalized SHA-256 asset in public v1.3.0-beta.1 and verified GitHub digests; other 26 assets and release text unchanged. No physical iPhone test is claimed. The video is from the preceding build, as confirmed by the user.
