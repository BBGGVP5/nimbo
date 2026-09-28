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
            ) { isOn in
                // Only a confirmed connection earns the cloud; transitional states
                // remain switchable off. Control Center requires the symbol asset.
                Label {
                    Text(isOn ? "Подключено" : "Отключено")
                } icon: {
                    if status == .connected {
                        Image("NimboCloudSymbol")
                    } else {
                        Image(systemName: "power")
                    }
                }
            }
        }
        .displayName("Nimbo VPN")
        .description("Включение и отключение туннеля")
    }

    static let kind = "com.nimbo.control.vpn"
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
        try await NimboTunnelControl.setEnabled(value)

        // Состояние в Пункте управления обновляется по просьбе: без неё
        // переключатель остаётся в прежнем положении до следующего открытия.
        ControlCenter.shared.reloadControls(ofKind: NimboControlWidget.kind)
        return .result()
    }
}
