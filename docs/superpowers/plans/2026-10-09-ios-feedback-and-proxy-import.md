# iOS feedback and proxy import implementation plan

**Goal:** Correct stale VPN state, recover NaiveProxy/TUIC imports, align mobile navigation and simplify settings/update surfaces.

**Architecture:** Preserve the native VPN lifecycle and publish the received state to Compose. Keep protocol identity in parsers; reject unsupported runtimes before starting VPN. Overlay native navigation with measured scroll clearance. Keep update actions prominent with bounded notes.

**Tech Stack:** SwiftUI/NetworkExtension, Kotlin Compose, Android JVM tests, shared desktop Compose captures.

## Work
- [x] RootView.swift / VpnController.swift: handle the new published value, deduplicate transitions, reconcile foreground status and diagnose configuration errors correctly. Add publisher regression coverage to iOS tests.
- [x] LinkParser.kt / SubscriptionManager.kt: extract Naive links from wrapped subscriptions, handle readable fragment names and escaped credentials, parse TUIC without VLESS fallback. Add import tests and bump parser revision with safe stored-profile migration.
- [x] NimboCoreSelection.swift (app/extension): do not claim unsupported Naive/TUIC share links are Xray; retain import visibility and show actionable admission errors. Verify actual pinned runtime support.
- [x] NimboTabBar.swift / RootView.swift / shared UI: equal symbol/text frames, floating overlay with scroll clearance, align section action and title; move On-demand into settings and diagnostic options into their own section.
- [x] UpdateDialog.kt / PostUpdateDialog.kt / ReleaseNotesText.kt / shared UpdatesPage: compact update hierarchy and remove website-only media/download sections from in-app notes; keep full release link available.
- [ ] Run shared/Android regressions, Swift syntax/source checks, Compose layout captures at iPhone widths and large fonts; build iOS on macOS CI if available. Physical iPhone connection remains a device validation requirement.
- [x] Mirror only owned files after comparing original bytes, preserve unrelated changes, record results and remaining native limitations.

## Evidence
The user supplied two local recordings and five screenshots. Contact sheets show a persistent connecting state for >55 seconds and an inaccurate configuration approval error. No private subscription content is copied into tests or documentation.

## Verification notes
- Android regression suite: 638 tests passed, including production Naive config and protocol imports. Shared suite: 140 tests passed, including small-screen interaction tests; additional phone-width baseline captures cover light/dark at 125% text.
- iOS source contracts: 93 tests passed. These are not a substitute for an iPhone or an Xcode build.
- Isolated NaiveProxy runtime A/B smoke: two endpoints failed an HTTPS probe with the previous IP-only configuration; both returned HTTP 204 with peer/SNI and an explicit dial-address mapping. Only a loopback SOCKS process was started; system routing, VPN and certificate validation were unchanged. No credentials are included here.
- TUIC/Mieru share links retain their identity but require a full Mihomo profile for this build's mobile runtime. iOS has no linked native NaiveProxy client: imports remain visible, and startup returns an explicit unsupported-runtime message instead of sending them to Xray.
- Device checks still required: iPhone connection/state synchronization, real SwiftUI overlay geometry, Android Naive sidecar inside a VPN session. Do not publish these changes as a verified new release before those checks.

## Follow-up design pass requested by the user
- [x] Unclamp provider announcements on Android/shared pages; retain exact line breaks.
- [x] Align Android home section baseline and use adaptive, uniformly sized navigation labels.
- [x] Remove redundant Android settings branding/tagline; place profile tools and diagnostics on focused subpages without removing capabilities.
- [x] Avoid fixed-height SwiftUI captions at larger text sizes.
- [ ] Test narrower phone layouts, preserve tap targets, compile Android, and build the native iOS target on macOS.

### Second-pass validation
- Shared production-page captures reviewed at 320, 390 and 430 px, both themes, 125% text. Section baselines match; complete announcements remain visible.
- Android debug app and instrumentation APK compile. 638 Android JVM and 140 shared tests pass; 93 iOS source contracts pass.
- Android instrumentation execution is still pending: the isolated local emulator exits during startup (hardware and software acceleration). No personal device or system virtualization settings were changed.
- First private iOS CI run passed its native checks but stopped before Xcode because a diagnostic filename version did not match the packaging semver policy. Retry uses the valid beta version and does not replace published release assets.
