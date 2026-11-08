import Foundation
import Network
import NetworkExtension

/// Transport-specific measurements. -1 is unavailable/failure; zero is success.
actor NimboPingService {
    typealias RouteProbe = (NETunnelProviderSession?, [String], URL, TimeInterval, String, Bool) async -> (id: String, latency: Int)
    typealias DiagnosticProbe = (String, String, URL, TimeInterval) async -> Int
    typealias EndpointProbe = (String, Int, TimeInterval) async -> Bool
    static let shared = NimboPingService()
    private let routeProbe: RouteProbe
    private let diagnosticProbe: DiagnosticProbe
    private let diagnosticClock: () -> TimeInterval
    private let endpointProbe: EndpointProbe
    private let parallelism = 16
    private let preflightParallelism = 8
    private var inFlight = false

    init(routeProbe: @escaping RouteProbe = NimboActiveRouteProbe.measure,
         diagnosticProbe: @escaping DiagnosticProbe = NimboDiagnosticProbe.measure,
         diagnosticClock: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime },
         endpointProbe: @escaping EndpointProbe = { host, port, timeout in
             (await NimboPingService.measure(host: host, port: port, timeout: timeout)).latency >= 0
         }) {
        self.routeProbe = routeProbe
        self.diagnosticProbe = diagnosticProbe
        self.diagnosticClock = diagnosticClock
        self.endpointProbe = endpointProbe
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
        let generation = NimboAutoPingCache.shared.refreshedGeneration
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
            // Defaults notifications may execute synchronously on a writer thread.
            // Never invert its lock order by reading defaults under our lease lock.
            let current = Settings.current
            let currentGeneration = generation == NimboAutoPingCache.shared.generation
            lock.lock()
            defer { lock.unlock() }
            return !invalidated && settings == current && currentGeneration
        }

        deinit { if let observer { NotificationCenter.default.removeObserver(observer) } }
    }

    private static func provenance(_ mode: NimboPingProtocol) -> NimboAutoPingCache.Provenance {
        switch mode {
        case .nimbo: return .configuredRouteHTTP
        // IPC proves the active route, but not that its staged config still
        // equals this subscription snapshot. Do not upgrade it to config proof.
        case .httpGet, .httpHead: return .activeRouteHTTP
        case .tcp: return .tcp
        case .icmp: return .icmp
        }
    }

    func measureOne(host: String, port: Int, id: String = "", session: NETunnelProviderSession? = nil, configuration: String? = nil) async -> Int {
        let lease = SettingsLease()
        let settings = lease.settings
        let ticket = NimboAutoPingCache.shared.begin(configuration: configuration)
        defer { NimboAutoPingCache.shared.discard(ticket) }
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
        NimboAutoPingCache.shared.complete(ticket, latency: value, provenance: Self.provenance(settings.mode),
                                          cancelled: Task.isCancelled || !lease.valid)
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
        // Capture immutable identities and attempt tokens before any probe await.
        let tickets = Dictionary(targets.compactMap { target -> (String, NimboAutoPingCache.Ticket)? in
            guard let ticket = NimboAutoPingCache.shared.begin(configuration: configurations[target.id]) else { return nil }
            return (target.id, ticket)
        }, uniquingKeysWith: { _, latest in latest })
        defer { for ticket in tickets.values { NimboAutoPingCache.shared.discard(ticket) } }
        let unavailable = Dictionary(targets.map { ($0.id, -1) }, uniquingKeysWith: { first, _ in first })
        var results = unavailable
        if settings.mode == .nimbo {
            guard let url = NimboPingPolicy.checkedURL(settings.rawURL) else { return unavailable }
            results = [:] // Unattempted targets are not failed measurements.
            let deadline = diagnosticClock() + Self.diagnosticBatchBudget(count: targets.count, timeout: settings.timeout)
            guard let rejected = await preflight(targets, configurations: configurations, lease: lease,
                                                 deadline: deadline, progress: progress) else { return nil }
            for id in rejected {
                results[id] = -1
                NimboAutoPingCache.shared.complete(tickets[id], latency: -1, provenance: .tcp,
                                                  cancelled: Task.isCancelled || !lease.valid)
            }
            for target in targets {
                if rejected.contains(target.id) { continue }
                let remaining = deadline - diagnosticClock()
                guard !Task.isCancelled, lease.valid, remaining > 0 else { break }
                let value: Int
                if let configuration = configurations[target.id] {
                    value = await diagnosticProbe(target.id, configuration, url, min(settings.timeout, remaining))
                } else { value = -1 } // This target was checked and has no usable config.
                NimboAutoPingCache.shared.complete(tickets[target.id], latency: value, provenance: .configuredRouteHTTP,
                                                  cancelled: Task.isCancelled || !lease.valid)
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
            if sample.latency >= 0, results[sample.id] != nil {
                NimboAutoPingCache.shared.complete(tickets[sample.id], latency: sample.latency, provenance: .activeRouteHTTP,
                                                  cancelled: Task.isCancelled || !lease.valid)
                results[sample.id] = sample.latency
            }
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
                    for await (id, value) in group {
                        NimboAutoPingCache.shared.complete(tickets[id], latency: value, provenance: Self.provenance(settings.mode),
                                                          cancelled: Task.isCancelled || !lease.valid)
                        results[id] = value
                    }
                }
                index += parallelism
            }
        }
        return !Task.isCancelled && lease.valid ? results : nil
    }

    /// Sliding window rejects closed/unreachable TCP endpoints in parallel. A
    /// reachable endpoint earns NO latency: it still needs the serial, forced
    /// per-server HTTP diagnostic. No extra Xray instances or DNS hooks are used.
    private func preflight(_ targets: [(id: String, host: String, port: Int)],
                           configurations: [String: String], lease: SettingsLease, deadline: TimeInterval,
                           progress: (@MainActor (String, Int) -> Void)?) async -> Set<String>? {
        let eligible = targets.filter { target in
            guard let configuration = configurations[target.id] else { return false }
            return NimboPingPolicy.canPreflight(configuration: configuration, host: target.host, port: target.port)
        }
        let probe = endpointProbe
        let clock = diagnosticClock
        var rejected = Set<String>()
        await withTaskGroup(of: (String, Bool).self) { group in
            var next = 0
            func enqueue() {
                guard next < eligible.count, !Task.isCancelled, lease.valid else { return }
                let remaining = deadline - clock()
                guard remaining > 0 else { return }
                let target = eligible[next]
                next += 1
                let timeout = min(lease.settings.timeout, remaining)
                group.addTask { (target.id, await probe(target.host, target.port, timeout)) }
            }
            for _ in 0..<preflightParallelism { enqueue() }
            for await (id, reachable) in group {
                guard !Task.isCancelled, lease.valid else { group.cancelAll(); break }
                if !reachable {
                    rejected.insert(id)
                    await MainActor.run {
                        guard !Task.isCancelled, lease.valid else { return }
                        progress?(id, -1)
                    }
                }
                enqueue()
            }
        }
        return !Task.isCancelled && lease.valid ? rejected : nil
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
