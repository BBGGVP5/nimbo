import Foundation
#if canImport(UIKit)
import SwiftUI
import UIKit
import UniformTypeIdentifiers
#endif

/// Contains subscription credentials: export only to a location the user trusts.
/// A full YAML is authoritative and must be recovered offline, not refetched.
enum NimboBackup {
    private static let version = 2
    static let maximumArchiveBytes = 32 * 1_024 * 1_024

    struct Payload: Codable {
        let version: Int
        let createdAt: Date
        let source: String?
        let profile: String?
        let activeServerID: String?
        // Optional for backwards-compatible decoding of version-1 backups.
        let fullConfiguration: NimboFullConfiguration?
        let subscriptionMeta: NimboSubscriptionMeta?
        let settings: [String: String]
    }

    private static let settingKeys = [
        "com.nimbo.appearance.showSpeedWidget",
        "com.nimbo.appearance.showMemoryWidget",
        "com.nimbo.appearance.themeMode",
        "com.nimbo.appearance.accentHex",
        "com.nimbo.appearance.textScale",
        "com.nimbo.appearance.haptics",
        "com.nimbo.appearance.navIconMotion",
        "com.nimbo.appearance.connectStyle",
        "com.nimbo.appearance.serverSort",
        "com.nimbo.appearance.favoritesFirst",
        "com.nimbo.appearance.pingOnLaunch",
        "com.nimbo.appearance.pingAfterRefresh",
        "com.nimbo.appearance.refreshOnLaunch",
        "com.nimbo.ping.protocol",
        "com.nimbo.ping.display",
        "com.nimbo.ping.timeoutMs",
        "com.nimbo.ping.url",
        "com.nimbo.update.channel",
        "com.nimbo.update.notify",
        "com.nimbo.routing.bypassLocal",
        "com.nimbo.routing.sniffing",
        "com.nimbo.routing.dns",
        "com.nimbo.routing.adBlocking",
        "com.nimbo.connection.vpnCore"
    ]

