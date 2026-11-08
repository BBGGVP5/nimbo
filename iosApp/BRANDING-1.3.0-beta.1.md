# iOS white-cloud branding plan and validation

Scope: `iosApp/` only. Parent plan: `docs/superpowers/plans/2026-09-13-branding-130-beta1.md`. Master artwork, shared Compose branding/release defaults, Android/desktop, root CI scripts and workflows belong to other workers. Preserve `iosApp/build`, GoBridge and the 1.2.0 release ledger. No publishing or CI dispatch.

- [x] Package the approved opaque master into the existing 1024px AppIcon using Node/sharp. Create `Branding/Branding.xcassets` with the supplied alpha cloud as a template imageset and a generated custom symbolset using the same path. Never replace it with an SF cloud.
- [x] Include this catalog as resources in both app and Control Widget targets, outside `Shared` so the packet tunnel does not inherit UI resources. Render `Image("NimboCloudSymbol")` in the control's existing label; preserve actions and state text. Add the cloud beside native Home/About Nimbo titles without changing layout/navigation behavior.
- [x] Keep App Shortcut action SF Symbols: Apple only exposes `systemImageName`, not a custom asset initializer. The new AppIcon updates the app identity that Shortcuts displays. Document this actual platform limitation in the source/report.
- [x] Set inherited build180, marketing1.3.0, display1.3.0-beta.1 for all targets. Set new-install iOS update-channel fallback to beta while retaining saved stable/beta, and make the convenience checker use the saved channel.
- [x] Add reproducible asset packaging plus focused Node/Python validation of pixels/alpha/shape, catalog targets, symbol guides/variants, version plists and update-channel preservation. Run available existing iOS source tests. Supply Mac checks for actool custom-symbol compilation/rendering and Swift update-channel execution; do not claim they ran on Windows.
- [x] Record exact changed paths and validation results here. No writes outside `iosApp/`.

## Apple contracts inspected

