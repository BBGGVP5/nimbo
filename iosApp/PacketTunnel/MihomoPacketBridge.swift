import Darwin
import Foundation
import CryptoKit
import LibXray
import Network
import NetworkExtension

enum MihomoPacketError: LocalizedError {
    case native(String), physicalPath, packetFlow, configuration
    var errorDescription: String? {
        switch self {
        case .native(let code):
            if code == "AD_BLOCKING_REQUIRES_RULE_MODE" {
                return NimboAdBlockingError.requiresMihomoRuleMode.localizedDescription
            }
            return "Mihomo: \(code)"
        case .physicalPath: return "Нет физического сетевого интерфейса (IOS_MIHOMO_PHYSICAL_PATH)."
        case .packetFlow: return "Пакетный поток Mihomo остановлен (IOS_MIHOMO_PACKET_FLOW)."
        case .configuration: return "Некорректная полная конфигурация Mihomo (IOS_MIHOMO_CONFIG)."
        }
    }
}

/// The socket callback only consults this lock and sets the Darwin interface
/// scope. It never calls Invoke, waits on lifecycleQueue or retains the FD.
final class MihomoPhysicalBinding {
    private let lock = NSLock()
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.nimbo.mihomo.physical")
    private var index: UInt32 = 0
    private var name = ""
    private var stopped = false
    private var pathState = NimboMihomoSessionPolicy.PhysicalPathState()

    init(onChange: @escaping (MihomoPhysicalBinding) -> Void) {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            // No Internet reachability gate. A restricted cellular path still
            // supplies an interface and can dial a whitelist server.
            let physical = path.availableInterfaces.filter {
                [.wifi, .cellular, .wiredEthernet].contains($0.type)
                    && !$0.name.hasPrefix("utun") && $0.name != "lo0"
            }
            let candidate = physical.first(where: { path.usesInterfaceType($0.type) }) ?? physical.first
            let next = candidate.map { if_nametoindex($0.name) } ?? 0
            let physicalPath: NimboMihomoSessionPolicy.PhysicalBinding? = next == 0 ? nil : .init(
                index: next, name: candidate?.name ?? "", supportsIPv4: path.supportsIPv4,
                supportsIPv6: path.supportsIPv6)
            self.lock.lock()
            let changed = !self.stopped && self.pathState.update(physicalPath)
            if !self.stopped { self.index = next; self.name = candidate?.name ?? "" }
            self.lock.unlock()
            if changed { onChange(self) }
        }
        monitor.start(queue: queue)
    }

    var isReady: Bool { lock.lock(); defer { lock.unlock() }; return !stopped && index != 0 }

    func protect(_ fd: Int64) -> Bool {
        guard fd >= 0, fd <= Int64(Int32.max) else { return false }
        lock.lock(); defer { lock.unlock() }
        guard !stopped, index != 0, !name.hasPrefix("utun") else { return false }
        var address = sockaddr_storage()
        var size = socklen_t(MemoryLayout<sockaddr_storage>.size)
        let result = withUnsafeMutablePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { getsockname(Int32(fd), $0, &size) }
        }
        guard result == 0 else { return false }
        var scope = index
        let bytes = socklen_t(MemoryLayout<UInt32>.size)
        switch Int32(address.ss_family) {
        case AF_INET: return setsockopt(Int32(fd), IPPROTO_IP, IP_BOUND_IF, &scope, bytes) == 0
        case AF_INET6: return setsockopt(Int32(fd), IPPROTO_IPV6, IPV6_BOUND_IF, &scope, bytes) == 0
        default: return false
        }
    }

    func close() {
        lock.lock(); stopped = true; index = 0; lock.unlock()
        monitor.cancel()
    }
}

/// One Go runtime, real native packet stack. Lifecycle methods are called only
/// from the provider's lifecycleQueue; pump callbacks own their separate lock.
final class MihomoPacketBridge {
    private let lock = NSLock()
    private let inputQueue = DispatchQueue(label: "com.nimbo.mihomo.packet.input")
    private let outputQueue = DispatchQueue(label: "com.nimbo.mihomo.packet.output")
    private var generation: UInt64?
    private var pendingStartID: String?
    private var pendingProbeID: String?
    private var sourceSHA256: String?
    private var readOutstanding = false
    private var flow: NEPacketTunnelFlow?
    private var binding: MihomoPhysicalBinding?
    private var protectorInstalled = false
    private var received: UInt64 = 0
    private var sent: UInt64 = 0
    var onFailure: (() -> Void)?

    var isRunning: Bool {
        guard let identity = currentIdentity, let status = try? command("status"),
              currentIdentity == identity else { return false }
        return NimboMihomoSessionPolicy.isRunning(status, identity: identity)
    }
    var isConfigured: Bool { currentGeneration != nil }
    private var currentGeneration: UInt64? { lock.lock(); defer { lock.unlock() }; return generation }
    private var currentIdentity: NimboMihomoSessionPolicy.Identity? {
        lock.lock(); defer { lock.unlock() }
        guard let generation, let sourceSHA256 else { return nil }
        return .init(generation: generation, sourceSHA256: sourceSHA256)
    }
    private var currentBinding: MihomoPhysicalBinding? {
        lock.lock(); defer { lock.unlock() }; return binding
    }
    var counters: (received: UInt64, sent: UInt64) {
        lock.lock(); defer { lock.unlock() }; return (received, sent)
    }

