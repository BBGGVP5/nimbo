# Linux broker IPv6 investigation implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Identify and repair the repeatable ARM64 broker TCP6 failure without disabling IPv6, weakening ownership or skipping real traffic assertions.

**Architecture:** First observe the exact native socket operations in the existing synthetic private network/mount namespace. Restrict syscall tracing to the disposable acceptance harness, never the product helper or provider credentials. Apply a minimal runtime or fixture correction only when supported by that evidence, then repeat the original dual-stack/crash/restore gates.

**Tech Stack:** Python 3 unittest/strace, pinned Mihomo Go adapter, Rust broker, GitHub Actions Linux ARM64/amd64 and Windows x64.

Execution stays inline on the existing authorized branch; the named superpowers execution skills are not installed. No new thread, worktree, host service, routes, ACL or firewall changes.

## Files and responsibility
- `scripts/ci/test-mihomo-helper-netns.py`: opt-in bounded syscall trace attached to the actual helper PID; retain original traffic failures and dump only synthetic fixture evidence.
- `scripts/ci/test_mihomo_netns_diagnostics.py`: pure tests for bounded trace output and process identity collection; no root/network mutation.
- `.github/workflows/mihomo-desktop-tun.yml`: ensure strace is present on disposable Linux runners and explicitly enable tracing.
- `crates/mihomo/tests/helper_tun.rs`: add actual TCP6 to the isolated Rust session gate after the cause is understood.
- `docs/platform-readiness-2026-10-02.md`: accurate build/acceptance evidence, including failures and pending gates.

## Task 1: Capture failing socket identity and route evidence
- [x] Add pure trace-tail test before implementation:
```python
with tempfile.TemporaryDirectory() as directory:
    path = Path(directory) / 'socket.trace'
    path.write_bytes(b'x' * 20000 + b'connect-failed\n')
    tail = broker.trace_tail(path)
    assert len(tail.encode()) <= 16384
    assert tail.endswith('connect-failed\n')
```
- [x] Run `python -m unittest discover -s scripts/ci -p test_mihomo_netns_diagnostics.py`; expect a missing trace-tail helper failure first.
- [x] Implement `trace_tail(path)` using a 16384-byte seek-from-end read with UTF-8 replacement. Add `--trace-native` and attach `strace -f -qq -s 96 -e trace=connect,bind,setsockopt,getsockname,getpeername -o <private-temp-file> -p <helper-pid>` only after existing root/fresh-netns/private-mount checks. Wait for TracerPid to confirm attachment. Keep tracing processes joined in finally, dump bounded traces and `ip -j -6 addr/neigh` on failure, re-raise the original exception.
- [x] Require strace in hosted Linux CI; pass `--trace-native`. Run pure unittest and Python syntax checks, inspect diff, commit only these files and push. Read real ARM64 logs; no retry wrapper around failed traffic.

## Task 2: Evidence-led correction and regression
- [x] Record failing socket errno/source/interface and whether the peer received the HTTP request in this plan before changing runtime behavior. Trace evidence must distinguish route/dial failure from fixture handler failure.
- [x] For the Rust isolated session, loop over both literal targets:
```rust
for address in ["203.0.113.10:18080", "[fdfe:dcba:9901::10]:18080"] {
    let mut tcp = std::net::TcpStream::connect_timeout(&address.parse().unwrap(), Duration::from_secs(5)).unwrap();
    tcp.set_read_timeout(Some(Duration::from_secs(5))).unwrap();
    tcp.write_all(b"GET /fixture HTTP/1.1\r\nHost: fixture\r\nConnection: close\r\n\r\n").unwrap();
    let mut response = Vec::new();
    tcp.read_to_end(&mut response).unwrap();
    assert!(response.ends_with(b"native-tun-fixture"));
}
```
- [x] Update this plan with the exact minimal correction and regression files based on captured evidence; do not speculate by changing DNS, GC limits, kernel IPv6 or timeouts.
- [x] Run scoped non-mutating Rust/Go tests, fmt and Clippy. Push and require full native traffic, hot selection, cancellation, crash and exact-restore gates on Linux ARM64/amd64 and Windows x64. A green build without these gates is not success.

