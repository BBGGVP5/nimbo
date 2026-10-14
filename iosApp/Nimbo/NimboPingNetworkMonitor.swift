import Foundation
import Network

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

    func start() {
        lock.lock()
        guard !started else { lock.unlock(); return }
        started = true
        lock.unlock()
        // This handler runs on one serial queue; the first event establishes a baseline.
        var lastSignature: String?
        monitor.pathUpdateHandler = { path in
            let interfaces = path.availableInterfaces.filter { path.usesInterfaceType($0.type) }
                .map { "\($0.index):\($0.name)" }.sorted().joined(separator: ",")
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
