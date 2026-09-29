import Foundation
import Network
import NetworkExtension

/// Transport-specific measurements. -1 is unavailable/failure; zero is success.
actor NimboPingService {
    typealias RouteProbe = (NETunnelProviderSession?, [String], URL, TimeInterval, String, Bool) async -> (id: String, latency: Int)
    typealias DiagnosticProbe = (String, String, URL, TimeInterval) async -> Int
    static let shared = NimboPingService()
    private let routeProbe: RouteProbe
    private let diagnosticProbe: DiagnosticProbe
    private let diagnosticClock: () -> TimeInterval
    private let parallelism = 16
    private var inFlight = false

    init(routeProbe: @escaping RouteProbe = NimboActiveRouteProbe.measure,
         diagnosticProbe: @escaping DiagnosticProbe = NimboDiagnosticProbe.measure,
         diagnosticClock: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime }) {
        self.routeProbe = routeProbe
        self.diagnosticProbe = diagnosticProbe
        self.diagnosticClock = diagnosticClock
    }

    /// Serial diagnostics need a per-node budget. Allow one second per node for
    /// queue/cleanup overhead, with a 30-minute safety ceiling for huge profiles.
    static func diagnosticBatchBudget(count: Int, timeout: TimeInterval) -> TimeInterval {
        min(30 * 60, Double(max(0, count)) * (timeout + 1))
    }

    private struct Settings: Equatable {
        let mode: NimboPingProtocol
        let rawURL: String
        let timeout: TimeInterval

        static var current: Settings {
            let defaults = UserDefaults.standard
            return Settings(mode: NimboPingProtocol(stored: defaults.string(forKey: "com.nimbo.ping.protocol")),
                            rawURL: defaults.string(forKey: "com.nimbo.ping.url") ?? NimboPingPolicy.defaultURL,
                            timeout: NimboPingPolicy.timeout(milliseconds: defaults.integer(forKey: "com.nimbo.ping.timeoutMs")))
        }
    }

    /// Sticky invalidation also rejects a setting changed away and then back.
    private final class SettingsLease: @unchecked Sendable {
        let settings = Settings.current
        private let lock = NSLock()
        private var invalidated = false
        private var observer: NSObjectProtocol?

        init() {
            observer = NotificationCenter.default.addObserver(forName: UserDefaults.didChangeNotification, object: nil, queue: nil) { [weak self] _ in
                guard let self, self.settings != .current else { return }
                self.lock.lock()
                self.invalidated = true
                self.lock.unlock()
            }
        }

        var valid: Bool {
            lock.lock()
            defer { lock.unlock() }
            return !invalidated && settings == .current
        }

        deinit { if let observer { NotificationCenter.default.removeObserver(observer) } }
    }

    func measureOne(host: String, port: Int, id: String = "", session: NETunnelProviderSession? = nil, configuration: String? = nil) async -> Int {
        let lease = SettingsLease()
        let settings = lease.settings
        let value: Int
        if settings.mode == .nimbo {
            guard !id.isEmpty, let configuration, let url = NimboPingPolicy.checkedURL(settings.rawURL) else { return -1 }
            value = await diagnosticProbe(id, configuration, url, settings.timeout)
        } else if settings.mode.httpMethod != nil {
            guard !id.isEmpty, let url = NimboPingPolicy.checkedURL(settings.rawURL) else { return -1 }
            value = await routeProbe(session, [id], url, settings.timeout, settings.mode.httpMethod!, settings.mode == .nimbo).latency
        } else {
            value = await Self.probe(host: host, port: port, settings: settings, timeout: settings.timeout)
        }
        return !Task.isCancelled && lease.valid ? value : -1
    }

    /// Nimbo uses each target's immutable config in the app process, independent
    /// of the currently selected/connected tunnel. Unattempted rows are omitted.
    func measureAll(_ targets: [(id: String, host: String, port: Int)], session: NETunnelProviderSession? = nil,
                    configurations: [String: String] = [:],
                    progress: (@MainActor (String, Int) -> Void)? = nil) async -> [String: Int]? {
        guard !inFlight else { return nil }
        inFlight = true
        defer { inFlight = false }
        let lease = SettingsLease()
        let settings = lease.settings
        let unavailable = Dictionary(targets.map { ($0.id, -1) }, uniquingKeysWith: { first, _ in first })
        var results = unavailable
        if settings.mode == .nimbo {
            guard let url = NimboPingPolicy.checkedURL(settings.rawURL) else { return unavailable }
            results = [:] // Unattempted targets are not failed measurements.
            let deadline = diagnosticClock() + Self.diagnosticBatchBudget(count: targets.count, timeout: settings.timeout)
            for target in targets {
                let remaining = deadline - diagnosticClock()
                guard !Task.isCancelled, lease.valid, remaining > 0 else { break }
                let value: Int
                if let configuration = configurations[target.id] {
                    value = await diagnosticProbe(target.id, configuration, url, min(settings.timeout, remaining))
                } else { value = -1 } // This target was checked and has no usable config.
                guard !Task.isCancelled, lease.valid else { break }
                results[target.id] = value
                await MainActor.run {
                    guard !Task.isCancelled, lease.valid else { return }
                    progress?(target.id, value)
                }
            }
            // No final writes from an interrupted or obsolete run. Already emitted
            // progress contains only completed samples, never queued placeholders.
            return !Task.isCancelled && lease.valid ? results : nil
        } else if settings.mode.httpMethod != nil {
            guard let url = NimboPingPolicy.checkedURL(settings.rawURL) else { return unavailable }
            let sample = await routeProbe(session, targets.map(\.id), url, settings.timeout, settings.mode.httpMethod!, settings.mode == .nimbo)
            if sample.latency >= 0, results[sample.id] != nil { results[sample.id] = sample.latency }
        } else {
            let deadline = ProcessInfo.processInfo.systemUptime + min(60, settings.timeout * Double(2 + targets.count / parallelism))
            var index = 0
            while index < targets.count && !Task.isCancelled && lease.valid {
                let remaining = deadline - ProcessInfo.processInfo.systemUptime
                guard remaining > 0 else { break }
                let timeout = min(settings.timeout, remaining)
                let slice = targets[index..<min(index + parallelism, targets.count)]
                await withTaskGroup(of: (String, Int).self) { group in
                    for target in slice {
                        group.addTask {
                            (target.id, await Self.probe(host: target.host, port: target.port, settings: settings, timeout: timeout))
                        }
                    }
                    for await (id, value) in group { results[id] = value }
                }
                index += parallelism
            }
        }
        return !Task.isCancelled && lease.valid ? results : unavailable
    }

    private static func probe(host: String, port: Int, settings: Settings, timeout: TimeInterval) async -> Int {
        guard !Task.isCancelled else { return -1 }
        switch settings.mode {
        case .tcp: return await measure(host: host, port: port, timeout: timeout).latency
        case .icmp:
            let started = ProcessInfo.processInfo.systemUptime
            // UDP readiness resolves asynchronously; it is never an ICMP result.
            guard let address = await connect(host: host, port: 9, timeout: timeout, udp: true).address else { return -1 }
            let remaining = timeout - (ProcessInfo.processInfo.systemUptime - started)
            return await NimboICMPProbe.measure(address: address, timeout: remaining)
        case .nimbo, .httpGet, .httpHead: return -1 // Route modes have no generic-network fallback.
        }
    }

    /// Legacy callers keep HEAD. New GET mode passes its method explicitly.
    static func measureHttp(url: URL, timeout: TimeInterval, method: String = "HEAD") async -> Int {
        await NimboHTTPProbe.measure(url: url, method: method, timeout: timeout)
    }

    static func measure(host: String, port: Int, timeout: TimeInterval) async -> (latency: Int, address: String?) {
        await connect(host: host, port: port, timeout: timeout, udp: false)
    }

    private static func connect(host: String, port: Int, timeout: TimeInterval, udp: Bool) async -> (latency: Int, address: String?) {
        guard !host.isEmpty, (1...65535).contains(port), timeout > 0, !Task.isCancelled,
              let endpointPort = NWEndpoint.Port(rawValue: UInt16(port)) else { return (-1, nil) }
        let parameters = udp ? NWParameters.udp : NWParameters.tcp
        parameters.preferNoProxies = true
        let connection = NWConnection(host: NWEndpoint.Host(host), port: endpointPort, using: parameters)
        let queue = DispatchQueue(label: "com.nimbo.ping.connection")
        let started = DispatchTime.now().uptimeNanoseconds
        let result = NimboPingCompletion<(latency: Int, address: String?)>()
        return await withTaskCancellationHandler(operation: {
            await withCheckedContinuation { continuation in
                queue.async {
                    let timer = DispatchWorkItem { result.finish((-1, nil)) }
                    result.install { value in
                        queue.async {
                            timer.cancel()
                            connection.stateUpdateHandler = nil
                            connection.cancel()
                            continuation.resume(returning: value)
                        }
                    }
                    guard !result.isFinished else { return }
                    connection.stateUpdateHandler = { state in
                        switch state {
                        case .ready:
                            result.finish((Int((DispatchTime.now().uptimeNanoseconds - started) / 1_000_000), address(of: connection)))
                        case .failed, .cancelled: result.finish((-1, nil))
                        default: break
                        }
                    }
                    connection.start(queue: queue)
                    queue.asyncAfter(deadline: .now() + timeout, execute: timer)
                }
            }
        }, onCancel: { result.finish((-1, nil)) })
    }

    private static func address(of connection: NWConnection) -> String? {
        guard case let .hostPort(host, _)? = connection.currentPath?.remoteEndpoint else { return nil }
        switch host {
        case let .ipv4(value): return "\(value)"
        case let .ipv6(value): return "\(value)"
        default: return nil
        }
    }
}
