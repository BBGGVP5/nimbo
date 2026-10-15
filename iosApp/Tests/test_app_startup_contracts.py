"""Startup wiring/resource contracts only; these do not execute SwiftUI or prove device rendering."""
from pathlib import Path
import plistlib
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


def source(name):
    return (ROOT / name).read_text(encoding='utf-8-sig')


class AppStartupContracts(unittest.TestCase):
    def test_active_app_constructs_root_before_async_storage_and_vpn_restore(self):
        app = source('iosApp/Nimbo/NimboApp.swift')
        self.assertRegex(app, r'@main\s+struct NimboApp: App')
        self.assertIn('@StateObject private var vpnController = VpnController()', app)
        body = app.split('var body: some Scene', 1)[1]
        self.assertLess(body.index('RootView()'), body.index('.task {'))
        self.assertIn('.environmentObject(vpnController)', body)
        task = body.split('.task {', 1)[1]
        self.assertLess(task.index('code: "IOS_APP_STARTED"'), task.index('migrateStoredProfileIfNeeded()'))
        self.assertIn('await vpnController.restore()', task)
        self.assertNotIn('if ', body.split('.task {', 1)[0], 'Root construction must not wait for a storage/VPN readiness condition')

    def test_home_constructs_the_real_compose_controller_and_native_navigation(self):
        root = source('iosApp/Nimbo/RootView.swift')
        self.assertIn('@State private var selectedTab: NimboTab = .home', root)
        screen = root.split('private var screen: some View', 1)[1].split('private var lifecycleLayer:', 1)[0]
        self.assertIn('ComposeScreen(tab: selectedTab)', screen)
        self.assertIn('NimboTabBar(selection: $selectedTab)', screen)
        self.assertIn('NimboWideSidebar(selection: $selectedTab)', screen)
        factory = source('iosApp/Nimbo/ComposeScreen.swift')
        self.assertIn('IosComposeControllerKt.NimboComposeViewController(screenName: tab.rawValue)', factory)
        bridge = source('shared/src/iosMain/kotlin/com/danila/nimbo/shared/ui/IosComposeController.kt')
        controller = bridge.split('fun NimboComposeViewController(', 1)[1]
        self.assertIn('ComposeUIViewController {', controller)
        self.assertIn('NimboSharedScreen(', controller)

    def test_shortcut_indexing_is_gated_and_deferred_until_after_root_construction(self):
        app = source('iosApp/Nimbo/NimboApp.swift')
        initializer = app.split('init() {', 1)[1].split('@StateObject', 1)[0]
        self.assertNotIn('updateAppShortcutParameters()', initializer)
        body = app.split('var body: some Scene', 1)[1]
        self.assertLess(body.index('RootView()'), body.index('.task {'))
        task = body.split('.task {', 1)[1]
        self.assertIn('Bundle.main.url(forResource: "extract", withExtension: "actionsdata",', task)
        self.assertIn('subdirectory: "Metadata.appintents") != nil', task)
        self.assertRegex(task, r'await NimboShortcutRegistration\.shared\.refreshIfAvailable\(hasMetadata: hasMetadata\)\s*\{\s*NimboShortcuts\.updateAppShortcutParameters\(\)')
        self.assertIn('code: "IOS_APPINTENTS_METADATA_MISSING"', task)

    def test_swiftui_and_ios_compose_use_the_same_bundled_font_bytes(self):
        native = source('iosApp/Nimbo/NimboNativeFonts.swift')
        names = re.findall(r'"((?:golos|manrope)_\w+)"', native)
        expected = {family + '_' + weight for family in ('golos', 'manrope')
                    for weight in ('regular', 'medium', 'semibold', 'bold')} | {'manrope_extrabold'}
        self.assertEqual(set(names), expected)
        self.assertIn('Bundle.main.url(forResource: name, withExtension: "ttf")', native)
        self.assertIn('NimboNativeFonts.register()', source('iosApp/Nimbo/NimboApp.swift'))
        ios_typography = source('shared/src/iosMain/kotlin/com/danila/nimbo/shared/ui/NimboPlatformTypography.kt')
        for token in ['NSBundle.mainBundle', 'bundle.pathForResource(file, "ttf")',
                      'bundle.pathForResource(file, "ttf", "Fonts")', 'contentsAtPath(path)',
                      '"regular"', '"medium"', '"semibold"', '"bold"', '"extrabold"']:
            self.assertIn(token, ios_typography)
        self.assertNotIn('Res.font', ios_typography, 'iOS does not use the separate Android/desktop Compose font bundle')
        for name in expected:
            data = (ROOT / 'iosApp/Nimbo/Fonts' / (name + '.ttf')).read_bytes()
            self.assertIn(data[:4], (b'\x00\x01\x00\x00', b'OTTO'), name)
            self.assertGreater(len(data), 1024, name)

    def test_compose_required_display_key_is_in_both_app_plist_sources(self):
        info = plistlib.loads((ROOT / 'iosApp/Nimbo/Info.plist').read_bytes())
        self.assertIs(info.get('CADisableMinimumFrameDurationOnPhone'), True)
        project = source('iosApp/project.yml').split('  Nimbo:', 1)[1].split('  NimboPacketTunnel:', 1)[0]
        self.assertIn('CADisableMinimumFrameDurationOnPhone: true', project)


if __name__ == '__main__':
    unittest.main()
