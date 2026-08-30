import NimboShared
import SwiftUI

/// Нижняя панель, нарисованная средствами системы.
///
/// Настоящее «жидкое стекло» умеет только сама iOS: она размывает то, что
/// лежит под панелью. Compose так не может — его слой рисуется поверх и о фоне
/// ничего не знает, поэтому любая имитация выходила либо мутной плёнкой, либо
/// прозрачной панелью, сквозь которую читался список. Здесь используется
/// системный материал, а на iOS 26 и новее — стекло из `glassEffect`.
struct NimboTabBar: View {
    @Binding var selection: NimboTab

    var body: some View {
        HStack(spacing: 4) {
            ForEach(NimboTab.allCases, id: \.self) { tab in
                Button {
                    guard tab != selection else { return }
                    withAnimation(.spring(response: 0.32, dampingFraction: 0.82)) {
                        selection = tab
                    }
                    IosComposeControllerKt.NimboSetIosScreen(wireName: tab.rawValue)
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: tab.symbol)
                            .font(.system(size: 20, weight: selection == tab ? .semibold : .regular))
                        Text(tab.title)
                            .font(.system(size: 10, weight: selection == tab ? .bold : .medium))
                    }
                    .foregroundStyle(selection == tab ? Color.nimboAccent : Color.nimboSecondary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(selectionBackground(for: tab))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(6)
        .background(barBackground)
        .padding(.horizontal, 18)
        .padding(.bottom, 6)
    }

    @ViewBuilder
    private func selectionBackground(for tab: NimboTab) -> some View {
        if selection == tab {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color.nimboAccent.opacity(0.18))
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .stroke(Color.nimboAccent.opacity(0.42), lineWidth: 1)
                )
        }
    }

    /// На iOS 26 доступно настоящее стекло; на более ранних версиях его роль
    /// исполняет `ultraThinMaterial` — он тоже размывает фон, просто без
    /// бликов по краю.
    @ViewBuilder
    private var barBackground: some View {
        let shape = RoundedRectangle(cornerRadius: 32, style: .continuous)
        if #available(iOS 26.0, *) {
            shape
                .fill(.clear)
                .glassEffect(.regular, in: shape)
        } else {
            shape
                .fill(.ultraThinMaterial)
                .overlay(
                    shape.stroke(Color.white.opacity(0.12), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.28), radius: 18, y: 6)
        }
    }
}

extension Color {
    /// Те же цвета, что в общем оформлении (`NimboPalette`).
    static let nimboAccent = Color(red: 0x75 / 255, green: 0xA7 / 255, blue: 0xFF / 255)
    static let nimboSecondary = Color(red: 0xEA / 255, green: 0xEB / 255, blue: 0xF2 / 255).opacity(0.66)
}
