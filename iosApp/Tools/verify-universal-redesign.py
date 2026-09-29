"""Source audit only; deliberately does not claim Swift typechecking or device tests.

Run from any directory with Python 3.11. Parser dependencies are optional but are
required for the recorded syntax audit:
python -m pip install --target iosApp/build/universal-redesign/python-deps tree-sitter==0.26.0 tree-sitter-swift==0.7.3
python iosApp/Tools/verify-universal-redesign.py
"""
import hashlib
import json
import plistlib
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

IOS = Path(__file__).resolve().parents[1]
AUDIT = IOS / 'build/universal-redesign'
BASELINE = AUDIT / 'baseline'
sys.path.insert(0, str(AUDIT / 'python-deps'))
from tree_sitter import Language, Parser
import tree_sitter_swift

parser = Parser(Language(tree_sitter_swift.language()))
checks = []

def check(name, passed, detail=''):
    checks.append(dict(name=name, passed=bool(passed), detail=detail))

def read(name):
    return (IOS / name).read_text(encoding='utf-8-sig')

def walk(node):
    yield node
    for child in node.children:
        yield from walk(child)

def parse(path):
    return parser.parse(path.read_bytes())

def functions(path):
    return {n.child_by_field_name('name').text.decode(): n.text.decode().replace('\r\n', '\n')
            for n in walk(parse(path).root_node)
            if n.type == 'function_declaration' and n.child_by_field_name('name')}

baseline = json.loads((AUDIT / 'baseline-sha256.json').read_text(encoding='utf-8-sig'))
changed = []
baseline_names = {entry['path'] for entry in baseline}
for entry in baseline:
    path = IOS / entry['path']
    digest = hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None
    if digest != entry['sha256'].lower():
        changed.append(entry['path'])
for path in (IOS / 'Nimbo').rglob('*'):
    if path.is_file() and path.relative_to(IOS).as_posix() not in baseline_names:
        changed.append(path.relative_to(IOS).as_posix())

parser_limitations = []
for name in sorted(set(changed)):
    if not name.endswith('.swift'):
        continue
    def errors(path):
        if not path.exists(): return []
        return [n.text.decode().replace('\r\n', '\n') for n in walk(parse(path).root_node)
                if n.type == 'ERROR' or n.is_missing]
    current_errors = errors(IOS / name)
    old_errors = errors(BASELINE / name)
    new_errors = [e for e in current_errors if e not in old_errors]
    check('Swift grammar: ' + name, not new_errors, new_errors)
    if current_errors:
        parser_limitations.append(dict(file=name, inheritedErrors=current_errors))

# Functional methods must remain byte-equivalent after newline normalization.
for file, exceptions in [('Nimbo/RootView.swift', {'downloadUpdate'}),
                         ('Nimbo/ProfilesContainerView.swift', {'activeConfigurationCard', 'serverList'}),
                         ('Nimbo/ReadinessView.swift', set()),
                         ('Nimbo/VpnController.swift', {'connect', 'disconnect', 'synchronizeStatus',
                          'scheduleStatusPollIfNeeded', 'reportUnexpectedDisconnect',
                          'applyDisconnectError', 'errorPresentation'})]:
    old, new = functions(BASELINE / file), functions(IOS / file)
    for name, source in old.items():
        if name not in exceptions and name != 'nimboCard':
            current = new.get(name)
            # Final reachable-UI audit adds only existing toast-channel feedback
            # for actual backup completion/failure. No storage/restore exemption.
            if file == 'Nimbo/RootView.swift' and name == 'restoreBackup' and current:
                current = current.replace('            notify("success", "Резервная копия восстановлена")\n', '')
                current = current.replace('            notify("error", NimboRedactor.redact(error.localizedDescription))\n', '')
            check(f'Preserved function (backup UI feedback excluded): {file}::{name}' if name == 'restoreBackup'
                  else f'Preserved function: {file}::{name}', current == source)

# Network, tunnel, subscription parser/storage, update service, sync protocol,
# signing and Control Center actions are outside this presentation change.
widget_file = 'ControlWidget/NimboControlWidget.swift'
protected = [e['path'] for e in baseline if e['path'].startswith(('PacketTunnel/', 'Shared/', 'ControlWidget/', 'Branding/')) and e['path'] != widget_file]
protected += ['Nimbo/Info.plist', 'Nimbo/ComposeScreen.swift',
              'Nimbo/NimboUpdateChecker.swift', 'Nimbo/NimboUpdateCenter.swift',
              'Nimbo/NimboSyncEngine.swift', 'Nimbo/NimboSyncProtocol.swift',
              'Nimbo/NimboSubscriptionRepository.swift', 'Nimbo/NimboSubscriptionMeta.swift',
              'Nimbo/NimboSubscriptionImporter.swift', 'Nimbo/NimboConfigurationStore.swift',
              'Nimbo/NimboPingService.swift', 'Nimbo/NimboBackup.swift']
