"""Startup wiring/resource contracts only; these do not execute SwiftUI or prove device rendering."""
from pathlib import Path
import plistlib
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


def source(name):
    return (ROOT / name).read_text(encoding='utf-8-sig')


class AppStartupContracts(unittest.TestCase):
    def test_active_app_defers_root_state_to_native_gate_and_retains_bootstrap(self):
        app = source('iosApp/Nimbo/NimboApp.swift')
        self.assertRegex(app, r'@main\s+struct NimboApp: App')
        self.assertIn('WindowGroup { NimboLaunchView() }', app)
        self.assertNotIn('RootView()', app)
        self.assertNotIn('VpnController()', app)
        owner = source('iosApp/Nimbo/NimboRootOwnerView.swift')
        self.assertIn('@StateObject private var vpnController = VpnController()', owner)
        body = owner.split('var body: some View', 1)[1]
        self.assertLess(body.index('RootView()'), body.index('.task {'))
        self.assertIn('.environmentObject(vpnController)', body)
        task = body.split('.task {', 1)[1]
        self.assertLess(task.index('code: "IOS_APP_STARTED"'), task.index('migrateStoredProfileIfNeeded()'))
        self.assertIn('await vpnController.restore()', task)
        view = source('iosApp/Nimbo/NimboLaunchView.swift')
        self.assertIn('if let attempt = launch.rootToken', view)
        self.assertIn('NimboRootOwnerView(attempt: attempt, launch: launch)', view)

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

    def test_compose_host_does_not_double_count_the_keyboard(self):
        # Compose screens lift fields with imePadding; a host shrunk by SwiftUI's
        # keyboard safe area as well left a black gap of one keyboard height.
        root = source('iosApp/Nimbo/RootView.swift')
        screen = root.split('private var screen: some View', 1)[1].split('private var lifecycleLayer:', 1)[0]
        self.assertIn('ComposeScreen(tab: selectedTab).ignoresSafeArea(.keyboard)', screen)
        phone = screen.split('GeometryReader { geometry in', 1)[1]
        self.assertLess(phone.index('ComposeScreen(tab: selectedTab)'), phone.index('.ignoresSafeArea(.keyboard)'))
        self.assertLess(phone.index('.ignoresSafeArea(.keyboard)'), phone.index('.overlay(alignment: .bottom)'))
        self.assertNotIn('ProfilesContainerView().environmentObject(vpn).ignoresSafeArea', screen)
        overlay = phone.split('.overlay(alignment: .bottom)', 1)[1]
        self.assertLess(overlay.index('if !keyboardVisible'), overlay.index('NimboTabBar(selection: $selectedTab)'))
        self.assertIn('UIResponder.keyboardWillShowNotification', screen)
        self.assertIn('UIResponder.keyboardWillHideNotification', screen)
        for module in ['NimboSettingsScreen.kt', 'NimboModulesScreen.kt', 'NimboRoutingProfilesScreen.kt']:
            self.assertIn('.imePadding()', source('shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/' + module))

    def test_shortcut_indexing_is_gated_after_native_root_host_display_acknowledgement(self):
        app = source('iosApp/Nimbo/NimboApp.swift')
        self.assertNotIn('updateAppShortcutParameters()', app)
        owner = source('iosApp/Nimbo/NimboRootOwnerView.swift')
        self.assertIn('.task(id: launch.gate.completed)', owner)
        self.assertIn('if launch.gate.completed { await registerOptionalShortcuts() }', owner)
        task = owner.split('private func registerOptionalShortcuts()', 1)[1]
        self.assertIn('launch.gate.completed', task)
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
        coordinator = source('iosApp/Nimbo/NimboLaunchCoordinator.swift')
        self.assertIn('NimboNativeFonts.register()', coordinator)
        self.assertLess(coordinator.index('await journal.save('), coordinator.index('NimboNativeFonts.register()'))
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
