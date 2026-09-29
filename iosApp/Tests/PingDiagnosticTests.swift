import Foundation

enum PingDiagnosticTests {
    /// No wall-clock sleeps: each attempted target consumes its configured timeout.
    final class Clock: @unchecked Sendable {
        private let lock = NSLock()
        private var elapsed: TimeInterval = 0
        private var attempted: [String] = []
        private var published: [String: Int] = [:]
        var now: TimeInterval { lock.lock(); defer { lock.unlock() }; return elapsed }
        var visits: [String] { lock.lock(); defer { lock.unlock() }; return attempted }
        var progress: [String: Int] { lock.lock(); defer { lock.unlock() }; return published }
        func attempt(_ id: String, timeout: TimeInterval) {
            lock.lock(); defer { lock.unlock() }
            elapsed += timeout
            attempted.append(id)
        }
        func publish(_ id: String, value: Int) {
            lock.lock(); defer { lock.unlock() }
            precondition(published[id] == nil, "Each completed target publishes once")
            published[id] = value
        }
    }

    static func batchBudgetTests() async {
        let targets = (0..<92).map { (id: "node-\($0)", host: "", port: 0) }
        let configs = Dictionary(uniqueKeysWithValues: targets.map { ($0.id, "config-\($0.id)") })
        let clock = Clock()
        let service = NimboPingService(diagnosticProbe: { id, _, _, timeout in
            precondition(timeout == 3, "Every ordinary-list target retains its whole timeout")
            clock.attempt(id, timeout: timeout)
            return -1
        }, diagnosticClock: { clock.now })
        let results = await service.measureAll(targets, configurations: configs, progress: { clock.publish($0, value: $1) })
        precondition(NimboPingService.diagnosticBatchBudget(count: 92, timeout: 3) == 368)
        precondition(clock.now == 276 && clock.visits == targets.map(\.id), "A 60s cap would silently skip most of these92 nodes")
        precondition(results?.count == 92 && clock.progress == results!, "Publish all92 completed timeouts, not queued placeholders")

        let huge = (0..<1000).map { (id: "large-\($0)", host: "", port: 0) }
        let hugeConfigs = Dictionary(uniqueKeysWithValues: huge.map { ($0.id, "config") })
        let cappedClock = Clock()
        let cappedService = NimboPingService(diagnosticProbe: { id, _, _, timeout in
            cappedClock.attempt(id, timeout: timeout)
            return -1
        }, diagnosticClock: { cappedClock.now })
        let capped = await cappedService.measureAll(huge, configurations: hugeConfigs, progress: { cappedClock.publish($0, value: $1) })
        precondition(NimboPingService.diagnosticBatchBudget(count: 1000, timeout: 3) == 1800)
        precondition(cappedClock.now == 1800 && cappedClock.visits.count == 600)
        precondition(capped?.count == 600 && cappedClock.progress == capped!)
        precondition(capped?["large-600"] == nil && capped?["large-999"] == nil, "Unattempted targets must not be synthetic failures")

        let cancelClock = Clock()
        let entered = NimboPingCompletion<Bool>()
        let release = NimboPingCompletion<Int>()
        let cancelService = NimboPingService(diagnosticProbe: { id, _, _, timeout in
            cancelClock.attempt(id, timeout: timeout)
            if id == "node-20" {
                entered.finish(true)
                return await withCheckedContinuation { continuation in release.install { continuation.resume(returning: $0) } }
            }
            return 0
        }, diagnosticClock: { cancelClock.now })
        let task = Task {
            await cancelService.measureAll(targets, configurations: configs, progress: { cancelClock.publish($0, value: $1) })
        }
        _ = await withCheckedContinuation { continuation in entered.install { continuation.resume(returning: $0) } }
        task.cancel()
        release.finish(-1) // Native cancellation returns only after cleanup.
        let cancelled = await task.value
        precondition(cancelled == nil, "Cancelled batch must not overwrite the UI with a final failure dictionary")
        precondition(cancelClock.now == 63 && cancelClock.visits == Array(targets.prefix(21).map(\.id)))
        precondition(cancelClock.progress.count == 20 && cancelClock.progress.values.allSatisfy { $0 == 0 })
        precondition(cancelClock.progress["node-20"] == nil && cancelClock.progress["node-91"] == nil, "No interrupted or queued target may publish a false failure")
        print("PASS: fake-clock92 slow nodes, proportional budget,30minute cap, cancellation with completed-only progress")
    }

