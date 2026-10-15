import CryptoKit
import Foundation

/// Separate from presentation history: neither legacy integers nor historical
/// process data are imported. Only digests and short-lived evidence are retained.
final class NimboAutoPingCache: @unchecked Sendable {
    static let shared = NimboAutoPingCache()
    typealias Ticket = NimboAutoSelectionPolicy.Ticket
    typealias Provenance = NimboAutoSelectionPolicy.Provenance
    private let policy = NimboAutoSelectionPolicy()
    private let lock = NSLock()
    private var policyIdentity: String?
    private var policyUsable = false
    // Matches current NimboRoutingSettings, Kotlin routing storage and core preference.
    private static let policyKeys = [
        "com.nimbo.connection.vpnCore", "com.nimbo.routing.bypassLocal",
        "com.nimbo.routing.sniffing", "com.nimbo.routing.adBlocking", "com.nimbo.routing.dns",
        "com.nimbo.routing.modules", "com.nimbo.routing.profiles", "com.nimbo.routing.profile-id",
        "com.nimbo.ping.protocol", "com.nimbo.ping.url", "com.nimbo.ping.timeoutMs"
    ]
    private static let maximumPolicyValueBytes = 256 * 1024
    private var observer: NSObjectProtocol?

    private init() {
        synchronizePolicy()
        observer = NotificationCenter.default.addObserver(forName: UserDefaults.didChangeNotification,
                                                          object: nil, queue: nil) { [weak self] _ in
            self?.synchronizePolicy()
        }
    }

    /// Only owned scalar keys, never dictionaryRepresentation or arbitrary plist
    /// serialization. Unexpected types/oversize data disable evidence until fixed;
    /// a stable invalid snapshot does not continuously rotate the generation.
    private func synchronizePolicy() {
        var usable = true
        var values: [String: String] = [:]
        for key in Self.policyKeys {
            let value = UserDefaults.standard.object(forKey: key)
            if value == nil { values[key] = "absent" }
            else if let string = value as? String {
                if string.utf8.count <= Self.maximumPolicyValueBytes { values[key] = "string:" + string }
                else { usable = false; values[key] = "oversize" }
            } else if let number = value as? NSNumber { values[key] = "number:" + number.stringValue }
            else { usable = false; values[key] = "invalid-type" }
        }
        // All values above are strings, so data/date/dictionary defaults cannot
        // make JSON encoding fail and trigger perpetual invalidation.
        let identity = (try? JSONSerialization.data(withJSONObject: values, options: [.sortedKeys]))
            .map { SHA256.hash(data: $0).map { String(format: "%02x", $0) }.joined() }
        // Read defaults before our lock: notifications can run on a writer thread.
        lock.lock(); defer { lock.unlock() }
        if policyIdentity != identity || policyUsable != (usable && identity != nil) {
            policyIdentity = identity
            policyUsable = usable && identity != nil
            policy.invalidate()
        }
    }

    // Progress guards are O(1); snapshots are refreshed at run start, completion,
    // staging and defaults notifications, never at every lease.valid check.
    var generation: UUID { policy.generation }
    var refreshedGeneration: UUID { synchronizePolicy(); return policy.generation }
    private var usable: Bool { lock.lock(); defer { lock.unlock() }; return policyUsable }

    func invalidate(pathEstablished: Bool = false) {
        policy.invalidate(pathEstablished: pathEstablished)
    }

    private func identity(_ configuration: String) -> String? {
        guard !configuration.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              configuration.utf8.count <= 1024 * 1024 else { return nil }
        return SHA256.hash(data: Data(configuration.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    /// Caller captures refreshedGeneration once before beginning a run/batch.
    func begin(configuration: String?) -> Ticket? {
        guard usable, let configuration, let identity = identity(configuration) else { return nil }
        return policy.begin(configurationIdentity: identity)
    }

    func discard(_ ticket: Ticket?) {
        if let ticket { policy.discard(ticket) }
    }

    func complete(_ ticket: Ticket?, latency: Int, provenance: Provenance, cancelled: Bool) {
        synchronizePolicy()
        guard let ticket, usable else { return }
        policy.complete(ticket, latency: latency, provenance: provenance, cancelled: cancelled,
                        now: ProcessInfo.processInfo.systemUptime)
    }

    func rankedIndices(configurations: [String], limit: Int) -> [Int] {
        synchronizePolicy()
        // Missing/oversize configuration is skipped, never assigned an empty hash.
        let eligible = configurations.enumerated().compactMap { index, configuration in
            identity(configuration).map { (index: index, identity: $0) }
        }
        return policy.rankedIndices(identities: eligible.map(\.identity),
                                    now: ProcessInfo.processInfo.systemUptime, limit: limit).map { eligible[$0].index }
    }
}
