# Combined LibXray and AmneziaWG for iOS

`bash scripts/ci/prepare-libxray-apple.sh` builds `iosApp/Vendor/LibXray.xcframework`
on macOS with Xcode and Go **1.27.1**. It produces iOS arm64 and a universal
arm64/x86_64 simulator slice, deployment target iOS 16. There is exactly one
`go build -buildmode=c-archive ./cgo_bridge` per architecture. Do not add a
separate AWG archive to the extension.

The script extracts LibXray **v26.9.9**, commit
`50b95979f5db551bd273165cf469e5daaf791341`, from its SHA-256-checked source archive
(`070a5b573f5a907d31dc23064c89a8cac2cbf9a8baf7df64c42b9cac78b50d4b`). The upstream
`cgo_bridge/main.go`, including `CGoInvoke` and `CGoFree`, is unchanged. These
Go files are copied alongside it. `go.mod`/`go.sum` here lock the combined
dependency graph, with AmneziaWG **v3.1.20260828** and Xray's newer gVisor
`v0.0.0-20260122175437-89a5d21be8f0`. The local `nimbo/awgcore` replace is adjusted
only in the temporary build directory. No `go get`, `@latest`, or upstream build
script that updates dependencies is used. The manifest records hashes of both
bridge and shared runtime sources. External cached modules are verified in a
copy of the module graph without the local `nimbo/awgcore` requirement; this
avoids Go trying to verify a nonexistent public zip for that local module.

The pinned Xray-core module is
`v1.260327.1-0.20260908222543-52a412d9e2f5` (runtime version **26.9.9**).
The build requires Go **1.27.1** exactly and sets `GOTOOLCHAIN=local`;
install that toolchain before running the Apple script. Go 1.27.0 can download
1.27.1 automatically for local module checks with `GOTOOLCHAIN=auto`.

This update requires invoke **API 3**. `LibXrayBridge.swift` sends `runXray`
with `payload.xrayJson`; API 1 and `runXrayFromJson`/`configJSON` are rejected.
`CGoInvoke` and `CGoFree` keep their signatures and response ownership rules.
Ping request migration is handled separately by the native ping implementation.

The added C ABI is:

- `char *NimboAWGStart(char *requestJSON)`: JSON `{config,listen,username,password}`;
  returns `{ok,port,version}` or a fixed error code. A second start is rejected.
- `void NimboAWGStop(void)`: closes the sole runtime; safe when already stopped.
- `char *NimboAWGStats(void)`: returns runtime state, version, and allowlisted
  numeric counters. It never returns configuration, keys, addresses or raw errors.
- Call upstream `CGoFree` for every returned string.

The extension retains the INI in memory to recreate AWG on wake or changes of
physical interface. Credentials are random per tunnel session. Restart reuses
the listener port and credentials, leaving Xray's TUN reader and SOCKS outbound
intact. Sleep closes AWG sockets; wake creates new ones. Start failures, stop,
failed recovery and sustained watchdog failure close the runtimes. An older
cancelled start cannot stop a newer generation.

The Xray outbound connects only to the authenticated `127.0.0.1` SOCKS listener.
The AWG encrypted peer socket uses `conn.NewDefaultBind` in the NetworkExtension
process; it is not dialed by Xray or through AWG's inner netstack.
`NETunnelProviderProtocol.includeAllNetworks = false` preserves the provider's
physical-network socket exemption. System default IPv4/IPv6 routes still capture
app traffic. No network settings or TUN descriptor are replaced during recovery.
Configured DNS and MTU apply to both the system settings and the Xray TUN;
the default AWG DNS is `1.1.1.1`, MTU 1280.

Native import recognizes raw single-peer INI, stores its raw text in the
existing Keychain profile/configuration store, stages it through the existing
provider configuration, and skips share-link conversion. All AWG 3.1 extension
fields remain intact. The shared profile format enum includes `amneziawg` so
Compose can decode the native profile. The Go parser remains authoritative for
complete validation; Swift performs import metadata and basic shape validation.

Validation commands:

```sh
python3 scripts/ci/check-ios-awg.py          # Windows/macOS source + Go stats tests
python3 tools/release/check-libxray.py       # actual AAR/hash/ELF and Android/Apple source pins/API 3
python3 scripts/ci/check-ios-awg.py --swift  # macOS: execute Swift INI tests too
bash scripts/ci/prepare-libxray-apple.sh     # native bridge tests + all Apple slices
bash scripts/ci/build-unsigned-ios.sh       # full re-signable IPA build
```

