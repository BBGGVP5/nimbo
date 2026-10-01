# Merged choice card Implementation Plan

> **For agentic workers:** Execute the scoped steps inline and verify before reporting completion.

**Goal:** Make routing and retention selectors expand from their anchor as one animated card, without overlapping adjacent controls.

**Architecture:** A reusable Android Compose Surface owns both the header and AnimatedVisibility options. Options expand/shrink from the top with a short fade; the chevron rotates. Selection values and preference callbacks are unchanged. Back collapses without selection, and Compose's animation duration scale handles disabled system animations.

**Tech Stack:** Kotlin, Compose animation/material3, Android BackHandler, Python source contracts, Compose interaction tests.

### Task 1: Protect behavior
- [x] Update `scripts/ci/test-dropdown-alignment.py` to assert one shared Surface, top-anchored enter/exit, radio semantics, Back dismissal and unchanged routing/retention mappings. Run it before implementation; expect failure.
- [x] Add `app/src/androidTest/java/com/danila/nimbo/ui/components/NimboExpandingChoiceCardTest.kt` exercising closed/open sizes, animation progress, repeated toggle without preference changes, and selection/collapse. Compile; run only if an authorized test device is available.

### Task 2: Implement
- [x] Create `app/src/main/java/com/danila/nimbo/ui/components/NimboExpandingChoiceCard.kt`: typed integer options; one clipped/bordered Surface; header clickable; `animateFloatAsState` chevron; `AnimatedVisibility` with `expandVertically`/`shrinkVertically` and fades; full-width, wrapping, minimum 64dp selectable rows; BackHandler. Closing rows must be disabled.
- [x] Replace AppRoutingModeSelector popup with options values 1/2 and its unchanged onModeChange callback. Replace LogRetentionOptionGrid with labels.zip(values), unchanged onSelect values. Leave unrelated toolbar menus alone.

### Task 3: Verify and publish source
- [x] Run Python contract tests, `:app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest :app:compileDebugAndroidTestKotlin`. Record results, do not claim unrun instrumented/device animation tests.
- [x] Review diff; copy only modified Android sources/tests into the primary non-Git checkout, or from primary to the isolated Git checkout. Commit only task paths, push the existing PR branch; preserve unrelated files.

## Verification result
- Primary checkout: `:app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest :app:compileDebugAndroidTestKotlin` succeeded (3m23s). Android unit tests: 589; shared host tests: 76; zero failures/errors/skips.
- Python dropdown contracts: 3 passed; adjacent sync settings contracts: 3 passed.
- Three Compose interaction tests cover intermediate animation frames/layout reflow, interrupted expansion without persistence changes, selection/collapse and Back dismissal. They compiled, but were **not executed on a device**. No phone/ADB access or device performance claim.
- Changed Android sources/tests match between primary and isolated Git checkouts. Preference values and VPN lifecycle remain unchanged.
