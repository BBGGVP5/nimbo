"""Integration guards; executed gate/cache/worker behavior lives in the Swift runner."""
import unittest
from test_vpn_startup_contracts import function, source


class SubscriptionRefreshContracts(unittest.TestCase):
    def test_ui_profile_snapshot_is_read_and_compacted_on_worker(self):
        apply = function('Nimbo/RootView.swift', 'applyComposeProfile')
        for forbidden in ['loadProfile(', 'loadFullConfiguration(', 'rawProfileJSON(', 'NimboConfigurationStore.shared']:
            self.assertNotIn(forbidden, apply)
        read = function('Nimbo/NimboSubscriptionRepository.swift', 'readPresentation')
        self.assertIn('workQueue.perform(lease:', read)
        self.assertIn('NimboProfilePresentationJSON.make', read)
        refresh = function('Nimbo/RootView.swift', 'refreshComposeProfile')
        self.assertIn('await NimboSubscriptionRepository.shared.readPresentation()', refresh)
        self.assertIn('profilePresentationGeneration == generation', refresh)

    def test_refresh_retires_diagnostics_before_profile_mutation(self):
        refresh = function('Nimbo/RootView.swift', 'refreshSubscription')
        self.assertLess(refresh.index('retirePingsForSubscriptionChange()'), refresh.index('NimboSubscriptionRepository.shared.refresh()'))
        retire = function('Nimbo/RootView.swift', 'retirePingsForSubscriptionChange')
        self.assertIn('previous?.cancel()', retire)
        self.assertIn('NimboDiagnosticProbe.waitUntilIdle(timeout: 3)', retire)
        self.assertIn('diagnosticStillRunning', retire)
        self.assertNotIn('await previous?.value', retire)

    def test_ping_settings_reads_do_not_hold_the_lease_lock(self):
        ping = source('Nimbo/NimboPingService.swift')
        valid = ping.split('var valid: Bool {', 1)[1].split('deinit', 1)[0]
        self.assertLess(valid.index('Settings.current'), valid.index('lock.lock()'))

    def test_notifications_have_unique_ids_and_identity_scoped_expiry(self):
        bridge = (source('Nimbo/RootView.swift'))
        notify = function('Nimbo/RootView.swift', 'notify')
        self.assertIn('toastDismissTask?.cancel()', notify)
        self.assertIn('NimboDismissIosToastIfCurrent', notify)
        self.assertNotIn('DispatchQueue.main.asyncAfter', notify)
        self.assertIn('NimboCurrentIosToastId()', notify)

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

    def test_subscription_hwid_survives_reinstall_like_the_profile(self):
        # The profile lives in Keychain and survives app deletion; a HWID kept only
        # in UserDefaults was regenerated, so the panel saw a new device and could
        # answer 404 once its device limit was reached.
        info = source('Nimbo/NimboPlatformInfo.swift')
        hwid = info.split('static let hardwareId: String = {', 1)[1].split('}()', 1)[0]
        self.assertLess(hwid.index('HardwareIdKeychain.read()'), hwid.index('UserDefaults.standard.string(forKey: defaultsKey)'))
        self.assertLess(hwid.index('UserDefaults.standard.string(forKey: defaultsKey)'), hwid.index('UUID().uuidString'))
        self.assertIn('HardwareIdKeychain.write(value)', hwid)
        self.assertIn('kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly', info)
        self.assertIn('"com.nimbo.subscription.hwid"', info)
        self.assertIn('request.setValue(NimboPlatformInfo.hardwareId, forHTTPHeaderField: "x-hwid")', info)

    def test_http_404_explains_reissued_link_or_device_limit(self):
        repo = source('Nimbo/NimboSubscriptionRepository.swift')
        message = repo.split('enum NimboSubscriptionHTTPMessage', 1)[1]
        self.assertIn('code == 404', message)
        self.assertIn('лимит устройств', message)
        self.assertIn('IOS_SUBSCRIPTION_HTTP', message)
        self.assertIn('case let .http(code): NimboSubscriptionHTTPMessage.text(code)',
                      source('Nimbo/NimboSubscriptionImporter.swift'))


if __name__ == '__main__':
    unittest.main()
