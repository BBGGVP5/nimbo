# Platform-aware update center implementation plan

> **For Codex:** Execute this plan task-by-task, keeping Android and desktop behavior aligned and verifying every build before publishing.

**Goal:** Make Nimbo's in-app updater show only platform-relevant changes, present a polished themed download experience, enforce the selected update channel and Wi-Fi policy correctly, and notify users about background subscription refreshes.

**Architecture:** Release notes gain explicit Android and desktop sections, with defensive legacy sanitizers in both clients. Each updater exposes structured byte progress to its UI. Existing preferences remain the source of truth for channel, Wi-Fi, and subscription refresh behavior; background workers/timers read those preferences before acting and publish concise notifications. The existing signed release flow and same-version artifact fingerprint checks remain unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, WorkManager, OkHttp, JUnit; Rust, Tauri, React, TypeScript, CSS; Gradle, Cargo, npm, GitHub CLI.

---

### Task 1: Lock platform and channel behavior with tests

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Modify: `app/src/test/java/com/danila/nimbo/network/UpdateManagerTest.kt`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src-tauri/src/updater.rs`

1. Add failing tests for tagged Android/desktop release-note sections and legacy Markdown/HTML sanitization.
2. Add or extend tests proving Stable accepts only stable releases while Beta accepts both beta and stable releases.
3. Implement explicit highest-compatible-release selection and platform-specific note extraction.
4. Run the focused Android and Rust updater tests.

### Task 2: Expose reliable download progress

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src-tauri/src/updater.rs`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/lib/api.ts`

1. Add structured progress containing downloaded bytes, total bytes, percentage, and stage.
2. Update resumed-download paths so progress starts from the existing partial file and reaches verification cleanly.
3. Emit Tauri progress events during desktop download and verification.
4. Ensure error and completion paths reset progress without losing resumable files.

### Task 3: Redesign the Android update center and dialog

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/UpdateDialog.kt`

1. Replace the Stable/Beta segmented control with a themed dropdown.
2. Rebuild the Wi-Fi-only row with consistent spacing, clear copy, and an aligned switch.
3. Replace the thin progress bar with a themed progress card showing percentage and downloaded/total size.
4. Render only sanitized user-facing notes and improve the update popup hierarchy, platform label, size, and verification message.
5. Run Compose/Kotlin compilation and unit tests.

### Task 4: Redesign the desktop update center and dialog

**Files:**
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/pages/Settings.tsx`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/App.tsx`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/styles.css`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/lib/i18n.ts`

1. Subscribe to updater progress and display percentage plus downloaded/total size.
2. Replace the channel buttons with a native-accessible themed dropdown.
3. Build a responsive update card and popup using current Nimbo Glass/Material color variables.
4. Render release notes as headings and bullets without raw HTML.
5. Run TypeScript, frontend, and Rust tests/build checks.

### Task 5: Make subscription auto-refresh visible and dependable

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/service/SubscriptionUpdateWorker.kt`
- Modify: `app/src/main/java/com/danila/nimbo/service/SubscriptionUpdateScheduler.kt`
- Modify: `app/src/main/java/com/danila/nimbo/NebulaGuardApplication.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/NotificationManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/receiver/BootRestoreReceiver.kt`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/App.tsx`

1. Re-check the auto-update preference inside the Android worker and schedule it at app/boot entry points.
2. Count successful and failed refreshes and publish a dedicated, permission-safe notification.
3. On desktop, make the tray-resident timer report the same outcome through Nimbo notifications when enabled.
4. Add focused policy tests and verify disabled auto-update produces no refresh notification.

### Task 6: Update release notes, rebuild, and replace the prerelease

**Files:**
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/RELEASE_NOTES_1.1.0_BETA1.md`
- Modify: release artifacts under the existing Android and desktop build output directories

1. Add concise user-facing Android and Windows/Linux sections with machine-readable platform markers.
2. Run the full relevant Android, frontend, Rust, and installer verification suites.
3. Build signed Android APK variants and the project's custom Windows/Linux installers.
4. Verify filenames, architecture, sizes, SHA-256 sums, and signatures where available.
5. Replace only the matching assets in GitHub prerelease `v1.1.0-beta.1`, update its notes, and confirm it remains a prerelease while `v1.0.2` remains latest stable.
