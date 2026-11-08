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
                Image("NimboCloudSymbol").font(.title2)
                    .foregroundStyle(statusColor(context.state.phase, stale: context.isStale))
                VStack(alignment: .leading, spacing: 4) {
                    Text("nimbo").font(.headline)
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
                    Image("NimboCloudSymbol").font(.title2).padding(.leading, 6)
                }
                DynamicIslandExpandedRegion(.trailing) { Text("nimbo").font(.headline) }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack(spacing: 8) {
                        Image(systemName: statusSymbol(context.state.phase, stale: context.isStale))
                            .foregroundStyle(statusColor(context.state.phase, stale: context.isStale))
                            .accessibilityHidden(true)
                        Text(context.state.phase.title(english: context.state.english, stale: context.isStale))
                            .font(.subheadline.weight(.medium)).lineLimit(2)
                    }
                    .padding(.vertical, 6)
                }
            } compactLeading: {
                Image("NimboCloudSymbol").accessibilityLabel("Nimbo")
            } compactTrailing: {
                if let text = context.state.phase.compactText(english: context.state.english, stale: context.isStale) {
                    Text(text).font(.caption2.weight(.semibold)).lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .foregroundStyle(statusColor(context.state.phase, stale: context.isStale))
                        .accessibilityLabel(context.state.phase.title(english: context.state.english, stale: context.isStale))
                }
            } minimal: {
                Image("NimboCloudSymbol")
                    .foregroundStyle(statusColor(context.state.phase, stale: context.isStale))
                    .accessibilityLabel("Nimbo. " + context.state.phase.title(english: context.state.english, stale: context.isStale))
            }
            .keylineTint(.white.opacity(0.35))
        }
    }

    private func statusColor(_ phase: NimboLivePhase, stale: Bool) -> Color {
        if stale { return .secondary }
        switch phase {
        case .connected: return .mint
        case .connecting, .recovering: return .yellow
        case .disconnecting, .idle: return .secondary
        }
    }

    private func statusSymbol(_ phase: NimboLivePhase, stale: Bool) -> String {
        if stale { return "questionmark.circle" }
        switch phase {
        case .connected: return "checkmark.circle.fill"
        case .connecting, .recovering: return "arrow.triangle.2.circlepath"
        case .disconnecting, .idle: return "power"
        }
    }
}
