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
}

enum NimboUpdateChecker {
    private static let releasesUrl = URL(string: "https://api.github.com/repos/BBGGVP5/nimbo/releases?per_page=10")!

    /// Последний релиз, который новее установленной сборки. `nil` — обновлений
    /// нет либо сеть недоступна: молчание здесь лучше ложной тревоги.
    static func latest(currentVersion: String) async -> NimboRelease? {
        var request = URLRequest(url: releasesUrl)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 12

        guard let (data, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse,
              (200 ... 299).contains(http.statusCode),
              let items = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }

        for item in items {
            guard let tag = item["tag_name"] as? String,
                  let page = item["html_url"] as? String,
                  (item["draft"] as? Bool) != true else {
                continue
            }
            let version = tag.hasPrefix("v") ? String(tag.dropFirst()) : tag
            guard isNewer(version, than: currentVersion) else { continue }

            let assets = item["assets"] as? [[String: Any]] ?? []
            let ipa = assets.first { ($0["name"] as? String)?.hasSuffix(".ipa") == true }
            return NimboRelease(
                version: version,
                title: (item["name"] as? String)?.trimmingCharacters(in: .whitespaces) ?? tag,
                notes: (item["body"] as? String) ?? "",
                pageUrl: page,
                assetUrl: ipa?["browser_download_url"] as? String,
                isPrerelease: (item["prerelease"] as? Bool) ?? false
            )
        }
        return nil
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
