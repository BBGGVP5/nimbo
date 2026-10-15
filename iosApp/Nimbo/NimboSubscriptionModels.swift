import Foundation

struct NimboSubscriptionServer: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let name: String
    let `protocol`: String
    let host: String
    let port: Int
    let transport: String
    let security: String
    let rawConfiguration: String
    let isNativeXrayJson: Bool

    var connectionLabel: String {
        [self.protocol.uppercased(), transport.uppercased(), security.capitalized]
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }
}

struct NimboSubscriptionProfile: Codable, Equatable, Sendable {
    let parserRevision: Int
    let title: String
    let source: String?
    let format: String
    let servers: [NimboSubscriptionServer]
    let diagnosticCode: String?

}

extension NimboSubscriptionServer {
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        let raw = try values.decode(String.self, forKey: .rawConfiguration)
        guard !raw.isEmpty else {
            throw DecodingError.dataCorruptedError(forKey: .rawConfiguration, in: values, debugDescription: "Missing cached server configuration")
        }
        let native = (try? JSONSerialization.jsonObject(with: Data(raw.utf8))) as? [String: Any]
        self.init(id: try values.decode(String.self, forKey: .id),
                  name: try values.decode(String.self, forKey: .name),
                  protocol: try values.decode(String.self, forKey: .protocol),
                  host: try values.decodeIfPresent(String.self, forKey: .host) ?? "",
                  port: try values.decodeIfPresent(Int.self, forKey: .port) ?? 0,
                  transport: try values.decodeIfPresent(String.self, forKey: .transport) ?? "",
                  security: try values.decodeIfPresent(String.self, forKey: .security) ?? "",
                  rawConfiguration: raw,
                  isNativeXrayJson: try values.decodeIfPresent(Bool.self, forKey: .isNativeXrayJson) ?? (native?["outbounds"] is [Any]))
    }
}

extension NimboSubscriptionProfile {
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        self.init(parserRevision: try values.decodeIfPresent(Int.self, forKey: .parserRevision) ?? 0,
                  title: try values.decodeIfPresent(String.self, forKey: .title) ?? "Подписка",
                  source: try values.decodeIfPresent(String.self, forKey: .source),
                  format: try values.decodeIfPresent(String.self, forKey: .format) ?? "unknown",
                  servers: try values.decode([NimboSubscriptionServer].self, forKey: .servers),
                  diagnosticCode: try values.decodeIfPresent(String.self, forKey: .diagnosticCode))
    }
}
