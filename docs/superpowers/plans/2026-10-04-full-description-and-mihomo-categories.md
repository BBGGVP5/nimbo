# Full Descriptions and Mihomo Categories Implementation Plan

> **For agentic workers:** Execute inline in the current checkout, test-first, preserving unrelated changes.

**Goal:** Display provider descriptions in full immediately and show the subscribed YAML's real Mihomo categories even when inspection reports configuration issues.

**Architecture:** Remove the announcement disclosure and visual clamp, retaining escaped original text and line breaks. Separate a digest-bound declared graph preview from native connection admission: inspection issues are displayed, not used to erase the graph; all offline selections remain disabled. Keep live groups bound to the matching active session and add explicit inspection/loading/empty/retry states instead of a silent blank list. Do not change native safety gates or synthesize categories from legacy Xray nodes.

**Tech Stack:** React, TypeScript, CSS, Node tests, Playwright/Vite, source-built pinned Mihomo inspector in inspect-only mode.

---

### Task 1: Capture and reproduce

**Files:**
- Create: `apps/ui/tests/fixtures/mihomo-warning-inspection.json`
- Modify: `apps/ui/tests/providerAnnouncement.test.mjs`
- Modify: `apps/ui/tests/core-subscription-groups.test.mjs`

- [x] Verify the staged helper SHA256 against its build manifest and pinned commit; run only `nimbo-mihomo.exe inspect` with a controlled local YAML containing `log-level: info`, `mixed-port: 7890`, and three declared groups. Native result: VPN, Streaming, Auto plus two UNSUPPORTED_CONFIG issues. No runtime, listener, TUN or user VPN was started.
- [x] Store an allowlisted fixture projection (name/type/proxies metadata, issue code/path, exact fake-source digest), excluding native provider/proxy credentials and URLs from the display graph.
- [x] Add regressions:

```js
assert.doesNotMatch(render(longDescription), /is-collapsed|<button\b/);
assert.deepEqual(subscriptionGroups(nativeFixtureProfile, null, false).map(([name]) => name), ['VPN', 'Streaming', 'Auto']);
```

- [x] Run `node --test tests/providerAnnouncement.test.mjs tests/core-subscription-groups.test.mjs` from `apps/ui`. Observed both expected failures before implementation, then all seven focused tests passed.

### Task 2: Implement the corrected display contracts

**Files:**
- Modify: `apps/ui/src/pages/profiles/ProviderAnnouncement.tsx`
- Modify: `apps/ui/src/pages/home/home-profile-polish.css`
- Modify: `apps/ui/src/components/core-subscription-groups.ts`
- Modify: `apps/ui/src/components/CoreSubscriptionControl.tsx`

- [x] Render `<section className="nimbo-provider-announcement" data-no-toggle><p>{description}</p></section>` after the empty-content check. Remove observer/disclosure state, generated headings, collapsed CSS, and disclosure-button CSS; preserve `white-space: pre-wrap` and wrapping.
- [x] Introduce `currentSubscriptionInspection(profile)` requiring API 1, matching source digest, an array of groups and an array of issues. Use that guard for offline categories; remove only the `issues.length` graph-suppression gate. Keep hidden/malformed group exclusion and actual live-controller authority.
- [x] Inspect absent or stale metadata through the existing native command and persisted readback; add an explicit retry button for failure/empty states, a compact expandable issue count, loading status and truthful no-visible-groups message. Never treat displayed categories as proof that a profile can connect; native mode-specific admission remains unchanged.
- [x] Reload core metadata when the subscribed profile id or fetched timestamp changes, deferring until a busy core operation finishes; retain a per-component version ref to avoid repeated reads. Test that a refreshed subscription with a changed YAML digest/revision replaces old categories after inspection without connecting. Observed browser regression fail before the version ref effect, then pass.

### Task 3: Browser regression and delivery

**Files:**
- Modify: `apps/ui/tests/browser/cross-platform-polish.tsx`
- Modify: `apps/ui/tests/browser/cross-platform-polish.mjs`

- [x] Replace read-more/clamped assertions with exact text, unbounded visible paragraph height and absent disclosure buttons on Home/Profiles at 360/1440px, both themes, including source update and resize.
- [x] Render the captured native graph with two issues at 360/1440px; require all three categories, disabled offline members, expandable diagnostics and zero connect/select IPC during rendering. Cover stale-inspection reload, failed inspection with explicit retry, real empty graph, and preservation of running-session selection/readback tests.
- [x] Run `npm test`, `npm run build`, `npm run test:polish`, `npm run test:settings`, and `npm run test:traffic:browser`. All passed: 119 Node tests, production TypeScript/Vite build, 71 polish cases, 18 settings cases and 44 traffic cases (133 browser cases). Visually inspected generated complete Home/Profiles descriptions and native-warning category previews at 360/1440px, dark/light themes. Browser IPC was isolated; no user subscription, runtime, TUN or proxy was used.
- [ ] Safely mirror only owned files after primary/HEAD preimage and hash checks; commit/push only the owned paths. Dispatch artifact-only Windows x64/x86/ARM64 build (`release.yml`, `publish=false`, `platform=windows`) and report its actual status.

## Scope boundaries

The native inspector can return a parsed graph together with mode-specific admission issues. Showing that graph is a read-only preview, not permission to connect, silently alter YAML, weaken validation, apply network settings, or infer external-provider members. When no trustworthy graph exists, show an explicit state and native retry instead of fabricated categories.
