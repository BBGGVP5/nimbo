# Desktop Resumable Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the desktop updater with resumable architecture-aware downloads, free-space and Wi-Fi enforcement, and a post-install “What changed” flow, without adding allowlist diagnostics.

**Architecture:** Keep Rust as the trusted update boundary: it re-fetches GitHub metadata, selects the native package, binds Wi-Fi-only downloads to a Wi-Fi interface, streams into a fingerprint-specific `.part` file, verifies size and SHA-256, and then opens the package. Extend the existing pending/installed receipt so the installer health check can safely hand release notes to React on the next normal launch.

**Tech Stack:** Rust 2021, Tauri 2, reqwest, fs2, Windows IP Helper API, React 19, TypeScript.

---

### Task 1: Persist the Wi-Fi-only preference

**Files:**
- Modify: `apps/ui/src-tauri/src/state.rs`
- Modify: `apps/ui/src/lib/api.ts`
- Modify: `apps/ui/src/pages/Settings.tsx`
- Modify: `apps/ui/src/lib/i18n.ts`

- [ ] **Step 1: Add the failing Rust compatibility test**

```rust
#[test]
fn update_wifi_only_defaults_to_false_and_round_trips() {
    let preferences: AppPreferences = serde_json::from_value(serde_json::json!({})).unwrap();
    assert!(!preferences.update_wifi_only);

    let mut preferences = AppPreferences::default();
    preferences.update_wifi_only = true;
    let restored: AppPreferences =
        serde_json::from_value(serde_json::to_value(preferences).unwrap()).unwrap();
    assert!(restored.update_wifi_only);
}
```

- [ ] **Step 2: Run the state test and verify it fails**

Run: `cargo test -p nimbo-ui state::tests::update_wifi_only_defaults_to_false_and_round_trips`

Expected: FAIL because `update_wifi_only` is absent.

- [ ] **Step 3: Add the preference to Rust and TypeScript**

Add `pub update_wifi_only: bool` after `update_channel`, initialize it to `false`, add `update_wifi_only: boolean` to `AppPreferences`, set the browser default to `false`, and normalize with `Boolean(value?.update_wifi_only)`.

- [ ] **Step 4: Expose the toggle in update settings**

Add a `ToggleRow` after “Check for updates on launch”:

```tsx
<ToggleRow
  label={m.settings.updateWifiOnly}
  description={m.settings.updateWifiOnlyDescription}
  enabled={preferences.update_wifi_only}
  onToggle={(update_wifi_only) => onChange({ update_wifi_only })}
  icon={<DownloadIcon />}
/>
```

- [ ] **Step 5: Add Russian and English labels**

Use these meanings:

```text
RU: «Скачивать обновления только по Wi‑Fi»
RU: «Загрузка запускается только через активный Wi‑Fi-интерфейс. Проверка наличия версии использует любую сеть.»
EN: “Download updates over Wi-Fi only”
EN: “Downloads start only through an active Wi-Fi interface. Update checks may use any network.”
```

- [ ] **Step 6: Run state tests and frontend build**

Run: `cargo test -p nimbo-ui state::tests`

Expected: PASS.

Run from `apps/ui`: `npm run build`

Expected: PASS.

### Task 2: Make installer selection explicitly architecture-safe

**Files:**
- Modify: `apps/ui/src-tauri/src/updater.rs`

- [ ] **Step 1: Add failing architecture policy tests**

Refactor `arch_score` into `arch_score_for(arch, name)` and cover:

```rust
assert!(arch_score_for("x86_64", "Nimbo_1.0.2_x64-setup.exe") > 0);
assert!(arch_score_for("x86_64", "Nimbo_1.0.2_x86-setup.exe") < 0);
assert!(arch_score_for("aarch64", "Nimbo_1.0.2_arm64-setup.exe") > 0);
assert!(arch_score_for("aarch64", "Nimbo_1.0.2_x64-setup.exe") < 0);
```

- [ ] **Step 2: Run the updater test and verify it fails**

Run: `cargo test -p nimbo-ui updater::tests::architecture_policy_rejects_incompatible_installers`

Expected: FAIL because the injectable helper does not exist.

- [ ] **Step 3: Implement and use the policy**

Make `arch_score(name)` delegate to `arch_score_for(std::env::consts::ARCH, name)`. Keep incompatible assets below zero so `asset_score` rejects them; native assets score above generic packages.

- [ ] **Step 4: Run updater tests**

Run: `cargo test -p nimbo-ui updater::tests`

Expected: PASS.

### Task 3: Stream, resume, and space-check update downloads

**Files:**
- Modify: `Cargo.toml`
- Modify: `apps/ui/src-tauri/Cargo.toml`
- Modify: `apps/ui/src-tauri/src/updater.rs`
- Modify: `Cargo.lock` through Cargo

- [ ] **Step 1: Add failing pure policy tests**

Add helpers and tests for:

```rust
assert_eq!(range_start(0, 1_000), None);
assert_eq!(range_start(400, 1_000), Some(400));
assert!(content_range_matches("bytes 400-999/1000", 400));
assert!(!content_range_matches("bytes 0-999/1000", 400));
assert_eq!(required_free_bytes(1_000, 400), 600 + UPDATE_STORAGE_RESERVE_BYTES);
```

- [ ] **Step 2: Run the updater tests and verify they fail**

Run: `cargo test -p nimbo-ui updater::tests`

Expected: FAIL because the resume policy helpers do not exist.

- [ ] **Step 3: Add cross-platform free-space support**

