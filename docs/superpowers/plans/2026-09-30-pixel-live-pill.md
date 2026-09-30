# Pixel Live Update and platform delivery Implementation Plan

> **For agentic workers:** Execute inline task-by-task in this session. Unavailable execution skills do not block the normal file/build tools.

**Goal:** Make Android 17 Pixel's native VPN status pill user-controllable, explain actual system eligibility, represent recovery accurately, and deliver existing cross-platform source updates and an iOS CI build without claiming unimplemented tunnels work.

**Architecture:** Reuse the sole VPN foreground notification and its existing channel/ID/actions. A pure Kotlin policy maps authoritative service state to bounded short text; Android helpers query system permission/channel state and open system settings only on user click. The app never draws an overlay over the clock, overrides a muted channel, or installs on the phone. Notification preview animation runs only while its settings section is visible, once per user click.

**Tech Stack:** Kotlin/Compose, AndroidX NotificationCompat, compileSdk 37, Gradle, Python verification, existing GitHub/Xcode CI.

### Task 1: Pure pill policy
**Files:** `app/src/main/java/com/danila/nimbo/utils/VpnLiveUpdate.kt`, `app/src/test/java/com/danila/nimbo/utils/VpnLiveUpdateTest.kt`.
- [x] Add enum `VpnPillState { CONNECTING, CONNECTED, WAITING_NETWORK, RECOVERING, PAUSED, ATTENTION }`.
- [x] Test `vpnPillText(state,seconds,english)` for every state/localization, text length <=7, CONNECTED's six-second boundary, negative durations, and no private names/links.
- [x] Implement localized fixed vocabulary (`РџРѕРґРєР».`/`Connect`, `VPN`, `РЎРµС‚СЊ`/`Network`, `РџРѕРІС‚РѕСЂ`/`Retry`, `РџР°СѓР·Р°`/`Paused`, `!`) with icon-only settled connection.

### Task 2: System eligibility and existing notification
**Files:** create `utils/VpnLiveUpdatePlatform.kt`, modify `utils/PreferencesManager.kt`, `utils/NotificationManager.kt`, `vpn/MyVpnService.kt`.
- [x] Add persisted default-on `vpnLiveUpdateEnabled`. Apply switch immediately through a service-owned SharedPreferences listener; unregister on destroy. Never launch VPN from settings.
- [x] Query app notification permission, channel NONE/MIN, and `canPostPromotedNotifications()` on supported systems. LOW remains allowed; never delete/recreate or increase user channel importance. API exceptions become UNKNOWN, not false proof of success.
- [x] Add system navigation intent `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` with package extra, falling back to app notification settings if unavailable. No full-screen intent, notification listener, or overlay request.
- [x] Pass explicit recovery state from the service into the builder, so traffic-budget status isn't labeled reconnecting. Preserve single ID, BigTextStyle, private visibility, disconnect/pause actions and showWhen(false).
- [x] Add redacted diagnostic eligibility fields (fixed vocabulary only; no node identifiers).

### Task 3: Settings and preview
**Files:** create `ui/screens/VpnLiveUpdateSettings.kt`; modify `ui/screens/NotificationHistoryScreen.kt`.
- [x] Add visible default-on pill switch and eligibility/system-settings row in Notifications settings. Refresh state on ON_RESUME.
- [x] Show cloud plus brief Connecting/VPN text in a compact animated capsule preview; settle to icon and offer replay. Respect app/OS reduced-motion; no perpetual timers.
- [x] Run `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest --no-daemon --console=plain` with Studio JBR. Verify APK signature and arm64 compiled metadata. Do not install.

### Task 4: Platform delivery
**Files:** existing isolated checkout `.codex-tmp/github-ios-build-20260928`, existing iOS CI workflow and source contracts.
- [x] Sync only reviewed task changes into the isolated checkout, preserving unrelated/untracked files. Check Android native artifact download/build plumbing before updating remote.
- [ ] Run Apple source/build/ping/navigation contracts with updated pins and desktop tests for transferred visual changes.
- [ ] Push reviewed source branch, attach created PR, dispatch existing unsigned IPA workflow. Report actual CI outcome, not source checks as an IPA build.
- [ ] Keep remaining full iOS Mihomo Network Extension ownership/all-protocol device testing explicit; no admission bypass or website false support claims.

## Evidence
Android official Live Updates requirements: BigTextStyle allowed, non-runtime POST_PROMOTED_NOTIFICATIONS and requested promotion, ongoing/title, no custom views/group summary/colorized true, channel must not be MIN. Native size, animation, promotion and icon-only presentation are controlled by SystemUI. User confirmed Android 17 Pixel.

## Scope extension: all supported systems (user clarification)
- Android: use capability/API checks, never restrict by Pixel/model or brand. Older unsupported systems keep the normal foreground notification.
- iOS/iPadOS: add a separate WidgetKit extension (minimum iOS 16.2, the ActivityContent stale-date API baseline), ActivityAttributes shared with the app, and one app-owned ActivityKit coordinator. Use observed NE status, not connection intent; end on disconnect/off and prevent duplicate/stale async requests. No node names/keys/URLs in activity content. Compact connected presentation is cloud-only; connecting/recovery have short labels. System controls expansion/collapse. Query Live Activity authorization and expose the switch on Notifications.
- Local iOS updates cannot be guaranteed after app suspension/termination without APNs. Set a bounded stale date and render stale status honestly; never add a background keepalive, fake connection, or a second Go runtime to the widget.
- Desktop: retain tray/status notification where the OS has no Android/iOS native island API. Do not fake a system pill overlay. Apple may mirror iPhone Live Activities to supported Macs; that is OS-managed, not a Windows/Linux feature.
- Tests: pure Swift policy tests on macOS CI + source/packaging contracts on Windows, real Xcode IPA build. Keep device/UI checks explicitly pending.

## Verification (2026-09-30)
Android debug assembly and 663 unit/host tests passed, no skipped tests. arm64 APK signature v2 verified; SHA256 58633dd0ba42aa4beead469243d5ffec76ce9612456e865238631e4095841e10. Native desktop helper rebuilt and staged from verified source; 47,939,072 bytes, SHA256 5195dce5c76fe3cc39975b72d4815b7d1b2730e6aaf4002b72811781de6f3255. Desktop frontend tests/build and three Xray pin/cache tests passed. iOS ActivityKit requires 16.2 for explicit stale-date safety; no APNs or background keepalive. iOS Xcode build and device/system-UI verification remain pending.
