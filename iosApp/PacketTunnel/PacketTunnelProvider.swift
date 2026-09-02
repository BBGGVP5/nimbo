import Foundation
import Network
import NetworkExtension

final class PacketTunnelProvider: NEPacketTunnelProvider {
    private let lifecycleQueue = DispatchQueue(label: "com.nimbo.packet-tunnel.lifecycle")
    private let core = LibXrayBridge()
    private var lifecycleGeneration: UInt64 = 0
    private var starting = false
    private var started = false
    /// Сколько исходящих в текущей конфигурации.
    ///
    /// Сама конфигурация здесь не хранится: её JSON занимает сотни килобайт, а
    /// после запуска ядра нужен только этот счётчик — при пределе памяти
    /// расширения такую разницу видно.
    private var outboundCount = 0
    /// Имя utun, который выдала система: по нему считаются байты туннеля.
    private var tunnelInterfaceName: String?
    /// Проверка, жив ли ещё процесс ядра.
    private var watchdog: DispatchSourceTimer?
    /// Сколько проверок подряд ядро промолчало.
    private var watchdogMisses = 0
    /// Наблюдение за сетью: смена Wi-Fi на сотовую и обратно.
    private var pathMonitor: NWPathMonitor?
    /// Когда маршруты переустанавливались в последний раз.
    private var lastRouteRefresh = Date.distantPast
    /// Была ли сеть доступна при прошлой проверке.
    private var networkWasSatisfied = true

    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        lifecycleQueue.async { [weak self] in
            guard let self else {
                completionHandler(PacketTunnelError.providerUnavailable)
                return
            }
            guard !self.starting, !self.started else {
                completionHandler(nil)
                return
            }
            self.lifecycleGeneration &+= 1
            let generation = self.lifecycleGeneration
            self.starting = true

            Task {
                do {
                    try await self.startTunnelInternal(generation: generation)
                    self.lifecycleQueue.async {
                        guard generation == self.lifecycleGeneration else {
                            completionHandler(PacketTunnelError.startCancelled)
                            return
                        }
                        self.starting = false
                        self.started = true
                        self.startWatchdog()
                        self.startPathMonitor()
                        completionHandler(nil)
                    }
                } catch {
                    self.lifecycleQueue.async {
                        if (try? self.core.isRunning()) == true { try? self.core.stop() }
                        let reportedError: Error = generation == self.lifecycleGeneration
                            ? error
                            : PacketTunnelError.startCancelled
                        self.starting = false
                        self.started = false
                        self.outboundCount = 0
                        completionHandler(Self.transportableError(reportedError))
                    }
                }
            }
        }
    }

    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        lifecycleQueue.async { [weak self] in
            guard let self else {
                completionHandler()
                return
            }
            self.lifecycleGeneration &+= 1
            self.stopWatchdog()
            self.stopPathMonitor()
            let stopError: Error?
            do {
                if (try? self.core.isRunning()) == true { try self.core.stop() }
                stopError = nil
            } catch {
                stopError = error
            }
            self.starting = false
            self.started = false
            self.outboundCount = 0

            Task {
                await NimboDiagnostics.shared.record(
                    stopError == nil ? .info : .warning,
                    stage: .stop,
                    code: stopError == nil ? "IOS_PACKET_TUNNEL_STOP" : "IOS_PACKET_TUNNEL_STOP_FAILED",
                    message: stopError.map { NimboRedactor.redact($0.localizedDescription) }
                        ?? "Packet Tunnel остановлено",
                    metadata: ["reason": "\(reason.rawValue)"]
                )
                completionHandler()
            }
        }
    }

    override func handleAppMessage(
        _ messageData: Data,
        completionHandler: ((Data?) -> Void)? = nil
    ) {
        lifecycleQueue.async { [weak self] in
            guard let self else {
                completionHandler?(Self.responseData(["ok": false, "error": "provider unavailable"]))
                return
            }
            let request = (try? JSONSerialization.jsonObject(with: messageData)) as? [String: Any]
            let command = request?["command"] as? String ?? "status"
            switch command {
            case "status":
                let running = (try? self.core.isRunning()) ?? false
                let version = (try? self.core.version()) ?? "unknown"
                completionHandler?(Self.responseData([
                    "ok": true,
                    "running": running,
                    "version": version,
                    "outbounds": self.outboundCount
                ]))
            case "metrics":
                // Счётчики берём у своего интерфейса: приложение видит все utun
                // и не может отличить наш от служебного.
                let counters = self.tunnelInterfaceName
                    .flatMap { NimboInterfaceCounters.counters(interface: $0) }
                    ?? NimboInterfaceCounters.busiestTunnel()
                completionHandler?(Self.responseData([
                    "ok": true,
                    "received": counters?.received ?? 0,
                    "sent": counters?.sent ?? 0,
                    // Предел памяти система ставит расширению, поэтому важна
                    // именно его занятая память, а не приложения.
                    "memoryMb": NimboInterfaceCounters.memoryFootprintMb()
                ]))
            case "diagnostics":
                let running = (try? self.core.isRunning()) ?? false
                let version = (try? self.core.version()) ?? "unknown"
                let outboundCount = self.outboundCount
                Task {
                    let records = (try? await NimboDiagnostics.shared.recentRecordsData(maxBytes: 384 * 1_024)) ?? Data()
                    completionHandler?(Self.responseData([
                        "ok": true,
                        "running": running,
                        "version": version,
                        "outbounds": outboundCount,
                        "records": String(decoding: records, as: UTF8.self)
                    ]))
                }
            default:
                completionHandler?(Self.responseData(["ok": false, "error": "unsupported command"]))
            }
        }
    }

    /// Наблюдение за ядром.
    ///
    /// Раньше сторож при первой же неудачной проверке отменял туннель. Но
    /// «не ответило» и «остановилось» — разные вещи: вызов к ядру может
    /// не пройти на секунду, а туннель при этом жив. Из-за этого рабочее
    /// соединение обрывалось на ровном месте, поэтому сторож только пишет в
    /// диагностику, и лишь после трёх молчаливых проверок подряд.
    private func startWatchdog() {
        stopWatchdog()
        watchdogMisses = 0
        let timer = DispatchSource.makeTimerSource(queue: lifecycleQueue)
        timer.schedule(deadline: .now() + 60, repeating: 60, leeway: .seconds(15))
        timer.setEventHandler { [weak self] in
            guard let self, self.started, !self.starting else { return }
            if (try? self.core.isRunning()) == true {
                self.watchdogMisses = 0
                return
            }
            self.watchdogMisses += 1
            guard self.watchdogMisses >= 3 else { return }
            self.stopWatchdog()
            Task {
                await NimboDiagnostics.shared.record(
                    .warning,
                    stage: .coreLoad,
                    code: "IOS_XRAY_SILENT",
                    message: "VPN-ядро не отвечает на проверки состояния"
                )
            }
        }
        timer.resume()
        watchdog = timer
    }

    private func stopWatchdog() {
        watchdog?.cancel()
        watchdog = nil
    }

    /// Смена сети — обычное дело для телефона: Wi-Fi дома, сотовая на улице.
    ///
    /// Маршруты туннеля при этом остаются от прежнего интерфейса, и трафик
    /// уходит в никуда: снаружи это выглядит как «подключено, но ничего не
    /// грузится». Системе нужно сказать, что маршруты пора перечитать.
    private func startPathMonitor() {
        stopPathMonitor()
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            self.lifecycleQueue.async {
                self.handleNetworkPath(path)
            }
        }
        monitor.start(queue: lifecycleQueue)
        pathMonitor = monitor
    }

    private func stopPathMonitor() {
        pathMonitor?.cancel()
        pathMonitor = nil
    }

    private func handleNetworkPath(_ path: Network.NWPath) {
        guard started, !starting else { return }
        let satisfied = path.status == .satisfied
        let returned = satisfied && !networkWasSatisfied
        networkWasSatisfied = satisfied

        Task {
            await NimboDiagnostics.shared.record(
                .info,
                stage: .route,
                code: "IOS_NETWORK_PATH_CHANGED",
                message: satisfied ? "Сеть доступна" : "Сеть недоступна",
                metadata: [
                    "wifi": path.usesInterfaceType(Network.NWInterface.InterfaceType.wifi) ? "да" : "нет",
                    "cellular": path.usesInterfaceType(Network.NWInterface.InterfaceType.cellular) ? "да" : "нет",
                    "expensive": path.isExpensive ? "да" : "нет"
                ]
            )
        }

        // Перечитывать маршруты чаще раза в десять секунд бессмысленно:
        // при переходе между сетями система шлёт события пачкой.
        guard returned, Date().timeIntervalSince(lastRouteRefresh) > 10 else { return }
        lastRouteRefresh = Date()
        refreshTunnelRoutes()
    }

    /// Переустановка сетевых настроек: тот же набор, что и при запуске.
    ///
    /// Это не разрыв соединения, а просьба к системе перечитать маршруты и
    /// DNS для нового интерфейса.
    private func refreshTunnelRoutes() {
        let routingOptions = NimboRoutingOptions(
            providerValue: (protocolConfiguration as? NETunnelProviderProtocol)?
                .providerConfiguration?["routing"]
        )
        setTunnelNetworkSettings(PacketTunnelNetwork.settings(options: routingOptions)) { error in
            Task {
                await NimboDiagnostics.shared.record(
                    error == nil ? .info : .warning,
                    stage: .route,
                    code: error == nil ? "IOS_ROUTES_REFRESHED" : "IOS_ROUTES_REFRESH_FAILED",
                    message: error == nil
                        ? "Маршруты перечитаны после смены сети"
                        : "Не удалось перечитать маршруты после смены сети"
                )
            }
        }
    }

    override func sleep(completionHandler: @escaping () -> Void) {
        completionHandler()
    }

    override func wake() {
        lifecycleQueue.async { [weak self] in
            guard let self, self.started else { return }
            guard (try? self.core.isRunning()) != true else { return }
            // Сразу после пробуждения ядро может не ответить, оставаясь живым.
            // Рвать из-за этого рабочее соединение нельзя, поэтому спрашиваем
            // ещё раз чуть погодя.
            self.lifecycleQueue.asyncAfter(deadline: .now() + 3) {
                guard self.started, (try? self.core.isRunning()) != true else { return }
                self.cancelTunnelWithError(PacketTunnelError.coreStoppedUnexpectedly)
            }
        }
    }

    private func startTunnelInternal(generation: UInt64) async throws {
        await NimboDiagnostics.shared.record(
            .info,
            stage: .tunnelStart,
            code: "IOS_PACKET_TUNNEL_START",
            message: "Packet Tunnel extension запущено"
        )

        guard let tunnelProtocol = protocolConfiguration as? NETunnelProviderProtocol,
              let data = tunnelProtocol.providerConfiguration?["configData"] as? Data,
              !data.isEmpty else {
            throw await recorded(PacketTunnelError.missingConfiguration, stage: .config, code: "IOS_PACKET_TUNNEL_CONFIG_MISSING")
        }

        let routingOptions = NimboRoutingOptions(
            providerValue: (protocolConfiguration as? NETunnelProviderProtocol)?
                .providerConfiguration?["routing"]
        )
        XrayConfigurationBuilder.moduleRulesJSON =
            tunnelProtocol.providerConfiguration?["modules"] as? String ?? ""
        XrayConfigurationBuilder.routingProfileJSON =
            tunnelProtocol.providerConfiguration?["routingProfile"] as? String ?? ""
        // Наборы geo видно в журнале: именно они упираются в предел памяти
        // расширения, и без записи причину падения приходится угадывать.
        await NimboDiagnostics.shared.record(
            .info,
            stage: .config,
            code: "IOS_ROUTING_PROFILE_LOADED",
            message: "Правила профиля маршрутизации получены",
            metadata: [
                "geo_sets": XrayConfigurationBuilder.routingGeoCodes().joined(separator: ",")
            ]
        )

        do {
            try await applyNetworkSettings(PacketTunnelNetwork.settings(options: routingOptions))
            await NimboDiagnostics.shared.record(
                .info,
                stage: .route,
                code: "IOS_TUNNEL_NETWORK_SETTINGS_APPLIED",
                message: "Системные IPv4/IPv6 маршруты и DNS применены"
            )
        } catch {
            throw await recorded(error, stage: .route, code: "IOS_TUNNEL_NETWORK_SETTINGS_FAILED")
        }
        try await ensureStartIsCurrent(generation)

        guard let descriptorInfo = PacketTunnelNetwork.utunDescriptorInfo() else {
            throw await recorded(PacketTunnelError.utunUnavailable, stage: .route, code: "IOS_UTUN_FD_NOT_FOUND")
        }

        // Имя интерфейса нужно позже для счётчиков: искать его повторно
        // бессмысленно, а угадывать по адресу — ошибочно.
        tunnelInterfaceName = descriptorInfo.interfaceName
        guard let assets = Bundle.main.resourceURL?.path,
              FileManager.default.fileExists(atPath: "\(assets)/geoip.dat"),
              FileManager.default.fileExists(atPath: "\(assets)/geosite.dat") else {
            throw await recorded(PacketTunnelError.geoDataMissing, stage: .coreLoad, code: "IOS_GEO_DATA_MISSING")
        }

        do {
            let startup = try await runCore(
                generation: generation,
                sourceData: data,
                descriptor: descriptorInfo.descriptor,
                interfaceName: descriptorInfo.interfaceName,
                assetDirectory: assets,
                options: routingOptions
            )

            await NimboDiagnostics.shared.record(
                .info,
                stage: .coreLoad,
                code: "IOS_XRAY_STARTED",
                message: "VPN-ядро запущено",
                metadata: [
                    "core": startup.coreVersion,
                    "outbounds": "\(startup.outboundCount)",
                    "tun_fd": "available",
                    "tun_interface": descriptorInfo.interfaceName
                ]
            )
        } catch {
            throw await recorded(error, stage: .coreLoad, code: "IOS_XRAY_START_FAILED")
        }
    }

    private func ensureStartIsCurrent(_ generation: UInt64) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            lifecycleQueue.async {
                guard generation == self.lifecycleGeneration, self.starting else {
                    continuation.resume(throwing: PacketTunnelError.startCancelled)
                    return
                }
                continuation.resume(returning: ())
            }
        }
    }

    private func runCore(
        generation: UInt64,
        sourceData: Data,
        descriptor: Int32,
        interfaceName: String,
        assetDirectory: String,
        options: NimboRoutingOptions
    ) async throws -> CoreStartupResult {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<CoreStartupResult, Error>) in
            lifecycleQueue.async {
                guard generation == self.lifecycleGeneration, self.starting else {
                    continuation.resume(throwing: PacketTunnelError.startCancelled)
                    return
                }
                do {
                    if (try? self.core.isRunning()) == true { try self.core.stop() }
                    try self.core.configureRuntimeEnvironment(
                        tunnelFileDescriptor: descriptor,
                        assetDirectory: assetDirectory
                    )
                    let configuration = try XrayConfigurationBuilder.prepare(
                        sourceData: sourceData,
                        tunnelFileDescriptor: descriptor,
                        tunnelInterfaceName: interfaceName,
                        assetDirectory: assetDirectory,
                        options: options,
                        bridge: self.core
                    )
                    try self.core.run(configurationJSON: configuration.json)
                    guard try self.core.isRunning() else { throw PacketTunnelError.coreDidNotStart }
                    let coreVersion = try self.core.version()
                    self.outboundCount = configuration.outboundCount
                    continuation.resume(returning: CoreStartupResult(
                        outboundCount: configuration.outboundCount,
                        coreVersion: coreVersion
                    ))
                } catch {
                    if (try? self.core.isRunning()) == true { try? self.core.stop() }
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    private func applyNetworkSettings(_ settings: NEPacketTunnelNetworkSettings) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            setTunnelNetworkSettings(settings) { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: ()) }
            }
        }
    }

    /// Через границу процесса у NSError выживают только домен и код: userInfo
    /// с текстом до приложения не доезжает, а собственные записи расширения без
    /// App Group лежат в чужом контейнере и в выгрузку не попадают. Поэтому
    /// причину упаковываем прямо в домен — это единственный канал наружу.
    private static func transportableError(_ error: Error) -> NSError {
        let text = NimboRedactor.redact(
            (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        )
        let bridged = error as NSError
        return NSError(
            domain: "Nimbo: \(text.prefix(400))",
            code: bridged.code,
            userInfo: [NSLocalizedDescriptionKey: text]
        )
    }

    private func recorded(
        _ error: Error,
        stage: NimboDiagnosticStage,
        code: String
    ) async -> Error {
        await NimboDiagnostics.shared.record(
            .error,
            stage: stage,
            code: code,
            message: NimboRedactor.redact(error.localizedDescription)
        )
        return error
    }

    private static func responseData(_ object: [String: Any]) -> Data? {
        try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
    }
}

