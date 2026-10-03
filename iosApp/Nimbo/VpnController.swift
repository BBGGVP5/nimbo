import Foundation
import NetworkExtension
import WidgetKit
// Правила модулей разбирает общий модуль: конфигурацию туннеля собирает он же.
import NimboShared

@MainActor
final class VpnController: ObservableObject {
    enum State: Equatable {
        case idle
        case preparing
        case connecting
        case connected
        case disconnecting
        case failed(code: String, message: String)
    }

    @Published private(set) var state: State = .idle {
        didSet { refreshLiveActivity() }
    }
    @Published private(set) var manager: NETunnelProviderManager?
    @Published private(set) var isSavingCorePreference = false
    @Published private(set) var switchingServerID: String?
    private var selectionIntent = UUID()
    private var isStartingConnection = false
    private var isStagingConfiguration = false
    private var isSavingOnDemand = false

    /// Only observed NE state is published; a button press is not a connection.
    func refreshLiveActivity() {
        let phase: NimboLivePhase
        switch manager?.connection.status {
        case .connected?: phase = .connected
        case .connecting?: phase = .connecting
        case .reasserting?: phase = .recovering
        case .disconnecting?: phase = .disconnecting
        default: phase = .idle
        }
        NimboLiveActivityBridge.synchronize(phase)
    }

    /// Called before any disconnect, profile selection, or NetworkExtension write.
    @discardableResult
    func validateCore(data: Data) throws -> NimboCoreProfile {
        guard !isSavingCorePreference, !isStagingConfiguration, !isSavingOnDemand else { throw NimboCoreSelectionError.busy }
        let preference = UserDefaults.standard.object(forKey: NimboCorePreference.defaultsKey)
        if let full = try NimboConfigurationStore.shared.loadFullConfiguration() {
            let engine = try NimboCoreAdmission.validate(preference: preference, data: full.sourceData,
                                                   declaredEngine: full.coreId)
            try NimboMihomoControl.validateAdBlocking(full, enabled: NimboRoutingSettings.current.adBlockingEnabled)
            try NimboMihomoControl.preflight(full.sourceData)
            return engine
        }
        return try NimboCoreAdmission.validate(preference: preference, data: data)
    }

    /// Saves the next-start choice only. The active provider receives no command.
    func setCorePreference(_ preference: NimboCorePreference) async throws {
        guard preference.isAvailable else { throw NimboCoreSelectionError.unavailable }
        guard !isSavingCorePreference, !isStagingConfiguration, !isSavingOnDemand,
              state != .preparing, state != .connecting, state != .disconnecting else {
            throw NimboCoreSelectionError.busy
        }
        isSavingCorePreference = true
        defer { isSavingCorePreference = false }
        // There is no shared defaults entitlement: the saved protocol is the
        // transport for widget/system starts, even while the app is terminated.
        // Loading existing profiles does not create one or prompt for VPN access.
        let existing = try await NimboTunnelControl.manager()
        if let existing {
            guard let proto = existing.protocolConfiguration?.copy() as? NETunnelProviderProtocol else {
                throw VpnControllerError.managerUnavailable
            }
            var values = proto.providerConfiguration ?? [:]
            values[NimboCorePreference.providerKey] = preference.rawValue
            proto.providerConfiguration = values
            existing.protocolConfiguration = proto
            try await existing.saveToPreferences()
        }
        UserDefaults.standard.set(preference.rawValue, forKey: NimboCorePreference.defaultsKey)
    }

    private var statusObserver: NSObjectProtocol?
    /// Последний увиденный системный статус — для журнала переходов.
    private var lastKnownStatus: NEVPNStatus?
    /// Tracks request acceptance separately from observed NE startup progress.
    private var startAttempt = NimboVpnStartAttempt()
    /// Опрос статуса, пока состояние переходное.
    ///
    /// Уведомление `NEVPNStatusDidChange` приходит не всегда: смена статуса,
    /// случившаяся до загрузки менеджера, теряется, и экран остаётся с
    /// вращающейся кнопкой. Опрос закрывает эту дыру.
    private var statusPollTimer: Timer?
    /// Когда началось текущее переходное состояние: по нему считается предел
    /// ожидания.
    private var transitionStartedAt: Date?
    /// Когда был запрошен запуск: по времени до обрыва видно, «не поднялось
    /// расширение» (доли секунды) или «не удалось достучаться до сервера».
    private var startRequestedAt: Date?

