# Nimbo embedded core adapter (wire API 1)

This is executable Go source using **actual Mihomo v1.19.31**, not a YAML-to-server
converter. Read [API.md](API.md). It is an initial managed native component, **not
full Android/iOS/desktop VPN support and not a release-readiness claim**.

## Source build

From repository root, run `scripts/ci/build-mihomo-core.ps1`. Requires already
installed Go 1.27.1 (optional `-GoRoot`); no OS/toolchain installation or downloaded
binary execution. Source comes through proxy.golang.org with sum.golang.org and
explicit pinned sums. Build/test concurrency is two. Generated artifacts, caches
and the sole patched dependency live under `.build/`. `-Resolve` is a maintainer
operation for dependency changes, never needed for a checked-in lock.

Output: `.build/bin/nimbo-mihomo.exe`, `.build/bin/build-manifest.json`, test log.
No binary is published. `pins.json`, go.mod/go.sum, patch and licenses are part of
the reproducible source input. CLI and gomobile use the exact same `Invoke`.
Optional `cmd/cbridge` contains versioned C entry/free symbols; CGO/linking requires
a platform compiler and is a separate build gate. Do not add another Go runtime
beside an existing Go archive to use this wrapper.

## Desktop implemented semantics and explicit limits

* Inspect returns exact original UTF-8, SHA-256 and complete declared proxy/group/
  provider mappings. Unknown keys remain in original/graph; strict issues prevent
  running them. Aliases are resolved only in the projection, never exported over
  the original source. Duplicate keys/multiple docs/alias bombs/non-JSON scalars fail.
* Native parse validates groups, references, protocols, rules. Supported groups:
  select, url-test, fallback, load-balance; dynamic use/include-all and category
  metadata (hidden/icon) remain native. No fake fixed server list is created.
* Admitted outbound schemas: direct, reject, ss, socks5, http, vmess, vless, trojan,
  hysteria2, tuic, anytls. Fields derive from pinned option structs. Opaque plugin
  maps, unknown fields, dialer-proxy chains/interface/routing marks and other
  protocols are explicit UNSUPPORTED_CONFIG, not discarded. This list is narrower
  than upstream; tests do not certify transport interoperability for every type.
* Proxy providers: inline/file/http, bounded YAML, filter/exclude-filter/exclude-type,
  interval and health checks. Native provider objects and dynamic native group
  resolution. Payloads are checked before atomic publication; duplicate/missing
  names/unknown protocol options fail the whole refresh. URL subscriptions converted
  from base64, provider override expressions/headers/age and MRS are not admitted yet.
* Application rules and logical/sub-rules are actual Mihomo routing, not OS routes.
  YAML/text rule providers: domain/ipcidr/classical, inline/file/http; invalid entries
  cannot silently disappear. Geosite/GEOIP/ASN asset management and process/UID
  ownership remain gates. Native parser, not a homemade routing engine, executes rules.
* Internal Mihomo DNS resolver and hosts are separate from **host DNS settings**.
  No DNS socket listener is created. Fake-IP/TUN DNS planning, DNS rule-provider
  policies and geodata filters are not admitted. IPv6 policy is currently disabled
  explicitly; enabling it requires platform tests, not source option coercion.
* Live managed inbound is TCP HTTP/SOCKS on an explicitly numeric loopback address.
  UDP/SOCKS UDP-associate and TUN are not claimed. Source listeners/controller/UI,
  OS routes, iptables, NTP clock changes, sniffing and other unimplemented top-level
  settings fail admission. Only harmless disabled flags for these fields are accepted.
* Managed defaults when fields are absent: IPv6 off, process lookup off, native logs
  silent, fallback geodata off, no automatic upstream controller/listeners/OS updates.
  These are documented defaults, not source rewriting. An explicit contrary option
  is rejected. Runtime stores the original YAML unchanged in memory until stop.
* Selection is session-only. `profile.store-selected: true` and store-fake-ip are
  currently rejected rather than falsely promising persistence. Provider source
  caches are app-owned files. Subscription metadata/ETag persistence are not claimed.

## Lifecycle, IO and ownership

There is one Mihomo instance per process. All native state mutations serialize;
status and stop cancellation do not wait for the operation lock. A runtime-owned
context cancels start, refresh and delay. Start's deadline is 20s default/25s max;
socket/provider HTTP deadlines are bounded. Native parse itself is synchronous;
hard cancellation of pathological upstream CPU parsing remains a process-isolation
or bounded-input/platform validation concern. A desktop owner can terminate its job
after a failed bounded stop. No mobile watchdog readiness is inferred from this.

