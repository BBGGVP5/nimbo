# iOS profile activation, subscription refresh and compact navigation plan

> **For agentic workers:** Execute inline in the attached checkout and preserve unrelated changes.

**Goal:** Re-enable Nimbo on explicit connect after another VPN, keep subscription parsing/storage off the UI thread, restore cached selection without a network refresh, and slightly reduce the floating tab bar.

**Architecture:** Refresh the owned NE manager before connect and persist isEnabled=true with the admitted profile before start. Serialize async subscription operations with a lease and a dedicated Dispatch worker; cancel queued commits and retain ownership of in-flight work. Use fresh decoders and autorelease pools. Retain selected IDs in Keychain and recover legacy selection by exact raw configuration. Migrate cached links locally; honor the existing refresh-on-launch opt-in.

**Tech Stack:** Swift, Foundation/Security, NetworkExtension, existing Kotlin parser, SwiftUI, Apple CI.

- [ ] Add pure `NimboSubscriptionOperationGate.swift`, `NimboSelectedServerRecovery.swift` and executed Swift gate/recovery tests; include them in the portable Apple regression runner.
- [ ] Update `VpnController.swift` to reload preferences before status/admission and explicitly activate only Nimbo before the final save/reload/start. Guard connection/config admission while subscription work owns the imported profile.
- [ ] Update `NimboSubscriptionRepository.swift` with a serial background worker for remote parsing, validation, commits and local migration. Remove the shared JSONDecoder; add Sendable to immutable profile/full-record/header models. Keep old data on HTTP/parse/cancellation failure.
- [ ] Update `NimboConfigurationStore.swift` to retain selected ID in Keychain; use cached raw configuration to repair older reinstalls without an ID. Keep full-document precedence and clear retained selection on removal.
- [ ] Update `RootView.swift`, `NimboApp.swift`, `NimboSubscriptionImporter.swift` to use async imports, prevent refresh/connect races, publish local restored profiles and only refresh automatically when the existing opt-in is enabled.
- [ ] Reduce normal tab icon/frame/gap/padding in `NimboTabBar.swift` while preserving labels, >=44pt targets and accessibility navigation. Keep measured bottom clearance.
- [ ] Run integration contracts, real Swift gate/recovery/blocked-worker tests and full Apple build. Verify SHA-256, embedded revision and extension catalogs, then replace iOS IPA/checksum in Beta 1 under existing publication authorization.

## Device evidence
User reports iOS leaves another app's VPN selected, and Nimbo starts after manual selection. Current app path reloads/enables a new manager but does not re-enable a restored disabled manager. Subscription refresh invokes synchronous parser/Keychain work without an explicit executor, retains one shared decoder, and startup migration can race manual refresh/connect. Screenshot contains no diagnostic stack; exact device crash attribution remains unconfirmed.