    /// Synchronous C-ABI seam: cancellation runs concurrently; Run returns only
    /// after its mock lifetime closes. The real Go route proof is tested by native tests.
    final class Backend: @unchecked Sendable {
        private let condition = NSCondition()
        private var cancelled: Set<String> = []
        private(set) var requests: [[String: Any]] = []
        private(set) var active = 0
        private(set) var maximumActive = 0
        var transport: NimboDiagnosticProbe.Transport {
            .init(run: { self.run($0) }, cancel: { self.cancel($0) })
        }
        private func run(_ data: Data) -> Data? {
            let request = try! JSONSerialization.jsonObject(with: data) as! [String: Any]
            condition.lock()
            defer { active -= 1; condition.unlock() }
            active += 1
            maximumActive = max(maximumActive, active)
            requests.append(request)
            let id = request["requestID"] as! String
            if request["config"] as? String == "slow" {
                let limit = Date().addingTimeInterval(2)
                while !cancelled.contains(id) && condition.wait(until: limit) {}
            }
            let wrong = request["config"] as? String == "wrong-identity"
            return try! JSONSerialization.data(withJSONObject: [
                "ok": !cancelled.contains(id), "requestID": id,
                "serverID": wrong ? "unrelated" : request["serverID"]!, "latency": 0
            ])
        }
        private func cancel(_ data: Data) {
            let request = try! JSONSerialization.jsonObject(with: data) as! [String: Any]
            condition.lock()
            cancelled.insert(request["requestID"] as! String)
            condition.broadcast()
            condition.unlock()
        }
    }

