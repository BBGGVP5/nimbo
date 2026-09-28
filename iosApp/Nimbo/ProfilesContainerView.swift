import SwiftUI

struct ProfilesContainerView: View {
    @EnvironmentObject private var vpn: VpnController
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @AppStorage("com.nimbo.appearance.textScale") private var textScale = 1.0
    @State private var importText = ""
    @State private var activeProfile = try? NimboSubscriptionRepository.shared.loadProfile()
    @State private var isImporting = false
    @State private var resultMessage: String?
    @State private var resultIsError = false
    @State private var serversExpanded = false
    @State private var showInfo = false
    @State private var confirmRemoval = false

    private var toolsLayout: AnyLayout {
        dynamicTypeSize >= .xxxLarge || textScale > 1.15
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 10))
            : AnyLayout(HStackLayout(alignment: .top, spacing: 10))
    }

    var body: some View {
        NavigationStack {
            NimboPage {
                if let activeProfile {
                    activeConfigurationCard(activeProfile)
                } else {
                    NimboNotice(title: "Добавьте первую подписку", detail: "Серверы появятся после импорта ссылки или конфигурации.", symbol: "square.stack.3d.up").nimboCard()
                }
                if let resultMessage {
                    NimboNotice(title: resultIsError ? "Требует внимания" : "Состояние профиля",
                                detail: resultMessage,
                                symbol: resultIsError ? "exclamationmark.circle" : "checkmark.circle",
                                tint: resultIsError ? NimboNative.error : NimboNative.secondary,
                                busy: isImporting).nimboCard()
                }
                importCard
            }
            .nimboSheetStyle()
            .navigationTitle("Профили")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.frame(minWidth: 44, minHeight: 44)
                }
            }
            .sheet(isPresented: $showInfo) {
                NimboProfileInfoView(title: activeProfile?.title ?? "Подписка")
            }
            .confirmationDialog("Удалить конфигурацию?", isPresented: $confirmRemoval, titleVisibility: .visible) {
                Button("Удалить конфигурацию", role: .destructive, action: removeConfiguration)
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
                        Text(profile.title).nimboFont(17, weight: .semibold)
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
            toolsLayout {
                Button { showInfo = true } label: {
                    Label("Информация", systemImage: "info.circle")
                }.buttonStyle(NimboActionStyle()).accessibilityLabel("Информация о подписке")
                Button { Task { await refreshProfile() } } label: {
                    Label("Обновить", systemImage: "arrow.clockwise")
                }
                .buttonStyle(NimboActionStyle())
                .disabled(isImporting || vpn.state == .connected || vpn.state == .connecting || vpn.state == .preparing || vpn.state == .disconnecting)
            }
            Button(role: .destructive) { confirmRemoval = true } label: {
                Label("Удалить конфигурацию", systemImage: "trash")
                    .foregroundStyle(NimboNative.error)
            }.buttonStyle(NimboActionStyle()).disabled(isImporting)
            if serversExpanded {
                Divider()
                serverList(profile)
            }
        }.nimboCard()
    }

    private func serverList(_ profile: NimboSubscriptionProfile) -> some View {
        LazyVStack(alignment: .leading, spacing: 0) {
            ForEach(profile.servers) { server in
                Button { select(server) } label: {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: server.id == profile.selectedServer?.id ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(server.id == profile.selectedServer?.id ? NimboNative.ink : NimboNative.secondary)
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(server.name).nimboFont(16, weight: .semibold)
                                .fixedSize(horizontal: false, vertical: true)
                            Text(server.connectionLabel.isEmpty ? server.protocol.uppercased() : server.connectionLabel)
                                .nimboFont(12, relativeTo: .caption).foregroundStyle(NimboNative.secondary)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(isImporting)
                .accessibilityAddTraits(server.id == profile.selectedServer?.id ? [.isSelected] : [])
                if server.id != profile.servers.last?.id { Divider() }
            }
        }
    }

    private var importCard: some View {
        NimboSection(title: "ДОБАВИТЬ ПОДПИСКУ") {
            Text("Вставьте ссылку подписки, share-ссылку, Xray JSON или конфигурацию AWG/WireGuard.")
                .nimboFont(14, relativeTo: .subheadline).foregroundStyle(NimboNative.secondary)
            TextEditor(text: $importText)
                .nimboFont(16).monospaced()
                .textInputAutocapitalization(.never).autocorrectionDisabled()
                .frame(minHeight: 140)
                .padding(10)
                .scrollContentBackground(.hidden)
                .background(NimboNative.raised, in: RoundedRectangle(cornerRadius: 14))
                .accessibilityLabel("Ссылка подписки или конфигурация")
            Button { Task { await importConfiguration() } } label: {
                HStack {
                    if isImporting { ProgressView().tint(NimboNative.onAccent) }
                    Text(isImporting ? "Импортируем…" : "Импортировать")
                }
            }
            .buttonStyle(NimboActionStyle(prominent: true))
            .disabled(isImporting || importText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
    }

    private func importConfiguration() async {
        let source = importText
        guard !source.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        isImporting = true
        resultMessage = nil
        defer { isImporting = false }

        do {
            let profile = try await NimboSubscriptionImporter.importProfile(source)
            activeProfile = profile
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
        guard !isImporting else { return }
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
            resultIsError = false
            resultMessage = "Подписка обновлена: \(profile.servers.count) серверов."
        } catch {
            resultIsError = true
            resultMessage = NimboRedactor.redact(error.localizedDescription)
        }
    }

    private func select(_ server: NimboSubscriptionServer) {
        do {
            let selected = try NimboSubscriptionRepository.shared.select(serverID: server.id)
            Task {
                do {
                    try await vpn.stageConfiguration(data: NimboSubscriptionRepository.shared.stagingData(for: selected))
                } catch {
                    resultIsError = true
                    resultMessage = NimboRedactor.redact(error.localizedDescription)
                }
            }
            activeProfile = try NimboSubscriptionRepository.shared.loadProfile()
            resultIsError = false
            resultMessage = "Выбран сервер «\(selected.name)»."
        } catch {
            resultIsError = true
            resultMessage = NimboRedactor.redact(error.localizedDescription)
        }
    }

    private func removeConfiguration() {
        do {
            try NimboConfigurationStore.shared.removeAll()
            Task { try? await vpn.clearConfiguration() }
            activeProfile = nil
            resultMessage = nil
        } catch {
            resultIsError = true
            resultMessage = error.localizedDescription
        }
    }

}
