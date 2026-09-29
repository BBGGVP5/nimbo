import SwiftUI

/// State is supplied exclusively by RootView's real download task.
struct NimboUpdateDownloadView: View {
    let version: String
    let isDownloading: Bool
    let fileURL: URL?
    let error: String?
    let retry: () -> Void
    let openRelease: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            NimboPage {
                NimboBrand()
                Text("Nimbo \(version)").nimboFont(24, relativeTo: .title2, weight: .semibold)
                if isDownloading {
                    VStack(alignment: .leading, spacing: 12) {
                        NimboNotice(title: "Загружаем файл сборки…", detail: "После загрузки файл будет доступен в «Файлы» → Nimbo → Обновления.", busy: true)
                        NimboOperationPhrase(operation: .updateDownload, isActive: isDownloading)
                    }.nimboCard()
                } else if let error {
                    NimboNotice(title: "Не удалось скачать", detail: error, symbol: "exclamationmark.circle", tint: NimboNative.error).nimboCard()
                    Button("Попробовать снова", action: retry).buttonStyle(NimboActionStyle(prominent: true))
                } else if let fileURL {
                    NimboNotice(title: "Файл сохранён", detail: fileURL.lastPathComponent, symbol: "checkmark.circle", tint: NimboNative.success).nimboCard()
                    ShareLink(item: fileURL) {
                        Label("Сохранить или передать файл", systemImage: "square.and.arrow.up")
                    }.buttonStyle(NimboActionStyle(prominent: true))
                }
                Text("Установите IPA тем же способом, которым установили Nimbo. Приложение не устанавливает обновление автоматически.")
                    .nimboFont(14, relativeTo: .subheadline).foregroundStyle(NimboNative.secondary)
                Button("Страница выпуска", action: openRelease).buttonStyle(NimboActionStyle())
            }
            .nimboSheetStyle()
            .navigationTitle("Обновление")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.frame(minWidth: 44, minHeight: 44)
                }
            }
        }
    }
}
