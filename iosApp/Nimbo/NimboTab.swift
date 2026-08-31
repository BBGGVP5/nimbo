import SwiftUI

enum NimboTab: String, CaseIterable, Identifiable {
    case home
    case profiles
    case stats
    case settings

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .home: "Главная"
        case .profiles: "Профили"
        case .stats: "Статистика"
        case .settings: "Настройки"
        }
    }

    var systemImage: String {
        switch self {
        case .home: "house.fill"
        case .profiles: "globe.europe.africa.fill"
        case .stats: "chart.bar.fill"
        case .settings: "gearshape.fill"
        }
    }

    /// Имя SF Symbol для системной панели.
    var symbol: String { systemImage }

    /// Подъём значка при выборе: прыгает только дом, остальным это ни к чему.
    var motionLift: CGFloat {
        switch self {
        case .home: -5
        default: 0
        }
    }

    /// Поворот при выборе. Глобус делает полный оборот, шестерёнка — четверть,
    /// статистика слегка качается.
    var motionRotation: Double {
        switch self {
        case .profiles: 360
        case .settings: 90
        case .stats: -12
        case .home: 0
        }
    }

    /// Покачивание возвращается в исходное, проворот — нет.
    var motionWobbles: Bool { self == .stats }
}
