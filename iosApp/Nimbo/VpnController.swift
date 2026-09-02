import Foundation
import NetworkExtension
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

    @Published private(set) var state: State = .idle
    @Published private(set) var manager: NETunnelProviderManager?

    private var statusObserver: NSObjectProtocol?
    /// Последний увиденный системный статус — для журнала переходов.
    private var lastKnownStatus: NEVPNStatus?
    /// Запуск запрошен, но соединение ещё ни разу не дошло до connected.
    /// Флаг нужен потому, что система гасит неудачный старт через промежуточный
    /// disconnecting: связка connecting -> disconnected напрямую не приходит.
    private var pendingConnection = false
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
        ) { [weak self] _ in
            Task { @MainActor in self?.synchronizeStatus() }
        }
    }

    deinit {
        if let statusObserver { NotificationCenter.default.removeObserver(statusObserver) }
    }

    func prepare() async {
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
            manager = try await loadOrCreateManager()
            if !hasStagedConfiguration,
               let stored = try NimboConfigurationStore.shared.loadConfiguration() {
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
        if manager == nil { manager = try await loadOrCreateManager() }
        guard let manager,
              let tunnelProtocol = manager.protocolConfiguration as? NETunnelProviderProtocol else {
            throw VpnControllerError.managerUnavailable
        }
        tunnelProtocol.providerConfiguration = [
            "schema": 2,
            "configData": data,
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

    private func sendProviderCommand(_ command: String) async throws -> [String: Any] {
        guard let session = manager?.connection as? NETunnelProviderSession else {
            throw VpnControllerError.managerUnavailable
        }
        let request = try JSONSerialization.data(withJSONObject: ["command": command])
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
    func tunnelMetrics() async -> (received: UInt64, sent: UInt64, memoryMb: Int)? {
        guard case .connected = state else { return nil }
        guard let response = try? await sendProviderCommand("metrics"),
              response["ok"] as? Bool == true else { return nil }
        let received = (response["received"] as? NSNumber)?.uint64Value ?? 0
        let sent = (response["sent"] as? NSNumber)?.uint64Value ?? 0
        let memory = (response["memoryMb"] as? NSNumber)?.intValue ?? 0
        return (received, sent, memory)
    }

    func connect() async {
        do {
            if manager == nil { manager = try await loadOrCreateManager() }
            guard let manager else { throw VpnControllerError.managerUnavailable }
            guard let tunnelProtocol = manager.protocolConfiguration as? NETunnelProviderProtocol,
                  let configuration = tunnelProtocol.providerConfiguration?["configData"] as? Data,
                  !configuration.isEmpty else {
                throw VpnControllerError.missingConfiguration
            }
            state = .connecting
            await NimboDiagnostics.shared.record(.info, stage: .tunnelStart, code: "IOS_TUNNEL_START_REQUESTED", message: "Запуск Packet Tunnel запрошен пользователем")
            // Правило «по требованию» здесь не включается намеренно. Оно
            // поднимает туннель само, и если запуск падает — а на пределе
            // памяти он падает, — система повторяет попытку по кругу:
            // со стороны это выглядит как VPN, который сам включается и
            // выключается, и кнопка перестаёт что-либо значить.
            try manager.connection.startVPNTunnel()
            pendingConnection = true
            startRequestedAt = Date()
            // Статус мог смениться прямо сейчас: уведомления об этом может уже
            // не быть, поэтому спрашиваем сами.
            synchronizeStatus()
        } catch {
            fail(code: "IOS_TUNNEL_START_FAILED", error: error)
        }
    }

    func disconnect() async {
        // Осознанное отключение не должно выглядеть как сбой запуска.
        pendingConnection = false
        state = .disconnecting
        // Менеджера может не быть: приложение перезапустили, а туннель поднят
        // системой. Без загрузки остановка не дошла бы до него, и кнопка
        // крутилась бы вечно.
        if manager == nil { manager = try? await loadOrCreateManager() }
        // Правило могло остаться от прежней версии: без снятия система
        // подняла бы туннель обратно через секунду, и кнопка выглядела бы
        // сломанной.
        if let manager { try? await setOnDemand(false, on: manager) }
        manager?.connection.stopVPNTunnel()
        synchronizeStatus()
        await NimboDiagnostics.shared.record(.info, stage: .stop, code: "IOS_TUNNEL_STOP_REQUESTED", message: "Остановка Packet Tunnel запрошена пользователем")
    }

    /// Включает или снимает правило автоматического подъёма туннеля.
    private func setOnDemand(_ enabled: Bool, on manager: NETunnelProviderManager) async throws {
        if enabled {
            let rule = NEOnDemandRuleConnect()
            // Без ограничения по интерфейсу: туннель нужен и на сотовой сети,
            // и на Wi-Fi.
            rule.interfaceTypeMatch = .any
            manager.onDemandRules = [rule]
        } else {
            manager.onDemandRules = []
        }
        guard manager.isOnDemandEnabled != enabled else { return }
        manager.isOnDemandEnabled = enabled
        try await manager.saveToPreferences()
        try await manager.loadFromPreferences()
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
        // У тех, кто успел получить прошлую сборку, правило осталось
        // включённым и продолжало бы поднимать туннель само.
        value.isOnDemandEnabled = false
        value.onDemandRules = []
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
            // Туннель упал, не дойдя до connected: раньше это молча возвращало
            // экран в исходное состояние, и причина терялась.
            if pendingConnection {
                pendingConnection = false
                reportUnexpectedDisconnect()
            } else {
                state = .idle
            }
        case .connecting, .reasserting: state = .connecting
        case .connected:
            pendingConnection = false
            state = .connected
        case .disconnecting: state = .disconnecting
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
            if case .disconnecting = state, waited > 5 {
                statusPollTimer?.invalidate()
                statusPollTimer = nil
                transitionStartedAt = nil
                pendingConnection = false
                state = .idle
                return
            }
            if waited > 30 {
                statusPollTimer?.invalidate()
                statusPollTimer = nil
                transitionStartedAt = nil
                pendingConnection = false
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
        state = .failed(
            code: "IOS_TUNNEL_DROPPED",
            message: "Расширение туннеля завершилось сразу после запуска. Выясняем причину…"
        )

        guard let connection = manager?.connection else { return }
        if #available(iOS 16.0, *) {
            connection.fetchLastDisconnectError { [weak self] error in
                Task { @MainActor in
                    self?.applyDisconnectError(error)
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

    private func applyDisconnectError(_ error: Error?) {
        guard let error else {
            state = .failed(
                code: "IOS_TUNNEL_DROPPED",
                // Если с подписью что-то не так, называем это прямо: гадать
                // «почему не работает» человеку не с чем.
                message: NimboSigningReport.problem
                    ?? "Расширение туннеля завершилось сразу после запуска, система не назвала причину. "
                        + "Чаще всего так ведёт себя сборка, где расширение подписано без Network Extensions "
                        + "или в нём нет рабочего ядра."
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
        // «Внутренняя ошибка» системы почти всегда означает подпись: если
        // видно, чего именно не хватает, показываем это вместо общей фразы.
        let signingProblem = NimboSigningReport.problem
        state = .failed(
            code: presentation.code,
            message: signingProblem.map { "\(presentation.message)\n\n\($0)" } ?? presentation.message
        )
        Task {
            var metadata = signingContractMetadata
            if let signingProblem { metadata["signing_problem"] = signingProblem }
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
                "iOS не дала приложению право на Packet Tunnel. Переподпишите Nimbo и вложенное расширение одним сертификатом с разрешением Network Extensions.",
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

    var errorDescription: String? {
        switch self {
        case .emptyConfiguration: "Конфигурация пуста (IOS_CONFIG_EMPTY)."
        case .missingConfiguration: "Сначала выберите сервер или импортируйте подписку (IOS_CONFIG_MISSING)."
        case .managerUnavailable: "Системная конфигурация VPN недоступна (IOS_VPN_MANAGER_UNAVAILABLE)."
        case .configurationTooLarge: "Конфигурация превышает лимит 15 МиБ (IOS_CONFIG_TOO_LARGE)."
        case .providerMessageEmpty: "Расширение VPN не вернуло статус (IOS_PROVIDER_MESSAGE_EMPTY)."
        }
    }
}
