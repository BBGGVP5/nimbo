import SwiftUI

struct NimboOnDemandSettingsView: View {
    @EnvironmentObject private var vpn: VpnController
    @Environment(\.dismiss) private var dismiss
    @State private var settings = NimboOnDemandSettings.load()
    @State private var ssids = NimboOnDemandSettings.load().trustedSSIDs.joined(separator: "\n")
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        Form {
            Section {
                Toggle("On-demand", isOn: $settings.enabled)
                Toggle("Wi-Fi", isOn: $settings.wifi)
                Toggle("Мобильная сеть", isOn: $settings.cellular)
            } footer: {
                Text("iOS подключит Nimbo автоматически в выбранных сетях. Проверка доступности Google или DNS не требуется.")
            }
            Section {
                TextEditor(text: $ssids).frame(minHeight: 90)
                    .autocorrectionDisabled().textInputAutocapitalization(.never)
                    .accessibilityLabel("Доверенные Wi-Fi сети, по одной на строку")
            } header: { Text("Доверенные Wi-Fi сети") } footer: {
                Text("Точное имя сети (SSID), по одному на строку. В этих сетях Nimbo отключится. Имя Wi-Fi не является проверкой безопасности сети.")
            }
            Section {
                Text("Ручное отключение приостанавливает on-demand. Нажмите подключение или сохраните правила снова, чтобы возобновить. Изменения требуют настроенного совместимого профиля.")
                    .font(.footnote)
            }
            if let error { Section { Text(error).foregroundStyle(.red) } }
        }
        .navigationTitle("Автоподключение")
        .disabled(saving)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                Button(saving ? "Сохранение…" : "Сохранить") {
                    Task { await save() }
                }.disabled(saving)
            }
        }
    }

    @MainActor private func save() async {
        saving = true
        defer { saving = false }
        do {
            settings.trustedSSIDs = ssids.components(separatedBy: .newlines)
            try await vpn.saveOnDemandSettings(settings.validated())
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}