Add `fs2 = "0.4"` to workspace dependencies and `fs2.workspace = true` to `nimbo-ui`. Check `fs2::available_space(downloads_dir)` against the remaining asset bytes plus a 64 MiB reserve before issuing a request.

- [ ] **Step 4: Replace in-memory download with a staged stream**

Use a fingerprint-specific final file and `.part` file. If the partial length is between zero and the GitHub asset size, send `Range: bytes=<length>-`; append only for HTTP 206 with a matching `Content-Range`. If the server returns HTTP 200, truncate and restart. Preserve incomplete `.part` files on transport errors, but delete files that fail final size or SHA-256 verification.

- [ ] **Step 5: Resume across direct and proxy attempts**

The direct attempt writes into the partial file. If it fails and Wi-Fi-only mode is disabled, retry through the existing local proxy using the newly written partial length. Never buffer the whole installer in memory.

- [ ] **Step 6: Verify the completed file**

Hash with a buffered `std::fs::File` reader, atomically rename the verified `.part` file to the final package, then write `pending.json` and open the installer.

- [ ] **Step 7: Run updater tests and Rust checks**

Run: `cargo test -p nimbo-ui updater::tests`

Expected: PASS.

Run: `cargo check -p nimbo-ui`

Expected: PASS.

### Task 4: Enforce Wi-Fi at the Rust download boundary

**Files:**
- Modify: `apps/ui/src-tauri/Cargo.toml`
- Modify: `apps/ui/src-tauri/src/updater.rs`

- [ ] **Step 1: Enable Windows adapter APIs**

Add `Win32_NetworkManagement_Ndis` and `Win32_Networking_WinSock` to the existing `windows-sys` features.

- [ ] **Step 2: Resolve an active Wi-Fi address**

On Windows, call `GetAdaptersAddresses`, accept only an IEEE 802.11 adapter with `OperStatus == Up`, and extract a non-loopback unicast IP. On Linux, find an `operstate=up` interface with `/sys/class/net/<name>/wireless` and read its address from `ip -j address show dev <name>`.

- [ ] **Step 3: Bind Wi-Fi-only downloads**

Read `state.snapshot().preferences.update_wifi_only` inside `install_app_update`. When enabled, require a resolved Wi-Fi address and apply `reqwest::ClientBuilder::local_address(address)`; skip the local proxy fallback so it cannot silently use another transport.

- [ ] **Step 4: Run Windows compilation**

Run: `cargo check -p nimbo-ui`

Expected: PASS with the Windows target currently in use.

### Task 5: Carry release notes through installation

**Files:**
- Modify: `apps/ui/src-tauri/src/updater.rs`
- Modify: `apps/ui/src-tauri/src/lib.rs`
- Modify: `apps/ui/src/lib/api.ts`

- [ ] **Step 1: Add receipt compatibility tests**

Test that old receipts without changelog fields deserialize and do not request a dialog, while a pending update round-trips:

```rust
assert!(!old_receipt.show_changelog);
assert_eq!(pending.release_notes.as_deref(), Some("- Fixed reconnect"));
assert!(pending.show_changelog);
```

- [ ] **Step 2: Extend the receipt**

Add serde-defaulted `release_notes`, `release_url`, and `show_changelog` fields. Baseline receipts use `show_changelog: false`; verified installer receipts use `true` and copy metadata from the trusted GitHub release.

- [ ] **Step 3: Add post-update commands**

Expose:

```rust
#[tauri::command]
pub fn get_post_update_info(app: AppHandle) -> Result<Option<AppPostUpdateInfo>, String>;

#[tauri::command]
pub fn dismiss_post_update_info(app: AppHandle) -> Result<(), String>;
```

The getter only returns an installed receipt matching `CARGO_PKG_VERSION`. Dismissal atomically rewrites `installed.json` with `show_changelog: false`.

- [ ] **Step 4: Register commands and TypeScript types**

Register both commands in `lib.rs`; add `AppPostUpdateInfo` plus `getPostUpdateInfo()` and `dismissPostUpdateInfo()` to `api.ts`, including browser-preview storage.

- [ ] **Step 5: Run Rust and frontend builds**

Run: `cargo test -p nimbo-ui updater::tests`

Expected: PASS.

Run from `apps/ui`: `npm run build`

Expected: PASS.

### Task 6: Show “What changed” after successful installation

**Files:**
- Modify: `apps/ui/src/App.tsx`
- Modify: `apps/ui/src/styles.css`
- Modify: `apps/ui/src/lib/i18n.ts`

- [ ] **Step 1: Load pending post-update metadata**

On application mount, call `api.getPostUpdateInfo()` and keep the returned value in state.

- [ ] **Step 2: Add the post-update dialog**

Initially show a compact success state with installed version and two buttons: “Later” and “What changed”. “What changed” expands the trusted release notes and optional release-page link; closing calls `dismissPostUpdateInfo()` so it appears only once after the user handles it.

- [ ] **Step 3: Add localized labels and styles**

Provide Russian and English title, success text, “What changed”, release link, and close labels. Reuse the existing update-dialog visual language and add only the classes required for the success state.

- [ ] **Step 4: Run final verification**

Run: `cargo test -p nimbo-ui`

Expected: PASS.

Run: `cargo check --workspace`

Expected: PASS.

Run from `apps/ui`: `npm run build`

Expected: PASS.

Run from `apps/ui` when NSIS is available: `npm run build:installer:current`

Expected: one current-architecture setup file in `target/release/bundle/nsis`.