    static func run() async {
        let defaults = UserDefaults.standard
        let keys = ["com.nimbo.ping.protocol", "com.nimbo.ping.url", "com.nimbo.ping.timeoutMs"]
        let saved = keys.map { defaults.object(forKey: $0) }
        defer { for (key, value) in zip(keys, saved) { defaults.set(value, forKey: key) } }
        defaults.set("nimbo", forKey: keys[0])
        defaults.set("https://example.invalid/probe?q=1", forKey: keys[1])
        defaults.set(3000, forKey: keys[2])
        await batchBudgetTests()
        let backend = Backend()
        let service = NimboPingService(routeProbe: { _, _, _, _, _, _ in
            preconditionFailure("Nimbo must not consult or mutate the connected VPN")
        }, diagnosticProbe: { id, config, url, timeout in
            await NimboDiagnosticProbe.measure(serverID: id, configuration: config, url: url, timeout: timeout, transport: backend.transport)
        })
        let targets = [(id: "a", host: "", port: 0), (id: "b", host: "", port: 0), (id: "missing", host: "", port: 0)]
        let result = await service.measureAll(targets, configurations: ["a": "route-A", "b": "route-B"])
        precondition(result == ["a": 0, "b": 0, "missing": -1], "Each disconnected server requires its own diagnostic")
        precondition(backend.requests.map { $0["config"] as! String } == ["route-A", "route-B"])
        precondition(backend.requests.map { $0["serverID"] as! String } == ["a", "b"])
        precondition(backend.requests.allSatisfy { $0["method"] as? String == "GET" && $0["url"] as? String == "https://example.invalid/probe?q=1" })
        precondition(backend.maximumActive == 1 && backend.active == 0)
        let url = URL(string: "https://example.invalid/")!
        let legacy = """
        {"outbounds":[{"protocol":"vless","tag":"saved-tag","sendThrough":"🇩🇪 Old beta label","settings":{"vnext":[{"address":"proxy.example","port":443}]},"streamSettings":{"sockopt":{"dialerProxy":"do-not-strip"}}}],"routing":{"rules":[{"outboundTag":"direct"}]}}
        """
        _ = await NimboDiagnosticProbe.measure(serverID: "legacy", configuration: legacy, url: url, timeout: 1, transport: backend.transport)
        let submitted = backend.requests.last!["config"] as! String
        let originalObject = try! JSONSerialization.jsonObject(with: Data(legacy.utf8)) as! [String: Any]
        let migratedObject = try! JSONSerialization.jsonObject(with: Data(submitted.utf8)) as! [String: Any]
        let oldOutbound = (originalObject["outbounds"] as! [[String: Any]])[0]
        let newOutbound = (migratedObject["outbounds"] as! [[String: Any]])[0]
        var expectedOutbound = oldOutbound
        expectedOutbound.removeValue(forKey: "sendThrough")
        precondition(NSDictionary(dictionary: newOutbound).isEqual(to: expectedOutbound), "Legacy label migration must not alter route/security fields")
        precondition(NSDictionary(dictionary: migratedObject["routing"] as! [String: Any]).isEqual(to: originalObject["routing"] as! [String: Any]))
        for bind in ["127.0.0.1", "0.0.0.0", " 192.0.2.10 ", "::", "2001:db8::1", "::ffff:192.0.2.10", "fe80::1%en0", "[::1]", "192.0.2.0/24"] {
            let data = try! JSONSerialization.data(withJSONObject: ["outbounds": [["protocol": "vless", "sendThrough": bind]]])
            let raw = String(decoding: data, as: UTF8.self)
            precondition(NimboDiagnosticProbe.migratingLegacyLabels(raw) == raw, "Actual or scoped binds must reach native unchanged for explicit unsupported-bind failure")
        }
        let malformed = "{not-json"
        precondition(NimboDiagnosticProbe.migratingLegacyLabels(malformed) == malformed)
        let share = "vless://fixture@proxy.example:443#Old%20label"
        precondition(NimboDiagnosticProbe.migratingLegacyLabels(share) == share)
        let wrong = await NimboDiagnosticProbe.measure(serverID: "a", configuration: "wrong-identity", url: url, timeout: 1, transport: backend.transport)
        precondition(wrong == -1, "A different server's response is never credited")
        let timed = await NimboDiagnosticProbe.measure(serverID: "a", configuration: "slow", url: url, timeout: 0.05, transport: backend.transport)
        precondition(timed == -1 && backend.active == 0, "Deadline must cancel native work and await cleanup")
        let task = Task { await NimboDiagnosticProbe.measure(serverID: "a", configuration: "slow", url: url, timeout: 2, transport: backend.transport) }
        try? await Task.sleep(nanoseconds: 50_000_000)
        task.cancel()
        let cancelled = await task.value
        precondition(cancelled == -1 && backend.active == 0, "Task cancellation must await native cleanup")

        let gate = NimboPingCompletion<Int>()
        let started = NimboPingCompletion<Bool>()
        let staleProgress = Clock()
        let staleService = NimboPingService(diagnosticProbe: { _, _, _, _ in
            started.finish(true)
            return await withCheckedContinuation { continuation in gate.install { continuation.resume(returning: $0) } }
        })
        let staleTask = Task {
            await staleService.measureAll(targets, configurations: ["a": "route-A", "b": "route-B"], progress: { staleProgress.publish($0, value: $1) })
        }
        _ = await withCheckedContinuation { continuation in started.install { continuation.resume(returning: $0) } }
        defaults.set("tcp", forKey: keys[0])
        NotificationCenter.default.post(name: UserDefaults.didChangeNotification, object: defaults)
        gate.finish(0)
        let stale = await staleTask.value
        precondition(stale == nil && staleProgress.progress.isEmpty, "Old-mode work must publish neither success nor failure")
        print("PASS: per-server app diagnostics, legacy-label migration preserves binds, no active VPN dependency, cancellation, deadlines, identity and stale-result guards")
    }
}
