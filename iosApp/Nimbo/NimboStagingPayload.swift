import Foundation

/// Готовит данные, которые уходят в Packet Tunnel для выбранного сервера.
///
/// Записи вроде «✨ Автобалансировщик» — не настоящий узел: панель подставляет
/// в них реальные серверы через `remnawave.injectHosts`, которого стандартный
/// libXray не понимает. Подключение по такой ссылке поднимается, но трафику
/// идти некуда. Поэтому для них расширение получает список реальных серверов
/// профиля и собирает балансировщик само — так же, как это делает Android.
enum NimboStagingPayload {
    static func make(
        for server: NimboSubscriptionServer,
        in profile: NimboSubscriptionProfile?
    ) -> Data {
        let plain = Data(server.rawConfiguration.utf8)
        guard isAutoBalancer(server), let profile else { return plain }

        let pool = profile.servers.filter { candidate in
            !isAutoBalancer(candidate) &&
                !candidate.isNativeXrayJson &&
                !candidate.host.isEmpty &&
                candidate.rawConfiguration.contains("://")
        }
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

    /// Те же признаки, что и в `ServerPolicyManager.isAutoBalancerServer`
    /// на Android: панели называют такие записи по-разному.
    static func isAutoBalancer(_ server: NimboSubscriptionServer) -> Bool {
        let normalized = server.name.lowercased()
        guard !normalized.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        let compact = normalized.replacingOccurrences(
            of: "[\s_-]",
            with: "",
            options: .regularExpression
        )
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
