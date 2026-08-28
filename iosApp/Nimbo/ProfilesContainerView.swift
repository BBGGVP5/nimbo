import SwiftUI

struct ProfilesContainerView: View {
    @EnvironmentObject private var vpn: VpnController
    @State private var importText = ""
    @State private var activeDescription = NimboConfigurationStore.shared.displayDescription
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
                        if let activeDescription {
                            activeConfigurationCard(activeDescription)
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

    private func activeConfigurationCard(_ description: String) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Label("Активная конфигурация", systemImage: "checkmark.shield.fill")
                .font(.headline)
                .foregroundStyle(.green)
            Text(description)
                .font(.body.weight(.semibold))
                .lineLimit(2)
            HStack {
                Label(statusText, systemImage: statusIcon)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Spacer()
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
            try NimboConfigurationStore.shared.save(
                configuration: resolved.data,
                source: resolved.source,
                description: resolved.description
            )
            try await vpn.stageConfiguration(data: resolved.data)
            activeDescription = resolved.description
            importText = ""
            resultIsError = false
            resultMessage = "Конфигурация добавлена. При первом подключении iOS запросит разрешение VPN."
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
            var request = URLRequest(url: url)
            request.timeoutInterval = 25
            request.cachePolicy = .reloadIgnoringLocalCacheData
            let (data, response) = try await NimboNetworkSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
                throw ProfilesImportError.http((response as? HTTPURLResponse)?.statusCode ?? -1)
            }
            guard !data.isEmpty, data.count <= 15 * 1_024 * 1_024 else {
                throw ProfilesImportError.invalidSize
            }
            return ResolvedConfiguration(
                data: data,
                source: source,
                description: url.host.map { "Подписка · \($0)" } ?? "Подписка"
            )
        }
        guard let data = source.data(using: .utf8), data.count <= 15 * 1_024 * 1_024 else {
            throw ProfilesImportError.invalidSize
        }
        return ResolvedConfiguration(data: data, source: nil, description: "Импортированная конфигурация")
    }

    private func removeConfiguration() {
        do {
            try NimboConfigurationStore.shared.removeAll()
            Task { try? await vpn.clearConfiguration() }
            activeDescription = nil
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
    let description: String
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
