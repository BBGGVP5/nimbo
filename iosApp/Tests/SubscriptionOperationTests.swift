import Foundation

@main enum SubscriptionOperationTests {
    static func main() throws {
        let gate = NimboSubscriptionOperationGate()
        let first = try gate.begin()
        precondition(gate.isWorking)
        first.invalidate() // In-flight parsing/storage still owns the profile.
        do { _ = try gate.begin(); preconditionFailure("Concurrent refresh admitted") }
        catch NimboSubscriptionOperationGate.GateError.busy {}
        gate.finish(NimboVpnCommandLease()) // A stale/non-owner callback must not unlock.
        precondition(gate.isWorking)
        gate.finish(first)
        precondition(!gate.isWorking)
        let retry = try gate.begin()
        gate.finish(first)
        precondition(gate.isWorking)
        gate.finish(retry)
        precondition(!gate.isWorking)

        let entries = [(id: "a", raw: "vless://dummy-a@node.invalid:443"),
                       (id: "b", raw: "vless://dummy-b@node.invalid:443")]
        precondition(NimboSelectedServerRecovery.exactID(configuration: Data(entries[1].raw.utf8), entries: entries) == "b")
        precondition(NimboSelectedServerRecovery.exactID(configuration: nil, entries: entries) == nil)
        precondition(NimboSelectedServerRecovery.exactID(configuration: Data("node.invalid".utf8), entries: entries) == nil)
        let ambiguous = [(id: "a", raw: entries[0].raw), (id: "b", raw: entries[0].raw)]
        precondition(NimboSelectedServerRecovery.exactID(configuration: Data(entries[0].raw.utf8), entries: ambiguous) == nil)
        precondition(NimboSelectedServerRecovery.isAutomatic(Data(#"{"nimbo":{"balancer":true},"shareLinks":["a","b"]}"#.utf8)))
        for value in [#"{"nimbo":{"balancer":1},"shareLinks":["a","b"]}"#,
                      #"{"nimbo":{"balancer":"true"},"shareLinks":["a","b"]}"#,
                      #"{"nimbo":{"balancer":true},"shareLinks":["a"]}"#, "invalid"] {
            precondition(!NimboSelectedServerRecovery.isAutomatic(Data(value.utf8)))
        }
        print("PASS: subscription operation ownership, cancelled/stale leases and secure cached selection recovery")
    }
}
