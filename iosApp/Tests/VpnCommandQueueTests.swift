import Foundation

@main enum VpnCommandQueueTests {
    enum TestError: Error { case expected }

    @MainActor static func main() async throws {
        try await blockedCommandLeavesMainActorResponsive()
        try await cancelledQueuedStartDoesNotRun()
        try await cancellationDoesNotReleaseInFlightOwnership()
        try await errorsReachTheCaller()
        print("PASS: VPN command queue responsiveness, queued cancellation, in-flight ownership, stop ordering and errors")
    }

    static func waitOffMain(_ semaphore: DispatchSemaphore) async {
        let result = await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                continuation.resume(returning: semaphore.wait(timeout: .now() + 3))
            }
        }
        precondition(result == .success)
    }

    @MainActor static func blockedCommandLeavesMainActorResponsive() async throws {
        let queue = NimboVpnCommandQueue(label: "test.vpn.blocked")
        let entered = DispatchSemaphore(value: 0), release = DispatchSemaphore(value: 0)
        let task = Task { try await queue.perform {
            precondition(!Thread.isMainThread)
            entered.signal()
            precondition(release.wait(timeout: .now() + 3) == .success)
            return 42
        } }
        await waitOffMain(entered)
        var uiTicks = 0
        for _ in 0..<5 {
            try await Task.sleep(nanoseconds: 10_000_000)
            uiTicks += 1
        }
        precondition(uiTicks == 5) // Must execute while the system command is blocked.
        release.signal()
        let value = try await task.value
        precondition(value == 42)
    }

    @MainActor static func cancelledQueuedStartDoesNotRun() async throws {
        let queue = NimboVpnCommandQueue(label: "test.vpn.queued")
        let entered = DispatchSemaphore(value: 0), release = DispatchSemaphore(value: 0)
        let first = Task { try await queue.perform {
            entered.signal()
            precondition(release.wait(timeout: .now() + 3) == .success)
        } }
        await waitOffMain(entered)
        let lease = NimboVpnCommandLease()
        let forbiddenStart: @Sendable () throws -> Void = {
            preconditionFailure("Cancelled start executed")
        }
        let start = Task { try await queue.perform(lease: lease, forbiddenStart) }
        await Task.yield()
        lease.invalidate()
        release.signal()
        try await first.value
        do { _ = try await start.value; preconditionFailure("Cancelled start succeeded") }
        catch is CancellationError {}
    }

    @MainActor static func cancellationDoesNotReleaseInFlightOwnership() async throws {
        let queue = NimboVpnCommandQueue(label: "test.vpn.in-flight")
        let entered = DispatchSemaphore(value: 0), release = DispatchSemaphore(value: 0)
        let stopExecuted = DispatchSemaphore(value: 0)
        let lease = NimboVpnCommandLease()
        let start = Task { try await queue.perform(lease: lease) {
            entered.signal()
            precondition(release.wait(timeout: .now() + 3) == .success)
        } }
        await waitOffMain(entered)
        start.cancel()
        precondition(!lease.isActive) // Tiny invalidation lock must not wait for IPC.
        let stop = Task { try await queue.perform { stopExecuted.signal() } }
        stop.cancel() // Cleanup still must execute, regardless of caller cancellation.
        try await Task.sleep(nanoseconds: 30_000_000)
        precondition(stopExecuted.wait(timeout: .now()) == .timedOut)
        release.signal()
        try await start.value
        _ = try await stop.value
        precondition(stopExecuted.wait(timeout: .now()) == .success)
    }

    static func errorsReachTheCaller() async throws {
        let queue = NimboVpnCommandQueue(label: "test.vpn.errors")
        do {
            let _: Void = try await queue.perform { throw TestError.expected }
            preconditionFailure("Lost command error")
        } catch TestError.expected {}
    }
}
