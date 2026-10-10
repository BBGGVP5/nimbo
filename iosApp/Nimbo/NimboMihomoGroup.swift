import Foundation

/// Sanitized category projection only: no source credentials, native calls or saved-state writes.
struct NimboMihomoGroup: Identifiable {
    let name: String
    let type: String
    let members: [String]
    let current: String?
    var id: String { name }
    var selectable: Bool { type.lowercased() == "select" || type.lowercased() == "selector" }

    static func declared(_ entries: [[String: Any]], selections: [String: String]) -> [Self] {
        var seen = Set<String>()
        return entries.compactMap { entry in
            guard let name = entry["name"] as? String, seen.insert(name).inserted else { return nil }
            return project(name, entry, membersKey: "proxies", saved: selections[name])
        }
    }

    static func live(_ entries: [String: [String: Any]], declaredOrder: [String]) -> [Self] {
        var seen = Set<String>()
        let ordered = (declaredOrder + entries.keys.sorted()).filter { seen.insert($0).inserted }
        return ordered.compactMap { name in
            guard let entry = entries[name] else { return nil }
            return project(name, entry, membersKey: "all", saved: nil)
        }
    }

    private static func project(_ name: String, _ entry: [String: Any], membersKey: String, saved: String?) -> Self? {
        guard !name.isEmpty, entry["hidden"] as? Bool != true,
              let type = entry["type"] as? String, !type.isEmpty,
              let raw = entry[membersKey] as? [String] else { return nil }
        var seen = Set<String>()
        let members = raw.filter { !$0.isEmpty && seen.insert($0).inserted }
        let current = membersKey == "all" ? entry["now"] as? String : saved
        return Self(name: name, type: type, members: members, current: current.flatMap { members.contains($0) ? $0 : nil })
    }

    static func active(_ groups: [Self], name: String?) -> Self? {
        groups.first { $0.name == name } ?? groups.first
    }
}