Start commits generation only after providers/listeners initialize. Rollback closes
app-owned resources. Stale-generation stop cannot cancel a newer running instance.
Mihomo global tunnel maps/resolvers are exclusively owned while running. Stop closes
accepted sockets including half-open proxy handshakes, native trackers and providers.
Upstream internal package goroutines exist for the process lifetime; this is not a
claim that the Go runtime unloads or every upstream goroutine joins synchronously.

Upstream cachefile.Cache is a sync.Once singleton with an exposed DB pointer. Adapter
explicitly owns, closes and reopens that DB for each dataDir. Corrupt cache preflight
fails without invoking upstream's delete-corrupt-cache recovery. Regression tests
reopen and rename the actual Windows DB after stop, including dataDir changes.
Inspect has no IO. Validate uses the native parser but does not apply config/fetch
providers/open cache/bind listeners; tests assert no dataDir writes. Some native
parsers can read configuration-related state: do not call validate “arbitrary full
Mihomo offline validation” outside this admitted schema.

## Mobile gates (must not enable picker as ready)

`StartIOS` returns explicit PLATFORM_UNAVAILABLE (INVALID_FD for nonpositive FD).
`SetSocketProtector` is a real fail-closed outgoing dialer hook, not proof that every
mobile DNS/provider/UDP path is protected. Both mobile owners require an egress audit.

1. **iOS:** source-link into existing LibXray+AWG single Go archive, preserve API3/
   diagnostics/free ABI, set replacement in the ROOT Go module. Existing PacketTunnel
   owns NE settings and Xray currently owns its only TUN reader. Stop/join old owner;
   native must dup trusted borrowedFD and close only the duplicate. Pinned sing-tun
   Darwin owns the supplied FD and its close invokes `dscacheutil -flushcache`; this
   path requires an explicit reviewed iOS-safe source patch or alternate packetFlow
   transport before it can be enabled. Four-byte AF framing, nonblocking changes,
   FD identity, MTU/address/DNS interception and real stack-ready ACK need device tests.
2. **Android:** current source has gojni AND separate wg-go, not an already merged
   archive. Agree packaging/migration first; do not add a third runtime. Establish
   via VpnService, borrow/dup FD, protect/bind every egress TCP/UDP/DNS socket before
   traffic; fail closed on protection failure. Disable OS auto-route/redirect/discovery.
3. Managed cancellation by unique requestId and Android FD handoff are implemented;
   mobile traffic counters,
   network changes/sleep/wake, actual IPv4/IPv6 TCP/UDP/DNS packet traffic and leaks
   remain acceptance gates. A loopback listener cannot acknowledge mobile TUN readiness.

Canonical handoffs: `iosApp/docs/mihomo-ios-integration-handoff.md` and
`app/docs/universal-redesign/reachable-surfaces/ANDROID-MIHOMO-HOOKS.md` (read-only).

## Upstream source / licensing

Mihomo commit `ab405bad5beeeac8b003bb01f60f134f6df54471`:
https://raw.githubusercontent.com/MetaCubeX/mihomo/ab405bad5beeeac8b003bb01f60f134f6df54471/go.mod
and sibling LICENSE, adapter/provider/provider.go, hub/executor/executor.go,
component/profile/cachefile/cache.go, listener/sing_tun/server.go are the audited
primary source references. Module metadata/checksums are pinned in pins.json/go.sum.

Mihomo declares GPL-3.0; retain its license/copyright/readme notices and audit the
combined application's GPL compatibility **before distribution**. README's downstream
naming restriction is retained; product is Nimbo, not renamed to upstream's product.
The protobuf fork retains its BSD license and PATENTS. The only vendor change is
go.mod `go 1.20` -> `go 1.22` (actual source uses integer range); exact original/patched
hashes and diff are retained and every file is verified against the immutable source.
No conflict-policy weakening, module-cache modification or dependency-binary download.

`collect-mihomo-source-manifest.ps1` collects dependency notices and hashes. Source
distribution must include the corresponding pinned source, local adapter/modifications,
build/install scripts and required notices; a binary plus go.sum alone is not a
complete GPL source offer. App-store/platform terms and complete transitive notice
inventory remain release/legal review gates, not waived by passing unit tests.

## Constrained Android native milestone (2026-09-24)

See [ANDROID-MILESTONE.md](ANDROID-MILESTONE.md) and the trusted Android section of [API.md](API.md). StartAndroid + with_gvisor owns a CLOEXEC duplicate and session-scoped IPv4 TCP/UDP/DNS tasks. Generic Android Invoke start is rejected. Android rule mode now supports only native IP-CIDR/SRC-IP-CIDR, port, network, managed RULE-SET and explicit final MATCH rules; unsupported rule classes are rejected and unmatched traffic drops. This is not full Mihomo mobile support; keep the product gate. Bootstrap DNS and provider HTTP are protected direct; application DNS follows the session's selected native rule graph over TCP without implicit direct fallback. Desktop behavior above remains separate.

