# Compact beta presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore logo-only GitHub presentation, compact six platform previews, extend verified user notes, and retain the chosen second poster locally only.

**Architecture:** Preserve all UI pixels and compiled release assets. Use a three-platform/two-row thumbnail table (desktop 300 px, phones 180 px) with links to the full PNGs. Existing universal poster has exactly the same pixels as the user's selected second image; copy it to local ignored release-media before removing only the three newly-created poster files from the Git tree. Leave historical poster assets untouched.

**Tech Stack:** Markdown, Python asset checks, Git/GitHub CLI; no image regeneration or runtime changes.

- [ ] Update `tools/previews/check_release_gallery.py` to expect fourteen preview PNGs, compact thumbnail widths and the normal icon header in `README.md` / `docs/releases/1.3.0-beta.1.md`; prohibit poster references in active GitHub-facing docs. Run it before changes and require failure.
- [ ] Preserve selected image bytes at primary `.codex-tmp/release-media-20261008/nimbo-poster.png`. Back up and remove only `docs/poster/nimbo-poster-orbit-2026.png`, `docs/poster/nimbo-poster-universal-2026.png`, and the newly-created `docs/poster/README.md` from the Git tree; preserve historical `nimbo-poster.png` and `nimbo-poster-en.png`.
- [ ] Update `README.md`, `docs/previews/README.md`, and `docs/releases/1.3.0-beta.1.md` with six linked thumbnails, avoiding repeated giant combined compositions. Remove poster rows from `docs/previews/1.3.0-beta.1/manifest.json`, retaining every preview hash and renderer note.
- [ ] Extend only the Beta 1 section of `CHANGELOG_NIMBO.md` and release notes: network transport selection/trusted SSIDs/manual pause/retry handling, Live Activity toggle and Android permission feedback, bounded full-profile URL import and soft memory budget. Claims must match inspected source and commits; do not present old Android network-profile features as newly added.
- [ ] Verify all fourteen hashes, six inline previews and local Markdown links; render draft notes through GitHub Markdown and check widths/logo/no poster. Preserve compiled source `a1a16fa` and all 22 binary asset identities/digests.
- [ ] Ownership-check and mirror only owned updates/removals to primary (local selected-poster deliverable remains). Commit/push explicit paths, update the existing draft notes without publishing/retargeting, and verify remote state.
