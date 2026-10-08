# Compact beta presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Restore logo-only GitHub presentation, compact six platform previews, extend verified user notes, and retain the chosen second poster locally only.

**Architecture:** Preserve all UI pixels and compiled release assets. Use a three-platform/two-row thumbnail table (desktop 300 px, phones 180 px) with links to the full PNGs. Existing universal poster has exactly the same pixels as the user's selected second image; copy it to local ignored release-media before removing only the three newly-created poster files from the Git tree. Leave historical poster assets untouched.

**Tech Stack:** Markdown, Python asset checks, Git/GitHub CLI; no image regeneration or runtime changes.

- [x] Update `tools/previews/check_release_gallery.py` to expect fourteen preview PNGs, compact thumbnail widths and the normal icon header in `README.md` / `docs/releases/1.3.0-beta.1.md`; prohibit poster references in active GitHub-facing docs. Run it before changes and require failure.
- [x] Preserve selected image bytes at primary `.codex-tmp/release-media-20261008/nimbo-poster.png`. Back up and remove only `docs/poster/nimbo-poster-orbit-2026.png`, `docs/poster/nimbo-poster-universal-2026.png`, and the newly-created `docs/poster/README.md` from the Git tree; preserve historical `nimbo-poster.png` and `nimbo-poster-en.png`.
- [x] Update `README.md`, `docs/previews/README.md`, and `docs/releases/1.3.0-beta.1.md` with six linked thumbnails, avoiding repeated giant combined compositions. Remove poster rows from `docs/previews/1.3.0-beta.1/manifest.json`, retaining every preview hash and renderer note.
- [x] Extend only the Beta 1 section of `CHANGELOG_NIMBO.md` and release notes: network transport selection/trusted SSIDs/manual pause/retry handling, Live Activity toggle and Android permission feedback, bounded full-profile URL import and soft memory budget. Claims must match inspected source and commits; do not present old Android network-profile features as newly added.
- [x] Include the user's follow-up request for a dedicated VPN-core rework section: Nimbo session lifecycle, cancellation, full-profile handling, helper/TUN integration and mobile memory work. Explain scope without claiming upstream Xray/Mihomo were rewritten from scratch.
- [x] Verify all fourteen hashes, six inline previews and local Markdown links; render draft notes through GitHub Markdown and check widths/logo/no poster. Preserve compiled source `a1a16fa` and all 22 binary asset identities/digests.
- [x] Ownership-check and mirror only owned updates/removals to primary (local selected-poster deliverable remains). Commit/push explicit paths, update the existing draft notes without publishing/retargeting, and verify remote state.

## Results

- Selected second poster pixel-identical to the existing universal image; local selected file SHA-256 85ed79cc62530a23181d664bef239ec7ab85fd8531c6e4aa6b83ccc9e9ee191c. No image regeneration.
- Restored 104 px rounded application logo; desktop thumbnails 300 px, phone thumbnails 180 px, all clickable at full resolution. Removed giant duplicate views and active poster links.
- Three newly-created poster files removed from the Git tree only after local backups; historical poster files preserved. Fourteen preview asset hashes unchanged.
- Expanded verified feature notes and added a dedicated Nimbo VPN-core rework section, distinguishing integration work from upstream core rewrites. Historical changelog unchanged.
- GitHub Markdown verified six linked compact images and no poster. All seven logo/preview URLs return 200. Draft/prerelease retained, compiled target a1a16fa and all 22 binary assets unchanged.
