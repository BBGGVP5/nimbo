import NimboShared
import SwiftUI

/// Нижняя панель, нарисованная средствами системы.
///
/// Настоящее «жидкое стекло» умеет только iOS: она размывает то, что лежит под
/// панелью. Compose так не может — его слой рисуется поверх и о фоне ничего не
/// знает, поэтому любая имитация выходила либо мутной плёнкой, либо прозрачной
/// панелью, сквозь которую читался список. Здесь системный материал, а на
/// iOS 26 и новее — настоящее стекло `glassEffect`.
struct NimboTabBar: View {
    @Binding var selection: NimboTab

    @Namespace private var indicator

    var body: some View {
        HStack(spacing: 2) {
            ForEach(NimboTab.allCases, id: \.self) { tab in
                Button {
                    guard tab != selection else { return }
                    withAnimation(.spring(response: 0.34, dampingFraction: 0.78)) {
                        selection = tab
                    }
                    IosComposeControllerKt.NimboSetIosScreen(wireName: tab.rawValue)
                    UIImpactFeedbackGenerator(style: .soft).impactOccurred()
                } label: {
                    item(for: tab)
                }
                .buttonStyle(NimboTabButtonStyle())
            }
        }
        .padding(4)
        .background(barBackground)
        .padding(.horizontal, 16)
        .padding(.bottom, 2)
    }

    private func item(for tab: NimboTab) -> some View {
        let isSelected = selection == tab
        return VStack(spacing: 2) {
            Image(systemName: tab.symbol)
                .font(.system(size: 19, weight: isSelected ? .semibold : .regular))
                .symbolRenderingMode(.hierarchical)
                .frame(height: 22)
            Text(tab.title)
                .font(.system(size: 10, weight: isSelected ? .semibold : .medium))
                .lineLimit(1)
                .minimumScaleFactor(0.85)
        }
        .foregroundStyle(isSelected ? Color.nimboAccent : Color.nimboSecondary)
        .frame(maxWidth: .infinity)
        .frame(height: 46)
        .background {
            if isSelected {
                // Подсветка переезжает между вкладками одним движением, а не
                // гаснет и загорается заново.
                selectionShape
                    .matchedGeometryEffect(id: "nimbo-tab-indicator", in: indicator)
            }
        }
        .contentShape(Rectangle())
    }

    private var selectionShape: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        return shape
            .fill(Color.nimboAccent.opacity(0.16))
            .overlay(
                shape.strokeBorder(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(0.35),
                            Color.nimboAccent.opacity(0.45)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    ),
                    lineWidth: 1
                )
            )
    }

    /// На iOS 26 доступно настоящее стекло; на более ранних версиях его роль
    /// исполняет `ultraThinMaterial` — он тоже размывает фон, просто без
    /// бликов, поэтому кромку рисуем сами.
    @ViewBuilder
    private var barBackground: some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        if #available(iOS 26.0, *) {
            shape
                .fill(.clear)
                .glassEffect(.regular, in: shape)
        } else {
            shape
                .fill(.ultraThinMaterial)
                .overlay(
                    shape.strokeBorder(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(0.30),
                                Color.white.opacity(0.06)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        ),
                        lineWidth: 0.8
                    )
                )
                .shadow(color: .black.opacity(0.32), radius: 20, y: 8)
        }
    }
}

/// Нажатие слегка утапливает вкладку — как системные элементы iOS.
private struct NimboTabButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

extension Color {
    /// Те же цвета, что в общем оформлении (`NimboPalette`).
    static let nimboAccent = Color(red: 0x75 / 255, green: 0xA7 / 255, blue: 0xFF / 255)
    static let nimboSecondary = Color(red: 0xEA / 255, green: 0xEB / 255, blue: 0xF2 / 255).opacity(0.62)
}
