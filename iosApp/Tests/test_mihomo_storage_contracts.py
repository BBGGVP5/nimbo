"""Structural integration guards only; execute Swift scenarios on an Apple host."""
from pathlib import Path
import unittest
import argparse
import platform
import subprocess
import tempfile

IOS = Path(__file__).resolve().parents[1]

def read(name):
    path = IOS / name
    return path.read_text(encoding='utf-8-sig') if path.exists() else ''

class MihomoStorageContracts(unittest.TestCase):
    def test_record_is_lossless_and_validated(self):
        code = read('Nimbo/NimboFullConfiguration.swift')
        for fragment in ['originalYAML', 'sourceSHA256', 'SHA256.hash', 'func validate()',
                         'schemaVersion == 1', 'coreId == "mihomo"', 'format == "mihomo-yaml"']:
            self.assertIn(fragment, code)
        self.assertNotIn('originalYAML.trimmingCharacters', code)

    def test_single_keychain_record_and_selection_guard(self):
        code = read('Nimbo/NimboConfigurationStore.swift')
        for fragment in ['full-configuration-v1', 'func saveFullConfiguration', 'func loadFullConfiguration',
                         'try configuration.validate()', 'NimboFullConfigurationError.fullConfigurationActive']:
            self.assertIn(fragment, code)
        self.assertIn('try delete(account: fullConfigurationAccount)', code)

    def test_importer_does_not_trim_payload(self):
        code = read('Nimbo/NimboSubscriptionImporter.swift')
        self.assertIn('rawSource.data(using: .utf8)', code)
        self.assertIn('resolve(source)', code)
        self.assertNotIn('resolve(trimmed)', code)
        ui = read('Nimbo/ProfilesContainerView.swift')
        self.assertIn('let source = importText\n', ui)

    def test_full_profile_never_uses_legacy_migration_or_server_selection(self):
        code = read('Nimbo/NimboSubscriptionRepository.swift')
        self.assertIn('func importFullConfiguration', code)
        self.assertIn('func refreshFullConfiguration', code)
        self.assertIn('fullConfigurationActive', code)
        self.assertIn('fullConfigurationProfile', code)

    def test_backup_validates_before_settings_and_does_not_refetch_full_source(self):
        code = read('Nimbo/NimboBackup.swift')
        self.assertIn('let fullConfiguration: NimboFullConfiguration?', code)
        self.assertIn('try fullConfiguration.validate()', code)
        self.assertLess(code.index('try fullConfiguration.validate()'), code.index('for (key, raw) in payload.settings'))
        self.assertIn('return payload.fullConfiguration == nil ? payload.source : nil', code)

    def test_swift_scenarios_exist_not_claimed_executed(self):
        code = read('Tests/FullConfigurationTests.swift')
        for test in ['exactRoundTrip', 'tamperedHash', 'unsupportedVersion', 'choicesBoundToSource', 'invalidUTF8', 'sizeLimit']:
            self.assertIn(test, code)

    def test_refresh_commits_against_complete_previous_record(self):
        code = read('Nimbo/NimboSubscriptionRepository.swift')
        self.assertIn('expected: previous', code)
        self.assertIn('subscriptionRequest(url: url, core: .mihomo)', code)
        store = read('Nimbo/NimboConfigurationStore.swift')
        self.assertIn('loadFullConfiguration() == expected', store)
        self.assertIn('NSRecursiveLock', store)

    def test_backup_is_bounded_versioned_and_fail_closed(self):
        code = read('Nimbo/NimboBackup.swift')
        self.assertIn('InputStream(url: url)', code)
        self.assertIn('maximumArchiveBytes', code)
        self.assertIn('payload.version == 1 || payload.version == version', code)
        self.assertIn('importFullConfiguration(fullConfiguration)', code)
        self.assertIn('restoreLegacyProfile', code)
        self.assertIn('"com.nimbo.connection.vpnCore"', code)
        self.assertIn('"com.nimbo.routing.adBlocking"', code)
        restore = code.split('static func restore(', 1)[1]
        self.assertLess(restore.index('importFullConfiguration(fullConfiguration)'), restore.index('for (key, raw) in payload.settings'))
        root = read('Nimbo/RootView.swift').split('private func restoreBackup(', 1)[1].split('/// Проверка обновлений', 1)[0]
        self.assertLess(root.index('vpn.state'), root.index('NimboBackup.restore'))

    def test_apple_workflows_execute_swift_storage_scenarios(self):
        for name in ['build-ios-unsigned.yml', 'ci.yml']:
            code = (IOS.parent / '.github/workflows' / name).read_text(encoding='utf-8')
            self.assertIn('test_mihomo_storage_contracts.py --swift', code)


def run_swift():
    if platform.system() != 'Darwin':
        raise SystemExit('--swift requires an Apple host; Swift tests were not run on Windows.')
    files = ['Nimbo/NimboFullConfiguration.swift', 'Nimbo/NimboBackup.swift', 'Nimbo/NimboSubscriptionMeta.swift',
             'Tests/BackupStorageStubs.swift', 'Tests/FullConfigurationTests.swift']
    with tempfile.TemporaryDirectory(prefix='nimbo-full-storage-') as folder:
        executable = str(Path(folder) / 'FullConfigurationTests')
        subprocess.run(['xcrun', 'swiftc', '-swift-version', '5', '-parse-as-library',
                        *[str(IOS / file) for file in files], '-o', executable], check=True, timeout=180)
        subprocess.run([executable], check=True, timeout=45)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--swift', action='store_true')
    args, remaining = parser.parse_known_args()
    result = unittest.main(argv=[__file__, *remaining], exit=False)
    if not result.result.wasSuccessful():
        raise SystemExit(1)
    if args.swift:
        run_swift()
