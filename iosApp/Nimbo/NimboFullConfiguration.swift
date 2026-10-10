import Foundation
import CryptoKit

/// Authoritative source, not a generated runtime config or a promise of mobile support.
/// Keep this record in Keychain; descriptions must never expose provider credentials.
struct NimboFullConfiguration: Codable, Equatable, CustomStringConvertible, Sendable {
    static let maximumSourceBytes = 4 * 1_024 * 1_024
    let schemaVersion: Int
    let coreId: String
    let format: String
    let originalYAML: String
    /// Foundation's textual JSON bridge may consume a leading BOM. Base64
    /// Data retains the authoritative bytes; older records can omit this field.
    private let originalUTF8: Data?
    let sourceSHA256: String
    let source: String?
    let title: String
    /// Desired choices only, scoped by this record's exact sourceSHA256.
    let groupSelections: [String: String]

    var description: String { "NimboFullConfiguration(source=<redacted>)" }
    var sourceData: Data { originalUTF8 ?? Data(originalYAML.utf8) }

    init(data: Data, source: String?, title: String = "Mihomo", groupSelections: [String: String] = [:]) throws {
        guard !data.isEmpty, data.count <= Self.maximumSourceBytes else {
            throw NimboFullConfigurationError.invalidSize
        }
        // String(data:encoding:) can consume a BOM. Validate the exact round trip
        // instead, preserving BOM, CRLF, trailing whitespace and Unicode spelling.
        let text = String(decoding: data, as: UTF8.self)
        guard Data(text.utf8) == data else { throw NimboFullConfigurationError.invalidEncoding }
        schemaVersion = 1
        coreId = "mihomo"
        format = "mihomo-yaml"
        originalYAML = text
        originalUTF8 = data
        sourceSHA256 = Self.digest(data)
        self.source = source
        self.title = title
        self.groupSelections = groupSelections
        try validate()
    }

    private enum CodingKeys: String, CodingKey {
        case schemaVersion, coreId, format, originalYAML, originalUTF8, sourceSHA256, source, title, groupSelections
    }

    init(from decoder: Decoder) throws {
        let fields = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try fields.decode(Int.self, forKey: .schemaVersion)
        coreId = try fields.decode(String.self, forKey: .coreId)
        format = try fields.decode(String.self, forKey: .format)
        let textualYAML = try fields.decode(String.self, forKey: .originalYAML)
        originalUTF8 = try fields.decodeIfPresent(Data.self, forKey: .originalUTF8)
        originalYAML = originalUTF8.map { String(decoding: $0, as: UTF8.self) } ?? textualYAML
        // The shadow string must agree semantically; it cannot replace the bytes.
        guard textualYAML == originalYAML else { throw NimboFullConfigurationError.sourceMismatch }
        sourceSHA256 = try fields.decode(String.self, forKey: .sourceSHA256)
        source = try fields.decodeIfPresent(String.self, forKey: .source)
        title = try fields.decode(String.self, forKey: .title)
        groupSelections = try fields.decode([String: String].self, forKey: .groupSelections)
        try validate()
    }

