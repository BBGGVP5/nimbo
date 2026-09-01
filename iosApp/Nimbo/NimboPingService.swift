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
    private let timeout: TimeInterval = 3.0
    /// Одновременных проверок: подписки бывают на сотню серверов, и открывать
    /// их разом — верный способ упереться в лимит дескрипторов.
    private let parallelism = 8

    private var inFlight = false

    /// Один узел: замер по требованию не должен ждать очереди общего прогона.
    func measureOne(host: String, port: Int) async -> Int {
        await NimboPingService.measure(host: host, port: port, timeout: timeout)
    }

    /// Возвращает задержку в миллисекундах для каждого сервера; `-1` означает,
    /// что узел не ответил за отведённое время.
    func measureAll(_ targets: [(id: String, host: String, port: Int)]) async -> [String: Int] {
        guard !inFlight else { return [:] }
        inFlight = true
        defer { inFlight = false }

        var results: [String: Int] = [:]
        var index = 0
        while index < targets.count {
            let slice = targets[index ..< min(index + parallelism, targets.count)]
            await withTaskGroup(of: (String, Int).self) { group in
                for target in slice {
                    group.addTask { [timeout] in
                        let value = await NimboPingService.measure(
                            host: target.host,
                            port: target.port,
                            timeout: timeout
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