## Task 3: Delivery and readiness
- [x] Check Windows installer run `37111046901` (product `923b851`, publish=false), including rollback/embedded-core checks and artifacts.
- [x] Mirror only baseline/receipt-matching intentional source files into the primary workspace; preserve unrelated frozen sources and notices.
- [ ] Update readiness and existing PR with verified outcomes. Keep remaining physical-device/provider/IPv6-bypass and reboot-persistent KS limitations explicit.

Task 1 checkpoint: 7c8a04f pushed; three pure tests first failed for the absent function, then passed. Native run 37113077401 is capturing actual broker socket calls; the IPv6 failure remains unresolved until evidence and corrected acceptance. Windows installer run 37111046901 SUCCESS, three local installers hash-recorded; native support stays x64-only.

## Second evidence checkpoint and stronger gate

7c8a04f run 37113077401: Linux ARM64/amd64 completed all traced broker lifecycle and provider gates SUCCESS. This is **not a runtime fix**: no product source changed, the tracer alters scheduling, and first broker TCP6 still costs about 3.6 seconds. Keep the original untraced gate as a separate mandatory invocation. On Windows, both product feature tests passed, but the emergency-reset utility included by `--ignored` failed `TUN_CLEANUP_FAILED`; its later finally invocation succeeded. Preserve that failure and investigate cleanup timing rather than claim an overall green workflow.

- [x] Extend isolated Rust session to TCP4 and TCP6, not TCP4 only; test remains ignored outside guarded fixtures.
- [x] Require per-request TUN RX progress and REJECT denial for both literal address families in direct-native and broker fixtures. Aggregate RX from a preceding TCP4 request cannot establish IPv6 capture.
- [x] Print bounded synthetic traces on success too, because tracer scheduling can hide the original error.
- [x] Add mandatory untraced broker invocation after traced acceptance in `.github/workflows/mihomo-desktop-tun.yml`; no retries/skip/timeout increase.
- [x] Require these strengthened hosted gates to pass before interpreting any earlier green IPv6 traffic result as correct native capture.

### Task 4: Observe IPv6 data path without ptrace scheduling

Evidence at 166f6a1 / 37113670171: traced ARM64 broker cycles pass; untraced actual Rust TCP6 receives EOF with no fixture body after 5 seconds. Traced core egress binds phys0 successfully and uses physical fdfe:dcba:9900::1, so disabling DNS/IPv6 or changing core source binding is unsupported. The existing kernel plan places both explicit-source /1 goto-main rules before the TUN-source lookup; an application bound to the TUN IPv6 address may therefore change path after a route-cache update. This is a hypothesis, not yet the root-cause claim.

- [x] Add a bounded 256-entry IPv6 TCP header-only AF_PACKET ring to the synthetic namespace fixture; filter literal fixture/TUN addresses, retain seq/ack/flags/length/interface only, never packet payloads. Guard construction with root/explicit opt-in/different-parent-netns/private-run checks. Dump ring on original failure and join the collector in finally.
- [x] Print actual Rust socket local addresses and fixture `ip -6 route get <TARGET6> from fdfe:dcba:5288::1 iif lo` before broker traffic. Use captured Tun-to-physical packet/routing evidence to decide whether the owned-source rule order needs correction.
- [x] Do not alter runtime policy or increase traffic deadlines until that evidence identifies the path. Any rule correction must stay within existing owner priorities/WAL and preserve normal core physical egress/foreign rules/cleanup gates.

Task 4 evidence at 79d9fb4 / 37114332615: the traced Rust TCP6 succeeded, but the next Python broker cycle's second TCP6 timed out. The recorded inbound TUN handshake and GET reached the stack, and the first request's physical HTTP response returned correctly. The final physical IPv6 connect remained EINPROGRESS with no observed SYN-ACK. The bound-TUN-source route resolves through phys0, but this alone does not prove it is the failing socket (the native egress is bound to phys0). Capture outgoing headers too: AF_PACKET ETH_P_ALL instead of ETH_P_IPV6, still SOCK_DGRAM/network headers and the existing pure address/protocol filter; retain neither link payloads nor HTTP content. Do not change policy ordering on this incomplete evidence.

### Task 5: Verify the physical peer before changing routing

