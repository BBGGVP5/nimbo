"""Final iosApp presentation contracts, not Swift typecheck or rendered UI tests."""
from pathlib import Path
import hashlib
import json
import re
import sys
import unittest

IOS = Path(__file__).resolve().parents[1]
AUDIT = IOS / 'build/final-native-routes-2026-09-19'
sys.path.insert(0, str(IOS / 'build/universal-redesign/python-deps'))
from tree_sitter import Language, Parser
import tree_sitter_swift
PARSER = Parser(Language(tree_sitter_swift.language()))
CHANGED = {'Nimbo/NimboTabBar.swift', 'Nimbo/ProfilesContainerView.swift',
           'Nimbo/NimboQrScannerView.swift', 'Nimbo/RootView.swift'}

def read(name, baseline=False):
    return ((AUDIT / 'baseline') if baseline else IOS).joinpath(name).read_text(encoding='utf-8-sig')

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
    def test_immutable_baseline_and_four_file_production_scope(self):
        hashes = json.loads((AUDIT / 'baseline-sha256.json').read_text())
        for name, digest in hashes.items():
            self.assertEqual(hashlib.sha256((AUDIT / 'baseline' / name).read_bytes()).hexdigest(), digest, name)
            if name in CHANGED or name.startswith(('Tests/', 'Tools/', 'docs/')): continue
            self.assertEqual(hashlib.sha256((IOS / name).read_bytes()).hexdigest(), digest, name)
        for p in IOS.rglob('*'):
            if p.is_file():
                name = p.relative_to(IOS).as_posix()
                if not name.startswith(('build/', 'Tests/', 'Tools/', 'docs/')):
                    self.assertIn(name, hashes, name)

    def test_all_28_notifications_map_to_read_only_compose_host(self):
        root = read('Nimbo/RootView.swift')
        kotlin = (IOS.parent / 'shared/src/iosMain/kotlin/com/danila/nimbo/shared/ui/IosComposeController.kt').read_text(encoding='utf-8')
        definitions = dict(re.findall(r'static let (nimbo\w+) = Notification.Name\("([^"]+)"\)', root))
        handlers = re.findall(r'publisher\(for: \.(nimbo\w+)\)', root)
        constants = dict(re.findall(r'private const val (\w+Action) = "(com.nimbo.action.[^"]+)"', kotlin))
        self.assertEqual(len(definitions), 28)
        self.assertEqual(set(definitions), set(handlers))
        self.assertEqual(set(definitions.values()), set(constants.values()))
        posted = set(re.findall(r'postIosAction\((\w+Action)', kotlin))
        self.assertEqual(posted, set(constants))
        self.assertEqual(handlers, re.findall(r'publisher\(for: \.(nimbo\w+)\)', read('Nimbo/RootView.swift', True)))

    def test_reachable_presentations_and_dormant_exclusions(self):
        root = read('Nimbo/RootView.swift')
        sheet_bindings = set(re.findall(r'\.sheet\((?:isPresented|item): \$(\w+)', root))
        self.assertEqual(sheet_bindings, {'showReadiness','showQrScanner','showProfiles','showDiagnostics','showAbout','showSync','backupUrl','showUpdateDownload','showBackupPicker'})
        self.assertIn('.fileImporter(', root)
        self.assertIn('NimboProfileInfoView(', read('Nimbo/ProfilesContainerView.swift'))
        self.assertIn('NimboQrScannerView(purpose: .sync)', read('Nimbo/NimboSyncView.swift'))
        self.assertIn('ReadinessView().environmentObject(vpn)', read('Nimbo/DiagnosticsView.swift'))
        self.assertIn('RootView()', read('Nimbo/NimboApp.swift'))
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
                         'role: .destructive, action: removeConfiguration']:
            self.assertIn(fragment, ui)
        self.assertNotIn('Image(systemName: "trash").frame(width: 44', ui)

    def test_compact_preview_changes_no_camera_policy_or_payload(self):
        ui = read('Nimbo/NimboQrScannerView.swift')
        self.assertIn('.frame(height: verticalSizeClass == .compact ? 160 : 280)', ui)
        marker = '@MainActor\nprivate struct NimboCameraPreview'
        self.assertEqual(ui.split(marker)[1], read('Nimbo/NimboQrScannerView.swift', True).split(marker)[1])

    def test_root_readiness_close_has_explicit_hit_target(self):
        self.assertIn('Button("Готово") { showReadiness = false }.frame(minWidth: 44, minHeight: 44)', read('Nimbo/RootView.swift'))

    def test_no_functional_root_profile_or_selection_changes(self):
        for file, exceptions in [('Nimbo/RootView.swift', set()), ('Nimbo/ProfilesContainerView.swift', {'activeConfigurationCard'}), ('Nimbo/NimboTabBar.swift', set())]:
            old, new = functions(read(file, True)), functions(read(file))
            for name, body in old.items():
                current = new[name]
                if name == 'restoreBackup':
                    current = current.replace('            notify("success", "Резервная копия восстановлена")\n', '')
                    current = current.replace('            notify("error", NimboRedactor.redact(error.localizedDescription))\n', '')
                if name not in exceptions: self.assertEqual(current, body, file+'::'+name)

    def test_backup_failures_and_completion_are_visible_without_storage_changes(self):
        root = read('Nimbo/RootView.swift')
        restore = functions(root)['restoreBackup']
        self.assertIn('if backupUrl == nil { notify("error", "Не удалось подготовить резервную копию") }', root)
        self.assertIn('notify("success", "Резервная копия восстановлена")', restore)
        self.assertIn('notify("error", NimboRedactor.redact(error.localizedDescription))', restore)
        self.assertLess(restore.index('await measurePings()'), restore.index('notify("success"'))
        self.assertEqual(read('Nimbo/NimboBackup.swift'), read('Nimbo/NimboBackup.swift', True))

    def test_glass_only_on_navigation_and_opaque_fallback_retained(self):
        glass_files = {p.name for p in (IOS/'Nimbo').glob('*.swift') if '.glassEffect(' in p.read_text(encoding='utf-8-sig') or '.regularMaterial' in p.read_text(encoding='utf-8-sig')}
        self.assertEqual(glass_files, {'NimboTabBar.swift'})
        nav = read('Nimbo/NimboTabBar.swift')
        for guard in ['reduceTransparency || contrast == .increased || !refraction', '#available(iOS 26.0, *)', '.regularMaterial']:
            self.assertIn(guard, nav)

    def test_changed_sources_parse(self):
        for file in CHANGED:
            self.assertFalse(PARSER.parse(read(file).encode()).root_node.has_error, file)

if __name__ == '__main__': unittest.main()
