# Visual Icon Controls and Beta 2 Republish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace text-only icon constructor controls with visual previews, add an arbitrary background color palette, then republish signed Android APKs and a rich Telegram Beta 2 post.

**Architecture:** Keep icon rendering in `CustomAppIconManager` as the single source of truth and make the Compose screen render every shape/cloud option through that renderer. Add a self-contained HSV picker dialog to the icon screen, persist the resulting ARGB value through the existing preferences, then validate and upload only freshly rebuilt signed APKs. Reuse the existing bot's Telegram publishing path and release poster for a formatted topic post.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android Canvas/HSV colors, Gradle, GitHub CLI, Python Telegram bot.

---

### Task 1: Visual shape and cloud selectors

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppIconSettingsScreen.kt`
- Test: `app/src/test/java/com/danila/nimbo/utils/CustomAppIconPresetTest.kt`

- [x] **Step 1: Replace the text-only shape row**

Render one real icon bitmap for each `CustomIconShape`, using the current background, cloud color and cloud style. Present the three bitmaps in equal square cards with selection border and accessibility descriptions; do not render `Сквиркл`, `Скруглённая` and `Круг` as the control contents.

- [x] **Step 2: Replace the text-only cloud row**

Render one real icon bitmap for every `CustomCloudStyle`, using the current shape and colors. Present the three cloud treatments as visual cards with selection state and accessibility descriptions; do not use the style names as the visible controls.

- [x] **Step 3: Verify rendering behavior**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`; existing preset/config tests remain green and Compose compilation accepts the new selectors.

### Task 2: Full background color palette

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppIconSettingsScreen.kt`

- [x] **Step 1: Add a palette entry beside preset colors**

Keep the useful preset swatches and append a multicolor palette control. Selecting a preset applies it immediately; selecting the palette opens the full picker.

- [x] **Step 2: Add a touch-driven HSV picker**

Implement a Compose dialog with a saturation/value field, a full hue strip, live icon preview, cancel and apply actions. Convert the selected HSV value to opaque ARGB before saving.

- [x] **Step 3: Persist and verify the arbitrary color**

Reuse `preferencesManager.customIconBackgroundColor`; verify a non-preset selected value updates the main preview and survives screen recreation.

- [x] **Step 4: Run Android verification**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL` with no resource or Kotlin errors.

### Task 3: Publish source and signed APK replacements

**Files:**
- Modify: `apps/android/app/src/main/java/com/danila/nimbo/ui/screens/AppIconSettingsScreen.kt` in the publication clone
- Modify: `RELEASE_NOTES_1.1.0_BETA2.md`

- [x] **Step 1: Sync the verified Android source**

Copy only source/configuration changes into the clean publication clone, excluding Gradle caches, IDE state, build outputs and signing material.

- [x] **Step 2: Update user-facing Beta 2 notes**

Add a short Android item explaining that icon shape/cloud choices are now visual and any background color can be selected from a full palette.

- [x] **Step 3: Commit and push**

Run `git diff --cached --check`, commit the source and notes, and push `main`.

- [ ] **Step 4: Validate newly rebuilt APKs**

For ARM64, ARMv7 and Universal, verify `versionName=1.1.0-beta.2`, `versionCode=6`, ABI contents, APK v2 signature and signer certificate.

- [ ] **Step 5: Replace release assets**

Generate fresh `.sha256` sidecars and upload all six files with `gh release upload v1.1.0-beta.2 --clobber`. Confirm GitHub digests match local APK hashes.

### Task 4: Rich Telegram Beta 2 post

**Files:**
- Inspect/modify only if required: bot publishing scripts under `C:/Users/Danila/Desktop/backup remnawave/NebulaAI_Security`

- [x] **Step 1: Prepare concise rich text**

Use headings and user-facing sections for TLS bypass from subscription, icon customization, synchronization, interface polish and platform downloads. Avoid internal implementation details.

- [x] **Step 2: Publish to topic 6135**

Use the bot's existing credentials and topic-posting mechanism, attach the existing Nimbo poster, enable rich formatting and release/download buttons.

- [x] **Step 3: Verify Telegram response**

Confirm the Bot API returns success and record the resulting message identifier; do not start a second bot polling process.

---

Self-review: all requested visual controls, arbitrary background palette, APK replacement and rich Beta 2 post are covered. No signing credentials or private subscription data are copied into the public repository.
