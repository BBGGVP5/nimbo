import SwiftUI
import UIKit

/// RootView forwards selection to Compose once; this view owns presentation only.
struct NimboTabBar: View {
    @Binding var selection: NimboTab
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.colorSchemeContrast) private var contrast
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @AppStorage("com.nimbo.appearance.haptics") private var haptics = true
    @AppStorage("com.nimbo.appearance.navIconMotion") private var navIconMotion = true
    @AppStorage("com.nimbo.appearance.textScale") private var textScale = 1.0
    @AppStorage("com.nimbo.appearance.accentHex") private var accentHex = "E8E8E8"

    private var expanded: Bool { dynamicTypeSize >= .xxxLarge || textScale > 1.15 }

    private var customAccent: UInt32? {
        let hex = accentHex.replacingOccurrences(of: "#", with: "").uppercased()
        guard hex != "E8E8E8", hex.count == 6 else { return nil }
        return UInt32(hex, radix: 16)
    }

    private var selectedFill: Color {
        guard let hex = customAccent else { return NimboNative.ink.opacity(0.12) }
        return Color(red: Double((hex >> 16) & 255) / 255,
                     green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }

    private var selectedInk: Color {
        guard let hex = customAccent else { return NimboNative.ink }
        let channels = [Double((hex >> 16) & 255), Double((hex >> 8) & 255), Double(hex & 255)].map { value in
            let c = value / 255
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        let luminance = channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
        return luminance > 0.179 ? .black : .white
    }

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize || verticalSizeClass == .compact {
                // One row in short landscape as well as at accessibility sizes.
                Menu {
                    ForEach(NimboTab.navigationTabs) { tab in
                        Button { select(tab) } label: {
                            Label(tab.title, systemImage: selection.navigationTab == tab ? "checkmark" : tab.symbol)
                        }
                    }
                } label: {
                    HStack {
                        Text(selection.navigationTab.title).nimboFont(17, weight: .semibold)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 8)
                        Image(systemName: "chevron.up.chevron.down").accessibilityHidden(true)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .foregroundStyle(NimboNative.ink)
                }
                .accessibilityLabel("Разделы приложения")
                .accessibilityValue(Text(selection.navigationTab.title))
            } else if expanded {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 4) {
                    tabs
                }
            } else {
                HStack(alignment: .center, spacing: 2) { tabs }
            }
        }
        .padding(6)
        .background { barBackground }
        .frame(maxWidth: 680)
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private var tabs: some View {
        ForEach(NimboTab.navigationTabs) { tab in
            Button {
                select(tab)
            } label: {
                VStack(spacing: 5) {
                    Image(systemName: tab.symbol)
                        .font(.system(size: 22, weight: .regular))
                        .frame(width: 28, height: 28)
                        .accessibilityHidden(true)
                    Text(tab.title).nimboFont(11, relativeTo: .caption2, weight: .semibold)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(height: 18)
                }
                .padding(.horizontal, 3)
                .padding(.vertical, 9)
                .frame(maxWidth: .infinity, minHeight: 54)
                .foregroundStyle(selection.navigationTab == tab ? selectedInk : NimboNative.secondary)
                .background(selection.navigationTab == tab ? selectedFill : .clear,
                            in: RoundedRectangle(cornerRadius: 28))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(tab.title))
            .accessibilityAddTraits(selection.navigationTab == tab ? [.isSelected] : [])
            .accessibilityIdentifier("nimbo.tab.\(tab.rawValue)")
        }
    }

    private func select(_ tab: NimboTab) {
        guard tab != selection else { return }
        withAnimation(reduceMotion || !navIconMotion ? nil : .easeOut(duration: 0.18)) {
            selection = tab
        }
        if haptics { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
    }

    @ViewBuilder private var barBackground: some View {
        let shape = RoundedRectangle(cornerRadius: 34)
        if reduceTransparency || contrast == .increased {
            shape.fill(NimboNative.panel)
                .overlay(shape.strokeBorder(NimboNative.border, lineWidth: 1))
        } else if #available(iOS 26.0, *) {
            shape.fill(.clear).glassEffect(.regular, in: shape)
        } else {
            shape.fill(.regularMaterial)
                .overlay(shape.strokeBorder(NimboNative.border, lineWidth: 1))
        }
    }
}

/// Wide iPad navigation exposes the existing Compose pages without changing iPhone tabs.
struct NimboWideSidebar: View {
    @Binding var selection: NimboTab
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage("com.nimbo.appearance.haptics") private var haptics = true

    private let primaryTabs: [NimboTab] = [.home, .profiles, .stats]
    private let toolTabs: [NimboTab] = [.routing, .modules, .routingProfiles, .notifications]
    private let appTabs: [NimboTab] = [.settings]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                NimboBrand().padding(.horizontal, 12)
                sidebarSection("ОСНОВНОЕ", tabs: primaryTabs)
                sidebarSection("ИНСТРУМЕНТЫ", tabs: toolTabs)
                sidebarSection("ПРИЛОЖЕНИЕ", tabs: appTabs)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(width: 248)
        .background(NimboNative.panel)
        .overlay(alignment: .trailing) { NimboNative.border.frame(width: 1) }
    }

    private func sidebarSection(_ heading: LocalizedStringKey, tabs: [NimboTab]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(heading).nimboFont(10, relativeTo: .caption2, weight: .semibold)
                .foregroundStyle(NimboNative.secondary)
                .padding(.horizontal, 12)
                .padding(.bottom, 4)
            ForEach(tabs) { tab in
                Button {
                    guard selection != tab else { return }
                    withAnimation(reduceMotion ? nil : .easeOut(duration: 0.18)) {
                        selection = tab
                    }
                    if haptics { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: tab.symbol).frame(width: 20)
                            .accessibilityHidden(true)
                        Text(tab.title).nimboFont(14, relativeTo: .body,
                                                weight: selection == tab ? .semibold : .regular)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 12)
                    .frame(minHeight: 48)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .foregroundStyle(selection == tab ? NimboNative.ink : NimboNative.secondary)
                    .background(selection == tab ? NimboNative.raised : Color.clear,
                                in: RoundedRectangle(cornerRadius: 14))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(tab.title))
                .accessibilityAddTraits(selection == tab ? [.isSelected] : [])
                .accessibilityIdentifier("nimbo.sidebar.\(tab.rawValue)")
            }
        }
    }
}
