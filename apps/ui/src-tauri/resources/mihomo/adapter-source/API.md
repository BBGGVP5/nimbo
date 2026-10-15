# Nimbo native wire API 1

Library: `mihomocore.Invoke(requestJSON string) string`. CLI and authenticated
controller call this exact function. Process-global singleton; never embed a second
Go runtime beside LibXray/AWG. Integrate Go source into the existing archive later.

Envelope: `{apiVersion:1,requestId:string,success:boolean,generation:uint64,
data?:object,error?:{code:string,message:string,path?:string}}`.
Generation starts at zero and increments on successful start. Optional request
`generation` checks stale sessions. Integers are JSON numbers (desktop u64).
Request: `{apiVersion:1,requestId,operation,yaml?,options?,generation?,group?,name?,
url?,timeoutMs?,expectedStatus?,targetRequestId?}`. Unknown/duplicate request fields are errors.

* `telemetry`: requires the active `generation`; read-only aggregate counters
  `{upload,download,proxyUpload,proxyDownload,directUpload,directDownload,
  routeAvailable,tcpConnections,udpConnections}`. Bytes are cumulative session
  deltas, protocol counts are currently active core-tracked connections.
  No destination, node identifiers or credentials are returned. The generation
  is checked before and after the read; missing/stale sessions fail, not zero.
  Older unpatched route managers return `routeAvailable:false`. This does not
  reset counters or create a connection-history database/polling task.
* `inspect`: exact UTF-8 source in `yaml`; no network, native construction or disk IO.
  Returns `{originalYAML,sourceSHA256,documentKind,rootKeys,declaredGraph:{mode,proxies:[],groups:[],providers:{}},
  strictIssues:[{code,message,path}]}`. Graph entries retain declared YAML
  mappings and unknown fields, except `smart` groups are projected to stable
  `url-test` (`interval:600`, `lazy:true`, `tolerance:100`) with ML/telemetry
  options removed. Dynamic flags/use are not materialized.
  `documentKind` is `mihomo` only when a parsed root mapping contains a recognized
  Mihomo key; `rootKeys` are sorted keys from that parsed mapping. Byte-exact source
  is authoritative (comments, anchors, ordering retained there).
  Unknown/unsupported content remains inspectable, but cannot validate/start.
* `probeDesktop`: stopped ownership only; `yaml,name,url,timeoutMs` (100..30000)
  and `expectedStatus`. CLI `nimbo-mihomo probe` accepts only this operation,
  replies once and exits. Checks a named static outbound with real HTTP GET.
  Returns `{delayMs,sourceSHA256,scope:"desktop-offline-probe",vpnStarted:false}`
  with generation zero. Only outbound adapters, static hosts and internal DNS
  are constructed; full source listeners/TUN/rules/providers, cache and native
  session are never loaded. Native start admission remains unchanged. Static
  nested groups check the first healthy declared leaf, not an active selection.
  Dynamic/provider/filter groups and runtime-dependent/chained outbounds fail
  explicitly with `PROBE_REQUIRES_SESSION`. Group membership and proxy protocol
  are not converted to Xray. No provider/asset downloads or persistence occur.
  Timeout/cancel closes adapters and restores resolver/log/IPv6 globals. Rust
  owns/reaps this child on Connect/Disconnect intent and validates source/scope/
  no-VPN proof before a latency value is allowed across frontend IPC.
  Independent desktop checks remove bare `#Group` DNS-routing hints from their
  ephemeral resolver projection, preserving `key=value` transport parameters,
  DNS endpoints and original source/hash. Such group hints alone do not require
  VPN startup; the configured resolver is contacted on the physical network.
* `diagnosticConfig`: returns the last prepared Android raw configuration, or
  `{available:false}` if none was prepared. Serialized with native operations;
  no network or disk IO, no core restart. This is an internal sensitive snapshot,
  not a sanitized export. Kotlin's fail-closed redactor must run before writing
  or sharing it. It can predate current settings and excludes final TUN ownership
  projection; reports label that scope explicitly.
* `validate`: strict policy then actual pinned Mihomo config parser, only stopped.
  No provider fetch, listener, TUN or rule asset downloads. Native parse errors fail.
* `preflightAndroid`: `yaml`; strict inspection followed by the unchanged Android
  mobile policy. Returns `{valid:true,sourceSHA256,scope:"android-vpn-policy"}`.
  Pure source admission: no native constructors, global mutations, disk/network
  IO, provider fetches or listeners. Available while a runtime is active, without
  taking the native-operation lock; optional generation checks still apply.
  The hash covers the exact supplied UTF-8 bytes. Unsupported source fails with
  the existing inspection/strict/mobile policy error, never by dropping fields.
  This does not verify native construction, fetched provider contents, compiled
  Android support, FD/protector readiness or external connectivity. StartAndroid
  repeats admission and performs runtime checks; provider rows are checked on load.
