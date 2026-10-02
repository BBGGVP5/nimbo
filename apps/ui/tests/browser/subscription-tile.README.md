# Subscription tile pointer fixture

This fixture mounts the production `SignalProfileCard` used by Home and Subscriptions. It supplies isolated data and callback counters without hydrating the store or calling native IPC. It is not an additional production page.

From `apps/ui`:

```powershell
node node_modules/typescript/bin/tsc --noEmit -p tests/browser/tsconfig.subscription-tile.json
node node_modules/vite/bin/vite.js --config tests/browser/subscription-tile.vite.mjs
```

Open `http://127.0.0.1:5197/tests/browser/subscription-tile.html` through CUA. Use screenshot-derived pointer coordinates (not DOM `.click()` or React callbacks). Read the counters after every action.

1. Click the header text twice: `toggles` increases once per click; the card expands then collapses.
2. Repeat for the outer top/side padding, quota bar, traffic/expiry text, and the footer timestamp/blank area. Each click toggles exactly once.
3. Click Ping and Refresh, including their SVG glyphs: only their counters increase.
4. Check **Disable ping and refresh**. Click the disabled buttons' text, icons, and edges: no counter changes and no disclosure.
5. Open Info and click the provider description inside the dialog: no disclosure. Close the dialog: no disclosure.
6. Open the menu: no disclosure. Click disabled Move up/Move down: no action or disclosure. Click Settings: only `menu` increases.
7. Expand the card. Click Select server: only `server` increases. Click Disabled server or the blank server region: no action or disclosure.
8. Focus the original header button and press Enter/Space: one disclosure per activation; other buttons remain independently keyboard-operable.

## Verified through parent CUA — 2026-09-23

TypeScript validation passed. After the initial worker session could not access a browser, parent opened CUA tab 6 and completed real pointer/keyboard QA. Missing stylesheet dependencies were corrected using production imports only: flags, base, universal, secondary, preview parity, and fonts. No CSS overrides or production edits were needed.

Observed results reported by parent:

- Top blank space, timestamp, quota bar, side padding, and header: one toggle each, reaching `toggles=5`.
- Ping/Refresh glyphs: `ping=1`, `refresh=1`, no disclosure. Disabled Ping/Refresh clicks: no changes.
- Select server: `server=1`; disabled server and blank server region: no changes.
- Info description/close: no disclosure. Disabled Move up/Move down: no changes. Settings: `menu=1`.
- Header Enter: `toggles=6, collapsed=true`; Space: `toggles=7, collapsed=false`.
- Final counters: `toggles=7, ping=1, refresh=1, menu=1, server=1, collapsed=false, busy=true`.

Parent closed the temporary tab and stopped Vite session 96858 with Ctrl+C. These results cover this production-component fixture, not native desktop IPC integration or device testing. Android/shared and desktop production files remained unchanged during fixture QA.

## Updated ping action — 2026-10-02

Ping is deliberately enabled while measuring: it now displays Cancel and stops that operation. Busy still disables Refresh. The original disabled-button QA above is historical; rerun step 4 expecting only the Ping counter to increase, never the disclosure counter.
