# Nimbo 1.1.0 Beta 5 Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Nimbo's branded Windows and Linux installers, package the freshly built Android APKs, and publish a verified GitHub prerelease with user-facing Beta 5 notes.

**Architecture:** The existing `apps/installer` Tauri application embeds the current desktop UI and produces the branded installers. Windows payloads are built locally for supported MSVC targets, Linux x64 is built natively in Ubuntu WSL, and all release binaries are copied into an isolated staging directory with SHA-256 sidecars before upload.

**Tech Stack:** Rust/Cargo, Tauri 2, React/Vite, PowerShell, WSL Ubuntu, Bash, Android APK artifacts, GitHub CLI.

---

### Task 1: Verify Beta 5 inputs

**Files:**
- Inspect: `Cargo.toml`
- Inspect: `apps/ui/package.json`
- Inspect: `apps/ui/src-tauri/tauri.conf.json`
- Inspect: `apps/installer/package.json`
- Inspect: `apps/installer/src-tauri/tauri.conf.json`
- Inspect: `C:/Users/Danila/AndroidStudioProjects/Nimbo/app/release/`

- [ ] Confirm every desktop manifest reports `1.1.0-beta.5`.
- [ ] Confirm ARM64, ARMv7 and universal APKs exist, are non-empty and were built on 17 August 2026.
- [ ] Confirm GitHub authentication succeeds and `v1.1.0-beta.5` does not already exist.

### Task 2: Build branded Windows installers

**Files:**
- Build: `apps/installer/build-custom-installer.ps1`
- Output: `target/release/bundle/custom/windows/NimboSetup_1.1.0-beta.5_x64.exe`
- Output when supported: `target/release/bundle/custom/windows/NimboSetup_1.1.0-beta.5_x86.exe`
- Output when supported: `target/release/bundle/custom/windows/NimboSetup_1.1.0-beta.5_arm64.exe`

- [ ] Run `powershell -NoProfile -ExecutionPolicy Bypass -File apps/installer/build-custom-installer.ps1 -All`.
- [ ] Require a fresh non-empty x64 installer and retain x86/ARM64 only when their local toolchains complete successfully.
- [ ] Verify every Windows artifact is a PE executable and contains Beta 5 file metadata.

### Task 3: Build branded Linux installer

**Files:**
- Build: `apps/installer/build-custom-linux-installer.sh`
- Output: `target/release/bundle/custom/linux/NimboSetup_1.1.0-beta.5_x64`

- [ ] Run `wsl -d Ubuntu-24.04 -- bash -lc "cd /mnt/c/Users/Danila/Desktop/nimbo-app-main && ./apps/installer/build-custom-linux-installer.sh --target x86_64-unknown-linux-gnu"`.
- [ ] Verify the output with `file` and require an executable x86-64 ELF binary.

### Task 4: Stage and verify release assets

**Files:**
- Create: `target/release/publish/v1.1.0-beta.5/`
- Create: one `.sha256` sidecar for every staged binary.
- Create: `RELEASE_NOTES_1.1.0_BETA5.md`

- [ ] Copy the three Android APKs without rebuilding or modifying them.
- [ ] Copy only the newly built custom Windows and Linux installers.
- [ ] Calculate SHA-256 for every artifact and write `<hash>  <filename>` to each sidecar.
- [ ] Recalculate and compare every sidecar before upload.
- [ ] Write release notes in plain Russian for users, without internal class names, ports, UUIDs or implementation details.

### Task 5: Publish and audit the prerelease

**Files:**
- Upload: all binaries and sidecars from `target/release/publish/v1.1.0-beta.5/`
- Read: `RELEASE_NOTES_1.1.0_BETA5.md`

- [ ] Create `v1.1.0-beta.5` as prerelease `Nimbo 1.1.0 Beta 5` targeted at `main`.
- [ ] Upload every staged artifact and checksum exactly once.
- [ ] Verify the release is marked prerelease, the asset set is complete and remote sizes/digests match the local files.
- [ ] Report the final release URL and exact published platform set.