    /// Start binding before route installation so the physical path is observed
    /// before NE's utun can become the default route. No DNS/HTTP availability test.
    func prepareBinding(onChange: @escaping () -> Void) -> MihomoPhysicalBinding {
        let candidate = MihomoPhysicalBinding { [weak self] binding in
            // Bypass the lifecycle queue's blocking probe. A callback belonging
            // to a retired physical binder cannot cancel a newer session's probe.
            self?.cancelLiveProbe(ifBoundTo: binding)
            onChange()
        }
        // Provider owns this object from here, including cancellation cleanup.
        lock.lock(); binding = candidate; lock.unlock()
        return candidate
    }

    func start(source: Data, directory: URL, flow: NEPacketTunnelFlow, selections: [String: String], adBlocking: Bool = false) throws {
        guard currentGeneration == nil, let binding = currentBinding, binding.isReady,
              let text = NimboMihomoSessionPolicy.exactUTF8(source) else {
            throw MihomoPacketError.configuration
        }
        let context = Unmanaged.passUnretained(binding).toOpaque()
        _ = try decode(NimboMihomoSetSocketProtectorV1({ fd, context in
            guard let context else { return 0 }
            return Unmanaged<MihomoPhysicalBinding>.fromOpaque(context).takeUnretainedValue().protect(fd) ? 1 : 0
        }, context))
        protectorInstalled = true
        do {
            let requestID = UUID().uuidString
            lock.lock(); pendingStartID = requestID; lock.unlock()
            defer { lock.lock(); pendingStartID = nil; lock.unlock() }
            var options: [String: Any] = ["dataDir": directory.path, "networkOwner": "ios-packet-flow",
                "packetIPv6": true, "startupTimeoutMs": 20_000]
            if adBlocking { options["adBlocking"] = true }
            let request: [String: Any] = ["apiVersion": 1, "requestId": requestID, "operation": "start",
                "yaml": text, "options": options]
            let json = String(decoding: try JSONSerialization.data(withJSONObject: request), as: UTF8.self)
            let reply = try json.withCString { try decode(NimboMihomoStartIOSPacketFlowV1(UnsafeMutablePointer(mutating: $0))) }
            let expectedSource = SHA256.hash(data: source).map { String(format: "%02x", $0) }.joined()
            guard let identity = NimboMihomoSessionPolicy.readyIdentity(reply, requestID: requestID, sourceSHA256: expectedSource) else {
                throw MihomoPacketError.packetFlow
            }
            let nativeGeneration = identity.generation
            lock.lock(); generation = nativeGeneration; sourceSHA256 = identity.sourceSHA256; self.flow = flow; received = 0; sent = 0; lock.unlock()
            for (group, member) in selections.sorted(by: { $0.key < $1.key }) {
                _ = try command("select", fields: ["group": group, "name": member])
            }
            readInput()
            outputQueue.async { [weak self] in self?.readOutput(generation: nativeGeneration) }
        } catch { stop(); throw error }
    }

    func command(_ operation: String, fields: [String: Any] = [:], requestID: String = UUID().uuidString) throws -> [String: Any] {
        let isProbe = operation == "delay" || operation == "nimboDelay"
        if isProbe { lock.lock(); pendingProbeID = requestID; lock.unlock() }
        defer {
            if isProbe {
                lock.lock()
                if pendingProbeID == requestID { pendingProbeID = nil }
                lock.unlock()
            }
        }
        var request = fields
        request["apiVersion"] = 1; request["requestId"] = requestID; request["operation"] = operation
        let identity = currentIdentity
        if let identity { request["generation"] = identity.generation }
        let json = String(decoding: try JSONSerialization.data(withJSONObject: request), as: UTF8.self)
        let reply = try json.withCString { try decode(NimboMihomoInvokeV1(UnsafeMutablePointer(mutating: $0))) }
        guard NimboMihomoSessionPolicy.matchesEnvelope(reply, requestID: requestID, identity: identity),
              identity == nil || currentIdentity == identity else {
            throw MihomoPacketError.native("STALE_GENERATION")
        }
        return reply
    }

    /// Cancellation must not queue behind the operation it interrupts. Bind it
    /// to both immutable source and runtime generation under the pump lock.
    func cancelProbe(_ requestID: String, sourceHash: String) -> Bool {
        guard UUID(uuidString: requestID) != nil else { return false }
        lock.lock()
        guard let ticket = generation, sourceSHA256 == sourceHash else { lock.unlock(); return false }
        lock.unlock()
        return cancelNativeProbe(requestID, identity: .init(generation: ticket, sourceSHA256: sourceHash))
    }

