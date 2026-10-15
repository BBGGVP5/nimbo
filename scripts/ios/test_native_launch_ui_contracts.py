"""Portable harness wiring checks; simulator execution is a separate mandatory CI step."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('launch_ui',Path(__file__).with_name('test_native_launch_ui.py'))
ui = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ui)


class HarnessContracts(unittest.TestCase):
    def test_host_uses_actual_native_components_but_no_real_root_or_core(self):
        project = ui.project_spec(Path('/source'),Path('/generated'))
        target = project['targets']['NativeLaunchHost']
        self.assertEqual(target['sources'],[str(Path('/source')/'iosApp/Nimbo'/n) for n in ui.COMPONENTS]+[str(Path('/generated/Host.swift'))])
        self.assertEqual(target['info']['properties']['NimboLaunchDiagnostics'],'1')
        for forbidden in ('NimboShared','NimboRootOwnerView.swift','RootView.swift','VpnController.swift'):
            self.assertNotIn(forbidden,str(target['sources']))
        self.assertIn('static var roots = 0',ui.HOST)
        self.assertIn('static var fonts = 0',ui.HOST)

    def test_ui_asserts_visible_gate_and_zero_factories_before_explicit_action(self):
        self.assertLess(ui.TESTS.index('"root=0;fonts=0"'),ui.TESTS.index('action.tap()'))
        self.assertIn('app.screenshot()',ui.TESTS)
        self.assertIn('testDiagnosticRelaunchNeverAutoEntersRoot',ui.TESTS)
        self.assertIn('Synthetic root mounted',ui.HOST)


if __name__ == '__main__': unittest.main()
