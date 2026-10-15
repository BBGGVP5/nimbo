# Windows DNS Reliability Implementation Plan

> **For agentic workers:** Execute the following tasks with the available tools; the superpowers execution sub-skills are not installed. Parent owns fixture/native/workflow edits. Dirac independently reviews Windows interface/WFP source read-only. Never execute live native acceptance on the developer computer.

**Goal:** Identify and repair the observed Windows native DNS failure without weakening Kill Switch, substituting system DNS, or converting a failed query into a passing retry.

**Architecture:** Start from the delivered source snapshot `2d58bbf16d91b6f25ee812350a1a510e8aeecfeb` and preserve the dirty checkout/index. Repeat the original complete Windows fixture on a disposable VM with the already-added pre-cleanup diagnostics. Select a production change only after a deterministic regression or correlated failure identifies the broken path; keep fixture defects separate from product defects.

**Tech Stack:** Rust native-session fixture and Windows WFP, Python 3.11 fixture/CI, pinned Mihomo and sing-tun Go sources, GitHub Actions Windows runner.

## Task 1: Preserve baseline and bound the reproducer

**Files:**
- Receipts: `.codex-tmp/windows-dns-fix-20261015/`.
- Modify: `scripts/ci/test-mihomo-windows-tun.py`.
- Test: `scripts/ci/test_mihomo_windows_diagnostics.py`.
- Modify: `.github/workflows/mihomo-desktop-tun.yml`.

- [x] Save real HEAD, current branch, index SHA256, handoff ref and porcelain status before any code edit. Keep the original failing log and both subsequent green runs intact.
- [ ] Add a bounded `--rounds` fixture argument, default1, accepted counts1/2/4/8. Repeat the original plain-TUN then Both cases0–4; the first failed driver exits into the existing pre-cleanup diagnostics. No automatic recovery/continuation after a failed round.

```python
def run_driver_rounds(driver, environment, rounds, before_case, after_case):
    for round_index in range(rounds):
        for case in MANDATORY_CASES:
            before_case(round_index, case)
            subprocess.run([str(driver), '--ignored', '--exact', case,
                            '--test-threads=1', '--nocapture'],
                           env=environment, check=True, timeout=240)
            after_case(round_index, case)
```

- [ ] Add pure mocked tests: exact ordering/count, default round, rejection of other counts, and first failure stops later cases/rounds while the original exception remains fatal. Run `python -B -m unittest discover -s scripts/ci -p test_mihomo_windows_diagnostics.py`; preserve initial red and final green logs.
- [ ] Expose optional workflow dispatch `windows_rounds` with default1 and choices1/2/4/8; pass it only as the fixture argument. Keep all existing assertions, three-second DNS deadline, targets, physical DNS denials and final snapshot checks.
- [ ] Snapshot only reviewed fixture/tests/workflow/plan using a separate Git index; push a separate `codex/windows-dns-reliability-20261015` ref. Dispatch four complete Windows rounds. Production runtime remains identical to the delivered snapshot during this investigation.

## Task 2: Identify a concrete failure boundary

**Files:**
- Inspect: `crates/mihomo/tests/windows_tun.rs`, Python synthetic DNS responder.
- Inspect: `tools/native/mihomo-core/desktop_runtime.go`, pinned Mihomo `listener/sing_tun/dns.go`, pinned sing-tun packet writer and WFP filters.
- Inspect: `apps/service/src/mihomo_windows.rs`, `apps/service/src/mihomo_firewall.rs`.
- Receipts: `.codex-tmp/windows-dns-fix-20261015/` only.

- [ ] Correlate exact failed stage, case/round, native/helper identity, DNS responder transaction/peer and pre-cleanup interface/WFP state. Distinguish missing upstream request, bad response, DNS relay drop, packet-writer/return-path drop and fixture failure.
- [ ] Independently inspect WFP/interface lifetime without changing permits, weights, strict-route, physical DNS policy or device identity checks. A plausible candidate alone does not authorize a production patch.
- [ ] For a source defect, add an executable regression exercising that actual code and retain a failing pre-fix run. If failure instead belongs to the synthetic fixture, repair and label that fixture defect without claiming a native DNS fix.

## Task 3: Repair and verify the identified path

### Narrowed candidate selected after source review

Microsoft documents EMPTY next-hop metadata when an outbound flow is reauthorized on its inbound reply. Nimbo's sole TUN permit currently requires a nonempty next-hop LUID. This is a concrete policy coverage gap, but has not yet been attributed to the historical timeout.

- Service write set: `apps/service/src/mihomo_firewall.rs`, optional `apps/service/src/mihomo_firewall/reauthorize_tests.rs`. Add an ephemeral soft role7 permit requiring ALL of REAUTHORIZE, EMPTY next-hop and the exact current owned arrival LUID. Keep role4 outbound permit and physical blocks. Journalv3 authorizes/retires exact role counts1=5,2=7,3=8; provider cleanup remains required for versions2/3. Roles5/6 alone use transport layers.
- VM reproducer write set: `crates/mihomo/tests/support/windows_dns_reauthorization.rs`, one module registration in `crates/mihomo/tests/windows_tun.rs`, Python fixture/parser/barrier and pure tests. Hold a named synthetic DNS response until the driver commits a separate ephemeral low-weight DNS block in the owned sublayer, causing policy-change reauthorization without changing the prevailing decision. Release one reply, retain the same three-second receive deadline, and capture before session/filter cleanup if it fails.
- Run the kernel reproducer first with the old productionv2 source; snapshot only the new test files. Then run identical test contents with the scoped productionv3 correction. Keep the original untraced full cases0–4 and all physical bypass denials mandatory. Passing only a descriptor model is not kernel proof.

- [ ] Update this plan with the exact narrowed production/test write set once the defect is established. Avoid unrelated UI, iOS, Android or core-version changes.
- [ ] Apply the smallest evidence-backed change. Existing explicit DNS upstream policy and fail-closed ownership must remain intact; no longer DNS timeout, hidden retry, direct/system resolver fallback or disabled protection.
- [ ] Run the new deterministic regression, existing pure/native Go or Rust suites relevant to the edit, source pin/license gates, and full disposable Windows acceptance with repeated connect/crash/reset cycles. Require the original untraced fixture as well as any traced diagnostic run.
- [ ] Mirror only reviewed files after before-hash checks. Reconfirm original HEAD/branch/index and unrelated changes remained untouched.
- [ ] Report the exact repaired mechanism and test evidence. If no product cause can be reproduced or established, state that explicitly instead of declaring a series of green runs a fix. No public release replacement is authorized in this task.
