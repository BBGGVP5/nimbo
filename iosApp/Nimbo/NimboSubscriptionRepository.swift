import Foundation
import NimboShared

struct NimboSubscriptionServer: Codable, Identifiable, Equatable {
    let id: String
    let name: String
    let `protocol`: String
    let host: String
    let port: Int
    let transport: String
    let security: String
    let rawConfiguration: String
    let isNativeXrayJson: Bool

    var connectionLabel: String {
        [self.protocol.uppercased(), transport.uppercased(), security.capitalized]
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }
}

struct NimboSubscriptionProfile: Codable, Equatable {
    let parserRevision: Int
    let title: String
    let source: String?
    let format: String
    let servers: [NimboSubscriptionServer]
    let diagnosticCode: String?

    var selectedServer: NimboSubscriptionServer? {
        guard let selectedID = NimboConfigurationStore.shared.activeServerID else {
            return servers.first
        }
        return servers.first(where: { $0.id == selectedID }) ?? servers.first
    }
}

final class NimboSubscriptionRepository {
    static let shared = NimboSubscriptionRepository()

    private let decoder = JSONDecoder()
    private let maximumInputBytes = 15 * 1_024 * 1_024

    private init() {}

    func importPayload(_ data: Data, source: String?) throws -> NimboSubscriptionProfile {
        guard !data.isEmpty, data.count <= maximumInputBytes else {
            throw NimboSubscriptionRepositoryError.invalidSize
        }
        guard let payload = String(data: data, encoding: .utf8) else {
            throw NimboSubscriptionRepositoryError.invalidEncoding
        }

        let json = SubscriptionPayloadBridgeKt.NimboParseSubscriptionPayload(
            payload: payload,
            source: source
        )
        guard let normalizedData = json.data(using: .utf8) else {
            throw NimboSubscriptionRepositoryError.bridgeEncoding
        }
        let profile = try decoder.decode(NimboSubscriptionProfile.self, from: normalizedData)
        guard !profile.servers.isEmpty else {
            throw NimboSubscriptionRepositoryError.noSupportedServers(profile.diagnosticCode)
        }

        let previousID = NimboConfigurationStore.shared.activeServerID
        let selected = profile.servers.first(where: { $0.id == previousID }) ?? profile.servers[0]
        try NimboConfigurationStore.shared.save(
            profile: normalizedData,
            selectedServer: Data(selected.rawConfiguration.utf8),
            selectedServerID: selected.id,
            source: source,
            description: profile.title
        )
        Task {
            await NimboDiagnostics.shared.record(
                .info,
                stage: .config,
                code: "IOS_SUBSCRIPTION_PARSED",
                message: "Подписка разобрана и сохранена",
                metadata: [
                    "bytes": "\(data.count)",
                    "format": profile.format,
                    "servers": "\(profile.servers.count)",
                    "parser_revision": "\(profile.parserRevision)"
                ]
            )
        }
        return profile
    }

    func loadProfile(migratingLegacy: Bool = true) throws -> NimboSubscriptionProfile? {
        if let data = try NimboConfigurationStore.shared.loadProfile() {
            let profile = try decoder.decode(NimboSubscriptionProfile.self, from: data)
            return profile
        }
        guard migratingLegacy,
              let legacy = try NimboConfigurationStore.shared.loadConfiguration() else {
            return nil
        }
        return try importPayload(legacy, source: try NimboConfigurationStore.shared.loadSource())
    }

    /// Reparse subscriptions saved by an older parser after an application update.
    /// A remote subscription is fetched again so migration never collapses the
    /// profile to the single server that happened to be selected before updating.
    func migrateStoredProfileIfNeeded() async throws -> NimboSubscriptionProfile? {
        guard let profile = try loadProfile(migratingLegacy: true) else { return nil }
        guard SubscriptionParserMigration.shared.needsMigration(parserRevision: Int32(profile.parserRevision)) else {
            return profile
        }
        if (try NimboConfigurationStore.shared.loadSource()) != nil {
            return try await refresh()
        }
        guard let selected = profile.selectedServer else { return profile }
        return try importPayload(Data(selected.rawConfiguration.utf8), source: nil)
    }

    func select(serverID: String) throws -> NimboSubscriptionServer {
        guard let profile = try loadProfile(migratingLegacy: true),
              let server = profile.servers.first(where: { $0.id == serverID }) else {
            throw NimboSubscriptionRepositoryError.serverNotFound
        }
        try NimboConfigurationStore.shared.saveSelection(
            configuration: Data(server.rawConfiguration.utf8),
            serverID: server.id
        )
        return server
    }

    func refresh() async throws -> NimboSubscriptionProfile {
        guard let source = try NimboConfigurationStore.shared.loadSource(),
              let url = URL(string: source),
              ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
            throw NimboSubscriptionRepositoryError.sourceUnavailable
        }
        let request = NimboNetworkSession.subscriptionRequest(url: url)
        let (data, response) = try await NimboNetworkSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
            throw NimboSubscriptionRepositoryError.http((response as? HTTPURLResponse)?.statusCode ?? -1)
        }
        guard !data.isEmpty, data.count <= maximumInputBytes else {
            throw NimboSubscriptionRepositoryError.invalidSize
        }
        return try importPayload(data, source: source)
    }

    func rawProfileJSON() -> String? {
        guard let data = try? NimboConfigurationStore.shared.loadProfile() else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

enum NimboSubscriptionRepositoryError: LocalizedError {
    case invalidSize
    case invalidEncoding
    case bridgeEncoding
    case noSupportedServers(String?)
    case serverNotFound
    case sourceUnavailable
    case http(Int)

    var errorDescription: String? {
        switch self {
        case .invalidSize:
            "Ответ подписки пуст или превышает 15 МиБ (IOS_SUBSCRIPTION_SIZE)."
        case .invalidEncoding:
            "Ответ подписки не является UTF-8 текстом (IOS_SUBSCRIPTION_ENCODING)."
        case .bridgeEncoding:
            "Общий модуль вернул некорректный результат (IOS_SUBSCRIPTION_BRIDGE)."
        case let .noSupportedServers(code):
            "В подписке не найдено поддерживаемых серверов (\(code ?? "IOS_SUBSCRIPTION_EMPTY"))."
        case .serverNotFound:
            "Выбранный сервер больше не найден в подписке (IOS_SERVER_NOT_FOUND)."
        case .sourceUnavailable:
            "У профиля нет адреса для обновления (IOS_SUBSCRIPTION_SOURCE_MISSING)."
        case let .http(code):
            "Сервис подписки вернул HTTP \(code) (IOS_SUBSCRIPTION_HTTP)."
        }
    }
}
