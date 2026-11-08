import Foundation

/// One TrustTunnel (AdGuard) deep link, `tt://?<base64url TLV>`. The TLV payload
/// is decoded and resolved by the Go bridge inside the Packet Tunnel; here we
/// only recognise and bound the input. Subscription-only links (format v2 with
/// just `subscription_url`) are imported as subscriptions by the app instead.
struct NimboTrustTunnelConfiguration {
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
        guard text.lowercased().hasPrefix("tt://") else { return nil }
        // Fragment labels are display-only and may contain unescaped spaces/emoji.
        let link = String(text.split(separator: "#", maxSplits: 1, omittingEmptySubsequences: false)[0])
        let payload = link.dropFirst(5).drop(while: { $0 == "?" })
        let alphabet = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_+/=")
        guard !payload.isEmpty, link.utf8.count <= 16 * 1024,
              payload.unicodeScalars.allSatisfy({ alphabet.contains($0) }) else {
            throw NimboTrustTunnelError.invalidConfiguration
        }
        return Self(rawText: link)
    }
}

enum NimboTrustTunnelError: LocalizedError {
    case invalidConfiguration, authRejected, certificateRejected, unreachable, runtimeFailure

    /// Maps a Go bridge error code (`nimbo/trusttunnelcore`).
    init(bridgeCode: String?) {
        switch bridgeCode {
        case "AUTH_REJECTED": self = .authRejected
        case "TLS_VERIFY": self = .certificateRejected
        case "UNREACHABLE", "TIMEOUT": self = .unreachable
        case "CONFIG", "REQUEST": self = .invalidConfiguration
        default: self = .runtimeFailure
        }
    }

    var errorDescription: String? {
        switch self {
        case .invalidConfiguration:
            return "Некорректная ссылка TrustTunnel. Проверьте ключ или обновите подписку (IOS_TRUSTTUNNEL_CONFIG)."
        case .authRejected:
            return "Сервер TrustTunnel отклонил логин или пароль (IOS_TRUSTTUNNEL_AUTH)."
        case .certificateRejected:
            return "Сертификат сервера TrustTunnel не прошёл проверку (IOS_TRUSTTUNNEL_TLS)."
        case .unreachable:
            return "Сервер TrustTunnel недоступен. Если в ключе включены anti-DPI или client_random, на iPhone они не поддерживаются (IOS_TRUSTTUNNEL_UNREACHABLE)."
        case .runtimeFailure:
            return "Не удалось запустить TrustTunnel. Повторите подключение или выберите другой сервер (IOS_TRUSTTUNNEL_RUNTIME)."
        }
    }
}
