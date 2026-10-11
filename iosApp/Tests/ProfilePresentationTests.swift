import Foundation

@main enum ProfilePresentationTests {
    static func main() throws {
        let secret = String(repeating: "private-token", count: 100_000)
        let original: [String: Any] = ["parserRevision": 5, "title": "Моя подписка", "format": "plain_links",
            "source": "https://subscription.invalid/private-token", "servers": [
                ["id": "node", "name": "Финляндия 🇫🇮", "protocol": "vless", "transport": "xhttp",
                 "security": "reality", "description": "Описание\nс переносом", "host": "private.invalid",
                 "port": 443, "rawConfiguration": secret, "isNativeXrayJson": false]]]
        let data = try JSONSerialization.data(withJSONObject: original)
        let compact = try NimboProfilePresentationJSON.make(data)!
        precondition(compact.utf8.count < 1_024)
        precondition(!compact.contains("private-token") && !compact.contains("private.invalid"))
        let decoded = try JSONSerialization.jsonObject(with: Data(compact.utf8)) as! [String: Any]
        let server = (decoded["servers"] as! [[String: Any]])[0]
        precondition(decoded["title"] as? String == "Моя подписка")
        precondition(server["id"] as? String == "node" && server["protocol"] as? String == "vless")
        precondition(server["transport"] as? String == "xhttp" && server["security"] as? String == "reality")
        precondition(server["description"] as? String == "Описание\nс переносом")
        precondition(server["rawConfiguration"] as? String == "")
        let untouched = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        precondition((untouched["servers"] as! [[String: Any]])[0]["rawConfiguration"] as? String == secret)
        let absent = try NimboProfilePresentationJSON.make(nil)
        precondition(absent == nil)
        do { _ = try NimboProfilePresentationJSON.make(Data("{}".utf8)); preconditionFailure("Invalid profile accepted") }
        catch NimboProfilePresentationJSON.PresentationError.invalidProfile {}
        print("PASS: bounded display-only profile, preserved labels and unchanged secret-bearing source")
    }
}
