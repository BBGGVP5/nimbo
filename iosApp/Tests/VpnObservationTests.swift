import Foundation

@main enum VpnObservationTests {
    static func main() {
        var gate = NimboVpnObservationGate()
        let old = gate.begin(now: 0, force: false)!
        precondition(gate.begin(now: 10, force: true) == nil) // No overlapping reads.
        gate.invalidate() // Another profile/config/stop now owns the screen.
        let current = gate.begin(now: 10, force: false)!
        precondition(!gate.finish(old))
        precondition(gate.begin(now: 20, force: true) == nil) // Old finish did not clear new read.
        precondition(gate.finish(current))
        precondition(gate.begin(now: 11, force: false) == nil)
        let forced = gate.begin(now: 11, force: true)!
        precondition(gate.finish(forced))
        let later = gate.begin(now: 13, force: false)!
        precondition(gate.finish(later))

        var stop = NimboVpnStopRequest()
        stop.begin()
        for status in [2, 3, 4, 5] { // NE connecting/connected/reasserting/disconnecting.
            stop.observe(rawStatus: status)
            precondition(stop.pending)
        }
        stop.observe(rawStatus: 1)
        precondition(!stop.pending)
        stop.begin()
        stop.observe(rawStatus: 0)
        precondition(!stop.pending)
        print("PASS: status observation ownership/throttle and stop intent until confirmed disconnection")
    }
}
