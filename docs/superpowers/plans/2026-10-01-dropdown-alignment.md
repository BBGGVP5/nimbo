# Anchored dropdown alignment implementation plan

> **For agentic workers:** Execute this scoped plan inline; preserve unrelated changes.

**Goal:** Align app-routing mode and log-retention dropdowns with their full-width fields.

**Architecture:** Use the existing Material3 ExposedDropdownMenuBox / PrimaryNotEditable anchor pattern already used in NetworkSettingsScreen. Match popup width to the field, preserve radio semantics and selection callbacks, and use existing Nimbo shape/colors. Toolbar overflow menus remain compact and unchanged.

**Tech Stack:** Kotlin/Compose Material3, Python source contracts, Android unit tests.

- [x] Add `scripts/ci/test-dropdown-alignment.py`: verify both production selectors use `ExposedDropdownMenuBox`, `PrimaryNotEditable`, `matchAnchorWidth = true`, no regular DropdownMenu, selectable group / radio semantics and existing on-select callbacks. Run before implementation to observe failure.
- [x] Update `AppProxySettingsScreen.kt::AppRoutingModeSelector`: replace Box / clickable trigger with exposed box / menuAnchor; set same shape, container and border as the field, width matching, minimum 64dp option rows, explicit primary/secondary text colors. Preserve mode IDs1/2 and close on dismissal/selection.
- [x] Update `NimboMiniApp.kt::LogRetentionOptionGrid`: replace Box / Surface onClick with exposed box / Surface anchor; match width and shape, preserve existing duration values and callback; add radio semantics and selected colors.
- [x] Run source contracts and `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest`; require zero XML failures/errors. Add the regression check to Android source CI, sync only task files to the existing Git checkout, review `git diff --check`, commit/push. No phone access or device screenshot verification is claimed.

Verification: two new dropdown contracts and three existing sync/settings contracts pass. Android debug assembled; 665 Android/shared host tests pass with zero failures/errors/skips. Material3 1.5.0-alpha26 local source confirms matchAnchorWidth is applied through exposedDropdownSize. Device screenshot verification was not performed. Main remains unchanged; this patch is published in the existing PR.
