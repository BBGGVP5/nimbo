import Foundation
import CryptoKit
import NimboShared

extension NimboSubscriptionProfile {
    var selectedServer: NimboSubscriptionServer? {
        let selectedID = NimboConfigurationStore.shared.activeServerID
        if selectedID == NimboStagingPayload.automaticServerID {
            return NimboStagingPayload.automaticServer(in: self) ?? servers.first
        }
        if let selectedID, let server = servers.first(where: { $0.id == selectedID }) { return server }
        let retained = try? NimboConfigurationStore.shared.loadConfiguration()
        if let recovered = NimboSelectedServerRecovery.exactID(configuration: retained,
            entries: servers.map { ($0.id, $0.rawConfiguration) }) {
            return servers.first(where: { $0.id == recovered })
        }
        if NimboSelectedServerRecovery.isAutomatic(retained), let automatic = NimboStagingPayload.automaticServer(in: self) {
            return automatic
        }
        return servers.first
    }
}

final class NimboSubscriptionRepository: @unchecked Sendable {
    static let shared = NimboSubscriptionRepository()

    // All remaining fields are immutable or protected by the operation gate.
    private static let workQueue = NimboVpnCommandQueue(label: "com.nimbo.subscription.work")
    private let operationGate = NimboSubscriptionOperationGate()
    var isWorking: Bool { operationGate.isWorking }

    private func runOperation(_ body: (NimboVpnCommandLease) async throws -> NimboSubscriptionProfile) async throws -> NimboSubscriptionProfile {
        let lease = try operationGate.begin()
        defer { operationGate.finish(lease) }
        return try await withTaskCancellationHandler(operation: { try await body(lease) },
            onCancel: { lease.invalidate() })
    }

    func importPayloadAsync(_ data: Data, source: String?) async throws -> NimboSubscriptionProfile {
        try await runOperation { lease in
            try await Self.workQueue.perform(lease: lease) {
                try self.importPayload(data, source: source, lease: lease)
            }
        }
    }
    private let maximumInputBytes = 15 * 1_024 * 1_024

    private init() {}

