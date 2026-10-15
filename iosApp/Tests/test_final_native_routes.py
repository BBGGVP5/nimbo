"""Final iosApp presentation contracts, not Swift typecheck or rendered UI tests."""
from pathlib import Path
import re
import sys
import unittest

IOS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(IOS / 'build/universal-redesign/python-deps'))
from tree_sitter import Language, Parser
import tree_sitter_swift
PARSER = Parser(Language(tree_sitter_swift.language()))
CHANGED = {'Nimbo/NimboTabBar.swift', 'Nimbo/ProfilesContainerView.swift',
           'Nimbo/NimboQrScannerView.swift', 'Nimbo/RootView.swift'}

def read(name):
    return IOS.joinpath(name).read_text(encoding='utf-8-sig')


def notification_routes(root, server_actions):
    definitions = dict(re.findall(r'static let (nimbo\w+) = Notification.Name\("([^"]+)"\)', root + server_actions))
    aliases = re.findall(r'publisher\(for: \.(nimbo\w+)\)', root)
    unknown = set(aliases) - set(definitions)
    if unknown:
        raise AssertionError('Notification handlers lack definitions: ' + ', '.join(sorted(unknown)))
    literal = re.findall(r'publisher\(for: Notification.Name\("([^"]+)"\)\)', root)
    return [wire for wire in [definitions[name] for name in aliases] + literal
            if wire.startswith('com.nimbo.action.')]

def functions(code):
    result = {}
    def visit(node):
        if node.type == 'function_declaration':
            name = node.child_by_field_name('name')
            if name:
                result[name.text.decode()] = node.text.decode()
        for child in node.children: visit(child)
    visit(PARSER.parse(code.encode()).root_node)
    return result

