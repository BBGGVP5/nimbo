import CryptoKit
import Foundation
import Network

/// Протокол кросс-синхронизации `nimbo-cross-sync-v1`.
///
/// Реализация повторяет андроидную (`sync/CrossPlatformSync.kt`) байт в байт:
/// кадр — четыре байта длины и JSON-конверт, содержимое зашифровано AES-256-GCM
/// с дополнительными данными «nimbo-sync-v1:<сеанс>». Любое расхождение здесь
/// означает, что вторая сторона просто не расшифрует пакет, поэтому имена полей
/// и порядок действий скопированы точно.
enum NimboSyncProtocol {
    static let schema = "nimbo-cross-sync-v1"
    static let aadPrefix = "nimbo-sync-v1:"
    static let maxFrameBytes = 2 * 1024 * 1024

    /// Ссылка сопряжения из QR: `nimbo-sync://pair?v=1&host=…&port=…&sid=…&key=…&exp=…`.
    struct PairingSession: Equatable {
        let host: String
        let port: Int
        let sessionId: String
        let key: Data
        let expiresAtMs: Int64
        let comparisonCode: String?

        var isExpired: Bool { Int64(Date().timeIntervalSince1970 * 1000) >= expiresAtMs }
    }

    static func parsePairingLink(_ raw: String) throws -> PairingSession {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let components = URLComponents(string: trimmed),
              components.scheme?.lowercased() == "nimbo-sync",
              components.host?.lowercased() == "pair" else {
            throw NimboSyncError.invalidLink("Это не ссылка синхронизации Nimbo")
        }
        var query: [String: String] = [:]
        for item in components.queryItems ?? [] {
            query[item.name] = item.value
        }
        guard query["v"] == "1" else {
            throw NimboSyncError.invalidLink("Версия протокола не поддерживается")
        }
        guard let host = query["host"], isPrivateIPv4(host) else {
            throw NimboSyncError.invalidLink("Ссылка указывает не на локальный адрес")
        }
        guard let port = query["port"].flatMap(Int.init), (1024 ... 65535).contains(port) else {
            throw NimboSyncError.invalidLink("Некорректный порт синхронизации")
        }
        guard let sessionId = query["sid"], !sessionId.isEmpty, sessionId.count <= 128 else {
            throw NimboSyncError.invalidLink("В ссылке нет сеанса")
        }
        guard let key = decodeBase64Url(query["key"] ?? ""), key.count == 32 else {
            throw NimboSyncError.invalidLink("Повреждён ключ синхронизации")
        }
        guard let expires = query["exp"].flatMap(Int64.init) else {
            throw NimboSyncError.invalidLink("В ссылке нет срока действия")
        }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        guard expires > now else {
            throw NimboSyncError.invalidLink("Ссылка устарела — обновите её на втором устройстве")
        }
        return PairingSession(
            host: host,
            port: port,
            sessionId: sessionId,
            key: key,
            expiresAtMs: expires,
            comparisonCode: query["code"]
        )
    }

    /// Вторая сторона принимает только частные адреса: сеанс живёт в локальной
    /// сети и наружу не выходит.
    static func isPrivateIPv4(_ host: String) -> Bool {
        let parts = host.split(separator: ".").compactMap { Int($0) }
        guard parts.count == 4, parts.allSatisfy({ (0 ... 255).contains($0) }) else { return false }
        switch (parts[0], parts[1]) {
        case (10, _): return true
        case (192, 168): return true
        case (172, let second) where (16 ... 31).contains(second): return true
        case (169, 254): return true
        case (127, _): return true
        default: return false
        }
    }

    // MARK: - base64url без дополнения, как в Java Base64.getUrlEncoder().withoutPadding()

    static func encodeBase64Url(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func decodeBase64Url(_ value: String) -> Data? {
        var normalized = value
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = normalized.count % 4
        if remainder > 0 {
            normalized += String(repeating: "=", count: 4 - remainder)
        }
        return Data(base64Encoded: normalized)
    }
}

enum NimboSyncError: LocalizedError {
    case invalidLink(String)
    case connection(String)
    case decryption
    case protocolMismatch(String)
    case remote(String)

