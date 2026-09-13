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
    private let started = DispatchTime.now().uptimeNanoseconds
    private var written = 0
    private var header = Data()
    private var receivedBytes = 0
    private var done = false
    private var deadline: DispatchWorkItem?

    private init?(url: URL, method: String, socks: NimboPingSOCKS?, completion: NimboPingCompletion<Int>) {
        guard let bytes = NimboPingPolicy.request(url: url, method: method), let host = url.host else { return nil }
        var read: InputStream?
        var write: OutputStream?
        let bare = host.hasPrefix("[") ? String(host.dropFirst().dropLast()) : host
        Stream.getStreamsToHost(withName: bare, port: url.port ?? (url.scheme?.lowercased() == "https" ? 443 : 80), inputStream: &read, outputStream: &write)
        guard let read, let write else { return nil }
        self.input = read
        self.output = write
        self.request = Array(bytes)
        self.completion = completion
        super.init()
        if let socks {
            let proxy: [String: Any] = [
                kCFStreamPropertySOCKSProxyHost as String: "127.0.0.1",
                kCFStreamPropertySOCKSProxyPort as String: socks.port,
                kCFStreamPropertySOCKSVersion as String: kCFStreamSocketSOCKSVersion5,
                kCFStreamPropertySOCKSUser as String: socks.username,
                kCFStreamPropertySOCKSPassword as String: socks.password
            ]
            // Explicit SOCKS socket streams do not discover/bypass system proxies.
            // Do not require the CFHTTP ProxyLocalBypass property on these streams.
            // A failed SOCKS property remains terminal: never open directly.
            guard read.setProperty(proxy as NSDictionary, forKey: Stream.PropertyKey(rawValue: kCFStreamPropertySOCKSProxy as String)),
                  write.setProperty(proxy as NSDictionary, forKey: Stream.PropertyKey(rawValue: kCFStreamPropertySOCKSProxy as String)) else {
                Self.trace("SOCKS property rejected")
                return nil
            }
        }
        if url.scheme?.lowercased() == "https" {
            // Never install a trust override or disable certificate-chain validation.
            guard read.setProperty(StreamSocketSecurityLevel.negotiatedSSL.rawValue as NSString, forKey: .socketSecurityLevelKey),
                  write.setProperty(StreamSocketSecurityLevel.negotiatedSSL.rawValue as NSString, forKey: .socketSecurityLevelKey) else { return nil }
        }
    }

    private static func trace(_ message: String) {
        #if NIMBO_PING_TESTS
        // Stage only: never print URLs, credentials or stream error descriptions.
        FileHandle.standardError.write(Data("NimboHTTPProbe: \(message)\n".utf8))
        #endif
    }

    static func measure(url: URL, method: String, timeout: TimeInterval, socks: NimboPingSOCKS? = nil) async -> Int {
        guard timeout > 0, !Task.isCancelled else { return -1 }
        let result = NimboPingCompletion<Int>()
        return await withTaskCancellationHandler(operation: {
            await withCheckedContinuation { continuation in
                DispatchQueue.main.async {
                    guard let probe = NimboHTTPProbe(url: url, method: method, socks: socks, completion: result) else {
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
                    if !result.isFinished { probe.start(timeout: timeout) }
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
