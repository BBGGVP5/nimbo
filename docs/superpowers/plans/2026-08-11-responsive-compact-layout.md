# Responsive Compact Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Nimbo's bottom navigation and profile content readable, reachable, and non-overlapping at the supported minimum window size of 320×520 and at compact intermediate widths.

**Architecture:** Preserve the existing floating glass navigation, but give it five explicit columns and reserve its physical viewport area in the main scroll container. Add semantic classes to the subscription summary so its action cluster moves to a dedicated row only at phone-width windows, without changing the desktop card.

**Tech Stack:** React 19, TypeScript, Tailwind utility classes, responsive CSS, Vite, in-app browser visual testing.

---

### Task 1: Make the compact navigation a single non-overlapping row

**Files:**
- Modify: `apps/ui/src/App.tsx:950-962`
- Modify: `apps/ui/src/lib/i18n.ts:6-24`
- Modify: `apps/ui/src/styles.css:8051-8160`
- Modify: `apps/ui/src/styles.css:11190-11252`

- [x] **Step 1: Correct the compact navigation column count**

Change the mobile navigation grid to five columns because `Home`, `Profiles`, `Apps`, `Sync`, and `Settings` all remain visible:

```css
.app-sidebar nav {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}
```

This prevents Settings from creating a clipped second row.

Use compact Russian labels that fit one fifth of a 320 pixel window:

```tsx
appsShort: "Прил.",
settingsShort: "Настр.",
```

Return `settingsShort` from `navLabel` when `short` is true. Full sidebar labels and link accessible names remain unchanged.

- [x] **Step 2: Reserve the navigation footprint in the scroll layout**

Replace the theme-specific bottom padding with an actual layout reservation:

```css
body[data-ui-style="nimbo"] .app-main,
body[data-ui-style="material_you"] .app-main,
body[data-ui-style="dotted"] .app-main {
  box-sizing: border-box;
  margin-bottom: 88px;
  padding-bottom: 12px;
}
```

The navigation remains visually floating at `bottom: 8px`, while `.app-main` ends above it and owns scrolling for the reduced height.

- [x] **Step 3: Add a low-height compact variant**

For windows no taller than 640 CSS pixels, reduce the bar without sacrificing touch targets:

```css
@media (max-width: 720px) and (max-height: 640px) {
  body[data-ui-style="nimbo"] .app-sidebar,
  body[data-ui-style="material_you"] .app-sidebar,
  body[data-ui-style="dotted"] .app-sidebar {
    left: 8px;
    right: 8px;
    bottom: 6px;
    height: 72px;
  }

  body[data-ui-style="nimbo"] .app-main,
  body[data-ui-style="material_you"] .app-main,
  body[data-ui-style="dotted"] .app-main {
    margin-bottom: 78px;
    padding-bottom: 10px;
  }
}
```

### Task 2: Reflow profile controls at the minimum width

**Files:**
- Modify: `apps/ui/src/pages/Subscriptions.tsx:470-557`
- Modify: `apps/ui/src/styles.css`

- [x] **Step 1: Add stable semantic hooks to the profile card**

Name the card sections without removing their existing utility classes:

```tsx
<section className="subscription-card panel relative">
  <div className="subscription-card-body cursor-pointer p-4">
    <div className="subscription-summary mb-4 grid grid-cols-[24px_minmax(0,1fr)_auto] ...">
      <div className="subscription-summary-main min-w-0">...</div>
      <div className="subscription-summary-actions flex items-center gap-1.5">...</div>
    </div>
    <div className="subscription-stats mobile-stack ...">...</div>
    <div className="subscription-description ...">...</div>
  </div>
</section>
```

- [x] **Step 2: Move actions below the title at 430 pixels and below**

Add the compact card rules:

```css
@media (max-width: 430px) {
  .subscription-card-body {
    padding: 14px;
  }

  .subscription-summary {
    grid-template-columns: 20px minmax(0, 1fr);
    gap: 10px;
  }

  .subscription-summary-actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
    flex-wrap: wrap;
    padding-left: 30px;
  }

  .subscription-summary-actions .subscription-reorder-control {
    min-height: 40px;
  }
}
```

This keeps the name/count row independent from the five action buttons and preserves 36–40 pixel controls at the supported 320 pixel width.

### Task 3: Verify compact breakpoints and production output

**Files:**
- Verify: `apps/ui/src/styles.css`
- Verify: `apps/ui/src/pages/Subscriptions.tsx`

- [x] **Step 1: Run the production build**

Run:

```powershell
npm run build
```

Expected: TypeScript and Vite complete successfully with no CSS parse errors.

- [x] **Step 2: Inspect the local app at the reported window sizes**

Run the Vite preview and inspect `/subscriptions` in the in-app browser at these viewport sizes:

```text
320×520  — minimum supported window
568×520  — short intermediate window matching the scaled screenshot
710×1180 — tall compact layout matching the scaled portrait screenshot
```

Expected at every size: exactly one navigation row, no clipped Settings icon, no content underneath the navigation, and horizontal profile controls that do not collide with the title.

- [x] **Step 3: Re-run the production build after visual adjustments**

Run:

```powershell
npm run build
```

Expected: the final responsive CSS and JSX compile successfully.
