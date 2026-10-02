# iOS and desktop completion pass implementation plan

> **For agentic workers:** Execute the scoped tasks inline. Separate verified improvements from the remaining platform-level blockers.

**Goal:** Close actionable desktop full-profile import and iOS profile-management gaps, polish their production screens and publish a truthful readiness inventory.

**Architecture:** URL download runs in the native desktop crate with bounded time/size, UTF-8 validation, conservative redirects and code-only errors; the existing source-preserving importer owns inspection/storage. UI retains the current core/TUN admission rules and uses sequential visible polling. iOS native selection validates a candidate before persisting, uses one awaited operation, and presents the same subscription metadata as Home.

**Tech Stack:** Rust/reqwest/Tauri, React/TypeScript, SwiftUI, Kotlin/Compose regression tests, macOS IPA CI.

### Task 1: desktop complete-profile URL import
- [x] Add `crates/mihomo/src/download.rs` tests for URL schemes/credentials/fragments, size limits including chunked bodies, HTTP errors, malformed UTF-8, preserved BOM/CRLF, redirects and code-only failures. Implement `fetch_source` with 4MiB cap, 20-second total deadline, five same-origin redirects, no HTTPS downgrade, no response-body logging. Cross-origin redirects require the user to supply the final URL explicitly.
- [x] Expose `import_mihomo_profile_url` in `mihomo_runtime.rs` and the Tauri command registry; reuse the existing validated text importer. Add typed API / frontend URL validation / redacted messages. URL is downloaded once, never persisted or claimed as an auto-refresh subscription.
- [x] Add a separate explicit URL form beside file/text import; preserve original bytes and disconnect/core settings. Replace the page's interval with the existing sequential visible polling helper. Polish focus, selected/active cards, wrapping, compact layout and busy announcements with existing restrained Nimbo tokens.

### Task 2: iOS profile screen correctness and polish
- [x] In `ProfilesContainerView.swift` serialize native selection, generate candidate staging bytes and call `vpn.validateCore` before `repository.select`; await staging instead of launching an untracked task. Disable competing import/refresh/removal during selection. The user's choice still prepares the next connection rather than silently reconnecting VPN.
- [x] Show complete metadata announcement in the native profile card and obvious selected-server background/border. Replace the fixed 140pt input editor with a growing multiline input. Preserve the existing shared profile-info sheet and redacted errors.
- [x] Add source/SDK checks to the existing iOS CI; explicitly record that device VPN transitions and native Mihomo TUN are not verified by UI typechecks.

### Task 3: verification and readiness
- [x] Run frontend tests/build, targeted native Rust tests and workspace checks, Android/shared host regression checks for source synchronization, iOS source checks and the latest macOS IPA workflow. Inspect real rendered desktop browser UI when available, not copied mockups. Preserve unrelated untracked files.
- [x] Publish `docs/platform-readiness-2026-10-02.md` with implemented/verified/device-only/blocker distinctions: iOS Mihomo TUN and desktop Mihomo TUN/KS/Linux System Proxy remain unavailable. No all-protocol or RAM/battery percentages without runtime measurements. Keep main unchanged until explicit tested integration.

### Parallel urgent release investigation
The user's Android failure list contains warnings but omits the packageRelease cause. Run the real local assembleRelease, retain its log outside Git and request the exact failure tail. Do not silence warnings or alter signing merely to make the log green.

## Resume — 2026-10-02
- The release investigation was completed separately: local unsigned packaging succeeded; user-key signing was not reproduced or altered.
- Production implementation adds `NimboProfileSelection.swift` and four executable Swift ordering tests, with source contracts in `iosApp/Tests/test_profile_screen_contracts.py` and a macOS IPA preflight. Removal now awaits system clearing before deleting local records; its existing explicit confirmation is preserved.
- Windows Rust workspace: 262 tests passed; Clippy `-- -D warnings` succeeded (AWG runtime artifact absent locally, not claimed as runnable). Frontend: 71 tests pass, TypeScript/Vite build passes. Six iOS source/release suites passed locally. macOS source contracts and all four executable Swift selection tests passed in run 36982167821; the full Release IPA build succeeded, compiling the production SwiftUI/NetworkExtension project. Artifact `Nimbo_v1.3.0-beta.1_ios_resignable` is available until 2026-10-09; it requires re-signing for installation.
- Real desktop UI inspected in IAB at default width and 390px: no horizontal overflow (`scrollWidth == innerWidth == 390`). Fixed Mihomo breadcrumb fallback discovered in the render. Captures are outside Git in primary `.codex-tmp/desktop-mihomo-import*-20261002.png`; preview is not a native connection test.
- iOS sources mirrored to the primary non-Git checkout only after comparison with the previous Git source. Unrelated untracked files are preserved.

- Primary checkout regression suite: 589 Android, 76 Android-host and 118 shared-desktop tests (783 total), zero failures. GitHub PR checks for source commit `9230991` passed on Windows/Linux and Android/shared.

- Full iOS build: https://github.com/BBGGVP5/nimbo/actions/runs/36982167821, source commit `9230991`, successful. No device session, signed distribution build or main merge is implied. All scoped plan tasks are complete; platform blockers remain listed in the readiness inventory.
