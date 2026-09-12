# Combined LibXray and AmneziaWG for iOS

`bash scripts/ci/prepare-libxray-apple.sh` builds `iosApp/Vendor/LibXray.xcframework`
on macOS with Xcode and Go **1.27.0**. It produces iOS arm64 and a universal
arm64/x86_64 simulator slice, deployment target iOS 16. There is exactly one
`go build -buildmode=c-archive ./cgo_bridge` per architecture. Do not add a
separate AWG archive to the extension.

The script extracts LibXray **v26.7.28**, commit
`80263da83e96b2972455b0a94b13ee1a10e51391`, from its SHA-256-checked source archive
(`1596603887679f7ac6cca99eb27ecb9153fb4ccc7828c1eacd4d07bcb6d94998`). The upstream
`cgo_bridge/main.go`, including `CGoInvoke` and `CGoFree`, is unchanged. These
Go files are copied alongside it. `go.mod`/`go.sum` here lock the combined
dependency graph, with AmneziaWG **v3.1.20260828** and Xray's newer gVisor
`v0.0.0-20260122175437-89a5d21be8f0`. The local `nimbo/awgcore` replace is adjusted
only in the temporary build directory. No `go get`, `@latest`, or upstream build
script that updates dependencies is used. The manifest records hashes of both
bridge and shared runtime sources. External cached modules are verified in a
copy of the module graph without the local `nimbo/awgcore` requirement; this
avoids Go trying to verify a nonexistent public zip for that local module.

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
python3 scripts/ci/check-ios-awg.py --swift  # macOS: execute Swift INI tests too
bash scripts/ci/prepare-libxray-apple.sh     # native bridge tests + all Apple slices
bash scripts/ci/build-unsigned-ios.sh       # full re-signable IPA build
```

Windows validation covered source/build contracts, secret filtering, shell
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
