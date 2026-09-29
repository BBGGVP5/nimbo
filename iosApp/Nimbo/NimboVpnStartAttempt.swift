/// Pure start-attempt bookkeeping; intentionally independent of NetworkExtension.
/// A successful start request does not mean that NE has published a new status yet.
struct NimboVpnStartAttempt {
    enum Phase: Equatable {
        case inactive, preparing, awaitingStatus, starting, reportingFailure
    }

    enum DisconnectedAction: Equatable {
        case waitForStart, reportFailure, preserveFailure, idle
    }

    private(set) var generation: UInt64 = 0
    private(set) var phase: Phase = .inactive

    var isPreparing: Bool { phase == .preparing }
    var isPending: Bool {
        phase == .preparing || phase == .awaitingStatus || phase == .starting
    }

    mutating func begin() -> UInt64 {
        generation &+= 1
        phase = .preparing
        return generation
    }

    func isCurrent(_ token: UInt64) -> Bool {
        generation == token && phase != .inactive
    }

    mutating func requestedStart(for token: UInt64) {
        guard isCurrent(token), phase == .preparing else { return }
        phase = .awaitingStatus
    }

    mutating func observedProgress() {
        guard phase == .awaitingStatus || phase == .starting || phase == .reportingFailure else { return }
        // A new transition invalidates callbacks from an earlier drop, even if
        // the same system connection recovers without reaching connected first.
        if phase == .reportingFailure { generation &+= 1 }
        phase = .starting
    }

    mutating func disconnectedAction() -> DisconnectedAction {
        switch phase {
        case .preparing, .awaitingStatus:
            return .waitForStart
        case .starting:
            phase = .reportingFailure
            return .reportFailure
        case .reportingFailure:
            return .preserveFailure
        case .inactive:
            return .idle
        }
    }

    func acceptsDisconnectError(for token: UInt64) -> Bool {
        generation == token && phase == .reportingFailure
    }

    mutating func invalidate() {
        generation &+= 1
        phase = .inactive
    }
}
