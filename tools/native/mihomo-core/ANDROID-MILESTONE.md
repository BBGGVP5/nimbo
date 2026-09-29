# Android borrowed-FD milestone — source handoff, 2026-09-24

**Status: constrained native milestone plus verified Android packaging (2026-09-25); not full Mihomo mobile VPN support.** The combined AAR and debug APK were built and checked as documented below. No device install or runtime VPN test was performed. iOS remains unavailable on this host.

## Plan and reversible baseline

- [x] Trusted StartAndroid / AndroidTunPlan, generic Android Invoke start denied.
- [x] Narrow mobile admission layered over unchanged strict desktop schema.
- [x] Session-owned TCP, bounded endpoint-dependent UDP NAT (256 flows, 32 packets each), DNS and protection; max512 concurrent TCP/DNS tasks.
- [x] Atomic CLOEXEC duplicate, nonblocking raw-IP single-queue TUN validation; one pinned gVisor FD reader; joined teardown, no OS routes/discovery/system-DNS commands.
- [x] Host API compatibility/protection/relay tests and real gVisor IPv4 UDP/TCP DNS packet tests. Android arm64 cross-compilation with installed NDK.
- [x] Exact exports, egress audit, constraints and source-freeze manifest.

Before-edit baseline: `.build/android-tun-baseline-20260923/manifest.json` and saved original files. No Git reset/restore or unrelated platform edits. Runtime/API modifications are additive except intentional GOOS Android start denial. No go.mod/go.sum/pin changes, no build script change.

## Scope / usable source example

Task 1 activation addition (2026-09-24): API1 `Invoke` operation
`preflightAndroid` performs strict inspection and the existing `mobilePolicy`
before replacing a working VPN. It is available during an active session and
does not acquire the native-operation lock, construct native adapters, mutate
native globals, access files, fetch providers or open listeners. Success is
`{valid:true,sourceSHA256,scope:"android-vpn-policy"}` for the exact source bytes.
This is policy admission only, not native runtime readiness or connectivity
proof. All protection/transport restrictions below remain in effect; startup
and provider loading still perform their own checks.

Task 1 before-edit copies/hashes and passing baseline host log are in
`.build/android-preflight-baseline-20260924/`. Tests in `preflight_test.go` cover
source hashes, manual provider declarations without fetching/reading them,
strict/mobile rejection, and active-session/global/selector preservation while
the native-operation lock is held. Final offline host and gVisor logs are
`.build/android-preflight-host.log` and `.build/android-preflight-gvisor.log`.
The updated source freeze is `.build/android-preflight-source-freeze.json` with
snapshot `.build/android-preflight-source-freeze/`. AAR build/ABI verification
and promotion belong to the parent after this freeze; this addition builds no
AAR and performs no phone or external-network verification.

```yaml
mode: global
dns:
  enable: true
  nameserver: [1.1.1.1] # bootstrap UDP/TCP direct; app DNS same IP via GLOBAL TCP
proxies:
  - {name: gateway, type: socks5, server: 192.0.2.10, port: 1080, udp: true}
proxy-groups:
  - name: GLOBAL
    type: select
    proxies: [gateway]
    empty-fallback: REJECT
```

Example TEST-NET gateway is not a working server. All groups must explicitly select `empty-fallback: REJECT`; global mode requires a declared GLOBAL. This prevents upstream implicit GLOBAL/DIRECT and empty COMPATIBLE/direct fallback. Selection follows native groups; active UDP flows keep their original outbound until idle expiry/stop. First declared member is the initial choice unless native default-selected specifies another. Explicit DIRECT remains a deliberate source/user choice, not a privacy guarantee.

Admitted transports (same policies apply to fetched provider rows before atomic publication):
- Direct TCP/UDP; SOCKS5 TCP/UDP (no TLS extension); HTTP CONNECT TCP (no UDP).
- VMess TCP with native Mihomo TLS/stream handling and the pinned VMess ciphers (`auto`, `none`/`zero`, AES-128-CFB/GCM, ChaCha20-Poly1305). Basic WebSocket over TCP (plain or TLS) is now separately admitted only without early data. Admission is limited to one independently dialed TCP stream; UDP, gRPC, mKCP, HTTP/2, Mekya, ECH and other pooled/auxiliary modes remain denied. Egress-protector denial is tested through the actual native VMess adapter before any connection can escape; this is not remote-server interoperability evidence.
- VLESS plain TCP, ordinary TLS and Reality/Vision over TCP with validated X25519 public key/short ID. Denied socket-protector tests cover Reality before any outbound TCP connection. **UDP rejected:** pinned NewVless silently enables XUDP by default; XUDP framing/lifecycle not validated here. This is not a device interoperability result.
- Trojan plain TLS TCP and its connection-owned UDP-over-TCP path.
- SS plain TCP/UDP, classic AEAD aes-128-gcm, aes-256-gcm, chacha20-ietf-poly1305 only.
- Select groups; inline/file/HTTP providers with interval omitted/0 and disabled health checks. Manual refresh/delay supported. Byte-exact source/unknown-field strictness unchanged.

