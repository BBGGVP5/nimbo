import Foundation

struct NimboVpnObservedConnection: Sendable {
    let rawStatus: Int
    let connectedDate: Date?
}

/// At most one read per profile generation. Old reads cannot replace the new
/// manager/stop state, and regular polling never piles up preference reloads.
struct NimboVpnObservationGate {
    private var generation: UInt64 = 0
    private var inFlight: UInt64?
    private var lastStarted: TimeInterval?

    mutating func begin(now: TimeInterval, force: Bool) -> UInt64? {
        guard inFlight == nil else { return nil }
        if !force, let lastStarted, now - lastStarted < 2 { return nil }
        generation &+= 1
        inFlight = generation
        lastStarted = now
        return generation
    }

    mutating func finish(_ token: UInt64) -> Bool {
        guard inFlight == token, generation == token else { return false }
        inFlight = nil
        return true
    }

    mutating func invalidate() {
        generation &+= 1
        inFlight = nil
        lastStarted = nil
    }
}

/// Stop remains the UI intent until NE confirms a terminal status. A late
/// connecting/connected snapshot must not turn the cancel button back on.
struct NimboVpnStopRequest {
    private(set) var pending = false
    mutating func begin() { pending = true }
    mutating func observe(rawStatus: Int) {
        if rawStatus == 0 || rawStatus == 1 { pending = false }
    }
}
