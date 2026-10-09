# iOS pending-start watchdog implementation plan

> **For agentic workers:** Execute inline; subagent-driven-development/executing-plans are not installed.

**Goal:** Bound the spinner during profile preparation and prevent a successful On Demand connection from being stopped as a cancelled attempt.

**Architecture:** Keep existing NE write ownership; never release an outstanding preferences write on timeout. Separate explicit cancellation epochs from attempt invalidation. Observe real status during preparation without issuing competing manager loads, latch timeout presentation across notifications, and allow late connected status to recover UI.

**Tech Stack:** Swift, NetworkExtension, Python source contracts, portable Swift regression executable, existing Apple CI.

### 1. Failing regressions
- [ ] `iosApp/Tests/VpnStartAttemptTests.swift`: success invalidation and explicit `cancel()` must affect different counters; repeated cancels remain observable. Test preparation (60s) and startup (30s) boundaries using an explicit clock input.
- [ ] `iosApp/Tests/test_vpn_startup_contracts.py`: require polling before the first connect await; preparation must not disable polling. Require timeout presentation retention, no competing manager loads, cancellation-only cleanup after On Demand persist, common-mode timer.

### 2. Production
- [ ] `iosApp/Nimbo/NimboVpnStartAttempt.swift`: add cancellationGeneration/cancel and pure `deadlineExceeded(elapsed:preparing:)`.
- [ ] `iosApp/Nimbo/VpnController.swift`: capture cancellation epoch, stop after a late preferences write only when epoch changed. Start timer before preparation awaits; retain existing write guards. Use 60s preparation / 30s startup with explicit timeout diagnostics, preserve failure while NE remains transitional, clear on connected or deliberate disconnect/retry. Schedule timer in common run-loop modes.
- [ ] Prevent post-timeout reloading/staging of another manager; a late system callback may finish but must not call startVPNTunnel for the invalidated attempt.

### 3. Verify
- [ ] `python -m unittest discover -s iosApp/Tests -p 'test_*contracts.py'` must pass; `git diff --check` clean.
- [ ] Mirror only changed owned files after verifying primary equals HEAD; preserve unrelated changes.
- [ ] Run existing Apple build workflow for real Swift compilation, portable policies and IPA packaging. No claim of reproducing the user's iOS beta behavior without diagnostics/device evidence; no public replacement without request.
