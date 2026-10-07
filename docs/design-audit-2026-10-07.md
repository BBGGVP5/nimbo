# New-design audit РІР‚вЂќ 7 October 2026

## Active surfaces and migration status

| Platform | Active implementation | Checked scope | Status |
|---|---|---|---|
| Desktop | React App routes and native Tauri shell | Home, subscription profiles, settings groups, notifications, tray, routing, modules, applications, connections, statistics/traffic, logs, sync, complete YAML screen | Main and secondary routes use existing Nimbo tokens; responsive automated coverage passes. Advanced YAML prose was still oversized and is now concise, with compatibility details collapsed. |
| Android | `MainScreen` РІвЂ вЂ™ `NimboMiniApp`, dedicated Mihomo profile/proxy screens | Compact ping components, selected-route monitoring, power/cloud transition, shared design/presentation rules | Kotlin compilation, shared tests, Android unit tests and APK build pass. Dedicated utility screens remain platform-specific, not a web-page copy. |
| iOS | `RootView` РІвЂ вЂ™ shared Compose for ordinary tabs; native `ProfilesContainerView` for full Mihomo configs; native settings/import/sync/diagnostics sheets | Shared typography/motion, native full-profile category migration and source/session guards, current source contracts | The native Mihomo list was an actual migration gap. It now uses category tabs and an adaptive card grid. Windows source tests are not a SwiftUI render or device acceptance; macOS build/compiled Foundation tests are required. |

## Fixed findings

- Desktop latency values now use 10px (Mihomo cards) / 11px (generic presentation), with a 12px subscription Ping action; hit targets remain at least 44px.
- Android ping values use 11sp; shared Compose uses 11sp numeric/quiet and 12sp mixed labels. Native iOS uses 11pt relative caption, preserving Dynamic Type.
- Desktop sync used unconditional intervals, including native status requests every 800ms in a hidden window. Visibility-aware sequential polling now pauses hidden work and discards disposed/hidden replies.
- Android Home selected-Mihomo-route polling now runs only while the lifecycle is STARTED, and clears stale live selection on stop. Source/generation checks remain intact.
- iOS native Mihomo categories hide hidden/malformed groups, deduplicate members, preserve declared order in a live snapshot and safely fall back if the current category disappears.
- iOS selection and ping now use the current stored configuration rather than the original card argument. Ping cancellation uses an operation identity and checks source/session before accepting replies or writing cache values.
- iOS refresh also has its own operation identity: a source/status change can replace an in-flight refresh without its late completion clearing a newer loading state or publishing an old session snapshot.
- iOS full-profile layout is now category tabs plus compact cards, with separate selectable/ping targets, full announcement text, empty/error/loading states and a single-column accessibility layout.
- Power/cloud layers stay mounted. Coordinated opacity, scale and rotation transitions reverse smoothly and support interruption; loading never changes native VPN state. System reduced motion and saved animation preferences remain authoritative.
- Android standalone probe DNS no longer tries to resolve bare VPN group routing hints while disconnected; actual DNS transport parameters remain intact. A loopback regression covers the projection without starting a VPN.
- Android instrumentation sources still used obsolete trailing-lambda call sites after the icon action API gained a final Boolean option. Explicit named `onClick` arguments restore test compilation; production interaction contracts are unchanged.
- Windows GUI/service build gates could choose an unrelated stale per-platform source snapshot, unlike the installer. All three now use the Windows staging-owned top-level provenance, and their source pin gates agree with the actual rebuilt helper.

## Local verification results

