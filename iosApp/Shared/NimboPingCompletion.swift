import Foundation

/// Cancellation can arrive before a callback is installed. Preserve that result,
/// resume exactly once, and invoke callbacks outside the lock.
final class NimboPingCompletion<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var result: Value?
    private var completed = false
    private var callback: ((Value) -> Void)?

    var isFinished: Bool {
        lock.lock()
        defer { lock.unlock() }
        return completed
    }

    func install(_ callback: @escaping (Value) -> Void) {
        lock.lock()
        if completed {
            let value = result!
            lock.unlock()
            callback(value)
        } else {
            self.callback = callback
            lock.unlock()
        }
    }

    func finish(_ value: Value) {
        lock.lock()
        guard !completed else { lock.unlock(); return }
        completed = true
        result = value
        let callback = self.callback
        self.callback = nil
        lock.unlock()
        callback?(value)
    }
}
