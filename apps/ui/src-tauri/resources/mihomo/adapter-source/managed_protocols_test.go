package mihomocore

import (
	"strings"
	"testing"
)

// Both adapters initialize lazily and implement Close. A source-only admission
// must not be advertised as a live peer test, but the managed session must be
// able to create/stop them without bypassing its platform ownership policy.
func TestManagedMieruAndWireGuardLifecycle(t *testing.T) {
	for name, options := range map[string]string{
		"mieru":     "type: mieru, server: 192.0.2.1, port: 443, transport: TCP, username: fixture, password: fixture",
		"wireguard": "type: wireguard, server: 192.0.2.1, port: 51820, ip: 10.0.0.2, private-key: 'CQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=', public-key: 'CQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA='",
	} {
		t.Run(name, func(t *testing.T) {
			stopTest(t)
			defer stopTest(t)
			source := strings.Replace(simpleConfig, "type: direct", options, 1)
			d, err := inspect(source)
			if err != nil {
				t.Fatal(err)
			}
			if issues := policy(d); len(issues) != 0 {
				t.Fatalf("pinned protocol rejected: %+v", issues)
			}
			requireOK(t, call(t, "start", startFields(t, source)))
			requireOK(t, call(t, "stop", nil))
			singleton.mu.Lock()
			retained := singleton.session != nil
			singleton.mu.Unlock()
			if retained {
				t.Fatal("stop retained a native session")
			}
			// Host interface binding remains forbidden even for an admitted protocol.
			unsafe := strings.Replace(source, options, options+", interface-name: forbidden", 1)
			bad, err := inspect(unsafe)
			if err != nil || len(policy(bad)) == 0 {
				t.Fatal("platform-owned interface policy was bypassed")
			}
		})
	}
}
