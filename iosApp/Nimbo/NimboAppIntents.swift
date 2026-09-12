import AppIntents
import WidgetKit

struct NimboConnectIntent: AppIntent {
    static let title: LocalizedStringResource = "Подключить Nimbo"
    static let description = IntentDescription("Подключает выбранный в Nimbo сервер. Сначала настройте VPN в приложении.")
    func perform() async throws -> some IntentResult & ProvidesDialog {
        try await NimboTunnelControl.setEnabled(true)
        if #available(iOS 18.0, *) { ControlCenter.shared.reloadControls(ofKind: "com.nimbo.control.vpn") }
        return .result(dialog: "Запуск Nimbo запрошен")
    }
}

struct NimboDisconnectIntent: AppIntent {
    static let title: LocalizedStringResource = "Отключить Nimbo"
    func perform() async throws -> some IntentResult & ProvidesDialog {
        try await NimboTunnelControl.setEnabled(false)
        if #available(iOS 18.0, *) { ControlCenter.shared.reloadControls(ofKind: "com.nimbo.control.vpn") }
        return .result(dialog: "Отключение Nimbo запрошено")
    }
}

struct NimboStatusIntent: AppIntent {
    static let title: LocalizedStringResource = "Состояние Nimbo"
    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        .result(value: try await NimboTunnelControl.statusDescription())
    }
}

struct NimboShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: NimboConnectIntent(), phrases: ["Подключить \(.applicationName)"], shortTitle: "Подключить", systemImageName: "power")
        AppShortcut(intent: NimboDisconnectIntent(), phrases: ["Отключить \(.applicationName)"], shortTitle: "Отключить", systemImageName: "stop.circle")
        AppShortcut(intent: NimboStatusIntent(), phrases: ["Статус \(.applicationName)"], shortTitle: "Состояние", systemImageName: "network")
    }
}