    init() {
        statusObserver = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            guard let connection = notification.object as? NEVPNConnection else { return }
            Task { @MainActor in
                guard let self, connection === self.manager?.connection else { return }
                self.synchronizeStatus()
            }
        }
    }

    deinit {
        if let statusObserver { NotificationCenter.default.removeObserver(statusObserver) }
    }

    func prepare() async {
        guard !isSavingCorePreference, !isStagingConfiguration, !isSavingOnDemand else { return }
        state = .preparing
        await NimboDiagnostics.shared.record(
            .info,
            stage: .permission,
            code: "IOS_VPN_PREPARE",
            message: "Подготовка системной конфигурации VPN",
            metadata: signingContractMetadata
        )
        // Подпись — первое, обо что разбивается запуск туннеля на чужом
        // сертификате, и увидеть её без Mac иначе нечем.
        await NimboDiagnostics.shared.record(
            NimboSigningReport.problem == nil ? .info : .warning,
            stage: .permission,
            code: "IOS_SIGNING_REPORT",
            message: NimboSigningReport.problem ?? "Подпись приложения и расширения выглядит пригодной",
            metadata: NimboSigningReport.summary
        )
        do {
            // Reopening the app must not validate a next-start preference against
            // an already running session or change that session's NE profile.
            let existing = try await NimboTunnelControl.manager()
            if let existing {
                switch existing.connection.status {
                case .connected, .connecting, .reasserting, .disconnecting:
                    manager = existing
                    synchronizeStatus()
                    return
                default: break
                }
            }
            let stored = try NimboConfigurationStore.shared.loadConfiguration()
            if let stored { try validateCore(data: stored) }
            manager = try await loadOrCreateManager()
            if !hasStagedConfiguration, let stored {
                try await stageConfiguration(data: stored)
            }
            synchronizeStatus()
        } catch {
            fail(code: "IOS_VPN_MANAGER_LOAD_FAILED", error: error)
        }
    }

    func stageConfiguration(json: String) async throws {
        guard let data = json.data(using: .utf8) else {
            throw VpnControllerError.emptyConfiguration
        }
        try await stageConfiguration(data: data)
    }

    func stageConfiguration(data: Data) async throws {
        guard !data.isEmpty else { throw VpnControllerError.emptyConfiguration }
        guard data.count <= 15 * 1_024 * 1_024 else { throw VpnControllerError.configurationTooLarge }
        let profileEngine = try validateCore(data: data)
        let preference = try NimboCorePreference.decode(
            UserDefaults.standard.object(forKey: NimboCorePreference.defaultsKey)
        )
        guard !isSavingCorePreference, !isStagingConfiguration, !isSavingOnDemand else { throw NimboCoreSelectionError.busy }
        isStagingConfiguration = true
        defer { isStagingConfiguration = false }
        if manager == nil { manager = try await loadOrCreateManager() }
        guard let manager,
              let tunnelProtocol = manager.protocolConfiguration as? NETunnelProviderProtocol else {
            throw VpnControllerError.managerUnavailable
        }
        // Preserve NetworkExtension's physical-network exemption for sockets
        // created by the provider (including the encrypted AWG UDP endpoint).
        // The default IPv4/IPv6 routes still capture the device's app traffic.
        tunnelProtocol.includeAllNetworks = false
        tunnelProtocol.providerConfiguration = [
            "schema": 2,
            NimboOnDemandRules.providerKey: try JSONEncoder().encode(NimboOnDemandSettings.load()),
            "configData": data,
            "mihomoSelections": (try NimboConfigurationStore.shared.loadFullConfiguration())?.groupSelections ?? [:],
            NimboCorePreference.providerKey: preference.rawValue,
            NimboCorePreference.profileEngineKey: profileEngine.rawValue,
            // Attribute only bytes that actually belong to the selected profile entry.
            "pingServerID": verifiedPingServerID(for: data) ?? "",
            // Маршрутизация едет отдельным ключом: конфигурацию ядра она не
            // трогает, зато нужна расширению для системных настроек туннеля.
            "routing": NimboRoutingSettings.current.providerValue,
            // Правила пользовательских модулей. Разбирает их общий модуль на
            // Kotlin — расширение получает готовый массив, потому что общих
            // NSUserDefaults у приложения и расширения нет.
            "modules": IosComposeControllerKt.NimboIosModuleRulesJson(),
            "routingProfile": IosComposeControllerKt.NimboIosRoutingProfileJson()
        ]
        manager.protocolConfiguration = tunnelProtocol
        try await manager.saveToPreferences()
        try await manager.loadFromPreferences()
        await NimboDiagnostics.shared.record(
            .info,
            stage: .config,
            code: "IOS_CONFIG_STAGED",
            message: "Активная конфигурация безопасно передана Packet Tunnel",
            metadata: [
                "bytes": "\(data.count)",
                "schema": "2",
                "selected_server_id_present": NimboConfigurationStore.shared.activeServerID == nil ? "false" : "true"
            ]
        )
    }

    func clearConfiguration() async throws {
        if state == .connected || state == .connecting { await disconnect() }
        if manager == nil { manager = try await loadOrCreateManager() }
        guard let manager,
              let tunnelProtocol = manager.protocolConfiguration as? NETunnelProviderProtocol else {
            throw VpnControllerError.managerUnavailable
        }
        tunnelProtocol.providerConfiguration = ["schema": 2]
        manager.protocolConfiguration = tunnelProtocol
        // Поднимать нечего: без конфигурации автоподъём только плодил бы
        // неудачные запуски.
        try? await setOnDemand(false, on: manager)
        try await manager.saveToPreferences()
        try await manager.loadFromPreferences()
    }

    private func verifiedPingServerID(for data: Data) -> String? {
        guard let profile = try? NimboSubscriptionRepository.shared.loadProfile(),
              let server = profile.selectedServer,
              NimboSubscriptionRepository.shared.stagingData(for: server) == data else { return nil }
        return server.id
    }

    func providerStatus() async throws -> [String: Any] {
        try await sendProviderCommand("status")
    }

    func providerDiagnostics() async throws -> Data {
        let response = try await sendProviderCommand("diagnostics")
        guard response["ok"] as? Bool == true,
              let records = response["records"] as? String else {
            throw VpnControllerError.providerMessageEmpty
        }
        let summary: [String: Any] = [
            "core_version": response["version"] as? String ?? "unknown",
            "core_running": response["running"] as? Bool ?? false,
            "outbounds": response["outbounds"] as? Int ?? 0
        ]
        var result = (try? JSONSerialization.data(withJSONObject: summary, options: [.prettyPrinted, .sortedKeys])) ?? Data()
        result.append(Data("\n\nPACKET TUNNEL EVENTS\n".utf8))
        result.append(Data(records.utf8))
        return result
    }

    private func sendProviderCommand(_ command: String, fields: [String: Any] = [:]) async throws -> [String: Any] {
        guard let session = manager?.connection as? NETunnelProviderSession else {
            throw VpnControllerError.managerUnavailable
        }
        var requestFields = fields
        requestFields["command"] = command
        let request = try JSONSerialization.data(withJSONObject: requestFields)
        let response: Data = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            do {
                try session.sendProviderMessage(request) { data in
                    if let data { continuation.resume(returning: data) }
                    else { continuation.resume(throwing: VpnControllerError.providerMessageEmpty) }
                }
            } catch {
                continuation.resume(throwing: error)
            }
        }
        guard let object = try JSONSerialization.jsonObject(with: response) as? [String: Any] else {
            throw VpnControllerError.providerMessageEmpty
        }
        return object
    }

    /// Показания туннеля: байты и занятая расширением память.
    ///
    /// Спрашиваем у самого расширения: приложение видит несколько utun и не
    /// может отличить наш от системного, а память расширения ему недоступна.
    func tunnelMetrics(includeTelemetry: Bool = false) async -> NimboTunnelReport? {
        guard case .connected = state else { return nil }
        let connectedAt = manager?.connection.connectedDate
        guard let response = try? await sendProviderCommand("metrics", fields: ["includeTelemetry": includeTelemetry]),
              case .connected = state, manager?.connection.connectedDate == connectedAt else { return nil }
        return NimboTunnelReport.decode(response)
    }

    func connect(selectionOwner: UUID? = nil) async {
        guard !isStartingConnection, switchingServerID == nil || selectionOwner == selectionIntent else { return }
        guard state != .connected, state != .connecting, state != .preparing,
              state != .disconnecting, !isSavingCorePreference, !isStagingConfiguration, !isSavingOnDemand else { return }
        isStartingConnection = true
        defer { isStartingConnection = false }
        let attempt = startAttempt.begin()
        statusPollTimer?.invalidate()
        statusPollTimer = nil
        transitionStartedAt = nil
        startRequestedAt = nil
        state = .connecting
        do {
            // Full documents take precedence over retained legacy server records.
            // Admission runs before loadOrCreateManager can change NE preferences.
            if let full = try NimboConfigurationStore.shared.loadFullConfiguration() {
                try validateCore(data: full.sourceData)
            }
            // Подписка сохраняется независимо от VPN permissions. Перед
            // запуском используем актуальный, а не ранее переданный профиль.
            if let profile = try NimboSubscriptionRepository.shared.loadProfile(migratingLegacy: false),
               let selected = profile.selectedServer {
                try await stageConfiguration(
                    data: NimboSubscriptionRepository.shared.stagingData(for: selected)
                )
            } else if let stored = try NimboConfigurationStore.shared.loadConfiguration() {
                try await stageConfiguration(data: stored)
            } else if let proto = manager?.protocolConfiguration as? NETunnelProviderProtocol,
                      let staged = proto.providerConfiguration?["configData"] as? Data {
                try NimboCoreAdmission.validate(
                    preference: UserDefaults.standard.object(forKey: NimboCorePreference.defaultsKey),
                    data: staged,
                    declaredEngine: proto.providerConfiguration?[NimboCorePreference.profileEngineKey]
                )
                try await stageConfiguration(data: staged)
            } else {
                throw VpnControllerError.missingConfiguration
            }
            guard startAttempt.isCurrent(attempt) else { return }
            if manager == nil {
                let loaded = try await loadOrCreateManager()
                guard startAttempt.isCurrent(attempt) else { return }
                manager = loaded
            }
            guard let manager else { throw VpnControllerError.managerUnavailable }
            guard let tunnelProtocol = manager.protocolConfiguration as? NETunnelProviderProtocol,
                  let configuration = tunnelProtocol.providerConfiguration?["configData"] as? Data,
                  !configuration.isEmpty else {
                throw VpnControllerError.missingConfiguration
            }
            try NimboCoreAdmission.validate(
                preference: tunnelProtocol.providerConfiguration?[NimboCorePreference.providerKey],
                data: configuration,
                declaredEngine: tunnelProtocol.providerConfiguration?[NimboCorePreference.profileEngineKey]
            )
            state = .connecting
            await NimboDiagnostics.shared.record(.info, stage: .tunnelStart, code: "IOS_TUNNEL_START_REQUESTED", message: "Запуск Packet Tunnel запрошен пользователем")
            guard startAttempt.isCurrent(attempt) else { return }
            // Only a user opt-in may arm On Demand. The same admitted staged
            // profile is used by app, widget and subsequent system starts.
            try await NimboOnDemandRules.persist(NimboOnDemandSettings.load(), on: manager)
            if !startAttempt.isCurrent(attempt) {
                // A user stop can race an awaited preferences write. No newer
                // app start is allowed until this owner exits; restore pause.
                try await setOnDemand(false, on: manager)
                manager.connection.stopVPNTunnel()
                synchronizeStatus()
                return
            }
            // Arming On Demand may already have started the same tunnel.
            // Do not issue a second start or turn a working session into failure.
            switch manager.connection.status {
            case .connected, .connecting, .reasserting:
                synchronizeStatus()
                return
            case .disconnecting: throw NimboCoreSelectionError.busy
            default: break
            }
            startAttempt.requestedStart(for: attempt)
            startRequestedAt = Date()
            transitionStartedAt = startRequestedAt
            try manager.connection.startVPNTunnel()
            // Статус мог смениться прямо сейчас: уведомления об этом может уже
            // не быть, поэтому спрашиваем сами.
            synchronizeStatus()
        } catch {
            guard startAttempt.isCurrent(attempt) else { return }
            startAttempt.invalidate()
            fail(code: "IOS_TUNNEL_START_FAILED", error: error)
            scheduleStatusPollIfNeeded()
        }
    }

    func disconnect(invalidateSelection: Bool = true) async {
        if invalidateSelection { selectionIntent = UUID() }
        // Осознанное отключение не должно выглядеть как сбой запуска.
        startAttempt.invalidate()
        transitionStartedAt = nil
        state = .disconnecting
        // Менеджера может не быть: приложение перезапустили, а туннель поднят
        // системой. Без загрузки остановка не дошла бы до него, и кнопка
        // крутилась бы вечно.
        if manager == nil { manager = try? await loadOrCreateManager() }
        // Правило могло остаться от прежней версии: без снятия система
        // подняла бы туннель обратно через секунду, и кнопка выглядела бы
        // сломанной.
        if let manager {
            do { try await setOnDemand(false, on: manager) }
            catch { fail(code: "IOS_ON_DEMAND_PAUSE_FAILED", error: error); return }
        }
        manager?.connection.stopVPNTunnel()
        synchronizeStatus()
        await NimboDiagnostics.shared.record(.info, stage: .stop, code: "IOS_TUNNEL_STOP_REQUESTED", message: "Остановка Packet Tunnel запрошена пользователем")
    }

    /// One owner for native rows and Compose actions. Never start until the old
    /// provider has actually stopped; manual disconnect revokes this intent.
    func selectServer(_ serverID: String) async throws -> (server: NimboSubscriptionServer, reconnecting: Bool) {
        guard switchingServerID == nil, !isStartingConnection, !isSavingCorePreference, !isStagingConfiguration,
              !isSavingOnDemand, state != .preparing, state != .disconnecting else {
            throw NimboCoreSelectionError.busy
        }
        guard let profile = try NimboSubscriptionRepository.shared.loadProfile(migratingLegacy: false),
              let candidate = serverID == NimboStagingPayload.automaticServerID
                ? NimboStagingPayload.automaticServer(in: profile)
                : profile.servers.first(where: { $0.id == serverID }) else {
            throw NimboSubscriptionRepositoryError.serverNotFound
        }
        let data = NimboStagingPayload.make(for: candidate, in: profile)
        // Incompatible selections must leave the current tunnel untouched.
        _ = try validateCore(data: data)
        let wasActive = state == .connected || state == .connecting ||
            manager?.connection.status == .connected || manager?.connection.status == .connecting ||
            manager?.connection.status == .reasserting
        if wasActive, NimboConfigurationStore.shared.activeServerID == serverID, profile.selectedServer?.id == candidate.id,
           let proto = manager?.protocolConfiguration as? NETunnelProviderProtocol,
           proto.providerConfiguration?["configData"] as? Data == data {
            return (candidate, false)
        }
        switchingServerID = serverID
        selectionIntent = UUID()
        let intent = selectionIntent
        defer { switchingServerID = nil }
        let selected = try await NimboProfileSelection.apply(
            validate: { _ = try self.validateCore(data: data) },
            stop: {
                guard wasActive else { return }
                await self.disconnect(invalidateSelection: false)
                if case let .failed(_, message) = self.state { throw VpnControllerError.switchFailed(message) }
                guard self.manager != nil else { throw VpnControllerError.managerUnavailable }
                let deadline = ProcessInfo.processInfo.systemUptime + 15
                while let status = self.manager?.connection.status, status != .disconnected && status != .invalid {
                    guard self.selectionIntent == intent else { throw CancellationError() }
                    try Task.checkCancellation()
                    guard ProcessInfo.processInfo.systemUptime < deadline else { throw VpnControllerError.switchStopTimeout }
                    try await Task.sleep(nanoseconds: 100_000_000)
                }
                self.synchronizeStatus()
            },
            persist: {
                guard self.selectionIntent == intent, !Task.isCancelled else { throw CancellationError() }
                return try NimboSubscriptionRepository.shared.select(serverID: serverID)
            },
            stage: { _ in try await self.stageConfiguration(data: data) },
            restart: {
                guard wasActive else { return }
                guard self.selectionIntent == intent, !Task.isCancelled else { throw CancellationError() }
                // connect publishes .connecting before its first suspension.
                await self.connect(selectionOwner: intent)
                if case let .failed(_, message) = self.state { throw VpnControllerError.switchFailed(message) }
                guard self.state == .connected || self.state == .connecting else { throw NimboCoreSelectionError.busy }
            }
        )
        return (selected, wasActive)
    }

    /// Reading settings never creates a VPN profile, saves NE preferences or
    /// enables paused rules. Recover the stored choice from the system profile.
    func loadOnDemandSettings() async throws -> (settings: NimboOnDemandSettings, armed: Bool) {
        let existing = try await NimboTunnelControl.manager()
        let proto = existing?.protocolConfiguration as? NETunnelProviderProtocol
        let settings = NimboOnDemandSettings.restored(
            local: UserDefaults.standard.data(forKey: NimboOnDemandSettings.preferenceKey),
            staged: proto?.providerConfiguration?[NimboOnDemandRules.providerKey] as? Data)
        return (settings, existing?.isOnDemandEnabled == true)
    }

    /// Explicit settings save can arm system starts. Never called on launch.
    func saveOnDemandSettings(_ candidate: NimboOnDemandSettings) async throws {
        guard !isSavingOnDemand, !isSavingCorePreference, !isStagingConfiguration,
              state != .preparing, state != .connecting, state != .disconnecting else {
            throw NimboCoreSelectionError.busy
        }
        let settings = try candidate.validated()
        isSavingOnDemand = true
        defer { isSavingOnDemand = false }
        guard let existing = try await NimboTunnelControl.manager() else {
            guard !settings.enabled else { throw VpnControllerError.missingConfiguration }
            UserDefaults.standard.set(try JSONEncoder().encode(settings),
                                      forKey: NimboOnDemandSettings.preferenceKey)
            return
        }
        guard let proto = existing.protocolConfiguration?.copy() as? NETunnelProviderProtocol else {
            throw VpnControllerError.managerUnavailable
        }
        if settings.enabled {
            guard let data = proto.providerConfiguration?["configData"] as? Data, !data.isEmpty else {
                throw VpnControllerError.missingConfiguration
            }
            try NimboCoreAdmission.validate(
                preference: proto.providerConfiguration?[NimboCorePreference.providerKey], data: data,
                declaredEngine: proto.providerConfiguration?[NimboCorePreference.profileEngineKey])
        }
        let encoded = try JSONEncoder().encode(settings)
        var values = proto.providerConfiguration ?? [:]
        values[NimboOnDemandRules.providerKey] = encoded
        proto.providerConfiguration = values
        existing.protocolConfiguration = proto
        existing.isEnabled = true
        try await NimboOnDemandRules.persist(settings, on: existing)
        UserDefaults.standard.set(encoded, forKey: NimboOnDemandSettings.preferenceKey)
        manager = existing
        synchronizeStatus()
    }

    /// Manual stop pauses system rules, but preserves saved opt-in for resume.
    private func setOnDemand(_ enabled: Bool, on manager: NETunnelProviderManager) async throws {
        try await NimboOnDemandRules.persist(
            enabled ? NimboOnDemandSettings.load() : NimboOnDemandSettings(), on: manager)
    }

    private func loadOrCreateManager() async throws -> NETunnelProviderManager {
        let wanted = NimboConstants.packetTunnelBundleIdentifier
        let all = try await NETunnelProviderManager.loadAllFromPreferences()
        let existing = all.first {
            ($0.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier == wanted
        }

        // Наши прежние записи, указывающие на другое расширение, удаляем:
        // конфигурация хранится в системе и переустановку приложения
        // переживает, а подключиться по ней уже нельзя.
        for stale in all where stale !== existing {
            let identifier = (stale.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier
            guard let identifier,
                  identifier != wanted,
                  identifier.hasSuffix(".PacketTunnel") || stale.localizedDescription == "Nimbo" else {
                continue
            }
            try? await stale.removeFromPreferences()
            await NimboDiagnostics.shared.record(
                .warning,
                stage: .config,
                code: "IOS_VPN_STALE_PROFILE_REMOVED",
                message: "Удалена прежняя конфигурация VPN, указывавшая на другое расширение",
                metadata: ["previous": identifier, "current": wanted]
            )
        }

        let value = existing ?? NETunnelProviderManager()
        let tunnelProtocol = (value.protocolConfiguration as? NETunnelProviderProtocol) ?? NETunnelProviderProtocol()
        tunnelProtocol.providerBundleIdentifier = NimboConstants.packetTunnelBundleIdentifier
        tunnelProtocol.serverAddress = "Nimbo"
        // Сон устройства не повод рвать туннель: иначе утром человек находит
        // приложение отключённым.
        tunnelProtocol.disconnectOnSleep = false
        if tunnelProtocol.providerConfiguration == nil {
            tunnelProtocol.providerConfiguration = ["schema": 2]
        }
        value.protocolConfiguration = tunnelProtocol
        value.localizedDescription = "Nimbo"
        value.isEnabled = true
        // Recover preferences used by the next explicit connect. Merely loading
        // a manually paused system profile does not enable its on-demand rules.
        let restored = NimboOnDemandSettings.restored(
            local: UserDefaults.standard.data(forKey: NimboOnDemandSettings.preferenceKey),
            staged: tunnelProtocol.providerConfiguration?[NimboOnDemandRules.providerKey] as? Data)
        UserDefaults.standard.set(try JSONEncoder().encode(restored), forKey: NimboOnDemandSettings.preferenceKey)
        // Preserve opt-in rules on reload. New/legacy profiles without our
        // explicit settings are disabled; old unconditional rules never migrate.
        if existing == nil || tunnelProtocol.providerConfiguration?[NimboOnDemandRules.providerKey] == nil {
            value.isOnDemandEnabled = false
            value.onDemandRules = []
        }
        try await value.saveToPreferences()
        try await value.loadFromPreferences()
        return value
    }

    private func synchronizeStatus() {
        guard let status = manager?.connection.status else {
            // Менеджер ещё не загружен: подождём и спросим снова, иначе
            // состояние застынет на переходном.
            scheduleStatusPollIfNeeded()
            return
        }
        let previous = lastKnownStatus
        lastKnownStatus = status

        if previous != status {
            if #available(iOS 18.0, *) {
                ControlCenter.shared.reloadControls(ofKind: "com.nimbo.control.vpn")
            }
            let statusName = Self.statusName(status)
            Task {
                await NimboDiagnostics.shared.record(
                    .info,
                    stage: .tunnelStart,
                    code: "IOS_VPN_STATUS",
                    message: "Системный статус VPN: \(statusName)",
                    metadata: [
                        "status": statusName,
                        "previous": previous.map(Self.statusName) ?? "unknown"
                    ]
                )
            }
        }

        switch status {
        case .invalid, .disconnected:
            switch startAttempt.disconnectedAction() {
            case .waitForStart:
                // startVPNTunnel returns before NE necessarily leaves its old
                // disconnected status. Continue polling, with the 30s watchdog.
                state = .connecting
            case .reportFailure:
                reportUnexpectedDisconnect()
            case .preserveFailure:
                break
            case .idle:
                // Duplicate terminal notifications must not erase real errors.
                if case .failed = state { break }
                state = .idle
            }
        case .connecting, .reasserting:
            startAttempt.observedProgress()
            state = .connecting
        case .connected:
            startAttempt.invalidate()
            state = .connected
        case .disconnecting:
            if startAttempt.isPreparing {
                state = .connecting
            } else {
                startAttempt.observedProgress()
                state = .disconnecting
            }
        @unknown default: state = .failed(code: "IOS_VPN_UNKNOWN_STATE", message: "Неизвестное состояние системного VPN")
        }

        scheduleStatusPollIfNeeded()
    }

    /// Переходное ли состояние: в нём кнопка показывает вращение.
    private var isTransitional: Bool {
        switch state {
        case .preparing, .connecting, .disconnecting: true
        default: false
        }
    }

    /// Пока состояние переходное, статус опрашивается сам.
    ///
    /// Заодно считается предел ожидания: висеть с вращающейся кнопкой хуже,
    /// чем честно сказать, что не получилось.
    private func scheduleStatusPollIfNeeded() {
        // Saving preferences / waiting for permission is not yet a tunnel start.
        // Avoid a second loadOrCreateManager while connect() is awaiting one.
        guard !startAttempt.isPreparing else { return }
        guard isTransitional else {
            statusPollTimer?.invalidate()
            statusPollTimer = nil
            transitionStartedAt = nil
            return
        }

        if transitionStartedAt == nil { transitionStartedAt = Date() }
        if let startedAt = transitionStartedAt {
            let waited = Date().timeIntervalSince(startedAt)
            // Отключение система выполняет быстро; если за пять секунд статус
            // не пришёл, туннеля уже нет — показываем покой.
            if case .disconnecting = state, !startAttempt.isPending, waited > 5 {
                statusPollTimer?.invalidate()
                statusPollTimer = nil
                transitionStartedAt = nil
                startAttempt.invalidate()
                state = .idle
                return
            }
            if waited > 30 {
                statusPollTimer?.invalidate()
                statusPollTimer = nil
                transitionStartedAt = nil
                startAttempt.invalidate()
                state = .failed(
                    code: "IOS_VPN_START_TIMEOUT",
                    message: "Система не подняла туннель за 30 секунд. Проверьте профиль VPN в настройках iOS."
                )
                return
            }
        }

        guard statusPollTimer == nil else { return }
        statusPollTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                if self.manager == nil {
                    self.manager = try? await self.loadOrCreateManager()
                }
                self.synchronizeStatus()
            }
        }
    }

    /// Спрашиваем систему, почему соединение разорвалось. Без этого в логе
    /// оставался только запрос на запуск, а причина не попадала никуда.
    private func reportUnexpectedDisconnect() {
        let attempt = startAttempt.generation
        // NE can recover before its asynchronous error response arrives. Publishing
        // failure here already sends RootView's error toast, even when the scoped
        // callback correctly rejects that response after a successful connection.
        // Keep polling (and the original 30s watchdog) until diagnosis is current.
        // applyDisconnectError preserves both real system errors and unknown drops.
        state = .connecting

        guard let connection = manager?.connection else { return }
        if #available(iOS 16.0, *) {
            connection.fetchLastDisconnectError { [weak self, weak connection] error in
                Task { @MainActor in
                    guard let self, let connection else { return }
                    self.applyDisconnectError(error, attempt: attempt, connection: connection)
                }
            }
        }
    }

    /// Сколько миллисекунд прожила попытка подключения.
    private var elapsedSinceStartMetadata: [String: String] {
        guard let startRequestedAt else { return [:] }
        let elapsed = Int(Date().timeIntervalSince(startRequestedAt) * 1000)
        return ["elapsed_ms": "\(elapsed)"]
    }

    private func applyDisconnectError(_ error: Error?, attempt: UInt64, connection: NEVPNConnection) {
        guard startAttempt.acceptsDisconnectError(for: attempt),
              connection === manager?.connection else { return }
        guard connection.status == .disconnected || connection.status == .invalid else {
            // A reconnect may beat its notification to the main queue.
            synchronizeStatus()
            return
        }
        guard let error else {
            state = .failed(
                code: "IOS_TUNNEL_DROPPED",
                message: "VPN отключился до завершения подключения. iOS не сообщила причину. Откройте диагностику и повторите подключение."
            )
            Task {
                await NimboDiagnostics.shared.record(
                    .error,
                    stage: .tunnelStart,
                    code: "IOS_TUNNEL_DROPPED",
                    message: "Расширение остановилось без сообщения об ошибке",
                    metadata: signingContractMetadata.merging(elapsedSinceStartMetadata) { _, new in new }
                )
            }
            return
        }

        let presentation = errorPresentation(defaultCode: "IOS_TUNNEL_DROPPED", error: error)
        state = .failed(
            code: presentation.code,
            message: presentation.message
        )
        Task {
            var metadata = signingContractMetadata
            metadata["error_domain"] = presentation.domain
            metadata["error_number"] = presentation.number
            metadata.merge(elapsedSinceStartMetadata) { _, new in new }
            await NimboDiagnostics.shared.record(
                .error,
                stage: .tunnelStart,
                code: presentation.code,
                message: presentation.message,
                metadata: metadata
            )
        }
    }

    private static func statusName(_ status: NEVPNStatus) -> String {
        switch status {
        case .invalid: return "invalid"
        case .disconnected: return "disconnected"
        case .connecting: return "connecting"
        case .connected: return "connected"
        case .reasserting: return "reasserting"
        case .disconnecting: return "disconnecting"
        @unknown default: return "unknown"
        }
    }

    private var hasStagedConfiguration: Bool {
        guard let tunnelProtocol = manager?.protocolConfiguration as? NETunnelProviderProtocol,
              let data = tunnelProtocol.providerConfiguration?["configData"] as? Data else {
            return false
        }
        return !data.isEmpty
    }

    private func fail(code: String, error: Error) {
        let presentation = errorPresentation(defaultCode: code, error: error)
        state = .failed(code: presentation.code, message: presentation.message)
        Task {
            var metadata = signingContractMetadata
            metadata["error_domain"] = presentation.domain
            metadata["error_number"] = presentation.number
            metadata["raw_error"] = NimboRedactor.redact(error.localizedDescription)
            await NimboDiagnostics.shared.record(
                .error,
                stage: .tunnelStart,
                code: presentation.code,
                message: presentation.message,
                metadata: metadata
            )
        }
    }

    private func errorPresentation(
        defaultCode: String,
        error: Error
    ) -> (code: String, message: String, domain: String, number: String) {
        let nsError = error as NSError
        // Расширение упаковывает причину в домен: только домен и код переживают
        // передачу ошибки между процессами.
        let smuggled = nsError.domain.hasPrefix("Nimbo: ")
            ? String(nsError.domain.dropFirst("Nimbo: ".count))
            : nil
        let described = smuggled ?? error.localizedDescription
        let raw = described.lowercased()
        let permissionDenied = raw.contains("permission denied")
            || raw.contains("not permitted")
            || (nsError.domain == NSCocoaErrorDomain && nsError.code == NSFileWriteNoPermissionError)

        // NEVPNErrorDomain 5 (configurationReadWriteFailed) прилетает, пока
        // пользователь не подтвердил системный запрос на добавление
        // VPN-конфигурации. Это не проблема подписи, и советовать
        // переподписывать приложение здесь неверно.
        if nsError.domain == NEVPNErrorDomain,
           let vpnCode = NEVPNError.Code(rawValue: nsError.code),
           vpnCode == .configurationReadWriteFailed || vpnCode == .configurationDisabled {
            return (
                "IOS_VPN_CONFIG_NOT_APPROVED",
                "iOS ещё не разрешила добавить VPN-конфигурацию. Подтвердите системный запрос — он появляется при первом подключении.",
                nsError.domain,
                "\(nsError.code)"
            )
        }

        if permissionDenied {
            return (
                "IOS_VPN_PERMISSION_DENIED",
                "Операция VPN завершилась отказом в доступе. \(NimboRedactor.redact(described))",
                nsError.domain,
                "\(nsError.code)"
            )
        }

        return (
            defaultCode,
            NimboRedactor.redact(described),
            nsError.domain,
            "\(nsError.code)"
        )
    }

    private var signingContractMetadata: [String: String] {
        let extensionURL = Bundle.main.builtInPlugInsURL?
            .appendingPathComponent("NimboPacketTunnel.appex", isDirectory: true)
        return [
            "main_bundle_id": NimboConstants.mainBundleIdentifier,
            "provider_bundle_id": NimboConstants.packetTunnelBundleIdentifier,
            "provider_embedded": extensionURL.map { FileManager.default.fileExists(atPath: $0.path) } == true ? "true" : "false",
            "required_entitlement": "packet-tunnel-provider",
            "install_note": "main app and embedded extension must be re-signed together"
        ]
    }
}

enum VpnControllerError: LocalizedError {
    case emptyConfiguration
    case missingConfiguration
    case managerUnavailable
    case configurationTooLarge
    case providerMessageEmpty
    case switchStopTimeout
    case switchFailed(String)

    var errorDescription: String? {
        switch self {
        case .emptyConfiguration: "Конфигурация пуста (IOS_CONFIG_EMPTY)."
        case .missingConfiguration: "Сначала выберите сервер или импортируйте подписку (IOS_CONFIG_MISSING)."
        case .managerUnavailable: "Системная конфигурация VPN недоступна (IOS_VPN_MANAGER_UNAVAILABLE)."
        case .configurationTooLarge: "Конфигурация превышает лимит 15 МиБ (IOS_CONFIG_TOO_LARGE)."
        case .providerMessageEmpty: "Расширение VPN не вернуло статус (IOS_PROVIDER_MESSAGE_EMPTY)."
        case .switchStopTimeout: "Не удалось дождаться остановки VPN. Новый сервер не запущен."
        case let .switchFailed(message): message
        }
    }
}
