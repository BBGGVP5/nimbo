import Foundation

// Portable seam: only Security persistence and observable defaults are replaced.
// The production ConfigurationStore implementation is compiled unchanged except
// for its unavailable Security import and the defaults type name on Linux.
typealias OSStatus = Int32
typealias CFDictionary = NSDictionary
typealias CFTypeRef = AnyObject
let errSecSuccess: OSStatus = 0
let errSecItemNotFound: OSStatus = -25300
let kSecClass = "class", kSecClassGenericPassword = "password"
let kSecAttrService = "service", kSecAttrAccount = "account"
let kSecValueData = "data", kSecAttrAccessible = "accessible"
let kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly = "afterUnlock"
let kSecReturnData = "return", kSecMatchLimit = "limit", kSecMatchLimitOne = "one"

private let records = NSLock()
private var items: [String: Data] = [:]
var launchForcedReadFailureAccount: String?
private func account(_ query: CFDictionary) -> String { query[kSecAttrAccount] as! String }
func SecItemUpdate(_ query: CFDictionary, _ attributes: CFDictionary) -> OSStatus {
    records.lock(); defer { records.unlock() }
    guard items[account(query)] != nil else { return errSecItemNotFound }
    items[account(query)] = attributes[kSecValueData] as? Data
    return errSecSuccess
}
func SecItemAdd(_ attributes: CFDictionary, _ result: UnsafeMutablePointer<CFTypeRef?>?) -> OSStatus {
    records.lock(); defer { records.unlock() }
    items[account(attributes)] = attributes[kSecValueData] as? Data
    return errSecSuccess
}
func SecItemCopyMatching(_ query: CFDictionary, _ result: UnsafeMutablePointer<CFTypeRef?>) -> OSStatus {
    records.lock(); defer { records.unlock() }
    if account(query) == launchForcedReadFailureAccount { return -25293 }
    guard let value = items[account(query)] else { return errSecItemNotFound }
    result.pointee = value as NSData
    return errSecSuccess
}
func SecItemDelete(_ query: CFDictionary) -> OSStatus {
    records.lock(); defer { records.unlock() }
    return items.removeValue(forKey: account(query)) == nil ? errSecItemNotFound : errSecSuccess
}

struct NimboFullConfiguration: Codable, Equatable {
    let title: String
    let source: String?
    let sourceData: Data
    func validate() throws {}
}
enum NimboFullConfigurationError: Error { case staleRefresh, fullConfigurationActive }

final class LaunchTestDefaults {
    static let standard = LaunchTestDefaults()
    private let lock = NSLock()
    private var values: [String: String] = [:]
    var onMutation: (() -> Void)?
    func string(forKey key: String) -> String? {
        lock.lock(); defer { lock.unlock() }; return values[key]
    }
    func set(_ value: String, forKey key: String) {
        lock.lock(); values[key] = value; lock.unlock(); onMutation?()
    }
    func removeObject(forKey key: String) {
        lock.lock(); values.removeValue(forKey: key); lock.unlock(); onMutation?()
    }
}
