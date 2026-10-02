# Linux helper packaging implementation plan

> **For agentic workers:** Execute inline in the existing authorized checkout; preserve unrelated changes.

**Goal:** Ship and configure the Linux TUN helper in AppImage/DEB/RPM on x64 and ARM64, and preserve tunnels during status requests.

**Architecture:** A Node build wrapper compiles the native helper, verifies its ELF target and stages a SHA-256 manifest before the UI build. The application verifies that helper and runs the existing privileged setup command when the user configures/connects TUN. The service copies itself into its protected stable directory before writing systemd configuration. Only a tunnel-owning IPC client can tear down its tunnel on disconnect.

**Tech Stack:** Rust/Tauri, Node.js, Linux systemd/polkit, Python package inspection, GitHub Actions.

### Package contents
- [x] Create `apps/ui/scripts/build-linux.mjs` with `stageLinuxHelper(source, destination, target)` and a native build wrapper: run `cargo build --locked -p nimbo-svc --release --target <target>`, verify ELF machine 62/183 and executable mode, write manifest with target/version/SHA-256, then launch Tauri.
- [x] Use the wrapper in `apps/ui/package.json`; add staged helper to `tauri.linux.conf.json` resource/files maps. Prepare the same helper before raw custom-installer UI builds.
- [x] Add `helper_build.rs` to reject mismatched/missing release helpers and expose the verified digest to runtime. Test staging with temporary ELF fixtures for both targets, wrong architecture and interrupted/invalid output.

### Runtime setup
- [x] Extend `helper_linux.rs` to resolve verified helpers beside the executable or in Tauri resources; add service setup and startup verification through existing `pkexec --install-service <uid>`.
- [x] Expose Linux helper status/install/uninstall commands and invoke setup from `ensure_tun_dependencies`; use Linux helper readiness when changing a live connection mode.
- [x] Change service setup to copy its executable to `/usr/local/lib/nimbo/nimbo-svc` with root-only write permissions before registering its fixed systemd path. Verify copy/permissions in temporary directories without root or service changes.

### Session lifetime
- [x] Track the active tunnel owner under a mutex. `TunUp` transfers ownership only on success; short status queries and stale sessions cannot tear down the current tunnel. Separate client IPC calls from the owning `TunSession` cleanup.
- [x] Exercise real Unix socket pairs for status/drop and owner disconnect; no native core, DNS, route or systemd mutation in these tests.

### Verification and release
- [ ] Inspect actual AppImage/DEB/RPM helper bytes, ELF architecture, mode and digest in Linux CI; build and inspect both Linux architectures.
- [ ] Run targeted Node/Rust tests, formatting and Clippy; push explicit sources to PR78, run GitHub checks and artifact-only package builds, download and verify results.
- [ ] Record platform readiness and remaining Mihomo TUN work. Preserve the already verified iOS IPA unless iOS sources change.
