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
- [ ] Add pure trace-tail test before implementation:
```python
with tempfile.TemporaryDirectory() as directory:
    path = Path(directory) / 'socket.trace'
    path.write_bytes(b'x' * 20000 + b'connect-failed\n')
    tail = broker.trace_tail(path)
    assert len(tail.encode()) <= 16384
    assert tail.endswith('connect-failed\n')
```
- [ ] Run `python -m unittest discover -s scripts/ci -p test_mihomo_netns_diagnostics.py`; expect a missing trace-tail helper failure first.
- [ ] Implement `trace_tail(path)` using a 16384-byte seek-from-end read with UTF-8 replacement. Add `--trace-native` and attach `strace -f -qq -s 96 -e trace=connect,bind,setsockopt,getsockname,getpeername -o <private-temp-file> -p <helper-pid>` only after existing root/fresh-netns/private-mount checks. Wait for TracerPid to confirm attachment. Keep tracing processes joined in finally, dump bounded traces and `ip -j -6 addr/neigh` on failure, re-raise the original exception.
- [ ] Require strace in hosted Linux CI; pass `--trace-native`. Run pure unittest and Python syntax checks, inspect diff, commit only these files and push. Read real ARM64 logs; no retry wrapper around failed traffic.

## Task 2: Evidence-led correction and regression
- [ ] Record failing socket errno/source/interface and whether the peer received the HTTP request in this plan before changing runtime behavior. Trace evidence must distinguish route/dial failure from fixture handler failure.
- [ ] For the Rust isolated session, loop over both literal targets:
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
- [ ] Update this plan with the exact minimal correction and regression files based on captured evidence; do not speculate by changing DNS, GC limits, kernel IPv6 or timeouts.
- [ ] Run scoped non-mutating Rust/Go tests, fmt and Clippy. Push and require full native traffic, hot selection, cancellation, crash and exact-restore gates on Linux ARM64/amd64 and Windows x64. A green build without these gates is not success.

## Task 3: Delivery and readiness
- [ ] Check Windows installer run `37111046901` (product `923b851`, publish=false), including rollback/embedded-core checks and artifacts.
- [ ] Mirror only baseline/receipt-matching intentional source files into the primary workspace; preserve unrelated frozen sources and notices.
- [ ] Update readiness and existing PR with verified outcomes. Keep remaining physical-device/provider/IPv6-bypass and reboot-persistent KS limitations explicit.
