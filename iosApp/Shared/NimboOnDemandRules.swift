import Foundation
import NetworkExtension

/// NetworkExtension evaluates first matching rule. Never require an external
/// URL/DNS probe: restricted mobile Internet must not prevent tunnel startup.
enum NimboOnDemandRules {
    static let providerKey = "nimboOnDemandSettings"

    static func make(_ settings: NimboOnDemandSettings) -> [NEOnDemandRule] {
        settings.rulePlan.map { item in
            switch item {
            case .disconnectTrustedWiFi(let ssids):
                let rule = NEOnDemandRuleDisconnect()
                rule.interfaceTypeMatch = .wiFi
                rule.ssidMatch = ssids
                return rule
            case .connectWiFi:
                let rule = NEOnDemandRuleConnect()
                rule.interfaceTypeMatch = .wiFi
                return rule
            case .connectCellular:
                let rule = NEOnDemandRuleConnect()
                rule.interfaceTypeMatch = .cellular
                return rule
            case .ignore:
                let rule = NEOnDemandRuleIgnore()
                rule.interfaceTypeMatch = .any
                return rule
            }
        }
    }

    static func apply(_ settings: NimboOnDemandSettings, to manager: NETunnelProviderManager) {
        manager.onDemandRules = make(settings)
        manager.isOnDemandEnabled = !settings.rulePlan.isEmpty
    }

    @MainActor private static var writeTail: Task<Void, Error>?
    @MainActor private static var writeGeneration: UInt64 = 0

    /// Serialize writes within each executable (app or widget).
    /// In particular a stop queued during a connect write wins afterwards.
    @MainActor static func persist(_ settings: NimboOnDemandSettings,
                                   on manager: NETunnelProviderManager) async throws {
        let settings = try settings.validated()
        writeGeneration &+= 1
        let generation = writeGeneration
        let previous = writeTail
        let task = Task { @MainActor in
            _ = try? await previous?.value
            apply(settings, to: manager)
            try await manager.saveToPreferences()
            try await manager.loadFromPreferences()
        }
        writeTail = task
        defer { if generation == writeGeneration { writeTail = nil } }
        try await task.value
    }

    static func stagedSettings(in proto: NETunnelProviderProtocol) -> NimboOnDemandSettings {
        guard let data = proto.providerConfiguration?[providerKey] as? Data,
              let value = try? JSONDecoder().decode(NimboOnDemandSettings.self, from: data),
              let settings = try? value.validated() else { return NimboOnDemandSettings() }
        return settings
    }
}
