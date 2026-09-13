import Foundation
import NetworkExtension

enum NimboActiveRouteProbe {
    static func measure(session: NETunnelProviderSession?, serverIDs: [String], url: URL, timeout: TimeInterval, method: String = "GET", nimbo: Bool = true) async -> (id: String, latency: Int) {
        let result = NimboPingCompletion<(id: String, latency: Int)>()
        let requestID = UUID().uuidString
        return await withTaskCancellationHandler(operation: {
            await withCheckedContinuation { continuation in
                DispatchQueue.main.async {
                    guard let session, session.status == .connected, !serverIDs.isEmpty,
                          let request = try? JSONSerialization.data(withJSONObject: [
                            "command": nimbo ? "nimboPing" : "httpPing", "method": method,
                            "requestID": requestID, "serverIDs": serverIDs,
                            "url": url.absoluteString, "timeoutMs": Int(timeout * 1000)
                          ]) else {
                        result.install { continuation.resume(returning: $0) }
                        result.finish(("", -1))
                        return
                    }
                    // Disconnect/reconnect to the same profile also invalidates the sample.
                    let observer = NotificationCenter.default.addObserver(forName: .NEVPNStatusDidChange, object: session, queue: .main) { _ in result.finish(("", -1)) }
                    let timer = DispatchWorkItem { result.finish(("", -1)) }
                    result.install { value in
                        DispatchQueue.main.async {
                            timer.cancel()
                            NotificationCenter.default.removeObserver(observer)
                            if let cancel = try? JSONSerialization.data(withJSONObject: ["command": "cancelNimboPing", "requestID": requestID]) {
                                try? session.sendProviderMessage(cancel)
                            }
                            continuation.resume(returning: value)
                        }
                    }
                    guard !result.isFinished else { return }
                    DispatchQueue.main.asyncAfter(deadline: .now() + timeout, execute: timer)
                    do {
                        try session.sendProviderMessage(request) { data in
                            guard session.status == .connected, let data,
                                  let response = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
                                  response["ok"] as? Bool == true,
                                  let id = response["serverID"] as? String, serverIDs.contains(id),
                                  let latency = response["latency"] as? Int, latency >= 0 else {
                                result.finish(("", -1)); return
                            }
                            result.finish((id, latency))
                        }
                    } catch { result.finish(("", -1)) }
                }
            }
        }, onCancel: { result.finish(("", -1)) })
    }
}
