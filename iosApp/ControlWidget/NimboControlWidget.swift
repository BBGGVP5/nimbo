import AppIntents
import NetworkExtension
import SwiftUI
import WidgetKit

/// Переключатель Nimbo в Пункте управления.
///
/// Android держит такую кнопку в шторке, и на iPhone ей место там же: включать
/// VPN чаще всего нужно на бегу, а ради этого открывать приложение —
/// лишний шаг.
///
/// Пункт управления умеет держать элементы приложений начиная с iOS 18,
/// поэтому весь виджет закрыт проверкой доступности: на более старых системах
/// расширение просто не предлагает ничего.
@available(iOS 18.0, *)
struct NimboControlWidget: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "com.nimbo.control.vpn") {
            ControlWidgetToggle(
                "Nimbo",
                isOn: NimboTunnelState.isRunning,
                action: NimboToggleTunnelIntent()
            ) { isOn in
                Label(isOn ? "Подключено" : "Отключено", systemImage: isOn ? "lock.shield.fill" : "lock.shield")
            }
        }
        .displayName("Nimbo VPN")
        .description("Включение и отключение туннеля")
    }
}

/// Состояние туннеля для элемента управления.
///
/// Значение читается синхронно при отрисовке: элемент управления рисуется
/// системой в свой момент, и ждать асинхронную загрузку ему нельзя.
@available(iOS 18.0, *)
enum NimboTunnelState {
    static var isRunning: Bool {
        NEVPNManager.shared().connection.status == .connected
            || NEVPNManager.shared().connection.status == .connecting
    }
}

/// Действие переключателя.
///
/// Расширение поднимает туннель само, без запуска приложения: для этого ему
/// нужно то же право `packet-tunnel-provider`, что и у самого туннеля.
@available(iOS 18.0, *)
struct NimboToggleTunnelIntent: SetValueIntent {
    static let title: LocalizedStringResource = "Nimbo VPN"
    static let description = IntentDescription("Включает и отключает туннель Nimbo")

    @Parameter(title: "Включён")
    var value: Bool

    init() {}

    init(value: Bool) {
        self.value = value
    }

    func perform() async throws -> some IntentResult {
        let managers = try await NETunnelProviderManager.loadAllFromPreferences()
        // Профиль создаёт приложение при первом подключении: без него включать
        // нечего, и честнее ничего не делать, чем создавать пустую настройку.
        guard let manager = managers.first else { return .result() }

        if value {
            manager.isEnabled = true
            try await manager.saveToPreferences()
            try await manager.loadFromPreferences()
            try manager.connection.startVPNTunnel()
        } else {
            manager.connection.stopVPNTunnel()
        }
        return .result()
    }
}
