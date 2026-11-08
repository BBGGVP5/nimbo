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
        StaticControlConfiguration(kind: NimboControlWidget.kind, provider: TunnelStateProvider()) { status in
            ControlWidgetToggle(
                "Nimbo",
                isOn: status == .connected || status == .connecting || status == .reasserting,
                action: NimboToggleTunnelIntent()
            ) { _ in
                // Keep the brand visible in the gallery and every tunnel state.
                // State is conveyed by the toggle and the actual status label.
                Label {
                    Text(statusTitle(status))
                } icon: {
                    Image("NimboCloudSymbol")
                }
            }
        }
        .displayName("NIMBO")
        .description("Включение и отключение туннеля")
    }

    nonisolated static let kind = "com.nimbo.control.vpn"

    private func statusTitle(_ status: NEVPNStatus) -> String {
        switch status {
        case .connected: return "Подключено"
        case .connecting: return "Подключение…"
        case .reasserting: return "Восстановление…"
        case .disconnecting: return "Отключение…"
        case .disconnected, .invalid: return "Отключено"
        @unknown default: return "Неизвестно"
        }
    }
}

/// Состояние туннеля для элемента управления.
struct TunnelStateProvider: ControlValueProvider {
    /// Каким элемент показывается в галерее, где настоящего состояния нет.
    var previewValue: NEVPNStatus { .disconnected }

    func currentValue() async throws -> NEVPNStatus {
        guard let manager = try await NimboTunnelControl.manager() else { return .disconnected }
        return manager.connection.status
    }
}

/// Действие переключателя.
///
/// Requests a state change for an existing profile, without creating a tunnel
/// provider in the widget. Validate profile access after third-party re-signing:
/// packaging an entitlement is not proof that iOS grants it to this process.
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
        try await NimboTunnelControl.setEnabled(value)

        // Состояние в Пункте управления обновляется по просьбе: без неё
        // переключатель остаётся в прежнем положении до следующего открытия.
        ControlCenter.shared.reloadControls(ofKind: NimboControlWidget.kind)
        return .result()
    }
}
