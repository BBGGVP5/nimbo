import SwiftUI

enum NimboTab: String, CaseIterable, Identifiable {
    case home
    case profiles
    case apps
    case settings

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .home: "Главная"
        case .profiles: "Профили"
        case .apps: "Приложения"
        case .settings: "Настройки"
        }
    }

    var systemImage: String {
        switch self {
        case .home: "bolt.fill"
        case .profiles: "globe.europe.africa.fill"
        case .apps: "apps.iphone"
        case .settings: "gearshape.fill"
        }
    }
}
