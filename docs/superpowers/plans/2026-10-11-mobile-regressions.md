# Mobile regression stabilization implementation plan

> **For agentic workers:** Execute inline, in the existing checkout; preserve unrelated source and artifacts.

**Goal:** Prevent iOS subscription refresh from blocking the UI and remove the unverified Android DNS runtime change if it caused the reported connection regression.

**Architecture:** Keep all profile/Keychain reads and presentation JSON preparation on the subscription serial worker. Send a compact display-only snapshot to MainActor, rejecting obsolete results. Cancel app-process diagnostic pings before mutation and bound the wait for actual native retirement without pretending the native worker is idle. Keep existing subscription routing, selected servers and native pins unchanged.

**Tech Stack:** Swift concurrency/DispatchQueue, Foundation JSON, existing Kotlin Compose bridge, Android Kotlin, Python integration contracts and Apple CI.

## 1. Regressions and evidence

- [ ] Test that a compact profile presentation removes raw server credentials/configuration but preserves IDs, names, protocol, description and title.
- [ ] Test that a native worker which ignores cancellation does not block MainActor or falsely report idle; real retirement clears ownership.
- [ ] Add source contracts: no Keychain/profile reads in Root presentation publication; reads use the repository queue; refresh cancels/drains diagnostics before importing.
- [ ] Confirm whether the connection regression is Android after the new APK (question requested); do not modify unrelated DNS or provider parameters.

## 2. iOS implementation

**Create:** `iosApp/Shared/NimboProfilePresentationJSON.swift`, `NimboWorkerActivity.swift`, `iosApp/Tests/ProfilePresentationTests.swift`, `WorkerActivityTests.swift`.
**Modify:** `iosApp/Nimbo/RootView.swift`, `NimboSubscriptionRepository.swift`, `NimboDiagnosticProbe.swift`, `NimboPingService.swift`, `iosApp/Tests/test_subscription_refresh_contracts.py`, affected portable test runners.

- [ ] Build the immutable presentation snapshot on `NimboSubscriptionRepository.workQueue`. Compact raw profile JSON before crossing into Kotlin UI; the stored original and transport configs are not altered.
- [ ] Make Root presentation updates asynchronous and generation checked. Move remaining Root profile/source reads to async repository methods.
- [ ] Track diagnostic requests through queue wait and native cleanup. Cancel them on refresh; wait asynchronously for at most three seconds, otherwise report a retryable busy message without writing a profile.
- [ ] Do not read UserDefaults while holding the ping settings lease lock.
- [ ] Preserve old profile on failed refresh; reject obsolete progress and snapshot publication. Record redacted stage codes, not keys or subscription URLs.

## 3. Android containment

- [ ] Inspect the last DNS commit and restore the previous runtime path rather than adding more speculative DNS rewrites. Preserve evidence/tests in Git history and document the regression; never replace the user's freshly signed APK with a debug build.
- [ ] Run Android regression tests after any rollback. Release replacement requires a verified signed rebuilt APK or an explicitly identified previous artifact.

## 4. Verification and delivery

- [ ] Run Python source contracts, portable Swift behavior tests on Apple CI, and the full unsigned IPA build. Record unsupported local Swift/device tests accurately.
- [ ] Verify embedded iOS revision, archive integrity and SHA-256 before replacing an asset. Leave Android/iOS assets not belonging to the verified replacement unchanged.
- [ ] Mirror only hash-matched owned files, check diff and commit. A successful CI build is not evidence of carrier/iPhone runtime correctness.

## Manual device acceptance

- While pinging 74+ nodes, tap refresh; tabs/scroll/close must keep working. The ping must retire before profile changes, or refresh should explain that it is still stopping.
- Refresh with no VPN/no ping, unavailable subscription host, and restored Keychain data after reinstall. Failed refresh must retain the current profile.
- After fresh subscription data, connect/disconnect/reconnect and switch another VPN → Nimbo. Inspect the embedded build revision so stale same-version IPA results are not attributed to new code.
