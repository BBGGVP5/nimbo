import Foundation
import CoreFoundation

/// Portable admission for the real C bridge, also executed by macOS CI.
/// A successful envelope alone is not proof that this packet owner is ready.
enum NimboMihomoSessionPolicy {
    static let coreVersion = "v1.19.32"
    static let coreCommit = "88dcbf7f1614a67c3b36b848ee3592dfa92ada36"

    struct Identity: Equatable {
        let generation: UInt64
        let sourceSHA256: String
    }

    static func exactUTF8(_ data: Data) -> String? {
        guard !data.isEmpty, data.count <= 4 * 1_024 * 1_024 else { return nil }
        let text = String(decoding: data, as: UTF8.self)
        return Data(text.utf8) == data ? text : nil
    }

    private static func integer(_ value: Any?) -> UInt64? {
        guard let number = value as? NSNumber,
              CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        return UInt64(number.stringValue)
    }

    static func matchesEnvelope(_ reply: [String: Any], requestID: String, identity: Identity?) -> Bool {
        guard integer(reply["apiVersion"]) == 1, reply["success"] as? Bool == true,
              reply["requestId"] as? String == requestID else { return false }
        return identity.map { integer(reply["generation"]) == $0.generation } ?? true
    }

    static func readyIdentity(_ reply: [String: Any], requestID: String, sourceSHA256: String) -> Identity? {
        guard sourceSHA256.utf8.count == 64,
              sourceSHA256.utf8.allSatisfy({ (48...57).contains($0) || (97...102).contains($0) }),
              matchesEnvelope(reply, requestID: requestID, identity: nil),
              let generation = integer(reply["generation"]), generation > 0 else { return nil }
        let identity = Identity(generation: generation, sourceSHA256: sourceSHA256)
        return isRunning(reply, identity: identity) ? identity : nil
    }

    static func isRunning(_ reply: [String: Any], identity: Identity) -> Bool {
        guard integer(reply["apiVersion"]) == 1, reply["success"] as? Bool == true,
              integer(reply["generation"]) == identity.generation,
              let data = reply["data"] as? [String: Any] else { return false }
        return integer(data["apiVersion"]) == 1 && data["state"] as? String == "running" &&
            data["tunReady"] as? Bool == true && data["networkOwner"] as? String == "ios-packet-flow" &&
            data["sourceSHA256"] as? String == identity.sourceSHA256 &&
            data["coreVersion"] as? String == coreVersion && data["coreCommit"] as? String == coreCommit
    }

    struct PhysicalBinding: Equatable {
        let index: UInt32
        let name: String
        let supportsIPv4: Bool
        let supportsIPv6: Bool
    }

    struct PhysicalPathState {
        private var hasBound = false
        private var current: PhysicalBinding?
        mutating func update(_ next: PhysicalBinding?) -> Bool {
            let changed = hasBound && current != next
            if next != nil { hasBound = true }
            current = next
            return changed
        }
    }
}
