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
    @State private var sessionStartedAt: Date?
    @State private var updatePageUrl: String?
    @State private var backupUrl: URL?
    @State private var showBackupPicker = false
    @State private var showSync = false
    /// Раз в секунду — как обновляется мониторинг на Android.
    private let metricsTimer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        lifecycleLayer
            .preferredColorScheme(nil)
    }

    /// Экран: Compose под системной панелью.
    private var screen: some View {
        ZStack(alignment: .bottom) {
            ComposeScreen(tab: .home)
                .ignoresSafeArea()
            // Панель рисует система: только она умеет размывать то, что под
            // ней, — Compose о своём фоне ничего не знает.
            NimboTabBar(selection: $selectedTab)
        }
    }

    private var lifecycleLayer: some View {
        interfaceLayer
            .onAppear(perform: synchronizeComposeState)
            .onAppear(perform: publishSessions)
            .task { await measurePings() }
            .task { await loadSubscriptionMetaIfNeeded() }
            .task { await checkForUpdate() }
    }

    private var interfaceLayer: some View {
        vpnLayer
            .onChange(of: selectedTab) { tab in
                IosComposeControllerKt.NimboSetIosScreen(wireName: tab.rawValue)
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboOpenScreen)) { notification in
                guard let wireName = notification.object as? String,
                      let tab = NimboTab(rawValue: wireName) else { return }
                selectedTab = tab
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboOpenUrl)) { notification in
                openExternalLink(notification.object as? String)
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboOpenUpdate)) { _ in
                openExternalLink(updatePageUrl)
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboSystemSettings)) { _ in
                openSystemSettings()
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboSaveAppRule)) { _ in
                openSystemSettings()
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboRouting)) { _ in
                Task { await restageConfiguration() }
            }
    }

    private var vpnLayer: some View {
        sheetsLayer
            .onReceive(vpn.$state) { (state: VpnController.State) in
                handleVpnState(state)
            }
            .onReceive(metricsTimer) { _ in publishMetrics() }
            .onReceive(NotificationCenter.default.publisher(for: .nimboToggleVpn)) { _ in
                Task { await toggleVpn() }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboRefreshProfile)) { _ in
                guard vpn.state != .connected, vpn.state != .connecting else { return }
                Task { await refreshSubscription() }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboSelectServer)) { notification in
                guard let serverID = notification.object as? String else { return }
                Task { await selectServer(serverID) }
            }
    }

    private var sheetsLayer: some View {
        screen
            .onReceive(NotificationCenter.default.publisher(for: .nimboAddProfile)) { _ in
                showProfiles = true
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboProfileSettings)) { _ in
                showProfiles = true
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboDiagnostics)) { _ in
                showDiagnostics = true
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboAbout)) { _ in
                showAbout = true
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboExportBackup)) { _ in
                backupUrl = NimboBackup.export()
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboImportBackup)) { _ in
                showBackupPicker = true
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboOpenSync)) { _ in
                showSync = true
            }
            .sheet(isPresented: $showProfiles, onDismiss: synchronizeComposeState) {
                ProfilesContainerView().environmentObject(vpn)
            }
            .sheet(isPresented: $showDiagnostics) {
                DiagnosticsView().environmentObject(vpn)
            }
            .sheet(isPresented: $showAbout) { AboutView() }
            .sheet(isPresented: $showSync, onDismiss: synchronizeComposeState) {
                NimboSyncView()
            }
            .sheet(item: $backupUrl) { url in NimboShareSheet(url: url) }
            .sheet(isPresented: $showBackupPicker) {
                NimboDocumentPicker { url in
                    showBackupPicker = false
                    Task { await restoreBackup(from: url) }
                }
            }
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

    /// Смена состояния туннеля: новая сессия обнуляет счётчики, завершённая —
    /// записывается в историю.
    private func handleVpnState(_ state: VpnController.State) {
        // `failed` несёт код и сообщение, поэтому сравнивать его через `==`
        // нельзя — только сопоставлением образца.
        switch state {
        case .connecting, .preparing:
            metrics.reset()
            sessionStartedAt = Date()
        case .idle, .failed:
            finishSession()
        default:
            break
        }
        synchronizeComposeState()
    }

    /// Переключение туннеля вынесено из тела: там оно раздувало выражение.
    private func toggleVpn() async {
        switch vpn.state {
        case .connected, .connecting, .preparing:
            await vpn.disconnect()
        case .idle, .disconnecting, .failed:
            await vpn.connect()
        }
    }

    private func selectServer(_ serverID: String) async {
        do {
            let server = try NimboSubscriptionRepository.shared.select(serverID: serverID)
            try await vpn.stageConfiguration(
                data: NimboSubscriptionRepository.shared.stagingData(for: server)
            )
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

    /// Восстановление всегда заканчивается обновлением подписки: настройки без
    /// свежего списка серверов бесполезны.
    private func restoreBackup(from url: URL) async {
        do {
            if let source = try NimboBackup.restore(from: url) {
                _ = try? NimboSubscriptionRepository.shared.importPayload(
                    Data(source.utf8),
                    source: source
                )
                _ = try? await NimboSubscriptionRepository.shared.refresh()
            }
            synchronizeComposeState()
            await measurePings()
        } catch {
            await NimboDiagnostics.shared.record(
                .warning,
                stage: .config,
                code: "IOS_BACKUP_RESTORE_FAILED",
                message: NimboRedactor.redact(error.localizedDescription)
            )
        }
    }

    /// Проверка обновлений заканчивается ссылкой: поставить сборку из
    /// приложения iOS не позволяет, её подписывают снаружи.
    private func checkForUpdate() async {
        guard let release = await NimboUpdateChecker.latest(
            currentVersion: NimboPlatformInfo.displayVersion
        ) else { return }
        updatePageUrl = release.assetUrl ?? release.pageUrl
        IosComposeControllerKt.NimboUpdateIosRelease(
            version: release.version,
            notes: String(release.notes.prefix(400))
        )
    }

    /// Сессия закрывается при отключении: ядро своей статистики наружу не
    /// отдаёт, поэтому итог берём из накопленных показаний интерфейса.
    private func finishSession() {
        guard let startedAt = sessionStartedAt else { return }
        sessionStartedAt = nil
        NimboSessionStore.append(
            NimboSession(
                startedAt: startedAt,
                endedAt: Date(),
                download: Int64(clamping: metrics.downloadTotal),
                upload: Int64(clamping: metrics.uploadTotal)
            )
        )
        publishSessions()
    }

    private func publishSessions() {
        let sessions = NimboSessionStore.all
        IosComposeControllerKt.NimboUpdateIosSessions(
            startedAt: sessions.map(\.startedAtLabel),
            durations: sessions.map(\.durationLabel),
            downloads: sessions.map { KotlinLong(longLong: $0.download) },
            uploads: sessions.map { KotlinLong(longLong: $0.upload) }
        )
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
    static let nimboOpenUpdate = Notification.Name("com.nimbo.action.open-update")
    static let nimboExportBackup = Notification.Name("com.nimbo.action.export-backup")
    static let nimboImportBackup = Notification.Name("com.nimbo.action.import-backup")
    static let nimboOpenSync = Notification.Name("com.nimbo.action.open-sync")
}
