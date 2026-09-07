import Foundation

enum NimboConstants {
    static let activeConfigurationFile = "active-config.json"
    static let diagnosticFile = "nimbo-ios-diagnostics.jsonl"
    static let diagnosticArchiveFile = "nimbo-ios-diagnostics.previous.jsonl"
    static let diagnosticManifestFile = "nimbo-ios-diagnostics-manifest.json"

    static var mainBundleIdentifier: String {
        let current = Bundle.main.bundleIdentifier ?? "com.nimbo.resignable"
        // Внутри расширения нужен идентификатор приложения, а не свой. Он
        // берётся из самого приложения: расширение лежит в его PlugIns, и
        // подниматься по пути надёжнее, чем отрезать известные суффиксы —
        // расширений уже два, и завтра появится третье.
        if Bundle.main.bundlePath.hasSuffix(".appex") {
            let hostURL = Bundle.main.bundleURL
                .deletingLastPathComponent()
                .deletingLastPathComponent()
            if let host = Bundle(url: hostURL)?.bundleIdentifier, !host.isEmpty {
                return host
            }
            if let separator = current.lastIndex(of: ".") {
                return String(current[current.startIndex ..< separator])
            }
        }
        return current
    }

    static var packetTunnelBundleIdentifier: String {
        embeddedPacketTunnelBundleIdentifier
            ?? resolvedInfoValue("NimboPacketTunnelBundleIdentifier")
            ?? "\(mainBundleIdentifier).PacketTunnel"
    }

    /// Re-signing tools commonly replace the application and extension bundle
    /// identifiers without touching custom Info.plist keys. Always prefer the
    /// identifier of the extension that is actually embedded in this build so
    /// NETunnelProviderManager can find it after AltStore/SideStore/TrollStore
    /// or a private signing service has applied its own App ID.
    private static var embeddedPacketTunnelBundleIdentifier: String? {
        // Из приложения смотрим в его PlugIns, из расширения — в папку, где
        // лежим сами: там же лежит и туннель.
        let plugIns: URL? = Bundle.main.bundlePath.hasSuffix(".appex")
            ? Bundle.main.bundleURL.deletingLastPathComponent()
            : Bundle.main.builtInPlugInsURL
        guard let plugInsURL = plugIns,
              let children = try? FileManager.default.contentsOfDirectory(
                  at: plugInsURL,
                  includingPropertiesForKeys: nil,
                  options: [.skipsHiddenFiles]
              ) else {
            return nil
        }

        for url in children where url.pathExtension == "appex" {
            guard let bundle = Bundle(url: url),
                  let extensionInfo = bundle.object(forInfoDictionaryKey: "NSExtension") as? [String: Any],
                  let identifier = bundle.bundleIdentifier,
                  !identifier.isEmpty else {
                continue
            }
            // Расширений в приложении несколько: рядом с туннелем лежит виджет
            // Пункта управления. Брать первое попавшееся нельзя — система
            // отвечает, что приложение VPN не установлено, потому что в
            // конфигурации оказывается идентификатор виджета.
            let point = extensionInfo["NSExtensionPointIdentifier"] as? String
            guard point == "com.apple.networkextension.packet-tunnel" else { continue }
            return identifier
        }
        return nil
    }

    static var appGroup: String? {
        resolvedInfoValue("NimboAppGroupIdentifier")
            ?? "group.\(mainBundleIdentifier)"
    }

    static var sharedContainer: URL? {
        guard let appGroup else { return nil }
        return FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)
    }

    static var diagnosticContainer: URL {
        if let sharedContainer { return sharedContainer }
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("Nimbo/Diagnostics", isDirectory: true)
    }

    static var activeConfigurationURL: URL? {
        sharedContainer?.appendingPathComponent(activeConfigurationFile)
    }

    private static func resolvedInfoValue(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
              !value.isEmpty,
              !value.contains("$(") else {
            return nil
        }
        return value
    }
}
