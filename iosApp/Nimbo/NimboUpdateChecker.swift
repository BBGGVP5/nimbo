import Foundation

/// Проверка обновлений.
///
/// Устанавливать обновление само приложение на iOS не может: сборка ставится
/// только переподписанной, через ваш инструмент. Поэтому проверка честно
/// заканчивается ссылкой на релиз — там лежит IPA, который вы подписываете и
/// ставите как обычно. Никакой «загрузки внутри приложения» тут быть не может.
struct NimboRelease: Equatable {
    let version: String
    let title: String
    let notes: String
    let pageUrl: String
    let assetUrl: String?
    let isPrerelease: Bool
    /// Имя файла сборки: под ним же он сохраняется в «Файлы».
    let assetName: String?
}

/// Какие сборки предлагать.
enum NimboUpdateChannel: String {
    case beta
    case stable

    init(stored: String?) {
        self = NimboUpdateChannel(rawValue: stored ?? "") ?? .beta
    }

    var title: String { self == .stable ? "стабильный" : "бета" }
}

/// Чем закончилась проверка. Отдельные случаи нужны подписи под кнопкой:
/// «обновлений нет» и «не дозвонились до GitHub» — разные вещи.
enum NimboUpdateCheckResult {
    case available(NimboRelease)
    case upToDate
    case failed
}

enum NimboUpdateChecker {
    private static let releasesUrl = URL(string: "https://api.github.com/repos/BBGGVP5/nimbo/releases?per_page=15")!
    /// Куда вести, когда конкретной сборки ещё не нашли.
    static let releasesPageUrl = "https://github.com/BBGGVP5/nimbo/releases"

    /// Проверка канала: есть ли сборка новее установленной.
    static func check(
        currentVersion: String,
        channel: NimboUpdateChannel
    ) async -> NimboUpdateCheckResult {
        guard let releases = await fetchReleases() else { return .failed }
        let candidates = releases.filter { channel == .beta || !$0.isPrerelease }
        guard let newest = candidates.first else { return .upToDate }
        return isNewer(newest.version, than: currentVersion) ? .available(newest) : .upToDate
    }

    /// Последний релиз, который новее установленной сборки. `nil` — обновлений
    /// нет либо сеть недоступна: молчание здесь лучше ложной тревоги.
    static func latest(
        currentVersion: String,
        channel: NimboUpdateChannel = .beta
    ) async -> NimboRelease? {
        if case let .available(release) = await check(currentVersion: currentVersion, channel: channel) {
            return release
        }
        return nil
    }

    /// Релизы репозитория по порядку публикации, черновики отброшены.
    private static func fetchReleases() async -> [NimboRelease]? {
        var request = URLRequest(url: releasesUrl)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 12

        guard let (data, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse,
              (200 ... 299).contains(http.statusCode),
              let items = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }

        return items.compactMap { item in
            guard let tag = item["tag_name"] as? String,
                  let page = item["html_url"] as? String,
                  (item["draft"] as? Bool) != true else {
                return nil
            }
            let assets = item["assets"] as? [[String: Any]] ?? []
            let ipa = assets.first { ($0["name"] as? String)?.hasSuffix(".ipa") == true }
            return NimboRelease(
                version: tag.hasPrefix("v") ? String(tag.dropFirst()) : tag,
                title: (item["name"] as? String)?.trimmingCharacters(in: .whitespaces) ?? tag,
                notes: (item["body"] as? String) ?? "",
                pageUrl: page,
                assetUrl: ipa?["browser_download_url"] as? String,
                isPrerelease: (item["prerelease"] as? Bool) ?? false,
                assetName: ipa?["name"] as? String
            )
        }
    }

    /// Сравнение вида «1.2.0-beta.3» > «1.2.0-beta.2»: сначала числа версии,
    /// затем предрелизный хвост. Релиз всегда старше своей же беты.
    static func isNewer(_ candidate: String, than current: String) -> Bool {
        let left = parse(candidate)
        let right = parse(current)
        for index in 0 ..< max(left.numbers.count, right.numbers.count) {
            let a = index < left.numbers.count ? left.numbers[index] : 0
            let b = index < right.numbers.count ? right.numbers[index] : 0
            if a != b { return a > b }
        }
        // Числа равны: сборка без хвоста новее любой беты с теми же числами.
        if left.preRelease.isEmpty != right.preRelease.isEmpty {
            return left.preRelease.isEmpty
        }
        return left.preRelease.compare(right.preRelease, options: .numeric) == .orderedDescending
    }

    private static func parse(_ version: String) -> (numbers: [Int], preRelease: String) {
        let trimmed = version.trimmingCharacters(in: .whitespaces)
        let parts = trimmed.split(separator: "-", maxSplits: 1, omittingEmptySubsequences: false)
        let numbers = parts[0]
            .split(separator: ".")
            .map { Int($0.filter(\.isNumber)) ?? 0 }
        let preRelease = parts.count > 1 ? String(parts[1]) : ""
        return (numbers, preRelease)
    }
}
