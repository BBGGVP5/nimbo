import Foundation

/// One selected share link, never a flattened native Mihomo document.
struct NimboNaiveConfiguration {
    let rawText: String
    static func parseIfPresent(_ source: String) throws -> Self? {
        var text = source.trimmingCharacters(in: .whitespacesAndNewlines)
        if let data = text.data(using: .utf8),
           let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
            // A native full document stays authoritative, even with auxiliary links.
            guard ["coreId", "outbounds", "proxies", "proxy-groups", "proxy-providers", "rule-providers", "originalYAML"]
                    .allSatisfy({ object[$0] == nil }),
                  let links = object["shareLinks"] as? [String], links.count == 1 else { return nil }
            text = links[0].trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let scheme = text.components(separatedBy: "://").first?.lowercased() ?? ""
        guard ["naive", "naive+https", "naive+quic"].contains(scheme) else { return nil }
        guard text.rangeOfCharacter(from: .newlines) == nil else { throw NimboNaiveError.invalidConfiguration }
        // Fragment labels are display-only and may contain unescaped spaces/emoji.
        let transport = String(text.split(separator: "#", maxSplits: 1, omittingEmptySubsequences: false)[0])
        guard transport.utf8.count <= 16 * 1024,
              transport.rangeOfCharacter(from: .whitespacesAndNewlines) == nil,
              let url = URLComponents(string: transport),
              let host = url.host, !host.isEmpty,
              let user = url.user, !user.isEmpty,
              let password = url.password, !password.isEmpty,
              (url.path.isEmpty || url.path == "/"),
              (url.port == nil || (1...65535).contains(url.port!)),
              !transport.contains("\u{0}"),
              (user + password).rangeOfCharacter(from: .controlCharacters) == nil else {
            throw NimboNaiveError.invalidConfiguration
        }
        return Self(rawText: transport)
    }
}
enum NimboNaiveError: LocalizedError {
    case invalidConfiguration, runtimeFailure
    var errorDescription: String? {
        switch self {
        case .invalidConfiguration: return "Некорректный ключ NaiveProxy. Проверьте адрес, порт и учётные данные (IOS_NAIVE_CONFIG)."
        case .runtimeFailure: return "Не удалось запустить NaiveProxy. Повторите подключение или выберите другой сервер (IOS_NAIVE_RUNTIME)."
        }
    }
}

/// Preserve the existing Xray TUN/router; only the encrypted upstream is Naive.
/// The standard Naive protocol is TCP-only: carry DNS over TCP too, never add
/// a direct UDP fallback. Explicit user routing rules remain authoritative.
enum NimboNaiveRouting {
    static func applying(to original: [String: Any], dnsServer: String) -> [String: Any] {
        var config = original
        var outbounds = config["outbounds"] as? [[String: Any]] ?? []
        outbounds.append([
            "tag": "nimbo-naive-dns", "protocol": "dns",
            "settings": ["rewriteNetwork": "tcp", "rewriteAddress": dnsServer, "rewritePort": 53,
                         "rules": [["action": "direct"]]],
            "proxySettings": ["tag": "proxy"]
        ])
        config["outbounds"] = outbounds
        var routing = config["routing"] as? [String: Any] ?? [:]
        // This applies only to TUN DNS, never to the private ping inbound.
        routing["rules"] = [["type": "field", "inboundTag": ["nimbo-naive-dns-query"], "outboundTag": "proxy"],
                            ["type": "field", "inboundTag": ["tun-in"], "port": "53",
                              "network": "tcp,udp", "outboundTag": "nimbo-naive-dns"]]
            + (routing["rules"] as? [[String: Any]] ?? [])
        config["routing"] = routing
        // Domain-based routing must not ask the OS resolver for user destinations.
        config["dns"] = ["servers": ["tcp://" + dnsServer], "tag": "nimbo-naive-dns-query"]
        return config
    }
}