* `start`: `yaml` and `options:{dataDir:absoluteAppOwnedPath,
  networkOwner:"desktop-proxy",mixedAddress:"127.0.0.1:0",
  controllerAddress:"127.0.0.1:0",secret:randomBearerAtLeast32Chars,
  startupTimeoutMs?:20000,adBlocking?:false}`. Deadline range 1..25000 ms.
  `adBlocking:true` prepends the bounded local advertising-domain REJECT rules
  and enables runtime HTTP/TLS/QUIC sniffing for missing protocols; source YAML,
  DNS, existing exclusions and provider/custom rules remain intact. It requires
  rule mode (`AD_BLOCKING_REQUIRES_RULE_MODE` otherwise), and does not claim to
  block encrypted DNS/ECH, IP-only or direct OS-bypass traffic. Off is omitted
  by callers for compatibility with older native bridges. No list download.
  Returns status only once listeners and providers are ready; rollback on error.
* `status`: `{state,mixedAddress,controllerAddress,networkOwner,sourceSHA256,
  coreVersion:"v1.19.32",coreCommit,apiVersion:1}`. No secret/source disclosed.
  States: stopped, starting, running, stopping; Android can additionally report failed.
* `snapshot`: `{groups:{name:upstreamProxyDTO},providers:{name:{name,vehicleType,
  version,proxies:upstreamProxyDTO[]}}}`. Snapshot members may change dynamically.
* `select`: `group,name`; actual Set and snapshot readback. Missing selection fails.
  A valid changed choice closes captured established connections whose native
  chain contains this exact group, so clients reconnect through the new choice.
  Same/invalid selections and other groups are not interrupted. Mobile route
  lookup/dial/registration owns the graph read lock, never the relay lifetime.
  The one-shot `autoSelect` uses the same established-connection handoff.
* `refreshProvider`: `name`; strict payload parsing before native atomic membership
  replacement. Errors retain last valid membership. Returns snapshot.
* `refreshRuleProvider`: `name`; same atomic strict replacement for rule providers.
  Snapshot additionally includes `ruleProviders:{name:{name,vehicleType,behavior,ruleCount}}`.
* `delay`: `name,url,timeoutMs` (1..30000), `expectedStatus` (upstream range syntax).
  Explicit HTTP(S) URL required, real outbound test. Returns `{delayMs}`.
* `stop`: idempotent, closes listeners, accepted sockets, providers and connections.
* `cancel`: `targetRequestId`; cancels a pending start without waiting for the operation
  lock. Can precede start dispatch: bounded tombstones retain the unique ID for two
  minutes (max128). Late cancel after commit returns ALREADY_COMMITTED; use the
  acknowledged generation with stop. Do not reuse start request IDs.

CLI `inspect` consumes raw UTF-8 YAML stdin to EOF (4 MiB cap), emits one envelope.
CLI `serve` consumes one start request to EOF (8 MiB cap), emits one readiness
envelope, stays alive until stop or interrupt. Error exit 1; usage exit 2.
Stdout is protocol-only; logs use stderr. HTTP `POST /v1/invoke` requires
`Authorization: Bearer <secret>`, JSON body <=8 MiB; no CORS/browser Origin.
HTTP errors 401/403/404/405/413; operation errors are envelopes with HTTP 200.
Only numeric loopback listen IPs accepted. No upstream controller/UI server.

