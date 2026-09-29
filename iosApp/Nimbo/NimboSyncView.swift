import SwiftUI

struct NimboSyncView: View {
    @StateObject private var engine = NimboSyncEngine()
    @Environment(\.dismiss) private var dismiss
    @State private var manualLink = ""
    @State private var showScanner = false

    private var canStart: Bool {
        switch engine.stage {
        case .idle, .completed, .failed: return true
        default: return false
        }
    }

    var body: some View {
        NavigationStack {
            NimboPage {
                NimboNotice(title: "Перенос подписок и настроек", detail: "Откройте синхронизацию на компьютере или Android, покажите QR и отсканируйте его здесь.", symbol: "arrow.left.arrow.right")
                stageCard
                if case .chooseDirection = engine.stage { directionButtons }
                if canStart {
                    Button { showScanner = true } label: {
                        Label("Сканировать QR", systemImage: "qrcode.viewfinder")
                    }.buttonStyle(NimboActionStyle(prominent: true))
                    NimboSection(title: "ИЛИ ВСТАВЬТЕ ССЫЛКУ") {
                        TextField("nimbo-sync://pair?…", text: $manualLink, axis: .vertical)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                            .padding(12).frame(minHeight: 48)
                            .background(NimboNative.raised, in: RoundedRectangle(cornerRadius: 14))
                            .accessibilityLabel("Ссылка синхронизации")
                        Button("Начать") { engine.start(link: manualLink) }
                            .buttonStyle(NimboActionStyle())
                            .disabled(manualLink.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
                NimboNotice(title: "Одна сеть Wi-Fi", detail: "Устройства должны быть в одной сети. При первом запуске разрешите доступ к локальной сети, чтобы связаться со вторым устройством.", symbol: "wifi").nimboCard()
            }
            .nimboSheetStyle()
            .navigationTitle("Синхронизация")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.frame(minWidth: 44, minHeight: 44)
                }
            }
            .sheet(isPresented: $showScanner) {
                NimboQrScannerView(purpose: .sync) { scanned in
                    showScanner = false
                    engine.start(link: scanned)
                }
            }
        }
    }

    @ViewBuilder private var stageCard: some View {
        switch engine.stage {
        case .idle:
            EmptyView()
        case .connecting:
            NimboNotice(title: "Связываемся с устройством…", detail: "Устанавливаем соединение в локальной сети.", busy: true).nimboCard()
        case let .awaitingApproval(code):
            NimboNotice(title: "Подтвердите на втором устройстве", detail: code.map { "Код сверки: \($0)" } ?? "Ждём подтверждения", symbol: "person.badge.clock").nimboCard()
        case let .chooseDirection(peer):
            NimboNotice(title: "Устройство \(peer) готово", detail: "Выберите направление переноса", symbol: "arrow.left.arrow.right").nimboCard()
        case .working:
            VStack(alignment: .leading, spacing: 12) {
                NimboNotice(title: "Переносим данные…", detail: "Дождитесь подтверждения получения.", busy: true)
                NimboOperationPhrase(operation: .syncTransfer, isActive: true)
            }.nimboCard()
        case let .completed(summary):
            NimboNotice(title: "Готово", detail: summary, symbol: "checkmark.circle", tint: NimboNative.success).nimboCard()
        case let .failed(reason):
            NimboNotice(title: "Не удалось синхронизировать", detail: reason, symbol: "exclamationmark.circle", tint: NimboNative.error).nimboCard()
        }
    }

    private var directionButtons: some View {
        NimboSection(title: "НАПРАВЛЕНИЕ ПЕРЕНОСА") {
            Button { engine.commit(direction: "desktop_to_android") } label: {
                directionLabel("Забрать на iPhone", subtitle: "Подписки и настройки со второго устройства", symbol: "arrow.down")
            }.buttonStyle(NimboActionStyle(prominent: true))
            Button { engine.commit(direction: "android_to_desktop") } label: {
                directionLabel("Отправить с iPhone", subtitle: "Перенести свои данные на второе устройство", symbol: "arrow.up")
            }.buttonStyle(NimboActionStyle())
        }
    }

    private func directionLabel(_ title: String, subtitle: String, symbol: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Label(title, systemImage: symbol)
            Text(subtitle).nimboFont(13, relativeTo: .caption)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

