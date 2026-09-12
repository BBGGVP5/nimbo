import Foundation

@main
enum AWGConfigurationTests {
    static func main() throws {
        let key = Data(repeating: 1, count: 32).base64EncodedString()
        let raw = """
        # AWG 3.1 import fixture, no real credentials
        [Interface]
        PrivateKey = \(key)
        Address = 10.11.0.2/32, fd00::2/128
        DNS = 10.11.0.1, fd00::1
        MTU = 1380
        Jc = 4
        Jmin = 40
        Jmax = 70
        S1 = 12
        S2 = 24
        S3 = 36
        S4 = 48
        H1 = 100-200
        H2 = 300-400
        H3 = 500-600
        H4 = 700-800
        I1 = <b 0x0102>
        I2 = <r 12>
        I3 = <t>
        I4 = <rc 2>
        I5 = <b 0xabcd>
        [Peer]
        PublicKey = \(key)
        PresharedKey = \(key)
        Endpoint = [2001:db8::7]:51820
        AllowedIPs = 0.0.0.0/0, ::/0
        PersistentKeepalive = 25
        """
        let parsed = try require(raw)
        precondition(parsed.rawText == raw, "AWG extension fields must survive staging verbatim")
        precondition(parsed.host == "2001:db8::7" && parsed.port == 51820)
        precondition(parsed.dns == ["10.11.0.1", "fd00::1"] && parsed.mtu == 1380)
        let defaults = raw.replacingOccurrences(of: "DNS = 10.11.0.1, fd00::1\n", with: "")
            .replacingOccurrences(of: "MTU = 1380\n", with: "")
        let defaultConfig = try require(defaults)
        precondition(defaultConfig.dns == ["1.1.1.1"] && defaultConfig.mtu == 1280)
        let shareLink = try NimboAWGConfiguration.parseIfPresent("vless://example")
        let xrayJSON = try NimboAWGConfiguration.parseIfPresent("{\"outbounds\":[]}")
        precondition(shareLink == nil && xrayJSON == nil)
        try rejected(raw + "\n[Peer]\nPublicKey = \(key)")
        try rejected(raw.replacingOccurrences(of: "PrivateKey = \(key)", with: "PrivateKey = bad"))
        try rejected(raw.replacingOccurrences(of: "Endpoint = [2001:db8::7]:51820", with: "Endpoint = host:70000"))
        try rejected(raw.replacingOccurrences(of: "AllowedIPs = 0.0.0.0/0, ::/0", with: ""))
        try rejected(raw.replacingOccurrences(of: "MTU = 1380", with: "MTU = broken"))
        try rejected(raw + String(repeating: " ", count: 128 * 1024) + "\n# end")
        print("AWG import preservation, defaults and rejection tests passed")
    }

    static func require(_ text: String) throws -> NimboAWGConfiguration {
        guard let result = try NimboAWGConfiguration.parseIfPresent(text) else {
            preconditionFailure("Expected INI recognition")
        }
        return result
    }

    static func rejected(_ text: String) throws {
        do {
            _ = try NimboAWGConfiguration.parseIfPresent(text)
            preconditionFailure("Invalid INI was accepted")
        } catch NimboAWGError.invalidConfiguration {
            // Fixed error text only; never include the input configuration.
        }
    }
}
