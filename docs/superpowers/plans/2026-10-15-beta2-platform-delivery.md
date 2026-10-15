# Beta 2 Platform Delivery Implementation Plan

> **For agentic workers:** Execute task-by-task with the available multi-agent tools; superpowers execution sub-skills are not installed. Preserve existing dirty work. Android owns local build outputs, the iOS test worker owns narrow contract/fixture corrections, and the parent owns desktop packaging/integration. Do not publish or replace release assets in this stage.

**Goal:** Produce traceable Beta 2 packages for Android, Windows and Linux and resolve reproducible verification failures without claiming unperformed device acceptance.

**Architecture:** Verify existing source changes and native artifact metadata first. Use the canonical checkout for source; mirror only reviewed owned changes after before-hash checks. Build Android locally, desktop packages from an explicit source snapshot on a separate codex branch through the existing release workflow with publish=false. Retain version, revision, artifact digests and test results in a delivery receipt.

**Tech Stack:** Gradle/Kotlin, Swift source and executable fixtures, Rust/Tauri, Node/browser tests, GitHub Actions, Python archive/payload checks.

## 1. Baseline and independent verification

- [x] Save canonical branch, HEAD, porcelain status, real-index hash and owned-file baselines under `.codex-tmp/beta2-delivery-20261015`; do not reset, stash or stage the real index.
- [x] Run `python tools/release/check-versions.py`, `python tools/release/check-libxray.py`, and relevant pinned payload checks; require Beta 2 and actual artifact hashes, not filenames alone.
- [x] Reproduce the historical iOS source-contract failures and correct outdated expectations only when actual production behavior still satisfies the intended invariant. Keep genuine failures visible. Run Foundation executable fixtures with the installed WSL Swift toolchain where supported.
- [x] Run `gradlew.bat :shared:desktopTest --no-daemon --max-workers=2`; reproduce and investigate the historical notification failure without silently excluding it.

## 2. Android

- [x] Verify the current combined native AAR against metadata before packaging.
- [x] Run `gradlew.bat :app:testDebugUnitTest :app:assembleRelease --no-daemon --max-workers=2` with the installed Android Studio JBR/SDK. Do not print signing secrets or substitute debug signing.
- [x] Verify package name, versionName/versionCode, ABI contents, native payload/notices, ZIP integrity and signatures. If release credentials are unavailable, retain explicitly unsigned release APKs and report the signing limitation.
- [x] Copy only verified outputs plus SHA-256 sidecars and a receipt to `artifacts/android/beta2-20261015`.

## 3. Desktop source snapshot and CI

- [x] Run UI production build, Node tests and browser regression suites in `apps/ui`; run focused production Rust tests/checks without changing host VPN, DNS, firewall or routing.
- [x] Create an explicit reviewed source snapshot with a temporary Git index and `git commit-tree`; preserve current checkout HEAD, real index and unrelated vendored license bytes. Exclude credentials, scratch, build outputs and third-party generated binaries that CI regenerates.
- [x] Push only a new `codex/beta2-delivery-20261015` branch and dispatch `.github/workflows/release.yml` with tag `v1.3.0-beta.2`, platform `all`, publish `false`. No tag creation, public release or main-branch changes.
- [x] Inspect actual workflow outcomes; repair concrete build/package failures in owned build infrastructure with regressions where appropriate. Do not weaken payload, helper installation or native safety gates.

## 4. Artifact verification and truthful handoff

- [x] Download Windows x64/x86/ARM64 installers and Linux x64/ARM64 AppImage/DEB/RPM plus the x64 custom installer. Verify GitHub artifact digests before extraction and preserve receipts.
- [x] Verify exact versions, core resources/notices, platform architectures, checksums and existing Linux helper installation gates. Record any platform that did not actually pass.
- [x] Reconfirm the existing iOS Beta 2 IPA provenance; if runtime source changes are required, rebuild it rather than claiming the old IPA contains the fix.
- [x] Inspect scoped diffs, mirror only reviewed owned files after before-hash checks, and verify the real Git index/current branch stayed unchanged.
- [x] Write a final platform matrix distinguishing source tests, package builds, installed-device/runtime acceptance and publication. iPhone signing/reconnect/Control Center/Dynamic Island and Android mobile-network acceptance require real device evidence; no synthetic pass.

## 5. Blocking iOS launch report received during execution