### Explicit blocked reasons, not arbitrary protocol translation

- grpc uses gunClient.Dial without caller context; pooled transport ownership/join needs another audit. xhttp has shared transport pools/auxiliary connections. VMess WebSocket is limited to the individually tested basic mode; WebSocket early data, V2Ray HTTP-upgrade fast-open, other protocol WebSockets, HTTP/2, ECH, encryption extensions, SS plugins/UOT/SS2022 are not included in the tested close/cancel proof. Their rejection is not a claim that protection is inherently impossible.
- Hysteria2/TUIC/AnyTLS remain outside this bounded transport audit; QUIC/shared-session lifetime and every alternate socket factory need validation before admission. VMess is admitted only for the separately audited TCP-only subset above; VMess UDP and non-independent/pooled transports remain denied.
- A bounded Android rule-mode slice is now admitted and executed by Mihomo's native rules against the session-owned metadata: IP-CIDR/SRC-IP-CIDR, DST-PORT/SRC-PORT, NETWORK, RULE-SET and explicit final MATCH. It does not call upstream tunnel.HandleUDPPacket (which uses a process-global queue/context.Background); UDP uses this adapter's session-owned NAT and selected native outbound. Unsupported host/process/geodata/sub-rules, unsupported rule params, missing terminal MATCH and unmatched packets fail closed. Managed application DNS uses the same native rule graph. This is not full Mihomo rule support and is not device-tested.
- FakeIP, system/DHCP DNS, DoH/DoQ and background health/provider updates need separate resolver/bootstrap/session ownership proofs. IPv6 and ICMP are not implemented by this plan.
- iOS: pinned Darwin close runs dscacheutil and has different FD/AF framing ownership. StartIOS hardgate remains unchanged; no Darwin FD borrowing is attempted.

## Egress and lifetime audit (pinned local primary source)

Source roots under `.build/mod/github.com/metacubex/`: mihomo@v1.19.31, sing-tun@v0.4.24, gvisor@v0.0.0-20260826100401-79317d808312.

| Path | Protection / session result |
|---|---|
| Mihomo component/dialer/dialer.go TCP/UDP, UDP ListenPacket | Permanent DefaultSocketHook before bind/connect. Non-nil hook bypasses interface finder, marks and TFO. Nil/false/panic protection fails closed on Android. Cancellation rechecked after callback. |
| direct.go, socks5.go, http.go | Native TCP and UDP socket factories use dialer. SOCKS UDP auxiliary TCP is closed with packet conn; helper goroutine can wind down after Close, but creates no further dials. No arbitrary NetDialer option admitted. |
| vless.go / trojan.go / shadowsocks.go | Plain paths call protected per-connection dialer. TLS handshake uses caller context. SS UDP protected ListenPacket. No plugin/mux/QUIC pool admitted. False-protector tests exercise actual native adapters, not mock acknowledgements. |
| mobile_dns.go bootstrap resolver | Numeric IPv4 upstream only. net.Dialer Control=DefaultSocketHook for UDP and TCP truncation fallback. Own session cancellation, tracked sockets, no detached singleflight, cache refresh, OS resolver or interface lookup. |
| mobile_dns.go application DNS | Intercept only managed 172.19.0.2:53; session-selected native outbound via the explicit mobile rule graph (or GLOBAL in global mode), using TCP to configured numeric upstream and no implicit direct fallback. UDP answers capped512/EDNS1232 with TC; DNS-over-TCP supported; AAAA answered empty. Explicit DIRECT rule/group selection is direct by intent. Arbitrary app DNS/DoH addresses otherwise follow the same session rule graph as ordinary TCP/UDP traffic. |
| providers.go managedVehicle | Protected direct HTTP(S) fetch, Proxy:nil, bounded body/deadlines/redirects; bootstrap names resolved by managed direct resolver. No environment proxy. Manual updates only; fetcher canceled on stop and operation serialized. These subscription requests are NOT tunneled by GLOBAL. |
| manual delay / selectors | Native p.URLTest receives session context; selected adapter uses same protected dialer/resolver. Mobile graph lock serializes selection/provider updates against new dials. |
| sing-tun tun_linux.go / stack_gvisor.go | FileDescriptor skips configure; route/rule/address removal skips borrowed-FD mode. EXP_DisableDNSHijack prevents resolvectl on close. gVisor attaches fdbased endpoint only once; closed callback revokes readiness. Detach/Wait precedes closing owned duplicate. |

