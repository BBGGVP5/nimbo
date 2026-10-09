import Foundation
@main enum NaiveConfigurationTests {
 static func main() throws {
  for scheme in ["naive", "naive+https", "naive+quic"] {
   let input = "\(scheme)://u%2B:p%3A%40@192.0.2.1:443?peer=proxy.example#Readable name"
   let cfg = try NimboNaiveConfiguration.parseIfPresent(input)
   precondition(cfg != nil && !cfg!.rawText.contains("Readable"))
  }
  for text in ["naive://u@host", "naive://u:p@host:0", "naive://u:p@host/path", "naive://u:p@host\nvless://x"] {
   do { _ = try NimboNaiveConfiguration.parseIfPresent(text); preconditionFailure("invalid Naive link accepted") }
   catch NimboNaiveError.invalidConfiguration {}
  }
  let unrelated = try NimboNaiveConfiguration.parseIfPresent("vless://u@host:443")
  precondition(unrelated == nil)
  let original: [String: Any] = ["outbounds": [["tag":"proxy", "protocol":"socks"]],
   "routing": ["rules": [["type":"field", "domain":["domain:private.example"], "outboundTag":"direct"]]]]
  let result = NimboNaiveRouting.applying(to: original, dnsServer: "9.9.9.9")
  let dns = (result["outbounds"] as! [[String:Any]]).last!
  precondition((dns["settings"] as! [String:Any])["rewriteNetwork"] as! String == "tcp")
  precondition((dns["settings"] as! [String:Any])["rewriteAddress"] as! String == "9.9.9.9")
  precondition((dns["proxySettings"] as! [String:String])["tag"] == "proxy")
  let rules = (result["routing"] as! [String:Any])["rules"] as! [[String:Any]]
  precondition(rules.count == 3 && rules.last!["outboundTag"] as! String == "direct")
  precondition(rules[0]["outboundTag"] as! String == "proxy")
  print("Native Naive share admission and TCP DNS routing passed")
 }
}
