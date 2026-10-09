import Foundation

// Kept byte-identical in Nimbo and PacketTunnel; source tests enforce parity.
// Each target already includes its own directory. No shared/native build changes.
enum NimboCorePreference: String, CaseIterable {
    case auto, xray, awg, mihomo

    static let defaultsKey = "com.nimbo.connection.vpnCore"
    static let providerKey = "vpnCore"
    static let profileEngineKey = "profileEngine"

    var title: String {
        switch self {
        case .auto: return "Auto"
        case .xray: return "Xray"
        case .awg: return "AWG"
        case .mihomo: return "Mihomo"
        }
    }

    var isAvailable: Bool { true }

    /// Only an absent legacy value defaults to Auto. Corrupt/future IDs fail closed.
    static func decode(_ value: Any?) throws -> Self {
        guard let value else { return .auto }
        guard let raw = value as? String, let preference = Self(rawValue: raw) else {
            throw NimboCoreSelectionError.unknownPreference
        }
        return preference
    }
}

enum NimboCoreProfile: String {
    case xray, awg, mihomo
}

enum NimboCoreAdmission {
    /// Selects the existing runtime path without rewriting or flattening the document.
    @discardableResult
    static func validate(preference: Any?, data: Data, declaredEngine: Any? = nil) throws -> NimboCoreProfile {
        let selected = try NimboCorePreference.decode(preference)
        guard selected.isAvailable else { throw NimboCoreSelectionError.unavailable }
        let profile = try classify(data, declaredEngine: declaredEngine)
        guard selected == .auto || selected.rawValue == profile.rawValue else {
            throw NimboCoreSelectionError.incompatible
        }
        return profile
    }

    static func classify(_ data: Data, declaredEngine: Any? = nil) throws -> NimboCoreProfile {
        guard !data.isEmpty, data.count <= 15 * 1_024 * 1_024,
              let original = String(data: data, encoding: .utf8) else {
            throw NimboCoreSelectionError.unsupportedProfile
        }
        let declared: NimboCoreProfile?
        if let declaredEngine {
            guard let raw = declaredEngine as? String, let engine = NimboCoreProfile(rawValue: raw) else {
                throw NimboCoreSelectionError.unsupportedProfile
            }
            // A native full document is authoritative, even if it contains Xray-like fields.
            if engine == .mihomo { return .mihomo }
            declared = engine
        } else {
            declared = nil
        }
        let text = original.trimmingCharacters(in: .whitespacesAndNewlines.union(CharacterSet(charactersIn: "\u{feff}")))
        let detected: NimboCoreProfile
        if let object = (try? JSONSerialization.jsonObject(with: Data(text.utf8))) as? [String: Any] {
            // Never feed a Mihomo JSON/YAML record or document through share-text conversion.
            if let coreID = object["coreId"] {
                guard let raw = coreID as? String, let engine = NimboCoreProfile(rawValue: raw) else {
                    throw NimboCoreSelectionError.unsupportedProfile
                }
                if engine == .mihomo { return .mihomo }
                guard engine == .xray else { throw NimboCoreSelectionError.unsupportedProfile }
            }
            if ["proxies", "proxy-groups", "proxy-providers", "rule-providers", "originalYAML"].contains(where: { object[$0] != nil }) {
                return .mihomo
            }
            if object["outbounds"] is [Any] {
                // Native Xray documents remain Xray, including a WireGuard outbound.
                detected = .xray
            } else if let links = object["shareLinks"] as? [String], !links.isEmpty {
                try rejectUnsupportedShareProtocols(links.joined(separator: "\n"))
                guard links.allSatisfy({ isXrayShareText($0) }) else { throw NimboCoreSelectionError.unsupportedProfile }
                detected = .xray
            } else {
                throw NimboCoreSelectionError.unsupportedProfile
            }
        } else if try NimboAWGConfiguration.parseIfPresent(text) != nil {
            detected = .awg
        } else {
            try rejectUnsupportedShareProtocols(text)
            guard isXrayShareText(text) else { throw NimboCoreSelectionError.unsupportedProfile }
            detected = .xray
        }
        guard declared == nil || declared == detected else { throw NimboCoreSelectionError.incompatible }
        return detected
    }

    private static func rejectUnsupportedShareProtocols(_ text: String) throws {
        for line in text.split(whereSeparator: \.isNewline) {
            let scheme = line.trimmingCharacters(in: .whitespacesAndNewlines).components(separatedBy: "://").first?.lowercased()
            if scheme == "naive" || scheme == "naive+https" || scheme == "naive+quic" {
                throw NimboCoreSelectionError.naiveUnavailable
            }
            if scheme == "tuic" || scheme == "mieru" { throw NimboCoreSelectionError.tuicRequiresMihomo }
        }
    }

    private static func isXrayShareText(_ text: String) -> Bool {
        let schemes: Set<String> = ["vless", "vmess", "trojan", "ss", "ssr", "hysteria2", "hy2",
                                    "hysteria", "socks", "socks5"]
        let lines = text.split(whereSeparator: \.isNewline)
        return !lines.isEmpty && lines.allSatisfy { line in
            let value = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let separator = value.range(of: "://") else { return false }
            return schemes.contains(String(value[..<separator.lowerBound]).lowercased())
        }
    }
}

enum NimboCoreSelectionError: LocalizedError {
    case unknownPreference, unavailable, incompatible, unsupportedProfile, busy, naiveUnavailable, tuicRequiresMihomo

    var errorDescription: String? {
        switch self {
        case .naiveUnavailable:
            return "NaiveProxy сохранён, но его нативный клиент ещё не встроен в Nimbo для iOS. Выберите другой сервер (IOS_NAIVE_UNAVAILABLE)."
        case .tuicRequiresMihomo:
            return "Для TUIC/Mieru импортируйте профиль Mihomo вашего провайдера и выберите ядро Auto или Mihomo (IOS_TUIC_REQUIRES_MIHOMO)."
        case .unknownPreference:
            return "Неизвестное ядро VPN. Выберите Auto, Xray, AWG или Mihomo в настройках (IOS_CORE_UNKNOWN)."
        case .unavailable:
            return "Связанный пакетный runtime Mihomo недоступен. Полная конфигурация не преобразуется в Xray (IOS_CORE_UNAVAILABLE)."
        case .incompatible:
            return "Выбранное ядро VPN несовместимо с профилем. Выберите Auto или совместимый профиль (IOS_CORE_INCOMPATIBLE)."
        case .unsupportedProfile:
            return "Формат или ядро профиля не поддерживается для VPN на iOS (IOS_CORE_PROFILE_UNSUPPORTED)."
        case .busy:
            return "Дождитесь завершения изменения настроек или подключения VPN (IOS_CORE_BUSY)."
        }
    }
}
