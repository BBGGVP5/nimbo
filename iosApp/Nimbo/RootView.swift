import SwiftUI
import NimboShared
import UIKit

struct RootView: View {
    @EnvironmentObject private var vpn: VpnController
    @State private var showProfiles = false
    @State private var showDiagnostics = false
    @State private var showAbout = false
    @State private var selectedTab: NimboTab = .home
    @State private var metrics = NimboMetricsAccumulator()
    /// Раз в секунду — как обновляется мониторинг на Android.
    private let metricsTimer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack(alignment: .bottom) {
            ComposeScreen(tab: .home)
                .ignoresSafeArea()
            // Панель рисует система: только она умеет размывать то, что под
            // ней, — Compose о своём фоне ничего не знает.
            NimboTabBar(selection: $selectedTab)
        }
            .onChange(of: selectedTab) { _ in
                IosComposeControllerKt.NimboSetIosScreen(wireName: selectedTab.rawValue)
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboOpenScreen)) { notification in
                guard let wireName = notification.object as? String,
                      let tab = NimboTab(rawValue: wireName) else { return }
                selectedTab = tab
            }
            .onAppear(perform: synchronizeComposeState)
            .task { await measurePings() }
            .task { await loadSubscriptionMetaIfNeeded() }
            .onReceive(vpn.$state) { state in
                // Новая сессия — счётчики трафика начинаем с нуля.
                if state == .connecting || state == .preparing { metrics.reset() }
                synchronizeComposeState()
            }
            .onReceive(metricsTimer) { _ in publishMetrics() }
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
            .onReceive(NotificationCenter.default.publisher(for: .nimboRouting)) { _ in
                Task { await restageConfiguration() }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboSaveAppRule)) { _ in openSystemSettings() }
            .onReceive(NotificationCenter.default.publisher(for: .nimboSelectServer)) { notification in
                guard let serverID = notification.object as? String else { return }
                Task {
                    do {
                        let server = try NimboSubscriptionRepository.shared.select(serverID: serverID)
                        try await vpn.stageConfiguration(data: NimboSubscriptionRepository.shared.stagingData(for: server))
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

    /// Почта владельца, трафик и срок живут в заголовках ответа панели, а не
    /// в ссылках. Пока подписку не обновляли, их просто нет — поэтому при
    /// первом запуске после обновления приложения тянем их сами, молча.
    private func loadSubscriptionMetaIfNeeded() async {
        guard NimboSubscriptionMetaStore.current.updatedAt == 0,
              (try? NimboConfigurationStore.shared.loadSource()) ?? nil != nil else { return }
        _ = try? await NimboSubscriptionRepository.shared.refresh()
        synchronizeComposeState()
    }

    /// ICMP обычному приложению на iOS недоступен, поэтому меряем время
    /// установления TCP-соединения с портом сервера — то же значение, что
    /// показывает Android для TCP-протоколов.
    private func measurePings() async {
        guard let profile = try? NimboSubscriptionRepository.shared.loadProfile() else { return }
        let targets = profile.servers
            .filter { !$0.host.isEmpty && $0.port > 0 }
            .map { (id: $0.id, host: $0.host, port: $0.port) }
        guard !targets.isEmpty else { return }

        IosComposeControllerKt.NimboUpdateIosPings(serverIds: [], values: [], inProgress: true)
        let results = await NimboPingService.shared.measureAll(targets)
        let ordered = results.map { ($0.key, $0.value) }
        IosComposeControllerKt.NimboUpdateIosPings(
            serverIds: ordered.map { $0.0 },
            values: ordered.map { KotlinInt(int: Int32($0.1)) },
            inProgress: false
        )
    }

    /// Настройки маршрутизации хранятся рядом с конфигурацией, поэтому после
    /// их изменения конфигурацию нужно передать в туннель заново. Действующее
    /// подключение при этом не рвём — новые правила вступят в силу при
    /// следующем.
    private func restageConfiguration() async {
        do {
            guard let profile = try NimboSubscriptionRepository.shared.loadProfile(),
                  let selected = profile.selectedServer else { return }
            try await vpn.stageConfiguration(
                data: NimboSubscriptionRepository.shared.stagingData(for: selected)
            )
        } catch {
            await NimboDiagnostics.shared.record(
                .warning,
                stage: .config,
                code: "IOS_ROUTING_RESTAGE_FAILED",
                message: NimboRedactor.redact(error.localizedDescription)
            )
        }
    }

    /// Показания снимаются со счётчиков utun-интерфейса: пакеты идут мимо
    /// приложения, поэтому считать их самому нечем.
    private func publishMetrics() {
        guard vpn.state == .connected else { return }
        metrics.tick()
        IosComposeControllerKt.NimboUpdateIosMetrics(
            uploadSpeed: Int64(clamping: metrics.uploadSpeed),
            downloadSpeed: Int64(clamping: metrics.downloadSpeed),
            uploadTotal: Int64(clamping: metrics.uploadTotal),
            downloadTotal: Int64(clamping: metrics.downloadTotal),
            uploadSamples: metrics.uploadSamples.map { KotlinLong(longLong: Int64(clamping: $0)) },
            downloadSamples: metrics.downloadSamples.map { KotlinLong(longLong: Int64(clamping: $0)) },
            memoryMb: Int32(metrics.memoryMb),
            memorySamples: metrics.memorySamples.map { KotlinInt(int: Int32($0)) }
        )
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
            activeProfileName: NimboSubscriptionMetaStore.current.title
                ?? profile?.title
                ?? "Подписка не добавлена",
            activeServerName: selected?.name ?? "Выберите сервер",
            serverCount: Int32(profile?.servers.count ?? 0),
            profileCount: Int32(profile == nil ? 0 : 1),
            deviceName: NimboPlatformInfo.device,
            systemName: NimboPlatformInfo.system,
            appVersion: NimboPlatformInfo.displayVersion,
            profileJson: profileJson,
            activeServerId: NimboConfigurationStore.shared.activeServerID
        )

        let meta = NimboSubscriptionMetaStore.current
        IosComposeControllerKt.NimboUpdateIosProfileMeta(
            title: meta.title,
            trafficLabel: meta.trafficLabel,
            expiryLabel: meta.expiryLabel,
            updatedLabel: profile == nil ? "" : meta.updatedLabel,
            announce: meta.announce ?? ""
        )
    }

    private func refreshSubscription() async {
        do {
            let profile = try await NimboSubscriptionRepository.shared.refresh()
            // Список серверов сменился — старые замеры больше не про них.
            Task { await measurePings() }
            guard let selected = profile.selectedServer else {
                throw NimboSubscriptionRepositoryError.serverNotFound
            }
            try await vpn.stageConfiguration(data: NimboSubscriptionRepository.shared.stagingData(for: selected))
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
    static let nimboRouting = Notification.Name("com.nimbo.action.routing")
    static let nimboOpenScreen = Notification.Name("com.nimbo.action.open-screen")
}
