package mihomocore

import (
	"encoding/json"
	"github.com/metacubex/mihomo/config"
	S "github.com/metacubex/mihomo/constant/sniffer"
	"testing"
)

func TestAdBlockingPreservesOriginalSourceAndProviderRules(t *testing.T) {
	original := "mode: rule\nproxies: []\nproxy-groups: [{name: Pick, type: select, proxies: [DIRECT]}]\nrules:\n - DOMAIN-SUFFIX,provider.invalid,REJECT\n - MATCH,Pick\n"
	d, err := inspect(original)
	if err != nil {
		t.Fatal(err)
	}
	before, _ := json.Marshal(d.root)
	cfg, err := nativeParse(d)
	if err != nil {
		t.Fatal(err)
	}
	count := len(cfg.Rules)
	if err := applyAdBlocking(cfg, false); err != nil || len(cfg.Rules) != count {
		t.Fatal("off mutated routing")
	}
	if err := applyAdBlocking(cfg, true); err != nil {
		t.Fatal(err)
	}
	if len(cfg.Rules) != count+len(adDomains) {
		t.Fatal("missing overlay")
	}
	if !cfg.Sniffer.Enable || !cfg.Sniffer.ParsePureIp || len(cfg.Sniffer.Sniffers) < 3 {
		t.Fatal("runtime domain visibility missing")
	}
	for _, protocol := range []S.Type{S.HTTP, S.TLS, S.QUIC} {
		if cfg.Sniffer.Sniffers[protocol].OverrideDest {
			t.Fatal("runtime overlay must not override dial destination")
		}
	}
	if cfg.Rules[0].Payload() != "doubleclick.net" || cfg.Rules[0].Adapter() != "REJECT" {
		t.Fatal("overlay order")
	}
	if cfg.Rules[len(adDomains)].Payload() != "provider.invalid" {
		t.Fatal("provider rule lost")
	}
	after, _ := json.Marshal(d.root)
	if string(before) != string(after) || d.OriginalYAML != original {
		t.Fatal("stored source changed")
	}
}

func TestAdBlockingDoesNotSilentlyIgnoreNonRuleMode(t *testing.T) {
	cfg := &config.Config{}
	if applyAdBlocking(cfg, true) == nil {
		t.Fatal("incomplete config accepted")
	}
}
