import Foundation

// Compiler/test seams for JSON-only builder tests. Never included in app targets.
// The real LibXrayBridge and PacketTunnelNetwork are validated by the IPA build.
final class LibXrayBridge {
    func convertShareText(_ text: String) throws -> [String: Any] {
        ["outbounds": [["tag": "one", "protocol": "vless"], ["tag": "two", "protocol": "vless"]]]
    }
}
enum PacketTunnelNetwork { static let mtu = 1500 }
