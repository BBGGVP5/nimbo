import SwiftUI

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            NimboPage {
                HStack(spacing: 14) {
                    Image("NimboCloudSymbol")
                        .resizable().scaledToFit().frame(width: 48, height: 48)
                        .accessibilityHidden(true)
                    NimboBrand()
                }
                .padding(.vertical, 8)
                .accessibilityElement(children: .combine)
                NimboSection(title: "ПРИЛОЖЕНИЕ") {
                    AboutRow(title: "Версия", value: NimboPlatformInfo.displayVersion)
                    Divider()
                    AboutRow(title: "Сборка", value: NimboPlatformInfo.buildNumber)
                }
                NimboSection(title: "УСТРОЙСТВО") {
                    AboutRow(title: "Система", value: NimboPlatformInfo.system)
                    Divider()
                    AboutRow(title: "Модель", value: NimboPlatformInfo.device)
                }
                NimboSection(title: "СЕТЬ") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("User-Agent").nimboFont(13, relativeTo: .caption)
                            .foregroundStyle(NimboNative.secondary)
                        Text(NimboPlatformInfo.userAgent).nimboFont(14, relativeTo: .subheadline)
                            .monospaced().textSelection(.enabled)
                    }
                }
            }
            .nimboSheetStyle()
            .navigationTitle("О приложении")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.frame(minWidth: 44, minHeight: 44)
                }
            }
        }
    }
}

private struct AboutRow: View {
    let title: String
    let value: String

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 16) {
                Text(title).fixedSize()
                Spacer(minLength: 12)
                Text(value).fixedSize().foregroundStyle(NimboNative.secondary)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                Text(value).foregroundStyle(NimboNative.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .textSelection(.enabled)
        .accessibilityElement(children: .combine)
    }
}
