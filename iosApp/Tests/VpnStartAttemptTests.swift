// Portable Swift logic tests: no SwiftUI, Apple SDK, NetworkExtension or signing.
// swiftc iosApp/Nimbo/NimboVpnStartAttempt.swift iosApp/Tests/VpnStartAttemptTests.swift -o vpn-start-tests
// ./vpn-start-tests
@main
enum VpnStartAttemptTests {
    static func main() {
        staleDisconnectedDoesNotMeanFailure()
        observedStartupFailureIsReportedOnce()
        successInvalidatesLateError()
        cancelAndRetryInvalidateOldWork()
        recoveredTransitionInvalidatesOldError()
        preflightStatusDoesNotPretendStartWasRequested()
        print("PASS: 6 VPN startup policy regression scenarios")
    }

    static func staleDisconnectedDoesNotMeanFailure() {
        var attempt = NimboVpnStartAttempt()
        let id = attempt.begin()
        precondition(attempt.isPreparing)
        precondition(attempt.disconnectedAction() == .waitForStart)
        attempt.requestedStart(for: id)
        for _ in 0..<60 {
            precondition(attempt.disconnectedAction() == .waitForStart)
            precondition(attempt.isPending)
            precondition(!attempt.acceptsDisconnectError(for: id))
        }
        // Controller's existing 30s watchdog invalidates this still-pending start.
        attempt.invalidate()
        precondition(!attempt.isPending)
        precondition(attempt.disconnectedAction() == .idle)
    }

    static func observedStartupFailureIsReportedOnce() {
        var attempt = NimboVpnStartAttempt()
        let id = attempt.begin()
        attempt.requestedStart(for: id)
        attempt.observedProgress() // NE connecting/reasserting, then disconnecting.
        attempt.observedProgress()
        precondition(attempt.disconnectedAction() == .reportFailure)
        precondition(attempt.acceptsDisconnectError(for: id))
        precondition(attempt.disconnectedAction() == .preserveFailure)
        precondition(!attempt.isPending)
    }

    static func successInvalidatesLateError() {
        var attempt = NimboVpnStartAttempt()
        let id = attempt.begin()
        attempt.requestedStart(for: id)
        attempt.observedProgress()
        precondition(attempt.disconnectedAction() == .reportFailure)
        attempt.invalidate() // NE connected before fetchLastDisconnectError callback.
        precondition(!attempt.acceptsDisconnectError(for: id))
        precondition(!attempt.isCurrent(id))
    }

    static func cancelAndRetryInvalidateOldWork() {
        var attempt = NimboVpnStartAttempt()
        let cancelled = attempt.begin()
        attempt.invalidate() // User disconnects while staging awaits preferences.
        attempt.requestedStart(for: cancelled)
        precondition(attempt.phase == .inactive)
        precondition(!attempt.isCurrent(cancelled))
        let retry = attempt.begin()
        precondition(retry != cancelled)
        attempt.requestedStart(for: retry)
        attempt.observedProgress()
        precondition(attempt.disconnectedAction() == .reportFailure)
        precondition(!attempt.acceptsDisconnectError(for: cancelled))
        precondition(attempt.acceptsDisconnectError(for: retry))
    }

    static func recoveredTransitionInvalidatesOldError() {
        var attempt = NimboVpnStartAttempt()
        let id = attempt.begin()
        attempt.requestedStart(for: id)
        attempt.observedProgress()
        precondition(attempt.disconnectedAction() == .reportFailure)
        attempt.observedProgress() // NE reconnects before the earlier error arrives.
        precondition(!attempt.acceptsDisconnectError(for: id))
        precondition(attempt.disconnectedAction() == .reportFailure)
        precondition(!attempt.acceptsDisconnectError(for: id))
        precondition(attempt.acceptsDisconnectError(for: attempt.generation))
    }

    static func preflightStatusDoesNotPretendStartWasRequested() {
        var attempt = NimboVpnStartAttempt()
        let id = attempt.begin()
        attempt.observedProgress() // A notification while staging the manager.
        precondition(attempt.isPreparing)
        precondition(attempt.disconnectedAction() == .waitForStart)
        attempt.requestedStart(for: id)
        attempt.invalidate() // The synchronous start API throws a genuine error.
        precondition(!attempt.isPending)
        precondition(!attempt.acceptsDisconnectError(for: id))
    }
}
