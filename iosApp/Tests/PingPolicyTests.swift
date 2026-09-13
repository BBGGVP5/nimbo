import Foundation
import CoreFoundation
import Darwin

@main
enum PingPolicyTests {
    static func main() {
        Task {
            do {
                try policyTests()
                try routingTests()
                await networkTests()
                await PingDiagnosticTests.run()
                print("PASS: Swift ping policy, routing, HTTP/SOCKS/TLS, cancellation and attribution contracts")
                exit(0)
            } catch { fatalError("Ping test failed: \(error)") }
        }
        CFRunLoopRun()
    }

    static func policyTests() throws {
        precondition(NimboPingProtocol(stored: nil) == .nimbo)
        precondition(NimboPingProtocol(stored: "bad") == .nimbo)
        precondition(NimboPingProtocol(stored: "") == .nimbo)
        precondition(NimboPingProtocol(stored: "tcp") == .tcp)
        precondition(NimboPingProtocol(stored: "http") == .httpHead)
        for mode in NimboPingProtocol.allCases { precondition(NimboPingProtocol(stored: mode.rawValue) == mode) }
        precondition(NimboPingProtocol.nimbo.httpMethod == "GET")
        precondition(NimboPingProtocol.httpGet.httpMethod == "GET")
        precondition(NimboPingProtocol.httpHead.httpMethod == "HEAD")
        precondition(NimboPingProtocol.icmp.httpMethod == nil)
        precondition(NimboPingPolicy.displayMilliseconds(raw: 330, mode: .nimbo) == "≈100")
        precondition(NimboPingPolicy.displayMilliseconds(raw: 990, mode: .nimbo) == "≈300")
        precondition(NimboPingPolicy.displayMilliseconds(raw: 1387, mode: .nimbo) == "≈420")
        precondition(NimboPingPolicy.displayMilliseconds(raw: 332, mode: .nimbo) == "≈101")
        precondition(NimboPingPolicy.displayMilliseconds(raw: 0, mode: .nimbo) == "≈0")
        precondition(NimboPingPolicy.displayMilliseconds(raw: -1, mode: .nimbo) == "—")
        for mode in [NimboPingProtocol.tcp, .httpGet, .httpHead, .icmp] {
            precondition(NimboPingPolicy.displayMilliseconds(raw: 330, mode: mode) == "330")
            precondition(NimboPingPolicy.displayMilliseconds(raw: 0, mode: mode) == "0")
        }
        precondition(NimboPingPolicy.selectionRank(0) == 0)
        precondition(NimboPingPolicy.selectionRank(1387) == 1387, "Balancer ranks RAW latency, never display-scaled values")
        precondition(NimboPingPolicy.selectionRank(0) < NimboPingPolicy.selectionRank(1))
        precondition(NimboPingPolicy.selectionRank(1) < NimboPingPolicy.selectionRank(nil))
        precondition(NimboPingPolicy.selectionRank(nil) < NimboPingPolicy.selectionRank(-1))
        precondition(NimboPingPolicy.timeout(milliseconds: -1) == 3)
        precondition(NimboPingPolicy.timeout(milliseconds: 1) == 1)
        precondition(NimboPingPolicy.timeout(milliseconds: Int.max) == 10)
        for raw in ["file:///etc/hosts", "ftp://host/file", "https://", "https://user:pass@host/", "https://host/#fragment", "https://host:0/", "https://host:65536/", "https://host/a\r\nInjected:yes", " https://host/", "https://host/a b"] {
            precondition(NimboPingPolicy.checkedURL(raw) == nil, "Accepted invalid URL")
        }
        let url = NimboPingPolicy.checkedURL("https://example.com:8443/a%20b?x=1")!
        for method in ["GET", "HEAD"] {
            let request = String(decoding: NimboPingPolicy.request(url: url, method: method)!, as: UTF8.self)
            precondition(request.hasPrefix("\(method) /a%20b?x=1 HTTP/1.1\r\nHost: example.com:8443\r\n"))
        }
        let ipv6 = NimboPingPolicy.checkedURL("http://[::1]:8080/")!
        precondition(String(decoding: NimboPingPolicy.request(url: ipv6, method: "GET")!, as: UTF8.self).contains("Host: [::1]:8080"))
        precondition(NimboPingPolicy.request(url: url, method: "POST") == nil)
        for status in [100, 204, 301, 404, 503] {
            precondition(NimboPingPolicy.responseStatus(Data("HTTP/1.1 \(status) Test\r\n\r\n".utf8)) == status)
        }
        precondition(NimboPingPolicy.responseStatus(Data("not HTTP\r\n\r\n".utf8)) == nil)

        let nonce: [UInt8] = Array(0..<16)
        for ipv6 in [false, true] {
            var reply = NimboICMPPacket.request(ipv6: ipv6, identifier: 0x1234, sequence: 7, nonce: nonce)
            reply[0] = ipv6 ? 129 : 0
            reply[2] = 0; reply[3] = 0
            if !ipv6 {
                let checksum = NimboICMPPacket.checksum(reply)
                reply[2] = UInt8(checksum >> 8); reply[3] = UInt8(checksum & 255)
                var header = [UInt8](repeating: 0, count: 20)
                header[0] = 0x45; header[9] = 1
                reply = header + reply
            }
            precondition(NimboICMPPacket.matches(reply, ipv6: ipv6, identifier: 0x1234, sequence: 7, nonce: nonce))
            precondition(!NimboICMPPacket.matches(reply, ipv6: ipv6, identifier: 0x1234, sequence: 8, nonce: nonce))
            precondition(!NimboICMPPacket.matches(reply, ipv6: ipv6, identifier: 0x9999, sequence: 7, nonce: nonce))
            reply[reply.count - 1] ^= 1
            precondition(!NimboICMPPacket.matches(reply, ipv6: ipv6, identifier: 0x1234, sequence: 7, nonce: nonce))
        }
        let completion = NimboPingCompletion<Int>()
        completion.finish(-1)
        var calls = 0
        completion.install { value in precondition(value == -1); calls += 1 }
        DispatchQueue.concurrentPerform(iterations: 100) { _ in completion.finish(5) }
        precondition(calls == 1)
    }

