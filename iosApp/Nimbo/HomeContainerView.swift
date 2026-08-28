import SwiftUI

struct HomeContainerView: View {
    @EnvironmentObject private var vpn: VpnController
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.025, green: 0.08, blue: 0.16), Color(red: 0.02, green: 0.035, blue: 0.09)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 28) {
                Spacer(minLength: 24)
                Text("Nimbo")
                    .font(.system(size: 38, weight: .bold, design: .rounded))
                Text(statusText)
                    .font(.headline)
                    .foregroundStyle(statusColor)
                    .multilineTextAlignment(.center)

                Button {
                    Task {
                        if vpn.state == .connected || vpn.state == .connecting {
                            await vpn.disconnect()
                        } else {
                            await vpn.connect()
                        }
                    }
                } label: {
                    Image(systemName: buttonIcon)
                        .font(.system(size: 52, weight: .semibold))
                        .frame(width: 154, height: 154)
                }
                .buttonStyle(NimboLiquidButtonStyle(
                    active: vpn.state == .connected,
                    reduceTransparency: reduceTransparency
                ))
                .disabled(vpn.state == .preparing || vpn.state == .disconnecting)
                .accessibilityLabel(vpn.state == .connected ? "Отключить VPN" : "Подключить VPN")

                if case let .failed(code, message) = vpn.state {
                    VStack(spacing: 6) {
                        Text(code).font(.caption.monospaced()).foregroundStyle(.orange)
                        Text(message).font(.footnote).multilineTextAlignment(.center)
                    }
                    .padding()
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                }
                Spacer()
            }
            .padding(24)
        }
        .animation(reduceMotion ? nil : .spring(response: 0.55, dampingFraction: 0.82), value: statusText)
    }

    private var statusText: String {
        switch vpn.state {
        case .idle: "Нажмите для подключения"
        case .preparing: "Подготавливаем VPN…"
        case .connecting: "Подключение…"
        case .connected: "Защищено"
        case .disconnecting: "Отключение…"
        case .failed: "Не удалось подключиться"
        }
    }

    private var statusColor: Color {
        switch vpn.state {
        case .connected: .green
        case .failed: .orange
        default: Color(red: 0.55, green: 0.72, blue: 1.0)
        }
    }

    private var buttonIcon: String {
        switch vpn.state {
        case .connected: "lock.shield.fill"
        case .connecting, .preparing, .disconnecting: "hourglass"
        case .idle, .failed: "power"
        }
    }
}

private struct NimboLiquidButtonStyle: ButtonStyle {
    let active: Bool
    let reduceTransparency: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(.white)
            .background {
                Circle()
                    .fill(reduceTransparency ? Color.blue : Color.blue.opacity(active ? 0.82 : 0.42))
                    .overlay {
                        Circle().stroke(.white.opacity(0.34), lineWidth: 1)
                    }
                    .shadow(color: active ? .green.opacity(0.42) : .blue.opacity(0.32), radius: 28)
            }
            .scaleEffect(configuration.isPressed ? 0.93 : 1)
            .animation(.spring(response: 0.32, dampingFraction: 0.68), value: configuration.isPressed)
    }
}
