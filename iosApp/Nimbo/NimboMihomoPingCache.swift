import CryptoKit
import Foundation

/// Keep the last completed measurement until re-probing. Keys are opaque source
/// and node digests, never subscription URLs, node addresses or credentials.
/// No timer clears values on app launch, disconnect, selection or path changes.
enum NimboMihomoPingCache {
    private static let lock = NSLock()
    private static let prefix = "com.nimbo.mihomo.ping.v1."
    private static let maximumNodes = 8192

    private static func key(_ source: String) -> String? {
        guard source.count == 64, source.unicodeScalars.allSatisfy({
            (48...57).contains($0.value) || (97...102).contains($0.value)
        }) else { return nil }
        return prefix + source
    }

    private static func nodeKey(_ name: String) -> String {
        SHA256.hash(data: Data(name.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    static func values(sourceSHA256: String, names: [String], defaults: UserDefaults = .standard) -> [String: Int] {
        guard let key = key(sourceSHA256) else { return [:] }
        lock.lock(); defer { lock.unlock() }
        let saved = defaults.dictionary(forKey: key) ?? [:]
        guard saved.count <= maximumNodes else { return [:] }
        return Dictionary(names.compactMap { name in
            guard let raw = saved[nodeKey(name)] as? Int, (-1...Int(Int32.max)).contains(raw) else { return nil }
            return (name, raw)
        }, uniquingKeysWith: { first, _ in first })
    }

    static func save(_ latency: Int, name: String, sourceSHA256: String, defaults: UserDefaults = .standard) {
        guard let key = key(sourceSHA256), name.utf8.count <= 4096,
              (-1...Int(Int32.max)).contains(latency) else { return }
        lock.lock(); defer { lock.unlock() }
        var saved = defaults.dictionary(forKey: key) ?? [:]
        let node = nodeKey(name)
        guard saved.count < maximumNodes || saved[node] != nil else { return }
        saved[node] = latency
        defaults.set(saved, forKey: key)
    }
}
