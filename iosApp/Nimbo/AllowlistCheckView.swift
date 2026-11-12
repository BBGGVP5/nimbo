import Foundation
import Network
import SwiftUI

/// Network check: allowlist reachability, ping and speed, as three tabs in
/// one screen. All traffic goes through the route iOS currently selects, so
/// a connected Packet Tunnel is included.
struct AllowlistCheckView: View {
    enum Tool: String, CaseIterable, Identifiable {
        case check = "Проверка", ping = "Пинг", speed = "Скорость"
        var id: String { rawValue }
        var symbol: String {
            switch self {
            case .check: "checkmark.shield"
            case .ping: "dot.radiowaves.left.and.right"
            case .speed: "speedometer"
            }
        }
    }

    @State private var tool: Tool = .check

    var body: some View {
        NimboPage {
            NetworkToolTabs(selection: $tool)
            switch tool {
            case .check: AllowlistCheckPane()
            case .ping: PingPane()
            case .speed: SpeedPane()
            }
        }
        .navigationTitle("Проверка сети")
        .animation(.easeInOut(duration: 0.2), value: tool)
    }
}

// MARK: - Shared pieces

private enum NetworkCheckTone {
    case ok, warn, bad, idle
    var color: Color {
        switch self {
        case .ok: NimboNative.success
        case .warn: Color(red: 0.78, green: 0.52, blue: 0.08)
        case .bad: NimboNative.error
        case .idle: NimboNative.secondary
        }
    }
}

private struct NetworkToolTabs: View {
    @Binding var selection: AllowlistCheckView.Tool