    private func cancelNativeProbe(_ targetID: String, identity: NimboMihomoSessionPolicy.Identity) -> Bool {
        let requestID = UUID().uuidString
        let request: [String: Any] = ["apiVersion": 1, "requestId": requestID,
            "operation": "cancel", "generation": identity.generation, "targetRequestId": targetID]
        guard let data = try? JSONSerialization.data(withJSONObject: request) else { return false }
        let json = String(decoding: data, as: UTF8.self)
        guard let reply = try? json.withCString({ try decode(NimboMihomoInvokeV1(UnsafeMutablePointer(mutating: $0))) }) else { return false }
        return NimboMihomoSessionPolicy.matchesEnvelope(reply, requestID: requestID, identity: identity)
    }

    /// Safe outside lifecycleQueue: stop cancels startup and a live delay before
    /// waiting for serialized teardown; cancellation bypasses native operation lock.
    func cancelPendingStart() {
        lock.lock(); let startID = pendingStartID; let probeID = pendingProbeID; lock.unlock()
        for requestID in [startID, probeID].compactMap({ $0 }) {
            _ = try? command("cancel", fields: ["targetRequestId": requestID])
        }
    }

    func cancelLiveProbe(ifBoundTo candidate: MihomoPhysicalBinding? = nil) {
        lock.lock()
        guard candidate == nil || binding === candidate else { lock.unlock(); return }
        let probe = pendingProbeID; let source = sourceSHA256; let ticket = generation
        lock.unlock()
        if let probe, let source, let ticket {
            _ = cancelNativeProbe(probe, identity: .init(generation: ticket, sourceSHA256: source))
        }
    }

    private func retireBinding() {
        lock.lock(); let old = binding; binding = nil; lock.unlock()
        old?.close()
    }

    func stop() {
        lock.lock(); generation = nil; sourceSHA256 = nil; flow = nil; lock.unlock()
        // Native stop cancels a blocking output read before joining this worker.
        _ = try? command("stop")
        outputQueue.sync {}
        if protectorInstalled {
            if (try? decode(NimboMihomoSetSocketProtectorV1(nil, nil))) != nil {
                protectorInstalled = false
                retireBinding()
            } // Retain context if unregister failed; never free a live callback.
        } else { retireBinding() }
    }

    private func readInput() {
        lock.lock()
        guard generation != nil, !readOutstanding, let flow else { lock.unlock(); return }
        readOutstanding = true
        let ticket = generation
        lock.unlock()
        flow.readPackets { [weak self] packets, families in
            guard let self else { return }
            self.inputQueue.async {
                self.lock.lock()
                let valid = self.generation == ticket && self.generation != nil
                self.lock.unlock()
                if valid, let ticket, packets.count == families.count {
                    for (packet, family) in zip(packets, families) {
                        guard packet.count >= 20, packet.count <= 1500, let first = packet.first,
                              (first >> 4 == 4 && family.int32Value == AF_INET) ||
                              (first >> 4 == 6 && family.int32Value == AF_INET6) else { continue }
                        let result = packet.withUnsafeBytes {
                            NimboMihomoWriteIOSPacketV1(ticket, UnsafeMutableRawPointer(mutating: $0.baseAddress), Int32(packet.count))
                        }
                        if result == -1 { break }
                        if result == 1 { self.lock.lock(); self.sent &+= UInt64(packet.count); self.lock.unlock() }
                    }
                }
                // A late old callback does not submit old packets, but releases
                // the single read ticket so a newly started owner can rearm.
                self.lock.lock(); self.readOutstanding = false; self.lock.unlock()
                self.readInput()
            }
        }
    }

    private func readOutput(generation ticket: UInt64) {
        var bytes = [UInt8](repeating: 0, count: 1500)
        while currentGeneration == ticket {
            var packets: [Data] = []; var families: [NSNumber] = []
            for index in 0..<32 {
                let count = bytes.withUnsafeMutableBytes {
                    NimboMihomoReadIOSPacketV1(ticket, $0.baseAddress, 1500, index == 0 ? 1000 : 1)
                }
                if count == 0 { break }
                if count < 0 {
                    if currentGeneration == ticket { onFailure?() }
                    return
                }
                packets.append(Data(bytes.prefix(Int(count))))
                families.append(NSNumber(value: bytes[0] >> 4 == 6 ? AF_INET6 : AF_INET))
            }
            lock.lock()
            guard generation == ticket, let flow else { lock.unlock(); return }
            let accepted = packets.isEmpty || flow.writePackets(packets, withProtocols: families)
            if accepted { for packet in packets { received &+= UInt64(packet.count) } }
            lock.unlock()
            if !accepted { onFailure?(); return }
        }
    }

    private func decode(_ pointer: UnsafeMutablePointer<CChar>?) throws -> [String: Any] {
        guard let pointer else { throw MihomoPacketError.native("NULL_RESPONSE") }
        defer { NimboMihomoFreeV1(pointer) }
        let data = Data(String(cString: pointer).utf8)
        guard let reply = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw MihomoPacketError.native("INVALID_RESPONSE")
        }
        guard reply["success"] as? Bool == true else {
            throw MihomoPacketError.native((reply["error"] as? [String: Any])?["code"] as? String ?? "FAILED")
        }
        return reply
    }
}
