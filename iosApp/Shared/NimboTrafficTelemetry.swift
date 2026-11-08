import Foundation
import CoreFoundation

/// Read-only core counters. An absent or old bridge response stays unavailable.
struct NimboTrafficTelemetry {
    struct Routes {
        let proxyUpload: UInt64
        let proxyDownload: UInt64
        let directUpload: UInt64
        let directDownload: UInt64
    }
    let upload: UInt64
    let download: UInt64
    let routes: Routes?
    let tcpConnections: Int32?
    let udpConnections: Int32?

    static func nonnegativeInteger(_ value: Any?) -> UInt64? {
        guard let number = value as? NSNumber,
              CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        return UInt64(number.stringValue)
    }

    static func decode(_ value: Any?) -> Self? {
        guard let data = value as? [String: Any],
              let upload = nonnegativeInteger(data["upload"]),
              let download = nonnegativeInteger(data["download"]) else { return nil }
        let routes: Routes?
        if data["routeAvailable"] as? Bool == true,
           let proxyUpload = nonnegativeInteger(data["proxyUpload"]),
           let proxyDownload = nonnegativeInteger(data["proxyDownload"]),
           let directUpload = nonnegativeInteger(data["directUpload"]),
           let directDownload = nonnegativeInteger(data["directDownload"]) {
            routes = Routes(proxyUpload: proxyUpload, proxyDownload: proxyDownload,
                            directUpload: directUpload, directDownload: directDownload)
        } else { routes = nil }
        return Self(upload: upload, download: download, routes: routes,
                    tcpConnections: nonnegativeInteger(data["tcpConnections"]).flatMap { Int32(exactly: $0) },
                    udpConnections: nonnegativeInteger(data["udpConnections"]).flatMap { Int32(exactly: $0) })
    }

    var providerValue: [String: Any] {
        var data: [String: Any] = ["upload": upload, "download": download,
            "routeAvailable": routes != nil,
            "tcpConnections": tcpConnections.map { NSNumber(value: $0) as Any } ?? NSNull(),
            "udpConnections": udpConnections.map { NSNumber(value: $0) as Any } ?? NSNull()]
        if let routes {
            data["proxyUpload"] = routes.proxyUpload; data["proxyDownload"] = routes.proxyDownload
            data["directUpload"] = routes.directUpload; data["directDownload"] = routes.directDownload
        }
        return data
    }
}

struct NimboTunnelReport {
    let received: UInt64
    let sent: UInt64
    let memoryMb: Int
    let telemetry: NimboTrafficTelemetry?
    let activeAdBlockingEnabled: Bool?

    static func decode(_ response: [String: Any]) -> Self? {
        guard response["ok"] as? Bool == true,
              let received = NimboTrafficTelemetry.nonnegativeInteger(response["received"]),
              let sent = NimboTrafficTelemetry.nonnegativeInteger(response["sent"]) else { return nil }
        let memory = NimboTrafficTelemetry.nonnegativeInteger(response["memoryMb"]).flatMap { Int(exactly: $0) } ?? 0
        return Self(received: received, sent: sent, memoryMb: memory,
                    telemetry: NimboTrafficTelemetry.decode(response["telemetry"]),
                    activeAdBlockingEnabled: response["activeAdBlockingEnabled"] as? Bool)
    }
}
