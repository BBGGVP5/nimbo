import SwiftUI

struct NimboOnDemandSettingsView: View {
    @EnvironmentObject private var vpn: VpnController
    @Environment(\.dismiss) private var dismiss
    @State private var settings = NimboOnDemandSettings.load()
    @State private var ssids = NimboOnDemandSettings.load().trustedSSIDs.joined(separator: "\n")
    @State private var saving = false
    @State private var loading = true
    @State private var armed = false
    @State private var error: String?

    var body: some View {
        Form {
            Section {
                Toggle("Автоподключение", isOn: $settings.enabled)
                Toggle("Wi-Fi", isOn: $settings.wifi)
                Toggle("Мобильная сеть", isOn: $settings.cellular)
            } footer: {
                Text("iOS подключит Nimbo автоматически в выбранных сетях. Проверка доступности Google или DNS не требуется.")
            }
            Section {
                TextField("Имя сети — по одному на строку", text: $ssids, axis: .vertical).lineLimit(2...6)
                    .autocorrectionDisabled().textInputAutocapitalization(.never)
                    .accessibilityLabel("Доверенные Wi-Fi сети, по одной на строку")
            } header: { Text("Доверенные Wi-Fi сети") } footer: {
                Text("Точное имя сети (SSID), по одному на строку. В этих сетях Nimbo отключится. Имя Wi-Fi не является проверкой безопасности сети.")
            }
            Section {
                Text(armed ? "Системные правила активны" : "Системные правила выключены или на паузе").font(.subheadline)
                Text("Ручное отключение приостанавливает on-demand. Нажмите подключение или сохраните правила снова, чтобы возобновить. Изменения требуют настроенного совместимого профиля.")
                    .font(.footnote)
            }
            if let error { Section { Text(error).foregroundStyle(.red) } }
        }
        .navigationTitle("Автоподключение")
        .disabled(saving || loading)
        .interactiveDismissDisabled(saving)
        .task {
            do {
                let snapshot = try await vpn.loadOnDemandSettings()
                guard !Task.isCancelled else { return }
                settings = snapshot.settings
                ssids = settings.trustedSSIDs.joined(separator: "\n")
                armed = snapshot.armed
            } catch { self.error = error.localizedDescription }
            loading = false
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() }.disabled(saving) }
            ToolbarItem(placement: .confirmationAction) {
                Button(saving ? "Сохранение…" : "Сохранить") {
                    Task { await save() }
                }.disabled(saving || loading)
            }
        }
    }

    @MainActor private func save() async {
        guard !saving, !loading else { return }
        saving = true
        error = nil
        defer { saving = false }
        do {
            settings.trustedSSIDs = ssids.components(separatedBy: .newlines)
            try await vpn.saveOnDemandSettings(settings.validated())
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}
