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

* `inspect`: exact UTF-8 source in `yaml`; no network, native construction or disk IO.
  Returns `{originalYAML,sourceSHA256,documentKind,rootKeys,declaredGraph:{proxies:[],groups:[],providers:{}},
  strictIssues:[{code,message,path}]}`. Graph entries retain declared YAML
  mappings and unknown fields, except `smart` groups are projected to stable
  `url-test` (`interval:600`, `lazy:true`, `tolerance:100`) with ML/telemetry
  options removed. Dynamic flags/use are not materialized.
  `documentKind` is `mihomo` only when a parsed root mapping contains a recognized
  Mihomo key; `rootKeys` are sorted keys from that parsed mapping. Byte-exact source
  is authoritative (comments, anchors, ordering retained there).
  Unknown/unsupported content remains inspectable, but cannot validate/start.
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
  startupTimeoutMs?:20000}`. Deadline range 1..25000 ms.
  Returns status only once listeners and providers are ready; rollback on error.
* `status`: `{state,mixedAddress,controllerAddress,networkOwner,sourceSHA256,
  coreVersion:"v1.19.31",coreCommit,apiVersion:1}`. No secret/source disclosed.
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
make iOS available: iOS `StartIOS` remains `PLATFORM_UNAVAILABLE` until a real
Network Extension packet-flow owner is implemented and device-verified.
