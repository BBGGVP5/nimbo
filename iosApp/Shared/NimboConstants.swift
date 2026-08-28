import Foundation

enum NimboConstants {
    static let activeConfigurationFile = "active-config.json"
    static let diagnosticFile = "nimbo-ios-diagnostics.jsonl"
    static let diagnosticArchiveFile = "nimbo-ios-diagnostics.previous.jsonl"
    static let diagnosticManifestFile = "nimbo-ios-diagnostics-manifest.json"

    static var mainBundleIdentifier: String {
        let current = Bundle.main.bundleIdentifier ?? "com.nimbo.resignable"
        return current.hasSuffix(".PacketTunnel")
            ? String(current.dropLast(".PacketTunnel".count))
            : current
    }

    static var packetTunnelBundleIdentifier: String {
        resolvedInfoValue("NimboPacketTunnelBundleIdentifier")
            ?? "\(mainBundleIdentifier).PacketTunnel"
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
