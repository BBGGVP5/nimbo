import Foundation

struct PreparedXrayConfiguration {
    let json: String
    let outboundCount: Int
}

enum XrayConfigurationBuilder {
    private static let maximumInputBytes = 15 * 1_024 * 1_024

    static func prepare(
        sourceData: Data,
        tunnelFileDescriptor: Int32,
        assetDirectory: String,
        bridge: LibXrayBridge
    ) throws -> PreparedXrayConfiguration {
        guard !sourceData.isEmpty else { throw XrayConfigurationError.empty }
        guard sourceData.count <= maximumInputBytes else { throw XrayConfigurationError.tooLarge }
        guard let sourceText = String(data: sourceData, encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines),
              !sourceText.isEmpty else {
            throw XrayConfigurationError.invalidEncoding
        }

        var configuration = try configurationObject(from: sourceText, bridge: bridge)
        let outbounds = configuration["outbounds"] as? [[String: Any]] ?? []
        guard !outbounds.isEmpty else { throw XrayConfigurationError.noOutbounds }

        configuration["log"] = normalizedLog(configuration["log"])
        configuration["inbounds"] = [tunnelInbound]
        configuration["outbounds"] = appendUtilityOutbounds(to: outbounds)
        configuration["routing"] = normalizedRouting(configuration["routing"])

        var environment = configuration["env"] as? [String: Any] ?? [:]
        environment["xray.location.asset"] = assetDirectory
        environment["xray.tun.fd"] = String(tunnelFileDescriptor)
        configuration["env"] = environment

        let data = try JSONSerialization.data(withJSONObject: configuration, options: [.sortedKeys])
        guard data.count <= maximumInputBytes,
              let json = String(data: data, encoding: .utf8) else {
            throw XrayConfigurationError.tooLarge
        }
        return PreparedXrayConfiguration(json: json, outboundCount: outbounds.count)
    }

    private static func configurationObject(
        from sourceText: String,
        bridge: LibXrayBridge
    ) throws -> [String: Any] {
        if let data = sourceText.data(using: .utf8),
           let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           object["outbounds"] is [Any] {
            return object
        }
        return try bridge.convertShareText(sourceText)
    }

    private static var tunnelInbound: [String: Any] {
        [
            "tag": "tun-in",
            "protocol": "tun",
            "settings": [
                "name": "tun0",
                "MTU": 1400,
                "userLevel": 0
            ],
            "sniffing": [
                "enabled": true,
                "routeOnly": false,
                "destOverride": ["http", "tls", "quic"]
            ]
        ]
    }

    private static func appendUtilityOutbounds(to outbounds: [[String: Any]]) -> [[String: Any]] {
        var result = outbounds
        let tags = Set(result.compactMap { $0["tag"] as? String })
        if !tags.contains("direct") {
            result.append(["tag": "direct", "protocol": "freedom", "settings": [:]])
        }
        if !tags.contains("block") {
            result.append(["tag": "block", "protocol": "blackhole", "settings": [:]])
        }
        return result
    }

    private static func normalizedRouting(_ value: Any?) -> [String: Any] {
        var routing = value as? [String: Any] ?? [:]
        if routing["domainStrategy"] == nil { routing["domainStrategy"] = "IPIfNonMatch" }
        if routing["rules"] == nil { routing["rules"] = [] }
        return routing
    }

    private static func normalizedLog(_ value: Any?) -> [String: Any] {
        var log = value as? [String: Any] ?? [:]
        // Never enable access logs in the extension: URLs and destination IPs
        // must not end up in a re-signable diagnostic bundle.
        log.removeValue(forKey: "access")
        log.removeValue(forKey: "error")
        log["loglevel"] = "warning"
        return log
    }
}

enum XrayConfigurationError: LocalizedError {
    case empty
    case tooLarge
    case invalidEncoding
    case noOutbounds

    var errorDescription: String? {
        switch self {
        case .empty:
            "Конфигурация VPN пуста (IOS_CONFIG_EMPTY)."
        case .tooLarge:
            "Подписка слишком велика для безопасной передачи ядру (IOS_CONFIG_TOO_LARGE)."
        case .invalidEncoding:
            "Подписка должна быть текстом UTF-8 (IOS_CONFIG_ENCODING)."
        case .noOutbounds:
            "В подписке не найдено поддерживаемых серверов (IOS_CONFIG_NO_OUTBOUNDS)."
        }
    }
}
