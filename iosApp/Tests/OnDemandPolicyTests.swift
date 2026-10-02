import Foundation

@main struct OnDemandPolicyTests {
    static func main() throws {
        let defaults = NimboOnDemandSettings()
        precondition(!defaults.enabled && defaults.rulePlan.isEmpty)
        var value = defaults
        value.enabled = true
        value.trustedSSIDs = [" Home ", "Home", "home", ""]
        value = try value.validated()
        precondition(value.trustedSSIDs == ["Home", "home"])
        precondition(value.rulePlan == [.disconnectTrustedWiFi(["Home", "home"]), .connectWiFi, .connectCellular, .ignore])
        value.wifi = false
        precondition(value.rulePlan == [.disconnectTrustedWiFi(["Home", "home"]), .connectCellular, .ignore])
        value.cellular = false
        do { _ = try value.validated(); preconditionFailure("missing transport accepted") }
        catch NimboOnDemandSettings.ValidationError.noTransport {}
        value.cellular = true
        for bad in [String(repeating: "x", count: 33), "hi\u{0}there", String(repeating: "я", count: 17)] {
            value.trustedSSIDs = [bad]
            do { _ = try value.validated(); preconditionFailure("invalid SSID accepted") }
            catch NimboOnDemandSettings.ValidationError.invalidSSID {}
        }
        value.trustedSSIDs = (0..<33).map { "WiFi-\($0)" }
        do { _ = try value.validated(); preconditionFailure("too many SSIDs accepted") }
        catch NimboOnDemandSettings.ValidationError.tooManySSIDs {}
        let domain = "nimbo.ondemand.test.\(UUID().uuidString)"
        let storage = UserDefaults(suiteName: domain)!
        defer { storage.removePersistentDomain(forName: domain) }
        precondition(NimboOnDemandSettings.load(defaults: storage) == defaults)
        var saved = defaults
        saved.enabled = true
        saved.wifi = false
        storage.set(try JSONEncoder().encode(saved), forKey: NimboOnDemandSettings.preferenceKey)
        precondition(NimboOnDemandSettings.load(defaults: storage) == saved)
        storage.set(Data("corrupt".utf8), forKey: NimboOnDemandSettings.preferenceKey)
        precondition(!NimboOnDemandSettings.load(defaults: storage).enabled)
        let encoded = try JSONEncoder().encode(saved)
        let disabled = try JSONEncoder().encode(defaults)
        precondition(NimboOnDemandSettings.restored(local: nil, staged: encoded) == saved)
        precondition(NimboOnDemandSettings.restored(local: disabled, staged: encoded) == saved)
        precondition(NimboOnDemandSettings.restored(local: encoded, staged: disabled) == defaults)
        precondition(NimboOnDemandSettings.restored(local: encoded, staged: Data("broken".utf8)) == saved)
        precondition(NimboOnDemandSettings.restored(local: nil, staged: nil) == defaults)
        // Pausing affects manager.isOnDemandEnabled, not the saved settings.
        // Restoration must recover the enabled intent without applying any rules.
        precondition(NimboOnDemandSettings.restored(local: nil, staged: encoded).enabled)
        print("On-demand policy and staged restoration: PASS")
    }
}