    func validate() throws {
        guard schemaVersion == 1, coreId == "mihomo", format == "mihomo-yaml" else {
            throw NimboFullConfigurationError.unsupportedRecord
        }
        guard !sourceData.isEmpty, sourceData.count <= Self.maximumSourceBytes else {
            throw NimboFullConfigurationError.invalidSize
        }
        guard Data(originalYAML.utf8) == sourceData else {
            throw NimboFullConfigurationError.invalidEncoding
        }
        guard sourceSHA256 == Self.digest(sourceData) else {
            throw NimboFullConfigurationError.sourceMismatch
        }
        guard title.utf8.count <= 1_024, groupSelections.count <= 20_000,
              groupSelections.allSatisfy({ !$0.key.isEmpty && !$0.value.isEmpty &&
                  $0.key.utf8.count <= 4_096 && $0.value.utf8.count <= 4_096 }) else {
            throw NimboFullConfigurationError.invalidMetadata
        }
        if let source {
            guard source.utf8.count <= 16_384, let url = URL(string: source),
                  url.host != nil, ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
                throw NimboFullConfigurationError.invalidSource
            }
        }
    }

    /// Do not extract source text through JSONSerialization/NSString: that
    /// bridge can consume a UTF-8 BOM inside an otherwise valid JSON string.
    static func sourceData(fromPayload data: Data) throws -> Data {
        guard let fields = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              fields["originalYAML"] != nil else { return data }
        if fields["schemaVersion"] != nil || fields["originalUTF8"] != nil {
            return try JSONDecoder().decode(Self.self, from: data).sourceData
        }
        struct Source: Decodable { let originalYAML: String }
        return Data(try JSONDecoder().decode(Source.self, from: data).originalYAML.utf8)
    }

    func replacingSource(_ data: Data) throws -> Self {
        let unchanged = Self.digest(data) == sourceSHA256
        return try Self(data: data, source: source, title: title,
                        groupSelections: unchanged ? groupSelections : [:])
    }

    /// Call only after live select/readback acknowledged this source/session.
    func recordingSelection(group: String, member: String, expectedSourceSHA256: String) throws -> Self {
        guard expectedSourceSHA256 == sourceSHA256 else { throw NimboFullConfigurationError.sourceMismatch }
        var choices = groupSelections
        choices[group] = member
        return try Self(data: sourceData, source: source, title: title, groupSelections: choices)
    }

    static func digest(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}

enum NimboFullConfigurationError: LocalizedError {
    case invalidSize, invalidEncoding, unsupportedRecord, sourceMismatch, invalidMetadata, invalidSource
    case fullConfigurationActive, inspectionFailed, staleRefresh

    var errorDescription: String? {
        switch self {
        case .invalidSize: "Полная конфигурация пуста или превышает 4 МиБ (IOS_FULL_CONFIG_SIZE)."
        case .invalidEncoding: "Конфигурация должна быть точным UTF-8 текстом (IOS_FULL_CONFIG_ENCODING)."
        case .unsupportedRecord: "Формат полной конфигурации не поддерживается (IOS_FULL_CONFIG_SCHEMA)."
        case .sourceMismatch: "Исходник конфигурации изменился (IOS_FULL_CONFIG_IDENTITY)."
        case .invalidMetadata: "Некорректные метаданные конфигурации (IOS_FULL_CONFIG_METADATA)."
        case .invalidSource: "Некорректный адрес обновления конфигурации (IOS_FULL_CONFIG_SOURCE)."
        case .fullConfigurationActive: "Для полной конфигурации используйте выбор группы, а не отдельного сервера (IOS_FULL_CONFIG_ACTIVE)."
        case .inspectionFailed: "Ядро не подтвердило исходник конфигурации (IOS_FULL_CONFIG_INSPECTION)."
        case .staleRefresh: "Конфигурация изменилась во время обновления (IOS_FULL_CONFIG_STALE)."
        }
    }
}

/// Wire API 1 inspection identity. The declared graph remains an independent
/// projection; neither a successful inspection nor stored YAML means TUN ready.
struct NimboMihomoInspection {
    let originalYAML: String
    let sourceSHA256: String

    static func decode(_ response: Data, requestID: String, sourceData: Data) throws -> Self {
        struct Payload: Decodable { let originalYAML: String; let sourceSHA256: String }
        struct Envelope: Decodable {
            let apiVersion: Int
            let requestId: String
            let success: Bool
            let generation: UInt64
            let data: Payload?
        }
        guard response.count <= 16 * 1_024 * 1_024, !requestID.isEmpty,
              !sourceData.isEmpty, sourceData.count <= NimboFullConfiguration.maximumSourceBytes,
              let envelope = try? JSONDecoder().decode(Envelope.self, from: response),
              envelope.apiVersion == 1, envelope.requestId == requestID, envelope.success,
              let payload = envelope.data, Data(payload.originalYAML.utf8) == sourceData,
              payload.sourceSHA256 == NimboFullConfiguration.digest(sourceData) else {
            throw NimboFullConfigurationError.inspectionFailed
        }
        return Self(originalYAML: payload.originalYAML, sourceSHA256: payload.sourceSHA256)
    }
}
