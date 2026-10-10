# Cross-platform traffic statistics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Provide a dedicated, truthful traffic dashboard on desktop, Android and iOS.

**Architecture:** Use cumulative core counters, never connection-count estimates, for route bytes. Existing session history and total counters remain; unsupported fields are nullable. Mihomo gets a bounded read-only telemetry operation with generation validation, not a polling connection-history database.

**Tech Stack:** Rust/Tauri, React/TypeScript, Go Mihomo bridge, Kotlin/Compose, Swift packet tunnel.

## Contract and file ownership
Desktop native: `apps/ui/src-tauri/src/commands.rs`, new `traffic_telemetry.rs`, `crates/mihomo/src/controller.rs`, and `tools/native/mihomo-core/telemetry.go`. Desktop UI: `Statistics.tsx`, `stats/SignalStatistics.tsx`, new `TrafficDashboard.tsx`, `api.ts` and scoped CSS/tests. Android: `app/src/main/java/com/danila/nimbo` only. Shared/iOS: `shared/src` and `iosApp` only.

- [x] Add failing parser tests: duplicate inbound/outbound totals must not be added together; ignore API/probe tags, reject negative values, keep missing route counters unavailable.
```ts
assert.equal(routeShare(null), null);
assert.equal(routeShare({proxy_upload: 60, proxy_download: 40, direct_upload: 0, direct_download: 0}), 1);
```
- [x] Implement wire shape: `route_traffic: {proxy_upload:number,proxy_download:number,direct_upload:number,direct_download:number}|null`, `tcp_connections:number|null`, `udp_connections:number|null`, `session_available:boolean`. Protocol values mean currently active core-tracked connections, not packet totals.
- [x] Implement generation-scoped Mihomo read-only telemetry using upstream manager cumulative counters and tracker metadata; no destinations, credentials, or identifiers in reply. Preserve old bridge compatibility with nullable fields.
- [x] Integrate native Xray outbound counters and Mihomo telemetry into existing traffic polling. Clear telemetry on stop/reconnect; include no internal API traffic; keep rate-window logic and session totals persistence.
- [x] Build two large upload/download cards with current speeds, measured route donut, TCP/UDP active-count cards and ad-block control in the existing separate Statistics destination. Use an empty ring for zero traffic and unavailable panels for unsupported telemetry. Retain history, ranges and resets.
- [x] Apply equivalent compact Compose dashboard on Android and shared/iOS; bind real measurements only, no synthetic donut inferred from selected server. Preserve existing navigation and stop/resume lifecycle.
- [x] Run `npm test`, `npm run build` in `apps/ui`; run `cargo test -p nimbo-mihomo`, `go test ./...` in `tools/native/mihomo-core`, Android/shared compilation and disposable browser fixtures. Check 360/800/1100 widths, dark/light and every app style.
- [x] Review diffs, mirror only verified owned files into the primary checkout, push the explicit changed paths, and dispatch source/build CI without publishing a release. Record platform validation limitations.


## Verification boundaries

Source implementation, focused tests and ownership-checked mirroring are complete. Native Android AAR and GitHub packaging are separate build operations, not physical-device acceptance. Mobile Xray has session-byte measurements but no route/protocol telemetry; raw Android AWG cannot apply domain blocking. Four styles and mobile 320/360/390 widths (including enlarged text) verify adjacent transfer cards. Retained unrelated baseline source-test failures are documented in platform readiness.
