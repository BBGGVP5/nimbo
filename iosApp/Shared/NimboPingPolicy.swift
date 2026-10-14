import Foundation

/// New installations use Nimbo. Explicit legacy selections retain their meanings.
enum NimboPingProtocol: String, CaseIterable {
    case nimbo, tcp, httpGet = "http_get", httpHead = "http_head", icmp

    init(stored: String?) {
        self = stored == "http" ? .httpHead : (stored.flatMap(Self.init(rawValue:)) ?? .nimbo)
    }

    var httpMethod: String? {
        switch self {
        case .nimbo, .httpGet: return "GET"
        case .httpHead: return "HEAD"
        default: return nil
        }
    }
}

enum NimboPingPolicy {
    static let defaultURL = "https://www.gstatic.com/generate_204"
    static let maximumHeaderBytes = 16 * 1024

    /// Presentation only, matching shared NimboPingPresentation. Measurements,
    /// cache values, route selection and bridge payloads must remain raw.
    static func displayMilliseconds(raw: Int, mode: NimboPingProtocol) -> String {
        guard raw >= 0 else { return "—" }
        if mode == .nimbo { return "≈\(Int((Double(raw) / 3.3).rounded()))" }
        return String(raw)
    }

    /// Raw balancer ordering: zero is a measured success, not an unavailable row.
    static func selectionRank(_ raw: Int?) -> Int {
        guard let raw else { return 100_000 }
        return raw >= 0 ? raw : 200_000
    }

    static func timeout(milliseconds: Int) -> TimeInterval {
        milliseconds > 0 ? min(10, max(1, Double(milliseconds) / 1000)) : 3
    }

    /// Negative-only optimisation, not a ping. Do not guess a QUIC transport,
    /// chained route, alternate XHTTP download endpoint or opaque share format.
    /// The endpoint must match the immutable configuration, not just its label.
    static func canPreflight(configuration: String, host: String, port: Int) -> Bool {
        guard !host.isEmpty, (1...65535).contains(port) else { return false }
        let tcpNetworks = ["tcp", "raw", "ws", "websocket", "grpc", "httpupgrade", "http", "h2"]
        let source = configuration.trimmingCharacters(in: .whitespacesAndNewlines)
        if source.hasPrefix("{") {
            guard let root = (try? JSONSerialization.jsonObject(with: Data(source.utf8))) as? [String: Any],
                  let outbounds = root["outbounds"] as? [[String: Any]] else { return false }
            let proxies = outbounds.filter { !["freedom", "blackhole", "dns"].contains($0["protocol"] as? String ?? "") }
            guard proxies.count == 1, let proxy = proxies.first,
                  ["vless", "vmess", "trojan", "shadowsocks", "socks", "http"].contains(proxy["protocol"] as? String ?? ""),
                  proxy["proxySettings"] == nil, proxy["sendThrough"] == nil else { return false }
            let stream = proxy["streamSettings"] as? [String: Any] ?? [:]
            guard tcpNetworks.contains(stream["network"] as? String ?? "tcp"),
                  stream["sockopt"] == nil else { return false }
            let settings = proxy["settings"] as? [String: Any] ?? [:]
            let peers = (settings["vnext"] ?? settings["servers"]) as? [[String: Any]] ?? []
            // Multi-address retries cannot be rejected from the first address.
            return peers.count == 1 && peers[0]["address"] as? String == host && peers[0]["port"] as? Int == port
        }
        guard !source.contains("\n"), !source.contains("\r"),
              let link = URLComponents(string: source),
              ["vless", "trojan", "ss", "socks", "socks5"].contains(link.scheme?.lowercased() ?? ""),
              link.host == host, link.port == port else { return false }
        let query = link.queryItems ?? []
        guard !query.contains(where: { ["plugin", "dialerProxy", "chain", "downloadSettings"].contains($0.name) }),
              query.filter({ $0.name == "type" }).count <= 1 else { return false }
        return tcpNetworks.contains(query.first(where: { $0.name == "type" })?.value ?? "tcp")
    }

