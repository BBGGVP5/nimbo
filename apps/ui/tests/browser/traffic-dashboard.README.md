# Traffic dashboard verification

Run in `apps/ui` in the delegated clone. The fixtures never hydrate the application,
read host telemetry, connect a tunnel, or alter services. The runner uses an ephemeral
loopback Vite server, an isolated headless browser, explicit traffic fixtures, and
an IPC allowlist containing only preference get/save and traffic-counter reset.

```powershell
npm run test:traffic
npm test
npm run build
# Optional: point at an already installed browser; do not install a browser for this test.
$env:NIMBO_CHROMIUM_PATH = 'C:\Users\Danila\AppData\Local\ms-playwright\chromium_headless_shell-1223\chrome-headless-shell-win64\chrome-headless-shell.exe'
npm run test:traffic:browser
```

Screenshots are written to ignored `dist/traffic-dashboard-screenshots` by default;
`NIMBO_TRAFFIC_ARTIFACT_DIR` can select another artifact directory. Run the build
before the browser tests, since a subsequent build clears `dist`.

Coverage: Signal, Material You, and Dotted; light/dark; 360/800/1100 widths;
session/month/all ranges; chart and history; measured, zero, unavailable, old-binary,
disconnected, and Xray nullable-protocol telemetry; English/Russian labels;
keyboard-accessible default-off ad switch; persisted on/off readback; save failure
and ignored-field compatibility; reset confirmation/cancellation. Screenshots
contain explicit fixture values and are not evidence of live native measurements.

Verified on 2026-10-03 in the delegated clone: focused unit suite 12/12;
`npm test` 102/102; `npm run build` passed; fixture type check and browser suite
26/26; 23 screenshots generated; scoped `git diff --check` passed. Browser
validation used the existing Chromium path shown above. No dependencies or
browsers were installed, and no commit/push or host configuration changes were made.

Rule-mode follow-up: the ad-control details explain that Mihomo requires `rule`
mode and rejects `global`/`direct` while ad blocking is on. The existing core error
mapping now recognizes `AD_BLOCKING_REQUIRES_RULE_MODE` with safe English/Russian
instructions. Relevant traffic/core unit suites passed 29/29, `tsc --noEmit`
passed, and the scoped diff check passed. No new screenshots or full browser
rerun were needed for this text/error-mapping change.

## Owned files

- `apps/ui/src/lib/api.ts`: optional/nullable traffic DTO extensions, default-off persisted preference, browser availability.
- `apps/ui/src/lib/statisticsPresentation.ts`: route/counter validation and range/availability presentation.
- `apps/ui/src/lib/coreProfiles.ts`: safe localized rule-mode rejection reason.
- `apps/ui/src/store.ts`: unavailable sessions excluded from new chart samples, app estimates, and history records.
- `apps/ui/src/pages/Statistics.tsx`: shared dashboard wiring, existing ranges/history/chart/reset flows.
- `apps/ui/src/pages/stats/SignalStatistics.tsx`: retained chart/history, nullable session rows, app-estimate label.
- `apps/ui/src/pages/stats/TrafficDashboard.tsx`: volume/live-speed cards, measured route ring, nullable TCP/UDP counts.
- `apps/ui/src/pages/stats/traffic-dashboard.css`: responsive dashboard and scoped style variants.
- `apps/ui/src/components/AdBlockingControl.tsx`: persisted accessible switch and next-connection/domain-filter explanation.
- `apps/ui/src/components/ad-blocking-control.css`: scoped control styling.
- `apps/ui/src/pages/Routing.tsx`: reuse of the same ad control.
- `apps/ui/tests/trafficDashboard.test.mjs`: focused production-code unit/render/store/API tests.
- `apps/ui/tests/coreSelection.test.mjs`: rule-mode error extraction/localization and secret-redaction regression case.
- `apps/ui/tests/browser/traffic-dashboard.html`: disposable browser fixture entry.
- `apps/ui/tests/browser/traffic-dashboard.tsx`: isolated Statistics/Routing fixtures.
- `apps/ui/tests/browser/traffic-dashboard.mjs`: browser assertions and screenshots.
- `apps/ui/tests/browser/tsconfig.traffic-dashboard.json`: fixture type check.
- `apps/ui/tests/browser/traffic-dashboard.README.md`: file ownership and validation instructions.
- `apps/ui/package.json`: focused unit and browser test commands.

Desktop native policy/telemetry and Android/iOS implementations belong to other
workers. These frontend checks do not prove device-level filtering or native
telemetry generation/lifecycle behavior. No blocked-request count is displayed.

Mobile follow-up: transfer cards stay adjacent at 360px as well; browser geometry asserts equal top/height at every viewport and no horizontal overflow. Production build and 26 cases passed again after the change.

Settings-placement follow-up (2026-10-03): ad blocking appears only in Settings → Routing,
not Statistics, on Android, shared/iOS and desktop. The visible description is two
short sentences; limitations remain in an opt-in info dialog/disclosure. The switch
still saves for the next connection without reconnecting. `npm test` 102/102,
production build and browser suite 44/44 passed; 42 screenshots cover Statistics
and compact Routing settings in three styles/two themes/360–1100 widths, plus
Russian at 360px. Android debug Kotlin/common metadata compiled; 21 shared render/
interaction tests passed, with 24 Routing renders in four styles/two themes/three
widths. Seven cross-platform/iOS source contracts passed. Mocked renders and source
checks are not device or packaged IPA/APK validation.
