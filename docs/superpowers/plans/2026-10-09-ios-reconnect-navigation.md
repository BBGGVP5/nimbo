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