    func importPayload(_ data: Data, source: String?, title: String? = nil, lease: NimboVpnCommandLease? = nil) throws -> NimboSubscriptionProfile {
        guard !data.isEmpty, data.count <= maximumInputBytes else {
            throw NimboSubscriptionRepositoryError.invalidSize
        }
        guard let payload = String(data: data, encoding: .utf8) else {
            throw NimboSubscriptionRepositoryError.invalidEncoding
        }

        if NimboMihomoControl.looksLikeConfiguration(data) {
            return try importFullConfiguration(data, source: source, lease: lease)
        }

        var normalizedData: Data
        if let awg = try NimboAWGConfiguration.parseIfPresent(payload) {
            let id = "awg-" + SHA256.hash(data: Data(awg.rawText.utf8)).map { String(format: "%02x", $0) }.joined()
            let server = NimboSubscriptionServer(
                id: id, name: "AmneziaWG", protocol: "amneziawg", host: awg.host,
                port: awg.port, transport: "udp", security: "", rawConfiguration: awg.rawText,
                isNativeXrayJson: false
            )
            normalizedData = try JSONEncoder().encode(NimboSubscriptionProfile(
                parserRevision: Int(SubscriptionParserMigration.shared.currentRevision),
                title: "AmneziaWG", source: source, format: "amneziawg",
                servers: [server], diagnosticCode: nil
            ))
        } else {
            let json = SubscriptionPayloadBridgeKt.NimboParseSubscriptionPayload(payload: payload, source: source)
            guard let data = json.data(using: .utf8) else {
                throw NimboSubscriptionRepositoryError.bridgeEncoding
            }
            normalizedData = data
        }
        if let title = title?.trimmingCharacters(in: .whitespacesAndNewlines), !title.isEmpty,
           var object = try JSONSerialization.jsonObject(with: normalizedData) as? [String: Any] {
            object["title"] = title
            normalizedData = try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
        }
        let profile = try JSONDecoder().decode(NimboSubscriptionProfile.self, from: normalizedData)
        guard !profile.servers.isEmpty else {
            throw NimboSubscriptionRepositoryError.noSupportedServers(profile.diagnosticCode)
        }

        let previousID = NimboConfigurationStore.shared.activeServerID
        let selected = (previousID == NimboStagingPayload.automaticServerID
            ? NimboStagingPayload.automaticServer(in: profile) : nil)
            ?? profile.servers.first(where: { $0.id == previousID }) ?? profile.selectedServer ?? profile.servers[0]
        guard lease?.isActive != false else { throw CancellationError() }
        try NimboConfigurationStore.shared.save(
            profile: normalizedData,
            selectedServer: NimboStagingPayload.make(for: selected, in: profile),
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

    func loadProfile(migratingLegacy: Bool = false) throws -> NimboSubscriptionProfile? {
        // Never resurrect the retained Xray profile under an active full document.
        if let full = try NimboConfigurationStore.shared.loadFullConfiguration() { return fullConfigurationProfile(full) }
        if let data = try NimboConfigurationStore.shared.loadProfile() {
            let profile = try JSONDecoder().decode(NimboSubscriptionProfile.self, from: data)
            return profile
        }
        guard migratingLegacy,
              let legacy = try NimboConfigurationStore.shared.loadConfiguration() else {
            return nil
        }
        return try importPayload(legacy, source: try NimboConfigurationStore.shared.loadSource())
    }

    /// Restore/migrate the complete cached profile without requiring Internet.
    /// A reinstall may clear defaults while retaining Keychain material.
    func migrateStoredProfileIfNeeded() async throws -> NimboSubscriptionProfile? {
        let lease = try operationGate.begin()
        defer { operationGate.finish(lease) }
        return try await withTaskCancellationHandler(operation: {
            try await Self.workQueue.perform(lease: lease) { () throws -> NimboSubscriptionProfile? in
                if let full = try NimboConfigurationStore.shared.loadFullConfiguration() {
                    _ = try NimboMihomoControl.inspection(full) // Warm the UI's immutable graph cache off-main.
                    return self.fullConfigurationProfile(full)
                }
                guard let profile = try self.loadProfile(migratingLegacy: true) else { return nil }
                guard lease.isActive else { throw CancellationError() }
                if let selected = profile.selectedServer {
                    try NimboConfigurationStore.shared.retainSelectedServerID(selected.id)
                }
                guard SubscriptionParserMigration.shared.needsMigration(parserRevision: Int32(profile.parserRevision)) else { return profile }
                let links = profile.servers.filter { !$0.isNativeXrayJson }.map(\.rawConfiguration)
                guard links.count == profile.servers.count, !links.isEmpty else { return profile }
                return try self.importPayload(Data(links.joined(separator: "\n").utf8),
                    source: try self.refreshSource(), title: profile.title, lease: lease)
            }
        }, onCancel: { lease.invalidate() })
    }

    /// Данные для Packet Tunnel: для автобалансировщика это список реальных
    /// серверов профиля, для обычной записи — её собственная конфигурация.
    func stagingData(for server: NimboSubscriptionServer) -> Data {
        let profile = (try? loadProfile(migratingLegacy: false)) ?? nil
        return NimboStagingPayload.make(for: server, in: profile)
    }

    func select(serverID: String) throws -> NimboSubscriptionServer {
        guard try NimboConfigurationStore.shared.loadFullConfiguration() == nil else {
            throw NimboFullConfigurationError.fullConfigurationActive
        }
        guard let profile = try loadProfile(migratingLegacy: true),
              let server = serverID == NimboStagingPayload.automaticServerID
                ? NimboStagingPayload.automaticServer(in: profile)
                : profile.servers.first(where: { $0.id == serverID }) else {
            throw NimboSubscriptionRepositoryError.serverNotFound
        }
        try NimboConfigurationStore.shared.saveSelection(
            configuration: NimboStagingPayload.make(for: server, in: profile),
            serverID: server.id
        )
        return server
    }

    /// В старом/восстановленном профиле URL мог остаться только внутри JSON.
    private func refreshSource() throws -> String? {
        func valid(_ candidate: String?) -> String? {
            guard let source = candidate?.trimmingCharacters(in: .whitespacesAndNewlines),
                  let url = URL(string: source), url.host != nil,
                  ["http", "https"].contains(url.scheme?.lowercased() ?? "") else { return nil }
            return source
        }
        // Наличие корректного URL позволяет восстановить даже повреждённый
        // JSON профиля: не декодируем его без необходимости.
        if let source = valid(try NimboConfigurationStore.shared.loadSource()) { return source }
        return valid(try loadProfile(migratingLegacy: false)?.source)
    }

    func refresh() async throws -> NimboSubscriptionProfile {
        try await runOperation { lease in
            if let full = try await Self.workQueue.perform(lease: lease, {
                try NimboConfigurationStore.shared.loadFullConfiguration()
            }) {
                return try await self.refreshFullConfigurationImpl(full, lease: lease)
            }
            guard let source = try await Self.workQueue.perform(lease: lease, { try self.refreshSource() }) else {
                throw NimboSubscriptionRepositoryError.sourceUnavailable
            }
            return try await self.importRemoteImpl(source, lease: lease)
        }
    }

    func importRemote(_ source: String) async throws -> NimboSubscriptionProfile {
        try await runOperation { lease in try await self.importRemoteImpl(source, lease: lease) }
    }

    private func importRemoteImpl(_ source: String, lease: NimboVpnCommandLease) async throws -> NimboSubscriptionProfile {
        guard let url = URL(string: source), url.host != nil,
              ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
            throw NimboSubscriptionRepositoryError.sourceUnavailable
        }
        await NimboDiagnostics.shared.record(
            .info, stage: .config, code: "IOS_SUBSCRIPTION_REFRESH_STARTED",
            message: "Запрошено обновление подписки"
        )
        let request = try NimboNetworkSession.subscriptionRequest(url: url)
        let (data, response) = try await NimboNetworkSession.shared.data(for: request)
        await NimboDiagnostics.shared.record(
            .info, stage: .config, code: "IOS_SUBSCRIPTION_RESPONSE",
            message: "Получен ответ сервиса подписки",
            metadata: ["bytes": "\(data.count)",
                       "http_status": "\((response as? HTTPURLResponse)?.statusCode ?? -1)"]
        )
        guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
            throw NimboSubscriptionRepositoryError.http((response as? HTTPURLResponse)?.statusCode ?? -1)
        }
        guard !data.isEmpty, data.count <= maximumInputBytes else {
            throw NimboSubscriptionRepositoryError.invalidSize
        }
        // Имя владельца подписки, трафик и срок панель отдаёт заголовками —
        // в самих ссылках этого нет.
        // Не меняем метаданные действующего профиля при ошибке разбора.
        let meta = NimboSubscriptionMeta(headers: http.allHeaderFields)
        return try await Self.workQueue.perform(lease: lease) {
            let profile: NimboSubscriptionProfile
            if NimboMihomoControl.looksLikeConfiguration(data) {
                profile = try self.importFullConfiguration(data, source: source, title: meta.title, lease: lease)
            } else {
                let preference = try NimboCorePreference.decode(UserDefaults.standard.object(forKey: NimboCorePreference.defaultsKey))
                guard preference != .mihomo else { throw NimboFullConfigurationError.inspectionFailed }
                profile = try self.importPayload(data, source: source, title: meta.title, lease: lease)
            }
            NimboSubscriptionMetaStore.save(meta)
            return profile
        }
    }

