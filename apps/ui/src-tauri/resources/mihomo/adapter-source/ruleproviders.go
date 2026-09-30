package mihomocore

import (
	"fmt"
	"reflect"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/metacubex/mihomo/component/resource"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	P "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/rules"
	RB "github.com/metacubex/mihomo/rules/bundle"
	RC "github.com/metacubex/mihomo/rules/common"
	RP "github.com/metacubex/mihomo/rules/provider"
	"github.com/metacubex/mihomo/tunnel"
)

// Block only rules needing unowned geodata/process facilities, including inside
// logical rules. Native parser remains the authority for all admitted syntax.
var gatedRule = regexp.MustCompile(`(?:^|\()\s*(?:GEOSITE|GEOIP|SRC-GEOIP|IP-ASN|SRC-IP-ASN|PROCESS-[A-Z-]+|UID)\s*,`)
var androidUnownedProcessRule = regexp.MustCompile(`(?:^|\()\s*(?:PROCESS-[A-Z-]+|UID)\s*,`)

func ruleListPolicy(value any, path string) []issue {
	out := []issue{}
	list, ok := value.([]any)
	if !ok {
		return []issue{{"INVALID_CONFIG", "rule string sequence required", path}}
	}
	for i, v := range list {
		s, ok := v.(string)
		if !ok {
			out = append(out, issue{"INVALID_CONFIG", "string required", fmt.Sprintf("%s[%d]", path, i)})
			continue
		}
		if gatedRule.MatchString(s) {
			out = append(out, issue{"UNSUPPORTED_CONFIG", "geodata/process rule requires explicit platform asset/process integration", fmt.Sprintf("%s[%d]", path, i)})
		}
	}
	return out
}
func ruleProviderPolicy(value any) []issue {
	out := []issue{}
	ps, ok := value.(map[string]any)
	if !ok {
		return []issue{{"INVALID_CONFIG", "mapping required", "rule-providers"}}
	}
	for name, v := range ps {
		path := "rule-providers." + name
		m, ok := v.(map[string]any)
		if !ok {
			out = append(out, issue{"INVALID_CONFIG", "mapping required", path})
			continue
		}
		for k := range m {
			switch k {
			case "type", "behavior", "format", "path", "url", "interval", "payload":
			default:
				out = append(out, issue{"UNSUPPORTED_CONFIG", "rule provider field not implemented", path + "." + k})
			}
		}
		if m["type"] != "inline" && m["type"] != "file" && m["type"] != "http" {
			out = append(out, issue{"UNSUPPORTED_CONFIG", "expected inline/file/http", path + ".type"})
		}
		if m["behavior"] != "domain" && m["behavior"] != "ipcidr" && m["behavior"] != "classical" {
			out = append(out, issue{"UNSUPPORTED_CONFIG", "expected domain/ipcidr/classical", path + ".behavior"})
		}
		if f := str(m, "format"); f != "" && f != "yaml" && f != "text" {
			out = append(out, issue{"UNSUPPORTED_CONFIG", "binary MRS not yet admitted by strict payload validator", path + ".format"})
		}
		if n, ok := m["interval"]; ok {
			if i, ok := n.(int); !ok || i < 0 || i > 2592000 {
				out = append(out, issue{"INVALID_CONFIG", "invalid interval", path + ".interval"})
			}
		}
		if payload, ok := m["payload"]; ok {
			out = append(out, ruleListPolicy(payload, path+".payload")...)
		}
	}
	return out
}

type androidRuleProviderSchema struct {
	Type         string              `provider:"type"`
	Behavior     string              `provider:"behavior"`
	Path         string              `provider:"path,omitempty"`
	URL          string              `provider:"url,omitempty"`
	Proxy        string              `provider:"proxy,omitempty"`
	Format       string              `provider:"format,omitempty"`
	Interval     int                 `provider:"interval,omitempty"`
	SizeLimit    int64               `provider:"size-limit,omitempty"`
	Payload      []string            `provider:"payload,omitempty"`
	Header       map[string][]string `provider:"header,omitempty"`
	PathInBundle string              `provider:"path-in-bundle,omitempty"`
}

