import Foundation

@main enum FullConfigurationTests {
    static let yaml = "\u{feff}# cafe\u{301} \r\nproxy-groups:\r\n  - {name: VPN, type: select, proxies: [DIRECT]}\r\nrules: [MATCH,VPN]\r\n "
    static let source = "https://fixture.invalid/sub?token=private-fixture"
    static let theme = "com.nimbo.appearance.themeMode"
    static var checks = 0

    static func main() throws {
        let scenarios: [(String, () throws -> Void)] = [
            ("exactRoundTrip", { try exactRoundTrip() }), ("tamperedHash", { try tamperedHash() }),
            ("unsupportedVersion", { try unsupportedVersion() }), ("choicesBoundToSource", { try choicesBoundToSource() }),
            ("invalidUTF8", { invalidUTF8() }), ("sizeLimit", { sizeLimit() }),
            ("offlineBackupRoundTrip", { try offlineBackupRoundTrip() }),
            ("oldVersionAndLocalLegacyRecovery", { try oldVersionAndLocalLegacyRecovery() }),
            ("invalidArchiveIsAtomic", { try invalidArchiveIsAtomic() }),
            ("admissionAndStorageFailuresAreAtomic", { try admissionAndStorageFailuresAreAtomic() }),
            ("metadataAndPreferencesAreRestored", { try metadataAndPreferencesAreRestored() }),
            ("corruptRecordCannotExportLegacy", { try corruptRecordCannotExportLegacy() })]
        for (name, operation) in scenarios {
            do { try operation() }
            catch {
                FileHandle.standardError.write(Data("FAIL scenario: \(name); \(error)\n".utf8))
                throw error
            }
            FileHandle.standardError.write(Data("PASS scenario: \(name)\n".utf8))
        }
        print("PASS: \(checks) full-record and production offline-backup scenarios; storage doubles, no VPN/Keychain/HTTP")
    }
    static func checked() { checks += 1 }
    static func rejects(_ operation: () throws -> Void) {
        do { try operation(); preconditionFailure("Expected validation rejection") } catch { }
    }
    static func record(choices: [String: String] = ["VPN": "DIRECT"]) throws -> NimboFullConfiguration {
        try NimboFullConfiguration(data: Data(yaml.utf8), source: source, title: "Fixture", groupSelections: choices)
    }
    static func exactRoundTrip() throws {
        let original = try record()
        let decoded = try JSONDecoder().decode(NimboFullConfiguration.self, from: JSONEncoder().encode(original))
        try decoded.validate()
        precondition(decoded == original && decoded.sourceData == Data(yaml.utf8))
        precondition(!decoded.description.contains("private-fixture"))
        var oldRecord = try object(NimboFullConfiguration(data: Data("proxies: []\r\n".utf8), source: nil))
        oldRecord.removeValue(forKey: "originalUTF8")
        let legacy = try JSONDecoder().decode(NimboFullConfiguration.self, from: JSONSerialization.data(withJSONObject: oldRecord))
        precondition(legacy.sourceData == Data("proxies: []\r\n".utf8))
        checked()
    }
    static func object(_ record: NimboFullConfiguration) throws -> [String: Any] {
        var result = try JSONSerialization.jsonObject(with: JSONEncoder().encode(record)) as! [String: Any]
        // Foundation's NSString JSON parser consumes a leading BOM inside this
        // value on Apple hosts. Fixtures must not silently corrupt the source
        // before exercising the production Codable decoder/validation.
        result["originalYAML"] = record.originalYAML
        return result
    }
    static func tamperedHash() throws {
        var value = try object(record()); value["originalYAML"] = "proxies: []\n"
        let data = try JSONSerialization.data(withJSONObject: value)
        rejects { _ = try JSONDecoder().decode(NimboFullConfiguration.self, from: data) }
        var changedBytes = try object(record()); changedBytes["originalUTF8"] = Data("proxies: []\n".utf8).base64EncodedString()
        let bytesData = try JSONSerialization.data(withJSONObject: changedBytes)
        rejects { _ = try JSONDecoder().decode(NimboFullConfiguration.self, from: bytesData) }; checked()
    }
    static func unsupportedVersion() throws {
        for pair in [("schemaVersion", 9 as Any), ("coreId", "xray" as Any), ("format", "future" as Any)] {
            var value = try object(record()); value[pair.0] = pair.1
            let data = try JSONSerialization.data(withJSONObject: value)
            rejects { _ = try JSONDecoder().decode(NimboFullConfiguration.self, from: data) }
        }
        checked()
    }
    static func choicesBoundToSource() throws {
        let original = try record()
        let same = try original.replacingSource(original.sourceData)
        precondition(same.groupSelections == original.groupSelections)
        let changed = try original.replacingSource(Data((yaml + "# changed\n").utf8))
        precondition(changed.groupSelections.isEmpty && changed.sourceSHA256 != original.sourceSHA256)
        rejects { _ = try changed.recordingSelection(group: "VPN", member: "DIRECT", expectedSourceSHA256: original.sourceSHA256) }
        checked()
    }
    static func invalidUTF8() {
        rejects { _ = try NimboFullConfiguration(data: Data([0xff, 0xfe]), source: nil) }; checked()
    }
    static func sizeLimit() {
        rejects { _ = try NimboFullConfiguration(data: Data(), source: nil) }
        rejects { _ = try NimboFullConfiguration(data: Data(repeating: 65, count: NimboFullConfiguration.maximumSourceBytes + 1), source: nil) }
        checked()
    }
    static func archive(_ full: NimboFullConfiguration? = nil, version: Int = 2) throws -> [String: Any] {
        var result: [String: Any] = ["version": version, "createdAt": "2026-10-04T00:00:00Z", "settings": [theme: "light"]]
        if let full {
            result["fullConfiguration"] = try object(full)
            if let source = full.source { result["source"] = source }
        }
        return result
    }
    static func withFixture(_ operation: (UserDefaults, URL) throws -> Void) throws {
        let suite = "nimbo.storage-tests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(suite, isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer {
            defaults.removePersistentDomain(forName: suite)
            try? FileManager.default.removeItem(at: folder)
            NimboConfigurationStore.shared.reset()
            NimboSubscriptionRepository.shared.refuseInspection = false
        }
        NimboConfigurationStore.shared.reset()
        NimboSubscriptionRepository.shared.refuseInspection = false
        defaults.set("dark", forKey: theme)
        try operation(defaults, folder.appendingPathComponent("fixture.json"))
    }
    static func write(_ value: [String: Any], to url: URL) throws {
        try JSONSerialization.data(withJSONObject: value).write(to: url, options: .atomic)
    }
    static func offlineBackupRoundTrip() throws {
        try withFixture { defaults, _ in
            let original = try record()
            let store = NimboConfigurationStore.shared; store.full = original
            // A retained legacy profile must never make it into a full backup.
            store.legacy = "legacy-private"; store.activeServerID = "old-node"
            guard let exported = NimboBackup.export(defaults: defaults) else { preconditionFailure("Export failed") }
            defer { try? FileManager.default.removeItem(at: exported) }
            let decoded = try JSONDecoder.iso8601.decode(NimboBackup.Payload.self, from: Data(contentsOf: exported))
            precondition(decoded.fullConfiguration == original && decoded.profile == nil && decoded.activeServerID == nil)
            store.reset()
            let refreshURL = try NimboBackup.restore(from: exported, defaults: defaults)
            precondition(refreshURL == nil && store.full == original && store.writes == 1); checked()
        }
    }
    static func oldVersionAndLocalLegacyRecovery() throws {
        try withFixture { defaults, url in
            var value = try archive(version: 1)
            value["profile"] = "{\"servers\":[{\"id\":\"old-node\"}]}"
            value["activeServerID"] = "old-node"
            try write(value, to: url)
            let localRefreshURL = try NimboBackup.restore(from: url, defaults: defaults)
            precondition(localRefreshURL == nil)
            precondition(NimboConfigurationStore.shared.activeServerID == "old-node")
            precondition(defaults.string(forKey: theme) == "light")
            value["source"] = source; try write(value, to: url)
            let remoteRefreshURL = try NimboBackup.restore(from: url, defaults: defaults)
            precondition(remoteRefreshURL == source); checked()
        }
    }
    static func unchanged(_ defaults: UserDefaults, original: NimboFullConfiguration) {
        precondition(NimboConfigurationStore.shared.full == original && NimboConfigurationStore.shared.writes == 0)
        precondition(defaults.string(forKey: theme) == "dark")
    }
    static func invalidArchiveIsAtomic() throws {
        try withFixture { defaults, url in
            let original = try record(); NimboConfigurationStore.shared.full = original
            var tampered = try archive(original)
            var full = tampered["fullConfiguration"] as! [String: Any]; full["sourceSHA256"] = "bad"
            tampered["fullConfiguration"] = full
            var mixed = try archive(original); mixed["activeServerID"] = "old-node"
            var invalidCore = try archive(original); invalidCore["settings"] = [theme: "light", NimboCorePreference.defaultsKey: "future"]
            for value in [try archive(original, version: 9), tampered, mixed, invalidCore] {
                try write(value, to: url)
                rejects { _ = try NimboBackup.restore(from: url, defaults: defaults) }
                unchanged(defaults, original: original)
            }
            try Data("not JSON".utf8).write(to: url)
            rejects { _ = try NimboBackup.restore(from: url, defaults: defaults) }; unchanged(defaults, original: original)
            try Data(repeating: 32, count: NimboBackup.maximumArchiveBytes + 1).write(to: url)
            rejects { _ = try NimboBackup.restore(from: url, defaults: defaults) }; unchanged(defaults, original: original)
            checked()
        }
    }
    static func admissionAndStorageFailuresAreAtomic() throws {
        try withFixture { defaults, url in
            let original = try record(); let store = NimboConfigurationStore.shared; store.full = original
            try write(archive(original), to: url)
            NimboSubscriptionRepository.shared.refuseInspection = true
            rejects { _ = try NimboBackup.restore(from: url, defaults: defaults) }; unchanged(defaults, original: original)
            NimboSubscriptionRepository.shared.refuseInspection = false; store.refuseWrite = true
            rejects { _ = try NimboBackup.restore(from: url, defaults: defaults) }; unchanged(defaults, original: original)
            checked()
        }
    }
    static func metadataAndPreferencesAreRestored() throws {
        try withFixture { defaults, url in
            let original = try record()
            var value = try archive(original)
            value["settings"] = [theme: "light", "com.nimbo.routing.adBlocking": "true", NimboCorePreference.defaultsKey: "mihomo", "unrelated-key": "not-imported"]
            var meta = NimboSubscriptionMeta.empty; meta.announce = "Provider description"; meta.title = "Fixture"
            value["subscriptionMeta"] = try JSONSerialization.jsonObject(with: JSONEncoder().encode(meta))
            try write(value, to: url)
            _ = try NimboBackup.restore(from: url, defaults: defaults)
            precondition(NimboConfigurationStore.shared.full?.sourceData == original.sourceData)
            precondition(defaults.bool(forKey: "com.nimbo.routing.adBlocking"))
            precondition(defaults.string(forKey: NimboCorePreference.defaultsKey) == "mihomo")
            precondition(defaults.object(forKey: "unrelated-key") == nil)
            precondition(NimboSubscriptionMetaStore.load(defaults: defaults) == meta); checked()
        }
    }
    static func corruptRecordCannotExportLegacy() throws {
        try withFixture { defaults, _ in
            let store = NimboConfigurationStore.shared; store.legacy = "retained legacy"; store.refuseRead = true
            precondition(NimboBackup.export(defaults: defaults) == nil && store.writes == 0); checked()
        }
    }
}
private extension JSONDecoder {
    static var iso8601: JSONDecoder { let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601; return decoder }
}