    /// Import/restore an entire validated document; never project it into Server.
    @discardableResult
    func importFullConfiguration(_ configuration: NimboFullConfiguration, lease: NimboVpnCommandLease? = nil) throws -> NimboSubscriptionProfile {
        try configuration.validate()
        try NimboMihomoControl.preflight(configuration.sourceData)
        _ = try NimboMihomoControl.inspection(configuration)
        guard lease?.isActive != false else { throw CancellationError() }
        try NimboConfigurationStore.shared.saveFullConfiguration(configuration)
        return fullConfigurationProfile(configuration)
    }

    private func fullSource(_ data: Data) throws -> Data {
        try NimboFullConfiguration.sourceData(fromPayload: data)
    }

    private func importFullConfiguration(_ data: Data, source: String?, title: String? = nil, lease: NimboVpnCommandLease? = nil) throws -> NimboSubscriptionProfile {
        let sourceData = try fullSource(data)
        let previous = try NimboConfigurationStore.shared.loadFullConfiguration()
        let candidate = try NimboFullConfiguration(data: sourceData, source: source,
            title: title ?? (previous?.source == source ? previous?.title : nil) ?? "Mihomo",
            groupSelections: previous?.sourceSHA256 == NimboFullConfiguration.digest(sourceData)
                ? (previous?.groupSelections ?? [:]) : [:])
        return try importFullConfiguration(candidate, lease: lease)
    }

