# Custom Desktop Installers Replacement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the generic desktop packages in `v1.1.0-beta.1` with Nimbo's branded custom installers built from the current Beta 1 sources.

**Architecture:** `apps/installer` is a separate Tauri application that embeds the already-built Nimbo UI, the Windows helper and verified Xray runtime. Windows is built locally for x64, x86 and ARM64; Linux x64 is built inside Ubuntu WSL. Existing Android assets remain untouched, while the release download table is updated to point at the custom installers.

**Tech Stack:** PowerShell, Rust/Cargo, Tauri 2, React/Vite, Ubuntu WSL, GitHub CLI.

---

### Task 1: Verify release and build inputs

**Files:**
- Inspect: `apps/installer/build-custom-installer.ps1`
- Inspect: `apps/installer/build-custom-linux-installer.sh`
- Inspect: `apps/installer/src-tauri/tauri.conf.json`
- Inspect: `apps/ui/src-tauri/tauri.conf.json`

- [x] **Step 1: Confirm every component reports `1.1.0-beta.1`**

Run:

```powershell
rg -n '1\.1\.0-beta\.1' Cargo.toml apps/installer apps/ui
```

Expected: the workspace, app and installer configs use the same Beta 1 version.

- [x] **Step 2: Record the current GitHub assets before mutation**

Run:

```powershell
gh release view v1.1.0-beta.1 --repo BBGGVP5/nimbo --json assets,url,isPrerelease
```

Expected: the release is a prerelease and Android assets are present.

### Task 2: Build custom Windows installers

**Files:**
- Build input: `apps/installer/src-tauri/src/payload.rs`
- Build input: `apps/installer/src/main.tsx`
- Output: `target/release/bundle/custom/windows/NimboSetup_1.1.0-beta.1_x64.exe`
- Output: `target/release/bundle/custom/windows/NimboSetup_1.1.0-beta.1_x86.exe`
- Output: `target/release/bundle/custom/windows/NimboSetup_1.1.0-beta.1_arm64.exe`

- [x] **Step 1: Build all supported Windows targets**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File apps/installer/build-custom-installer.ps1 -All
```

Expected: three custom `NimboSetup` executables are emitted under `target/release/bundle/custom/windows`.

- [x] **Step 2: Verify executable metadata and embedded size**

Run:

```powershell
Get-ChildItem target/release/bundle/custom/windows/NimboSetup_1.1.0-beta.1_*.exe | Select-Object Name,Length,VersionInfo
```

Expected: every file is a PE executable, reports Beta 1, and is large enough to contain the app and runtime payload.

### Task 3: Build custom Linux installer

**Files:**
- Build input: `apps/installer/build-custom-linux-installer.sh`
- Output: `target/release/bundle/custom/linux/NimboSetup_1.1.0-beta.1_x64`

- [x] **Step 1: Build x64 in Ubuntu WSL**

Run:

```powershell
wsl -d Ubuntu-24.04 -- bash -lc "cd /mnt/c/Users/Danila/Desktop/nimbo-app-main && ./apps/installer/build-custom-linux-installer.sh --target x86_64-unknown-linux-gnu"
```

Expected: one executable custom Linux installer is emitted under `target/release/bundle/custom/linux`.

- [x] **Step 2: Verify the Linux executable**

Run:

```powershell
wsl -d Ubuntu-24.04 -- bash -lc "file /mnt/c/Users/Danila/Desktop/nimbo-app-main/target/release/bundle/custom/linux/NimboSetup_1.1.0-beta.1_x64"
```

Expected: x86-64 ELF executable.

### Task 4: Publish exact checksummed assets

**Files:**
- Create: one `.sha256` sidecar beside every custom installer.
- Modify remotely: GitHub release `v1.1.0-beta.1` assets and body.

- [x] **Step 1: Generate SHA-256 sidecars**

Run `Get-FileHash -Algorithm SHA256` for each installer and write the conventional `<hash>  <filename>` line to its matching `.sha256` file.

Expected: four sidecars whose hashes match a fresh local calculation.

- [x] **Step 2: Remove only superseded generic Windows assets**

Run:

```powershell
gh release delete-asset v1.1.0-beta.1 Nimbo_1.1.0-beta.1_windows_x64_setup.exe --repo BBGGVP5/nimbo --yes
gh release delete-asset v1.1.0-beta.1 Nimbo_1.1.0-beta.1_windows_x64_setup.exe.sha256 --repo BBGGVP5/nimbo --yes
```

Expected: Android and existing Linux package assets remain unchanged.

- [x] **Step 3: Upload custom desktop installers and checksums**

Run `gh release upload v1.1.0-beta.1 --repo BBGGVP5/nimbo` with the four custom installers and four sidecars.

Expected: all eight files appear on the release and GitHub reports their SHA-256 digests.

- [x] **Step 4: Update the release download table**

Replace the Windows link with the x64/x86/ARM64 custom installers and add the custom Linux x64 installer link while retaining AppImage, DEB and RPM alternatives.

Expected: every badge downloads an existing asset.

### Task 5: Final remote verification

**Files:**
- Inspect remotely: GitHub release `v1.1.0-beta.1`.

- [x] **Step 1: Compare local and remote digests**

Run:

```powershell
gh release view v1.1.0-beta.1 --repo BBGGVP5/nimbo --json assets,body,url,isPrerelease
```

Expected: custom installers and sidecars exist, generic Windows setup is gone, the release remains a prerelease, and all download links resolve.

- [x] **Step 2: Confirm Android assets were not changed**

Compare Android asset names, sizes and digests with the pre-mutation snapshot.

Expected: every APK and APK checksum is identical.
