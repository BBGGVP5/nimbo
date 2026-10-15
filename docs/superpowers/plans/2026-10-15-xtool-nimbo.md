# Nimbo xtool Build Implementation Plan

> **For agentic workers:** Execute the following isolated dependency export and packaging tasks in parallel; integrate and validate before producing the final IPA.

**Goal:** Build Nimbo 1.3.0-beta.2 for iPhone from Windows/WSL using xtool with verified macOS-built Kotlin and native core dependencies.

**Architecture:** macOS exports device-only NimboShared and LibXray frameworks with source and payload hashes. A Python generator maps the XcodeGen target graph into a private SwiftPM workspace with four targets. WSL compiles the exact Swift sources; packaging verification checks extensions, versions, resources and VPN entitlements.

**Tech Stack:** Swift 6.4.1, xtool 1.22.2, iOS SDK 27.2, Python, Kotlin/Native, Go 1.27.1, GitHub Actions macos-26.

### Task 1: Export Apple dependencies
- [x] Add NIMBO_APPLE_DEVICE_ONLY opt-in while retaining the normal three-slice native build.
- [x] Create scripts/ci/export-ios-xtool-dependencies.sh that invokes the current pinned native build and :shared:linkReleaseFrameworkIosArm64, then stages both XCFrameworks, GeoIP resources, notices and provenance.
- [x] Run python scripts/ci/test_xtool_dependency_export.py and bash syntax checks.
- [x] Snapshot only iOS build inputs and the export workflow to a separate codex/ Git branch using a temporary Git index; preserve the current working tree and staged files.
- [x] Dispatch the existing build-ios-unsigned.yml workflow on that branch with the dependency-export job definition. Download and verify the dependency tarball and SHA-256.

### Task 2: Generate the xtool workspace
- [x] Create scripts/ios/xtool_prepare.py with explicit --repo-root, --dependencies-root and --output arguments, refusing missing dependencies and nonempty output directories.
- [x] Map the app, PacketTunnel, ControlWidget and LiveActivity target memberships, deployment versions, Info.plists, entitlements and link settings from iosApp/project.yml.
- [x] Preserve root resources used through Bundle.main and static framework headers/linkage. Record source and dependency hashes.
- [x] Run python scripts/ios/test_xtool_prepare.py with target graph and validation tests.

### Task 3: Compile and verify
- [x] Generate an isolated WSL workspace from the recorded source snapshot and downloaded dependencies.
- [x] Run xtool dev build --configuration release --ipa using pinned Swift, SDK and local zip tool.
- [x] Resolve concrete SwiftPM/SDK/linker failures without stubbing product APIs or replacing current Kotlin/core code with old artifacts.
- [x] Verify the final IPA contains arm64 iOS device binaries, the three extension bundles, their Info.plists/entitlements, geo resources, branding, notices, Beta2 version and source provenance.
- [x] Save the local IPA and build receipt under artifacts/ios-xtool. Report device execution separately from build verification.

## Executed validation

- Apple dependencies: GitHub Actions run 38735999195, source c7b9d776b5d9953bca809f303c80284c2ba6dce9, success.
- Clean Windows/WSL build: Swift 6.4.1, xtool 1.22.2, iOS SDK 27.2, release build completed in 37.30 seconds.
- Corrected SwiftBuild frontend target forwarding and Objective-C linker flags. Corrected xtool root-entitlement inheritance using per-executable Procursus ldid.
- Final artifact verified: arm64 iOS, three extensions, minimum systems 16.0/16.2/18.0, exact per-bundle entitlements, revision, assets, icon PNGs, nine fonts, notices and geo resources.
- Generator/export/dependency/package tests passed. Branding IPA checks and 20 version-field checks passed.
- Device execution and Apple provisioning still require an iPhone acceptance test. Existing Swift 5 concurrency warnings remain; no runtime source annotations were changed to suppress them.
