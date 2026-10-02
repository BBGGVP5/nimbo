import SwiftUI

struct ProfilesContainerView: View {
    @EnvironmentObject private var vpn: VpnController
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage("com.nimbo.appearance.textScale") private var textScale = 1.0
    @State private var importText = ""
    @State private var fullConfiguration = try? NimboConfigurationStore.shared.loadFullConfiguration()
    @State private var activeProfile = try? NimboSubscriptionRepository.shared.loadProfile()
    @State private var isImporting = false
    @State private var selectingServerID: String?
    @State private var isRemoving = false
    @State private var meta = NimboSubscriptionMetaStore.current
    @State private var resultMessage: String?
    @State private var resultIsError = false
    @State private var serversExpanded = false
    @State private var showInfo = false
    @State private var confirmRemoval = false

    private var isWorking: Bool { isImporting || selectingServerID != nil || isRemoving || vpn.switchingServerID != nil }

    private func displayTitle(_ profile: NimboSubscriptionProfile) -> String {
        let title = meta.title?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return title.isEmpty ? profile.title : title
    }

    private var toolsLayout: AnyLayout {
        dynamicTypeSize >= .xxxLarge || textScale > 1.15
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 10))
            : AnyLayout(HStackLayout(alignment: .top, spacing: 10))
    }

    var body: some View {
        NavigationStack {
            NimboPage {
                if let fullConfiguration {
                    NimboMihomoProfileCard(full: fullConfiguration).environmentObject(vpn)
                    Button(role: .destructive) { confirmRemoval = true } label: {
                        Label("Удалить конфигурацию", systemImage: "trash")
                    }.buttonStyle(NimboActionStyle()).disabled(isWorking)
                } else if let activeProfile {
                    activeConfigurationCard(activeProfile)
                } else {
                    NimboNotice(title: "Добавьте первую подписку", detail: "Серверы появятся после импорта ссылки или конфигурации.", symbol: "square.stack.3d.up").nimboCard()
                }
                if let resultMessage {
                    NimboNotice(title: resultIsError ? "Требует внимания" : "Состояние профиля",
                                detail: resultMessage,
                                symbol: resultIsError ? "exclamationmark.circle" : "checkmark.circle",
                                tint: resultIsError ? NimboNative.error : NimboNative.secondary,
                                busy: isWorking).nimboCard()
                }
                importCard
            }
            .nimboSheetStyle()
            .interactiveDismissDisabled(isWorking)
            .navigationTitle("Профили")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.frame(minWidth: 44, minHeight: 44).disabled(isWorking)
                }
            }
            .sheet(isPresented: $showInfo) {
                NimboProfileInfoView(title: activeProfile.map(displayTitle) ?? "Подписка")
            }
            .confirmationDialog("Удалить конфигурацию?", isPresented: $confirmRemoval, titleVisibility: .visible) {
                Button("Удалить конфигурацию", role: .destructive) { Task { await removeConfiguration() } }
                Button("Отмена", role: .cancel) {}
            }
        }
    }

    private func activeConfigurationCard(_ profile: NimboSubscriptionProfile) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Button { serversExpanded.toggle() } label: {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "square.stack.3d.up").accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 5) {
                        Text(displayTitle(profile)).nimboFont(17, weight: .semibold)
                            .fixedSize(horizontal: false, vertical: true)
                        Text("Серверов: \(profile.servers.count)").nimboFont(13, relativeTo: .caption)
                            .foregroundStyle(NimboNative.secondary)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: serversExpanded ? "chevron.up" : "chevron.down").accessibilityHidden(true)
                }.frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            }
            .buttonStyle(.plain)
            .accessibilityValue(serversExpanded ? "Список развёрнут" : "Список свёрнут")
            .accessibilityHint("Показать или скрыть серверы")
            if let announcement = meta.announce, !announcement.isEmpty {
                Text(announcement).nimboFont(14, relativeTo: .subheadline)
                    .foregroundStyle(NimboNative.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
            }
            toolsLayout {
                Button { showInfo = true } label: {
                    Label("Информация", systemImage: "info.circle")
                }.buttonStyle(NimboActionStyle()).accessibilityLabel("Информация о подписке")
                Button { Task { await refreshProfile() } } label: {
                    Label("Обновить", systemImage: "arrow.clockwise")
                }
                .buttonStyle(NimboActionStyle())
                .disabled(isWorking || vpn.state == .connected || vpn.state == .connecting || vpn.state == .preparing || vpn.state == .disconnecting)
            }
            Button(role: .destructive) { confirmRemoval = true } label: {
                Label("Удалить конфигурацию", systemImage: "trash")
                    .foregroundStyle(NimboNative.error)
            }.buttonStyle(NimboActionStyle()).disabled(isWorking)
            if serversExpanded {
                serverList(profile)
                    .transition(.opacity)
            }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.22), value: serversExpanded)
        .nimboCard()
    }

    private func serverList(_ profile: NimboSubscriptionProfile) -> some View {
        LazyVStack(alignment: .leading, spacing: 8) {
            ForEach(profile.servers) { server in
                serverRow(server, isSelected: server.id == profile.selectedServer?.id)
            }
        }
    }

    private func serverRow(_ server: NimboSubscriptionServer, isSelected: Bool) -> some View {
        Button { Task { await select(server) } } label: {
            serverRowLabel(server, isSelected: isSelected)
        }
        .buttonStyle(.plain)
        .disabled(isWorking)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("nimbo.profile.server.\(server.id)")
        .contextMenu {
            Button("Пинг сервера", systemImage: "gauge.with.dots.needle.67percent") {
                NotificationCenter.default.post(name: .nimboPingServer, object: server.id)
            }
        }
        .accessibilityAction(named: "Пинг сервера") {
            NotificationCenter.default.post(name: .nimboPingServer, object: server.id)
        }
    }

    private func serverRowLabel(_ server: NimboSubscriptionServer, isSelected: Bool) -> some View {
        HStack(alignment: .top, spacing: 12) {
            if selectingServerID == server.id {
                ProgressView().tint(NimboNative.accent).accessibilityHidden(true)
            } else {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isSelected ? NimboNative.ink : NimboNative.secondary)
                    .accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(server.name).nimboFont(16, weight: .semibold)
                    .fixedSize(horizontal: false, vertical: true)
                Text(server.connectionLabel.isEmpty ? server.protocol.uppercased() : server.connectionLabel)
                    .nimboFont(12, relativeTo: .caption).foregroundStyle(NimboNative.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
        .background(isSelected ? NimboNative.raised : Color.clear, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(NimboNative.accent, lineWidth: isSelected ? 2 : 0))
        .contentShape(RoundedRectangle(cornerRadius: 12))
    }

    private var importCard: some View {
        NimboSection(title: "ДОБАВИТЬ ПОДПИСКУ") {
            Text("Вставьте ссылку подписки, share-ссылку, Xray JSON или конфигурацию AWG/WireGuard.")
                .nimboFont(14, relativeTo: .subheadline).foregroundStyle(NimboNative.secondary)
            TextField("Ссылка или конфигурация", text: $importText, axis: .vertical)
                .nimboFont(16).monospaced()
                .textInputAutocapitalization(.never).autocorrectionDisabled()
                .lineLimit(2...8)
                .disabled(isWorking)
                .padding(10)
                .background(NimboNative.raised, in: RoundedRectangle(cornerRadius: 14))
                .accessibilityLabel("Ссылка подписки или конфигурация")
            Button { Task { await importConfiguration() } } label: {
                HStack {
                    if isImporting { ProgressView().tint(NimboNative.onAccent) }
                    Text(isImporting ? "Импортируем…" : "Импортировать")
                }
            }
            .buttonStyle(NimboActionStyle(prominent: true))
            .disabled(isWorking || importText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
    }

    private func importConfiguration() async {
        guard !isWorking else { return }
        let source = importText
        guard !source.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        isImporting = true
        resultMessage = nil
        defer { isImporting = false }

        do {
            let profile = try await NimboSubscriptionImporter.importProfile(source)
            activeProfile = profile
            fullConfiguration = try NimboConfigurationStore.shared.loadFullConfiguration()
            meta = NimboSubscriptionMetaStore.current
            importText = ""
            resultIsError = false
            resultMessage = "Добавлено серверов: \(profile.servers.count). При первом подключении iOS запросит разрешение VPN."
        } catch {
            resultIsError = true
            resultMessage = NimboRedactor.redact(error.localizedDescription)
            await NimboDiagnostics.shared.record(
                .error,
                stage: .config,
                code: "IOS_PROFILE_IMPORT_FAILED",
                message: NimboRedactor.redact(error.localizedDescription)
            )
        }
    }

    private func refreshProfile() async {
        guard !isWorking else { return }
        guard vpn.state != .connected, vpn.state != .connecting,
              vpn.state != .preparing, vpn.state != .disconnecting else {
            resultIsError = false
            resultMessage = "Отключите VPN для обновления подписки."
            return
        }
        isImporting = true
        resultIsError = false
        resultMessage = "Обновление подписки…"
        defer { isImporting = false }
        do {
            let profile = try await NimboSubscriptionRepository.shared.refresh()
            activeProfile = profile
            fullConfiguration = try NimboConfigurationStore.shared.loadFullConfiguration()
            meta = NimboSubscriptionMetaStore.current
            resultIsError = false
            resultMessage = "Подписка обновлена: \(profile.servers.count) серверов."
        } catch {
            resultIsError = true
            resultMessage = NimboRedactor.redact(error.localizedDescription)
        }
    }

    @MainActor
    private func select(_ server: NimboSubscriptionServer) async {
        guard !isWorking else { return }
        selectingServerID = server.id
        resultMessage = nil
        defer { selectingServerID = nil }
        do {
            let selection = try await vpn.selectServer(server.id)
            activeProfile = try NimboSubscriptionRepository.shared.loadProfile()
            resultIsError = false
            resultMessage = selection.reconnecting
                ? "Переключаемся на «\(selection.server.name)»…"
                : "Выбран сервер «\(selection.server.name)»."
        } catch {
            // Staging can fail after persistence; do not display stale selection or fake success.
            activeProfile = try? NimboSubscriptionRepository.shared.loadProfile()
            resultIsError = true
            resultMessage = NimboRedactor.redact(error.localizedDescription)
        }
    }

    @MainActor
    private func removeConfiguration() async {
        guard !isWorking else { return }
        isRemoving = true
        defer { isRemoving = false }
        do {
            try await vpn.clearConfiguration()
            try NimboConfigurationStore.shared.removeAll()
            NimboSubscriptionMetaStore.clear()
            meta = .empty
            activeProfile = nil
            fullConfiguration = nil
            resultMessage = nil
        } catch {
            resultIsError = true
            resultMessage = NimboRedactor.redact(error.localizedDescription)
        }
    }

}
