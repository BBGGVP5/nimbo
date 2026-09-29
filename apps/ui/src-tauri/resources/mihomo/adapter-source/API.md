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
  Returns `{originalYAML,sourceSHA256,declaredGraph:{proxies:[],groups:[],providers:{}},
  strictIssues:[{code,message,path}]}`. Graph entries are **complete declared YAML
  mappings**, including unknown fields; dynamic flags/use are not materialized.
  Byte-exact source is authoritative (comments, anchors, ordering retained there).
  Unknown/unsupported content remains inspectable, but cannot validate/start.
* `validate`: strict policy then actual pinned Mihomo config parser, only stopped.
  No provider fetch, listener, TUN or rule asset downloads. Native parse errors fail.
* `start`: `yaml` and `options:{dataDir:absoluteAppOwnedPath,
  networkOwner:"desktop-proxy",mixedAddress:"127.0.0.1:0",
  controllerAddress:"127.0.0.1:0",secret:randomBearerAtLeast32Chars,
  startupTimeoutMs?:20000}`. Deadline range 1..25000 ms.
  Returns status only once listeners and providers are ready; rollback on error.
* `status`: `{state,mixedAddress,controllerAddress,networkOwner,sourceSHA256,
  coreVersion:"v1.19.31",coreCommit,apiVersion:1}`. No secret/source disclosed.
  States: stopped, starting, running, stopping.
* `snapshot`: `{groups:{name:upstreamProxyDTO},providers:{name:{name,vehicleType,
  version,proxies:upstreamProxyDTO[]}}}`. Snapshot members may change dynamically.
* `select`: `group,name`; actual Set and snapshot readback. Missing selection fails.
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
for the managed lifecycle; integrating it with mobile FD handoff remains a gate.
Do not infer readiness from spawn or an open port. Stop before switching cores.
No automatic restart/retry of failed starts with unknown caller intent.

This revision runs **managed TCP HTTP/SOCKS mixed proxy**, not platform VPN.
Platform networkOwner values/FD injection return PLATFORM_UNAVAILABLE until native
ownership integration is separately implemented. Source config host listener, TUN,
host routing and unsafe/controller fields are rejected, never silently overridden.
Mihomo application rules, logical/sub-rules, strict YAML/text rule providers and
internal DNS resolution are separate from host ownership and are implemented.
Geodata/process rules, binary MRS and fake-IP/mobile DNS planning remain explicit gates.
Managed defaults and exact supported schema are documented in README.md and
enforced in policy.go. Inspect is lossless, not a promise every config can run.
