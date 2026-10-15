import SwiftUI

enum NimboTab: String, CaseIterable, Identifiable {
    case home
    case profiles
    case stats
    case routing
    case settings
    case modules
    case routingProfiles = "routing-profiles"
    case notifications

    static let navigationTabs: [NimboTab] = [.home, .profiles, .stats, .settings]

    var navigationTab: NimboTab {
        switch self {
        case .routing, .modules, .routingProfiles, .notifications: .settings
        default: self
        }
    }

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .home: "Главная"
        case .profiles: "Профили"
        case .stats: "Статистика"
        case .routing: "Маршруты"
        case .settings: "Настройки"
        case .modules: "Модули"
        case .routingProfiles: "Профили маршрутизации"
        case .notifications: "Уведомления"
        }
    }

    var systemImage: String {
        switch self {
        case .home: "power"
        case .profiles: "square.stack.3d.up"
        case .stats: "waveform.path"
        case .routing: "arrow.triangle.branch"
        case .settings: "slider.horizontal.3"
        case .modules: "square.grid.2x2"
        case .routingProfiles: "arrow.triangle.branch"
        case .notifications: "bell"
        }
    }

    /// Имя SF Symbol для системной панели.
    var symbol: String { systemImage }

}
