import NimboShared
import SwiftUI
import UIKit

extension Notification.Name {
    /// Kotlin сообщает, что серверы, пинги или состояние VPN поменялись (см. IosComposeController.kt).
    static let nimboHomeMapChanged = Notification.Name("com.nimbo.home-map.changed")
}

/// Снимок для карты: то же, что видит Compose-главная, без собственного источника данных.
struct NimboHomeMapSnapshot: Decodable, Equatable {
    struct Server: Decodable, Equatable, Identifiable {
        let id: String
        let name: String
        let description: String
        let ping: Int?
    }

    var vpnState: String
    var activeServerId: String?
    var activeServerName: String
    var pingInProgress: Bool
    var servers: [Server]

    static let empty = NimboHomeMapSnapshot(vpnState: "idle", activeServerId: nil,
                                            activeServerName: "", pingInProgress: false, servers: [])

    static func current() -> NimboHomeMapSnapshot {
        let json = IosComposeControllerKt.NimboIosHomeMapJson()
        guard let data = json.data(using: .utf8),
              let decoded = try? JSONDecoder().decode(NimboHomeMapSnapshot.self, from: data) else { return .empty }
        return decoded
    }

    var activeServer: Server? { servers.first { $0.id == activeServerId } }
}

/// Флаг страны на карте: все серверы этой страны в одной точке.
private struct NimboMapMarker: Identifiable, Equatable {
    let code: String
    let point: NimboGeoPoint
    let serverIDs: [String]
    /// Сервер, который выберется по нажатию на флаг: самый быстрый из замеренных.
    let preferredServerID: String
    let isActive: Bool
    var id: String { code }
}

/// Главный экран в виде карты: точки суши, флаги серверов, дуга «вы здесь» → сервер.
struct NimboMapHomeView: View {
    @EnvironmentObject private var vpn: VpnController
    @Environment(\.colorScheme) private var colorScheme
    @AppStorage("com.nimbo.appearance.accentHex") private var accentHex = "E8E8E8"
    @State private var snapshot = NimboHomeMapSnapshot.empty

