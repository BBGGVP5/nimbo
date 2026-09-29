import Foundation
import SwiftUI

/// A reachability check through the route currently selected by iOS. It uses
/// normal URLSession traffic, so a connected Packet Tunnel is included.
struct AllowlistCheckView: View {
    private struct Target: Identifiable {
        let id: String
        let group: String
        let url: URL
        let exactStatus: Int?
    }

    private struct Result {
        let available: Bool
        let elapsedMs: Int?
        let detail: String
    }

    private static let targets: [Target] = [
        Target(id: "Google Static", group: "Международные", url: URL(string: "https://www.gstatic.com/generate_204")!, exactStatus: 204),
        Target(id: "Cloudflare", group: "Международные", url: URL(string: "https://cp.cloudflare.com/generate_204")!, exactStatus: 204),
        Target(id: "Apple", group: "Международные", url: URL(string: "https://captive.apple.com/hotspot-detect.html")!, exactStatus: 200),
        Target(id: "GitHub", group: "Международные", url: URL(string: "https://github.com/")!, exactStatus: nil),
        Target(id: "Яндекс", group: "Локальные", url: URL(string: "https://ya.ru/")!, exactStatus: nil),
        Target(id: "VK", group: "Локальные", url: URL(string: "https://vk.com/")!, exactStatus: nil),
        Target(id: "Google Analytics", group: "Статистика", url: URL(string: "https://www.google-analytics.com/")!, exactStatus: nil),
        Target(id: "Яндекс Метрика", group: "Статистика", url: URL(string: "https://mc.yandex.ru/")!, exactStatus: nil)
    ]

    @State private var results: [String: Result] = [:]
    @State private var isChecking = false

    var body: some View {
        NimboPage {
            NimboNotice(title: "Проверка БС", detail: "Проверяем HTTPS-сервисы через текущую сеть. Если VPN подключён, запросы проходят через него.", symbol: "network")
            ForEach(["Международные", "Локальные", "Статистика"], id: \.self) { group in
                NimboSection(title: group.uppercased()) {
                    ForEach(Self.targets.filter { $0.group == group }) { target in
                        HStack(spacing: 12) {
                            Image(systemName: results[target.id]?.available == true ? "checkmark.circle.fill" :
                                  results[target.id] == nil ? "circle.dotted" : "xmark.circle.fill")
                                .foregroundStyle(results[target.id]?.available == true ? NimboNative.success :
                                                 results[target.id] == nil ? NimboNative.accent : NimboNative.error)
                            Text(target.id)
                            Spacer(minLength: 8)
                            Text(results[target.id].map { $0.available ? "\($0.elapsedMs ?? 0) мс" : $0.detail } ?? "Ожидание")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .frame(minHeight: 42)
                        if target.id != Self.targets.last(where: { $0.group == group })?.id { Divider() }
                    }
                }
            }
            Button {
                Task { await runChecks() }
            } label: {
                Label(isChecking ? "Проверяем…" : "Проверить снова", systemImage: "arrow.clockwise")
            }
            .buttonStyle(NimboActionStyle(prominent: true))
            .disabled(isChecking)
            NimboNotice(title: "Что означает результат", detail: "Это доступность контрольных сайтов на этом устройстве. Проверка не доказывает доступность всех приложений или отсутствие блокировки на другом маршруте.", symbol: "info.circle").nimboCard()
        }
        .navigationTitle("Проверка БС")
        .task { await runChecks() }
    }

    @MainActor private func runChecks() async {
        guard !isChecking else { return }
        isChecking = true
        results = [:]
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 3
        configuration.timeoutIntervalForResource = 4
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel(); isChecking = false }
        for target in Self.targets {
            if Task.isCancelled { return }
            results[target.id] = await Self.check(target, session: session)
        }
    }

    private static func check(_ target: Target, session: URLSession) async -> Result {
        var request = URLRequest(url: target.url)
        request.httpMethod = "GET"
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let started = Date()
        do {
            let (_, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                return Result(available: false, elapsedMs: nil, detail: "Нет HTTP-ответа")
            }
            let sameHost = http.url?.host?.lowercased() == target.url.host?.lowercased()
            let accepted = target.exactStatus.map { http.statusCode == $0 }
                ?? (200...299).contains(http.statusCode)
            let available = sameHost && accepted
            return Result(available: available,
                          elapsedMs: available ? max(1, Int(Date().timeIntervalSince(started) * 1000)) : nil,
                          detail: sameHost ? "HTTP \(http.statusCode)" : "Переадресация")
        } catch {
            return Result(available: false, elapsedMs: nil,
                          detail: (error as? URLError)?.code == .timedOut ? "Таймаут" : "Нет ответа")
        }
    }
}
