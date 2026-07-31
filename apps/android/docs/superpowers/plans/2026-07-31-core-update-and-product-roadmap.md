# Nimbo Core Update and Product Roadmap Implementation Plan

> **For Codex:** Execute the verification and replacement steps in order. Do not replace a native artifact unless its official digest, archive structure, and application API are all validated.

**Goal:** Safely bring the Android and Desktop Xray runtimes to their latest stable official releases, verify both projects, and prepare a practical cross-platform feature roadmap without implementing unrequested product features.

**Architecture:** Android embeds the official `XTLS/libXray` AAR and talks to it through the single `LibXray.invoke` JNI API. Desktop embeds architecture-specific official `XTLS/Xray-core` payloads in installers and can also download a missing runtime after validating the release `.dgst`. The update procedure must preserve these two different integration models.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose, libXray AAR, Rust, Tauri 2, React/TypeScript, PowerShell, GitHub Releases, SHA-256.

---

### Task 1: Record the installed and official core versions

**Files:**
- Inspect: `app/libs/libxray.aar`
- Inspect: `app/src/main/java/com/danila/nimbo/vpn/XrayCoreProtocol.kt`
- Inspect: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Inspect: `C:/Users/Danila/Desktop/nimbo-app-main/target/xray/*/xray.exe`
- Inspect: `C:/Users/Danila/Desktop/nimbo-app-main/apps/installer/build-custom-installer.ps1`
- Inspect: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src-tauri/src/commands.rs`

**Step 1: Hash and inspect the embedded Android AAR**

Record the SHA-256, archive members, supported ABIs, and current version references. Confirm that the app uses `libXray.LibXray.invoke` and not a different native API.

**Step 2: Query official stable releases**

Use the official GitHub Releases API for `XTLS/libXray` and `XTLS/Xray-core`. Reject draft or prerelease entries and record each tag, publication time, asset URL, and official asset digest.

**Step 3: Inspect every Desktop architecture**

Run `xray.exe version` on compatible local binaries and validate the three embedded payload hashes. Confirm that x64, x86, and ARM64 packages correspond to the current official release.

### Task 2: Validate the candidate Android library before replacement

**Files:**
- Modify after validation: `app/libs/libxray.aar`

**Step 1: Download to an isolated temporary directory**

Download only `libxray-android.zip` from the selected stable release. Do not write the candidate directly over the project artifact.

**Step 2: Verify the official SHA-256**

Compare the downloaded archive hash with the `digest` field returned by GitHub Releases. Abort on any mismatch.

**Step 3: Verify packaging and compatibility**

Require exactly one AAR, all four expected Android JNI ABIs, `classes.jar`, and the existing `libXray.LibXray.invoke` API. Compare public Java signatures with the current AAR before replacement.

**Step 4: Replace atomically through the patch workflow**

Copy the verified AAR to `app/libs/libxray.aar`, then record its final SHA-256. Preserve the old digest in the execution report so the artifact can be restored from source history or the previous official release.

### Task 3: Keep application metadata accurate

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/XrayCoreProtocol.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Modify: `app/src/test/java/com/danila/nimbo/vpn/XrayCoreProtocolTest.kt`
- Modify if needed: `app/src/test/java/com/danila/nimbo/utils/XrayVersionFormatterTest.kt`

**Step 1: Update integration documentation**

Change stale `26.7.11+` references to `26.7.28+` only where they describe the libXray wrapper contract.

**Step 2: Correct the About screen label**

Do not present a libXray release tag as an Xray-core version. Display the embedded wrapper and underlying core versions separately, or use an unambiguous `libXray` label.

**Step 3: Update focused tests**

Keep the existing invocation-envelope test aligned with the new supported wrapper baseline. Retain the formatter coverage for the independent Xray-core version string.

### Task 4: Verify Android behavior and packaging

**Files:**
- Verify: Android unit tests and debug APK

**Step 1: Run unit tests**

Run `gradlew.bat testDebugUnitTest` and require a successful result.

**Step 2: Build the APK**

Run `gradlew.bat assembleDebug`. Inspect the resulting APK to confirm the expected native ABIs are packaged and no duplicate native libraries were introduced.

**Step 3: Record artifact hashes**

Record SHA-256 for the final AAR and debug APK in the handoff.

### Task 5: Verify Desktop remains current and buildable

**Files:**
- Verify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui`
- Verify: `C:/Users/Danila/Desktop/nimbo-app-main/crates`
- Verify: `C:/Users/Danila/Desktop/nimbo-app-main/target/xray`

**Step 1: Do not rewrite current binaries**

If the embedded version equals the latest official stable Xray-core release and the architecture mapping is correct, leave the payloads byte-for-byte unchanged.

**Step 2: Run focused runtime updater tests**

Run the Rust tests that cover Xray archive digest parsing, archive installation, and platform asset selection. Expand to the workspace test suite if practical.

**Step 3: Build the Desktop UI**

Use the repository's lockfile-selected package manager and build script. Do not regenerate dependencies or lockfiles unless required.

### Task 6: Prepare the product roadmap

**Files:**
- Inspect: Android and Desktop settings, diagnostics, routing, update, subscription, and connection features

**Step 1: Avoid duplicate suggestions**

Compare ideas against already implemented features such as stable/beta updates, signed/digest validation, split routing, connection diagnostics, whitelist checks, haptics, glass themes, server tests, and subscription refresh.

**Step 2: Prioritize outcomes**

Group proposals into a short next-release tier, a medium-term tier, and optional experiments. Give each proposal its user benefit and implementation risk.

**Step 3: Hand off verified facts**

Report exactly what changed, what was already current, test/build results, remaining device-only checks, and links to the official releases used for verification.
