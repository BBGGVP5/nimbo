import Foundation
import CoreFoundation

/// Recover the old selection from the securely retained config, not from host
/// or display-name similarity. Never guess between different credentials.
enum NimboSelectedServerRecovery {
    static func exactID(configuration: Data?, entries: [(id: String, raw: String)]) -> String? {
        guard let configuration, !configuration.isEmpty else { return nil }
        let matches = Set(entries.filter { Data($0.raw.utf8) == configuration }.map(\.id))
        return matches.count == 1 ? matches.first : nil
    }

    static func isAutomatic(_ data: Data?) -> Bool {
        guard let data, let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let nimbo = object["nimbo"] as? [String: Any],
              let flag = nimbo["balancer"] as? NSNumber,
              CFGetTypeID(flag) == CFBooleanGetTypeID(), flag.boolValue,
              let links = object["shareLinks"] as? [String], links.count >= 2 else { return false }
        return true
    }
}
