import Darwin
import Foundation

/// Apple's SimplePing uses SOCK_DGRAM/IPPROTO_ICMP{V6}, without raw-socket
/// privileges. DispatchSource replaces its CFSocket run-loop integration.
enum NimboICMPProbe {
    static func measure(address: String, timeout: TimeInterval) async -> Int {
        guard timeout > 0, !Task.isCancelled else { return -1 }
        let result = NimboPingCompletion<Int>()
        let queue = DispatchQueue(label: "com.nimbo.ping.icmp")
        return await withTaskCancellationHandler(operation: {
            await withCheckedContinuation { continuation in
                queue.async {
                    let ipv6 = address.contains(":")
                    let fd = socket(ipv6 ? AF_INET6 : AF_INET, SOCK_DGRAM, ipv6 ? IPPROTO_ICMPV6 : IPPROTO_ICMP)
                    guard fd >= 0 else {
                        result.install { continuation.resume(returning: $0) }
                        result.finish(-1)
                        return
                    }
                    let source = DispatchSource.makeReadSource(fileDescriptor: fd, queue: queue)
                    let timer = DispatchWorkItem { result.finish(-1) }
                    source.setCancelHandler { Darwin.close(fd) }
                    result.install { value in
                        queue.async {
                            timer.cancel()
                            source.cancel() // Close only after queued read handlers have drained.
                            continuation.resume(returning: value)
                        }
                    }
                    source.resume() // Balanced even if cancelled before installation.
                    guard !result.isFinished else { return }
                    guard fcntl(fd, F_SETFL, O_NONBLOCK) >= 0, connect(fd, address: address, ipv6: ipv6) else {
                        result.finish(-1); return
                    }
                    let identifier = UInt16.random(in: 1...UInt16.max)
                    let sequence = UInt16.random(in: 0...UInt16.max)
                    let nonce = (0..<16).map { _ in UInt8.random(in: 0...255) }
                    let packet = NimboICMPPacket.request(ipv6: ipv6, identifier: identifier, sequence: sequence, nonce: nonce)
                    let started = DispatchTime.now().uptimeNanoseconds
                    source.setEventHandler {
                        guard !result.isFinished else { return }
                        // A connected datagram socket filters the source endpoint.
                        // Bound work per event so a flood cannot starve cancellation.
                        for _ in 0..<16 {
                            var bytes = [UInt8](repeating: 0, count: 2048)
                            let count = Darwin.recv(fd, &bytes, bytes.count, 0)
                            if count < 0 {
                                if errno != EAGAIN && errno != EWOULDBLOCK { result.finish(-1) }
                                return
                            }
                            if NimboICMPPacket.matches(Array(bytes.prefix(count)), ipv6: ipv6, identifier: identifier, sequence: sequence, nonce: nonce) {
                                result.finish(Int((DispatchTime.now().uptimeNanoseconds - started) / 1_000_000))
                                return
                            }
                        }
                    }
                    let sent = packet.withUnsafeBytes { Darwin.send(fd, $0.baseAddress, $0.count, 0) }
                    guard sent == packet.count else { result.finish(-1); return }
                    queue.asyncAfter(deadline: .now() + timeout, execute: timer)
                }
            }
        }, onCancel: { result.finish(-1) })
    }

    private static func connect(_ fd: Int32, address: String, ipv6: Bool) -> Bool {
        if ipv6 {
            var endpoint = sockaddr_in6()
            endpoint.sin6_len = UInt8(MemoryLayout<sockaddr_in6>.size)
            endpoint.sin6_family = sa_family_t(AF_INET6)
            let pieces = address.split(separator: "%", maxSplits: 1).map(String.init)
            guard let host = pieces.first, host.withCString({ inet_pton(AF_INET6, $0, &endpoint.sin6_addr) }) == 1 else { return false }
            if pieces.count == 2 {
                endpoint.sin6_scope_id = UInt32(pieces[1]) ?? pieces[1].withCString { if_nametoindex($0) }
                guard endpoint.sin6_scope_id != 0 else { return false }
            }
            return withUnsafePointer(to: &endpoint) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { Darwin.connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in6>.size)) == 0 }
            }
        }
        var endpoint = sockaddr_in()
        endpoint.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        endpoint.sin_family = sa_family_t(AF_INET)
        guard address.withCString({ inet_pton(AF_INET, $0, &endpoint.sin_addr) }) == 1 else { return false }
        return withUnsafePointer(to: &endpoint) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { Darwin.connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) == 0 }
        }
    }
}
