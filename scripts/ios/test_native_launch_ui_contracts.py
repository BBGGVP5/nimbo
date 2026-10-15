"""Portable wiring contracts only; these do not execute SwiftUI or a simulator."""
import ast
import importlib.util
from pathlib import Path
import re
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('launch_ui', Path(__file__).with_name('test_native_launch_ui.py'))
ui = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ui)


class HarnessContracts(unittest.TestCase):
    def setUp(self):
        self.project = ui.project_spec(Path('/source'), Path('/generated'))
        self.target = self.project['targets']['NativeLaunchHost']

    def swift_test(self, name):
        return ui.TESTS.split('func ' + name + '()', 1)[1].split('\n    func ', 1)[0]

    def test_host_includes_actual_tab_bar_and_model_without_application_services(self):
        self.assertEqual(ui.COMPONENTS, [
            'NimboLaunchModel.swift', 'NimboLaunchJournal.swift',
            'NimboLaunchCoordinator.swift', 'NimboLaunchAppearanceObserver.swift',
            'NimboLaunchView.swift', 'NimboTabBar.swift', 'NimboTab.swift'])
        self.assertEqual(self.target['sources'], [
            str(Path('/source') / 'iosApp/Nimbo' / name) for name in ui.COMPONENTS
        ] + [str(Path('/generated/Host.swift'))])
        for name in ui.COMPONENTS:
            self.assertTrue((ui.ROOT / 'iosApp/Nimbo' / name).is_file(), name)
        self.assertEqual(self.target['info']['properties']['NimboLaunchDiagnostics'], '1')
        self.assertEqual(self.target['info']['properties']['CFBundleVersion'], '183')
        for forbidden in ('NimboShared', 'NimboRootOwnerView.swift', 'RootView.swift',
                          'VpnController.swift', 'NimboStorage', 'NimboNativeFonts.swift',
                          'NimboNativeDesign.swift'):
            self.assertNotIn(forbidden, str(self.target['sources']))
        self.assertEqual(re.findall(r'^import (\w+)', ui.HOST, re.MULTILINE), ['SwiftUI'])
        for forbidden in ('IosComposeControllerKt', 'NimboDiagnostics.shared',
                          'NimboProfile', 'NimboStorage', 'VpnController',
                          'UserDefaults', 'AppStorage', 'CoreText'):
            self.assertNotIn(forbidden, ui.HOST)

    def test_presentation_stubs_cover_actual_tab_bar_dependencies_with_system_fonts(self):
        tab_bar = (ui.ROOT / 'iosApp/Nimbo/NimboTabBar.swift').read_text(encoding='utf-8')
        self.assertIn('presentation-only', ui.HOST)
        self.assertIn('enum NimboNative {', ui.HOST)
        for color in set(re.findall(r'NimboNative\.(\w+)', tab_bar)):
            self.assertRegex(ui.HOST, r'static let ' + color + r'\s*=')
        self.assertIn('struct NimboBrand: View', ui.HOST)
        self.assertIn('func nimboFont(', ui.HOST)
        self.assertIn('relativeTo style: Font.TextStyle = .body', ui.HOST)
        self.assertIn('weight: Font.Weight = .regular', ui.HOST)
        self.assertIn('.font(.system(size: size, weight: weight))', ui.HOST)
        self.assertNotIn('.custom(', ui.HOST)
        self.assertIn('static var roots = 0', ui.HOST)
        self.assertIn('static var fonts = 0', ui.HOST)
        self.assertIn('static func register() { SmokeMeter.fonts += 1 }', ui.HOST)

    def test_actual_bar_overlays_full_size_content_with_selection_and_independent_heartbeat(self):
        self.assertIn('@State private var selection: NimboTab = .home', ui.HOST)
        self.assertIn('@State private var heartbeat = 0', ui.HOST)
        self.assertIn('.frame(maxWidth: .infinity, maxHeight: .infinity)', ui.HOST)
        self.assertIn('.overlay(alignment: .bottom)', ui.HOST)
        self.assertIn('NimboTabBar(selection: $selection)', ui.HOST)
        self.assertIn('Text(selection.rawValue)', ui.HOST)
        self.assertIn('.accessibilityIdentifier("smoke.selection")', ui.HOST)
        self.assertIn('Text("\\(heartbeat)")', ui.HOST)
        self.assertIn('.accessibilityIdentifier("smoke.heartbeat")', ui.HOST)
        self.assertIn('while !Task.isCancelled', ui.HOST)
        self.assertIn('try await Task.sleep(nanoseconds: 250_000_000)', ui.HOST)
        self.assertIn('heartbeat += 1', ui.HOST)
        self.assertNotIn('.task(id: selection)', ui.HOST)
        self.assertNotIn('.onChange(of: selection)', ui.HOST)
        self.assertIn('guard launch.claimBootstrap(attempt)', ui.HOST)
        self.assertIn('launch.rootDisplayAcknowledged(attempt)', ui.HOST)

    def test_ui_preserves_zero_factory_gate_and_native_diagnostic_relaunch(self):
        first = self.swift_test('testFirstVisibleNativeFrameDoesNotConstructRootOrRegisterFonts')
        self.assertLess(first.index('"root=0;fonts=0"'), first.index('action.tap()'))
        self.assertIn('app.buttons["nimbo.launch.stay"].tap()', first)
        self.assertIn('XCTAssertFalse(app.staticTexts["smoke.root"].exists)', first)
        relaunch = self.swift_test('testDiagnosticRelaunchNeverAutoEntersRoot')
        self.assertIn('app.terminate(); app.launch()', relaunch)
        self.assertIn('"root=0;fonts=0"', relaunch)
        self.assertIn('XCTAssertFalse(app.staticTexts["smoke.root"].exists)', relaunch)
        self.assertIn('app.screenshot()', ui.TESTS)
        self.assertIn('image.lifetime = .keepAlways', ui.TESTS)

    def test_repeated_actual_tab_taps_assert_raw_selection_and_later_heartbeat(self):
        test = self.swift_test('testActualNativeTabBarRepeatedSelectionKeepsHeartbeatAdvancing')
        self.assertLess(test.index('"root=0;fonts=0"'), test.index('action.tap()'))
        self.assertLess(test.index('action.tap()'), test.index('for _ in 0..<3'))
        tab_model = (ui.ROOT / 'iosApp/Nimbo/NimboTab.swift').read_text(encoding='utf-8')
        navigation = re.search(r'static let navigationTabs: \[NimboTab\] = \[(.*?)\]', tab_model).group(1)
        actual_tabs = re.findall(r'\.(\w+)', navigation)
        tested_tabs = re.findall(r'"([a-z-]+)"', re.search(r'let tabs = \[(.*?)\]', test).group(1))
        self.assertEqual(tested_tabs, actual_tabs)
        self.assertIn('for rawValue in tabs', test)
        self.assertIn('app.buttons["nimbo.tab.\\(rawValue)"]', test)
        self.assertIn('XCTAssertTrue(button.isHittable)', test)
        self.assertIn('button.tap()', test)
        self.assertIn('XCTAssertEqual(selected.label, rawValue)', test)
        self.assertIn('XCTAssertTrue(button.isSelected)', test)
        self.assertLess(test.index('XCTAssertEqual(selected.label, rawValue)'),
                        test.index('let before = try heartbeatValue(heartbeat)'))
        self.assertIn('heartbeatAdvanced(after: before, element: heartbeat)', test)
        self.assertIn('XCTAssertGreaterThan(try heartbeatValue(heartbeat), before)', test)
        self.assertIn('attach(app, "Actual native floating tab bar after repeated selections', test)
        self.assertIn('Int(element.label)', ui.TESTS)
        self.assertIn('XCTUnwrap(Int(element.label), "Invalid heartbeat:', ui.TESTS)
        self.assertIn('value > before', ui.TESTS)
        self.assertIn('XCTWaiter.wait(for: [advanced], timeout: 5)', ui.TESTS)

    def test_simulator_commands_and_ui_waits_are_bounded(self):
        source = Path(ui.__file__).read_text(encoding='utf-8')
        tree = ast.parse(source)
        commands = [node for node in ast.walk(tree) if isinstance(node, ast.Call)
                    and isinstance(node.func, ast.Attribute)
                    and isinstance(node.func.value, ast.Name)
                    and node.func.value.id == 'subprocess'
                    and node.func.attr in ('run', 'check_output')]
        self.assertEqual(len(commands), 3)
        for command in commands:
            timeout = next((kw.value for kw in command.keywords if kw.arg == 'timeout'), None)
            self.assertIsInstance(timeout, ast.Constant)
            self.assertGreater(timeout.value, 0)
            self.assertLessEqual(timeout.value, 600)
        self.assertIn("'-parallel-testing-enabled','NO'", source)
        self.assertIn("'-test-timeouts-enabled','YES'", source)
        self.assertIn("'-maximum-test-execution-time-allowance','180'", source)
        self.assertNotIn('sleep(', ui.TESTS)

    def test_non_macos_fails_without_creating_success_receipts(self):
        with patch.object(ui.platform, 'system', return_value='Windows'), \
                patch('sys.argv', ['test_native_launch_ui.py']), \
                patch.object(ui.Path, 'mkdir') as mkdir, \
                patch.object(ui.subprocess, 'run') as run, \
                patch.object(ui.subprocess, 'check_output') as check_output:
            with self.assertRaisesRegex(SystemExit, 'requires macOS/Xcode'):
                ui.main()
        mkdir.assert_not_called()
        run.assert_not_called()
        check_output.assert_not_called()

    def test_receipt_limits_claims_to_component_simulator_scope(self):
        source = Path(ui.__file__).read_text(encoding='utf-8')
        for field in ('fullAppLaunchTested=False', 'composeGpuPresentationTested=False',
                      'physicalDeviceTested=False', 'iOS27AcceptanceTested=False'):
            self.assertIn(field, source)
        self.assertIn('presentation-only', source)
        self.assertIn('tabBarAndModelSources', source)
        self.assertIn('NOT full Nimbo/Compose/iOS27/device acceptance', source)


if __name__ == '__main__':
    unittest.main()