    var errorDescription: String? {
        switch self {
        case let .invalidLink(reason): reason
        case let .connection(reason): "Не удалось связаться с устройством: \(reason)"
        case .decryption: "Пакет синхронизации не расшифровался — ключ не подходит"
        case let .protocolMismatch(reason): "Неожиданный ответ второго устройства: \(reason)"
        case let .remote(message): message
        }
    }
}

// MARK: - Шифрование

enum NimboSyncCrypto {
    /// AES-256-GCM: 12-байтовый nonce, тег на 128 бит дописан к шифротексту —
    /// ровно то, что возвращает JCE на Android.
    static func encrypt(key: Data, sessionId: String, plaintext: Data) throws -> NimboSyncEnvelope {
        let symmetricKey = SymmetricKey(data: key)
        let nonce = try AES.GCM.Nonce()
        let aad = Data((NimboSyncProtocol.aadPrefix + sessionId).utf8)
        let sealed = try AES.GCM.seal(plaintext, using: symmetricKey, nonce: nonce, authenticating: aad)
        let payload = sealed.ciphertext + sealed.tag
        return NimboSyncEnvelope(
            version: 1,
            sessionId: sessionId,
            nonce: NimboSyncProtocol.encodeBase64Url(Data(nonce)),
            ciphertext: NimboSyncProtocol.encodeBase64Url(payload)
        )
    }

    static func decrypt(key: Data, envelope: NimboSyncEnvelope) throws -> Data {
        guard envelope.version == 1,
              let nonceData = NimboSyncProtocol.decodeBase64Url(envelope.nonce),
              nonceData.count == 12,
              let payload = NimboSyncProtocol.decodeBase64Url(envelope.ciphertext),
              payload.count > 16 else {
            throw NimboSyncError.decryption
        }
        let symmetricKey = SymmetricKey(data: key)
        let aad = Data((NimboSyncProtocol.aadPrefix + envelope.sessionId).utf8)
        do {
            let nonce = try AES.GCM.Nonce(data: nonceData)
            let tag = payload.suffix(16)
            let ciphertext = payload.prefix(payload.count - 16)
            let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
            return try AES.GCM.open(box, using: symmetricKey, authenticating: aad)
        } catch {
            throw NimboSyncError.decryption
        }
    }
}

// MARK: - Модель обмена

struct NimboSyncEnvelope: Codable {
    let version: Int
    let sessionId: String
    let nonce: String
    let ciphertext: String

    enum CodingKeys: String, CodingKey {
        case version = "v"
        case sessionId = "sid"
        case nonce
        case ciphertext
    }
}

struct NimboSyncSubscription: Codable {
    let url: String
    let name: String?
    let order: Int
}

struct NimboSyncDeviceInfo: Codable {
    let name: String
    let platform: String
    let osName: String
    let osVersion: String?
    let appVersion: String?
    let architecture: String?

    enum CodingKeys: String, CodingKey {
        case name, platform
        case osName = "os_name"
        case osVersion = "os_version"
        case appVersion = "app_version"
        case architecture
    }
}

struct NimboSyncAppearance: Codable {
    let themeMode: String
    let uiStyle: String
    let accentColor: String
    let panelBrightness: Int
    let transparency: Int
    let blur: Int
    let rounding: Int
    let providerTheme: Bool
    let showSubscriptionLogo: Bool

    enum CodingKeys: String, CodingKey {
        case themeMode = "theme_mode"
        case uiStyle = "ui_style"
        case accentColor = "accent_color"
        case panelBrightness = "panel_brightness"
        case transparency, blur, rounding
        case providerTheme = "provider_theme"
        case showSubscriptionLogo = "show_subscription_logo"
    }
}

struct NimboSyncConnection: Codable {
    let killSwitch: Bool
    let tlsFragmentation: Bool
    let showSpeedChart: Bool