The Apple build also compiles a temporary host C shared library from the same
merged source and runs `scripts/ci/test-libxray-cabi.py` against it before
building the Apple slices. The test derives the run method/key/API version
from the production Swift source and executes real version, conversion,
start/state/stop requests, rejection of obsolete envelopes, and AWG C ABI
start/stats/duplicate-start/stop checks. All C responses are freed through
`CGoFree`. It uses only local listeners; it does not establish an iOS tunnel.

For the 26.9.9 update, Windows passed all upstream libXray packages and the
shared AWG tests with the merged lock, module-cache verification, AWG vet and
CGO-disabled `ios/arm64` compilation of AWG and Xray. Linux additionally built
and executed the real combined C library and API 3/AWG contract test. Artifacts
and the exact commands are under `artifacts/libxray-26.9.9/`. The Apple C/Swift
link and NetworkExtension/device checks remain pending on macOS/iOS.

Before this update, Windows validation covered source/build contracts, secret filtering, shell
syntax, external module checksum verification, and Go `ios/arm64` package
compilation with CGO disabled for both awgcore and Xray. Xray's Go package tests
also passed using the merged graph. After the shared runtime's IPv4-mapped
endpoint fix, the final Windows test suite passed three consecutive runs against
this merged graph, and `go vet nimbo/awgcore` passed. This includes encrypted TCP
half-close and UDP echo in WG-compatible, AWG obfuscation and AWG 3.1 header
protection/random-trailer modes, plus the hex-key regression. Go package
compilation for `ios/arm64` with CGO disabled also passed again on the final
sources. This verifies
the Go runtime with Xray's newer gVisor, not an iOS device connection. These
checks do not validate the C/Swift link or NetworkExtension behavior. macOS CI runs Swift tests,
compiles the real combined C archive and verifies exported symbols before the
IPA build. Each Apple slice also compiles and links the production Swift AWG
bridge against its generated C header, module map and sole Go archive. The
temporary link-check dylib is not shipped or executed. PacketTunnel and the
link check explicitly link libresolv, Security and CoreFoundation, which are
needed by Go's native networking/certificate support. These new native link
checks have not yet executed on macOS for stable 1.2.0.

The stable release defaults to **1.2.0 / build 170**. The IPA script rejects
prerelease labels and build numbers at or below 160, checks matching versions
in the app and extensions, and records the build number/channel in its manifest.
New installs default to the stable update channel; saved beta preferences
remain valid. Release preflight regressions run as part of `check-ios-awg.py`.
See `iosApp/RELEASE-1.2.0.md` for the source handoff and pending macOS checks.

Device testing must cover encrypted TCP, DNS/UDP, repeated starts,
sleep/wake and Wi-Fi/cellular transitions. A listening SOCKS port or `running`
status only indicates a local runtime; use `last_handshake_time_sec` and traffic
checks to establish peer connectivity.

## App-process per-server diagnostics

The same combined framework now exports two additional functions. They are
for the **Nimbo app process only**, never PacketTunnel. The native Apple guard
rejects a main bundle with the `.appex` extension; a second guard rejects a
process with a running managed Xray core before creating a diagnostic instance.
The caller must serialize all app-process core lifetimes and must never call
managed `runXray` concurrently with diagnostics. The extension keeps its own
framework/runtime in its separate process.

```c
char *NimboDiagnosticRun(char *requestJSON);
char *NimboDiagnosticCancel(char *requestJSON);
```

Release **every non-null result from both functions** with `CGoFree`, once.
Run blocks until the request and all connection/core cleanup finish. Run on a
serial worker queue; Cancel must use a different queue so it can interrupt Run.
There is exactly one native diagnostic lifetime including cleanup; another Run
returns `DIAGNOSTIC_BUSY`, so bulk callers queue requests instead of starting
parallel independent cores.

Run request schema:

```json
{"apiVersion":1,"requestID":"6f8891ba-7731-441d-ac7a-1fd388304c98","serverID":"profile-id","config":"original single-server share link or Xray JSON","format":"share","url":"https://example.com/","method":"GET","timeoutMs":3000}
```

