# Sync selector and settings deduplication implementation plan

> **For agentic workers:** Execute the scoped tasks below in this session; preserve unrelated edits.

**Goal:** Show Wi-Fi, Bluetooth and Auto as three equal icon-over-label choices; remove settings links duplicating primary navigation.

**Architecture:** A controlled Compose selector receives the existing transport string and emits the same persisted keys. Settings retain configuration controls but omit shortcuts already owned by the bottom navigation. Shared settings also stop repeating Activity, Routing and Diagnostics inside General.

**Tech Stack:** Kotlin/Compose, Python source contracts, Android unit/host tests.

### Task 1: regression contracts
- [x] Create `scripts/ci/test-sync-settings-layout.py`; assert `SyncTransportSelector` uses a single `Row`, `weight(1f)`, radio semantics and icon-before-label. Verify the screen still writes `preferencesManager.crossSyncTransportMode` and primary settings do not contain `onConnectionsClick` or the My subscriptions row. Run `python scripts/ci/test-sync-settings-layout.py` and observe failure before implementation.

### Task 2: UI and navigation
- [x] Add `app/src/main/java/com/danila/nimbo/ui/screens/SyncTransportSelector.kt` with keys `wifi`, `bluetooth`, `both`; use `Icons.Default.Wifi`, `Bluetooth`, `Sync`, labels Wi-Fi / Bluetooth / localized Auto. Put the icon above text; equal-width radio cards have intrinsic equal height, minimum 88dp, wrapping labels and a visible selected border.
- [x] Replace the three stacked buttons in `CrossPlatformSyncScreen.kt` with `SyncTransportSelector(transportMode) { transportMode = it; preferencesManager.crossSyncTransportMode = it }`.
- [x] In `NimboMiniApp.kt` remove My subscriptions / Activity settings shortcuts and the now-unused Activity callback. Keep subscription refresh, server configuration and statistics configuration. In shared `NimboSettingsScreen.kt` remove repeated Activity history / Routing / Diagnostics rows from General, leaving their canonical entry points.

### Task 3: verification and source delivery
- [x] Update `shared/src/desktopTest/kotlin/com/danila/nimbo/shared/ui/NimboSettingsRedesignInteractionTest.kt`: replace the removed History shortcut contract with a real rendered General screen, assert no duplicated History / Routing / Diagnostics, then click bottom-bar Activity and assert the real stats screen opens with no bridge callbacks or VPN/preferences mutation.
- [x] Run `python scripts/ci/test-sync-settings-layout.py`, `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest :shared:desktopTest`; verify XML failures/errors are zero. Sync only these files to the existing Git checkout and commit/push after `git diff --check`. Do not claim device screenshot verification.

Verification: Android debug assembled; Android 589 + Android shared host 76 + desktop shared 118 tests passed with zero failures/errors/skips. Three source contracts passed. The rendered 320x480 / textScale1.25 shared General screen was inspected, and real bottom-bar Activity navigation was exercised. Android selector device screenshot verification was not performed. Sources are published in the existing PR; main is unchanged.
