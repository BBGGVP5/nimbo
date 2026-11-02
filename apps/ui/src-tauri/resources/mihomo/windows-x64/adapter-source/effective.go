package mihomocore

import "go.yaml.in/yaml/v3"

// OriginalYAML and its hash remain the subscription identity. Only runtime
// groups are normalized: no telemetry/ML database and no rapid route flapping.
func normalizeSmartGroups(root map[string]any) bool {
	changed := false
	groups, _ := root["proxy-groups"].([]any)
	for _, item := range groups {
		g, ok := item.(map[string]any)
		if !ok || g["type"] != "smart" {
			continue
		}
		g["type"] = "url-test"
		g["interval"] = 600
		g["lazy"] = true
		g["tolerance"] = 100
		for _, key := range []string{"collect-data", "use-lightgbm", "lightgbm-model-path", "policy-priority", "sample-rate", "uselightgbm"} {
			delete(g, key)
		}
		changed = true
	}
	return changed
}

func effectiveYAML(d *inspection) ([]byte, error) {
	return yaml.Marshal(d.root)
}
