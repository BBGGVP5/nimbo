import Darwin
import Foundation

struct PreparedXrayConfiguration {
    let json: String
    let outboundCount: Int
}

enum XrayConfigurationBuilder {
    private static let maximumInputBytes = 15 * 1_024 * 1_024
    private static let proxyTagPrefix = "proxy/"
    private static let balancerTag = "balancer"

    static func prepare(
        sourceData: Data,
        tunnelFileDescriptor: Int32,
        tunnelInterfaceName: String,
        assetDirectory: String,
        options: NimboRoutingOptions = .default,
        bridge: LibXrayBridge
    ) throws -> PreparedXrayConfiguration {
        guard !tunnelInterfaceName.isEmpty else { throw XrayConfigurationError.tunnelInterfaceUnknown }
        guard !sourceData.isEmpty else { throw XrayConfigurationError.empty }
        guard sourceData.count <= maximumInputBytes else { throw XrayConfigurationError.tooLarge }
        guard let sourceText = String(data: sourceData, encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines),
              !sourceText.isEmpty else {
            throw XrayConfigurationError.invalidEncoding
        }

        let source = try configurationObject(from: sourceText, bridge: bridge)
        var configuration = (withoutNulls(source.configuration) as? [String: Any]) ?? source.configuration
        let outbounds = configuration["outbounds"] as? [[String: Any]] ?? []
        guard !outbounds.isEmpty else { throw XrayConfigurationError.noOutbounds }

        configuration["log"] = normalizedLog(configuration["log"])
        // TUN-дескриптор и каталог гео-данных ядро читает из корневого объекта
        // "env" конфигурации — ровно так их передаёт рабочая сборка Android.
        // Одних переменных окружения процесса недостаточно.
        configuration["env"] = runtimeEnvironment(
            existing: configuration["env"],
            tunnelFileDescriptor: tunnelFileDescriptor,
            assetDirectory: assetDirectory
        )
        configuration["inbounds"] = [
            tunnelInbound(interfaceName: tunnelInterfaceName, sniffing: options.sniffingEnabled)
        ]
        configuration["outbounds"] = appendUtilityOutbounds(
            to: sanitizedOutbounds(outbounds, balanced: source.balanced)
        )
        configuration["routing"] = normalizedRouting(configuration["routing"], balanced: source.balanced)
        if source.balanced {
            configuration["observatory"] = observatorySettings
        }

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
    ) throws -> (configuration: [String: Any], balanced: Bool) {
        var object: [String: Any]?
        if let data = sourceText.data(using: .utf8) {
            object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        }

        // Конверт приложения для автобалансировщика: реальные серверы профиля
        // приходят списком ссылок, потому что сама запись балансировщика
        // рабочего узла не содержит.
        if let links = object?["shareLinks"] as? [String] {
            let text = links
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
                .joined(separator: "\n")
            guard !text.isEmpty else { throw XrayConfigurationError.empty }
            let requested = (object?["nimbo"] as? [String: Any])?["balancer"] as? Bool ?? true
            return (try bridge.convertShareText(text), requested && links.count > 1)
        }

        if let object, object["outbounds"] is [Any] {
            return (object, false)
        }
        return (try bridge.convertShareText(sourceText), false)
    }

    /// leastPing выбирает выход по замерам обсерватории; до первого замера она
    /// пуста и стратегия возвращает пустой тег, поэтому у балансировщика есть
    /// fallbackTag — иначе первые соединения после подключения никуда не идут.
    private static var observatorySettings: [String: Any] {
        [
            "subjectSelector": [proxyTagPrefix],
            "probeURL": "https://www.gstatic.com/generate_204",
            "probeInterval": "1m",
            "enableConcurrency": true
        ]
    }

    /// `infra/conf/tun.go` разбирает настройки как `name`/`mtu` строчными
    /// буквами, а имя обязано быть настоящим `utunN`: на Darwin ядро без
    /// дескриптора пытается открыть интерфейс по имени и отвергает «tun0».
    private static func tunnelInbound(interfaceName: String, sniffing: Bool) -> [String: Any] {
        var inbound: [String: Any] = [
            "tag": "tun-in",
            "protocol": "tun",
            "settings": [
                "name": interfaceName,
                "mtu": PacketTunnelNetwork.mtu
            ]
        ]
        if sniffing {
            inbound["sniffing"] = [
                "enabled": true,
                "routeOnly": false,
                "destOverride": ["http", "tls", "quic"]
            ]
        }
        return inbound
    }

