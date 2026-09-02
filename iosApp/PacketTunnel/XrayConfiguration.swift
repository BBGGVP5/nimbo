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
        configuration["policy"] = memoryPolicy(configuration["policy"])
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
        let sanitized = sanitizedOutbounds(outbounds, balanced: source.balanced)
        configuration["outbounds"] = appendUtilityOutbounds(to: sanitized)
        // Тег первого выхода приходит из подписки и обычно равен имени сервера,
        // поэтому правила, написанные про «proxy», без подстановки указывали бы
        // на несуществующий выход.
        configuration["routing"] = normalizedRouting(
            configuration["routing"],
            balanced: source.balanced,
            proxyTag: (sanitized.first?["tag"] as? String) ?? "proxy"
        )
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
            // Полторы минуты — предел, после которого отвалившийся узел
            // становится заметен человеку: страницы просто перестают
            // открываться. Память при этом бережёт не редкость проверок, а
            // их количество: узлов в балансировщике теперь немного.
            "probeInterval": "90s",
            // Проверка идёт разом: по очереди круг на десятке узлов
            // растягивался на минуты, и всё это время выбор оставался
            // вчерашним. Всплеск памяти сдерживается размером списка.
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

    private static func normalizedRouting(
        _ value: Any?,
        balanced: Bool,
        proxyTag: String
    ) -> [String: Any] {
        var routing = value as? [String: Any] ?? [:]
        let profile = routingProfile()
        // Стратегия доменов относится ко всей маршрутизации, поэтому её задаёт
        // профиль, а не отдельное правило.
        routing["domainStrategy"] = profile.strategy
            ?? (routing["domainStrategy"] as? String)
            ?? "IPIfNonMatch"
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
        // профиль, ни балансировщик не должны перебивать явный маршрут. Затем
        // идёт профиль — он решает, что вообще уходит в туннель.
        routing["rules"] = retargeted(moduleRules(), balanced: balanced, proxyTag: proxyTag)
            + retargeted(profile.rules, balanced: balanced, proxyTag: proxyTag)
            + rules
        return routing
    }

    /// Правила профиля маршрутизации, полученные из конфигурации туннеля.
    ///
    /// Их собирает общий модуль на Kotlin: набор правил обязан совпадать с
    /// андроидным, иначе один и тот же профиль вёл бы себя по-разному.
    static var routingProfileJSON: String = ""

    /// Наборы `geoip:`/`geosite:`, которые ядро загрузит при старте.
    ///
    /// Каждый набор разворачивается в памяти расширения, а её здесь около
    /// 50 МБ: по журналу должно быть видно, что именно загружалось.
    static func routingGeoCodes() -> [String] {
        let rules = routingProfile().rules + moduleRules()
        let values = rules.flatMap { rule -> [String] in
            let domains = rule["domain"] as? [String] ?? []
            let ips = rule["ip"] as? [String] ?? []
            return domains + ips
        }
        return Array(Set(values.filter {
            $0.hasPrefix("geosite:") || $0.hasPrefix("geoip:")
        })).sorted()
    }

    private static func routingProfile() -> (strategy: String?, rules: [[String: Any]]) {
        guard let data = routingProfileJSON.data(using: .utf8),
              let parsed = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return (nil, [])
        }
        return (
            parsed["domainStrategy"] as? String,
            parsed["rules"] as? [[String: Any]] ?? []
        )
    }

    /// Подстановка настоящего выхода вместо условного «proxy».
    ///
    /// В обычном режиме тег берётся у первого выхода подписки, в режиме
    /// балансировщика правило вместо выхода указывает на сам балансировщик —
    /// иначе выбор быстрейшего узла обходился бы стороной.
    private static func retargeted(
        _ rules: [[String: Any]],
        balanced: Bool,
        proxyTag: String
    ) -> [[String: Any]] {
        rules.map { rule in
            guard (rule["outboundTag"] as? String) == "proxy" else { return rule }
            var result = rule
            if balanced {
                result.removeValue(forKey: "outboundTag")
                result["balancerTag"] = balancerTag
            } else {
                result["outboundTag"] = proxyTag
            }
            return result
        }
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

    /// Буферы и таймауты под предел памяти расширения.
    ///
    /// Каждое соединение держит внутренний буфер, и при десятках соединений
    /// умолчания ядра съедают больше, чем система вообще даёт расширению.
    /// Простаивающие соединения закрываются быстрее по той же причине: пока
    /// соединение живо, его буфер занят.
    private static func memoryPolicy(_ value: Any?) -> [String: Any] {
        var policy = value as? [String: Any] ?? [:]
        var levels = policy["levels"] as? [String: Any] ?? [:]
        var zero = levels["0"] as? [String: Any] ?? [:]
        zero["handshake"] = 4
        zero["connIdle"] = 120
        zero["uplinkOnly"] = 1
        zero["downlinkOnly"] = 1
        // Размер в килобайтах на соединение.
        zero["bufferSize"] = 4
        levels["0"] = zero
        policy["levels"] = levels

        // Счётчики трафика ядра здесь не нужны: скорость считается по
        // счётчикам самого интерфейса, а статистика ядра держит записи по
        // каждому пользователю и исходящему.
        policy["system"] = [
            "statsInboundUplink": false,
            "statsInboundDownlink": false,
            "statsOutboundUplink": false,
            "statsOutboundDownlink": false
        ]
        return policy
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
