import Foundation

@main enum WorkerActivityTests {
    @MainActor static func main() async throws {
        let worker = NimboWorkerActivity()
        precondition(worker.isIdle)
        worker.begin(); worker.begin()
        worker.finish()
        precondition(!worker.isIdle)
        var uiTicked = false
        let wait = Task { @MainActor in await worker.waitUntilIdle(timeout: 0.08) }
        let tick = Task { @MainActor in uiTicked = true }
        await tick.value
        let timedOut = await wait.value
        precondition(uiTicked && !timedOut && !worker.isIdle)
        worker.finish()
        let retired = await worker.waitUntilIdle(timeout: 0.01)
        precondition(retired)
        worker.begin()
        let cancelled = Task { await worker.waitUntilIdle(timeout: 5) }
        cancelled.cancel()
        let cancelledResult = await cancelled.value
        precondition(!cancelledResult && !worker.isIdle)
        worker.finish()
        print("PASS: bounded retirement, responsive MainActor and no fake idle after cancellation")
    }
}
