# Cross-Platform User Changelog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a user-facing Nimbo 1.0.2 changelog to Android and Desktop, and make both applications show the correct platform-specific text when GitHub release notes are absent.

**Architecture:** Each application owns a small version-keyed release-notes registry with Russian and English text. GitHub release notes remain authoritative when present; bundled notes provide an offline fallback for update, current-version, and post-install screens. A matching `CHANGELOG.md` documents the release for repository readers.

**Tech Stack:** Kotlin, Jetpack Compose, Rust, Tauri, React/TypeScript, Markdown.

---

### Task 1: Android bundled release notes

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/network/BundledReleaseNotes.kt`
- Modify: `app/src/main/java/com/danila/nimbo/network/UpdateManager.kt`
- Test: `app/src/test/java/com/danila/nimbo/network/BundledReleaseNotesTest.kt`

- [ ] **Step 1: Write a failing version-selection test**

```kotlin
assertTrue(BundledReleaseNotes.forVersion("v1.0.2", false).orEmpty().contains("Проверка БС"))
assertTrue(BundledReleaseNotes.forVersion("1.0.2", true).orEmpty().contains("Allowlist check"))
assertNull(BundledReleaseNotes.forVersion("9.9.9", false))
```

- [ ] **Step 2: Run the test and verify that `BundledReleaseNotes` is unresolved**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*BundledReleaseNotesTest"`

Expected: FAIL because the registry does not exist.

- [ ] **Step 3: Implement the Android registry and fallback**

Create a registry keyed by normalized `1.0.2`, return Russian or English Markdown, and use it when filtered GitHub release notes are blank in both `checkUpdate` and `getReleaseInfoForTag`.

- [ ] **Step 4: Run Android tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: BUILD SUCCESSFUL.

### Task 2: Desktop bundled release notes

**Files:**
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src-tauri/src/updater.rs`

- [ ] **Step 1: Write a failing Rust test**

```rust
assert!(bundled_release_notes("v1.0.2", Language::Ru).unwrap().contains("Проверка БС"));
assert!(bundled_release_notes("1.0.2", Language::En).unwrap().contains("Allowlist check"));
assert!(bundled_release_notes("9.9.9", Language::Ru).is_none());
```

- [ ] **Step 2: Run the focused test**

Run: `cargo test -p nimbo-ui updater::tests::bundled_changelog_is_versioned_and_localized`

Expected: FAIL because `bundled_release_notes` does not exist.

- [ ] **Step 3: Implement and connect the Desktop fallback**

Add localized Markdown for 1.0.2 and use it when GitHub has no release body during update checks and installation receipt creation.

- [ ] **Step 4: Run Desktop tests**

Run: `cargo test -p nimbo-ui`

Expected: all tests pass.

### Task 3: Repository changelog documents and release builds

**Files:**
- Create: `CHANGELOG.md`
- Create: `C:/Users/Danila/Desktop/nimbo-app-main/CHANGELOG.md`

- [ ] **Step 1: Add matching 1.0.2 user changelogs**

Document common changes and separate Android/Desktop sections without internal implementation terminology.

- [ ] **Step 2: Verify Android**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL and architecture-specific debug APKs are generated.

- [ ] **Step 3: Verify Desktop**

Run: `npm run build` in `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui`.

Expected: TypeScript and Vite production build complete successfully.

