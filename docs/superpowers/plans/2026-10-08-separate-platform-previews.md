# Separate platform previews Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Display six readable, separate device previews inline in the repository and existing GitHub beta draft, without publishing it or replacing compiled assets.

**Architecture:** Reuse sanitized phone screens and the monitor/phone composition. Capture each device independently at 3× scale. Following the user's monitor feedback, rerender desktop pages at 16:9 and replace the old square display with a thin-bezel monitor and slim stand; refresh combined views too. Embed platform sections rather than requiring users to open raw image links. Preserve provenance and the draft's binary commit target.

**Tech Stack:** Playwright, HTML device frames, Markdown, Python asset checks and GitHub CLI.

- [x] Update `tools/previews/check_release_gallery.py` to require six separate device PNGs in a 16-asset manifest and inline entries in `docs/previews/README.md`. Run `python tools/previews/check_release_gallery.py`; expect failure before the new assets exist.
- [x] Add `--separate` to `tools/previews/capture-device-showcase.mjs`: use deviceScaleFactor 3 and screenshot `.monitor`, `.phone:nth-child(2)`, `.phone:nth-child(3)` for Home and Settings, naming output `desktop-home-device.png`, `desktop-mihomo-device.png`, `android-home-device.png`, `android-settings-device.png`, `ios-home-device.png`, `ios-settings-device.png`. Do not overwrite combined images in this mode.
- [x] Run `node tools/previews/capture-device-showcase.mjs --separate` with `NIMBO_CHROMIUM_PATH` pointing to installed Chrome; inspect all platforms. Update only the new manifest rows and require PNG dimensions/hashes and unclipped device bounds to pass.
- [x] Embed separate Desktop/Android/iOS sections in `docs/previews/README.md` and `README.md`, keeping existing combined views. Commit/push assets first; embed immutable raw asset URLs from that commit in `docs/releases/1.3.0-beta.1.md`.
- [x] Ownership-check and mirror only modified/new files to the primary workspace. Preserve all other dirty/untracked files. Commit/push documentation and verify the remote commit.
- [x] Edit notes of the existing `v1.3.0-beta.1` draft only. Verify `isDraft`, `isPrerelease`, `targetCommitish` and all 22 compiled asset IDs/sizes/digests unchanged; image URLs must return HTTP 200. Deliver the verified draft and gallery links.

## Results

- Six independent 3× device images embedded inline, not just linked.
- Desktop pages are genuine 1600×900 production-component renders; monitor is 16:9 with slim bezel/stand. Existing combined compositions refreshed after user feedback.
- 107 browser cases passed; 16 asset hashes/dimensions and two capture modes passed. Existing mobile source/provenance retained.
- GitHub sanitized Markdown contains all six separate image URLs; all returned HTTP 200. Draft/prerelease and compiled target a1a16fa preserved, 22 compiled assets unchanged. No release publication or PR merge.
