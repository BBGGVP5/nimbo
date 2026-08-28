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
        let configuration = URLSessionConfiguration.ephemeral
        configuration.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        configuration.urlCache = nil
        var headers = configuration.httpAdditionalHeaders ?? [:]
        headers["User-Agent"] = NimboPlatformInfo.userAgent
        headers["X-Nimbo-Platform"] = "iOS"
        headers["Accept"] = "text/plain, application/json;q=0.9, application/octet-stream;q=0.8, */*;q=0.5"
        configuration.httpAdditionalHeaders = headers
        return URLSession(configuration: configuration)
    }()

    static func subscriptionRequest(url: URL, timeout: TimeInterval = 25) -> URLRequest {
        var request = URLRequest(url: url)
        request.timeoutInterval = timeout
        request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        request.setValue("no-cache", forHTTPHeaderField: "Pragma")
        request.setValue("text/plain, application/json;q=0.9, application/octet-stream;q=0.8, */*;q=0.5", forHTTPHeaderField: "Accept")
        return request
    }
}
