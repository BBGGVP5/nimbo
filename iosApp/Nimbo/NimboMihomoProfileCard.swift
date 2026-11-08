import NetworkExtension
import SwiftUI

/// Categories are source-owned; selection is committed only after native readback.
struct NimboMihomoProfileCard: View {
    @EnvironmentObject private var vpn: VpnController
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let full: NimboFullConfiguration
    @State private var currentFull: NimboFullConfiguration?
    @State private var declarationRevision: UInt64?
    @State private var groups: [NimboMihomoControl.Group] = []
    @State private var category: String?
    @State private var types: [String: String] = [:]
    @State private var refreshRunID: UUID?
    @State private var selecting = false
    @State private var error: String?
    @State private var delays: [String: Int] = [:]
    @State private var pingTask: Task<Void, Never>?
    @State private var pingRunID: UUID?
    @State private var pingName: String?

    private var configuration: NimboFullConfiguration { currentFull ?? full }
    private var busy: Bool { selecting || refreshRunID != nil }
    private var activeGroup: NimboMihomoControl.Group? { NimboMihomoGroup.active(groups, name: category) }
    private var columns: [GridItem] {
        Array(repeating: GridItem(.flexible(minimum: 0), spacing: 8), count: dynamicTypeSize.isAccessibilitySize ? 1 : 2)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(configuration.title).nimboFont(16, weight: .semibold)
            if let announcement = NimboSubscriptionMetaStore.current.announce, !announcement.isEmpty {
                Text(announcement).nimboFont(12).foregroundStyle(NimboNative.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading).fixedSize(horizontal: false, vertical: true)
            }
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 8) { actions }
                VStack(alignment: .leading, spacing: 8) { actions }
            }
            if let error { Text(error).nimboFont(12).foregroundStyle(NimboNative.error) }
            if busy && groups.isEmpty { ProgressView().accessibilityLabel("Загрузка категорий") }
            else if let activeGroup {
                categoryTabs
                LazyVGrid(columns: columns, alignment: .leading, spacing: 8) {
                    ForEach(activeGroup.members, id: \.self) { member in memberCard(member, group: activeGroup) }
                }
                if activeGroup.members.isEmpty { Text("Нет серверов").nimboFont(12).foregroundStyle(NimboNative.secondary) }
            } else { Text("Нет категорий").nimboFont(12).foregroundStyle(NimboNative.secondary) }
        }
        .nimboCard()
        .task(id: full.sourceSHA256 + ":" + String(vpn.observedSystemStatus?.rawValue ?? 0)) {
            invalidatePing()
            await refresh()
        }
        .onDisappear { invalidatePing(); refreshRunID = nil }
    }

    @ViewBuilder private var actions: some View {
        Button {
            if pingTask != nil { invalidatePing() }
            else { ping(activeGroup?.members ?? []) }
        } label: { Label(pingTask == nil ? "Пинг" : "Отмена", systemImage: "arrow.up.arrow.down") }
            .buttonStyle(NimboActionStyle()).disabled(pingTask == nil && (busy || vpn.state != .connected || activeGroup?.members.isEmpty != false))
        Button { Task { invalidatePing(); await refresh() } } label: { Label("Обновить", systemImage: "arrow.clockwise") }
            .buttonStyle(NimboActionStyle()).disabled(busy)
    }

    private var categoryTabs: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 18) {
                ForEach(groups) { group in
                    Button { category = group.name } label: {
                        VStack(spacing: 6) {
                            Text(group.name).nimboFont(12, weight: .semibold).lineLimit(1)
                            Capsule().fill(activeGroup?.name == group.name ? NimboNative.ink : .clear).frame(height: 2)
                        }.frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(activeGroup?.name == group.name ? NimboNative.ink : NimboNative.secondary)
                    .accessibilityAddTraits(activeGroup?.name == group.name ? .isSelected : [])
                }
            }
        }.accessibilityLabel("Категории серверов")
    }

    private func memberCard(_ member: String, group: NimboMihomoControl.Group) -> some View {
        let selected = group.current == member
        return ZStack(alignment: .bottomTrailing) {
            Button { Task { await select(member, group: group) } } label: {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(alignment: .top, spacing: 6) {
                        Text(member == "DIRECT" ? "Напрямую" : member == "REJECT" ? "Блокировать" : member)
                            .nimboFont(13, weight: .medium).lineLimit(3).frame(maxWidth: .infinity, alignment: .leading)
                        if selected { Image(systemName: "checkmark").font(.caption).accessibilityHidden(true) }
                    }
                    Spacer(minLength: 8)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(types[member] ?? groups.first(where: { $0.name == member })?.type ?? "Прокси")
                            .nimboFont(11, relativeTo: .caption).foregroundStyle(NimboNative.secondary).lineLimit(1)
                        Text(pingName == member ? "…" : delays[member].map { $0 < 0 ? "н/д" : "\($0) ms" } ?? "—")
                            .nimboFont(11, relativeTo: .caption).monospacedDigit().foregroundStyle(NimboNative.secondary)
                    }.padding(.trailing, 32)
                }
                .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                .frame(minHeight: dynamicTypeSize.isAccessibilitySize ? 160 : 132)
                .background(selected ? NimboNative.raised : NimboNative.panel, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(selected ? NimboNative.ink : NimboNative.border, lineWidth: selected ? 1.5 : 1))
                .contentShape(RoundedRectangle(cornerRadius: 14))
            }
            .buttonStyle(.plain).disabled(busy || !group.selectable)
            .accessibilityAddTraits(selected ? .isSelected : [])
            Button { ping([member]) } label: {
                if pingName == member { ProgressView() }
                else { Image(systemName: "arrow.up.arrow.down").font(.system(size: 13)) }
            }
            .frame(minWidth: 44, minHeight: 44).buttonStyle(.plain)
            .disabled(busy || pingTask != nil || vpn.state != .connected)
            .accessibilityLabel("Пинг: \(member)")
            .accessibilityHint(vpn.state == .connected ? "Проверить задержку без смены сервера" : "Для полного Mihomo-профиля требуется активное подключение")
            .padding(3)
        }
    }

    @MainActor private func invalidatePing() {
        pingRunID = nil; pingTask?.cancel(); pingTask = nil; pingName = nil
    }

    @MainActor private func refresh() async {
        guard !selecting else { return }
        let runID = UUID(); refreshRunID = runID
        let connection = vpn.manager?.connection
        let status = vpn.observedSystemStatus
        defer { if refreshRunID == runID { refreshRunID = nil } }
        do {
            let declaration = try await NimboMihomoControl.readDeclared(full)
            let request = declaration.full
            let declared = declaration.groups
            let next: [NimboMihomoControl.Group]
            if vpn.state == .connected, let session = vpn.manager?.connection as? NETunnelProviderSession {
                next = try await NimboMihomoControl.liveGroups(request, session: session, declaredOrder: declared.map(\.name))
            } else { next = declared }
            guard !Task.isCancelled, refreshRunID == runID,
                  vpn.manager?.connection === connection, vpn.observedSystemStatus == status,
                  NimboConfigurationStore.shared.revision == declaration.storeRevision,
                  full.sourceSHA256 == request.sourceSHA256 else { return }
            if currentFull?.sourceSHA256 != request.sourceSHA256 { delays = [:]; category = nil }
            currentFull = request
            groups = next
            types = declaration.types
            declarationRevision = declaration.storeRevision
            delays = NimboMihomoPingCache.values(sourceSHA256: request.sourceSHA256, names: groups.flatMap(\.members))
            error = nil
        } catch {
            if !Task.isCancelled, refreshRunID == runID {
                self.error = NimboRedactor.redact(error.localizedDescription)
            }
        }
    }

    @MainActor private func select(_ member: String, group: NimboMihomoControl.Group) async {
        guard !busy, group.selectable else { return }
        invalidatePing(); refreshRunID = nil; selecting = true
        do {
            let declaration = try await NimboMihomoControl.readDeclared(configuration)
            let request = declaration.full
            guard request.sourceSHA256 == configuration.sourceSHA256 else { throw NimboFullConfigurationError.staleRefresh }
            try await NimboMihomoControl.select(group: group, member: member, full: request,
                session: vpn.manager?.connection as? NETunnelProviderSession, observedStatus: vpn.observedSystemStatus)
            error = nil
        } catch { self.error = NimboRedactor.redact(error.localizedDescription) }
        selecting = false
        await refresh()
    }

    @MainActor private func ping(_ names: [String]) {
        guard pingTask == nil, !busy, let revision = declarationRevision,
              revision == NimboConfigurationStore.shared.revision,
              let session = vpn.manager?.connection as? NETunnelProviderSession, vpn.state == .connected else { return }
        let request = configuration
        let defaults = UserDefaults.standard
        let mode = NimboPingProtocol(stored: defaults.string(forKey: "com.nimbo.ping.protocol"))
        guard let method = mode.httpMethod else { error = "Для Mihomo выберите Nimbo Ping или HTTP."; return }
        let url = defaults.string(forKey: "com.nimbo.ping.url") ?? NimboPingPolicy.defaultURL
        guard NimboPingPolicy.checkedURL(url) != nil else { error = "Проверьте адрес пинга."; return }
        let timeout = Int(NimboPingPolicy.timeout(milliseconds: defaults.integer(forKey: "com.nimbo.ping.timeoutMs")) * 1000)
        var seen = Set<String>()
        // GET through a group can change the measured target; only HEAD is allowed for group entries.
        let unique = names.filter { name in seen.insert(name).inserted && (method == "HEAD" || !groups.contains(where: { $0.name == name })) }
        guard !unique.isEmpty else { error = "Для пинга группы выберите HTTP HEAD."; return }
        let runID = UUID(); pingRunID = runID; error = nil
        pingTask = Task {
            for name in unique {
                guard !Task.isCancelled, pingRunID == runID, vpn.observedSystemStatus == .connected, vpn.manager?.connection === session,
                      NimboConfigurationStore.shared.revision == revision, configuration.sourceSHA256 == request.sourceSHA256 else { break }
                pingName = name
                let reply = try? await NimboMihomoControl.rpc("mihomoDelay", full: request, session: session,
                    fields: ["name": name, "url": url, "timeoutMs": timeout, "httpMethod": method, "expectedStatus": "200-299"])
                guard !Task.isCancelled, pingRunID == runID, vpn.observedSystemStatus == .connected, vpn.manager?.connection === session,
                      NimboConfigurationStore.shared.revision == revision, configuration.sourceSHA256 == request.sourceSHA256 else { break }
                let ms = (reply?["data"] as? [String: Any])?["delayMs"] as? Int ?? -1
                let latency = ms >= 0 && ms <= 65535 ? ms : -1
                delays[name] = latency
                NimboMihomoPingCache.save(latency, name: name, sourceSHA256: request.sourceSHA256)
            }
            if pingRunID == runID { pingName = nil; pingTask = nil; pingRunID = nil }
        }
    }
}
