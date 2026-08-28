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
                Task { await refreshSubscription() }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboDiagnostics)) { _ in showDiagnostics = true }
            .onReceive(NotificationCenter.default.publisher(for: .nimboAbout)) { _ in showAbout = true }
            .onReceive(NotificationCenter.default.publisher(for: .nimboSystemSettings)) { _ in openSystemSettings() }
            .onReceive(NotificationCenter.default.publisher(for: .nimboOpenUrl)) { notification in
                openExternalLink(notification.object as? String)
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboSaveAppRule)) { _ in openSystemSettings() }
            .onReceive(NotificationCenter.default.publisher(for: .nimboSelectServer)) { notification in
                guard let serverID = notification.object as? String else { return }
                Task {
                    do {
                        let server = try NimboSubscriptionRepository.shared.select(serverID: serverID)
                        try await vpn.stageConfiguration(data: Data(server.rawConfiguration.utf8))
                        synchronizeComposeState()
                    } catch {
                        await NimboDiagnostics.shared.record(
                            .error,
                            stage: .config,
                            code: "IOS_SERVER_SELECTION_FAILED",
                            message: NimboRedactor.redact(error.localizedDescription)
                        )
                    }
                }
            }
            .sheet(isPresented: $showProfiles, onDismiss: synchronizeComposeState) {
                ProfilesContainerView().environmentObject(vpn)
            }
            .sheet(isPresented: $showDiagnostics) {
                DiagnosticsView().environmentObject(vpn)
            }
            .sheet(isPresented: $showAbout) { AboutView() }
        .preferredColorScheme(nil)
    }

    /// Ссылки поддержки и сайта подписки открываются системным браузером.
    private func openExternalLink(_ value: String?) {
        guard let value, let url = URL(string: value), url.scheme?.hasPrefix("http") == true else { return }
        UIApplication.shared.open(url)
    }

    private func synchronizeComposeState() {
        let presentation = vpn.state.composePresentation
        let profile = try? NimboSubscriptionRepository.shared.loadProfile()
        let selected = profile?.selectedServer
        let profileJson = NimboSubscriptionRepository.shared.rawProfileJSON()
        IosComposeControllerKt.NimboUpdateIosUiState(
            vpnState: presentation.state,
            errorCode: presentation.code,
            errorMessage: presentation.message,
            activeProfileName: profile?.title ?? "Подписка не добавлена",
            activeServerName: selected?.name ?? "Выберите сервер",
            serverCount: Int32(profile?.servers.count ?? 0),
            profileCount: Int32(profile == nil ? 0 : 1),
            deviceName: NimboPlatformInfo.device,
            systemName: NimboPlatformInfo.system,
            appVersion: NimboPlatformInfo.displayVersion,
            profileJson: profileJson,
            activeServerId: NimboConfigurationStore.shared.activeServerID
        )
    }

    private func refreshSubscription() async {
        do {
            let profile = try await NimboSubscriptionRepository.shared.refresh()
            guard let selected = profile.selectedServer else {
                throw NimboSubscriptionRepositoryError.serverNotFound
            }
            try await vpn.stageConfiguration(data: Data(selected.rawConfiguration.utf8))
            synchronizeComposeState()
            await NimboDiagnostics.shared.record(
                .info,
                stage: .config,
                code: "IOS_SUBSCRIPTION_REFRESHED",
                message: "Подписка обновлена и активный сервер повторно передан Packet Tunnel",
                metadata: ["servers": "\(profile.servers.count)"]
            )
        } catch {
            await NimboDiagnostics.shared.record(
                .error,
                stage: .config,
                code: "IOS_SUBSCRIPTION_REFRESH_FAILED",
                message: NimboRedactor.redact(error.localizedDescription)
            )
        }
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
    static let nimboSelectServer = Notification.Name("com.nimbo.action.select-server")
    static let nimboOpenUrl = Notification.Name("com.nimbo.action.open-url")
}
