import Foundation

/// Сведения о подписке, которые панель отдаёт заголовками ответа.
///
/// Разбор ссылок этого не даёт: там только серверы. Отсюда берутся имя
/// владельца подписки (обычно почта или ник), остаток трафика, срок действия и
/// адреса поддержки. Набор заголовков совпадает с тем, что читает Android в
/// `SubscriptionManager`.
struct NimboSubscriptionMeta: Codable, Equatable {
    var title: String?
    var supportUrl: String?
    var websiteUrl: String?
    var announce: String?
    var usedTraffic: Int64
    var totalTraffic: Int64
    var expireAt: TimeInterval
    var updatedAt: TimeInterval

    static let empty = NimboSubscriptionMeta(
        title: nil,
        supportUrl: nil,
        websiteUrl: nil,
        announce: nil,
        usedTraffic: 0,
        totalTraffic: 0,
        expireAt: 0,
        updatedAt: 0
    )

    init(
        title: String?,
        supportUrl: String?,
        websiteUrl: String?,
        announce: String?,
        usedTraffic: Int64,
        totalTraffic: Int64,
        expireAt: TimeInterval,
        updatedAt: TimeInterval
    ) {
        self.title = title
        self.supportUrl = supportUrl
        self.websiteUrl = websiteUrl
        self.announce = announce
        self.usedTraffic = usedTraffic
        self.totalTraffic = totalTraffic
        self.expireAt = expireAt
        self.updatedAt = updatedAt
    }

    init(headers: [AnyHashable: Any]) {
        func header(_ names: [String]) -> String? {
            for name in names {
                for (key, value) in headers where (key as? String)?.lowercased() == name.lowercased() {
                    if let text = value as? String, !text.trimmingCharacters(in: .whitespaces).isEmpty {
                        return text
                    }
                }
            }
            return nil
        }

        let rawTitle = header(["profile-title", "profile_title"])
        let decodedAnnounce = NimboSubscriptionMeta.decodePossiblyBase64(
            header(["announce", "subscription-description"])
        )
        // Панель присылает своё имя в profile-title («🛡 NebulaGuard»), а сам
        // аккаунт — внутри announce. Android читает именно оттуда, поэтому на
        // карточке и стоит почта, а не название сервиса.
        self.title = NimboSubscriptionMeta.accountName(
            announce: decodedAnnounce,
            title: NimboSubscriptionMeta.decodePossiblyBase64(rawTitle)
        )
        self.announce = decodedAnnounce
        self.supportUrl = header(["support-url", "x-support-url"])
        self.websiteUrl = header(["website-url", "x-website-url", "profile-web-page-url"])

        let info = NimboSubscriptionMeta.parseUserInfo(header(["subscription-userinfo", "profile_userinfo"]))
        self.usedTraffic = info.used
        self.totalTraffic = info.total
        self.expireAt = info.expire
        self.updatedAt = Date().timeIntervalSince1970
    }

