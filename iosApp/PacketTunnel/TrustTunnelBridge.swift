import Foundation
import LibXray

/// Lifecycle-queue only. Mihomo's TrustTunnel client in the same Go archive,
/// exposed as an authenticated loopback SOCKS5 that Xray's `proxy` outbound uses.
/// Extension-originated sockets never enter this tunnel, so no protector is needed.
final class TrustTunnelBridge {
    static let version = "mihomo-v1.19.32"
    private(set) var isConfigured = false

    func start(_ configuration: NimboTrustTunnelConfiguration) throws -> Data {
        close()
        let username = UUID().uuidString
        let password = UUID().uuidString + UUID().uuidString
        let request = try JSONSerialization.data(withJSONObject: [
            "link": configuration.rawText, "listen": "127.0.0.1:0",
            "socksUsername": username, "socksPassword": password, "timeoutMs": 10000
        ])
        let response = try String(decoding: request, as: UTF8.self).withCString {
            try decode(NimboTrustTunnelStart(UnsafeMutablePointer(mutating: $0)))
        }
        guard response["ok"] as? Bool == true else {
            close()
            throw NimboTrustTunnelError(bridgeCode: response["error"] as? String)
        }
        guard let port = response["port"] as? Int, (1...65535).contains(port),
              response["version"] as? String == Self.version else {
            close()
            throw NimboTrustTunnelError.runtimeFailure
        }
        isConfigured = true
        return try JSONSerialization.data(withJSONObject: ["outbounds": [[
            "tag": "proxy", "protocol": "socks",
            "settings": ["servers": [["address": "127.0.0.1", "port": port,
                "users": [["user": username, "pass": password]]]]]
        ]]])
    }

    var isRunning: Bool {
        guard isConfigured else { return false }
        return (try? decode(NimboTrustTunnelStatus()))?["running"] as? Bool == true
    }

    func networkChanged() { if isConfigured { NimboTrustTunnelNetworkChanged() } }

    func close() { NimboTrustTunnelStop(); isConfigured = false }

    private func decode(_ pointer: UnsafeMutablePointer<CChar>?) throws -> [String: Any] {
        guard let pointer else { throw NimboTrustTunnelError.runtimeFailure }
        defer { CGoFree(pointer) }
        let data = Data(String(cString: pointer).utf8)
        guard data.count <= 16 * 1024,
              let value = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NimboTrustTunnelError.runtimeFailure
        }
        return value
    }
}
