# Release gallery and expanded user notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Deliver a reusable new-brand poster, honest desktop/Android/iOS design previews, and a complete user-facing Beta 1 changelog.

**Architecture:** Capture production UI with isolated demo data rather than publish private subscription screenshots. Desktop uses existing React browser fixtures; iOS uses the production shared Compose page renderer, explicitly labelled as a component preview rather than a device capture. Prefer a fresh Android emulator and the existing debug APK for native screenshots. Keep already-built release assets and the draft's compiled source target unchanged.

**Tech Stack:** image_gen, React/Vite/Playwright, Kotlin ImageComposeScene, Android SDK/adb, Markdown and GitHub CLI.

### Task 1: Poster
- [x] Copy the selected generated image to `docs/poster/nimbo-poster-universal-2026.png` without replacing the historical posters. Record the prompt and tool in `docs/poster/README.md`.
- [x] Inspect the result: exact current cloud, no version/beta/feature text, new architectural graphite backdrop, platform line inside margins. Verify PNG dimensions and SHA-256.

### Task 2: Actual UI previews
- [x] Add `shared/src/desktopTest/kotlin/com/danila/nimbo/shared/ui/NimboReleasePreviewTest.kt`, rendering Home and Settings through `NimboAppShell` at 390×844 with public demo values and no native/network operations.
- [x] Run `./gradlew :shared:desktopTest --tests '*NimboReleasePreviewTest'`; require visible semantic content and valid PNG signatures. Save iOS-shared page captures under `shared/build/reports/release-previews`.
- [x] Capture current desktop Home and Mihomo from the existing browser fixture using installed Chrome. Require no page errors, no horizontal overflow, no unexpected IPC, then visually inspect.
- [x] Use a dedicated Android AVD under ignored `.codex-tmp/release-preview-avd`, never a user's existing device/AVD. Install only the existing debug APK, do not start VPN, and capture Home/Settings if available. The SDK failed to boot; use subsequently supplied Android screenshots, sanitize private fields with image_gen, and preserve provenance in the manifest.
- [x] Copy verified captures to `docs/previews/1.3.0-beta.1/` and write `docs/previews/README.md` with renderer/source/sample-data provenance. Never call the shared Compose capture an iOS device screenshot.

- [x] Render two 2700×1650 device compositions, including standard status bars and NIMBO pills, with no edit annotations on the artwork. Verify all screen and device bounds.

### Task 3: Release prose
- [x] Expand `docs/releases/1.3.0-beta.1.md` and only the Beta 1 section in `CHANGELOG_NIMBO.md`: ad-domain filtering, traffic/session telemetry, routing, full profiles/backup, on-demand, native cores, platform recovery, and design/accessibility. Map claims to source changes; preserve limits (default-off/next connection/rule-mode/raw AWG limitations).
- [x] Add the poster and gallery links to `README.md` and beta notes. Validate every relative image path and inspect the Markdown/image hierarchy.

### Task 4: Delivery
- [x] Mirror only owned edits to the primary workspace after comparing existing files to the committed source baseline; preserve unrelated dirty files and historical notes.
- [x] Commit/push assets first, use their immutable raw GitHub URLs for draft release images, and commit/push documentation. Check the new links over HTTP.
- [x] Edit notes of the existing draft only. Compare `isDraft`, `isPrerelease`, `targetCommitish` and all compiled asset IDs/digests before/after; do not publish, retarget binaries, or merge PR 78.
- [x] Deliver the saved poster path and gallery; summarize added changelog sections and accurately label preview limitations.

## Results

- Selected orbital poster and quieter alternative saved without version/change text.
- Final gallery: 2700×1650 monitor/phone compositions, standard 9:41 status and NIMBO pill, no artwork edit labels.
- Android inputs were supplied by the user after isolated emulator attempts failed; private originals were not committed.
- Desktop browser: 107 passing cases. Shared Compose capture: one passing test/two pages. iOS traffic/ad-block source contracts: eight passing tests. Ten PNG SHA/dimension checks and two device-bound checks passed.
- GitHub draft notes updated and matched the source; all 22 compiled asset identities/digests and target a1a16fa remained unchanged. All three immutable image URLs returned HTTP 200. Draft was not published and PR 78 was not merged.
