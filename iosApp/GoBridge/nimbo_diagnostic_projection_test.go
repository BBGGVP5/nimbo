package main

import (
	"encoding/json"
	"testing"
)

func projectionFixture(text string) []json.RawMessage {
	var doc struct {
		Outbounds []json.RawMessage `json:"outbounds"`
	}
	if json.Unmarshal([]byte(text), &doc) != nil {
		panic("invalid fixture")
	}
	return doc.Outbounds
}
func TestDiagnosticTerminalFragmentProjection(t *testing.T) {
	fixture := `{"outbounds":[{"tag":"node","protocol":"vless","settings":{"vnext":[{"address":"fixture.example","port":443}]},"streamSettings":{"security":"reality","realitySettings":{"publicKey":"fixture-key","fingerprint":"chrome"},"sockopt":{"dialerProxy":"fragment","tcpKeepAliveIdle":37}}},{"tag":"fragment","protocol":"freedom","settings":{"fragment":{"packets":"tlshello","length":"100-200","interval":"1-5"}},"streamSettings":{"sockopt":{"tcpNoDelay":true}}},{"tag":"direct","protocol":"freedom"}]}`
	raw := projectionFixture(fixture)
	projected, code := diagnosticRawOutbounds(raw)
	if code != "" || len(projected) != 3 {
		t.Fatalf("fragment route lost: %s", code)
	}
	var node map[string]any
	json.Unmarshal(projected[1], &node)
	if node["tag"] != diagnosticTag {
		t.Fatal("forced identity lost")
	}
	stream := node["streamSettings"].(map[string]any)
	if stream["sockopt"].(map[string]any)["dialerProxy"] != "fragment" || stream["realitySettings"].(map[string]any)["publicKey"] != "fixture-key" {
		t.Fatal("raw transport lost")
	}
	var helper map[string]any
	json.Unmarshal(projected[2], &helper)
	if helper["tag"] != "fragment" {
		t.Fatal("helper identity lost")
	}
	for _, mutate := range []func(map[string]any){
		func(h map[string]any) { h["settings"].(map[string]any)["redirect"] = "127.0.0.1:9" },
		func(h map[string]any) { h["proxySettings"] = map[string]any{"tag": "direct"} },
		func(h map[string]any) {
			h["streamSettings"].(map[string]any)["sockopt"].(map[string]any)["dialerProxy"] = "direct"
		},
		func(h map[string]any) {
			h["streamSettings"].(map[string]any)["sockopt"].(map[string]any)["interface"] = "en0"
		},
	} {
		var h map[string]any
		json.Unmarshal(raw[1], &h)
		mutate(h)
		bad, _ := json.Marshal(h)
		if _, code := diagnosticRawOutbounds([]json.RawMessage{raw[0], bad}); code != "DIAGNOSTIC_UNSAFE_ROUTE" {
			t.Fatal("unsafe helper accepted:", code)
		}
	}
	// Additional/nested XHTTP dialers must not be excused by a valid root helper.
	var n map[string]any
	json.Unmarshal(raw[0], &n)
	n["streamSettings"].(map[string]any)["xhttpSettings"] = map[string]any{"extra": map[string]any{"downloadSettings": map[string]any{"sockopt": map[string]any{"dialerProxy": "direct"}}}}
	bad, _ := json.Marshal(n)
	if _, code := diagnosticRawOutbounds([]json.RawMessage{bad, raw[1]}); code != "DIAGNOSTIC_UNSAFE_ROUTE" {
		t.Fatal("nested dialer accepted")
	}
	if _, code := diagnosticRawOutbounds([]json.RawMessage{raw[0], raw[1], raw[1]}); code == "" {
		t.Fatal("duplicate tag accepted")
	}
	if _, code := diagnosticRawOutbounds([]json.RawMessage{raw[0], raw[1], json.RawMessage(`{"tag":"second","protocol":"vless"}`)}); code != "DIAGNOSTIC_AMBIGUOUS_ROUTE" {
		t.Fatal("group collapsed to first member")
	}
}
