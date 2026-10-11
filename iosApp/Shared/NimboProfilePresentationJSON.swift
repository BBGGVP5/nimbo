import Foundation

/// Display-only wire data. The original profile/credentials remain in the repository.
enum NimboProfilePresentationJSON {
    enum PresentationError: Error { case invalidProfile }

    static func make(_ data: Data?) throws -> String? {
        guard let data else { return nil }
        guard let source = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let servers = source["servers"] as? [[String: Any]] else {
            throw PresentationError.invalidProfile
        }
        var result: [String: Any] = [:]
        for key in ["parserRevision", "title", "format", "diagnosticCode"] {
            result[key] = source[key]
        }
        result["servers"] = servers.map { server in
            var display: [String: Any] = ["rawConfiguration": ""]
            for key in ["id", "name", "protocol", "transport", "security", "description"] {
                display[key] = server[key]
            }
            return display
        }
        let compact = try JSONSerialization.data(withJSONObject: result, options: [.sortedKeys])
        return String(decoding: compact, as: UTF8.self)
    }
}
