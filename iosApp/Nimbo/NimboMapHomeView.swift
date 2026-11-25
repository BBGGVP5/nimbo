import NetworkExtension
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

private enum NimboRoutePhase: Equatable {
    case idle, connecting, connected, disconnecting
}

/// Плавный переход по кадровым часам: никаких таймеров, только время кадра.
private struct NimboTween<Value: Equatable>: Equatable {
    var from: Value
    var to: Value
    var start: Date
    var duration: Double

    func fraction(at date: Date) -> Double {
        guard duration > 0 else { return 1 }
        return nimboSmoothstep(date.timeIntervalSince(start) / duration)
    }
}

private func nimboSmoothstep(_ value: Double) -> Double {
    let t = min(max(value, 0), 1)
    return t * t * (3 - 2 * t)
}

private func nimboClamp01(_ value: Double) -> Double { min(max(value, 0), 1) }

/// Главный экран в виде карты: точки суши, флаги серверов, маршрут «вы здесь» → страна выхода.
struct NimboMapHomeView: View {
    @EnvironmentObject private var vpn: VpnController
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("com.nimbo.appearance.accentHex") private var accentHex = "E8E8E8"
    @State private var snapshot = NimboHomeMapSnapshot.empty

    /// Страна выхода по cloudflare trace; nil — не знаем, используем страну сервера.
    @State private var exitCountry: String?

    /// Окно, выбранное пальцем; nil — авто-окно по маршруту.
    @State private var userViewport: NimboMapViewport?
    @State private var gestureBase: NimboMapViewport?
    @State private var pinchScale: Double?
    @State private var dragOffset: CGSize?
    @State private var viewportTween: NimboTween<NimboMapViewport>?
    @State private var settledViewport: NimboMapViewport?

    /// Конец дуги перетекает к новой точке, а не перерисовывается с нуля.
    @State private var endTween: NimboTween<NimboGeoPoint>?
    @State private var settledEnd: NimboGeoPoint?

    @State private var lastPhase: NimboRoutePhase?
    @State private var drawInStart = Date.distantPast
    @State private var retractStart: Date?

