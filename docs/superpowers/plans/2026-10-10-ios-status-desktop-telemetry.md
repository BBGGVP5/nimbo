# iOS connection cancellation/status and desktop telemetry plan

> **For agentic workers:** Execute inline and preserve unrelated changes in the existing checkout.

**Goal:** Keep cancellation available during iOS connection preparation, reconcile a refreshed owned NE profile, issue stop before awaited preference writes, and decode Xray CLI's omitted zero counters.

**Architecture:** Connection-phase presentation is separate from whether cancellation is enabled. A stop request remains pending until observed terminal status. Read fresh owned manager snapshots during transitional polling without competing preference writes, discard reloads across state/mutation changes, and observe same-provider notifications even when NE replaces connection object identity. Restrict desktop zero-default decoding to a named, recognized counter with an absent value; reject explicit malformed values and absent stat arrays.

**Tech Stack:** Kotlin Compose interaction tests, Swift NetworkExtension, portable Swift policies, Rust telemetry parser, real pinned Xray isolated stats API, Apple CI.

- [x] Correct both compact and classic connection controls; click/pointer tests must invoke cancellation even with an empty cached server list. Disconnecting stays disabled.
- [x] Update VpnController stop ordering, terminal-state ownership and read-only fresh-manager reloads (single in flight, throttled, mutation/generation guarded); compare provider identity for status notifications.
- [x] Update shared TunnelControl stop ordering for widget/shortcuts. No new VPN profile creation during reconciliation.
- [x] Add/execute portable observation/stop policy regressions plus integration guards; run Kotlin rendered interaction tests and Apple typechecks/build.
- [x] Correct crates/xray-config/src/telemetry.rs for protobuf's omitted zero `value` and add malformed/known-zero regression coverage. Validate against the exact pinned CLI with isolated loopback endpoints and no changes to system VPN/routing.
- [x] Mirror only owned files; verify packaged iOS IPA, publish its replacement in Beta 1 under the existing authorization, and document desktop source/tests/package availability without claiming a rebuilt installer unless actually built.

## Evidence
The shared Compose control currently uses enabled=!connectionBusy, so a connecting/preparing state cannot send its cancel action. Controller stop awaits On Demand persistence before issuing stop. Polling reads only the cached connection object and the observer filters solely by reference identity. Exact cause of the user's stale device state requires diagnostic/device evidence. Xray v26.9.30's reflection JSON serializer omits zero fields carrying omitempty; named zero stats are valid rather than missing measurements.

## Verification and publication outcome
112 Python integration/source contracts passed. Eight rendered Kotlin interaction tests passed, including cancellation availability for both button styles in connecting/preparing states with no servers. Rust telemetry tests passed. The exact pinned Windows Xray 26.9.30 archive/executable hashes were verified, then an isolated loopback SOCKS/HTTP request produced an actual statsquery response with two omitted zero direct counters. Compiling and running the production telemetry parser against that response returned upload=48/download=1526 and measured zero direct bytes. No host VPN, routing or installed application was changed for the test.

Apple CI 38043411336 succeeded at be06edb9ff95852c3482a003e208e92bf7483cb4, including status-observation ownership/stop-intent regressions, startup policy tests, extension typechecks, Kotlin release link and full Xcode build. IPA ZIP integrity, embedded revision be06edb9ff95, extension catalogs and SHA-256 verified: 4ace1897a1078531faa27bce6aac21e1e7e2ba4875617c29571c2ecd105cab93 (76,342,186 bytes).

Windows x64 application/service were rebuilt from the same runtime commit. The legacy NSIS output was not published because it omits current core resources. The full custom installer was assembled from the freshly compiled payloads. Packaging resource verification found a pinned CRLF notice stored as LF in the existing workspace; an isolated archive/copy staging directory restored only the exact hash-matching line ending bytes. The original notice remains unchanged at SHA-256 5d966570d7a442d4e969892860a914e542c97f262c873baee8f0aa48e1f40212. The custom payload checker verified exact app/service/Xray/AWG/Mihomo bytes and required sources/notices; NaiveProxy presence was checked separately. Installer SHA-256: d5483c01f7a285f0bde7943254238ae79bb862e785caba3878d6c2da33bfab2f (179,195,392 bytes).

Published iOS IPA/checksum and Windows x64 installer/checksum replacements in public v1.3.0-beta.1. GitHub asset digests match verified local files. Release description and all other platform assets preserved. No physical iPhone freeze reproduction or installed Windows TUN test is claimed. Source formatting after the runtime commit only affects Rust test layout.