    enum CodingKeys: String, CodingKey {
        case killSwitch = "kill_switch"
        case tlsFragmentation = "tls_fragmentation"
        case showSpeedChart = "show_speed_chart"
    }
}

/// Модуль маршрутизации в переносе: текст правил как его написал человек.
///
/// Переносим исходник, а не разобранные правила: разбор общий для платформ, а
/// текст человек ещё будет править.
struct NimboSyncRoutingModule: Codable {
    let id: String
    let name: String
    let enabled: Bool
    let text: String
}

struct NimboSyncBundle: Codable {
    let schema: String
    let platform: String
    let deviceName: String
    let createdAtMs: Int64
    let deviceInfo: NimboSyncDeviceInfo?
    let subscriptions: [NimboSyncSubscription]
    let appearance: NimboSyncAppearance?
    let connection: NimboSyncConnection?
    let routingModules: [NimboSyncRoutingModule]?

    enum CodingKeys: String, CodingKey {
        case schema, platform, subscriptions, appearance, connection
        case deviceName = "device_name"
        case createdAtMs = "created_at_ms"
        case deviceInfo = "device_info"
        case routingModules = "routing_modules"
    }
}

struct NimboSyncCategories: Codable {
    let subscriptions: Bool
    let appearance: Bool
    let connection: Bool
    let automation: Bool
    /// Пользовательские модули маршрутизации.
    let routing: Bool?

    static let all = NimboSyncCategories(
        subscriptions: true,
        appearance: true,
        connection: true,
        automation: true,
        routing: true
    )
}

struct NimboSyncRequest: Codable {
    let action: String
    let deviceId: String?
    let deviceName: String?
    let bundle: NimboSyncBundle?
    let direction: String?
    let categories: NimboSyncCategories?

    enum CodingKeys: String, CodingKey {
        case action, bundle, direction, categories
        case deviceId = "device_id"
        case deviceName = "device_name"
    }

    init(
        action: String,
        deviceId: String? = nil,
        deviceName: String? = nil,
        bundle: NimboSyncBundle? = nil,
        direction: String? = nil,
        categories: NimboSyncCategories? = nil
    ) {
        self.action = action
        self.deviceId = deviceId
        self.deviceName = deviceName
        self.bundle = bundle
        self.direction = direction
        self.categories = categories
    }
}

struct NimboSyncResponse: Codable {
    let state: String
    let comparisonCode: String?
    let desktopBundle: NimboSyncBundle?
    let desktopDeviceInfo: NimboSyncDeviceInfo?
    let message: String?
    let paired: Bool?
    let deviceId: String?
    let pairedKey: String?
    let direction: String?

    enum CodingKeys: String, CodingKey {
        case state, message, paired, direction
        case comparisonCode = "comparison_code"
        case desktopBundle = "desktop_bundle"
        case desktopDeviceInfo = "desktop_device_info"
        case deviceId = "device_id"
        case pairedKey = "paired_key"
    }
}

// MARK: - Транспорт

/// Один обмен: соединиться, отправить кадр, получить ответ, закрыть.
///
/// Вторая сторона так и работает — каждое действие это отдельное соединение,
/// поэтому долгоживущего сокета здесь нет намеренно.
enum NimboSyncTransport {
    static func exchange(
        session: NimboSyncProtocol.PairingSession,
        request: NimboSyncRequest
    ) async throws -> NimboSyncResponse {
        let encoder = JSONEncoder()
        let plaintext = try encoder.encode(request)
        guard plaintext.count <= NimboSyncProtocol.maxFrameBytes else {
            throw NimboSyncError.protocolMismatch("пакет слишком велик")
        }
        let envelope = try NimboSyncCrypto.encrypt(
            key: session.key,
            sessionId: session.sessionId,
            plaintext: plaintext
        )
        let frame = try encoder.encode(envelope)

        let responseFrame = try await roundTrip(host: session.host, port: session.port, frame: frame)
        let responseEnvelope = try JSONDecoder().decode(NimboSyncEnvelope.self, from: responseFrame)
        guard responseEnvelope.sessionId == session.sessionId else {
            throw NimboSyncError.protocolMismatch("ответ другого сеанса")
        }
        let plain = try NimboSyncCrypto.decrypt(key: session.key, envelope: responseEnvelope)
        return try JSONDecoder().decode(NimboSyncResponse.self, from: plain)
    }

