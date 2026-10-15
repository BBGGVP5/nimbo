# Beta desktop/iOS readiness implementation plan

> **For agentic workers:** Execute inline in focused tasks; use checkbox steps. Do not mutate the developer host's network or publish a public release without confirmation.

**Goal:** Resolve the Windows native TUN/Both/Kill Switch failure, verify rounded branding, prepare user-facing beta notes, and produce fresh desktop/iOS artifacts.

**Architecture:** Keep persistent fail-closed protection and explicit same-owner reset. Investigate the re-entry failure against real WFP/interface semantics; validate on the disposable GitHub VM, not by weakening tests. Keep Apple/Android icon masters unchanged; reuse the approved rounded Windows variant for README/release artwork.

**Tech Stack:** Rust/WFP, controlled native Windows/Linux CI, Node/Sharp icon validation, Tauri desktop packaging, Go/Swift iOS build.

## 1. Windows failure
- [x] Confirm first failure versus cascade: case 0 restores TCP/DNS; re-entry case 1 TCP times out; subsequent preflight reports retained Kill Switch. Record this as fail-closed behavior, not a reason to auto-disable protection.
- [x] Add a pure regression for the routed-interface permit (`apps/service/src/mihomo_firewall.rs`), retaining persistent block/loopback/DHCP/process ownership. Check Microsoft layer/condition contracts before changing the field.
- [x] Preserve all five crash/SCM/lease cases in `crates/mihomo/tests/windows_tun.rs`; improve explicit phase diagnostics and verify re-entry/IPv4/IPv6/UDP/DNS and physical-bypass denial in `.github/workflows/mihomo-desktop-tun.yml`.
- [x] Never run the ignored TUN/WFP tests locally. Run normal service/IPC/core tests and strict formatting/Clippy locally, then require the real hosted native gate to pass. If the hypothesis fails, use controlled failure diagnostics before another patch.

## 2. Icon and README
- [x] Validate the existing ICO's 256px-first ten-size layout, PNG antialiasing/transparency and app/installer shared icon wiring with `scripts/tests/windows-icons.test.cjs`; do not rewrite the opaque Apple master or reset Explorer/icon caches.
- [x] Point the README image to the approved rounded PNG, and document what the release icon actually contains. Verify it visually at common sizes and, where available, check the compiled Windows executable icon resources.

## 3. User changelog and release staging
- [x] Create `docs/releases/1.3.0-beta.1.md` and prepend a matching user-facing section to `CHANGELOG_NIMBO.md`: new design, Mihomo YAML/category cards, AmneziaWG 3.1, latency/connection UX, cross-device duplicate fix, updated cores and explicit platform limitations. Avoid claiming device acceptance or unsupported modes.
- [x] Update the release publisher to use user notes and the actual workflow commit for a new draft beta tag. Refuse mutation of an already-published release. Public publication remains a separate explicit user action.
- [ ] Mirror only owned paths using HEAD preimage and postimage hashes, commit/push the existing branch, and dispatch fresh Windows/Linux packages plus re-signable iOS IPA from the same verified commit. Android builds are excluded by the user's request.
- [ ] Observe build/native-gate outcomes and save artifact/commit/checksum receipts. Report incomplete or failed gates rather than treating a dispatch as success.

## Evidence before artifact dispatch
Native run 37773141864 passed all three platforms from 1b487e9. Local service/IPC/core tests, strict Clippy/formatting, 128 UI tests, four icon/regeneration regressions, eight draft-publication policy regressions and 20 version-field checks passed. Real TUN/WFP ran only on disposable GitHub machines. No Android build or public release was started.
