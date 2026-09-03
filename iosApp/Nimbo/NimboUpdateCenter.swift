import Foundation
import UserNotifications

/// Обновления на iOS.
///
/// Поставить сборку сама система не даст: `.ipa` ставится тем же внешним
/// инструментом, каким установлено это приложение. Всё остальное приложение
/// умеет само — узнать о новой сборке, сообщить о ней уведомлением и положить
/// файл в «Файлы», откуда его подхватит инструмент подписи.
enum NimboUpdateCenter {
    private static let prefix = "com.nimbo.update."
    private static let announcedKey = prefix + "announced"
    private static let checkedAtKey = prefix + "checked-at"

    /// Не чаще раза в шесть часов: релизы выходят не поминутно, а лишний
    /// запрос к GitHub на мобильной сети — лишний расход.
    private static let automaticInterval: TimeInterval = 6 * 3600

    static var channel: NimboUpdateChannel {
        NimboUpdateChannel(stored: UserDefaults.standard.string(forKey: prefix + "channel"))
    }

    static var notifies: Bool {
        let defaults = UserDefaults.standard
        guard defaults.object(forKey: prefix + "notify") != nil else { return true }
        return defaults.bool(forKey: prefix + "notify")
    }

    /// Пора ли проверять самому. Ручная проверка этот срок не спрашивает.
    static var automaticCheckIsDue: Bool {
        let last = UserDefaults.standard.double(forKey: checkedAtKey)
        return Date().timeIntervalSince1970 - last >= automaticInterval
    }

    static func rememberCheck() {
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: checkedAtKey)
    }

    /// Уведомление о новой сборке — один раз на версию.
    ///
    /// Иначе о той же самой сборке напоминало бы при каждом запуске, а
    /// напоминание, которое нельзя прекратить, читается как навязчивость.
    static func announce(_ release: NimboRelease) async {
        guard notifies else { return }
        let defaults = UserDefaults.standard
        guard defaults.string(forKey: announcedKey) != release.version else { return }

        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        guard granted else { return }

        let content = UNMutableNotificationContent()
        content.title = "Nimbo \(release.version)"
        content.body = release.isPrerelease
            ? "Вышла бета новее вашей сборки"
            : "Вышла новая версия"
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: "com.nimbo.update.\(release.version)",
            content: content,
            trigger: nil
        )
        try? await center.add(request)
        defaults.set(release.version, forKey: announcedKey)
    }

    /// Файл сборки в папке приложения.
    ///
    /// Папка видна в «Файлах» (`UIFileSharingEnabled`), поэтому скачанное
    /// никуда не пропадает и его не нужно ловить окном обмена.
    static func downloadedFile(named name: String) -> URL? {
        let url = directory()?.appendingPathComponent(name)
        guard let url, FileManager.default.fileExists(atPath: url.path) else { return nil }
        return url
    }

    /// Загрузка файла сборки. Возвращает путь, по которому он лежит.
    static func download(_ release: NimboRelease) async throws -> URL {
        guard let asset = release.assetUrl, let source = URL(string: asset) else {
            throw NimboUpdateError.noAsset
        }
        let name = release.assetName ?? source.lastPathComponent
        guard let directory = directory() else { throw NimboUpdateError.storageUnavailable }
        let destination = directory.appendingPathComponent(name)

        var request = URLRequest(url: source)
        // Загрузка в 30 МиБ на мобильной сети идёт минутами: короткий предел
        // здесь означал бы, что докачать её нельзя в принципе.
        request.timeoutInterval = 600
        let (temporary, response) = try await URLSession.shared.download(for: request)
        guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
            throw NimboUpdateError.http((response as? HTTPURLResponse)?.statusCode ?? -1)
        }

        if FileManager.default.fileExists(atPath: destination.path) {
            try? FileManager.default.removeItem(at: destination)
        }
        try FileManager.default.moveItem(at: temporary, to: destination)
        return destination
    }

    private static func directory() -> URL? {
        guard let documents = FileManager.default.urls(
            for: .documentDirectory,
            in: .userDomainMask
        ).first else { return nil }
        let updates = documents.appendingPathComponent("Обновления", isDirectory: true)
        if !FileManager.default.fileExists(atPath: updates.path) {
            try? FileManager.default.createDirectory(at: updates, withIntermediateDirectories: true)
        }
        return updates
    }
}

enum NimboUpdateError: LocalizedError {
    case noAsset
    case storageUnavailable
    case http(Int)

    var errorDescription: String? {
        switch self {
        case .noAsset:
            "У этой сборки нет файла для iPhone"
        case .storageUnavailable:
            "Некуда сохранить файл"
        case let .http(code):
            "GitHub ответил \(code)"
        }
    }
}
