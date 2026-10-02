package mihomocore

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"sort"
	"strings"
	"unicode/utf8"

	"go.yaml.in/yaml/v3"
)

const maxSource = 4 << 20
const maxRequest = 8 << 20

type issue struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Path    string `json:"path,omitempty"`
}

func (e *issue) Error() string                  { return e.Code + ": " + e.Path + ": " + e.Message }
func problem(code, path, message string) *issue { return &issue{code, message, path} }

type declaredGraph struct {
	Proxies   []map[string]any          `json:"proxies"`
	Groups    []map[string]any          `json:"groups"`
	Providers map[string]map[string]any `json:"providers"`
}
type inspection struct {
	OriginalYAML  string        `json:"originalYAML"`
	SourceSHA256  string        `json:"sourceSHA256"`
	DocumentKind  string        `json:"documentKind"`
	RootKeys      []string      `json:"rootKeys"`
	DeclaredGraph declaredGraph `json:"declaredGraph"`
	StrictIssues  []issue       `json:"strictIssues"`
	root          map[string]any
	mobile        bool
	finalConfig   map[string]any
	desktop       bool
	android       bool
}

func decodeDocument(source string) (map[string]any, error) {
	if len(source) == 0 || len(source) > maxSource || !utf8.ValidString(source) {
		return nil, problem("INVALID_YAML", "$", "expected 1..4194304 bytes of UTF-8")
	}
	dec := yaml.NewDecoder(strings.NewReader(source))
	var doc yaml.Node
	if err := dec.Decode(&doc); err != nil {
		return nil, problem("INVALID_YAML", "$", err.Error())
	}
	var extra yaml.Node
	if err := dec.Decode(&extra); err != io.EOF {
		return nil, problem("INVALID_YAML", "$", "exactly one YAML document required")
	}
	count := 0
	var check func(*yaml.Node, int, map[*yaml.Node]bool) error
	check = func(n *yaml.Node, depth int, active map[*yaml.Node]bool) error {
		count++
		if count > 100000 || depth > 64 || active[n] {
			return problem("INVALID_YAML", "$", "YAML complexity/alias cycle limit")
		}
		active[n] = true
		defer delete(active, n)
		if n.Kind == yaml.MappingNode {
			seen := map[string]bool{}
			for i := 0; i < len(n.Content); i += 2 {
				k := n.Content[i]
				if k.Kind != yaml.ScalarNode || (k.Tag != "!!str" && k.Tag != "!!merge") {
					return problem("INVALID_YAML", "$", "mapping keys must be strings")
				}
				if seen[k.Value] {
					return problem("INVALID_YAML", k.Value, "duplicate mapping key")
				}
				seen[k.Value] = true
			}
		}
		if n.Alias != nil {
			if err := check(n.Alias, depth+1, active); err != nil {
				return err
			}
		}
		for _, c := range n.Content {
			if err := check(c, depth+1, active); err != nil {
				return err
			}
		}
		return nil
	}
	if err := check(&doc, 0, map[*yaml.Node]bool{}); err != nil {
		return nil, err
	}
	var root map[string]any
	if err := doc.Decode(&root); err != nil {
		return nil, problem("INVALID_YAML", "$", err.Error())
	}
	if root == nil {
		return nil, problem("INVALID_YAML", "$", "mapping required")
	}
	// Reject non-JSON values rather than corrupting a declared graph projection.
	if _, err := json.Marshal(root); err != nil {
		return nil, problem("INVALID_YAML", "$", "configuration contains non-JSON scalar values")
	}
	return root, nil
}

func inspect(source string) (*inspection, error) {
	root, err := decodeDocument(source)
	if err != nil {
		return nil, err
	}
	normalizeSmartGroups(root)
	sum := sha256.Sum256([]byte(source))
	d := &inspection{OriginalYAML: source, SourceSHA256: hex.EncodeToString(sum[:]), DocumentKind: "yaml", RootKeys: make([]string, 0, len(root)), root: root, StrictIssues: []issue{}, DeclaredGraph: declaredGraph{Proxies: []map[string]any{}, Groups: []map[string]any{}, Providers: map[string]map[string]any{}}}
	for key := range root {
		d.RootKeys = append(d.RootKeys, key)
	}
	sort.Strings(d.RootKeys)
	for _, key := range d.RootKeys {
		if isMihomoRootKey(key) {
			d.DocumentKind = "mihomo"
			break
		}
	}
	for _, key := range []string{"proxies", "proxy-groups"} {
		if value, exists := root[key]; exists {
			items, ok := value.([]any)
			if !ok {
				return nil, problem("INVALID_YAML", key, "sequence required")
			}
			for i, v := range items {
				m, ok := v.(map[string]any)
				if !ok {
					return nil, problem("INVALID_YAML", fmt.Sprintf("%s[%d]", key, i), "mapping required")
				}
				if key == "proxies" {
					d.DeclaredGraph.Proxies = append(d.DeclaredGraph.Proxies, m)
				} else {
					d.DeclaredGraph.Groups = append(d.DeclaredGraph.Groups, m)
				}
			}
		}
	}
	if value, exists := root["proxy-providers"]; exists {
		providers, ok := value.(map[string]any)
		if !ok {
			return nil, problem("INVALID_YAML", "proxy-providers", "mapping required")
		}
		for name, v := range providers {
			m, ok := v.(map[string]any)
			if !ok {
				return nil, problem("INVALID_YAML", "proxy-providers."+name, "mapping required")
			}
			d.DeclaredGraph.Providers[name] = m
		}
	}
	d.StrictIssues = policy(d)
	return d, nil
}

// isMihomoRootKey classifies source using the already parsed YAML mapping. It
// deliberately avoids substring heuristics: ordinary share-link text and
// unrelated YAML must never be mistaken for a native profile.
func isMihomoRootKey(key string) bool {
	switch key {
	case "proxies", "proxy-groups", "proxy-providers", "rule-providers", "rules", "sub-rules",
		"mode", "mixed-port", "port", "socks-port", "redir-port", "tproxy-port", "allow-lan",
		"bind-address", "ipv6", "dns", "hosts", "tun", "profile", "log-level", "external-controller",
		"external-controller-tls", "external-controller-unix", "external-controller-pipe", "external-ui",
		"external-ui-url", "external-ui-name", "secret", "find-process-mode", "interface-name",
		"routing-mark", "geodata-mode", "geo-auto-update", "geox-url", "global-client-fingerprint",
		"unified-delay", "tcp-concurrent", "keep-alive-idle", "keep-alive-interval", "disable-keep-alive",
		"sniffer", "ntp", "iptables", "listeners", "authentication", "lan-allowed-ips", "lan-disallowed-ips",
		"default-nameserver", "proxy-server-nameserver", "proxy-server-nameserver-policy", "experimental":
		return true
	default:
		return false
	}
}
