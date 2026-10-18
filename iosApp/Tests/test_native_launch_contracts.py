"""Source contracts complement Foundation tests; these do not execute SwiftUI or prove GPU presentation."""
from pathlib import Path
import plistlib
import unittest

ROOT = Path(__file__).resolve().parents[2]


def source(path):
    return (ROOT / path).read_text(encoding='utf-8-sig')


class NativeLaunchContracts(unittest.TestCase):
    def test_bootstrap_marks_log_boundary_before_any_await(self):
        owner = source('iosApp/Nimbo/NimboRootOwnerView.swift')
        task = owner.split('.task {', 1)[1]
        self.assertLess(task.index('guard launch.claimBootstrap(attempt)'),
                        task.index('launch.mark(.recordingAppStart, attempt: attempt)'))
        self.assertLess(task.index('launch.mark(.recordingAppStart, attempt: attempt)'),
                        task.index('await NimboDiagnostics.shared.record'))
        self.assertLess(task.index('await NimboDiagnostics.shared.record'),
                        task.index('launch.mark(.migratingProfile, attempt: attempt)'))
        self.assertIn('case recordingAppStart = "RECORDING_APP_START"',
                      source('iosApp/Nimbo/NimboLaunchModel.swift'))

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

    def test_vpn_state_replay_cannot_rerender_root_forever(self):
        # Build 183 stopped right after RECORDING_APP_START with the main thread
        # blocked; build 181's watchdog sample was a CPU-bound SwiftUI graph loop.
        # A per-render publisher replaying the same state must not mutate State.
        root = source('iosApp/Nimbo/RootView.swift')
        self.assertNotIn('vpn.$state.removeDuplicates()', root)
        handler = root.split('.onReceive(vpn.$state)', 1)[1]
        self.assertLess(handler.index('guard state != handledVpnState else'),
                        handler.index('handledVpnState = state'))
        self.assertLess(handler.index('handledVpnState = state'), handler.index('handleVpnState(state)'))
        self.assertLess(handler.index('markLaunchOnce(.vpnStateReplayIgnored)'), handler.index('handleVpnState(state)'))

    def test_normal_build_hides_launch_overlay_after_completed_root(self):
        view = source('iosApp/Nimbo/NimboLaunchView.swift')
        self.assertIn('launch.exposeRoot && (launch.gate.diagnostics || (launch.hasFailure && !launch.gate.completed))', view)
        self.assertNotIn('(launch.gate.diagnostics || launch.hasFailure)', view)

    def test_root_first_frame_breadcrumbs_are_recorded_once(self):
        model = source('iosApp/Nimbo/NimboLaunchModel.swift')
        for phase in ['ROOT_VIEW_APPEARED', 'ROOT_KOTLIN_BRIDGES_SYNCED',
                      'FIRST_VPN_STATE_HANDLED', 'VPN_STATE_REPLAY_IGNORED']:
            self.assertIn('"' + phase + '"', model)
        coordinator = source('iosApp/Nimbo/NimboLaunchCoordinator.swift')
        once = coordinator.split('func markOnce(', 1)[1].split('\n    }', 1)[0]
        self.assertLess(once.index('record.events.contains'), once.index('mark(next, attempt: attempt)'))
        root = source('iosApp/Nimbo/RootView.swift')
        self.assertIn('launchContext.coordinator.markOnce(phase, attempt: launchContext.attempt)', root)
        self.assertNotIn('coordinator.mark(', root)


if __name__ == '__main__':
    unittest.main()
