import Foundation

/// Counts real worker lifetimes, including queued work and native cleanup.
/// Cancellation never pretends a blocked worker has already retired.
final class NimboWorkerActivity: @unchecked Sendable {
    private let lock = NSLock()
    private var active = 0

    func begin() { lock.lock(); active += 1; lock.unlock() }
    func finish() {
        lock.lock()
        precondition(active > 0)
        active -= 1
        lock.unlock()
    }
    var isIdle: Bool { lock.lock(); defer { lock.unlock() }; return active == 0 }

    func waitUntilIdle(timeout: TimeInterval) async -> Bool {
        guard timeout.isFinite, timeout >= 0 else { return false }
        let deadline = ProcessInfo.processInfo.systemUptime + timeout
        while !isIdle {
            let remaining = deadline - ProcessInfo.processInfo.systemUptime
            guard remaining > 0, !Task.isCancelled else { return false }
            do { try await Task.sleep(nanoseconds: UInt64(min(0.02, remaining) * 1_000_000_000)) }
            catch { return false }
        }
        return !Task.isCancelled
    }
}
