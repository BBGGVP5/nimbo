import SwiftUI

struct DiagnosticsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var vpn: VpnController
    @State private var exportedURL: URL?
    @State private var errorMessage: String?
    @State private var isPreparing = false

    var body: some View {
        NavigationStack {
            List {
                Section("Что попадёт в файл") {
                    Label("Этапы запуска приложения и туннеля", systemImage: "list.bullet.rectangle")
                    Label("Версии приложения, iOS и устройства", systemImage: "iphone")
                    Label("Короткие коды ошибок и состояние сети", systemImage: "waveform.path.ecg")
                }
                Section("Конфиденциальность") {
                    Text("Ссылки подписок, токены, UUID, пароли и IP-адреса маскируются до записи на диск.")
                }
                if let errorMessage {
                    Section("Ошибка") { Text(errorMessage).foregroundStyle(.red) }
                }
                Section {
                    Button {
                        prepareExport()
                    } label: {
                        Label(isPreparing ? "Подготовка…" : "Подготовить диагностику", systemImage: "square.and.arrow.up")
                    }
                    .disabled(isPreparing)

                    if let exportedURL {
                        ShareLink(item: exportedURL) {
                            Label("Отправить файл", systemImage: "paperplane.fill")
                        }
                    }
                }
            }
            .navigationTitle("Диагностика iOS")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
        }
    }

    private func prepareExport() {
        isPreparing = true
        errorMessage = nil
        Task {
            do {
                await NimboDiagnostics.shared.record(.info, stage: .app, code: "IOS_DIAGNOSTICS_EXPORT", message: "Пользователь подготовил диагностический пакет")
                var sections: [String: Data] = [:]
                if vpn.manager?.connection.status == .connected {
                    if let providerData = try? await vpn.providerDiagnostics() {
                        sections["packet-tunnel-provider.txt"] = providerData
                    }
                }
                let url = try await NimboDiagnostics.shared.exportBundle(additionalSections: sections)
                await MainActor.run { exportedURL = url; isPreparing = false }
            } catch {
                await MainActor.run { errorMessage = error.localizedDescription; isPreparing = false }
            }
        }
    }
}
