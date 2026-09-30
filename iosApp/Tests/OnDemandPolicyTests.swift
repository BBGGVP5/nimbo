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
        print("On-demand policy: PASS")
    }
}
