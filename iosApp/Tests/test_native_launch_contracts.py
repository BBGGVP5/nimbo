"""Source contracts complement Foundation tests; these do not execute SwiftUI or prove GPU presentation."""
from pathlib import Path
import plistlib
import unittest

ROOT = Path(__file__).resolve().parents[2]


def source(path):
    return (ROOT / path).read_text(encoding='utf-8-sig')


class NativeLaunchContracts(unittest.TestCase):
    def test_app_entry_has_only_native_launch_view(self):
        app = source('iosApp/Nimbo/NimboApp.swift')
        self.assertIn('WindowGroup { NimboLaunchView() }', app)
        for forbidden in ['RootView()', 'VpnController()', 'NimboNativeFonts', 'UserDefaults', '.task', 'NimboShared']:
            self.assertNotIn(forbidden, app)

    def test_lazy_root_is_in_conditional_child_and_has_its_own_vpn_owner(self):
        view = source('iosApp/Nimbo/NimboLaunchView.swift')
        self.assertIn('if let attempt = launch.rootToken', view)
        self.assertIn('NimboRootOwnerView(attempt: attempt, launch: launch)', view)
        self.assertNotIn('RootView()', view)
        owner = source('iosApp/Nimbo/NimboRootOwnerView.swift')
        self.assertIn('@StateObject private var vpnController = VpnController()', owner)
        self.assertIn('RootView()', owner)
        coordinator = source('iosApp/Nimbo/NimboLaunchCoordinator.swift')
        self.assertLess(coordinator.index('await journal.save('), coordinator.index('NimboNativeFonts.register()'))
        self.assertIn('gate.enter(attempt)', coordinator)
        self.assertLess(coordinator.index('mark(.rootBoundaryArmed'), coordinator.index('gate.enter(attempt)'))
        self.assertIn('let armed = await journal.save(record)', coordinator)

    def test_native_surface_and_observer_do_not_enter_application_services(self):
        for file in ['NimboLaunchView.swift', 'NimboLaunchAppearanceObserver.swift']:
            text = source('iosApp/Nimbo/' + file)
            for forbidden in ['UserDefaults', 'NimboShared', 'NimboConfigurationStore', 'NimboNativeFonts', '.nimboFont(']:
                self.assertNotIn(forbidden, text)
        observer = source('iosApp/Nimbo/NimboLaunchAppearanceObserver.swift')
        for token in ['viewDidAppear', 'CADisplayLink', 'DispatchQueue.main.async', 'view.window != nil']:
            self.assertIn(token, observer)

    def test_journal_uses_new_storage_and_export_has_no_existing_data_dependency(self):
        journal = source('iosApp/Nimbo/NimboLaunchJournal.swift')
        for token in ['NimboLaunchDiagnostics', '.atomic', 'maximumBytes', 'queue.async']:
            self.assertIn(token, journal)
        for forbidden in ['UserDefaults', 'NimboConfigurationStore', 'NimboDiagnostics.shared', 'Keychain', 'appGroup']:
            self.assertNotIn(forbidden, journal)
        view = source('iosApp/Nimbo/NimboLaunchView.swift')
        self.assertIn('ShareLink', view)

    def test_diagnostics_require_visible_confirmation_not_native_cycle_as_ready(self):
        coordinator = source('iosApp/Nimbo/NimboLaunchCoordinator.swift')
        self.assertIn('append(.awaitingVisualConfirmation)', coordinator)
        self.assertIn('append(.userConfirmedInterface)', coordinator)
        self.assertIn('func markFactory(', coordinator)
        factory = source('iosApp/Nimbo/ComposeScreen.swift')
        self.assertIn('coordinator.markFactory(.creatingComposeController', factory)
        view = source('iosApp/Nimbo/NimboLaunchView.swift')
        self.assertIn('Button("Интерфейс виден")', view)
        self.assertIn('launch.confirmVisibleInterface()', view)

    def test_build_flag_has_default_and_both_plist_paths(self):
        info = plistlib.loads((ROOT / 'iosApp/Nimbo/Info.plist').read_bytes())
        self.assertEqual(info['NimboLaunchDiagnostics'], '$(NIMBO_LAUNCH_DIAGNOSTICS)')
        project = source('iosApp/project.yml')
        self.assertIn("NIMBO_LAUNCH_DIAGNOSTICS: '0'", project)
        self.assertIn("NimboLaunchDiagnostics: '$(NIMBO_LAUNCH_DIAGNOSTICS)'", project)
        model = source('iosApp/Nimbo/NimboLaunchModel.swift')
        self.assertIn('value == "1"', model)


if __name__ == '__main__':
    unittest.main()
