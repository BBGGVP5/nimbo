import Foundation

@main
struct ConfigurationStoreLaunchTests {
    static func main() throws {
        let store = NimboConfigurationStore.shared
        try store.removeAll()
        let defaults = LaunchTestDefaults.standard
        defaults.set("old-node", forKey: "nimbo.active.server.id")
        defaults.set("old title", forKey: "nimbo.active.configuration.description")

        // Model the crash's synchronous observer waiting for a store read.
        // A held record lock makes that observer time out instead of hanging
        // the test process forever. No native Keychain/UI/VPN is contacted.
        var blockedObserver = false
        defaults.onMutation = {
            let completed = DispatchSemaphore(value: 0)
            DispatchQueue.global().async {
                _ = try? store.loadFullConfiguration()
                completed.signal()
            }
            if completed.wait(timeout: .now() + 0.5) != .success { blockedObserver = true }
        }
        try store.save(profile: Data("profile".utf8), selectedServer: Data("server".utf8),
                       selectedServerID: "new-node", source: "https://example.invalid/sub", description: "new title")
        guard !blockedObserver else { throw Failure.observerWaitedForRecordLock }
        guard store.activeServerID == "new-node", store.displayDescription == "new title" else {
            throw Failure.keychainDidNotWin
        }
        launchForcedReadFailureAccount = "selected-server-id-v1"
        guard store.activeServerID == nil else { throw Failure.staleFallbackAfterReadError }
        launchForcedReadFailureAccount = nil
        let descriptionQuery: [String: Any] = [kSecAttrAccount: "active-description-v1"]
        precondition(SecItemUpdate(descriptionQuery as CFDictionary,
                                  [kSecValueData: Data([0xff])] as CFDictionary) == errSecSuccess)
        guard store.displayDescription == nil else { throw Failure.staleFallbackAfterReadError }
        try store.saveSelection(configuration: Data("other".utf8), serverID: "other-node")
        try store.retainSelectedServerID("restored-node")
        guard store.activeServerID == "restored-node", !blockedObserver else { throw Failure.keychainDidNotWin }
        let full = NimboFullConfiguration(title: "full", source: nil, sourceData: Data("yaml".utf8))
        try store.saveFullConfiguration(full)
        guard store.activeServerID == nil, store.displayDescription == "full" else { throw Failure.fullRecordLost }
        launchForcedReadFailureAccount = "full-configuration-v1"
        guard store.activeServerID == nil, store.displayDescription == nil else { throw Failure.staleFallbackAfterReadError }
        launchForcedReadFailureAccount = nil
        try store.removeAll()
        guard !blockedObserver, store.activeServerID == nil, store.displayDescription == nil else {
            throw Failure.clearPublishedUnderLock
        }
        print("ConfigurationStore: observer lock boundary, Keychain precedence, full-record isolation and removal passed")
    }
    enum Failure: Error {
        case observerWaitedForRecordLock, keychainDidNotWin, fullRecordLost, clearPublishedUnderLock, staleFallbackAfterReadError
    }
}