- [Control widget templates](https://developer.apple.com/documentation/swiftui/controlwidgettemplate) use symbol images, so an ordinary PNG/SVG imageset is insufficient for the Control Center glyph.
- [Custom symbol images](https://developer.apple.com/documentation/uikit/creating-custom-symbol-images-for-your-app) use SVG `Symbols`/named weight-size variants, alignment guides and a Symbol Image Set. The packaging script supplies the original cloud path in all weights/sizes rather than relying on interpolation to alter the brand.
- [AppShortcut](https://developer.apple.com/documentation/appintents/appshortcut) lists `systemImageName` initializers only; [Apple DTS](https://developer.apple.com/forums/thread/758159) confirms custom symbols cannot be passed there.
- [App icon catalogs](https://developer.apple.com/documentation/xcode/configuring-your-app-icon) support one 1024px image for generated iOS/iPadOS variants.

## Results

Source implementation complete; Mac integration verification remains outstanding.

- PASS `node iosApp/Tests/test_branding_assets.cjs`: approved-master pixel equality, icon1024x1024/no alpha, white transparent template, exact cloud path in 27 symbol variants, all alignment/margin guides.
- PASS `python -B iosApp/Tests/test_branding_contracts.py`: 5 tests, version180/1.3.0/1.3.0-beta.1 inherited by all3 targets, both UI targets include catalog, packet tunnel excluded, custom Control Center symbol/state/action preserved, shortcut API constraint, beta fallback preserves explicit stable/beta.
- PASS `python -B iosApp/Tests/test_ping_contracts.py`: all18 existing source tests.
- Node/sharp 0.35.4 used for packaging/pixel validation. This Windows session used `NODE_PATH=C:/Users/Danila/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules`. No Python image processing.
- No old active version defaults remain in iosApp; the version-comparison comment in NimboUpdateChecker and immutable RELEASE-1.2.0.md remain historical examples. NimboPlatformInfo already reads the plist settings and keeps its honest unavailable-value fallback.
- `NimboUpdateChecker.latest()` now consults `NimboUpdateCenter.channel` when no channel argument is supplied, so saved stable is respected by that convenience API too. No stored preference is rewritten.

**Mac-only limitations:** `xcrun actool`, XcodeGen, Swift/AppIntents metadata compilation, Simulator and device Control Center rendering were unavailable and were not run. Execute `python3 iosApp/Tests/test_branding_contracts.py --mac` to compile the actual catalog separately for app/widget and run the extracted actual Swift channel policy. Then generate/build the existing Xcode project normally and verify the Control Center gallery/toggle in both states and Shortcuts app icon after installation. This script is validation only and does not publish or sign anything.

**Shortcut limitation:** Public AppShortcut API takes only SF Symbol names; using NimboCloudSymbol in `systemImageName` would render blank. Existing connect/disconnect/status action symbols remain, while the new AppIcon supplies the app identity. Ordinary image assets are NOT used for Control Center: it uses the custom NimboCloudSymbol.symbolset via Image by asset name, as supported by Apple.

**Ownership:** Only the16 paths below were edited/created by this task. The native Home/About views now show the cloud. The active RootView hosts Compose; shared Compose artwork is outside iosApp and stays with main. No changes to root scripts/workflows, master assets, shared code, Android/desktop, GoBridge, iosApp/build or RELEASE-1.2.0.md. The workspace is not a Git checkout, so no git diff/commit is claimed. No CI dispatch or publication.

## Exact changed paths

This report: `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/BRANDING-1.3.0-beta.1.md`.

| Absolute path | SHA256 at handoff |
| --- | --- |

| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/project.yml` | `a1f6fa99cc8e07ccba2eacc640e5f4a79700b8eecbeb29847ca02a93d709c165` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/ControlWidget/NimboControlWidget.swift` | `dbdb050fa53c9210c19e7cbdab51e5432b4aaadaecbe5bb98dbb7678064f607f` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Nimbo/NimboAppIntents.swift` | `8c22cdab6130851d5e7c7662c4960784ef337cb3fe586f13d4eaa31c1015e77d` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Nimbo/HomeContainerView.swift` | `0c70851b228c91aba42e6b5aa04146d535d416b2eb8fb019e5d135f48adfae3c` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Nimbo/AboutView.swift` | `0558b6303b8759f461ac8b3cc5c91112da45b8bc4842cfdd0fd879bbee60013d` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Nimbo/NimboUpdateChecker.swift` | `a9825a483bf002e24650754b6bd40c88174644c894957691cefbbbd445e12895` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Nimbo/Assets.xcassets/AppIcon.appiconset/Nimbo-AppIcon-1024.png` | `eaa1f148417a1f4d9db6542b64d95351f2562d66d7e0bd9ec1e25348c54e0db9` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Branding/Branding.xcassets/Contents.json` | `f5fb0e1f3a8142892ed092efb5955c1c2cc537003a89ad7bbdb9dade7fe8985f` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Branding/Branding.xcassets/NimboCloud.imageset/Contents.json` | `d79dff7a80d6b0f9a2bf6711fda865ca73327ef2d11e4c4d974236ac6f5c5f60` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Branding/Branding.xcassets/NimboCloud.imageset/cloud-template.svg` | `e13f2b14bce016a510014d49ef3afbb0202f1eefe25b9f580c37c13a03c33409` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Branding/Branding.xcassets/NimboCloudSymbol.symbolset/Contents.json` | `67c8382c4101992837d34b644830cdb4abc0288bb2834f7ab26235f10b61b74d` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Branding/Branding.xcassets/NimboCloudSymbol.symbolset/NimboCloudSymbol.svg` | `ac8328008432958f4d6e4ae96338bd1817e979a0e519149d29a7e334ddb8d0e6` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Tools/package-branding.cjs` | `898bc5ff8f7a9bfb680da86c5499b6d397a04cbe69625ed7086967d488b98d61` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Tests/test_branding_assets.cjs` | `df7779d9bb141247b000fefd595d4a06c2fdc4928f72b446003cad65a35bd1ff` |
| `C:/Users/Danila/AndroidStudioProjects/Nimbo/iosApp/Tests/test_branding_contracts.py` | `0c07097dc7d78835b63ff884ba1184e64773f64196a5098e1e1dcfde45cfd2e2` |