check('Protected implementation files unchanged', not (set(protected) & set(changed)))
widget = read(widget_file)
old_widget = (BASELINE / widget_file).read_text(encoding='utf-8-sig')
intent_marker = 'struct NimboToggleTunnelIntent: SetValueIntent'
check('Control Center complete VPN intent unchanged', widget[widget.index(intent_marker):] == old_widget[old_widget.index(intent_marker):])
check('Control Center reads actual VPN status and retains transitional off action',
      'func currentValue() async throws -> NEVPNStatus' in widget
      and 'return manager.connection.status' in widget
      and 'isOn: status == .connected || status == .connecting || status == .reasserting' in widget
      and 'var previewValue: NEVPNStatus { .disconnected }' in widget)
check('Control Center cloud only for actual connection', bool(re.search(r'if status == \.connected\s*\{\s*Image\("NimboCloudSymbol"\)\s*\} else \{\s*Image\(systemName: "power"\)', widget)))
native_home = read('Nimbo/HomeContainerView.swift')
check('Native Home cloud only for actual connection', bool(re.search(r'if vpn.state == \.connected\s*\{\s*Image\("NimboCloud"\).*?\} else \{\s*Image\(systemName: "power"\)', native_home, re.DOTALL)))
check('Native Home action and busy gating preserved',
      bool(re.search(r'if vpn.state == \.connected \|\| vpn.state == \.connecting\s*\{\s*await vpn.disconnect\(\)\s*\} else \{\s*await vpn.connect\(\)', native_home))
      and '.disabled(vpn.state == .preparing || vpn.state == .disconnecting)' in native_home
      and 'busy: vpn.state == .preparing || vpn.state == .connecting || vpn.state == .disconnecting' in native_home)

root = read('Nimbo/RootView.swift')
old_root = (BASELINE / 'Nimbo/RootView.swift').read_text(encoding='utf-8-sig')
notifications = lambda source: re.findall(r'publisher\(for: (\.nimbo\w+)\)', source)
check('All existing native notification handlers retained', notifications(root) == notifications(old_root))
check('Native panel reserves dynamic bottom safe area', '.safeAreaInset(edge: .bottom, spacing: 0)' in root)
check('Bridge still creates shared Compose home', 'ComposeScreen(tab: .home)' in root)
nav = read('Nimbo/NimboTabBar.swift')
check('Navigation sends bridge selection once via RootView', 'NimboSetIosScreen' not in nav and root.count('NimboSetIosScreen') == old_root.count('NimboSetIosScreen'))
check('Native glass availability and accessibility fallback', all(x in nav for x in ['#available(iOS 26.0, *)', 'reduceTransparency || contrast == .increased || !refraction', '.regularMaterial']))
check('Large text has an expanded navigation layout', 'LazyVGrid' in nav and 'dynamicTypeSize >= .xxxLarge' in nav and 'minimumScaleFactor' not in nav and 'lineLimit' not in nav)
check('No ornamental navigation timers', 'asyncAfter' not in nav and 'Timer' not in nav)
check('App scale supplements Dynamic Type', '@ScaledMetric' in read('Nimbo/NimboNativeDesign.swift') and 'com.nimbo.appearance.textScale' in read('Nimbo/NimboNativeDesign.swift'))
check('Routing is a primary tab; stats remains addressable', '[.home, .profiles, .routing, .settings]' in read('Nimbo/NimboTab.swift') and 'case stats' in read('Nimbo/NimboTab.swift'))
profiles = read('Nimbo/ProfilesContainerView.swift')
check('Subscription accordion, independent information and refresh', all(x in profiles for x in ['serversExpanded.toggle()', 'showInfo = true', 'await refreshProfile()', 'select(server)']))
scanner = read('Nimbo/NimboQrScannerView.swift')
check('QR permission, teardown and subscription callback', all(x in scanner for x in ['requestAccess(for: .video)', 'dismantleUIViewController', 'purpose == .sync &&', 'onScan(value)', 'preview?.frame = view.bounds']))
check('Sync direction wire values preserved', all(x in read('Nimbo/NimboSyncView.swift') for x in ['desktop_to_android', 'android_to_desktop', 'NimboQrScannerView(purpose: .sync)']))
check('Download uses real async operation and reentry guard', all(x in root for x in ['guard !isDownloadingUpdate else', 'try await NimboUpdateCenter.download(release)', 'defer { isDownloadingUpdate = false }']))
project = read('project.yml')
# Do not rebaseline or broadly exempt project.yml: allow only this exact camera
# purpose correction while retaining all version, signing and target settings.
camera_description = 'Камера нужна для сканирования QR-кодов подписок и синхронизации устройств.'
camera_block = '        NSCameraUsageDescription: >-\n          ' + camera_description + '\n'
camera_pattern = r'^        NSCameraUsageDescription: >-\n(?:          [^\n]*\n)+'
old_project = (BASELINE / 'project.yml').read_text(encoding='utf-8-sig')
expected_project, replacements = re.subn(camera_pattern, lambda _: camera_block, old_project, flags=re.MULTILINE)
check('project.yml baseline exception limited to camera purpose', replacements == 1 and project == expected_project)
check('Camera purpose describes subscription and sync QR', re.findall(camera_pattern, project, flags=re.MULTILINE) == [camera_block])
info_plist = plistlib.loads((IOS / 'Nimbo/Info.plist').read_bytes())
check('Info.plist camera literal agrees when present', 'NSCameraUsageDescription' not in info_plist or info_plist['NSCameraUsageDescription'] == camera_description,
      'No camera literal in checked-in plist; project.yml provides it during project generation.' if 'NSCameraUsageDescription' not in info_plist else '')
