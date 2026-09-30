import ActivityKit
import SwiftUI
import WidgetKit

@main
struct NimboLiveActivityBundle: WidgetBundle {
    var body: some Widget { NimboLiveActivityWidget() }
}

struct NimboLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: NimboLiveActivityAttributes.self) { context in
            HStack(spacing: 12) {
                Image(systemName: context.isStale ? "cloud" : "cloud.fill").font(.title2)
                VStack(alignment: .leading, spacing: 4) {
                    Text("NIMBO").font(.headline)
                    Text(context.state.phase.title(english: context.state.english, stale: context.isStale))
                        .font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .activityBackgroundTint(Color(.secondarySystemBackground))
            .activitySystemActionForegroundColor(.primary)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "cloud.fill").font(.title2).padding(.leading, 6)
                }
                DynamicIslandExpandedRegion(.trailing) { Text("NIMBO").font(.headline) }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(context.state.phase.title(english: context.state.english, stale: context.isStale))
                        .font(.subheadline).lineLimit(2).padding(.bottom, 4)
                }
            } compactLeading: {
                Image(systemName: "cloud.fill").accessibilityLabel("NIMBO")
            } compactTrailing: {
                if let text = context.state.phase.compactText(english: context.state.english, stale: context.isStale) {
                    Text(text).font(.caption2).lineLimit(1)
                }
            } minimal: {
                Image(systemName: context.isStale ? "cloud" : "cloud.fill").accessibilityLabel("NIMBO")
            }
            .keylineTint(.white.opacity(0.35))
        }
    }
}
