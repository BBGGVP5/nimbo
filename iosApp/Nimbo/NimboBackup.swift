import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Резервная копия настроек и подписки.
///
/// App Group у сборки нет, зато файлы iOS отдаёт свободно: копия уходит в
/// «Поделиться» (Файлы, AirDrop, мессенджер), а возвращается через системный
/// выбор файла. Секреты внутри — адрес подписки и конфигурации серверов,
/// поэтому копию стоит хранить там же, где хранят пароли.
enum NimboBackup {
    private static let version = 1

    struct Payload: Codable {
        let version: Int
        let createdAt: Date
        let source: String?
        let profile: String?
        let activeServerID: String?
        let settings: [String: String]
    }

    /// Ключи настроек, которые имеет смысл переносить: оформление и
    /// маршрутизация. Замеры, сессии и кеши восстанавливать незачем.
    private static let settingKeys = [
        "com.nimbo.appearance.backgroundStyle",
        "com.nimbo.appearance.backgroundPalette",
        "com.nimbo.appearance.backgroundMotion",
        "com.nimbo.appearance.showSpeedWidget",
        "com.nimbo.appearance.showMemoryWidget",
        "com.nimbo.appearance.elementStyle",
        "com.nimbo.appearance.themeMode",
        "com.nimbo.appearance.accentHex",
        "com.nimbo.appearance.brightness",
        "com.nimbo.appearance.transparency",
        "com.nimbo.appearance.corners",
        "com.nimbo.appearance.textScale",
        "com.nimbo.appearance.refraction",
        "com.nimbo.appearance.haptics",
        "com.nimbo.appearance.navIconMotion",
        "com.nimbo.appearance.statusParticles",
        "com.nimbo.appearance.connectStyle",
        "com.nimbo.appearance.serverSort",
        "com.nimbo.appearance.favoritesFirst",
        "com.nimbo.appearance.pingOnLaunch",
        "com.nimbo.appearance.pingAfterRefresh",
        "com.nimbo.appearance.refreshOnLaunch",
        "com.nimbo.ping.protocol",
        "com.nimbo.ping.timeoutMs",
        "com.nimbo.ping.url",
        "com.nimbo.update.channel",
        "com.nimbo.update.notify",
        "com.nimbo.routing.bypassLocal",
        "com.nimbo.routing.sniffing",
        "com.nimbo.routing.dns"
    ]

    static func export() -> URL? {
        let defaults = UserDefaults.standard
        var settings: [String: String] = [:]
        for key in settingKeys {
            guard let value = defaults.object(forKey: key) else { continue }
            settings[key] = String(describing: value)
        }

        let payload = Payload(
            version: version,
            createdAt: Date(),
            source: (try? NimboConfigurationStore.shared.loadSource()) ?? nil,
            profile: NimboSubscriptionRepository.shared.rawProfileJSON(),
            activeServerID: NimboConfigurationStore.shared.activeServerID,
            settings: settings
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(payload) else { return nil }

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd-HHmm"
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("nimbo-backup-\(formatter.string(from: Date())).json")
        guard (try? data.write(to: url, options: .atomic)) != nil else { return nil }
        return url
    }

    /// Возвращает адрес подписки, если он был в копии: восстановление всегда
    /// заканчивается обновлением подписки, чтобы список серверов был свежим.
    static func restore(from url: URL) throws -> String? {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        let data = try Data(contentsOf: url)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let payload = try decoder.decode(Payload.self, from: data)

        let defaults = UserDefaults.standard
        for (key, raw) in payload.settings {
            guard settingKeys.contains(key) else { continue }
            // Текстовый HEX может состоять только из цифр: не превращаем
            // акцент, DNS и прочие строковые ключи в NSNumber.
            let suffix = key.components(separatedBy: ".").last ?? ""
            if ["accentHex", "themeMode", "elementStyle", "connectStyle", "serverSort", "dns", "protocol", "url", "channel"].contains(suffix) {
                defaults.set(raw, forKey: key)
                continue
            }
            switch raw {
            case "true", "false": defaults.set(raw == "true", forKey: key)
            default:
                if let number = Int(raw) {
                    defaults.set(number, forKey: key)
                } else if let number = Double(raw), number.isFinite {
                    defaults.set(number, forKey: key)
                } else {
                    defaults.set(raw, forKey: key)
                }
            }
        }
        return payload.source
    }
}

/// Системное «Поделиться» для файла копии.
struct NimboShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context _: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_: UIActivityViewController, context _: Context) {}
}

/// Системный выбор файла — им возвращают копию обратно.
struct NimboDocumentPicker: UIViewControllerRepresentable {
    let onPick: (URL) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.json])
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = false
        return picker
    }

    func updateUIViewController(_: UIDocumentPickerViewController, context _: Context) {}

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        private let onPick: (URL) -> Void

        init(onPick: @escaping (URL) -> Void) {
            self.onPick = onPick
        }

        func documentPicker(_: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let url = urls.first else { return }
            onPick(url)
        }
    }
}

/// Лист «Поделиться» принимает элемент по Identifiable — для временного файла
/// достаточно его пути.
extension URL: Identifiable {
    public var id: String { absoluteString }
}
