import SwiftUI

/// Presented by RootView from the shared Settings connection row.
struct NimboCoreSettingsView: View {
    @EnvironmentObject private var vpn: VpnController
    @Environment(\.dismiss) private var dismiss
    @AppStorage(NimboCorePreference.defaultsKey) private var storedCore = "auto"
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(NimboCorePreference.allCases, id: \.rawValue) { core in
                        Button { select(core) } label: {
                            HStack {
                                Text(core.title)
                                Spacer()
                                if !core.isAvailable {
                                    Text("Недоступно").foregroundStyle(.secondary)
                                }
                                if storedCore == core.rawValue {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                        .disabled(!core.isAvailable || vpn.isSavingCorePreference ||
                                  vpn.state == .preparing || vpn.state == .connecting || vpn.state == .disconnecting)
                        .accessibilityLabel(core.title + (core.isAvailable ? "" : " — недоступно"))
                        .accessibilityValue(storedCore == core.rawValue ? "Выбрано" : "")
                    }
                } header: {
                    Text("Ядро VPN")
                } footer: {
                    Text("Выбор применяется при следующем подключении. Текущее подключение продолжает работать. Auto выбирает ядро по профилю. AWG — для конфигураций AmneziaWG и WireGuard в формате INI. Mihomo пока недоступно для VPN на iOS.")
                }
                if NimboCorePreference(rawValue: storedCore) == nil {
                    Text("Сохранено неизвестное ядро. Выберите Auto, Xray или AWG.")
                        .foregroundStyle(.red)
                }
                if let errorMessage {
                    Text(errorMessage).foregroundStyle(.red)
                }
            }
            .navigationTitle("Подключение")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
        }
    }

    private func select(_ core: NimboCorePreference) {
        Task { @MainActor in
            do {
                try await vpn.setCorePreference(core)
                errorMessage = nil
            } catch {
                errorMessage = NimboRedactor.redact(error.localizedDescription)
            }
        }
    }
}
