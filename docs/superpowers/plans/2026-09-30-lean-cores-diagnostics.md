# Lean cores and private diagnostics implementation plan

> **For agentic workers:** Execute task-by-task in this session; unavailable superpowers skills are not required to block implementation.

**Goal:** Remove mesh adapters from compiled Mihomo, normalize expensive smart groups, stop fetching the emergency subscription pool, export private diagnostic files, and refresh LibXray from the verified release.

**Architecture:** Use upstream built-in mesh exclusion tags on verified staging copies of Mihomo; keep existing reviewed lifecycle/Reality patches. Keep original subscription YAML and its source hash intact; derive a separate effective runtime root. Diagnostics use a fail-closed structural projection, never raw logs or raw HTTP headers.

**Tech Stack:** Kotlin/Compose, Go 1.27.1, Python staging/verifiers, Gradle, existing website PHP/HTML exporter.

---

### Task 1: Native size and smart groups
- [x] Verified upstream already supplies `no_tailscale`, `no_zerotier`, `no_easytier` tags and rejecting stubs; no duplicate custom patch required. Explicit admission rejection and regression test added.
- [x] Enforce `no_tailscale,no_zerotier,no_easytier` in Android/Apple/desktop source builders. PowerShell parser checks pass, comma-containing native arguments quoted.
- [x] Add `tools/native/mihomo-core/effective.go` and tests. Normalize only `type: smart` to `type: url-test`, `interval: 600`, `lazy: true`, `tolerance: 100`; remove only known smart-specific options. Retain all members, use lists, rules and original source hash.
- [x] Run `go test -tags=with_gvisor,no_tailscale,no_zerotier,no_easytier ./...` against a freshly staged patched module; prove `go list -deps` excludes mesh modules but retains ordinary WireGuard.

### Task 2: Subscription pool
- [x] Remove `mergeFallbackPool` from `app/src/main/java/com/danila/nimbo/network/SubscriptionManager.kt`, ignore nimbo-fallback headers, leave ordinary subscription rules/whitelist untouched.
- [x] Do not remove native fallback groups or turn failures into DIRECT: those are unrelated routing semantics.

### Task 3: Diagnostic export
- [x] Add pure `SupportDiagnosticRedactor.kt`: parse JSON into safe field projection; unknown string values become `[redacted]`. YAML is exported through Mihomo inspect's JSON root projection, not regex sanitization of arbitrary YAML.
- [x] Tests assert secrets, custom header values, addresses, labels and embedded links never appear; keep protocol, transport, types, booleans and numeric tuning settings.
- [x] Add `SupportDiagnosticStore.kt`: bounded already-redacted files in cache, effective core config snapshot, header-name-only snapshot. No network request on export, no raw logcat.
- [x] Add hidden Developer settings section after five taps within two seconds on settings top bar; button runs export on IO and opens Android share chooser with explicit URI grants.
- [x] Run targeted JUnit plus `:app:compileDebugKotlin`; full Android/shared tests and APK assembly also pass. Final cache-retention tests/rebuild also passed.

### Task 4: Core update and artifacts
- [x] Verify LibXray 26.9.30 commit `3c694b23290f9849fe52284a345ebd4343bc90cd` and codeload SHA256 `0b9162518c1eb2aadca13f39e63e9c5f7c904c4843ad6bc8f8251f2d1f99c185` before build.
- [x] Update source pins and merged root graph to upstream Xray `v1.260327.1-0.20260930074004-b26a91de4f32`, preserve AWG and custom bridge exports.
- [x] Build verified merged AAR in fresh staging; promote only after JNI, ABI and 16KiB checks. Record compressed and uncompressed arm64 native bytes against previous AAR (no phone installation).
- [x] Build website PHP/HTML archive with all earlier edits using `node tools/build-php-html.mjs 1.3.0-beta.1-updated`; verify contents contain new cursor/links/protocol table.
- [x] Report actual achieved changes and remaining platform/device verification, never repeat 8/24MiB estimates as measurements.

## Verification / delivery record

- Android AAR promoted after native staged verifier: `822653121099b6d6582b385035c09aa97975de5ad88b5d9599ea6e2b2db2b326`. Existing AAR backed up in native staging. Four ABIs, API-3/JNI compatibility and 16 KiB alignment verified.
- Latest Android/shared full unit tests and `assembleDebug`: successful; log `.codex-tmp/lean-final-build-20260930.log`. Additional export-cache retention tests and final rerun also passed (48 seconds), tracked in same log.
- Host Go tests against verified patched source with all four tags: passed. Mesh exclusion also verified in the actual compiled AAR build metadata.
- Apple source/build regression tests: 6 + 6 passed. Combined AWG source/C-ABI contract check passed. No new IPA or device run.
- Native staging/AAR verifier tests: 9 passed; Android and desktop PowerShell source builders parse successfully.
- Actual packaged arm64 native comparison against September 28 APK: identical NDK strip output proven for both baselines. Native payload went from 93,769,496 to 71,501,272 bytes unpacked and 34,073,772 to 25,957,095 bytes compressed. Saved 22,268,224 raw native bytes / 8,116,677 packed bytes. Whole APK comparison includes intervening app edits; total installed size is NOT measured.
- Source/original config hash is preserved; normalized smart groups affect only effective graph/runtime. Subscription-defined whitelist and fallback groups remain; external emergency pool fetching/cached members removed.
- Report scope is explicitly "last prepared configuration": no connection/network lookup is started by export. Only already-redacted snapshots reach disk; unavailable snapshots are marked, never raw-fallback. Last ping fixed-vocabulary phases/failure are included.
- Website archive: `C:/Users/Danila/Documents/Nimbo-site/release/nimbo-site-php-html-1.3.0-beta.1-updated-2026-09-30.zip`, 40 entries, verified. Not deployed.
- Remaining: real phone/SystemUI/tunnel tests; IPA/macOS archive build and signing; desktop redistribution. Current Android/iOS/desktop changes are published in PR #77; IPA CI is running. Website archive remains a separate local export.
