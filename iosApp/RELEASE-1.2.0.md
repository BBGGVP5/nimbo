# iOS stable 1.2.0 Implementation Plan

**Goal:** Prepare stable 1.2.0 / build 170 for main's macOS release build.

**Architecture:** Keep Xray and real AWG in one Go C archive per Apple slice.
Preserve saved update-channel preferences. Main owns workflow changes, source
sync, dispatch and publication; this task edits only iosApp/, scripts/ci/ and
scripts/ios/ and runs no Gradle build.

**Tech stack:** Swift, XcodeGen, Go 1.27.0, Bash, Python, PowerShell.

- [x] Set project and IPA defaults to stable 1.2.0 / 170; reject prerelease
  versions and nonmonotonic build numbers before invoking build tools.
- [x] Change only missing/invalid native channel fallback and the convenience
  API default to stable, retaining explicitly stored beta preferences.
- [x] Inspect combined Go/native build prerequisites and add a per-slice Swift
  import/link check against the real archive. Execution awaits macOS CI.
- [x] Fix source sync omissions for GoBridge, native runtime and new CI/tests.
- [x] Check app and embedded-extension version equality; avoid old ZIP entries
  surviving a repeated IPA build; preserve the XcodeGen source specification.
- [x] Run source contracts, Go checks, shell validation and release-script
  regressions available on Windows; record unexecuted macOS/device checks.

Source ready for main's macOS dispatch as of 2026-09-12. No Gradle, native Apple
build, source sync application, push or publication was run in this task.

Exact changed paths, relative to `C:/Users/Danila/AndroidStudioProjects/Nimbo`:

- `iosApp/project.yml`: 1.2.0 / 170; explicit Security/CoreFoundation linkage.
- `iosApp/Nimbo/NimboUpdateChecker.swift`: stable defaults, saved beta retained.
- `iosApp/GoBridge/README.md`: accurate native validation/release status.
- `iosApp/RELEASE-1.2.0.md`: this handoff.
- `scripts/ci/build-libxray-awg-apple.sh`: lock toolchain before checking its
  version; diagnostic symbol checks; compile/link production Swift AWG bridge
  with each real generated C archive. Exactly one Go archive per Apple slice.
- `scripts/ci/build-unsigned-ios.sh`: stable input validation, LF line endings,
  temporary XcodeGen specification, extension version checks, fresh ZIP output,
  manifest build number/channel, temporary-file cleanup.
- `scripts/ci/check-ios-awg.py`: includes release regressions in existing CI gate.
- `scripts/ci/test-ios-release.py`: four regression tests, including 14 executed
  version/build preflight cases and real optional-widget spec transformation.
- `scripts/ci/sync-build-checkout.ps1`: includes new bridge/runtime/tests/scripts;
  excludes logs and generated binaries; defaults to `.codex-tmp/release-1.2.0`
  and accepts `-CheckoutPath`. Only dry-run mode was executed here.

Passed on Windows:

- `python scripts/ci/check-ios-awg.py`: source contracts, Go stats secret
  filtering, shell syntax and all four release regression tests.
- `scripts/ios/validate_ios_bundle_contract.ps1`: Compose UIKit plist contract.
- From `iosApp/GoBridge`, with `GOTOOLCHAIN=local GOWORK=off CGO_ENABLED=0`:
  `go test -mod=readonly -count=1 nimbo/awgcore` and
  `go vet -mod=readonly nimbo/awgcore`.
- From the existing combined source at
  `iosApp/build/awg-source/libXray-80263da83e96b2972455b0a94b13ee1a10e51391`,
  `GOOS=ios GOARCH=arm64 CGO_ENABLED=0 GOTOOLCHAIN=local GOWORK=off
  go build -mod=readonly nimbo/awgcore github.com/xtls/libxray`.
- Final diff against saved source baselines; dry-run sync against main's new
  release checkout. Main must resync the last changed files before dispatch,
  especially the newly added `scripts/ci/test-ios-release.py`.

Pinned dependencies are unchanged: Go **1.27.0**, LibXray **26.7.28** commit
`80263da83e96b2972455b0a94b13ee1a10e51391`, source SHA-256
`1596603887679f7ac6cca99eb27ecb9153fb4ccc7828c1eacd4d07bcb6d94998`, AWG
**v3.1.20260828**, gVisor **v0.0.0-20260122175437-89a5d21be8f0**.
The committed Go module graph is unchanged; no floating dependency resolution
or second Go runtime was introduced.

Main confirmed the workflow now supplies `NIMBO_VERSION=1.2.0` and
`NIMBO_BUILD_NUMBER=170`. macOS requires Xcode with device/simulator SDKs,
Go 1.27.0, Java 17, Python 3, XcodeGen and ldid-procursus. XcodeGen and ldid
installation versions remain owned by the workflow; these scripts do not pin
Homebrew packages. Shared Kotlin sources and `tools/native/awg-core` must be
present, along with Android's geoip.dat/geosite.dat assets.

Pending macOS gates: `python3 scripts/ci/check-ios-awg.py --swift`, real combined
cgo tests/archive compilation, exported symbols, all three Swift link checks,
then `bash scripts/ci/build-unsigned-ios.sh` (which invokes native preparation
itself). Expected artifact: `Nimbo_v1.2.0_ios_resignable.ipa`, with manifest
`version=1.2.0`, `build_number=170`, `channel=stable` and
`go_runtime_archives_per_slice=1`.

Neither the new C/Swift link checks nor an IPA/device connection has executed
on this Windows host. Device acceptance still needs an entitled installation
and real AWG peer: encrypted TCP, DNS/UDP, repeated starts, sleep/wake and
Wi-Fi/cellular transitions. A local running status alone is not proof of a
successful peer handshake.
