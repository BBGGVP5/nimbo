import Foundation

/// Run by test_traffic_statistics_contracts.py --swift on an Apple host.
@main
enum TrafficPolicyTests {
    static func main() throws {
        try preferences()
        try xrayConfigurations()
        try mihomoRuleMode()
        telemetry()
        print("PASS: traffic telemetry, opt-in preference and Xray runtime overlay")
    }

    static func preferences() throws {
        precondition(!NimboRoutingOptions.default.adBlockingEnabled)
        precondition(!NimboRoutingOptions(providerValue: nil).adBlockingEnabled)
        precondition(!NimboRoutingOptions(providerValue: ["dns": "google"]).adBlockingEnabled)
        let options = NimboRoutingOptions(bypassLocalNetworks: false, sniffingEnabled: true,
                                         dnsPreset: "google", adBlockingEnabled: true)
        precondition(NimboRoutingOptions(providerValue: options.providerValue) == options)
        let suite = "NimboTrafficPolicyTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        precondition(!NimboRoutingSettings.load(defaults: defaults).adBlockingEnabled)
        defaults.set(true, forKey: "com.nimbo.routing.adBlocking")
        precondition(NimboRoutingSettings.load(defaults: defaults).adBlockingEnabled)
        defaults.set(false, forKey: "com.nimbo.routing.adBlocking")
        precondition(!NimboRoutingSettings.load(defaults: defaults).adBlockingEnabled)
    }

    static func xrayConfigurations() throws {
        let providerRule: [String: Any] = ["type": "field", "domain": ["domain:provider.example"], "outboundTag": "block"]
        let original: [String: Any] = ["outbounds": [["tag": "proxy", "protocol": "vless"],
            ["tag": "nimbo-ad-block", "protocol": "freedom"], ["tag": "block", "protocol": "blackhole"]],
            "routing": ["rules": [providerRule, ["type": "field", "network": "tcp,udp", "outboundTag": "proxy"]]]]
        precondition(NSDictionary(dictionary: NimboAdBlocking.applying(to: original, enabled: false)).isEqual(to: original))
        let effective = NimboAdBlocking.applying(to: original, enabled: true)
        let routes = (effective["routing"] as! [String: Any])["rules"] as! [[String: Any]]
        precondition(routes.count == 3)
        precondition(NSDictionary(dictionary: routes[1]).isEqual(to: providerRule))
        let block = routes[0]["outboundTag"] as! String
        precondition(block != "nimbo-ad-block")
        precondition((effective["outbounds"] as! [[String: Any]]).contains { $0["tag"] as? String == block && $0["protocol"] as? String == "blackhole" })
        precondition((routes[0]["domain"] as! [String]).count == 20)
        precondition(routes[0]["inboundTag"] as! [String] == ["tun-in"])

        let full = try JSONSerialization.data(withJSONObject: original, options: [.sortedKeys])
        let digestInput = full
        for source in [full, Data("vless://test-source".utf8)] {
            for enabled in [false, true] {
                let options = NimboRoutingOptions(bypassLocalNetworks: true, sniffingEnabled: false,
                    dnsPreset: "cloudflare", adBlockingEnabled: enabled)
                let prepared = try XrayConfigurationBuilder.prepare(sourceData: source, tunnelFileDescriptor: 42,
                    tunnelInterfaceName: "utun42", assetDirectory: "/tmp", options: options, bridge: LibXrayBridge())
                let json = try JSONSerialization.jsonObject(with: Data(prepared.json.utf8)) as! [String: Any]
                let rules = (json["routing"] as! [String: Any])["rules"] as! [[String: Any]]
                let overlay = rules.filter { ($0["domain"] as? [String])?.contains("domain:doubleclick.net") == true }
                precondition(overlay.count == (enabled ? 1 : 0))
                if enabled { precondition((rules.first?["domain"] as? [String])?.contains("domain:doubleclick.net") == true) }
                let inbound = (json["inbounds"] as! [[String: Any]]).first!
                if enabled {
                    let sniffing = inbound["sniffing"] as! [String: Any]
                    precondition(sniffing["enabled"] as? Bool == true && sniffing["routeOnly"] as? Bool == true)
                    precondition((sniffing["destOverride"] as! [String]).contains("tls"))
                } else { precondition(inbound["sniffing"] == nil) }
                precondition(!options.sniffingEnabled)
            }
        }
        precondition(full == digestInput)
    }

    static func mihomoRuleMode() throws {
        try NimboAdBlocking.requireMihomoRuleMode("rule", enabled: true)
        try NimboAdBlocking.requireMihomoRuleMode(nil, enabled: true)
        for mode in ["global", "direct", "invalid"] {
            try NimboAdBlocking.requireMihomoRuleMode(mode, enabled: false)
            do {
                try NimboAdBlocking.requireMihomoRuleMode(mode, enabled: true)
                preconditionFailure("Non-rule mode must fail before a connection switch")
            } catch NimboAdBlockingError.requiresMihomoRuleMode { }
        }
    }

    static func telemetry() {
        precondition(NimboTrafficTelemetry.decode(nil) == nil)
        precondition(NimboTrafficTelemetry.decode([:]) == nil)
        precondition(NimboTrafficTelemetry.decode(["upload": -1, "download": 0]) == nil)
        precondition(NimboTrafficTelemetry.decode(["upload": true, "download": 0]) == nil)
        var data: [String: Any] = ["upload": 100, "download": 200, "routeAvailable": true,
            "proxyUpload": 60, "proxyDownload": 40, "directUpload": 0, "directDownload": 0,
            "tcpConnections": 0, "udpConnections": 3]
        let valid = NimboTrafficTelemetry.decode(data)!
        precondition(valid.routes?.proxyUpload == 60 && valid.routes?.directDownload == 0)
        precondition(valid.tcpConnections == 0 && valid.udpConnections == 3)
        data.removeValue(forKey: "directDownload")
        precondition(NimboTrafficTelemetry.decode(data)?.routes == nil)
        data["routeAvailable"] = false; data["tcpConnections"] = -1; data["udpConnections"] = UInt64.max
        let unsupported = NimboTrafficTelemetry.decode(data)!
        precondition(unsupported.routes == nil && unsupported.tcpConnections == nil && unsupported.udpConnections == nil)
        precondition(NimboTunnelReport.decode(["ok": true, "received": -1, "sent": 0]) == nil)
        let old = NimboTunnelReport.decode(["ok": true, "received": 1, "sent": 2])!
        precondition(old.telemetry == nil && old.activeAdBlockingEnabled == nil)
        let identity = NimboMihomoSessionPolicy.Identity(generation: 7, sourceSHA256: String(repeating: "a", count: 64))
        precondition(!NimboMihomoSessionPolicy.matchesEnvelope(["apiVersion": 1, "success": true,
            "requestId": "sample", "generation": 6], requestID: "sample", identity: identity))
    }
}
