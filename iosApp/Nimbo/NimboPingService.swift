import Foundation
import Network

/// Измерение задержки до сервера.
///
/// ICMP-пинг обычному приложению на iOS недоступен (нужны raw-сокеты), поэтому
/// меряем то же, что и Android для TCP-протоколов: время до установления
/// TCP-соединения с портом сервера. Это честная задержка до узла, а не время
/// прохождения трафика через туннель.
actor NimboPingService {
    static let shared = NimboPingService()

    /// Дольше ждать смысла нет: такой узел всё равно непригоден.
    /// Значение задаётся в настройках, здесь — запасное.
    private let fallbackTimeout: TimeInterval = 3.0

    /// Ключи те же, что пишет общий экран настроек.
    private static let timeoutKey = "com.nimbo.ping.timeoutMs"
    private static let protocolKey = "com.nimbo.ping.protocol"
    private static let urlKey = "com.nimbo.ping.url"

    private var timeout: TimeInterval {
        let milliseconds = UserDefaults.standard.integer(forKey: NimboPingService.timeoutKey)
        return milliseconds > 0 ? TimeInterval(milliseconds) / 1000 : fallbackTimeout
    }

    /// HTTP-замер меряет рабочий маршрут целиком, а не путь до конкретного
    /// узла: через туннель все серверы дают одно и то же число.
    private var usesHttp: Bool {
        UserDefaults.standard.string(forKey: NimboPingService.protocolKey) == "http"
    }

    private var checkUrl: URL {
        let stored = UserDefaults.standard.string(forKey: NimboPingService.urlKey)
        return URL(string: stored ?? "") ?? URL(string: "https://www.gstatic.com/generate_204")!
    }
    /// Одновременных проверок: подписки бывают на сотню серверов, и открывать
    /// их разом — верный способ упереться в лимит дескрипторов.
    private let parallelism = 16

    private var inFlight = false

    /// Разрешённые адреса узлов: имя → адрес и время ответа.
    ///
    /// Кэш живёт десять минут: подписки меняют адреса редко, а держать его
    /// дольше значит рисковать замером до узла, которого уже нет.
    private var resolved: [String: (address: String, at: Date)] = [:]
    private let resolutionLifetime: TimeInterval = 600

    /// Известный адрес узла, если он уже встречался в этом прогоне.
    ///
    /// Имя разрешает сама сетевая подсистема при подключении, и адрес
    /// возвращается вместе с замером. Первый замер узла поэтому включает
    /// время DNS, последующие — уже нет.
    private func cachedAddress(for host: String) -> String? {
        if host.isEmpty { return nil }
        if NimboPingService.isAddressLiteral(host) { return host }
        guard let cached = resolved[host],
              Date().timeIntervalSince(cached.at) < resolutionLifetime else { return nil }
        return cached.address
    }

    private func remember(address: String?, for host: String) {
        guard let address, !address.isEmpty, !NimboPingService.isAddressLiteral(host) else { return }
        resolved[host] = (address, Date())
    }

    /// Похоже ли на готовый адрес: у IPv6 есть двоеточия, у IPv4 — только
    /// цифры и точки.
    private static func isAddressLiteral(_ host: String) -> Bool {
        if host.contains(":") { return true }
        let parts = host.split(separator: ".")
        return parts.count == 4 && parts.allSatisfy { UInt8($0) != nil }
    }

    /// Один узел: замер по требованию не должен ждать очереди общего прогона.
    func measureOne(host: String, port: Int) async -> Int {
        if usesHttp {
            return await NimboPingService.measureHttp(url: checkUrl, timeout: timeout)
        }
        let probe = await NimboPingService.measure(
            host: cachedAddress(for: host) ?? host,
            port: port,
            timeout: timeout
        )
        remember(address: probe.address, for: host)
        return probe.latency
    }

    /// Время до первого ответа по HTTP. Тело не читаем: нужен отклик, а не
    /// содержимое, поэтому запрос идёт методом HEAD.
    static func measureHttp(url: URL, timeout: TimeInterval) async -> Int {
        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"
        request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        request.timeoutInterval = timeout

        let started = Date()
        do {
            _ = try await URLSession.shared.data(for: request)
            return Int(Date().timeIntervalSince(started) * 1000)
        } catch {
            return -1
        }
    }

    /// Возвращает задержку в миллисекундах для каждого сервера; `-1` означает,
    /// что узел не ответил за отведённое время. `nil` — прогон уже идёт:
    /// пустой набор здесь означал бы «все узлы молчат», а это не так.
    func measureAll(_ targets: [(id: String, host: String, port: Int)]) async -> [String: Int]? {
        guard !inFlight else { return nil }
        inFlight = true
        defer { inFlight = false }

        var results: [String: Int] = [:]
        if usesHttp {
            // Один запрос на весь список: маршрут общий, и сотня одинаковых
            // запросов ничего не уточнит, только задержит.
            let value = await NimboPingService.measureHttp(url: checkUrl, timeout: timeout)
            for target in targets { results[target.id] = value }
            return results
        }
        // Общий срок на весь прогон: без него список из сотни узлов в сети,
        // где соединения просто молчат, держал бы «Проверяю…» минутами.
        let deadline = Date().addingTimeInterval(min(60, timeout * Double(2 + targets.count / parallelism)))
        var index = 0
        while index < targets.count {
            if Task.isCancelled || Date() >= deadline {
                for target in targets[index...] where results[target.id] == nil {
                    results[target.id] = -1
                }
                break
            }
            let slice = targets[index ..< min(index + parallelism, targets.count)]
            let currentTimeout = timeout
            // Имя разрешает сама сетевая подсистема во время подключения:
            // отдельный круг через системный резолвер занимал по потоку на
            // каждое имя, и в сети, где DNS молчит, эти потоки повисали — вместе
            // с ними вставало всё остальное в приложении.
            let known = Dictionary(
                slice.map { ($0.id, cachedAddress(for: $0.host) ?? $0.host) },
                uniquingKeysWith: { first, _ in first }
            )
            var learned: [String: String] = [:]
            await withTaskGroup(of: (String, String, Int, String?).self) { group in
                for target in slice {
                    let endpoint = known[target.id] ?? target.host
                    group.addTask {
                        let probe = await NimboPingService.measure(
                            host: endpoint,
                            port: target.port,
                            timeout: currentTimeout
                        )
                        return (target.id, target.host, probe.latency, probe.address)
                    }
                }
                for await (id, host, value, address) in group {
                    results[id] = value
                    if let address { learned[host] = address }
                }
            }
            for (host, address) in learned {
                remember(address: address, for: host)
            }
            index += parallelism
        }
        return results
    }

    /// Время установления TCP-соединения, мс. `-1` — не ответил.
    static func measure(
        host: String,
        port: Int,
        timeout: TimeInterval
    ) async -> (latency: Int, address: String?) {
        guard !host.isEmpty, port > 0, port <= 65_535,
              let endpointPort = NWEndpoint.Port(rawValue: UInt16(port)) else {
            return (-1, nil)
        }

        let parameters = NWParameters.tcp
        // Проверяем сам узел, а не маршрут через туннель.
        parameters.preferNoProxies = true
        let connection = NWConnection(
            host: NWEndpoint.Host(host),
            port: endpointPort,
            using: parameters
        )
        let queue = DispatchQueue(label: "com.nimbo.ping")
        let startedAt = DispatchTime.now()

        return await withCheckedContinuation { continuation in
            let finished = NimboPingFlag()

            func finish(_ value: Int, _ address: String?) {
                guard finished.take() else { return }
                connection.cancel()
                continuation.resume(returning: (value, address))
            }

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    let elapsed = DispatchTime.now().uptimeNanoseconds - startedAt.uptimeNanoseconds
                    finish(Int(elapsed / 1_000_000), NimboPingService.address(of: connection))
                case .failed, .cancelled:
                    finish(-1, nil)
                default:
                    break
                }
            }
            connection.start(queue: queue)
            queue.asyncAfter(deadline: .now() + timeout) { finish(-1, nil) }
        }
    }

    /// Адрес, к которому соединение в итоге пришло.
    ///
    /// Его сообщает сама сетевая подсистема, поэтому следующий замер того же
    /// узла обходится без разрешения имени и меряет только TCP.
    private static func address(of connection: NWConnection) -> String? {
        guard case let .hostPort(host, _)? = connection.currentPath?.remoteEndpoint else {
            return nil
        }
        switch host {
        case let .ipv4(value):
            return "\(value)"
        case let .ipv6(value):
            // У адреса может быть суффикс с интерфейсом — соединению он не нужен.
            return String("\(value)".split(separator: "%").first ?? "")
        default:
            return nil
        }
    }
}

/// Однократный флаг: обработчик состояния и таймаут могут сработать вместе, а
/// продолжение разрешается возобновить ровно один раз.
private final class NimboPingFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var used = false

    func take() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if used { return false }
        used = true
        return true
    }
}
