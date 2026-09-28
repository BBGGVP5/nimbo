import Foundation
import NetworkExtension

/// Exact ownership is essential: never toggle another provider's VPN profile.
enum NimboTunnelControl {
    enum ControlError: LocalizedError {
        case needsSetup, missingConfiguration, busy
        var errorDescription: String? {
            switch self {
            case .needsSetup: return "Откройте Nimbo и выполните проверку готовности: VPN-профиль ещё не создан."
            case .missingConfiguration: return "Откройте Nimbo, добавьте подписку и выберите сервер."
            case .busy: return "VPN меняет состояние. Дождитесь завершения и повторите действие."
            }
        }
    }

    static func manager() async throws -> NETunnelProviderManager? {
        let identifier = NimboConstants.packetTunnelBundleIdentifier
        let managers = try await NETunnelProviderManager.loadAllFromPreferences()
        return managers.first {
            ($0.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier == identifier
        }
    }

    static func setEnabled(_ enabled: Bool) async throws {
        guard let manager = try await manager() else { throw ControlError.needsSetup }
        if !enabled {
            // Explicit stop must also disable legacy on-demand rules.
            if manager.isOnDemandEnabled {
                manager.isOnDemandEnabled = false
                manager.onDemandRules = []
                try await manager.saveToPreferences()
                try await manager.loadFromPreferences()
            }
            manager.connection.stopVPNTunnel()
            return
        }
        switch manager.connection.status {
        case .connected, .connecting, .reasserting: return
        case .disconnecting: throw ControlError.busy
        default: break
        }
        guard let proto = manager.protocolConfiguration as? NETunnelProviderProtocol,
              let data = proto.providerConfiguration?["configData"] as? Data, !data.isEmpty else {
            throw ControlError.missingConfiguration
        }
        if !manager.isEnabled {
            manager.isEnabled = true
            try await manager.saveToPreferences()
            try await manager.loadFromPreferences()
        }
        try manager.connection.startVPNTunnel()
    }

    static func statusDescription() async throws -> String {
        guard let manager = try await manager() else { return "Nimbo ещё не настроен" }
        switch manager.connection.status {
        case .connected: return "Nimbo подключён"
        case .connecting, .reasserting: return "Nimbo подключается"
        case .disconnecting: return "Nimbo отключается"
        default: return "Nimbo отключён"
        }
    }
}
