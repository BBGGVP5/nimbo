import Foundation

// Compiler/test seams for JSON-only builder tests. Never included in app targets.
// The real LibXrayBridge and PacketTunnelNetwork are validated by the IPA build.
final class LibXrayBridge {
    private let convertedConfiguration: [String: Any]?
    private(set) var conversionCount = 0

    init(convertedConfiguration: [String: Any]? = nil) {
        self.convertedConfiguration = convertedConfiguration
    }

    func convertShareText(_ text: String) throws -> [String: Any] {
        conversionCount += 1
        return convertedConfiguration ?? ["outbounds": [["tag": "one", "protocol": "vless"], ["tag": "two", "protocol": "vless"]]]
    }
}
enum PacketTunnelNetwork { static let mtu = 1500 }
