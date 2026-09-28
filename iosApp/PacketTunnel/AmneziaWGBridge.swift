import Foundation
import LibXray

/// All methods run on PacketTunnelProvider.lifecycleQueue. The INI and random
/// SOCKS credentials stay in extension memory only and are cleared on stop.
final class AmneziaWGBridge {
    private var configuration: String?
    private var username = ""
    private var password = ""
    private(set) var port = 0
    private(set) var suspended = false
    var isConfigured: Bool { configuration != nil }

    func start(_ configuration: NimboAWGConfiguration) throws -> Data {
        close()
        self.configuration = configuration.rawText
        username = UUID().uuidString
        password = UUID().uuidString + UUID().uuidString
        do {
            try startRuntime()
            let server: [String: Any] = [
                "address": "127.0.0.1", "port": port,
                "users": [["user": username, "pass": password]]
            ]
            let outbound: [String: Any] = [
                "tag": "proxy", "protocol": "socks",
                "settings": ["servers": [server]]
            ]
            return try JSONSerialization.data(withJSONObject: ["outbounds": [outbound]])
        } catch {
            close()
            throw NimboAWGError.runtimeFailure
        }
    }

    private func startRuntime() throws {
        guard let configuration else { throw NimboAWGError.runtimeFailure }
        let request = try JSONSerialization.data(withJSONObject: [
            "config": configuration, "listen": "127.0.0.1:\(port)",
            "username": username, "password": password
        ])
        let response = try String(decoding: request, as: UTF8.self).withCString {
            try decode(NimboAWGStart(UnsafeMutablePointer(mutating: $0)))
        }
        guard response["ok"] as? Bool == true,
              response["version"] as? String == NimboAWGConfiguration.version,
              let actualPort = response["port"] as? Int, (1...65535).contains(actualPort),
              port == 0 || port == actualPort else {
            NimboAWGStop()
            throw NimboAWGError.runtimeFailure
        }
        port = actualPort
        suspended = false
    }

    func suspend() {
        guard isConfigured else { return }
        NimboAWGStop()
        suspended = true
    }

    /// Xray keeps its TUN and SOCKS outbound. Only the AWG sockets are replaced.
    func restart() throws {
        guard isConfigured else { return }
        suspend()
        do { try startRuntime() }
        catch { close(); throw NimboAWGError.runtimeFailure }
    }

    func stats() throws -> [String: Any] { try decode(NimboAWGStats()) }

    func close() {
        NimboAWGStop()
        configuration = nil
        username = ""
        password = ""
        port = 0
        suspended = false
    }

    private func decode(_ pointer: UnsafeMutablePointer<CChar>?) throws -> [String: Any] {
        guard let pointer else { throw NimboAWGError.runtimeFailure }
        defer { CGoFree(pointer) }
        let data = Data(String(cString: pointer).utf8)
        guard data.count <= 16 * 1024,
              let response = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              response["ok"] as? Bool == true else { throw NimboAWGError.runtimeFailure }
        return response
    }
}
