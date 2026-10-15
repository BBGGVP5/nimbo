import Foundation

/// One async import/migration owns the profile through network and commit work.
/// Cancelling a lease does not release ownership until its caller has returned.
final class NimboSubscriptionOperationGate: @unchecked Sendable {
    enum GateError: LocalizedError {
        case busy
        var errorDescription: String? {
            "Дождитесь завершения обновления подписки (IOS_SUBSCRIPTION_BUSY)."
        }
    }

    private let lock = NSLock()
    private var owner: NimboVpnCommandLease?

    var isWorking: Bool {
        lock.lock()
        defer { lock.unlock() }
        return owner != nil
    }

    func begin() throws -> NimboVpnCommandLease {
        lock.lock()
        defer { lock.unlock() }
        guard owner == nil else { throw GateError.busy }
        let lease = NimboVpnCommandLease()
        owner = lease
        return lease
    }

    func finish(_ lease: NimboVpnCommandLease) {
        lock.lock()
        if owner === lease { owner = nil }
        lock.unlock()
    }
}
