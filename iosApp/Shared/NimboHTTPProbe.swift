import Foundation
import CFNetwork

struct NimboPingSOCKS {
    let port: Int
    let username: String
    let password: String
}

/// A single HTTP/1.1 request with a monotonic deadline, no body buffering,
/// no redirects, no connection pool and normal certificate/hostname validation.
/// All stream operations are serialized on the main run loop; none block it.
final class NimboHTTPProbe: NSObject, StreamDelegate {
    private let input: InputStream
    private let output: OutputStream
    private let request: [UInt8]
    private let completion: NimboPingCompletion<Int>
    private var tunnel: NimboSOCKSTunnel?
    private let started = DispatchTime.now().uptimeNanoseconds
    private var written = 0
    private var header = Data()
    private var receivedBytes = 0
    private var done = false
    private var deadline: DispatchWorkItem?

    private init?(url: URL, method: String, tunnel: NimboSOCKSTunnel?, completion: NimboPingCompletion<Int>) {
        guard let bytes = NimboPingPolicy.request(url: url, method: method), let host = url.host else { return nil }
        var readReference: Unmanaged<CFReadStream>?
        var writeReference: Unmanaged<CFWriteStream>?
        let bare = host.hasPrefix("[") ? String(host.dropFirst().dropLast()) : host
        let port = UInt32(url.port ?? (url.scheme?.lowercased() == "https" ? 443 : 80))
        if let tunnel {
            CFStreamCreatePairWithSocket(kCFAllocatorDefault, tunnel.descriptor, &readReference, &writeReference)
        } else {
            CFStreamCreatePairWithSocketToHost(kCFAllocatorDefault, bare as CFString, port, &readReference, &writeReference)
        }
        // Consume both retained references, even if creating one side failed.
        let read = readReference?.takeRetainedValue()
        let write = writeReference?.takeRetainedValue()
        guard let read, let write else { return nil }
        self.input = read as InputStream
        self.output = write as OutputStream
        self.request = Array(bytes)
        self.completion = completion
        self.tunnel = tunnel
        super.init()
        if url.scheme?.lowercased() == "https" {
            // Never install a trust override or disable certificate-chain validation.
            // Native-socket streams have no target hostname: explicitly supply the
            // original URL peer name, not the loopback proxy's name, for TLS/SNI.
            let tls: [String: Any] = [kCFStreamSSLPeerName as String: bare,
                                     kCFStreamSSLLevel as String: kCFStreamSocketSecurityLevelNegotiatedSSL as String]
            guard CFReadStreamSetProperty(read, CFStreamPropertyKey(rawValue: kCFStreamPropertySSLSettings), tls as CFDictionary) else {
                Self.trace("CFReadStream TLS configuration rejected before open")
                return nil
            }
            Self.trace("CFReadStream TLS configuration accepted")
        }
    }

    private static func trace(_ message: String) {
        #if NIMBO_PING_TESTS
        // Stage only: never print URLs, credentials or stream error descriptions.
        FileHandle.standardError.write(Data("NimboHTTPProbe: \(message)\n".utf8))
        #endif
    }

    static func measure(url: URL, method: String, timeout: TimeInterval, socks: NimboPingSOCKS? = nil) async -> Int {
        guard timeout.isFinite, timeout > 0, !Task.isCancelled,
              NimboPingPolicy.request(url: url, method: method) != nil else { return -1 }
        let result = NimboPingCompletion<Int>()
        let deadline = ProcessInfo.processInfo.systemUptime + timeout
        return await withTaskCancellationHandler(operation: {
            let tunnel: NimboSOCKSTunnel?
            if let socks {
                guard let connected = await NimboSOCKSTunnel.connect(url: url, socks: socks, deadline: deadline, cancellation: result) else { return -1 }
                tunnel = connected
            } else { tunnel = nil }
            return await withCheckedContinuation { continuation in
                DispatchQueue.main.async {
                    let remaining = deadline - ProcessInfo.processInfo.systemUptime
                    guard !result.isFinished, remaining > 0,
                          let probe = NimboHTTPProbe(url: url, method: method, tunnel: tunnel, completion: result) else {
                        result.install { continuation.resume(returning: $0) }
                        result.finish(-1)
                        return
                    }
                    result.install { value in
                        DispatchQueue.main.async {
                            probe.close()
                            continuation.resume(returning: value)
                        }
                    }
                    if !result.isFinished { probe.start(timeout: remaining) }
                }
            }
        }, onCancel: { result.finish(-1) })
    }

    private func start(timeout: TimeInterval) {
        for stream in [input as Stream, output as Stream] {
            stream.delegate = self
            stream.schedule(in: .main, forMode: .common)
            stream.open()
        }
        let work = DispatchWorkItem { [weak self] in self?.completion.finish(-1) }
        deadline = work
        DispatchQueue.main.asyncAfter(deadline: .now() + timeout, execute: work)
    }

    private func close() {
        guard !done else { return }
        done = true
        deadline?.cancel()
        deadline = nil
        for stream in [input as Stream, output as Stream] {
            stream.close()
            stream.remove(from: .main, forMode: .common)
            stream.delegate = nil
        }
        tunnel = nil // Close descriptor only after both non-owning CF streams close.
    }

    func stream(_ aStream: Stream, handle eventCode: Stream.Event) {
        guard !done else { return }
        switch eventCode {
        case .hasSpaceAvailable:
            guard written < request.count else { return }
            let count = request.withUnsafeBufferPointer {
                output.write($0.baseAddress!.advanced(by: written), maxLength: request.count - written)
            }
            if count < 0 { completion.finish(-1) } else { written += count }
        case .hasBytesAvailable:
            var buffer = [UInt8](repeating: 0, count: 2048)
            let count = input.read(&buffer, maxLength: buffer.count)
            guard count > 0 else { completion.finish(-1); return }
            // Stop at the final header, even when the first read also contains body bytes.
            for byte in buffer.prefix(count) {
                receivedBytes += 1
                guard receivedBytes <= NimboPingPolicy.maximumHeaderBytes else { completion.finish(-1); return }
                header.append(byte)
                if header.suffix(4) == Data([13, 10, 13, 10]) {
                    guard let status = NimboPingPolicy.responseStatus(header) else { completion.finish(-1); return }
                    if status < 200 && status != 101 { header.removeAll(keepingCapacity: true); continue }
                    guard (200...299).contains(status) else { completion.finish(-1); return }
                    completion.finish(Int((DispatchTime.now().uptimeNanoseconds - started) / 1_000_000))
                    return
                }
            }
        case .errorOccurred:
            Self.trace("stream error \((aStream.streamError as NSError?)?.code ?? 0)")
            completion.finish(-1)
        case .endEncountered: completion.finish(-1)
        default: break
        }
    }
}
