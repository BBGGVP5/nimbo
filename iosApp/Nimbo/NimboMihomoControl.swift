import Foundation
import LibXray
import NetworkExtension

enum NimboMihomoControl {
    typealias Group = NimboMihomoGroup
    struct DeclaredSnapshot: Sendable {
        let full: NimboFullConfiguration
        let storeRevision: UInt64
        let groups: [Group]
        let types: [String: String]
    }
    private static let inspectionWorker = NimboCoreValidationWorker(label: "com.nimbo.mihomo.inspection")
    private static let selectionQueue = NimboVpnCommandQueue(label: "com.nimbo.mihomo.selection")
    private static let inspectionCache: NSCache<NSString, NSDictionary> = {
        let cache = NSCache<NSString, NSDictionary>()
        cache.countLimit = 2
        cache.totalCostLimit = 12 * 1_024 * 1_024
        return cache
    }()

    static func cachedInspection(_ full: NimboFullConfiguration) -> [String: Any]? {
        guard let payload = inspectionCache.object(forKey: full.sourceSHA256 as NSString) as? [String: Any],
              payload["originalYAML"] as? String == full.originalYAML else { return nil }
        return payload
    }

    static func looksLikeConfiguration(_ data: Data) -> Bool {
        guard let text = String(data: data, encoding: .utf8) else { return false }
        if let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
            return object["coreId"] as? String == "mihomo" || object["proxies"] != nil || object["proxy-groups"] != nil || object["originalYAML"] != nil
        }
        return text.split(whereSeparator: \.isNewline).contains {
            let line = $0.trimmingCharacters(in: .whitespacesAndNewlines)
            return ["proxies:", "proxy-groups:", "proxy-providers:", "rule-providers:"].contains { line.hasPrefix($0) }
        }
    }

    static func preflight(_ source: Data) throws {
        let response = try native("preflightIOSPacketFlow", source: source)
        guard (response["data"] as? [String: Any])?["compiled"] as? Bool == true else {
            throw NimboCoreSelectionError.unavailable
        }
    }

    static func inspection(_ full: NimboFullConfiguration) throws -> [String: Any] {
        if let cached = cachedInspection(full) { return cached }
        let response = try native("inspect", source: full.sourceData)
        guard let payload = response["data"] as? [String: Any],
              payload["sourceSHA256"] as? String == full.sourceSHA256,
              payload["originalYAML"] as? String == full.originalYAML else {
            throw NimboFullConfigurationError.inspectionFailed
        }
        inspectionCache.setObject(payload as NSDictionary, forKey: full.sourceSHA256 as NSString,
            cost: full.sourceData.count * 3)
        return payload
    }

    static func validateAdBlocking(_ full: NimboFullConfiguration, enabled: Bool) throws {
        guard enabled else { return }
        let graph = try inspection(full)["declaredGraph"] as? [String: Any]
        try NimboAdBlocking.requireMihomoRuleMode(graph?["mode"] as? String, enabled: enabled)
    }

    private static func declaredGroups(_ full: NimboFullConfiguration) throws -> [Group] {
        let graph = try inspection(full)["declaredGraph"] as? [String: Any]
        return NimboMihomoGroup.declared(graph?["groups"] as? [[String: Any]] ?? [], selections: full.groupSelections)
    }

    static func readDeclared(_ fallback: NimboFullConfiguration) async throws -> DeclaredSnapshot {
        try await inspectionWorker.perform {
            let store = NimboConfigurationStore.shared
            let revision = store.revision
            guard let full = try store.loadFullConfiguration(), full.sourceSHA256 == fallback.sourceSHA256 else {
                throw NimboFullConfigurationError.staleRefresh
            }
            let graph = try inspection(full)["declaredGraph"] as? [String: Any]
            let groups = try declaredGroups(full)
            var types: [String: String] = ["DIRECT": "Direct", "REJECT": "Reject"]
            for node in graph?["proxies"] as? [[String: Any]] ?? [] {
                if let name = node["name"] as? String, let type = node["type"] as? String { types[name] = type }
            }
            guard store.revision == revision else { throw NimboFullConfigurationError.staleRefresh }
            return DeclaredSnapshot(full: full, storeRevision: revision, groups: groups, types: types)
        }
    }

    static func liveGroups(_ full: NimboFullConfiguration, session: NETunnelProviderSession,
                           declaredOrder: [String]) async throws -> [Group] {
        let reply = try await rpc("mihomoSnapshot", full: full, session: session)
        let data = reply["data"] as? [String: Any]
        let groups = data?["groups"] as? [String: [String: Any]] ?? [:]
        return NimboMihomoGroup.live(groups, declaredOrder: declaredOrder)
    }

    @MainActor static func select(group: Group, member: String, full: NimboFullConfiguration,
                                 session: NETunnelProviderSession?, observedStatus: NEVPNStatus?) async throws {
        guard group.selectable, group.members.contains(member) else { throw NimboCoreSelectionError.incompatible }
        if let session, observedStatus == .connected {
            let reply = try await rpc("mihomoSelect", full: full, session: session, fields: ["group": group.name, "name": member])
            let groups = (reply["data"] as? [String: Any])?["groups"] as? [String: [String: Any]]
            guard groups?[group.name]?["now"] as? String == member else { throw NimboFullConfigurationError.staleRefresh }
        } else if session != nil && observedStatus != .disconnected && observedStatus != .invalid {
            throw NimboCoreSelectionError.busy
        }
        let lease = NimboVpnCommandLease()
        try await selectionQueue.perform(lease: lease) {
            guard let current = try NimboConfigurationStore.shared.loadFullConfiguration(), current.sourceSHA256 == full.sourceSHA256 else {
                throw NimboFullConfigurationError.staleRefresh
            }
            let updated = try current.recordingSelection(group: group.name, member: member, expectedSourceSHA256: full.sourceSHA256)
            guard lease.isActive else { throw CancellationError() }
            try NimboConfigurationStore.shared.saveFullConfiguration(updated, expected: current)
        }
    }

    private static func native(_ operation: String, source: Data) throws -> [String: Any] {
        guard let text = NimboMihomoSessionPolicy.exactUTF8(source) else { throw NimboFullConfigurationError.invalidEncoding }
        let requestID = UUID().uuidString
        let json = String(decoding: try JSONSerialization.data(withJSONObject: ["apiVersion": 1,
            "requestId": requestID, "operation": operation, "yaml": text]), as: UTF8.self)
        let data: Data = try json.withCString {
            guard let pointer = NimboMihomoInvokeV1(UnsafeMutablePointer(mutating: $0)) else { throw NimboFullConfigurationError.inspectionFailed }
            defer { NimboMihomoFreeV1(pointer) }
            return Data(String(cString: pointer).utf8)
        }
        guard let reply = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              reply["requestId"] as? String == requestID, reply["success"] as? Bool == true else {
            let reply = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            throw NSError(domain: "Nimbo.Mihomo", code: 1, userInfo: [NSLocalizedDescriptionKey:
                "Mihomo: " + ((reply?["error"] as? [String: Any])?["code"] as? String ?? "INSPECTION_FAILED")])
        }
        if operation == "inspect" {
            // Validate the typed wire envelope before exposing its graph. Restore
            // its exact source after Foundation's lossy NSString JSON projection.
            let identity = try NimboMihomoInspection.decode(data, requestID: requestID, sourceData: source)
            guard var payload = reply["data"] as? [String: Any] else {
                throw NimboFullConfigurationError.inspectionFailed
            }
            payload["originalYAML"] = identity.originalYAML
            var validated = reply
            validated["data"] = payload
            return validated
        }
        return reply
    }

    static func rpc(_ command: String, full: NimboFullConfiguration, session: NETunnelProviderSession,
                    fields: [String: Any] = [:]) async throws -> [String: Any] {
        let requestID = UUID().uuidString
        var request = fields; request["requestID"] = requestID; request["command"] = command; request["sourceSHA256"] = full.sourceSHA256
        let message = try JSONSerialization.data(withJSONObject: request)
        let completion = NimboPingCompletion<Result<Data, Error>>()
        let cancelNative: () -> Void = {
            guard command == "mihomoDelay", let data = try? JSONSerialization.data(withJSONObject:
                ["command": "cancelMihomoProbe", "requestID": requestID, "sourceSHA256": full.sourceSHA256]) else { return }
            try? session.sendProviderMessage(data, responseHandler: nil)
        }
        let response: Data = try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
                let timer = DispatchWorkItem { cancelNative(); completion.finish(.failure(NimboFullConfigurationError.staleRefresh)) }
                let observer = NotificationCenter.default.addObserver(forName: .NEVPNStatusDidChange, object: session, queue: nil) { _ in
                    completion.finish(.failure(NimboFullConfigurationError.staleRefresh))
                }
                completion.install { result in
                    timer.cancel(); NotificationCenter.default.removeObserver(observer)
                    continuation.resume(with: result)
                }
                guard !completion.isFinished, session.status == .connected else {
                    completion.finish(.failure(NimboCoreSelectionError.busy)); return
                }
                DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 12, execute: timer)
                do {
                    try session.sendProviderMessage(message) { data in
                        guard let data, session.status == .connected else {
                            completion.finish(.failure(NimboFullConfigurationError.staleRefresh)); return
                        }
                        completion.finish(.success(data))
                    }
                } catch { completion.finish(.failure(error)) }
            }
        }, onCancel: { cancelNative(); completion.finish(.failure(CancellationError())) })
        guard let outer = try JSONSerialization.jsonObject(with: response) as? [String: Any],
              outer["ok"] as? Bool == true, outer["sourceSHA256"] as? String == full.sourceSHA256,
              let reply = outer["reply"] as? [String: Any], reply["success"] as? Bool == true else {
            throw NimboFullConfigurationError.staleRefresh
        }
        return reply
    }
}