    static func checkedURL(_ raw: String) -> URL? {
        guard raw.utf8.count <= 4096,
              !raw.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) || CharacterSet.whitespacesAndNewlines.contains($0) }),
              let parts = URLComponents(string: raw),
              ["http", "https"].contains(parts.scheme?.lowercased() ?? ""),
              let host = parts.host, !host.isEmpty,
              !host.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) || CharacterSet.whitespacesAndNewlines.contains($0) }),
              parts.user == nil, parts.password == nil, parts.fragment == nil,
              parts.port == nil || (1...65535).contains(parts.port!),
              let url = parts.url else { return nil }
        return url
    }

    static func request(url: URL, method: String) -> Data? {
        guard ["GET", "HEAD"].contains(method), checkedURL(url.absoluteString) != nil,
              let parts = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let host = parts.host else { return nil }
        let bracketed = host.contains(":") && !host.hasPrefix("[") ? "[\(host)]" : host
        let authority = bracketed + (parts.port.map { ":\($0)" } ?? "")
        let path = parts.percentEncodedPath.isEmpty ? "/" : parts.percentEncodedPath
        let query = parts.percentEncodedQuery.map { "?\($0)" } ?? ""
        return Data("\(method) \(path)\(query) HTTP/1.1\r\nHost: \(authority)\r\nConnection: close\r\nCache-Control: no-cache, no-store\r\nAccept: */*\r\n\r\n".utf8)
    }

    /// Parse complete HTTP headers. The probe accepts only 2xx as success;
    /// redirects/errors/101 fail, and other interim 1xx headers are skipped.
    /// No redirects are followed and body download is not timed.
    static func responseStatus(_ header: Data) -> Int? {
        guard header.count <= maximumHeaderBytes,
              let text = String(data: header, encoding: .isoLatin1),
              text.hasSuffix("\r\n\r\n"), let line = text.components(separatedBy: "\r\n").first else { return nil }
        let fields = line.split(separator: " ", omittingEmptySubsequences: false)
        guard fields.count >= 2, ["HTTP/1.0", "HTTP/1.1"].contains(fields[0]),
              fields[1].count == 3, let status = Int(fields[1]), (100...599).contains(status) else { return nil }
        return status
    }
}

/// Internet checksum and exact echo correlation, shared by native fixtures and Darwin I/O.
enum NimboICMPPacket {
    static func checksum(_ bytes: [UInt8]) -> UInt16 {
        var sum: UInt32 = 0
        for index in stride(from: 0, to: bytes.count, by: 2) {
            sum += UInt32(bytes[index]) << 8
            if index + 1 < bytes.count { sum += UInt32(bytes[index + 1]) }
        }
        while sum >> 16 != 0 { sum = (sum & 0xffff) + (sum >> 16) }
        return ~UInt16(sum)
    }

    static func request(ipv6: Bool, identifier: UInt16, sequence: UInt16, nonce: [UInt8]) -> [UInt8] {
        var bytes: [UInt8] = [ipv6 ? 128 : 8, 0, 0, 0,
                             UInt8(identifier >> 8), UInt8(identifier & 255),
                             UInt8(sequence >> 8), UInt8(sequence & 255)] + nonce
        if !ipv6 {
            let value = checksum(bytes)
            bytes[2] = UInt8(value >> 8)
            bytes[3] = UInt8(value & 255)
        }
        return bytes
    }

    static func matches(_ packet: [UInt8], ipv6: Bool, identifier: UInt16, sequence: UInt16, nonce: [UInt8]) -> Bool {
        var bytes = packet
        if !ipv6 {
            // Darwin ICMPv4 datagrams include an IPv4 header (SimplePing).
            guard bytes.count >= 20, bytes[0] >> 4 == 4, bytes[9] == 1 else { return false }
            let length = Int(bytes[0] & 15) * 4
            guard length >= 20, length + 8 <= bytes.count else { return false }
            bytes.removeFirst(length)
            guard checksum(bytes) == 0 else { return false }
        }
        // Darwin validates the IPv6 pseudo-header checksum in the kernel.
        guard bytes.count == 8 + nonce.count, bytes[0] == (ipv6 ? 129 : 0), bytes[1] == 0,
              bytes[4] == UInt8(identifier >> 8), bytes[5] == UInt8(identifier & 255),
              bytes[6] == UInt8(sequence >> 8), bytes[7] == UInt8(sequence & 255) else { return false }
        return Array(bytes.dropFirst(8)) == nonce
    }
}
