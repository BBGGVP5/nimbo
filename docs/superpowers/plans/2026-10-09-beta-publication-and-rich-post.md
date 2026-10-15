# Beta publication and Rich topic post Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Publish the verified Beta 1 GitHub prerelease and send one Telegram Rich Message in the human-confirmed topic, using the selected local poster and colored/custom-emoji buttons.

**Architecture:** Publication is now explicitly authorized by the user. Preserve all 28 uploaded assets and the desktop/iOS compiled source target. Telegram uses official Bot API `sendRichMessage`, not a photo caption or plain message; use verified recipient/credential and real custom emoji IDs, never guess a forum topic or downgrade silently. Do not expose tokens in logs or repository files.

**Tech Stack:** GitHub CLI, official Telegram Bot API rich HTML/media/button schema, local poster and release metadata.

- [x] Revalidate published-asset hashes and complete 28-asset manifest before changing draft state. Publish `v1.3.0-beta.1` with prerelease true and latest false; verify release state, resolved tag commit and unchanged asset IDs/digests.
- [x] Complete Android artifact report/release notes and mark Beta 1 published on 9 October 2026 in current docs only. Ownership-check/mirror explicit documentation edits and push; no PR merge or binary retargeting.
- [x] Resolve prior Telegram publisher credentials and the exact target topic from trusted user evidence. Verify bot permissions and real premium/custom emoji IDs with read-only calls. Missing recipient/token requires user input, not another bot account or guessed destination.
- [x] Prepare one rich HTML post with selected second poster, concise core/design/Mihomo/AWG/ad-blocking/statistics improvements, relevant beta limitations, full platform download table and colored primary/success buttons with custom emoji. Validate links against the published release and allowed rich schema; do not pay for broadcasts or spam test messages.
- [x] Send once after recipient and credentials are verified; store receipt without secrets. A network-ambiguous send must not be retried blindly. Verify returned rich_message, topic, buttons/colors/custom emoji and message link.
- [x] Report GitHub and Telegram links separately; never claim posting if external access is unavailable.

## Results

GitHub prerelease published with 28 unchanged/verified assets and tag a1a16fa. Existing prior bot configuration and exact human topic link verified; no credentials printed or persisted in Git. `sendRichMessage` returned message 16052 in topic 6135 with photo, rich blocks, colored buttons and custom-emoji IDs. Keyboard adjusted in place to two green, two red and one blue button after user feedback. Table updated in place with seven custom-emoji entities (two headings/five platforms), retaining photo and keyboard. No paid broadcast, new topic, old post deletion, duplicate send, PR merge or physical-device acceptance claim.
