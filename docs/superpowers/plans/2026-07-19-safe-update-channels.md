# Safe Update Channels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add stable/beta release channels, verified downloads, same-version asset revision detection, and automatic Windows installer rollback.

**Architecture:** The Rust updater remains the source of truth and reads the GitHub release list, filters it by the selected channel, selects the platform asset, and compares both semantic version and a persisted asset fingerprint. Downloads are staged under the Nimbo data directory and accepted only after SHA-256 verification; the Windows installer preserves the previous binaries, runs a headless health check, and restores those binaries if installation or health validation fails.

**Tech Stack:** Rust 2021, Tauri 2, React 19, TypeScript, NSIS, GitHub Actions, GitHub Releases REST API.

---

### Task 1: Persist the selected update channel

**Files:**
- Modify: `apps/ui/src-tauri/src/state.rs`
- Modify: `apps/ui/src/lib/api.ts`
- Modify: `apps/ui/src/pages/Settings.tsx`
- Modify: `apps/ui/src/lib/i18n.ts`

- [ ] **Step 1: Add a failing Rust compatibility test**

Add a test that deserializes preferences without `update_channel` and asserts `UpdateChannel::Stable`, plus a test that round-trips `"beta"`.

- [ ] **Step 2: Run the focused Rust test and verify it fails**

Run: `cargo test -p nimbo-ui state::tests::missing_update_channel_defaults_to_stable`

Expected: FAIL because `UpdateChannel` and `update_channel` do not exist.

- [ ] **Step 3: Add the shared channel field**

Define the backend enum and field exactly as:

```rust
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "snake_case")]
pub enum UpdateChannel {
    #[default]
    Stable,
    Beta,
}

pub struct AppPreferences {
    // existing fields
    pub check_updates_on_launch: bool,
    pub update_channel: UpdateChannel,
}
```

Mirror it in TypeScript as `export type UpdateChannel = "stable" | "beta"`, normalize unknown persisted values to `"stable"`, and add a two-button channel selector to `UpdatesSection`.

- [ ] **Step 4: Add Russian and English labels**

Add localized labels for stable, beta, channel description, SHA-256 verification, reissued builds, release notes, and asset upload time.

- [ ] **Step 5: Run preference and frontend checks**

Run: `cargo test -p nimbo-ui state::tests`

Expected: PASS.

Run: `npm run build` in `apps/ui`.

Expected: TypeScript and Vite build complete successfully.

### Task 2: Detect release channels and same-version asset revisions

**Files:**
- Modify: `apps/ui/src-tauri/src/updater.rs`
- Modify: `apps/ui/src/lib/api.ts`
- Modify: `apps/ui/src/App.tsx`
- Modify: `apps/ui/src/pages/Settings.tsx`

- [ ] **Step 1: Add failing updater unit tests**

Cover these exact cases with in-memory release fixtures:

```rust
assert_eq!(select_release(&releases, UpdateChannel::Stable).unwrap().tag_name, "v1.2.0");
assert_eq!(select_release(&releases, UpdateChannel::Beta).unwrap().tag_name, "v1.3.0-beta.1");
assert_eq!(update_reason("1.2.0", "1.2.0", Some("old"), "new"), Some(UpdateReason::Reissued));
assert_eq!(update_reason("1.2.0", "1.2.0", Some("same"), "same"), None);
```

- [ ] **Step 2: Run the focused updater tests and verify they fail**

Run: `cargo test -p nimbo-ui updater::tests`

Expected: FAIL because channel selection, asset fingerprints, and reissue reasons do not exist.

- [ ] **Step 3: Replace `/releases/latest` with channel-aware release listing**

Fetch `https://api.github.com/repos/BBGGVP5/nimbo/releases?per_page=20`; discard drafts, use the first non-prerelease release for stable, and use the first published release for beta. Parse release versions from `tag_name`, never from a human-readable release title.

- [ ] **Step 4: Extend release asset metadata**

Expose these fields to the frontend:

```rust
pub struct AppUpdateAsset {
    pub id: u64,
    pub name: String,
    pub download_url: String,
    pub size: u64,
    pub content_type: Option<String>,
    pub digest: Option<String>,
    pub created_at: Option<String>,
    pub updated_at: Option<String>,
    pub fingerprint: String,
}
```

The fingerprint must include asset ID, `updated_at`, size, and digest so a GitHub `--clobber` re-upload changes identity even when the tag and app version stay unchanged.

- [ ] **Step 5: Persist installed fingerprints and calculate availability**

Store successful update receipts in the platform data directory under `Nimbo/updates/installed.json`. Report `new_version` when SemVer is newer and `reissued` when versions match but the selected asset fingerprint differs from the installed receipt for that version. Without a prior receipt, do not infer a same-version reissue.

- [ ] **Step 6: Show release reason and notes**

Pass `preferences.update_channel` from both startup and manual checks. In the dialog show the release body when it is non-empty; otherwise show a localized “bug fixes and improvements” fallback and the release target commit.

