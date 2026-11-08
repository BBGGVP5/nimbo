import Foundation

@main enum CoreValidationWorkerTests {
    enum Expected: Error { case failure }

    static func waitOffMain(_ semaphore: DispatchSemaphore) async {
        let result = await withCheckedContinuation { continuation in
            DispatchQueue.global().async { continuation.resume(returning: semaphore.wait(timeout: .now() + 3)) }
        }
        precondition(result == .success)
    }

    @MainActor static func main() async throws {
        try await mainActorRemainsResponsive()
        try await timeoutRetainsOwnership()
        try await cancelRejectsLateAdmission()
        try await queuedCancellationAndErrors()
        print("PASS: bounded core preparation, responsive MainActor, real ownership retirement, cancellation and no late admission")
    }

    @MainActor static func mainActorRemainsResponsive() async throws {
        let worker = NimboCoreValidationWorker(label: "test.core.ui")
        let entered = DispatchSemaphore(value: 0), release = DispatchSemaphore(value: 0)
        let task = Task { try await worker.perform {
            precondition(!Thread.isMainThread)
            entered.signal()
            precondition(release.wait(timeout: .now() + 3) == .success)
            return 7
        } }
        await waitOffMain(entered)
        var ticks = 0
        for _ in 0..<5 { try await Task.sleep(nanoseconds: 5_000_000); ticks += 1 }
        precondition(ticks == 5)
        release.signal()
        let value = try await task.value
        precondition(value == 7)
        // Retirement precedes success: sequential validation must never spuriously fail busy.
        for index in 0..<100 {
            let next = try await worker.perform { index }
            precondition(next == index)
        }
    }

    @MainActor static func timeoutRetainsOwnership() async throws {
        let worker = NimboCoreValidationWorker(label: "test.core.timeout")
        let entered = DispatchSemaphore(value: 0), release = DispatchSemaphore(value: 0)
        let task = Task { try await worker.perform(timeout: 0.05) {
            entered.signal()
            precondition(release.wait(timeout: .now() + 3) == .success)
            return true
        } }
        await waitOffMain(entered)
        do { _ = try await task.value; preconditionFailure("Timed-out preparation admitted") }
        catch NimboCoreValidationWorker.WorkerError.timedOut {}
        do { _ = try await worker.perform { true }; preconditionFailure("Timeout released native ownership") }
        catch NimboCoreValidationWorker.WorkerError.busy {}
        release.signal()
        try await eventuallyRetired(worker)
    }

    @MainActor static func cancelRejectsLateAdmission() async throws {
        let worker = NimboCoreValidationWorker(label: "test.core.cancel")
        let entered = DispatchSemaphore(value: 0), release = DispatchSemaphore(value: 0)
        var staged = false
        let task = Task {
            _ = try await worker.perform {
                entered.signal()
                precondition(release.wait(timeout: .now() + 3) == .success)
                return true
            }
            staged = true // Simulated caller-owned NE side effect, never native worker work.
        }
        await waitOffMain(entered)
        worker.cancelCurrent()
        do { try await task.value; preconditionFailure("Cancelled preparation admitted") }
        catch is CancellationError {}
        precondition(!staged)
        do { _ = try await worker.perform { true }; preconditionFailure("Cancellation released ownership") }
        catch NimboCoreValidationWorker.WorkerError.busy {}
        release.signal()
        try await eventuallyRetired(worker)
        precondition(!staged, "Late native return must not perform a system write")
    }

    @MainActor static func queuedCancellationAndErrors() async throws {
        let worker = NimboCoreValidationWorker(label: "test.core.errors")
        let task = Task {
            try Task.checkCancellation()
            _ = try await worker.perform { () -> Bool in preconditionFailure("Already cancelled work executed") }
        }
        task.cancel()
        do { try await task.value; preconditionFailure("Cancelled caller succeeded") }
        catch is CancellationError {}
        try await eventuallyRetired(worker)
        do { let _: Bool = try await worker.perform { throw Expected.failure }; preconditionFailure("Lost native error") }
        catch Expected.failure {}
    }

    static func eventuallyRetired(_ worker: NimboCoreValidationWorker) async throws {
        for _ in 0..<100 {
            do { _ = try await worker.perform { true }; return }
            catch NimboCoreValidationWorker.WorkerError.busy { try await Task.sleep(nanoseconds: 2_000_000) }
        }
        preconditionFailure("Native ownership did not retire")
    }
}
