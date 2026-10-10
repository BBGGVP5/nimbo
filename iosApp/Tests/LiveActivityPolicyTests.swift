import Foundation

@main
struct LiveActivityPolicyTests {
    static func main() {
        for phase in [NimboLivePhase.connecting, .connected, .recovering, .disconnecting, .idle] {
            precondition(NimboLiveActivityPolicy.shouldEnd(phase: phase, enabled: false, authorized: true))
            precondition(NimboLiveActivityPolicy.shouldEnd(phase: phase, enabled: true, authorized: false))
            precondition(NimboLiveActivityPolicy.shouldEnd(phase: phase, enabled: true, authorized: true) == !phase.isOngoing)
            for english in [false, true] {
                let compact = phase.compactText(english: english) ?? ""
                precondition(!compact.isEmpty && compact.count <= 9)
                precondition(phase.compactText(english: english, stale: true) == (english ? "Check" : "Проверить"))
                precondition(!phase.title(english: english, stale: true).isEmpty)
            }
            precondition(NimboLiveActivityPolicy.shouldStart(phase: phase, enabled: true,
                authorized: true, foreground: true, dismissed: false) == (phase.isOngoing && phase != .disconnecting))
        }
        precondition(NimboLivePhase.connected.compactText(english: false) == "VPN вкл.")
        precondition(NimboLivePhase.connected.compactText(english: true) == "VPN on")
        precondition(!NimboLiveActivityPolicy.shouldEnd(phase: .disconnecting, enabled: true, authorized: true))
        precondition(NimboLivePhase.disconnecting.title(english: false) == "Отключаемся")
        for denial in 0..<4 {
            precondition(!NimboLiveActivityPolicy.shouldStart(phase: .connected, enabled: denial != 0,
                authorized: denial != 1, foreground: denial != 2, dismissed: denial == 3))
        }
        precondition(NimboLivePhase.connected.title(english: false) == "VPN включён")
        precondition(NimboLivePhase.connected.title(english: true) == "VPN is on")
        precondition(NimboLiveActivityPolicy.staleInterval <= 90)
        print("Live Activity policy: PASS (states, localized compact status, stale state, disconnect, permission, foreground, dismissal)")
    }
}
