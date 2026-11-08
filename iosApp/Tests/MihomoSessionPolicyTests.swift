import Foundation

// Executes the production pure policy, not a mock NetworkExtension or C runtime.
@main
enum MihomoSessionPolicyTests {
    static let source = String(repeating: "a", count: 64)
    static let identity = NimboMihomoSessionPolicy.Identity(generation: 7, sourceSHA256: source)
    static func reply() -> [String: Any] {
        ["apiVersion": 1, "requestId": "fixture", "success": true, "generation": 7,
         "data": ["apiVersion": 1, "state": "running", "tunReady": true,
                  "networkOwner": "ios-packet-flow", "sourceSHA256": source,
                  "coreVersion": NimboMihomoSessionPolicy.coreVersion,
                  "coreCommit": NimboMihomoSessionPolicy.coreCommit]]
    }
    static func main() {
        let original = Data([0xef, 0xbb, 0xbf]) + Data("# source\r\nproxies: []\r\n".utf8)
        precondition(Data(NimboMihomoSessionPolicy.exactUTF8(original)!.utf8) == original)
        precondition(NimboMihomoSessionPolicy.exactUTF8(Data([0xff])) == nil)
        precondition(NimboMihomoSessionPolicy.exactUTF8(Data()) == nil)
        precondition(NimboMihomoSessionPolicy.readyIdentity(reply(), requestID: "fixture", sourceSHA256: source) == identity)
        let wire = try! JSONSerialization.jsonObject(with: JSONSerialization.data(withJSONObject: reply())) as! [String: Any]
        precondition(NimboMihomoSessionPolicy.readyIdentity(wire, requestID: "fixture", sourceSHA256: source) == identity)
        for (key, value) in [("apiVersion", 2 as Any), ("apiVersion", true as Any),
                             ("requestId", "old" as Any), ("generation", 0 as Any),
                             ("generation", -1 as Any), ("success", false as Any)] {
            var bad = reply(); bad[key] = value
            precondition(NimboMihomoSessionPolicy.readyIdentity(bad, requestID: "fixture", sourceSHA256: source) == nil, key)
        }
        for (key, value) in [("apiVersion", 2 as Any), ("state", "failed" as Any),
                             ("tunReady", false as Any), ("networkOwner", "desktop-tun" as Any),
                             ("coreCommit", "other" as Any), ("coreVersion", "other" as Any),
                             ("sourceSHA256", String(repeating: "b", count: 64) as Any)] {
            var bad = reply(); var data = bad["data"] as! [String: Any]; data[key] = value; bad["data"] = data
            precondition(NimboMihomoSessionPolicy.readyIdentity(bad, requestID: "fixture", sourceSHA256: source) == nil, key)
            precondition(!NimboMihomoSessionPolicy.isRunning(bad, identity: identity), key)
        }
        precondition(NimboMihomoSessionPolicy.readyIdentity(reply(), requestID: "fixture", sourceSHA256: "") == nil)
        precondition(NimboMihomoSessionPolicy.isRunning(reply(), identity: identity))
        var old = reply(); old["generation"] = 6
        precondition(!NimboMihomoSessionPolicy.isRunning(old, identity: identity))
        precondition(!NimboMihomoSessionPolicy.matchesEnvelope(old, requestID: "fixture", identity: identity))
        precondition(!NimboMihomoSessionPolicy.matchesEnvelope(reply(), requestID: "old", identity: identity))
        var state = NimboMihomoSessionPolicy.PhysicalPathState()
        let wifi = NimboMihomoSessionPolicy.PhysicalBinding(index: 4, name: "en0", supportsIPv4: true, supportsIPv6: true)
        let cellular = NimboMihomoSessionPolicy.PhysicalBinding(index: 7, name: "pdp_ip0", supportsIPv4: true, supportsIPv6: true)
        precondition(!state.update(nil))
        precondition(!state.update(wifi), "Initial binding must not reset a starting runtime")
        precondition(!state.update(wifi), "Unchanged ticks must not tear down sockets")
        precondition(state.update(nil), "Path loss must retire old sockets")
        precondition(!state.update(nil))
        precondition(state.update(wifi), "Path return must reset even after index became zero")
        precondition(state.update(cellular))
        precondition(state.update(.init(index: 7, name: "pdp_ip0", supportsIPv4: false, supportsIPv6: true)))
        precondition(!state.update(.init(index: 7, name: "pdp_ip0", supportsIPv4: false, supportsIPv6: true)))
        print("Mihomo readiness identity, reply generation and physical path transitions passed")
    }
}
