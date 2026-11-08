import Foundation

struct NimboOnDemandSettings: Codable, Equatable {
    static let preferenceKey = "com.nimbo.vpn.onDemand"
    var enabled = false
    var wifi = true
    var cellular = true
    var trustedSSIDs: [String] = []

    func validated() throws -> Self {
        var value = self
        var seen = Set<String>()
        value.trustedSSIDs = try trustedSSIDs.compactMap { raw in
            let ssid = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if ssid.isEmpty { return nil }
            guard ssid.utf8.count <= 32, !ssid.unicodeScalars.contains(where: {
                CharacterSet.controlCharacters.contains($0)
            }) else { throw ValidationError.invalidSSID }
            return seen.insert(ssid).inserted ? ssid : nil
        }
        guard value.trustedSSIDs.count <= 32 else { throw ValidationError.tooManySSIDs }
        guard !enabled || wifi || cellular else { throw ValidationError.noTransport }
        return value
    }

    enum ValidationError: LocalizedError {
        case invalidSSID, tooManySSIDs, noTransport
        var errorDescription: String? {
            switch self {
            case .invalidSSID: return "Имя Wi-Fi должно быть не длиннее 32 байт и без управляющих символов."
            case .tooManySSIDs: return "Можно добавить до 32 доверенных Wi-Fi сетей."
            case .noTransport: return "Выберите Wi-Fi или мобильную сеть."
            }
        }
    }

    static func decoded(_ data: Data?) -> Self? {
        guard let data, let value = try? JSONDecoder().decode(Self.self, from: data) else { return nil }
        return try? value.validated()
    }

    // The persisted system profile is authoritative after a partial save or
    // app-data reset. This recovers intent only; it never re-arms paused rules.
    static func restored(local: Data?, staged: Data?) -> Self {
        decoded(staged) ?? decoded(local) ?? Self()
    }

    static func load(defaults: UserDefaults = .standard) -> Self {
        decoded(defaults.data(forKey: preferenceKey)) ?? Self()
    }

    enum Rule: Equatable {
        case disconnectTrustedWiFi([String]), connectWiFi, connectCellular, ignore
    }
    var rulePlan: [Rule] {
        guard enabled && (wifi || cellular) else { return [] }
        var result: [Rule] = []
        if !trustedSSIDs.isEmpty { result.append(.disconnectTrustedWiFi(trustedSSIDs)) }
        if wifi { result.append(.connectWiFi) }
        if cellular { result.append(.connectCellular) }
        result.append(.ignore)
        return result
    }
}