    static func routingTests() throws {
        let good: [String: Any] = ["protocol": "vless", "tag": "selected"]
        precondition(NimboPingRoute.verifiedTag(outbounds: [good], balanced: false) == "selected")
        precondition(NimboPingRoute.verifiedTag(outbounds: [good], balanced: true) == nil)
        precondition(NimboPingRoute.verifiedTag(outbounds: [good, good], balanced: false) == nil)
        for proto in ["freedom", "blackhole", "loopback", "dns", "unknown"] {
            precondition(NimboPingRoute.verifiedTag(outbounds: [["protocol": proto, "tag": "selected"]], balanced: false) == nil)
        }
        let extras: [[String: Any]] = [["proxySettings": ["tag": "direct"]], ["streamSettings": ["sockopt": ["dialerProxy": "direct"]]], ["streamSettings": ["xhttpSettings": [:]]]]
        for extra in extras {
            precondition(NimboPingRoute.verifiedTag(outbounds: [good.merging(extra) { _, b in b }], balanced: false) == nil)
        }
        let route = NimboPingRoute(socks: NimboPingSOCKS(port: 54321, username: "fixture", password: "fixture-only"))
        XrayConfigurationBuilder.moduleRulesJSON = "[{\"type\":\"field\",\"network\":\"tcp\",\"outboundTag\":\"direct\"}]"
        defer { XrayConfigurationBuilder.moduleRulesJSON = "" }
        let source = try JSONSerialization.data(withJSONObject: ["outbounds": [good]])
        let prepared = try XrayConfigurationBuilder.prepare(sourceData: source, tunnelFileDescriptor: 4, tunnelInterfaceName: "utun7", assetDirectory: "/tmp", pingRoute: route, bridge: LibXrayBridge())
        precondition(prepared.pingRouteVerified)
        let object = try JSONSerialization.jsonObject(with: Data(prepared.json.utf8)) as! [String: Any]
        let rules = (object["routing"] as! [String: Any])["rules"] as! [[String: Any]]
        precondition(rules[0]["inboundTag"] as? [String] == [NimboPingRoute.inboundTag])
        precondition(rules[0]["outboundTag"] as? String == "selected")
        precondition(rules[1]["outboundTag"] as? String == "direct")
        let inbound = (object["inbounds"] as! [[String: Any]]).last!
        precondition(inbound["listen"] as? String == "127.0.0.1")
        precondition((inbound["settings"] as! [String: Any])["auth"] as? String == "password")
    }

