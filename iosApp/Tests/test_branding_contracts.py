"""Branding/version contracts. --mac additionally compiles assets and channel policy."""
from pathlib import Path
import argparse
import plistlib
import re
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

IOS = Path(__file__).resolve().parents[1]


def read(name):
    return (IOS / name).read_text(encoding='utf-8')


class BrandingContracts(unittest.TestCase):
    def test_all_target_versions_inherit_release(self):
        project = read('project.yml')
        self.assertEqual(re.findall(r'CURRENT_PROJECT_VERSION: (.+)', project), ['180'])
        self.assertEqual(re.findall(r'MARKETING_VERSION: (.+)', project), ['1.3.0'])
        self.assertEqual(re.findall(r'NIMBO_DISPLAY_VERSION: (.+)', project), ['1.3.0-beta.1'])
        for folder in ['Nimbo', 'PacketTunnel', 'ControlWidget']:
            plist = plistlib.loads((IOS / folder / 'Info.plist').read_bytes())
            self.assertEqual(plist['CFBundleVersion'], '$(CURRENT_PROJECT_VERSION)')
            self.assertEqual(plist['CFBundleShortVersionString'], '$(MARKETING_VERSION)')
        self.assertIn('NimboDisplayVersion: "$(NIMBO_DISPLAY_VERSION)"', project)
        plist = plistlib.loads((IOS / 'Nimbo/Info.plist').read_bytes())
        self.assertEqual(plist['NimboDisplayVersion'], '$(NIMBO_DISPLAY_VERSION)')

    def test_shared_catalog_in_app_and_widget_not_tunnel(self):
        targets = read('project.yml').split('targets:', 1)[1].split('\nschemes:', 1)[0]
        app, rest = targets.split('\n  NimboPacketTunnel:', 1)
        tunnel, widget = rest.split('\n  NimboControlWidget:', 1)
        for target in [app, widget]:
            self.assertIn('resources:', target)
            self.assertIn('- path: Branding/Branding.xcassets', target)
        self.assertNotIn('Branding.xcassets', tunnel)

    def test_control_uses_custom_symbol_preserves_behavior(self):
        control = read('ControlWidget/NimboControlWidget.swift')
        self.assertIn('Image("NimboCloudSymbol")', control)
        self.assertNotIn('Image("NimboCloud")', control)
        self.assertNotIn('systemImage:', control)
        self.assertIn('Text(isOn ? "Подключено" : "Отключено")', control)
        self.assertIn('try await NimboTunnelControl.setEnabled(value)', control)
        symbol = ET.fromstring(read('Branding/Branding.xcassets/NimboCloudSymbol.symbolset/NimboCloudSymbol.svg'))
        ids = {element.get('id') for element in symbol.iter()}
        self.assertTrue({'Symbols', 'Guides', 'Regular-M', 'Baseline-M', 'Capline-M'} <= ids)

    def test_supported_shortcuts_and_native_branding(self):
        shortcuts = read('Nimbo/NimboAppIntents.swift')
        self.assertEqual(shortcuts.count('AppShortcut(intent:'), 3)
        self.assertNotIn('systemImageName: "NimboCloud', shortcuts)
        for glyph in ['power', 'stop.circle', 'network']:
            self.assertIn(f'systemImageName: "{glyph}"', shortcuts)
        self.assertIn('Apple DTS thread/758159', shortcuts)
        self.assertIn('Image("NimboCloud")', read('Nimbo/HomeContainerView.swift'))
        self.assertIn('Image("NimboCloudSymbol")', read('Nimbo/AboutView.swift'))

    def test_new_installs_beta_existing_choices_preserved(self):
        checker = read('Nimbo/NimboUpdateChecker.swift')
        self.assertIn('NimboUpdateChannel(rawValue: stored ?? "") ?? .beta', checker)
        self.assertIn('channel: NimboUpdateChannel = NimboUpdateCenter.channel', checker)
        center = read('Nimbo/NimboUpdateCenter.swift')
        self.assertIn('NimboUpdateChannel(stored: UserDefaults.standard.string(forKey: prefix + "channel"))', center)
        self.assertNotIn('defaults.set("beta"', center)
        self.assertNotIn('UserDefaults.standard.set("beta"', checker)


def mac_checks():
    if not shutil.which('xcrun'):
        raise SystemExit('Mac with Xcode required for --mac (not run on this host)')
    with tempfile.TemporaryDirectory(prefix='.branding-', dir=IOS / 'Tests') as name:
        work = Path(name).resolve()
        assert work.is_relative_to(IOS / 'Tests')
        for target, minimum in [('app', '16.0'), ('widget', '18.0')]:
            out = work / target
            out.mkdir()
            command = ['xcrun', 'actool', str(IOS / 'Branding/Branding.xcassets')]
            if target == 'app':
                command += [str(IOS / 'Nimbo/Assets.xcassets'), '--app-icon', 'AppIcon',
                            '--output-partial-info-plist', str(out / 'asset-info.plist')]
            command += ['--compile', str(out), '--platform', 'iphonesimulator', '--minimum-deployment-target', minimum,
                        '--target-device', 'iphone', '--target-device', 'ipad', '--warnings', '--errors']
            subprocess.run(command, check=True)
            assert (out / 'Assets.car').exists()
        source = read('Nimbo/NimboUpdateChecker.swift')
        enum = source[source.index('enum NimboUpdateChannel:'):source.index('/// Чем закончилась')]
        test = work / 'ChannelTests.swift'
        test.write_text(enum + '''
@main enum ChannelTests {
    static func main() {
        precondition(NimboUpdateChannel(stored: nil) == .beta)
        precondition(NimboUpdateChannel(stored: "") == .beta)
        precondition(NimboUpdateChannel(stored: "stable") == .stable)
        precondition(NimboUpdateChannel(stored: "beta") == .beta)
        print("PASS: actual Swift new-install and saved-channel policy")
    }
}
''', encoding='utf-8')
        binary = work / 'channel-tests'
        subprocess.run(['xcrun', 'swiftc', '-parse-as-library', str(test), '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mac', action='store_true')
    args = parser.parse_args()
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(BrandingContracts))
    if not result.wasSuccessful():
        raise SystemExit(1)
    if args.mac:
        mac_checks()
    else:
        print('Mac actool/Swift/device checks not run; use --mac on a Mac with Xcode')
