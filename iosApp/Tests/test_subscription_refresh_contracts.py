"""Integration guards; executed gate/cache/worker behavior lives in the Swift runner."""
import unittest
from test_vpn_startup_contracts import function, source


class SubscriptionRefreshContracts(unittest.TestCase):
    def test_owned_profile_is_reloaded_and_enabled_before_start(self):
        connect = function('Nimbo/VpnController.swift', 'connect')
        self.assertLess(connect.index('manager.loadFromPreferences()'), connect.index('status != .invalid'))
        enable = connect.index('manager.isEnabled = true')
        save = connect.index('NimboOnDemandRules.persist(')
        start = connect.index('NimboVpnSystemCommands.start(')
        self.assertLess(enable, save)
        self.assertLess(save, connect.index('guard manager.isEnabled else'))
        self.assertLess(connect.index('guard manager.isEnabled else'), start)

    def test_remote_parse_and_commit_use_background_worker(self):
        for method in ['importRemoteImpl', 'refreshFullConfigurationImpl', 'migrateStoredProfileIfNeeded']:
            body = function('Nimbo/NimboSubscriptionRepository.swift', method)
            self.assertIn('workQueue.perform(lease:', body)
        remote = function('Nimbo/NimboSubscriptionRepository.swift', 'importRemoteImpl')
        self.assertLess(remote.index('workQueue.perform(lease:'), remote.index('self.importPayload('))
        repo = source('Nimbo/NimboSubscriptionRepository.swift')
        self.assertNotIn('private let decoder = JSONDecoder()', repo)
        self.assertIn('guard lease?.isActive != false', repo)
        self.assertIn('expected: previous', repo)
        importer = source('Nimbo/NimboSubscriptionImporter.swift')
        self.assertIn('try await NimboSubscriptionRepository.shared.importPayloadAsync(', importer)

    def test_cached_restore_has_no_network_requirement_or_hidden_refresh(self):
        migrate = function('Nimbo/NimboSubscriptionRepository.swift', 'migrateStoredProfileIfNeeded')
        for forbidden in ['await refresh()', 'data(for:', 'importRemote(', 'refreshFullConfiguration(']:
            self.assertNotIn(forbidden, migrate)
        self.assertIn('title: profile.title', migrate)
        store = source('Nimbo/NimboConfigurationStore.swift')
        self.assertIn('selected-server-id-v1', store)
        self.assertIn('try delete(account: selectedServerAccount)', store)
        self.assertIn('NimboSelectedServerRecovery.exactID', source('Nimbo/NimboSubscriptionRepository.swift'))
        launch = function('Nimbo/RootView.swift', 'loadSubscriptionMetaIfNeeded')
        self.assertIn('refreshOnLaunch', launch)
        self.assertNotIn('updatedAt == 0', launch)
        toggle = function('Nimbo/RootView.swift', 'toggleVpn')
        self.assertIn('!isRefreshingSubscription', toggle)
        self.assertIn('!NimboSubscriptionRepository.shared.isWorking', toggle)


if __name__ == '__main__':
    unittest.main()
