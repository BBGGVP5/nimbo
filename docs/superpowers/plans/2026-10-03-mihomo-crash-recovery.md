# Linux Mihomo Crash Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover only Nimbo-owned Linux policy rules after a native SIGKILL, including partial startup, without deleting foreign network configuration.

**Architecture:** A pinned, narrowly patched sing-tun records its complete rule plan before the first RuleAdd. Each plan has a random kernel-visible fwmark with mask zero (ownership metadata, not a packet filter); a root-only write-ahead journal records boot, network namespace and process-start identity. Recovery requires an exclusive root lock and removes only exact planned rules with that marker; unknown/foreign state fails closed. Windows and mobile callbacks stay unset.

**Tech Stack:** Go 1.27.1, pinned Mihomo v1.19.31 / sing-tun v0.4.24, Linux netlink, Rust broker, Python namespace fixtures.

**Execution:** Inline in this existing checkout; helper execution skills are not available. No host network changes or public release.

## Files and ownership
- `tools/native/mihomo-core/desktop_journal_linux.go`: protected WAL, exact rule identity, recovery under the owner lock.
- `tools/native/mihomo-core/desktop_journal_linux_test.go`: pure validation/normalization and root-private filesystem tests.
- `tools/native/mihomo-core/desktop_tun_linux.go`: recover stale journal before vacant-state admission; close verifies cleanup.
- `tools/native/mihomo-core/desktop_runtime.go`, platform stubs, CLI: attach trusted callbacks; fixed root recovery entry.
- `tools/native/mihomo-core/sing-tun-rule-journal.patch`, `mihomo-rule-journal.patch`, `pins.json`: source-only narrow patches plus `netlink-rule-identity.patch` readback of action/mark presence; callbacks excluded from YAML/JSON.
- `scripts/ci/prepare-mihomo-merged.py`, desktop/Windows/Android builders: verified replacement, frozen patch manifests and notices.
- `apps/service/src/mihomo_owner.rs`: abnormal child exit invokes only the fixed, rehashed root core recovery command.
- `scripts/ci/test-mihomo-desktop-netns.py`, `test-mihomo-helper-netns.py`: actual crash and foreign-rule retention in disposable network AND mount namespaces.

## Task 1: Failing crash acceptance
- [x] Extend the existing native fixture with SIGKILL, assert stale rules remain before recovery, invoke `recover-tun`, compare the complete baseline, then reconnect. Add a foreign rule sharing a priority and require it to survive normal close and crash recovery.
```python
process.kill(); process.wait(timeout=5)
assert snapshot_network() != before
subprocess.check_call([str(binary), 'recover-tun'])
verify_restored(before)
```
- [x] Run against the previous source-built binary with `NIMBO_DISPOSABLE_NETNS=1`; observed ordinary cleanup deleting the foreign same-priority rule first (regression confirmed); recovery was also absent, not any host mutation.

## Task 2: Protected write-ahead ownership
- [x] Add pure tests and failing namespace fixture before implementation: planned/kernel rule normalization, changed marker/mask/destination rejection, bad schema/namespace/boot/live PID rejection, oversize/symlink/non-private WAL rejection.
```go
func TestOwnedRuleRequiresExactPlan(t *testing.T) {
    planned := *netlink.NewRule()
    planned.Family, planned.Priority, planned.Table = netlink.FAMILY_V4, desktopTunRule, desktopTunTable
    planned.Mark, planned.MarkSet, planned.Mask = 12345, true, 0
    kernel := planned; kernel.MarkSet = false
    if !sameDesktopRule(planned, kernel) { t.Fatal("kernel parser normalization") }
    kernel.Priority++
    if sameDesktopRule(planned, kernel) { t.Fatal("foreign rule adopted") }
}
```
- [x] Implement bounded schema/identity validation, fsync-before-RuleAdd atomic journal writes, exact kernel matching, deletion using the original planned rule, and retained journal on unknown owned-mark state. No broad table/priority deletion, no route or foreign interface deletion.
- [x] Add source-verified sing-tun pin and two patches: runtime-only marker/record/cleanup callbacks; unowned behavior unchanged. Stage them in every builder and freeze all inputs.
- [x] Run native Go tests/vet and staging patch-scope tests; require unchanged dependency versions and complete license notices.

## Task 3: Broker crash path
- [x] Add fixed CLI `recover-tun` with no client path or network arguments; require Linux root and exclusive lock. Start also recovers a dead owner before admission.
- [x] On an abnormal native exit, the broker runs the same protected, SHA-checked binary in recovery mode with a bounded timeout. Report cleanup failure if recovery fails; never report a dead session connected.
- [x] Run full namespace fixture, including active-owner refusal, same-priority foreign retention, partial planned-rule recovery and helper/native crash reconnect. Run Rust IPC/service/runtime tests.

## Task 4: Review and delivery
- [x] `cargo fmt --all -- --check`, `git diff --check`, Python source contracts and native tests pass.
- [x] Mirror only absent/baseline-matching intentional files into the primary workspace; preserve unrelated changes.
- [ ] Commit/push draft PR78; launch native Linux x64/ARM64/Windows, refreshed IPA and desktop packaging. Record actual states; do not equate queued builds with success.
- [ ] Update platform readiness. Windows privileged TUN, real iPhone networking/pressure and live-provider transport acceptance remain separate gates.


## Verified execution notes
- Previous binary failed the new foreign-priority ordinary-close test: the rule was actually deleted. Fresh kernel/mount namespaces only.
- New Linux native unit binary executed the complete Go suite successfully from its real package working directory; four ownership tests included. First standalone invocation from the checkout root failed the relative geosite fixture lookup; rerun from the package passed.
- Root harness covered all shutdown/crash modes, partial WAL subset, unsafe journal refusal and foreign-rule retention. Both-crash fixture SIGSTOPs the native process before killing the helper, preventing accidental graceful EOF cleanup.
- Broker status/down recovery does not release failed ownership; retained WAL blocks legacy ownership even after helper restart. Rust service 10/10 and scoped Clippy passed.
- No host services, routes or DNS were changed; Windows native TUN and signed real-iPhone traffic remain open.
