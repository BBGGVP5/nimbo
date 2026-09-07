# Nimbo 1.1.0 Beta 3 Release Implementation Plan

### Task 0: Android pre-release polish gate

- [ ] Unify subpage headers without a full-width rounded capsule.
- [ ] Keep network profiles, captive-portal help, smart groups, traffic limits and disconnect history inside the existing «Соединения» page.
- [ ] Expand haptic profiles and replay the currently selected profile when it is tapped again.
- [ ] Place the Beta badge in the adaptive icon's upper-right corner and reuse the same resource in settings previews.
- [ ] Run Android unit tests and Kotlin compilation; accept only APKs rebuilt after this gate.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Nimbo's branded Windows and Linux installers, package the user's signed Android APKs, and publish a verified GitHub prerelease with a user-facing Beta 3 changelog.

**Architecture:** The existing `apps/installer` scripts remain the only source of desktop installers: Windows payloads are built locally for every installed MSVC target and Linux x64 is built natively inside Ubuntu WSL. Release assets are staged in a Beta 3 directory, renamed consistently, hashed with SHA-256, and uploaded only after local build verification succeeds.

**Tech Stack:** Rust/Cargo, Tauri 2, PowerShell, WSL Ubuntu, Bash, Android APK artifacts, GitHub CLI.

---

### Task 1: Verify release inputs

**Files:**
- Inspect: `Cargo.toml`
- Inspect: `apps/ui/src-tauri/tauri.conf.json`
- Inspect: `apps/installer/src-tauri/tauri.conf.json`
- Inspect: `C:/Users/Danila/AndroidStudioProjects/Nimbo/app/release/`

- [ ] Confirm every desktop manifest is `1.1.0-beta.3` and Android APK filenames contain `1.1.0-beta.3`.
- [ ] Confirm `gh auth status` succeeds for `BBGGVP5` and `v1.1.0-beta.3` does not already exist.
- [ ] Confirm Android contains ARM64, ARMv7, and universal APKs with non-zero sizes.

### Task 2: Build branded desktop installers

**Files:**
- Build: `apps/installer/build-custom-installer.ps1`
- Build: `apps/installer/build-custom-linux-installer.sh`
- Output: `target/release/bundle/custom/windows/NimboSetup_1.1.0-beta.3_*.exe`
- Output: `target/release/bundle/custom/linux/NimboSetup_1.1.0-beta.3_x64`

- [ ] Run `powershell -NoProfile -ExecutionPolicy Bypass -File apps/installer/build-custom-installer.ps1 -All` and require at least the current x64 installer; include x86 and ARM64 when installed toolchains are available.
- [ ] Run `wsl -d Ubuntu-24.04 -- bash -lc "cd /mnt/c/Users/Danila/Desktop/nimbo-app-main && ./apps/installer/build-custom-linux-installer.sh --target x86_64-unknown-linux-gnu"`.
- [ ] Check every result has the Beta 3 filename, non-zero size, and a fresh modification time.

### Task 3: Stage and verify release assets

**Files:**
- Create: `target/release/publish/v1.1.0-beta.3/`
- Create: one `.sha256` file beside every release binary.

- [ ] Copy the custom desktop installers into the staging directory.
- [ ] Copy the user's APKs and rename them to `Nimbo_v1.1.0-beta.3_<abi>_release.apk`.
- [ ] Calculate SHA-256 for every APK and installer and write `<hash>  <filename>` to its sidecar.
- [ ] Verify each sidecar against the staged binary before any upload.

### Task 4: Write the user-facing changelog

**Files:**
- Create: `RELEASE_NOTES_1.1.0_BETA3.md`

- [ ] Add the same visual header, download badges, platform markers, warning callout, and download table used by Beta 2.
- [ ] Describe Android changes in plain language: Network 2.0 profiles, captive Wi-Fi helper, disconnect explanations, smart server groups, traffic limits, temporary quick rules, icon customization, glass cleanup, sync animations, navigation auto-hide, localization, and haptics.
- [ ] Describe Windows changes in plain language: quick rules from active connections, protected program launch, Explorer integration, tray controls, and reliability improvements.
- [ ] Do not expose internal implementation names, APIs, or debugging details.

### Task 5: Publish and audit the prerelease

**Files:**
- Read: `RELEASE_NOTES_1.1.0_BETA3.md`
- Upload: all files from `target/release/publish/v1.1.0-beta.3/`

- [ ] Create `v1.1.0-beta.3` as a prerelease named `Nimbo 1.1.0 Beta 3`, targeted at `main`, using the prepared notes.
- [ ] Upload all staged binaries and SHA-256 sidecars.
- [ ] Re-read the release through GitHub and confirm `isPrerelease=true`, every expected asset exists once, and the displayed digest matches the local SHA-256.
- [ ] Open the final release URL and report the exact published asset set.
