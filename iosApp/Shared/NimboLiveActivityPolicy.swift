import Foundation

/// Only fixed public states cross the ActivityKit boundary.
enum NimboLivePhase: String, Codable, Hashable {
    case connecting, connected, recovering, disconnecting, idle

    var isOngoing: Bool { self != .idle && self != .disconnecting }
    func compactText(english: Bool, stale: Bool = false) -> String? {
        if stale { return "?" }
        switch self {
        case .connecting: return english ? "Connect" : "Подкл."
        case .recovering: return english ? "Retry" : "Повтор"
        case .connected, .disconnecting, .idle: return nil
        }
    }
    func title(english: Bool, stale: Bool = false) -> String {
        if stale { return english ? "Open Nimbo to update status" : "Откройте Nimbo для обновления статуса" }
        switch self {
        case .connecting: return english ? "Connecting" : "Подключаемся"
        case .connected: return english ? "NIMBO connected" : "NIMBO подключён"
        case .recovering: return english ? "Reconnecting" : "Восстанавливаем связь"
        case .disconnecting, .idle: return english ? "NIMBO disconnected" : "NIMBO отключён"
        }
    }
}

enum NimboLiveActivityPolicy {
    static let preferenceKey = "com.nimbo.notifications.liveActivity"
    static let staleInterval: TimeInterval = 65
    static func shouldEnd(phase: NimboLivePhase, enabled: Bool, authorized: Bool) -> Bool {
        !phase.isOngoing || !enabled || !authorized
    }
    static func shouldStart(phase: NimboLivePhase, enabled: Bool, authorized: Bool,
                            foreground: Bool, dismissed: Bool) -> Bool {
        phase.isOngoing && enabled && authorized && foreground && !dismissed
    }
}
