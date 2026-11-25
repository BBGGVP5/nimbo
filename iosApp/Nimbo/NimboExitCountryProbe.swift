import Foundation

/// Реальная страна выхода трафика.
///
/// Страна в названии сервера может врать (обходы с подменным адресом,
/// автовыбор, балансировщик), поэтому после подключения приложение один раз
/// спрашивает Cloudflare, откуда пришёл запрос: `loc=XX` в ответе
/// `cdn-cgi/trace`. Запрос идёт из приложения, то есть через поднятый туннель,
/// только пока VPN подключён; результат хранится на сессию подключения.
/// Ошибки молча игнорируются — тогда карта остаётся при стране сервера.
enum NimboExitCountryProbe {
    private static let traceURL = URL(string: "https://www.cloudflare.com/cdn-cgi/trace")!
    private static let cache = NimboExitCountryCache()

    /// `sessionKey` различает подключения: сервер + время подключения.
    static func country(sessionKey: String) async -> String? {
        if let cached = await cache.value(for: sessionKey) { return cached }
        // Сеть — вне главного потока, даже если ждёт вид на MainActor.
        let code = await Task.detached(priority: .utility) { await fetch() }.value
        guard !Task.isCancelled, let code else { return nil }
        await cache.store(code, for: sessionKey)
        return code
    }

    private static func fetch() async -> String? {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 5
        configuration.timeoutIntervalForResource = 5
        configuration.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        configuration.waitsForConnectivity = false
        let session = URLSession(configuration: configuration)
        defer { session.finishTasksAndInvalidate() }
        var request = URLRequest(url: traceURL, cachePolicy: .reloadIgnoringLocalAndRemoteCacheData, timeoutInterval: 5)
        request.httpMethod = "GET"
        guard let (data, response) = try? await session.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let text = String(data: data, encoding: .utf8) else { return nil }
        return parse(text)
    }

    /// `loc=DE` → «DE». Служебные коды Cloudflare (XX — неизвестно, T1 — Tor) отбрасываются.
    static func parse(_ text: String) -> String? {
        for line in text.split(whereSeparator: \.isNewline) {
            guard line.hasPrefix("loc=") else { continue }
            let code = line.dropFirst(4).trimmingCharacters(in: .whitespaces).uppercased()
            guard code.count == 2, code != "XX", code != "T1",
                  code.unicodeScalars.allSatisfy({ (65...90).contains($0.value) }) else { return nil }
            return code
        }
        return nil
    }
}

private actor NimboExitCountryCache {
    private var values: [String: String] = [:]
    private var order: [String] = []

    func value(for key: String) -> String? { values[key] }

    func store(_ value: String, for key: String) {
        if values[key] == nil { order.append(key) }
        values[key] = value
        // Хватает нескольких последних сессий; старые не нужны.
        while order.count > 8 { values[order.removeFirst()] = nil }
    }
}
