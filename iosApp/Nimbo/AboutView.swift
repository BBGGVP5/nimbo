import SwiftUI

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Nimbo") {
                    AboutRow(title: "Версия", value: NimboPlatformInfo.displayVersion)
                    AboutRow(title: "Сборка", value: NimboPlatformInfo.buildNumber)
                }

                Section("Устройство") {
                    AboutRow(title: "Система", value: NimboPlatformInfo.system)
                    AboutRow(title: "Модель", value: NimboPlatformInfo.device)
                }

                Section("Сеть") {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("User-Agent")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(NimboPlatformInfo.userAgent)
                            .font(.footnote.monospaced())
                            .textSelection(.enabled)
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle("О приложении")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
        }
    }
}

private struct AboutRow: View {
    let title: String
    let value: String

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 16) {
            Text(title)
            Spacer(minLength: 12)
            Text(value)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.trailing)
                .textSelection(.enabled)
        }
    }
}
