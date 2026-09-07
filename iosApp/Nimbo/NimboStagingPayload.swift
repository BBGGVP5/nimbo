import Foundation

/// Готовит данные, которые уходят в Packet Tunnel для выбранного сервера.
///
/// Записи вроде «✨ Автобалансировщик» — не настоящий узел: панель подставляет
/// в них реальные серверы через `remnawave.injectHosts`, которого стандартный
/// libXray не понимает. Подключение по такой ссылке поднимается, но трафику
/// идти некуда. Поэтому для них расширение получает список реальных серверов
/// профиля и собирает балансировщик само — так же, как это делает Android.
enum NimboStagingPayload {
    /// Сколько узлов уходит в балансировщик.
    private static let maximumBalancerNodes = 16

    static func make(
        for server: NimboSubscriptionServer,
        in profile: NimboSubscriptionProfile?
    ) -> Data {
        let plain = Data(server.rawConfiguration.utf8)
        guard isAutoBalancer(server), let profile else { return plain }

        let candidates = profile.servers.filter { candidate in
            !isAutoBalancer(candidate) &&
                !candidate.isNativeXrayJson &&
                !candidate.host.isEmpty &&
                candidate.rawConfiguration.contains("://")
        }
        // Балансировщику ни к чему вся подписка: каждый узел в нём — это
        // отдельный выход в ядре и отдельная проверка по кругу. На сотне
        // узлов расширение упирается в память, а круг проверок растягивается
        // настолько, что отвалившийся узел замечается через минуты молчания.
        //
        // Отбираются узлы с лучшим замером: брать первые по списку значит
        // сложить балансировщик из тех, кто просто оказался выше в подписке.
        let pool = Array(sortedByLatency(candidates).prefix(maximumBalancerNodes))
        // Балансировать нечего — честнее подключиться по исходной ссылке,
        // чем подсунуть ядру конфигурацию с единственным выходом.
        guard pool.count >= 2 else { return plain }

        let payload: [String: Any] = [
            "nimbo": ["balancer": true],
            "shareLinks": pool.map(\.rawConfiguration)
        ]
        guard let data = try? JSONSerialization.data(
            withJSONObject: payload,
            options: [.sortedKeys]
        ) else {
            return plain
        }
        return data
    }

    /// Узлы по возрастанию задержки; неизмеренные идут следом за измеренными,
    /// а молчащие — в конец: замер мог не состояться, но узел живой.
    private static func sortedByLatency(_ servers: [NimboSubscriptionServer]) -> [NimboSubscriptionServer] {
        let stored = UserDefaults.standard.string(forKey: "com.nimbo.ping.results")
            .flatMap { $0.data(using: .utf8) }
            .flatMap { try? JSONDecoder().decode([String: Int].self, from: $0) }
            ?? [:]
        guard !stored.isEmpty else { return servers }

        return servers.enumerated().sorted { left, right in
            let leftRank = rank(stored[left.element.id])
            let rightRank = rank(stored[right.element.id])
            if leftRank != rightRank { return leftRank < rightRank }
            return left.offset < right.offset
        }.map(\.element)
    }

    /// Чем меньше число, тем раньше узел попадёт в балансировщик.
    private static func rank(_ latency: Int?) -> Int {
        guard let latency else { return 100_000 }
        return latency > 0 ? latency : 200_000
    }

    /// Те же признаки, что и в `ServerPolicyManager.isAutoBalancerServer`
    /// на Android: панели называют такие записи по-разному.
    static func isAutoBalancer(_ server: NimboSubscriptionServer) -> Bool {
        let normalized = server.name.lowercased()
        guard !normalized.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        let compact = normalized.filter { !$0.isWhitespace && $0 != "_" && $0 != "-" }
        return normalized.contains("balancer") ||
            normalized.contains("балансер") ||
            normalized.contains("балансиров") ||
            compact.contains("loadbalance") ||
            compact.contains("lastping") ||
            compact.contains("leastping") ||
            compact.contains("leastload") ||
            compact.contains("leastloaded")
    }
}
