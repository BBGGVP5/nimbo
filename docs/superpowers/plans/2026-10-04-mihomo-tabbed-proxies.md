# Mihomo Tabbed Proxies Implementation Plan

> **For agentic workers:** Execute task-by-task inline in the current checkout; no new thread or worktree is needed.

**Goal:** Replace the subscription's accordion categories with FlClash-like category tabs and proxy cards, removing the inspector notes, profile-check button and repeated explanations.

**Architecture:** Keep native metadata loading, digest/session guards and confirmed selection in CoreSubscriptionControl. A focused MihomoProxyGroups presentation component owns a horizontally scrolling accessible tab bar, the existing NimboSelect category menu and one responsive proxy-card panel. Metadata comes only from the sanitized current inspection or matching runtime snapshot; offline navigation is allowed, native selection is not fabricated.

**Tech Stack:** React, TypeScript, Nimbo CSS tokens, existing NimboSelect, Node tests and isolated-IPC Playwright fixtures.

---

### Task 1: Regression and display metadata

**Files:**
- Modify: `apps/ui/src/components/core-subscription-groups.ts`
- Modify: `apps/ui/tests/core-subscription-groups.test.mjs`
- Modify: `apps/ui/tests/browser/cross-platform-polish.mjs`
- Modify: `apps/ui/tests/browser/cross-platform-polish.tsx`
- Create: `apps/ui/tests/fixtures/mihomo-tabbed-inspection.json`

- [x] Add metadata tests before implementation. Require explicit proxy types and nested-group current names; reject stale inspection types and unrelated snapshots. Builtins DIRECT/REJECT are recognized by exact name, unknown entries remain unknown.

```js
assert.deepEqual(subscriptionMemberDetails(profile, null, false, 'FI'), {type:'vless'});
assert.deepEqual(subscriptionMemberDetails({...profile, source_digest:'changed'}, null, false, 'FI'), {});
assert.deepEqual(subscriptionMemberDetails(profile, {groups:{Auto:{type:'URLTest',now:'FI'}},providers:{},ruleProviders:{}}, true, 'Auto'), {type:'URLTest',now:'FI'});
```

- [x] Run `node --test tests/core-subscription-groups.test.mjs` from `apps/ui`; observe the missing helper failure, then implement the allowlisted metadata lookup. No credentials, source parsing or invented latency.
- [x] Replace browser accordion assertions with tabs/panel assertions: exactly one category panel, no inspector/disclosure/check-profile text, navigation without connect/select IPC, correct active category after subscription refresh, two equal mobile columns, compact aligned cards and no page overflow.
- [x] Run `npm run test:polish`; observe failure against the existing accordion before changing presentation.

### Task 2: Tabs and cards

**Files:**
- Create: `apps/ui/src/components/MihomoProxyGroups.tsx`
- Modify: `apps/ui/src/components/CoreSubscriptionControl.tsx`
- Modify: `apps/ui/src/components/core-subscription-groups.css`

- [x] Create the tab component using scoped `useId` IDs, `role="tablist"`, roving tabIndex and ArrowLeft/Right/Home/End activation. Use the existing NimboSelect for quick category navigation; only horizontal tab scrolling may occur when switching categories.

```tsx
const active = groups.find(([name]) => choice?.profileId === profileId && choice?.name === name) ?? groups[0];
// Render all category tabs, but only active[1].all in the associated tabpanel.
```

- [x] Render whole-button proxy cards with original member names, truthful protocol/group metadata and current nested selection when available. Mark the entire card only after matching-session runtime readback; retain busy/native/Selector guards. Use `grid-template-columns:repeat(2,minmax(0,1fr))` on mobile and auto-fill 176px columns on desktop; all cards share a compact minimum height.
- [x] Remove inspector notes, the manual Inspect profile button, category type captions and always-visible connection explanations from CoreSubscriptionControl. Retain background metadata inspection (needed to populate categories), connection/disconnection action, concise loading/empty states and actionable safe operation errors.
- [x] Re-run browser tests. Cover real warning-bearing graph, keyboard/category-menu switching, actual empty graph, automatic groups read-only, successful native selection/readback and native selection/admission failure. Inspection failure recovers through the existing subscription Refresh action, not a new profile-check action.

### Task 3: Verification and delivery

- [x] Run `npm test`, `npm run build`, `npm run test:polish`, `npm run test:settings` and `npm run test:traffic:browser`; inspect dark/light screenshots at 360px and 1440px with realistic category names and proxy cards.
- [ ] Compare primary preimages to normalized committed HEAD and check hashes before copying only owned paths. Commit/push those paths, verify the remote SHA and dispatch `release.yml` for Windows with `publish=false`; record actual build state in ignored delivery receipts.

**Scope boundaries:** No host VPN/TUN/system-proxy operation, native validation weakening, hidden errors, YAML rewriting, legacy Xray-node substitution, provider-member fabrication, raw source/credentials in the UI, or fake ping values. Provider announcement remains complete exactly as requested previously.

## Verification results

- Observed expected red tests: missing member-metadata helper and missing category tabs. The corrected metadata helper then passed all five focused tests.
- Final runs passed: 120 Node tests, production TypeScript/Vite build, 83 polish browser cases, 18 settings cases and 44 traffic cases (145 browser cases).
- Visually inspected 320/360/1440px dark/light cards, mobile two-column layout, full-card native-confirmed selection and nested fallback route. Country flags use the existing SVG flag assets rather than Windows emoji glyphs.
- Category navigation/menu/keyboard generates no connect/select/inspect IPC; automatic groups remain read-only, selection and admission failures remain visible. Inspection recovery uses the existing subscription Refresh action.
- No native resources or host network settings were changed; provider descriptions remain complete.
