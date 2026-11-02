package mihomocore

import (
	"strings"
	"testing"
)

func TestSmartGroupsAreStableURLTestWithoutChangingSourceIdentity(t *testing.T) {
	source := "proxy-groups:\n - {name: Auto, type: smart, proxies: [a, b], url: https://example.test/check, interval: 10, collect-data: true, use-lightgbm: true, sample-rate: 1}\nrules: ['MATCH,Auto']\n"
	d, err := inspect(source)
	if err != nil {
		t.Fatal(err)
	}
	if d.OriginalYAML != source {
		t.Fatal("source identity changed")
	}
	g := d.DeclaredGraph.Groups[0]
	if g["type"] != "url-test" || g["interval"] != 600 || g["tolerance"] != 100 || g["lazy"] != true {
		t.Fatalf("effective group: %v", g)
	}
	if _, ok := g["collect-data"]; ok {
		t.Fatal("smart telemetry survived")
	}
	effective, err := effectiveYAML(d)
	if err != nil || strings.Contains(string(effective), "smart") || !strings.Contains(string(effective), "url-test") {
		t.Fatalf("effective YAML: %s %v", effective, err)
	}
	if len(g["proxies"].([]any)) != 2 {
		t.Fatal("members lost")
	}
}

func TestOrdinaryGroupsAndRulesStayUnchanged(t *testing.T) {
	source := "proxy-groups: [{name: Auto, type: url-test, proxies: [a], tolerance: 20, interval: 60}]\nrules: ['MATCH,Auto']\n"
	d, err := inspect(source)
	if err != nil {
		t.Fatal(err)
	}
	if d.DeclaredGraph.Groups[0]["interval"] != 60 || d.DeclaredGraph.Groups[0]["tolerance"] != 20 {
		t.Fatal("ordinary group changed")
	}
}

func TestRemovedMeshProtocolsFailAdmission(t *testing.T) {
	for _, protocol := range []string{"tailscale", "zerotier", "easytier"} {
		t.Run(protocol, func(t *testing.T) {
			issues := mihomoProxyIssues(map[string]any{"name": "private mesh", "type": protocol}, "proxies[0]")
			if len(issues) != 1 || issues[0].Code != "UNSUPPORTED_CONFIG" || issues[0].Path != "proxies[0].type" {
				t.Fatalf("removed mesh admitted: %v", issues)
			}
		})
	}
}
