# Desktop layout refinement Implementation Plan

> **For agentic workers:** Execute the following checkboxes inline; preserve unrelated changes and use isolated browser fixtures instead of host VPN/service operations.

**Goal:** Separate the round/compact connection action, align home cards, simplify sidebar/settings/notifications and expose subscribed Mihomo groups inline.

**Architecture:** Keep existing React connection/store callbacks authoritative. Scope geometry to the home/profile/settings/notification components, replacing conflicting legacy CSS rather than changing routing or source ownership. Runtime group choices come from the active controller; disconnected group previews come only from verified inspection metadata.

**Tech Stack:** React, TypeScript, Zustand, CSS, Node test runner, Vite and Playwright.

---

### Task 1: Home action and sidebar
Files: `apps/ui/src/pages/Home.tsx`, `apps/ui/src/pages/home/SignalHome.tsx`, `apps/ui/src/pages/home/home-profile-polish.css`, `apps/ui/src/components/SignalSidebar.tsx`, `apps/ui/src/App.tsx`; tests: `apps/ui/tests/home.test.mjs`, `apps/ui/tests/browser/cross-platform-polish.{tsx,mjs}`.

- [x] Change the preference regression test and add browser geometry coverage, then run `npm run test:home` from `apps/ui` (expect failure for the currently hardcoded compact variant).
  ```js
  assert.equal(button.props['data-variant'], compact ? 'compact' : 'round');
  assert.equal(await page.locator('.universal-connection .nimbo-connect-action').count(), 0);
  assert(Math.abs(layout.connection.width - layout.card.width) < 2);
  assert(Math.abs(layout.connection.height - layout.card.height) < 2);
  ```
- [x] Move the existing action above the grid and render its text outside the normal circle, keeping compact text inside:
  ```tsx
  <div className="universal-connect-control"><div className="universal-power">{actions}</div></div>
  // Home action
  data-variant={isCompactButton ? 'compact' : 'round'}
  ```
  Wide home grid uses `grid-template-columns:repeat(2,minmax(0,1fr))`; sections and their cards stretch equally. Below 1000px use one column. Round is 128px square with a 44px icon; compact remains a 48px-high pill. Keep cancel outside the card grid. Keep update action but remove the footer version and pass `coreLabel={m.settings.connection}`.
- [x] Run `npm run test:home` and `npm run test:polish`; inspect generated wide/narrow dark/light screenshots, verify accessible labels, busy guard, pull refresh and actual Mihomo connect callback.

### Task 2: Settings and notifications
Files: `apps/ui/src/pages/settings-shell.css`, `apps/ui/src/components/CorePreferenceSetting.tsx`, `apps/ui/src/pages/Notifications.tsx`, new `apps/ui/src/pages/notifications-polish.css`; tests: `apps/ui/tests/browser/settings-polish.mjs`, cross-platform browser fixtures above.

- [x] Add computed-style assertions: last visible settings row has no trailing divider, no Advanced YAML link, notification icons are inset at least 12px and messages wrap without overflow.
  ```js
  assert.equal(await page.getByRole('link', {name:/Дополнительно:/}).count(), 0);
  assert.equal(getComputedStyle(lastVisibleRow, '::after').content, 'none');
  assert.equal(getComputedStyle(notification, '::before').content, 'none');
  ```
- [x] Remove the Advanced link, switch settings separators to top dividers on subsequent visible rows, stop stretching sparse overview cards, tighten detail control spacing. Use a dedicated scoped notification stylesheet with 16px padding, 36px icon, text/time/delete columns, 44px delete target, no unread edge stripe, readable wrapped text, and time beneath text on narrow screens. Keep deletion/filter/clear confirmation behavior.
- [x] Run `npm run test:settings` and notification browser cases in `npm run test:polish`; verify both themes at 360 and 1440px, including long text, filters, delete and clear/cancel.

### Task 3: Profile header and Mihomo groups
Files: `apps/ui/src/pages/profiles/SignalProfileCard.tsx`, `apps/ui/src/components/CoreSubscriptionControl.tsx`, new `apps/ui/src/components/core-subscription-groups.ts`; tests: new `apps/ui/tests/core-subscription-groups.test.mjs` and cross-platform browser fixtures.

- [x] Test verified disconnected inspection groups, live groups, hidden-group exclusion and stale inspection rejection. Add browser assertions for disclosure alignment and inline member selections.
- [x] Remove the legacy double-rotated collapse span; use an explicit 44px disclosure button alongside info/menu with the same sizing. Replace Mihomo dropdown/link with inline disclosure sections and member buttons. Selectors call `core.live('select', name, member)` only in the matching running session; automatic groups remain read-only. Render verified inspection previews disabled when disconnected; do not invent native success or connect implicitly.
- [ ] Run `npm test`, `npm run build`, `npm run test:polish`, `npm run test:settings`; inspect screenshots. Mirror owned files only after normalized preimage/hash checks. Commit only owned paths, push the existing branch and dispatch Windows artifact build with `publish=false`; report its actual status.

## Verification record

- 115 Node tests passed. Production TypeScript/Vite build passed.
- 54 isolated home/sidebar/profile/notification/Mihomo browser cases passed at 360–1440px.
- 18 settings cases and 44 traffic-dashboard regression cases passed across themes/styles.
- Screenshots reviewed in dark/light and wide/narrow layouts. No host VPN, service, ACL or firewall changes.
- Primary-workspace preflight matched all 22 owned paths; mirror and Windows artifact dispatch are the remaining delivery steps.
