import Darwin
import Foundation

/// Credentials never cross IPC or persist. This entry belongs to one core generation.
struct NimboPingRoute {
    static let inboundTag = "nimbo-ping-private"
    let socks: NimboPingSOCKS

    static func make() -> NimboPingRoute? {
        let fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
        guard fd >= 0 else { return nil }
        defer { Darwin.close(fd) }
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_addr.s_addr = inet_addr("127.0.0.1")
        let bound = withUnsafePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { Darwin.bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
        }
        guard bound == 0 else { return nil }
        var size = socklen_t(MemoryLayout<sockaddr_in>.size)
        let read = withUnsafeMutablePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { getsockname(fd, $0, &size) }
        }
        guard read == 0 else { return nil }
        return NimboPingRoute(socks: NimboPingSOCKS(port: Int(UInt16(bigEndian: address.sin_port)),
                                                   username: UUID().uuidString, password: UUID().uuidString))
    }

    /// Conservative proof: no balance selection, duplicate tags, direct protocol,
    /// proxy chains or transport dialer overrides whose final route is unknown.
    static func verifiedTag(outbounds: [[String: Any]], balanced: Bool) -> String? {
        guard !balanced, let first = outbounds.first,
              let proto = first["protocol"] as? String,
              ["vless", "vmess", "trojan", "shadowsocks", "socks", "http", "wireguard", "hysteria"].contains(proto),
              let tag = first["tag"] as? String, !tag.isEmpty,
              outbounds.filter({ $0["tag"] as? String == tag }).count == 1,
              first["proxySettings"] == nil else { return nil }
        let stream = first["streamSettings"] as? [String: Any] ?? [:]
        let sockopt = stream["sockopt"] as? [String: Any] ?? [:]
        guard sockopt["dialerProxy"] == nil else { return nil }
        // XHTTP extra streams may have independent dialers; leave these unavailable.
        guard stream["xhttpSettings"] == nil, stream["splithttpSettings"] == nil else { return nil }
        return tag
    }

    var inbound: [String: Any] {
        ["tag": Self.inboundTag, "listen": "127.0.0.1", "port": socks.port, "protocol": "socks",
         "settings": ["auth": "password", "accounts": [["user": socks.username, "pass": socks.password]], "udp": false],
         "sniffing": ["enabled": false]]
    }

    static func rule(proxyTag: String) -> [String: Any] {
        ["type": "field", "inboundTag": [inboundTag], "network": "tcp", "outboundTag": proxyTag]
    }
}
