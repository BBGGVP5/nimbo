import Foundation

/// Что говорит о себе подпись приложения и расширения.
///
/// Система запускает расширение туннеля и молча убивает его, если подпись её
/// не устраивает: нет права `packet-tunnel-provider`, профиль просрочен или
/// приложение и расширение подписаны по-разному. Снаружи это выглядит как
/// «внутренняя ошибка» через двадцать миллисекунд, и без Mac причину не
/// увидеть. Поэтому приложение читает профили сборки само.
enum NimboSigningReport {
    struct Profile {
        let name: String?
        let teamIdentifier: String?
        let applicationIdentifier: String?
        let allowsNetworkExtension: Bool
        let expirationDate: Date?

        var expired: Bool {
            guard let expirationDate else { return false }
            return expirationDate < Date()
        }
    }

    /// Профиль основного приложения.
    static var applicationProfile: Profile? {
        read(at: Bundle.main.bundleURL.appendingPathComponent("embedded.mobileprovision"))
    }

    /// Профиль расширения туннеля, если оно вложено в сборку.
    static var packetTunnelProfile: Profile? {
        guard let plugIns = Bundle.main.builtInPlugInsURL,
              let children = try? FileManager.default.contentsOfDirectory(
                  at: plugIns,
                  includingPropertiesForKeys: nil,
                  options: [.skipsHiddenFiles]
              ) else {
            return nil
        }
        for url in children where url.pathExtension == "appex" {
            guard let bundle = Bundle(url: url),
                  let info = bundle.object(forInfoDictionaryKey: "NSExtension") as? [String: Any],
                  info["NSExtensionPointIdentifier"] as? String == "com.apple.networkextension.packet-tunnel" else {
                continue
            }
            return read(at: url.appendingPathComponent("embedded.mobileprovision"))
        }
        return nil
    }

    /// Короткая сводка для журнала: по ней сразу видно, чего не хватает.
    static var summary: [String: String] {
        let app = applicationProfile
        let tunnel = packetTunnelProfile
        var values: [String: String] = [
            "app_profile": app == nil ? "отсутствует" : "есть",
            "tunnel_profile": tunnel == nil ? "отсутствует" : "есть"
        ]
        if let app {
            values["app_network_extension"] = app.allowsNetworkExtension ? "да" : "нет"
            values["app_team"] = app.teamIdentifier ?? "неизвестно"
            values["app_profile_expired"] = app.expired ? "да" : "нет"
        }
        if let tunnel {
            values["tunnel_network_extension"] = tunnel.allowsNetworkExtension ? "да" : "нет"
            values["tunnel_team"] = tunnel.teamIdentifier ?? "неизвестно"
            values["tunnel_profile_expired"] = tunnel.expired ? "да" : "нет"
        }
        if let app, let tunnel {
            // Разные команды у приложения и расширения — верный отказ системы:
            // расширение обязано принадлежать тому же аккаунту.
            values["same_team"] = (app.teamIdentifier == tunnel.teamIdentifier) ? "да" : "нет"
        }
        return values
    }

    /// Человеческое объяснение, если с подписью что-то не так.
    static var problem: String? {
        let app = applicationProfile
        let tunnel = packetTunnelProfile

        // Профилей нет вовсе — сборка ещё не подписана настоящим сертификатом.
        guard let app else {
            return "Профиль Apple не найден: права подписи нельзя подтвердить по embedded.mobileprovision. Проверьте готовность установки; окончательную проверку выполняет iOS."
        }
        if app.expired {
            return "Профиль подписи истёк. Подпишите приложение заново."
        }
        if !app.allowsNetworkExtension {
            return "В профиле подписи нет права Network Extensions. С таким профилем система не запускает туннель — нужен аккаунт разработчика, где эта возможность включена."
        }
        guard let tunnel else {
            return "Профиль подписи расширения не найден. Проверьте наличие расширения и его права в проверке готовности установки."
        }
        if tunnel.expired { return "Профиль подписи расширения истёк. Подпишите приложение и расширение заново." }
        if !tunnel.allowsNetworkExtension {
            return "У расширения туннеля нет права Network Extensions: программа подписи выдала ему другой профиль. Подпишите приложение вместе с расширением."
        }
        if app.teamIdentifier != tunnel.teamIdentifier {
            return "Приложение и расширение подписаны разными аккаунтами. Система такое сочетание не запускает — подпишите их одним сертификатом."
        }
        return nil
    }

    /// Разбор `embedded.mobileprovision`.
    ///
    /// Файл — подписанный контейнер, но нужный список свойств лежит в нём
    /// открытым текстом между заголовком и концом XML: читаем этот кусок, а
    /// не разбираем всю подпись.
    private static func read(at url: URL) -> Profile? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        guard let start = data.range(of: Data("<?xml".utf8)),
              let end = data.range(of: Data("</plist>".utf8)) else {
            return nil
        }
        let payload = data[start.lowerBound ..< end.upperBound]
        guard let plist = try? PropertyListSerialization.propertyList(
            from: payload,
            options: [],
            format: nil
        ) as? [String: Any] else {
            return nil
        }

        let entitlements = plist["Entitlements"] as? [String: Any] ?? [:]
        let networkExtension = entitlements["com.apple.developer.networking.networkextension"] as? [String] ?? []
        let applicationIdentifier = entitlements["application-identifier"] as? String
        return Profile(
            name: plist["Name"] as? String,
            teamIdentifier: (plist["TeamIdentifier"] as? [String])?.first,
            applicationIdentifier: applicationIdentifier,
            allowsNetworkExtension: networkExtension.contains("packet-tunnel-provider"),
            expirationDate: plist["ExpirationDate"] as? Date
        )
    }
}
