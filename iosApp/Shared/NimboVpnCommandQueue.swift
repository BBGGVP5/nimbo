import Foundation

/// Revokes work which has not entered a synchronous system call yet.
/// Never hold this lock while running IPC: cancellation must stay responsive.
final class NimboVpnCommandLease: @unchecked Sendable {
    private let lock = NSLock()
    private var active = true

    var isActive: Bool {
        lock.lock()
        defer { lock.unlock() }
        return active
    }

    func invalidate() {
        lock.lock()
        active = false
        lock.unlock()
    }
}

/// A blocked IPC keeps command ownership until it returns. UI tasks suspend
/// without blocking MainActor, and a queued stop follows the actual start.
final class NimboVpnCommandQueue: @unchecked Sendable {
    private let queue: DispatchQueue

    init(label: String) {
        queue = DispatchQueue(label: label, qos: .userInitiated)
    }

    func perform<T: Sendable>(lease: NimboVpnCommandLease? = nil,
                             _ work: @escaping @Sendable () throws -> T) async throws -> T {
        try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                queue.async {
                    guard lease?.isActive != false else {
                        continuation.resume(throwing: CancellationError())
                        return
                    }
                    do { continuation.resume(returning: try work()) }
                    catch { continuation.resume(throwing: error) }
                }
            }
        }, onCancel: { lease?.invalidate() })
    }
}
