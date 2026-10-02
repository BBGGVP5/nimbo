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
        .task(id: vpn.state.composePresentation.state) { await refresh() }
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
        .disabled(busy || !group.selectable)
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
        pingName = name
        pingTask = Task {
            let reply = try? await NimboMihomoControl.rpc("mihomoDelay", full: full, session: session,
                fields: ["name": name, "url": "https://www.gstatic.com/generate_204", "timeoutMs": 3000, "expectedStatus": "200-299"])
            guard !Task.isCancelled, pingName == name else { return }
            delays[name] = (reply?["data"] as? [String: Any])?["delayMs"] as? Int ?? -1
            pingName = nil; pingTask = nil
        }
    }
}
