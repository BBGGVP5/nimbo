import Foundation
import UIKit

enum NimboPlatformInfo {
    static let displayVersion: String =
        Bundle.main.object(forInfoDictionaryKey: "NimboDisplayVersion") as? String
        ?? Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        ?? "—"

    static let buildNumber: String =
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "—"

    static let system: String = {
        let device = UIDevice.current
        return "\(device.systemName) \(device.systemVersion)"
    }()

    static let hardwareIdentifier: String = {
        if let simulatorModel = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] {
            return simulatorModel
        }

        var systemInfo = utsname()
        uname(&systemInfo)
        let machine = withUnsafePointer(to: &systemInfo.machine) { pointer in
            pointer.withMemoryRebound(to: CChar.self, capacity: 1) {
                String(cString: $0)
            }
        }
        return machine.isEmpty ? "unknown" : machine
    }()

    static let device: String = {
        let family = UIDevice.current.localizedModel
        let identifier = hardwareIdentifier
        guard identifier != "unknown" else { return family }
        return "\(family) · \(identifier)"
    }()

    static let userAgent: String = {
        let platform = UIDevice.current.userInterfaceIdiom == .pad ? "iPadOS" : "iOS"
        let version = UIDevice.current.systemVersion
        return "Nimbo/\(displayVersion) (\(platform) \(version); \(hardwareIdentifier))"
    }()
}

enum NimboNetworkSession {
    /// Use this session for every native iOS request so a subscription provider
    /// never receives the Android User-Agent from a shared/default client.
    static let shared: URLSession = {
        let configuration = URLSessionConfiguration.default
        var headers = configuration.httpAdditionalHeaders ?? [:]
        headers["User-Agent"] = NimboPlatformInfo.userAgent
        headers["X-Nimbo-Platform"] = "iOS"
        configuration.httpAdditionalHeaders = headers
        return URLSession(configuration: configuration)
    }()
}
