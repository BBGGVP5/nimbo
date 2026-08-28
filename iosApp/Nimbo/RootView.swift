import SwiftUI
import NimboShared
import UIKit

struct RootView: View {
    @EnvironmentObject private var vpn: VpnController
    @State private var showProfiles = false
    @State private var showDiagnostics = false
    @State private var showAbout = false

    var body: some View {
        ComposeScreen(tab: .home)
            .ignoresSafeArea()
            .onAppear(perform: synchronizeComposeState)
            .onReceive(vpn.$state) { _ in synchronizeComposeState() }
            .onReceive(NotificationCenter.default.publisher(for: .nimboToggleVpn)) { _ in
                Task {
                    switch vpn.state {
                    case .connected, .connecting, .preparing:
                        await vpn.disconnect()
                    case .idle, .disconnecting, .failed:
                        await vpn.connect()
                    }
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboAddProfile)) { _ in showProfiles = true }
            .onReceive(NotificationCenter.default.publisher(for: .nimboProfileSettings)) { _ in showProfiles = true }
            .onReceive(NotificationCenter.default.publisher(for: .nimboRefreshProfile)) { _ in
                guard vpn.state != .connected, vpn.state != .connecting else { return }
                Task { await vpn.prepare() }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboDiagnostics)) { _ in showDiagnostics = true }
            .onReceive(NotificationCenter.default.publisher(for: .nimboAbout)) { _ in showAbout = true }
            .onReceive(NotificationCenter.default.publisher(for: .nimboSystemSettings)) { _ in openSystemSettings() }
            .onReceive(NotificationCenter.default.publisher(for: .nimboSaveAppRule)) { _ in openSystemSettings() }
            .sheet(isPresented: $showProfiles, onDismiss: synchronizeComposeState) {
                ProfilesContainerView().environmentObject(vpn)
            }
            .sheet(isPresented: $showDiagnostics) {
                DiagnosticsView().environmentObject(vpn)
            }
            .sheet(isPresented: $showAbout) { AboutView() }
        .preferredColorScheme(nil)
    }

    private func synchronizeComposeState() {
        let presentation = vpn.state.composePresentation
        let hasProfile = NimboConfigurationStore.shared.displayDescription != nil
        IosComposeControllerKt.NimboUpdateIosUiState(
            vpnState: presentation.state,
            errorCode: presentation.code,
            errorMessage: presentation.message,
            activeProfileName: NimboConfigurationStore.shared.displayDescription ?? "Подписка не добавлена",
            activeServerName: hasProfile ? "Активная конфигурация" : "Выберите сервер",
            serverCount: hasProfile ? 1 : 0,
            profileCount: hasProfile ? 1 : 0,
            deviceName: NimboPlatformInfo.device,
            systemName: NimboPlatformInfo.system,
            appVersion: NimboPlatformInfo.displayVersion
        )
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

private extension VpnController.State {
    var composePresentation: (state: String, code: String?, message: String?) {
        switch self {
        case .idle: ("idle", nil, nil)
        case .preparing: ("preparing", nil, nil)
        case .connecting: ("connecting", nil, nil)
        case .connected: ("connected", nil, nil)
        case .disconnecting: ("disconnecting", nil, nil)
        case let .failed(code, message): ("failed", code, message)
        }
    }
}

private extension Notification.Name {
    static let nimboToggleVpn = Notification.Name("com.nimbo.action.toggle-vpn")
    static let nimboAddProfile = Notification.Name("com.nimbo.action.add-profile")
    static let nimboRefreshProfile = Notification.Name("com.nimbo.action.refresh-profile")
    static let nimboProfileSettings = Notification.Name("com.nimbo.action.profile-settings")
    static let nimboSaveAppRule = Notification.Name("com.nimbo.action.save-app-rule")
    static let nimboDiagnostics = Notification.Name("com.nimbo.action.diagnostics")
    static let nimboAbout = Notification.Name("com.nimbo.action.about")
    static let nimboSystemSettings = Notification.Name("com.nimbo.action.system-settings")
}
