# Cross-platform design and interaction audit implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Reduce latency typography across active desktop/Android/iOS surfaces, fix concrete audit findings, and document verified versus unverified design migration coverage.

**Architecture:** Keep existing Nimbo tokens and native platform ownership. Desktop browser fixtures cover the real production routes using isolated data/IPC. Shared Compose latency presentation keeps real measurements and accessible targets while shrinking only text. Android native monitoring is lifecycle-bound. iOS native Mihomo profiles use category navigation and compact proxy cards with source/session-scoped actions, hidden-group filtering and no stale-reply publication.

**Tech Stack:** React/TypeScript/Vite/Playwright, Kotlin Compose/Gradle tests, SwiftUI/Foundation contracts and macOS CI.

---

### Task 1: Audit baseline and bounded fixes
- [x] Inventory active routes: desktop App routes, Android MainScreen/NimboMiniApp and specialized screens, iOS RootView shared Compose versus native full-profile sheets. Do not mistake unused legacy fallback views for live production UI.
- [x] Run existing source contracts, shared desktop tests and Android compilation; keep all personal data and native networking out of test fixtures. Audit main/secondary screens in dark/light and 320/360/800/1440 widths with keyboard, long text, empty/loading/error and reduced motion.
- [x] Lower ping value typography without changing latency, timeout settings or 44/48px targets: desktop card results 10px and generic latency 11px; shared Compose quiet/numeric labels 11sp, mixed display 12sp; Android dedicated ping component 11/12sp; native iOS 11pt relative caption.

### Task 2: Desktop regressions
**Files:** Modify `apps/ui/src/components/{LatencyDisplay.tsx,core-subscription-groups.css}`, relevant verified controls; extend `apps/ui/tests/browser` fixtures/runners.
- [x] Add assertions for compact ping results and production secondary routes (routing/modules/apps/activity/logs/sync/Mihomo) using strict isolated API readbacks. Verify no overflow, inaccessible icon actions or console failures.
- [x] Fix reproducible defects rather than suppressing overflow/errors globally; rerun complete UI unit/build/browser suites.

### Task 3: Android/shared Compose regressions
**Files:** Modify focused ping presentation/components and native selected-server monitoring in `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`; extend source/JVM regressions.
- [x] Put Home Mihomo polling inside `repeatOnLifecycle(Lifecycle.State.STARTED)`; native probes remain on Dispatchers.IO and session/source guards stay intact.
- [x] Test compact font contracts and polling lifecycle ownership. Compile app debug Kotlin and shared desktop tests without installing or starting VPN. Report SDK/native-artifact limitations explicitly if compilation is unavailable.

### Task 4: iOS Mihomo migration and identity fixes
**Files:** Modify `iosApp/Nimbo/NimboMihomoProfileCard.swift`, `NimboMihomoControl.swift`; create a Foundation-only group projection/presentation helper, test runner and Swift tests under `iosApp/Tests`; wire the existing iOS workflow to those contracts.
- [x] Filter hidden/malformed/deduplicated categories; preserve declared category order for live data and fall back safely when selected category disappears. Cover the pure projection with compiled Foundation tests on macOS and source contracts locally.
- [x] Replace the unbounded vertical group list with horizontally navigable category tabs and an adaptive card grid. Keep independent selection/ping controls, dynamic type, full source announcement, loading/empty/error and reduced-motion support. No duplicate Mihomo/connect or inspector text.
- [x] Use the current stored configuration for refresh, select and ping. Bind tasks/results to source digest and real connected session; invalidate on source/status/disappearance, check native readback and discard stale completions. Never invent offline native measurements or bypass connection admission.

### Task 5: Coverage report and delivery
- [x] Save a repository audit report with precise active-surface coverage, fixes, passing checks and remaining limitations. Windows cannot render SwiftUI/iOS Simulator; do not label source assertions as device verification or claim every production flow is flawless.
- [x] Mirror only owned source/artifact paths with normalized preimages/SHA checks (preserve existing document prefixes), commit/push existing branch, dispatch verified Windows artifacts, Android/shared checks and unsigned iOS build when changed. No installer run, publication, real VPN connection or icon-cache manipulation.

### User-added task: Connection icon transformation
**Files:** `apps/ui/src/components/ConnectionStateIcon.tsx` and its CSS; `shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboConnectionMotion.kt`, `NimboUniversalComponents.kt`; Android `UniversalPrimitives.kt`.
- [x] Keep power/cloud layers alive and animate their opacity/scale/rotation together, with busy state independent and cloud only after native-connected acknowledgement. Use explicit 240/440ms desktop transitions and a shared 420ms Compose progress state; reverse/interruption reuses the same state, not a detached completion callback.
- [x] Respect system reduced-motion plus saved animation preference. Do not disable Cancel/Disconnect during animation, animate labels misleadingly, or change network state from a visual timer.
- [x] Browser test real production icons through disconnected в†’ loading в†’ connected в†’ disconnected, asserts no node replacement, endpoint visibility, no extra connection side effects and no transitions with reduced motion. Shared tests keep real state/icon admission unchanged.

### User-added task: Reviewed core upgrades
- [x] Query upstream GitHub releases/tags, compare installed pins and record exact UTC release evidence. Do not replace reviewed stable runtime pins with floating Alpha/head builds.
- [x] Mihomo v1.19.32: checksum-verify Go module/source ZIP, stage all Nimbo lifecycle/ownership patches against new source, resolve dependency graph in an isolated modfile and run complete native tests before any production promotion. Keep mobile AAR/framework identities in sync; do not claim a new mobile core while shipping the old native library.
- [x] NaiveProxy v154.0.8037.49-4: verify each official Windows/Linux/Android archive against GitHub SHA256; extract only the required regular binary entry, verify PE/ELF machine and update runtime checksum pins/metadata with the actual payload. Check harmless Windows --version, Android package build, native packaging contracts and licenses.
- [x] Xray/LibXray 26.9.30 and AmneziaWG v3.1.20260828 are current upstream reviewed releases/tags; no downgrade or unnecessary rebuild solely to change dates.

## Verification status
Implementation and local checks completed. macOS compiled Foundation/SwiftUI and Windows artifact CI must be observed after dispatch; physical-device acceptance remains unverified. Local results and explicit limits are recorded in docs/design-audit-2026-10-07.md.
