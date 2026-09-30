import ActivityKit
import Foundation

@available(iOS 16.2, *)
struct NimboLiveActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var phase: NimboLivePhase
        var english: Bool
        var checkedAt: Date
    }
}
