# Windows Mihomo Both and external Kill Switch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make Windows x64 Both and external session Kill Switch genuinely usable through the protected helper.

**Architecture:** Both uses the same owned native TUN with its verified mixed loopback listener and the existing per-user proxy recovery journal. Kill Switch is a service-owned, transactional WFP sublayer independent of the native process. Static filters survive native/helper failure until an authenticated explicit disconnect/reset, but do not survive BFE restart/reboot; no global firewall profile changes, arbitrary client application exceptions or foreign filter deletion.

**Tech Stack:** Rust, windows-sys 0.59 WFP/IP Helper, authenticated SCM pipe, TypeScript/React, disposable GitHub Windows acceptance.

Execution: required superpowers execution skills are unavailable in this environment. Execute inline in the existing branch and draft PR; never create another chat or change this host's network/ACLs. Network-mutating acceptance requires NIMBO_DISPOSABLE_WINDOWS=1 AND GITHUB_ACTIONS=true.

## File map
- `apps/service/src/mihomo_firewall.rs`: WFP journal, transactions and exact owned filter cleanup; no YAML parsing.
- `apps/service/src/mihomo_windows.rs`: arm before native start, allow exact TUN LUID after readiness, retain on abnormal exit, explicit reset.
- `apps/service/src/platform/mihomo_pipe.rs`: authenticated peer SID and reset command.
- `crates/ipc/src/lib.rs`: default-false kill_switch request and explicit helper capability.
- `crates/mihomo/src/{process.rs,helper_windows.rs}`: options-aware TUN start, capability and authenticated reset.
- `apps/ui/src-tauri/src/{mihomo_runtime.rs,commands.rs}`: Both transaction, capability, no false hot KS toggle, scoped reset.
- `apps/ui/src/{lib/coreApi.ts,lib/coreProfiles.ts,components/CorePreferenceSetting.tsx}`: capability-controlled availability and accurate copy.
- `crates/mihomo/tests/windows_tun.rs`, `scripts/ci/test-mihomo-windows-tun.py`: live Both + bypass + native-crash denial + reset/restore in disposable VM only.

### Task 1: Backward-compatible request and capability
- [x] Add failing protocol test: deserialize old request without kill_switch and assert false; roundtrip true; unknown executable still denied.
```rust
let old = serde_json::json!({"yaml":"x", "source_sha256":"a", "binary_sha256":"b", "mixed":true});
assert!(!serde_json::from_value::<MihomoTunRequest>(old).unwrap().kill_switch);
```
- [x] Run `cargo test --locked -p nimbo-ipc`: missing field must fail compilation first, then pass after adding `#[serde(default)] pub kill_switch: bool` and default-false availability field. Keep existing start_tun callers working by delegating to `start_tun_options(binary, profile, mixed, false)`.
- [x] Linux rejects kill_switch before mutation; do not advertise Windows capabilities there.

### Task 2: External WFP ownership
- [x] Test unique UUID-derived filter keys, journal owner mismatch, and absence of dynamic/global policy writes with `cargo test --locked -p nimbo-svc` (non-mutating host tests only).
- [x] Add `Firewall::arm(sid: &str, core: &Path)`, `allow_tun()`, `release(sid: &str)` and `pending()` using a SY/BA-only atomic journal written before WFP commit. Sublayer and every filter key derive from its random UUID; recovery deletes only those keys in one transaction. Require journal SID match. Invalid/private journal fails closed.
- [x] Use ALE_AUTH_CONNECT_V4/V6: high-weight soft permits for fixed protected core AppID, loopback and narrowly scoped DHCP; low-weight hard block. Add exact native interface LUID permit only after readiness; permit neither all local subnet nor GUI nor resolver DNS. Do not clear action right on permits, preserving other firewall vetoes.
- [x] WFP session is static, not dynamic: closing the service handle must not remove protection. Successful explicit stop releases filters after joined native cleanup. Native failure/EOF after failure retains journal/blocking and failed TUN owner. Same authenticated SID reset is allowed only with no live native core.

### Task 3: Both runtime and authoritative capability
- [x] Add frontend regression assertions:
```javascript
assert.equal(mihomoBlockReason({...cap, both_available:true, kill_switch_available:true}, 'mihomo', 'both', true), null);
assert.equal(mihomoBlockReason({...cap, both_available:false}, 'mihomo', 'both', false), 'MIHOMO_TUN_UNAVAILABLE');
```
- [x] Start Both through `Session::start_tun_options(..., true, kill_switch)`, validate actual mixed endpoint, write durable proxy snapshot/port before apply. Restore only if still owned on cancellation/error. Store runtime proxy only after state commit; no WinHTTP writes.
- [x] Expose capability from the authenticated helper, not cfg!(windows) alone: old helper has false KS field. Active KS changes require explicit disconnect/reconnect instead of pretending to apply live.
- [x] Scoped reset uses protected pipe first and avoids legacy global-policy cleanup when no legacy owned snapshot exists.
- [x] Run `cargo test --locked -p nimbo-ipc -p nimbo-mihomo -p nimbo-svc`, desktop Rust tests, `npm test` and frontend production build; fmt and scoped Clippy.

