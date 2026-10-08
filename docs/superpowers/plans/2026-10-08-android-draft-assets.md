# Android draft assets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add the user's existing signed Android Beta 1 APKs and SHA-256 sidecars to the current draft without rebuilding Android or publishing the release.

**Architecture:** Inspect the three release APKs under primary `app/release`, snapshot their bytes to ignored release staging, and upload only verified files. Preserve the existing draft, compiled desktop/iOS target and all existing release asset identities. Android APK source revision is not independently attested; report artifact checks rather than claim a fresh rebuild.

**Tech Stack:** Android SDK aapt2/apksigner, Python zip/hash checks, GitHub CLI.

- [x] Validate package `com.danila.nimbo`, version `1.3.0-beta.1`, versionCode 18, minSdk 29, targetSdk 37, non-debuggable manifest, correct ABI variants and one common non-debug signing certificate. Require `apksigner verify` exit 0 for every APK.
- [x] Check ZIP integrity, native library entries and manifest/classes resources. Snapshot the three files into ignored `.codex-tmp/android-draft-assets-20261008`, verify unchanged source SHA-256, and create standard `.apk.sha256` sidecars. Keep user originals untouched.
- [x] Read the existing draft and refuse changes if it is published or asset names conflict. Upload only the three APKs and their three sidecars without clobbering other assets.
- [x] Verify GitHub digests/sizes match local files; all 22 existing asset IDs/digests/sizes and compiled target `a1a16fa` remain unchanged; draft/prerelease remain true.
- [x] Add a concise Android download/ABI selection section to `docs/releases/1.3.0-beta.1.md`, record signed user-built artifact validation in `docs/build/android-draft-assets-2026-10-08.md`, mirror owned doc changes, commit/push explicit paths and update draft notes only.
- [x] Deliver the current draft URL and confirm three APKs plus checksums are attached; do not publish or claim device/runtime acceptance.

## Result and later authorization

Three release-signed user APKs and their checksum files were added; all six GitHub digests matched. Package/version/ABI/signature/ZIP checks passed. On 9 October 2026 the user explicitly authorized publication: the verified draft was published as a prerelease, retaining all prior assets and target a1a16fa. See `2026-10-09-beta-publication-and-rich-post.md`; the original no-publication scope was superseded only by that human request.