At 5a17224 / 37114754380 all traced broker cycles passed, but an untraced replacement session timed out. Outgoing headers show five physical SYNs sourced from **fdfe:dcba:9900::1 on phys0**, with no physical SYN-ACK. This refutes the proposed TUN-source-binding explanation for that failure. Observe bounded NS/NA headers (type/code/target only) and peer IPv6 neighbor/listener/route state in the synthetic namespace to distinguish physical NDP loss from a dead fixture listener.

- [x] Add failing pure `test_neighbor_evidence_retains_headers_only` with a synthetic ICMPv6 NS plus private option sentinel. Expect missing `ipv6_neighbor_header`; assert output contains only target/type, not option bytes, and truncated headers are rejected.
- [x] Implement `ipv6_neighbor_header(packet, interface)` for valid literal fixture-prefix ICMPv6 135/136 only, at least 64 bytes, preserve no options; combine it with the existing TCP header parser in the bounded 256-row ring. On the original exception, read `ip -n nimbo-fixture -j -6 neigh show`, `ip -n nimbo-fixture -6 route get fdfe:dcba:9900::1 from fdfe:dcba:9901::10`, and `ip netns exec nimbo-fixture ss -6 -n -t -a -i`, truncate each to 16384 chars. No public addresses, packet payloads, product logger change or retries.
- [x] Run pure tests/syntax/diff check and original mandatory traced/untraced ARM64 gates; record packet/peer evidence before any runtime correction.

### Task 6: Correct the broken peer link-local baseline and require cold return-path NDP

Evidence at b20d47f / 37115357150 **Linux amd64**: the physical SYN uses fdfe:dcba:9900::1, peer `ss` has an alive LISTEN socket and SYN-RECV for that exact connection, return route is peer0, neighbor fdfe:dcba:9900::1 is INCOMPLETE, and no NS was emitted. Thus the same failure is not ARM-specific and occurs at the physical peer return path, not YAML, native binding or TUN-source policy. Both synthetic veths use `addrgenmode none` to avoid asynchronous DAD but have no manual link-local addresses. Linux ndisc_send_ns returns without sending when no packet-selected source exists and ipv6_get_lladdr cannot find a usable address; a SYN-ACK sourced from the loopback fixture target triggers this fallback. Primary source: https://raw.githubusercontent.com/torvalds/linux/v6.8/net/ipv6/ndisc.c (ndisc_send_ns / ndisc_solicit).

Files: `scripts/ci/test-mihomo-desktop-netns.py` (fixture-only deterministic link-local setup and preflight), existing pure diagnostic tests, existing full broker native gate unchanged. Product routing, kernel global IPv6, five-second deadlines and owner/WAL logic stay untouched.

- [x] Add a failing mock test for `configure_link_local()`: exactly `ip -6 addr add fe80::1/64 dev phys0 nodad` and `ip -n nimbo-fixture -6 addr add fe80::2/64 dev peer0 nodad`. Expect function absent; do not install static neighbor entries.
- [x] Implement those two commands after physical links are up and before snapshot. Keep addrgenmode none, so no delayed automatic LL DAD can change the strict baseline.
- [x] Add `verify_peer_ipv6_return_path()`: GET literal TARGET6 with the original five-second deadline to warm the outgoing neighbor; flush only peer0's synthetic IPv6 neighbor cache; repeat the GET requiring the exact body. This cold return path must pass before any native core startup, so a broken fixture cannot be mistaken for a core regression. Both links remain dynamic NDP, not fixed MAC neighbors.
- [x] Run pure tests, syntax and diff check; push and require original per-family RX/REJECT, traced/untraced Rust/broker/provider/crash/exact-restore gates on both Linux architectures. Do not claim a product IPv6 fix if only the fixture was corrected.

Verified Task 6 checkpoint: 63bc144 / 37115765634 completed **SUCCESS on Windows x64, Linux amd64 and Linux ARM64**, including the untraced IPv6 Rust/broker gate previously failing, all original crash/foreign-rule/exact-restore/provider gates and the new pre-core cold NDP control. No product Linux source or policy changed. Six pure tests first failed for absent helpers then passed; syntax/fmt/diff checks pass. Project 37115768222 and Android/shared 37115768199 SUCCESS. Fresh Windows installers 37114600859 (product 79d9fb4, reset fix included) still building, publish=false.
