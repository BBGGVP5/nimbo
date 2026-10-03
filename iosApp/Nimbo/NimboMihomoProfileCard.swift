import NetworkExtension
import SwiftUI

/// Whole-document groups stay whole. Selecting a native group never flattens
/// its transports/providers into an Xray server, or replaces the system TUN.
struct NimboMihomoProfileCard: View {
    @EnvironmentObject private var vpn: VpnController
    let full: NimboFullConfiguration
    @State private var groups: [NimboMihomoControl.Group] = []
    @State private var busy = false
    @State private var error: String?
    @State private var delays: [String: Int] = [:]
    @State private var pingTask: Task<Void, Never>?
    @State private var pingName: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label(full.title, systemImage: "square.stack.3d.up").nimboFont(19, weight: .semibold)
            Text("Mihomo · полная конфигурация").foregroundStyle(NimboNative.secondary)
            if let announcement = NimboSubscriptionMetaStore.current.announce {
                Text(announcement).nimboFont(13).foregroundStyle(NimboNative.secondary)
            }
            if let error { Text(error).foregroundStyle(NimboNative.error) }
            ForEach(groups) { group in
                groupCard(group)
            }
            if groups.isEmpty { Text("Маршрут определяется правилами конфигурации.").foregroundStyle(NimboNative.secondary) }
            Button { Task { await refresh() } } label: { Label("Обновить группы", systemImage: "arrow.clockwise") }
                .buttonStyle(NimboActionStyle()).disabled(busy)
        }
        .nimboCard()
        .task(id: vpn.manager?.connection.status.rawValue) {
            await refresh()
        }
        .onDisappear { pingTask?.cancel(); pingTask = nil }
    }

    private func groupCard(_ group: NimboMihomoControl.Group) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(group.name).nimboFont(16, weight: .semibold)
            Text(group.current.map { "\(group.type) · \($0)" } ?? group.type)
                .nimboFont(12).foregroundStyle(NimboNative.secondary)
            ForEach(group.members, id: \.self) { member in
                memberRow(member, group: group)
            }
        }
    }

    private func memberRow(_ member: String, group: NimboMihomoControl.Group) -> some View {
        let selected = group.current == member
        return Button {
            guard !busy, group.selectable else { return }
            Task { await select(member, group: group) }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                Text(member).frame(maxWidth: .infinity, alignment: .leading)
                if pingName == member { ProgressView() }
                else if let delay = delays[member] { Text(delay < 0 ? "н/д" : "\(delay) ms").font(.caption) }
            }
            .padding(12).frame(minHeight: 48)
            .background(NimboNative.raised, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(selected ? NimboNative.ink : .clear, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .disabled(busy)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .contextMenu {
            Button(pingName == member ? "Отменить пинг" : "Пинг сервера", systemImage: "gauge.with.dots.needle.67percent") {
                ping(member)
            }.disabled(vpn.state != .connected)
        }
    }

    @MainActor private func refresh() async {
        guard !busy else { return }
        busy = true; defer { busy = false }
        do {
            let current = try NimboConfigurationStore.shared.loadFullConfiguration() ?? full
            if vpn.state == .connected, let session = vpn.manager?.connection as? NETunnelProviderSession {
                groups = try await NimboMihomoControl.liveGroups(current, session: session)
            } else { groups = try NimboMihomoControl.declaredGroups(current) }
            delays = NimboMihomoPingCache.values(sourceSHA256: full.sourceSHA256, names: groups.flatMap(\.members))
            error = nil
        } catch { self.error = NimboRedactor.redact(error.localizedDescription) }
    }

    @MainActor private func select(_ member: String, group: NimboMihomoControl.Group) async {
        guard !busy else { return }
        busy = true
        do {
            try await NimboMihomoControl.select(group: group, member: member, full: full,
                session: vpn.manager?.connection as? NETunnelProviderSession)
            error = nil
        } catch { self.error = NimboRedactor.redact(error.localizedDescription) }
        busy = false
        await refresh()
    }

    @MainActor private func ping(_ name: String) {
        if pingTask != nil { pingTask?.cancel(); pingTask = nil; pingName = nil; return }
        guard let session = vpn.manager?.connection as? NETunnelProviderSession, vpn.state == .connected else { return }
        let defaults = UserDefaults.standard
        let mode = NimboPingProtocol(stored: defaults.string(forKey: "com.nimbo.ping.protocol"))
        guard let method = mode.httpMethod else {
            // Full native graph has no flattened host/port for TCP/ICMP. Never
            // substitute a different probe while labelling it as that method.
            error = "Mihomo: выберите Nimbo Ping, HTTP GET или HTTP HEAD."
            return
        }
        let url = defaults.string(forKey: "com.nimbo.ping.url") ?? NimboPingPolicy.defaultURL
        guard NimboPingPolicy.checkedURL(url) != nil else { return }
        let timeout = Int(NimboPingPolicy.timeout(milliseconds: defaults.integer(forKey: "com.nimbo.ping.timeoutMs")) * 1000)
        let groupTarget = groups.contains { $0.name == name }
        guard !groupTarget || method == "HEAD" else {
            error = "Для группы выберите HTTP HEAD; Nimbo Ping проверяет конкретный сервер."
            return
        }
        pingName = name
        pingTask = Task {
            let reply = try? await NimboMihomoControl.rpc("mihomoDelay", full: full, session: session,
                fields: ["name": name, "url": url, "timeoutMs": timeout, "httpMethod": method, "expectedStatus": "200-299"])
            guard !Task.isCancelled, pingName == name else { return }
            let latency = (reply?["data"] as? [String: Any])?["delayMs"] as? Int ?? -1
            delays[name] = latency
            NimboMihomoPingCache.save(latency, name: name, sourceSHA256: full.sourceSHA256)
            pingName = nil; pingTask = nil
        }
    }
}