    /// The captured full record owns the request. Preference changes cannot turn a
    /// YAML refresh into a legacy migration, nor resurrect the retained Xray node.
    func refreshFullConfiguration(_ previous: NimboFullConfiguration) async throws -> NimboSubscriptionProfile {
        try await runOperation { lease in try await self.refreshFullConfigurationImpl(previous, lease: lease) }
    }

    private func refreshFullConfigurationImpl(_ previous: NimboFullConfiguration, lease: NimboVpnCommandLease) async throws -> NimboSubscriptionProfile {
        try await Self.workQueue.perform(lease: lease) { try previous.validate() }
        guard let source = previous.source, let url = URL(string: source) else {
            throw NimboSubscriptionRepositoryError.sourceUnavailable
        }
        let request = try NimboNetworkSession.subscriptionRequest(url: url, core: .mihomo)
        let (data, response) = try await NimboNetworkSession.shared.data(for: request)
        try Task.checkCancellation()
        guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
            throw NimboSubscriptionRepositoryError.http((response as? HTTPURLResponse)?.statusCode ?? -1)
        }
        guard !data.isEmpty, data.count <= NimboFullConfiguration.maximumSourceBytes else {
            throw NimboFullConfigurationError.invalidSize
        }
        let meta = NimboSubscriptionMeta(headers: http.allHeaderFields)
        return try await Self.workQueue.perform(lease: lease) {
            guard NimboMihomoControl.looksLikeConfiguration(data) else { throw NimboFullConfigurationError.inspectionFailed }
            let refreshed = try previous.replacingSource(self.fullSource(data))
            let candidate = try NimboFullConfiguration(data: refreshed.sourceData, source: refreshed.source,
                title: meta.title ?? refreshed.title, groupSelections: refreshed.groupSelections)
            try NimboMihomoControl.preflight(candidate.sourceData)
            _ = try NimboMihomoControl.inspection(candidate)
            guard lease.isActive else { throw CancellationError() }
            try NimboConfigurationStore.shared.saveFullConfiguration(candidate, expected: previous)
            NimboSubscriptionMetaStore.save(meta)
            return self.fullConfigurationProfile(candidate)
        }
    }

    /// Legacy offline backups also keep their whole server list and selected ID.
    /// Decoding and validation finish before the first Keychain write.
    func restoreLegacyProfile(_ data: Data, source: String?, selectedServerID: String?) throws {
        guard !data.isEmpty, data.count <= maximumInputBytes else {
            throw NimboSubscriptionRepositoryError.invalidSize
        }
        let decoded = try JSONDecoder().decode(NimboSubscriptionProfile.self, from: data)
        guard !decoded.servers.isEmpty, decoded.servers.count <= 20_000,
              Set(decoded.servers.map(\.id)).count == decoded.servers.count,
              decoded.servers.allSatisfy({ !$0.id.isEmpty && !$0.rawConfiguration.isEmpty }) else {
            throw NimboSubscriptionRepositoryError.noSupportedServers(decoded.diagnosticCode)
        }
        let profile = NimboSubscriptionProfile(parserRevision: decoded.parserRevision, title: decoded.title,
            source: source ?? decoded.source, format: decoded.format, servers: decoded.servers,
            diagnosticCode: decoded.diagnosticCode)
        let selected = (selectedServerID == NimboStagingPayload.automaticServerID
            ? NimboStagingPayload.automaticServer(in: profile) : nil)
            ?? profile.servers.first(where: { $0.id == selectedServerID }) ?? profile.servers[0]
        try NimboConfigurationStore.shared.save(profile: JSONEncoder().encode(profile),
            selectedServer: NimboStagingPayload.make(for: selected, in: profile),
            selectedServerID: selected.id, source: profile.source, description: profile.title)
    }

    private func fullConfigurationProfile(_ full: NimboFullConfiguration) -> NimboSubscriptionProfile {
        NimboSubscriptionProfile(parserRevision: Int(SubscriptionParserMigration.shared.currentRevision),
            title: full.title, source: full.source, format: "mihomo-yaml", servers: [], diagnosticCode: nil)
    }

    func rawProfileJSON() -> String? {
        do {
            guard try NimboConfigurationStore.shared.loadFullConfiguration() == nil else { return nil }
            guard let data = try NimboConfigurationStore.shared.loadProfile() else { return nil }
            return String(data: data, encoding: .utf8)
        } catch { return nil }
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
