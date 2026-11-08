import Foundation

// Storage/native seams only. The admission, full-document identity, staging
// and preparation code compiled by the runner are the production sources.
final class NimboConfigurationStore {
    static let shared = NimboConfigurationStore()
    var revision: UInt64 = 0
    var full: NimboFullConfiguration?
    var legacy: Data?
    var activeServerID: String?
    func loadFullConfiguration() throws -> NimboFullConfiguration? { full }
    func loadConfiguration() throws -> Data? { legacy }
}
final class NimboSubscriptionRepository {
    static let shared = NimboSubscriptionRepository()
    var profile: NimboSubscriptionProfile?
    func loadProfile(migratingLegacy: Bool = true) throws -> NimboSubscriptionProfile? {
        precondition(!migratingLegacy, "Read-only preparation may not migrate/write")
        return profile
    }
}
extension NimboSubscriptionProfile {
    var selectedServer: NimboSubscriptionServer? {
        servers.first { $0.id == NimboConfigurationStore.shared.activeServerID }
    }
}
enum NimboMihomoControl {
    static var calls: [String] = []
    static var onPreflight: (() throws -> Void)?
    static func validateAdBlocking(_ full: NimboFullConfiguration, enabled: Bool) throws {
        calls.append("admission")
    }
    static func preflight(_ data: Data) throws {
        calls.append("preflight")
        precondition(data == NimboConfigurationStore.shared.full?.sourceData)
        try onPreflight?()
    }
}

@main enum VpnProfilePreparationTests {
    enum Expected: Error { case nativeFailure }
    static let server = NimboSubscriptionServer(id: "fixture", name: "Fixture", protocol: "vless",
        host: "example.invalid", port: 443, transport: "tcp", security: "reality",
        rawConfiguration: "vless://00000000-0000-0000-0000-000000000000@example.invalid:443?security=reality#Fixture",
        isNativeXrayJson: false)
    static var data: Data { Data(server.rawConfiguration.utf8) }

    static func reset() {
        let store = NimboConfigurationStore.shared
        store.full = nil; store.legacy = nil; store.activeServerID = server.id; store.revision &+= 1
        NimboSubscriptionRepository.shared.profile = NimboSubscriptionProfile(parserRevision: 1, title: "Fixture",
            source: nil, format: "links", servers: [server], diagnosticCode: nil)
        NimboMihomoControl.calls = []; NimboMihomoControl.onPreflight = nil
    }

    static func main() throws {
        reset()
        do {
            _ = try NimboVpnProfilePreparation.prepare(data: data, preference: .mihomo, adBlockingEnabled: false)
            preconditionFailure("Plain VLESS must not be silently converted to Mihomo")
        } catch NimboCoreSelectionError.incompatible {}
        precondition(NimboMihomoControl.calls.isEmpty, "Incompatible subscription entered native core")
        for preference in [NimboCorePreference.auto, .xray] {
            let result = try NimboVpnProfilePreparation.prepare(data: data, preference: preference, adBlockingEnabled: false)
            precondition(result.engine == .xray && result.data == data && result.pingServerID == server.id)
            precondition(result.storeRevision == NimboConfigurationStore.shared.revision)
        }
        let unrelated = Data("vless://different@example.invalid:443#Other".utf8)
        let unattributed = try NimboVpnProfilePreparation.prepare(data: unrelated, preference: .auto, adBlockingEnabled: false)
        precondition(unattributed.pingServerID == nil)

        reset()
        let yaml = Data("\u{feff}mode: rule\r\nproxies: []\r\nproxy-groups: []\r\nrules: []\r\n".utf8)
        let full = try NimboFullConfiguration(data: yaml, source: nil, groupSelections: ["Sites": "DIRECT"])
        NimboConfigurationStore.shared.full = full
        let authoritative = try NimboVpnProfilePreparation.configuration()
        precondition(authoritative == yaml, "Full record lost to retained legacy entries")
        for preference in [NimboCorePreference.auto, .mihomo] {
            let result = try NimboVpnProfilePreparation.prepare(data: yaml, preference: preference, adBlockingEnabled: true)
            precondition(result.engine == .mihomo && result.data == yaml && result.mihomoSelections == full.groupSelections)
            precondition(result.pingServerID == nil)
        }
        precondition(NimboMihomoControl.calls == ["admission", "preflight", "admission", "preflight"])
        do {
            _ = try NimboVpnProfilePreparation.prepare(data: data, preference: .auto, adBlockingEnabled: false)
            preconditionFailure("Stale selected server replaced authoritative full document")
        } catch NimboFullConfigurationError.staleRefresh {}
        do {
            _ = try NimboVpnProfilePreparation.prepare(data: yaml, preference: .xray, adBlockingEnabled: false)
            preconditionFailure("Full Mihomo document flattened to Xray")
        } catch NimboCoreSelectionError.incompatible {}

        NimboMihomoControl.onPreflight = { NimboConfigurationStore.shared.revision &+= 1 }
        do {
            _ = try NimboVpnProfilePreparation.prepare(data: yaml, preference: .mihomo, adBlockingEnabled: false)
            preconditionFailure("Source mutation during native work admitted")
        } catch NimboFullConfigurationError.staleRefresh {}
        NimboMihomoControl.onPreflight = { throw Expected.nativeFailure }
        do {
            _ = try NimboVpnProfilePreparation.prepare(data: yaml, preference: .mihomo, adBlockingEnabled: false)
            preconditionFailure("Native rejection swallowed")
        } catch Expected.nativeFailure {}

        reset()
        NimboSubscriptionRepository.shared.profile = nil
        let missing = try NimboVpnProfilePreparation.configuration()
        precondition(missing == nil)
        let fallback = try NimboVpnProfilePreparation.configuration(fallback: data)
        precondition(fallback == data)
        NimboConfigurationStore.shared.legacy = unrelated
        let stored = try NimboVpnProfilePreparation.configuration(fallback: data)
        precondition(stored == unrelated)
        print("PASS: ordinary subscription admission, full-source precedence/bytes/selections, stale-source and native rejection, ping attribution, fallback")
    }
}