class FinalNativeRoutes(unittest.TestCase):
    def test_presentation_owners_delegate_vpn_and_storage_mutations(self):
        # A September change-scope snapshot under ignored build/ cannot constrain
        # later features. Keep its presentation-only boundary as a durable guard.
        for name in CHANGED:
            code = read(name)
            for forbidden in ['startVPNTunnel(', 'stopVPNTunnel(', 'saveToPreferences(',
                              'SecItemAdd(', 'SecItemUpdate(', 'AVCaptureSession()']:
                self.assertNotIn(forbidden, code, name)
        self.assertIn('try await vpn.selectServer(server.id)', functions(read('Nimbo/ProfilesContainerView.swift'))['select'])
        self.assertIn('await vpn.connect()', functions(read('Nimbo/RootView.swift'))['toggleVpn'])

    def test_all_named_and_literal_notifications_match_compose_actions(self):
        root = read('Nimbo/RootView.swift')
        kotlin = (IOS.parent / 'shared/src/iosMain/kotlin/com/danila/nimbo/shared/ui/IosComposeController.kt').read_text(encoding='utf-8')
        constants = dict(re.findall(r'private const val (\w+Action) = "(com.nimbo.action.[^"]+)"', kotlin))
        declarations = read('Nimbo/NimboServerActions.swift') + read('Nimbo/NimboPingNetworkMonitor.swift')
        handlers = notification_routes(root, declarations)
        posted = set(re.findall(r'postIosAction\((\w+Action)', kotlin))
        self.assertEqual(posted, set(constants))
        literal = set(re.findall(r'postIosAction\("(com.nimbo.action.[^"]+)"', kotlin))
        expected = {'toggle-vpn', 'add-profile', 'refresh-profile', 'profile-settings', 'save-app-rule',
                    'diagnostics', 'about', 'system-settings', 'open-core-settings', 'select-server',
                    'connect-fastest', 'copy-text', 'export-module', 'ping-server', 'ping-all',
                    'import-subscription', 'import-clipboard', 'import-file', 'scan-qr', 'open-url',
                    'routing', 'open-screen', 'open-update', 'check-update', 'download-update',
                    'export-backup', 'import-backup', 'open-sync', 'on-demand-settings'}
        expected = {'com.nimbo.action.' + name for name in expected}
        self.assertEqual(set(constants.values()) | literal, expected)
        self.assertEqual(set(handlers), expected)
        self.assertEqual(len(handlers), len(set(handlers)), 'Each action must be handled exactly once')

    def test_notification_extraction_resolves_separate_declarations_and_literal_routes(self):
        root = 'publisher(for: .nimboPingServer) publisher(for: Notification.Name("com.nimbo.action.on-demand-settings"))'
        declaration = 'static let nimboPingServer = Notification.Name("com.nimbo.action.ping-server")'
        self.assertEqual(notification_routes(root, declaration),
                         ['com.nimbo.action.ping-server', 'com.nimbo.action.on-demand-settings'])
        with self.assertRaisesRegex(AssertionError, 'lack definitions'):
            notification_routes(root, '')

    def test_notification_extraction_preserves_duplicate_action_handlers_for_detection(self):
        declaration = 'static let nimboScanQr = Notification.Name("com.nimbo.action.scan-qr")'
        root = 'publisher(for: .nimboScanQr) publisher(for: .nimboScanQr)'
        self.assertEqual(notification_routes(root, declaration), ['com.nimbo.action.scan-qr'] * 2)

    def test_reachable_presentations_and_dormant_exclusions(self):
        root = read('Nimbo/RootView.swift')
        sheet_bindings = set(re.findall(r'\.sheet\((?:isPresented|item): \$(\w+)', root))
        self.assertEqual(sheet_bindings, {'showQrScanner','showProfiles','showDiagnostics','showAbout','showSync',
                                        'backupUrl','showUpdateDownload','showBackupPicker','showCoreSettings','showOnDemandSettings'})
        self.assertNotIn('showReadiness', root, 'Readiness is pushed from Diagnostics, not presented over startup')
        for binding, destination in [('showCoreSettings', 'NimboCoreSettingsView()'),
                                     ('showOnDemandSettings', 'NimboOnDemandSettingsView()')]:
            sheet = root.split('.sheet(isPresented: $' + binding + ')', 1)[1].split('.sheet(', 1)[0]
            self.assertIn(destination, sheet)
        self.assertIn('.fileImporter(', root)
        self.assertIn('NimboProfileInfoView(', read('Nimbo/ProfilesContainerView.swift'))
        self.assertIn('NimboQrScannerView(purpose: .sync)', read('Nimbo/NimboSyncView.swift'))
        self.assertIn('ReadinessView().environmentObject(vpn)', read('Nimbo/DiagnosticsView.swift'))
        self.assertIn('NimboLaunchView()', read('Nimbo/NimboApp.swift'))
        self.assertIn('RootView()', read('Nimbo/NimboRootOwnerView.swift'))
        for name in ['HomeContainerView', 'SettingsContainerView', 'LegacyComposeShell', 'LiquidGlassTabShell']:
            self.assertNotIn(name + '(', root)

    def test_native_menu_handles_short_height_and_accessibility(self):
        nav = read('Nimbo/NimboTabBar.swift')
        self.assertIn(r'@Environment(\.verticalSizeClass)', nav)
        self.assertIn('dynamicTypeSize.isAccessibilitySize || verticalSizeClass == .compact', nav)
        self.assertIn('Menu {', nav)
        self.assertIn('selection = tab', nav)
        self.assertNotIn('NimboSetIosScreen', nav)

    def test_labeled_profile_tools_and_large_type_layout(self):
        ui = read('Nimbo/ProfilesContainerView.swift')
        for fragment in ['Label("Информация", systemImage: "info.circle")', 'Label("Удалить конфигурацию", systemImage: "trash")',
                         'AnyLayout(VStackLayout', 'AnyLayout(HStackLayout', 'dynamicTypeSize >= .xxxLarge', 'textScale > 1.15',
                         'showInfo = true', 'await refreshProfile()', 'confirmRemoval = true',
                         'Button("Удалить конфигурацию", role: .destructive) { Task { await removeConfiguration() } }']:
            self.assertIn(fragment, ui)
        self.assertNotIn('Image(systemName: "trash").frame(width: 44', ui)

    def test_compact_preview_delegates_camera_lifecycle_and_single_delivery(self):
        ui = read('Nimbo/NimboQrScannerView.swift')
        self.assertIn('.frame(height: verticalSizeClass == .compact ? 160 : 280)', ui)
        preview = ui.split('@MainActor\nprivate struct NimboCameraPreview', 1)[1]
        for fragment in ['UIViewControllerRepresentable', 'controller.setSceneActive(isSceneActive)', 'controller.dismantle()']:
            self.assertIn(fragment, preview)
        self.assertNotIn('AVCaptureSession()', ui)
        delivery = ui.split('case let .scanned(value):', 1)[1]
        self.assertIn('purpose == .sync && !value.hasPrefix("nimbo-sync://")', delivery)
        for retire in ['lifecycle.finish()', 'capture.stop()']:
            self.assertLess(delivery.index(retire), delivery.index('onScan(value)'))

    def test_readiness_is_reachable_in_dismissible_diagnostics_with_explicit_hit_target(self):
        diagnostics = read('Nimbo/DiagnosticsView.swift')
        self.assertIn('NavigationLink {\n                    ReadinessView().environmentObject(vpn)', diagnostics)
        self.assertIn('Button("Готово") { dismiss() }.frame(minWidth: 44, minHeight: 44)', diagnostics)
        self.assertIn('DiagnosticsView().environmentObject(vpn)', read('Nimbo/RootView.swift'))

    def test_profile_removal_is_confirmed_and_clears_vpn_before_persisted_records(self):
        ui = read('Nimbo/ProfilesContainerView.swift')
        self.assertIn('.confirmationDialog("Удалить конфигурацию?", isPresented: $confirmRemoval', ui)
        removal = functions(ui)['removeConfiguration']
        self.assertIn('guard !isWorking else { return }', removal)
        self.assertLess(removal.index('try await vpn.clearConfiguration()'), removal.index('try NimboConfigurationStore.shared.removeAll()'))
        self.assertLess(removal.index('try NimboConfigurationStore.shared.removeAll()'), removal.index('NimboSubscriptionMetaStore.clear()'))
        self.assertIn('resultMessage = NimboRedactor.redact(error.localizedDescription)', removal)
        self.assertNotIn('try?', removal, 'A failed clear must not be hidden before deleting the retained configuration')

    def test_backup_failures_and_completion_follow_successful_storage_admission(self):
        root = read('Nimbo/RootView.swift')
        restore = functions(root)['restoreBackup']
        self.assertIn('if backupUrl == nil { notify("error", "Не удалось подготовить резервную копию") }', root)
        self.assertIn('notify("success", "Резервная копия восстановлена")', restore)
        self.assertIn('notify("error", NimboRedactor.redact(error.localizedDescription))', restore)
        self.assertLess(restore.index('try await retirePingsForSubscriptionChange()'), restore.index('NimboBackup.restore(from: url)'))
        self.assertLess(restore.index('NimboBackup.restore(from: url)'), restore.index('notify("success"'))
        storage = functions(read('Nimbo/NimboBackup.swift'))['restore']
        self.assertLess(storage.index('try validate(payload)'), storage.index('importFullConfiguration(fullConfiguration)'))
        self.assertLess(storage.index('importFullConfiguration(fullConfiguration)'), storage.index('for (key, raw) in payload.settings'))
        self.assertIn('guard settingKeys.contains(key) else { continue }', storage)

    def test_glass_only_on_navigation_and_opaque_fallback_retained(self):
        glass_files = {p.name for p in (IOS/'Nimbo').glob('*.swift') if '.glassEffect(' in p.read_text(encoding='utf-8-sig') or '.regularMaterial' in p.read_text(encoding='utf-8-sig')}
        self.assertEqual(glass_files, {'NimboTabBar.swift'})
        nav = read('Nimbo/NimboTabBar.swift')
        for guard in ['reduceTransparency || contrast == .increased', '#available(iOS 26.0, *)', '.regularMaterial']:
            self.assertIn(guard, nav)
        background = nav.split('private var barBackground:', 1)[1].split('/// Wide iPad', 1)[0]
        self.assertLess(background.index('shape.fill(NimboNative.panel)'), background.index('.glassEffect('))
        self.assertNotIn('refraction', nav, 'The retired preference must not control accessibility fallback')

    def test_changed_sources_parse(self):
        for file in CHANGED:
            self.assertFalse(PARSER.parse(read(file).encode()).root_node.has_error, file)

if __name__ == '__main__': unittest.main()
