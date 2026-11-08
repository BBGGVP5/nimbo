import Foundation

/// Read-only app-side work only. Never put a late NE save/start in this worker:
/// callers can time out before native returns, but native ownership stays held.
final class NimboCoreValidationWorker: @unchecked Sendable {
    enum WorkerError: LocalizedError {
        case busy, timedOut
        var errorDescription: String? {
            switch self {
            case .busy: return "Предыдущая проверка профиля ещё завершается. Повторите позже (IOS_CORE_VALIDATION_BUSY)."
            case .timedOut: return "Проверка профиля не завершилась за отведённое время. Интерфейс доступен; дождитесь завершения или перезапустите приложение (IOS_CORE_VALIDATION_TIMEOUT)."
            }
        }
    }

    private let queue: DispatchQueue
    private let lock = NSLock()
    private var owner: UUID?
    private var cancelWaiter: (@Sendable () -> Void)?

    init(label: String) {
        queue = DispatchQueue(label: label, qos: .userInitiated, autoreleaseFrequency: .workItem)
    }

    func cancelCurrent() {
        lock.lock()
        let cancel = cancelWaiter
        lock.unlock()
        cancel?() // A continuation callback must never execute under the ownership lock.
    }

    private func acquire(_ id: UUID, cancel: @escaping @Sendable () -> Void) throws {
        lock.lock(); defer { lock.unlock() }
        guard owner == nil else { throw WorkerError.busy }
        owner = id; cancelWaiter = cancel
    }

    private func retire(_ id: UUID) {
        lock.lock(); defer { lock.unlock() }
        guard owner == id else { return }
        owner = nil; cancelWaiter = nil
    }

    func perform<T: Sendable>(timeout: TimeInterval = 12,
                             _ work: @escaping @Sendable () throws -> T) async throws -> T {
        try Task.checkCancellation()
        guard timeout.isFinite, timeout > 0 else { throw WorkerError.timedOut }
        let id = UUID()
        let completion = NimboPingCompletion<Result<T, Error>>()
        try acquire(id) { completion.finish(.failure(CancellationError())) }
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                completion.install { continuation.resume(with: $0) }
                let timer = DispatchWorkItem { completion.finish(.failure(WorkerError.timedOut)) }
                DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + timeout, execute: timer)
                queue.async {
                    let result: Result<T, Error>
                    if completion.isFinished { result = .failure(CancellationError()) }
                    else {
                        do { result = .success(try work()) }
                        catch { result = .failure(error) }
                    }
                    // Work's own defers/native cleanup have returned. Release before
                    // success publication so a legitimate next request is not busy.
                    self.retire(id)
                    timer.cancel()
                    completion.finish(result) // Ignored after timeout/cancel: no late success.
                }
            }
        }, onCancel: { completion.finish(.failure(CancellationError())) })
    }
}
