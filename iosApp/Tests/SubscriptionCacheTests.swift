import Foundation

@main enum SubscriptionCacheTests {
    static func main() throws {
        let legacy = Data(#"{"servers":[{"id":"dummy","name":"Cached","protocol":"vless","rawConfiguration":"vless://dummy@node.invalid:443"}]}"#.utf8)
        let profile = try JSONDecoder().decode(NimboSubscriptionProfile.self, from: legacy)
        precondition(profile.parserRevision == 0) // Must trigger the local parser migration.
        precondition(profile.servers.count == 1)
        precondition(profile.servers[0].rawConfiguration == "vless://dummy@node.invalid:443")
        precondition(profile.servers[0].host == "" && !profile.servers[0].isNativeXrayJson)
        let encoded = try JSONEncoder().encode(profile)
        let roundTrip = try JSONDecoder().decode(NimboSubscriptionProfile.self, from: encoded)
        precondition(roundTrip == profile)

        let native = Data(#"{"servers":[{"id":"json","name":"Native","protocol":"vless","rawConfiguration":"{\"outbounds\":[{\"protocol\":\"vless\"}]}"}]}"#.utf8)
        let nativeProfile = try JSONDecoder().decode(NimboSubscriptionProfile.self, from: native)
        precondition(nativeProfile.servers[0].isNativeXrayJson) // Do not flatten a native record into share links.

        for invalid in [#"{"servers":[{"id":"x","name":"Bad","protocol":"vless"}]}"#,
                        #"{"servers":[{"id":"x","name":"Bad","protocol":"vless","rawConfiguration":""}]}"#,
                        #"{"servers":"wrong"}"#] {
            do {
                _ = try JSONDecoder().decode(NimboSubscriptionProfile.self, from: Data(invalid.utf8))
                preconditionFailure("Invalid cached profile was fabricated")
            } catch is DecodingError {}
        }
        print("PASS: legacy cached subscription decoding, round trip, native JSON preservation and invalid-record rejection")
    }
}
