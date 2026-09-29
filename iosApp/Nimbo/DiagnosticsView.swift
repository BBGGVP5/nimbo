import SwiftUI

struct DiagnosticsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var vpn: VpnController
    @State private var exportedURL: URL?
    @State private var errorMessage: String?
    @State private var isPreparing = false

    var body: some View {
        NavigationStack {
            NimboPage {
                NimboNotice(title: "Проверить подключение", detail: "Проверьте установку или подготовьте файл с событиями приложения и туннеля.", symbol: "waveform.path.ecg")
                NavigationLink {
                    ReadinessView().environmentObject(vpn)
                } label: {
                    HStack {
                        Label("Готовность установки", systemImage: "checklist")
                        Spacer(minLength: 8)
                        Image(systemName: "chevron.right").accessibilityHidden(true)
                    }
                }
                .buttonStyle(NimboActionStyle())
                NavigationLink {
                    AllowlistCheckView()
                } label: {
                    HStack {
                        Label("Проверка БС", systemImage: "network")
                        Spacer(minLength: 8)
                        Image(systemName: "chevron.right").accessibilityHidden(true)
                    }
                }
                .buttonStyle(NimboActionStyle())
                NimboSection(title: "ДИАГНОСТИЧЕСКИЙ ФАЙЛ") {
                    Label("Этапы запуска приложения и туннеля", systemImage: "list.bullet.rectangle")
                    Divider()
                    Label("Версии приложения, iOS и устройства", systemImage: "iphone")
                    Divider()
                    Label("Короткие коды ошибок и состояние сети", systemImage: "waveform.path.ecg")
                }
                NimboNotice(title: "Конфиденциальность", detail: "Ссылки подписок, токены, UUID, пароли и IP-адреса маскируются до записи на диск.", symbol: "lock").nimboCard()
                if let errorMessage {
                    NimboNotice(title: "Не удалось подготовить файл", detail: errorMessage,
                                symbol: "exclamationmark.circle", tint: NimboNative.error).nimboCard()
                }
                if isPreparing {
                    NimboNotice(title: "Подготовка диагностики…", detail: "Собираем события приложения и доступные данные туннеля.", busy: true).nimboCard()
                }
                Button(action: prepareExport) {
                    Label(isPreparing ? "Подготовка…" : "Подготовить диагностику", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(NimboActionStyle(prominent: true))
                .disabled(isPreparing)
                if let exportedURL {
                    NimboNotice(title: "Файл готов", detail: exportedURL.lastPathComponent,
                                symbol: "checkmark.circle", tint: NimboNative.success).nimboCard()
                    ShareLink(item: exportedURL) {
                        Label("Отправить файл", systemImage: "square.and.arrow.up")
                    }.buttonStyle(NimboActionStyle())
                }
            }
            .nimboSheetStyle()
            .navigationTitle("Диагностика iOS")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.frame(minWidth: 44, minHeight: 44)
                }
            }
        }
    }

    private func prepareExport() {
        guard !isPreparing else { return }
        isPreparing = true
        errorMessage = nil
        exportedURL = nil
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
                await MainActor.run { errorMessage = NimboRedactor.redact(error.localizedDescription); isPreparing = false }
            }
        }
    }
}
