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
    private static let statusWorker = NimboVpnCommandQueue(label: "com.nimbo.vpn.status")

    static func snapshot(_ connection: NEVPNConnection) async -> NimboVpnObservedConnection {
        // These getters may contend with synchronous start/stop IPC. Never
        // touch them from MainActor, including from UI timers and didSet.
        let snapshot = try? await statusWorker.perform {
            NimboVpnObservedConnection(rawStatus: connection.status.rawValue,
                connectedDate: connection.connectedDate)
        }
        return snapshot ?? NimboVpnObservedConnection(rawStatus: -1, connectedDate: nil)
    }

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
