# Dynamic Island status implementation plan

> **For agentic workers:** Execute inline; this workspace has no executing-plans sub-skill.

**Goal:** Show a compact, truthful VPN status beside Nimbo's cloud, with a clearer expanded presentation.

**Architecture:** Keep the existing ActivityKit payload and NE-derived phase. Localize compact text, use a custom bundled symbol, retain stale-state handling and privacy. Keep an existing activity during disconnecting but never start a new activity only to display disconnection.

**Tech Stack:** SwiftUI, WidgetKit, ActivityKit, XcodeGen, portable Swift tests.

### Tasks
- [x] In `iosApp/Tests/LiveActivityPolicyTests.swift`, replace icon-only assertions with nonempty compact status (maximum 9 characters); test stale labels and disconnect lifecycle.
- [x] In `iosApp/Shared/NimboLiveActivityPolicy.swift`, return localized compact status for every phase, preserve stale override, show disconnecting distinctly, exclude disconnecting from shouldStart only.
- [x] In `iosApp/LiveActivity/NimboLiveActivityWidget.swift`, use `Image("NimboCloudSymbol")` in all layouts, compact status with one line/minimum scale, expanded phase title and a semantic status mark. No subscription/server names or invented traffic/security claims.
- [x] Register Branding.xcassets in LiveActivity sources/resources in `iosApp/project.yml`. Extend final-IPA branding verification and fixtures in `iosApp/Tests/test_branding_contracts.py` to include LiveActivity assets/executable.
- [x] Run all Python source contracts, then Apple CI Swift policy/typechecks and full IPA build including the pending-start fix. Check actual Assets.car in all three bundles. Device layout/NE behavior still requires an iPhone.

## Verification outcome
107 Python source/packaging contracts passed. Apple CI 37954117800 succeeded at 3697e20dd0bbaf8b911ac5ba47d0d47f594fa6c2, including 8 executed Swift startup scenarios, Live Activity policy/typechecks and final Xcode build. IPA ZIP integrity, SHA-256 and NimboCloudSymbol in app/ControlWidget/LiveActivity Assets.car verified. IPA SHA-256: 41bb20961815057bc29b0d3251ba4048517aad03741c1dbde52181873cbaa69c. No real-device iOS 27.2 beta test or public release asset replacement in this turn.
