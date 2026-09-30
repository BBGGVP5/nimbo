import Foundation

@main
struct LiveActivityPolicyTests {
    static func main() {
        for phase in [NimboLivePhase.connecting, .connected, .recovering, .disconnecting, .idle] {
            for english in [false, true] {
                precondition((phase.compactText(english: english)?.count ?? 0) <= 7)
                precondition(phase.compactText(english: english, stale: true) == "?")
                precondition(!phase.title(english: english, stale: true).isEmpty)
            }
            precondition(NimboLiveActivityPolicy.shouldStart(phase: phase, enabled: true,
                authorized: true, foreground: true, dismissed: false) == phase.isOngoing)
        }
        precondition(NimboLivePhase.connected.compactText(english: false) == nil)
        precondition(NimboLivePhase.connected.compactText(english: true) == nil)
        for denial in 0..<4 {
            precondition(!NimboLiveActivityPolicy.shouldStart(phase: .connected, enabled: denial != 0,
                authorized: denial != 1, foreground: denial != 2, dismissed: denial == 3))
        }
        precondition(NimboLivePhase.connected.title(english: false).contains("NIMBO"))
        precondition(NimboLivePhase.connected.title(english: true).contains("NIMBO"))
        precondition(NimboLiveActivityPolicy.staleInterval <= 90)
        print("Live Activity policy: PASS (states, localization, icon-only, permission, foreground, dismissal)")
    }
}
