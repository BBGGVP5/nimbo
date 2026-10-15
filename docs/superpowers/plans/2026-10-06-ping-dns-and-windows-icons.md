# Ping DNS reliability and Windows icon polish implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the reproduced common offline-ping failure, unify Ping glyphs, and deliver smooth rounded Windows application/installer icons.

**Architecture:** Keep the independent, owned outbound probe. Its resolver projection removes only bare VPN group-routing tags from DNS URLs, retaining transport `key=value` parameters and original profile bytes. Keep native connection admission unchanged. Reuse one canonical Ping component throughout UI. Generate Windows-specific transparent rounded artwork from the approved vector (not a raster edit), supersample every ICO frame and put 256px first because Tauri codegen decodes ICO entry zero for its window icon.

**Tech Stack:** Go/Rust native tests, React/TypeScript, SVG/Sharp, ICO packaging, isolated Playwright.

---

### Task 1: Reproduced DNS failure
**Files:** Modify `tools/native/mihomo-core/desktop_probe.go`, `desktop_probe_test.go`, `API.md`; modify `apps/ui/src/lib/mihomoPing.ts` and its tests, `components/MihomoProxyGroups.tsx`.
- [ ] Add a failing native test using DNS entries such as `https://192.0.2.1/dns-query#Fixture group&h3=true` with a strictly loopback GET destination. Preserve source digest and stopped ownership, and prove no provider/session is created.
- [ ] Implement a small DNS-string projection: `base,fragment := strings.Cut(server,"#")`; retain only fragment pieces containing `=`; preserve their order/value. The removed bare piece is a VPN group route, not a transport parameter. DNS URI/settings and profile source remain unchanged except this ephemeral route-independent probe projection.
- [ ] Test mixed transport parameters, plain addresses, Unicode routing tags, invalid DNS and local outbound-host DNS resolution. Rerun the actual saved-source DIRECT probe against loopback only, printing fixed error codes and counts, never source/credentials/addresses.
- [ ] Distinguish fixed unsupported/DNS/helper presentation states from genuinely unreachable nodes. Validate via Node tests and isolated browser IPC; never expose arbitrary native exception messages.

### Task 2: Consistent Ping glyph
**Files:** Create `apps/ui/src/components/PingIcon.tsx`; modify `pages/home/SignalServerRail.tsx`, `components/MihomoProxyGroups.tsx`, browser polish assertions.
- [ ] Centralize the existing upward/downward round-trip arrow SVG paths. Keep old exports working and reuse the same component for card/footer/home actions, with existing pending/focus/44px/reduced-motion behavior.
- [ ] Assert every card/footer Ping uses identical SVG path data and that loading/errors remain readable at 360/1440 widths.

### Task 3: Windows-only rounded assets
**Files:** Create `scripts/package-windows-icons.cjs`, `scripts/tests/windows-icons.test.cjs`, `assets/branding/1.3.0-beta.1/app-icon-windows.svg`; regenerate `apps/ui/src-tauri/icons/{icon.ico,icon.png,32x32.png,128x128.png,128x128@2x.png}`; update branding README.
- [ ] Add failing artifact tests for transparent corners, fractional alpha antialiasing, preserved cloud path, correct PNG dimensions and multi-size ICO coverage with a 256px first entry. Existing Apple/Android/ICNS masters stay unchanged.
- [ ] Produce the Windows vector from the approved master: replace only its full-bleed background rectangle with an inset rounded rectangle. Render each requested size directly from vector with supersampling and Lanczos downsampling; do not upscale the 16px frame.
- [ ] Package descending ICO sizes `[256,128,96,64,48,40,32,24,20,16]`. Existing app/installer configs and shortcut payload already share this ICO; the correct first entry fixes the installer’s previously decoded 16px taskbar icon. Keep the separate transparent tray glyph untouched.
- [ ] Inspect alpha-composited previews at 16/20/32/40/64/256 on light/dark backgrounds, and validate with `node --test scripts/tests/windows-icons.test.cjs` using bundled Sharp.

### Task 4: Verify and deliver
- [ ] Run pinned Go suite + source build, Rust offline-helper loopback tests, UI Node/build/polish/settings/traffic checks and icon artifact checks. No real subscription remote ping, host VPN, app installation, icon-cache clearing or unrelated native-resource write.
- [ ] Mirror owned files only, verify text preimages normalized against HEAD and binary preimages byte-exact, snapshot/recheck SHA256, commit/push existing branch, verify mirrored/remote commit and dispatch artifact-only Windows release (`publish=false`).

## Verified results
- A readonly saved-source DIRECT check against a controlled loopback HTTP endpoint reproduced `PROBE_REQUIRES_SESSION` before this fix (zero endpoint requests), then succeeded after the fix (one request, `vpnStarted:false`). Profile source, names, credentials and endpoint addresses were not printed or persisted; no real subscription server was contacted.
- Pinned-source Go complete suite passed, including routing-fragment projection, unchanged source hash, actual configured proxy-host DNS resolution on loopback, cancellation and no VPN ownership. Four real-helper Rust loopback/request/proof tests passed with the fixed source-built helper.
- 128 UI Node cases, 103 polish browser cases, 18 settings cases, 44 traffic cases and production build passed. The browser verifies identical footer/card SVG paths and fixed DNS/helper/session messages without leaking exception text.
- Two Windows asset tests passed: correct RGBA dimensions, unchanged approved contour/opaque Apple master, transparent rounded corners, fractional-alpha edges, root/app ICO identity and high-resolution-first multi-DPI frames. Existing branding/SSR tests were updated for the new deliberate asset/component contracts.
- Windows icon previews were inspected on dark/light backgrounds. No Explorer restart, icon-cache deletion, installer/app startup or host network mutation occurred. `master-verification.json`, existing unrelated resources and historical Apple/ICNS/tray artwork remain untouched.
- Root branding regeneration now delegates its ICO packaging to the Windows-specific generator so it cannot regress to opaque rectangular, 16px-first artwork.
