# Server selection and contextual actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clear accent outline for selected servers; hold opens actions without selecting, permanent server dots/ping buttons disappear, single-server ping moves into that menu.

**Architecture:** Android/shared Compose use `combinedClickable` and read-only latency; native SwiftUI uses `contextMenu`. React uses a common portal/hold wrapper. Keep existing single-ID callbacks and subscription-wide controls untouched.

**Tech stack:** Compose, SwiftUI, React/TypeScript, Node tests, Gradle, macOS IPA CI.

- [x] Android `UniversalPrimitives.kt`/`NimboMiniApp.kt`: add `onOpenMenu`, 2dp selected accent outline and whole-row hold; remove only server dots; menu ping calls `onPing()` and toggles cancellation while pinging without erasing the last completed result. Update `MihomoProxiesScreen.kt` with per-member hold menu invoking `measureOnly(member)`. Legacy `ProfileServersScreen.kt` retains hold and gets the same ping action/border.
- [x] Shared `NimboProfilesScreen.kt`: keyed menu state, whole-row hold including selected rows, read-only ping, menu ping calls `onPing(server.id)`. Native `ProfilesContainerView.swift` context menu posts `.nimboPingServer` with this ID only; preserve its 2pt outline and provide accessibility action.
- [x] Desktop: common `ServerContextMenu.tsx` and hold gesture (500ms, cancel on >8px movement/pointer cancel, suppress click after hold, reset on next down). Right-click and Shift+F10 open the same actions. Portal uses existing `menuPosition`; Escape/arrows/outside dismissal restore focus. Wrap SignalProfiles, ServerLine and ServerRow, remove only server dots, keep per-ID ping/rename/hide/favorite. Use a 2px accent outline without layout shifts.
- [x] Test short click/hold/suppression/movement/cancel/repeated gesture, full frontend tests/build, browser real UI right-click/Shift+F10/per-ID action; hold timing is covered by unit tests, not browser injection. Android/shared Kotlin compile/tests; iOS source contracts; full macOS IPA build remains the separate unchecked gate below. Mirror only edited native sources after baseline comparison; explicit Git staging/push to PR78.
- [x] Independently finish pending Linux package/helper-install checks; update plan/readiness/artifact links with actual results.

### Cancellation follow-up
- [x] MainViewModel repeat actions cancel by active row key; automated silent batches do not act as stop buttons. Android TCP uses an owned socket closed by coroutine cancellation; two tests prove pending cancellation and resource release.
- [x] Shared/iOS retain completed cache values while pending. RootView tracks user tasks by UUID, waits for diagnostic retirement and rejects late writes/cleanup. Native single-server action carries only that ID.
- [x] Desktop deduplicated bounded queue, one shared operation owner across profile cards, cancellation barrier before a replacement, discard stale replies, no eager cache clearing; Rust TCP/ICMP pending futures wake on explicit cancellation. Subscription ping buttons stay enabled to cancel, refresh semantics unchanged.
- [x] Node gesture/cancellation tests and browser production component check: right click did not select, menu checked only fixture-1, Shift+F10 displayed Stop ping, repeat action cancelled without changing selection.
- [ ] macOS full IPA compilation of this source revision; retain old artifact links as historical until the new run completes.
