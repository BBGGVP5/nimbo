import ActivityKit
import Foundation
import UIKit

@MainActor
enum NimboLiveActivityBridge {
    static var enabled: Bool {
        UserDefaults.standard.object(forKey: NimboLiveActivityPolicy.preferenceKey) == nil
            || UserDefaults.standard.bool(forKey: NimboLiveActivityPolicy.preferenceKey)
    }
    static var authorized: Bool {
        if #available(iOS 16.2, *) { return ActivityAuthorizationInfo().areActivitiesEnabled }
        return false
    }
    static func synchronize(_ phase: NimboLivePhase) {
        if #available(iOS 16.2, *) {
            NimboLiveActivityController.shared.synchronize(phase, enabled: enabled)
        }
    }
}

/// Owns one activity, not a tunnel. Never asks NE to connect or extends background runtime.
@available(iOS 16.2, *)
@MainActor
private final class NimboLiveActivityController {
    static let shared = NimboLiveActivityController()
    private var pending: Task<Void, Never>?
    private var generation: UInt64 = 0
    private var ownedID: String?
    private var dismissed = false

    func synchronize(_ phase: NimboLivePhase, enabled: Bool) {
        generation &+= 1
        let token = generation
        let previous = pending
        pending = Task { [weak self] in
            await previous?.value
            guard let self, token == self.generation else { return }
            await self.apply(phase, enabled: enabled, token: token)
        }
    }

    private func apply(_ phase: NimboLivePhase, enabled: Bool, token: UInt64) async {
        let activities = Activity<NimboLiveActivityAttributes>.activities
        if !phase.isOngoing || !enabled || !ActivityAuthorizationInfo().areActivitiesEnabled {
            for activity in activities { await finish(activity) }
            ownedID = nil
            dismissed = false
            return
        }
        let active = activities.filter { $0.activityState == .active || $0.activityState == .stale }
        if let ownedID, !active.contains(where: { $0.id == ownedID }) { dismissed = true }
        let selected = active.first(where: { $0.id == ownedID }) ?? active.first
        for duplicate in active where duplicate.id != selected?.id { await finish(duplicate) }
        guard token == generation else { return }
        let english = Locale.preferredLanguages.first?.hasPrefix("en") == true
        let state = NimboLiveActivityAttributes.ContentState(phase: phase, english: english, checkedAt: Date())
        if let selected {
            ownedID = selected.id
            await selected.update(ActivityContent(state: state,
                staleDate: Date().addingTimeInterval(NimboLiveActivityPolicy.staleInterval)))
        } else if NimboLiveActivityPolicy.shouldStart(phase: phase, enabled: enabled,
            authorized: ActivityAuthorizationInfo().areActivitiesEnabled,
            foreground: UIApplication.shared.applicationState == .active, dismissed: dismissed) {
            do {
                let activity: Activity<NimboLiveActivityAttributes>
                activity = try Activity.request(attributes: NimboLiveActivityAttributes(),
                    content: ActivityContent(state: state,
                        staleDate: Date().addingTimeInterval(NimboLiveActivityPolicy.staleInterval)), pushType: nil)
                ownedID = activity.id
            } catch {
                // ActivityKit denial must never fail/disconnect the VPN or leak identifiers.
                dismissed = true
            }
        }
    }

    private func finish(_ activity: Activity<NimboLiveActivityAttributes>) async {
        await activity.end(nil, dismissalPolicy: .immediate)
    }
}
