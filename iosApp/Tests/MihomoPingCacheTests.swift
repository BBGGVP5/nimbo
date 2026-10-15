import Foundation

@main struct MihomoPingCacheTests {
    static func main() {
        let suite = "nimbo-mihomo-cache-tests-" + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let source = String(repeating: "a", count: 64)
        let changed = String(repeating: "b", count: 64)
        NimboMihomoPingCache.save(0, name: "zero", sourceSHA256: source, defaults: defaults)
        NimboMihomoPingCache.save(-1, name: "failed", sourceSHA256: source, defaults: defaults)
        NimboMihomoPingCache.save(148, name: "node", sourceSHA256: source, defaults: defaults)
        let reopened = UserDefaults(suiteName: suite)!
        precondition(NimboMihomoPingCache.values(sourceSHA256: source, names: ["zero", "failed", "node", "unprobed"], defaults: reopened) == ["zero": 0, "failed": -1, "node": 148])
        precondition(NimboMihomoPingCache.values(sourceSHA256: changed, names: ["node"], defaults: reopened).isEmpty)
        NimboMihomoPingCache.save(27, name: "node", sourceSHA256: source, defaults: reopened)
        precondition(NimboMihomoPingCache.values(sourceSHA256: source, names: ["node"], defaults: defaults)["node"] == 27)
        precondition(!String(describing: defaults.persistentDomain(forName: suite)).contains("failed"))
        NimboMihomoPingCache.save(-2, name: "node", sourceSHA256: source, defaults: defaults)
        precondition(NimboMihomoPingCache.values(sourceSHA256: source, names: ["node"], defaults: defaults)["node"] == 27)
        precondition(NimboMihomoPingCache.values(sourceSHA256: "invalid", names: ["node"], defaults: defaults).isEmpty)
        print("Mihomo ping cache round-trip and source identity passed")
    }
}
