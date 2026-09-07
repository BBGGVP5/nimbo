import Foundation

/// Импорт подписки из ссылки, буфера, файла или QR.
///
/// Раньше это умел только экран профилей, и его логика была заперта внутри
/// вида. Общий интерфейс добавления подписки живёт в Compose и вызывает те же
/// действия, поэтому разбор источника вынесен отдельно.
enum NimboSubscriptionImporter {

    /// Больше пятнадцати мегабайт подписки не бывает: всё, что крупнее, —
    /// либо ошибка, либо попытка занять память.
    private static let maximumBytes = 15 * 1_024 * 1_024

    enum ImportError: LocalizedError {
        case http(Int)
        case invalidSize
        case emptySource

        var errorDescription: String? {
            switch self {
            case let .http(code): "Сервис подписки вернул HTTP \(code) (IOS_SUBSCRIPTION_HTTP)."
            case .invalidSize: "Ответ подписки пуст или слишком велик (IOS_SUBSCRIPTION_SIZE)."
            case .emptySource: "Пустая ссылка или конфигурация (IOS_SUBSCRIPTION_EMPTY)."
            }
        }
    }

    /// Скачивает подписку по ссылке либо принимает готовую конфигурацию как
    /// есть: `vless://…` и JSON приходят текстом и качать их неоткуда.
    static func resolve(_ rawSource: String) async throws -> (data: Data, source: String?) {
        let source = rawSource.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !source.isEmpty else { throw ImportError.emptySource }

        if let url = URL(string: source), ["http", "https"].contains(url.scheme?.lowercased() ?? "") {
            let request = NimboNetworkSession.subscriptionRequest(url: url)
            let (data, response) = try await NimboNetworkSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
                throw ImportError.http((response as? HTTPURLResponse)?.statusCode ?? -1)
            }
            guard !data.isEmpty, data.count <= maximumBytes else { throw ImportError.invalidSize }
            return (data, source)
        }

        guard let data = source.data(using: .utf8), data.count <= maximumBytes else {
            throw ImportError.invalidSize
        }
        return (data, nil)
    }

    /// Импорт не требует прав системного VPN. VpnController передаёт
    /// сохранённую конфигурацию расширению непосредственно перед запуском.
    @discardableResult
    static func importProfile(_ source: String) async throws -> NimboSubscriptionProfile {
        let trimmed = source.trimmingCharacters(in: .whitespacesAndNewlines)
        let profile: NimboSubscriptionProfile
        if let url = URL(string: trimmed), ["http", "https"].contains(url.scheme?.lowercased() ?? "") {
            profile = try await NimboSubscriptionRepository.shared.importRemote(trimmed)
        } else {
            let resolved = try await resolve(trimmed)
            profile = try NimboSubscriptionRepository.shared.importPayload(
                resolved.data, source: resolved.source
            )
            NimboSubscriptionMetaStore.save(.empty)
        }
        await NimboDiagnostics.shared.record(
            .info,
            stage: .config,
            code: "IOS_SUBSCRIPTION_IMPORTED",
            message: "Подписка импортирована",
            metadata: ["servers": "\(profile.servers.count)"]
        )
        return profile
    }
}
