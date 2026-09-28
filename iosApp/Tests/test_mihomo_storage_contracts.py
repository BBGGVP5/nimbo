"""Structural integration guards only; execute Swift scenarios on an Apple host."""
from pathlib import Path
import unittest

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

if __name__ == '__main__':
    unittest.main()
