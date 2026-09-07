import Darwin
import Foundation
import NetworkExtension

enum PacketTunnelNetwork {
    static let mtu = 1400

    struct DescriptorInfo {
        let descriptor: Int32
        let interfaceName: String
    }

    static func settings(options: NimboRoutingOptions = .default) -> NEPacketTunnelNetworkSettings {
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")

        let ipv4 = NEIPv4Settings(
            addresses: ["198.18.0.1"],
            subnetMasks: ["255.255.255.252"]
        )
        ipv4.includedRoutes = [NEIPv4Route.default()]
        if options.bypassLocalNetworks {
            // Принтеры, NAS и роутер должны оставаться доступны напрямую.
            ipv4.excludedRoutes = [
                NEIPv4Route(destinationAddress: "10.0.0.0", subnetMask: "255.0.0.0"),
                NEIPv4Route(destinationAddress: "172.16.0.0", subnetMask: "255.240.0.0"),
                NEIPv4Route(destinationAddress: "192.168.0.0", subnetMask: "255.255.0.0"),
                NEIPv4Route(destinationAddress: "169.254.0.0", subnetMask: "255.255.0.0")
            ]
        }
        settings.ipv4Settings = ipv4

        let ipv6 = NEIPv6Settings(
            addresses: ["fd00:1::1"],
            networkPrefixLengths: [64]
        )
        ipv6.includedRoutes = [NEIPv6Route.default()]
        settings.ipv6Settings = ipv6

        // «Системный» набор означает отсутствие своих DNS: адреса выдаёт сеть.
        if let servers = options.dnsServers {
            let dns = NEDNSSettings(servers: servers)
            dns.matchDomains = [""]
            settings.dnsSettings = dns
        }
        settings.mtu = NSNumber(value: mtu)
        return settings
    }

    /// NetworkExtension does not expose its utun descriptor directly. Xray's
    /// official iOS integration scans the provider process descriptors and
    /// identifies utun sockets through SYSPROTO_CONTROL/UTUN_OPT_IFNAME.
    static func utunDescriptorInfo() -> DescriptorInfo? {
        let prefix = Array("utun".utf8CString.dropLast())

        for descriptor in Int32(0) ... Int32(1024) {
            var name = [CChar](repeating: 0, count: Int(IFNAMSIZ))
            var length = socklen_t(name.count)
            let result = name.withUnsafeMutableBytes { buffer in
                getsockopt(descriptor, 2, 2, buffer.baseAddress, &length)
            }
            guard result == 0, name.starts(with: prefix) else { continue }
            return DescriptorInfo(
                descriptor: descriptor,
                interfaceName: String(cString: name)
            )
        }
        return nil
    }
}