**Privacy boundary:** bootstrap proxy/provider DNS and provider HTTP(S) are protected DIRECT egress; protect avoids VPN loops, it does not encrypt or tunnel DNS. Application DNS and traffic follow the selected rule target; explicit DIRECT rules are direct by user/source intent, and unsupported/unmatched routes are dropped rather than implicitly sent DIRECT. Do not label this leak-safe/full VPN. IPv6 blocking, per-app routes, correct MTU/address/DNS settings remain Android owner's responsibility. No traffic counters/fatal callback exported; poll failed status and stop. Bound synchronous protector callback is a platform contract; a stuck callback can stall stop.

Official platform references: [VpnService protection/ownership](https://developer.android.com/reference/android/net/VpnService), [Builder setBlocking](https://developer.android.com/reference/android/net/VpnService.Builder#setBlocking(boolean)), [ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor). Platform API documentation plus pinned source were inspected, not an assumption that mobile FD semantics equal desktop TUN semantics.

## Verification / source freeze

Commands from this directory, offline (`GOPROXY=off GOSUMDB=off GOTOOLCHAIN=local GOWORK=off GOENV=off`), installed Go1.27.1, `.build/mod`, `.build/cache`, `.build/tmp`, `GOFLAGS=-p=2`:

```
go test -mod=readonly -count=1 -timeout=90s ./...
go test -mod=readonly -tags=with_gvisor -count=5 -timeout=90s ./...
go test -mod=readonly -tags=with_gvisor -run TestMobile -count=5 -timeout=90s -v .
# GOOS=android GOARCH=arm64 CGO_ENABLED=1 GOFLAGS="-p=2 -tags=with_gvisor"
# CC=C:/Users/Danila/AppData/Local/Android/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android24-clang.cmd
go build -mod=readonly .
```

Logs: `.build/android-milestone-{host,gvisor,mobile-repeat,arm64}.log`. Full repeat originally exposed reused cancellation test IDs/tombstones (first run passed, four repeats rejected). Fixed test IDs only with atomic per-run suffix; production cancellation deadlines/tombstones unchanged. Original failure retained in `.build/android-milestone-repeat-all-limitation.log`.

Final file hashes/changed path list: `.build/android-milestone-source-freeze.json` / `.build/android-milestone-changed-paths.json`. Snapshot: `.build/android-milestone-source-freeze/`. These exclude generated caches and include source/module/pins/patches/license inputs. Parent root build must recreate the existing local protobuf replacement in its staging directory.

Not verified here: Android kernel TUN ioctl/dup/close behavior on device, actual VpnService protector/binding and leaks, JNI/gomobile merged root dependency graph, other Android ABIs, iOS, encrypted remote-server interoperability, race detector, release readiness. gVisor host tests use real TCP handshake/IP packets and local HTTP CONNECT DNS peer, not a real Android VPN. Parent owns immutable merged LibXray/AAR packaging; no second Go runtime/library.

## Parent integration and packaging verification — 2026-09-25

- Combined LibXray 26.9.9 + Mihomo v1.19.31 AAR was built from pinned sources, checked against the previous AAR, and promoted to `app/libs/libxray.aar` only after verification.
- AAR SHA-256: `c4f503ff6e9c4d39325c8a0a8aedf6312be1f7a584c23770d77137d8d4f09606`; contains `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, one `libgojni.so` per ABI, 16 KiB-compatible ELF load alignment, the prior LibXray JNI API, and the Mihomo bridge exports. The prior AAR (`fc929523baadd14ee1b8bf0cd0a35e89ae8fab8011c74ac5f2eb9078b32f8b16`) is preserved as `artifacts/mihomo-android-native-20260925-130750-f32068cc/previous-libxray.aar` and `app/libs/libxray.pre-promotion.aar`.
- The pinned `gobind` parser and gomobile ABI loop are patched only in isolated build-stage copies: parser concurrency is capped at four, and Android ABI builds are sequential to fit Windows memory/commit limits. The verified application/native source graph and shipped Go runtime are not altered by those build-tool limits.
- `:app:testDebugUnitTest`: 526 tests, 0 failures/errors/skips. `:app:compileDebugAndroidTestKotlin`: passed. The Mihomo pointer instrumentation test was updated to use named parameters after the profile-card API gained a refresh timestamp parameter.
- `:app:assembleDebug`: passed. Signed debug outputs are under `app/build/outputs/apk/debug/`; APK signatures verify with v2. The universal APK embeds `libgojni.so` for all four ABIs and the `MihomoBridge` class. No APK was installed; no live Android VPN, network-leak, or remote-server interoperability test was performed.
- These checks prove packaging/bridge presence, not full product support. Android now has a bounded native rule-routing slice as described above, but still excludes host/process/geodata/sub-rules, IPv6, several transport families, automatic provider/health scheduling and device-level leak/lifecycle proof. iOS `StartIOS` remains unavailable and the selector must stay gated until Darwin Network Extension ownership is implemented and tested on macOS. Desktop remains limited to its separately verified Windows managed System Proxy owner; TUN/Kill Switch and other OS/architecture support are not claimed.

## Bounded native rules and refreshed Android build — 2026-09-25

- Rule-mode TCP/UDP dispatch now evaluates Mihomo's parsed native rule objects against session-owned packet metadata. The admitted classifiers are IP-CIDR/SRC-IP-CIDR, source/destination port, NETWORK, managed IP-CIDR/Classical RULE-SET entries and an explicit final MATCH. It does not use the upstream process-global UDP tunnel queue. Application DNS follows the same selected native route. Unmatched routes and unsupported rule classes fail closed. Domain/process/geodata/sub-rules remain rejected because this packet owner has no hostname/process attribution or asset lifecycle.
- The Android profile and core-selection screens now describe this bounded subset instead of claiming that all rule mode is unavailable. Unsupported settings remain explicitly shown; readiness is not external connectivity.
- Focused Mihomo Go tests: host and `with_gvisor` full suites passed; rule routing/provider/application-DNS tests passed 10 repetitions. Race detector remains unavailable on this Windows host because no GCC/cgo compiler is installed.
- Combined LibXray 26.9.9 + Mihomo v1.19.31 AAR rebuilt from the current root Go graph and verified before promotion. SHA-256: `b8690eeb487cc09e56ad71e15380cca74f327a969bcc610da69b7e08a17f397b`; four Android ABIs and existing Xray/Mihomo bridge contracts preserved. Prior AAR retained as `app/libs/libxray.pre-rule-routing.aar` (baseline hash `c4f503ff6e9c4d39325c8a0a8aedf6312be1f7a584c23770d77137d8d4f09606`). Build and promotion records are in `artifacts/mihomo-android-native-20260925-141300-b2e7ed9f/`.
- `:app:testDebugUnitTest`: 526 passed, 0 failures/errors/skips; `:app:compileDebugAndroidTestKotlin` and `:app:assembleDebug` passed. Universal and arm64-v8a/armeabi-v7a split APK signatures verify with APK v2. No APK was installed or run on a device. Universal APK SHA-256: `be277efc678a47b8591a6a5286db038f1cfa4fc73a290889d91df378a961e208`.
- This refresh validates Android packaging only. It does not lift iOS `StartIOS` or desktop TUN/Kill Switch gates and does not constitute full Mihomo support or device-level leak/interoperability acceptance.

## VMess TCP admission, Mihomo subscription refresh, and APK — 2026-09-25

- Added only a narrow Mihomo VMess/TCP Android transport slice: validated UUID/alterId/cipher, TCP network, allowlisted TLS options, with UDP, gRPC, mKCP, WebSocket, HTTP/2, Mekya and ECH rejected. Egress-protector denial, actual native-adapter TCP relay/close/join, and Android capability reporting are covered by repeated tests. This proves the audited local path, not compatibility with a public VMess endpoint or Android-device VPN behavior.
- Ordinary subscription refresh and Refresh All now fetch and atomically replace Mihomo YAML subscriptions, retain the prior working YAML when fetch/admission fails, report local YAML as non-refreshable, and do not count the marker server as a pingable Mihomo node.
- Go verification passed with the pinned Go 1.27.1 toolchain: host suite, `with_gvisor` suite repeated five times, and the VMess protected-relay/capability tests repeated ten times. The verified merged LibXray 26.9.9 + Mihomo v1.19.31 AAR has SHA-256 `9f66738259c5ebbde78929024053bcbe99e9b9a1ed476a932c5f93393e2e8316`, all four Android ABIs, and preserved JNI/bridge contracts. Previous AAR SHA-256 `b8690eeb487cc09e56ad71e15380cca74f327a969bcc610da69b7e08a17f397b` is retained as `artifacts/mihomo-android-native-20260925-145606-337694af/libxray.before-vmess.aar`. Build and promotion manifests/logs are in the same timestamped directory.
- `:app:testDebugUnitTest`: 526 passed, 0 failures/errors; `:app:compileDebugAndroidTestKotlin` and `:app:assembleDebug` passed. The arm64, armeabi-v7a and universal debug APK signatures verify. Universal APK SHA-256 `bd953281893e51270cdf5b002671689f81ca3b9cec015a922b8013e9c5412c8c`; arm64 APK SHA-256 `aca4d986e66740e92e779bdddc7f6f06cdfff1cd0052d8437ff9008280bb8f99`.
- No APK was installed. This remains **partial Mihomo support**: Android still lacks auto/fallback/load-balance groups and owned health scheduling, several protocols/transports, IPv6 and full DNS/rules/provider behavior; device-level tunnel/leak proof is outstanding. iOS `StartIOS` remains gated and unimplemented for Network Extension, and desktop TUN/Kill Switch and other OS implementations are not complete. Do not present this APK as full cross-platform integration.

## Bounded VMess WebSocket/TLS support — 2026-09-25

- Android admission now includes only basic VMess WebSocket over TCP, both plaintext and TLS. `ws-opts` is restricted to a relative path and bounded ordinary headers; handshake-control headers are owned by Mihomo. Early-data config/query, V2Ray HTTP-upgrade fast-open, fingerprint/ECH, client certs, UDP, and every other unreviewed transport option remain rejected.
- Added native adapter relay/close/join tests for real WebSocket upgrade over protected TCP and over a local self-signed TLS server, plus strict subscription-provider acceptance and capability-plan reporting. These are local tests, not public endpoint compatibility, device TUN/DNS leak tests, or a complete transport audit.
- Updated the Android Mihomo capability text to say VMess TCP/WS/TLS without early data; older milestone entries above are historical snapshots and describe the scope at the time they were built.
- Pinned Go 1.27.1 verification passed: complete host suite, complete `with_gvisor` suite repeated five times, and focused VMess TCP/WS/TLS adapter, provider and capability tests repeated ten times.
- Rebuilt and verified the combined LibXray 26.9.9 + Mihomo v1.19.31 AAR from the merged source graph. SHA-256: `c267458344661b6bbf924d420ce424f4e17642ae12b6a186158441496ed7b94e`; all four Android ABIs and existing JNI/bridge APIs preserved. Prior AAR SHA `9f66738259c5ebbde78929024053bcbe99e9b9a1ed476a932c5f93393e2e8316` is kept at `artifacts/mihomo-android-native-20260925-155912-07440052/libxray.before-vmess-ws.aar`; build and promotion records are in that directory.
- `:app:testDebugUnitTest`: 526 passed, zero failures/errors/skips. `:app:compileDebugAndroidTestKotlin` and `:app:assembleDebug` passed. arm64, armeabi-v7a and universal debug APK v2 signatures verify. SHA-256: arm64 `681f3d61ec6491678d4c211c0f145d9367e5fb694f99f7303496a8392d27bdc6`, armeabi-v7a `cc2a6268f277e7b95a9dac274cdb89bccae5821e6ec9874e58ac0370631bf032`, universal `9c83e83f6f0d8e23ca60f533a8cfda5b4ec753f32ef7d5ecd98ac1975e5080c1`.
- APKs were built only, not installed or run. No Android device, public VMess server, or live VPN/DNS/leak test was used. Support remains partial; the remaining work listed above is still required before describing Mihomo as fully integrated.
- Support remains partial: automatic proxy groups/health lifecycle, remaining protocols and transports, full DNS/rules/IPv6, Android device validation, iOS Network Extension and desktop TUN remain open.

## Explicit one-shot “pick a reachable node” action — 2026-09-25

- Added a manual action to active Android Mihomo selector groups. It probes at most 128 remote candidates (8 workers, 30 s overall cap) through their native adapters, requires the requested HTTP health status, picks the lowest measured delay, and excludes DIRECT/synthetic routes. It does not keep polling or switch after success; a failed run leaves the prior selection unchanged. The screen now reports the chosen node and measured probe delay.
- Mobile probes are registered as session work. Stop cancels them and waits for workers; the packet graph lock is released while probing so existing TUN traffic can continue. A protected-proxy test verifies healthy-vs-slow selection, DIRECT exclusion, socket-protector use, no loop on snapshot, and preservation of the current route when all probes fail.
- Pinned Go 1.27.1 host suite passed, the `with_gvisor` suite passed five repetitions, and focused picker/WebSocket/egress tests passed ten repetitions. The merged AAR SHA-256 is `984ed629630c1a123930bca1e55934f1fded414a2b26ffdd7cdf603326168d0e`; all four ABIs and JNI APIs verified. Previous AAR SHA `c267458344661b6bbf924d420ce424f4e17642ae12b6a186158441496ed7b94e` is preserved as `artifacts/mihomo-android-native-20260925-163140-1ac02fc8/libxray.before-one-shot-auto-pick.aar`.
- Android unit tests: 526 passed, zero failures/errors/skips. Android-test Kotlin compilation and debug APK assembly passed; all three output signatures verify with APK v2. APK SHA-256: arm64 `dac09f817143453e3f9b64c7907f3931ec85a395397c2addd485ddc4212f3e71`, armeabi-v7a `801f5450b4826adcfe878346ef822874d7c08b0847ada1240d1494ef2daf9f47`, universal `c3fc47d088e8ae3cba02dc270e3aa52ece0d95f05a0b9745f30a1ab0497534d8`.
- APKs were built only, not installed. This is a user-triggered one-shot chooser for select groups—not support for Mihomo `url-test`/`fallback`/`load-balance` groups, provider health scheduling, or automatic failover. Android device validation, remaining transports, full DNS/rules/IPv6, iOS Network Extension, and desktop TUN remain unfinished.

## Native automatic groups and owned health-check lifecycle — 2026-09-25

- Android policy now admits native Mihomo `url-test`, `fallback`, and `load-balance` groups from the exact YAML. Health-check URL, interval, timeout, lazy flag, nested group routes, and dynamic provider rows are bounded/validated; routes containing `DIRECT` are rejected so an unavailable proxy cannot silently escape the tunnel. Automatic groups are read-only in the app; manual `select` remains limited to native selector groups. The one-shot “Подобрать” action remains distinct and does not create a polling loop.
- The pinned Mihomo v1.19.31 build uses `mihomo-session-lifecycle.patch` in isolated, checksum-verified source copies. Provider health-check loops and provider-update-triggered checks are tracked/canceled/joined, and outbound-group dial-failure tasks are joined before session-owned TUN cleanup. Provider subscription `interval` remains disabled; refresh is manual. The same patched dependency is staged by Android, Apple bridge, and Windows desktop build scripts rather than editing the read-only Go module cache.
- The local integration test constructs native `url-test`, `fallback`, and `load-balance` groups over inline providers, verifies real HTTP health probes traverse protected sockets, confirms all three group types are present in snapshots, and confirms automatic groups cannot be manually selected. The separate one-shot test covers fastest-node selection. Verification must be repeated against the final merged AAR and app build before distribution.
- iOS is still **not a functioning Mihomo VPN**. `StartIOS` returns `PLATFORM_UNAVAILABLE`; this host has no Mac/Xcode or iPhone test path. GitHub's macOS workflow can compile the Go archive/Swift target and run CI contracts, but an IPA build does not prove Packet Tunnel traffic, DNS/routing, cancellation, or leak behavior on a real iPhone. Do not enable the iOS core selector until a Network Extension packet-flow owner and device acceptance exist.
- Windows desktop remains managed System Proxy only. TUN, Kill Switch, platform route/DNS ownership, and Linux/macOS desktop packages are not enabled by the lifecycle patch. No cross-platform “full Mihomo support” claim is made.