    var body: some View {
        HStack(spacing: 6) {
            ForEach(AllowlistCheckView.Tool.allCases) { item in
                Button {
                    selection = item
                } label: {
                    Label(item.rawValue, systemImage: item.symbol)
                        .nimboFont(14, relativeTo: .subheadline, weight: .semibold)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .foregroundStyle(selection == item ? NimboNative.onAccent : NimboNative.ink)
                        .background(selection == item ? NimboNative.accent : Color.clear,
                                    in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selection == item ? .isSelected : [])
            }
        }
        .padding(4)
        .background(NimboNative.panel, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(NimboNative.border, lineWidth: 1))
    }
}

/// Large status card: eyebrow, headline, coloured dot and a short detail line.
private struct NetworkHeroCard<Footer: View>: View {
    let eyebrow: String
    let title: String
    let detail: String
    let tone: NetworkCheckTone
    var busy = false
    @ViewBuilder var footer: () -> Footer

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                if busy {
                    ProgressView().controlSize(.small).tint(tone.color)
                } else {
                    Circle().fill(tone.color).frame(width: 10, height: 10)
                }
                Text(eyebrow.uppercased())
                    .nimboFont(12, relativeTo: .caption, weight: .semibold)
                    .foregroundStyle(NimboNative.secondary)
            }
            Text(title)
                .nimboFont(30, relativeTo: .largeTitle, weight: .bold)
                .fixedSize(horizontal: false, vertical: true)
            if !detail.isEmpty {
                Text(detail).nimboFont(15, relativeTo: .subheadline)
                    .foregroundStyle(NimboNative.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            footer()
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(NimboNative.panel, in: RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(NimboNative.border, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}

private struct NetworkMetric: View {
    let label: String
    let value: String
    let unit: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label.uppercased()).nimboFont(11, relativeTo: .caption2, weight: .semibold)
                .foregroundStyle(NimboNative.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value).nimboFont(22, relativeTo: .title2, weight: .bold).monospacedDigit()
                Text(unit).nimboFont(12, relativeTo: .caption).foregroundStyle(NimboNative.secondary)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(NimboNative.raised, in: RoundedRectangle(cornerRadius: 16))
        .accessibilityElement(children: .combine)
    }
}

private struct NetworkChips: View {
    let items: [String]
    let selected: String
    let pick: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items, id: \.self) { item in
                    Button { pick(item) } label: {
                        Text(item).nimboFont(14, relativeTo: .subheadline, weight: .medium)
                            .padding(.horizontal, 14)
                            .frame(minHeight: 40)
                            .background(item == selected ? NimboNative.raised : Color.clear,
                                        in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12)
                                .strokeBorder(NimboNative.border, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

private func milliseconds(since start: Date) -> Int {
    max(1, Int(Date().timeIntervalSince(start) * 1000))
}

// MARK: - Allowlist check

private struct AllowlistCheckPane: View {
    private enum Group: String, CaseIterable {
        case international = "Международные", local = "Локальные", telemetry = "Статистика", infra = "Инфраструктура"
    }

    private struct Target: Identifiable {
        let id: String
        let group: Group
        let url: URL?
        var host: String? = nil
        var exactStatus: Int? = nil
        var acceptClientErrors = false
    }

    private struct Result {
        let available: Bool
        let elapsedMs: Int?
        let detail: String
    }

    private static let targets: [Target] = [
        Target(id: "Google", group: .international, url: URL(string: "https://www.google.com/generate_204"), exactStatus: 204),
        Target(id: "Cloudflare", group: .international, url: URL(string: "https://cp.cloudflare.com/generate_204"), exactStatus: 204),
        Target(id: "GitHub", group: .international, url: URL(string: "https://github.com/")),
        Target(id: "Telegram", group: .international, url: URL(string: "https://telegram.org/")),
        Target(id: "Apple", group: .international, url: URL(string: "https://captive.apple.com/hotspot-detect.html"), exactStatus: 200),
        Target(id: "Яндекс", group: .local, url: URL(string: "https://ya.ru/")),
        Target(id: "VK", group: .local, url: URL(string: "https://vk.com/")),
        Target(id: "Google Analytics", group: .telemetry, url: URL(string: "https://www.google-analytics.com/g/collect"), acceptClientErrors: true),
        Target(id: "Google Static", group: .telemetry, url: URL(string: "https://www.gstatic.com/generate_204"), exactStatus: 204),
        Target(id: "Яндекс Метрика", group: .telemetry, url: URL(string: "https://mc.yandex.ru/watch/0"), acceptClientErrors: true),
        Target(id: "DNS Google", group: .infra, url: nil, host: "8.8.8.8"),
        Target(id: "DNS Cloudflare", group: .infra, url: nil, host: "1.1.1.1")
    ]

    @State private var results: [String: Result] = [:]
    @State private var isChecking = false
    @State private var checkedAt: Date?

    private var done: Int { results.count }
    private var total: Int { Self.targets.count }

    private var verdict: (title: String, detail: String, tone: NetworkCheckTone) {
        if isChecking {
            return ("Проверяем…", "\(done) из \(total) адресов", .idle)
        }
        guard !results.isEmpty else { return ("Не проверено", "Нажмите «Проверить», чтобы начать.", .idle) }
        let ok = { (group: Group) in Self.targets.contains { $0.group == group && results[$0.id]?.available == true } }
        let available = results.values.filter(\.available).count
        if available == 0 {
            return ("Нет интернета", "Ни один контрольный адрес не ответил.", .bad)
        }
        if ok(.international) && ok(.local) && Double(available) / Double(total) >= 0.7 {
            return ("Сеть в норме", "Доступно \(available) из \(total) адресов.", .ok)
        }
        if !ok(.international) && ok(.local) {
            return ("Белые списки", "Открываются только локальные сайты — похоже на ограничение по белому списку.", .warn)
        }
        return ("Ограниченный доступ", "Доступно \(available) из \(total) адресов.", .warn)
    }

    var body: some View {
        let state = verdict
        NetworkHeroCard(eyebrow: "Состояние сети", title: state.title, detail: state.detail,
                        tone: state.tone, busy: isChecking) {
            if isChecking {
                ProgressView(value: Double(done), total: Double(total)).tint(NimboNative.accent)
            } else if let checkedAt {
                Text("Проверено в \(checkedAt.formatted(date: .omitted, time: .shortened))")
                    .nimboFont(13, relativeTo: .caption).foregroundStyle(NimboNative.secondary)
            }
        }
        HStack(spacing: 10) {
            Button {
                Task { await runChecks() }
            } label: {
                Label(isChecking ? "Проверяем…" : (results.isEmpty ? "Проверить" : "Проверить снова"),
                      systemImage: "arrow.clockwise")
            }
            .buttonStyle(NimboActionStyle(prominent: true))
            .disabled(isChecking)
            if !results.isEmpty && !isChecking {
                ShareLink(item: report) {
                    Label("Отправить", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(NimboActionStyle())
            }
        }
        ForEach(Group.allCases, id: \.self) { group in
            NimboSection(title: group.rawValue.uppercased()) {
                let rows = Self.targets.filter { $0.group == group }
                ForEach(rows) { target in
                    row(target)
                    if target.id != rows.last?.id { Divider() }
                }
            }
        }
        NimboNotice(title: "Что означает результат",
                    detail: "Это доступность контрольных адресов на этом устройстве. Проверка не доказывает доступность всех приложений или отсутствие блокировки на другом маршруте.",
                    symbol: "info.circle").nimboCard()
            .task { if results.isEmpty { await runChecks() } }
    }

    private func row(_ target: Target) -> some View {
        let result = results[target.id]
        let tone: NetworkCheckTone = result == nil ? .idle : (result!.available ? .ok : .bad)
        return HStack(spacing: 12) {
            Image(systemName: result == nil ? (isChecking ? "circle.dotted" : "circle")
                  : (result!.available ? "checkmark.circle.fill" : "xmark.circle.fill"))
                .foregroundStyle(tone.color)
                .accessibilityHidden(true)
            Text(target.id)
            Spacer(minLength: 8)
            Text(result.map { $0.available ? "\($0.elapsedMs ?? 0) мс" : $0.detail } ?? (isChecking ? "…" : "—"))
                .nimboFont(13, relativeTo: .caption).monospacedDigit()
                .foregroundStyle(NimboNative.secondary)
        }
        .frame(minHeight: 40)
        .accessibilityElement(children: .combine)
    }

    private var report: String {
        var lines = ["Nimbo — проверка сети", verdict.title]
        if let checkedAt { lines.append(checkedAt.formatted(date: .abbreviated, time: .shortened)) }
        for target in Self.targets {
            let r = results[target.id]
            lines.append("\(r?.available == true ? "✓" : "✗") \(target.id): " +
                         (r.map { $0.available ? "\($0.elapsedMs ?? 0) мс" : $0.detail } ?? "—"))
        }
        return lines.joined(separator: "\n")
    }

    @MainActor private func runChecks() async {
        guard !isChecking else { return }
        isChecking = true
        results = [:]
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 3.2
        configuration.timeoutIntervalForResource = 4
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel(); isChecking = false; checkedAt = Date() }
        await withTaskGroup(of: (String, Result).self) { group in
            for target in Self.targets {
                group.addTask { (target.id, await Self.check(target, session: session)) }
            }
            for await (id, result) in group {
                results[id] = result
            }
        }
    }

    private static func check(_ target: Target, session: URLSession) async -> Result {
        if let host = target.host {
            let started = Date()
            let ok = await TcpProbe.connect(host: host, port: 53, timeout: 3)
            return Result(available: ok, elapsedMs: ok ? milliseconds(since: started) : nil,
                          detail: ok ? "TCP" : "Нет ответа")
        }
        guard let url = target.url else { return Result(available: false, elapsedMs: nil, detail: "—") }
        var request = URLRequest(url: url)
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        let started = Date()
        do {
            let (_, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                return Result(available: false, elapsedMs: nil, detail: "Нет HTTP-ответа")
            }
            let sameHost = http.url?.host?.lowercased() == url.host?.lowercased()
            let accepted = target.exactStatus.map { http.statusCode == $0 }
                ?? ((200...399).contains(http.statusCode)
                    || (target.acceptClientErrors && (400...499).contains(http.statusCode)))
            let available = sameHost && accepted
            return Result(available: available, elapsedMs: available ? milliseconds(since: started) : nil,
                          detail: sameHost ? "HTTP \(http.statusCode)" : "Переадресация")
        } catch {
            return Result(available: false, elapsedMs: nil,
                          detail: (error as? URLError)?.code == .timedOut ? "Таймаут" : "Нет ответа")
        }
    }
}

// MARK: - Ping

/// One TCP handshake through the current route; resumes exactly once.
private enum TcpProbe {
    private final class Once: @unchecked Sendable {
        private let lock = NSLock()
        private var done = false
        func claim() -> Bool { lock.lock(); defer { lock.unlock() }; if done { return false }; done = true; return true }
    }

    static func connect(host: String, port: UInt16, timeout: TimeInterval) async -> Bool {
        guard let endpointPort = NWEndpoint.Port(rawValue: port) else { return false }
        let connection = NWConnection(host: NWEndpoint.Host(host), port: endpointPort, using: .tcp)
        let queue = DispatchQueue(label: "com.nimbo.network-check.tcp")
        let once = Once()
        return await withCheckedContinuation { continuation in
            let finish: (Bool) -> Void = { ok in
                guard once.claim() else { return }
                connection.cancel()
                continuation.resume(returning: ok)
            }
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready: finish(true)
                case .failed, .cancelled: finish(false)
                case .waiting: finish(false)
                default: break
                }
            }
            connection.start(queue: queue)
            queue.asyncAfter(deadline: .now() + timeout) { finish(false) }
        }
    }
}

private struct PingPane: View {
    private static let quickTargets = ["google.com", "ya.ru", "1.1.1.1", "8.8.8.8", "telegram.org"]
    private static let attempts = 5

    @AppStorage("com.nimbo.networkCheck.pingTarget") private var target = "google.com"
    @State private var samples: [Int?] = []
    @State private var isRunning = false

    private var received: [Int] { samples.compactMap { $0 } }
    private var average: Int? { received.isEmpty ? nil : received.reduce(0, +) / received.count }
    private var jitter: Int? {
        guard received.count > 1 else { return nil }
        let diffs = zip(received.dropFirst(), received).map { abs($0 - $1) }
        return diffs.reduce(0, +) / diffs.count
    }
    private var loss: Int? {
        samples.isEmpty ? nil : (samples.count - received.count) * 100 / samples.count
    }

    private var tone: NetworkCheckTone {
        guard let average, !isRunning else { return samples.isEmpty || isRunning ? .idle : .bad }
        if (loss ?? 0) > 20 || average > 300 { return .bad }
        if (loss ?? 0) > 0 || average > 120 { return .warn }
        return .ok
    }

    var body: some View {
        NetworkHeroCard(eyebrow: "Задержка · TCP 443",
                        title: average.map { "\($0) мс" } ?? (isRunning ? "Измеряем…" : (samples.isEmpty ? "—" : "Нет ответа")),
                        detail: samples.isEmpty ? "Время TCP-рукопожатия с адресом через текущий маршрут."
                            : "\(target) · \(received.count) из \(samples.count) ответов",
                        tone: tone, busy: isRunning) {
            if !samples.isEmpty {
                HStack(spacing: 8) {
                    NetworkMetric(label: "Мин", value: received.min().map(String.init) ?? "—", unit: "мс")
                    NetworkMetric(label: "Джиттер", value: jitter.map(String.init) ?? "—", unit: "мс")
                    NetworkMetric(label: "Потери", value: loss.map(String.init) ?? "—", unit: "%")
                }
            }
        }
        VStack(alignment: .leading, spacing: 10) {
            Text("АДРЕС").nimboFont(12, relativeTo: .caption, weight: .semibold)
                .foregroundStyle(NimboNative.secondary)
            TextField("Домен или IP-адрес", text: $target)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .submitLabel(.go)
                .onSubmit { Task { await run() } }
                .padding(14)
                .background(NimboNative.panel, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(NimboNative.border, lineWidth: 1))
            Text("БЫСТРЫЙ ВЫБОР").nimboFont(12, relativeTo: .caption, weight: .semibold)
                .foregroundStyle(NimboNative.secondary)
            NetworkChips(items: Self.quickTargets, selected: target.trimmingCharacters(in: .whitespaces)) {
                target = $0
            }
        }
        Button {
            Task { await run() }
        } label: {
            Label(isRunning ? "Измеряем…" : "Запустить пинг", systemImage: "dot.radiowaves.left.and.right")
        }
        .buttonStyle(NimboActionStyle(prominent: true))
        .disabled(isRunning || host.isEmpty)
        if !samples.isEmpty {
            NimboSection(title: "ПОПЫТКИ") {
                ForEach(Array(samples.enumerated()), id: \.offset) { index, sample in
                    HStack {
                        Text("#\(index + 1)")
                        Spacer()
                        Text(sample.map { "\($0) мс" } ?? "нет ответа")
                            .monospacedDigit()
                            .foregroundStyle(sample == nil ? NimboNative.error : NimboNative.secondary)
                    }
                    .nimboFont(14, relativeTo: .subheadline)
                }
            }
        }
    }

    private var host: String {
        var value = target.trimmingCharacters(in: .whitespacesAndNewlines)
        if let url = URL(string: value), let parsed = url.host, value.contains("://") { value = parsed }
        return value.split(separator: "/").first.map(String.init) ?? ""
    }

    @MainActor private func run() async {
        guard !isRunning, !host.isEmpty else { return }
        isRunning = true
        samples = []
        defer { isRunning = false }
        for _ in 0..<Self.attempts {
            if Task.isCancelled { return }
            let started = Date()
            let ok = await TcpProbe.connect(host: host, port: 443, timeout: 3)
            samples.append(ok ? milliseconds(since: started) : nil)
            try? await Task.sleep(nanoseconds: 200_000_000)
        }
    }
}

// MARK: - Speed

private struct SpeedPane: View {
    private enum Phase: Equatable { case idle, latency, download, upload, done, failed(String) }

    private static let downloadBytes = 25_000_000
    private static let uploadBytes = 8_000_000

    @State private var phase: Phase = .idle
    @State private var download: Double?
    @State private var upload: Double?
    @State private var latency: Int?
    @State private var task: Task<Void, Never>?

    private var running: Bool { [.latency, .download, .upload].contains(phase) }
    private var headline: Double? { phase == .upload ? upload : (download ?? upload) }

    var body: some View {
        NetworkHeroCard(eyebrow: phaseLabel, title: headline.map { String(format: "%.1f Мбит/с", $0) } ?? (running ? "Измеряем…" : "—"),
                        detail: detailText, tone: tone, busy: running) {
            SpeedGauge(value: headline ?? 0, active: running)
                .frame(height: 120)
                .frame(maxWidth: .infinity)
            HStack(spacing: 8) {
                NetworkMetric(label: "Загрузка", value: download.map { String(format: "%.1f", $0) } ?? "—", unit: "Мбит/с")
                NetworkMetric(label: "Отдача", value: upload.map { String(format: "%.1f", $0) } ?? "—", unit: "Мбит/с")
                NetworkMetric(label: "Пинг", value: latency.map(String.init) ?? "—", unit: "мс")
            }
        }
        Button {
            if running { task?.cancel(); phase = .idle } else { start() }
        } label: {
            Label(running ? "Остановить" : (phase == .idle ? "Начать тест" : "Повторить"),
                  systemImage: running ? "stop.fill" : "speedometer")
        }
        .buttonStyle(NimboActionStyle(prominent: !running))
        NimboNotice(title: "Сколько трафика уйдёт",
                    detail: "Около 33 МБ: \(Self.downloadBytes / 1_000_000) МБ загрузки и \(Self.uploadBytes / 1_000_000) МБ отдачи через speed.cloudflare.com. На мобильном интернете учитывайте тариф.",
                    symbol: "info.circle").nimboCard()
            .onDisappear { task?.cancel() }
    }

    private var phaseLabel: String {
        switch phase {
        case .idle: "Скорость"
        case .latency: "Пинг"
        case .download: "Загрузка"
        case .upload: "Отдача"
        case .done: "Готово"
        case .failed: "Ошибка"
        }
    }

    private var detailText: String {
        if case .failed(let message) = phase { return message }
        return phase == .idle ? "Загрузка и отдача через текущий маршрут, включая VPN." : ""
    }

    private var tone: NetworkCheckTone {
        if case .failed = phase { return .bad }
        guard phase == .done, let download else { return .idle }
        return download >= 20 ? .ok : download >= 5 ? .warn : .bad
    }

    private func start() {
        download = nil; upload = nil; latency = nil
        task = Task { @MainActor in
            let configuration = URLSessionConfiguration.ephemeral
            configuration.timeoutIntervalForRequest = 15
            configuration.timeoutIntervalForResource = 40
            configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
            let session = URLSession(configuration: configuration)
            defer { session.invalidateAndCancel() }
            do {
                phase = .latency
                var best: Int?
                for _ in 0..<3 {
                    let started = Date()
                    _ = try await session.data(from: URL(string: "https://speed.cloudflare.com/__down?bytes=0")!)
                    let ms = milliseconds(since: started)
                    best = min(best ?? ms, ms)
                }
                latency = best
                phase = .download
                let downStart = Date()
                let (data, _) = try await session.data(from: URL(string: "https://speed.cloudflare.com/__down?bytes=\(Self.downloadBytes)")!)
                download = Self.mbps(bytes: data.count, since: downStart)
                phase = .upload
                var request = URLRequest(url: URL(string: "https://speed.cloudflare.com/__up")!)
                request.httpMethod = "POST"
                request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
                let upStart = Date()
                _ = try await session.upload(for: request, from: Data(count: Self.uploadBytes))
                upload = Self.mbps(bytes: Self.uploadBytes, since: upStart)
                phase = .done
            } catch is CancellationError {
                phase = .idle
            } catch {
                if Task.isCancelled { phase = .idle; return }
                phase = .failed((error as? URLError)?.code == .timedOut
                                ? "Сервер теста не ответил вовремя." : "Не удалось связаться с сервером теста.")
            }
        }
    }

    private static func mbps(bytes: Int, since start: Date) -> Double {
        let seconds = max(Date().timeIntervalSince(start), 0.001)
        return Double(bytes) * 8 / seconds / 1_000_000
    }
}

/// Half-ring gauge on a logarithmic-ish scale that grows with the measured value.
private struct SpeedGauge: View {
    let value: Double
    let active: Bool

    private var maximum: Double {
        [50.0, 100, 250, 500, 1000].first { value <= $0 * 0.9 } ?? 1000
    }

    var body: some View {
        GeometryReader { proxy in
            let side = min(proxy.size.width, proxy.size.height * 2)
            ZStack {
                Circle().trim(from: 0, to: 0.5)
                    .stroke(NimboNative.raised, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                Circle().trim(from: 0, to: 0.5 * min(value / maximum, 1))
                    .stroke(NimboNative.accent, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                    .animation(.easeOut(duration: 0.4), value: value)
            }
            .rotationEffect(.degrees(180))
            .frame(width: side, height: side)
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .top)
            .overlay(alignment: .bottom) {
                HStack {
                    Text("0")
                    Spacer()
                    Text("\(Int(maximum))")
                }
                .nimboFont(11, relativeTo: .caption2)
                .foregroundStyle(NimboNative.secondary)
                .frame(width: side)
            }
            .opacity(active || value > 0 ? 1 : 0.6)
        }
        .accessibilityHidden(true)
    }
}