// Android reuses Mihomo's native rule-provider implementation, including
// HTTP headers/proxy/size-limit, bundle providers and binary MRS. Only rules
// requiring process/UID flow metadata are blocked because Android's TUN bridge
// does not expose that metadata to Mihomo.
func androidRuleProviderIssues(provider map[string]any, path string) []issue {
	if issues := checkAndroidStructuredFields(provider, reflect.TypeOf(androidRuleProviderSchema{}), "provider", path); len(issues) > 0 {
		return issues
	}
	typeName, _ := provider["type"].(string)
	if typeName != "inline" && typeName != "file" && typeName != "http" {
		return []issue{{"UNSUPPORTED_ANDROID_CONFIG", "expected native Mihomo inline/file/http rule provider", path + ".type"}}
	}
	behavior, _ := provider["behavior"].(string)
	if behavior != "domain" && behavior != "ipcidr" && behavior != "classical" {
		return []issue{{"INVALID_CONFIG", "expected domain/ipcidr/classical", path + ".behavior"}}
	}
	format, _ := provider["format"].(string)
	if format != "" && format != "yaml" && format != "text" && format != "mrs" {
		return []issue{{"INVALID_CONFIG", "expected yaml, text or mrs", path + ".format"}}
	}
	for _, key := range []string{"interval", "size-limit"} {
		if value, exists := provider[key]; exists {
			n, ok := numericConfig(value)
			if !ok || n < 0 {
				return []issue{{"INVALID_CONFIG", "must be a nonnegative number", path + "." + key}}
			}
		}
	}
	if typeName == "http" {
		if err := checkURL(str(provider, "url")); err != nil {
			return []issue{{"INVALID_CONFIG", "explicit HTTP(S) URL without userinfo required", path + ".url"}}
		}
	}
	if payload, exists := provider["payload"]; exists {
		rows, ok := payload.([]any)
		if !ok {
			return []issue{{"INVALID_CONFIG", "string sequence required", path + ".payload"}}
		}
		for index, value := range rows {
			_, ok := value.(string)
			if !ok {
				return []issue{{"INVALID_CONFIG", "string required", fmt.Sprintf("%s.payload[%d]", path, index)}}
			}

		}
	}
	return nil
}

type managedRule struct {
	mu       sync.RWMutex
	current  P.RuleProvider
	fetcher  *resource.Fetcher[P.RuleProvider]
	name     string
	behavior P.RuleBehavior
	kind     P.VehicleType
	mobile   bool
	android  bool
}

func (p *managedRule) Name() string               { return p.name }
func (p *managedRule) Type() P.ProviderType       { return P.Rule }
func (p *managedRule) VehicleType() P.VehicleType { return p.kind }
func (p *managedRule) Behavior() P.RuleBehavior   { return p.behavior }
func (p *managedRule) Count() int {
	p.mu.RLock()
	defer p.mu.RUnlock()
	if p.current == nil {
		return 0
	}
	return p.current.Count()
}
func (p *managedRule) Strategy() any {
	p.mu.RLock()
	defer p.mu.RUnlock()
	if p.current == nil {
		return nil
	}
	return p.current.Strategy()
}
func (p *managedRule) Match(m *C.Metadata, h C.RuleMatchHelper) bool {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.current != nil && p.current.Match(m, h)
}
func (p *managedRule) Initial() error {
	if p.fetcher == nil {
		return nil
	}
	_, err := p.fetcher.Initial()
	return err
}
func (p *managedRule) Update() error {
	if p.fetcher == nil {
		return nil
	}
	_, _, err := p.fetcher.Update()
	return err
}
func (p *managedRule) Close() error {
	if p.fetcher != nil {
		return p.fetcher.Close()
	}
	return nil
}
func (p *managedRule) publish(r P.RuleProvider) {
	p.mu.Lock()
	p.current = r
	p.mu.Unlock()
	tunnel.Tunnel.RuleUpdateCallback().Emit(p)
}

