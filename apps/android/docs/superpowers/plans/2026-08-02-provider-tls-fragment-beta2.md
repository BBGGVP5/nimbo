# Provider TLS Fragment and Beta 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add validated, subscription-controlled TLS ClientHello fragmentation to Android, Windows, and Linux, then prepare and publish the Nimbo 1.1.0 Beta 2 prerelease.

**Architecture:** Both clients parse the same optional `Nimbo-TLS-Fragment` response header into a persisted per-subscription value. The active subscription overrides the manual fallback toggle; generated Xray configuration receives a dedicated freedom dialer with validated `packets`, `length`, and `interval` values. Release notes remain user-facing and platform-aware.

**Tech Stack:** Kotlin, JUnit, Android/Compose, Rust, Cargo tests, Tauri/React, GitHub CLI.

---

### Task 1: Shared Android header contract

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/network/TlsFragmentConfig.kt`
- Create: `app/src/test/java/com/danila/nimbo/network/TlsFragmentConfigTest.kt`

- [ ] **Step 1: Write failing parser tests**

```kotlin
assertEquals(
    TlsFragmentConfig(true, "tlshello", "100-200", "10-20"),
    TlsFragmentConfig.parse("enabled=true; packets=tlshello; length=100-200; interval=10-20")
)
assertEquals(TlsFragmentConfig(enabled = false), TlsFragmentConfig.parse("off"))
assertNull(TlsFragmentConfig.parse("enabled=true; length=bad"))
```

- [ ] **Step 2: Run test and confirm missing type failure**

Run: `./gradlew testDebugUnitTest --tests com.danila.nimbo.network.TlsFragmentConfigTest`

- [ ] **Step 3: Implement strict parsing and defaults**

Support key/value and compact comma formats. Accept `tlshello` or a numeric packet range, length values from 1–1024, and interval values from 0–1000.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew testDebugUnitTest --tests com.danila.nimbo.network.TlsFragmentConfigTest`
Expected: PASS.

### Task 2: Persist Android provider parameters

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/network/SubscriptionManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/ProfilesScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/MainViewModel.kt`

- [ ] **Step 1: Parse provider headers**

Read, in order, `nimbo-tls-fragment`, `x-nimbo-tls-fragment`, and `dropweb-tls-fragment`.

- [ ] **Step 2: Add the optional value to `SubscriptionInfo` and `SubscriptionProfile`**

```kotlin
val tlsFragment: TlsFragmentConfig? = null
```

- [ ] **Step 3: Replace the value after every successful refresh**

An absent header clears the previous provider override; a failed refresh keeps the previous working profile.

### Task 3: Apply Android fragmentation to all generated configurations

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/vpn/XrayManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [ ] **Step 1: Resolve provider config from the selected server profile**

Provider `enabled` overrides the manual preference. Without provider data, use the existing manual toggle and safe defaults.

- [ ] **Step 2: Apply the fragment dialer to generated and template configurations**

Add one `freedom` outbound and attach its tag to every real proxy outbound through `streamSettings.sockopt.dialerProxy`.

- [ ] **Step 3: Explain the fallback toggle in the UI**

Rename the description so users understand that provider parameters are automatic and the switch is only for subscriptions without them.

### Task 4: Parse and persist the same contract on Desktop

**Files:**
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/crates/subscription/src/model.rs`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/crates/subscription/src/fetcher.rs`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/crates/subscription/src/lib.rs`

- [ ] **Step 1: Add Rust parser tests**

Test key/value, compact, `off`, invalid range, and missing header cases.

- [ ] **Step 2: Implement `TlsFragmentConfig` and header extraction**

Persist it in `SubscriptionMeta` so refreshes replace provider values atomically.

- [ ] **Step 3: Run subscription crate tests**

Run: `cargo test -p nimbo-subscription`
Expected: PASS.

### Task 5: Apply provider parameters in Desktop Xray config

**Files:**
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src-tauri/src/commands.rs`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/pages/Settings.tsx`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src/lib/i18n.ts`

- [ ] **Step 1: Add config-generation tests**

Verify provider enable overrides local off, provider off overrides local on, exact ranges reach Xray, and every proxy outbound uses the fragment dialer.

- [ ] **Step 2: Pass active subscription metadata into runtime preferences**

Use the subscription that owns the selected server, not a global or first-subscription value.

- [ ] **Step 3: Update settings copy and run tests**

Run: `cargo test -p nimbo-ui` and `npm run build` in `apps/ui`.

### Task 6: Version, release notes, and prerelease

**Files:**
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/Cargo.toml`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/Cargo.lock`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/package.json`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/package-lock.json`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/installer/package.json`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/installer/package-lock.json`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/ui/src-tauri/tauri.conf.json`
- Modify: `C:/Users/Danila/Desktop/nimbo-app-main/apps/installer/src-tauri/tauri.conf.json`
- Create: `C:/Users/Danila/Desktop/nimbo-app-main/RELEASE_NOTES_1.1.0_BETA2.md`

- [ ] **Step 1: Bump Desktop to `1.1.0-beta.2`**

- [ ] **Step 2: Write a user-facing changelog**

Lead with automatic TLS bypass, then cover adaptive/custom icons, animated synchronization, bottom-bar behavior, and visual fixes. Do not expose implementation details.

- [ ] **Step 3: Build available Desktop artifacts and SHA-256 files**

Use the repository's custom installer scripts; do not substitute stock Tauri installers.

- [ ] **Step 4: Publish `v1.1.0-beta.2` as a prerelease**

Create the GitHub prerelease after source is pushed. Upload Android APK files when the user supplies their signed build.
