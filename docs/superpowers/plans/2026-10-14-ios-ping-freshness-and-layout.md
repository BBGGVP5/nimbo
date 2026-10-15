# iOS ping freshness, blocked-network preflight and layout implementation plan

> **For agentic workers:** Execute this plan inline. No delegation or worktree creation is needed; reuse the existing checkout and preserve unrelated native-resource edits.

**Goal:** Reduce time spent on unreachable TCP endpoints, remove stale successful ping values and keep latency clear of server names.

**Architecture:** Keep native diagnostic lifetimes serialized because Xray has process globals. A bounded eight-request TCP preflight can only reject a proven TCP endpoint; it can never produce a successful latency. The immutable configuration remains authoritative, with unknown/UDP/QUIC/chained configurations bypassing preflight. Clear requested cached measurements when a new run begins, and clear/cancel all measurements when the observed network or VPN context changes. Render names and latency in disjoint rows.

**Tech Stack:** Swift concurrency, Network framework, Kotlin/Compose Multiplatform, Python source contracts, Apple CI.

---

### Task 1: Conservative preflight
**Files:** `iosApp/Shared/NimboPingPolicy.swift`, `iosApp/Nimbo/NimboPingService.swift`, `iosApp/Tests/PingPolicyTests.swift`, `iosApp/Tests/PingDiagnosticTests.swift`, `iosApp/Tests/test_ping_contracts.py`.
- [ ] Add endpoint-policy fixtures for explicit TCP VLESS/SS, JSON host/port identity, unknown transports, QUIC and alternate download routes.
- [ ] Inject an async endpoint reachability probe. Test eight-way bounded scheduling, rejected-only failure publication, unchanged per-server HTTP proof, cancellation and settings invalidation.
- [ ] Implement conservative endpoint eligibility and sliding-window negative preflight. Keep serial native work and full configured per-attempt timeout.
- [ ] Run `python iosApp/Tests/test_ping_contracts.py`; compile/execute production Swift through the existing `--swift` Apple workflow.

### Task 2: Freshness
**Files:** `shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboPingPresentation.kt`, `shared/src/iosMain/kotlin/com/danila/nimbo/shared/ui/IosComposeController.kt`, `iosApp/Nimbo/NimboPingNetworkMonitor.swift`, `iosApp/Nimbo/RootView.swift`, ping unit/source tests.
- [ ] Test `invalidatingPingIds(mapOf("a" to 10, "b" to 20), setOf("a")) == mapOf("b" to 20)` and cancellation keeping an unattempted row unknown, not restoring a previous success.
- [ ] Remove requested old samples from memory and persisted ranking data before beginning a run. Provide `NimboClearIosPings()` for network invalidation.
- [ ] Observe a deduplicated NWPath signature off-main; invalidate Root run identity and shared samples on network/VPN transitions. First monitor event establishes a baseline, not a false change.
- [ ] Run shared ping tests and subscription-refresh/source contracts to protect prior deadlock repairs.

### Task 3: Separate name and latency
**Files:** `shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboProfilesScreen.kt`, `shared/src/desktopTest/kotlin/com/danila/nimbo/shared/ui/NimboServerPingLayoutTest.kt`.
- [ ] Render real ProfileServerCard at 320/390/430px and font scales 1.0/1.5/2.0 with long server labels and all badge modes.
- [ ] Assert text layout stays bounded, name/ping rectangles do not intersect and favourite hit target remains >=44dp.
- [ ] Use a name/favourite header and a distinct description/latency footer; stack the footer at narrow widths/large fonts. Do not reduce touch targets.
- [ ] Run `gradlew.bat :shared:desktopTest --tests *NimboServerPingLayoutTest --tests *NimboPingPresentationTest`; inspect rendered PNGs.

### Verification and delivery
- [ ] Copy only changed app source/tests into the primary Android Studio workspace after verifying originals match (preserve its pre-existing ping-contract variant).
- [ ] Check diff/whitespace and protected changes, then push the focused commit and dispatch the private Apple build for compiled Swift/iOS validation.
- [ ] Do not replace public Beta assets in this turn: user requested investigation/fixes, not a new publication.
- [ ] Report evidence and limitations: no carrier white-list or physical-iPhone test, a successful HTTP probe only proves this target works on the current route at that instant.

## Execution evidence (14 October 2026)

- Implemented in `3d144bf849878d9f2c0586b84aea5a2b8f9d93f9`; native Go, DNS and VPN startup code unchanged.
- 40 targeted Python source checks passed (ping 20, profile screen 6, refresh 7, design 4, iPad 3).
- 21 executed Kotlin/Compose tests passed across ping presentation, 36 layout variants, server context actions, mobile feedback and redesign interactions. Rendered 390px/100% and 320px/200% outputs were inspected.
- Primary workspace synchronisation verified; its pre-existing source-runner variant retained and augmented rather than overwritten. Protected unrelated LICENSE hash unchanged.
- Private Apple compilation/build dispatched: https://github.com/BBGGVP5/nimbo/actions/runs/38562413747. Its result remains pending until reported below. Public release assets not replaced.
- A positive result proves one HTTP control request through the selected outbound on the current route, not that every website works. A connected system VPN can change that route; no physical-device/white-list carrier test is claimed.

### Apple Swift verification
- Passed on macOS: https://github.com/BBGGVP5/nimbo/actions/runs/38564061831. The registered build workflow now has an optional `ping_only` job; its full-build default is unchanged.
- Executed actual production Swift policy/service/adapter code with local HTTP/SOCKS/TLS and injected native ABI fixtures, including eight-way rejection-only preflight and cancellation. Native diagnostics still run one at a time.
- Full IPA run 38562413747 passed the Swift source/transport gates and continues packaging separately; public assets remain untouched.
