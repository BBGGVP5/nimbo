import SwiftUI

struct DiagnosticsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var vpn: VpnController
    @State private var preparedReport: NimboPreparedSupportReport?
    @State private var preparationTask: Task<Void, Never>?
    @State private var errorMessage: String?
    @State private var isPreparing = false

    var body: some View {
        NavigationStack {
            NimboPage {
                NimboNotice(title: "Проверить подключение", detail: "Проверьте установку или подготовьте безопасный отчёт о событиях приложения.", symbol: "waveform.path.ecg")
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
                NimboSection(title: "ОТЧЁТ О СОБЫТИЯХ ПРИЛОЖЕНИЯ") {
                    Label("Время, этапы и уровни событий приложения", systemImage: "list.bullet.rectangle")
                    Divider()
                    Label("Версии приложения, iOS и устройства", systemImage: "iphone")
                    Divider()
                    Label("До 200 событий и счётчики по этапам и уровням", systemImage: "waveform.path.ecg")
                }
                NimboNotice(title: "Конфиденциальность", detail: "В отчёт входят только версии и типизированные события. Тексты сообщений, метаданные, коды, идентификаторы и адреса исключены. Полные данные туннеля не собираются. Отправка — только по вашей команде.", symbol: "lock").nimboCard()
                if let errorMessage {
                    NimboNotice(title: "Не удалось подготовить файл", detail: errorMessage,
                                symbol: "exclamationmark.circle", tint: NimboNative.error).nimboCard()
                }
                if isPreparing {
                    NimboNotice(title: "Подготовка диагностики…", detail: "Читаем ограниченный фрагмент локальных событий приложения. Подключение не требуется.", busy: true).nimboCard()
                }
                Button(action: prepareExport) {
                    Label(isPreparing ? "Подготовка…" : "Подготовить предпросмотр", systemImage: "doc.text.magnifyingglass")
                }
                .buttonStyle(NimboActionStyle(prominent: true))
                .disabled(isPreparing)
                if let preparedReport {
                    NimboNotice(title: "Проверьте отчёт перед отправкой",
                                detail: "Ниже — всё содержимое файла (не более 64 КиБ). Пропуски указаны в omissions; счётчики относятся к включённым событиям. Новые события не меняют этот отчёт.",
                                symbol: "doc.text").nimboCard()
                    ScrollView([.vertical, .horizontal]) {
                        Text(preparedReport.preview)
                            .font(.system(.caption, design: .monospaced))
                            .textSelection(.enabled)
                            .fixedSize(horizontal: true, vertical: true)
                            .padding(12)
                    }
                    .frame(height: 280)
                    .nimboCard()
                    .accessibilityLabel("Предпросмотр всего отчёта")
                    ShareLink(item: preparedReport.url) {
                        Label("Поделиться этим отчётом", systemImage: "square.and.arrow.up")
                    }.buttonStyle(NimboActionStyle())
                }
            }
            .nimboSheetStyle()
            .navigationTitle("Диагностика iOS")
            .onDisappear {
                preparationTask?.cancel()
                preparationTask = nil
                isPreparing = false
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.frame(minWidth: 44, minHeight: 44)
                }
            }
        }
    }

    @MainActor
    private func prepareExport() {
        guard !isPreparing else { return }
        isPreparing = true
        errorMessage = nil
        preparedReport = nil
        preparationTask = Task { @MainActor in
            do {
                let report = try await NimboDiagnostics.shared.prepareSupportReport()
                try Task.checkCancellation()
                preparedReport = report
                isPreparing = false
            } catch is CancellationError {
                // Disappearance cancels this request; do not publish a late snapshot.
            } catch {
                guard !Task.isCancelled else { return }
                errorMessage = "Не удалось прочитать локальные события или сохранить отчёт. Попробуйте ещё раз."
                isPreparing = false
            }
        }
    }
}
