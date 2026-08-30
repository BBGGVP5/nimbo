import Foundation

/// Настройки маршрутизации, общие для приложения и расширения.
///
/// Раздельного туннелирования по приложениям iOS обычному VPN не даёт, поэтому
/// управляем тем, что система действительно позволяет: обходом локальных сетей,
/// DNS туннеля и определением доменов в ядре.
struct NimboRoutingOptions: Equatable {
    var bypassLocalNetworks: Bool
    var sniffingEnabled: Bool
    var dnsPreset: String

    static let `default` = NimboRoutingOptions(
        bypassLocalNetworks: true,
        sniffingEnabled: true,
        dnsPreset: "cloudflare"
    )

    /// Адреса DNS для системных настроек туннеля. `nil` — «системный» набор:
    /// свои серверы не навязываем, адреса выдаёт сеть.
    var dnsServers: [String]? {
        switch dnsPreset {
        case "google": return ["8.8.8.8", "8.8.4.4", "2001:4860:4860::8888"]
        case "adguard": return ["94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff"]
        case "system": return nil
        default: return ["1.1.1.1", "1.0.0.1", "2606:4700:4700::1111"]
        }
    }

    /// Представление для `providerConfiguration`: только простые типы, иначе
    /// NetworkExtension не сохранит настройки.
    var providerValue: [String: Any] {
        [
            "bypassLocal": bypassLocalNetworks,
            "sniffing": sniffingEnabled,
            "dns": dnsPreset
        ]
    }

    init(bypassLocalNetworks: Bool, sniffingEnabled: Bool, dnsPreset: String) {
        self.bypassLocalNetworks = bypassLocalNetworks
        self.sniffingEnabled = sniffingEnabled
        self.dnsPreset = dnsPreset
    }

    /// Разбор того, что приложение положило в `providerConfiguration`.
    /// Отсутствующие ключи означают старую конфигурацию — берём значения по
    /// умолчанию, а не отключаем всё подряд.
    init(providerValue: Any?) {
        let stored = providerValue as? [String: Any] ?? [:]
        self.bypassLocalNetworks = stored["bypassLocal"] as? Bool ?? NimboRoutingOptions.default.bypassLocalNetworks
        self.sniffingEnabled = stored["sniffing"] as? Bool ?? NimboRoutingOptions.default.sniffingEnabled
        self.dnsPreset = stored["dns"] as? String ?? NimboRoutingOptions.default.dnsPreset
    }
}

/// Хранилище настроек на стороне приложения.
///
/// Ключи совпадают с теми, что пишет общий интерфейс (`applyRoutingChange` в
/// `IosComposeController`), поэтому экран и туннель видят одно и то же.
enum NimboRoutingSettings {
    private static let prefix = "com.nimbo.routing."

    static var current: NimboRoutingOptions {
        let defaults = UserDefaults.standard
        return NimboRoutingOptions(
            bypassLocalNetworks: flag("bypassLocal", default: true),
            sniffingEnabled: flag("sniffing", default: true),
            dnsPreset: defaults.string(forKey: prefix + "dns") ?? "cloudflare"
        )
    }

    private static func flag(_ key: String, default fallback: Bool) -> Bool {
        let defaults = UserDefaults.standard
        guard defaults.object(forKey: prefix + key) != nil else { return fallback }
        return defaults.bool(forKey: prefix + key)
    }
}
