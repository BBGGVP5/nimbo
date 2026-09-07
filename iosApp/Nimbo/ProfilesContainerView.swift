import SwiftUI

struct ProfilesContainerView: View {
    @EnvironmentObject private var vpn: VpnController
    @State private var importText = ""
    @State private var activeProfile = try? NimboSubscriptionRepository.shared.loadProfile()
    @State private var isImporting = false
    @State private var resultMessage: String?
    @State private var resultIsError = false

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [Color(red: 0.025, green: 0.08, blue: 0.16), Color(red: 0.02, green: 0.035, blue: 0.09)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 18) {
                        if let activeProfile {
                            activeConfigurationCard(activeProfile)
                            serverList(activeProfile)
                        }
                        importCard
                        privacyCard
                    }
                    .padding(20)
                }
            }
            .navigationTitle("Профили")
            .navigationBarTitleDisplayMode(.large)
        }
    }

    private func activeConfigurationCard(_ profile: NimboSubscriptionProfile) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Label(profile.title, systemImage: "checkmark.shield.fill")
                .font(.headline)
                .foregroundStyle(.green)
            HStack {
                Label("\(profile.servers.count) серверов", systemImage: "server.rack")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Spacer()
                Button {
                    Task { await refreshProfile() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .buttonStyle(.bordered)
                .disabled(isImporting || vpn.state == .connected || vpn.state == .connecting)
                .accessibilityLabel("Обновить подписку")
                Button(role: .destructive) {
                    removeConfiguration()
                } label: {
                    Image(systemName: "trash")
                }
                .buttonStyle(.bordered)
                .accessibilityLabel("Удалить конфигурацию")
            }
        }
        .nimboCard()
    }

    private func serverList(_ profile: NimboSubscriptionProfile) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Серверы")
                .font(.headline)
            ForEach(profile.servers) { server in
                Button {
                    select(server)
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: server.id == NimboConfigurationStore.shared.activeServerID
                              ? "checkmark.circle.fill" : "globe")
                            .font(.title3)
                            .foregroundStyle(server.id == NimboConfigurationStore.shared.activeServerID ? .blue : .secondary)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(server.name)
                                .font(.body.weight(.semibold))
                                .lineLimit(1)
                            Text(server.connectionLabel.isEmpty ? server.protocol.uppercased() : server.connectionLabel)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        Spacer()
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if server.id != profile.servers.last?.id {
                    Divider().opacity(0.28)
                }
            }
        }
        .nimboCard()
    }

    private var importCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label("Добавить подписку", systemImage: "plus.circle.fill")
                .font(.headline)
            Text("Вставьте ссылку подписки, share-ссылку или готовый Xray JSON.")
                .font(.footnote)
                .foregroundStyle(.secondary)

            TextEditor(text: $importText)
                .font(.system(.body, design: .monospaced))
                .frame(minHeight: 118)
                .padding(10)
                .scrollContentBackground(.hidden)
                .background(.black.opacity(0.18), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay {
                    if importText.isEmpty {
                        Text("https://…  или  vless://…")
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 18)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                            .allowsHitTesting(false)
                    }
                }

            if let resultMessage {
                Text(resultMessage)
                    .font(.footnote)
                    .foregroundStyle(resultIsError ? .orange : .green)
            }

            Button {
                Task { await importConfiguration() }
            } label: {
                HStack {
                    if isImporting { ProgressView().tint(.white) }
                    Text(isImporting ? "Импортируем…" : "Импортировать")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(isImporting || importText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .nimboCard()
    }

    private var privacyCard: some View {
        Label(
            "Секреты хранятся в Keychain. В логи не попадают URL подписки, UUID и ключи.",
            systemImage: "lock.fill"
        )
        .font(.footnote)
        .foregroundStyle(.secondary)
        .nimboCard()
    }

    private func importConfiguration() async {
        let source = importText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !source.isEmpty else { return }
        isImporting = true
        resultMessage = nil
        defer { isImporting = false }

        do {
            let resolved = try await resolve(source)
            let profile = try NimboSubscriptionRepository.shared.importPayload(
                resolved.data,
                source: resolved.source
            )
            guard let selected = profile.selectedServer else {
                throw NimboSubscriptionRepositoryError.serverNotFound
            }
            try await vpn.stageConfiguration(data: NimboSubscriptionRepository.shared.stagingData(for: selected))
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

    private func resolve(_ source: String) async throws -> ResolvedConfiguration {
        if let url = URL(string: source), ["http", "https"].contains(url.scheme?.lowercased() ?? "") {
            let request = NimboNetworkSession.subscriptionRequest(url: url)
            let (data, response) = try await NimboNetworkSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
                throw ProfilesImportError.http((response as? HTTPURLResponse)?.statusCode ?? -1)
            }
            guard !data.isEmpty, data.count <= 15 * 1_024 * 1_024 else {
                throw ProfilesImportError.invalidSize
            }
            await NimboDiagnostics.shared.record(
                .info,
                stage: .config,
                code: "IOS_SUBSCRIPTION_DOWNLOADED",
                message: "Ответ подписки загружен",
                metadata: [
                    "bytes": "\(data.count)",
                    "http_status": "\(http.statusCode)",
                    "content_type": http.value(forHTTPHeaderField: "Content-Type") ?? "unknown"
                ]
            )
            return ResolvedConfiguration(
                data: data,
                source: source
            )
        }
        guard let data = source.data(using: .utf8), data.count <= 15 * 1_024 * 1_024 else {
            throw ProfilesImportError.invalidSize
        }
        return ResolvedConfiguration(data: data, source: nil)
    }

    private func refreshProfile() async {
        isImporting = true
        defer { isImporting = false }
        do {
            let profile = try await NimboSubscriptionRepository.shared.refresh()
            if let selected = profile.selectedServer {
                try await vpn.stageConfiguration(data: NimboSubscriptionRepository.shared.stagingData(for: selected))
            }
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

    private var statusText: String {
        switch vpn.state {
        case .connected: "VPN подключён"
        case .connecting, .preparing: "Подключение…"
        case .disconnecting: "Отключение…"
        case .failed: "Требуется проверка"
        case .idle: "Готово к подключению"
        }
    }

    private var statusIcon: String {
        vpn.state == .connected ? "lock.shield.fill" : "power"
    }
}

private struct ResolvedConfiguration {
    let data: Data
    let source: String?
}

private enum ProfilesImportError: LocalizedError {
    case http(Int)
    case invalidSize

    var errorDescription: String? {
        switch self {
        case let .http(code): "Сервис подписки вернул HTTP \(code) (IOS_SUBSCRIPTION_HTTP)."
        case .invalidSize: "Ответ подписки пуст или превышает 15 МиБ (IOS_SUBSCRIPTION_SIZE)."
        }
    }
}

private extension View {
    func nimboCard() -> some View {
        padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(.white.opacity(0.14), lineWidth: 1)
            }
    }
}
