import Foundation

@main
enum AutoSelectionPolicyTests {
    static func main() {
        let store = NimboAutoSelectionPolicy()
        func order(_ ids: [String], now: TimeInterval = 100) -> [Int] {
            store.rankedIndices(identities: ids, now: now, limit: 16)
        }
        func complete(_ identity: String, _ latency: Int,
                      _ provenance: NimboAutoSelectionPolicy.Provenance = .configuredRouteHTTP,
                      now: TimeInterval = 100, cancelled: Bool = false) {
            store.complete(store.begin(configurationIdentity: identity), latency: latency,
                           provenance: provenance, cancelled: cancelled, now: now)
        }
        complete("startup", 0)
        precondition(order(["unknown", "startup"]) == [0, 1], "Startup has no trusted path")
        let startup = store.begin(configurationIdentity: "startup")
        store.invalidate(pathEstablished: true)
        store.complete(startup, latency: 0, provenance: .configuredRouteHTTP, cancelled: false, now: 100)
        precondition(order(["unknown", "startup"]) == [0, 1], "First callback cannot adopt historical/in-flight proof")

        complete("zero", 0)
        complete("slow", Int(Int32.max))
        complete("failed", -1)
        precondition(order(["failed", "unknown", "slow", "zero"]) == [3, 2, 1, 0])
        precondition(order(["unknown", "zero"], now: 400) == [1, 0], "TTL boundary is inclusive")
        precondition(order(["unknown", "zero"], now: 401) == [0, 1], "Expired proof is unknown")
        precondition(order(["unknown", "zero"], now: 99) == [0, 1], "Clock reversal is unknown")
        precondition(order(["unknown", "zero"], now: .infinity) == [0, 1])

        for provenance in [NimboAutoSelectionPolicy.Provenance.tcp, .icmp, .activeRouteHTTP] {
            complete("endpoint", 0, provenance)
            precondition(order(["unknown", "endpoint"]) == [0, 1], "Endpoint/direct/active unbound proof must not be promoted")
            complete("endpoint", -1, provenance)
            precondition(order(["endpoint", "unknown"]) == [0, 1], "TCP negative cannot prove route failure")
        }
        complete("cancelled", 0, cancelled: true)
        precondition(order(["unknown", "cancelled"]) == [0, 1])
        complete("bad", -2)
        precondition(order(["bad", "unknown"]) == [0, 1])
        complete("bad", Int(Int32.max) + 1)
        precondition(order(["unknown", "bad"]) == [0, 1])
        complete("bad", 0, now: .nan)
        precondition(order(["unknown", "bad"]) == [0, 1])
        complete("old-config", 1)
        precondition(order(["unknown", "new-config"]) == [0, 1], "Credential/config replacement has no inherited proof")

        let old = store.begin(configurationIdentity: "race")
        let latest = store.begin(configurationIdentity: "race")
        store.complete(latest, latency: -1, provenance: .configuredRouteHTTP, cancelled: false, now: 100)
        store.complete(old, latency: 0, provenance: .configuredRouteHTTP, cancelled: false, now: 100)
        precondition(order(["race", "unknown"]) == [1, 0], "Late previous attempt cannot overwrite failure")
        store.complete(latest, latency: 0, provenance: .configuredRouteHTTP, cancelled: false, now: 100)
        precondition(order(["race", "unknown"]) == [1, 0], "Ticket may complete only once")

        let discarded = store.begin(configurationIdentity: "discard-race")
        let replacement = store.begin(configurationIdentity: "discard-race")
        store.discard(discarded)
        store.complete(replacement, latency: 0, provenance: .configuredRouteHTTP, cancelled: false, now: 100)
        store.discard(replacement)
        precondition(order(["unknown", "discard-race"]) == [1, 0], "Old cleanup must preserve newer attempt and completed sample")

        let obsolete = store.begin(configurationIdentity: "obsolete")
        let generation = store.generation
        store.invalidate()
        precondition(store.generation != generation)
        store.complete(obsolete, latency: 0, provenance: .configuredRouteHTTP, cancelled: false, now: 100)
        precondition(order(["unknown", "obsolete", "zero"]) == [0, 1, 2], "Path/session changes retire all proof")
        complete("fresh", 5)
        precondition(order(["unknown", "fresh"]) == [1, 0])
        _ = store.begin(configurationIdentity: "fresh")
        precondition(order(["unknown", "fresh"]) == [0, 1], "Reprobe retires earlier success")

        let identities = (0..<40).map { "node-\($0)" }
        complete("node-39", 0)
        precondition(order(identities) == [39] + Array(0..<15))
        precondition(order(["duplicate", "duplicate", "other"]) == [0, 2])
        precondition(store.rankedIndices(identities: identities, now: 100, limit: 0).isEmpty)
        precondition(store.rankedIndices(identities: identities, now: 100, limit: -1).isEmpty)
        for _ in 0..<20 { precondition(order(identities) == [39] + Array(0..<15)) }

        let bounded = NimboAutoSelectionPolicy()
        bounded.invalidate(pathEstablished: true)
        for index in 0..<9000 {
            bounded.complete(bounded.begin(configurationIdentity: "\(index)"), latency: index,
                             provenance: .configuredRouteHTTP, cancelled: false, now: 100)
        }
        precondition(bounded.entryCount <= 8192, "Storage must stay bounded")
        let queued = NimboAutoSelectionPolicy()
        queued.invalidate(pathEstablished: true)
        let unfinished = (0..<8192).map { queued.begin(configurationIdentity: "queued-\($0)") }
        precondition(queued.entryCount == 8192)
        for ticket in unfinished { queued.discard(ticket) }
        precondition(queued.entryCount == 0, "Interrupted batch must release all queued capacity")
        queued.complete(queued.begin(configurationIdentity: "new"), latency: 0,
                        provenance: .configuredRouteHTTP, cancelled: false, now: 100)
        precondition(queued.rankedIndices(identities: ["unknown", "new"], now: 100, limit: 16) == [1, 0])
        print("PASS: auto evidence startup, provenance, generation, attempts, cancellation, config identity, TTL, stable capped ranking and bounds")
    }
}
