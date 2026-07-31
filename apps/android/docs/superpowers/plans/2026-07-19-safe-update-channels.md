# Safe Update Channels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add stable/beta update channels, cryptographically verify every downloaded APK, and offer a re-uploaded release asset even when its semantic app version did not change.

**Architecture:** GitHub release and asset metadata are converted into a durable artifact identity made from the asset id, update timestamp, digest, and size. Update policy remains pure and unit-testable; Android-specific download validation compares the SHA-256 digest, package id, version code, and signing certificate before opening the atomic system installer. Shared preferences record the selected channel plus pending/installed/notified artifact identities so a same-version replacement is offered once and confirmed only after Android reports a newer package `lastUpdateTime`.

**Tech Stack:** Kotlin, Android PackageManager, Jetpack Compose, WorkManager, OkHttp, Gson, JUnit 4.

---

### Task 1: Artifact metadata and update policy

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/network/UpdatePolicy.kt`
- Modify: `app/src/main/java/com/danila/nimbo/model/UpdateInfo.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdatePolicyTest.kt`

- [ ] **Step 1: Write failing policy tests**

Cover stable release filtering, beta inclusion, newer semantic versions, same-version changed artifact identities, already-installed identities, and the same-version repair changelog fallback:

```kotlin
assertNull(UpdatePolicy.decide("1.0.1", currentCode = 2, installedArtifactId = "asset-1", candidate = sameAsset))
assertEquals(UpdateKind.REPAIR, UpdatePolicy.decide("1.0.1", 2, "asset-1", changedAsset)?.kind)
assertEquals(UpdateKind.VERSION, UpdatePolicy.decide("1.0.1", 2, "asset-1", newerVersion)?.kind)
assertEquals("Исправленный файл релиза: исправления ошибок и улучшения стабильности.",
    UpdatePolicy.changelog("", UpdateKind.REPAIR, null, false))
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.danila.nimbo.network.UpdatePolicyTest`

Expected: compilation fails because `UpdatePolicy` and the artifact fields do not exist.

- [ ] **Step 3: Add the pure domain model and policy**

Add `UpdateChannel(STABLE, BETA)`, `UpdateKind(VERSION, REPAIR)`, `ReleaseCandidate`, `artifactIdentity()`, and:

```kotlin
internal fun decide(
    currentVersion: String,
    currentCode: Int,
    installedArtifactId: String?,
    candidate: ReleaseCandidate
): UpdateKind? = when {
    candidate.versionCode?.let { it > currentCode } == true -> UpdateKind.VERSION
    isSemanticVersionNewer(candidate.tagName, currentVersion) -> UpdateKind.VERSION
    normalizedVersionTag(candidate.tagName) == normalizedVersionTag(currentVersion) &&
        candidate.artifactIdentity() != installedArtifactId -> UpdateKind.REPAIR
    else -> null
}
```

Extend `UpdateInfo` with `channel`, `kind`, `artifactId`, `assetUpdatedAt`, `sha256`, `assetName`, and `releaseUrl` while retaining serializability and defaults.

- [ ] **Step 4: Run the policy tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.danila.nimbo.network.UpdatePolicyTest`

Expected: all `UpdatePolicyTest` tests pass.

### Task 2: Persist update channel and exact installed artifact

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/NebulaGuardApplication.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdatePolicyTest.kt`

- [ ] **Step 1: Add preference keys and accessors**

Add string properties for `updateChannel`, `installedUpdateArtifactId`, `pendingUpdateArtifactId`, `pendingUpdateVersionName`, and longs for `pendingUpdateStartedAt` and `pendingUpdatePackageTime`. Replace notification/dialog deduplication with the exact artifact identity, not only the release version.

- [ ] **Step 2: Confirm successful installation on process startup**

Call this before scheduling checks:

```kotlin
UpdateManager.confirmPendingInstallation(this)
```

The method promotes the pending artifact only when the installed version matches and `PackageInfo.lastUpdateTime` is newer than the value captured before invoking the installer. Cancelled installs therefore do not suppress the repair prompt.

- [ ] **Step 3: Re-run focused update tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.danila.nimbo.network.Update*'`

Expected: all focused update tests pass.

### Task 3: GitHub channels, timestamps, and cryptographic verification

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateWorker.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/UpdateManagerTest.kt`

- [ ] **Step 1: Add parsing tests for GitHub asset metadata**

Assert that numeric Gson ids, `updated_at`, `digest: sha256:...`, APK name/size, release `prerelease`, and `target_commitish` produce a stable artifact identity and the expected channel candidate.

- [ ] **Step 2: Fetch releases for the selected channel**

Change the API call to `GET /repos/BBGGVP5/nimbo/releases?per_page=20`, discard drafts, select the first stable release for `STABLE`, and include prereleases for `BETA`. Keep ABI preference when choosing an APK. When release notes are blank, fetch `GET /repos/BBGGVP5/nimbo/commits/{target_commitish}` and use the first commit-message line; use the localized repair fallback if that is also blank.

- [ ] **Step 3: Validate the downloaded APK before installation**

Download into a per-artifact temporary file and require all of the following before renaming it to the verified cache entry:

```kotlin
require(actualSize == updateInfo.fileSize)
require(expectedSha256 == sha256(apkFile))
require(archiveInfo.packageName == context.packageName)
require(archiveInfo.longVersionCode == updateInfo.versionCode.toLong())
require(archiveSignerDigests == installedSignerDigests)
```

If GitHub has no digest for an older asset, retain package/signature validation and mark the artifact as signature-verified. Never invoke the installer after a failed check. Record the pending artifact and current package update time only after validation succeeds.

- [ ] **Step 4: Use context-aware checks everywhere**

Update the worker and launch-time dialog to call `UpdateManager.checkUpdate(context)`. Deduplicate notifications and “Later” actions with `artifactId`, so a re-uploaded file for the same tag creates a new notification.

- [ ] **Step 5: Run update tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.danila.nimbo.network.Update*'`

Expected: all update tests pass.

### Task 4: Update settings and repair presentation

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/UpdateDialog.kt`

- [ ] **Step 1: Add the stable/beta selector**

Render a Material 3 single-choice segmented row under update settings. Persist `stable` or `beta`, clear the check throttle on change, and refresh immediately. Explain that beta may contain prereleases.

- [ ] **Step 2: Show exact artifact status**

For `UpdateKind.REPAIR`, render “Исправление текущей версии” / “Repair for current version”; show the asset update date, channel, and “SHA-256 + certificate” or “certificate” verification label. Keep release notes/commit message visible with a bug-fix fallback.

- [ ] **Step 3: Compile and run the complete unit suite**

Run: `./gradlew.bat :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL` and all unit tests pass.

- [ ] **Step 4: Build a debug APK**

Run: `./gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`; debug APK outputs are created under `app/build/outputs/apk/debug/`.

### Task 5: Rollback boundary and atomic failure behavior

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`

- [ ] **Step 1: Preserve the installed app on every pre-install failure**

Use a `.part` download, delete it after any size/digest/package/signature failure, and open Android's installer only for the verified final file. Android then performs the package replacement atomically; a rejected or interrupted installation leaves the installed version intact.

- [ ] **Step 2: State the runtime rollback limit in the UI**

Show a concise safety note: downloads are verified and failed installs keep the current version. Do not claim silent post-install rollback: ordinary self-updating apps cannot downgrade a successfully installed higher `versionCode` without privileged installer/device-owner rights and user intervention.

---

The checkout contains no `.git` directory, so commit steps are intentionally omitted; implementation remains split into independently testable tasks.
