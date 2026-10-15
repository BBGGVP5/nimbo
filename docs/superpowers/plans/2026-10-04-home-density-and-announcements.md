# Home Density and Subscription Announcements Implementation Plan

> **For agentic workers:** Execute this focused refinement inline, task-by-task; retain unrelated workspace changes.

**Goal:** Enlarge the standalone round connection action, reduce the equal desktop home cards, and preserve subscription announcement formatting even before expansion without an extra description heading.

**Architecture:** Scope density rules to the existing home layout, leaving profile management and the compact action intact. Render provider text verbatim as escaped React text; visually clamp the collapsed paragraph to three lines and offer expansion only when its rendered content exceeds those lines. Keep desktop card widths/heights equal without a fixed content height.

**Tech Stack:** React 19, TypeScript, CSS, Node test runner, Playwright/Vite fixtures.

---

### Task 1: Regression tests

**Files:**
- Create: `apps/ui/tests/providerAnnouncement.test.mjs`
- Modify: `apps/ui/tests/browser/cross-platform-polish.tsx`
- Modify: `apps/ui/tests/browser/cross-platform-polish.mjs`

- [x] Add SSR assertions for original whitespace/newlines, escaped HTML-like text, absent heading, and empty descriptions:

```js
assert(html.includes('  Provider\n\nsecond line  '));
assert(!html.includes('<h3>'));
assert(!html.includes('<script>'));
```

- [x] Give the browser fixture a multi-line provider announcement including blank lines and literal HTML; expose its exact source text for comparison. Assert paragraph `textContent` equals it both before and after disclosure, `white-space: pre-wrap`, collapsed height at most three lines, and unchanged profile disclosure state.
- [x] Require a round diameter of 176px on desktop / 144px on small screens, a compact action no taller than 52px, and ordinary desktop home cards no taller than 300px. Retain existing equal-card, overflow, touch-target, connection callback, core selection, and navigation checks.
- [x] Run `node --test tests/providerAnnouncement.test.mjs` and `npm run test:polish` from `apps/ui`; expect failures against the current normalized announcement and 128px circle.

### Task 2: Focused implementation

**Files:**
- Modify: `apps/ui/src/pages/profiles/ProviderAnnouncement.tsx`
- Modify: `apps/ui/src/pages/home/home-profile-polish.css`

- [x] Replace normalization/truncation with `<p>{description}</p>`. Retain `useId` for disclosure association; measure paragraph overflow using `scrollHeight > parseFloat(getComputedStyle(p).lineHeight) * 3 + 1` in an effect/ResizeObserver, and reset expansion when the source description changes. Remove the generated `Описание` heading; keep user-authored content unchanged.
- [x] Apply visual collapse only with:

```css
.nimbo-provider-announcement.is-collapsed p {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}
```

- [x] Increase the round action to 176px and icon to 56px; use 144px/48px on small screens. Preserve the 48px compact pill. Center a maximum-1000px home card grid, reduce connection-row padding and title spacing, and keep profile-header controls at least 44px high. Never impose a fixed announcement/card height or crop expanded content.

### Task 3: Verify and deliver

- [x] Run `npm test`, `npm run build`, `npm run test:polish`, and `npm run test:settings`; reject fixture IPC outside the isolated mocks.
- [x] Inspect saved desktop/mobile screenshots in dark/light themes, including collapsed/expanded multiline announcements. Fix actual computed geometry rather than lower-priority CSS declarations.
- [ ] Compare each owned primary-workspace preimage with `git show HEAD:path`, recheck hashes before mirroring, copy only owned files, and record an ignored receipt.
- [ ] Stage only the five implementation/test files and this plan; commit and push `codex/ondemand-memory-20260930`. Dispatch `release.yml` with `tag=v1.3.0-beta.1`, `publish=false`, `platform=windows`; report the actual build status without claiming a published update or installed binary.

## Latest clarification

The user confirms the fully expanded provider description is formatted correctly. Preserve the disclosure for long rendered text, but keep original line breaks in the collapsed preview too; remove only the generated description heading, not a word authored by the provider.

## Verification results

- Red: the initial source-whitespace regression and the minimum-circle browser assertion both failed against the previous implementation.
- Green: 118 Node tests, 63 shell/home/profile/dropdown/core/tray browser cases, 18 settings browser cases, and 44 traffic dashboard browser cases passed (125 browser scenarios total). TypeScript/Vite production build passed.
- Visual inspection: desktop round/compact actions in dark/light themes, narrow-screen navigation, and exact five-line provider content collapsed/expanded on Home and Profiles. Screenshots are in the ignored primary-workspace `.codex-tmp/home-density-20261004` folder.
- Delivery receipts record the mirror hashes, pushed source commit, and artifact-only Windows build dispatch after this plan snapshot is committed.