### Task 4: Real hosted acceptance, publication and mirror
- [x] Extend disposable Rust driver: mixed SOCKS/HTTP traffic alongside native TCP4/6/UDP/DNS; bind a socket to physical interface to prove bypass is denied; terminate only the fixed native child; verify no plaintext bypass after death; authenticated reset restores physical traffic and removes exact filters. Existing no-KS cycles remain.
- [x] Use an interface-bound TCP-only control to the already resolved GitHub IPv4 endpoint (443); send no HTTP or credentials. A local self-address is WFP loopback and cannot prove physical denial. Baseline must pass before arming, denial must follow, and restoration must pass; no skipped baseline failures. Snapshot firewall policy/user proxy before/after.
- [x] Push only intentional files to current branch, launch native hosted CI and Windows installers with publish=false. Read actual outcomes; failed assertions are not skipped/relabelled.
- [x] Update readiness and plan with exact successful/pending gates, mirror only verified baseline/identical source files to primary workspace. Host ACL remains untouched and hardware/nonadmin/BFE-restart gates remain accurately documented.

Checkpoint: 130 desktop tests, 86 frontend tests/build, Windows and Linux scoped suites and Clippy passed without host networking/service/ACL mutations. Adapter absence uses positive GetIfTable2 enumeration, not alias-error inference. New native/helper crash and physical UDP fixtures compile; hosted live results remain pending. Microsoft WFP object-management/filter-arbitration and ALE conditions documentation was checked. One follow-up test compilation mistake was immediately fixed at 841f78b; it is not a successful validation run.

### Task 5: Exact crash-retained Wintun retirement

Evidence: hosted dccf85e Windows test passed normal Both traffic/denial/stop, then failed authenticated reset after forced native death with TUN_CLEANUP_FAILED. Six TCP, two UDP and two DNS fixture exchanges were observed. Never bypass the retirement gate.

- [x] Add non-mutating identity tests: changed GUID, LUID or device instance cannot match an owned adapter; foreign SID and unknown journal fields remain denied.
- [x] Create `apps/service/src/mihomo_adapter.rs`: capture exact MIB interface GUID/LUID plus SetupAPI network-class Wintun hardware ID and device instance after native readiness. Persist identity in protected WFP journal before permitting TUN. Store native PID + creation time before first network mutation; reset verifies that exact process is no longer live, even after helper restart.
```rust
assert!(!owned.matches(&replacement));
```
- [x] Explicit same-SID reset may remove only the persisted device instance after rechecking GUID/LUID/Wintun hardware ID and native death; use checked SetupAPI DIF_REMOVE, then positive interface enumeration. Missing ownership remains a failure. Do not remove by alias or delete drivers/global routes.
- [x] Add fixture progress per failure case and ensure disposable uninstall executes even if emergency reset times out. Original test/reset failure remains failure.
- [x] Run scoped Rust tests/Clippy/fmt; push and require live Windows acceptance success before updating readiness. Mirror only receipt-matching primary files.

Verified checkpoint: 923b851 and exact-driver 929e1b7 Windows hosted tests passed all three normal/native-crash/helper-crash cases and physical restoration (TCP15/UDP6/DNS5). Installers 37111046901 remain building, publish=false. Both runs still have a real unresolved Linux ARM64 broker TCP6 failure; not an overall green workflow. 81a343a selects exact Linux Cargo artifact too; hosted 37111545340 again passes Windows/Linux amd64 but still fails Linux ARM64 TCP6 (after explicit cleanup in this run). Exact artifact selection does not fix that network failure. Helper Windows x86/ARM64 cross-target checks also pass, without advertising native support. 27 intentional files mirror to primary; host networking/ACL untouched. Hardware, physical IPv6 bypass and BFE/reboot-persistent protection remain open.

### Task 6: Conservative reset while asynchronous adapter retirement settles

Evidence: 7c8a04f Windows job 111174599479 passed Both/KS native/helper crash resets and normal TUN cycles, then immediate emergency reset failed TUN_CLEANUP_FAILED; a separate finally reset a few milliseconds later passed. This supports, but does not prove, an asynchronous final interface-row retirement window. Do not drop the failing reset test or erase an unknown adapter.

Files: `apps/service/src/mihomo_firewall.rs` (read-only bounded positive retirement), existing pure service tests in that file, existing hosted Windows driver unchanged.

- [ ] Add injected-check tests: transient false then true succeeds, persistent false fails, enumeration error returns unchanged immediately.
```rust
let mut checks = 0;
await_adapter_retired(|| { checks += 1; Ok(checks == 2) }, Duration::from_millis(50)).unwrap();
assert_eq!(checks, 2);
assert_eq!(await_adapter_retired(|| Ok(false), Duration::ZERO), Err("TUN_CLEANUP_FAILED".into()));
```
- [ ] Implement the no-journal reset branch as `await_adapter_retired(adapter_retired, Duration::from_secs(2))`: poll positive GetIfTable2 absence at 10 ms; return existing error on deadline. No enumeration-error fallback, object removal or WFP release before absence. Native-alive/SID/device-identity checks stay unchanged. This is not an extension of traffic/startup deadlines.
- [ ] Run service unit tests, scoped fmt/Clippy and existing unmodified Windows live reset/crash gates. Require actual success, including emergency reset, before marking the reset issue resolved.
- [ ] Record latest installers SUCCESS (all three architectures; only x64 native TUN), mirror receipt-matching source and update PR accurately.
