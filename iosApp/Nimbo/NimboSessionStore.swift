import Foundation

/// Завершённые сессии подключения.
///
/// Считает их приложение по счётчикам utun-интерфейса: ядро внутри расширения
/// своей статистики наружу не отдаёт. Храним последние два десятка — этого
/// хватает экрану статистики, а места занимает мало.
struct NimboSession: Codable, Equatable {
    let startedAt: Date
    let endedAt: Date
    let download: Int64
    let upload: Int64

    var duration: TimeInterval { endedAt.timeIntervalSince(startedAt) }

    var startedAtLabel: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.dateFormat = Calendar.current.isDateInToday(startedAt)
            ? "Сегодня, HH:mm"
            : "d MMM, HH:mm"
        return formatter.string(from: startedAt)
    }

    var durationLabel: String {
        let total = Int(duration.rounded())
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let seconds = total % 60
        if hours > 0 { return "\(hours) ч \(minutes) мин" }
        if minutes > 0 { return "\(minutes) мин \(seconds) с" }
        return "\(seconds) с"
    }
}

enum NimboSessionStore {
    private static let key = "com.nimbo.sessions"
    private static let limit = 20

    static var all: [NimboSession] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let value = try? JSONDecoder().decode([NimboSession].self, from: data) else {
            return []
        }
        return value
    }

    /// Совсем короткие подключения (меньше пяти секунд) не записываем: это
    /// обрывы и переподключения, в списке от них только шум.
    static func append(_ session: NimboSession) {
        guard session.duration >= 5, session.download + session.upload > 0 else { return }
        var sessions = all
        sessions.insert(session, at: 0)
        if sessions.count > limit { sessions.removeLast(sessions.count - limit) }
        guard let data = try? JSONEncoder().encode(sessions) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }
}
