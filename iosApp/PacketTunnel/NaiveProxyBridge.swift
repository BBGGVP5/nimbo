import Foundation
import LibXray

/// Lifecycle-queue only. The in-process Chromium client owns no TUN or subprocess.
final class NaiveProxyBridge {
    private(set) var isConfigured = false
    func start(_ configuration: NimboNaiveConfiguration) throws -> Data {
        close()
        let username = UUID().uuidString
        let password = UUID().uuidString + UUID().uuidString
        let request = try JSONSerialization.data(withJSONObject: [
            "link": configuration.rawText, "listen": "127.0.0.1:0",
            "username": username, "password": password
        ])
        do {
            let response = try String(decoding: request, as: UTF8.self).withCString {
                try decode(NimboNaiveStart(UnsafeMutablePointer(mutating: $0)))
            }
            guard let port = response["port"] as? Int, (1...65535).contains(port),
                  response["version"] as? String == "150.0.7871.63" else { throw NimboNaiveError.runtimeFailure }
            isConfigured = true
            return try JSONSerialization.data(withJSONObject: ["outbounds": [[
                "tag": "proxy", "protocol": "socks",
                "settings": ["servers": [["address": "127.0.0.1", "port": port,
                    "users": [["user": username, "pass": password]]]]]
            ]]])
        } catch { close(); throw NimboNaiveError.runtimeFailure }
    }
    var isRunning: Bool {
        guard isConfigured else { return false }
        return (try? decode(NimboNaiveStatus()))?["running"] as? Bool == true
    }
    func networkChanged() { if isConfigured { NimboNaiveResetConnections() } }
    func close() { NimboNaiveStop(); isConfigured = false }
    private func decode(_ pointer: UnsafeMutablePointer<CChar>?) throws -> [String: Any] {
        guard let pointer else { throw NimboNaiveError.runtimeFailure }
        defer { CGoFree(pointer) }
        let data = Data(String(cString: pointer).utf8)
        guard data.count <= 16 * 1024,
              let value = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              value["ok"] as? Bool == true else { throw NimboNaiveError.runtimeFailure }
        return value
    }
}