    /// libXray прячет имя сервера из #fragment ссылки в поле `sendThrough`
    /// (share/xray_json.go, setOutboundName) и использует его как переносчик
    /// названия. Xray-core же ждёт там локальный IP-адрес и отвергает всю
    /// конфигурацию: "unable to send through: <имя сервера>". Поэтому имя
    /// переносим в tag, а из sendThrough оставляем только настоящие адреса.
    /// libXray сериализует структуры Xray целиком, без `omitempty`, поэтому в
    /// готовом JSON оказываются "target": null и "dest": null. Xray-core же
    /// смотрит на наличие ключа, а json.RawMessage от null не пуст — из-за
    /// этого клиентский REALITY уходит в серверную ветку и требует
    /// serverNames. Пустые значения убираем целиком.
    private static func withoutNulls(_ value: Any) -> Any? {
        if value is NSNull { return nil }
        if let dictionary = value as? [String: Any] {
            var result: [String: Any] = [:]
            for (key, item) in dictionary {
                if let cleaned = withoutNulls(item) { result[key] = cleaned }
            }
            return result
        }
        if let array = value as? [Any] {
            return array.compactMap { withoutNulls($0) }
        }
        return value
    }

    private static func sanitizedOutbounds(
        _ outbounds: [[String: Any]],
        balanced: Bool
    ) -> [[String: Any]] {
        outbounds.enumerated().map { index, outbound in
            var result = outbound
            let carried = (result["sendThrough"] as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if let carried, !isIPAddress(carried) {
                result.removeValue(forKey: "sendThrough")
            }
            // Балансировщик выбирает выходы по префиксу тега, поэтому имена
            // из подписки здесь не годятся — нумеруем сами.
            if balanced {
                result["tag"] = "\(proxyTagPrefix)\(index)"
                return result
            }
            let tag = (result["tag"] as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if tag.isEmpty {
                result["tag"] = index == 0 ? "proxy" : "proxy-\(index + 1)"
            }
            return result
        }
    }

    private static func isIPAddress(_ value: String) -> Bool {
        var address4 = in_addr()
        var address6 = in6_addr()
        return value.withCString { pointer in
            inet_pton(AF_INET, pointer, &address4) == 1 ||
                inet_pton(AF_INET6, pointer, &address6) == 1
        }
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

    private static func runtimeEnvironment(
        existing: Any?,
        tunnelFileDescriptor: Int32,
        assetDirectory: String
    ) -> [String: Any] {
        var environment = existing as? [String: Any] ?? [:]
        environment["xray.tun.fd"] = String(tunnelFileDescriptor)
        environment["xray.location.asset"] = assetDirectory
        return environment
    }

    private static func normalizedRouting(_ value: Any?, balanced: Bool) -> [String: Any] {
        var routing = value as? [String: Any] ?? [:]
        if routing["domainStrategy"] == nil { routing["domainStrategy"] = "IPIfNonMatch" }
        var rules = routing["rules"] as? [[String: Any]] ?? []

        if balanced {
            routing["balancers"] = [[
                "tag": balancerTag,
                "selector": [proxyTagPrefix],
                "strategy": ["type": "leastPing"],
                "fallbackTag": "\(proxyTagPrefix)0"
            ]]
            // Балансировщик забирает весь остальной трафик, поэтому его
            // правило должно идти последним.
            rules = [[
                "type": "field",
                "network": "tcp,udp",
                "balancerTag": balancerTag
            ]]
        }

        // Модули впереди всего: человек написал их под конкретную задачу, и ни
        // профиль, ни балансировщик не должны перебивать явный маршрут.
        routing["rules"] = moduleRules() + rules
        return routing
    }

    /// Правила пользовательских модулей, полученные из конфигурации туннеля.
    ///
    /// Текст разбирает общий модуль на Kotlin — Android и iOS обязаны понимать
    /// один и тот же набор одинаково. Расширение получает готовый массив:
    /// линковать сюда Kotlin ради разбора строк незачем.
    static var moduleRulesJSON: String = ""

    private static func moduleRules() -> [[String: Any]] {
        guard let data = moduleRulesJSON.data(using: .utf8),
              let parsed = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }
        return parsed
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
    case tunnelInterfaceUnknown

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
        case .tunnelInterfaceUnknown:
            "Не удалось определить имя utun-интерфейса (IOS_TUN_INTERFACE_UNKNOWN)."
        }
    }
}
