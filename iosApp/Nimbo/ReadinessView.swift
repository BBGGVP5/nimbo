import SwiftUI
import NetworkExtension

struct ReadinessView: View {
    @EnvironmentObject private var vpn: VpnController
    @State private var checks: [Check] = []
    @State private var busy = false

    private struct Check: Identifiable {
        let id: String
        let title: String
        let detail: String
        let passed: Bool?
    }

    var body: some View {
        List {
            Section {
                Text("Проверка не подключает VPN и не отправляет данные. Наличие профиля подписи — только косвенная проверка; окончательное решение принимает iOS.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            Section("Готовность установки") {
                ForEach(checks) { check in
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: check.passed == true ? "checkmark.circle" : check.passed == false ? "exclamationmark.circle" : "questionmark.circle")
                            .foregroundStyle(check.passed == false ? Color.orange : Color.accentColor)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(check.title).font(.headline)
                            Text(check.detail).font(.subheadline).foregroundStyle(.secondary)
                            Text(check.id).font(.caption.monospaced()).foregroundStyle(.secondary)
                        }
                    }.padding(.vertical, 4)
                }
            }
            Section {
                Button("Проверить снова") { Task { await inspect() } }.disabled(busy)
                Button("Настроить VPN-профиль") {
                    Task {
                        busy = true
                        await vpn.prepare()
                        await inspect()
                    }
                }.disabled(busy || vpn.state == .connected || vpn.state == .connecting || vpn.state == .disconnecting || vpn.state == .preparing)
                Text("Настройка может вызвать системный запрос разрешения VPN. Туннель не запускается.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Section("Быстрое управление") {
                Text("В приложении «Команды»: Подключить Nimbo, Отключить Nimbo, Состояние Nimbo. Сначала добавьте подписку и выберите сервер в Nimbo.")
                if #available(iOS 18.0, *) {
                    Text("В Пункте управления удерживайте свободное место → Добавить элемент управления → Nimbo.")
                } else {
                    Text("Элемент Пункта управления доступен с iOS 18. На этой версии используйте «Команды».")
                }
            }
        }
        .navigationTitle("Готовность iOS")
        .task { await inspect() }
    }

    @MainActor private func inspect() async {
        busy = true
        defer { busy = false }
        var result: [Check] = []
        let pluginURLs = Bundle.main.builtInPlugInsURL.flatMap {
            try? FileManager.default.contentsOfDirectory(at: $0, includingPropertiesForKeys: nil)
        } ?? []
        let tunnel = pluginURLs.compactMap { Bundle(url: $0) }.first {
            (($0.infoDictionary?["NSExtension"] as? [String: Any])?["NSExtensionPointIdentifier"] as? String) == "com.apple.networkextension.packet-tunnel"
        }
        let executablePresent = tunnel?.executableURL.map { FileManager.default.fileExists(atPath: $0.path) } ?? false
        let assetsPresent = ["geoip.dat", "geosite.dat"].allSatisfy { name in
            tunnel?.resourceURL.map { FileManager.default.fileExists(atPath: $0.appendingPathComponent(name).path) } ?? false
        }
        result.append(Check(id: "IOS_READY_CORE_ASSETS", title: "Данные ядра", detail: assetsPresent ? "Базы geoip и geosite присутствуют в расширении." : "Не найдены базы ядра. Требуется полная сборка IPA с ресурсами туннеля.", passed: assetsPresent))
        result.append(Check(id: "IOS_READY_EXTENSION", title: "Расширение туннеля", detail: executablePresent ? "Расширение и исполняемый файл присутствуют." : "Расширение отсутствует или повреждено. При переподписи сохраняйте вложенные расширения.", passed: executablePresent))
        let appProfile = NimboSigningReport.applicationProfile
        let tunnelProfile = NimboSigningReport.packetTunnelProfile
        for (id, title, profile) in [("APP", "Подпись приложения", appProfile), ("TUNNEL", "Подпись расширения", tunnelProfile)] {
            let passed: Bool? = profile.map { !$0.expired && $0.allowsNetworkExtension }
            result.append(Check(id: "IOS_READY_SIGNING_\(id)", title: title,
                detail: profile == nil ? "Профиль Apple не найден. Нельзя подтвердить права по этому файлу; это не проверка фактической подписи." : passed == true ? "Профиль не истёк и разрешает Packet Tunnel. Фактические права подписи проверяет iOS." : "Профиль истёк или не разрешает Packet Tunnel. Проверьте переподпись.", passed: passed))
        }
        if let appTeam = appProfile?.teamIdentifier, let tunnelTeam = tunnelProfile?.teamIdentifier {
            result.append(Check(id: "IOS_READY_TEAM", title: "Совместимость профилей", detail: appTeam == tunnelTeam ? "Команда приложения и расширения совпадает." : "Приложение и расширение принадлежат разным командам.", passed: appTeam == tunnelTeam))
        }
        do {
            let manager = try await NimboTunnelControl.manager()
            let data = (manager?.protocolConfiguration as? NETunnelProviderProtocol)?.providerConfiguration?["configData"] as? Data
            result.append(Check(id: "IOS_READY_MANAGER", title: "Системный VPN-профиль", detail: manager == nil ? "Профиль Nimbo не найден. Нажмите «Настроить VPN-профиль»." : "Найден профиль именно для вложенного расширения Nimbo.", passed: manager != nil))
            result.append(Check(id: "IOS_READY_CONFIG", title: "Конфигурация сервера", detail: data?.isEmpty == false ? "Конфигурация передана расширению через VPN-профиль." : "Добавьте подписку и выберите сервер. Пустую конфигурацию запускать нельзя.", passed: data?.isEmpty == false))
        } catch {
            result.append(Check(id: "IOS_READY_MANAGER_ERROR", title: "Доступ к VPN-профилям", detail: NimboRedactor.redact(error.localizedDescription), passed: false))
        }
        result.append(Check(id: "IOS_READY_STORAGE", title: "Передача данных расширению", detail: "Конфигурация передаётся через providerConfiguration. App Groups необязательны для запуска туннеля.", passed: nil))
        checks = result
        await NimboDiagnostics.shared.record(.info, stage: .permission, code: "IOS_READINESS_CHECK", message: result.map { "\($0.id)=\($0.passed.map { $0 ? "ok" : "attention" } ?? "unknown")" }.joined(separator: "; "))
    }
}