- [ ] **Step 7: Run updater and frontend checks**

Run: `cargo test -p nimbo-ui updater::tests`

Expected: PASS.

Run: `npm run build` in `apps/ui`.

Expected: PASS.

### Task 3: Download and verify an update before opening it

**Files:**
- Modify: `apps/ui/src-tauri/src/updater.rs`
- Modify: `apps/ui/src-tauri/src/lib.rs`
- Modify: `apps/ui/src/lib/api.ts`
- Modify: `apps/ui/src/App.tsx`
- Modify: `apps/ui/src/pages/Settings.tsx`

- [ ] **Step 1: Add failing digest validation tests**

Test valid, malformed, and mismatched SHA-256 values:

```rust
assert!(verify_sha256(b"nimbo", &format!("sha256:{}", sha256_hex(b"nimbo"))).is_ok());
assert!(verify_sha256(b"nimbo", "md5:bad").is_err());
assert!(verify_sha256(b"nimbo", &format!("sha256:{}", "00".repeat(32))).is_err());
```

- [ ] **Step 2: Run the focused digest tests and verify they fail**

Run: `cargo test -p nimbo-ui updater::tests::verifies_sha256_digest`

Expected: FAIL because verification helpers do not exist.

- [ ] **Step 3: Add a verified download command**

Replace the browser-only asset action with a backend command that validates the GitHub HTTPS host, downloads the chosen asset to `Nimbo/updates/downloads`, requires a `sha256:` release-asset digest, hashes the bytes, deletes a mismatched file, and only then opens the installer/package. Return a structured result with `verified: true`, the normalized digest, and whether rollback is supported.

- [ ] **Step 4: Wire verified status into the UI**

Disable installation when the selected asset has no supported digest. Change the primary button to “Verify and install”, show progress/busy state, and report verification errors without opening the downloaded file.

- [ ] **Step 5: Run verification and build checks**

Run: `cargo test -p nimbo-ui updater::tests`

Expected: PASS.

Run: `cargo check -p nimbo-ui` and `npm run build` in `apps/ui`.

Expected: PASS.

### Task 4: Roll back a failed Windows installer update

**Files:**
- Modify: `apps/ui/src-tauri/src/lib.rs`
- Modify: `apps/ui/src-tauri/src/updater.rs`
- Modify: `apps/ui/src-tauri/windows/nimbo-installer.nsi`
- Modify: `apps/ui/src-tauri/windows/hooks.nsh`
- Modify: `apps/ui/src-tauri/windows/build-local-installer.ps1`

- [ ] **Step 1: Add a headless application health check**

Handle `--update-health-check` before starting Tauri. It must load and persist application state, verify the current executable exists, and return exit code 0 only when these checks pass.

- [ ] **Step 2: Preserve update receipt data before launch**

After a download verifies, write `pending.json` atomically with the version, channel, fingerprint, digest, asset timestamp, and current executable path. The health check promotes it to `installed.json`; a failed install leaves it pending so the same update is offered again.

- [ ] **Step 3: Add NSIS rollback branches**

Keep `Nimbo.exe.old` and `nimbo-svc.exe.old` until service registration and `Nimbo.exe --update-health-check` both return success. On any non-zero result, stop the new helper, delete the new binaries, rename both `.old` files back, reinstall the old helper, and abort with a clear localized error. Delete `.old` only after health succeeds.

- [ ] **Step 4: Pass the real product version into the custom installer**

Add `/DPRODUCT_VERSION=$version` to `build-local-installer.ps1` and guard the NSIS default with `!ifndef PRODUCT_VERSION`, so file metadata and uninstall registry entries match the release version.

- [ ] **Step 5: Validate the NSIS source and Rust health path**

Run: `cargo test -p nimbo-ui updater::tests` and `cargo check -p nimbo-ui`.

Expected: PASS.

Run the current-architecture installer build when NSIS and the target toolchain are available:

`npm run build:installer:current` in `apps/ui`.

Expected: one `Nimbo_<version>_<arch>-setup.exe` is created.

### Task 5: Publish beta metadata and document the safety contract

**Files:**
- Modify: `.github/workflows/release.yml`
- Modify: `README.md`

- [ ] **Step 1: Mark prerelease tags correctly**

When creating a release, pass `--prerelease` for SemVer tags containing a prerelease suffix such as `-beta`, `-alpha`, or `-rc`; stable tags remain normal published releases. Continue using generated release notes so the application can show commit-derived notes.

- [ ] **Step 2: Document re-upload behavior**

Document that `gh release upload --clobber` changes the selected asset fingerprint, clients with an installed receipt receive a `reissued` notification even at the same SemVer, and downloads are rejected when GitHub does not provide a SHA-256 digest.

- [ ] **Step 3: Run final verification**

Run: `cargo test -p nimbo-ui`

Expected: PASS.

Run: `cargo check --workspace`

Expected: PASS.

Run: `npm run build` in `apps/ui`.

Expected: PASS.

