# Desktop native TUN checkpoint — 2026-10-02

Source-built pinned Linux/amd64 binary exercised in fresh WSL Linux network +
mount namespaces, not the host namespace. Nine actual start/traffic/stop cycles
passed: EOF, SIGTERM and generation-bound controller stop, each repeated three
times. Each cycle validates TCPv4/TCPv6/UDP, DNS over UDP/TCP, native selection,
REJECT without a direct fallback, resuming the selected group, stale generation
and exact restoration of the pre-start route/rule snapshot. No public Internet,
provider credentials, host DNS or physical host routes were used.

Partial startup rollback (occupied controller after TUN construction) also passed:
no retained interface or route/rule entries and the next owner acquired its lease.
The fixture exposed asynchronous native interface retirement: Close now waits
up to two seconds before releasing ownership. Cleanup failures propagate through
the native API and produce a failed CLI exit rather than a success indication.

Windows host Go suite with_gvisor: 182 passed test cases, zero failures; one
private Android provider fixture deliberately skipped (no user secrets loaded).
Default build tests and go vet passed. Pure iOS source gates: 9 packet contracts
and 6 merged-source contracts passed; these are not C ABI or Apple compile tests.
The prior full IPA at 98a6a59 passed separately (GitHub run 37028406362).

An actual namespace fixture exposed and fixed two desktop socket bugs: the mobile
hook suppressing native physical-interface selection (TUN loop), and rejection of
the valid empty UDP wildcard host. Windows dual-stack UDP binds both families.
No Windows TUN adapter/DNS runtime acceptance is claimed from host Go compilation.

Remaining release gates: protected verified helper installation, authorized UID/SID
IPC lease, GUI endpoint lifetime, service hard-crash route/DNS recovery, Windows
adapter acceptance, explicit System Proxy backend on Linux, and firewall Kill Switch.
These remain closed in desktop availability. Source-based CI tests both Linux CPU
architectures and builds Windows; it creates internal artifacts, not public releases.

The historical freeze below describes the narrower 2026-09-20 proxy milestone,
not the current mobile or privileged TUN implementation.

---

# Native component freeze — 2026-09-20

## Executed evidence

Host: Windows/amd64, existing Go 1.27.1, CGO_ENABLED=0, GOMAXPROCS=2, build -p=2.
All test destinations are generated loopback fixtures; no user profile, host TUN,
route, system DNS, system proxy or firewall operation was used.

* Full shuffled Go suite: **23 top-level tests, 41 pass events including subtests,
  zero failed/skipped**. JSON log `.build/final-tests.jsonl`.
* Lifecycle/cache/cancellation/protection subset repeated **10 times**, passed:
  `.build/repeat-tests.log`.
* Actual same suite compiled/executed with **with_gvisor**, passed:
  `.build/with-gvisor-tests.log` (not a TUN test).
* Source module verification: `go mod verify` passes. Local protobuf is separately
  verified file-by-file against its pinned upstream tree; only go.mod differs.
* 177 modules inventoried, 103 reached by the CLI build, **zero reached modules
  missing collected notices**. This is an inventory, not a legal clearance.
* Actual CLI/Invoke golden: `testdata/inspect-wire-v1.json`, public source in
  `testdata/inspect-source.yaml`, original source SHA-256
  `dad64b17d4e83a4edaad211d721aec46f5e1c289bac22b2561247a16b2c8b507`.
  Main independently reported the Go→Kotlin exact-source/digest/graph contract passed.

Tests cover actual HTTP proxy traffic, DIRECT/REJECT/group readback, loopback bearer
controller, native group types and include-all ordering, strict source retention,
reference/cycle errors, invalid provider atomic rollback, application RULE-SET
routing and refresh, internal DNS through a loopback UDP DNS fixture, socket-protect
false/panic fail-closed, cancellation during start/delay/refresh, cancel before
dispatch/late cancel distinction, stale generations, concurrent idempotent stop,
accepted-handshake socket closure, DB close/reopen/Windows file rename across
different dataDirs, no validation cache writes, and no fabricated mobile FD success.

## Honest gates

