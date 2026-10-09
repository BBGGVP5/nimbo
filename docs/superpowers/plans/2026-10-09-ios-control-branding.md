# iOS Control Center branding implementation plan

> **For agentic workers:** Execute inline; the subagent/executing-plans skills are not installed in this workspace.

**Goal:** Package Nimbo's symbol in the Control Center extension and retain its identity when disconnected.

**Architecture:** Register the shared catalog as XcodeGen target sources with the resources build phase. Validate the final IPA, not just the source SVG. Do not change VPN entitlements or claim third-party re-signing/device validation.

**Tech Stack:** SwiftUI, WidgetKit, XcodeGen, Python unittest, macOS build shell.

### Task 1: Reproduce packaging failure
- [x] Add `--ipa` to `iosApp/Tests/test_branding_contracts.py`. Require nonempty Assets.car in both app and ControlWidget, plus the embedded WidgetKit extension executable and Info.plist.
- [x] Test missing widget/catalog and valid synthetic ZIP fixtures. Run against build 61675d2; expect missing widget Assets.car.
- [x] Replace the source contract that incorrectly accepts a top-level resources key with a resources-phase catalog under sources.

### Task 2: Fix catalog and control identity
- [x] In `iosApp/project.yml`, use `sources: - path: Branding/Branding.xcassets; buildPhase: resources` in app and widget. The app's own Assets.xcassets is already discovered under the Nimbo sources directory.
- [x] In `iosApp/ControlWidget/NimboControlWidget.swift`, always use `Image("NimboCloudSymbol")`; distinguish actual NEVPNStatus values in the text, not an absent/power icon.
- [x] Run Python branding tests and all iOS source contracts; review the diff and mirror only owned files to the primary workspace.

### Task 3: Guard actual release artifacts
- [x] In `scripts/ci/build-unsigned-ios.sh`, after creating the IPA and before SHA256, run `python3 .../test_branding_contracts.py --ipa "$OUTPUT_PATH"` when the ControlWidget is included.
- [x] Do not replace public release assets. A new Apple build and a real iPhone check after Feather re-signing are still required; a ZIP check does not validate signing authority or VPN access from the widget process.

## Device acceptance (not performed on this Windows host)
Preserve and sign app, PacketTunnel, ControlWidget, LiveActivity. Set up a server/profile once. Check gallery symbol, off/on states, connect/disconnect/reconnect with app closed, lock/unlock, and reinstall update. Inspect the final signed IPA to distinguish removed extensions/entitlement changes from app defects. Do not request signing private keys.

## Validation outcome
106 Python source/packaging-contract tests passed. The packaging guard rejects the actual 61675d2 IPA for missing ControlWidget/Assets.car. XcodeGen registration and state-independent symbol use are corrected in source. No new Apple build, signed-device verification, or public release replacement in this change.