- UI: 128 Node tests; TypeScript/Vite production build; 249 browser scenarios (107 polish/motion, 80 secondary routes, 18 settings and 44 traffic).
- Kotlin: 617 Android and 132 shared JVM tests, zero failures/skips; Android instrumentation test compilation; three debug APK packages. No instrumentation test was run on a device.
- Native: complete source-built Mihomo Go suite, 40 Rust core tests and opt-in owned-child offline loopback/cancellation checks; five Tauri Mihomo admission/cancellation tests and 16 privileged service ownership tests; AAR compatibility verifier and core/Naive payload-pin tests.
- The actual combined Android AAR has SHA-256 `bb46ee1321735b3fb00b97a3f7311b39c645b9fe073ca0346a1e077aca1efed1`. All four ABIs retain the existing Java/JNI API and 16KiB load-segment alignment. Packaged APK JNI bytes match this AAR after the pinned NDK's normal debug-symbol stripping; Naive payload bytes match exactly.
- Current iOS source-contract suites pass locally; compiled Foundation and complete SwiftUI/Go/Xcode gates are dispatched to macOS CI. Their result must be checked separately before calling the iOS build complete.

## Verification boundaries

Browser checks use controlled data and IPC, not personal subscriptions. New secondary coverage includes 320/800/1440 widths, light/dark, long titles, empty/error states, modal bounds and visibility transitions. Existing polish coverage includes 320/360/800/1440 profile cards, home breakpoints, full descriptions, native readbacks, cancellations, keyboard tabs and reduced motion. Traffic checks cover separate styles/themes and meaningful availability states.

No desktop application was installed or connected to a VPN by this audit. APK packaging/host tests do not establish real-device packet flow, reconnection, DNS/IPv6 leak behavior, camera permission behavior or iOS signing/resigning correctness. Windows cannot run Xcode/Simulator; native iOS visual acceptance and physical Android/iOS acceptance remain explicitly unverified. Native full-profile Mihomo latency on iOS is still connected-session-only; offline full-profile probing has not been invented or substituted with another protocol.

Two old one-off iOS scripts (`test_final_native_routes.py` and `test_scanner_follow_up.py`) refer to ignored September 19 baseline snapshots and old exact layout/function counts. They fail in a clean current checkout for those historical assumptions. The current CI source-contract suites are the relevant regression gates; no historical snapshots or tests were fabricated to make the old scripts green.

## Core review

Reviewed directly against upstream release/tag APIs on 7 October 2026:

- [Mihomo v1.19.32](https://github.com/MetaCubeX/mihomo/releases/tag/v1.19.32): newer than the old v1.19.31. Requires sing-tun v0.4.27, not the old v0.4.24; the initial mismatched staged combination failed to compile and was not promoted. Updated source/ZIP/module checksums, lifecycle/rule/traffic patches, native identities and ROOT mobile dependency graph preserve reviewed Xray/AWG pins. Complete standalone native tests/build and actual owned-child loopback tests pass.
- [NaiveProxy v154.0.8037.49-4](https://github.com/klzgrad/naiveproxy/releases/tag/v154.0.8037.49-4): official archives for Windows/Linux x64 and Android ARM32/ARM64 were hash-verified, extracted narrowly, machine-checked and promoted with new executable checksums/licenses. Windows `--version` prints 154.0.8037.49. ARM64 keeps 16KiB PT_LOAD alignment; ARM32 retains its upstream 4KiB alignment.
- [Xray/LibXray 26.9.30](https://github.com/XTLS/libXray/releases/tag/v26.9.30) and [AmneziaWG v3.1.20260828](https://github.com/amnezia-vpn/amneziawg-go/tags) already match the latest reviewed published release/tag. Floating Alpha/master builds were not substituted.

See the ignored delivery receipts for exact commit, test counts, native artifact identities and CI URLs. A queued/running artifact build is not a completed release or installation.

Local custom-installer compilation was not completed because its pre-existing generated Windows AWG payload is absent in this checkout. The release workflow rebuilds the matching AWG and source-verifies/stages Mihomo before packaging; source pin guards passing is not a full installer acceptance claim.

macOS CI compiled the new category projection/design gate successfully. Its first complete iOS gate then caught an obsolete file-wide assertion against `.disabled(busy || !group.selectable)`: the new sibling selection control correctly needs that guard, while the separate Ping button must not inherit it. The regression now checks both controls separately (including the 44pt ping target), preserving the automatic-group latency invariant. Compiled/native iOS gates are rerun after this correction.
