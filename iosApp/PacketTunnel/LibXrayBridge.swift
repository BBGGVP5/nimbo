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
}

enum LibXrayBridgeError: LocalizedError {
    case requestTooLarge
    case invalidRequestEncoding
    case emptyResponse
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
        case let .invalidResponse(details):
            "Некорректный ответ VPN-ядра: \(details) (IOS_CORE_INVALID_RESPONSE)."
        case let .core(details):
            "VPN-ядро сообщило об ошибке: \(details) (IOS_CORE_FAILURE)."
        }
    }
}
