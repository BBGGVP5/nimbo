import Foundation

/// Lightweight import metadata only. The Go runtime validates the full AWG 3.1
/// grammar. Keep the original INI: reconstructing it would lose I1-I5/H1-H4.
struct NimboAWGConfiguration {
    static let version = "v3.1.20260828"
    let rawText: String
    let host: String
    let port: Int
    let dns: [String]
    let mtu: Int

    static func parseIfPresent(_ text: String) throws -> NimboAWGConfiguration? {
        let text = text.trimmingCharacters(in: .whitespacesAndNewlines.union(CharacterSet(charactersIn: "\u{feff}")))
        let lines = text.components(separatedBy: .newlines).map {
            $0.trimmingCharacters(in: .whitespaces)
        }.filter { !$0.isEmpty && !$0.hasPrefix(";") && !$0.hasPrefix("#") }
        guard lines.contains(where: { $0.lowercased() == "[interface]" }) else { return nil }
        guard text.utf8.count <= 128 * 1024 else { throw NimboAWGError.invalidConfiguration }
        var section = ""
        var interface: [String: String] = [:]
        var peer: [String: String] = [:]
        var sections = Set<String>()
        for line in lines {
            if line.hasPrefix("[") {
                section = line.lowercased()
                guard ["[interface]", "[peer]"].contains(section), sections.insert(section).inserted else {
                    throw NimboAWGError.invalidConfiguration
                }
                continue
            }
            let parts = line.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard parts.count == 2, !section.isEmpty else { throw NimboAWGError.invalidConfiguration }
            let key = parts[0].trimmingCharacters(in: .whitespaces).lowercased()
            let value = parts[1].trimmingCharacters(in: .whitespaces)
            guard !key.isEmpty, !value.isEmpty, !value.contains("\0") else { throw NimboAWGError.invalidConfiguration }
            if section == "[interface]" {
                guard interface[key] == nil else { throw NimboAWGError.invalidConfiguration }
                interface[key] = value
            } else {
                guard peer[key] == nil else { throw NimboAWGError.invalidConfiguration }
                peer[key] = value
            }
        }
        func validKey(_ value: String?) -> Bool {
            guard let value else { return false }
            if let data = Data(base64Encoded: value), data.count == 32 { return true }
            return value.count == 64 && value.allSatisfy(\.isHexDigit)
        }
        guard validKey(interface["privatekey"]), validKey(peer["publickey"]),
              !(interface["address"] ?? "").isEmpty, !(peer["allowedips"] ?? "").isEmpty,
              let endpoint = peer["endpoint"],
              let colon = endpoint.lastIndex(of: ":"),
              let port = Int(endpoint[endpoint.index(after: colon)...]), (1...65535).contains(port) else {
            throw NimboAWGError.invalidConfiguration
        }
        if let key = peer["presharedkey"], !validKey(key) { throw NimboAWGError.invalidConfiguration }
        let hostPart = String(endpoint[..<colon])
        let host: String
        if hostPart.hasPrefix("["), hostPart.hasSuffix("]") {
            host = String(hostPart.dropFirst().dropLast())
        } else {
            guard !hostPart.contains(":") else { throw NimboAWGError.invalidConfiguration }
            host = hostPart
        }
        guard !host.isEmpty, !host.contains(where: { $0.isWhitespace || $0 == "/" }) else {
            throw NimboAWGError.invalidConfiguration
        }
        let mtu = interface["mtu"].flatMap(Int.init) ?? 1280
        guard (1280...9000).contains(mtu), interface["mtu"] == nil || interface["mtu"].flatMap(Int.init) != nil else {
            throw NimboAWGError.invalidConfiguration
        }
        let dns = (interface["dns"] ?? "1.1.1.1").split(separator: ",").map {
            $0.trimmingCharacters(in: .whitespaces)
        }.filter { !$0.isEmpty }
        return Self(rawText: text, host: host, port: port, dns: dns, mtu: mtu)
    }
}

enum NimboAWGError: LocalizedError {
    case invalidConfiguration
    case runtimeFailure

    var errorDescription: String? {
        switch self {
        case .invalidConfiguration:
            "Некорректная конфигурация AWG/WireGuard с одним peer (IOS_AWG_CONFIG_INVALID)."
        case .runtimeFailure:
            "Не удалось запустить или восстановить AmneziaWG (IOS_AWG_RUNTIME_FAILED)."
        }
    }
}
