import Foundation
import NetworkExtension

/// NEVPNConnection is thread safe. Its synchronous command methods must not
/// hold the UI thread while communicating with the system VPN service.
enum NimboVpnSystemCommands {
    enum CommandError: LocalizedError {
        case busy
        var errorDescription: String? { "VPN меняет состояние. Дождитесь завершения и повторите действие." }
    }
    private static let worker = NimboVpnCommandQueue(label: "com.nimbo.vpn.commands")

    static func start(_ connection: NEVPNConnection, lease: NimboVpnCommandLease) async throws {
        try await worker.perform(lease: lease) {
            // On Demand can start this same session while the command is queued.
            switch connection.status {
            case .connected, .connecting, .reasserting: return
            case .disconnecting: throw CommandError.busy
            default: break
            }
            try connection.startVPNTunnel()
        }
    }

    static func stop(_ connection: NEVPNConnection) async {
        // A user's stop is cleanup and must run even when its calling Task was
        // cancelled. No lease is passed, and stopVPNTunnel does not throw.
        _ = try? await worker.perform { connection.stopVPNTunnel() }
    }
}
