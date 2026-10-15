# Beta 1 publication — 2026-10-09 (Europe/Samara)

## GitHub

- Published prerelease: [Nimbo 1.3.0 Beta 1](https://github.com/BBGGVP5/nimbo/releases/tag/v1.3.0-beta.1).
- Published at `2026-10-08T20:05:42Z`, which is 9 October in Europe/Samara. `isDraft=false`, `isPrerelease=true`; not marked Latest.
- Tag resolves to desktop/iOS compiled source `a1a16fa80fe4390274927cb80b6b7e4db8ffc12f`. No PR merge or tag retargeting.
- 28 uploaded assets: 14 packages/APKs and 14 SHA-256 sidecars. Original 22 asset IDs/sizes/digests retained; the user's three signed Android APKs and three checksum files were added and their GitHub digests verified.
- Android artifact checks: [validation record](./android-draft-assets-2026-10-08.md). No Android rebuild or installation was performed.
- Compact gallery and ordinary rounded app logo remain in release notes. The selected poster is not added to the GitHub-facing presentation.

## Telegram

- Human-confirmed topic: `https://t.me/c/2561763286/6135`.
- Posted once with the established bot using official `sendRichMessage`: [release announcement](https://t.me/c/2561763286/6135/16052).
- Returned `message_id=16052`, matching topic `6135`; `rich_message` contains structured content and an embedded photo, not a photo caption or plain text post.
- Selected second poster SHA-256: `85ed79cc62530a23181d664bef239ec7ab85fd8531c6e4aa6b83ccc9e9ee191c`. Multipart rich media upload avoided republishing the poster in GitHub.
- Fourteen unique platform download links validated against published assets; HTTP HEAD succeeded. APK/IPA/Windows/Linux options and relevant beta limitations are included.
- Nine animated custom emoji IDs verified with `getCustomEmojiStickers`; five buttons retain custom-emoji icons. Telegram returned the color/style fields in the message.
- Following the user's table feedback, the same Rich message body was edited in place: two download-table headings and all five platform rows now contain custom-emoji entities. Telegram returned exactly seven custom-emoji entities in the table; the photo and colored keyboard were retained.
- Following the user's color feedback, the existing message keyboard was edited in place: main download and Android green, iPhone and Linux red, Windows blue. No duplicate post and no previous announcement deletion.
- No paid broadcast, token/keystore disclosure, or change to the bot's configuration.

## Evidence and limitations

Ignored local records hold artifact manifests, publication preflight/state checks, rich HTML/payload, send attempt marker and returned Telegram receipts. Bot credentials remain in the existing configuration and were never written to Git.

Artifact/source checks and successful publishing do not prove physical-device VPN acceptance, iOS provisioning, or a dependency/security audit. Existing platform limitations remain documented in the user release notes. GitHub reports dependency alerts on the default branch; this task did not validate or remediate those advisories.