Start deadline defaults to 20s and is capped at 25s (below iOS's 30s watchdog).
Stop cancels the runtime context independently of the operation lock, including
pending provider fetches and delay tests; generation is checked before cancel.
Start failure rolls back before its response. RequestId cancellation is implemented
for the managed lifecycle including the trusted Android FD entry described below.
Do not infer readiness from spawn or an open port. Stop before switching cores.
No automatic restart/retry of failed starts with unknown caller intent.

Desktop runs **managed TCP HTTP/SOCKS mixed proxy**, not platform VPN.
iOS ownership/FD injection remains PLATFORM_UNAVAILABLE. Android has a constrained
trusted FD entry, not full Mihomo mobile product support. Source config host listener, TUN,
host routing and unsafe/controller fields are rejected, never silently overridden.
Mihomo application rules, logical/sub-rules, strict YAML/text rule providers and
internal DNS resolution are separate from host ownership and are implemented.
Geodata/process rules, binary MRS and fake-IP/mobile DNS planning remain explicit gates.
Managed defaults and exact supported schema are documented in README.md and
enforced in policy.go. Inspect is lossless, not a promise every config can run.

## Trusted Android entry (compile capability, NOT release readiness)

`StartAndroid(requestJSON string, borrowedFD int64) string` takes an API1 `start`
request with `options.networkOwner:"android-vpn"`, absolute app-owned `dataDir`,
optional startupTimeoutMs, and no mixedAddress/controllerAddress/secret. FD cannot
be supplied by JSON. On GOOS=android **every generic Invoke start is rejected**,
including networkOwner desktop-proxy. Other Invoke operations remain API1.

`SetSocketProtector(p SocketProtector) string`: register while stopped; interface
method `Protect(fd int64) bool`. Required Android pre-bind/connect callback; false,
nil, panic or canceled session rejects egress. Callback must be synchronous/bounded,
must not reenter Invoke, and must remain alive until stop has completed.

`AndroidTunPlan() string` is a capability DTO `{apiVersion:1,success:true,data:{...}}`,
not a session/readiness envelope. `compiled` is true only for android+with_gvisor;
`deviceVerified:false` is deliberate. Address 172.19.0.1/30, route 0.0.0.0/0, DNS
172.19.0.2, MTU1500, IPv6 blocked by Android (do not allowFamily(AF_INET6)), ICMP
dropped; no OS route/redirect/interface discovery. Native cannot verify Builder
settings, per-app coverage or kernel behavior merely from an integer descriptor.

Original FD remains Android-owned on success AND failure. Keep it open through
stop; native atomically duplicates with CLOEXEC, validates nonblocking read/write
single-queue IFF_TUN|IFF_NO_PI, owns only that duplicate and one fdbased reader.
No temporary blocking-mode mutation on the shared open file description. Cancel
start by unique requestId; stop before closing original or switching engine.

Start returns `state:running,tunReady:true` only after providers initialize and the
gVisor endpoint attaches. It does NOT prove external connectivity. Reader exit
cancels the session/protected egress and status returns `failed` (tunReady omitted
means false). Owner must poll status, stop and reconcile foreground state. No
native autonomous restart. Stop joins adapter tasks and packet reader, closes
duplicate, then restores globals. Underlying-network changes require stop/restart.

Admission is detailed in [ANDROID-MILESTONE.md](ANDROID-MILESTONE.md): global mode
with an explicit GLOBAL select group, or a bounded rule mode using only
IP-CIDR/SRC-IP-CIDR, DST-PORT/SRC-PORT, NETWORK, RULE-SET and final MATCH rules.
Rule mode requires an explicit terminal MATCH target; unsupported host/process,
geodata and sub-rule features are rejected, and unmatched traffic fails closed.
Android additionally admits native `url-test`, `fallback` and `load-balance`
groups with bounded health-check URL/interval/timeout settings. Their proxy tests
use the ordinary native adapters and the Android socket protector; automatic
provider subscription downloads (`proxy-providers.interval`) remain unsupported.
Application DNS follows the same session-owned native rule graph over TCP with no
direct fallback; numeric bootstrap DNS/provider HTTP are intentionally protected
DIRECT, not private DNS. No mobile counters API, IPv6, device leak test or broad
transport interoperability claim. Preserve the production Android picker/readiness
gate.

The current Android transport admission additionally includes VMess TCP and
WebSocket-over-TCP (plain or TLS) without early data: one independently dialed
protected socket per stream, bounded path/headers, actual WebSocket upgrade,
self-signed local TLS handshake, provider parsing, and VMess relay/stop-join
tests. WebSocket early-data query/config, V2Ray HTTP upgrade fast-open, VMess
UDP, gRPC/mKCP/H2/Mekya/ECH/shared-session modes remain rejected.
The `AndroidTunPlan` protocol list reports this bounded scope and still returns
`deviceVerified:false`.

The explicit `autoSelect` operation is a one-shot action for a manual `select`
group, not Mihomo's automatic-group scheduler: it concurrently tests up to 128
remote candidates (8 workers, 30-second overall cap, request timeout 1..10000 ms)
through the native proxy adapters and selects the lowest-latency node whose
response matches the caller's required HTTP status range. `DIRECT` and synthetic
routes are never auto-selected. If no node passes, the current selection remains
unchanged. Mobile probes are session-counted, canceled on stop, joined before the
operation returns, and egress-protected by the same Android socket hook. The
operation does not run in a loop or switch nodes after it succeeds.

This one-shot action is separate from native automatic groups. Android supports
Mihomo's own `url-test` (fastest healthy node), `fallback` (first healthy node),
and `load-balance` (native strategy) behavior from the original YAML; automatic
groups are not manually selectable through API `select`. Provider health-check
workers and outbound-group failure workers are lifecycle-tracked in the pinned
Mihomo build and joined during stop before the borrowed TUN is released. Provider
subscription pull intervals stay disabled: only explicit refresh is admitted.
Windows desktop builds use the same patched pinned source and can run those native
groups behind the managed System Proxy owner. This does not enable desktop TUN or
use the legacy iOS FD ABI: `StartIOS` remains `PLATFORM_UNAVAILABLE`. The new
public packet-flow owner is documented below; real iPhone acceptance is separate.


### iOS public packet-flow owner (device acceptance pending)

The legacy borrowed-FD `StartIOS` / `NimboMihomoStartIOSV1` remains unavailable.
New production code uses `StartIOSPacketFlow` / `NimboMihomoStartIOSPacketFlowV1`
with the trusted `ios-packet-flow` owner. Pure JSON Invoke cannot forge that owner.
The same merged Go archive includes gVisor and Mihomo's real sing_tun handler:
source protocol adapters, groups, full routing/sniffer and managed native DNS.
No Xray conversion, host interface discovery, auto-route or second Go runtime.

Start request API1 options: absolute app-private `dataDir`, `networkOwner` equal to
`ios-packet-flow`, explicit `packetIPv6`, optional numeric `packetSystemDNS`,
startup deadline <=25s. No mixed/controller listener. Install a socket protector
while stopped: Swift binds IPv4/IPv6 egress to the underlying physical interface
before connect, without checking public Internet availability.

Binary packet ABI in generated libXray.h:
- `NimboMihomoWriteIOSPacketV1(uint64 generation, void *input, int length)`:
  raw IP without Darwin family prefix, 20..1500 bytes, copied synchronously.
  Return 1 accepted, -1 stale/stopped owner, -2 malformed/invalid argument.
- `NimboMihomoReadIOSPacketV1(uint64 generation, void *output, int capacity, int timeoutMs)`:
  caller-owned output >=1500 and <=65536 bytes; 1..1000ms deadline. Return packet
  byte count, 0 timeout, -1 stale/stop, -2 invalid arguments. No per-packet JSON,
  base64 or native malloc; reads do not take the operation mutex. Stop cancels
  pending reads; generation is rechecked after dequeue.
- Start/control JSON responses must be freed once with `NimboMihomoFreeV1`.

Public `NEPacketTunnelFlow` has one outstanding input read, generation-tagged
late callbacks, <=32 packet output batches and a bounded 128-packet native queue.
The system MTU/address/DNS settings match 1500, 172.19.0.1/30, 172.19.0.2 and
optional fdfe:dcba:9876::1/126 / ::2. DNS must be source-enabled; implicit public
fallback is not installed. `preflightIOSPacketFlow` checks source ownership before
changing NetworkExtension preferences. Process/UID/package/host route filters and
classical remote rule providers are rejected rather than silently ignored.
Domain/IP providers are native. Full YAML/source identity survives import.

`networkChanged` closes old native trackers and DNS connections. Native group
`select` closes established flows using only the changed group, with no TUN
replacement; full-document choices persist only after source-bound readback.
Readiness is local stack attachment, NOT external connectivity. Loopback native
fixtures exercise TCP IPv4+IPv6, UDP DNS, live group switching and stop/wake/stale
packet ownership. Apple archive/Swift/IPA and a real iPhone remain separate gates.

Live delay/nimboDelay now register a generation-bound cancellable probe. `cancel`
interrupts the outbound immediately, including a cancellation that arrived before
probe dispatch. iOS cancel IPC bypasses lifecycleQueue with immutable source and
generation checks. Desktop controller future-drop sends the same authenticated
native cancellation (three-second cleanup bound); it never cancels a new session.
iOS completed results persist under source/node hashes; cancellation preserves
previous measurements. HTTP URL/method/deadline follow saved ping settings; no
TCP/ICMP-to-HTTP substitution for a whole document.

## Privileged desktop TUN native boundary (API1, 2026-10-02)

`preflightDesktopTun` is source-only and has no host side effects. It admits the
full pinned native proxy/group/rule/resolver graph, preserving the original YAML
and SHA-256. Source-defined host listeners, arbitrary FD/interface/routes/table
indices, system/DHCP DNS, custom TUN stack or incomplete DNS hijack are rejected.

`StartDesktopTun(JSON)` is a **trusted local entry**, not a JSON field or public
controller operation. Windows requires an elevated service token; Linux requires
root. Plain `Invoke` cannot obtain desktop-tun ownership. `desktopTun` capability
means the native primitive compiled, not that the GUI/service is installed.
The Rust GUI still keeps TUN/Both/KS closed pending authenticated helper integration.
Never elevate the entire GUI or expose new root commands on the legacy broad pipe.

Start options: networkOwner `desktop-tun`, protected absolute service-private
`dataDir`, `desktopIPv6: true`, authenticated loopback `controllerAddress` and
random bearer secret >=32 bytes. Optional loopback mixed listener allows a future
Both owner, but does not itself apply System Proxy. The service must verify and
install the source-built binary and its matching resources in protected paths,
clear unsafe inherited environment, authorize the installing user's UID/SID and
coordinate ownership with existing Xray TUN before invoking the native entry.

The actual sing-tun **system** stack owns `nimbo-mh0`, MTU 1500, IPv4
172.29.255.1/30 and IPv6 fdfe:dcba:5288::1/126. UDP/TCP DNS port 53 is intercepted
by the source-enabled native resolver, with no public DNS fallback. Native Linux
routing table 52888 and priority range 22888..22903 are reserved and checked for
existing entries before construction; existing interfaces are never adopted or
removed. A root-only process lock / protected Windows global mutex prevents two
native owners. Upstream listener construction/Close owns monitors, routes and
adapter DNS; this is not a fake TUN readiness flag.

The always-installed mobile socket hook suppresses upstream auto-interface binding.
Desktop therefore binds each native socket to the physical interface in that hook:
Linux SO_BINDTODEVICE; Windows IP_UNICAST_IF/IPV6_UNICAST_IF (including both families
for dual-stack UDP). Empty/wildcard listen addresses are valid. A missing/invalid
physical interface after readiness fails closed, not an unbound outbound fallback.
Mobile protector callbacks remain unchanged.

Privileged CLI `serve-tun`: one 4-byte big-endian length-prefixed JSON start frame
<=8 MiB; retain stdin as the process lease. One JSON readiness line on stdout.
EOF/extra lease bytes/SIGINT/SIGTERM cancel an in-flight start by request identity,
then stop/join actual native cleanup. Authenticated generation-bound controller
stop performs the same cleanup. The owner lock is retained during bounded interface
retirement (up to two seconds); failure is TUN_CLEANUP_FAILED, and lease shutdown
exits nonzero. The collision rollback fixture validates cleanup before readiness.
Legacy `inspect` and `serve` remain unchanged.
**Hard crash recovery and external firewall Kill Switch still require the service
journal**: a SIGKILL is not a graceful cleanup or a claim of crash-safe readiness.

`scripts/ci/test-mihomo-desktop-netns.py` checks real TCP IPv4/IPv6, UDP echo,
UDP/TCP DNS, native live group selection, stale-generation rejection and exact
route/rule restoration after EOF/SIGTERM/controller stop. Two synthetic network
namespaces, no Internet or host routing changes; explicit disposable-test opt-in.
These fixtures do not certify real provider transports or Windows adapter cleanup.


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

## Windows protected broker (API1, 3 October 2026)

The Windows x64 desktop host delegates `MihomoPreflight`, `MihomoUp`,
`MihomoStatus`, `MihomoDown` through the separate local-only
`\\.\pipe\Nimbo.Mihomo.Tun.v1` endpoint. Its specific interactive client rights
exclude `FILE_CREATE_PIPE_INSTANCE`. The service identifies the pipe token's
SID/session after each complete frame and always reverts before privileged work;
per-connection owner IDs authorize down. The client checks the kernel server PID
against SCM and the fixed protected installed service image before sending YAML.
No command installs an image, changes paths/environment, or executes client files.

Only a source-frozen artifact with `windowsTunOwnership:
exclusive-adapter-rollback-v1` is eligible. SHA mismatches, unsafe ACL/reparses or
an old proxy-only artifact keep availability false. Native startup is framed only
after Job attachment; EOF/cancel closes the lease. Forced kill is not successful
cleanup. An existing adapter is not adopted, and no foreign routes/DNS/interfaces
are deleted. Live disposable-VM results, hardware roaming/sleep and Windows
hard-crash recovery remain separate readiness evidence, not implied by API1.
