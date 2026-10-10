import Foundation
import Security

/// Stores imported subscription material in the iOS Keychain. The active copy
/// is also staged in NETunnelProviderProtocol.providerConfiguration because the
/// separately signed Packet Tunnel process cannot rely on an App Group.
final class NimboConfigurationStore {
    static let shared = NimboConfigurationStore()

    private let fullRecordLock = NSRecursiveLock()

    private let service = "com.nimbo.resignable.configuration"
    private let configurationAccount = "active-configuration"
    private let sourceAccount = "active-source"
    private let profileAccount = "normalized-profile-v2"
    private let selectedServerAccount = "selected-server-id-v1"
    private let fullConfigurationAccount = "full-configuration-v1"
    private let descriptionKey = "nimbo.active.configuration.description"
    private let activeServerIDKey = "nimbo.active.server.id"

    private init() {}

    /// One SecItem write commits source, identity and desired choices together.
    /// Legacy accounts are retained but cannot override an active full record.
    func saveFullConfiguration(_ configuration: NimboFullConfiguration) throws {
        fullRecordLock.lock()
        defer { fullRecordLock.unlock() }
        try configuration.validate()
        try write(JSONEncoder().encode(configuration), account: fullConfigurationAccount)
    }

    /// Compare and commit are one critical section, including a live choice write.
    func saveFullConfiguration(_ configuration: NimboFullConfiguration, expected: NimboFullConfiguration) throws {
        fullRecordLock.lock()
        defer { fullRecordLock.unlock() }
        guard try loadFullConfiguration() == expected else {
            throw NimboFullConfigurationError.staleRefresh
        }
        try saveFullConfiguration(configuration)
    }

    func loadFullConfiguration() throws -> NimboFullConfiguration? {
        fullRecordLock.lock()
        defer { fullRecordLock.unlock() }
        guard let data = try read(account: fullConfigurationAccount) else { return nil }
        let configuration = try JSONDecoder().decode(NimboFullConfiguration.self, from: data)
        try configuration.validate()
        return configuration
    }

    func save(configuration: Data, source: String?, description: String) throws {
        fullRecordLock.lock()
        defer { fullRecordLock.unlock() }
        try saveLegacyConfiguration(configuration, source: source, description: description)
        try delete(account: fullConfigurationAccount)
    }

    private func saveLegacyConfiguration(_ configuration: Data, source: String?, description: String) throws {
        guard !configuration.isEmpty else { throw NimboConfigurationStoreError.empty }
        try write(configuration, account: configurationAccount)
        if let source, let sourceData = source.data(using: .utf8) {
            try write(sourceData, account: sourceAccount)
        } else {
            try? delete(account: sourceAccount)
        }
        UserDefaults.standard.set(description, forKey: descriptionKey)
    }

    func save(
        profile: Data,
        selectedServer: Data,
        selectedServerID: String,
        source: String?,
        description: String
    ) throws {
        fullRecordLock.lock()
        defer { fullRecordLock.unlock() }
        guard !profile.isEmpty, !selectedServer.isEmpty else { throw NimboConfigurationStoreError.empty }
        try write(profile, account: profileAccount)
        try saveLegacyConfiguration(selectedServer, source: source, description: description)
        try write(Data(selectedServerID.utf8), account: selectedServerAccount)
        UserDefaults.standard.set(selectedServerID, forKey: activeServerIDKey)
        // Commit active-engine replacement only after all legacy writes succeed.
        try delete(account: fullConfigurationAccount)
    }

    func saveSelection(configuration: Data, serverID: String) throws {
        fullRecordLock.lock()
        defer { fullRecordLock.unlock() }
        guard try loadFullConfiguration() == nil else {
            throw NimboFullConfigurationError.fullConfigurationActive
        }
        guard !configuration.isEmpty, !serverID.isEmpty else { throw NimboConfigurationStoreError.empty }
        try write(configuration, account: configurationAccount)
        try write(Data(serverID.utf8), account: selectedServerAccount)
        UserDefaults.standard.set(serverID, forKey: activeServerIDKey)
    }

    func loadConfiguration() throws -> Data? {
        if let full = try loadFullConfiguration() { return full.sourceData }
        return try read(account: configurationAccount)
    }

    func loadProfile() throws -> Data? {
        try read(account: profileAccount)
    }

    func loadSource() throws -> String? {
        if let full = try loadFullConfiguration() { return full.source }
        guard let data = try read(account: sourceAccount) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    var displayDescription: String? {
        if let full = try? loadFullConfiguration() { return full.title }
        return UserDefaults.standard.string(forKey: descriptionKey)
    }

    var activeServerID: String? {
        // Presence, not successful decoding, prevents falling back to an old node.
        if (try? read(account: fullConfigurationAccount)) != nil { return nil }
        if let id = UserDefaults.standard.string(forKey: activeServerIDKey) { return id }
        guard let data = try? read(account: selectedServerAccount) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func retainSelectedServerID(_ id: String) throws {
        fullRecordLock.lock()
        defer { fullRecordLock.unlock() }
        guard !id.isEmpty, try loadFullConfiguration() == nil else { return }
        try write(Data(id.utf8), account: selectedServerAccount)
        UserDefaults.standard.set(id, forKey: activeServerIDKey)
    }

    func removeAll() throws {
        fullRecordLock.lock()
        defer { fullRecordLock.unlock() }
        try delete(account: configurationAccount)
        try delete(account: sourceAccount)
        try delete(account: profileAccount)
        try delete(account: selectedServerAccount)
        try delete(account: fullConfigurationAccount)
        UserDefaults.standard.removeObject(forKey: descriptionKey)
        UserDefaults.standard.removeObject(forKey: activeServerIDKey)
    }

    private func write(_ data: Data, account: String) throws {
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let updates: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let status = SecItemUpdate(base as CFDictionary, updates as CFDictionary)
        if status == errSecSuccess { return }
        if status != errSecItemNotFound { throw NimboConfigurationStoreError.keychain(status) }

        var item = base
        updates.forEach { item[$0.key] = $0.value }
        let addStatus = SecItemAdd(item as CFDictionary, nil)
        guard addStatus == errSecSuccess else {
            throw NimboConfigurationStoreError.keychain(addStatus)
        }
    }

    private func read(account: String) throws -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else {
            throw NimboConfigurationStoreError.keychain(status)
        }
        return data
    }

    private func delete(account: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw NimboConfigurationStoreError.keychain(status)
        }
    }
}

enum NimboConfigurationStoreError: LocalizedError {
    case empty
    case keychain(OSStatus)

    var errorDescription: String? {
        switch self {
        case .empty:
            "Конфигурация пуста (IOS_STORE_EMPTY)."
        case let .keychain(status):
            "Keychain вернул код \(status) (IOS_KEYCHAIN_FAILURE)."
        }
    }
}
