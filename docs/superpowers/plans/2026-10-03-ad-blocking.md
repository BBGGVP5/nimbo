# Cross-platform ad blocking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Offer an opt-in lightweight ad-domain routing overlay on every platform with domain-aware cores (raw Android AWG reports unavailable).

**Architecture:** Persist a default-off boolean. Prepend a bounded local domain list to effective runtime rules, leaving provider subscription contents, provider/custom blocking rules and stored profiles untouched. Explain DNS/domain filtering limitations and next-connection application; never silently reconnect or elevate.

**Tech Stack:** Rust/Tauri, React/TypeScript, Go Mihomo bridge, Kotlin/Compose, Swift packet tunnel.

## Contract and file ownership
Desktop preference: `AppPreferences.ad_blocking_enabled` in `state.rs` and `api.ts`. Android preference: `PreferencesManager.adBlockingEnabled`. Shared/iOS preference: `NimboUiState.adBlockingEnabled` persisted by host. Mihomo start option: `options.adBlocking`, applied after validation to parsed runtime rules, never original YAML.

- [x] Write failing on/off policy tests; on prepends `domain:doubleclick.net` blocking, off returns original rules unchanged, user/provider blocking remains on both paths.
```rust
assert_eq!(ad_domains(false), Vec::<String>::new());
assert!(ad_domains(true).contains(&"domain:doubleclick.net".into()));
```
- [x] Add default-off persisted settings on each platform with round-trip/migration tests. UI distinguishes saved preference from active session configuration and labels application at next connection.
- [x] Use the same bounded suffix list for Xray and Mihomo: `doubleclick.net, googlesyndication.com, googleadservices.com, googleads.g.doubleclick.net, adservice.google.com, ads.yahoo.com, advertising.com, adsrvr.org, adnxs.com, adform.net, adroll.com, taboola.com, outbrain.com, criteo.com, criteo.net, scorecardresearch.com, quantserve.com, ads.facebook.com, app-measurement.com, amazon-adsystem.com`.
- [x] Xray prepends a blackhole domain rule before template/custom/global routing catchalls. Mihomo parses `DOMAIN-SUFFIX,<domain>,REJECT` before original rules. Do not download large block lists or change DNS, OS firewall, Kill Switch or server configuration.
- [x] Add accessible toggle and explanation: blocks known advertising/tracking domains in Nimbo-routed traffic, not all in-app/video ads, direct OS bypass traffic, or encrypted application-specific DNS; provider rules may still block with the option off.
- [x] Execute Rust/Go policy unit tests and Android/shared config tests, validate native source builds in CI, and prove stored YAML and subscription contents retain their digests.
- [x] Commit explicit paths only, mirror ownership-checked source files, update readiness evidence without claiming device validation or a measured blocked-request count.


## Verification boundaries

Source implementation, focused tests and ownership-checked mirroring are complete. Native Android AAR and GitHub packaging are separate build operations, not physical-device acceptance. Mobile Xray has session-byte measurements but no route/protocol telemetry; raw Android AWG cannot apply domain blocking. Four styles and mobile 320/360/390 widths (including enlarged text) verify adjacent transfer cards. Retained unrelated baseline source-test failures are documented in platform readiness.
