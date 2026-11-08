import Foundation

/// JSON builder fixtures, run by test_ping_contracts.py --swift on an Apple host.
/// The bridge is a stub: native sticky selection requires separate integration tests.
enum AutoStickyConfigurationTests {
    static let marker = "nimbo-auto-sticky-v1"
    static let dns: [String: Any] = ["servers": ["https://dns.example/dns-query"], "queryStrategy": "UseIPv4"]
    static let stream: [String: Any] = ["network": "ws", "security": "tls",
        "tlsSettings": ["serverName": "fixture.example"], "wsSettings": ["path": "/fixture"]]

    static func run() throws {
        let savedModules = XrayConfigurationBuilder.moduleRulesJSON
        let savedProfile = XrayConfigurationBuilder.routingProfileJSON
        defer {
            XrayConfigurationBuilder.moduleRulesJSON = savedModules
            XrayConfigurationBuilder.routingProfileJSON = savedProfile
        }
        XrayConfigurationBuilder.moduleRulesJSON = ""
        XrayConfigurationBuilder.routingProfileJSON = ""
        try automaticPool()
        try singleAndNonAutomaticSources()
        try nativeUserRouting()
        try moduleAndProfileRouting()
        print("PASS: automatic sticky JSON opt-in, 16-node pool, single/custom preservation, no direct fallback (stub bridge)")
    }

    static func converted(_ count: Int) -> [String: Any] {
        let outbounds: [[String: Any]] = (0..<count).map { index in
            ["tag": "node-\(index)", "protocol": "vless", "streamSettings": stream,
             "settings": ["vnext": [["address": "node-\(index).example", "port": 443]]]]
        }
        return ["outbounds": outbounds, "dns": dns]
    }

    static func envelope(_ count: Int, automatic: Bool? = true) -> [String: Any] {
        var result: [String: Any] = ["shareLinks": (0..<count).map { "vless://fixture@node-\($0).example:443" }]
        if let automatic { result["nimbo"] = ["balancer": automatic] }
        return result
    }

    static func prepare(_ source: Any, converted: [String: Any]? = nil) throws -> [String: Any] {
        let bridge = LibXrayBridge(convertedConfiguration: converted)
        let data: Data
        if let text = source as? String { data = Data(text.utf8) }
        else { data = try JSONSerialization.data(withJSONObject: source) }
        let prepared = try XrayConfigurationBuilder.prepare(sourceData: data, tunnelFileDescriptor: 4,
            tunnelInterfaceName: "utun7", assetDirectory: "/tmp", bridge: bridge)
        if let native = source as? [String: Any], native["outbounds"] != nil {
            precondition(bridge.conversionCount == 0, "Native JSON must never pass through share-link conversion")
        } else {
            precondition(bridge.conversionCount == 1)
        }
        precondition(!prepared.pingRouteVerified)
        return try JSONSerialization.jsonObject(with: Data(prepared.json.utf8)) as! [String: Any]
    }

    static func assertDNSAndTransports(_ object: [String: Any], count: Int) {
        precondition(NSDictionary(dictionary: object["dns"] as! [String: Any]).isEqual(to: dns))
        let outbounds = object["outbounds"] as! [[String: Any]]
        precondition(outbounds.count == count + 2)
        for index in 0..<count {
            precondition(NSDictionary(dictionary: outbounds[index]["streamSettings"] as! [String: Any]).isEqual(to: stream))
            let expected = (converted(count)["outbounds"] as! [[String: Any]])[index]["settings"] as! [String: Any]
            precondition(NSDictionary(dictionary: outbounds[index]["settings"] as! [String: Any]).isEqual(to: expected))
        }
    }

    static func assertBalancer(_ object: [String: Any], tag: String, interval: String) {
        let routing = object["routing"] as! [String: Any]
        let balancers = routing["balancers"] as! [[String: Any]]
        precondition(balancers.count == 1)
        let balancer = balancers[0]
        precondition(balancer["tag"] as? String == tag)
        precondition(balancer["selector"] as? [String] == ["proxy/"])
        precondition((balancer["strategy"] as? [String: String]) == ["type": "leastPing"])
        precondition(balancer["fallbackTag"] as? String == "proxy/0", "No direct fallback, including before first observation")
        let rules = routing["rules"] as! [[String: Any]]
        precondition(rules.last?["balancerTag"] as? String == tag)
        precondition(rules.last?["network"] as? String == "tcp,udp")
        precondition(rules.last?["outboundTag"] == nil)
        let observatory = object["observatory"] as! [String: Any]
        precondition(observatory["probeInterval"] as? String == interval)
        precondition(observatory["subjectSelector"] as? [String] == ["proxy/"])
        precondition(observatory["probeURL"] as? String == "https://www.gstatic.com/generate_204")
        precondition(observatory["enableConcurrency"] as? Bool == true)
    }