    var bottomInset: CGFloat = 0
    var onOpenProfiles: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            header
            mapCard.layoutPriority(1)
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
        .padding(.top, 6)
        .padding(.bottom, bottomInset + 8)
        .frame(maxWidth: 680)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .foregroundStyle(NimboNative.ink)
        .background(NimboNative.canvas.ignoresSafeArea())
        .onAppear {
            reload()
            lastPhase = routePhase
            settledEnd = routeTarget
        }
        // Стабильный издатель без операторов, как у остальных уведомлений в RootView.
        .onReceive(NotificationCenter.default.publisher(for: .nimboHomeMapChanged)) { _ in
            if Thread.isMainThread { reload() } else { DispatchQueue.main.async { reload() } }
        }
        // onChange сравнивает значения при пересчёте вида; это не подписка на издатель VPN.
        .onChange(of: routePhase) { handlePhaseChange($0) }
        .onChange(of: routeTarget) { handleTargetChange($0) }
        .task(id: exitProbeKey) { await probeExitCountry(key: exitProbeKey) }
    }

    private func reload() {
        let next = NimboHomeMapSnapshot.current()
        if next != snapshot { snapshot = next }
    }

    // MARK: - Модель маршрута

    private var routePhase: NimboRoutePhase {
        switch vpn.state {
        case .connected: .connected
        case .preparing, .connecting: .connecting
        case .disconnecting: .disconnecting
        case .idle, .failed: .idle
        }
    }

    private var here: NimboGeoPoint { NimboWorldMap.shared?.here() ?? NimboWorldMap.fallbackHere }

    private var markers: [NimboMapMarker] {
        guard let map = NimboWorldMap.shared else { return [] }
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

    private var serverCode: String? {
        snapshot.activeServer.flatMap { NimboServerCountry.code(name: $0.name, description: $0.description) }
    }

    /// Страна выхода известна только в подключённой сессии.
    private var exitCode: String? { routePhase == .connected ? exitCountry : nil }

    /// Конец дуги: реальная страна выхода, иначе страна выбранного сервера.
    private var routeTarget: NimboGeoPoint? {
        let countries = NimboWorldMap.shared?.countries ?? [:]
        if let exitCode, let point = countries[exitCode] { return point }
        if let serverCode, let point = countries[serverCode] { return point }
        return nil
    }

    private var exitProbeKey: String {
        guard routePhase == .connected else { return "off" }
        let connectedAt = vpn.manager?.connection.connectedDate?.timeIntervalSince1970 ?? 0
        return "\(snapshot.activeServerId ?? "auto")|\(Int(connectedAt))"
    }

    private func probeExitCountry(key: String) async {
        guard key != "off" else {
            if exitCountry != nil { exitCountry = nil }
            return
        }
        exitCountry = nil
        // Даём туннелю устояться после подключения или смены сервера.
        try? await Task.sleep(nanoseconds: 1_200_000_000)
        guard !Task.isCancelled else { return }
        let code = await NimboExitCountryProbe.country(sessionKey: key)
        guard !Task.isCancelled, let code else { return }
        if exitCountry != code { exitCountry = code }
    }

    private func handlePhaseChange(_ phase: NimboRoutePhase) {
        let now = Date()
        let previous = lastPhase ?? phase
        if (previous == .connected || previous == .connecting) && (phase == .idle || phase == .disconnecting) {
            if retractStart == nil || previous != .disconnecting { retractStart = now }
        }
        if phase == .connecting || (phase == .connected && previous != .connecting) {
            drawInStart = now
            retractStart = nil
        }
        if phase == .connected { retractStart = nil }
        // При подключении окно само подлетает к маршруту.
        if phase != previous { userViewport = nil }
        lastPhase = phase
    }

    private func handleTargetChange(_ target: NimboGeoPoint?) {
        let now = Date()
        let current = endTween.map { $0.from.interpolated(to: $0.to, fraction: $0.fraction(at: now)) } ?? settledEnd
        if let current, let target, current != target, !reduceMotion {
            endTween = NimboTween(from: current, to: target, start: now, duration: 0.8)
        } else {
            endTween = nil
            if current == nil && target != nil { drawInStart = now }
        }
        settledEnd = target
        userViewport = nil
    }

    private func displayedEnd(at date: Date) -> NimboGeoPoint? {
        guard let target = routeTarget else { return nil }
        guard let tween = endTween, tween.to == target else { return target }
        return tween.from.interpolated(to: tween.to, fraction: tween.fraction(at: date))
    }

    // MARK: - Окно карты

    private func autoViewport(size: CGSize) -> NimboMapViewport {
        let aspect = Double(size.width / max(size.height, 1))
        var points = [here]
        if let target = routeTarget { points.append(target) }
        return NimboMapViewport.fitting(points, aspect: aspect, minLonSpan: 25)
    }

    private func displayedViewport(base: NimboMapViewport, at date: Date) -> NimboMapViewport {
        guard let tween = viewportTween, tween.to == base else { return base }
        return tween.from.interpolated(to: tween.to, fraction: tween.fraction(at: date))
    }

    private func handleViewportChange(_ base: NimboMapViewport) {
        let now = Date()
        let current = viewportTween.map { $0.from.interpolated(to: $0.to, fraction: $0.fraction(at: now)) } ?? settledViewport
        if gestureBase == nil, !reduceMotion, let current, current != base {
            viewportTween = NimboTween(from: current, to: base, start: now, duration: 0.75)
        } else {
            viewportTween = nil
        }
        settledViewport = base
    }

    private func isAnimatingTransition(at date: Date) -> Bool {
        if let tween = viewportTween, date.timeIntervalSince(tween.start) < tween.duration { return true }
        if let tween = endTween, date.timeIntervalSince(tween.start) < tween.duration { return true }
        if let retractStart, date.timeIntervalSince(retractStart) < 1.4 { return true }
        return false
    }

    // MARK: - Карта

    private var mapCard: some View {
        GeometryReader { geometry in
            let size = geometry.size
            let base = userViewport ?? autoViewport(size: size)
            let pins = markers
            let phase = routePhase
            let animatedIdle = phase == .idle && !isAnimatingTransition(at: Date())
            TimelineView(.animation(minimumInterval: animatedIdle ? 1.0 / 15 : 1.0 / 30,
                                    paused: reduceMotion || scenePhase != .active)) { timeline in
                let now = timeline.date
                let viewport = displayedViewport(base: base, at: now)
                let scene = NimboMapScene(markers: pins, here: here, end: displayedEnd(at: now),
                                          viewport: viewport, size: size)
                ZStack {
                    NimboMapDots(land: NimboWorldMap.shared?.land ?? [], step: NimboWorldMap.shared?.step ?? 1,
                                 viewport: viewport, size: size, route: scene.routePolyline,
                                 color: colorScheme == .dark ? Color.white.opacity(0.18) : Color.black.opacity(0.14),
                                 routeColor: accent.opacity(phase == .connected ? 0.6 : 0.32),
                                 vignette: NimboNative.panel)
                        .equatable()
                    Canvas { context, _ in
                        NimboMapPainter(scene: scene, phase: phase, now: now, motion: !reduceMotion,
                                        drawInStart: drawInStart, retractStart: retractStart,
                                        accent: accent, onAccent: accentInk, panel: NimboNative.panel,
                                        border: NimboNative.border, ink: NimboNative.ink,
                                        secondary: NimboNative.secondary)
                            .draw(in: &context)
                    }
                }
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .gesture(SimultaneousGesture(pinchGesture(size: size, base: base), dragGesture(size: size, base: base)))
            .simultaneousGesture(
                SpatialTapGesture(count: 2).onEnded { _ in userViewport = nil }
                    .exclusively(before: SpatialTapGesture().onEnded { value in
                        handleTap(at: value.location, size: size, base: base, pins: pins)
                    })
            )
            .onAppear { settledViewport = base }
            .onChange(of: base) { handleViewportChange($0) }
        }
        .frame(minHeight: 320, maxHeight: .infinity)
        .background(NimboNative.panel, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(NimboNative.border, lineWidth: 1))
        .overlay(alignment: .bottomTrailing) {
            if userViewport != nil {
                Button { userViewport = nil } label: {
                    Image(systemName: "scope")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(width: 36, height: 36)
                        .background(NimboNative.panel.opacity(0.92), in: Circle())
                        .overlay(Circle().strokeBorder(NimboNative.border, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .padding(10)
                .accessibilityLabel("Вернуть карту к маршруту")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text(mapAccessibilityLabel))
        .accessibilityActions {
            ForEach(markers) { marker in
                Button(markerLabel(marker)) {
                    NotificationCenter.default.post(name: .nimboSelectServer, object: marker.preferredServerID)
                }
            }
        }
    }

    private func pinchGesture(size: CGSize, base: NimboMapViewport) -> some Gesture {
        MagnificationGesture()
            .onChanged { value in
                if gestureBase == nil { gestureBase = displayedViewport(base: base, at: Date()) }
                pinchScale = Double(value)
                applyGesture(size: size)
            }
            .onEnded { value in
                if let start = gestureBase { gestureBase = start.zoomed(by: Double(value)) }
                pinchScale = nil
                finishGestureIfIdle(size: size)
            }
    }

    private func dragGesture(size: CGSize, base: NimboMapViewport) -> some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                if gestureBase == nil { gestureBase = displayedViewport(base: base, at: Date()) }
                dragOffset = value.translation
                applyGesture(size: size)
            }
            .onEnded { value in
                if let start = gestureBase {
                    // Сдвиг делался поверх зума: переводим его в масштаб исходного окна.
                    let scale = pinchScale ?? 1
                    gestureBase = start.panned(by: CGSize(width: value.translation.width / scale,
                                                          height: value.translation.height / scale), in: size)
                }
                dragOffset = nil
                finishGestureIfIdle(size: size)
            }
    }

    private func applyGesture(size: CGSize) {
        guard let start = gestureBase else { return }
        userViewport = start.zoomed(by: pinchScale ?? 1).panned(by: dragOffset ?? .zero, in: size)
    }

    private func finishGestureIfIdle(size: CGSize) {
        if pinchScale == nil && dragOffset == nil {
            if let start = gestureBase { userViewport = start }
            gestureBase = nil
        } else {
            applyGesture(size: size)
        }
    }

    private func handleTap(at location: CGPoint, size: CGSize, base: NimboMapViewport, pins: [NimboMapMarker]) {
        let viewport = displayedViewport(base: base, at: Date())
        let scene = NimboMapScene(markers: pins, here: here, end: displayedEnd(at: Date()), viewport: viewport, size: size)
        guard let cluster = scene.cluster(at: location) else { return }
        if cluster.edgeDirection != nil {
            // Стрелка у края: перелёт к дальнему серверу с тем же масштабом.
            userViewport = .centered(cluster.main.point, lonSpan: viewport.lonSpan, latSpan: viewport.latSpan)
        } else if cluster.members.count > 1 {
            // Кластер: приблизить, чтобы флаги разошлись.
            userViewport = viewport.zoomed(by: 2.4, around: viewport.unproject(cluster.center, in: size))
        } else if !cluster.isActive {
            NotificationCenter.default.post(name: .nimboSelectServer, object: cluster.main.preferredServerID)
        }
    }

    private var mapAccessibilityLabel: String {
        let destination = exitCode.flatMap { NimboServerCountry.displayName(for: $0) }
            ?? serverCode.flatMap { NimboServerCountry.displayName(for: $0) }
        guard let destination else { return "Карта серверов" }
        return routePhase == .connected ? "Карта: трафик идёт в \(destination)" : "Карта: маршрут до \(destination)"
    }

    private func markerLabel(_ marker: NimboMapMarker) -> String {
        let name = NimboServerCountry.displayName(for: marker.code) ?? marker.code
        return marker.serverIDs.count > 1 ? "\(name), серверов: \(marker.serverIDs.count)" : name
    }

    // MARK: - Шапка, карточка, кнопка

    private var header: some View {
        HStack(alignment: .center, spacing: 10) {
            Text("nimbo").nimboFont(26, relativeTo: .title, weight: .bold)
            Spacer(minLength: 8)
            statusPill
        }
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
        .background(NimboNative.panel, in: Capsule())
        .overlay(Capsule().strokeBorder(NimboNative.border, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }

    private var serverCard: some View {
        let server = snapshot.activeServer
        let code = serverCode
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
        // «Выход: …», когда реальная страна не совпадает с флагом сервера (обходы, автовыбор).
        let exitLine: String? = exitCode.flatMap { exit in
            guard exit != code else { return nil }
            return "Выход: \(NimboServerCountry.displayName(for: exit) ?? exit)"
        }
        return Button {
            if snapshot.servers.isEmpty {
                NotificationCenter.default.post(name: .nimboAddProfile, object: nil)
            } else {
                onOpenProfiles()
            }
        } label: {
            HStack(spacing: 12) {
                Text(server == nil ? NimboServerCountry.flag(for: exitCode) : NimboServerCountry.flag(for: code))
                    .font(.system(size: 26))
                    .frame(width: 44, height: 44)
                    .background(NimboNative.raised, in: Circle())
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).nimboFont(16, weight: .semibold).lineLimit(1)
                    Text(subtitle).nimboFont(13, relativeTo: .footnote)
                        .foregroundStyle(NimboNative.secondary).lineLimit(1)
                    if let exitLine {
                        Text(exitLine).nimboFont(12, relativeTo: .caption, weight: .semibold)
                            .foregroundStyle(accent).lineLimit(1)
                    }
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
            .padding(12)
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
    private var accentRGB: (Double, Double, Double)? {
        let hex = accentHex.trimmingCharacters(in: CharacterSet(charactersIn: "# ")).uppercased()
        if hex == "E8E8E8" && colorScheme == .light { return (0x20 / 255.0, 0x20 / 255.0, 0x20 / 255.0) }
        guard hex.count == 6, let value = UInt32(hex, radix: 16) else { return nil }
        return (Double((value >> 16) & 255) / 255, Double((value >> 8) & 255) / 255, Double(value & 255) / 255)
    }

    private var accent: Color {
        guard let rgb = accentRGB else { return NimboNative.accent }
        return Color(red: rgb.0, green: rgb.1, blue: rgb.2)
    }

    /// Цвет текста поверх акцента (значок «+N»).
    private var accentInk: Color {
        guard let rgb = accentRGB else { return NimboNative.onAccent }
        let luminance = 0.299 * rgb.0 + 0.587 * rgb.1 + 0.114 * rgb.2
        return luminance > 0.6 ? .black : .white
    }
}

// MARK: - Раскладка кадра

/// Флаги, собранные в экранных координатах: перекрывающиеся — в один кружок с «+N».
private struct NimboMapCluster {
    var center: CGPoint
    var radius: CGFloat
    var members: [NimboMapMarker]
    var isActive: Bool
    /// Для сервера за краем окна — направление стрелки; nil — флаг на карте.
    var edgeDirection: CGVector?
    var main: NimboMapMarker { members[0] }
}

/// Всё, что нужно для одного кадра: проекция, дуга, кластеры, место подписи «Вы здесь».
private struct NimboMapScene {
    static let hereLabelSize = CGSize(width: 74, height: 22)

    let size: CGSize
    let viewport: NimboMapViewport
    let here: CGPoint
    let end: CGPoint?
    let control: CGPoint?
    let clusters: [NimboMapCluster]
    let hereLabel: CGRect

    init(markers: [NimboMapMarker], here hereGeo: NimboGeoPoint, end endGeo: NimboGeoPoint?,
         viewport: NimboMapViewport, size: CGSize) {
        self.size = size
        self.viewport = viewport
        let here = viewport.project(hereGeo, in: size)
        let end = endGeo.map { viewport.project($0, in: size) }
        self.here = here
        self.end = end
        self.control = end.flatMap { Self.control(from: here, to: $0) }

        let bounds = CGRect(origin: .zero, size: size)
        let inner = bounds.insetBy(dx: 22, dy: 22)
        let middle = CGPoint(x: size.width / 2, y: size.height / 2)
        let ordered = markers.sorted { lhs, rhs in
            if lhs.isActive != rhs.isActive { return lhs.isActive }
            return lhs.serverIDs.count > rhs.serverIDs.count
        }
        var clusters: [NimboMapCluster] = []
        for marker in ordered {
            var point = viewport.project(marker.point, in: size)
            var direction: CGVector?
            if !bounds.insetBy(dx: -6, dy: -6).contains(point) {
                let dx = point.x - middle.x
                let dy = point.y - middle.y
                let length = max((dx * dx + dy * dy).squareRoot(), 0.001)
                direction = CGVector(dx: dx / length, dy: dy / length)
                point = Self.clampRay(from: middle, toward: point, into: inner)
            }
            let radius: CGFloat = marker.isActive ? 21 : (direction == nil ? 16 : 13)
            if !marker.isActive,
               let index = clusters.firstIndex(where: { cluster in
                   (cluster.edgeDirection == nil) == (direction == nil) &&
                       Self.distance(cluster.center, point) < cluster.radius + radius - 2
               }) {
                clusters[index].members.append(marker)
            } else {
                clusters.append(NimboMapCluster(center: point, radius: radius, members: [marker],
                                                isActive: marker.isActive, edgeDirection: direction))
            }
        }
        self.clusters = clusters
        self.hereLabel = Self.placeHereLabel(here: here, clusters: clusters, end: end, bounds: bounds)
    }

    /// Изгиб вверх, пропорциональный длине маршрута; слишком короткий маршрут не рисуется.
    static func control(from: CGPoint, to: CGPoint) -> CGPoint? {
        let dx = to.x - from.x
        let dy = to.y - from.y
        let distance = (dx * dx + dy * dy).squareRoot()
        guard distance > 10 else { return nil }
        var normal = CGPoint(x: -dy / distance, y: dx / distance)
        if normal.y > 0 { normal = CGPoint(x: -normal.x, y: -normal.y) }
        let lift = min(distance * 0.3, 140)
        return CGPoint(x: (from.x + to.x) / 2 + normal.x * lift, y: (from.y + to.y) / 2 + normal.y * lift)
    }

    static func point(on t: Double, from p0: CGPoint, control p1: CGPoint, to p2: CGPoint) -> CGPoint {
        let u = 1 - t
        return CGPoint(x: u * u * p0.x + 2 * u * t * p1.x + t * t * p2.x,
                       y: u * u * p0.y + 2 * u * t * p1.y + t * t * p2.y)
    }

    var arc: Path? {
        guard let end, let control else { return nil }
        var path = Path()
        path.move(to: here)
        path.addQuadCurve(to: end, control: control)
        return path
    }

    /// Ломаная вдоль дуги — для подсветки «дорожки» точек суши.
    var routePolyline: [CGPoint] {
        guard let end, let control else { return [] }
        return (0...20).map { Self.point(on: Double($0) / 20, from: here, control: control, to: end) }
    }

    func cluster(at location: CGPoint) -> NimboMapCluster? {
        clusters
            .map { ($0, Self.distance($0.center, location)) }
            .filter { $0.1 <= $0.0.radius + 10 }
            .min { $0.1 < $1.1 }?.0
    }

    static func distance(_ a: CGPoint, _ b: CGPoint) -> CGFloat {
        let dx = a.x - b.x
        let dy = a.y - b.y
        return (dx * dx + dy * dy).squareRoot()
    }

    /// Точка на краю внутренней рамки по лучу от центра к цели.
    static func clampRay(from origin: CGPoint, toward target: CGPoint, into rect: CGRect) -> CGPoint {
        let dx = target.x - origin.x
        let dy = target.y - origin.y
        var scale = CGFloat.greatestFiniteMagnitude
        if dx > 0 { scale = min(scale, (rect.maxX - origin.x) / dx) }
        if dx < 0 { scale = min(scale, (rect.minX - origin.x) / dx) }
        if dy > 0 { scale = min(scale, (rect.maxY - origin.y) / dy) }
        if dy < 0 { scale = min(scale, (rect.minY - origin.y) / dy) }
        guard scale.isFinite else { return origin }
        return CGPoint(x: origin.x + dx * scale, y: origin.y + dy * scale)
    }

    /// Подпись «Вы здесь» уходит в ту сторону, где нет флагов и конца дуги.
    static func placeHereLabel(here: CGPoint, clusters: [NimboMapCluster], end: CGPoint?, bounds: CGRect) -> CGRect {
        let size = hereLabelSize
        let gap: CGFloat = 14
        let candidates = [
            CGRect(x: here.x + gap, y: here.y - size.height / 2, width: size.width, height: size.height),
            CGRect(x: here.x - gap - size.width, y: here.y - size.height / 2, width: size.width, height: size.height),
            CGRect(x: here.x - size.width / 2, y: here.y - gap - size.height, width: size.width, height: size.height),
            CGRect(x: here.x - size.width / 2, y: here.y + gap, width: size.width, height: size.height)
        ]
        let obstacles = clusters.map { CGRect(x: $0.center.x - $0.radius, y: $0.center.y - $0.radius,
                                              width: $0.radius * 2, height: $0.radius * 2) }
            + (end.map { [CGRect(x: $0.x - 24, y: $0.y - 24, width: 48, height: 48)] } ?? [])
        let safe = bounds.insetBy(dx: 4, dy: 4)
        return candidates.first { rect in safe.contains(rect) && !obstacles.contains { $0.intersects(rect) } }
            ?? candidates.first { safe.contains($0) }
            ?? candidates[0]
    }
}

// MARK: - Рисование

/// Рисует маршрут, флаги и «Вы здесь» для одного кадра. Время — только из TimelineView.
private struct NimboMapPainter {
    let scene: NimboMapScene
    let phase: NimboRoutePhase
    let now: Date
    let motion: Bool
    let drawInStart: Date
    let retractStart: Date?
    let accent: Color
    let onAccent: Color
    let panel: Color
    let border: Color
    let ink: Color
    let secondary: Color

    private var time: Double { now.timeIntervalSinceReferenceDate }

    func draw(in context: inout GraphicsContext) {
        drawRoute(in: &context)
        for cluster in scene.clusters where !cluster.isActive { drawCluster(cluster, in: &context) }
        for cluster in scene.clusters where cluster.isActive { drawCluster(cluster, in: &context) }
        drawHere(in: &context)
    }

    private func elapsed(since date: Date) -> Double { now.timeIntervalSince(date) }

    private func drawRoute(in context: inout GraphicsContext) {
        guard let arc = scene.arc, let end = scene.end, let control = scene.control else { return }
        let round = StrokeStyle(lineWidth: 2.4, lineCap: .round)

        switch phase {
        case .idle, .disconnecting:
            if motion, let retractStart, elapsed(since: retractStart) < 0.8 {
                // Отключение: дуга втягивается обратно к вам и гаснет.
                let p = nimboSmoothstep(elapsed(since: retractStart) / 0.8)
                let keep = 1 - p
                if keep > 0.01 {
                    let part = arc.trimmedPath(from: 0, to: keep)
                    context.stroke(part, with: .color(accent.opacity(0.16 * keep)), style: StrokeStyle(lineWidth: 9, lineCap: .round))
                    context.stroke(part, with: .color(accent.opacity(0.9 * keep)), style: round)
                    let head = NimboMapScene.point(on: keep, from: scene.here, control: control, to: end)
                    fillDot(at: head, radius: 3.5, color: accent.opacity(keep), in: &context)
                }
            } else {
                // Не подключено: тонкая пунктирная подсказка, после втягивания — плавное появление.
                let fadeIn = motion ? retractStart.map { nimboClamp01((elapsed(since: $0) - 0.8) / 0.5) } ?? 1 : 1
                context.stroke(arc, with: .color(secondary.opacity(0.5 * fadeIn)),
                               style: StrokeStyle(lineWidth: 1.6, lineCap: .round, dash: [3, 5]))
            }

        case .connecting:
            let p = motion ? nimboSmoothstep(elapsed(since: drawInStart) / 1.0) : 1
            context.stroke(arc, with: .color(accent.opacity(0.25)),
                           style: StrokeStyle(lineWidth: 1.6, lineCap: .round, dash: [4, 5]))
            let drawn = arc.trimmedPath(from: 0, to: p)
            context.stroke(drawn, with: .color(accent.opacity(0.14)), style: StrokeStyle(lineWidth: 8, lineCap: .round))
            context.stroke(drawn, with: .color(accent.opacity(0.8)), style: round)
            if motion {
                // Пока дуга дорисовывается, комета ведёт её конец; потом бегает по кругу.
                let head = p < 1 ? p : (time / 1.1).truncatingRemainder(dividingBy: 1)
                drawComet(arc: arc, head: head, control: control, end: end, in: &context)
                let pulse = (time / 1.2).truncatingRemainder(dividingBy: 1)
                strokeRing(at: end, radius: 20 + 14 * pulse, color: accent.opacity(0.5 * (1 - pulse)), in: &context)
            }

        case .connected:
            let p = motion ? nimboSmoothstep(elapsed(since: drawInStart) / 1.0) : 1
            let drawn = arc.trimmedPath(from: 0, to: p)
            context.stroke(drawn, with: .color(accent.opacity(0.18)), style: StrokeStyle(lineWidth: 10, lineCap: .round))
            context.stroke(drawn, with: .color(accent.opacity(0.92)), style: round)
            guard motion else { return }
            // Непрерывный поток частиц от вас к стране выхода.
            let count = 14
            for index in 0..<count {
                let q = (time / 2.6 + Double(index) / Double(count)).truncatingRemainder(dividingBy: 1)
                guard q <= p else { continue }
                let point = NimboMapScene.point(on: q, from: scene.here, control: control, to: end)
                let strength = sin(Double.pi * q)
                fillDot(at: point, radius: 5 + 3 * strength, color: accent.opacity(0.12 * strength), in: &context)
                fillDot(at: point, radius: 1.6 + 1.2 * strength, color: accent.opacity(0.35 + 0.6 * strength), in: &context)
            }
            // Мягкие расходящиеся кольца вокруг страны выхода.
            for ring in 0..<3 {
                let r = (time / 2.8 + Double(ring) / 3).truncatingRemainder(dividingBy: 1)
                strokeRing(at: end, radius: 16 + 34 * r, color: accent.opacity(0.42 * (1 - r)), in: &context)
            }
        }
    }

    private func drawComet(arc: Path, head: Double, control: CGPoint, end: CGPoint, in context: inout GraphicsContext) {
        let tail = 0.22
        let pieces = 8
        for index in 0..<pieces {
            let start = head - tail + tail * Double(index) / Double(pieces)
            let stop = start + tail / Double(pieces) + 0.005
            guard stop > 0, start < 1 else { continue }
            let alpha = 0.15 + 0.85 * Double(index + 1) / Double(pieces)
            context.stroke(arc.trimmedPath(from: max(start, 0), to: min(stop, 1)),
                           with: .color(accent.opacity(alpha)), style: StrokeStyle(lineWidth: 3.4, lineCap: .round))
        }
        let point = NimboMapScene.point(on: head, from: scene.here, control: control, to: end)
        fillDot(at: point, radius: 7, color: accent.opacity(0.25), in: &context)
        fillDot(at: point, radius: 3.6, color: accent, in: &context)
    }

    private func drawCluster(_ cluster: NimboMapCluster, in context: inout GraphicsContext) {
        let center = cluster.center
        let radius = cluster.radius
        let circle = Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
        var shadow = context
        shadow.addFilter(.shadow(color: .black.opacity(0.25), radius: 4, y: 2))
        shadow.fill(circle, with: .color(panel))
        context.stroke(circle, with: .color(cluster.isActive ? accent : border), lineWidth: cluster.isActive ? 2.4 : 1)

        let flag = context.resolve(Text(NimboServerCountry.flag(for: cluster.main.code))
            .font(.system(size: cluster.isActive ? 22 : (cluster.edgeDirection == nil ? 17 : 13))))
        context.draw(flag, at: center, anchor: .center)

        if let direction = cluster.edgeDirection {
            // Стрелка-указатель наружу: сервер дальше края окна.
            let tip = CGPoint(x: center.x + direction.dx * (radius + 8), y: center.y + direction.dy * (radius + 8))
            let baseCenter = CGPoint(x: center.x + direction.dx * (radius + 2), y: center.y + direction.dy * (radius + 2))
            let perpendicular = CGVector(dx: -direction.dy * 4.5, dy: direction.dx * 4.5)
            var arrow = Path()
            arrow.move(to: tip)
            arrow.addLine(to: CGPoint(x: baseCenter.x + perpendicular.dx, y: baseCenter.y + perpendicular.dy))
            arrow.addLine(to: CGPoint(x: baseCenter.x - perpendicular.dx, y: baseCenter.y - perpendicular.dy))
            arrow.closeSubpath()
            context.fill(arrow, with: .color(ink.opacity(0.7)))
        }

        if cluster.members.count > 1 {
            let badge = context.resolve(Text("+\(cluster.members.count - 1)")
                .font(.system(size: 10, weight: .bold)).foregroundColor(onAccent))
            let textSize = badge.measure(in: CGSize(width: 60, height: 20))
            let badgeCenter = CGPoint(x: center.x + radius * 0.75, y: center.y - radius * 0.75)
            let rect = CGRect(x: badgeCenter.x - max(textSize.width / 2 + 5, 9), y: badgeCenter.y - 8,
                              width: max(textSize.width + 10, 18), height: 16)
            context.fill(Path(roundedRect: rect, cornerRadius: 8), with: .color(accent))
            context.draw(badge, at: badgeCenter, anchor: .center)
        }
    }

    private func drawHere(in context: inout GraphicsContext) {
        let here = scene.here
        // «Дыхание» точки, пока не подключены.
        let breath = motion && phase == .idle ? (sin(time * 2.2) + 1) / 2 : 0.5
        fillDot(at: here, radius: 10 + 4 * breath, color: accent.opacity(0.14 + 0.12 * breath), in: &context)
        fillDot(at: here, radius: 6.5, color: panel, in: &context)
        fillDot(at: here, radius: 4.5, color: accent, in: &context)

        let rect = scene.hereLabel
        let capsule = Path(roundedRect: rect, cornerRadius: rect.height / 2)
        context.fill(capsule, with: .color(panel.opacity(0.94)))
        context.stroke(capsule, with: .color(border), lineWidth: 1)
        let label = context.resolve(Text("Вы здесь").font(.system(size: 11, weight: .semibold)).foregroundColor(ink))
        context.draw(label, at: CGPoint(x: rect.midX, y: rect.midY), anchor: .center)
    }

    private func fillDot(at point: CGPoint, radius: CGFloat, color: Color, in context: inout GraphicsContext) {
        context.fill(Path(ellipseIn: CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2)),
                     with: .color(color))
    }

    private func strokeRing(at point: CGPoint, radius: CGFloat, color: Color, in context: inout GraphicsContext) {
        context.stroke(Path(ellipseIn: CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2)),
                       with: .color(color), lineWidth: 1.8)
    }
}

/// Точки суши с подсветкой «дорожки» вдоль маршрута и виньеткой к краям.
/// Отдельный Equatable-вид: перерисовывается только при смене окна, маршрута или темы,
/// а не на каждом кадре частиц.
private struct NimboMapDots: View, Equatable {
    let land: [NimboGeoPoint]
    let step: Double
    let viewport: NimboMapViewport
    let size: CGSize
    let route: [CGPoint]
    let color: Color
    let routeColor: Color
    let vignette: Color

    static func == (lhs: NimboMapDots, rhs: NimboMapDots) -> Bool {
        lhs.land.count == rhs.land.count && lhs.step == rhs.step && lhs.viewport == rhs.viewport &&
            lhs.size == rhs.size && lhs.route == rhs.route && lhs.color == rhs.color &&
            lhs.routeColor == rhs.routeColor && lhs.vignette == rhs.vignette
    }

    var body: some View {
        Canvas { context, canvasSize in
            guard viewport.lonSpan > 0, viewport.latSpan > 0 else { return }
            let cell = canvasSize.width / viewport.lonSpan * step
            let radius = min(max(cell * 0.3, 0.6), 3.4)
            let corridor: CGFloat = max(cell * 1.6, 9)
            let routeBox = route.isEmpty ? CGRect.null : route.reduce(CGRect.null) {
                $0.union(CGRect(x: $1.x, y: $1.y, width: 0, height: 0))
            }.insetBy(dx: -corridor, dy: -corridor)
            var plain = Path()
            var lit = Path()
            for point in land where viewport.contains(point, margin: step) {
                let center = viewport.project(point, in: canvasSize)
                let rect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
                if !routeBox.isNull, routeBox.contains(center), nearRoute(center, within: corridor) {
                    lit.addEllipse(in: rect)
                } else {
                    plain.addEllipse(in: rect)
                }
            }
            context.fill(plain, with: .color(color))
            context.fill(lit, with: .color(routeColor))

            let bounds = CGRect(origin: .zero, size: canvasSize)
            context.fill(Path(bounds), with: .radialGradient(
                Gradient(stops: [.init(color: vignette.opacity(0), location: 0.55),
                                 .init(color: vignette.opacity(0.8), location: 1)]),
                center: CGPoint(x: bounds.midX, y: bounds.midY), startRadius: 0,
                endRadius: max(bounds.width, bounds.height) * 0.72))
        }
        .accessibilityHidden(true)
    }

    private func nearRoute(_ point: CGPoint, within limit: CGFloat) -> Bool {
        guard route.count > 1 else { return false }
        for index in 1..<route.count where distanceToSegment(point, route[index - 1], route[index]) <= limit {
            return true
        }
        return false
    }

    private func distanceToSegment(_ p: CGPoint, _ a: CGPoint, _ b: CGPoint) -> CGFloat {
        let abx = b.x - a.x
        let aby = b.y - a.y
        let lengthSquared = abx * abx + aby * aby
        guard lengthSquared > 0 else { return NimboMapScene.distance(p, a) }
        let t = min(max(((p.x - a.x) * abx + (p.y - a.y) * aby) / lengthSquared, 0), 1)
        return NimboMapScene.distance(p, CGPoint(x: a.x + abx * t, y: a.y + aby * t))
    }
}