    static func export(defaults: UserDefaults = .standard) -> URL? {
        do {
            // Do not silently export a retained legacy node if this read fails.
            let full = try NimboConfigurationStore.shared.loadFullConfiguration()
            var settings: [String: String] = [:]
            for key in settingKeys {
                if let value = defaults.object(forKey: key) { settings[key] = String(describing: value) }
            }
            let source: String?
            if let full { source = full.source }
            else { source = try NimboConfigurationStore.shared.loadSource() }
            let payload = Payload(version: version, createdAt: Date(),
                source: source,
                profile: full == nil ? NimboSubscriptionRepository.shared.rawProfileJSON() : nil,
                activeServerID: full == nil ? NimboConfigurationStore.shared.activeServerID : nil,
                fullConfiguration: full, subscriptionMeta: NimboSubscriptionMetaStore.load(defaults: defaults), settings: settings)
            try validate(payload)
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            encoder.dateEncodingStrategy = .iso8601
            let data = try encoder.encode(payload)
            guard data.count <= maximumArchiveBytes else { throw NimboBackupError.invalidSize }
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("nimbo-backup-\(UUID().uuidString).json")
            try data.write(to: url, options: .atomic)
            try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: url.path)
            return url
        } catch { return nil }
    }

    private static func validate(_ payload: Payload) throws {
        guard payload.version == 1 || payload.version == version else {
            throw NimboBackupError.unsupportedVersion
        }
        guard payload.settings.count <= 256,
              payload.settings.allSatisfy({ $0.key.utf8.count <= 256 && $0.value.utf8.count <= 16_384 }),
              (payload.activeServerID?.utf8.count ?? 0) <= 4_096,
              (payload.profile?.utf8.count ?? 0) <= 15 * 1_024 * 1_024 else {
            throw NimboBackupError.invalidMetadata
        }
        if let source = payload.source {
            guard source.utf8.count <= 16_384, let url = URL(string: source), url.host != nil,
                  ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
                throw NimboBackupError.invalidMetadata
            }
        }
        if let core = payload.settings[NimboCorePreference.defaultsKey], NimboCorePreference(rawValue: core) == nil {
            throw NimboBackupError.invalidMetadata
        }
        if let meta = payload.subscriptionMeta {
            guard (meta.title?.utf8.count ?? 0) <= 1_024, (meta.announce?.utf8.count ?? 0) <= 65_536,
                  meta.usedTraffic >= 0, meta.totalTraffic >= 0,
                  meta.expireAt.isFinite, meta.expireAt >= 0,
                  meta.updatedAt.isFinite, meta.updatedAt >= 0 else { throw NimboBackupError.invalidMetadata }
            for address in [meta.supportUrl, meta.websiteUrl].compactMap({ $0 }) {
                guard address.utf8.count <= 16_384, let url = URL(string: address), url.host != nil,
                      ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
                    throw NimboBackupError.invalidMetadata
                }
            }
        }
        if let fullConfiguration = payload.fullConfiguration {
            try fullConfiguration.validate()
            // Mixed/corrupt archives cannot hide a second legacy profile/source.
            guard payload.profile == nil, payload.activeServerID == nil,
                  payload.source == fullConfiguration.source else { throw NimboBackupError.invalidMetadata }
        }
    }

    private static func readArchive(_ url: URL) throws -> Data {
        guard url.isFileURL, let stream = InputStream(url: url) else { throw NimboBackupError.invalidArchive }
        stream.open()
        defer { stream.close() }
        var result = Data()
        let capacity = 64 * 1_024
        var buffer = [UInt8](repeating: 0, count: capacity)
        while true {
            let count = stream.read(&buffer, maxLength: capacity)
            guard count >= 0 else { throw NimboBackupError.invalidArchive }
            if count == 0 { break }
            guard count <= maximumArchiveBytes - result.count else { throw NimboBackupError.invalidSize }
            result.append(contentsOf: buffer.prefix(count))
        }
        guard !result.isEmpty else { throw NimboBackupError.invalidArchive }
        return result
    }

    /// Legacy subscriptions may refresh afterwards. A full profile returns nil:
    /// restore the exact source/choices now, even without access to its provider.
    static func restore(from url: URL, defaults: UserDefaults = .standard) throws -> String? {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let data = try readArchive(url)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let payload = try decoder.decode(Payload.self, from: data)
        try validate(payload)

        // Native admission and Keychain commit precede all settings writes.
        if let fullConfiguration = payload.fullConfiguration {
            try fullConfiguration.validate()
            _ = try NimboSubscriptionRepository.shared.importFullConfiguration(fullConfiguration)
        } else if let profile = payload.profile {
            try NimboSubscriptionRepository.shared.restoreLegacyProfile(Data(profile.utf8),
                source: payload.source, selectedServerID: payload.activeServerID)
        }
        if payload.fullConfiguration != nil || payload.profile != nil || payload.subscriptionMeta != nil {
            NimboSubscriptionMetaStore.save(payload.subscriptionMeta ?? .empty, defaults: defaults)
        }
        for (key, raw) in payload.settings {
            guard settingKeys.contains(key) else { continue }
            let suffix = key.components(separatedBy: ".").last ?? ""
            if ["accentHex", "themeMode", "serverSort", "dns", "protocol", "display", "url", "channel", "vpnCore"].contains(suffix) {
                defaults.set(raw, forKey: key)
                continue
            }
            switch raw {
            case "true", "false": defaults.set(raw == "true", forKey: key)
            default:
                if let number = Int(raw) { defaults.set(number, forKey: key) }
                else if let number = Double(raw), number.isFinite { defaults.set(number, forKey: key) }
                else { defaults.set(raw, forKey: key) }
            }
        }
        return payload.fullConfiguration == nil ? payload.source : nil
    }
}

enum NimboBackupError: LocalizedError {
    case invalidArchive, invalidSize, invalidMetadata, unsupportedVersion
    var errorDescription: String? {
        switch self {
        case .invalidArchive: "Не удалось прочитать резервную копию (IOS_BACKUP_INVALID)."
        case .invalidSize: "Резервная копия превышает 32 МиБ (IOS_BACKUP_SIZE)."
        case .invalidMetadata: "Некорректные данные резервной копии (IOS_BACKUP_METADATA)."
        case .unsupportedVersion: "Версия резервной копии не поддерживается (IOS_BACKUP_VERSION)."
        }
    }
}

#if canImport(UIKit)
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

#endif
