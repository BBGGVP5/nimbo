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
    @Environment(\.colorScheme) private var colorScheme
    @AppStorage("com.nimbo.appearance.accentHex") private var accentHex = "75A7FF"
    @AppStorage("com.nimbo.appearance.corners") private var corners = 1.0
    @AppStorage("com.nimbo.appearance.brightness") private var brightness = 1.0
    @AppStorage("com.nimbo.appearance.transparency") private var transparency = 0.0
    @AppStorage("com.nimbo.appearance.refraction") private var refraction = true
    @AppStorage("com.nimbo.appearance.haptics") private var haptics = true
    @AppStorage("com.nimbo.appearance.textScale") private var textScale = 1.0

    private var accent: Color {
        let hex = accentHex.replacingOccurrences(of: "#", with: "")
        let value = hex.count == 6 ? UInt(hex, radix: 16) ?? 0x75A7FF : 0x75A7FF
        return Color(red: Double((value >> 16) & 255) / 255,
                     green: Double((value >> 8) & 255) / 255,
                     blue: Double(value & 255) / 255)
    }
    private var ink: Color { colorScheme == .dark ? Color(red: 244/255, green: 238/255, blue: 223/255) : Color(red: 20/255, green: 18/255, blue: 14/255) }
    private var paper: Color { colorScheme == .dark ? Color(red: 27/255, green: 24/255, blue: 20/255) : Color(red: 251/255, green: 247/255, blue: 236/255) }
    private var cornerScale: Double { min(max(corners, 0.25), 2) }
    private var barRadius: Double {
        (isManga ? 3 : elementStyle == "dotted" ? 9 : elementStyle == "signal" ? 18 : 26) * cornerScale
    }

    @Namespace private var indicator
    /// Вкладка, значок которой сейчас подпрыгивает.
    @State private var bumpedTab: NimboTab?
    /// Приседание при посадке: значок на миг становится шире и ниже.
    @State private var squashedTab: NimboTab?
    /// Текущий наклон качающегося значка.
    @State private var wobble: [String: Double] = [:]
    @State private var wobbleGeneration: [String: Int] = [:]
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
                    if haptics { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
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
                .font(.system(size: 11 * min(max(textScale, 0.85), 1.25), weight: isSelected ? .semibold : .medium))
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
        return wobble[tab.rawValue] ?? 0
    }

    private func tint(isSelected: Bool) -> Color {
        if isManga {
            return isSelected ? ink : ink.opacity(0.7)
        }
        return isSelected ? accent : Color.primary.opacity(0.65)
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
            let generation = (wobbleGeneration[tab.rawValue] ?? 0) + 1
            wobbleGeneration[tab.rawValue] = generation
            // Затухающее покачивание: одиночный наклон читается как сбой
            // отрисовки, а не как отклик.
            let sway: [(Double, Double)] = [(15, 0), (-10, 0.11), (5, 0.22), (0, 0.33)]
            for (angle, delay) in sway {
                DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                    guard wobbleGeneration[tab.rawValue] == generation else { return }
                    withAnimation(.easeInOut(duration: 0.11)) { wobble[tab.rawValue] = angle }
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
        let shape = RoundedRectangle(cornerRadius: (isManga ? 2 : elementStyle == "dotted" ? 6 : 20) * cornerScale, style: .continuous)
        if isManga {
            shape
                .fill(paper)
                .overlay(shape.fill(accent.opacity(0.18)))
                .overlay(shape.strokeBorder(accent, lineWidth: 2))
                .shadow(color: ink.opacity(0.16), radius: 0, x: 3, y: 3)
        } else if elementStyle == "dotted" {
            shape.fill(accent.opacity(0.18))
                .overlay(shape.strokeBorder(accent, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [0.1, 4])))
        } else if elementStyle == "material" || elementStyle == "signal" {
            shape.fill(accent.opacity(0.22))
        } else {
            shape
                .fill(accent.opacity(0.16))
                .overlay(
                    shape.strokeBorder(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(0.35),
                                accent.opacity(0.45)
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
        let shape = RoundedRectangle(cornerRadius: barRadius, style: .continuous)
        if isManga {
            // Бумага с чернильным контуром: стекло здесь противоречит стилю.
            shape
                .fill(paper)
                .brightness((min(max(brightness, 0.5), 2) - 1) * 0.1)
                .overlay(shape.strokeBorder(ink, lineWidth: 2))
        } else if elementStyle == "dotted" {
            shape.fill(Color(uiColor: .secondarySystemBackground))
                .overlay {
                    Canvas { context, size in
                        for x in stride(from: 6.0, to: size.width, by: 11) {
                            for y in stride(from: 6.0, to: size.height, by: 11) {
                                context.fill(Path(ellipseIn: CGRect(x: x, y: y, width: 1.4, height: 1.4)), with: .color(accent.opacity(0.2)))
                            }
                        }
                    }.clipShape(shape)
                }
                .overlay(shape.strokeBorder(accent.opacity(0.8), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [0.1, 4])))
        } else if elementStyle == "material" || elementStyle == "signal" {
            shape.fill(Color(uiColor: .secondarySystemBackground))
                .overlay(shape.fill(accent.opacity(elementStyle == "material" ? 0.14 : 0.03)))
                .brightness((min(max(brightness, 0.5), 2) - 1) * 0.1)
        } else if #available(iOS 26.0, *), refraction {
            shape
                .fill(.clear)
                .glassEffect(.regular, in: shape)
                .overlay(shape.fill(accent.opacity(0.12 * (1 - min(max(transparency, 0), 1)))))
                .brightness((min(max(brightness, 0.5), 2) - 1) * 0.1)
        } else {
            shape
                .fill(.ultraThinMaterial)
                .overlay(shape.fill(accent.opacity(0.12 * (1 - min(max(transparency, 0), 1)))))
                .brightness((min(max(brightness, 0.5), 2) - 1) * 0.1)
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
