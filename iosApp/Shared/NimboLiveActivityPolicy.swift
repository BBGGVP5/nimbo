import Foundation

/// Only fixed public states cross the ActivityKit boundary.
enum NimboLivePhase: String, Codable, Hashable {
    case connecting, connected, recovering, disconnecting, idle

    var isOngoing: Bool { self != .idle }
    func compactText(english: Bool, stale: Bool = false) -> String? {
        if stale { return english ? "Check" : "Проверить" }
        switch self {
        case .connecting: return english ? "Starting" : "Подкл.…"
        case .recovering: return english ? "Retrying" : "Повтор…"
        case .connected: return english ? "VPN on" : "VPN вкл."
        case .disconnecting: return english ? "Stopping" : "Откл.…"
        case .idle: return english ? "VPN off" : "VPN выкл."
        }
    }
    func title(english: Bool, stale: Bool = false) -> String {
        if stale { return english ? "Open Nimbo to update status" : "Откройте Nimbo для обновления статуса" }
        switch self {
        case .connecting: return english ? "Connecting" : "Подключаемся"
        case .connected: return english ? "VPN is on" : "VPN включён"
        case .recovering: return english ? "Reconnecting" : "Восстанавливаем связь"
        case .disconnecting: return english ? "Disconnecting" : "Отключаемся"
        case .idle: return english ? "VPN is off" : "VPN отключён"
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
        phase.isOngoing && phase != .disconnecting && enabled && authorized && foreground && !dismissed
    }
}
