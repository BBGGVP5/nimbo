import Darwin
import Foundation
#if !NIMBO_PING_TESTS
import LibXray
#endif

/// App-process only. Never include this file in PacketTunnel: Xray has
/// process-global DNS/outbound state even when individual instances are local.
enum NimboDiagnosticProbe {
    struct Transport: Sendable {
        let run: @Sendable (Data) -> Data?
        let cancel: @Sendable (Data) -> Void
    }

    // One native lifetime through cleanup; single-row requests cannot overlap a batch.
    private static let queue = DispatchQueue(label: "com.nimbo.ping.diagnostic", qos: .utility)
    private static let maximumConfigBytes = 1024 * 1024
    private static let maximumResponseBytes = 64 * 1024

    static func measure(serverID: String, configuration: String, url: URL, timeout: TimeInterval) async -> Int {
        await measure(serverID: serverID, configuration: configuration, url: url, timeout: timeout, transport: live)
    }

    static func measure(serverID: String, configuration: String, url: URL, timeout: TimeInterval, transport: Transport) async -> Int {
        guard !Task.isCancelled, timeout.isFinite, timeout > 0, timeout <= 60,
              !serverID.isEmpty, serverID.utf8.count <= 512,
              !configuration.isEmpty, configuration.utf8.count <= maximumConfigBytes,
              NimboPingPolicy.checkedURL(url.absoluteString) != nil else { return -1 }
        let requestID = UUID().uuidString
        let source = configuration.trimmingCharacters(in: .whitespacesAndNewlines)
        let format = source.hasPrefix("{") ? "xray" : (source.contains("[Interface]") && source.contains("[Peer]") ? "awg" : "share")
        let deadline = ProcessInfo.processInfo.systemUptime + timeout
        let diagnosticConfiguration = format == "xray" ? migratingLegacyLabels(configuration) : configuration
        guard diagnosticConfiguration.utf8.count <= maximumConfigBytes else { return -1 }
        let cancelData = try! JSONSerialization.data(withJSONObject: ["apiVersion": 1, "requestID": requestID])
        let state = Cancellation(transport: transport, request: cancelData)
        return await withTaskCancellationHandler(operation: {
            await withCheckedContinuation { continuation in
                let timer = DispatchWorkItem { state.cancel() }
                DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + timeout, execute: timer)
                queue.async {
                    defer { timer.cancel() }
                    let remaining = deadline - ProcessInfo.processInfo.systemUptime
                    guard !state.isCancelled, remaining > 0,
                          let request = try? JSONSerialization.data(withJSONObject: [
                            "apiVersion": 1, "requestID": requestID, "serverID": serverID,
                            "config": diagnosticConfiguration, "format": format, "url": url.absoluteString,
                            "method": "GET", "timeoutMs": max(1, Int(remaining * 1000))
                          ]) else { continuation.resume(returning: -1); return }
                    // Run returns only after its HTTP connections and temporary core close.
                    // Cancellation calls a separate native entry; never runXray/stopXray.
                    let response = transport.run(request)
                    guard !state.isCancelled, ProcessInfo.processInfo.systemUptime < deadline,
                          let response, response.count <= maximumResponseBytes,
                          let object = (try? JSONSerialization.jsonObject(with: response)) as? [String: Any],
                          object["ok"] as? Bool == true,
                          object["requestID"] as? String == requestID,
                          object["serverID"] as? String == serverID,
                          let latency = object["latency"] as? Int, latency >= 0,
                          Double(latency) <= timeout * 1000 else {
                        continuation.resume(returning: -1); return
                    }
                    continuation.resume(returning: latency)
                }
            }
        }, onCancel: { state.cancel() })
    }

    /// Old libXray converters carried display labels in outbound.sendThrough.
    /// Match the tunnel builder's non-IP label migration, without clearing actual
    /// source binds. Also preserve scoped/bracketed/CIDR forms conservatively:
    /// native must reject unsupported binding, never silently change its route.
    /// This transient copy does not alter stored JSON, routing, chains or settings.
    static func migratingLegacyLabels(_ configuration: String) -> String {
        guard var root = (try? JSONSerialization.jsonObject(with: Data(configuration.utf8))) as? [String: Any],
              var outbounds = root["outbounds"] as? [[String: Any]] else { return configuration }
        var changed = false
        for index in outbounds.indices {
            guard let raw = outbounds[index]["sendThrough"] as? String else { continue }
            let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            var ipv4 = in_addr()
            var ipv6 = in6_addr()
            let isIP = value.withCString {
                inet_pton(AF_INET, $0, &ipv4) == 1 || inet_pton(AF_INET6, $0, &ipv6) == 1
            }
            guard !isIP, !value.contains("%"), !value.contains("/"),
                  !value.contains("["), !value.contains("]") else { continue }
            outbounds[index].removeValue(forKey: "sendThrough")
            changed = true
        }
        guard changed else { return configuration }
        root["outbounds"] = outbounds
        guard let data = try? JSONSerialization.data(withJSONObject: root, options: [.sortedKeys]),
              let migrated = String(data: data, encoding: .utf8) else { return configuration }
        return migrated
    }

    private final class Cancellation: @unchecked Sendable {
        private let lock = NSLock()
        private var cancelled = false
        private let transport: Transport
        private let request: Data
        init(transport: Transport, request: Data) { self.transport = transport; self.request = request }
        var isCancelled: Bool { lock.lock(); defer { lock.unlock() }; return cancelled }
        func cancel() {
            lock.lock()
            guard !cancelled else { lock.unlock(); return }
            cancelled = true
            lock.unlock()
            // Run occupies the serial queue. Cancel must be able to interrupt it.
            DispatchQueue.global(qos: .utility).async { self.transport.cancel(self.request) }
        }
    }

    private static let live = Transport(run: { request in
        #if NIMBO_PING_TESTS
        return nil // Tests inject a native transport; never load an app VPN runtime.
        #else
        return String(decoding: request, as: UTF8.self).withCString { pointer in
            guard let response = NimboDiagnosticRun(UnsafeMutablePointer(mutating: pointer)) else { return nil }
            defer { CGoFree(response) }
            let count = strnlen(response, maximumResponseBytes + 1)
            guard count <= maximumResponseBytes else { return nil }
            return Data(bytes: response, count: count)
        }
        #endif
    }, cancel: { request in
        #if !NIMBO_PING_TESTS
        String(decoding: request, as: UTF8.self).withCString { pointer in
            if let response = NimboDiagnosticCancel(UnsafeMutablePointer(mutating: pointer)) { CGoFree(response) }
        }
        #endif
    })
}