    static func networkTests() async {
        let env = ProcessInfo.processInfo.environment
        let origin = "http://127.0.0.1:\(env["NIMBO_TEST_HTTP_PORT"]!)"
        func url(_ path: String) -> URL { URL(string: origin + path)! }
        let tcp = await NimboPingService.measure(host: "127.0.0.1", port: Int(env["NIMBO_TEST_HTTP_PORT"]!)!, timeout: 1)
        precondition(tcp.latency >= 0)
        let cancelledTCP = Task { await NimboPingService.measure(host: "127.0.0.1", port: 1, timeout: 10) }
        cancelledTCP.cancel()
        let tcpCancelled = await cancelledTCP.value
        precondition(tcpCancelled.latency == -1)
        for method in ["GET", "HEAD"] {
            let value = await NimboPingService.measureHttp(url: url("/method/\(method)"), timeout: 2, method: method)
            precondition(value >= 0, "HTTP method must receive headers")
        }
        for path in ["/interim", "/large-body"] {
            let value = await NimboHTTPProbe.measure(url: url(path), method: "GET", timeout: 2)
            precondition(value >= 0, "Successful final HTTP response must be timed")
        }
        for path in ["/redirect", "/forbidden", "/error", "/upgrade", "/malformed", "/large-header", "/stall"] {
            let value = await NimboHTTPProbe.measure(url: url(path), method: "GET", timeout: 0.15)
            precondition(value == -1, "Invalid/stalled HTTP must fail")
        }
        let task = Task { await NimboHTTPProbe.measure(url: url("/cancel"), method: "GET", timeout: 10) }
        try? await Task.sleep(nanoseconds: 50_000_000)
        let before = ProcessInfo.processInfo.systemUptime
        task.cancel()
        let cancelled = await task.value
        precondition(cancelled == -1 && ProcessInfo.processInfo.systemUptime - before < 1)
        let socks = NimboPingSOCKS(port: Int(env["NIMBO_TEST_SOCKS_PORT"]!)!, username: "fixture", password: "fixture-only")
        let viaProxy = await NimboHTTPProbe.measure(url: url("/must-not-hit-origin"), method: "GET", timeout: 2, socks: socks)
        precondition(viaProxy >= 0, "Explicit SOCKS route must work even for a loopback URL")
        let badProxy = NimboPingSOCKS(port: socks.port, username: "wrong", password: "wrong")
        let failedProxy = await NimboHTTPProbe.measure(url: url("/must-not-hit-origin"), method: "GET", timeout: 1, socks: badProxy)
        precondition(failedProxy == -1, "Rejected proxy must never fall back directly")
        let remote = await NimboHTTPProbe.measure(url: URL(string: "http://only-via-proxy.invalid:8443/remote-dns?exact=1")!, method: "HEAD", timeout: 2, socks: socks)
        precondition(remote >= 0, "Original unresolved target authority must be sent in SOCKS CONNECT, not resolved locally")
        for host in ["reject.invalid", "bad-reply.invalid"] {
            let rejected = await NimboHTTPProbe.measure(url: URL(string: "http://\(host)/must-not-hit-origin")!, method: "GET", timeout: 1, socks: socks)
            precondition(rejected == -1, "CONNECT status/version/reserved framing must be validated without fallback")
        }
        let stalledSOCKS = NimboPingSOCKS(port: socks.port, username: "stall", password: "fixture-only")
        let handshakeStarted = ProcessInfo.processInfo.systemUptime
        let handshakeTimed = await NimboHTTPProbe.measure(url: url("/must-not-hit-origin"), method: "GET", timeout: 0.15, socks: stalledSOCKS)
        precondition(handshakeTimed == -1 && ProcessInfo.processInfo.systemUptime - handshakeStarted < 1)
        let handshakeTask = Task { await NimboHTTPProbe.measure(url: url("/must-not-hit-origin"), method: "GET", timeout: 10, socks: stalledSOCKS) }
        let stalled = await NimboHTTPProbe.measure(url: url("/wait-second-socks-stall"), method: "GET", timeout: 3)
        precondition(stalled >= 0, "Wait for native SOCKS auth to stall before cancelling, not a scheduling guess")
        let handshakeCancelledAt = ProcessInfo.processInfo.systemUptime
        handshakeTask.cancel()
        let handshakeCancelled = await handshakeTask.value
        precondition(handshakeCancelled == -1 && ProcessInfo.processInfo.systemUptime - handshakeCancelledAt < 1)
        let tunnelTLS = await NimboHTTPProbe.measure(url: URL(string: "https://tls-through.invalid/must-not-hit-origin")!, method: "GET", timeout: 2, socks: socks)
        precondition(tunnelTLS == -1, "TLS after CONNECT must reject an untrusted certificate with original peer-name checking")
        let tls = await NimboHTTPProbe.measure(url: URL(string: "https://localhost:\(env["NIMBO_TEST_TLS_PORT"]!)/")!, method: "GET", timeout: 2)
        precondition(tls == -1, "An untrusted local TLS certificate must fail")
        let defaults = UserDefaults.standard
        let keys = ["com.nimbo.ping.protocol", "com.nimbo.ping.url", "com.nimbo.ping.timeoutMs", "com.nimbo.ping.display"]
        let saved = keys.map { defaults.object(forKey: $0) }
        defer { for (key, value) in zip(keys, saved) { defaults.set(value, forKey: key) } }
        defaults.set(origin + "/must-not-hit-origin", forKey: keys[1])
        for mode in ["nimbo", "http_get", "http_head", "http"] {
            defaults.set(mode, forKey: keys[0])
            let values = await NimboPingService.shared.measureAll([(id: "a", host: "127.0.0.1", port: 1), (id: "b", host: "127.0.0.1", port: 2)])
            precondition(values == ["a": -1, "b": -1], "Disconnected route probes must clear every old value")
        }
        defaults.set("http_get", forKey: keys[0])
        let targets = [(id: "a", host: "host-a", port: 1), (id: "b", host: "host-b", port: 2)]
        let attributed = NimboPingService(routeProbe: { _, _, requestedURL, _, method, nimbo in
            precondition(requestedURL.absoluteString == origin + "/must-not-hit-origin")
            precondition(method == "GET" && !nimbo)
            return ("a", 0)
        })
        let sample = await attributed.measureAll(targets)
        precondition(sample == ["a": 0, "b": -1], "Only the confirmed active route gets zero-ms success")
        for (key, changed) in [(keys[0], "http_head"), (keys[1], origin + "/changed"), (keys[2], "10000")] {
            let gate = NimboPingCompletion<(id: String, latency: Int)>()
            let service = NimboPingService(routeProbe: { _, _, _, _, _, _ in
                await withCheckedContinuation { continuation in gate.install { continuation.resume(returning: $0) } }
            })
            let task = Task { await service.measureAll(targets) }
            try? await Task.sleep(nanoseconds: 50_000_000)
            let old = defaults.object(forKey: key)
            defaults.set(changed, forKey: key)
            NotificationCenter.default.post(name: UserDefaults.didChangeNotification, object: defaults)
            defaults.set(old, forKey: key)
            gate.finish(("a", 12))
            let stale = await task.value
            precondition(stale == ["a": -1, "b": -1], "Settings changed away/back must reject in-flight success")
        }
        let displayGate = NimboPingCompletion<(id: String, latency: Int)>()
        let displayService = NimboPingService(routeProbe: { _, _, _, _, _, _ in
            await withCheckedContinuation { continuation in displayGate.install { continuation.resume(returning: $0) } }
        })
        let displayTask = Task { await displayService.measureAll(targets) }
        try? await Task.sleep(nanoseconds: 50_000_000)
        defaults.set("dots", forKey: keys[3])
        NotificationCenter.default.post(name: UserDefaults.didChangeNotification, object: defaults)
        displayGate.finish(("a", 0))
        let displayResult = await displayTask.value
        precondition(displayResult == ["a": 0, "b": -1], "Display-only changes must preserve valid measurements")
        for address in ["127.0.0.1", "::1"] {
            let echo = await NimboICMPProbe.measure(address: address, timeout: 1)
            precondition(echo >= 0, "Unprivileged ICMP loopback must receive a matching echo reply")
        }
        let icmpCancelled = Task { await NimboICMPProbe.measure(address: "invalid-address", timeout: 10) }
        icmpCancelled.cancel()
        let icmp = await icmpCancelled.value
        precondition(icmp == -1)
    }
}
