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
- [ ] Add `python3 iosApp/Tests/test_mihomo_storage_contracts.py --swift` to Apple jobs; compile the production full record and backup files with test storage doubles using `xcrun swiftc -swift-version 5 -parse-as-library`.
- [ ] Run Python discovery after installing tree-sitter only into the ignored local test dependency directory; record any remaining failures, never claim local Swift execution on Windows.
- [ ] Verify unchanged older build status; source diff/UTF-8 checks; mirror hashes; commit/push only owned files and dispatch new unsigned iOS artifact build. Desktop code is unchanged in this repair.

## Local checkpoint
84 iOS source contracts pass; 11 changed Swift files parse without syntax errors using tree-sitter. The previous packet-flow string guard was updated to assert stronger native-inspection-before-save and complete-record compare-and-save ordering after the repository extraction. Provider description/quota metadata now travels in the backup as well; a dedicated UserDefaults suite prevents test preferences touching a user's settings. Apple executable scenarios (12 named cases, including old schema and failure atomicity) are queued through the new gate; no local Swift execution is claimed.