    /// Кадр: четыре байта длины (big-endian) и следом JSON.
    private static func roundTrip(host: String, port: Int, frame: Data) async throws -> Data {
        guard let endpointPort = NWEndpoint.Port(rawValue: UInt16(port)) else {
            throw NimboSyncError.connection("некорректный порт")
        }
        let connection = NWConnection(
            host: NWEndpoint.Host(host),
            port: endpointPort,
            using: .tcp
        )
        let queue = DispatchQueue(label: "com.nimbo.sync")

        return try await withCheckedThrowingContinuation { continuation in
            let guardFlag = NimboSyncOnce()

            func fail(_ error: Error) {
                guard guardFlag.take() else { return }
                connection.cancel()
                continuation.resume(throwing: error)
            }

            func succeed(_ data: Data) {
                guard guardFlag.take() else { return }
                connection.cancel()
                continuation.resume(returning: data)
            }

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    var header = Data(count: 4)
                    let length = UInt32(frame.count)
                    header[0] = UInt8((length >> 24) & 0xFF)
                    header[1] = UInt8((length >> 16) & 0xFF)
                    header[2] = UInt8((length >> 8) & 0xFF)
                    header[3] = UInt8(length & 0xFF)
                    connection.send(content: header + frame, completion: .contentProcessed { error in
                        if let error {
                            fail(NimboSyncError.connection(error.localizedDescription))
                            return
                        }
                        receiveResponse(connection: connection, onDone: succeed, onFail: fail)
                    })
                case let .failed(error):
                    fail(NimboSyncError.connection(error.localizedDescription))
                case .cancelled:
                    fail(NimboSyncError.connection("соединение закрыто"))
                default:
                    break
                }
            }
            connection.start(queue: queue)
            queue.asyncAfter(deadline: .now() + 12) {
                fail(NimboSyncError.connection("устройство не ответило"))
            }
        }
    }

    private static func receiveResponse(
        connection: NWConnection,
        onDone: @escaping (Data) -> Void,
        onFail: @escaping (Error) -> Void
    ) {
        connection.receive(minimumIncompleteLength: 4, maximumLength: 4) { header, _, _, error in
            if let error {
                onFail(NimboSyncError.connection(error.localizedDescription))
                return
            }
            guard let header, header.count == 4 else {
                onFail(NimboSyncError.protocolMismatch("оборванный заголовок"))
                return
            }
            let length = Int(header[0]) << 24 | Int(header[1]) << 16 | Int(header[2]) << 8 | Int(header[3])
            guard length > 0, length <= NimboSyncProtocol.maxFrameBytes else {
                onFail(NimboSyncError.protocolMismatch("некорректная длина ответа"))
                return
            }
            var received = Data()

            func readMore() {
                connection.receive(
                    minimumIncompleteLength: 1,
                    maximumLength: length - received.count
                ) { chunk, _, isComplete, error in
                    if let error {
                        onFail(NimboSyncError.connection(error.localizedDescription))
                        return
                    }
                    if let chunk { received.append(chunk) }
                    if received.count >= length {
                        onDone(received)
                    } else if isComplete {
                        onFail(NimboSyncError.protocolMismatch("ответ оборван"))
                    } else {
                        readMore()
                    }
                }
            }
            readMore()
        }
    }
}

/// Продолжение можно возобновить только один раз, а обработчики соединения и
/// таймаут иногда срабатывают вместе.
private final class NimboSyncOnce: @unchecked Sendable {
    private let lock = NSLock()
    private var used = false

    func take() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if used { return false }
        used = true
        return true
    }
}
