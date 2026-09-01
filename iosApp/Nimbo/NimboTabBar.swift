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
    let elementStyle: String

    @Namespace private var indicator
    /// Вкладка, значок которой сейчас подпрыгивает.
    @State private var bumpedTab: NimboTab?
    /// Приседание при посадке: значок на миг становится шире и ниже.
    @State private var squashedTab: NimboTab?
    /// Текущий наклон качающегося значка.
    @State private var wobble: Double = 0
    /// Накопленный угол для значков, которые проворачиваются.
    ///
    /// Именно накопленный: если возвращать угол к нулю, глобус после оборота
    /// откручивался бы назад — движение читалось бы как рывок туда-сюда.
    @State private var spin: [String: Double] = [:]

    /// Тот же ключ, что и у общего экрана настроек: выключатель один на всё
    /// приложение, а не отдельный для панели.
    /// Стиль интерфейса берём из тех же настроек, что и общий экран: панель
    /// обязана меняться вместе с остальным, иначе стиль выглядит недоделанным.
    private var isManga: Bool {
        elementStyle == "manga"
    }

    private var motionEnabled: Bool {
        let key = "com.nimbo.appearance.navIconMotion"
        guard UserDefaults.standard.object(forKey: key) != nil else { return true }
        return UserDefaults.standard.bool(forKey: key)
    }

    var body: some View {
        HStack(spacing: 2) {
            ForEach(NimboTab.allCases, id: \.self) { tab in
                Button {
                    guard tab != selection else { return }
                    withAnimation(.spring(response: 0.34, dampingFraction: 0.78)) {
                        selection = tab
                    }
                    bump(tab)
                    IosComposeControllerKt.NimboSetIosScreen(wireName: tab.rawValue)
                    UIImpactFeedbackGenerator(style: .soft).impactOccurred()
                } label: {
                    item(for: tab)
                }
                .buttonStyle(NimboTabButtonStyle())
            }
        }
        .padding(5)
        .background(barBackground)
        .padding(.horizontal, 16)
        .padding(.bottom, 2)
    }

    private func item(for tab: NimboTab) -> some View {
        let isSelected = selection == tab
        return VStack(spacing: 2) {
            Image(systemName: tab.symbol)
                .font(.system(size: 21, weight: isSelected ? .semibold : .regular))
                .symbolRenderingMode(.hierarchical)
                .frame(height: 24)
                // Движение объясняет сам значок: дом подпрыгивает, глобус
                // проворачивается, статистика покачивается, шестерёнка
                // поворачивается на четверть оборота.
                .scaleEffect(
                    x: (bumpedTab == tab ? 1.14 : 1) * (squashedTab == tab ? 1.10 : 1),
                    y: (bumpedTab == tab ? 1.14 : 1) / (squashedTab == tab ? 1.10 : 1)
                )
                .offset(y: bumpedTab == tab ? tab.motionLift : 0)
                .rotationEffect(.degrees(rotation(for: tab)))
            Text(tab.title)
                .font(.system(size: 11, weight: isSelected ? .semibold : .medium))
                .lineLimit(1)
                .minimumScaleFactor(0.85)
        }
        .foregroundStyle(tint(isSelected: isSelected))
        .frame(maxWidth: .infinity)
        .frame(height: 54)
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

    /// Проворот накапливается, покачивание — временное.
    private func rotation(for tab: NimboTab) -> Double {
        let accumulated = spin[tab.rawValue] ?? 0
        guard tab.motionWobbles else { return accumulated }
        return bumpedTab == tab ? wobble : 0
    }

    private func tint(isSelected: Bool) -> Color {
        if isManga {
            return isSelected ? Color.mangaInk : Color.mangaInk.opacity(0.7)
        }
        return isSelected ? Color.nimboAccent : Color.nimboSecondary
    }

    /// Короткий подскок значка при переходе. Возврат идёт пружиной, поэтому
    /// достаточно снять признак — анимация доиграет сама.
    private func bump(_ tab: NimboTab) {
        guard motionEnabled else { return }
        withAnimation(.spring(response: 0.22, dampingFraction: 0.45)) {
            bumpedTab = tab
        }
        if !tab.motionWobbles, tab.motionRotation != 0 {
            withAnimation(.easeInOut(duration: 0.52)) {
                spin[tab.rawValue] = (spin[tab.rawValue] ?? 0) + tab.motionRotation
            }
        }
        if tab.motionWobbles {
            // Затухающее покачивание: одиночный наклон читается как сбой
            // отрисовки, а не как отклик.
            let sway: [(Double, Double)] = [(15, 0), (-10, 0.11), (5, 0.22), (0, 0.33)]
            for (angle, delay) in sway {
                DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                    withAnimation(.easeInOut(duration: 0.11)) { wobble = angle }
                }
            }
        }
        if tab.motionLift != 0 {
            // Приседание на посадке — там же, где значок касается панели.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.16) {
                withAnimation(.easeOut(duration: 0.09)) { squashedTab = tab }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.09) {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) { squashedTab = nil }
                }
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.16) {
            withAnimation(.spring(response: 0.36, dampingFraction: 0.55)) {
                bumpedTab = nil
            }
        }
    }

    @ViewBuilder
    private var selectionShape: some View {
        let shape = RoundedRectangle(cornerRadius: isManga ? 2 : 20, style: .continuous)
        if isManga {
            shape
                .fill(Color.nimboAccent.opacity(0.18))
                .overlay(shape.strokeBorder(Color.nimboAccent, lineWidth: 2))
                .shadow(color: Color.mangaInk.opacity(0.16), radius: 0, x: 3, y: 3)
        } else {
            shape
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
    }

    /// На iOS 26 доступно настоящее стекло; на более ранних версиях его роль
    /// исполняет `ultraThinMaterial` — он тоже размывает фон, просто без
    /// бликов, поэтому кромку рисуем сами.
    @ViewBuilder
    private var barBackground: some View {
        let shape = RoundedRectangle(cornerRadius: isManga ? 3 : 26, style: .continuous)
        if isManga {
            // Бумага с чернильным контуром: стекло здесь противоречит стилю.
            shape
                .fill(Color.mangaPaper)
                .overlay(shape.strokeBorder(Color.mangaInk, lineWidth: 2))
        } else if #available(iOS 26.0, *) {
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

    /// Бумага и чернила стиля Manga — те же значения, что в общем модуле.
    static let mangaPaper = Color(red: 0x14 / 255, green: 0x14 / 255, blue: 0x1A / 255)
    static let mangaInk = Color(red: 0xF2 / 255, green: 0xEC / 255, blue: 0xDD / 255)
    static let mangaAccent = Color(red: 0xE6 / 255, green: 0x33 / 255, blue: 0x29 / 255)
}
