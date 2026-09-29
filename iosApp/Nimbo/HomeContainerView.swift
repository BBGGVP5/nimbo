import SwiftUI

/// Native fallback surface; the active RootView continues to render shared Compose.
struct HomeContainerView: View {
    @EnvironmentObject private var vpn: VpnController
    @ScaledMetric(relativeTo: .body) private var connectionIconSize = 24.0

    var body: some View {
        NimboPage {
            NimboBrand()
            VStack(alignment: .leading, spacing: 16) {
                NimboNotice(title: statusText, detail: "VPN", symbol: vpn.state == .connected ? "checkmark.circle" : "power",
                            tint: vpn.state == .connected ? NimboNative.success : NimboNative.secondary,
                            busy: vpn.state == .preparing || vpn.state == .connecting || vpn.state == .disconnecting)
                NimboOperationPhrase(operation: .connection,
                                     isActive: vpn.state == .preparing || vpn.state == .connecting)
                Button {
                    Task {
                        if vpn.state == .connected || vpn.state == .connecting {
                            await vpn.disconnect()
                        } else {
                            await vpn.connect()
                        }
                    }
                } label: {
                    Label {
                        Text(vpn.state == .connected || vpn.state == .connecting ? "Отключить VPN" : "Подключить VPN")
                    } icon: {
                        if vpn.state == .connected {
                            Image("NimboCloud")
                                .renderingMode(.template)
                                .resizable()
                                .scaledToFit()
                                .frame(width: connectionIconSize, height: connectionIconSize)
                                .accessibilityHidden(true)
                        } else {
                            Image(systemName: "power").accessibilityHidden(true)
                        }
                    }
                }
                .buttonStyle(NimboActionStyle(prominent: true))
                .disabled(vpn.state == .preparing || vpn.state == .disconnecting)
            }.nimboCard()
            if case let .failed(code, message) = vpn.state {
                NimboSection(title: "ОШИБКА ПОДКЛЮЧЕНИЯ") {
                    NimboNotice(title: "Не удалось подключиться", detail: NimboRedactor.redact(message), symbol: "exclamationmark.circle", tint: NimboNative.error)
                    Text(code).nimboFont(12, relativeTo: .caption).monospaced().textSelection(.enabled)
                }
            }
        }
        .nimboSheetStyle()
    }

    private var statusText: String {
        switch vpn.state {
        case .idle: "Готово к подключению"
        case .preparing: "Подготавливаем VPN…"
        case .connecting: "Подключение…"
        case .connected: "Подключено"
        case .disconnecting: "Отключение…"
        case .failed: "Не удалось подключиться"
        }
    }
}
