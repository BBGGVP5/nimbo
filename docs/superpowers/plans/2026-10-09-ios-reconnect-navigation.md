# iOS reconnect and navigation repair

## Scope
Fix the reported connect/disconnect/reconnect regression across Xray, AWG, Naive and Mihomo, first-launch presentation races, edge-back navigation and safe-area layout. Preserve unrelated working-tree files and current public beta assets.

## Evidence
- Controller incorrectly publishes idle after five seconds of system disconnecting.
- Launch migrates/stages VPN preferences and automatically presents readiness while import/update UI may also appear.
- Video ends with IOS_CORE_INCOMPATIBLE; this is a real admission error, not proof of a particular native deadlock. Never silently change the selected core.
- Pinned Xray Darwin TUN wraps a borrowed NE fd in os.File, but does not close that wrapper on iOS. Audit cancellation and finalizer ownership.

## Steps
1. Add regressions; guard profile writes/start by observed system state, retain stop timeout and support read-only restoration.
2. Repair owned duplicate TUN descriptor lifecycle in pinned Apple build with socketpair lifecycle tests; exercise repeat native starts for all four engines.
3. Remove automatic readiness presentation, add edge-only back for shared screens/dialogs and extend scrolling canvas behind safe areas without placing controls under system UI.
4. Run source contracts, Kotlin compilation/tests, Apple CI and native tests where available. Mirror only owned files with conflict checks.

## Validation boundaries
Windows cannot reproduce an iPhone NetworkExtension session. Host socketpair/C ABI and simulator build checks do not establish physical-device connectivity, signing or lifecycle correctness. Report the distinction and require device connect/stop/reconnect + force-reopen testing for each engine. Do not publish over the beta without a new request.

## Completed validation
- Runtime commits: d53a77b and 61675d2. Primary workspace mirrors all owned files; unrelated changes preserved.
- 105 Python iOS source-contract tests, 11 packet-flow checks and 143 Kotlin desktop-host tests passed. Pointer tests exercise production edge navigation; layout renders at 320/390/430 points were checked in light/dark themes.
- Windows merged Go tests passed for cgo_bridge, AWG, Mihomo and Naive (private-only tests skipped on this host).
- Apple CI run 37917500053, commit 61675d28ec992f017d780dd40a9ff5b791f4e8b1: success. Portable Swift admission/start policies, native contracts, device/ simulator bridge linking and final device IPA build passed.
- Darwin TUN socketpair test: twelve close/reopen/GC cycles with race detection; original fd stays usable and retired readers exit.
- Real combined-library C ABI: eight Xray/AWG cycles, eight Mihomo cycles, eight Naive HTTPS/QUIC cycles. Localhost fixtures only; not a remote-server or iPhone connectivity claim.
- IPA: Nimbo_v1.3.0-beta.1_ios_resignable.ipa, 76,236,456 bytes, SHA-256 f93b3a353f0e52693c49d28b39fe97bf6341e1cbaa5dac3bca2031e3d79fb7a6. ZIP CRC, app/extension native Naive symbols and NetworkExtension entitlements verified after download. Requires appropriate re-signing.
- No public beta asset replacement or Telegram announcement was performed.

## Device follow-up (not executed here)
On iPhone, repeat connect / stop / connect for VLESS/Xray, AWG, Naive and full Mihomo profiles; reopen the app during/after stop; verify actual traffic and state agree. Check edge-back, cancelled gestures, unsaved-editor confirmation, first import and portrait/landscape safe areas.
The video's IOS_CORE_INCOMPATIBLE remains an honest admission error for a mismatched explicit core choice. Launch restoration no longer triggers it merely by rewriting/validating next-start configuration. No automatic preference change or protocol fallback was introduced.