    static func automaticPool() throws {
        let object = try prepare(envelope(16), converted: converted(16))
        assertBalancer(object, tag: marker, interval: "30s")
        assertDNSAndTransports(object, count: 16)
        let proxies = (object["outbounds"] as! [[String: Any]]).prefix(16)
        precondition(proxies.compactMap { $0["tag"] as? String } == (0..<16).map { "proxy/\($0)" })
        precondition(proxies.allSatisfy { $0["protocol"] as? String == "vless" })
        let rules = (object["routing"] as! [String: Any])["rules"] as! [[String: Any]]
        precondition(rules.count == 1, "Automatic catch-all must not introduce a direct route")
    }

    static func singleAndNonAutomaticSources() throws {
        let sources: [(Any, Int)] = [(envelope(1), 1), (converted(1), 1),
            ("vless://fixture@node-0.example:443", 1), (envelope(2, automatic: false), 2),
            ("vless://fixture@node-0.example:443\nvless://fixture@node-1.example:443", 2)]
        for (source, count) in sources {
            let object = try prepare(source, converted: converted(count))
            let routing = object["routing"] as! [String: Any]
            precondition(routing["balancers"] == nil && object["observatory"] == nil)
            precondition((routing["rules"] as! [[String: Any]]).isEmpty)
            precondition((object["outbounds"] as! [[String: Any]])[0]["tag"] as? String == "node-0")
            assertDNSAndTransports(object, count: count)
        }
        // Legacy shareLinks envelopes keep their existing balancer without opting in.
        let legacy = try prepare(envelope(2, automatic: nil), converted: converted(2))
        assertBalancer(legacy, tag: "balancer", interval: "90s")
    }

    static func nativeUserRouting() throws {
        for incidentalEnvelope in [false, true] {
            var native = converted(2)
            let routing: [String: Any] = ["domainStrategy": "AsIs", "balancers": [["tag": "user-balancer",
                "selector": ["node-"], "strategy": ["type": "leastPing"], "fallbackTag": "node-1"]],
                "rules": [["type": "field", "network": "tcp,udp", "balancerTag": "user-balancer"]]]
            let observatory: [String: Any] = ["subjectSelector": ["node-"], "probeInterval": "71s",
                "probeURL": "https://probe.example/check", "enableConcurrency": false]
            native["routing"] = routing
            native["observatory"] = observatory
            if incidentalEnvelope {
                native.merge(envelope(16)) { _, extra in extra }
            }
            let object = try prepare(native)
            precondition(NSDictionary(dictionary: object["routing"] as! [String: Any]).isEqual(to: routing))
            precondition(NSDictionary(dictionary: object["observatory"] as! [String: Any]).isEqual(to: observatory))
            precondition((object["outbounds"] as! [[String: Any]]).prefix(2).compactMap { $0["tag"] as? String } == ["node-0", "node-1"])
            assertDNSAndTransports(object, count: 2)
        }
    }

    static func moduleAndProfileRouting() throws {
        XrayConfigurationBuilder.moduleRulesJSON = #"[{"type":"field","domain":["domain:direct.example"],"outboundTag":"direct"},{"type":"field","domain":["domain:proxy.example"],"outboundTag":"proxy"}]"#
        XrayConfigurationBuilder.routingProfileJSON = #"{"domainStrategy":"AsIs","rules":[{"type":"field","domain":["domain:block.example"],"outboundTag":"block"},{"type":"field","domain":["domain:profile.example"],"outboundTag":"proxy"}]}"#
        for automatic in [false, true] {
            let object = try prepare(envelope(2, automatic: automatic), converted: converted(2))
            let routing = object["routing"] as! [String: Any]
            let rules = routing["rules"] as! [[String: Any]]
            precondition(routing["domainStrategy"] as? String == "AsIs")
            precondition(rules.count == (automatic ? 5 : 4))
            precondition(rules[0]["outboundTag"] as? String == "direct")
            precondition(rules[2]["outboundTag"] as? String == "block")
            for index in [1, 3] {
                if automatic {
                    precondition(rules[index]["balancerTag"] as? String == marker && rules[index]["outboundTag"] == nil)
                } else {
                    precondition(rules[index]["outboundTag"] as? String == "node-0" && rules[index]["balancerTag"] == nil)
                }
            }
            if automatic { assertBalancer(object, tag: marker, interval: "30s") }
        }
    }
}
