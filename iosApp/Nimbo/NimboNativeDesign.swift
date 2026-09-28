import SwiftUI
import UIKit

/// Universal-v2 colors, resolved for each native presentation's appearance.
enum NimboNative {
    static let canvas = adaptive(0xF5F4F1, 0x121212)
    static let panel = adaptive(0xFFFFFF, 0x1C1C1C)
    static let raised = adaptive(0xECECE8, 0x262626)
    static let ink = adaptive(0x202020, 0xF1F1F1)
    static let secondary = adaptive(0x676767, 0xA0A0A0)
    static let border = adaptive(0xDEDEDB, 0x303030)
    static let accent = adaptive(0x202020, 0xE8E8E8)
    static let onAccent = adaptive(0xFFFFFF, 0x202020)
    static let error = adaptive(0xB42318, 0xFFB4AB)
    static let success = adaptive(0x246B45, 0x9AD6AF)

    private static func adaptive(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((hex >> 16) & 255) / 255,
                           green: CGFloat((hex >> 8) & 255) / 255,
                           blue: CGFloat(hex & 255) / 255, alpha: 1)
        })
    }
}

private struct NimboNativeFont: ViewModifier {
    @AppStorage("com.nimbo.appearance.textScale") private var textScale = 1.0
    @ScaledMetric private var size: CGFloat
    let weight: Font.Weight
    let face: String

    init(size: CGFloat, relativeTo style: Font.TextStyle, weight: Font.Weight) {
        _size = ScaledMetric(wrappedValue: size, relativeTo: style)
        self.weight = weight
        self.face = NimboNativeFonts.face(relativeTo: style, weight: weight)
    }

    func body(content: Content) -> some View {
        // ScaledMetric already applies Dynamic Type; fixedSize avoids applying it twice.
        content.font(.custom(face, fixedSize: size * min(max(textScale, 0.85), 1.25)))
    }
}

extension View {
    func nimboFont(_ size: CGFloat = 17, relativeTo style: Font.TextStyle = .body,
                   weight: Font.Weight = .regular) -> some View {
        modifier(NimboNativeFont(size: size, relativeTo: style, weight: weight))
    }

    func nimboCard() -> some View {
        padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(NimboNative.panel, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NimboNative.border, lineWidth: 1))
    }

    func nimboSheetStyle() -> some View {
        self.nimboFont()
            .foregroundStyle(NimboNative.ink)
            .tint(NimboNative.accent)
            .background(NimboNative.canvas.ignoresSafeArea())
            .toolbarBackground(NimboNative.canvas, for: .navigationBar)
            .navigationBarTitleDisplayMode(.inline)
    }
}

struct NimboPage<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16, content: content)
                .frame(maxWidth: 680)
                .padding(16)
                .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(NimboNative.canvas.ignoresSafeArea())
    }
}

struct NimboSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).nimboFont(12, relativeTo: .caption, weight: .semibold)
                .foregroundStyle(NimboNative.secondary)
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: 14, content: content).nimboCard()
        }
    }
}

struct NimboNotice: View {
    let title: String
    let detail: String
    var symbol = "info.circle"
    var tint: Color = NimboNative.secondary
    var busy = false

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if busy {
                ProgressView().tint(tint).accessibilityLabel(Text(title))
            } else {
                Image(systemName: symbol).foregroundStyle(tint).accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text(title).nimboFont(16, weight: .semibold)
                    .fixedSize(horizontal: false, vertical: true)
                if !detail.isEmpty {
                    Text(detail).nimboFont(14, relativeTo: .subheadline)
                        .foregroundStyle(NimboNative.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .textSelection(.enabled)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

struct NimboActionStyle: ButtonStyle {
    var prominent = false
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .nimboFont(16, weight: .semibold)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 48)
            .foregroundStyle(prominent ? NimboNative.onAccent : NimboNative.ink)
            .background(prominent ? NimboNative.accent : NimboNative.raised,
                        in: RoundedRectangle(cornerRadius: 14))
            .opacity(isEnabled ? (configuration.isPressed ? 0.75 : 1) : 0.45)
    }
}

struct NimboBrand: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("nimbo").nimboFont(30, relativeTo: .largeTitle, weight: .bold)
            Text("v\(NimboPlatformInfo.displayVersion)").nimboFont(11, relativeTo: .caption)
                .foregroundStyle(NimboNative.secondary)
        }
        .foregroundStyle(NimboNative.ink)
        .accessibilityElement(children: .combine)
    }
}