    var bottomInset: CGFloat = 0
    var onOpenProfiles: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            NimboBrand()
            mapCard
            serverCard
            if case let .failed(_, message) = vpn.state {
                Text(NimboRedactor.redact(message))
                    .nimboFont(13, relativeTo: .footnote)
                    .foregroundStyle(NimboNative.error)
                    .lineLimit(3)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            connectButton
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, bottomInset + 8)
        .frame(maxWidth: 680)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .foregroundStyle(NimboNative.ink)
        .background(NimboNative.canvas.ignoresSafeArea())
        .onAppear(perform: reload)
        // Стабильный издатель без операторов, как у остальных уведомлений в RootView.
        .onReceive(NotificationCenter.default.publisher(for: .nimboHomeMapChanged)) { _ in
            if Thread.isMainThread { reload() } else { DispatchQueue.main.async { reload() } }
        }
    }

    private func reload() {
        let next = NimboHomeMapSnapshot.current()
        if next != snapshot { snapshot = next }
    }

    // MARK: - Карта

    private var mapCard: some View {
        GeometryReader { geometry in
            let size = geometry.size
            let map = NimboWorldMap.shared
            let here = map?.here() ?? NimboWorldMap.fallbackHere
            let pins = buildMarkers(map: map)
            let viewport = NimboMapViewport.fitting([here] + pins.map(\.point),
                                                    aspect: Double(size.width / max(size.height, 1)))
            let target = pins.first(where: \.isActive)?.point
            ZStack(alignment: .topLeading) {
                NimboMapDots(land: map?.land ?? [], step: map?.step ?? 1, viewport: viewport, size: size,
                             color: colorScheme == .dark ? Color.white.opacity(0.17) : Color.black.opacity(0.13))
                    .equatable()
                NimboMapRoute(from: viewport.project(here, in: size),
                              to: target.map { viewport.project($0, in: size) },
                              phase: routePhase, accent: accent, idleColor: NimboNative.secondary)
                youAreHere.position(viewport.project(here, in: size))
                ForEach(pins) { marker in
                    flagMarker(marker).position(viewport.project(marker.point, in: size))
                }
                statusPill.padding(12)
            }
            .frame(width: size.width, height: size.height)
        }
        .frame(minHeight: 240, maxHeight: .infinity)
        .background(NimboNative.panel, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(NimboNative.border, lineWidth: 1))
    }

    private func buildMarkers(map: NimboWorldMap?) -> [NimboMapMarker] {
        guard let map else { return [] }
        var order: [String] = []
        var grouped: [String: [NimboHomeMapSnapshot.Server]] = [:]
        for server in snapshot.servers {
            guard let code = NimboServerCountry.code(name: server.name, description: server.description),
                  map.countries[code] != nil else { continue }
            if grouped[code] == nil { order.append(code) }
            grouped[code, default: []].append(server)
        }
        return order.compactMap { code in
            guard let servers = grouped[code], let point = map.countries[code], let first = servers.first else { return nil }
            let fastest = servers.filter { ($0.ping ?? -1) > 0 }.min { ($0.ping ?? 0) < ($1.ping ?? 0) }
            return NimboMapMarker(code: code, point: point, serverIDs: servers.map(\.id),
                                  preferredServerID: (fastest ?? first).id,
                                  isActive: servers.contains { $0.id == snapshot.activeServerId })
        }
    }

    private var routePhase: NimboMapRoute.Phase {
        switch vpn.state {
        case .connected: .connected
        case .preparing, .connecting: .connecting
        default: .idle
        }
    }

    private var youAreHere: some View {
        VStack(spacing: 4) {
            Text("Вы здесь")
                .nimboFont(10, relativeTo: .caption2, weight: .semibold)
                .padding(.horizontal, 7)
                .padding(.vertical, 3)
                .background(NimboNative.panel.opacity(0.9), in: Capsule())
                .overlay(Capsule().strokeBorder(NimboNative.border, lineWidth: 1))
            Circle()
                .fill(accent)
                .frame(width: 10, height: 10)
                .overlay(Circle().strokeBorder(NimboNative.panel, lineWidth: 2))
                .background(Circle().fill(accent.opacity(0.25)).frame(width: 22, height: 22))
        }
        // Точка, а не подпись, должна стоять на координате.
        .offset(y: -11)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Вы здесь")
    }

    private func flagMarker(_ marker: NimboMapMarker) -> some View {
        let diameter: CGFloat = marker.isActive ? 40 : 30
        return Button {
            NotificationCenter.default.post(name: .nimboSelectServer, object: marker.preferredServerID)
        } label: {
            Text(NimboServerCountry.flag(for: marker.code))
                .font(.system(size: marker.isActive ? 22 : 16))
                .frame(width: diameter, height: diameter)
                .background(Circle().fill(NimboNative.panel))
                .overlay(Circle().strokeBorder(marker.isActive ? accent : NimboNative.border,
                                               lineWidth: marker.isActive ? 2 : 1))
                .shadow(color: .black.opacity(colorScheme == .dark ? 0.4 : 0.12), radius: 4, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(markerLabel(marker)))
        .accessibilityHint(Text(marker.isActive ? "Текущий сервер" : "Выбрать сервер этой страны"))
    }

    private func markerLabel(_ marker: NimboMapMarker) -> String {
        let name = NimboServerCountry.displayName(for: marker.code) ?? marker.code
        return marker.serverIDs.count > 1 ? "\(name), серверов: \(marker.serverIDs.count)" : name
    }

    private var statusPill: some View {
        let (text, color): (String, Color) = switch vpn.state {
        case .connected: ("Подключено", NimboNative.success)
        case .preparing, .connecting: ("Подключение…", accent)
        case .disconnecting: ("Отключение…", NimboNative.secondary)
        case .failed: ("Не удалось подключиться", NimboNative.error)
        case .idle: ("Не подключено", NimboNative.secondary)
        }
        return HStack(spacing: 6) {
            Circle().fill(color).frame(width: 7, height: 7)
            Text(text).nimboFont(12, relativeTo: .caption, weight: .semibold)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(NimboNative.panel.opacity(0.92), in: Capsule())
        .overlay(Capsule().strokeBorder(NimboNative.border, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }

    // MARK: - Карточка сервера и кнопка

    private var serverCard: some View {
        let server = snapshot.activeServer
        let code = server.flatMap { NimboServerCountry.code(name: $0.name, description: $0.description) }
        let serverName = cleanName(server?.name ?? snapshot.activeServerName)
        let title: String
        let subtitle: String
        if snapshot.servers.isEmpty {
            title = "Нет серверов"
            subtitle = "Добавьте подписку, чтобы выбрать сервер"
        } else if server == nil {
            title = snapshot.activeServerId == "nimbo:auto" ? "Автовыбор" : "Сервер не выбран"
            subtitle = snapshot.activeServerId == "nimbo:auto" ? "Самый быстрый из проверенных" : "Нажмите, чтобы выбрать"
        } else {
            title = NimboServerCountry.displayName(for: code) ?? serverName
            subtitle = serverName
        }
        return Button {
            if snapshot.servers.isEmpty {
                NotificationCenter.default.post(name: .nimboAddProfile, object: nil)
            } else {
                onOpenProfiles()
            }
        } label: {
            HStack(spacing: 12) {
                Text(server == nil ? "🌐" : NimboServerCountry.flag(for: code))
                    .font(.system(size: 26))
                    .frame(width: 44, height: 44)
                    .background(NimboNative.raised, in: Circle())
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).nimboFont(16, weight: .semibold).lineLimit(1)
                    Text(subtitle).nimboFont(13, relativeTo: .footnote)
                        .foregroundStyle(NimboNative.secondary).lineLimit(1)
                }
                Spacer(minLength: 8)
                if server != nil {
                    Text(pingLabel(server?.ping))
                        .nimboFont(13, relativeTo: .footnote, weight: .semibold)
                        .monospacedDigit()
                        .foregroundStyle(pingColor(server?.ping))
                }
                Image(systemName: snapshot.servers.isEmpty ? "plus" : "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(NimboNative.secondary)
                    .accessibilityHidden(true)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(NimboNative.panel, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(NimboNative.border, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityHint(Text(snapshot.servers.isEmpty ? "Добавить подписку" : "Открыть список серверов"))
    }

    private func cleanName(_ name: String) -> String {
        var scalars = Substring(name).unicodeScalars.drop { scalar in
            (0x1F1E6...0x1F1FF).contains(scalar.value) || scalar.properties.isWhitespace
        }
        if scalars.isEmpty { scalars = Substring(name).unicodeScalars[...] }
        return String(scalars)
    }

    private func pingLabel(_ ping: Int?) -> String {
        guard let ping else { return snapshot.pingInProgress ? "…" : "— мс" }
        return ping < 0 ? "нет ответа" : "\(ping) мс"
    }

    private func pingColor(_ ping: Int?) -> Color {
        guard let ping else { return NimboNative.secondary }
        if ping < 0 { return NimboNative.error }
        return ping <= 150 ? NimboNative.success : NimboNative.ink
    }

    private var connectButton: some View {
        let active = vpn.state == .connected || vpn.state == .connecting || vpn.state == .preparing
        return Button {
            NotificationCenter.default.post(name: .nimboToggleVpn, object: nil)
        } label: {
            Label(vpn.state == .disconnecting ? "Отключение…" : (active ? "Отключить" : "Подключить"),
                  systemImage: "power")
        }
        .buttonStyle(NimboActionStyle(prominent: !active))
        .disabled(vpn.state == .disconnecting || (!active && snapshot.servers.isEmpty))
    }

    /// Выбранный акцент оформления; светлое значение по умолчанию на светлой теме — тёмное, как в Compose.
    private var accent: Color {
        let hex = accentHex.trimmingCharacters(in: CharacterSet(charactersIn: "# ")).uppercased()
        if hex == "E8E8E8" && colorScheme == .light { return Color(red: 0x20 / 255, green: 0x20 / 255, blue: 0x20 / 255) }
        guard hex.count == 6, let value = UInt32(hex, radix: 16) else { return NimboNative.accent }
        return Color(red: Double((value >> 16) & 255) / 255,
                     green: Double((value >> 8) & 255) / 255,
                     blue: Double(value & 255) / 255)
    }
}

/// Точки суши. Отдельный Equatable-вид: перерисовывается только при смене окна или темы,
/// а не на каждом кадре анимации дуги.
private struct NimboMapDots: View, Equatable {
    let land: [NimboGeoPoint]
    let step: Double
    let viewport: NimboMapViewport
    let size: CGSize
    let color: Color

    static func == (lhs: NimboMapDots, rhs: NimboMapDots) -> Bool {
        lhs.land.count == rhs.land.count && lhs.step == rhs.step && lhs.viewport == rhs.viewport &&
            lhs.size == rhs.size && lhs.color == rhs.color
    }

    var body: some View {
        Canvas { context, canvasSize in
            guard viewport.lonSpan > 0, viewport.latSpan > 0 else { return }
            let cell = canvasSize.width / viewport.lonSpan * step
            let radius = min(max(cell * 0.3, 0.6), 3.2)
            var path = Path()
            for point in land where viewport.contains(point, margin: step) {
                let center = viewport.project(point, in: canvasSize)
                path.addEllipse(in: CGRect(x: center.x - radius, y: center.y - radius,
                                           width: radius * 2, height: radius * 2))
            }
            context.fill(path, with: .color(color))
        }
        .accessibilityHidden(true)
    }
}

/// Дуга от «вы здесь» до сервера. Анимация идёт от часов TimelineView,
/// а не от издателей состояния: подписки на них здесь нет вовсе.
private struct NimboMapRoute: View {
    enum Phase: Equatable { case idle, connecting, connected }

    let from: CGPoint
    let to: CGPoint?
    let phase: Phase
    let accent: Color
    let idleColor: Color

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    private var animating: Bool {
        to != nil && phase != .idle && !reduceMotion && scenePhase == .active
    }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !animating)) { timeline in
            Canvas { context, _ in
                draw(in: &context, time: timeline.date.timeIntervalSinceReferenceDate)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func draw(in context: inout GraphicsContext, time: TimeInterval) {
        guard let to else { return }
        let dx = to.x - from.x
        let dy = to.y - from.y
        let distance = (dx * dx + dy * dy).squareRoot()
        guard distance > 8 else { return }

        // Изгиб вверх, пропорционально длине маршрута.
        var normal = CGPoint(x: -dy / distance, y: dx / distance)
        if normal.y > 0 { normal = CGPoint(x: -normal.x, y: -normal.y) }
        let lift = min(distance * 0.28, 120)
        let control = CGPoint(x: (from.x + to.x) / 2 + normal.x * lift,
                              y: (from.y + to.y) / 2 + normal.y * lift)
        var arc = Path()
        arc.move(to: from)
        arc.addQuadCurve(to: to, control: control)

        switch phase {
        case .idle:
            context.stroke(arc, with: .color(idleColor.opacity(0.55)),
                           style: StrokeStyle(lineWidth: 1.6, lineCap: .round, dash: [3, 5]))
        case .connecting:
            context.stroke(arc, with: .color(accent.opacity(0.6)),
                           style: StrokeStyle(lineWidth: 1.8, lineCap: .round, dash: [4, 5]))
        case .connected:
            context.stroke(arc, with: .color(accent.opacity(0.16)), style: StrokeStyle(lineWidth: 8, lineCap: .round))
            context.stroke(arc, with: .color(accent.opacity(0.85)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
        }

        guard phase != .idle else { return }
        let period = phase == .connected ? 2.2 : 1.1
        let progress = reduceMotion ? 1 : (time / period).truncatingRemainder(dividingBy: 1)

        // Светящийся «хвост»: несколько отрезков с нарастающей яркостью.
        if !reduceMotion {
            let tail = 0.22
            let pieces = 8
            for index in 0..<pieces {
                let start = progress - tail + tail * Double(index) / Double(pieces)
                let end = start + tail / Double(pieces) + 0.005
                guard end > 0, start < 1 else { continue }
                let alpha = 0.15 + 0.85 * Double(index + 1) / Double(pieces)
                context.stroke(arc.trimmedPath(from: max(start, 0), to: min(end, 1)),
                               with: .color(accent.opacity(alpha)),
                               style: StrokeStyle(lineWidth: 3.2, lineCap: .round))
            }
            let head = point(on: progress, from: from, control: control, to: to)
            context.fill(Path(ellipseIn: CGRect(x: head.x - 3.5, y: head.y - 3.5, width: 7, height: 7)),
                         with: .color(accent))
        }

        // Пульс у сервера, пока подключены.
        if phase == .connected {
            let pulse = reduceMotion ? 0.5 : (time / 2).truncatingRemainder(dividingBy: 1)
            let radius = 22 + 16 * pulse
            context.stroke(Path(ellipseIn: CGRect(x: to.x - radius, y: to.y - radius, width: radius * 2, height: radius * 2)),
                           with: .color(accent.opacity(0.45 * (1 - pulse))), lineWidth: 2)
        }
    }

    private func point(on t: Double, from p0: CGPoint, control p1: CGPoint, to p2: CGPoint) -> CGPoint {
        let u = 1 - t
        return CGPoint(x: u * u * p0.x + 2 * u * t * p1.x + t * t * p2.x,
                       y: u * u * p0.y + 2 * u * t * p1.y + t * t * p2.y)
    }
}
