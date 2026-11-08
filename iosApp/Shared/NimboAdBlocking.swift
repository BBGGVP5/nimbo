import Foundation

/// Runtime-only, bounded suffix policy; never rewrites the stored subscription.
enum NimboAdBlocking {
    /// Older inspection replies may omit mode; native start remains authoritative.
    static func requireMihomoRuleMode(_ mode: String?, enabled: Bool) throws {
        guard enabled, let mode else { return }
        guard mode.lowercased() == "rule" else { throw NimboAdBlockingError.requiresMihomoRuleMode }
    }
    static let suffixDomains = [
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googleads.g.doubleclick.net",
        "adservice.google.com", "ads.yahoo.com", "advertising.com", "adsrvr.org", "adnxs.com", "adform.net",
        "adroll.com", "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "scorecardresearch.com",
        "quantserve.com", "ads.facebook.com", "app-measurement.com", "amazon-adsystem.com"
    ]

    static func applying(to original: [String: Any], enabled: Bool) -> [String: Any] {
        guard enabled else { return original }
        var configuration = original
        var outbounds = configuration["outbounds"] as? [[String: Any]] ?? []
        // A template may already use "block" for something other than blackhole.
        let tags = Set(outbounds.compactMap { $0["tag"] as? String })
        var tag = "nimbo-ad-block"
        while tags.contains(tag) { tag += "-local" }
        outbounds.append(["tag": tag, "protocol": "blackhole", "settings": [:]])
        configuration["outbounds"] = outbounds
        var routing = configuration["routing"] as? [String: Any] ?? [:]
        let rule: [String: Any] = ["type": "field", "domain": suffixDomains.map { "domain:" + $0 },
            "inboundTag": ["tun-in"], "outboundTag": tag]
        // Keep private diagnostic inbounds pinned to their verified route.
        routing["rules"] = [rule] + (routing["rules"] as? [[String: Any]] ?? [])
        configuration["routing"] = routing
        return configuration
    }
}

enum NimboAdBlockingError: LocalizedError {
    case requiresMihomoRuleMode
    var errorDescription: String? {
        "Для блокировки рекламы выберите режим правил (rule) в конфигурации Mihomo (AD_BLOCKING_REQUIRES_RULE_MODE)."
    }
}
