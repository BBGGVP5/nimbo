import Foundation
import NetworkExtension

@MainActor
final class VpnController: ObservableObject {
    enum State: Equatable {
        case idle
        case preparing
        case connecting
        case connected
        case disconnecting
        case failed(code: String, message: String)
    }

    @Published private(set) var state: State = .idle
    @Published private(set) var manager: NETunnelProviderManager?

    private var statusObserver: NSObjectProtocol?

    init() {
        statusObserver = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.synchronizeStatus() }
        }
    }

    deinit {
        if let statusObserver { NotificationCenter.default.removeObserver(statusObserver) }
    }

    func prepare() async {
        state = .preparing
        await NimboDiagnostics.shared.record(
            .info,
            stage: .permission,
            code: "IOS_VPN_PREPARE",
            message: "Подготовка системной конфигурации VPN",
            metadata: signingContractMetadata
        )
        do {
            manager = try await loadOrCreateManager()
            if !hasStagedConfiguration,
               let stored = try NimboConfigurationStore.shared.loadConfiguration() {
                try await stageConfiguration(data: stored)
            }
            synchronizeStatus()
        } catch {
            fail(code: "IOS_VPN_MANAGER_LOAD_FAILED", error: error)
        }
    }

    func stageConfiguration(json: String) async throws {
        guard let data = json.data(using: .utf8) else {
            throw VpnControllerError.emptyConfiguration
        }
        try await stageConfiguration(data: data)
    }

    func stageConfiguration(data: Data) async throws {
        guard !data.isEmpty else { throw VpnControllerError.emptyConfiguration }
        guard data.count <= 15 * 1_024 * 1_024 else { throw VpnControllerError.configurationTooLarge }
        if manager == nil { manager = try await loadOrCreateManager() }
        guard let manager,
              let tunnelProtocol = manager.protocolConfiguration as? NETunnelProviderProtocol else {
            throw VpnControllerError.managerUnavailable
        }
        tunnelProtocol.providerConfiguration = [
            "schema": 2,
            "configData": data
        ]
        manager.protocolConfiguration = tunnelProtocol
        try await manager.saveToPreferences()
        try await manager.loadFromPreferences()
        await NimboDiagnostics.shared.record(
            .info,
            stage: .config,
            code: "IOS_CONFIG_STAGED",
            message: "Активная конфигурация безопасно передана Packet Tunnel",
            metadata: [
                "bytes": "\(data.count)",
                "schema": "2",
                "selected_server_id_present": NimboConfigurationStore.shared.activeServerID == nil ? "false" : "true"
            ]
        )
    }

    func clearConfiguration() async throws {
        if state == .connected || state == .connecting { await disconnect() }
        if manager == nil { manager = try await loadOrCreateManager() }
        guard let manager,
              let tunnelProtocol = manager.protocolConfiguration as? NETunnelProviderProtocol else {
            throw VpnControllerError.managerUnavailable
        }
        tunnelProtocol.providerConfiguration = ["schema": 2]
        manager.protocolConfiguration = tunnelProtocol
        try await manager.saveToPreferences()
        try await manager.loadFromPreferences()
    }

    func providerStatus() async throws -> [String: Any] {
        try await sendProviderCommand("status")
    }

    func providerDiagnostics() async throws -> Data {
        let response = try await sendProviderCommand("diagnostics")
        guard response["ok"] as? Bool == true,
              let records = response["records"] as? String else {
            throw VpnControllerError.providerMessageEmpty
        }
        let summary: [String: Any] = [
            "core_version": response["version"] as? String ?? "unknown",
            "core_running": response["running"] as? Bool ?? false,
            "outbounds": response["outbounds"] as? Int ?? 0
        ]
        var result = (try? JSONSerialization.data(withJSONObject: summary, options: [.prettyPrinted, .sortedKeys])) ?? Data()
        result.append(Data("\n\nPACKET TUNNEL EVENTS\n".utf8))
        result.append(Data(records.utf8))
        return result
    }

    private func sendProviderCommand(_ command: String) async throws -> [String: Any] {
        guard let session = manager?.connection as? NETunnelProviderSession else {
            throw VpnControllerError.managerUnavailable
        }
        let request = try JSONSerialization.data(withJSONObject: ["command": command])
        let response: Data = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            do {
                try session.sendProviderMessage(request) { data in
                    if let data { continuation.resume(returning: data) }
                    else { continuation.resume(throwing: VpnControllerError.providerMessageEmpty) }
                }
            } catch {
                continuation.resume(throwing: error)
            }
        }
        guard let object = try JSONSerialization.jsonObject(with: response) as? [String: Any] else {
            throw VpnControllerError.providerMessageEmpty
        }
        return object
    }

    func connect() async {
        do {
            if manager == nil { manager = try await loadOrCreateManager() }
            guard let manager else { throw VpnControllerError.managerUnavailable }
            guard let tunnelProtocol = manager.protocolConfiguration as? NETunnelProviderProtocol,
                  let configuration = tunnelProtocol.providerConfiguration?["configData"] as? Data,
                  !configuration.isEmpty else {
                throw VpnControllerError.missingConfiguration
            }
            state = .connecting
            await NimboDiagnostics.shared.record(.info, stage: .tunnelStart, code: "IOS_TUNNEL_START_REQUESTED", message: "Запуск Packet Tunnel запрошен пользователем")
            try manager.connection.startVPNTunnel()
        } catch {
            fail(code: "IOS_TUNNEL_START_FAILED", error: error)
        }
    }

    func disconnect() async {
        state = .disconnecting
        manager?.connection.stopVPNTunnel()
        await NimboDiagnostics.shared.record(.info, stage: .stop, code: "IOS_TUNNEL_STOP_REQUESTED", message: "Остановка Packet Tunnel запрошена пользователем")
    }

    private func loadOrCreateManager() async throws -> NETunnelProviderManager {
        let existing = try await NETunnelProviderManager.loadAllFromPreferences()
            .first { ($0.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier == NimboConstants.packetTunnelBundleIdentifier }
        let value = existing ?? NETunnelProviderManager()
        let tunnelProtocol = (value.protocolConfiguration as? NETunnelProviderProtocol) ?? NETunnelProviderProtocol()
        tunnelProtocol.providerBundleIdentifier = NimboConstants.packetTunnelBundleIdentifier
        tunnelProtocol.serverAddress = "Nimbo"
        if tunnelProtocol.providerConfiguration == nil {
            tunnelProtocol.providerConfiguration = ["schema": 2]
        }
        value.protocolConfiguration = tunnelProtocol
        value.localizedDescription = "Nimbo"
        value.isEnabled = true
        try await value.saveToPreferences()
        try await value.loadFromPreferences()
        return value
    }

    private func synchronizeStatus() {
        guard let status = manager?.connection.status else { return }
        switch status {
        case .invalid, .disconnected: state = .idle
        case .connecting, .reasserting: state = .connecting
        case .connected: state = .connected
        case .disconnecting: state = .disconnecting
        @unknown default: state = .failed(code: "IOS_VPN_UNKNOWN_STATE", message: "Неизвестное состояние системного VPN")
        }
    }

    private var hasStagedConfiguration: Bool {
        guard let tunnelProtocol = manager?.protocolConfiguration as? NETunnelProviderProtocol,
              let data = tunnelProtocol.providerConfiguration?["configData"] as? Data else {
            return false
        }
        return !data.isEmpty
    }

    private func fail(code: String, error: Error) {
        let presentation = errorPresentation(defaultCode: code, error: error)
        state = .failed(code: presentation.code, message: presentation.message)
        Task {
            var metadata = signingContractMetadata
            metadata["error_domain"] = presentation.domain
            metadata["error_number"] = presentation.number
            metadata["raw_error"] = NimboRedactor.redact(error.localizedDescription)
            await NimboDiagnostics.shared.record(
                .error,
                stage: .tunnelStart,
                code: presentation.code,
                message: presentation.message,
                metadata: metadata
            )
        }
    }

    private func errorPresentation(
        defaultCode: String,
        error: Error
    ) -> (code: String, message: String, domain: String, number: String) {
        let nsError = error as NSError
        let raw = nsError.localizedDescription.lowercased()
        let permissionDenied = raw.contains("permission denied")
            || raw.contains("not permitted")
            || nsError.domain == NSCocoaErrorDomain && nsError.code == NSFileWriteNoPermissionError

        if permissionDenied {
            return (
                "IOS_VPN_PERMISSION_DENIED",
                "iOS не дала приложению право на Packet Tunnel. Переподпишите Nimbo и вложенное расширение одним сертификатом с разрешением Network Extensions.",
                nsError.domain,
                "\(nsError.code)"
            )
        }

        return (
            defaultCode,
            NimboRedactor.redact(error.localizedDescription),
            nsError.domain,
            "\(nsError.code)"
        )
    }

    private var signingContractMetadata: [String: String] {
        let extensionURL = Bundle.main.builtInPlugInsURL?
            .appendingPathComponent("NimboPacketTunnel.appex", isDirectory: true)
        return [
            "main_bundle_id": NimboConstants.mainBundleIdentifier,
            "provider_bundle_id": NimboConstants.packetTunnelBundleIdentifier,
            "provider_embedded": extensionURL.map { FileManager.default.fileExists(atPath: $0.path) } == true ? "true" : "false",
            "required_entitlement": "packet-tunnel-provider",
            "install_note": "main app and embedded extension must be re-signed together"
        ]
    }
}

enum VpnControllerError: LocalizedError {
    case emptyConfiguration
    case missingConfiguration
    case managerUnavailable
    case configurationTooLarge
    case providerMessageEmpty

    var errorDescription: String? {
        switch self {
        case .emptyConfiguration: "Конфигурация пуста (IOS_CONFIG_EMPTY)."
        case .missingConfiguration: "Сначала выберите сервер или импортируйте подписку (IOS_CONFIG_MISSING)."
        case .managerUnavailable: "Системная конфигурация VPN недоступна (IOS_VPN_MANAGER_UNAVAILABLE)."
        case .configurationTooLarge: "Конфигурация превышает лимит 15 МиБ (IOS_CONFIG_TOO_LARGE)."
        case .providerMessageEmpty: "Расширение VPN не вернуло статус (IOS_PROVIDER_MESSAGE_EMPTY)."
        }
    }
}
