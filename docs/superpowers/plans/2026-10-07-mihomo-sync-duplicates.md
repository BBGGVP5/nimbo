# Mihomo sync duplicate fix вЂ” implementation plan

> **For agentic workers:** Execute this focused plan inline, task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Prevent internal Android Mihomo companion identifiers becoming empty desktop subscription cards, and remove only already-imported empty placeholders.

**Architecture:** Cross-sync v1 transfers links, not native YAML documents. Android exports the real parent/source link once with the parent title; desktop refuses internal `mihomo:` identifiers on import/export. Desktop startup cleanup targets only uninitialized empty internal records with default metadata; real remote subscriptions and full native profiles are untouched.

**Tech Stack:** Kotlin/JUnit, Rust/Tauri, existing link-only cross-sync v1.

## Task 1: Regression tests
- [x] Add `CrossSyncProfileLinksTest.kt` covering parent/child order, actual source fallback, local YAML omission, URI scheme casing, exact token URLs and real provider titles ending in Mihomo.
- [x] Extend `cross_sync.rs` tests: mixed old Android bundles import the real URL only; repeat application does not add another row; export never propagates an internal URL.
- [x] Extend `state.rs` tests: cleanup is persisted/idempotent and clears only dangling subscription selections; initialized/linked/local full profiles and empty HTTP subscription imports survive.
- [x] Run targeted tests first and record their expected failure against old behavior.

## Task 2: Implementation
- [x] Create pure `app/src/main/java/com/danila/nimbo/sync/CrossSyncProfileLinks.kt`, returning `List<SyncSubscription>`: use original parent/source URLs for native profiles, deduplicate canonical URLs, keep parent names and contiguous ordering. No YAML source is sent through v1.
- [x] Replace only the export subscriptions mapping in `CrossPlatformSync.kt` with `crossSyncProfileLinks(profiles)`.
- [x] Add a case-insensitive reserved-scheme predicate to `cross_sync.rs`; apply it before importer insertion and export enumeration. Do not reject the whole bundle when an older peer includes a bad child link.
- [x] In `PersistedState.normalize_runtime_defaults`, remove only `mihomo:` records with parser revision/fetch timestamp zero, no servers/info and default metadata; keep actual `core_profiles` byte-equivalent.

## Task 3: Verify and deliver
- [x] Run `cargo test -p nimbo-ui --offline cross_sync::tests` and `state::tests`; run workspace formatting and strict Clippy.
- [x] Run Android `testDebugUnitTest` and `assembleDebug` with the existing SDK/native AAR; verify ordinary and native profile regressions still pass.
- [x] Mirror only owned code/tests/plan paths after normalized HEAD preimage checks, stage explicit paths, commit/push the existing branch and dispatch a non-publishing Windows artifact build. Do not edit the running app's private data or remove actual user YAML.

## Verification record
Old desktop behavior failed both new sync regression tests and the startup migration test; old Android mapping failed 5 of 6 link tests. Fixed mapping passes all 623 Android unit tests and debug APK assembly. Desktop sync/state suites pass (14 and 12 tests), the complete GUI suite passes 135 tests with its 4 existing ignored tests, and strict workspace Clippy/formatting pass. No real app data was read or edited: cleanup was exercised on generated temporary fixtures. Final delivery receipts record exact mirror hashes, source commit and Windows build status.
