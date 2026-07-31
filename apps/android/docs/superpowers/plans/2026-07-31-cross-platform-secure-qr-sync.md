# Secure Cross-Platform QR Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pair Nimbo Desktop with Nimbo Android by scanning a rotating QR code and securely transfer selected subscriptions and compatible settings in either direction.

**Architecture:** Desktop hosts a short-lived raw TCP pairing server on the local network. The QR contains only its private address, session id, expiration, and a random 256-bit AES-GCM key; all bundles are encrypted and authenticated, length-bounded, one-session-at-a-time, and require desktop approval before export or import. Both apps use the same versioned JSON bundle, merge subscriptions by normalized URL, apply only explicitly selected compatible categories, and never sync device-local permissions, executable/package allowlists, logs, traffic, or runtime VPN state.

**Tech Stack:** Kotlin/JVM cryptography, CameraX, ML Kit, Compose, OkHttp/Gson; Rust, Tokio TCP, ring AES-256-GCM, Tauri 2; React 19, TypeScript, qrcode.react; JUnit 4 and Rust unit tests.

---

## Shared protocol

```json
{
  "schema": "nimbo-cross-sync-v1",
  "platform": "android|desktop",
  "device_name": "Pixel 9|DESKTOP-PC",
  "created_at_ms": 0,
  "subscriptions": [{ "url": "https://provider/sub/key", "name": "Main" }],
  "appearance": {
    "theme_mode": "system|light|dark|black",
    "ui_style": "nimbo|material_you",
    "accent_color": "#75a7ff",
    "panel_brightness": 100,
    "transparency": 0,
    "blur": 25,
    "rounding": 100,
    "provider_theme": true,
    "show_subscription_logo": true
  },
  "connection": {
    "kill_switch": false,
    "tls_fragmentation": false,
    "show_speed_chart": true
  },
  "automation": {
    "language": "ru|en|system",
    "ping_on_launch": true,
    "update_channel": "stable|beta",
    "update_wifi_only": false,
    "subscriptions_auto_update": true,
    "subscriptions_update_interval_hours": 6,
    "subscriptions_update_on_launch": false,
    "subscriptions_ping_after_update": false
  }
}
```

Each TCP request is a four-byte big-endian length followed by this authenticated envelope:

```json
{
  "v": 1,
  "sid": "uuid",
  "nonce": "base64url-no-padding",
  "ciphertext": "base64url-no-padding"
}
```

AES-GCM additional authenticated data is `nimbo-sync-v1:<sid>`. Frames larger than 2 MiB, expired/consumed sessions, mismatched ids, malformed base64, reused states, and unauthenticated ciphertext are rejected.

### Task 1: Implement and test the shared Android protocol

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/sync/CrossPlatformSync.kt`
- Create: `app/src/test/java/com/danila/nimbo/sync/CrossPlatformSyncTest.kt`

- [x] **Step 1: Add failing QR, policy, merge, and crypto tests**

Test that `nimbo-sync://pair?v=1&host=192.168.1.5&port=42000&sid=<uuid>&key=<43-char-key>&exp=<future>` parses, public/non-private hosts and expired sessions fail, an empty target recommends the non-empty source, subscription URLs merge case-insensitively without duplicates, and AES-GCM round-trips while tampering fails.

- [x] **Step 2: Run the focused Android test**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.danila.nimbo.sync.CrossPlatformSyncTest"
```

Expected: compilation fails because the sync protocol types do not exist.

- [x] **Step 3: Implement versioned models, QR validation, AES-GCM, and TCP framing**

Use `SecureRandom`, `Cipher.getInstance("AES/GCM/NoPadding")`, `GCMParameterSpec(128, nonce)`, `Socket.connect(..., 5000)`, read/write timeouts, a 2 MiB bound, and the fixed AAD string. Keep `hello`, `status`, `commit`, and `receipt` as explicit action payloads.

- [x] **Step 4: Re-run the focused Android test**

Expected: all protocol tests pass.

### Task 2: Add Android bundle mapping and selective apply

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/sync/CrossPlatformSync.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`
- Test: `app/src/test/java/com/danila/nimbo/sync/CrossPlatformSyncTest.kt`

- [x] **Step 1: Test category selection and URL conflict resolution**

Verify that disabled categories leave their local values unchanged and subscriptions merge by canonical URL with incoming names only filling blank local names.

- [x] **Step 2: Export compatible Android values**

Map theme mode, style, custom accent, brightness/transparency/blur/rounding, provider branding, kill switch, TLS fragmentation, speed graph, language, update channel, Wi-Fi-only updates, and subscription automation to the shared model.

- [x] **Step 3: Apply only selected categories**

Apply appearance and preferences through public `PreferencesManager` setters so Compose state updates. Add missing subscription URLs through `MainViewModel.addSubscription()` so Android immediately fetches current nodes; never overwrite cached nodes, device lists, VPN permission, or runtime connection state.

### Task 3: Build the secure rotating desktop pairing server

