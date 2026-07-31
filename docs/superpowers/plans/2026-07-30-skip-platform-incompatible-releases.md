# Skip Platform-Incompatible Releases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent Nimbo from showing an update dialog for releases that do not contain an installer compatible with the current desktop operating system and architecture.

**Architecture:** Move compatibility into release selection instead of selecting the highest channel release first and checking its assets afterward. Both update checking and verified installation will therefore resolve the same newest compatible release, while Android-only releases remain visible on GitHub but are ignored by desktop clients.

**Tech Stack:** Rust, Tauri, GitHub Releases API, Cargo unit tests, NSIS release build.

---

### Task 1: Reproduce the Android-only release regression

**Files:**
- Modify: `apps/ui/src-tauri/src/updater.rs`
- Modify: `apps/ui/src/App.tsx`
- Test: `apps/ui/src-tauri/src/updater.rs`

- [x] **Step 1: Add a failing release-selection test**

Add a GitHub asset fixture and verify that Windows-compatible selection skips a newer Android-only stable release in favor of the newest stable release containing a Windows x64 installer:

```rust
#[test]
fn skips_newer_releases_without_a_compatible_asset() {
    let releases = vec![
        release_with_assets("v1.0.2", false, &["Nimbo_v1.0.2_universal_release.apk"]),
        release_with_assets("v1.0.1", false, &["NimboSetup_1.0.1_x64.exe"]),
    ];
    assert_eq!(
        select_release_for_target(&releases, UpdateChannel::Stable, "windows", "x86_64")
            .unwrap()
            .tag_name,
        "v1.0.1"
    );
}
```

- [x] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
cargo test -p nimbo-ui updater::tests::skips_newer_releases_without_a_compatible_asset
```

Expected: compilation fails because `select_release_for_target` and the fixture helper do not exist yet.

### Task 2: Select the newest compatible release

**Files:**
- Modify: `apps/ui/src-tauri/src/updater.rs`
- Test: `apps/ui/src-tauri/src/updater.rs`

- [x] **Step 1: Add target-aware asset scoring**

Introduce target-parameterized helpers so production uses `std::env::consts::{OS, ARCH}` and tests can explicitly exercise Windows:

```rust
fn select_asset_for_target<'a>(
    assets: &'a [GithubAsset],
    os: &str,
    arch: &str,
) -> Option<&'a GithubAsset> {
    assets
        .iter()
        .filter_map(|asset| asset_score_for(asset, os, arch).map(|score| (score, asset)))
        .max_by_key(|(score, _)| *score)
        .map(|(_, asset)| asset)
}
```

Use strict desktop extensions for each operating system. In particular, `.apk` must never be eligible for Windows, macOS, or Linux.

- [x] **Step 2: Filter releases by compatible assets before comparing versions**

Implement:

```rust
fn select_release_for_target<'a>(
    releases: &'a [GithubRelease],
    channel: UpdateChannel,
    os: &str,
    arch: &str,
) -> Option<&'a GithubRelease> {
    releases
        .iter()
        .filter(|release| release_matches_channel(release, channel))
        .filter(|release| select_asset_for_target(&release.assets, os, arch).is_some())
        .max_by(|left, right| compare_versions(&left.tag_name, &right.tag_name))
}
```

Make `check_app_update` and `install_app_update` call the same production wrapper so an asset accepted during checking is also the one verified during installation.

- [x] **Step 3: Guard automatic dialogs against incomplete update metadata**

Add a shared UI predicate:

```tsx
function isInstallableAppUpdate(update: AppUpdateInfo | null | undefined) {
  return Boolean(update?.available && update.asset?.digest);
}
```

Use it for the startup check and `APP_UPDATE_DIALOG_EVENT`. Manual checks in Settings continue showing unavailable or incomplete release information without interrupting startup.

- [x] **Step 4: Run updater tests**

Run:

```powershell
cargo test -p nimbo-ui updater::tests
```

Expected: all updater tests pass, including the Android-only regression and existing architecture tests.

### Task 3: Verify and package the fix

**Files:**
- Verify: `apps/ui/src-tauri/src/updater.rs`
- Build: `target/release/bundle/nsis/Nimbo_1.0.1_x64-setup.exe`

- [x] **Step 1: Run project checks**

Run:

```powershell
cargo fmt --all -- --check
cargo test -p nimbo-ui
cargo check --workspace
npm run build
```

Expected: formatting succeeds, all Rust tests pass, the workspace compiles, and Vite produces the production UI bundle.

- [x] **Step 2: Build the replacement Windows installer**

Run from `apps/ui`:

```powershell
npm run build:installer:current
```

Expected: NSIS creates `target/release/bundle/nsis/Nimbo_1.0.1_x64-setup.exe`.

- [x] **Step 3: Record artifact metadata**

Run:

```powershell
Get-Item target/release/bundle/nsis/Nimbo_1.0.1_x64-setup.exe
Get-FileHash -Algorithm SHA256 target/release/bundle/nsis/Nimbo_1.0.1_x64-setup.exe
```

Expected: the installer reports product version `1.0.1` and a non-empty SHA-256 hash.
