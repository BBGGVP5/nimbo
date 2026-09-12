AWG 3.1 binaries are staged here by `node apps/ui/scripts/build-awg.mjs`.
Set `NIMBO_AWG_CORE_DIR` to the shared `tools/native/awg-core` directory.
Optional arguments are Rust target triples; no arguments builds all five supported targets.

Windows x64/x86/ARM64 and Linux x64/ARM64 use CGO_ENABLED=0 and the pinned
shared module's CLI. Each platform folder contains nimbo-awg(.exe) and its
version/target/SHA-256 manifest. Tauri release builds invoke this script for
their Rust target. The app embeds the binary digest at compile time and checks
it before launching. Missing source or mismatched manifest fails release builds.

The GUI owns AWG as the logged-in user, even with the Linux TUN helper. Only
Xray and route setup run privileged. AWG config/auth are sent over stdin;
the open pipe is the process lifetime. No AWG config file or child log is written.
TUN requires a pinned IPv4 peer endpoint because the current helper's bypass
and kill-switch rules are IPv4. IPv6 peer endpoints work in system proxy mode.

For an opt-in real CLI regression, set NIMBO_AWG_TEST_BINARY to an absolute
host binary path and run `cargo test -p nimbo-ui awg_runtime -- --include-ignored`.

Windows ARM64 Rust checks require LLVM for ring. If clang is not on PATH, set
CC_aarch64_pc_windows_msvc to the installed LLVM clang.exe before cargo check.
The AWG Go cross-build itself does not need LLVM or a C compiler.

AWG deliberately does not apply full Xray subscription templates: those can
contain balancers or proxy outbounds for a different transport. Routing profiles,
modules, and per-app routes still apply. Mux/XUDP is not applied to AWG's SOCKS
outbound. The installed Xray must support native TUN with loopback exclusions
for autoOutboundsInterface (verified against local Xray 26.7.28).

Linux launches a verified executable copy in the user app data directory, with
mode 0700, so stripped execute bits on packaged resource files do not break AWG.