func makeRulePayload(name string, behavior P.RuleBehavior, values []string, mobile, android bool) (P.RuleProvider, error) {
	if len(values) == 0 {
		return nil, fmt.Errorf("rule provider payload empty")
	}
	for _, s := range values {
		if s == "" || (!android && gatedRule.MatchString(s)) {
			return nil, fmt.Errorf("empty or gated rule provider entry")
		}
		if behavior == P.Classical {
			if mobile {
				if err := mobileRuleEntryPolicy(s, false); err != nil {
					return nil, fmt.Errorf("mobile rule provider entry rejected: %w", err)
				}
			}
			tp, payload, target, params := RC.ParseRulePayload(s, false)
			if tp == "MATCH" || tp == "RULE-SET" || tp == "SUB-RULE" {
				return nil, fmt.Errorf("classical rule provider cannot contain %s", tp)
			}
			if _, err := rules.ParseRule(tp, payload, target, params, nil); err != nil {
				return nil, err
			}
		}
	}
	p := RP.NewInlineProvider(name, behavior, values, rules.ParseRule)
	if p.Count() != len(values) {
		return nil, fmt.Errorf("native rule provider rejected/dropped entries; refusing replacement")
	}
	return p, nil
}
func ruleStrings(v any) ([]string, error) {
	list, ok := v.([]any)
	if !ok {
		return nil, fmt.Errorf("rule payload must be string sequence")
	}
	out := []string{}
	for _, s := range list {
		value, ok := s.(string)
		if !ok {
			return nil, fmt.Errorf("rule entry must be string")
		}
		out = append(out, value)
	}
	return out, nil
}
func rebindRuleProviders(cfg *config.Config, d *inspection, home string) error {
	defs, _ := d.root["rule-providers"].(map[string]any)
	if d.android {
		// Use Mihomo's native rule provider implementation for YAML, text, MRS,
		// inline payloads, HTTP headers/size limits and bundled MRS assets. The
		// parser wrapper only removes process/UID rules, which Android's TUN flow
		// metadata does not expose; every network fetch still uses the mandatory
		// protected socket hook.
		RP.SetTunnel(tunnel.Tunnel)
		for name, raw := range defs {
			mapping, ok := raw.(map[string]any)
			if !ok {
				return fmt.Errorf("rule provider %s must be a mapping", name)
			}
			provider, err := RP.ParseRuleProvider(name, mapping, parseAndroidRule, RB.MakeBundleFile)
			if err != nil {
				return fmt.Errorf("rule provider %s: %w", name, err)
			}
			closeProvider(cfg.RuleProviders[name])
			cfg.RuleProviders[name] = provider
		}
		return nil
	}
	for name, v := range defs {
		m := v.(map[string]any)
		behavior, err := P.ParseBehavior(str(m, "behavior"))
		if err != nil {
			return err
		}
		p := &managedRule{name: name, behavior: behavior, kind: P.Inline, mobile: d.mobile, android: d.android}
		parser := func(b []byte) (P.RuleProvider, error) {
			var values []string
			if str(m, "format") == "text" {
				if len(b) > maxSource {
					return nil, fmt.Errorf("rule payload too large")
				}
				for _, s := range strings.Split(string(b), "\n") {
					s = strings.TrimSpace(s)
					if s != "" && !strings.HasPrefix(s, "#") {
						values = append(values, s)
					}
				}
			} else {
				doc, err := decodeDocument(string(b))
				if err != nil {
					return nil, err
				}
				if len(doc) != 1 || doc["payload"] == nil {
					return nil, fmt.Errorf("rule provider YAML requires only payload")
				}
				values, err = ruleStrings(doc["payload"])
				if err != nil {
					return nil, err
				}
			}
			return makeRulePayload(name, behavior, values, p.mobile, p.android)
		}
		if raw, exists := m["payload"]; exists {
			values, err := ruleStrings(raw)
			if err != nil {
				return err
			}
			p.current, err = makeRulePayload(name, behavior, values, p.mobile, p.android)
			if err != nil {
				return err
			}
		}
		if m["type"] == "inline" {
			if p.current == nil {
				return fmt.Errorf("inline rule provider requires payload")
			}
		} else {
			v := &managedVehicle{kind: P.File}
			path := str(m, "path")
			if m["type"] == "http" {
				v.kind = P.HTTP
				v.url = str(m, "url")
				if err := checkURL(v.url); err != nil {
					return err
				}
				if path == "" {
					path = "rules/" + fmt.Sprintf("%x", []byte(name)) + ".yaml"
				}
			}
			v.path, err = safeProviderPath(home, path)
			if err != nil {
				return err
			}
			p.kind = v.kind
			p.fetcher = resource.NewFetcher(name, time.Duration(integer(m, "interval"))*time.Second, v, nil, parser, p.publish)
		}
		closeProvider(cfg.RuleProviders[name])
		cfg.RuleProviders[name] = p
	}
	return nil
}

func parseAndroidRule(tp, payload, target string, params []string, subRules map[string][]C.Rule) (C.Rule, error) {
	return rules.ParseRule(tp, payload, target, params, subRules)
}