**Files:**
- Create: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src-tauri/src/cross_sync.rs`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src-tauri/Cargo.toml`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src-tauri/src/lib.rs`
- Test: unit tests inside `cross_sync.rs`

- [x] **Step 1: Add Rust crypto, expiry, direction, and merge tests**

Test AES-256-GCM round-trip/tamper rejection, private-address filtering, expiration, empty-target recommendation, and URL merge.

- [x] **Step 2: Add locked dependencies**

Use existing workspace `base64`, `tokio`, `uuid`, `sha2`, and direct `ring = "0.17"`; do not introduce a web server framework.

- [x] **Step 3: Implement the bounded TCP server and session state machine**

Bind an ephemeral port on `0.0.0.0`, publish only a private LAN IPv4, generate a 256-bit key with `ring::rand::SystemRandom`, expire after 75 seconds, accept encrypted `hello/status/commit/receipt`, and stop exporting after a completed or replaced session.

- [x] **Step 4: Add Tauri commands**

Expose `cross_sync_start`, `cross_sync_status`, `cross_sync_approve`, `cross_sync_reject`, `cross_sync_accept_import`, and `cross_sync_cancel`. Desktop approval unlocks its bundle; accepting an import mutates persisted state only for selected categories and merges subscription URLs.

- [x] **Step 5: Run focused Rust tests**

```powershell
cargo test -p nimbo-ui cross_sync
```

Expected: all cross-sync tests pass.

### Task 4: Add the desktop synchronization page

**Files:**
- Create: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/pages/CrossPlatformSync.tsx`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/lib/api.ts`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/App.tsx`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/styles.css`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/package.json`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/package-lock.json`

- [x] **Step 1: Add typed Tauri sync APIs and a `/sync` route**

Model session status, remote inventory, categories, direction, and transfer result. Add a sidebar item named “Синхронизация”.

- [x] **Step 2: Render the rotating QR and safety state**

Use `qrcode.react`, show the 60-second countdown and six-character comparison code, rotate an unpaired QR automatically, and provide a manual “Новый QR” button.

- [x] **Step 3: Add remote approval and import confirmation**

Show the Android device name and inventory before approval. Require a second explicit desktop confirmation before mobile-to-desktop import; show added subscription count, applied categories, errors, and last successful sync.

- [x] **Step 4: Add persistent category defaults**

Store the four desktop checkboxes under `nimbo.crossSync.categories.v1` and display a note listing excluded device-local data.

- [x] **Step 5: Build the desktop frontend**

```powershell
npm --prefix apps/ui run build
```

Expected: TypeScript and Vite build successfully.

### Task 5: Add the Android synchronization page and scanner flow

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/screens/CrossPlatformSyncScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/QrScannerScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`

- [x] **Step 1: Add a settings destination and action tile**

Add “Синхронизация” as a separate settings sub-page using the existing Nimbo glass/material components and navigation transitions.

- [x] **Step 2: Add persisted category toggles and manual scan**

Show four category switches, last sync time/device, a “Сканировать QR с ПК” button, and security/exclusion explanations. Reuse `QrScannerScreen` with sync-specific title and instruction.

- [x] **Step 3: Pair and compare inventories**

After scanning, send encrypted hello, poll approval with a bounded timeout, display the comparison code on Android, then show desktop/mobile counts and recommend the direction when one side is empty.

- [x] **Step 4: Confirm and apply transfer**

For desktop-to-mobile, show a final confirmation, apply the selected bundle, add/fetch missing subscriptions, recreate the Activity when appearance changes, and send a receipt. For mobile-to-desktop, send a commit and wait for desktop confirmation before showing success.

### Task 6: Verify both applications and document the feature

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/CHANGELOG_NIMBO.md`

- [x] **Step 1: Run complete Android verification**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Expected: zero test failures and architecture-specific plus universal APKs.

- [x] **Step 2: Run complete desktop verification**

```powershell
cargo test -p nimbo-ui
npm --prefix apps/ui run build
```

Expected: all Rust tests, TypeScript compilation, and Vite production build pass.

- [x] **Step 3: Document user-visible behavior**

Describe rotating QR pairing, bidirectional selective transfer, empty-device recommendations, dual confirmation, encryption, and the list of intentionally excluded device-local data.

### Task 7: Add rich remote-device and subscription previews

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/sync/CrossPlatformSync.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/CrossPlatformSyncScreen.kt`
- Modify: `app/src/test/java/com/danila/nimbo/sync/CrossPlatformSyncTest.kt`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src-tauri/src/cross_sync.rs`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/lib/api.ts`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/pages/CrossPlatformSync.tsx`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/styles.css`

- [x] **Step 1: Extend the versioned bundle with device metadata**

Add optional `device_info` containing device name, platform, OS name/version, Nimbo version, and architecture. Keep the field optional/defaulted so v1 peers that omit it remain readable.

- [x] **Step 2: Add safe subscription previews**

Expose subscription display names and counts to the pairing UI, never raw subscription URLs. Limit the rendered preview and show an explicit remaining-count label.

- [x] **Step 3: Render the Android device card on Desktop**

Replace the one-line Android row with a compact glass device passport showing model/name, Android version, Nimbo version, architecture, and subscription-name chips.

- [x] **Step 4: Render the Desktop device card on Android**

Show the PC name, Windows/system label, Nimbo version, architecture, and subscription names alongside the existing direction recommendation.

- [x] **Step 5: Verify protocol compatibility and both builds**

Run the focused Android/Rust sync tests, full Android tests and APK assembly, Rust tests, formatting check, and the Desktop TypeScript/Vite production build.