* Race detector was attempted but unavailable: `go: -race requires cgo`; no C
  compiler was installed/found. Concurrent functional tests are **not** a race
  detector pass. Upstream background-provider/health-check goroutine convergence
  and selection/traffic races require a supported CGO/race environment and further
  lifecycle review before mobile embedding. No synchronous unload/join guarantee
  for all upstream goroutines is claimed.
* C wrapper is source only; no C link, gomobile AAR/framework or Apple SDK build was
  verified here. This module's standalone lock is not proof that the final merged
  LibXray/AWG/Mihomo mobile root builds; propagate ROOT replacements and retest it.
* iOS/Android exclusive FD ownership, duplication/close rollback, packet readiness,
  all-path TCP/UDP/DNS socket protection, network settings, IPv6, sleep/wake/network
  transitions and actual device traffic are unimplemented/unverified gates. iOS
  Darwin DNS shell helper also needs an explicit mobile-safe treatment. Android
  currently has separate gojni/wg-go: no already-merged-runtime assumption.
* Full upstream config support is **not** claimed. Exact admitted fields/protocols,
  rules/providers/DNS and explicit unsupported features are in README.md/policy.go.
  Unsupported original YAML is preserved for future support, never flattened into
  an apparently working server profile. No final app installers were produced.

## Files / final artifact provenance

New module files: API.md, README.md, VERIFICATION.md, go.mod/go.sum, pins.json,
protobuf-directive.patch; inspect.go, policy.go, json.go, runtime.go, listeners.go,
providers.go, ruleproviders.go, dns.go, cache.go, platform.go; runtime_test.go,
cache_test.go, hardening_test.go; cmd/nimbo-mihomo/main.go, cmd/cbridge/main.go;
testdata pair; licenses/ (notice archive isolated by its own go.mod) and
source-license-manifest.json. Generated caches/logs/binary remain in `.build/`.

New build helpers only: scripts/ci/build-mihomo-core.ps1 and
scripts/ci/collect-mihomo-source-manifest.ps1. No GoBridge, Gradle, existing platform
runtime or UI file was edited by this component task.

The **authoritative final binary hash** is `.build/bin/build-manifest.json`, not an
earlier message or this document (avoids a self-referential source hash). It records
the binary, toolchain, upstream commit, locks, pins, license manifest and allowlisted
sourceFiles hashes. Source inputs are hashed before tests/build and checked again
afterwards; a changed source aborts manifest publication. Freeze only after this
final helper succeeds, then Dirac stages and reruns real Rust→helper IPC tests.


## Linux native crash-rule recovery (3 October 2026)

The root desktop owner writes a bounded, mode-0600 `/run/nimbo-mihomo-tun-rules.json`
plan before the first kernel rule addition. It records boot ID, network namespace,
PID/start time, exact selectors/actions and a random per-session fwmark with zero
mask. The zero mask is metadata only, not an additional packet filter. The pinned
sing-tun ownership patch routes cleanup through this exact plan; ordinary mobile
and Windows construction leave those trusted callbacks unset. Callbacks are
excluded from subscription YAML/JSON. The pinned netlink readback patch retains
rule action and mark presence, so foreign action changes are not normalized away.

`nimbo-mihomo recover-tun` is a fixed Linux-root CLI, not an Invoke operation or a
client-selected path. It requires the exclusive ownership lock, same boot/netns,
a dead journal process identity and a retired TUN. It only deletes exact marked
planned rules; unknown marked rules and invalid/unsafe journals fail closed.
Foreign routes, interfaces and same-priority rules are not deleted. A retained or
unsafe WAL also reserves network ownership across helper restart, preventing
legacy Xray/AWG from taking over unknown state; a failed down keeps its lease. Both normal
close and abnormal-child broker cleanup verify rule retirement before deleting
and fsyncing the journal. A later native start can replay a dead owner's WAL after
both native and helper SIGKILL. Old unjournaled stale state is deliberately not
adopted; broad migration cleanup is unsafe.

Local opt-in tests ran in fresh network AND mount namespaces with private `/run`
and installation roots. They exercised TCP4/6, UDP, UDP/TCP DNS, hot selection,
ordinary shutdown, native/helper/both-process crashes, a partial installed WAL
subset, foreign-rule retention and refusal of live-owner, wrong-identity,
symlink, non-private, oversized and unknown-mark state. This does not verify
physical-machine power loss, Windows TUN ownership or real-provider availability.
