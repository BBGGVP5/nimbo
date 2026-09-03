import Foundation
import SwiftUI
import UniformTypeIdentifiers
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
    /// Найденная сборка: из неё берётся файл для загрузки.
    @State private var updateRelease: NimboRelease?
    /// Скачанный файл — его показывает окно обмена, чтобы положить куда удобно.
    @State private var updateFileUrl: URL?
    @State private var backupUrl: URL?
    @State private var showBackupPicker = false
    @State private var showSync = false
    @State private var showQrScanner = false
    @State private var showFileImporter = false
    @Environment(\.scenePhase) private var scenePhase
    @State private var elementStyle = UserDefaults.standard.string(
        forKey: "com.nimbo.appearance.elementStyle"
    ) ?? "glass"
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
            NimboTabBar(selection: $selectedTab, elementStyle: elementStyle)
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
                // Пока проверка не нашла сборку, ссылки на файл нет — открываем
                // страницу релизов: раньше в этом случае кнопка молчала.
                openExternalLink(updatePageUrl ?? NimboUpdateChecker.releasesPageUrl)
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboCheckUpdate)) { _ in
                Task { await checkForUpdate(manual: true) }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboDownloadUpdate)) { _ in
                Task { await downloadUpdate() }
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
            .onReceive(NotificationCenter.default.publisher(for: .nimboAppearanceChanged)) { notification in
                elementStyle = (notification.object as? String)
                    ?? UserDefaults.standard.string(forKey: "com.nimbo.appearance.elementStyle")
                    ?? "glass"
            }
            .onReceive(NotificationCenter.default.publisher(for: UserDefaults.didChangeNotification)) { _ in
                elementStyle = UserDefaults.standard.string(forKey: "com.nimbo.appearance.elementStyle")
                    ?? "glass"
            }
    }

    private var vpnLayer: some View {
        sheetsLayer
            .onReceive(vpn.$state) { (state: VpnController.State) in
                handleVpnState(state)
            }
            .onReceive(metricsTimer) { _ in
                // В фоне показания читать некому: экран не виден, а каждый
                // опрос будит расширение и тратит батарею.
                guard scenePhase == .active else { return }
                Task { await publishMetrics() }
            }
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
            .onReceive(NotificationCenter.default.publisher(for: .nimboPingServer)) { notification in
                guard let serverID = notification.object as? String else { return }
                Task { await measurePing(serverID) }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboPingAll)) { _ in
                Task { await measurePings() }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboConnectFastest)) { _ in
                Task { await connectFastest() }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboCopyText)) { notification in
                guard let text = notification.object as? String, !text.isEmpty else { return }
                UIPasteboard.general.string = text
                notify("info", "Скопировано в буфер обмена")
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboExportModule)) { notification in
                guard let payload = notification.object as? String else { return }
                // Имя и текст приходят одной строкой: первая строка — название.
                let parts = payload.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false)
                let name = parts.first.map(String.init) ?? "module"
                let text = parts.count > 1 ? String(parts[1]) : ""
                guard let url = exportModuleFile(name: name, text: text) else { return }
                backupUrl = url
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboImportSubscription)) { notification in
                guard let source = notification.object as? String else { return }
                Task { await importSubscription(source) }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboImportClipboard)) { _ in
                // Вставка из буфера — самый частый путь: ссылку присылают в
                // мессенджере, и переписывать её руками никто не будет.
                guard let text = UIPasteboard.general.string else { return }
                Task { await importSubscription(text) }
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboImportFile)) { _ in
                showFileImporter = true
            }
            .onReceive(NotificationCenter.default.publisher(for: .nimboScanQr)) { _ in
                showQrScanner = true
            }
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [.json, .text, .plainText, .data],
                allowsMultipleSelection: false
            ) { result in
                guard case let .success(urls) = result, let url = urls.first else { return }
                Task { await importFile(url) }
            }
            .sheet(isPresented: $showQrScanner) {
                NimboQrScannerView { scanned in
                    showQrScanner = false
                    Task { await importSubscription(scanned) }
                }
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
            .sheet(item: $updateFileUrl) { url in NimboShareSheet(url: url) }
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

        // Замер — тоже событие: на Android он подсвечивается теми же частицами.
        IosComposeControllerKt.NimboPushIosBurst(trigger: "activity")
        IosComposeControllerKt.NimboUpdateIosPings(serverIds: [], values: [], inProgress: true)
        // Признак «идёт замер» снимается в любом случае: если экран закрыли и
        // задачу отменили, надпись «Проверяю…» иначе оставалась навсегда.
        defer {
            IosComposeControllerKt.NimboUpdateIosPings(serverIds: [], values: [], inProgress: false)
        }
        guard let results = await NimboPingService.shared.measureAll(targets) else { return }
        let ordered = results.map { ($0.key, $0.value) }
        IosComposeControllerKt.NimboUpdateIosPings(
            serverIds: ordered.map { $0.0 },
            values: ordered.map { KotlinInt(int: Int32($0.1)) },
            inProgress: false
        )
    }

    /// Сообщение пользователю: всплывает сейчас и остаётся в истории.
    ///
    /// Время форматируется здесь: у приложения есть локаль устройства, а
    /// общий модуль о ней ничего не знает.
    private func notify(_ kind: String, _ message: String) {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateFormat = "HH:mm"
        IosComposeControllerKt.NimboPushIosNotification(
            kind: kind,
            message: message,
            timeLabel: formatter.string(from: Date()),
            timestampSeconds: Int64(Date().timeIntervalSince1970)
        )
        // Полоса не должна висеть: на Android она гаснет сама через несколько
        // секунд, история при этом остаётся.
        DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
            IosComposeControllerKt.NimboDismissIosToast()
        }
    }

    /// Импорт подписки из строки: ссылка, конфигурация или содержимое QR.
    private func importSubscription(_ source: String) async {
        do {
            let profile = try await NimboSubscriptionImporter.importAndStage(source, vpn: vpn)
            synchronizeComposeState()
            notify("success", "Подписка добавлена: \(profile.servers.count) серверов")
            await measurePings()
        } catch {
            notify("error", NimboRedactor.redact(error.localizedDescription))
            await NimboDiagnostics.shared.record(
                .error,
                stage: .config,
                code: "IOS_SUBSCRIPTION_IMPORT_FAILED",
                message: NimboRedactor.redact(error.localizedDescription)
            )
        }
    }

    /// Импорт из файла профиля.
    ///
    /// Доступ к файлу за пределами песочницы даётся на время: без
    /// `startAccessingSecurityScopedResource` чтение упало бы на выбранном в
    /// «Файлах» документе.
    private func importFile(_ url: URL) async {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let text = try? String(contentsOf: url, encoding: .utf8) else { return }
        await importSubscription(text)
    }

    /// Замер по одному серверу: нажали на плашку — перемеряли только его.
    /// Гонять весь список ради одной строки долго и незачем.
    private func measurePing(_ serverID: String) async {
        guard let profile = try? NimboSubscriptionRepository.shared.loadProfile(),
              let server = profile.servers.first(where: { $0.id == serverID }),
              !server.host.isEmpty, server.port > 0 else { return }

        IosComposeControllerKt.NimboUpdateIosPings(
            serverIds: [serverID],
            values: [],
            inProgress: true
        )
        let value = await NimboPingService.shared.measureOne(host: server.host, port: server.port)
        IosComposeControllerKt.NimboUpdateIosPings(
            serverIds: [serverID],
            values: [KotlinInt(int: Int32(value))],
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
        case .connected:
            IosComposeControllerKt.NimboPushIosBurst(trigger: "connected")
        case .idle, .failed:
            finishSession()
            IosComposeControllerKt.NimboPushIosBurst(trigger: "disconnected")
            if case let .failed(_, message) = state {
                notify("error", message)
            }
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

    /// Замер всех узлов и подключение к лучшему.
    ///
    /// Замер делается заново: сохранённые числа могли быть сняты вчера, а узел
    /// с тех пор успел деградировать — именно от такого выбора человек и
    /// уходит, нажимая «авто».
    private func connectFastest() async {
        guard let profile = try? NimboSubscriptionRepository.shared.loadProfile() else { return }
        let candidates = profile.servers.filter {
            !$0.host.isEmpty && $0.port > 0 && !NimboStagingPayload.isAutoBalancer($0)
        }
        guard !candidates.isEmpty else {
            notify("error", "Нет серверов для выбора")
            return
        }

        IosComposeControllerKt.NimboPushIosBurst(trigger: "activity")
        IosComposeControllerKt.NimboUpdateIosPings(serverIds: [], values: [], inProgress: true)
        defer {
            IosComposeControllerKt.NimboUpdateIosPings(serverIds: [], values: [], inProgress: false)
        }
        // Пустой ответ означал бы «все узлы молчат»; когда замер уже идёт,
        // служба возвращает ничего — и повторять его незачем.
        guard let results = await NimboPingService.shared.measureAll(
            candidates.map { (id: $0.id, host: $0.host, port: $0.port) }
        ) else { return }
        let ordered = results.map { ($0.key, $0.value) }
        IosComposeControllerKt.NimboUpdateIosPings(
            serverIds: ordered.map { $0.0 },
            values: ordered.map { KotlinInt(int: Int32($0.1)) },
            inProgress: false
        )

        // Молчащий узел — не «ноль миллисекунд»: такие в выбор не идут.
        guard let best = results.filter({ $0.value > 0 }).min(by: { $0.value < $1.value }) else {
            // В сети, где проверка не проходит, узлы молчат все разом — а сам
            // туннель при этом поднимается. Оставлять человека без соединения
            // из-за неудавшегося замера незачем: подключаемся к выбранному.
            notify("error", "Проверка не прошла — подключаюсь к выбранному серверу")
            if vpn.state != .connected, vpn.state != .connecting {
                await vpn.connect()
            }
            return
        }
        let name = candidates.first { $0.id == best.key }?.name ?? ""
        await selectServer(best.key)
        notify("info", "Выбран \(name.isEmpty ? "самый быстрый узел" : name) · \(best.value) мс")
        if vpn.state != .connected, vpn.state != .connecting {
            await vpn.connect()
        }
    }

    /// Модуль файлом для системного окна обмена.
    ///
    /// Имя берётся из названия набора, но без символов, которые файловая система не
    /// примет: иначе выгрузка молча не состоится.
    private func exportModuleFile(name: String, text: String) -> URL? {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: " -_"))
        let safe = name.unicodeScalars.map { allowed.contains($0) ? Character($0) : "-" }
        let fileName = String(safe).trimmingCharacters(in: .whitespaces)
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(fileName.isEmpty ? "module" : fileName)
            .appendingPathExtension("conf")
        do {
            try text.data(using: .utf8)?.write(to: url, options: .atomic)
            return url
        } catch {
            notify("error", "Не удалось подготовить файл модуля")
            return nil
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

    /// Проверка обновлений.
    ///
    /// Поставить сборку сама iOS не даст — её подписывают снаружи. Всё
    /// остальное приложение делает само: узнаёт о версии, сообщает о ней и
    /// кладёт файл в «Файлы».
    ///
    /// Итог всегда виден подписью под кнопкой: молчание при отсутствии
    /// обновлений читалось как сломанная кнопка.
    private func checkForUpdate(manual: Bool = false) async {
        guard manual || NimboUpdateCenter.automaticCheckIsDue else { return }
        if manual {
            IosComposeControllerKt.NimboUpdateIosProgress(status: "Проверяю…", downloadStatus: "")
        }
        let result = await NimboUpdateChecker.check(
            currentVersion: NimboPlatformInfo.displayVersion,
            channel: NimboUpdateCenter.channel
        )
        NimboUpdateCenter.rememberCheck()

        switch result {
        case let .available(release):
            updateRelease = release
            updatePageUrl = release.pageUrl
            IosComposeControllerKt.NimboUpdateIosRelease(
                version: release.version,
                notes: String(release.notes.prefix(400))
            )
            let saved = release.assetName.flatMap(NimboUpdateCenter.downloadedFile(named:))
            IosComposeControllerKt.NimboUpdateIosProgress(
                status: "Установлена \(NimboPlatformInfo.displayVersion)",
                downloadStatus: saved == nil ? "" : "Уже скачано — открыть в «Файлах»"
            )
            updateFileUrl = nil
            await NimboUpdateCenter.announce(release)
            if manual { notify("info", "Доступна версия \(release.version)") }
        case .upToDate:
            updateRelease = nil
            updatePageUrl = nil
            IosComposeControllerKt.NimboUpdateIosRelease(version: "", notes: "")
            IosComposeControllerKt.NimboUpdateIosProgress(
                status: "Установлена последняя версия · канал \(NimboUpdateCenter.channel.title)",
                downloadStatus: ""
            )
        case .failed:
            IosComposeControllerKt.NimboUpdateIosProgress(
                status: "Не удалось связаться с GitHub",
                downloadStatus: ""
            )
        }
    }

    /// Загрузка файла сборки в папку приложения и окно обмена поверх неё.
    ///
    /// Установить `.ipa` приложение не может — этим занимается инструмент
    /// подписи, поэтому наша задача довести файл до «Файлов».
    private func downloadUpdate() async {
        guard let release = updateRelease else {
            await checkForUpdate(manual: true)
            return
        }
        if let name = release.assetName, let saved = NimboUpdateCenter.downloadedFile(named: name) {
            updateFileUrl = saved
            return
        }
        IosComposeControllerKt.NimboUpdateIosProgress(
            status: "Доступна \(release.version)",
            downloadStatus: "Загружаю файл сборки…"
        )
        do {
            let url = try await NimboUpdateCenter.download(release)
            updateFileUrl = url
            IosComposeControllerKt.NimboUpdateIosProgress(
                status: "Доступна \(release.version)",
                downloadStatus: "Сохранено: \(url.lastPathComponent)"
            )
            notify("success", "Файл сборки сохранён в «Файлы» → Nimbo")
        } catch {
            IosComposeControllerKt.NimboUpdateIosProgress(
                status: "Доступна \(release.version)",
                downloadStatus: "Не удалось скачать: \(error.localizedDescription)"
            )
            notify("error", NimboRedactor.redact(error.localizedDescription))
        }
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
    private func publishMetrics() async {
        guard vpn.state == .connected else { return }
        // Показания спрашиваем у расширения: оно знает свой интерфейс и свою
        // занятую память, а приложение — ни того, ни другого.
        let reported = await vpn.tunnelMetrics()
        metrics.tick(reported: reported)
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
            notify("success", "Подписка обновлена: \(profile.servers.count) серверов")
            IosComposeControllerKt.NimboPushIosBurst(trigger: "activity")
            await NimboDiagnostics.shared.record(
                .info,
                stage: .config,
                code: "IOS_SUBSCRIPTION_REFRESHED",
                message: "Подписка обновлена и активный сервер повторно передан Packet Tunnel",
                metadata: ["servers": "\(profile.servers.count)"]
            )
        } catch {
            // Молчаливая неудача хуже ошибки: человек жмёт обновление и не
            // понимает, произошло ли что-нибудь.
            notify("error", NimboRedactor.redact(error.localizedDescription))
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
    static let nimboConnectFastest = Notification.Name("com.nimbo.action.connect-fastest")
    static let nimboCopyText = Notification.Name("com.nimbo.action.copy-text")
    static let nimboExportModule = Notification.Name("com.nimbo.action.export-module")
    static let nimboPingServer = Notification.Name("com.nimbo.action.ping-server")
    static let nimboPingAll = Notification.Name("com.nimbo.action.ping-all")
    static let nimboImportSubscription = Notification.Name("com.nimbo.action.import-subscription")
    static let nimboImportClipboard = Notification.Name("com.nimbo.action.import-clipboard")
    static let nimboImportFile = Notification.Name("com.nimbo.action.import-file")
    static let nimboScanQr = Notification.Name("com.nimbo.action.scan-qr")
    static let nimboOpenUrl = Notification.Name("com.nimbo.action.open-url")
    static let nimboRouting = Notification.Name("com.nimbo.action.routing")
    static let nimboOpenScreen = Notification.Name("com.nimbo.action.open-screen")
    static let nimboOpenUpdate = Notification.Name("com.nimbo.action.open-update")
    static let nimboCheckUpdate = Notification.Name("com.nimbo.action.check-update")
    static let nimboDownloadUpdate = Notification.Name("com.nimbo.action.download-update")
    static let nimboExportBackup = Notification.Name("com.nimbo.action.export-backup")
    static let nimboImportBackup = Notification.Name("com.nimbo.action.import-backup")
    static let nimboOpenSync = Notification.Name("com.nimbo.action.open-sync")
    static let nimboAppearanceChanged = Notification.Name("com.nimbo.action.appearance-changed")
}
