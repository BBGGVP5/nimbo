# Dynamic Island status implementation plan

> **For agentic workers:** Execute inline; this workspace has no executing-plans sub-skill.

**Goal:** Show a compact, truthful VPN status beside Nimbo's cloud, with a clearer expanded presentation.

**Architecture:** Keep the existing ActivityKit payload and NE-derived phase. Localize compact text, use a custom bundled symbol, retain stale-state handling and privacy. Keep an existing activity during disconnecting but never start a new activity only to display disconnection.

**Tech Stack:** SwiftUI, WidgetKit, ActivityKit, XcodeGen, portable Swift tests.

### Tasks
- [ ] In `iosApp/Tests/LiveActivityPolicyTests.swift`, replace icon-only assertions with nonempty compact status (maximum 9 characters); test stale labels and disconnect lifecycle.
- [ ] In `iosApp/Shared/NimboLiveActivityPolicy.swift`, return localized compact status for every phase, preserve stale override, show disconnecting distinctly, exclude disconnecting from shouldStart only.
- [ ] In `iosApp/LiveActivity/NimboLiveActivityWidget.swift`, use `Image("NimboCloudSymbol")` in all layouts, compact status with one line/minimum scale, expanded phase title and a semantic status mark. No subscription/server names or invented traffic/security claims.
- [ ] Register Branding.xcassets in LiveActivity sources/resources in `iosApp/project.yml`. Extend final-IPA branding verification and fixtures in `iosApp/Tests/test_branding_contracts.py` to include LiveActivity assets/executable.
- [ ] Run all Python source contracts, then Apple CI Swift policy/typechecks and full IPA build including the pending-start fix. Check actual Assets.car in all three bundles. Device layout/NE behavior still requires an iPhone.
