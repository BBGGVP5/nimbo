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

    static func load(defaults: UserDefaults = .standard) -> Self {
        guard let data = defaults.data(forKey: preferenceKey),
              let value = try? JSONDecoder().decode(Self.self, from: data),
              let valid = try? value.validated() else { return Self() }
        return valid
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
