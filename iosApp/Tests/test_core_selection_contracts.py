"""Source integration checks, not a Swift compiler/runtime substitute.

Run CoreSelectionTests.swift with swiftc separately when available.
"""
from pathlib import Path
import unittest

from test_vpn_startup_contracts import PARSER, function, source

IOS = Path(__file__).resolve().parents[1]
CONTROLLER = 'Nimbo/VpnController.swift'
PROVIDER = 'PacketTunnel/PacketTunnelProvider.swift'
POLICY = 'Nimbo/NimboCoreSelection.swift'
ROOT = 'Nimbo/RootView.swift'
COMMON_UI = IOS.parent / 'shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui'
IOS_UI = IOS.parent / 'shared/src/iosMain/kotlin/com/danila/nimbo/shared/ui'


class CoreSelectionContracts(unittest.TestCase):
    def test_target_policies_are_identical_and_auto_included(self):
        self.assertEqual((IOS / POLICY).read_bytes(), (IOS / 'PacketTunnel/NimboCoreSelection.swift').read_bytes())
        project = source('project.yml')
        self.assertIn('- path: Nimbo', project)
        self.assertIn('- path: PacketTunnel', project)

    def test_strict_wire_preference_and_legacy_default(self):
        body = function(POLICY, 'decode')
        self.assertIn('guard let value else { return .auto }', body)
        self.assertIn('Self(rawValue: raw)', body)
        self.assertIn('throw NimboCoreSelectionError.unknownPreference', body)
        self.assertNotIn('?? .auto', source(POLICY))

    def test_staging_admits_before_any_ne_write_and_preserves_data(self):
        body = function(CONTROLLER, 'stageConfiguration')
        # First overload forwards JSON; inspect the Data overload directly.
        body = source(CONTROLLER).split('func stageConfiguration(data: Data)', 1)[1].split('func clearConfiguration', 1)[0]
        self.assertLess(body.index('try validateCore(data: data)'), body.index('loadOrCreateManager()'))
        self.assertIn('"configData": data', body)
        self.assertIn('NimboCorePreference.providerKey: preference.rawValue', body)
        self.assertIn('NimboCorePreference.profileEngineKey: profileEngine.rawValue', body)

    def test_full_record_has_precedence_before_profile_or_manager(self):
        body = function(CONTROLLER, 'connect')
        self.assertLess(body.index('loadFullConfiguration()'), body.index('loadProfile('))
        self.assertLess(body.index('try validateCore(data: full.sourceData)'), body.index('try await stageConfiguration'))
        self.assertLess(body.index('try validateCore(data: full.sourceData)'), body.index('try await loadOrCreateManager()'))
        validation = function(CONTROLLER, 'validateCore')
        self.assertIn('declaredEngine: full.coreId', validation)
        self.assertIn('object(forKey: NimboCorePreference.defaultsKey)', validation)
        self.assertNotIn('try?', validation)
        prepare = function(CONTROLLER, 'prepare')
        self.assertLess(prepare.index('try validateCore(data: stored)'), prepare.index('try await loadOrCreateManager()'))

    def test_quick_connect_admits_before_disconnect_and_selection(self):
        body = function(ROOT, 'connectFastest')
        admission = body.index('try vpn.validateCore(')
        self.assertLess(admission, body.index('await vpn.disconnect()'))
        self.assertLess(admission, body.index('NimboSubscriptionRepository.shared.select('))
        selection = function(ROOT, 'selectServer')
        self.assertLess(selection.index('try vpn.validateCore('), selection.index('NimboSubscriptionRepository.shared.select('))

    def test_extension_rechecks_raw_wire_values_before_network_or_core(self):
        body = function(PROVIDER, 'startTunnelInternal')
        admission = body.index('try NimboCoreAdmission.validate(')
        for effect in ['let routingOptions', 'applyNetworkSettings(', 'runCore(']:
            self.assertLess(admission, body.index(effect))
        self.assertIn('providerConfiguration?[NimboCorePreference.providerKey]', body)
        self.assertIn('providerConfiguration?[NimboCorePreference.profileEngineKey]', body)
        # Wrong types must not become absent values and silently enable Auto.
        self.assertNotIn('providerKey] as? String', body)

    def test_preference_updates_only_next_start_without_runtime_commands(self):
        body = function(CONTROLLER, 'setCorePreference')
        self.assertLess(body.index('guard preference.isAvailable'), body.index('NimboTunnelControl.manager()'))
        self.assertIn('NimboTunnelControl.manager()', body)
        self.assertIn('values[NimboCorePreference.providerKey] = preference.rawValue', body)
        self.assertLess(body.index('try await existing.saveToPreferences()'), body.index('UserDefaults.standard.set('))
        for forbidden in ['disconnect()', 'stopVPNTunnel', 'startVPNTunnel', 'sendProvider', 'loadOrCreateManager', 'isOnDemandEnabled']:
            self.assertNotIn(forbidden, body)
        for method in ['restartCore', 'restartAWG', 'handleNetworkPath', 'wake']:
            recovery = function(PROVIDER, method)
            self.assertNotIn('providerConfiguration', recovery)
            self.assertNotIn('UserDefaults', recovery)
            self.assertNotIn('NimboCorePreference', recovery)
        self.assertIn('lastCoreConfiguration', function(PROVIDER, 'restartCore'))
        self.assertIn('try awg.restart()', function(PROVIDER, 'restartAWG'))

    def test_reopening_app_observes_running_session_before_next_preference(self):
        body = function(CONTROLLER, 'prepare')
        current = body.index('switch existing.connection.status')
        self.assertLess(current, body.index('try validateCore(data: stored)'))
        running = body[current:body.index('let stored =')]
        self.assertIn('case .connected, .connecting, .reasserting, .disconnecting:', running)
        self.assertIn('synchronizeStatus()\n                    return', running)
        self.assertNotIn('saveToPreferences', running)

    def test_selector_is_reachable_on_live_settings_and_mihomo_disabled(self):
        root = source(ROOT)
        self.assertIn('.sheet(isPresented: $showCoreSettings)', root)
        self.assertIn('NimboCoreSettingsView().environmentObject(vpn)', root)
        self.assertNotIn('NimboCoreSettingsEntry', root)
        self.assertNotIn('.safeAreaInset(edge: .top', root)
        ui = source('Nimbo/NimboCoreSettingsView.swift')
        self.assertIn('\nstruct NimboCoreSettingsView: View', ui)
        self.assertNotIn('private struct NimboCoreSettingsView', ui)
        self.assertNotIn('NimboCoreSettingsEntry', ui)
        self.assertIn('@AppStorage(NimboCorePreference.defaultsKey)', ui)
        self.assertIn('Text("Ядро VPN")', ui)
        self.assertIn('.navigationTitle("Подключение")', ui)
        self.assertIn('.disabled(!core.isAvailable', ui)
        self.assertIn('Text("Недоступно")', ui)
        self.assertIn('следующем подключении', ui)
        self.assertIn('var isAvailable: Bool { self != .mihomo }', source(POLICY))

    def test_shared_connection_row_only_exists_on_settings_index(self):
        settings = (COMMON_UI / 'NimboSettingsScreen.kt').read_text(encoding='utf-8-sig')
        index = settings.split('if (selected == null) {', 1)[1].split('} else {', 1)[0]
        connection = index.split('SettingsSection("Подключение") {', 1)[1].split('SettingsSection("Приложение")', 1)[0]
        self.assertIn('actions.onOpenCoreSettings?.let { openCoreSettings ->', connection)
        self.assertIn('SettingsRow(NimboIconName.CONNECTION, "Ядро VPN",', connection)
        self.assertIn('"Выбор для следующего подключения", showDivider = true', connection)
        self.assertIn('onClick = openCoreSettings', connection)
        self.assertEqual(settings.count('"Ядро VPN"'), 1)

    def test_core_sheet_notification_bridge_and_optional_callback_match(self):
        shell = (COMMON_UI / 'NimboAppShell.kt').read_text(encoding='utf-8-sig')
        actions = shell.split('data class NimboUiActions(', 1)[1].split('\n)', 1)[0]
        # Appended, defaulted capability keeps earlier positional/named clients intact.
        self.assertTrue(actions.rstrip().endswith('val onOpenCoreSettings: (() -> Unit)? = null'))
        bridge = (IOS_UI / 'IosComposeController.kt').read_text(encoding='utf-8-sig')
        self.assertIn('onOpenCoreSettings = { postIosAction(OpenCoreSettingsAction) }', bridge)
        wire = 'com.nimbo.action.open-core-settings'
        self.assertIn('private const val OpenCoreSettingsAction = "' + wire + '"', bridge)
        root = source(ROOT)
        self.assertIn('static let nimboOpenCoreSettings = Notification.Name("' + wire + '")', root)
        self.assertIn('.onReceive(NotificationCenter.default.publisher(for: .nimboOpenCoreSettings)) { _ in\n                showCoreSettings = true', root)
        self.assertEqual(root.count('.sheet(isPresented: $showCoreSettings)'), 1)

    def test_native_documents_are_never_converted_by_admission(self):
        body = source(POLICY)
        self.assertLess(body.index('object["coreId"]'), body.index('object["outbounds"]'))
        self.assertLess(body.index('"proxy-groups"'), body.index('object["outbounds"]'))
        for forbidden in ['convertShareText', 'XrayConfigurationBuilder', 'LibXray', 'StartIOS(']:
            self.assertNotIn(forbidden, body)

    def test_changed_swift_and_portable_matrix_parse(self):
        for name in [POLICY, 'PacketTunnel/NimboCoreSelection.swift', ROOT,
                     'Nimbo/NimboCoreSettingsView.swift', 'Tests/CoreSelectionTests.swift']:
            with self.subTest(name=name):
                self.assertFalse(PARSER.parse(source(name).encode()).root_node.has_error)
        # The installed grammar already reports errors on baseline provider
        # continuations and a Bool fallback in the controller. Parse changed
        # methods individually instead of claiming these files compile.
        for name, methods in [(CONTROLLER, ['validateCore', 'setCorePreference', 'prepare', 'connect'])]:
            for method in methods:
                with self.subTest(name=name, method=method):
                    self.assertFalse(PARSER.parse(function(name, method).encode()).root_node.has_error)
        provider = function(PROVIDER, 'startTunnelInternal')
        admission = provider[provider.index('try NimboCoreAdmission.validate('):provider.index('let routingOptions')]
        self.assertFalse(PARSER.parse(('func check() throws {\n' + admission + '\n}').encode()).root_node.has_error)
        swift = source('Tests/CoreSelectionTests.swift')
        for scenario in ['compatibilityMatrix', 'unknownIDsFailClosed', 'fullDocumentIdentity',
                         'malformedAndUnsupportedInputs', 'persistentPreferenceAndSessionSnapshot']:
            self.assertIn('static func ' + scenario + '()', swift)


if __name__ == '__main__':
    unittest.main()
