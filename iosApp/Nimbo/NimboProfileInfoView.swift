import SwiftUI

struct NimboProfileInfoView: View {
    let title: String
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @AppStorage("com.nimbo.appearance.textScale") private var textScale = 1.0
    private var meta: NimboSubscriptionMeta { NimboSubscriptionMetaStore.current }
    private var profile: NimboSubscriptionProfile? { try? NimboSubscriptionRepository.shared.loadProfile() }
    private var columns: [GridItem] {
        Array(repeating: GridItem(.flexible(), spacing: 9),
              count: dynamicTypeSize >= .xxxLarge || textScale > 1.15 ? 1 : 2)
    }

    var body: some View {
        NavigationStack {
            NimboPage {
                HStack(spacing: 13) {
                    Text(String((meta.title ?? title).prefix(2)).uppercased())
                        .nimboFont(18, weight: .semibold)
                        .frame(width: 51, height: 51)
                        .background(NimboNative.raised, in: RoundedRectangle(cornerRadius: 14))
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(meta.title ?? title).nimboFont(20, relativeTo: .title3, weight: .semibold)
                        Text("Информация о подписке").nimboFont(12, relativeTo: .caption)
                            .foregroundStyle(NimboNative.secondary)
                    }.fixedSize(horizontal: false, vertical: true)
                }
                LazyVGrid(columns: columns, alignment: .leading, spacing: 9) {
                    fact(meta.trafficLabel.isEmpty ? "Нет данных" : meta.trafficLabel, "Трафик подписки", "chart.bar")
                    fact(meta.expiryLabel.isEmpty ? "Не указан" : meta.expiryLabel, "Срок действия", "calendar")
                    fact("\(profile?.servers.count ?? 0)", "Доступные серверы", "server.rack")
                    fact(meta.updatedAt > 0 ? meta.updatedLabel : "Ещё не обновлялась", "Последнее обновление", "arrow.clockwise")
                }
                if let description = meta.announce, !description.isEmpty {
                    Divider()
                    Text(description).nimboFont(14).foregroundStyle(NimboNative.secondary)
                        .fixedSize(horizontal: false, vertical: true).textSelection(.enabled)
                }
                Divider()
                if let url = externalURL(meta.supportUrl) {
                    Link(destination: url) { Label("Поддержка", systemImage: "headphones") }
                        .buttonStyle(NimboActionStyle())
                }
                if let url = externalURL(meta.websiteUrl) {
                    Link(destination: url) { Label("Сайт провайдера", systemImage: "arrow.up.right") }
                        .buttonStyle(NimboActionStyle())
                }
            }
            .nimboSheetStyle()
            .navigationTitle("О подписке")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.frame(minWidth: 44, minHeight: 44)
                }
            }
        }
        .presentationDragIndicator(.visible)
    }

    private func fact(_ value: String, _ label: String, _ symbol: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: symbol).foregroundStyle(NimboNative.secondary).accessibilityHidden(true)
            Text(value).nimboFont(15, weight: .semibold)
            Text(label).nimboFont(12, relativeTo: .caption).foregroundStyle(NimboNative.secondary)
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(13).frame(maxWidth: .infinity, alignment: .leading)
        .background(NimboNative.raised, in: RoundedRectangle(cornerRadius: 10))
        .accessibilityElement(children: .combine)
    }

    private func externalURL(_ value: String?) -> URL? {
        guard let value, let url = URL(string: value),
              ["https", "http", "tg", "mailto"].contains(url.scheme?.lowercased() ?? "") else { return nil }
        return url
    }
}
