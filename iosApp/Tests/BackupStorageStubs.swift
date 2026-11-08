import Foundation

// Apple-host backup tests use a dedicated defaults suite and memory-only storage.
// These doubles never call Security, NetworkExtension, HTTP or the native core.
final class NimboConfigurationStore {
    static let shared = NimboConfigurationStore()
    var full: NimboFullConfiguration?
    var legacy: String?
    var activeServerID: String?
    var legacySource: String?
    var writes = 0
    var refuseWrite = false
    var refuseRead = false
    func reset() {
        full = nil; legacy = nil; activeServerID = nil; legacySource = nil
        writes = 0; refuseWrite = false; refuseRead = false
    }
    func loadFullConfiguration() throws -> NimboFullConfiguration? {
        if refuseRead { throw NimboFullConfigurationError.inspectionFailed }
        return full
    }
    func loadSource() throws -> String? { full?.source ?? legacySource }
    func saveFullConfiguration(_ value: NimboFullConfiguration) throws {
        try value.validate()
        if refuseWrite { throw NimboFullConfigurationError.inspectionFailed }
        full = value; writes += 1
    }
}
final class NimboSubscriptionRepository {
    static let shared = NimboSubscriptionRepository()
    var refuseInspection = false
    func rawProfileJSON() -> String? { NimboConfigurationStore.shared.legacy }
    func importFullConfiguration(_ value: NimboFullConfiguration) throws {
        try value.validate()
        if refuseInspection { throw NimboFullConfigurationError.inspectionFailed }
        try NimboConfigurationStore.shared.saveFullConfiguration(value)
    }
    func restoreLegacyProfile(_ data: Data, source: String?, selectedServerID: String?) throws {
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let servers = object["servers"] as? [[String: Any]], !servers.isEmpty else {
            throw NimboFullConfigurationError.invalidMetadata
        }
        let store = NimboConfigurationStore.shared
        if store.refuseWrite { throw NimboFullConfigurationError.inspectionFailed }
        store.full = nil; store.legacy = String(decoding: data, as: UTF8.self)
        store.activeServerID = selectedServerID; store.legacySource = source; store.writes += 1
    }
}
enum NimboCorePreference: String {
    case auto, xray, awg, mihomo
    static let defaultsKey = "com.nimbo.connection.vpnCore"
}
