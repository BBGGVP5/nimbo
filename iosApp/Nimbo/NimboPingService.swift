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

    /// Адрес узла: литерал возвращается как есть, имя — разрешается и
    /// запоминается.
    private func address(for host: String) async -> String? {
        if host.isEmpty { return nil }
        if NimboPingService.isAddressLiteral(host) { return host }
        if let cached = resolved[host], Date().timeIntervalSince(cached.at) < resolutionLifetime {
            return cached.address
        }
        guard let address = await NimboPingService.resolveAddress(host) else { return nil }
        resolved[host] = (address, Date())
        return address
    }

    /// Адрес с ограничением по времени.
    ///
    /// У системного резолвера своего предела нет: пока туннель поднимается,
    /// DNS может не отвечать вовсе, и одно имя задерживало весь список на
    /// десятки секунд. Не успели — узел считается молчащим.
    private func resolvedAddress(for host: String) async -> String? {
        let deadline = timeout
        return await withTaskGroup(of: String?.self) { group in
            group.addTask { [weak self] in await self?.address(for: host) }
            group.addTask {
                try? await Task.sleep(nanoseconds: UInt64(deadline * 1_000_000_000))
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }

    /// Похоже ли на готовый адрес: у IPv6 есть двоеточия, у IPv4 — только
    /// цифры и точки.
    private static func isAddressLiteral(_ host: String) -> Bool {
        if host.contains(":") { return true }
        let parts = host.split(separator: ".")
        return parts.count == 4 && parts.allSatisfy { UInt8($0) != nil }
    }

    /// Разрешение имени через системный резолвер.
    ///
    /// Делается отдельно от замера: иначе время ответа DNS попадает в
    /// задержку узла и на мобильной сети даёт лишние сотни миллисекунд.
    private static func resolveAddress(_ host: String) async -> String? {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .utility).async {
                var hints = addrinfo(
                    ai_flags: 0,
                    ai_family: AF_UNSPEC,
                    ai_socktype: SOCK_STREAM,
                    ai_protocol: IPPROTO_TCP,
                    ai_addrlen: 0,
                    ai_canonname: nil,
                    ai_addr: nil,
                    ai_next: nil
                )
                var result: UnsafeMutablePointer<addrinfo>?
                defer { if let result { freeaddrinfo(result) } }
                guard getaddrinfo(host, nil, &hints, &result) == 0, let head = result else {
                    continuation.resume(returning: nil)
                    return
                }

                var buffer = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                var node: UnsafeMutablePointer<addrinfo>? = head
                while let current = node {
                    if getnameinfo(
                        current.pointee.ai_addr,
                        current.pointee.ai_addrlen,
                        &buffer,
                        socklen_t(buffer.count),
                        nil,
                        0,
                        NI_NUMERICHOST
                    ) == 0 {
                        continuation.resume(returning: String(cString: buffer))
                        return
                    }
                    node = current.pointee.ai_next
                }
                continuation.resume(returning: nil)
            }
        }
    }

    /// Один узел: замер по требованию не должен ждать очереди общего прогона.
    func measureOne(host: String, port: Int) async -> Int {
        if usesHttp {
            return await NimboPingService.measureHttp(url: checkUrl, timeout: timeout)
        }
        guard let address = await address(for: host) else { return -1 }
        return await NimboPingService.measure(host: address, port: port, timeout: timeout)
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
    /// что узел не ответил за отведённое время.
    func measureAll(_ targets: [(id: String, host: String, port: Int)]) async -> [String: Int] {
        guard !inFlight else { return [:] }
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
        var index = 0
        while index < targets.count {
            let slice = targets[index ..< min(index + parallelism, targets.count)]
            // Адреса разрешаются до замера: иначе ответ DNS попадёт в задержку
            // и узел рядом покажет сотни миллисекунд. Разрешаются они разом:
            // по одному сотня имён складывалась в минуты ожидания, и список
            // просто висел на «Проверяю…».
            var addresses: [String: String] = [:]
            await withTaskGroup(of: (String, String?).self) { group in
                for target in slice {
                    group.addTask { [weak self] in
                        guard let self else { return (target.id, nil) }
                        return (target.id, await self.resolvedAddress(for: target.host))
                    }
                }
                for await (id, value) in group where value != nil {
                    addresses[id] = value
                }
            }
            await withTaskGroup(of: (String, Int).self) { group in
                for target in slice {
                    let currentTimeout = timeout
                    guard let host = addresses[target.id] ?? nil else {
                        results[target.id] = -1
                        continue
                    }
                    group.addTask {
                        let value = await NimboPingService.measure(
                            host: host,
                            port: target.port,
                            timeout: currentTimeout
                        )
                        return (target.id, value)
                    }
                }
                for await (id, value) in group {
                    results[id] = value
                }
            }
            index += parallelism
        }
        return results
    }

    /// Время установления TCP-соединения, мс. `-1` — не ответил.
    static func measure(host: String, port: Int, timeout: TimeInterval) async -> Int {
        guard !host.isEmpty, port > 0, port <= 65_535,
              let endpointPort = NWEndpoint.Port(rawValue: UInt16(port)) else {
            return -1
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

            func finish(_ value: Int) {
                guard finished.take() else { return }
                connection.cancel()
                continuation.resume(returning: value)
            }

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    let elapsed = DispatchTime.now().uptimeNanoseconds - startedAt.uptimeNanoseconds
                    finish(Int(elapsed / 1_000_000))
                case .failed, .cancelled:
                    finish(-1)
                default:
                    break
                }
            }
            connection.start(queue: queue)
            queue.asyncAfter(deadline: .now() + timeout) { finish(-1) }
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
