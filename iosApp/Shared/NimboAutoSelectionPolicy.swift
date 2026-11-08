import Foundation

/// Process-local evidence only. No legacy latency or persisted network identity
/// can establish a route. The owner supplies monotonic completion/read times.
final class NimboAutoSelectionPolicy: @unchecked Sendable {
    static let maximumEntries = 8192
    static let maximumAge: TimeInterval = 300

    enum Provenance {
        case configuredRouteHTTP, activeRouteHTTP, tcp, icmp
    }

    struct Ticket {
        let generation: UUID
        let attempt: UUID
        let configurationIdentity: String
    }

    private struct Measurement {
        let latency: Int
        let completedAt: TimeInterval
        let provenance: Provenance
    }

    private let lock = NSLock()
    private var epoch = UUID()
    private var pathEstablished = false
    private var attempts: [String: UUID] = [:]
    private var samples: [String: Measurement] = [:]

    var generation: UUID { lock.lock(); defer { lock.unlock() }; return epoch }
    var entryCount: Int { lock.lock(); defer { lock.unlock() }; return samples.count + attempts.count }

    func invalidate(pathEstablished: Bool = false) {
        lock.lock(); defer { lock.unlock() }
        epoch = UUID()
        self.pathEstablished = self.pathEstablished || pathEstablished
        attempts.removeAll(keepingCapacity: true)
        samples.removeAll(keepingCapacity: true)
    }

    func begin(configurationIdentity: String) -> Ticket {
        lock.lock(); defer { lock.unlock() }
        let ticket = Ticket(generation: epoch, attempt: UUID(), configurationIdentity: configurationIdentity)
        samples.removeValue(forKey: configurationIdentity)
        if attempts[configurationIdentity] == nil && attempts.count + samples.count >= Self.maximumEntries {
            // Deterministic oldest eviction; never evict an active attempt for a
            // new one. Over-capacity queued work can still display, but not rank.
            if let oldest = samples.min(by: {
                $0.value.completedAt == $1.value.completedAt ? $0.key < $1.key : $0.value.completedAt < $1.value.completedAt
            }) { samples.removeValue(forKey: oldest.key) }
            else { return ticket }
        }
        attempts[configurationIdentity] = ticket.attempt
        return ticket
    }

    func complete(_ ticket: Ticket, latency: Int, provenance: Provenance, cancelled: Bool, now: TimeInterval) {
        lock.lock(); defer { lock.unlock() }
        let generation = epoch
        guard generation == ticket.generation,
              attempts[ticket.configurationIdentity] == ticket.attempt else { return }
        attempts.removeValue(forKey: ticket.configurationIdentity)
        guard !cancelled, pathEstablished, now.isFinite, now >= 0,
              (-1...Int(Int32.max)).contains(latency) else { return }
        samples[ticket.configurationIdentity] = Measurement(latency: latency, completedAt: now, provenance: provenance)
    }

    /// Cleanup consumes only this unfinished attempt, never a newer attempt or
    /// an already completed sample. Unattempted work is not a failed probe.
    func discard(_ ticket: Ticket) {
        lock.lock(); defer { lock.unlock() }
        guard epoch == ticket.generation, attempts[ticket.configurationIdentity] == ticket.attempt else { return }
        attempts.removeValue(forKey: ticket.configurationIdentity)
    }

    func rankedIndices(identities: [String], now: TimeInterval, limit: Int) -> [Int] {
        guard limit > 0 else { return [] }
        lock.lock(); defer { lock.unlock() }
        var seen = Set<String>()
        let candidates = identities.enumerated().compactMap { index, identity -> (index: Int, tier: Int, latency: Int)? in
            guard seen.insert(identity).inserted else { return nil }
            guard pathEstablished, now.isFinite, let sample = samples[identity],
                  sample.provenance == .configuredRouteHTTP else { return (index, 1, 0) }
            let age = now - sample.completedAt
            guard age >= 0, age <= Self.maximumAge else { return (index, 1, 0) }
            return sample.latency >= 0 ? (index, 0, sample.latency) : (index, 2, 0)
        }
        return candidates.sorted {
            if $0.tier != $1.tier { return $0.tier < $1.tier }
            if $0.latency != $1.latency { return $0.latency < $1.latency }
            return $0.index < $1.index
        }.prefix(limit).map(\.index)
    }
}
