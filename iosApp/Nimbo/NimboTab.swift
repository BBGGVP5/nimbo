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
        case .home: "bolt.fill"
        case .profiles: "globe.europe.africa.fill"
        case .stats: "chart.bar.fill"
        case .settings: "gearshape.fill"
        }
    }

    /// Имя SF Symbol для системной панели.
    var symbol: String { systemImage }
}
