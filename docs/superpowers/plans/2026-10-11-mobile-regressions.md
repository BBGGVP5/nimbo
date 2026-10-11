# Mobile regression stabilization implementation plan

> **For agentic workers:** Execute inline, in the existing checkout; preserve unrelated source and artifacts.

**Goal:** Prevent iOS subscription refresh from blocking the UI and remove the unverified Android DNS runtime change if it caused the reported connection regression.

**Architecture:** Keep all profile/Keychain reads and presentation JSON preparation on the subscription serial worker. Send a compact display-only snapshot to MainActor, rejecting obsolete results. Cancel app-process diagnostic pings before mutation and bound the wait for actual native retirement without pretending the native worker is idle. Keep existing subscription routing, selected servers and native pins unchanged.

**Tech Stack:** Swift concurrency/DispatchQueue, Foundation JSON, existing Kotlin Compose bridge, Android Kotlin, Python integration contracts and Apple CI.

## 1. Regressions and evidence

- [x] Test that a compact profile presentation removes raw server credentials/configuration but preserves IDs, names, protocol, description and title.
- [x] Test that a native worker which ignores cancellation does not block MainActor or falsely report idle; real retirement clears ownership.
- [x] Add source contracts: no Keychain/profile reads in Root presentation publication; reads use the repository queue; refresh cancels/drains diagnostics before importing.
- [x] Confirm whether the connection regression is Android after the new APK (question requested); do not modify unrelated DNS or provider parameters.

## 2. iOS implementation

**Create:** `iosApp/Shared/NimboProfilePresentationJSON.swift`, `NimboWorkerActivity.swift`, `iosApp/Tests/ProfilePresentationTests.swift`, `WorkerActivityTests.swift`.
**Modify:** `iosApp/Nimbo/RootView.swift`, `NimboSubscriptionRepository.swift`, `NimboDiagnosticProbe.swift`, `NimboPingService.swift`, `iosApp/Tests/test_subscription_refresh_contracts.py`, affected portable test runners.

- [x] Build the immutable presentation snapshot on `NimboSubscriptionRepository.workQueue`. Compact raw profile JSON before crossing into Kotlin UI; the stored original and transport configs are not altered.
- [x] Make Root presentation updates asynchronous and generation checked. Move remaining Root profile/source reads to async repository methods.
- [x] Track diagnostic requests through queue wait and native cleanup. Cancel them on refresh; wait asynchronously for at most three seconds, otherwise report a retryable busy message without writing a profile.
- [x] Do not read UserDefaults while holding the ping settings lease lock.
- [x] Preserve old profile on failed refresh; reject obsolete progress and snapshot publication. Record redacted stage codes, not keys or subscription URLs.

## 3. Android containment

- [x] Inspect the last DNS commit and restore the previous runtime path rather than adding more speculative DNS rewrites. Preserve evidence/tests in Git history and document the regression; never replace the user's freshly signed APK with a debug build.
- [x] Run Android regression tests after any rollback. Release replacement requires a verified signed rebuilt APK or an explicitly identified previous artifact.

## 4. Verification and delivery

- [x] Run Python source contracts, portable Swift behavior tests on Apple CI, and the full unsigned IPA build. Record unsupported local Swift/device tests accurately.
- [x] Verify embedded iOS revision, archive integrity and SHA-256 before replacing an asset. Leave Android/iOS assets not belonging to the verified replacement unchanged.
- [x] Mirror only hash-matched owned files, check diff and commit. A successful CI build is not evidence of carrier/iPhone runtime correctness.

## Manual device acceptance

- While pinging 74+ nodes, tap refresh; tabs/scroll/close must keep working. The ping must retire before profile changes, or refresh should explain that it is still stopping.
- Refresh with no VPN/no ping, unavailable subscription host, and restored Keychain data after reinstall. Failed refresh must retain the current profile.
- After fresh subscription data, connect/disconnect/reconnect and switch another VPN → Nimbo. Inspect the embedded build revision so stale same-version IPA results are not attributed to new code.

## Verified outcomes so far

- Android runtime rollback: commit `6eefbba`. Removed the entire unverified loopback/system-DNS adapter, not just a UI flag. Native core binary and provider DNS remain unchanged.
- Public Beta 1 restored the exact previously published Android ARM64/ARMv7/Universal APKs plus checksums; old certificate and original digests reverified. All 22 other assets and release text were preserved. No previous-user-data removal is required.
- iOS runtime/UI patch: `36e827a`; CI dependency-only follow-up: `c818f49`. Snapshot publication is off-main, compact display JSON excludes connection secrets, obsolete reads/progress are rejected, diagnostics retain actual lifetime ownership, and pending pings receive a bounded retryable error before mutation.
- Notification cards now mirror the Android compact icon/message/48-point-close layout. Unique new IDs plus deterministic migration repair old duplicates; identity-scoped expiry cannot dismiss a later toast. Full text remains in history.
- Local verification: **638 Android JVM tests**, **12 shared rendered/policy tests**, **116 current iOS source contracts**, all passed. Two historical scanner/UI audit modules are not part of the current workflow; their pre-existing fixed-snapshot/four-file-scope checks do not match subsequent redesign work and were not weakened to obtain a passing count.
- First Apple run `38134304733` passed all portable/native Swift checks, including the real diagnostic mock which ignores Cancel and the new presentation/worker tests. It stopped because the newly added Python source-contract step lacked tree-sitter dependencies, before IPA compilation. The dependency-only correction pins the same locally used parser versions in an isolated CI environment.
- Full corrected Apple build **38134637046** succeeded, including Swift policy/typecheck checks, Kotlin iOS framework, Xcode compilation and IPA packaging. No fresh IPA was published before this build and integrity/revision verification complete. Physical iPhone and carrier-network behavior remain device acceptance tests, not something claimed by source contracts or CI.
- Local evidence: `.codex-tmp/mobile-regressions-20261011/verification.json`; public Android rollback evidence: `artifacts/android/rollback-20261011/verification.json`.

- Apple run: https://github.com/BBGGVP5/nimbo/actions/runs/38134637046 ; artifact source revision is expected to be `c818f494a5d7`. IPA integrity/revision and public replacement are the remaining checks.

## Final publication

- IPA revision `c818f494a5d7`, version 1.3.0/build 180, SHA-256 `3bae1105100ff79120d1590d076802414a0283bb38fc4b43a8ce095c02d0d7cd`. Full ZIP integrity, all three cloud icon catalogs and packaged extensions verified.
- Beta 1 IPA and checksum replaced; all **26 other assets** (including the restored Android files) and release text/tag preserved. Physical iPhone verification is still required, especially ping + refresh and repeated connect/disconnect.
