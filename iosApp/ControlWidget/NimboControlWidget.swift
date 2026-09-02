import AppIntents
import NetworkExtension
import SwiftUI
import WidgetKit

/// Переключатель Nimbo в Пункте управления.
///
/// Android держит такую кнопку в шторке, и на iPhone ей место там же: включать
/// VPN чаще всего нужно на бегу, а ради этого открывать приложение — лишний
/// шаг.
struct NimboControlWidget: ControlWidget {
    var body: some ControlWidgetConfiguration {
        // Состояние приходит от поставщика, а не вычисляется при построении:
        // система рисует элемент в свой момент, и синхронное чтение настроек
        // VPN там не успевает — элемент оставался пустым кружком.
        StaticControlConfiguration(kind: NimboControlWidget.kind, provider: TunnelStateProvider()) { isRunning in
            ControlWidgetToggle(
                "Nimbo",
                isOn: isRunning,
                action: NimboToggleTunnelIntent()
            ) { isOn in
                Label(isOn ? "Подключено" : "Отключено", systemImage: isOn ? "lock.shield.fill" : "lock.shield")
            }
            .tint(.blue)
        }
        .displayName("Nimbo VPN")
        .description("Включение и отключение туннеля")
    }

    static let kind = "com.nimbo.control.vpn"
}

/// Состояние туннеля для элемента управления.
struct TunnelStateProvider: ControlValueProvider {
    /// Каким элемент показывается в галерее, где настоящего состояния нет.
    var previewValue: Bool { false }

    func currentValue() async throws -> Bool {
        let managers = try? await NETunnelProviderManager.loadAllFromPreferences()
        guard let manager = managers?.first else { return false }
        let status = manager.connection.status
        return status == .connected || status == .connecting
    }
}

/// Действие переключателя.
///
/// Расширение включает туннель, не открывая приложение. Своего туннеля оно не
/// поднимает — только переключает уже настроенный, поэтому ему хватает права
/// `allow-vpn`. Право туннеля здесь не просто лишнее: с ним iOS 27 отвергает
/// связку расширений целиком, и перестаёт запускаться сам туннель.
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
        // нечего, и создавать пустую настройку из шторки нельзя — система
        // спросит разрешение, а спрашивать её некому.
        guard let manager = managers.first else { return .result() }

        if value {
            manager.isEnabled = true
            try await manager.saveToPreferences()
            try await manager.loadFromPreferences()
            try manager.connection.startVPNTunnel()
        } else {
            manager.connection.stopVPNTunnel()
        }

        // Состояние в Пункте управления обновляется по просьбе: без неё
        // переключатель остаётся в прежнем положении до следующего открытия.
        ControlCenter.shared.reloadControls(ofKind: NimboControlWidget.kind)
        return .result()
    }
}
