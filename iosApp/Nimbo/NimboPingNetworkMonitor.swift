import Foundation
import Network
import NetworkExtension

extension Notification.Name {
    static let nimboPingNetworkChanged = Notification.Name("com.nimbo.ping.network-changed")
}

/// No DNS override, socket binding or VPN lifecycle changes. This only retires
/// measurements when Network reports a different route/interface family.
final class NimboPingNetworkMonitor: @unchecked Sendable {
    static let shared = NimboPingNetworkMonitor()
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.nimbo.ping.network")
    private let lock = NSLock()
    private var started = false
    private var sessionObserver: NSObjectProtocol?

    func start() {
        lock.lock()
        guard !started else { lock.unlock(); return }
        started = true
        sessionObserver = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange, object: nil, queue: nil
        ) { _ in
            // Synchronous retirement also catches disconnect/reconnect to the same profile.
            NimboAutoPingCache.shared.invalidate()
        }
        lock.unlock()
        // This handler runs on one serial queue; the first event establishes a baseline.
        var lastSignature: String?
        monitor.pathUpdateHandler = { path in
            // Every callback retires proof, even an unchanged coarse signature:
            // NWPath cannot prove that two Wi-Fi paths are the same network.
            // The initial callback establishes readiness, never adopts old proof.
            NimboAutoPingCache.shared.invalidate(pathEstablished: true)
            let interfaces = path.availableInterfaces.filter { path.usesInterfaceType($0.type) }
                .map { "\($0.index):\($0.type)" }.sorted().joined(separator: ",")
            let signature = "\(path.status)|\(interfaces)|\(path.supportsIPv4)|\(path.supportsIPv6)|\(path.isExpensive)"
            let previous = lastSignature
            lastSignature = signature
            guard let previous, previous != signature else { return }
            DispatchQueue.main.async {
                NotificationCenter.default.post(name: .nimboPingNetworkChanged, object: nil)
            }
        }
        monitor.start(queue: queue)
    }
}