- [x] Compare the delivered xtool IPA with the previous Xcode IPA: ordinary resources match by membership; App Intents metadata is absent from the xtool package. The executable entry resolves to NimboApp. This packaging difference is not proof of the black-screen cause.
- [x] Start a conventional Xcode Beta 2 comparison build from the same runtime source; retain the experimental xtool artifact, but do not present it as device-validated.
- [x] Move optional shortcut-index refresh out of synchronous App initialization into a once-only background actor after the root task begins. Skip refresh with an explicit diagnostic if compiled App Intents metadata is missing; do not invent metadata or disable VPN validation.
- [x] Add a regression requiring App Intents metadata in distributable IPA verification, and make the existing xtool IPA fail that new completeness check. Preserve the historical build receipt rather than rewriting it as a new pass.
- [x] Rebuild the updated application with standard Xcode, verify the delivered metadata/versions/entitlements, and keep physical-device launch confirmation outstanding until actually observed.


## Verification checkpoint

- Android: 664/664 unit tests; three release APKs (code19) verified with native files, notice inventory, ZIP/ELF alignment, manifest and SHA256. They are explicitly unsigned; no release credentials were configured and no debug key was substituted.
- Shared: 153/153 tests. The historical toast failure was an obsolete visible-text lookup; the actual accessible close button, callback and geometry assertions are now tested.
- Desktop: production UI build;136/136 Node tests;256 browser cases (18settings,80secondary,107polish,44traffic,7support);139 Rust unit tests plus2 real pinned Xray CLI loopback scenarios and1 real AWG userspace-CLI lifecycle scenario. The internal child fixture is not a standalone product test.
- iOS:165 source contracts pass without skips;19 portable Foundation executables passed,3 CryptoKit fixtures require macOS. The standard macOS workflow separately passed its Swift policies, actual-store startup regression and all165 source contracts before packaging.
- Release workflow38747213239 at62e4b0a completed successfully on Windows x64/x86/ARM64 and Linux x64/ARM64. All10 desktop packages were downloaded with immutable GitHub ZIP digests verified; local package SHA/header/version checks passed. The six Linux normalized package digests match the CI helper payload reports. Windows171 installer-layout cases and16 rollback tests passed; Linux helper install/upgrade/restart/uninstall passed on both disposable architectures.
- Standard Xcode replacement IPA38749716891 at7695a0b completed successfully: build181, SHA256 ee64e76071f2e5b71db90b1df34112c23174315a01ec6e9d9600ad2296272ff8. All four bundle versions/arm64 platform/minimum OS/entitlements, real AppIntents metadata (3 app actions plus Control Center toggle), fonts, icons/catalogs, notices and exact mobile geodata were verified. The IPA was shared for real-device testing and still requires re-signing. Earlier comparison38747294386 was cancelled; the historical xtool artifact remains retained with a known-issues notice and fails the new metadata completeness gate.
- Native acceptance38749895808: Linux x64/ARM64 passed. Windows attempt1 timed out receiving DNS in Both case3, before SCM stop; attempt2 on identical runtime source passed the entire gate. This is observed intermittency, not a claimed production fix. The reviewed test-only diagnostic patch4dbc0b0 adds pre-cleanup retained-session/ownership-safe proxy capture, bounded responder/WFP identities and independent cleanup error aggregation;13 pure Python tests and12 fake-backend Rust tests pass, and the Windows driver compiles locally without execution. Full diagnostic VM run38757348691 passed Windows and Linux x64/ARM64, including the original full Windows Both cases0–4. This adds passing evidence but does not identify or fix the earlier intermittent DNS cause. No production network policy was changed.
- The supplied crash log is build180 captured9October. Its store-lock/SwiftUI-notification inversion was reproduced with an injected observable-defaults seam, then the actual store regression passed after removing defaults writes from the record critical section. This does not establish the black-screen cause of build181 on the user's phone.
- No physical-device acceptance, release publication, tag creation or user-host VPN/firewall/proxy changes were performed. Original checkout HEAD, branch, index and unrelated frozen-license edit remain unchanged.

## Delivery handoff

- The local matrix is `artifacts/beta2-delivery-20261015/delivery-matrix.json`; individual platform folders contain package SHA256 sidecars and immutable-artifact/verification receipts. It separates build/source gates, device acceptance and publication.
- Fourteen package files are available:3 unsigned Android APKs,1 re-signable standard-Xcode IPA and10 Windows/Linux packages. None was published or substituted into a public release.
- Remaining device acceptance: iPhone launch/subscription update/repeated connect-disconnect with each core and the real signer; Control Center/Dynamic Island; signed Android install/update and mobile-network VLESS reconnect.
- Windows intermittent native DNS remains an explicitly recorded open observation despite later passing runs. No unqualified all-platform stability claim is made.
