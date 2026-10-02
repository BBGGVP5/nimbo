# Xray probe fidelity and compact controls implementation plan

> **For agentic workers:** Execute this plan task-by-task in the existing checkout; preserve unrelated changes.

**Goal:** Correct reproducible Xray per-node probe failures, anchor server actions to the title, compact choice controls, and verify and dispatch cross-platform builds.

**Architecture:** Preserve the selected outbound and its verified transport dependencies rather than replacing a route with TCP or an arbitrary balancer member. Menus use the title as their anchor on Android/shared iOS/desktop. Compact controls retain 48dp touch targets and scale with text.

**Tech Stack:** Kotlin/Compose, Swift/Go bridge, React/TypeScript, Rust, Gradle and GitHub Actions.

### Task 1: Xray probe route fidelity
- [x] Trace selected Xray route, balancer initialization and transport dependencies; add failing sanitized fixtures.
- [x] Fix proven configuration-loss/rejection issues without direct fallback or fabricated ping.
- [x] Run Android/native/desktop regression tests; document unsupported paths explicitly.

### Task 2: Title-anchored server menus
- [x] Move Android and shared Compose menu anchors into the title region.
- [x] Make desktop hold/right-click/keyboard menu placement independent of ping badge position.
- [x] Add focused regression checks and compile all touched UIs.

### Task 3: Compact choices
- [x] Replace oversized interval choices and ping-format tiles with centered content-fitting controls (48dp minimum touch height).
- [ ] Verify semantics, long labels and font scaling; preserve other settings.

### Task 4: Verify and build
- [x] Mirror only baseline-matching Android/shared/iOS files to the user's primary project.
- [x] Run targeted tests and inspect previous GitHub build outcomes.
- [x] Commit explicit paths, push existing PR branch and dispatch artifact-only desktop and re-signable iOS builds.
- [x] Record verified results and remaining device/native-runtime gaps without claiming all protocols work.


### Verification and scope notes
- The initially attempted checkout test run was blocked by missing SDK/AAR, not a successful red test run. The locally installed pinned AAR was copied only into an ignored build directory; no native artifact or provider configuration is committed.
- Regression fixtures cover owned/partial/injected members, backup loopback routes, direct/conditional/cyclic failures, and terminal fragment helpers/redirects/chains. Final primary-project tests additionally cover explicit virtual-loopback selection.
- 790 Kotlin tests and 82 desktop tests passed before the last virtual-route assertion; the primary-project verification/build rerun includes that assertion. iOS profile source contracts have five passing tests; a full IPA is still required.
- A differing primary iOS source-contract file was preserved. Runtime files were mirrored only after baseline comparison; no partial desktop checkout was copied to the primary project.
- Browser proof: title x=378, menu x=378, title bottom=438.734375, menu top=444.734375; modal portal remains in dialog. This is synthetic UI-only QA, not a real VPN test.
- Remaining: Android large-text/device visual QA, full latest Apple build and device connectivity, and native iOS/desktop health-strategy balancer diagnostics. These are intentionally not checked off as complete.

- Final primary rerun passed all 790 Kotlin tests including virtual-loopback selection and both debug/release packaging. Debug ARM64 is signed and side-by-side (`.debug`); release ARM64 is unsigned and is not presented as an installable release update.
- Pushed runtime revision 33dea19 to PR78. IPA run 37005611847 and artifact-only desktop run 37005616172 are executing from that exact runtime revision. No main merge, tag publication, or user-host networking changes.