    /// Панели присылают заголовки как обычным текстом, так и в base64.
    ///
    /// Переносы строк в объявлениях — норма, поэтому отбрасывать по ним нельзя:
    /// именно из-за этого на экране оставалась строка «base64:8J+boe…».
    /// Признак удачного разбора — отсутствие управляющих символов, кроме
    /// переносов и табуляции.
    private static func decodePossiblyBase64(_ value: String?) -> String? {
        guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
            return nil
        }
        let payload = value.lowercased().hasPrefix("base64:")
            ? String(value.dropFirst("base64:".count))
            : value
        let normalized = payload
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let padded = normalized.padding(
            toLength: normalized.count + (4 - normalized.count % 4) % 4,
            withPad: "=",
            startingAt: 0
        )
        if let data = Data(base64Encoded: padded, options: [.ignoreUnknownCharacters]),
           let decoded = String(data: data, encoding: .utf8) {
            let trimmed = decoded.trimmingCharacters(in: .whitespacesAndNewlines)
            let readable = trimmed.unicodeScalars.allSatisfy { scalar in
                !CharacterSet.controlCharacters.contains(scalar) || scalar == "\n" || scalar == "\t"
            }
            if !trimmed.isEmpty, readable {
                return trimmed
            }
        }
        return value
    }

    /// Владелец подписки без названия панели.
    ///
    /// Заголовок приходит видом «NebulaGuard · user_8f21», а на карточке нужен
    /// только сам аккаунт: название сервиса пользователь и так знает.
    static func accountName(announce: String?, title: String?) -> String? {
        if let fromAnnounce = accountFromAnnounce(announce) { return fromAnnounce }
        return accountName(from: title)
    }

    /// В announce аккаунт помечен человечком: «👤 user@example.com · …».
    private static func accountFromAnnounce(_ announce: String?) -> String? {
        guard let announce, !announce.isEmpty else { return nil }
        guard let marker = announce.range(of: "\u{1F464}") else { return nil }
        let tail = announce[marker.upperBound...]
        let stop = CharacterSet(charactersIn: "·|\n\r—–")
        let value = tail.unicodeScalars.prefix { !stop.contains($0) }
        let name = String(String.UnicodeScalarView(value))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty ? nil : name
    }

    static func accountName(from title: String?) -> String? {
        guard let title = title?.trimmingCharacters(in: .whitespacesAndNewlines), !title.isEmpty else {
            return nil
        }
        let separators = CharacterSet(charactersIn: "·|—–\u{00B7}")
        let parts = title
            .components(separatedBy: separators)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        guard parts.count > 1 else { return title }
        // Почта — самый однозначный признак аккаунта; иначе берём последнюю
        // часть: панели ставят имя сервиса первым.
        return parts.first(where: { $0.contains("@") }) ?? parts.last
    }

    /// Формат заголовка: `upload=1; download=2; total=3; expire=1700000000`.
    private static func parseUserInfo(_ value: String?) -> (used: Int64, total: Int64, expire: TimeInterval) {
        guard let value else { return (0, 0, 0) }
        var upload: Int64 = 0
        var download: Int64 = 0
        var total: Int64 = 0
        var expire: TimeInterval = 0
        for part in value.split(separator: ";") {
            let pair = part.split(separator: "=", maxSplits: 1)
            guard pair.count == 2 else { continue }
            let key = pair[0].trimmingCharacters(in: .whitespaces).lowercased()
            let raw = pair[1].trimmingCharacters(in: .whitespaces)
            switch key {
            case "upload": upload = Int64(raw) ?? 0
            case "download": download = Int64(raw) ?? 0
            case "total": total = Int64(raw) ?? 0
            case "expire": expire = TimeInterval(raw) ?? 0
            default: break
            }
        }
        return (upload &+ download, total, expire)
    }

    /// «12,4 ГБ / 200 ГБ» либо «12,4 ГБ / ∞», если лимита нет.
    var trafficLabel: String {
        guard usedTraffic > 0 || totalTraffic > 0 else { return "" }
        let used = NimboSubscriptionMeta.formatBytes(usedTraffic)
        return totalTraffic > 0 ? "\(used) / \(NimboSubscriptionMeta.formatBytes(totalTraffic))" : "\(used) / ∞"
    }

    var expiryLabel: String {
        guard expireAt > 0 else { return "" }
        let date = Date(timeIntervalSince1970: expireAt)
        let days = Int(date.timeIntervalSinceNow / 86_400)
        if days < 0 { return "Подписка истекла" }
        let formatter = DateFormatter()
        formatter.dateFormat = "d MMMM yyyy"
        formatter.locale = Locale(identifier: "ru_RU")
        if days <= 30 {
            return "Осталось \(days) дн. · до \(formatter.string(from: date))"
        }
        return "До \(formatter.string(from: date))"
    }

    var updatedLabel: String {
        guard updatedAt > 0 else { return "" }
        let formatter = DateFormatter()
        formatter.dateFormat = "d MMM, HH:mm"
        formatter.locale = Locale(identifier: "ru_RU")
        return formatter.string(from: Date(timeIntervalSince1970: updatedAt))
    }

    private static func formatBytes(_ bytes: Int64) -> String {
        if bytes <= 0 { return "0 Б" }
        let units = ["Б", "КБ", "МБ", "ГБ", "ТБ"]
        var value = Double(bytes)
        var unit = 0
        while value >= 1024, unit < units.count - 1 {
            value /= 1024
            unit += 1
        }
        return value >= 100 || unit == 0
            ? "\(Int(value)) \(units[unit])"
            : String(format: "%.1f %@", value, units[unit])
    }
}

/// Хранилище сведений о подписке: они переживают перезапуск, чтобы имя и
/// остаток трафика были видны сразу, а не только после обновления.
enum NimboSubscriptionMetaStore {
    private static let key = "com.nimbo.subscription.meta"

    static var current: NimboSubscriptionMeta {
        guard let data = UserDefaults.standard.data(forKey: key),
              let value = try? JSONDecoder().decode(NimboSubscriptionMeta.self, from: data) else {
            return .empty
        }
        return value
    }

    static func save(_ value: NimboSubscriptionMeta) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: key)
    }
}
