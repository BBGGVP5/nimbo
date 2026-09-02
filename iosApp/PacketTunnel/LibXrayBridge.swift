import Darwin
import Foundation
import LibXray

/// Small, serialization-safe wrapper around the official libXray C ABI.
///
/// libXray owns the process-wide Xray instance, therefore every call must be
/// made through the Packet Tunnel target and the caller must not run ping/test
/// methods while the tunnel is active.
final class LibXrayBridge {
    private static let apiVersion = 1
    private static let maximumEnvelopeBytes = 16 * 1_024 * 1_024

    /// Xray's iOS TUN implementation reads the NetworkExtension descriptor
    /// and geo-data directory from the process environment. Putting these
    /// values into the Xray JSON does not configure the Darwin TUN backend.
    func configureRuntimeEnvironment(
        tunnelFileDescriptor: Int32,
        assetDirectory: String
    ) throws {
        guard tunnelFileDescriptor >= 0 else {
            throw LibXrayBridgeError.invalidTunnelDescriptor
        }
        guard !assetDirectory.isEmpty else {
            throw LibXrayBridgeError.invalidAssetDirectory
        }

        let descriptor = String(tunnelFileDescriptor)
        try setEnvironmentValue(descriptor, key: "xray.tun.fd")
        try setEnvironmentValue(descriptor, key: "XRAY_TUN_FD")
        try setEnvironmentValue(assetDirectory, key: "xray.location.asset")
        try setEnvironmentValue(assetDirectory, key: "XRAY_LOCATION_ASSET")

    }

    func convertShareText(_ text: String) throws -> [String: Any] {
        let data = try invoke(
            method: "convertShareLinksToXrayJson",
            payload: ["text": text]
        )
        guard let configuration = data as? [String: Any] else {
            throw LibXrayBridgeError.invalidResponse("The converted configuration is not a JSON object")
        }
        return configuration
    }

    func run(configurationJSON: String) throws {
        _ = try invoke(
            method: "runXrayFromJson",
            payload: ["configJSON": configurationJSON]
        )
    }

    func stop() throws {
        _ = try invoke(method: "stopXray", payload: [:])
    }

    func isRunning() throws -> Bool {
        let data = try invoke(method: "getXrayState", payload: [:])
        guard let state = data as? [String: Any], let running = state["running"] as? Bool else {
            throw LibXrayBridgeError.invalidResponse("The Xray state response is incomplete")
        }
        return running
    }

    func version() throws -> String {
        let data = try invoke(method: "xrayVersion", payload: [:])
        guard let object = data as? [String: Any],
              let version = object["version"] as? String,
              !version.isEmpty else {
            throw LibXrayBridgeError.invalidResponse("The Xray version response is incomplete")
        }
        return version
    }

    private func invoke(method: String, payload: [String: Any]) throws -> Any {
        let request: [String: Any] = [
            "apiVersion": Self.apiVersion,
            "method": method,
            "payload": payload
        ]
        let requestData = try JSONSerialization.data(withJSONObject: request, options: [.sortedKeys])
        guard requestData.count <= Self.maximumEnvelopeBytes else {
            throw LibXrayBridgeError.requestTooLarge
        }
        guard let requestJSON = String(data: requestData, encoding: .utf8) else {
            throw LibXrayBridgeError.invalidRequestEncoding
        }

        let responseJSON: String = try requestJSON.withCString { requestPointer in
            guard let responsePointer = CGoInvoke(UnsafeMutablePointer(mutating: requestPointer)) else {
                throw LibXrayBridgeError.emptyResponse
            }
            defer { CGoFree(responsePointer) }
            return String(cString: responsePointer)
        }

        guard let responseData = responseJSON.data(using: .utf8),
              responseData.count <= Self.maximumEnvelopeBytes,
              let response = try JSONSerialization.jsonObject(with: responseData) as? [String: Any],
              let success = response["success"] as? Bool else {
            throw LibXrayBridgeError.invalidResponse("libXray returned an invalid JSON envelope")
        }

        if !success {
            let rawError = (response["error"] as? String) ?? "Unknown libXray error"
            throw LibXrayBridgeError.core(NimboRedactor.redact(rawError))
        }
        return response["data"] ?? [:]
    }

    private func setEnvironmentValue(_ value: String, key: String) throws {
        let result = key.withCString { keyPointer in
            value.withCString { valuePointer in
                Darwin.setenv(keyPointer, valuePointer, 1)
            }
        }
        guard result == 0 else {
            throw LibXrayBridgeError.environment(errno)
        }
    }
}

enum LibXrayBridgeError: LocalizedError {
    case requestTooLarge
    case invalidRequestEncoding
    case emptyResponse
    case invalidTunnelDescriptor
    case invalidAssetDirectory
    case environment(Int32)
    case invalidResponse(String)
    case core(String)

    var errorDescription: String? {
        switch self {
        case .requestTooLarge:
            "Конфигурация превышает лимит libXray 16 МиБ (IOS_CORE_REQUEST_TOO_LARGE)."
        case .invalidRequestEncoding:
            "Не удалось подготовить запрос к VPN-ядру (IOS_CORE_REQUEST_ENCODING)."
        case .emptyResponse:
            "VPN-ядро не вернуло ответ (IOS_CORE_EMPTY_RESPONSE)."
        case .invalidTunnelDescriptor:
            "Packet Tunnel вернул некорректный TUN-дескриптор (IOS_TUN_FD_INVALID)."
        case .invalidAssetDirectory:
            "Не найден каталог данных VPN-ядра (IOS_CORE_ASSET_PATH_INVALID)."
        case let .environment(code):
            "Не удалось передать TUN-окружение VPN-ядру: errno \(code) (IOS_CORE_ENVIRONMENT)."
        case let .invalidResponse(details):
            "Некорректный ответ VPN-ядра: \(details) (IOS_CORE_INVALID_RESPONSE)."
        case let .core(details):
            "VPN-ядро сообщило об ошибке: \(details) (IOS_CORE_FAILURE)."
        }
    }
}