private struct CoreStartupResult {
    let outboundCount: Int
    let coreVersion: String
}

private enum PacketTunnelError: LocalizedError {
    case missingConfiguration
    case providerUnavailable
    case utunUnavailable
    case geoDataMissing
    case coreDidNotStart
    case coreStoppedUnexpectedly
    case startCancelled

    var errorDescription: String? {
        switch self {
        case .missingConfiguration:
            "Конфигурация VPN не передана расширению (IOS_PACKET_TUNNEL_CONFIG_MISSING)."
        case .providerUnavailable:
            "Расширение VPN недоступно (IOS_PACKET_TUNNEL_UNAVAILABLE)."
        case .utunUnavailable:
            "Не найден системный интерфейс iOS utun (IOS_UTUN_FD_NOT_FOUND)."
        case .geoDataMissing:
            "В расширении отсутствуют geoip.dat или geosite.dat (IOS_GEO_DATA_MISSING)."
        case .coreDidNotStart:
            "VPN-ядро не перешло в рабочее состояние (IOS_XRAY_NOT_RUNNING)."
        case .coreStoppedUnexpectedly:
            "VPN-ядро остановилось после пробуждения устройства (IOS_XRAY_STOPPED)."
        case .startCancelled:
            "Запуск VPN отменён новым системным событием (IOS_TUNNEL_START_CANCELLED)."
        }
    }
}