`requestID` is a fresh UUID per attempt. `format` is `share`, `xray`, or `awg`;
AWG currently returns `DIAGNOSTIC_UNSUPPORTED` rather than using the global
AWG tunnel. Native methods are GET or HEAD; the Nimbo diagnostic UI uses GET.
Timeout is 1–60000 milliseconds; configuration is limited to 1 MiB, the C request
to 2 MiB, and response headers to 32 KiB. URL must be absolute HTTP(S), without
userinfo or fragments. Response:

```json
{"ok":true,"requestID":"6f8891ba-7731-441d-ac7a-1fd388304c98","serverID":"profile-id","latency":0}
```

`latency` is integer milliseconds around `http.Client.Do`, from request start
to response headers. Zero is a valid success; setup and cleanup are excluded.
The overall cancellation deadline still starts before parsing/setup. The result
is returned only after cleanup, which can extend wall time beyond the deadline;
the caller must await that completion before starting another core.

Failures have `ok:false`, `latency:-1`, and a fixed `error` code. No raw core,
HTTP, certificate or parser error, configuration, URL, or credentials is included.
Codes are `DIAGNOSTIC_REQUEST`, `DIAGNOSTIC_APP_ONLY`,
`DIAGNOSTIC_MANAGED_ACTIVE`, `DIAGNOSTIC_BUSY`, `DIAGNOSTIC_CANCELLED`,
`DIAGNOSTIC_TIMEOUT`, `DIAGNOSTIC_CONFIG`, `DIAGNOSTIC_UNSUPPORTED`,
`DIAGNOSTIC_UNSUPPORTED_BIND`, `DIAGNOSTIC_UNSAFE_ROUTE`,
`DIAGNOSTIC_AMBIGUOUS_ROUTE`, `DIAGNOSTIC_SETUP`, `DIAGNOSTIC_NETWORK`,
and `DIAGNOSTIC_HTTP_STATUS`.

Cancel accepts `{"apiVersion":1,"requestID":"..."}` and returns
`{"ok":true,"requestID":"...","cancelled":true}` when the matching request
is active. An unknown/completed ID returns `cancelled:false` and cannot cancel
another request. A bounded cache of 256 IDs covers cancel-before-Run races and
immediate reuse; callers must always generate fresh UUIDs.

Only outbounds are projected into a fresh `core.New` configuration. User root
environment, DNS, routing, logging, metrics and listeners/TUN are never applied.
Exactly one supported proxy outbound is required: HTTP, SOCKS, VMess, VLESS,
Trojan or Shadowsocks. Direct/block/DNS helper outbounds are never selected;
direct-only or ambiguous configurations are rejected. Forced outbound context
always targets the selected proxy; there is no direct target fallback or URL
proxy-from-environment behavior. Proxy chains, explicit socket interface
selection, and external certificate/key files are rejected. Nonempty
`sendThrough` is a real native bind address in this pinned release and returns
`DIAGNOSTIC_UNSUPPORTED_BIND`; it is never silently cleared. The Swift adapter
owns any narrow migration of older saved display labels before submission.

HTTP redirects are not followed, only 2xx is successful, target TLS uses default
certificate verification, and response bodies are closed without buffering.
Cancellation closes tracked connections; Run waits for dial completion and
closes its core before releasing the native slot. It never calls managed
`runXray`/`stopXray` or the global AWG start/stop exports.

**Measurement path:** the target request goes through the selected server, but
the app's outer connection to that server follows iOS networking. With an active
VPN it may itself travel through the active VPN, so latency can include a nested
path. No physical-interface bypass or independence from the current VPN is
claimed. When disconnected, the same app-process diagnostic works without any
PacketTunnel IPC. Physical path and device lifecycle behavior require iOS testing.

`scripts/ci/test-libxray-diagnostic.py` executes the new ABI against a real combined
shared library, including separate proxy routes, GET/HEAD, raw-share SOCKS auth,
no direct fallback, root isolation, wrong-ID/pre-start/in-flight cancellation,
deadline cleanup, serialization, unsupported binds/AWG, redirects, status codes,
oversized headers and rejection of an untrusted target TLS certificate. It uses
only loopback fixtures and is invoked by `test-libxray-cabi.py` in the Apple host
contract stage. `openssl` is needed for the ephemeral TLS fixture. Go unit tests
also cover the app-extension guard and concurrent cancellation identity handling.
