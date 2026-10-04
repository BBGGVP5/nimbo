# iOS full-profile recovery Implementation Plan

> **For agentic workers:** Execute inline, task-by-task; preserve unrelated dirty resources and mirror only verified owned paths.

**Goal:** Fix the four existing iOS storage/branding failures with real offline Mihomo backup recovery, guarded refresh, and executable Apple-host regression scenarios.

**Architecture:** Keep the full YAML record authoritative in Keychain. Restore validates the archive before profile/settings writes; full records never refetch or fall back to retained Xray nodes. Refresh compares the complete previous record after awaiting HTTP, rejecting stale source or group choices. Swift backup tests execute production serialization/restore with isolated in-memory storage, never a VPN/Keychain operation.

**Tech Stack:** Swift Foundation/CryptoKit, existing native Mihomo inspection, SwiftUI, Python source contracts, macOS GitHub Actions.

## 1. Source and executable regressions
Files: `iosApp/Tests/test_mihomo_storage_contracts.py`, new `FullConfigurationTests.swift`, `BackupStorageStubs.swift`.
- [x] Reproduce original failing source tests: `python iosApp/Tests/test_mihomo_storage_contracts.py` (three failures before implementation).
- [x] Add source guards for explicit full import/refresh, early legacy-selection rejection, bounded file reads, and compare-and-save before storage.
- [x] Add Swift scenarios: exact BOM/CRLF/Unicode JSON round trip; altered digest and unsupported version rejection; changed-source choice reset; invalid UTF-8 and size limits. Example: `let changed = try original.replacingSource(Data("proxy-groups: []\n".utf8)); precondition(changed.groupSelections.isEmpty)`.
- [x] Execute production backup restore with a dedicated UserDefaults suite and fake store: full offline restore, old version-1 archive, invalid/version/oversize/tampered archive leaves both store and defaults untouched, native/save refusal leaves settings untouched, full source is never returned for HTTP refetch.

## 2. Repository and persistence
Files: `iosApp/Nimbo/NimboSubscriptionRepository.swift`, `NimboConfigurationStore.swift`, `NimboPlatformInfo.swift`.
- [x] Route full payload through `importFullConfiguration`; validate/native-inspect before `saveFullConfiguration`. Expose a separate legacy backup restore entry point, retaining explicit selected/automatic IDs.
- [x] Full refresh must request Mihomo, reject non-full response, retain choices only for identical source, and call compare-and-save against the entire captured record: `guard try loadFullConfiguration() == expected else { throw NimboFullConfigurationError.staleRefresh }`.
- [x] Lock full-record read/compare/write, so the compare cannot race a live selection write. Preserve legacy migration for legacy records only.
- [x] Request Mihomo representation explicitly for full refresh; preserve the previous profile on error and never implicitly alter the running VPN.

## 3. Offline backup and native presentation
Files: `iosApp/Nimbo/NimboBackup.swift`, `RootView.swift`, `AboutView.swift`, `NimboSubscriptionMeta.swift`.
- [x] Add optional `fullConfiguration` in archive schema 2; accept old schema 1. Export a validated record; restore `try fullConfiguration.validate()` and commit profile before settings. Return `payload.fullConfiguration == nil ? payload.source : nil`.
- [x] Read selected backup files through a capped InputStream (32 MiB); reject unsupported versions and invalid metadata before mutation. Include persisted core/ad policy keys; keep hardware IDs/telemetry out.
- [x] Reject restore during a live/busy VPN or another import; full recovery is offline and skips pinging a legacy node.
- [x] Render the existing `Image("NimboCloudSymbol")` in the About header with an accessibility label; do not alter branding assets or system shortcuts.

## 4. Verification and delivery
Files: `.github/workflows/build-ios-unsigned.yml`, `.github/workflows/ci.yml`, plan/report.
- [x] Add `python3 iosApp/Tests/test_mihomo_storage_contracts.py --swift` to Apple jobs; compile the production full record and backup files with test storage doubles using `xcrun swiftc -swift-version 5 -parse-as-library`.
- [x] Run Python discovery after installing tree-sitter only into the ignored local test dependency directory; record any remaining failures, never claim local Swift execution on Windows.
- [x] Verify unchanged older build status; source diff/UTF-8 checks; mirror hashes; commit/push only owned files and dispatch new unsigned iOS artifact build. Desktop runtime changes are formatting and an equivalent fetch-options initializer; the Linux helper test now includes the added ad-blocking field.

## Local checkpoint
84 iOS source contracts, 11 packet-flow source checks and 9 native-source checks pass locally; changed Swift files parse without syntax errors using tree-sitter. Production backup/full-record code executed on macOS in run 37188972048: all 14 scenarios passed, including offline metadata/settings recovery, exact source-envelope extraction and native inspection identity. Storage doubles and a dedicated UserDefaults suite do not touch a user's Keychain, network or VPN.

The executable regression exposed Foundation's NSString JSON parser consuming a BOM inside an originalYAML value. Exact source extraction and inspection now use typed Codable decoding before the graph projection. Persisted records retain authoritative UTF-8 Data with additive backwards-compatible decoding, digest validation and no silent source replacement. Test fixtures also preserve the source before intentional tampering; validation was not relaxed to make them pass.

Local Rust checks passed 164 tests across IPC, Mihomo, subscriptions and the Windows helper (seven external/native cases remain explicitly ignored). cargo fmt passes. Strict Clippy identified one fetch-options default reassignment; use the equivalent struct initializer rather than disabling its lint. The Linux CI compile failure was a missing ad_blocking field in a test fixture. No host TUN/firewall/service changes were performed.

Prior binaries for source 320981e built successfully: unsigned iOS run 37186792796 and Windows/Linux artifacts run 37186794539. Updated iOS artifacts are being built in run 37188974238; its final status belongs in the delivery receipt. A successful build does not replace signed physical-device Packet Tunnel testing.
