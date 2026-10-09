import Foundation

// Portable executable, no NetworkExtension/SwiftUI/native runtime or network.
// swiftc iosApp/Shared/NimboAWGConfiguration.swift iosApp/Nimbo/NimboCoreSelection.swift \
//   iosApp/Tests/CoreSelectionTests.swift -o /tmp/nimbo-core-tests && /tmp/nimbo-core-tests
@main
enum CoreSelectionTests {
    static let xray = Data(#"{"outbounds":[{"protocol":"vless","settings":{}}]}"#.utf8)
    static let wireguardInXray = Data(#"{"outbounds":[{"protocol":"wireguard","settings":{}}]}"#.utf8)
    static let mihomo = Data("proxies: []\nproxy-groups: []\nrules: [MATCH,DIRECT]\n".utf8)

    static func main() throws {
        try compatibilityMatrix()
        for scheme in ["naive", "naive+https", "naive+quic"] {
            let data = Data("\(scheme)://u:p@example.invalid".utf8)
            for selected in ["auto", "xray"] {
                let result = try NimboCoreAdmission.validate(preference: selected, data: data)
                precondition(result == .naive)
            }
            for selected in ["awg", "mihomo"] {
                try rejected(.incompatible) { try NimboCoreAdmission.validate(preference: selected, data: data) }
            }
        }
        for scheme in ["tuic", "mieru"] {
            try rejected(.tuicRequiresMihomo) {
                try NimboCoreAdmission.validate(preference: "auto", data: Data("\(scheme)://id:p@example.invalid".utf8))
            }
        }
        try unknownIDsFailClosed()
        try fullDocumentIdentity()
        try malformedAndUnsupportedInputs()
        try persistentPreferenceAndSessionSnapshot()
        print("Core preference matrix, strict IDs, full-document identity and persistence passed")
    }

    static func ini(awg: Bool) -> Data {
        let key = Data(repeating: 1, count: 32).base64EncodedString()
        let obfuscation = awg ? "Jc = 4\nS1 = 12\nH1 = 100-200\n" : ""
        return Data(("[Interface]\nPrivateKey = \(key)\nAddress = 10.0.0.2/32\n" + obfuscation +
                     "[Peer]\nPublicKey = \(key)\nEndpoint = example.invalid:51820\nAllowedIPs = 0.0.0.0/0\n").utf8)
    }

    static func compatibilityMatrix() throws {
        let cases: [(Data, String?, NimboCoreProfile)] = [
            (xray, nil, .xray), (wireguardInXray, nil, .xray),
            (Data("vless://test@example.invalid:443".utf8), nil, .xray),
            (Data(#"{"shareLinks":["vless://a@example.invalid:443","trojan://b@example.invalid:443"]}"#.utf8), nil, .xray),
            (ini(awg: true), nil, .awg), (ini(awg: false), nil, .awg),
            (mihomo, "mihomo", .mihomo)
        ]
        for (data, declared, engine) in cases {
            for selected in NimboCorePreference.allCases {
                if selected == .auto || selected.rawValue == engine.rawValue {
                    let result = try NimboCoreAdmission.validate(preference: selected.rawValue, data: data, declaredEngine: declared)
                    precondition(result == engine)
                } else {
                    try rejected(.incompatible) {
                        try NimboCoreAdmission.validate(preference: selected.rawValue, data: data, declaredEngine: declared)
                    }
                }
            }
            if engine != .mihomo {
                let legacy = try NimboCoreAdmission.validate(preference: nil, data: data)
                precondition(legacy == engine, "Absent legacy preference must preserve routing")
            }
        }
    }

    static func unknownIDsFailClosed() throws {
        precondition(NimboCorePreference.allCases.map(\.rawValue) == ["auto", "xray", "awg", "mihomo"])
        for raw: Any in ["future", "", "AUTO", "wireguard", 1, true, ["xray"]] {
            try rejected(.unknownPreference) { try NimboCoreAdmission.validate(preference: raw, data: xray) }
        }
        for engine: Any in ["future", "auto", "", 1, ["xray"]] {
            try rejected(.unsupportedProfile) {
                try NimboCoreAdmission.validate(preference: "auto", data: xray, declaredEngine: engine)
            }
        }
        try rejected(.unsupportedProfile) {
            try NimboCoreAdmission.validate(preference: "auto", data: Data(#"{"coreId":"future","outbounds":[]}"#.utf8))
        }
    }

    static func fullDocumentIdentity() throws {
        // Even a native Xray WireGuard outbound stays an Xray full document.
        let engine = try NimboCoreAdmission.validate(preference: "xray", data: wireguardInXray)
        precondition(engine == .xray)
        try rejected(.incompatible) {
            try NimboCoreAdmission.validate(preference: "awg", data: wireguardInXray)
        }
        for data in [Data(#"{"coreId":"mihomo","outbounds":[]}"#.utf8),
                     Data(#"{"coreId":"mihomo","shareLinks":["naive://u:p@host"]}"#.utf8),
                     Data(#"{"originalYAML":"proxies: []","shareLinks":["naive://u:p@host"]}"#.utf8),
                     Data(#"{"proxies":[],"outbounds":[]}"#.utf8),
                     Data(#"{"originalYAML":"proxies: []","outbounds":[]}"#.utf8)] {
            try rejected(.incompatible) { try NimboCoreAdmission.validate(preference: "xray", data: data) }
        }
        try rejected(.incompatible) {
            try NimboCoreAdmission.validate(preference: "xray", data: xray, declaredEngine: "mihomo")
        }
        try rejected(.incompatible) {
            try NimboCoreAdmission.validate(preference: "auto", data: xray, declaredEngine: "awg")
        }
        try rejected(.incompatible) {
            try NimboCoreAdmission.validate(preference: "auto", data: ini(awg: false), declaredEngine: "xray")
        }
        // Unknown/YAML payloads are rejected rather than flattened into Xray nodes.
        try rejected(.unsupportedProfile) { try NimboCoreAdmission.validate(preference: "auto", data: mihomo) }
    }

    static func malformedAndUnsupportedInputs() throws {
        for data in [Data(), Data([0xff]), Data("unknown://host".utf8),
                     Data("wireguard://unsupported-link".utf8), Data("awg://unsupported-link".utf8),
                     Data(#"{"shareLinks":["vless://a","wg://b"]}"#.utf8), Data("{}".utf8)] {
            try rejected(.unsupportedProfile) { try NimboCoreAdmission.validate(preference: "auto", data: data) }
        }
        do {
            try NimboCoreAdmission.validate(preference: "auto", data: Data("[Interface]\nPrivateKey = bad".utf8))
            preconditionFailure("Malformed AWG reached the runtime")
        } catch NimboAWGError.invalidConfiguration { }
        let exact = Data("\u{feff}\r\n{\"outbounds\":[{\"protocol\":\"wireguard\"}]} \r\n".utf8)
        let before = exact
        _ = try NimboCoreAdmission.validate(preference: "auto", data: exact)
        precondition(exact == before, "Admission must not rewrite source bytes")
    }

    static func persistentPreferenceAndSessionSnapshot() throws {
        let name = "Nimbo.CoreSelectionTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let legacy = try NimboCorePreference.decode(defaults.object(forKey: NimboCorePreference.defaultsKey))
        precondition(legacy == .auto)
        defaults.set("xray", forKey: NimboCorePreference.defaultsKey)
        let session = try NimboCoreAdmission.validate(preference: defaults.object(forKey: NimboCorePreference.defaultsKey), data: xray)
        defaults.set("awg", forKey: NimboCorePreference.defaultsKey)
        let reopened = UserDefaults(suiteName: name)!
        precondition(reopened.string(forKey: NimboCorePreference.defaultsKey) == "awg")
        precondition(session == .xray, "A saved preference change cannot mutate the current session")
        try rejected(.incompatible) {
            try NimboCoreAdmission.validate(preference: reopened.object(forKey: NimboCorePreference.defaultsKey), data: xray)
        }
    }

    static func rejected(_ expected: NimboCoreSelectionError, _ action: () throws -> NimboCoreProfile) throws {
        do {
            _ = try action()
            preconditionFailure("Expected rejection")
        } catch let error as NimboCoreSelectionError {
            precondition(error.errorDescription == expected.errorDescription)
        }
    }
}
