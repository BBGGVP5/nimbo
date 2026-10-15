# Beta 2 Platform Delivery Implementation Plan

> **For agentic workers:** Execute task-by-task with the available multi-agent tools; superpowers execution sub-skills are not installed. Preserve existing dirty work. Android owns local build outputs, the iOS test worker owns narrow contract/fixture corrections, and the parent owns desktop packaging/integration. Do not publish or replace release assets in this stage.

**Goal:** Produce traceable Beta 2 packages for Android, Windows and Linux and resolve reproducible verification failures without claiming unperformed device acceptance.

**Architecture:** Verify existing source changes and native artifact metadata first. Use the canonical checkout for source; mirror only reviewed owned changes after before-hash checks. Build Android locally, desktop packages from an explicit source snapshot on a separate codex branch through the existing release workflow with publish=false. Retain version, revision, artifact digests and test results in a delivery receipt.

**Tech Stack:** Gradle/Kotlin, Swift source and executable fixtures, Rust/Tauri, Node/browser tests, GitHub Actions, Python archive/payload checks.

## 1. Baseline and independent verification

- [ ] Save canonical branch, HEAD, porcelain status, real-index hash and owned-file baselines under `.codex-tmp/beta2-delivery-20261015`; do not reset, stash or stage the real index.
- [ ] Run `python tools/release/check-versions.py`, `python tools/release/check-libxray.py`, and relevant pinned payload checks; require Beta 2 and actual artifact hashes, not filenames alone.
- [ ] Reproduce the historical iOS source-contract failures and correct outdated expectations only when actual production behavior still satisfies the intended invariant. Keep genuine failures visible. Run Foundation executable fixtures with the installed WSL Swift toolchain where supported.
- [ ] Run `gradlew.bat :shared:desktopTest --no-daemon --max-workers=2`; reproduce and investigate the historical notification failure without silently excluding it.

## 2. Android

- [ ] Verify the current combined native AAR against metadata before packaging.
- [ ] Run `gradlew.bat :app:testDebugUnitTest :app:assembleRelease --no-daemon --max-workers=2` with the installed Android Studio JBR/SDK. Do not print signing secrets or substitute debug signing.
- [ ] Verify package name, versionName/versionCode, ABI contents, native payload/notices, ZIP integrity and signatures. If release credentials are unavailable, retain explicitly unsigned release APKs and report the signing limitation.
- [ ] Copy only verified outputs plus SHA-256 sidecars and a receipt to `artifacts/android/beta2-20261015`.

## 3. Desktop source snapshot and CI

- [ ] Run UI production build, Node tests and browser regression suites in `apps/ui`; run focused production Rust tests/checks without changing host VPN, DNS, firewall or routing.
- [ ] Create an explicit reviewed source snapshot with a temporary Git index and `git commit-tree`; preserve current checkout HEAD, real index and unrelated vendored license bytes. Exclude credentials, scratch, build outputs and third-party generated binaries that CI regenerates.
- [ ] Push only a new `codex/beta2-delivery-20261015` branch and dispatch `.github/workflows/release.yml` with tag `v1.3.0-beta.2`, platform `all`, publish `false`. No tag creation, public release or main-branch changes.
- [ ] Inspect actual workflow outcomes; repair concrete build/package failures in owned build infrastructure with regressions where appropriate. Do not weaken payload, helper installation or native safety gates.

## 4. Artifact verification and truthful handoff

- [ ] Download Windows x64/x86/ARM64 installers and Linux x64/ARM64 AppImage/DEB/RPM plus the x64 custom installer. Verify GitHub artifact digests before extraction and preserve receipts.
- [ ] Verify exact versions, core resources/notices, platform architectures, checksums and existing Linux helper installation gates. Record any platform that did not actually pass.
- [ ] Reconfirm the existing iOS Beta 2 IPA provenance; if runtime source changes are required, rebuild it rather than claiming the old IPA contains the fix.
- [ ] Inspect scoped diffs, mirror only reviewed owned files after before-hash checks, and verify the real Git index/current branch stayed unchanged.
- [ ] Write a final platform matrix distinguishing source tests, package builds, installed-device/runtime acceptance and publication. iPhone signing/reconnect/Control Center/Dynamic Island and Android mobile-network acceptance require real device evidence; no synthetic pass.
