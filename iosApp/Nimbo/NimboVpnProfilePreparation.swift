import Foundation

struct NimboPreparedVpnProfile: Sendable {
    let data: Data
    let preference: NimboCorePreference
    let engine: NimboCoreProfile
    let adBlockingEnabled: Bool
    let mihomoSelections: [String: String]
    let pingServerID: String?
    let storeRevision: UInt64
}

/// Synchronous READ-ONLY worker body. No NE managers, saves, selected-profile
/// writes, runtime starts or conversion of full documents belong here.
enum NimboVpnProfilePreparation {
    static func configuration(fallback: Data? = nil) throws -> Data? {
        let store = NimboConfigurationStore.shared
        let revision = store.revision
        let data: Data?
        if let full = try store.loadFullConfiguration() { data = full.sourceData }
        else if let profile = try NimboSubscriptionRepository.shared.loadProfile(migratingLegacy: false),
                let selected = profile.selectedServer {
            data = NimboStagingPayload.make(for: selected, in: profile)
        } else { data = try store.loadConfiguration() ?? fallback }
        guard store.revision == revision else { throw NimboFullConfigurationError.staleRefresh }
        return data
    }

    static func prepare(data: Data, preference: NimboCorePreference,
                        adBlockingEnabled: Bool) throws -> NimboPreparedVpnProfile {
        let store = NimboConfigurationStore.shared
        let revision = store.revision
        let full = try store.loadFullConfiguration()
        if let full, full.sourceData != data { throw NimboFullConfigurationError.staleRefresh }
        let engine = try NimboCoreAdmission.validate(preference: preference.rawValue, data: data,
                                                     declaredEngine: full?.coreId)
        if engine == .mihomo {
            let document = try full ?? NimboFullConfiguration(data: data, source: nil, title: "Mihomo")
            try NimboMihomoControl.validateAdBlocking(document, enabled: adBlockingEnabled)
            try NimboMihomoControl.preflight(data)
        }
        var pingServerID: String?
        if full == nil, let profile = try? NimboSubscriptionRepository.shared.loadProfile(migratingLegacy: false),
           let selected = profile.selectedServer,
           NimboStagingPayload.make(for: selected, in: profile) == data {
            pingServerID = selected.id
        }
        guard store.revision == revision else { throw NimboFullConfigurationError.staleRefresh }
        return NimboPreparedVpnProfile(data: data, preference: preference, engine: engine, adBlockingEnabled: adBlockingEnabled,
            mihomoSelections: full?.groupSelections ?? [:], pingServerID: pingServerID, storeRevision: revision)
    }
}
