# iOS connection cancellation/status and desktop telemetry plan

> **For agentic workers:** Execute inline and preserve unrelated changes in the existing checkout.

**Goal:** Keep cancellation available during iOS connection preparation, reconcile a refreshed owned NE profile, issue stop before awaited preference writes, and decode Xray CLI's omitted zero counters.

**Architecture:** Connection-phase presentation is separate from whether cancellation is enabled. A stop request remains pending until observed terminal status. Read fresh owned manager snapshots during transitional polling without competing preference writes, discard reloads across state/mutation changes, and observe same-provider notifications even when NE replaces connection object identity. Restrict desktop zero-default decoding to a named, recognized counter with an absent value; reject explicit malformed values and absent stat arrays.

**Tech Stack:** Kotlin Compose interaction tests, Swift NetworkExtension, portable Swift policies, Rust telemetry parser, real pinned Xray isolated stats API, Apple CI.

- [ ] Correct both compact and classic connection controls; click/pointer tests must invoke cancellation even with an empty cached server list. Disconnecting stays disabled.
- [ ] Update VpnController stop ordering, terminal-state ownership and read-only fresh-manager reloads (single in flight, throttled, mutation/generation guarded); compare provider identity for status notifications.
- [ ] Update shared TunnelControl stop ordering for widget/shortcuts. No new VPN profile creation during reconciliation.
- [ ] Add/execute portable observation/stop policy regressions plus integration guards; run Kotlin rendered interaction tests and Apple typechecks/build.
- [ ] Correct crates/xray-config/src/telemetry.rs for protobuf's omitted zero `value` and add malformed/known-zero regression coverage. Validate against the exact pinned CLI with isolated loopback endpoints and no changes to system VPN/routing.
- [ ] Mirror only owned files; verify packaged iOS IPA, publish its replacement in Beta 1 under the existing authorization, and document desktop source/tests/package availability without claiming a rebuilt installer unless actually built.

## Evidence
The shared Compose control currently uses enabled=!connectionBusy, so a connecting/preparing state cannot send its cancel action. Controller stop awaits On Demand persistence before issuing stop. Polling reads only the cached connection object and the observer filters solely by reference identity. Exact cause of the user's stale device state requires diagnostic/device evidence. Xray v26.9.30's reflection JSON serializer omits zero fields carrying omitempty; named zero stats are valid rather than missing measurements.
