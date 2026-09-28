import Foundation

/// Only the cancellation bit crosses queues. Every access is protected by lock.
final class NimboCameraRequest: @unchecked Sendable {
    private let lock = NSLock()
    private var cancelled = false

    var isCancelled: Bool {
        lock.lock()
        defer { lock.unlock() }
        return cancelled
    }

    func cancel() {
        lock.lock()
        defer { lock.unlock() }
        cancelled = true
    }
}

/// Value policy owned by the controller's MainActor; independently testable.
struct NimboCameraLifecycle {
    private var visible = false
    private var sceneActive = false
    private var finished = false
    private(set) var request: NimboCameraRequest?

    /// Returns only a newly admitted request, never one already in progress.
    mutating func update(visible: Bool? = nil, sceneActive: Bool? = nil) -> NimboCameraRequest? {
        if let visible { self.visible = visible }
        if let sceneActive { self.sceneActive = sceneActive }
        guard self.visible && self.sceneActive && !finished else {
            invalidate()
            return nil
        }
        guard request == nil else { return nil }
        let next = NimboCameraRequest()
        request = next
        return next
    }

    func accepts(_ candidate: NimboCameraRequest) -> Bool {
        visible && sceneActive && !finished && request === candidate && !candidate.isCancelled
    }

    mutating func finish() {
        finished = true
        invalidate()
    }

    private mutating func invalidate() {
        request?.cancel()
        request = nil
    }
}
