import Darwin
import Foundation

/// Owns one established socket. Native-socket CF streams do not own/close it;
/// the HTTP probe keeps this owner alive until both streams are closed.
final class NimboSOCKSTunnel: @unchecked Sendable {
    let descriptor: Int32
    private init(_ descriptor: Int32) { self.descriptor = descriptor }
    deinit { Darwin.close(descriptor) }

    static func connect(url: URL, socks: NimboPingSOCKS, deadline: TimeInterval,
                        cancellation: NimboPingCompletion<Int>) async -> NimboSOCKSTunnel? {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .utility).async {
                continuation.resume(returning: establish(url: url, socks: socks, deadline: deadline, cancellation: cancellation))
            }
        }
    }

    private static func establish(url: URL, socks: NimboPingSOCKS, deadline: TimeInterval,
                                  cancellation: NimboPingCompletion<Int>) -> NimboSOCKSTunnel? {
        guard !cancellation.isFinished, (1...65535).contains(socks.port),
              let rawHost = url.host else { return nil }
        let host = rawHost.hasPrefix("[") ? String(rawHost.dropFirst().dropLast()) : rawHost
        let user = Array(socks.username.utf8), password = Array(socks.password.utf8)
        guard (1...255).contains(user.count), (1...255).contains(password.count),
              let target = targetAddress(host) else { return nil }
        let targetPort = url.port ?? (url.scheme?.lowercased() == "https" ? 443 : 80)
        guard (1...65535).contains(targetPort) else { return nil }
        let fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
        guard fd >= 0 else { return nil }
        let owner = NimboSOCKSTunnel(fd) // Every unsuccessful return closes the socket.
        let flags = fcntl(fd, F_GETFL)
        var enabled: Int32 = 1
        guard flags >= 0, fcntl(fd, F_SETFL, flags | O_NONBLOCK) == 0,
              setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &enabled, socklen_t(MemoryLayout<Int32>.size)) == 0 else { return nil }
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_port = UInt16(socks.port).bigEndian
        address.sin_addr.s_addr = UInt32(0x7f000001).bigEndian
        let status = withUnsafePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        guard status == 0 || errno == EINPROGRESS else { return nil }
        let io = IO(fd: fd, deadline: deadline, cancellation: cancellation)
        guard io.ready(Int16(POLLOUT)) else { return nil }
        var error: Int32 = 0
        var size = socklen_t(MemoryLayout<Int32>.size)
        guard getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &size) == 0, error == 0 else { return nil }

        // Offer ONLY RFC1929 authentication. A no-auth downgrade is not accepted.
        guard io.write([5, 1, 2]), io.read(2) == [5, 2],
              io.write([1, UInt8(user.count)] + user + [UInt8(password.count)] + password),
              io.read(2) == [1, 0] else { return nil }
        let request: [UInt8] = [5, 1, 0] + target + [UInt8(targetPort >> 8), UInt8(targetPort & 255)]
        guard io.write(request), let reply = io.read(4),
              reply[0] == 5, reply[1] == 0, reply[2] == 0 else { return nil }
        // BND.ADDR belongs to the proxy, not the target. Validate its framing,
        // consume exactly it+port, and leave subsequent TLS/HTTP bytes untouched.
        let length: Int
        switch reply[3] {
        case 1: length = 4
        case 4: length = 16
        case 3:
            guard let count = io.read(1)?.first, count > 0 else { return nil }
            length = Int(count)
        default: return nil
        }
        guard io.read(length + 2) != nil, io.valid else { return nil }
        return owner
    }

    private static func targetAddress(_ host: String) -> [UInt8]? {
        var v4 = in_addr(), v6 = in6_addr()
        if host.withCString({ inet_pton(AF_INET, $0, &v4) }) == 1 {
            return [1] + withUnsafeBytes(of: v4) { Array($0) }
        }
        if host.withCString({ inet_pton(AF_INET6, $0, &v6) }) == 1 {
            return [4] + withUnsafeBytes(of: v6) { Array($0) }
        }
        let name = Array(host.utf8)
        guard (1...255).contains(name.count) else { return nil }
        // No local DNS lookup: send the original name through the selected route.
        return [3, UInt8(name.count)] + name
    }

    private struct IO {
        let fd: Int32
        let deadline: TimeInterval
        let cancellation: NimboPingCompletion<Int>
        var valid: Bool { !cancellation.isFinished && ProcessInfo.processInfo.systemUptime < deadline }
        func ready(_ events: Int16) -> Bool {
            while valid {
                let remaining = deadline - ProcessInfo.processInfo.systemUptime
                var descriptor = pollfd(fd: fd, events: events, revents: 0)
                let status = Darwin.poll(&descriptor, 1, Int32(min(50, max(1, remaining * 1000))))
                if status < 0 { if errno == EINTR { continue }; return false }
                if status > 0 {
                    if descriptor.revents & events != 0 { return valid }
                    if descriptor.revents & Int16(POLLERR | POLLHUP | POLLNVAL) != 0 { return false }
                }
            }
            return false
        }
        func write(_ bytes: [UInt8]) -> Bool {
            var offset = 0
            while offset < bytes.count {
                guard ready(Int16(POLLOUT)) else { return false }
                let count = bytes.withUnsafeBytes { Darwin.send(fd, $0.baseAddress!.advanced(by: offset), bytes.count - offset, 0) }
                if count < 0 { if errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK { continue }; return false }
                guard count > 0 else { return false }
                offset += count
            }
            return true
        }
        func read(_ count: Int) -> [UInt8]? {
            var bytes = [UInt8](repeating: 0, count: count)
            var offset = 0
            while offset < count {
                guard ready(Int16(POLLIN)) else { return nil }
                let size = bytes.withUnsafeMutableBytes { Darwin.recv(fd, $0.baseAddress!.advanced(by: offset), count - offset, 0) }
                if size < 0 { if errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK { continue }; return nil }
                guard size > 0 else { return nil }
                offset += size
            }
            return bytes
        }
    }
}