check('Version unchanged: 1.3.0-beta.1 / 180', 'CURRENT_PROJECT_VERSION: 180' in project and 'NIMBO_DISPLAY_VERSION: 1.3.0-beta.1' in project)
check('Approved cloud exact source match', (IOS / 'Branding/Branding.xcassets/NimboCloud.imageset/cloud-template.svg').read_bytes() == (IOS.parent / 'assets/branding/1.3.0-beta.1/cloud-template.svg').read_bytes())
widget_target = project.split('  NimboControlWidget:\n', 1)[1].split('\nschemes:', 1)[0]
check('Control widget target includes brand catalog and is embedded/registered',
      'resources:\n      - path: Branding/Branding.xcassets' in widget_target
      and '- target: NimboControlWidget\n        embed: true' in project
      and 'NimboControlWidget()' in read('ControlWidget/NimboControlWidgetBundle.swift'))
symbol_dir = IOS / 'Branding/Branding.xcassets/NimboCloudSymbol.symbolset'
symbol_manifest = json.loads((symbol_dir / 'Contents.json').read_text(encoding='utf-8-sig'))
check('Control Center custom symbol manifest resolves', symbol_manifest.get('symbols') == [{'filename': 'NimboCloudSymbol.svg', 'idiom': 'universal'}] and (symbol_dir / 'NimboCloudSymbol.svg').is_file())
svg_ns = {'svg': 'http://www.w3.org/2000/svg'}
cloud_svg = ET.parse(IOS.parent / 'assets/branding/1.3.0-beta.1/cloud-template.svg')
cloud_outline = cloud_svg.find('svg:path', svg_ns).get('d')
symbol_svg = ET.parse(symbol_dir / 'NimboCloudSymbol.svg')
symbol_paths = symbol_svg.findall('.//svg:path', svg_ns)
check('All 27 custom symbol variants use approved cloud outline', len(symbol_paths) == 27 and all(p.get('d') == cloud_outline for p in symbol_paths))
widget_icons = re.findall(r'Image\((?:systemName: )?"([^"]+)"\)', widget)
check('Control Center has only cloud/power icons, no legacy fallback', widget_icons == ['NimboCloudSymbol', 'power'] and 'action: NimboToggleTunnelIntent()' in widget)
engine_stages = re.search(r'enum Stage: Equatable \{(.*?)\n    \}', read('Nimbo/NimboSyncEngine.swift'), re.DOTALL).group(1)
declared_stages = set(re.findall(r'case (\w+)', engine_stages))
sync_ui = read('Nimbo/NimboSyncView.swift')
stage_card = sync_ui.split('@ViewBuilder private var stageCard: some View {', 1)[1].split('private var directionButtons:', 1)[0]
rendered_stages = set(re.findall(r'case (?:let )?\.(\w+)', stage_card))
check('Native sync renders all seven real engine stages', len(declared_stages) == 7 and rendered_stages == declared_stages, sorted(rendered_stages))
check('Native sync QR/manual entry and both actual transfer actions retained', all(s in sync_ui for s in [
    'NimboQrScannerView(purpose: .sync)', 'engine.start(link: scanned)', 'engine.start(link: manualLink)',
    'engine.commit(direction: "desktop_to_android")', 'engine.commit(direction: "android_to_desktop")',
    'code.map', 'detail: summary', 'detail: reason', '.nimboSheetStyle()']))
json.loads(read('Nimbo/Assets.xcassets/LaunchBackground.colorset/Contents.json'))
check('Launch appearance JSON parses', True)

result = dict(scope='iOS native source audit; no Apple compilation or rendered/device verification',
              passed=all(c['passed'] for c in checks), checks=checks,
              inheritedParserLimitations=parser_limitations,
              changedProductionFiles=sorted(set(changed)))
(AUDIT / 'source-verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"{sum(c['passed'] for c in checks)}/{len(checks)} source checks passed")
for c in checks:
    if not c['passed']: print('FAIL:', c['name'], c['detail'])
print('Inherited parser limitations:', len(parser_limitations))
print('Changed production files:', len(set(changed)))
sys.exit(0 if result['passed'] else 1)
